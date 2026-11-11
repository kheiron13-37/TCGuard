package net.tcguard;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

final class CommandFilter implements Listener {
    private final TCGuard plugin;
    private volatile List<String> blocked = new ArrayList<>();

    CommandFilter(TCGuard plugin) {
        this.plugin = plugin;
    }

    void reload() {
        List<String> next = new ArrayList<>();
        for (String entry : plugin.getConfig().getStringList("commands.blocked")) next.add(normalize(entry));
        blocked = next;
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();
        if (plugin.bypasses(player)) return;
        String command = normalize(event.getMessage());
        for (String entry : blocked) {
            if (!matches(command, entry)) continue;
            event.setCancelled(true);
            player.sendMessage(plugin.message("unknown-command"));
            plugin.count();
            plugin.violations().add(player, Violations.Category.COMMANDS, 1, "command " + entry);
            return;
        }
    }

    static boolean matches(String command, String entry) {
        if (entry.endsWith("*")) return command.startsWith(entry.substring(0, entry.length() - 1));
        return command.equals(entry) || command.startsWith(entry + " ");
    }

    static String normalize(String command) {
        String c = command.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
        if (!c.startsWith("/")) c = "/" + c;
        int space = c.indexOf(' '), colon = c.indexOf(':');
        boolean namespaced = colon > 0 && (space < 0 || colon < space);
        return namespaced ? "/" + c.substring(colon + 1) : c;
    }
}
