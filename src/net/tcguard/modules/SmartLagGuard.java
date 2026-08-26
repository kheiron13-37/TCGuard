package net.tcguard.modules;

import net.tcguard.TCGuard;
import net.tcguard.api.Compatibility;
import org.bukkit.Chunk;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.ItemSpawnEvent;

public class SmartLagGuard implements Listener {

    private final TCGuard plugin;
    private boolean emergencyLagMode = false;
    private long lastAlertTime = 0;

    public SmartLagGuard(TCGuard plugin) {
        this.plugin = plugin;
    }

    public void updateTpsStatus() {
        if (!plugin.getConfig().getBoolean("smart-lag.enabled", true)) {
            emergencyLagMode = false;
            return;
        }

        double[] tps = Compatibility.getTPS();
        double currentTps = tps.length > 0 ? tps[0] : 20.0;

        if (currentTps < plugin.getConfig().getDouble("smart-lag.emergency-tps-threshold", 16.0)) {
            if (!emergencyLagMode) {
                emergencyLagMode = true;
                long now = System.currentTimeMillis();
                if (now - lastAlertTime > 60000L) {
                    plugin.alertAdmins("§c🚨 [SmartLag] Low TPS detected (" + String.format("%.2f", currentTps) + "). Emergency Lag Mode activated.");
                    lastAlertTime = now;
                }
            }
        } else if (currentTps >= plugin.getConfig().getDouble("smart-lag.restore-tps-threshold", 19.0)) {
            if (emergencyLagMode) {
                emergencyLagMode = false;
                plugin.alertAdmins("§a🟢 [SmartLag] Server TPS restored (" + String.format("%.2f", currentTps) + "). Restrictions lifted.");
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onExplosion(EntityExplodeEvent event) {
        if (!emergencyLagMode) return;
        if (!plugin.getConfig().getBoolean("smart-lag.cancel-explosions-under-lag", true)) return;

        event.blockList().clear();
        plugin.getStatsManager().recordIncident("Server", "CRASH", "SmartLag: Explosion Damage Cancelled");
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onItemSpawn(ItemSpawnEvent event) {
        if (!emergencyLagMode) return;
        if (!plugin.getConfig().getBoolean("smart-lag.limit-item-drops-under-lag", true)) return;

        Chunk chunk = event.getLocation().getChunk();
        if (chunk != null && chunk.getEntities().length > 35) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onCreatureSpawn(CreatureSpawnEvent event) {
        if (!emergencyLagMode) return;
        if (event.getSpawnReason() == CreatureSpawnEvent.SpawnReason.CUSTOM) return;

        Chunk chunk = event.getLocation().getChunk();
        if (chunk != null && chunk.getEntities().length > 50) {
            event.setCancelled(true);
        }
    }

    public boolean isEmergencyLagMode() {
        return emergencyLagMode;
    }
}
