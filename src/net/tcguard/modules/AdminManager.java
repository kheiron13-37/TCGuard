package net.tcguard.modules;

import net.tcguard.TCGuard;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

public class AdminManager {

    private final TCGuard plugin;
    private final Set<String> authorizedAdmins = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private final File adminFile;
    private static final Pattern USERNAME_PATTERN = Pattern.compile("^[a-zA-Z0-9_]{3,16}$");

    public AdminManager(TCGuard plugin) {
        this.plugin = plugin;
        this.adminFile = new File(plugin.getDataFolder(), "admins.yml");
    }

    public void load() {
        authorizedAdmins.clear();
        if (!adminFile.exists()) {
            save();
            return;
        }

        try {
            FileConfiguration cfg = YamlConfiguration.loadConfiguration(adminFile);
            List<String> list = cfg.getStringList("authorized-admins");
            if (list != null) {
                for (String name : list) {
                    if (name != null && isValidUsername(name)) {
                        authorizedAdmins.add(name.toLowerCase());
                    }
                }
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Error loading admins.yml: " + e.getMessage());
        }
    }

    public void save() {
        List<String> snapshot = new ArrayList<>(authorizedAdmins);
        org.bukkit.Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                if (!plugin.getDataFolder().exists()) {
                    plugin.getDataFolder().mkdirs();
                }
                FileConfiguration cfg = new YamlConfiguration();
                cfg.set("authorized-admins", snapshot);
                cfg.save(adminFile);
            } catch (Exception e) {
                plugin.getLogger().warning("Error saving admins.yml: " + e.getMessage());
            }
        });
    }

    public boolean isAdmin(CommandSender sender) {
        if (sender == null) return false;
        if (sender instanceof ConsoleCommandSender) return true;
        if (sender instanceof Player) {
            return isPlayerAdmin((Player) sender);
        }
        return false;
    }

    public boolean isPlayerAdmin(Player player) {
        if (player == null) return false;
        return authorizedAdmins.contains(player.getName().toLowerCase());
    }

    public boolean addAdmin(String username) {
        if (!isValidUsername(username)) return false;
        boolean added = authorizedAdmins.add(username.toLowerCase());
        if (added) {
            save();
        }
        return added;
    }

    public boolean removeAdmin(String username) {
        if (username == null) return false;
        boolean removed = authorizedAdmins.remove(username.toLowerCase());
        if (removed) {
            save();
        }
        return removed;
    }

    public Set<String> getAuthorizedAdmins() {
        return Collections.unmodifiableSet(authorizedAdmins);
    }

    public boolean isValidUsername(String name) {
        if (name == null || name.length() < 3 || name.length() > 16) return false;
        return USERNAME_PATTERN.matcher(name).matches();
    }
}
