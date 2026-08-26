package net.tcguard.modules;

import net.tcguard.TCGuard;
import net.tcguard.config.ConfigManager;

import java.net.InetAddress;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

public class AntiBotManager {

    private final TCGuard plugin;
    private final Map<String, IpJoinTracker> ipTrackers = new ConcurrentHashMap<>();
    private static final int MAX_IP_ENTRIES = 3000;

    private volatile int globalJoinsThisSecond = 0;
    private volatile long lastGlobalReset = System.currentTimeMillis();
    private volatile boolean underAttack = false;

    private Pattern compiledNamePattern;

    public AntiBotManager(TCGuard plugin) {
        this.plugin = plugin;
        updatePattern();
    }

    public void updatePattern() {
        try {
            compiledNamePattern = Pattern.compile(plugin.getConfigManager().getNameRegex());
        } catch (Exception e) {
            compiledNamePattern = Pattern.compile("^[a-zA-Z0-9_]{3,16}$");
        }
    }

    public String checkConnection(String playerName, InetAddress address) {
        ConfigManager cfg = plugin.getConfigManager();
        if (!cfg.isAntiBotEnabled()) return null;

        if (address == null) {
            return cfg.getKickMessageBot();
        }

        String ip = address.getHostAddress();

        if (cfg.isEnforceProxyOnly()) {
            boolean allowed = false;
            for (String allowedIp : cfg.getAllowedProxyIps()) {
                if (ip.equals(allowedIp) || ip.startsWith(allowedIp)) {
                    allowed = true;
                    break;
                }
            }
            if (!allowed) {
                return cfg.getLanguageManager().get("proxy_only");
            }
        }

        if (playerName == null || playerName.length() < 3 || playerName.length() > 16) {
            return cfg.getKickMessageExploit();
        }

        if (compiledNamePattern != null && !compiledNamePattern.matcher(playerName).matches()) {
            return cfg.getKickMessageExploit();
        }

        if (cfg.isBlockInvisibleNames() && hasInvisibleCharacters(playerName)) {
            return cfg.getKickMessageExploit();
        }

        long now = System.currentTimeMillis();
        synchronized (this) {
            if (now - lastGlobalReset > 1000L) {
                globalJoinsThisSecond = 1;
                lastGlobalReset = now;
                underAttack = false;
            } else {
                globalJoinsThisSecond++;
                if (globalJoinsThisSecond > cfg.getMaxGlobalJoinsPerSecond()) {
                    underAttack = true;
                    plugin.alertAdmins("Anti-Bot: Global join flood (" + globalJoinsThisSecond + "/s) - Attack Shield Active");
                    plugin.getStatsManager().recordIncident("BotSwarm", "BOT", "Global Join Flood (" + globalJoinsThisSecond + "/s)");
                    return cfg.getKickMessageBot();
                }
            }
        }

        if (ipTrackers.size() > MAX_IP_ENTRIES) {
            cleanupOldEntries();
            if (ipTrackers.size() > MAX_IP_ENTRIES) {
                ipTrackers.clear();
            }
        }

        IpJoinTracker tracker = ipTrackers.computeIfAbsent(ip, k -> new IpJoinTracker());
        if (tracker.recordAndCheck(cfg.getMaxJoinsPerIpPerMinute())) {
            plugin.alertAdmins("Anti-Bot: Excessive joins from " + ip);
            plugin.getStatsManager().recordIncident(playerName, "BOT", "IP Rate Exceeded: " + ip);
            plugin.getThreatIntelligence().quarantineIp(ip, 120);
            return cfg.getKickMessageBot();
        }

        String threatResult = plugin.getThreatIntelligence().analyzeConnection(playerName, address);
        if (threatResult != null) {
            return threatResult;
        }

        return null;
    }

    private boolean hasInvisibleCharacters(String str) {
        for (char c : str.toCharArray()) {
            if (Character.isISOControl(c) || Character.isWhitespace(c)
                    || c == '\u200B' || c == '\u200C' || c == '\u200D' || c == '\uFEFF') {
                return true;
            }
        }
        return false;
    }

    public boolean isUnderAttack() {
        return underAttack;
    }

    public void cleanupOldEntries() {
        long now = System.currentTimeMillis();
        ipTrackers.entrySet().removeIf(e -> (now - e.getValue().lastJoinTime) > 60000L);
    }

    public void clear() {
        ipTrackers.clear();
        globalJoinsThisSecond = 0;
        underAttack = false;
    }

    private static class IpJoinTracker {
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
