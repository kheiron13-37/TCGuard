package net.tcguard.modules;

import net.tcguard.TCGuard;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class HoneypotGuard implements Listener {

    private final TCGuard plugin;
    private final Map<UUID, Integer> honeypotStrikes = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastStrikeTime = new ConcurrentHashMap<>();

    private static final Set<String> HONEYPOT_COMMANDS = new HashSet<>(Arrays.asList(
            "//calc",
            "//eval",
            "/.",
            "/pex promote",
            "/pex user *",
            "/system",
            "/eval",
            "/plugman unload",
            "/plugman disable",
            "/op @a",
            "/give @a command_block",
            "/give @a barrier 64"
    ));

    public HoneypotGuard(TCGuard plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerCommand(PlayerCommandPreprocessEvent event) {
        if (!plugin.getConfig().getBoolean("honeypot.enabled", true)) return;

        Player player = event.getPlayer();
        if (plugin.getAdminManager().isPlayerAdmin(player)) return;

        String msg = event.getMessage().toLowerCase().trim();

        for (String trap : HONEYPOT_COMMANDS) {
            if (msg.equals(trap) || msg.startsWith(trap + " ")) {
                event.setCancelled(true);

                UUID uuid = player.getUniqueId();
                long now = System.currentTimeMillis();
                Long lastTime = lastStrikeTime.get(uuid);

                if (lastTime == null || (now - lastTime) > 30000L) {
                    honeypotStrikes.put(uuid, 1);
                } else {
                    honeypotStrikes.put(uuid, honeypotStrikes.getOrDefault(uuid, 0) + 1);
                }
                lastStrikeTime.put(uuid, now);

                int strikes = honeypotStrikes.getOrDefault(uuid, 1);
                String ip = player.getAddress() != null ? player.getAddress().getAddress().getHostAddress() : "Unknown";

                plugin.alertAdmins("§4§l🍯 HONEYPOT TRAP: §c" + player.getName() + " executed trap command: §e" + event.getMessage() + " §7(Strike: " + strikes + "/3)");
                plugin.getStatsManager().recordIncident(player.getName(), "COMMAND", "Honeypot Trap: " + trap);

                player.sendMessage(plugin.getConfigManager().getUnknownCommandMsg());

                if (strikes >= 3 && plugin.getConfig().getBoolean("honeypot.kick-player", true)) {
                    plugin.getDiscordWebhook().sendAlert("HONEYPOT EXPLOIT BLOCKED", player.getName(),
                            "Repeated Exploit Command Attempts",
                            "Player: " + player.getName() + " (" + ip + ")\nCommand: `" + event.getMessage() + "`\nStrikes: " + strikes, 0xFF0000);

                    if (plugin.getThreatIntelligence() != null && !ip.equals("Unknown")) {
                        plugin.getThreatIntelligence().quarantineIp(ip, 300);
                    }

                    honeypotStrikes.remove(uuid);
                    plugin.kickPlayer(player, "§8[§cTCG-Security§8] §cRepeated suspicious command activity detected.");
                }
                return;
            }
        }
    }
}

