package net.tcguard.modules;

import net.tcguard.TCGuard;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;

public class StatsManager {

    private final TCGuard plugin;
    private final AtomicLong blockedCrashes = new AtomicLong(0);
    private final AtomicLong blockedBots = new AtomicLong(0);
    private final AtomicLong blockedPacketFloods = new AtomicLong(0);
    private final AtomicLong blockedNbtExploits = new AtomicLong(0);
    private final AtomicLong blockedRedstoneLags = new AtomicLong(0);
    private final AtomicLong blockedCommands = new AtomicLong(0);

    private final List<IncidentRecord> recentIncidents = new CopyOnWriteArrayList<>();
    private final SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm:ss");
    private static final int MAX_RECENT = 20;

    public StatsManager(TCGuard plugin) {
        this.plugin = plugin;
    }

    public void recordIncident(String playerName, String type, String details) {
        switch (type.toUpperCase()) {
            case "CRASH":
            case "LOG4J":
                blockedCrashes.incrementAndGet();
                break;
            case "BOT":
            case "BOTNET_SUBNET":
                blockedBots.incrementAndGet();
                break;
            case "PACKET_FLOOD":
            case "AUTO_CLICKER":
            case "FAST_BREAK":
            case "FAST_PLACE":
            case "NETTY_FRAME":
                blockedPacketFloods.incrementAndGet();
                break;
            case "NBT_EXPLOIT":
            case "DUPLICATION":
                blockedNbtExploits.incrementAndGet();
                break;
            case "REDSTONE_LAG":
                blockedRedstoneLags.incrementAndGet();
                break;
            case "COMMAND":
                blockedCommands.incrementAndGet();
                break;
        }

        if (recentIncidents.size() >= MAX_RECENT) {
            recentIncidents.remove(0);
        }
        recentIncidents.add(new IncidentRecord(playerName, type, details, timeFormat.format(new Date())));

        if (plugin != null && plugin.getDatabaseManager() != null) {
            plugin.getDatabaseManager().logIncident(playerName, type, details);
        }
    }

    public long getBlockedCrashes() { return blockedCrashes.get(); }
    public long getBlockedBots() { return blockedBots.get(); }
    public long getBlockedPacketFloods() { return blockedPacketFloods.get(); }
    public long getBlockedNbtExploits() { return blockedNbtExploits.get(); }
    public long getBlockedRedstoneLags() { return blockedRedstoneLags.get(); }
    public long getBlockedCommands() { return blockedCommands.get(); }

    public long getTotalBlocked() {
        return blockedCrashes.get() + blockedBots.get() + blockedPacketFloods.get()
                + blockedNbtExploits.get() + blockedRedstoneLags.get() + blockedCommands.get();
    }

    public List<IncidentRecord> getRecentIncidents() {
        return recentIncidents;
    }

    public static class IncidentRecord {
        private final String playerName;
        private final String type;
        private final String details;
        private final String time;

        public IncidentRecord(String playerName, String type, String details, String time) {
            this.playerName = playerName;
            this.type = type;
            this.details = details;
            this.time = time;
        }

        public String getPlayerName() { return playerName; }
        public String getType() { return type; }
        public String getDetails() { return details; }
        public String getTime() { return time; }
    }
}
