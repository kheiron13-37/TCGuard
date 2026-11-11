package net.tcguard;

import io.netty.channel.Channel;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public final class TCGuard extends JavaPlugin implements Listener {
    private final Violations violations = new Violations(this);
    private final CommandFilter commands = new CommandFilter(this);
    private final OpGuard ops = new OpGuard(this);
    private final Log4jFilter log4j = new Log4jFilter(this);

    private final boolean legacy = Bukkit.getBukkitVersion().startsWith("1.8");
    private final NettyGuard netty = new NettyGuard(this, legacy);
    private ItemSanitizer items;

    private final Set<UUID> kicking = ConcurrentHashMap.newKeySet();
    private final Set<UUID> bypassing = ConcurrentHashMap.newKeySet();
    private final Set<UUID> muted = ConcurrentHashMap.newKeySet();
    private final AtomicLong blocked = new AtomicLong();

    @Override
    public void onEnable() {
        saveDefaultConfig();
        if (legacy) items = new ItemSanitizer(this);
        load();

        try {
            netty.enable();
        } catch (ReflectiveOperationException | RuntimeException e) {
            getLogger().severe("Frame guard is off, connections are not inspected: " + e);
        }
        try {
            log4j.install();
        } catch (RuntimeException | LinkageError e) {
            getLogger().warning("Log filter is off: " + e);
        }
        getServer().getPluginManager().registerEvents(this, this);
        getServer().getPluginManager().registerEvents(commands, this);
        getServer().getPluginManager().registerEvents(log4j, this);
        if (legacy) {
            getServer().getPluginManager().registerEvents(items, this);
            getServer().getPluginManager().registerEvents(new WorldPatches(this), this);
        } else {
            getLogger().info("Running on " + Bukkit.getBukkitVersion() + ": frame size, compression and rate limits, command filter, "
                    + "operator list and origin lock are active. Packet content checks and item rebuilding exist for 1.8 only.");
        }
        getServer().getScheduler().runTaskTimer(this, ops, 20, 20);
        for (Player player : Bukkit.getOnlinePlayers()) track(player);
    }

    @Override
    public void onDisable() {
        netty.disable();
        log4j.uninstall();
    }

    private void load() {
        reloadConfig();
        FrameInspector.Limits limits = new FrameInspector.Limits();
        limits.frame = getConfig().getInt("frames.max-bytes");
        limits.itemFrame = getConfig().getInt("frames.max-item-bytes");
        limits.nbtDepth = getConfig().getInt("frames.nbt.max-depth");
        limits.nbtTags = getConfig().getInt("frames.nbt.max-tags");
        limits.nbtList = getConfig().getInt("frames.nbt.max-list-size");
        limits.nbtString = getConfig().getInt("frames.nbt.max-string-bytes");
        netty.limits = limits;
        netty.maxFrame = getConfig().getInt("frames.modern-max-bytes");
        netty.byteWeight = getConfig().getDouble("violations.packets.byte-weight");
        Map<String, Double> weights = new HashMap<>();
        for (ConfigurationSection config : new ConfigurationSection[]{getConfig().getDefaults(), getConfig()}) {
            ConfigurationSection section = config.getConfigurationSection("violations.packets.weights");
            if (section == null) continue;
            for (String key : section.getKeys(false)) weights.put(key, section.getDouble(key));
        }
        netty.weights = weights;
        netty.defaultWeight = weights.getOrDefault("OTHER", 0.1);
        Set<InetAddress> proxies = new HashSet<>();
        for (String host : getConfig().getStringList("proxy-protocol.trusted")) {
            try {
                proxies.add(InetAddress.getByName(host));
            } catch (UnknownHostException e) {
                getLogger().warning("proxy-protocol.trusted: unknown host " + host);
            }
        }
        netty.trustedProxies = proxies;
        String secret = getConfig().getString("proxy-protocol.secret", "");
        netty.proxySecret = secret.isEmpty() ? null : secret.getBytes(StandardCharsets.UTF_8);
        violations.reload();
        if (items != null) items.reload();
        commands.reload();
        ops.reload();
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onJoin(PlayerJoinEvent event) {
        track(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        netty.unbind(event.getPlayer());
        violations.forget(id);
        kicking.remove(id);
        bypassing.remove(id);
        muted.remove(id);
    }

    private void track(Player player) {
        netty.bind(player);
        if (player.hasPermission("tcguard.bypass")) bypassing.add(player.getUniqueId());
    }

    Violations violations() {
        return violations;
    }

    boolean bypasses(Player player) {
        return bypassing.contains(player.getUniqueId());
    }

    boolean isKicking(Player player) {
        return player != null && kicking.contains(player.getUniqueId());
    }

    void count() {
        blocked.incrementAndGet();
    }

    String message(String key) {
        return ChatColor.translateAlternateColorCodes('&', getConfig().getString("messages." + key));
    }

    void sync(Runnable task) {
        if (Bukkit.isPrimaryThread()) task.run();
        else if (isEnabled()) Bukkit.getScheduler().runTask(this, task);
    }

    void kick(Player player) {
        if (!kicking.add(player.getUniqueId())) return;
        sync(() -> player.kickPlayer(message("kick")));
    }

    void reject(Player player, Channel channel, String reason) {
        count();
        String who = player != null ? player.getName() : String.valueOf(ProxyHeader.address(channel));
        alert(who + " sent a hostile frame: " + reason);
        if (player != null) kick(player);
        else channel.close();
    }

    void alert(String text) {
        sync(() -> {
            String line = message("prefix") + text;
            Bukkit.getConsoleSender().sendMessage(line);
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (player.hasPermission("tcguard.alerts") && !muted.contains(player.getUniqueId())) player.sendMessage(line);
            }
        });
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String action = args.length == 0 ? "status" : args[0].toLowerCase(Locale.ROOT);
        switch (action) {
            case "reload":
                load();
                sender.sendMessage(message("prefix") + "Configuration reloaded.");
                return true;
            case "status":
                sender.sendMessage(message("prefix") + "TCGuard " + getDescription().getVersion() + ": "
                        + netty.connections() + " guarded connections, " + blocked + " blocked since start.");
                return true;
            case "alerts":
                if (!(sender instanceof Player)) return false;
                UUID id = ((Player) sender).getUniqueId();
                boolean on = !muted.add(id) && muted.remove(id);
                sender.sendMessage(message("prefix") + "Alerts " + (on ? "on." : "off."));
                return true;
            case "vl":
                Player target = args.length > 1 ? Bukkit.getPlayerExact(args[1]) : null;
                if (target == null) return false;
                StringBuilder line = new StringBuilder(message("prefix")).append(target.getName()).append(':');
                for (Violations.Category category : Violations.Category.values()) {
                    line.append(String.format(Locale.ROOT, " %s %.1f", category.name().toLowerCase(Locale.ROOT), violations.get(target, category)));
                }
                sender.sendMessage(line.toString());
                return true;
            default:
                return false;
        }
    }
}
