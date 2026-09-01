package net.tcguard.modules;

import net.tcguard.TCGuard;
import org.bukkit.entity.Player;

public class BypassManager {

    public static boolean isBypassed(Player player) {
        if (player == null) return false;
        TCGuard plugin = TCGuard.getInstance();
        if (plugin == null) return false;

        // Check if player is an authorized admin in admins.yml
        if (plugin.getAdminManager() != null && plugin.getAdminManager().isPlayerAdmin(player)) {
            return true;
        }

        // Check explicit bypass permission if configured
        String perm = (plugin.getConfigManager() != null) ? plugin.getConfigManager().getBypassPermission() : "tcguard.bypass";
        try {
            if (perm != null && !perm.isEmpty() && player.hasPermission(perm)) {
                return true;
            }
        } catch (Exception ignored) {}

        return false;
    }
}
