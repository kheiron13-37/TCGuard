package net.tcguard.config;

import net.tcguard.TCGuard;
import net.tcguard.util.ColorUtil;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

public class LanguageManager {

    private final TCGuard plugin;
    private String currentLanguage = "en";
    private final Map<String, String> messages = new HashMap<>();

    public LanguageManager(TCGuard plugin) {
        this.plugin = plugin;
    }

    public void load() {
        currentLanguage = plugin.getConfig().getString("language", "en").toLowerCase();
        if (!currentLanguage.equals("en") && !currentLanguage.equals("tr") && !currentLanguage.equals("es")) {
            currentLanguage = "en";
        }

        messages.clear();
        loadBuiltinDefaults(currentLanguage);

        File langFolder = new File(plugin.getDataFolder(), "lang");
        if (!langFolder.exists()) {
            langFolder.mkdirs();
        }

        File langFile = new File(langFolder, "messages_" + currentLanguage + ".yml");
        if (!langFile.exists()) {
            saveDefaultLangFile(langFile, currentLanguage);
        }

        if (langFile.exists()) {
            FileConfiguration cfg = YamlConfiguration.loadConfiguration(langFile);
            for (String key : cfg.getKeys(true)) {
                if (cfg.isString(key)) {
                    messages.put(key, ColorUtil.colorize(cfg.getString(key)));
                }
            }
        }
    }

    public String get(String key) {
        String msg = messages.get(key);
        if (msg == null) return key;
        return ColorUtil.colorize(msg);
    }

    public String get(String key, String fallback) {
        String msg = messages.get(key);
        if (msg == null) return ColorUtil.colorize(fallback);
        return ColorUtil.colorize(msg);
    }

    private void loadBuiltinDefaults(String lang) {
        if ("tr".equals(lang)) {
            loadTurkishDefaults();
        } else if ("es".equals(lang)) {
            loadSpanishDefaults();
        } else {
            loadEnglishDefaults();
        }
    }

    private void loadEnglishDefaults() {
        messages.put("prefix", "&8[&6TC&8-&cGuard&8]");
        messages.put("no_permission", "&8[&cTCG-Security&8] &cYou do not have permission to use this.");
        messages.put("only_console", "&8[&cTCG-Security&8] &cThis command can ONLY be executed from the server CONSOLE!");
        messages.put("only_players", "&cThis command can only be executed by in-game players.");
        messages.put("config_reloaded", "&8[&aTCG-Security&8] &aConfiguration and security lists reloaded successfully.");
        messages.put("admin_added", "&8[&aTCG-Security&8] &aPlayer &e{player} &ahas been added to authorized TCGuard administrators.");
        messages.put("admin_removed", "&8[&cTCG-Security&8] &cPlayer &e{player} &ahas been removed from authorized TCGuard administrators.");
        messages.put("admin_already_exists", "&8[&eTCG-Security&8] &ePlayer &f{player} &eis already an authorized administrator.");
        messages.put("admin_not_found", "&8[&eTCG-Security&8] &ePlayer &f{player} &ewas not found in the administrator list.");
        messages.put("admin_list_header", "&8[&6TCG-Security&8] &eAuthorized TCGuard Administrators ({count}):");
        messages.put("invalid_username", "&8[&cTCG-Security&8] &cInvalid player username (Must be 3-16 characters).");
        messages.put("auth_required", "&8[&6TCG-Security&8] &cPlease authenticate first: &e/login <password>");
        messages.put("command_cooldown", "&8[&cTCG-Security&8] &cYou are sending commands too quickly!");
        messages.put("command_blocked", "&6Unknown command. Type &c/help &6for help.");
        messages.put("kick_exploit", "&8[&cSecurity&8] &cInvalid packet or exploit detected.");
        messages.put("kick_bot", "&8[&cSecurity&8] &cConnection rejected (Anti-Bot Protection). Please retry.");
        messages.put("kick_spam", "&8[&cSecurity&8] &cChat or packet spam detected.");
        messages.put("kick_packet_flood", "&8[&cSecurity&8] &cPacket limit exceeded.");
        messages.put("kick_illegal_item", "&8[&cSecurity&8] &cIllegal NBT item detected and removed.");
        messages.put("proxy_only", "&8[&cTCG-Security&8] &cDirect connection disallowed. Please join through the network proxy.");
        messages.put("quarantine_msg", "&8[&cTCG-Shield&8] &cYour IP is temporarily quarantined due to suspicious bot activity ({seconds}s).");
        messages.put("subnet_flood_msg", "&8[&cTCG-Shield&8] &cToo many connections from your subnet. Please wait 2 minutes.");
        messages.put("timing_bot_msg", "&8[&cTCG-Shield&8] &cConnection completed unnaturally fast (0.0ms Bot Detected). Please retry.");
        messages.put("ping_gate_msg", "&8[&cTCG-Shield&8] &cPlease refresh your server list before joining (Ping Verification).");
        messages.put("bot_name_heuristic_msg", "&8[&cTCG-Shield&8] &cSuspicious username pattern detected (Anti-Bot Filter).");
        messages.put("datacenter_blocked_msg", "&8[&cTCG-Shield&8] &cConnections from datacenters/cloud proxies are prohibited.");
        messages.put("challenge_prompt_gui", "&8[&6TCG-Security&8] &eBot attack active! Please click the &a&lEMERALD &eto verify.");
        messages.put("challenge_success", "&8[&aTCG-Security&8] &aVerification successful! Welcome to the server.");
        messages.put("challenge_failed", "&8[&cTCG-Security&8] &cVerification failed or timed out.");
        messages.put("lang_changed_msg", "&8[&aTCG-Security&8] &aLanguage changed to: &e{lang}");
        messages.put("reset_quarantine_msg", "&8[&aTCG-Security&8] &aAll IP quarantines and attack states have been reset.");
        messages.put("guardcode_prompt_set", "&8[&6TCG-Security&8] &cYou MUST set your administrator GuardCode! &eUsage: /guardcode set <pin>");
        messages.put("guardcode_prompt_enter", "&8[&6TCG-Security&8] &cSecurity verification required! Please enter your GuardCode: &e/guardcode <pin>");
        messages.put("guardcode_set_success", "&8[&aTCG-Security&8] &aGuardCode successfully set! Your session is now verified.");
        messages.put("guardcode_verify_success", "&8[&aTCG-Security&8] &aGuardCode verified! Full administrator access unlocked.");
        messages.put("guardcode_wrong", "&8[&cTCG-Security&8] &cIncorrect GuardCode! Please try again: &e/guardcode <pin>");
        messages.put("guardcode_already_set", "&8[&cTCG-Security&8] &cYou have already set a GuardCode! To reset, use console: &eguardcode-reset <username>");
        messages.put("guardcode_reset_success", "&8[&aTCG-Security&8] &aGuardCode for player &e{player} &ahas been reset.");
        messages.put("guardcode_kick_failed", "&8[&cTCG-Security&8] &cToo many failed GuardCode verification attempts.");

        messages.put("gui_title", "§8[§6TCG§8-§cGuard§8] §0Radar & Dashboard");
        messages.put("gui_radar_title", "§6§lTCGuard Live Threat Radar");
        messages.put("gui_radar_desc", "§7Live Server Performance & Security Radar");
        messages.put("gui_version", "§f» Version: §a1.{version} §7(Universal Single-JAR)");
        messages.put("gui_active_lang", "§f» Active Language: §e{lang}");
        messages.put("gui_tps", "§f» Live TPS: {color}{tps} {status}");
        messages.put("gui_ram", "§f» Memory (RAM): §e{used}MB / {max}MB §8(§7%{percent}§8)");
        messages.put("gui_players", "§f» Online Players: §b{online}§7/{max}");
        messages.put("gui_threat_attack", "§c🚨 UNDER ATTACK (%100 SHIELD)");
        messages.put("gui_threat_normal", "§a🟢 Normal (Secure)");
        messages.put("gui_threat_level", "§f» Threat Level: {status}");
        messages.put("gui_total_blocked", "§f» Total Blocked Threats: §c{count}");

        messages.put("gui_state", "§fState: ");
        messages.put("gui_state_enabled", "§aENABLED");
        messages.put("gui_state_disabled", "§cDISABLED");
        messages.put("gui_click_to_toggle", "§eClick to toggle.");
        messages.put("gui_click_to_cycle", "§eClick to cycle language.");

        messages.put("gui_mod_netty", "§e§lNetty Pipeline Shield");
        messages.put("gui_mod_netty_desc", "§7Filters corrupt frames, decoder exceptions & socket byte floods.");

        messages.put("gui_mod_click", "§e§lAuto-Clicker & Macro Guard");
        messages.put("gui_mod_click_desc", "§7Blocks 22+ CPS & standard deviation macro patterns.");

        messages.put("gui_mod_bot", "§e§lAnti-Bot & Subnet Guard");
        messages.put("gui_mod_bot_desc", "§7Prevents 0.0ms handshake bots and /24 CIDR botnet swarms.");

        messages.put("gui_mod_fastbreak", "§e§lFastBreak & FastPlace");
        messages.put("gui_mod_fastbreak_desc", "§7Stops impossible block break and fast block place exploits.");

        messages.put("gui_mod_lag", "§e§lRedstone & Anti-Lag");
        messages.put("gui_mod_lag_desc", "§7Freezes redstone clocks & piston lag loop machines.");

        messages.put("gui_mod_forceop", "§e§lForce-OP & Backdoor Shield");
        messages.put("gui_mod_forceop_desc", "§7Monitors operator snapshots and instantly revokes rogue OPs.");

        messages.put("gui_mod_honeypot", "§e§lHoneypot Griefer Trap");
        messages.put("gui_mod_honeypot_desc", "§7Lures and auto-quarantines hackers probing trap commands.");

        messages.put("gui_mod_nbt", "§e§lDeep NBT & Item Protection");
        messages.put("gui_mod_nbt_desc", "§7Cleans shulker bombs, firework particles & illegal NBT tags.");

        messages.put("gui_mod_probe", "§e§lCommand Probe Shield");
        messages.put("gui_mod_probe_desc", "§7Blocks plugin leaks (/pl, /?) and colon syntax execution.");

        messages.put("gui_mod_reach", "§e§lReach & Interaction Distance");
        messages.put("gui_mod_reach_desc", "§7Blocks opening chests, anvils and beacons beyond max reach.");

        messages.put("gui_mod_smartlag", "§e§lSmart-Lag Emergency Saver");
        messages.put("gui_mod_smartlag_desc", "§7Cancels explosions and drops when TPS drops below 16.0.");

        messages.put("gui_mod_proxy", "§e§lProxy Edge Bridge");
        messages.put("gui_mod_proxy_desc", "§7Syncs network kicks and attack mode across Bungee/Velocity.");

        messages.put("gui_mod_webhook", "§e§lDiscord Webhook Integration");
        messages.put("gui_mod_webhook_desc", "§7Dispatches rich embed alerts to your Discord staff channel.");

        messages.put("gui_mod_db", "§e§lPersistent Forensics Database");
        messages.put("gui_mod_db_desc", "§7Zero-config async SQLite/MySQL logging for forensics analysis.");

        messages.put("gui_feed_title", "§e§lLive Incident Feed");
        messages.put("gui_feed_header", "§7Recent Security Incidents:");
        messages.put("gui_feed_empty", "§8[§a✓§8] §7No violations recorded yet.");

        messages.put("gui_btn_lang", "§b§lChange Language");
        messages.put("gui_btn_reload", "§a§lReload Configuration");
        messages.put("gui_btn_reload_desc", "§7Click to reload config.yml, admins.yml and translations.");
        messages.put("gui_btn_reset", "§c§lReset Quarantine & Attack Mode");
        messages.put("gui_btn_reset_desc", "§7Click to clear all IP quarantines and reset attack status.");
    }

    private void loadTurkishDefaults() {
        messages.put("prefix", "&8[&6TC&8-&cGuard&8]");
        messages.put("no_permission", "&8[&cTCG-Güvenlik&8] &cBu işlemi yapmak için yetkiniz bulunmuyor.");
        messages.put("only_console", "&8[&cTCG-Güvenlik&8] &cBu komut YALNIZCA sunucu KONSOLUNDAN çalıştırılabilir!");
        messages.put("only_players", "&cBu komut sadece oyun içinden kullanılabilir.");
        messages.put("config_reloaded", "&8[&aTCG-Güvenlik&8] &aYapılandırma ve güvenlik listeleri başarıyla yenilendi.");
        messages.put("admin_added", "&8[&aTCG-Güvenlik&8] &e{player} &aadlı kullanıcı yetkili TCGuard yöneticilerine eklendi.");
        messages.put("admin_removed", "&8[&cTCG-Güvenlik&8] &e{player} &aadlı kullanıcı yetkili yöneticiler listesinden çıkarıldı.");
        messages.put("admin_already_exists", "&8[&eTCG-Güvenlik&8] &f{player} &azaten yetkili bir yönetici.");
        messages.put("admin_not_found", "&8[&eTCG-Güvenlik&8] &f{player} &ayönetici listesinde bulunamadı.");
        messages.put("admin_list_header", "&8[&6TCG-Güvenlik&8] &eYetkili TCGuard Yöneticileri ({count}):");
        messages.put("invalid_username", "&8[&cTCG-Güvenlik&8] &cGeçersiz kullanıcı adı (3-16 karakter olmalıdır).");
        messages.put("auth_required", "&8[&6TCG-Güvenlik&8] &cLütfen önce giriş yapın: &e/login <şifre>");
        messages.put("command_cooldown", "&8[&cTCG-Güvenlik&8] &cKomutları çok hızlı gönderiyorsunuz!");
        messages.put("command_blocked", "&6Bilinmeyen komut. Yardım için &c/yardim &6yazınız.");
        messages.put("kick_exploit", "&8[&cGüvenlik&8] &cGeçersiz paket veya exploit tespit edildi.");
        messages.put("kick_bot", "&8[&cGüvenlik&8] &cBağlantı reddedildi (Anti-Bot Koruması).");
        messages.put("kick_spam", "&8[&cGüvenlik&8] &cSpam tespit edildi.");
        messages.put("kick_packet_flood", "&8[&cGüvenlik&8] &cAşırı paket gönderimi (Packet Flood).");
        messages.put("kick_illegal_item", "&8[&cGüvenlik&8] &cYasaklı veya hasarlı eşya temizlendi.");
        messages.put("proxy_only", "&8[&cTCG-Güvenlik&8] &cLütfen ana sunucu proxy adresinden bağlanın.");
        messages.put("quarantine_msg", "&8[&cTCG-Kalkan&8] &cIP adresiniz bot şüphesiyle geçici olarak karantinaya alındı ({seconds} sn).");
        messages.put("subnet_flood_msg", "&8[&cTCG-Kalkan&8] &cBulunduğunuz IP bloğundan aşırı bağlantı algılandı. Lütfen 2 dakika bekleyin.");
        messages.put("timing_bot_msg", "&8[&cTCG-Kalkan&8] &cBağlantı çok hızlı gerçekleşti (Bot Tespiti). Lütfen tekrar deneyin.");
        messages.put("ping_gate_msg", "&8[&cTCG-Kalkan&8] &cLütfen sunucuya girmeden önce sunucu listesini yenileyin (Ping Doğrulaması).");
        messages.put("bot_name_heuristic_msg", "&8[&cTCG-Kalkan&8] &cŞüpheli kullanıcı adı deseni tespit edildi (Bot Filtresi).");
        messages.put("datacenter_blocked_msg", "&8[&cTCG-Kalkan&8] &cVeri merkezi ve proxy sunucularından girişler engellenmiştir.");
        messages.put("challenge_prompt_gui", "&8[&6TCG-Güvenlik&8] &eBot saldırısı aktif! Lütfen doğrulamak için &a&lZÜMRÜTE &etıklayın.");
        messages.put("challenge_success", "&8[&aTCG-Güvenlik&8] &aDoğrulama başarılı! Sunucuya hoş geldiniz.");
        messages.put("challenge_failed", "&8[&cTCG-Güvenlik&8] &cDoğrulama başarısız oldu veya süre doldu.");
        messages.put("lang_changed_msg", "&8[&aTCG-Güvenlik&8] &aDil başarıyla değiştirildi: &e{lang}");
        messages.put("reset_quarantine_msg", "&8[&aTCG-Güvenlik&8] &aTüm IP karantinaları ve saldırı kalkanı sıfırlandı.");
        messages.put("guardcode_prompt_set", "&8[&6TCG-Güvenlik&8] &cYönetici güvenlik kodunuzu (GuardCode) belirlemelisiniz! &eKullanım: /guardcode set <kod>");
        messages.put("guardcode_prompt_enter", "&8[&6TCG-Güvenlik&8] &cYönetici doğrulaması gerekli! Lütfen güvenlik kodunuzu girin: &e/guardcode <kod>");
        messages.put("guardcode_set_success", "&8[&aTCG-Güvenlik&8] &aGüvenlik kodu (GuardCode) başarıyla ayarlandı! Oturumunuz doğrulandı.");
        messages.put("guardcode_verify_success", "&8[&aTCG-Güvenlik&8] &aGüvenlik kodu doğrulandı! Yönetici yetkileriniz aktif edildi.");
        messages.put("guardcode_wrong", "&8[&cTCG-Güvenlik&8] &cHatalı güvenlik kodu! Lütfen tekrar deneyin: &e/guardcode <kod>");
        messages.put("guardcode_already_set", "&8[&cTCG-Güvenlik&8] &cZaten bir güvenlik kodunuz var! Sıfırlamak için konsoldan: &eguardcode-reset <kullanıcı>");
        messages.put("guardcode_reset_success", "&8[&aTCG-Güvenlik&8] &e{player} &aadlı kullanıcının güvenlik kodu sıfırlandı.");
        messages.put("guardcode_kick_failed", "&8[&cTCG-Güvenlik&8] &cÇok fazla hatalı güvenlik kodu denemesi yapıldı.");

        messages.put("gui_title", "§8[§6TCG§8-§cGuard§8] §0Radar & Kontrol Paneli");
        messages.put("gui_radar_title", "§6§lTCGuard Canlı Tehdit Radarı");
        messages.put("gui_radar_desc", "§7Canlı Sunucu Performansı & Güvenlik Radarı");
        messages.put("gui_version", "§f» Sürüm: §a1.{version} §7(Evrensel Tek JAR)");
        messages.put("gui_active_lang", "§f» Aktif Dil: §e{lang}");
        messages.put("gui_tps", "§f» Canlı TPS: {color}{tps} {status}");
        messages.put("gui_ram", "§f» Bellek (RAM): §e{used}MB / {max}MB §8(§7%{percent}§8)");
        messages.put("gui_players", "§f» Çevrimiçi Oyuncu: §b{online}§7/{max}");
        messages.put("gui_threat_attack", "§c🚨 SALDIRI MODU AKTİF (%100 KALKAN)");
        messages.put("gui_threat_normal", "§a🟢 Normal (Güvenli)");
        messages.put("gui_threat_level", "§f» Tehdit Durumu: {status}");
        messages.put("gui_total_blocked", "§f» Toplam Engellenen Tehdit: §c{count}");

        messages.put("gui_state", "§fDurum: ");
        messages.put("gui_state_enabled", "§aAKTİF");
        messages.put("gui_state_disabled", "§cDEVRE DIŞI");
        messages.put("gui_click_to_toggle", "§eDurumu değiştirmek için tıklayın.");
        messages.put("gui_click_to_cycle", "§eDili değiştirmek için tıklayın.");

        messages.put("gui_mod_netty", "§e§lNetty Boru Hattı Kalkanı");
        messages.put("gui_mod_netty_desc", "§7Bozuk frame, decoder hataları ve soket taşmalarını filtreler.");

        messages.put("gui_mod_click", "§e§lAuto-Clicker & Makro Koruması");
        messages.put("gui_mod_click_desc", "§722+ CPS ve standart sapma makro düzenlerini engeller.");

        messages.put("gui_mod_bot", "§e§lAnti-Bot & Subnet Koruması");
        messages.put("gui_mod_bot_desc", "§70.0ms handshake botlarını ve /24 CIDR botnet saldırılarını önler.");

        messages.put("gui_mod_fastbreak", "§e§lFastBreak & FastPlace");
        messages.put("gui_mod_fastbreak_desc", "§7İmkansız hızda blok kırma ve hızlı blok koyma hilelerini durdurur.");

        messages.put("gui_mod_lag", "§e§lRedstone & Anti-Lag");
        messages.put("gui_mod_lag_desc", "§7Aşırı hızlı redstone saatlerini ve piston lag makinelerini dondurur.");

        messages.put("gui_mod_forceop", "§e§lForce-OP & Arka Kapı Kalkanı");
        messages.put("gui_mod_forceop_desc", "§7Operator anlık görüntüsünü izler ve izinsiz OP yetkilerini anında siler.");

        messages.put("gui_mod_honeypot", "§e§lBal Küpü Griefer Tuzağı");
        messages.put("gui_mod_honeypot_desc", "§7Hile komutlarını deneyen saldırganları otomatik karantinaya alır.");

        messages.put("gui_mod_nbt", "§e§lDerin NBT & Eşya Koruması");
        messages.put("gui_mod_nbt_desc", "§7Shulker bombalarını, havai fişek crash ve yasaklı NBT etiketlerini temizler.");

        messages.put("gui_mod_probe", "§e§lKomut Bilgi Sızdırmazlığı");
        messages.put("gui_mod_probe_desc", "§7Eklenti listeleme (/pl, /?) ve kolon syntax komutlarını engeller.");

        messages.put("gui_mod_reach", "§e§lReach & Etkileşim Menzili");
        messages.put("gui_mod_reach_desc", "§7Sandık, örs ve fenerleri uzaktan açma hilelerini engeller.");

        messages.put("gui_mod_smartlag", "§e§lAkıllı TPS & Lag Kurtarıcısı");
        messages.put("gui_mod_smartlag_desc", "§7TPS 16.0'ın altına düştüğünde patlama ve eşya droplarını iptal eder.");

        messages.put("gui_mod_proxy", "§e§lProxy Ağ Köprüsü");
        messages.put("gui_mod_proxy_desc", "§7Bungee/Velocity ağında saldırganı tüm sunuculardan atar.");

        messages.put("gui_mod_webhook", "§e§lDiscord Webhook Bildirimleri");
        messages.put("gui_mod_webhook_desc", "§7Güvenlik alarmlarını Discord yetkili kanalına Embed olarak iletir.");

        messages.put("gui_mod_db", "§e§lAdli Bilişim Veritabanı");
        messages.put("gui_mod_db_desc", "§7Tüm ihlalleri sıfır gecikmeyle SQLite veya MySQL'e kaydeder.");

        messages.put("gui_feed_title", "§e§lCanlı İhlal Akışı");
        messages.put("gui_feed_header", "§7Son Gerçekleşen Güvenlik Olayları:");
        messages.put("gui_feed_empty", "§8[§a✓§8] §7Henüz kaydedilen bir ihlal yok.");

        messages.put("gui_btn_lang", "§b§lDili Değiştir");
        messages.put("gui_btn_reload", "§a§lYapılandırmayı Yenile");
        messages.put("gui_btn_reload_desc", "§7config.yml, admins.yml ve çevirileri yeniden yüklemek için tıklayın.");
        messages.put("gui_btn_reset", "§c§lKarantina & Saldırı Modunu Sıfırla");
        messages.put("gui_btn_reset_desc", "§7Tüm IP karantinalarını ve saldırı kalkanını sıfırlamak için tıklayın.");
    }

    private void loadSpanishDefaults() {
        messages.put("prefix", "&8[&6TC&8-&cGuard&8]");
        messages.put("no_permission", "&8[&cTCG-Seguridad&8] &cNo tienes permiso para ejecutar este comando.");
        messages.put("only_console", "&8[&cTCG-Seguridad&8] &c¡Este comando SOLO se puede ejecutar desde la CONSOLA del servidor!");
        messages.put("only_players", "&cEste comando solo puede ser ejecutado por jugadores en el juego.");
        messages.put("config_reloaded", "&8[&aTCG-Seguridad&8] &aConfiguración y listas de seguridad recargadas con éxito.");
        messages.put("admin_added", "&8[&aTCG-Seguridad&8] &aEl jugador &e{player} &aha sido añadido a los administradores autorizados de TCGuard.");
        messages.put("admin_removed", "&8[&cTCG-Seguridad&8] &cEl jugador &e{player} &aha sido eliminado de los administradores de TCGuard.");
        messages.put("admin_already_exists", "&8[&eTCG-Seguridad&8] &eEl jugador &f{player} &eya es un administrador autorizado.");
        messages.put("admin_not_found", "&8[&eTCG-Seguridad&8] &eEl jugador &f{player} &eno fue encontrado en la lista de administradores.");
        messages.put("admin_list_header", "&8[&6TCG-Seguridad&8] &eAdministradores Autorizados de TCGuard ({count}):");
        messages.put("invalid_username", "&8[&cTCG-Seguridad&8] &cNombre de usuario inválido (Debe tener entre 3 y 16 caracteres).");
        messages.put("auth_required", "&8[&6TCG-Seguridad&8] &cPor favor autentícate primero: &e/login <contraseña>");
        messages.put("command_cooldown", "&8[&cTCG-Seguridad&8] &c¡Estás enviando comandos demasiado rápido!");
        messages.put("command_blocked", "&6Comando desconocido. Escribe &c/help &6para ayuda.");
        messages.put("kick_exploit", "&8[&cSeguridad&8] &cPaquete inválido o exploit detectado.");
        messages.put("kick_bot", "&8[&cSeguridad&8] &cConexión rechazada (Protección Anti-Bot).");
        messages.put("kick_spam", "&8[&cSeguridad&8] &cSpam de chat o paquetes detectado.");
        messages.put("kick_packet_flood", "&8[&cSeguridad&8] &cLímite de paquetes excedido.");
        messages.put("kick_illegal_item", "&8[&cSeguridad&8] &cObjeto NBT ilegal detectado y eliminado.");
        messages.put("proxy_only", "&8[&cTCG-Seguridad&8] &cConexión directa no permitida. Conéctese a través del proxy.");
        messages.put("quarantine_msg", "&8[&cTCG-Escudo&8] &cSu IP está temporalmente en cuarentena por actividad sospechosa de bots ({seconds}s).");
        messages.put("subnet_flood_msg", "&8[&cTCG-Escudo&8] &cDemasiadas conexiones desde su subred. Espere 2 minutos.");
        messages.put("timing_bot_msg", "&8[&cTCG-Escudo&8] &cHandshake demasiado rápido (Bot detectado).");
        messages.put("ping_gate_msg", "&8[&cTCG-Escudo&8] &cPor favor, actualiza la lista de servidores antes de entrar (Verificación Ping).");
        messages.put("bot_name_heuristic_msg", "&8[&cTCG-Escudo&8] &cPatrón de nombre de usuario sospechoso detectado (Anti-Bot).");
        messages.put("datacenter_blocked_msg", "&8[&cTCG-Escudo&8] &cLas conexiones desde centros de datos y proxies están prohibidas.");
        messages.put("challenge_prompt_gui", "&8[&6TCG-Seguridad&8] &e¡Ataque bot activo! Haz clic en la &a&lESMERALDA &epara verificar.");
        messages.put("challenge_success", "&8[&aTCG-Seguridad&8] &a¡Verificación exitosa! Bienvenido al servidor.");
        messages.put("challenge_failed", "&8[&cTCG-Seguridad&8] &cVerificación fallida o tiempo agotado.");
        messages.put("lang_changed_msg", "&8[&aTCG-Seguridad&8] &aIdioma cambiado con éxito a: &e{lang}");
        messages.put("reset_quarantine_msg", "&8[&aTCG-Seguridad&8] &aTodas las cuarentenas de IP y estados de ataque han sido restablecidos.");
        messages.put("guardcode_prompt_set", "&8[&6TCG-Seguridad&8] &c¡DEBES establecer tu GuardCode de administrador! &eUso: /guardcode set <código>");
        messages.put("guardcode_prompt_enter", "&8[&6TCG-Seguridad&8] &c¡Verificación de seguridad requerida! Introduce tu GuardCode: &e/guardcode <código>");
        messages.put("guardcode_set_success", "&8[&aTCG-Seguridad&8] &a¡GuardCode establecido con éxito! Tu sesión ha sido verificada.");
        messages.put("guardcode_verify_success", "&8[&aTCG-Seguridad&8] &a¡GuardCode verificado! Acceso de administrador desbloqueado.");
        messages.put("guardcode_wrong", "&8[&cTCG-Seguridad&8] &c¡GuardCode incorrecto! Inténtalo de nuevo: &e/guardcode <código>");
        messages.put("guardcode_already_set", "&8[&cTCG-Seguridad&8] &c¡Ya has establecido un GuardCode! Para restablecerlo desde consola: &eguardcode-reset <usuario>");
        messages.put("guardcode_reset_success", "&8[&aTCG-Seguridad&8] &aEl GuardCode del jugador &e{player} &aha sido restablecido.");
        messages.put("guardcode_kick_failed", "&8[&cTCG-Seguridad&8] &cDemasiados intentos fallidos de verificación de GuardCode.");

        messages.put("gui_title", "§8[§6TCG§8-§cGuard§8] §0Radar y Panel de Control");
        messages.put("gui_radar_title", "§6§lRadar de Amenazas en Vivo TCGuard");
        messages.put("gui_radar_desc", "§7Radar de Rendimiento y Seguridad del Servidor");
        messages.put("gui_version", "§f» Versión: §a1.{version} §7(JAR Único Universal)");
        messages.put("gui_active_lang", "§f» Idioma Activo: §e{lang}");
        messages.put("gui_tps", "§f» TPS en Vivo: {color}{tps} {status}");
        messages.put("gui_ram", "§f» Memoria (RAM): §e{used}MB / {max}MB §8(§7%{percent}§8)");
        messages.put("gui_players", "§f» Jugadores Online: §b{online}§7/{max}");
        messages.put("gui_threat_attack", "§c🚨 BAJO ATAQUE (ESCUDO %100)");
        messages.put("gui_threat_normal", "§a🟢 Normal (Seguro)");
        messages.put("gui_threat_level", "§f» Nivel de Amenaza: {status}");
        messages.put("gui_total_blocked", "§f» Amenazas Bloqueadas: §c{count}");

        messages.put("gui_state", "§fEstado: ");
        messages.put("gui_state_enabled", "§aACTIVADO");
        messages.put("gui_state_disabled", "§cDESACTIVADO");
        messages.put("gui_click_to_toggle", "§eHaz clic para cambiar.");
        messages.put("gui_click_to_cycle", "§eHaz clic para cambiar de idioma.");

        messages.put("gui_mod_netty", "§e§lEscudo de Canal Netty");
        messages.put("gui_mod_netty_desc", "§7Filtra tramas corruptas, excepciones de decodificador y desbordamientos.");

        messages.put("gui_mod_click", "§e§lAuto-Clicker y Macros");
        messages.put("gui_mod_click_desc", "§7Bloquea 22+ CPS y patrones de macros por desviación estándar.");

        messages.put("gui_mod_bot", "§e§lAnti-Bot y Subred");
        messages.put("gui_mod_bot_desc", "§7Detiene bots de handshake 0.0ms y ataques de subred /24 CIDR.");

        messages.put("gui_mod_fastbreak", "§e§lFastBreak y FastPlace");
        messages.put("gui_mod_fastbreak_desc", "§7Detiene velocidades imposibles de rotura y colocación de bloques.");

        messages.put("gui_mod_lag", "§e§lRedstone y Anti-Lag");
        messages.put("gui_mod_lag_desc", "§7Congela relojes de redstone rápidos y máquinas de pistones.");

        messages.put("gui_mod_forceop", "§e§lProtección Force-OP");
        messages.put("gui_mod_forceop_desc", "§7Supervisa operadores y revoca permisos de OP no autorizados.");

        messages.put("gui_mod_honeypot", "§e§lTrampa para Griefer (Honeypot)");
        messages.put("gui_mod_honeypot_desc", "§7Pone en cuarentena automática a hackers que prueban comandos trampa.");

        messages.put("gui_mod_nbt", "§e§lProtección NBT y Objetos");
        messages.put("gui_mod_nbt_desc", "§7Limpia cajas shulker bomba, fuegos artificiales y etiquetas ilegales.");

        messages.put("gui_mod_probe", "§e§lProtección de Comandos");
        messages.put("gui_mod_probe_desc", "§7Bloquea filtraciones de plugins (/pl, /?) y sintaxis de dos puntos.");

        messages.put("gui_mod_reach", "§e§lAlcance e Interacción");
        messages.put("gui_mod_reach_desc", "§7Bloquea apertura remota de cofres, yunques y balizas.");

        messages.put("gui_mod_smartlag", "§e§lAhorro de Lag de Emergencia");
        messages.put("gui_mod_smartlag_desc", "§7Cancela explosiones y drops cuando el TPS cae por debajo de 16.0.");

        messages.put("gui_mod_proxy", "§e§lPuente de Red Proxy");
        messages.put("gui_mod_proxy_desc", "§7Sincroniza expulsiones y modo de ataque en toda la red Bungee/Velocity.");

        messages.put("gui_mod_webhook", "§e§lIntegración Discord Webhook");
        messages.put("gui_mod_webhook_desc", "§7Envía alertas con formato Embed a tu canal de Discord.");

        messages.put("gui_mod_db", "§e§lBase de Datos Forense");
        messages.put("gui_mod_db_desc", "§7Registra incidentes de seguridad en SQLite o MySQL sin generar lag.");

        messages.put("gui_feed_title", "§e§lRegistro de Incidentes en Vivo");
        messages.put("gui_feed_header", "§7Incidentes de Seguridad Recientes:");
        messages.put("gui_feed_empty", "§8[§a✓§8] §7No hay infracciones registradas.");

        messages.put("gui_btn_lang", "§b§lCambiar Idioma");
        messages.put("gui_btn_reload", "§a§lRecargar Configuración");
        messages.put("gui_btn_reload_desc", "§7Haz clic para recargar config.yml, admins.yml y traducciones.");
        messages.put("gui_btn_reset", "§c§lRestablecer Cuarentena y Ataque");
        messages.put("gui_btn_reset_desc", "§7Haz clic para borrar cuarentenas de IP y reiniciar estado de ataque.");
    }

    private void saveDefaultLangFile(File file, String lang) {
        try {
            FileConfiguration cfg = new YamlConfiguration();
            for (Map.Entry<String, String> entry : messages.entrySet()) {
                cfg.set(entry.getKey(), entry.getValue().replace('§', '&'));
            }
            cfg.save(file);
        } catch (Exception ignored) {}
    }

    public String getCurrentLanguage() {
        return currentLanguage;
    }
}
