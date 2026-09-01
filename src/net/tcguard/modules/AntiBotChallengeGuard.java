package net.tcguard.modules;

import net.tcguard.TCGuard;
import net.tcguard.api.Compatibility;
import net.tcguard.config.LanguageManager;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.*;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class AntiBotChallengeGuard implements Listener {

    private final TCGuard plugin;
    private final Set<UUID> verifiedPlayers = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private final Map<UUID, ChallengeSession> activeChallenges = new ConcurrentHashMap<>();

    private static final String GUI_TITLE = "§8[§cSecurity§8] §0Verification";

    public AntiBotChallengeGuard(TCGuard plugin) {
        this.plugin = plugin;
    }

    public boolean isVerified(Player player) {
        if (player == null) return true;
        if (BypassManager.isBypassed(player)) return true;
        return verifiedPlayers.contains(player.getUniqueId());
    }

    public boolean isPendingChallenge(Player player) {
        if (player == null) return false;
        return activeChallenges.containsKey(player.getUniqueId());
    }

    public void startChallenge(Player player) {
        if (player == null || !player.isOnline()) return;
        if (isVerified(player)) return;

        LanguageManager lang = plugin.getConfigManager().getLanguageManager();
        int timeoutSec = plugin.getConfig().getInt("anti-bot.challenge-mode.timeout-seconds", 15);
        int correctSlot = new Random().nextInt(27);

        BukkitTask timeoutTask = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            ChallengeSession session = activeChallenges.remove(player.getUniqueId());
            if (session != null && player.isOnline()) {
                plugin.getStatsManager().recordIncident(player.getName(), "BOT_CHALLENGE", "Challenge Timeout (" + timeoutSec + "s)");
                plugin.alertAdmins("§4[Anti-Bot] §c" + player.getName() + " failed interactive challenge (Timeout).");
                plugin.kickPlayer(player, lang.get("challenge_failed"));
            }
        }, timeoutSec * 20L);

        ChallengeSession session = new ChallengeSession(player.getUniqueId(), correctSlot, timeoutTask);
        activeChallenges.put(player.getUniqueId(), session);

        player.sendMessage(lang.get("challenge_prompt_gui"));
        openChallengeGui(player, correctSlot);
    }

    public void openChallengeGui(Player player, int correctSlot) {
        Inventory inv = Bukkit.createInventory(null, 27, GUI_TITLE);

        ItemStack filler = Compatibility.createItem("STAINED_GLASS_PANE", "GRAY_STAINED_GLASS_PANE", (short) 7, "§7...");
        for (int i = 0; i < 27; i++) {
            if (i != correctSlot) {
                inv.setItem(i, filler);
            }
        }

        ItemStack emerald = Compatibility.createItem("EMERALD", "EMERALD", (short) 0, "§a§l✔ CLICK HERE TO VERIFY");
        ItemMeta meta = emerald.getItemMeta();
        if (meta != null) {
            meta.setLore(Arrays.asList("§7Click this item to prove you are", "§7a real human player."));
            emerald.setItemMeta(meta);
        }
        inv.setItem(correctSlot, emerald);

        player.openInventory(inv);
        Compatibility.playSound(player, 1.0f, 1.0f, "NOTE_PLING", "BLOCK_NOTE_BLOCK_PLING");
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (BypassManager.isBypassed(player)) return;

        boolean challengeEnabled = plugin.getConfig().getBoolean("anti-bot.challenge-mode.enabled", true);
        if (!challengeEnabled) return;

        boolean requireOnlyUnderAttack = plugin.getConfig().getBoolean("anti-bot.challenge-mode.only-under-attack", true);
        boolean underAttack = plugin.getThreatIntelligence().isUnderAttack() || plugin.getAntiBotManager().isUnderAttack();

        if (requireOnlyUnderAttack && !underAttack) {
            return;
        }

        if (!isVerified(player)) {
            // Lock down player immediately at Tick 0 to prevent initial 0-250ms packet spam
            activeChallenges.put(player.getUniqueId(), new ChallengeSession(player.getUniqueId(), -1, null));
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (player.isOnline() && !isVerified(player)) {
                    startChallenge(player);
                }
            }, 3L);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        Player player = (Player) event.getWhoClicked();

        ChallengeSession session = activeChallenges.get(player.getUniqueId());
        if (session == null) return;

        event.setCancelled(true);

        int slot = event.getRawSlot();
        if (slot < 0 || slot >= 27) return;

        LanguageManager lang = plugin.getConfigManager().getLanguageManager();

        if (slot == session.correctSlot) {
            session.cancelTask();
            activeChallenges.remove(player.getUniqueId());
            verifiedPlayers.add(player.getUniqueId());

            player.closeInventory();
            player.sendMessage(lang.get("challenge_success"));
            Compatibility.playSound(player, 1.0f, 1.6f, "LEVEL_UP", "ENTITY_PLAYER_LEVELUP");
            plugin.getStatsManager().recordIncident(player.getName(), "CHALLENGE_PASS", "Verified Human");
            plugin.alertAdmins("§a✓ §f" + player.getName() + " passed interactive bot challenge.");
        } else {
            session.cancelTask();
            activeChallenges.remove(player.getUniqueId());
            plugin.getStatsManager().recordIncident(player.getName(), "BOT_CHALLENGE", "Wrong GUI item clicked");
            plugin.alertAdmins("§4[Anti-Bot] §c" + player.getName() + " failed interactive challenge (Wrong item).");
            plugin.kickPlayer(player, lang.get("challenge_failed"));
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player)) return;
        Player player = (Player) event.getPlayer();

        ChallengeSession session = activeChallenges.get(player.getUniqueId());
        if (session != null && player.isOnline()) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (activeChallenges.containsKey(player.getUniqueId()) && player.isOnline()) {
                    openChallengeGui(player, session.correctSlot);
                }
            }, 5L);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        if (activeChallenges.containsKey(player.getUniqueId())) {
            Location from = event.getFrom();
            Location to = event.getTo();
            if (to == null) return;
            if (from.getBlockX() != to.getBlockX() || from.getBlockZ() != to.getBlockZ() || from.getBlockY() != to.getBlockY()) {
                event.setTo(from);
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        if (activeChallenges.containsKey(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerCommand(PlayerCommandPreprocessEvent event) {
        if (activeChallenges.containsKey(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        if (activeChallenges.containsKey(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        if (activeChallenges.containsKey(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onPlayerDrop(PlayerDropItemEvent event) {
        if (activeChallenges.containsKey(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
            try { event.getPlayer().updateInventory(); } catch (Throwable ignored) {}
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (activeChallenges.containsKey(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
            try { event.getPlayer().updateInventory(); } catch (Throwable ignored) {}
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onPlayerItemHeld(PlayerItemHeldEvent event) {
        if (activeChallenges.containsKey(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        ChallengeSession session = activeChallenges.remove(uuid);
        if (session != null) {
            session.cancelTask();
        }
    }

    public void clearAll() {
        for (ChallengeSession session : activeChallenges.values()) {
            session.cancelTask();
        }
        activeChallenges.clear();
        verifiedPlayers.clear();
    }

    private static class ChallengeSession {
        public final UUID playerUuid;
        public final int correctSlot;
        public final BukkitTask timeoutTask;

        public ChallengeSession(UUID playerUuid, int correctSlot, BukkitTask timeoutTask) {
            this.playerUuid = playerUuid;
            this.correctSlot = correctSlot;
            this.timeoutTask = timeoutTask;
        }

        public void cancelTask() {
            if (timeoutTask != null) {
                timeoutTask.cancel();
            }
        }
    }
}
