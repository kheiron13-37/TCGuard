package net.tcguard.modules;

import net.tcguard.TCGuard;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class ThreatIntelligence {

    private final TCGuard plugin;

    private final Map<String, Long> handshakeTimestamps = new ConcurrentHashMap<>();
    private final Map<String, SubnetTracker> subnetTrackers = new ConcurrentHashMap<>();
    private final Map<String, Long> quarantinedIps = new ConcurrentHashMap<>();

    private volatile boolean underAttack = false;
    private volatile long attackModeExpires = 0;
    private final java.util.concurrent.atomic.AtomicInteger blockedBotsCount = new java.util.concurrent.atomic.AtomicInteger(0);

    private static final int MAX_MAP_ENTRIES = 5000;

    public ThreatIntelligence(TCGuard plugin) {
        this.plugin = plugin;
        // Schedule periodic asynchronous map cleanup every 30 seconds
        try {
            org.bukkit.Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, this::cleanupOldData, 600L, 600L);
        } catch (Throwable ignored) {}
    }

    public void recordHandshake(InetSocketAddress address) {
        if (address == null || address.getAddress() == null) return;
        String ip = address.getAddress().getHostAddress();
        if (handshakeTimestamps.size() < MAX_MAP_ENTRIES) {
            handshakeTimestamps.put(ip, System.currentTimeMillis());
        }
    }

    public String analyzeConnection(String playerName, InetAddress address) {
        if (address == null) return "§cInvalid IP address.";
        String ip = address.getHostAddress();
        long now = System.currentTimeMillis();

        Long unbanTime = quarantinedIps.get(ip);
        if (unbanTime != null) {
            if (now < unbanTime) {
                long remainingSeconds = (unbanTime - now) / 1000L;
                return plugin.getConfigManager().getLanguageManager().get("quarantine_msg").replace("{seconds}", String.valueOf(remainingSeconds));
            } else {
                quarantinedIps.remove(ip);
            }
        }

        boolean isLocal = ip.equals("127.0.0.1") || ip.equals("localhost") || ip.startsWith("192.168.") || ip.startsWith("10.");

        if (!isLocal && plugin.getConfig().getBoolean("anti-bot.timing-protection.enabled", true)) {
            Long handshakeTime = handshakeTimestamps.remove(ip);
            if (handshakeTime != null) {
                long elapsed = now - handshakeTime;
                int minTimingMs = plugin.getConfig().getInt("anti-bot.timing-protection.min-handshake-duration-ms", 25);

                if (elapsed < minTimingMs) {
                    quarantineIp(ip, 120);
                    plugin.alertAdmins("§4[Anti-Bot] §c" + playerName + " (" + ip + ") 0.0ms Handshake Bot blocked (" + elapsed + "ms)");
                    plugin.getStatsManager().recordIncident(playerName, "BOT", "0ms Handshake Timing (" + elapsed + "ms)");
                    plugin.getDiscordWebhook().sendAlert("0ms Handshake Bot Blocked", playerName,
                            "Handshake Timing Exploit", "IP: " + maskIp(ip) + " | Elapsed: " + elapsed + "ms", 0xFF0000);
                    return plugin.getConfigManager().getLanguageManager().get("timing_bot_msg");
                }
            }
        }

        if (!isLocal && plugin.getConfig().getBoolean("anti-bot.subnet-protection.enabled", true)) {
            String subnet = extractSubnet(ip);
            SubnetTracker tracker = subnetTrackers.computeIfAbsent(subnet, k -> new SubnetTracker());

            int maxPerSubnet = underAttack ? 
                    plugin.getConfig().getInt("anti-bot.subnet-protection.max-joins-under-attack", 2) :
                    plugin.getConfig().getInt("anti-bot.subnet-protection.max-joins-per-subnet-per-minute", 6);

            if (tracker.recordAndCheck(maxPerSubnet)) {
                quarantineIp(ip, 180);
                triggerAttackMode();
                plugin.alertAdmins("§4[Anti-Bot] §c" + subnet + " distributed botnet flood blocked.");
                plugin.getStatsManager().recordIncident(playerName, "BOTNET_SUBNET", "Subnet Limit: " + subnet);
                return plugin.getConfigManager().getLanguageManager().get("subnet_flood_msg");
            }
        }

        if (underAttack) {
            blockedBotsCount.incrementAndGet();
        }

        return null;
    }

    public synchronized void triggerAttackMode() {
        long now = System.currentTimeMillis();
        int durationSec = plugin.getConfig().getInt("anti-bot.attack-mode.duration-seconds", 60);
        attackModeExpires = now + (durationSec * 1000L);

        if (!underAttack) {
            underAttack = true;
            plugin.alertAdmins("§c🚨 [TCG-Security] Dynamic Attack Mode Activated.");
            plugin.getDiscordWebhook().sendAlert("ATTACK MODE ACTIVATED", "Security Shield",
                    "Bot Flood Detected", "Duration: " + durationSec + "s", 0xFF0000);
            if (plugin.getProxyBridge() != null) {
                plugin.getProxyBridge().syncAttackModeToNetwork();
            }
        }
    }

    public void quarantineIp(String ip, int seconds) {
        if (ip == null) return;
        quarantinedIps.put(ip, System.currentTimeMillis() + (seconds * 1000L));
    }

    public int getBlockedBotsCount() {
        return blockedBotsCount.get();
    }

    public void incrementBlockedBots() {
        blockedBotsCount.incrementAndGet();
    }

    public boolean isUnderAttack() {
        if (underAttack && System.currentTimeMillis() > attackModeExpires) {
            synchronized (this) {
                if (underAttack && System.currentTimeMillis() > attackModeExpires) {
                    underAttack = false;
                    plugin.alertAdmins("§a🛡️ [TCG-Security] Attack ended. Normal mode restored.");
                }
            }
        }
        return underAttack;
    }

    public static String extractSubnet(String ip) {
        if (ip == null) return "unknown";
        if (ip.contains(".")) {
            int lastDot = ip.lastIndexOf('.');
            if (lastDot > 0) {
                return ip.substring(0, lastDot) + ".0/24";
            }
        } else if (ip.contains(":")) {
            String[] parts = ip.split(":");
            if (parts.length >= 3) {
                return parts[0] + ":" + parts[1] + ":" + parts[2] + "::/48";
            }
        }
        return ip;
    }

    private String maskIp(String ip) {
        if (ip == null) return "***";
        int lastDot = ip.lastIndexOf('.');
        if (lastDot > 0) {
            return ip.substring(0, lastDot) + ".***";
        }
        return ip;
    }

    public void cleanupOldData() {
        long now = System.currentTimeMillis();
        handshakeTimestamps.entrySet().removeIf(e -> (now - e.getValue()) > 30000L);
        subnetTrackers.entrySet().removeIf(e -> (now - e.getValue().lastJoinTime) > 60000L);
        quarantinedIps.entrySet().removeIf(e -> now > e.getValue());
    }

    public void clearAll() {
        handshakeTimestamps.clear();
        subnetTrackers.clear();
        quarantinedIps.clear();
        underAttack = false;
    }

    private static class SubnetTracker {
        private int count = 0;
        private long windowStart = System.currentTimeMillis();
        private long lastJoinTime = System.currentTimeMillis();

        public synchronized boolean recordAndCheck(int maxPerMinute) {
            long now = System.currentTimeMillis();
            lastJoinTime = now;
            if (now - windowStart > 60000L) {
                count = 1;
                windowStart = now;
                return false;
            }
            count++;
            return count > maxPerMinute;
        }
    }
}
