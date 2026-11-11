package net.tcguard;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.Filter;
import org.apache.logging.log4j.core.Logger;
import org.apache.logging.log4j.core.config.Configuration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;

final class Log4jFilter implements Listener {
    private static final String[] LOOKUPS = {"jndi:", "env:", "sys:", "java:", "lower:", "upper:", "date:",
            "ctx:", "main:", "bundle:", "base64:", "url:", "log4j:", "sd:", "web:", "::-", "${"};

    private final TCGuard plugin;
    private Configuration logging;
    private Filter filter;

    Log4jFilter(TCGuard plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onChat(AsyncPlayerChatEvent event) {
        if (matches(event.getMessage())) refuse(event.getPlayer(), event);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        if (matches(event.getMessage())) refuse(event.getPlayer(), event);
    }

    private void refuse(Player player, org.bukkit.event.Cancellable event) {
        event.setCancelled(true);
        plugin.reject(player, null, "log4j lookup in chat");
    }

    static boolean matches(String text) {
        if (text == null || text.indexOf('$') < 0 && text.indexOf('%') < 0 && text.indexOf('\\') < 0) return false;
        String s = decode(text.length() > 32768 ? text.substring(0, 32768) : text);
        for (int i = s.indexOf("${"); i >= 0; i = s.indexOf("${", i + 2)) {
            for (String lookup : LOOKUPS) {
                if (s.startsWith(lookup, i + 2)) return true;
            }
            int colon = s.indexOf(':', i + 2);
            if (colon > 0 && colon - i < 130 && s.substring(i + 2, colon).replaceAll("[^a-z]", "").contains("jndi")) return true;
        }
        return false;
    }

    private static String decode(String text) {
        StringBuilder out = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            try {
                if (c == '%' && i + 2 < text.length()) {
                    c = (char) Integer.parseInt(text.substring(i + 1, i + 3), 16);
                    i += 2;
                } else if (c == '\\' && i + 5 < text.length() && (text.charAt(i + 1) | 0x20) == 'u') {
                    c = (char) Integer.parseInt(text.substring(i + 2, i + 6), 16);
                    i += 5;
                }
            } catch (NumberFormatException e) {
                c = text.charAt(i);
            }
            if (!Character.isWhitespace(c)) out.append(Character.toLowerCase(c));
        }
        return out.toString();
    }

    void install() {
        Filter.Result deny = Filter.Result.DENY, neutral = Filter.Result.NEUTRAL;
        InvocationHandler handler = (proxy, method, args) -> {
            switch (method.getName()) {
                case "filter":
                    return hostile(args) ? deny : neutral;
                case "getOnMatch":
                case "getOnMismatch":
                    return neutral;
                case "isStarted":
                    return true;
                case "equals":
                    return proxy == args[0];
                case "hashCode":
                    return System.identityHashCode(proxy);
                case "toString":
                    return "TCGuard Log4jFilter";
                default:
                    return method.getReturnType() == boolean.class ? Boolean.FALSE : null;
            }
        };
        filter = (Filter) Proxy.newProxyInstance(Filter.class.getClassLoader(), new Class<?>[]{Filter.class}, handler);
        logging = ((Logger) LogManager.getRootLogger()).getContext().getConfiguration();
        logging.addFilter(filter);
    }

    private static boolean hostile(Object[] args) {
        for (Object arg : args) {
            if (arg instanceof Object[] ? hostile((Object[]) arg) : arg != null && !(arg instanceof Enum) && matches(arg.toString())) return true;
        }
        return false;
    }

    void uninstall() {
        if (filter != null) logging.removeFilter(filter);
    }
}
