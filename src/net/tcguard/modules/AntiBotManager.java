package net.tcguard.modules;

import net.tcguard.TCGuard;
import net.tcguard.config.ConfigManager;

import java.net.InetAddress;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

public class AntiBotManager {

    private final TCGuard plugin;
    private final Map<String, IpJoinTracker> ipTrackers = new ConcurrentHashMap<>();
    private final Map<String, Long> pingTimestamps = new ConcurrentHashMap<>();
    private final Map<String, Boolean> datacenterCache = new ConcurrentHashMap<>();
    private final List<RecentJoinEntry> recentJoins = Collections.synchronizedList(new ArrayList<>());

    private static final int MAX_IP_ENTRIES = 4000;
    private static final Pattern HEX_PATTERN = Pattern.compile("^[a-f0-9]{8,16}$", Pattern.CASE_INSENSITIVE);
    private static final Pattern EXCESSIVE_NUMBERS = Pattern.compile(".*\\d{5,}.*");

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

    public void recordPing(InetAddress address) {
        if (address == null) return;
        String ip = address.getHostAddress();
        pingTimestamps.put(ip, System.currentTimeMillis());

        if (pingTimestamps.size() > MAX_IP_ENTRIES) {
            cleanupOldEntries();
        }
    }

    public boolean hasPingedRecently(InetAddress address, long maxAgeMs) {
        if (address == null) return false;
        String ip = address.getHostAddress();
        Long lastPing = pingTimestamps.get(ip);
        if (lastPing == null) return false;
        return (System.currentTimeMillis() - lastPing) <= maxAgeMs;
    }

    public String checkConnection(String playerName, InetAddress address) {
        ConfigManager cfg = plugin.getConfigManager();
        if (!cfg.isAntiBotEnabled()) return null;

        if (address == null) {
            return cfg.getKickMessageBot();
        }

        String ip = address.getHostAddress();
        boolean isLocal = ip.equals("127.0.0.1") || ip.equals("localhost") || ip.startsWith("192.168.") || ip.startsWith("10.");

        if (cfg.isEnforceProxyOnly()) {
            boolean allowed = false;
            for (String allowedIp : cfg.getAllowedProxyIps()) {
                if (allowedIp == null || allowedIp.isEmpty()) continue;
                if (ip.equals(allowedIp)) {
                    allowed = true;
                    break;
                }
                if (allowedIp.endsWith(".") && ip.startsWith(allowedIp)) {
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

        // 1. Next-Gen Ping Gate Check
        if (!isLocal && plugin.getConfig().getBoolean("anti-bot.ping-gate.enabled", true)) {
            boolean onlyUnderAttack = plugin.getConfig().getBoolean("anti-bot.ping-gate.only-under-attack", false);
            boolean currentAttack = isUnderAttack() || plugin.getThreatIntelligence().isUnderAttack();

            if (!onlyUnderAttack || currentAttack) {
                int maxPingAge = plugin.getConfig().getInt("anti-bot.ping-gate.max-ping-age-seconds", 60);
                if (!hasPingedRecently(address, maxPingAge * 1000L)) {
                    plugin.alertAdmins("§4[Ping-Gate] §c" + playerName + " (" + ip + ") joined without pinging server list.");
                    plugin.getStatsManager().recordIncident(playerName, "BOT_PING_GATE", "Direct join without MOTD ping");
                    return cfg.getLanguageManager().get("ping_gate_msg");
                }
            }
        }

        // 2. Next-Gen Algorithmic Name Heuristics Check
        if (plugin.getConfig().getBoolean("anti-bot.name-heuristics.enabled", true)) {
            if (isSuspiciousBotName(playerName)) {
                plugin.alertAdmins("§4[Name-Heuristics] §c" + playerName + " algorithmic bot pattern blocked.");
                plugin.getStatsManager().recordIncident(playerName, "BOT_NAME_HEURISTIC", "Algorithmic username generator");
                return cfg.getLanguageManager().get("bot_name_heuristic_msg");
            }

            if (checkLevenshteinClustering(playerName)) {
                triggerAttackMode();
                plugin.alertAdmins("§4[Name-Cluster] §cBotnet storm detected via name clustering (" + playerName + ").");
                plugin.getStatsManager().recordIncident(playerName, "BOT_CLUSTER", "Levenshtein Name Storm");
                return cfg.getLanguageManager().get("bot_name_heuristic_msg");
            }
        }

        // 3. Datacenter / ASN Shield
        if (!isLocal && plugin.getConfig().getBoolean("anti-bot.datacenter-shield.enabled", false)) {
            if (isDatacenterIp(address)) {
                plugin.alertAdmins("§4[Datacenter-Shield] §c" + playerName + " (" + ip + ") blocked from cloud hosting ASN.");
                plugin.getStatsManager().recordIncident(playerName, "BOT_DATACENTER", "Datacenter IP: " + ip);
                return cfg.getLanguageManager().get("datacenter_blocked_msg");
            }
        }

        // 4. Rate Limits & Attack Mode Detection
        long now = System.currentTimeMillis();
        synchronized (this) {
            if (now - lastGlobalReset > 1000L) {
                globalJoinsThisSecond = 1;
                lastGlobalReset = now;
                underAttack = false;
            } else {
                globalJoinsThisSecond++;
                if (globalJoinsThisSecond > cfg.getMaxGlobalJoinsPerSecond()) {
                    triggerAttackMode();
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

    public boolean isSuspiciousBotName(String name) {
        if (name == null || name.length() < 4) return false;

        if (HEX_PATTERN.matcher(name).matches()) {
            return true;
        }

        if (EXCESSIVE_NUMBERS.matcher(name).matches()) {
            return true;
        }

        double maxConsonantRatio = plugin.getConfig().getDouble("anti-bot.name-heuristics.max-consonant-ratio", 0.88);
        int letters = 0;
        int consonants = 0;
        String lower = name.toLowerCase();

        for (int i = 0; i < lower.length(); i++) {
            char c = lower.charAt(i);
            if (c >= 'a' && c <= 'z') {
                letters++;
                if (c != 'a' && c != 'e' && c != 'i' && c != 'o' && c != 'u') {
                    consonants++;
                }
            }
        }

        if (letters >= 6) {
            double ratio = (double) consonants / letters;
            if (ratio >= maxConsonantRatio) {
                return true;
            }
        }

        return false;
    }

    private boolean checkLevenshteinClustering(String currentName) {
        long now = System.currentTimeMillis();
        recentJoins.removeIf(e -> (now - e.time) > 8000L);

        int similarCount = 0;
        for (RecentJoinEntry entry : recentJoins) {
            int distance = calculateLevenshtein(currentName.toLowerCase(), entry.name.toLowerCase());
            if (distance <= 2) {
                similarCount++;
            }
        }

        recentJoins.add(new RecentJoinEntry(currentName, now));
        return similarCount >= 3;
    }

    private int calculateLevenshtein(String s1, String s2) {
        int[][] dp = new int[s1.length() + 1][s2.length() + 1];
        for (int i = 0; i <= s1.length(); i++) dp[i][0] = i;
        for (int j = 0; j <= s2.length(); j++) dp[0][j] = j;

        for (int i = 1; i <= s1.length(); i++) {
            for (int j = 1; j <= s2.length(); j++) {
                int cost = (s1.charAt(i - 1) == s2.charAt(j - 1)) ? 0 : 1;
                dp[i][j] = Math.min(Math.min(dp[i - 1][j] + 1, dp[i][j - 1] + 1), dp[i - 1][j - 1] + cost);
            }
        }
        return dp[s1.length()][s2.length()];
    }

    private boolean isDatacenterIp(InetAddress address) {
        String ip = address.getHostAddress();
        Boolean cached = datacenterCache.get(ip);
        if (cached != null) return cached;

        try {
            String host = address.getCanonicalHostName().toLowerCase();
            List<String> blockedHosts = plugin.getConfig().getStringList("anti-bot.datacenter-shield.blocked-hosts");
            if (blockedHosts == null || blockedHosts.isEmpty()) {
                blockedHosts = Arrays.asList("ovh", "hetzner", "digitalocean", "linode", "vultr", "scaleway", "choopa", "leaseweb", "contabo", "aws", "amazon");
            }

            for (String kw : blockedHosts) {
                if (host.contains(kw.toLowerCase())) {
                    datacenterCache.put(ip, true);
                    return true;
                }
            }
        } catch (Throwable ignored) {}

        datacenterCache.put(ip, false);
        return false;
    }

    public void triggerAttackMode() {
        underAttack = true;
        if (plugin.getThreatIntelligence() != null) {
            plugin.getThreatIntelligence().triggerAttackMode();
        }
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
        return underAttack || (plugin.getThreatIntelligence() != null && plugin.getThreatIntelligence().isUnderAttack());
    }

    public void cleanupOldEntries() {
        long now = System.currentTimeMillis();
        ipTrackers.entrySet().removeIf(e -> (now - e.getValue().lastJoinTime) > 60000L);
        pingTimestamps.entrySet().removeIf(e -> (now - e.getValue()) > 120000L);
        recentJoins.removeIf(e -> (now - e.time) > 10000L);
        if (datacenterCache.size() > 2000) {
            datacenterCache.clear();
        }
    }

    public void clear() {
        ipTrackers.clear();
        pingTimestamps.clear();
        datacenterCache.clear();
        recentJoins.clear();
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

    private static class RecentJoinEntry {
        public final String name;
        public final long time;

        public RecentJoinEntry(String name, long time) {
            this.name = name;
            this.time = time;
        }
    }
}
