package net.tcguard.modules;

import net.tcguard.TCGuard;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffectType;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class FastInteractGuard {

    private final TCGuard plugin;

    private final Map<UUID, Long> lastBlockDigStart = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastBlockPlaceTime = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> placesThisTick = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastPlaceTickTime = new ConcurrentHashMap<>();

    private static final int MAX_MAP_ENTRIES = 3000;

    public FastInteractGuard(TCGuard plugin) {
        this.plugin = plugin;
    }

    public void recordDigStart(Player player) {
        if (player == null) return;
        lastBlockDigStart.put(player.getUniqueId(), System.currentTimeMillis());
    }

    public boolean checkFastBreak(Player player, Block block) {
        if (player == null || block == null) return false;
        if (player.getGameMode() == GameMode.CREATIVE) return false;
        if (!plugin.getConfig().getBoolean("packet-protection.fast-interact.block-fast-break", true)) return false;

        UUID uuid = player.getUniqueId();
        Long startTime = lastBlockDigStart.get(uuid);
        long now = System.currentTimeMillis();

        if (startTime != null) {
            long duration = now - startTime;
            Material mat = block.getType();

            if (isInstaBreakBlock(mat)) {
                return false;
            }

            boolean hasHaste = player.hasPotionEffect(PotionEffectType.FAST_DIGGING);
            if (!hasHaste && duration < 60L) {
                plugin.alertAdmins(player.getName() + " FastBreak detected (" + mat.name() + " broken in " + duration + "ms)");
                plugin.getStatsManager().recordIncident(player.getName(), "FAST_BREAK", "Fast Break (" + duration + "ms): " + mat.name());
                return true;
            }
        }

        return false;
    }

    public boolean checkFastPlace(Player player) {
        if (player == null) return false;
        if (!plugin.getConfig().getBoolean("packet-protection.fast-interact.block-fast-place", true)) return false;

        UUID uuid = player.getUniqueId();
        long now = System.currentTimeMillis();

        Long lastTick = lastPlaceTickTime.get(uuid);
        if (lastTick == null || (now - lastTick) > 50L) {
            lastPlaceTickTime.put(uuid, now);
            placesThisTick.put(uuid, 1);
            return false;
        }

        int count = placesThisTick.getOrDefault(uuid, 0) + 1;
        placesThisTick.put(uuid, count);

        int maxPerTick = plugin.getConfig().getInt("packet-protection.fast-interact.max-places-per-tick", 4);
        if (count > maxPerTick) {
            plugin.alertAdmins(player.getName() + " FastPlace blocked (" + count + " blocks/tick)");
            plugin.getStatsManager().recordIncident(player.getName(), "FAST_PLACE", "Fast Place: " + count + "/tick");
            return true;
        }

        return false;
    }

    private boolean isInstaBreakBlock(Material mat) {
        if (mat == null) return true;
        String name = mat.name();
        return name.contains("FLOWER") || name.contains("TORCH") || name.contains("SAPLING")
                || name.contains("GRASS") || name.contains("REDSTONE") || name.contains("REED")
                || name.contains("SUGAR_CANE") || name.contains("CROPS") || name.contains("TRIPWIRE")
                || name.contains("LEVER") || name.contains("BUTTON") || mat == Material.AIR;
    }

    public void removePlayer(UUID uuid) {
        lastBlockDigStart.remove(uuid);
        lastBlockPlaceTime.remove(uuid);
        placesThisTick.remove(uuid);
        lastPlaceTickTime.remove(uuid);
    }

    public void cleanup() {
        if (lastBlockDigStart.size() > MAX_MAP_ENTRIES) {
            lastBlockDigStart.clear();
            lastBlockPlaceTime.clear();
            placesThisTick.clear();
            lastPlaceTickTime.clear();
        }
    }
}
