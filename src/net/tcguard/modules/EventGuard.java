package net.tcguard.modules;

import net.tcguard.TCGuard;
import net.tcguard.config.ConfigManager;
import net.tcguard.config.LanguageManager;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.*;
import org.bukkit.event.server.ServerListPingEvent;
import org.bukkit.inventory.ItemStack;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class EventGuard implements Listener {

    private final TCGuard plugin;
    private final Map<String, Long> pingFlooderMap = new ConcurrentHashMap<>();
    private final Map<UUID, Long> cmdCooldownMap = new ConcurrentHashMap<>();

    public EventGuard(TCGuard plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onAsyncPlayerPreLogin(AsyncPlayerPreLoginEvent event) {
        if (event.getAddress() != null && plugin.getThreatIntelligence() != null) {
            plugin.getThreatIntelligence().recordHandshake(new InetSocketAddress(event.getAddress(), 0));
        }

        String kickReason = plugin.getAntiBotManager().checkConnection(event.getName(), event.getAddress());
        if (kickReason != null) {
            event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER, kickReason);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerLogin(PlayerLoginEvent event) {
        Player player = event.getPlayer();
        if (BypassManager.isBypassed(player)) return;

        String kickReason = plugin.getAntiBotManager().checkConnection(player.getName(), event.getAddress());
        if (kickReason != null) {
            event.disallow(PlayerLoginEvent.Result.KICK_OTHER, kickReason);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        if (BypassManager.isBypassed(player)) return;

        ConfigManager cfg = plugin.getConfigManager();
        Action action = event.getAction();

        if (action == Action.RIGHT_CLICK_BLOCK && event.getClickedBlock() != null) {
            if (cfg.isReachProtectionEnabled()) {
                Material type = event.getClickedBlock().getType();
                String name = type.name();
                if (name.contains("CHEST") || name.contains("SHULKER") || name.contains("HOPPER")
                        || name.contains("DISPENSER") || name.contains("DROPPER") || name.contains("FURNACE")
                        || name.contains("ANVIL") || name.contains("BEACON") || name.contains("BARREL")) {
                    Location playerLoc = player.getLocation();
                    Location blockLoc = event.getClickedBlock().getLocation().add(0.5, 0.5, 0.5);
                    if (playerLoc.distanceSquared(blockLoc) > cfg.getMaxInteractionDistanceSquared()) {
                        event.setCancelled(true);
                        plugin.alertAdmins(player.getName() + " inventory reach attempt blocked.");
                        plugin.getStatsManager().recordIncident(player.getName(), "CRASH", "Inventory Reach (" + name + ")");
                    }
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onServerListPing(ServerListPingEvent event) {
        InetAddress address = event.getAddress();
        if (address == null) return;
        String ip = address.getHostAddress();
        long now = System.currentTimeMillis();

        if (plugin.getThreatIntelligence() != null) {
            plugin.getThreatIntelligence().recordHandshake(new InetSocketAddress(address, 0));
        }
        if (plugin.getAntiBotManager() != null) {
            plugin.getAntiBotManager().recordPing(address);
        }

        Long lastPing = pingFlooderMap.get(ip);
        if (lastPing != null && (now - lastPing) < 80) {
            event.setMotd("§cServer Security Shield");
            event.setMaxPlayers(0);
            return;
        }

        if (plugin.getThreatIntelligence().isUnderAttack()) {
            event.setMotd("§c[TCG] Attack Shield Active");
        }

        pingFlooderMap.put(ip, now);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerCommandPreprocess(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();
        String raw = event.getMessage().trim();
        if (!raw.startsWith("/")) return;

        String clean = raw.replaceAll("[\\s\\u00A0\\u2000-\\u200B\\u202F\\uFEFF]+", " ").replaceFirst("^/+", "").trim();
        if (clean.isEmpty()) {
            event.setCancelled(true);
            return;
        }

        String[] parts = clean.split(" ");
        String cmdBase = parts[0].toLowerCase();
        ConfigManager cfg = plugin.getConfigManager();
        LanguageManager lang = cfg.getLanguageManager();

        if (plugin.getNbtValidator().containsLog4jExploit(raw)) {
            event.setCancelled(true);
            plugin.kickPlayer(player, cfg.getKickMessageExploit());
            plugin.alertAdmins("§4[LOG4J] §c" + player.getName() + " command JNDI attack blocked: /" + cmdBase);
            plugin.getStatsManager().recordIncident(player.getName(), "LOG4J", "Command JNDI: /" + cmdBase);
            plugin.getDiscordWebhook().sendAlert("Log4J Attack Blocked", player.getName(),
                    "JNDI Injection (CVE-2021-44228)", "Command: /" + cmdBase, 0xFF0000);
            return;
        }

        if (isAuthMeInstalled() && !isPlayerAuthenticated(player)) {
            if (!cmdBase.equals("login") && !cmdBase.equals("l") && !cmdBase.equals("giris") && !cmdBase.equals("giriş")
                    && !cmdBase.equals("register") && !cmdBase.equals("reg") && !cmdBase.equals("kayit") && !cmdBase.equals("kayıt")) {
                event.setCancelled(true);
                player.sendMessage(lang.get("auth_required"));
                return;
            }
        }

        if (BypassManager.isBypassed(player) || (plugin.getAdminManager().isPlayerAdmin(player) && raw.startsWith("//"))) {
            return;
        }

        if (!plugin.getAdminManager().isPlayerAdmin(player)) {
            Long lastCmd = cmdCooldownMap.get(player.getUniqueId());
            long now = System.currentTimeMillis();
            if (lastCmd != null && (now - lastCmd) < cfg.getCommandCooldownMs()) {
                event.setCancelled(true);
                player.sendMessage(lang.get("command_cooldown"));
                return;
            }
            cmdCooldownMap.put(player.getUniqueId(), now);
        }

        if (cfg.isBlockColonSyntax() && clean.contains(":")) {
            event.setCancelled(true);
            player.sendMessage(cfg.getUnknownCommandMsg());
            plugin.getStatsManager().recordIncident(player.getName(), "COMMAND", "Colon Syntax: " + clean);
            return;
        }

        if (cfg.isBlockProbeCommands() && cfg.getForbiddenCommands().contains(cmdBase)) {
            event.setCancelled(true);
            player.sendMessage(cfg.getUnknownCommandMsg());
            plugin.alertAdmins(player.getName() + " tried probe command: /" + cmdBase);
            plugin.getStatsManager().recordIncident(player.getName(), "COMMAND", "Forbidden Command: /" + cmdBase);
            return;
        }

        if (cfg.isUseCommandWhitelist() && !plugin.getAdminManager().isPlayerAdmin(player)) {
            if (!cfg.getAllowedCommands().contains(cmdBase)) {
                event.setCancelled(true);
                player.sendMessage(cfg.getUnknownCommandMsg());
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        if (BypassManager.isBypassed(player)) return;

        ConfigManager cfg = plugin.getConfigManager();
        String message = event.getMessage();

        if (message.length() > cfg.getMaxChatLength()) {
            event.setCancelled(true);
            player.sendMessage(cfg.getPrefix() + " " + cfg.getKickMessageSpam());
            return;
        }

        if (plugin.getNbtValidator().containsLog4jExploit(message)) {
            event.setCancelled(true);
            plugin.kickPlayer(player, plugin.getConfigManager().getKickMessageExploit());
            plugin.alertAdmins("§4[LOG4J] §c" + player.getName() + " chat JNDI attack blocked");
            plugin.getStatsManager().recordIncident(player.getName(), "LOG4J", "Chat JNDI Injection");
            plugin.getDiscordWebhook().sendAlert("Log4J Attack Blocked", player.getName(),
                    "JNDI Injection (CVE-2021-44228)", "From chat channel", 0xFF0000);
            return;
        }

        if (cfg.isBlockChatTranslationExploit() && plugin.getNbtValidator().containsTranslationCrash(message)) {
            event.setCancelled(true);
            plugin.alertAdmins(player.getName() + " sent chat translation crash string");
            return;
        }

        if (plugin.getRateLimiter().checkChatRate(player.getUniqueId(), cfg.getMaxChatPerSecond())) {
            event.setCancelled(true);
            player.sendMessage(cfg.getPrefix() + " " + cfg.getKickMessageSpam());
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onBookEdit(PlayerEditBookEvent event) {
        Player player = event.getPlayer();
        if (BypassManager.isBypassed(player)) return;

        if (plugin.getNbtValidator().isIllegalBook(event.getNewBookMeta())) {
            event.setCancelled(true);
            plugin.kickPlayer(player, plugin.getConfigManager().getKickMessageExploit());
            plugin.alertAdmins(player.getName() + " Book exploit attempt blocked");
            plugin.getStatsManager().recordIncident(player.getName(), "NBT_EXPLOIT", "Illegal Book Edit");
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onSignChange(SignChangeEvent event) {
        Player player = event.getPlayer();
        if (BypassManager.isBypassed(player)) return;

        ConfigManager cfg = plugin.getConfigManager();
        for (int i = 0; i < 4; i++) {
            String line = event.getLine(i);
            if (line != null) {
                if (line.length() > cfg.getMaxSignLineLength()) {
                    event.setLine(i, line.substring(0, cfg.getMaxSignLineLength()));
                    plugin.alertAdmins(player.getName() + " sign line truncated");
                }
                if (cfg.isBlockTranslationCrash() && plugin.getNbtValidator().containsTranslationCrash(line)) {
                    event.setLine(i, "");
                    plugin.alertAdmins(player.getName() + " sign translation crash string cleared");
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        Player player = (Player) event.getWhoClicked();
        if (BypassManager.isBypassed(player)) return;

        if (player.getGameMode() != GameMode.CREATIVE) {
            ItemStack item = event.getCurrentItem();
            if (item != null && plugin.getNbtValidator().isIllegalItem(item)) {
                event.setCancelled(true);
                item.setType(Material.AIR);
                plugin.alertAdmins(player.getName() + " illegal item cleared from inventory");
                plugin.getStatsManager().recordIncident(player.getName(), "NBT_EXPLOIT", "Illegal Inventory Item: " + item.getType().name());
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onItemDrop(PlayerDropItemEvent event) {
        Player player = event.getPlayer();
        if (BypassManager.isBypassed(player)) return;

        ItemStack item = event.getItemDrop().getItemStack();
        if (plugin.getNbtValidator().isIllegalItem(item)) {
            event.getItemDrop().remove();
            event.setCancelled(true);
            plugin.alertAdmins(player.getName() + " illegal item drop cancelled");
            plugin.getStatsManager().recordIncident(player.getName(), "NBT_EXPLOIT", "Illegal Item Drop: " + item.getType().name());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        plugin.getRateLimiter().cleanupPlayer(uuid);
        cmdCooldownMap.remove(uuid);
        if (plugin.getClickAnalysisGuard() != null) {
            plugin.getClickAnalysisGuard().removePlayer(uuid);
        }
        if (plugin.getFastInteractGuard() != null) {
            plugin.getFastInteractGuard().removePlayer(uuid);
        }
    }

    private boolean isAuthMeInstalled() {
        return Bukkit.getPluginManager().getPlugin("AuthMe") != null;
    }

    private boolean isPlayerAuthenticated(Player player) {
        try {
            Class<?> apiClass = Class.forName("fr.xephi.authme.api.v3.AuthMeApi");
            Object apiInstance = apiClass.getMethod("getInstance").invoke(null);
            return (boolean) apiClass.getMethod("isAuthenticated", Player.class).invoke(apiInstance, player);
        } catch (Exception ignored) {
            return true;
        }
    }

    public void cleanupOldData() {
        long now = System.currentTimeMillis();
        pingFlooderMap.entrySet().removeIf(e -> (now - e.getValue()) > 30000L);
        cmdCooldownMap.entrySet().removeIf(e -> Bukkit.getPlayer(e.getKey()) == null);
    }
}
