package net.tcguard.api;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.lang.reflect.Method;

public class Compatibility {

    private static final int SERVER_VERSION;

    static {
        int version = 8;
        try {
            String versionString = Bukkit.getBukkitVersion();
            if (versionString.contains("1.8")) version = 8;
            else if (versionString.contains("1.9")) version = 9;
            else if (versionString.contains("1.10")) version = 10;
            else if (versionString.contains("1.11")) version = 11;
            else if (versionString.contains("1.12")) version = 12;
            else if (versionString.contains("1.13")) version = 13;
            else if (versionString.contains("1.14")) version = 14;
            else if (versionString.contains("1.15")) version = 15;
            else if (versionString.contains("1.16")) version = 16;
            else if (versionString.contains("1.17")) version = 17;
            else if (versionString.contains("1.18")) version = 18;
            else if (versionString.contains("1.19")) version = 19;
            else if (versionString.contains("1.20")) version = 20;
            else if (versionString.contains("1.21")) version = 21;
        } catch (Exception ignored) {}
        SERVER_VERSION = version;
    }

    public static int getServerVersion() {
        return SERVER_VERSION;
    }

    public static boolean isLegacy() {
        return SERVER_VERSION < 13;
    }

    public static Material getMaterial(String... names) {
        for (String name : names) {
            try {
                Material mat = Material.matchMaterial(name);
                if (mat != null) return mat;
            } catch (Exception ignored) {}
        }
        return Material.STONE;
    }

    public static void playSound(Player player, float volume, float pitch, String... soundNames) {
        if (player == null) return;
        for (String name : soundNames) {
            try {
                Sound sound = Sound.valueOf(name);
                player.playSound(player.getLocation(), sound, volume, pitch);
                return;
            } catch (Exception ignored) {}
        }
    }

    public static String getPlayerLocale(Player player) {
        if (player == null) return "en_US";
        try {
            Method getLocaleMethod = player.getClass().getMethod("getLocale");
            Object localeObj = getLocaleMethod.invoke(player);
            if (localeObj != null) return localeObj.toString();
        } catch (Exception ignored) {}
        try {
            Player.Spigot spigot = player.spigot();
            Method getLocaleMethod = spigot.getClass().getMethod("getLocale");
            Object localeObj = getLocaleMethod.invoke(spigot);
            if (localeObj != null) return localeObj.toString();
        } catch (Exception ignored) {}
        return "en_US";
    }

    public static ItemStack getItemInHand(Player player) {
        if (player == null) return null;
        try {
            if (!isLegacy()) {
                Method m = player.getInventory().getClass().getMethod("getItemInMainHand");
                return (ItemStack) m.invoke(player.getInventory());
            }
        } catch (Exception ignored) {}
        try {
            return player.getItemInHand();
        } catch (Exception ignored) {}
        return null;
    }

    public static double[] getTPS() {
        try {
            Object server = Bukkit.getServer().getClass().getMethod("getServer").invoke(Bukkit.getServer());
            java.lang.reflect.Field tpsField = server.getClass().getField("recentTps");
            return (double[]) tpsField.get(server);
        } catch (Exception ignored) {}
        try {
            return Bukkit.spigot().getTPS();
        } catch (Throwable ignored) {}
        return new double[]{20.0, 20.0, 20.0};
    }
}
