package net.tcguard.modules;

import net.tcguard.TCGuard;
import org.bukkit.Bukkit;

import java.io.File;
import java.sql.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.Date;
import java.util.concurrent.ConcurrentLinkedQueue;

public class DatabaseManager {

    private final TCGuard plugin;
    private Connection connection;
    private final ConcurrentLinkedQueue<LogEntry> writeQueue = new ConcurrentLinkedQueue<>();
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    private boolean isMySql = false;
    private volatile boolean isConnecting = false;

    public DatabaseManager(TCGuard plugin) {
        this.plugin = plugin;
    }

    public void init() {
        if (!plugin.getConfig().getBoolean("database.enabled", true)) {
            plugin.getLogger().info("[Database] Database disabled (RAM mode).");
            return;
        }

        if (isConnecting) return;
        isConnecting = true;

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                isMySql = "MYSQL".equalsIgnoreCase(plugin.getConfig().getString("database.type", "SQLITE"));

                if (isMySql) {
                    String host = plugin.getConfig().getString("database.mysql.host", "localhost");
                    int port = plugin.getConfig().getInt("database.mysql.port", 3306);
                    String db = plugin.getConfig().getString("database.mysql.database", "tcguard");
                    String user = plugin.getConfig().getString("database.mysql.username", "root");
                    String pass = plugin.getConfig().getString("database.mysql.password", "");
                    boolean useSsl = plugin.getConfig().getBoolean("database.mysql.use-ssl", false);

                    String url = "jdbc:mysql://" + host + ":" + port + "/" + db + "?useSSL=" + useSsl + "&autoReconnect=true&characterEncoding=utf8";
                    connection = DriverManager.getConnection(url, user, pass);
                    plugin.getLogger().info("[Database] MySQL connected: " + host + ":" + port + "/" + db);
                } else {
                    File dataFolder = plugin.getDataFolder();
                    if (!dataFolder.exists()) dataFolder.mkdirs();
                    File dbFile = new File(dataFolder, "tcguard_data.db");

                    Class.forName("org.sqlite.JDBC");
                    connection = DriverManager.getConnection("jdbc:sqlite:" + dbFile.getAbsolutePath());
                    plugin.getLogger().info("[Database] SQLite active: " + dbFile.getName());
                }

                createTables();
                pruneOldLogs(plugin.getConfig().getInt("database.retention-days", 30));

                Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, this::flushQueue, 60L, 60L);

            } catch (Exception e) {
                plugin.getLogger().warning("[Database] Connection error: " + e.getMessage());
            } finally {
                isConnecting = false;
            }
        });
    }

    private void createTables() {
        if (connection == null) return;
        try (Statement stmt = connection.createStatement()) {
            if (isMySql) {
                stmt.executeUpdate("CREATE TABLE IF NOT EXISTS tcguard_logs (" +
                        "id INT AUTO_INCREMENT PRIMARY KEY, " +
                        "timestamp BIGINT NOT NULL, " +
                        "player_name VARCHAR(32) NOT NULL, " +
                        "threat_type VARCHAR(32) NOT NULL, " +
                        "details TEXT, " +
                        "formatted_time VARCHAR(32), " +
                        "INDEX idx_player (player_name), " +
                        "INDEX idx_type (threat_type)" +
                        ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;");
            } else {
                stmt.executeUpdate("CREATE TABLE IF NOT EXISTS tcguard_logs (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                        "timestamp BIGINT NOT NULL, " +
                        "player_name VARCHAR(32) NOT NULL, " +
                        "threat_type VARCHAR(32) NOT NULL, " +
                        "details TEXT, " +
                        "formatted_time VARCHAR(32)" +
                        ");");
                stmt.executeUpdate("CREATE INDEX IF NOT EXISTS idx_player ON tcguard_logs(player_name);");
                stmt.executeUpdate("CREATE INDEX IF NOT EXISTS idx_type ON tcguard_logs(threat_type);");
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("[Database] Error creating tables: " + e.getMessage());
        }
    }

    private final Object dbLock = new Object();

    public void logIncident(String playerName, String threatType, String details) {
        if (!plugin.getConfig().getBoolean("database.enabled", true)) return;

        long now = System.currentTimeMillis();
        String formattedTime = dateFormat.format(new Date(now));
        writeQueue.add(new LogEntry(now, playerName != null ? playerName : "Unknown", threatType, details, formattedTime));
    }

    private void flushQueue() {
        if (writeQueue.isEmpty()) return;

        synchronized (dbLock) {
            try {
                if (connection == null || connection.isClosed()) {
                    if (!isConnecting) {
                        init();
                    }
                    return;
                }

                String sql = "INSERT INTO tcguard_logs (timestamp, player_name, threat_type, details, formatted_time) VALUES (?, ?, ?, ?, ?)";
                try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
                    connection.setAutoCommit(false);

                    int count = 0;
                    while (!writeQueue.isEmpty() && count < 500) {
                        LogEntry entry = writeQueue.poll();
                        if (entry == null) break;

                        pstmt.setLong(1, entry.timestamp);
                        pstmt.setString(2, entry.playerName);
                        pstmt.setString(3, entry.threatType);
                        pstmt.setString(4, entry.details != null ? entry.details : "");
                        pstmt.setString(5, entry.formattedTime);
                        pstmt.addBatch();
                        count++;
                    }

                    if (count > 0) {
                        pstmt.executeBatch();
                        connection.commit();
                    }
                    connection.setAutoCommit(true);
                }
            } catch (Exception e) {
                plugin.getLogger().warning("[Database] Batch write error: " + e.getMessage());
            }
        }
    }

    public List<LogEntry> queryPlayerHistory(String playerName, int limit) {
        List<LogEntry> results = new ArrayList<>();
        synchronized (dbLock) {
            if (connection == null) return results;

            String sql = "SELECT timestamp, player_name, threat_type, details, formatted_time FROM tcguard_logs WHERE LOWER(player_name) = LOWER(?) ORDER BY timestamp DESC LIMIT ?";
            try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
                pstmt.setString(1, playerName);
                pstmt.setInt(2, limit);

                try (ResultSet rs = pstmt.executeQuery()) {
                    while (rs.next()) {
                        results.add(new LogEntry(
                                rs.getLong("timestamp"),
                                rs.getString("player_name"),
                                rs.getString("threat_type"),
                                rs.getString("details"),
                                rs.getString("formatted_time")
                        ));
                    }
                }
            } catch (Exception e) {
                plugin.getLogger().warning("[Database] History query error: " + e.getMessage());
            }
        }
        return results;
    }

    public List<LogEntry> queryRecentLogs(int limit) {
        List<LogEntry> results = new ArrayList<>();
        synchronized (dbLock) {
            if (connection == null) return results;

            String sql = "SELECT timestamp, player_name, threat_type, details, formatted_time FROM tcguard_logs ORDER BY timestamp DESC LIMIT ?";
            try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
                pstmt.setInt(1, limit);

                try (ResultSet rs = pstmt.executeQuery()) {
                    while (rs.next()) {
                        results.add(new LogEntry(
                                rs.getLong("timestamp"),
                                rs.getString("player_name"),
                                rs.getString("threat_type"),
                                rs.getString("details"),
                                rs.getString("formatted_time")
                        ));
                    }
                }
            } catch (Exception e) {
                plugin.getLogger().warning("[Database] Recent logs query error: " + e.getMessage());
            }
        }
        return results;
    }

    public Map<String, Integer> queryThreatSummary() {
        Map<String, Integer> summary = new LinkedHashMap<>();
        synchronized (dbLock) {
            if (connection == null) return summary;

            String sql = "SELECT threat_type, COUNT(*) as total FROM tcguard_logs GROUP BY threat_type ORDER BY total DESC";
            try (Statement stmt = connection.createStatement();
                 ResultSet rs = stmt.executeQuery(sql)) {
                while (rs.next()) {
                    summary.put(rs.getString("threat_type"), rs.getInt("total"));
                }
            } catch (Exception ignored) {}
        }
        return summary;
    }

    public void pruneOldLogs(int days) {
        synchronized (dbLock) {
            if (connection == null || days <= 0) return;
            long cutoff = System.currentTimeMillis() - (days * 86400000L);

            String sql = "DELETE FROM tcguard_logs WHERE timestamp < ?";
            try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
                pstmt.setLong(1, cutoff);
                int deleted = pstmt.executeUpdate();
                if (deleted > 0) {
                    plugin.getLogger().info("[Database] Pruned " + deleted + " old log entries.");
                }
            } catch (Exception ignored) {}
        }
    }

    public void close() {
        try {
            flushQueue();
        } catch (Exception ignored) {}

        if (connection != null) {
            try {
                connection.close();
            } catch (Exception ignored) {}
        }
    }

    public static class LogEntry {
        public final long timestamp;
        public final String playerName;
        public final String threatType;
        public final String details;
        public final String formattedTime;

        public LogEntry(long timestamp, String playerName, String threatType, String details, String formattedTime) {
            this.timestamp = timestamp;
            this.playerName = playerName;
            this.threatType = threatType;
            this.details = details;
            this.formattedTime = formattedTime;
        }
    }
}
