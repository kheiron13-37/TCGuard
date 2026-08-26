package net.tcguard.modules;

import net.tcguard.TCGuard;
import org.bukkit.Chunk;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockRedstoneEvent;
import org.bukkit.event.entity.EntitySpawnEvent;
import org.bukkit.event.entity.ItemSpawnEvent;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class LagGuard implements Listener {

    private final TCGuard plugin;

    private final Map<String, SlidingRateCounter> redstoneCounters = new ConcurrentHashMap<>();
    private final Map<String, SlidingRateCounter> pistonCounters = new ConcurrentHashMap<>();
    private final Map<String, Long> lastAlertTime = new ConcurrentHashMap<>();

    private final SlidingRateCounter globalRedstoneCounter = new SlidingRateCounter();
    private static final int MAX_MAP_ENTRIES = 2000;

    public LagGuard(TCGuard plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockRedstone(BlockRedstoneEvent event) {
        if (!plugin.getConfig().getBoolean("lag-protection.redstone-limiter.enabled", true)) return;

        if (globalRedstoneCounter.incrementAndCheck(500)) {
            event.setNewCurrent(0);
            return;
        }

        Chunk chunk = event.getBlock().getChunk();
        String chunkKey = chunk.getWorld().getName() + ":" + chunk.getX() + ":" + chunk.getZ();
        int maxTicks = plugin.getConfig().getInt("lag-protection.redstone-limiter.max-ticks-per-chunk", 200);

        if (redstoneCounters.size() > MAX_MAP_ENTRIES) {
            cleanupOldData();
        }

        SlidingRateCounter counter = redstoneCounters.computeIfAbsent(chunkKey, k -> new SlidingRateCounter());
        if (counter.incrementAndCheck(maxTicks)) {
            event.setNewCurrent(0);

            long now = System.currentTimeMillis();
            Long lastAlert = lastAlertTime.get(chunkKey);
            if (lastAlert == null || (now - lastAlert) > 10000L) {
                lastAlertTime.put(chunkKey, now);
                plugin.alertAdmins("Anti-Lag: [" + chunkKey + "] High frequency Redstone Clock stopped.");
                plugin.getStatsManager().recordIncident("RedstoneClock", "REDSTONE_LAG", "Chunk: " + chunkKey);
                plugin.getDiscordWebhook().sendAlert("Redstone Clock Blocked", "World", "Excessive Redstone Signal", "Location: " + chunkKey, 0xFFAA00);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent event) {
        handlePiston(event.getBlock().getChunk(), event);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent event) {
        handlePiston(event.getBlock().getChunk(), event);
    }

    private void handlePiston(Chunk chunk, org.bukkit.event.Cancellable event) {
        if (!plugin.getConfig().getBoolean("lag-protection.piston-limiter.enabled", true)) return;

        String chunkKey = chunk.getWorld().getName() + ":" + chunk.getX() + ":" + chunk.getZ();
        int maxPiston = plugin.getConfig().getInt("lag-protection.piston-limiter.max-piston-moves-per-chunk", 60);

        if (pistonCounters.size() > MAX_MAP_ENTRIES) {
            cleanupOldData();
        }

        SlidingRateCounter counter = pistonCounters.computeIfAbsent(chunkKey, k -> new SlidingRateCounter());
        if (counter.incrementAndCheck(maxPiston)) {
            event.setCancelled(true);
            long now = System.currentTimeMillis();
            Long lastAlert = lastAlertTime.get("piston:" + chunkKey);
            if (lastAlert == null || (now - lastAlert) > 10000L) {
                lastAlertTime.put("piston:" + chunkKey, now);
                plugin.alertAdmins("Anti-Lag: [" + chunkKey + "] Piston lag machine frozen.");
                plugin.getStatsManager().recordIncident("PistonLoop", "REDSTONE_LAG", "Chunk: " + chunkKey);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntitySpawn(EntitySpawnEvent event) {
        if (!plugin.getConfig().getBoolean("lag-protection.chunk-entity-limiter.enabled", true)) return;

        Chunk chunk = event.getLocation().getChunk();
        int maxEntities = plugin.getConfig().getInt("lag-protection.chunk-entity-limiter.max-entities-per-chunk", 100);

        if (chunk.getEntities().length > maxEntities) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onItemSpawn(ItemSpawnEvent event) {
        if (!plugin.getConfig().getBoolean("lag-protection.chunk-entity-limiter.enabled", true)) return;

        Chunk chunk = event.getLocation().getChunk();
        int maxEntities = plugin.getConfig().getInt("lag-protection.chunk-entity-limiter.max-entities-per-chunk", 100);

        if (chunk.getEntities().length > (maxEntities + 20)) {
            event.setCancelled(true);
        }
    }

    public void cleanupOldData() {
        long now = System.currentTimeMillis();
        redstoneCounters.entrySet().removeIf(e -> (now - e.getValue().lastUpdate) > 20000L);
        pistonCounters.entrySet().removeIf(e -> (now - e.getValue().lastUpdate) > 20000L);
        lastAlertTime.entrySet().removeIf(e -> (now - e.getValue()) > 45000L);
    }

    private static class SlidingRateCounter {
        private int count = 0;
        private long windowStart = System.currentTimeMillis();
        private long lastUpdate = System.currentTimeMillis();

        public synchronized boolean incrementAndCheck(int max) {
            long now = System.currentTimeMillis();
            lastUpdate = now;
            if (now - windowStart > 1000L) {
                count = 1;
                windowStart = now;
                return false;
            }
            count++;
            return count > max;
        }
    }
}
