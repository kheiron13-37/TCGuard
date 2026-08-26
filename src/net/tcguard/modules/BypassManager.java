package net.tcguard.modules;

import net.tcguard.TCGuard;
import org.bukkit.entity.Player;

public class BypassManager {

    public static boolean isBypassed(Player player) {
        if (player == null) return false;
        TCGuard plugin = TCGuard.getInstance();
        if (plugin != null && plugin.getAdminManager() != null) {
            return plugin.getAdminManager().isPlayerAdmin(player);
        }
        return false;
    }
}
