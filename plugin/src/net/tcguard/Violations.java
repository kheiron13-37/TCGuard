package net.tcguard;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

final class Violations {
    enum Category { PACKETS, ITEMS, COMMANDS }

    private static final long REPEAT_NANOS = 5_000_000_000L;

    private static final class Rule {
        double cancel, decay;
        long graceNanos;
        final TreeMap<Double, List<String>> actions = new TreeMap<>();
    }

    private static final class Score {
        double value;
        long updated = System.nanoTime(), raised;
        final Map<Double, Long> fired = new HashMap<>();

        double current(Rule rule, long now) {
            long from = Math.max(updated, raised + rule.graceNanos);
            if (now > from) value = Math.max(0, value - rule.decay * (now - from) / 1e9);
            updated = now;
            return value;
        }
    }

    private final TCGuard plugin;
    private final Map<UUID, Score[]> scores = new ConcurrentHashMap<>();
    private volatile Map<Category, Rule> rules = Collections.emptyMap();

    Violations(TCGuard plugin) {
        this.plugin = plugin;
    }

    void reload() {
        Map<Category, Rule> next = new EnumMap<>(Category.class);
        for (Category category : Category.values()) {
            String path = "violations." + category.name().toLowerCase(Locale.ROOT) + ".";
            Rule rule = new Rule();
            rule.cancel = plugin.getConfig().getDouble(path + "cancel-vls");
            rule.decay = plugin.getConfig().getDouble(path + "reduce-vls");
            rule.graceNanos = (long) (plugin.getConfig().getDouble(path + "decay-delay-seconds") * 1e9);
            for (String line : plugin.getConfig().getStringList(path + "actions")) {
                String[] parts = line.trim().split("\\s+", 2);
                try {
                    rule.actions.computeIfAbsent(Double.parseDouble(parts[0]), level -> new ArrayList<>()).add(parts[1]);
                } catch (RuntimeException e) {
                    plugin.getLogger().warning(path + "actions: cannot read '" + line + "'");
                }
            }
            next.put(category, rule);
        }
        rules = next;
    }

    double add(Player player, Category category, double points, String check) {
        Rule rule = rules.get(category);
        Score score = score(player, category);
        double after;
        List<String> actions = new ArrayList<>();
        synchronized (score) {
            long now = System.nanoTime();
            double before = score.current(rule, now);
            after = score.value = before + points;
            score.raised = now;
            for (Map.Entry<Double, List<String>> level : rule.actions.subMap(before, false, after, true).entrySet()) {
                Long last = score.fired.put(level.getKey(), now);
                if (last == null || now - last > REPEAT_NANOS) actions.addAll(level.getValue());
            }
        }
        if (!actions.isEmpty()) run(player, category, check, after, actions);
        return after;
    }

    double get(Player player, Category category) {
        Score score = score(player, category);
        synchronized (score) {
            return score.current(rules.get(category), System.nanoTime());
        }
    }

    boolean cancels(Player player, Category category) {
        return get(player, category) >= rules.get(category).cancel;
    }

    void forget(UUID player) {
        scores.remove(player);
    }

    private Score score(Player player, Category category) {
        Score[] all = scores.computeIfAbsent(player.getUniqueId(), id -> {
            Score[] fresh = new Score[Category.values().length];
            for (int i = 0; i < fresh.length; i++) fresh[i] = new Score();
            return fresh;
        });
        return all[category.ordinal()];
    }

    private void run(Player player, Category category, String check, double vls, List<String> actions) {
        String level = String.format(Locale.ROOT, "%.1f", vls);
        plugin.sync(() -> {
            for (String action : actions) {
                if (action.equalsIgnoreCase("notification")) {
                    plugin.alert(player.getName() + " failed " + check + " (" + category.name().toLowerCase(Locale.ROOT) + " " + level + " vls)");
                } else if (action.equalsIgnoreCase("kick")) {
                    plugin.kick(player);
                } else {
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), action
                            .replace("%player%", player.getName())
                            .replace("%uuid%", player.getUniqueId().toString())
                            .replace("%check%", check)
                            .replace("%vls%", level));
                }
            }
        });
    }
}
