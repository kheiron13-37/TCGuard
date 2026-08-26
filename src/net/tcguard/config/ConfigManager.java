package net.tcguard.config;

import net.tcguard.TCGuard;
import net.tcguard.util.ColorUtil;
import org.bukkit.configuration.file.FileConfiguration;

import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.util.*;

public class ConfigManager {

    private final TCGuard plugin;
    private LanguageManager languageManager;

    private int maxPacketsPerSecond;
    private int maxCustomPayloadSize;
    private boolean blockTabComplete;
    private boolean blockCreativePackets;
    private boolean checkInvalidPositions;
    private boolean checkVehicleMovement;
    private boolean checkWindowClicks;
    private Map<String, Integer> packetSpecificLimits = new HashMap<>();

    private int maxNbtDepth;
    private int maxItemNbtSize;
    private int maxBookPages;
    private int maxBookPageLength;
    private int maxSignLineLength;
    private boolean blockTranslationCrash;
    private boolean blockIllegalItems;
    private int maxItemNameLength;
    private int maxItemLoreLines;
    private int maxItemEnchants;

    private boolean antiBotEnabled;
    private int maxJoinsPerIpPerMinute;
    private int maxGlobalJoinsPerSecond;
    private boolean enforceProxyOnly;
    private List<String> allowedProxyIps;
    private String nameRegex;
    private boolean blockInvisibleNames;

    private boolean reachProtectionEnabled;
    private double maxInteractionDistanceSquared;

    private boolean blockColonSyntax;
    private boolean blockProbeCommands;
    private boolean useCommandWhitelist;
    private Set<String> forbiddenCommands = new HashSet<>();
    private Set<String> allowedCommands = new HashSet<>();
    private long commandCooldownMs;
    private String unknownCommandMsg;

    private int maxChatPerSecond;
    private int maxChatLength;
    private boolean blockChatTranslationExploit;

    private boolean discordProxyEnabled;
    private String discordProxyType;
    private String discordProxyHost;
    private int discordProxyPort;
    private String discordCustomUrl;

    private String alertPermission;
    private String bypassPermission;

    private String prefix;
    private String kickMessageExploit;
    private String kickMessageBot;
    private String kickMessageSpam;
    private String kickMessagePacketFlood;
    private String kickMessageIllegalItem;

    public ConfigManager(TCGuard plugin) {
        this.plugin = plugin;
        this.languageManager = new LanguageManager(plugin);
    }

    public void load() {
        File configFile = new File(plugin.getDataFolder(), "config.yml");
        if (!configFile.exists()) {
            if (!plugin.getDataFolder().exists()) {
                plugin.getDataFolder().mkdirs();
            }
            saveLocalizedConfigFile("en");
        }

        plugin.reloadConfig();
        FileConfiguration config = plugin.getConfig();

        languageManager.load();

        maxPacketsPerSecond = config.getInt("packet-protection.max-packets-per-second", 300);
        maxCustomPayloadSize = config.getInt("packet-protection.max-custom-payload-size", 32767);
        blockTabComplete = config.getBoolean("packet-protection.block-tab-complete", true);
        blockCreativePackets = config.getBoolean("packet-protection.block-creative-packets", true);
        checkInvalidPositions = config.getBoolean("packet-protection.check-invalid-positions", true);
        checkVehicleMovement = config.getBoolean("packet-protection.check-vehicle-movement", true);
        checkWindowClicks = config.getBoolean("packet-protection.check-window-clicks", true);

        packetSpecificLimits.clear();
        if (config.isConfigurationSection("packet-protection.limits")) {
            for (String key : config.getConfigurationSection("packet-protection.limits").getKeys(false)) {
                packetSpecificLimits.put(key.toUpperCase(), config.getInt("packet-protection.limits." + key));
            }
        }

        maxNbtDepth = config.getInt("nbt-protection.max-nbt-depth", 16);
        maxItemNbtSize = config.getInt("nbt-protection.max-item-nbt-size", 65536);
        maxBookPages = config.getInt("nbt-protection.max-book-pages", 50);
        maxBookPageLength = config.getInt("nbt-protection.max-book-page-length", 256);
        maxSignLineLength = config.getInt("nbt-protection.max-sign-line-length", 45);
        blockTranslationCrash = config.getBoolean("nbt-protection.block-translation-crash", true);
        blockIllegalItems = config.getBoolean("nbt-protection.block-illegal-items", true);
        maxItemNameLength = config.getInt("nbt-protection.max-item-name-length", 128);
        maxItemLoreLines = config.getInt("nbt-protection.max-item-lore-lines", 25);
        maxItemEnchants = config.getInt("nbt-protection.max-item-enchants", 10);

        antiBotEnabled = config.getBoolean("anti-bot.enabled", true);
        maxJoinsPerIpPerMinute = config.getInt("anti-bot.max-joins-per-ip-per-minute", 3);
        maxGlobalJoinsPerSecond = config.getInt("anti-bot.max-global-joins-per-second", 5);
        enforceProxyOnly = config.getBoolean("anti-bot.enforce-proxy-only", false);
        allowedProxyIps = config.getStringList("anti-bot.allowed-proxy-ips");
        if (allowedProxyIps == null || allowedProxyIps.isEmpty()) {
            allowedProxyIps = Arrays.asList("127.0.0.1", "localhost", "192.168.", "10.");
        }
        nameRegex = config.getString("anti-bot.name-regex", "^[a-zA-Z0-9_]{3,16}$");
        blockInvisibleNames = config.getBoolean("anti-bot.block-invisible-names", true);

        reachProtectionEnabled = config.getBoolean("reach-protection.enabled", true);
        maxInteractionDistanceSquared = config.getDouble("reach-protection.max-interaction-distance-squared", 36.0);

        blockColonSyntax = config.getBoolean("command-protection.block-colon-syntax", true);
        blockProbeCommands = config.getBoolean("command-protection.block-probe-commands", true);
        useCommandWhitelist = config.getBoolean("command-protection.use-whitelist", false);
        commandCooldownMs = config.getLong("command-protection.cooldown-ms", 1200L);
        unknownCommandMsg = languageManager.get("command_blocked");

        forbiddenCommands.clear();
        for (String cmd : config.getStringList("command-protection.forbidden-commands")) {
            forbiddenCommands.add(cmd.toLowerCase());
        }

        allowedCommands.clear();
        for (String cmd : config.getStringList("command-protection.allowed-commands")) {
            allowedCommands.add(cmd.toLowerCase());
        }

        maxChatPerSecond = config.getInt("chat-protection.max-chat-per-second", 4);
        maxChatLength = config.getInt("chat-protection.max-chat-length", 256);
        blockChatTranslationExploit = config.getBoolean("chat-protection.block-translation-exploit", true);

        discordProxyEnabled = config.getBoolean("discord-webhook.proxy.enabled", false);
        discordProxyType = config.getString("discord-webhook.proxy.type", "HTTP").toUpperCase();
        discordProxyHost = config.getString("discord-webhook.proxy.host", "127.0.0.1");
        discordProxyPort = config.getInt("discord-webhook.proxy.port", 1080);
        discordCustomUrl = config.getString("discord-webhook.custom-url", "").trim();

        alertPermission = config.getString("security.alert-permission", "tcguard.alerts");
        bypassPermission = config.getString("security.bypass-permission", "tcguard.bypass");

        prefix = languageManager.get("prefix");
        kickMessageExploit = languageManager.get("kick_exploit");
        kickMessageBot = languageManager.get("kick_bot");
        kickMessageSpam = languageManager.get("kick_spam");
        kickMessagePacketFlood = languageManager.get("kick_packet_flood");
        kickMessageIllegalItem = languageManager.get("kick_illegal_item");
    }

    public void saveLocalizedConfigFile(String lang) {
        File configFile = new File(plugin.getDataFolder(), "config.yml");
        String content = getLocalizedConfigTemplate(lang);

        try (PrintWriter writer = new PrintWriter(new FileWriter(configFile))) {
            writer.write(content);
        } catch (Exception e) {
            plugin.getLogger().warning("Error writing localized config.yml: " + e.getMessage());
        }
    }

    private String getLocalizedConfigTemplate(String lang) {
        if ("tr".equalsIgnoreCase(lang)) {
            return getTurkishConfigTemplate();
        } else if ("es".equalsIgnoreCase(lang)) {
            return getSpanishConfigTemplate();
        } else {
            return getEnglishConfigTemplate();
        }
    }

    private String getEnglishConfigTemplate() {
        return "# active language: en (english), tr (turkish), es (spanish)\n" +
                "language: en\n\n" +
                "# login and authentication security\n" +
                "auth-security:\n" +
                "  # enable authentication shield\n" +
                "  enabled: true\n" +
                "  brute-force:\n" +
                "    # limit failed login attempts\n" +
                "    enabled: true\n" +
                "    # max failed attempts before temporary lockout\n" +
                "    max-attempts: 3\n" +
                "    # lockout duration in minutes\n" +
                "    lockout-minutes: 10\n" +
                "  password-strength:\n" +
                "    # block weak and leaked passwords on register\n" +
                "    enabled: true\n" +
                "    # minimum password length\n" +
                "    min-length: 6\n" +
                "  admin-ip-lock:\n" +
                "    # allow admin accounts only from trusted ip addresses\n" +
                "    enabled: false\n" +
                "    trusted-ips:\n" +
                "      - 127.0.0.1\n\n" +
                "# packet and exploit filtering\n" +
                "packet-protection:\n" +
                "  # max incoming packets per second per player\n" +
                "  max-packets-per-second: 300\n" +
                "  # max custom payload packet byte size\n" +
                "  max-custom-payload-size: 32767\n" +
                "  # block tab complete crash attacks\n" +
                "  block-tab-complete: true\n" +
                "  # prevent survival players from sending creative packets\n" +
                "  block-creative-packets: true\n" +
                "  # validate movement coordinates against nan and infinity\n" +
                "  check-invalid-positions: true\n" +
                "  # check vehicle position packets\n" +
                "  check-vehicle-movement: true\n" +
                "  # check invalid inventory window click packets\n" +
                "  check-window-clicks: true\n" +
                "  # socket level netty exception filter\n" +
                "  netty-pipeline-shield: true\n" +
                "  # packet specific limits per second\n" +
                "  limits:\n" +
                "    ARM_ANIMATION: 35\n" +
                "    HELD_ITEM_SLOT: 25\n" +
                "    WINDOW_CLICK: 30\n" +
                "    CUSTOM_PAYLOAD: 20\n" +
                "    SET_CREATIVE_SLOT: 15\n" +
                "    USE_ENTITY: 25\n" +
                "    USE_ITEM: 25\n" +
                "    BLOCK_PLACE: 30\n" +
                "    BLOCK_DIG: 40\n" +
                "    POSITION: 80\n" +
                "    POSITION_LOOK: 80\n" +
                "    FLYING: 90\n" +
                "    TRANSACTION: 25\n" +
                "    UPDATE_SIGN: 8\n" +
                "  auto-clicker:\n" +
                "    # auto-clicker and macro standard deviation check\n" +
                "    enabled: true\n" +
                "    # max allowed clicks per second\n" +
                "    max-cps: 22\n" +
                "    # minimum click interval variance in ms\n" +
                "    min-standard-deviation: 2.2\n" +
                "  fast-interact:\n" +
                "    # block impossible block break speeds\n" +
                "    block-fast-break: true\n" +
                "    # block impossible fast block place\n" +
                "    block-fast-place: true\n" +
                "    # max block place packets per tick\n" +
                "    max-places-per-tick: 4\n\n" +
                "# nbt item and crash validation\n" +
                "nbt-protection:\n" +
                "  # max nested compound tag depth\n" +
                "  max-nbt-depth: 16\n" +
                "  # max item nbt byte size\n" +
                "  max-item-nbt-size: 65536\n" +
                "  # max book pages\n" +
                "  max-book-pages: 50\n" +
                "  # max characters per book page\n" +
                "  max-book-page-length: 256\n" +
                "  # max sign line length\n" +
                "  max-sign-line-length: 45\n" +
                "  # block translation string crash attempts\n" +
                "  block-translation-crash: true\n" +
                "  # block bedrock, barrier and illegal items in survival\n" +
                "  block-illegal-items: true\n" +
                "  # max item display name length\n" +
                "  max-item-name-length: 128\n" +
                "  # max lore lines\n" +
                "  max-item-lore-lines: 25\n" +
                "  # max enchantments per item\n" +
                "  max-item-enchants: 10\n\n" +
                "# anti-bot and connection rate limits\n" +
                "anti-bot:\n" +
                "  # enable anti-bot protection\n" +
                "  enabled: true\n" +
                "  # max joins per ip per minute\n" +
                "  max-joins-per-ip-per-minute: 3\n" +
                "  # max global joins per second before attack mode\n" +
                "  max-global-joins-per-second: 5\n" +
                "  # allow connections only through proxy ip\n" +
                "  enforce-proxy-only: false\n" +
                "  allowed-proxy-ips:\n" +
                "    - 127.0.0.1\n" +
                "    - localhost\n" +
                "    - 192.168.\n" +
                "    - 10.\n" +
                "  # valid player username regex\n" +
                "  name-regex: '^[a-zA-Z0-9_]{3,16}$'\n" +
                "  # block invisible or zero-width unicode names\n" +
                "  block-invisible-names: true\n" +
                "  timing-protection:\n" +
                "    # identify 0.0ms synthetic bot handshakes\n" +
                "    enabled: true\n" +
                "    # min milliseconds between handshake and login\n" +
                "    min-handshake-duration-ms: 25\n" +
                "  subnet-protection:\n" +
                "    # limit joins from the same /24 ip subnet\n" +
                "    enabled: true\n" +
                "    # max joins per subnet per minute\n" +
                "    max-joins-per-subnet-per-minute: 6\n" +
                "    # max joins per subnet during attack mode\n" +
                "    max-joins-under-attack: 2\n" +
                "  attack-mode:\n" +
                "    # automatically trigger attack mode on join flood\n" +
                "    auto-trigger: true\n" +
                "    # attack mode duration in seconds\n" +
                "    duration-seconds: 60\n\n" +
                "# operator and backdoor protection\n" +
                "force-op-protection:\n" +
                "  # monitor operator snapshots and revoke unauthorized ops\n" +
                "  enabled: true\n" +
                "  # kick unauthorized players trying to get op\n" +
                "  kick-on-unauthorized-op: true\n" +
                "  # whitelisted operator usernames\n" +
                "  whitelisted-ops: []\n\n" +
                "# honeypot trap for exploit probes\n" +
                "honeypot:\n" +
                "  # trap common hacker commands like //calc or /pex\n" +
                "  enabled: true\n" +
                "  # kick player on honeypot trigger\n" +
                "  kick-player: true\n\n" +
                "# emergency anti-lag saver\n" +
                "smart-lag:\n" +
                "  # enable dynamic lag mitigation\n" +
                "  enabled: true\n" +
                "  # tps threshold to enter emergency mode\n" +
                "  emergency-tps-threshold: 16.0\n" +
                "  # tps threshold to restore normal mode\n" +
                "  restore-tps-threshold: 19.0\n" +
                "  # cancel explosions during low tps\n" +
                "  cancel-explosions-under-lag: true\n" +
                "  # limit ground item drops during low tps\n" +
                "  limit-item-drops-under-lag: true\n\n" +
                "# interaction distance checks\n" +
                "reach-protection:\n" +
                "  # block opening containers from excessive distance\n" +
                "  enabled: true\n" +
                "  # max reach distance squared\n" +
                "  max-interaction-distance-squared: 36.0\n\n" +
                "# command leakage and colon syntax protection\n" +
                "command-protection:\n" +
                "  # block commands containing colons like /minecraft:tell\n" +
                "  block-colon-syntax: true\n" +
                "  # block probe commands like /pl, /plugins, /ver\n" +
                "  block-probe-commands: true\n" +
                "  # use strict command whitelist\n" +
                "  use-whitelist: false\n" +
                "  # command cooldown in milliseconds\n" +
                "  cooldown-ms: 1200\n" +
                "  # forbidden probe commands\n" +
                "  forbidden-commands:\n" +
                "    - pl\n" +
                "    - plugins\n" +
                "    - ver\n" +
                "    - version\n" +
                "    - '?'\n" +
                "    - help\n" +
                "    - about\n" +
                "    - icanhasbukkit\n" +
                "  # allowed commands when whitelist is enabled\n" +
                "  allowed-commands:\n" +
                "    - login\n" +
                "    - register\n" +
                "    - l\n" +
                "    - reg\n" +
                "    - msg\n" +
                "    - r\n" +
                "    - spawn\n" +
                "    - tpa\n" +
                "    - tpaccept\n\n" +
                "# chat rate and length protection\n" +
                "chat-protection:\n" +
                "  # max chat messages per second\n" +
                "  max-chat-per-second: 4\n" +
                "  # max chat message length\n" +
                "  max-chat-length: 256\n" +
                "  # block chat translation exploits\n" +
                "  block-translation-exploit: true\n\n" +
                "# redstone and piston lag limiter\n" +
                "lag-protection:\n" +
                "  redstone-limiter:\n" +
                "    # freeze high frequency redstone clocks\n" +
                "    enabled: true\n" +
                "    # max redstone updates per chunk per second\n" +
                "    max-ticks-per-chunk: 200\n" +
                "  piston-limiter:\n" +
                "    # limit fast piston machines\n" +
                "    enabled: true\n" +
                "    # max piston moves per chunk per second\n" +
                "    max-piston-moves-per-chunk: 60\n" +
                "  chunk-entity-limiter:\n" +
                "    # limit entities per chunk\n" +
                "    enabled: true\n" +
                "    # max entities allowed in a single chunk\n" +
                "    max-entities-per-chunk: 100\n\n" +
                "# forensics database settings\n" +
                "database:\n" +
                "  # enable security incident logging\n" +
                "  enabled: true\n" +
                "  # database type: sqlite or mysql\n" +
                "  type: SQLITE\n" +
                "  # log retention in days\n" +
                "  retention-days: 30\n" +
                "  mysql:\n" +
                "    host: localhost\n" +
                "    port: 3306\n" +
                "    database: tcguard\n" +
                "    username: root\n" +
                "    password: ''\n" +
                "    use-ssl: false\n\n" +
                "# network proxy sync (bungeecord / velocity)\n" +
                "proxy-bridge:\n" +
                "  # enable proxy message bridge\n" +
                "  enabled: true\n" +
                "  # kick player across the whole network on violation\n" +
                "  kick-from-network: true\n" +
                "  # broadcast alerts to all servers\n" +
                "  broadcast-staff-alerts: true\n" +
                "  # synchronize attack mode across all servers\n" +
                "  sync-attack-mode-network: true\n\n" +
                "# discord webhook alerts\n" +
                "discord-webhook:\n" +
                "  # send embed alerts to discord\n" +
                "  enabled: false\n" +
                "  # discord webhook url\n" +
                "  url: ''\n" +
                "  proxy:\n" +
                "    # use http/socks proxy for discord requests\n" +
                "    enabled: false\n" +
                "    type: HTTP\n" +
                "    host: 127.0.0.1\n" +
                "    port: 1080\n\n" +
                "# admin guardcode 2fa verification\n" +
                "admin-guardcode:\n" +
                "  # require all op/admin players to verify with a code on each login\n" +
                "  enabled: true\n" +
                "  # max failed verification attempts before kick\n" +
                "  max-failed-attempts: 5\n" +
                "  # send periodic reminders to unverified admins (seconds)\n" +
                "  reminder-interval-seconds: 30\n";
    }

    private String getTurkishConfigTemplate() {
        return "# aktif dil secimi: en (ingilizce), tr (turkce), es (ispanyolca)\n" +
                "language: tr\n\n" +
                "# giris ve kimlik dogrulama guvenligi\n" +
                "auth-security:\n" +
                "  # giris korumasini aktif eder\n" +
                "  enabled: true\n" +
                "  brute-force:\n" +
                "    # hatali sifre denemelerini sinirlar\n" +
                "    enabled: true\n" +
                "    # gecici kilit icin maksimum hatali deneme sayisi\n" +
                "    max-attempts: 3\n" +
                "    # kilit suresi (dakika)\n" +
                "    lockout-minutes: 10\n" +
                "  password-strength:\n" +
                "    # kayit sirasinda zayif ve sizdirilmis sifreleri engeller\n" +
                "    enabled: true\n" +
                "    # minimum sifre uzunlugu\n" +
                "    min-length: 6\n" +
                "  admin-ip-lock:\n" +
                "    # yetkili hesaplara sadece guvenilen ip adreslerinden girilmesini saglar\n" +
                "    enabled: false\n" +
                "    trusted-ips:\n" +
                "      - 127.0.0.1\n\n" +
                "# paket ve exploit filtreleme\n" +
                "packet-protection:\n" +
                "  # oyuncu basina saniyelik maksimum paket sayisi\n" +
                "  max-packets-per-second: 300\n" +
                "  # maksimum ozel payload paket bayt boyutu\n" +
                "  max-custom-payload-size: 32767\n" +
                "  # sekme tamamlama crash saldirilarini engeller\n" +
                "  block-tab-complete: true\n" +
                "  # hayatta kalma modundaki oyuncularin yaratici mod paketi gondermesini engeller\n" +
                "  block-creative-packets: true\n" +
                "  # hareket koordinatlarini nan ve sonsuzluk hatalarina karsi denetler\n" +
                "  check-invalid-positions: true\n" +
                "  # arac hareket paketlerini denetler\n" +
                "  check-vehicle-movement: true\n" +
                "  # gecersiz envanter pencere tiklama paketlerini engeller\n" +
                "  check-window-clicks: true\n" +
                "  # soket seviyesinde netty hata filtresi\n" +
                "  netty-pipeline-shield: true\n" +
                "  # paket basina saniyelik ozel limitler\n" +
                "  limits:\n" +
                "    ARM_ANIMATION: 35\n" +
                "    HELD_ITEM_SLOT: 25\n" +
                "    WINDOW_CLICK: 30\n" +
                "    CUSTOM_PAYLOAD: 20\n" +
                "    SET_CREATIVE_SLOT: 15\n" +
                "    USE_ENTITY: 25\n" +
                "    USE_ITEM: 25\n" +
                "    BLOCK_PLACE: 30\n" +
                "    BLOCK_DIG: 40\n" +
                "    POSITION: 80\n" +
                "    POSITION_LOOK: 80\n" +
                "    FLYING: 90\n" +
                "    TRANSACTION: 25\n" +
                "    UPDATE_SIGN: 8\n" +
                "  auto-clicker:\n" +
                "    # auto-clicker ve makro standart sapma analizi\n" +
                "    enabled: true\n" +
                "    # maksimum izin verilen saniyelik tiklama sayisi (cps)\n" +
                "    max-cps: 22\n" +
                "    # milisaniye cinsinden minimum tiklama aralik sapmasi\n" +
                "    min-standard-deviation: 2.2\n" +
                "  fast-interact:\n" +
                "    # imkansiz hizda blok kirma hilesini engeller\n" +
                "    block-fast-break: true\n" +
                "    # asiri hizli blok koyma hilesini engeller\n" +
                "    block-fast-place: true\n" +
                "    # tick basina maksimum blok koyma paketi\n" +
                "    max-places-per-tick: 4\n\n" +
                "# nbt esya ve crash denetimi\n" +
                "nbt-protection:\n" +
                "  # maksimum ic ice nbt derinligi\n" +
                "  max-nbt-depth: 16\n" +
                "  # maksimum esya nbt bayt boyutu\n" +
                "  max-item-nbt-size: 65536\n" +
                "  # maksimum kitap sayfa sayisi\n" +
                "  max-book-pages: 50\n" +
                "  # kitap sayfasi basina maksimum karakter sayisi\n" +
                "  max-book-page-length: 256\n" +
                "  # tabela satiri basina maksimum karakter sayisi\n" +
                "  max-sign-line-length: 45\n" +
                "  # ceviri dizisi crash denemelerini engeller\n" +
                "  block-translation-crash: true\n" +
                "  # katman kayasi, bariyer gibi yasakli esyalari engeller\n" +
                "  block-illegal-items: true\n" +
                "  # maksimum esya isim uzunlugu\n" +
                "  max-item-name-length: 128\n" +
                "  # maksimum aciklama satiri\n" +
                "  max-item-lore-lines: 25\n" +
                "  # esya basina maksimum buyu sayisi\n" +
                "  max-item-enchants: 10\n\n" +
                "# anti-bot ve baglanti limitleri\n" +
                "anti-bot:\n" +
                "  # anti-bot korumasini aktif eder\n" +
                "  enabled: true\n" +
                "  # ip basina dakikalik maksimum giris sayisi\n" +
                "  max-joins-per-ip-per-minute: 3\n" +
                "  # saldiri moduna gecmeden onceki saniyelik genel giris limiti\n" +
                "  max-global-joins-per-second: 5\n" +
                "  # sadece proxy ip adresinden baglantiya izin verir\n" +
                "  enforce-proxy-only: false\n" +
                "  allowed-proxy-ips:\n" +
                "    - 127.0.0.1\n" +
                "    - localhost\n" +
                "    - 192.168.\n" +
                "    - 10.\n" +
                "  # gecerli oyuncu kullanici adi kurali\n" +
                "  name-regex: '^[a-zA-Z0-9_]{3,16}$'\n" +
                "  # gorunmez veya sifir genislikli karakterleri engeller\n" +
                "  block-invisible-names: true\n" +
                "  timing-protection:\n" +
                "    # 0.0ms yapay bot handshake baglantilarini tespit eder\n" +
                "    enabled: true\n" +
                "    # handshake ile login arasindaki minimum milisaniye\n" +
                "    min-handshake-duration-ms: 25\n" +
                "  subnet-protection:\n" +
                "    # ayni /24 ip blogundan gelen girisleri sinirlar\n" +
                "    enabled: true\n" +
                "    # subnet basina dakikalik maksimum giris\n" +
                "    max-joins-per-subnet-per-minute: 6\n" +
                "    # saldiri modundayken subnet basina izin verilen giris\n" +
                "    max-joins-under-attack: 2\n" +
                "  attack-mode:\n" +
                "    # giris selinde otomatik saldiri modunu baslatir\n" +
                "    auto-trigger: true\n" +
                "    # saldiri modu suresi (saniye)\n" +
                "    duration-seconds: 60\n\n" +
                "# operator ve arka kapi korumasi\n" +
                "force-op-protection:\n" +
                "  # operator anlik goruntusunu izler ve izinsiz op yetkilerini geri alir\n" +
                "  enabled: true\n" +
                "  # izinsiz op olmaya calisan oyunculari sunucudan atar\n" +
                "  kick-on-unauthorized-op: true\n" +
                "  # guvenilen operator kullanici adlari\n" +
                "  whitelisted-ops: []\n\n" +
                "# saldiri denemeleri icin bal kupu tuzagi\n" +
                "honeypot:\n" +
                "  # //calc veya /pex gibi hile komutlarini yakalar\n" +
                "  enabled: true\n" +
                "  # tuzaga dusen oyuncuyu sunucudan atar\n" +
                "  kick-player: true\n\n" +
                "# acil durum anti-lag kurtaricisi\n" +
                "smart-lag:\n" +
                "  # dinamik lag frenini aktif eder\n" +
                "  enabled: true\n" +
                "  # acil durum moduna gecis icin tps esigi\n" +
                "  emergency-tps-threshold: 16.0\n" +
                "  # normal moda donus icin tps esigi\n" +
                "  restore-tps-threshold: 19.0\n" +
                "  # dusuk tps sirasinda patlamalari iptal eder\n" +
                "  cancel-explosions-under-lag: true\n" +
                "  # dusuk tps sirasinda yere dusen esyalari sinirlar\n" +
                "  limit-item-drops-under-lag: true\n\n" +
                "# etkilesim menzili denetimi\n" +
                "reach-protection:\n" +
                "  # asiri uzak mesafeden sandik ve envanter acmayi engeller\n" +
                "  enabled: true\n" +
                "  # maksimum etkilesim mesafesi karesi\n" +
                "  max-interaction-distance-squared: 36.0\n\n" +
                "# komut sizdirma ve kolon syntax korumasi\n" +
                "command-protection:\n" +
                "  # /minecraft:tell gibi iki nokta iceren komutlari engeller\n" +
                "  block-colon-syntax: true\n" +
                "  # /pl, /plugins, /ver gibi eklenti arama komutlarini engeller\n" +
                "  block-probe-commands: true\n" +
                "  # kati komut beyaz listesi kullanir\n" +
                "  use-whitelist: false\n" +
                "  # komutlar arasi bekleme suresi (milisaniye)\n" +
                "  cooldown-ms: 1200\n" +
                "  # yasakli eklenti arama komutlari\n" +
                "  forbidden-commands:\n" +
                "    - pl\n" +
                "    - plugins\n" +
                "    - ver\n" +
                "    - version\n" +
                "    - '?'\n" +
                "    - help\n" +
                "    - about\n" +
                "    - icanhasbukkit\n" +
                "  # beyaz liste aktifken izin verilen komutlar\n" +
                "  allowed-commands:\n" +
                "    - login\n" +
                "    - register\n" +
                "    - l\n" +
                "    - reg\n" +
                "    - msg\n" +
                "    - r\n" +
                "    - spawn\n" +
                "    - tpa\n" +
                "    - tpaccept\n\n" +
                "# sohbet hizi ve uzunluk korumasi\n" +
                "chat-protection:\n" +
                "  # saniyelik maksimum sohbet mesaji\n" +
                "  max-chat-per-second: 4\n" +
                "  # maksimum mesaj uzunlugu\n" +
                "  max-chat-length: 256\n" +
                "  # sohbet ceviri exploitlerini engeller\n" +
                "  block-translation-exploit: true\n\n" +
                "# redstone ve piston lag sinirlayici\n" +
                "lag-protection:\n" +
                "  redstone-limiter:\n" +
                "    # asiri hizli redstone saatlerini dondurur\n" +
                "    enabled: true\n" +
                "    # chunk basina saniyelik maksimum redstone ticki\n" +
                "    max-ticks-per-chunk: 200\n" +
                "  piston-limiter:\n" +
                "    # hizli piston makinelerini sinirlar\n" +
                "    enabled: true\n" +
                "    # chunk basina saniyelik maksimum piston hareketi\n" +
                "    max-piston-moves-per-chunk: 60\n" +
                "  chunk-entity-limiter:\n" +
                "    # chunk basina canli varlik limitini belirler\n" +
                "    enabled: true\n" +
                "    # tek bir chunkta bulunabilecek maksimum varlik\n" +
                "    max-entities-per-chunk: 100\n\n" +
                "# adli bilisim veritabani ayarlari\n" +
                "database:\n" +
                "  # guvenlik kayitlarini veritabanina yazmayi aktif eder\n" +
                "  enabled: true\n" +
                "  # veritabani turu: sqlite veya mysql\n" +
                "  type: SQLITE\n" +
                "  # loglarin saklanma suresi (gun)\n" +
                "  retention-days: 30\n" +
                "  mysql:\n" +
                "    host: localhost\n" +
                "    port: 3306\n" +
                "    database: tcguard\n" +
                "    username: root\n" +
                "    password: ''\n" +
                "    use-ssl: false\n\n" +
                "# ag proxy senkronizasyonu (bungeecord / velocity)\n" +
                "proxy-bridge:\n" +
                "  # proxy mesaj koprusunu aktif eder\n" +
                "  enabled: true\n" +
                "  # ihlalde oyuncuyu tum agdan atar\n" +
                "  kick-from-network: true\n" +
                "  # uyarilari tum sunuculara iletir\n" +
                "  broadcast-staff-alerts: true\n" +
                "  # saldiri modunu tum agda senkronize eder\n" +
                "  sync-attack-mode-network: true\n\n" +
                "# discord webhook bildirimleri\n" +
                "discord-webhook:\n" +
                "  # guvenlik alarmlarini discord kanalina gonderir\n" +
                "  enabled: false\n" +
                "  # discord webhook baglanti adresi\n" +
                "  url: ''\n" +
                "  proxy:\n" +
                "    # discord istekleri icin http/socks proxy kullanir\n" +
                "    enabled: false\n" +
                "    type: HTTP\n" +
                "    host: 127.0.0.1\n" +
                "    port: 1080\n\n" +
                "# yonetici guardcode 2fa dogrulama\n" +
                "admin-guardcode:\n" +
                "  # tum op/yetkili oyuncularin her giriste kod ile dogrulama yapmasini zorunlu kilar\n" +
                "  enabled: true\n" +
                "  # kick oncesi maksimum basarisiz dogrulama denemesi\n" +
                "  max-failed-attempts: 5\n" +
                "  # dogrulanmamis yoneticilere hatirlatma araligi (saniye)\n" +
                "  reminder-interval-seconds: 30\n";
    }

    private String getSpanishConfigTemplate() {
        return "# seleccion de idioma: en (ingles), tr (turco), es (espanol)\n" +
                "language: es\n\n" +
                "# seguridad de autenticacion e inicio de sesion\n" +
                "auth-security:\n" +
                "  # activa la proteccion de autenticacion\n" +
                "  enabled: true\n" +
                "  brute-force:\n" +
                "    # limita los intentos fallidos de contrasena\n" +
                "    enabled: true\n" +
                "    # maximo de intentos fallidos antes del bloqueo temporal\n" +
                "    max-attempts: 3\n" +
                "    # duracion del bloqueo en minutos\n" +
                "    lockout-minutes: 10\n" +
                "  password-strength:\n" +
                "    # bloquea contrasenas debiles y filtradas al registrarse\n" +
                "    enabled: true\n" +
                "    # longitud minima de contrasena\n" +
                "    min-length: 6\n" +
                "  admin-ip-lock:\n" +
                "    # permite cuentas de administrador solo desde ips de confianza\n" +
                "    enabled: false\n" +
                "    trusted-ips:\n" +
                "      - 127.0.0.1\n\n" +
                "# filtrado de paquetes y exploits\n" +
                "packet-protection:\n" +
                "  # paquetes maximos por segundo por jugador\n" +
                "  max-packets-per-second: 300\n" +
                "  # tamano maximo del paquete de carga util personalizada\n" +
                "  max-custom-payload-size: 32767\n" +
                "  # bloquea ataques de bloqueo por autocompletar\n" +
                "  block-tab-complete: true\n" +
                "  # evita que jugadores de supervivencia envien paquetes creativos\n" +
                "  block-creative-packets: true\n" +
                "  # valida coordenadas de movimiento contra nan e infinito\n" +
                "  check-invalid-positions: true\n" +
                "  # comprueba paquetes de movimiento de vehiculos\n" +
                "  check-vehicle-movement: true\n" +
                "  # comprueba paquetes invalidos de clics en ventanas\n" +
                "  check-window-clicks: true\n" +
                "  # filtro de excepciones netty a nivel de socket\n" +
                "  netty-pipeline-shield: true\n" +
                "  # limites especificos de paquetes por segundo\n" +
                "  limits:\n" +
                "    ARM_ANIMATION: 35\n" +
                "    HELD_ITEM_SLOT: 25\n" +
                "    WINDOW_CLICK: 30\n" +
                "    CUSTOM_PAYLOAD: 20\n" +
                "    SET_CREATIVE_SLOT: 15\n" +
                "    USE_ENTITY: 25\n" +
                "    USE_ITEM: 25\n" +
                "    BLOCK_PLACE: 30\n" +
                "    BLOCK_DIG: 40\n" +
                "    POSITION: 80\n" +
                "    POSITION_LOOK: 80\n" +
                "    FLYING: 90\n" +
                "    TRANSACTION: 25\n" +
                "    UPDATE_SIGN: 8\n" +
                "  auto-clicker:\n" +
                "    # verificacion de desviacion estandar de macros\n" +
                "    enabled: true\n" +
                "    # clics maximos permitidos por segundo (cps)\n" +
                "    max-cps: 22\n" +
                "    # desviacion minima del intervalo de clic en ms\n" +
                "    min-standard-deviation: 2.2\n" +
                "  fast-interact:\n" +
                "    # bloquea velocidades imposibles de rotura de bloques\n" +
                "    block-fast-break: true\n" +
                "    # bloquea colocacion rapida de bloques\n" +
                "    block-fast-place: true\n" +
                "    # paquetes maximos de colocacion de bloques por tick\n" +
                "    max-places-per-tick: 4\n\n" +
                "# validacion de nbt y objetos ilegales\n" +
                "nbt-protection:\n" +
                "  # profundidad maxima de etiquetas nbt anidadas\n" +
                "  max-nbt-depth: 16\n" +
                "  # tamano maximo de nbt del objeto en bytes\n" +
                "  max-item-nbt-size: 65536\n" +
                "  # paginas maximas del libro\n" +
                "  max-book-pages: 50\n" +
                "  # caracteres maximos por pagina del libro\n" +
                "  max-book-page-length: 256\n" +
                "  # longitud maxima de linea de senal\n" +
                "  max-sign-line-length: 45\n" +
                "  # bloquea intentos de crasheo por cadenas de traduccion\n" +
                "  block-translation-crash: true\n" +
                "  # bloquea objetos ilegales como piedra base y barreras\n" +
                "  block-illegal-items: true\n" +
                "  # longitud maxima del nombre del objeto\n" +
                "  max-item-name-length: 128\n" +
                "  # lineas maximas de descripcion\n" +
                "  max-item-lore-lines: 25\n" +
                "  # encantamientos maximos por objeto\n" +
                "  max-item-enchants: 10\n\n" +
                "# proteccion anti-bot y limites de conexion\n" +
                "anti-bot:\n" +
                "  # activa la proteccion anti-bot\n" +
                "  enabled: true\n" +
                "  # conexiones maximas por ip por minuto\n" +
                "  max-joins-per-ip-per-minute: 3\n" +
                "  # conexiones globales por segundo antes del modo de ataque\n" +
                "  max-global-joins-per-second: 5\n" +
                "  # permite conexiones solo a traves de ip proxy\n" +
                "  enforce-proxy-only: false\n" +
                "  allowed-proxy-ips:\n" +
                "    - 127.0.0.1\n" +
                "    - localhost\n" +
                "    - 192.168.\n" +
                "    - 10.\n" +
                "  # expresion regular de nombre de usuario valido\n" +
                "  name-regex: '^[a-zA-Z0-9_]{3,16}$'\n" +
                "  # bloquea nombres invisibles o con caracteres de ancho cero\n" +
                "  block-invisible-names: true\n" +
                "  timing-protection:\n" +
                "    # detecta handshakes de bots sinteticos de 0.0ms\n" +
                "    enabled: true\n" +
                "    # milisegundos minimos entre handshake e inicio de sesion\n" +
                "    min-handshake-duration-ms: 25\n" +
                "  subnet-protection:\n" +
                "    # limita conexiones de la misma subred ip /24\n" +
                "    enabled: true\n" +
                "    # conexiones maximas por subred por minuto\n" +
                "    max-joins-per-subnet-per-minute: 6\n" +
                "    # conexiones maximas por subred en modo de ataque\n" +
                "    max-joins-under-attack: 2\n" +
                "  attack-mode:\n" +
                "    # activa automaticamente el modo de ataque ante oleadas de bots\n" +
                "    auto-trigger: true\n" +
                "    # duracion del modo de ataque en segundos\n" +
                "    duration-seconds: 60\n\n" +
                "# proteccion de operadores y puertas traseras\n" +
                "force-op-protection:\n" +
                "  # supervisa operadores y revoca permisos no autorizados\n" +
                "  enabled: true\n" +
                "  # expulsa a jugadores que intentan obtener op ilegalmente\n" +
                "  kick-on-unauthorized-op: true\n" +
                "  # nombres de usuario de operadores autorizados\n" +
                "  whitelisted-ops: []\n\n" +
                "# trampa para comandos de exploit (honeypot)\n" +
                "honeypot:\n" +
                "  # captura comandos de hackers como //calc o /pex\n" +
                "  enabled: true\n" +
                "  # expulsa al jugador al activar la trampa\n" +
                "  kick-player: true\n\n" +
                "# ahorro de lag de emergencia\n" +
                "smart-lag:\n" +
                "  # activa el freno dinamico de lag\n" +
                "  enabled: true\n" +
                "  # umbral de tps para activar el modo de emergencia\n" +
                "  emergency-tps-threshold: 16.0\n" +
                "  # umbral de tps para restaurar el modo normal\n" +
                "  restore-tps-threshold: 19.0\n" +
                "  # cancela explosiones con tps bajo\n" +
                "  cancel-explosions-under-lag: true\n" +
                "  # limita objetos en el suelo con tps bajo\n" +
                "  limit-item-drops-under-lag: true\n\n" +
                "# comprobacion de distancia de interaccion\n" +
                "reach-protection:\n" +
                "  # bloquea la apertura de contenedores a distancia excesiva\n" +
                "  enabled: true\n" +
                "  # distancia de interaccion maxima al cuadrado\n" +
                "  max-interaction-distance-squared: 36.0\n\n" +
                "# proteccion de sintaxis de dos puntos y sondeo de comandos\n" +
                "command-protection:\n" +
                "  # bloquea comandos con dos puntos como /minecraft:tell\n" +
                "  block-colon-syntax: true\n" +
                "  # bloquea comandos de sondeo como /pl, /plugins, /ver\n" +
                "  block-probe-commands: true\n" +
                "  # usa lista blanca estricta de comandos\n" +
                "  use-whitelist: false\n" +
                "  # tiempo de espera entre comandos en milisegundos\n" +
                "  cooldown-ms: 1200\n" +
                "  # comandos de sondeo prohibidos\n" +
                "  forbidden-commands:\n" +
                "    - pl\n" +
                "    - plugins\n" +
                "    - ver\n" +
                "    - version\n" +
                "    - '?'\n" +
                "    - help\n" +
                "    - about\n" +
                "    - icanhasbukkit\n" +
                "  # comandos permitidos con lista blanca activa\n" +
                "  allowed-commands:\n" +
                "    - login\n" +
                "    - register\n" +
                "    - l\n" +
                "    - reg\n" +
                "    - msg\n" +
                "    - r\n" +
                "    - spawn\n" +
                "    - tpa\n" +
                "    - tpaccept\n\n" +
                "# proteccion de chat y longitud de mensajes\n" +
                "chat-protection:\n" +
                "  # mensajes de chat maximos por segundo\n" +
                "  max-chat-per-second: 4\n" +
                "  # longitud maxima del mensaje de chat\n" +
                "  max-chat-length: 256\n" +
                "  # bloquea exploits de traduccion en el chat\n" +
                "  block-translation-exploit: true\n\n" +
                "# limitador de lag por redstone y pistones\n" +
                "lag-protection:\n" +
                "  redstone-limiter:\n" +
                "    # congela relojes de redstone de alta frecuencia\n" +
                "    enabled: true\n" +
                "    # actualizaciones de redstone maximas por chunk por segundo\n" +
                "    max-ticks-per-chunk: 200\n" +
                "  piston-limiter:\n" +
                "    # limita maquinas rapidas de pistones\n" +
                "    enabled: true\n" +
                "    # movimientos de piston maximos por chunk por segundo\n" +
                "    max-piston-moves-per-chunk: 60\n" +
                "  chunk-entity-limiter:\n" +
                "    # limita entidades por chunk\n" +
                "    enabled: true\n" +
                "    # entidades maximas permitidas en un solo chunk\n" +
                "    max-entities-per-chunk: 100\n\n" +
                "# configuracion de base de datos forense\n" +
                "database:\n" +
                "  # activa el registro de incidentes en la base de datos\n" +
                "  enabled: true\n" +
                "  # tipo de base de datos: sqlite o mysql\n" +
                "  type: SQLITE\n" +
                "  # dias de retencion de registros\n" +
                "  retention-days: 30\n" +
                "  mysql:\n" +
                "    host: localhost\n" +
                "    port: 3306\n" +
                "    database: tcguard\n" +
                "    username: root\n" +
                "    password: ''\n" +
                "    use-ssl: false\n\n" +
                "# sincronizacion de red proxy (bungeecord / velocity)\n" +
                "proxy-bridge:\n" +
                "  # activa el puente de mensajes de proxy\n" +
                "  enabled: true\n" +
                "  # expulsa al jugador de toda la red ante infracciones\n" +
                "  kick-from-network: true\n" +
                "  # retransmite alertas a todos los servidores\n" +
                "  broadcast-staff-alerts: true\n" +
                "  # sincroniza el modo de ataque en toda la red\n" +
                "  sync-attack-mode-network: true\n\n" +
                "# alertas de webhook de discord\n" +
                "discord-webhook:\n" +
                "  # envia alertas a un canal de discord\n" +
                "  enabled: false\n" +
                "  # url del webhook de discord\n" +
                "  url: ''\n" +
                "  proxy:\n" +
                "    # usa proxy http/socks para solicitudes a discord\n" +
                "    enabled: false\n" +
                "    type: HTTP\n" +
                "    host: 127.0.0.1\n" +
                "    port: 1080\n\n" +
                "# verificacion 2fa de guardcode para administradores\n" +
                "admin-guardcode:\n" +
                "  # exige que todos los operadores/administradores verifiquen con un codigo al conectarse\n" +
                "  enabled: true\n" +
                "  # intentos fallidos maximos antes de expulsar\n" +
                "  max-failed-attempts: 5\n" +
                "  # intervalo de recordatorios para administradores no verificados (segundos)\n" +
                "  reminder-interval-seconds: 30\n";
    }

    public LanguageManager getLanguageManager() { return languageManager; }

    public int getMaxPacketsPerSecond() { return maxPacketsPerSecond; }
    public int getMaxCustomPayloadSize() { return maxCustomPayloadSize; }
    public boolean isBlockTabComplete() { return blockTabComplete; }
    public boolean isBlockCreativePackets() { return blockCreativePackets; }
    public boolean isCheckInvalidPositions() { return checkInvalidPositions; }
    public boolean isCheckVehicleMovement() { return checkVehicleMovement; }
    public boolean isCheckWindowClicks() { return checkWindowClicks; }
    public Map<String, Integer> getPacketSpecificLimits() { return packetSpecificLimits; }

    public int getMaxNbtDepth() { return maxNbtDepth; }
    public int getMaxItemNbtSize() { return maxItemNbtSize; }
    public int getMaxBookPages() { return maxBookPages; }
    public int getMaxBookPageLength() { return maxBookPageLength; }
    public int getMaxSignLineLength() { return maxSignLineLength; }
    public boolean isBlockTranslationCrash() { return blockTranslationCrash; }
    public boolean isBlockIllegalItems() { return blockIllegalItems; }
    public int getMaxItemNameLength() { return maxItemNameLength; }
    public int getMaxItemLoreLines() { return maxItemLoreLines; }
    public int getMaxItemEnchants() { return maxItemEnchants; }

    public boolean isAntiBotEnabled() { return antiBotEnabled; }
    public int getMaxJoinsPerIpPerMinute() { return maxJoinsPerIpPerMinute; }
    public int getMaxGlobalJoinsPerSecond() { return maxGlobalJoinsPerSecond; }
    public boolean isEnforceProxyOnly() { return enforceProxyOnly; }
    public List<String> getAllowedProxyIps() { return allowedProxyIps; }
    public String getNameRegex() { return nameRegex; }
    public boolean isBlockInvisibleNames() { return blockInvisibleNames; }

    public boolean isReachProtectionEnabled() { return reachProtectionEnabled; }
    public double getMaxInteractionDistanceSquared() { return maxInteractionDistanceSquared; }

    public boolean isBlockColonSyntax() { return blockColonSyntax; }
    public boolean isBlockProbeCommands() { return blockProbeCommands; }
    public boolean isUseCommandWhitelist() { return useCommandWhitelist; }
    public Set<String> getForbiddenCommands() { return forbiddenCommands; }
    public Set<String> getAllowedCommands() { return allowedCommands; }
    public long getCommandCooldownMs() { return commandCooldownMs; }
    public String getUnknownCommandMsg() { return unknownCommandMsg; }

    public int getMaxChatPerSecond() { return maxChatPerSecond; }
    public int getMaxChatLength() { return maxChatLength; }
    public boolean isBlockChatTranslationExploit() { return blockChatTranslationExploit; }

    public boolean isDiscordProxyEnabled() { return discordProxyEnabled; }
    public String getDiscordProxyType() { return discordProxyType; }
    public String getDiscordProxyHost() { return discordProxyHost; }
    public int getDiscordProxyPort() { return discordProxyPort; }
    public String getDiscordCustomUrl() { return discordCustomUrl; }

    public String getAlertPermission() { return alertPermission; }
    public String getBypassPermission() { return bypassPermission; }

    public String getPrefix() { return prefix; }
    public String getKickMessageExploit() { return kickMessageExploit; }
    public String getKickMessageBot() { return kickMessageBot; }
    public String getKickMessageSpam() { return kickMessageSpam; }
    public String getKickMessagePacketFlood() { return kickMessagePacketFlood; }
    public String getKickMessageIllegalItem() { return kickMessageIllegalItem; }
}
