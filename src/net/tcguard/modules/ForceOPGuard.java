package net.tcguard.modules;

import net.tcguard.TCGuard;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.server.ServerCommandEvent;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class ForceOPGuard implements Listener {

    private final TCGuard plugin;
    private final Set<UUID> opSnapshot = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private final Set<String> whitelistedOps = Collections.newSetFromMap(new ConcurrentHashMap<>());

    private static final Set<String> OP_COMMANDS = new HashSet<>(Arrays.asList(
            "/op", "/deop",
            "/minecraft:op", "/minecraft:deop",
            "/bukkit:op", "/bukkit:deop"
    ));

    public ForceOPGuard(TCGuard plugin) {
        this.plugin = plugin;
    }

    public void init() {
        loadWhitelist();
        takeSnapshot();
        Bukkit.getScheduler().runTaskTimer(plugin, this::checkOpSnapshot, 100L, 100L);
    }

    public void loadWhitelist() {
        whitelistedOps.clear();
        List<String> list = plugin.getConfig().getStringList("force-op-protection.whitelisted-ops");
        if (list != null) {
            for (String s : list) {
                if (s != null && !s.trim().isEmpty()) {
                    whitelistedOps.add(s.trim().toLowerCase());
                }
            }
        }
    }

    public void takeSnapshot() {
        opSnapshot.clear();
        try {
            for (OfflinePlayer op : Bukkit.getOperators()) {
                if (op != null && op.getUniqueId() != null) {
                    opSnapshot.add(op.getUniqueId());
                }
            }
        } catch (Exception ignored) {}
    }

    private void checkOpSnapshot() {
        if (!plugin.getConfig().getBoolean("force-op-protection.enabled", true)) return;

        try {
            Set<UUID> currentOps = new HashSet<>();
            for (OfflinePlayer op : Bukkit.getOperators()) {
                if (op != null && op.getUniqueId() != null) {
                    currentOps.add(op.getUniqueId());
                }
            }

            for (OfflinePlayer op : Bukkit.getOperators()) {
                if (op == null || op.getUniqueId() == null) continue;
                UUID uuid = op.getUniqueId();
                String name = op.getName() != null ? op.getName().toLowerCase() : uuid.toString().toLowerCase();

                boolean isWhitelisted = whitelistedOps.contains(name) || whitelistedOps.contains(uuid.toString().toLowerCase());
                boolean isAuthorizedAdmin = op.getName() != null && plugin.getAdminManager() != null && plugin.getAdminManager().getAuthorizedAdmins().contains(op.getName().toLowerCase());

                if (!opSnapshot.contains(uuid) && !isWhitelisted && !isAuthorizedAdmin) {
                    String opName = op.getName() != null ? op.getName() : uuid.toString();

                    plugin.alertAdmins("§4§l⚠ ALARM: §cUnauthorized Force-OP escalation detected! Rogue OP: §e" + opName);
                    plugin.getDiscordWebhook().sendAlert("CRITICAL: FORCE-OP DETECTED", opName,
                            "Unauthorized Operator Escalation",
                            "Player: " + opName + " (" + uuid + ")\nAction: OP revoked automatically!", 0xFF0000);

                    op.setOp(false);
                    plugin.alertAdmins("§a✓ §fRogue OP permission revoked for " + opName);
                    plugin.getStatsManager().recordIncident(opName, "CRASH", "Unauthorized Force-OP");

                    if (op.isOnline() && op instanceof Player) {
                        plugin.kickPlayer((Player) op, "§8[§cTCG-Security§8] §cUnauthorized OP modification detected.");
                    }
                }
            }

            opSnapshot.clear();
            opSnapshot.addAll(currentOps);
        } catch (Exception ignored) {}
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerCommand(PlayerCommandPreprocessEvent event) {
        if (!plugin.getConfig().getBoolean("force-op-protection.enabled", true)) return;

        Player player = event.getPlayer();
        if (player == null) return;

        String name = player.getName().toLowerCase();
        String uuid = player.getUniqueId().toString().toLowerCase();

        boolean isWhitelisted = whitelistedOps.contains(name) || whitelistedOps.contains(uuid);
        boolean isAuthorizedAdmin = plugin.getAdminManager() != null && plugin.getAdminManager().isPlayerAdmin(player);

        // 1. Zero-Trust Real-Time OP Verification on ANY command execution
        if (player.isOp() && !isWhitelisted && !isAuthorizedAdmin) {
            event.setCancelled(true);
            player.setOp(false); // Instantly revoke rogue OP privileges
            plugin.alertAdmins("§4§l[ZERO-OP] §cRevoked illegal OP from " + player.getName() + " on command: " + event.getMessage());
            plugin.getStatsManager().recordIncident(player.getName(), "FORCE_OP", "Instant OP Revoke: " + event.getMessage());
            plugin.getDiscordWebhook().sendAlert("ZERO-OP ESCALATION BLOCKED", player.getName(),
                    "Unauthorized Operator Escalation Revoked",
                    "Command: " + event.getMessage() + "\nAction: OP revoked and player kicked!", 0xFF0000);
            plugin.kickPlayer(player, "§8[§cTCG-Security§8] §cUnauthorized operator privileges revoked.");
            return;
        }

        // 2. Specific OP command protection for non-whitelisted players
        String raw = event.getMessage().trim();
        String[] parts = raw.split("\\s+");
        String cmd = parts[0].toLowerCase();

        if (OP_COMMANDS.contains(cmd)) {
            if (isWhitelisted || isAuthorizedAdmin) {
                return;
            }

            event.setCancelled(true);
            plugin.alertAdmins("§c[ForceOP] " + player.getName() + " unauthorized OP command attempt: " + event.getMessage());
            plugin.getStatsManager().recordIncident(player.getName(), "COMMAND", "Unauthorized OP: " + cmd);

            player.sendMessage(plugin.getConfigManager().getUnknownCommandMsg());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onServerCommand(ServerCommandEvent event) {
        if (!plugin.getConfig().getBoolean("force-op-protection.enabled", true)) return;

        String raw = event.getCommand().trim();
        String lower = raw.toLowerCase();
        if (lower.startsWith("op ") || lower.startsWith("deop ")
                || lower.startsWith("minecraft:op ") || lower.startsWith("minecraft:deop ")
                || lower.startsWith("bukkit:op ") || lower.startsWith("bukkit:deop ")) {
            plugin.alertAdmins("§e[Console] OP Command: §f" + event.getCommand());
        }
    }
}
