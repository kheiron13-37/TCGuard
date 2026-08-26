package net.tcguard.modules;

import net.tcguard.TCGuard;
import net.tcguard.api.Compatibility;
import net.tcguard.config.LanguageManager;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.*;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class GuardCodeManager implements Listener {

    private final TCGuard plugin;
    private final File codeFile;
    private final Map<String, String> hashedCodes = new ConcurrentHashMap<>();
    private final Set<UUID> verifiedSessions = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private final Map<UUID, Integer> failedAttempts = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastPromptTime = new ConcurrentHashMap<>();

    private static final Set<String> ALLOWED_COMMANDS = new HashSet<>(Arrays.asList(
            "login", "l", "register", "reg", "kayit", "kayıt", "authme",
            "guardcode", "gcode", "tcguardcode", "2fa"
    ));

    public GuardCodeManager(TCGuard plugin) {
        this.plugin = plugin;
        this.codeFile = new File(plugin.getDataFolder(), "admin_codes.yml");
    }

    public void load() {
        hashedCodes.clear();
        if (!codeFile.exists()) {
            save();
            return;
        }

        try {
            FileConfiguration cfg = YamlConfiguration.loadConfiguration(codeFile);
            if (cfg.isConfigurationSection("codes")) {
                for (String key : cfg.getConfigurationSection("codes").getKeys(false)) {
                    String hash = cfg.getString("codes." + key);
                    if (hash != null) {
                        hashedCodes.put(key.toLowerCase(), hash);
                    }
                }
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Error loading admin_codes.yml: " + e.getMessage());
        }
    }

    public void save() {
        try {
            if (!plugin.getDataFolder().exists()) {
                plugin.getDataFolder().mkdirs();
            }
            FileConfiguration cfg = new YamlConfiguration();
            for (Map.Entry<String, String> entry : hashedCodes.entrySet()) {
                cfg.set("codes." + entry.getKey(), entry.getValue());
            }
            cfg.save(codeFile);
        } catch (Exception e) {
            plugin.getLogger().warning("Error saving admin_codes.yml: " + e.getMessage());
        }
    }

    public boolean isGuardCodeRequired(Player player) {
        if (player == null) return false;
        if (!plugin.getConfig().getBoolean("admin-guardcode.enabled", true)) return false;

        boolean isOp = player.isOp();
        boolean isAdmin = plugin.getAdminManager().isPlayerAdmin(player);

        if (!isOp && !isAdmin) return false;
        return !verifiedSessions.contains(player.getUniqueId());
    }

    public boolean hasCodeSet(String username) {
        if (username == null) return false;
        return hashedCodes.containsKey(username.toLowerCase());
    }

    public boolean setCode(String username, String rawCode) {
        if (username == null || rawCode == null || rawCode.length() < 4) return false;
        String hash = hashPassword(username.toLowerCase() + ":" + rawCode);
        hashedCodes.put(username.toLowerCase(), hash);
        save();
        return true;
    }

    public boolean resetCode(String username) {
        if (username == null) return false;
        boolean removed = hashedCodes.remove(username.toLowerCase()) != null;
        if (removed) {
            save();
        }
        Player target = Bukkit.getPlayerExact(username);
        if (target != null) {
            verifiedSessions.remove(target.getUniqueId());
            failedAttempts.remove(target.getUniqueId());
            lastPromptTime.remove(target.getUniqueId());
            if (isGuardCodeRequired(target)) {
                promptPlayer(target);
            }
        }
        return removed;
    }

    public boolean verifyCode(Player player, String rawCode) {
        if (player == null || rawCode == null) return false;
        String username = player.getName().toLowerCase();
        String storedHash = hashedCodes.get(username);
        if (storedHash == null) return false;

        String inputHash = hashPassword(username + ":" + rawCode);
        if (storedHash.equals(inputHash)) {
            verifiedSessions.add(player.getUniqueId());
            failedAttempts.remove(player.getUniqueId());
            return true;
        }

        int attempts = failedAttempts.getOrDefault(player.getUniqueId(), 0) + 1;
        failedAttempts.put(player.getUniqueId(), attempts);

        int maxAttempts = plugin.getConfig().getInt("admin-guardcode.max-failed-attempts", 5);
        if (attempts >= maxAttempts) {
            plugin.alertAdmins("§4[GuardCode] §c" + player.getName() + " failed GuardCode verification " + attempts + " times! Kicking...");
            plugin.getStatsManager().recordIncident(player.getName(), "GUARDCODE_FAIL", "Failed code attempts: " + attempts);
            plugin.kickPlayer(player, plugin.getConfigManager().getLanguageManager().get("guardcode_kick_failed"));
        }
        return false;
    }

    private String hashPassword(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (Exception e) {
            return String.valueOf(input.hashCode());
        }
    }

    public void promptPlayer(Player player) {
        if (player == null) return;
        long now = System.currentTimeMillis();
        Long last = lastPromptTime.get(player.getUniqueId());
        if (last != null && (now - last) < 2500L) {
            return;
        }
        lastPromptTime.put(player.getUniqueId(), now);

        LanguageManager lang = plugin.getConfigManager().getLanguageManager();
        String username = player.getName().toLowerCase();

        if (!hasCodeSet(username)) {
            player.sendMessage(lang.get("guardcode_prompt_set"));
        } else {
            player.sendMessage(lang.get("guardcode_prompt_enter"));
        }
        Compatibility.playSound(player, 0.8f, 0.8f, "NOTE_BASS", "BLOCK_NOTE_BLOCK_BASS");
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (isGuardCodeRequired(player)) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> promptPlayer(player), 10L);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        verifiedSessions.remove(uuid);
        failedAttempts.remove(uuid);
        lastPromptTime.remove(uuid);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        if (isGuardCodeRequired(player)) {
            Location from = event.getFrom();
            Location to = event.getTo();
            if (to == null) return;
            if (from.getBlockX() != to.getBlockX() || from.getBlockZ() != to.getBlockZ() || from.getBlockY() != to.getBlockY()) {
                event.setTo(from);
                promptPlayer(player);
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onCommandPreprocess(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();
        if (!isGuardCodeRequired(player)) return;

        String raw = event.getMessage().trim();
        if (!raw.startsWith("/")) return;

        String clean = raw.replaceFirst("^/+", "").trim();
        String[] parts = clean.split("\\s+");
        String cmd = parts[0].toLowerCase();

        if (cmd.contains(":")) {
            cmd = cmd.substring(cmd.indexOf(':') + 1);
        }

        if (!ALLOWED_COMMANDS.contains(cmd)) {
            event.setCancelled(true);
            promptPlayer(player);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        if (isGuardCodeRequired(event.getPlayer())) {
            event.setCancelled(true);
            promptPlayer(event.getPlayer());
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        if (isGuardCodeRequired(event.getPlayer())) {
            event.setCancelled(true);
            promptPlayer(event.getPlayer());
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (isGuardCodeRequired(event.getPlayer())) {
            event.setCancelled(true);
            promptPlayer(event.getPlayer());
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getWhoClicked() instanceof Player) {
            Player p = (Player) event.getWhoClicked();
            if (isGuardCodeRequired(p)) {
                event.setCancelled(true);
                promptPlayer(p);
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onItemDrop(PlayerDropItemEvent event) {
        if (isGuardCodeRequired(event.getPlayer())) {
            event.setCancelled(true);
            promptPlayer(event.getPlayer());
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onEntityDamage(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player) {
            Player p = (Player) event.getDamager();
            if (isGuardCodeRequired(p)) {
                event.setCancelled(true);
                promptPlayer(p);
            }
        }
    }
}
