package net.tcguard;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockDispenseEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.world.StructureGrowEvent;
import org.bukkit.material.Directional;
import org.bukkit.material.MaterialData;

final class WorldPatches implements Listener {
    private final TCGuard plugin;

    WorldPatches(TCGuard plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onDispense(BlockDispenseEvent event) {
        Material item = event.getItem().getType();
        if (item != Material.WATER_BUCKET && item != Material.LAVA_BUCKET) return;
        Block block = event.getBlock();
        MaterialData data = block.getState().getData();
        if (data instanceof Directional && block.getRelative(((Directional) data).getFacing()).getType() == Material.ENDER_PORTAL) {
            refuse(event);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onBucket(PlayerBucketEmptyEvent event) {
        if (event.getBlockClicked().getRelative(event.getBlockFace()).getType() == Material.ENDER_PORTAL) refuse(event);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onGrow(StructureGrowEvent event) {
        for (BlockState state : event.getBlocks()) {
            if (state.getBlock().getType() == Material.ENDER_PORTAL) {
                refuse(event);
                return;
            }
        }
    }

    private void refuse(org.bukkit.event.Cancellable event) {
        event.setCancelled(true);
        plugin.count();
    }
}
