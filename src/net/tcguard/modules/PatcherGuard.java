package net.tcguard.modules;

import net.tcguard.TCGuard;
import net.tcguard.api.Compatibility;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.entity.Entity;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockDispenseEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.event.world.StructureGrowEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.material.Dispenser;
import org.bukkit.material.MaterialData;

import java.lang.reflect.Method;
import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class PatcherGuard implements Listener {

    private final TCGuard plugin;
    private final Map<UUID, Long> portalCooldownMap = new ConcurrentHashMap<>();

    public PatcherGuard(TCGuard plugin) {
        this.plugin = plugin;
    }

    private static final BlockFace[] CROP_CHECK_FACES = {
        BlockFace.DOWN, BlockFace.UP, BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST
    };

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onPlayerInteractBed(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;

        Player player = event.getPlayer();
        if (BypassManager.isBypassed(player)) return;

        ItemStack heldItem = Compatibility.getItemInHand(player);
        if (heldItem == null) return;

        String itemType = heldItem.getType().name();
        if (!itemType.endsWith("_BED") && !itemType.equals("BED")) return;

        Block clickedBlock = event.getClickedBlock();
        if (clickedBlock == null) return;

        Block targetBlock = clickedBlock.getRelative(event.getBlockFace());
        if (targetBlock == null) return;

        for (BlockFace face : CROP_CHECK_FACES) {
            Block neighbor = targetBlock.getRelative(face);
            if (neighbor == null) continue;
            String name = neighbor.getType().name();
            if (name.equals("WHEAT") || name.equals("CROPS") || name.equals("POTATOES")
                    || name.equals("POTATO") || name.equals("CARROTS") || name.equals("CARROT")
                    || name.equals("NETHER_WARTS") || name.equals("NETHER_WARP_PLANT")
                    || name.equals("BEETROOTS") || name.equals("SUGAR_CANE_BLOCK")) {
                event.setCancelled(true);
                plugin.alertAdmins(player.getName() + " crop duplication attempt blocked.");
                plugin.getStatsManager().recordIncident(player.getName(), "DUPLICATION", "Bed Crop Duplication");
                break;
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onRedstonePlace(BlockPlaceEvent event) {
        Block block = event.getBlock();
        if (block == null) return;

        String blockType = block.getType().name();
        if (blockType.equals("REDSTONE") || blockType.equals("REDSTONE_WIRE")) {
            Block blockBelow = block.getRelative(BlockFace.DOWN);
            if (blockBelow != null && blockBelow.getType().name().contains("TRAPDOOR")) {
                event.setCancelled(true);
                plugin.alertAdmins(event.getPlayer().getName() + " redstone trapdoor crash attempt blocked.");
                plugin.getStatsManager().recordIncident(event.getPlayer().getName(), "CRASH", "Redstone Trapdoor Crash");
            }
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onBlockDispense(BlockDispenseEvent event) {
        Block block = event.getBlock();
        if (block == null) return;

        Location location = block.getLocation();
        World world = location.getWorld();
        if (world == null) return;

        int maxHeight = world.getMaxHeight() - 1;
        int minHeight = 0;
        try {
            Method minHeightMethod = world.getClass().getMethod("getMinHeight");
            minHeight = (int) minHeightMethod.invoke(world);
        } catch (Throwable ignored) {}

        try {
            MaterialData materialData = block.getState().getData();
            if (materialData instanceof Dispenser) {
                Dispenser dispenser = (Dispenser) materialData;
                BlockFace face = dispenser.getFacing();
                double y = location.getY();

                if ((y >= maxHeight && face == BlockFace.UP) || (y <= (minHeight + 1) && face == BlockFace.DOWN)) {
                    event.setCancelled(true);
                    plugin.getLogger().warning("Dispenser dispensing out of world bounds blocked at " + location.getBlockX() + "," + location.getBlockY() + "," + location.getBlockZ());
                }
            }
        } catch (Exception ignored) {}
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onStructureGrow(StructureGrowEvent event) {
        World world = event.getWorld();
        for (BlockState blockBefore : event.getBlocks()) {
            Block block = world.getBlockAt(blockBefore.getLocation());
            String type = block.getType().name();
            if (type.contains("PORTAL") || type.contains("GATEWAY")) {
                event.setCancelled(true);
                plugin.alertAdmins("Portal destruction via mushroom growth blocked.");
                return;
            }
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onBucketEmpty(PlayerBucketEmptyEvent event) {
        Block relative = event.getBlockClicked().getRelative(event.getBlockFace());
        String materialName = relative.getType().name();
        if (materialName.contains("END_PORTAL") || materialName.contains("ENDER_PORTAL") || materialName.contains("END_GATEWAY")) {
            event.setCancelled(true);
            event.getPlayer().updateInventory();
            plugin.alertAdmins(event.getPlayer().getName() + " bucket portal destruction blocked.");
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onPlayerPortalTeleport(PlayerTeleportEvent event) {
        PlayerTeleportEvent.TeleportCause cause = event.getCause();
        if (cause == PlayerTeleportEvent.TeleportCause.END_PORTAL
                || cause == PlayerTeleportEvent.TeleportCause.NETHER_PORTAL
                || cause.name().equals("END_GATEWAY")) {
            Player player = event.getPlayer();
            UUID uuid = player.getUniqueId();
            long now = System.currentTimeMillis();
            Long lastUse = portalCooldownMap.get(uuid);

            if (lastUse != null && (now - lastUse) < 1000L) {
                event.setCancelled(true);
                return;
            }
            portalCooldownMap.put(uuid, now);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onProjectileLaunch(ProjectileLaunchEvent event) {
        org.bukkit.entity.Projectile projectile = event.getEntity();
        if (projectile == null) return;

        if (projectile.getVelocity().lengthSquared() > 15.0) {
            event.setCancelled(true);
            if (projectile.getShooter() instanceof Player) {
                Player shooter = (Player) projectile.getShooter();
                plugin.alertAdmins(shooter.getName() + " fast projectile (Bow Bomb / Crash exploit) blocked.");
                plugin.getStatsManager().recordIncident(shooter.getName(), "CRASH", "Bow Bomb Projectile");
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onChunkUnload(ChunkUnloadEvent event) {
        Chunk chunk = event.getChunk();
        if (chunk == null) return;

        for (Entity entity : chunk.getEntities()) {
            if (entity instanceof InventoryHolder) {
                InventoryHolder holder = (InventoryHolder) entity;
                Inventory inventory = holder.getInventory();
                if (inventory != null) {
                    for (HumanEntity viewer : inventory.getViewers()) {
                        viewer.closeInventory();
                    }
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onSelfDamage(EntityDamageByEntityEvent event) {
        Entity entity = event.getEntity();
        Entity damager = event.getDamager();

        if (entity instanceof Player && damager instanceof Player && entity.equals(damager)) {
            event.setCancelled(true);
        }
    }
}
