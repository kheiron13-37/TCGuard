package net.tcguard.util;

import org.bukkit.ChatColor;

import java.util.ArrayList;
import java.util.List;

public class ColorUtil {

    public static String colorize(String message) {
        if (message == null) return "";
        return ChatColor.translateAlternateColorCodes('&', message);
    }

    public static List<String> colorize(List<String> messages) {
        if (messages == null) return new ArrayList<>();
        List<String> colored = new ArrayList<>();
        for (String msg : messages) {
            colored.add(colorize(msg));
        }
        return colored;
    }

    public static String stripColor(String message) {
        if (message == null) return "";
        return ChatColor.stripColor(message);
    }
}
