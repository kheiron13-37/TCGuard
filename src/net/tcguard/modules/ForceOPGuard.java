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

            for (UUID uuid : opSnapshot) {
                if (!currentOps.contains(uuid)) {
                    OfflinePlayer p = Bukkit.getOfflinePlayer(uuid);
                    String name = (p != null && p.getName() != null) ? p.getName() : null;
                    if (name != null) {
                        plugin.getAdminManager().removeAdmin(name);
                    }
                }
            }

            for (OfflinePlayer op : Bukkit.getOperators()) {
                if (op == null || op.getUniqueId() == null) continue;
                UUID uuid = op.getUniqueId();

                String name = op.getName() != null ? op.getName().toLowerCase() : uuid.toString();

                if (!opSnapshot.contains(uuid) && !whitelistedOps.contains(name) && !whitelistedOps.contains(uuid.toString().toLowerCase())) {
                    String opName = op.getName() != null ? op.getName() : uuid.toString();

                    plugin.alertAdmins("§4§l⚠ ALARM: §cUnexpected OP modification detected! Player: §e" + opName);
                    plugin.getDiscordWebhook().sendAlert("CRITICAL: FORCE-OP DETECTED", opName,
                            "Unauthorized OP Promotion",
                            "Player: " + opName + " (" + uuid + ")\nAction: OP revoked automatically!", 0xFF0000);

                    op.setOp(false);
                    plugin.getAdminManager().removeAdmin(opName);
                    plugin.alertAdmins("§a✓ §fOP permission revoked for " + opName);
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

        String raw = event.getMessage().trim();
        String[] parts = raw.split("\\s+");
        String cmd = parts[0].toLowerCase();

        if (OP_COMMANDS.contains(cmd)) {
            Player player = event.getPlayer();
            String name = player.getName().toLowerCase();
            String uuid = player.getUniqueId().toString().toLowerCase();

            if (whitelistedOps.contains(name) || whitelistedOps.contains(uuid) || plugin.getAdminManager().isPlayerAdmin(player)) {
                if (cmd.equals("/deop") || cmd.equals("/minecraft:deop") || cmd.equals("/bukkit:deop")) {
                    if (parts.length >= 2) {
                        plugin.getAdminManager().removeAdmin(parts[1]);
                    }
                }
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

            if (lower.startsWith("deop ") || lower.startsWith("minecraft:deop ") || lower.startsWith("bukkit:deop ")) {
                String[] parts = raw.split("\\s+");
                if (parts.length >= 2) {
                    plugin.getAdminManager().removeAdmin(parts[1]);
                }
            }
        }
    }
}
