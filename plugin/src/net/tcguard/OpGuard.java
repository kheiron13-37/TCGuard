package net.tcguard;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

final class OpGuard implements Runnable {
    private final TCGuard plugin;
    private Set<String> allowed = new HashSet<>();

    OpGuard(TCGuard plugin) {
        this.plugin = plugin;
    }

    void reload() {
        Set<String> next = new HashSet<>();
        for (String name : plugin.getConfig().getStringList("op-guard.allowed")) next.add(name.toLowerCase(Locale.ROOT));
        allowed = next;
    }

    @Override
    public void run() {
        if (allowed.isEmpty()) return;
        for (OfflinePlayer op : Bukkit.getOperators()) {
            String name = op.getName();
            if (name != null && allowed.contains(name.toLowerCase(Locale.ROOT))) continue;
            if (allowed.contains(op.getUniqueId().toString())) continue;
            op.setOp(false);
            plugin.count();
            plugin.alert("removed operator status from " + (name != null ? name : op.getUniqueId()) + " (not in op-guard.allowed)");
            if (op.isOnline()) plugin.kick(op.getPlayer());
        }
    }
}
