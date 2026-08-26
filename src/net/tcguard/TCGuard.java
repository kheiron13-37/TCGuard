package net.tcguard;

import net.tcguard.api.Compatibility;
import net.tcguard.config.ConfigManager;
import net.tcguard.config.LanguageManager;
import net.tcguard.modules.*;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Set;
import java.util.logging.Level;

public class TCGuard extends JavaPlugin implements CommandExecutor {

    private static TCGuard instance;

    private ConfigManager configManager;
    private AdminManager adminManager;
    private RateLimiter rateLimiter;
    private NbtValidator nbtValidator;
    private AntiBotManager antiBotManager;
    private PacketGuard packetGuard;
    private EventGuard eventGuard;
    private LagGuard lagGuard;
    private GuardGui guardGui;
    private StatsManager statsManager;
    private DiscordWebhook discordWebhook;
    private PatcherGuard patcherGuard;
    private NettyPipelineGuard nettyPipelineGuard;
    private ClickAnalysisGuard clickAnalysisGuard;
    private FastInteractGuard fastInteractGuard;
    private ThreatIntelligence threatIntelligence;
    private DatabaseManager databaseManager;
    private ProxyBridge proxyBridge;
    private ForceOPGuard forceOPGuard;
    private HoneypotGuard honeypotGuard;
    private SmartLagGuard smartLagGuard;
    private AuthSecurityGuard authSecurityGuard;
    private GuardCodeManager guardCodeManager;

    @Override
    public void onEnable() {
        instance = this;

        configManager = new ConfigManager(this);
        configManager.load();

        adminManager = new AdminManager(this);
        adminManager.load();

        databaseManager = new DatabaseManager(this);
        databaseManager.init();

        proxyBridge = new ProxyBridge(this);
        proxyBridge.register();

        rateLimiter = new RateLimiter();
        nbtValidator = new NbtValidator(this);
        threatIntelligence = new ThreatIntelligence(this);
        antiBotManager = new AntiBotManager(this);
        statsManager = new StatsManager(this);
        discordWebhook = new DiscordWebhook(this);
        clickAnalysisGuard = new ClickAnalysisGuard(this);
        fastInteractGuard = new FastInteractGuard(this);
        nettyPipelineGuard = new NettyPipelineGuard(this);

        forceOPGuard = new ForceOPGuard(this);
        forceOPGuard.init();
        getServer().getPluginManager().registerEvents(forceOPGuard, this);

        honeypotGuard = new HoneypotGuard(this);
        getServer().getPluginManager().registerEvents(honeypotGuard, this);

        smartLagGuard = new SmartLagGuard(this);
        getServer().getPluginManager().registerEvents(smartLagGuard, this);

        eventGuard = new EventGuard(this);
        getServer().getPluginManager().registerEvents(eventGuard, this);

        lagGuard = new LagGuard(this);
        getServer().getPluginManager().registerEvents(lagGuard, this);

        patcherGuard = new PatcherGuard(this);
        getServer().getPluginManager().registerEvents(patcherGuard, this);

        getServer().getPluginManager().registerEvents(nettyPipelineGuard, this);

        authSecurityGuard = new AuthSecurityGuard(this);
        getServer().getPluginManager().registerEvents(authSecurityGuard, this);

        guardCodeManager = new GuardCodeManager(this);
        guardCodeManager.load();
        getServer().getPluginManager().registerEvents(guardCodeManager, this);

        guardGui = new GuardGui(this);
        getServer().getPluginManager().registerEvents(guardGui, this);

        if (getServer().getPluginManager().getPlugin("ProtocolLib") != null) {
            try {
                packetGuard = new PacketGuard(this);
                packetGuard.register();
            } catch (Exception e) {
                getLogger().log(Level.WARNING, "ProtocolLib initialization error:", e);
            }
        } else {
            getLogger().warning("ProtocolLib not detected. Low-level packet filters disabled.");
        }

        Bukkit.getScheduler().runTaskTimer(this, () -> smartLagGuard.updateTpsStatus(), 40L, 40L);

        Bukkit.getScheduler().runTaskTimerAsynchronously(this, () -> {
            antiBotManager.cleanupOldEntries();
            threatIntelligence.cleanupOldData();
            eventGuard.cleanupOldData();
            lagGuard.cleanupOldData();
            clickAnalysisGuard.cleanup();
            fastInteractGuard.cleanup();
        }, 1200L, 1200L);

        if (getCommand("tcguard") != null) getCommand("tcguard").setExecutor(this);
        if (getCommand("guard") != null) getCommand("guard").setExecutor(this);
        if (getCommand("guard-add") != null) getCommand("guard-add").setExecutor(this);
        if (getCommand("guard-remove") != null) getCommand("guard-remove").setExecutor(this);
        if (getCommand("guard-list") != null) getCommand("guard-list").setExecutor(this);
        if (getCommand("guardcode") != null) getCommand("guardcode").setExecutor(this);
        if (getCommand("guardcode-reset") != null) getCommand("guardcode-reset").setExecutor(this);

        getLogger().info("TCGuard Enterprise v" + getDescription().getVersion() + " Enabled. Language: " + configManager.getLanguageManager().getCurrentLanguage().toUpperCase());
    }

    @Override
    public void onDisable() {
        if (nettyPipelineGuard != null) nettyPipelineGuard.uninjectAll();
        if (databaseManager != null) databaseManager.close();
        if (rateLimiter != null) rateLimiter.clearAll();
        if (antiBotManager != null) antiBotManager.clear();
        if (threatIntelligence != null) threatIntelligence.clearAll();
        getLogger().info("TCGuard disabled.");
    }

    public static TCGuard getInstance() {
        return instance;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        String name = cmd.getName().toLowerCase();
        LanguageManager lang = configManager.getLanguageManager();

        if (name.equals("guard-add")) {
            if (!(sender instanceof ConsoleCommandSender)) {
                sender.sendMessage(lang.get("only_console"));
                return true;
            }
            if (args.length < 1) {
                sender.sendMessage("§cUsage: /guard-add <username>");
                return true;
            }
            String target = args[0];
            if (!adminManager.isValidUsername(target)) {
                sender.sendMessage(lang.get("invalid_username"));
                return true;
            }
            if (adminManager.addAdmin(target)) {
                sender.sendMessage(lang.get("admin_added").replace("{player}", target));
            } else {
                sender.sendMessage(lang.get("admin_already_exists").replace("{player}", target));
            }
            return true;
        }

        if (name.equals("guard-remove")) {
            if (!(sender instanceof ConsoleCommandSender)) {
                sender.sendMessage(lang.get("only_console"));
                return true;
            }
            if (args.length < 1) {
                sender.sendMessage("§cUsage: /guard-remove <username>");
                return true;
            }
            String target = args[0];
            if (adminManager.removeAdmin(target)) {
                sender.sendMessage(lang.get("admin_removed").replace("{player}", target));
            } else {
                sender.sendMessage(lang.get("admin_not_found").replace("{player}", target));
            }
            return true;
        }

        if (name.equals("guard-list")) {
            if (!(sender instanceof ConsoleCommandSender)) {
                sender.sendMessage(lang.get("only_console"));
                return true;
            }
            Set<String> admins = adminManager.getAuthorizedAdmins();
            sender.sendMessage(lang.get("admin_list_header").replace("{count}", String.valueOf(admins.size())));
            for (String adm : admins) {
                sender.sendMessage("§8» §a" + adm);
            }
            return true;
        }

        if (name.equals("guardcode") || name.equals("gcode") || name.equals("tcguardcode") || name.equals("2fa")) {
            if (!(sender instanceof Player)) {
                sender.sendMessage(lang.get("only_players"));
                return true;
            }
            Player player = (Player) sender;
            boolean isOp = player.isOp();
            boolean isAdmin = adminManager.isPlayerAdmin(player);

            if (!isOp && !isAdmin) {
                sender.sendMessage(lang.get("no_permission"));
                return true;
            }

            if (args.length == 0) {
                if (!guardCodeManager.hasCodeSet(player.getName())) {
                    sender.sendMessage(lang.get("guardcode_prompt_set"));
                } else {
                    sender.sendMessage(lang.get("guardcode_prompt_enter"));
                }
                return true;
            }

            if (args[0].equalsIgnoreCase("reset") || args[0].equalsIgnoreCase("sifirla") || args[0].equalsIgnoreCase("sıfırla")) {
                sender.sendMessage(lang.get("only_console"));
                return true;
            }

            if (args[0].equalsIgnoreCase("set")) {
                if (args.length < 2) {
                    sender.sendMessage(lang.get("guardcode_prompt_set"));
                    return true;
                }
                if (guardCodeManager.hasCodeSet(player.getName()) && !guardCodeManager.isGuardCodeRequired(player)) {
                    sender.sendMessage(lang.get("guardcode_already_set"));
                    return true;
                }
                String pin = args[1];
                if (pin.length() < 4) {
                    sender.sendMessage("§8[§cTCG-Security§8] §cGuardCode must be at least 4 characters long.");
                    return true;
                }
                guardCodeManager.setCode(player.getName(), pin);
                guardCodeManager.verifyCode(player, pin);
                sender.sendMessage(lang.get("guardcode_set_success"));
                Compatibility.playSound(player, 1.0f, 1.5f, "LEVEL_UP", "ENTITY_PLAYER_LEVELUP");
                return true;
            }

            String pin = args[0];
            if (guardCodeManager.verifyCode(player, pin)) {
                sender.sendMessage(lang.get("guardcode_verify_success"));
                Compatibility.playSound(player, 1.0f, 1.4f, "ORB_PICKUP", "ENTITY_EXPERIENCE_ORB_PICKUP");
            } else {
                sender.sendMessage(lang.get("guardcode_wrong"));
                Compatibility.playSound(player, 1.0f, 0.7f, "ANVIL_USE", "BLOCK_ANVIL_USE");
            }
            return true;
        }

        if (name.equals("guardcode-reset") || name.equals("gcode-reset") || name.equals("resetcode") || name.equals("tcguardcode-reset")) {
            if (!(sender instanceof ConsoleCommandSender)) {
                sender.sendMessage(lang.get("only_console"));
                return true;
            }
            if (args.length < 1) {
                sender.sendMessage("§cUsage: /guardcode-reset <username>");
                return true;
            }
            String target = args[0];
            if (guardCodeManager.resetCode(target)) {
                sender.sendMessage(lang.get("guardcode_reset_success").replace("{player}", target));
            } else {
                sender.sendMessage("§8[§cTCG-Security§8] §cPlayer §e" + target + " §cdoes not have a registered GuardCode.");
            }
            return true;
        }

        if (name.equals("tcguard") || name.equals("guard")) {
            if (args.length > 0 && (args[0].equalsIgnoreCase("resetcode") || args[0].equalsIgnoreCase("codereset") || args[0].equalsIgnoreCase("guardcode-reset"))) {
                if (!(sender instanceof ConsoleCommandSender)) {
                    sender.sendMessage(lang.get("only_console"));
                    return true;
                }
                if (args.length < 2) {
                    sender.sendMessage("§cUsage: /guard resetcode <username>");
                    return true;
                }
                String target = args[1];
                if (guardCodeManager.resetCode(target)) {
                    sender.sendMessage(lang.get("guardcode_reset_success").replace("{player}", target));
                } else {
                    sender.sendMessage("§8[§cTCG-Security§8] §cPlayer §e" + target + " §cdoes not have a registered GuardCode.");
                }
                return true;
            }

            if (!adminManager.isAdmin(sender)) {
                sender.sendMessage(lang.get("no_permission"));
                return true;
            }

            if (args.length > 0 && args[0].equalsIgnoreCase("gui")) {
                if (!(sender instanceof Player)) {
                    sender.sendMessage(lang.get("only_players"));
                    return true;
                }
                guardGui.openGui((Player) sender);
                return true;
            }

            if (args.length > 0 && args[0].equalsIgnoreCase("reload")) {
                configManager.load();
                adminManager.load();
                antiBotManager.updatePattern();
                if (forceOPGuard != null) forceOPGuard.loadWhitelist();
                sender.sendMessage(lang.get("config_reloaded"));
                return true;
            }

            if (args.length > 1 && (args[0].equalsIgnoreCase("history") || args[0].equalsIgnoreCase("gecmis"))) {
                String targetPlayer = args[1];
                sender.sendMessage("§8[§eTCG-Security§8] §eSearching history for " + targetPlayer + "...");
                Bukkit.getScheduler().runTaskAsynchronously(this, () -> {
                    java.util.List<DatabaseManager.LogEntry> history = databaseManager.queryPlayerHistory(targetPlayer, 8);
                    if (history.isEmpty()) {
                        sender.sendMessage("§8[§aTCG-Security§8] §aNo security violations recorded for " + targetPlayer + ".");
                        return;
                    }
                    sender.sendMessage("§8§m----------------------------------------------------");
                    sender.sendMessage("§8[§c§lTCG-Security§8] §e" + targetPlayer + " Violation History (" + history.size() + "):");
                    for (DatabaseManager.LogEntry entry : history) {
                        sender.sendMessage("§8» §7[" + entry.formattedTime + "] §c" + entry.threatType + " §8| §f" + entry.details);
                    }
                    sender.sendMessage("§8§m----------------------------------------------------");
                });
                return true;
            }

            if (args.length > 0 && (args[0].equalsIgnoreCase("logs") || args[0].equalsIgnoreCase("loglar"))) {
                int limit = 8;
                if (args.length > 1) {
                    try { limit = Math.min(30, Integer.parseInt(args[1])); } catch (Exception ignored) {}
                }
                final int finalLimit = limit;
                sender.sendMessage("§8[§eTCG-Security§8] §eListing last " + finalLimit + " logs...");
                Bukkit.getScheduler().runTaskAsynchronously(this, () -> {
                    java.util.List<DatabaseManager.LogEntry> logs = databaseManager.queryRecentLogs(finalLimit);
                    if (logs.isEmpty()) {
                        sender.sendMessage("§8[§aTCG-Security§8] §aNo security logs recorded yet.");
                        return;
                    }
                    sender.sendMessage("§8§m----------------------------------------------------");
                    sender.sendMessage("§8[§6§lTCG-Security§8] §eRecent Security Incidents:");
                    for (DatabaseManager.LogEntry entry : logs) {
                        sender.sendMessage("§8» §7[" + entry.formattedTime + "] §e" + entry.playerName + " §8- §c" + entry.threatType + " §8(§7" + entry.details + "§8)");
                    }
                    sender.sendMessage("§8§m----------------------------------------------------");
                });
                return true;
            }

            if (args.length > 0 && args[0].equalsIgnoreCase("stats")) {
                sender.sendMessage("§8§m----------------------------------------------------");
                sender.sendMessage("§8[§6§lTCG-Security§8] §eSecurity Statistics");
                sender.sendMessage("§8» §fBlocked Crashes: §c" + statsManager.getBlockedCrashes());
                sender.sendMessage("§8» §fBlocked Bots: §c" + statsManager.getBlockedBots());
                sender.sendMessage("§8» §fPacket Floods: §c" + statsManager.getBlockedPacketFloods());
                sender.sendMessage("§8» §fIllegal NBT: §c" + statsManager.getBlockedNbtExploits());
                sender.sendMessage("§8» §fRedstone Lags: §c" + statsManager.getBlockedRedstoneLags());
                sender.sendMessage("§8» §fAttack Mode: " + (threatIntelligence.isUnderAttack() ? "§c🚨 ACTIVE" : "§a🟢 Normal"));
                sender.sendMessage("§8» §fTotal Blocked: §e" + statsManager.getTotalBlocked());
                sender.sendMessage("§8§m----------------------------------------------------");
                return true;
            }

            sender.sendMessage("§8§m----------------------------------------------------");
            sender.sendMessage("§8[§6§lTCG-Security§8] §eTCGuard Enterprise Dashboard");
            sender.sendMessage("§8» §fVersion: §a1." + Compatibility.getServerVersion() + " §7(Universal)");
            sender.sendMessage("§8» §fLanguage: §e" + lang.getCurrentLanguage().toUpperCase());
            sender.sendMessage("§8» §fMax Packets/s: §e" + configManager.getMaxPacketsPerSecond());
            sender.sendMessage("§8» §fAnti-Bot: " + (configManager.isAntiBotEnabled() ? "§aActive" : "§cDisabled"));
            sender.sendMessage("§8» §fForce-OP Guard: " + (getConfig().getBoolean("force-op-protection.enabled", true) ? "§aActive" : "§cDisabled"));
            sender.sendMessage("§8» §fAttack Mode: " + (threatIntelligence.isUnderAttack() ? "§c🚨 ACTIVE" : "§aNormal"));
            sender.sendMessage("§8» §fTotal Blocked: §c" + statsManager.getTotalBlocked());
            sender.sendMessage("§8» §e/guard gui §8| §e/guard reload §8| §e/guard stats");
            sender.sendMessage("§8§m----------------------------------------------------");
            return true;
        }

        return false;
    }

    public void kickPlayer(Player player, String reason) {
        if (player == null || !player.isOnline()) return;
        if (proxyBridge != null && getConfig().getBoolean("proxy-bridge.kick-from-network", true)) {
            proxyBridge.kickFromNetwork(player.getName(), reason);
        }
        Bukkit.getScheduler().runTask(this, () -> player.kickPlayer(reason));
    }

    public void alertAdmins(String msg) {
        String formatted = "§8[§cTCG-Security§8] §7" + msg;
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (adminManager.isPlayerAdmin(p)) {
                p.sendMessage(formatted);
            }
        }
        getLogger().info(ChatColor.stripColor(formatted));

        if (proxyBridge != null && getConfig().getBoolean("proxy-bridge.broadcast-staff-alerts", true)) {
            proxyBridge.broadcastNetworkAlert(ChatColor.stripColor(msg));
        }
    }

    public ConfigManager getConfigManager() { return configManager; }
    public AdminManager getAdminManager() { return adminManager; }
    public RateLimiter getRateLimiter() { return rateLimiter; }
    public NbtValidator getNbtValidator() { return nbtValidator; }
    public AntiBotManager getAntiBotManager() { return antiBotManager; }
    public ThreatIntelligence getThreatIntelligence() { return threatIntelligence; }
    public DatabaseManager getDatabaseManager() { return databaseManager; }
    public ProxyBridge getProxyBridge() { return proxyBridge; }
    public ForceOPGuard getForceOPGuard() { return forceOPGuard; }
    public HoneypotGuard getHoneypotGuard() { return honeypotGuard; }
    public SmartLagGuard getSmartLagGuard() { return smartLagGuard; }
    public StatsManager getStatsManager() { return statsManager; }
    public DiscordWebhook getDiscordWebhook() { return discordWebhook; }
    public PatcherGuard getPatcherGuard() { return patcherGuard; }
    public NettyPipelineGuard getNettyPipelineGuard() { return nettyPipelineGuard; }
    public ClickAnalysisGuard getClickAnalysisGuard() { return clickAnalysisGuard; }
    public FastInteractGuard getFastInteractGuard() { return fastInteractGuard; }
    public AuthSecurityGuard getAuthSecurityGuard() { return authSecurityGuard; }
    public GuardCodeManager getGuardCodeManager() { return guardCodeManager; }
}
