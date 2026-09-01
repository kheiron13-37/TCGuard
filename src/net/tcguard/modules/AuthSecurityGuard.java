package net.tcguard.modules;

import net.tcguard.TCGuard;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class AuthSecurityGuard implements Listener {

    private final TCGuard plugin;

    private final Map<String, Integer> failedAttemptsPerIp = new ConcurrentHashMap<>();
    private final Map<String, Long> lockoutExpiryPerIp = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> playerLoginTries = new ConcurrentHashMap<>();

    private static final Set<String> WEAK_PASSWORDS = new HashSet<>(Arrays.asList(
            "123456", "12345678", "123456789", "password", "pass123", "qwerty", "123123",
            "admin", "admin123", "minecraft", "sunucu", "111111", "000000", "default",
            "welcome", "secret", "iloveyou", "password123", "root", "qwertyuiop", "12345"
    ));

    public AuthSecurityGuard(TCGuard plugin) {
        this.plugin = plugin;
        hookAuthMeEvents();
    }

    private void hookAuthMeEvents() {
        Plugin authMe = Bukkit.getPluginManager().getPlugin("AuthMe");
        if (authMe != null && authMe.isEnabled()) {
            try {
                // Safely listen to AuthMe Login and FailedLogin events if classes exist
                Class<?> failedLoginClass = Class.forName("fr.xephi.authme.events.FailedLoginEvent");
                Class<?> loginClass = Class.forName("fr.xephi.authme.events.LoginEvent");

                Bukkit.getPluginManager().registerEvent(
                        (Class<? extends org.bukkit.event.Event>) failedLoginClass,
                        new Listener() {},
                        EventPriority.MONITOR,
                        (listener, event) -> {
                            try {
                                Method getPlayer = event.getClass().getMethod("getPlayer");
                                Player p = (Player) getPlayer.invoke(event);
                                if (p != null) {
                                    recordFailedLogin(p);
                                }
                            } catch (Exception ignored) {}
                        },
                        plugin
                );

                Bukkit.getPluginManager().registerEvent(
                        (Class<? extends org.bukkit.event.Event>) loginClass,
                        new Listener() {},
                        EventPriority.MONITOR,
                        (listener, event) -> {
                            try {
                                Method getPlayer = event.getClass().getMethod("getPlayer");
                                Player p = (Player) getPlayer.invoke(event);
                                if (p != null) {
                                    recordSuccessfulLogin(p);
                                }
                            } catch (Exception ignored) {}
                        },
                        plugin
                );

                plugin.getLogger().info("[AuthSecurity] Successfully hooked into AuthMe login events.");
            } catch (Throwable ignored) {
                plugin.getLogger().fine("[AuthSecurity] AuthMe events hook fallback to command interception.");
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onAuthCommand(PlayerCommandPreprocessEvent event) {
        if (!plugin.getConfig().getBoolean("auth-security.enabled", true)) return;

        Player player = event.getPlayer();
        String raw = event.getMessage().trim();
        if (!raw.startsWith("/")) return;

        String clean = raw.replaceFirst("^/+", "").trim();
        String[] parts = clean.split("\\s+");
        if (parts.length == 0) return;

        String cmd = parts[0].toLowerCase();
        String ip = player.getAddress() != null ? player.getAddress().getAddress().getHostAddress() : "127.0.0.1";
        long now = System.currentTimeMillis();

        Long lockout = lockoutExpiryPerIp.get(ip);
        if (lockout != null) {
            if (now < lockout) {
                long remainingSec = (lockout - now) / 1000L;
                event.setCancelled(true);
                player.sendMessage("§8[§cTCG-Security§8] §cToo many failed authentication attempts. Locked for " + remainingSec + "s.");
                return;
            } else {
                lockoutExpiryPerIp.remove(ip);
                failedAttemptsPerIp.remove(ip);
            }
        }

        if (parts.length >= 2 && (cmd.equals("register") || cmd.equals("reg") || cmd.equals("kayit") || cmd.equals("kayıt"))) {
            if (plugin.getConfig().getBoolean("auth-security.password-strength.enabled", true)) {
                String password = parts[1];
                int minLength = plugin.getConfig().getInt("auth-security.password-strength.min-length", 6);

                if (isWeakPassword(password, player.getName(), minLength)) {
                    event.setCancelled(true);
                    player.sendMessage("§8[§cTCG-Security§8] §cWeak or commonly leaked password! Choose a safer password (min " + minLength + " characters, no simple sequences).");
                    return;
                }
            }
        }

        if (cmd.equals("login") || cmd.equals("l") || cmd.equals("giris") || cmd.equals("giriş")) {
            int tries = playerLoginTries.getOrDefault(player.getUniqueId(), 0) + 1;
            playerLoginTries.put(player.getUniqueId(), tries);

            int maxAttempts = plugin.getConfig().getInt("auth-security.brute-force.max-attempts", 5);
            if (tries > maxAttempts * 2) {
                recordFailedLogin(player);
            }
        }

        if (plugin.getConfig().getBoolean("auth-security.admin-ip-lock.enabled", true)) {
            if (plugin.getAdminManager().isPlayerAdmin(player)) {
                List<String> trustedIps = plugin.getConfig().getStringList("auth-security.admin-ip-lock.trusted-ips");
                if (trustedIps != null && !trustedIps.isEmpty()) {
                    boolean ipMatched = false;
                    for (String trusted : trustedIps) {
                        if (ip.equals(trusted) || ip.startsWith(trusted)) {
                            ipMatched = true;
                            break;
                        }
                    }
                    if (!ipMatched) {
                        event.setCancelled(true);
                        plugin.alertAdmins("§4[AdminSecurity] §cUnauthorized IP (" + ip + ") tried to access Admin account " + player.getName());
                        plugin.getStatsManager().recordIncident(player.getName(), "ADMIN_IP_BREACH", "Untrusted IP: " + ip);
                        plugin.kickPlayer(player, "§8[§cTCG-Security§8] §cUntrusted IP address for Administrator account.");
                    }
                }
            }
        }
    }

    public void recordFailedLogin(Player player) {
        if (player == null || player.getAddress() == null) return;
        String ip = player.getAddress().getAddress().getHostAddress();

        int maxAttempts = plugin.getConfig().getInt("auth-security.brute-force.max-attempts", 3);
        int lockMinutes = plugin.getConfig().getInt("auth-security.brute-force.lockout-minutes", 10);

        int attempts = failedAttemptsPerIp.getOrDefault(ip, 0) + 1;
        failedAttemptsPerIp.put(ip, attempts);

        if (attempts >= maxAttempts) {
            long lockExpiry = System.currentTimeMillis() + (lockMinutes * 60000L);
            lockoutExpiryPerIp.put(ip, lockExpiry);

            plugin.alertAdmins("§c[BruteForce] " + player.getName() + " (" + ip + ") locked out for " + lockMinutes + " minutes (" + attempts + " failed attempts)");
            plugin.getStatsManager().recordIncident(player.getName(), "BRUTE_FORCE", "Lockout: " + ip);
            plugin.kickPlayer(player, "§8[§cTCG-Security§8] §cAccount locked for " + lockMinutes + " minutes due to multiple failed login attempts.");
        }
    }

    public void recordSuccessfulLogin(Player player) {
        if (player == null) return;
        playerLoginTries.remove(player.getUniqueId());
        if (player.getAddress() == null) return;
        String ip = player.getAddress().getAddress().getHostAddress();
        failedAttemptsPerIp.remove(ip);
        lockoutExpiryPerIp.remove(ip);
    }

    public boolean isWeakPassword(String password, String username, int minLength) {
        if (password == null || password.length() < minLength) {
            return true;
        }

        String lowerPass = password.toLowerCase();
        if (WEAK_PASSWORDS.contains(lowerPass)) {
            return true;
        }

        if (username != null && lowerPass.contains(username.toLowerCase())) {
            return true;
        }

        boolean allSame = true;
        for (int i = 1; i < password.length(); i++) {
            if (password.charAt(i) != password.charAt(0)) {
                allSame = false;
                break;
            }
        }
        return allSame;
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        playerLoginTries.remove(event.getPlayer().getUniqueId());
    }

    public void cleanup() {
        long now = System.currentTimeMillis();
        lockoutExpiryPerIp.entrySet().removeIf(e -> now > e.getValue());
    }
}
