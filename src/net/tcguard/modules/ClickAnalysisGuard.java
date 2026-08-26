package net.tcguard.modules;

import net.tcguard.TCGuard;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class ClickAnalysisGuard {

    private final TCGuard plugin;
    private final Map<UUID, ClickProfile> profiles = new ConcurrentHashMap<>();
    private static final int MAX_PROFILES = 3000;

    public ClickAnalysisGuard(TCGuard plugin) {
        this.plugin = plugin;
    }

    public boolean analyzeClick(Player player, String clickType) {
        if (player == null) return false;
        if (!plugin.getConfig().getBoolean("packet-protection.auto-clicker.enabled", true)) return false;

        UUID uuid = player.getUniqueId();
        if (profiles.size() > MAX_PROFILES) {
            cleanup();
        }

        ClickProfile profile = profiles.computeIfAbsent(uuid, k -> new ClickProfile());
        long now = System.currentTimeMillis();

        ClickResult result = profile.recordAndAnalyze(now);

        int maxCps = plugin.getConfig().getInt("packet-protection.auto-clicker.max-cps", 22);
        double minDeviation = plugin.getConfig().getDouble("packet-protection.auto-clicker.min-standard-deviation", 2.2);

        if (result.cps > maxCps) {
            plugin.alertAdmins(player.getName() + " excessive CPS (" + result.cps + " CPS - " + clickType + " blocked)");
            plugin.getStatsManager().recordIncident(player.getName(), "AUTO_CLICKER", "Excessive CPS: " + result.cps);
            return true;
        }

        if (result.cps >= 12 && result.samplesCount >= 15 && result.standardDeviation < minDeviation) {
            long lastAlert = profile.lastMacroAlert;
            if (now - lastAlert > 4000L) {
                profile.lastMacroAlert = now;
                plugin.alertAdmins(player.getName() + " robotic macro/auto-clicker detected (" + result.cps + " CPS, Dev: " + String.format(Locale.US, "%.2f", result.standardDeviation) + "ms)");
                plugin.getStatsManager().recordIncident(player.getName(), "AUTO_CLICKER", "Robotic Macro (Dev: " + String.format(Locale.US, "%.2f", result.standardDeviation) + "ms)");
                plugin.getDiscordWebhook().sendAlert("Auto-Clicker Detected", player.getName(),
                        "Robotic Click Deviation", "Speed: " + result.cps + " CPS | Dev: " + String.format(Locale.US, "%.2f", result.standardDeviation) + "ms", 0xFFAA00);
            }
            return true;
        }

        return false;
    }

    public void removePlayer(UUID uuid) {
        profiles.remove(uuid);
    }

    public void cleanup() {
        long now = System.currentTimeMillis();
        profiles.entrySet().removeIf(e -> (now - e.getValue().lastClickTime) > 60000L);
    }

    private static class ClickResult {
        final int cps;
        final double standardDeviation;
        final int samplesCount;

        ClickResult(int cps, double standardDeviation, int samplesCount) {
            this.cps = cps;
            this.standardDeviation = standardDeviation;
            this.samplesCount = samplesCount;
        }
    }

    private static class ClickProfile {
        private final ArrayDeque<Long> clickTimestamps = new ArrayDeque<>(35);
        private final ArrayDeque<Long> intervals = new ArrayDeque<>(20);
        private long lastClickTime = System.currentTimeMillis();
        private long lastMacroAlert = 0;

        public synchronized ClickResult recordAndAnalyze(long now) {
            long delta = now - lastClickTime;
            lastClickTime = now;

            clickTimestamps.addLast(now);

            while (!clickTimestamps.isEmpty() && (now - clickTimestamps.peekFirst()) > 1000L) {
                clickTimestamps.pollFirst();
            }
            int cps = clickTimestamps.size();

            if (delta > 0 && delta < 500L) {
                intervals.addLast(delta);
                if (intervals.size() > 20) {
                    intervals.pollFirst();
                }
            }

            double standardDeviation = 99.0;
            int count = intervals.size();
            if (count >= 10) {
                double sum = 0;
                for (Long iv : intervals) {
                    sum += iv;
                }
                double mean = sum / count;

                double sumSq = 0;
                for (Long iv : intervals) {
                    double diff = iv - mean;
                    sumSq += diff * diff;
                }
                standardDeviation = Math.sqrt(sumSq / count);
            }

            return new ClickResult(cps, standardDeviation, count);
        }
    }
}
