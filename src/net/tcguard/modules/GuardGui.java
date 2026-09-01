package net.tcguard.modules;

import net.tcguard.TCGuard;
import net.tcguard.api.Compatibility;
import net.tcguard.config.LanguageManager;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;

public class GuardGui implements Listener {

    private final TCGuard plugin;

    public GuardGui(TCGuard plugin) {
        this.plugin = plugin;
    }

    public static class GuardHolder implements InventoryHolder {
        @Override
        public Inventory getInventory() {
            return null;
        }
    }

    public void openGui(Player player) {
        LanguageManager lang = plugin.getConfigManager().getLanguageManager();

        if (!plugin.getAdminManager().isPlayerAdmin(player)) {
            player.sendMessage(lang.get("no_permission"));
            return;
        }

        String title = lang.get("gui_title", "§8[§6TCG§8-§cGuard§8] §0Dashboard");
        Inventory inv = Bukkit.createInventory(new GuardHolder(), 54, title);

        ItemStack grayGlass = createItem(Compatibility.getMaterial("STAINED_GLASS_PANE", "GRAY_STAINED_GLASS_PANE"), 1, (short) 7, " ", null);
        ItemStack darkGlass = createItem(Compatibility.getMaterial("STAINED_GLASS_PANE", "BLACK_STAINED_GLASS_PANE"), 1, (short) 15, " ", null);

        for (int i = 0; i < 54; i++) {
            if (i < 9 || i >= 45 || i % 9 == 0 || i % 9 == 8) {
                inv.setItem(i, (i == 0 || i == 8 || i == 45 || i == 53) ? darkGlass : grayGlass);
            }
        }

        StatsManager stats = plugin.getStatsManager();
        ThreatIntelligence threatIntel = plugin.getThreatIntelligence();

        double[] tpsArr = Compatibility.getTPS();
        double tps = tpsArr.length > 0 ? Math.min(20.0, tpsArr[0]) : 20.0;
        String tpsColor = tps >= 19.0 ? "§a" : (tps >= 16.0 ? "§e" : "§c");
        String tpsStatus = tps >= 19.0 ? "§a(20.0)" : (tps >= 16.0 ? "§e(Fair)" : "§c(Lag)");

        Runtime runtime = Runtime.getRuntime();
        long usedMem = (runtime.totalMemory() - runtime.freeMemory()) / 1048576L;
        long maxMem = runtime.maxMemory() / 1048576L;
        int memPercent = (int) ((usedMem * 100) / (maxMem > 0 ? maxMem : 1));

        boolean underAttack = threatIntel.isUnderAttack();
        String threatStatus = underAttack ? lang.get("gui_threat_attack") : lang.get("gui_threat_normal");

        List<String> radarLore = new ArrayList<>();
        radarLore.add(lang.get("gui_radar_desc"));
        radarLore.add("");
        radarLore.add(lang.get("gui_version").replace("{version}", String.valueOf(Compatibility.getServerVersion())));
        radarLore.add(lang.get("gui_active_lang").replace("{lang}", lang.getCurrentLanguage().toUpperCase()));
        radarLore.add(lang.get("gui_tps").replace("{color}", tpsColor).replace("{tps}", String.format(Locale.US, "%.2f", tps)).replace("{status}", tpsStatus));
        radarLore.add(lang.get("gui_ram").replace("{used}", String.valueOf(usedMem)).replace("{max}", String.valueOf(maxMem)).replace("{percent}", String.valueOf(memPercent)));
        radarLore.add(lang.get("gui_players").replace("{online}", String.valueOf(Bukkit.getOnlinePlayers().size())).replace("{max}", String.valueOf(Bukkit.getMaxPlayers())));
        radarLore.add("");
        radarLore.add(lang.get("gui_threat_level").replace("{status}", threatStatus));
        radarLore.add(lang.get("gui_total_blocked").replace("{count}", String.valueOf(stats.getTotalBlocked())));

        Material radarMat = underAttack ? Compatibility.getMaterial("REDSTONE_BLOCK", "TNT") : Compatibility.getMaterial("NETHER_STAR", "BEACON");
        inv.setItem(4, createItem(radarMat, 1, (short) 0, lang.get("gui_radar_title"), radarLore));

        inv.setItem(10, createToggleItem(lang.get("gui_mod_netty"), plugin.getConfig().getBoolean("packet-protection.netty-pipeline-shield", true),
                Collections.singletonList(lang.get("gui_mod_netty_desc")), lang));
        inv.setItem(11, createToggleItem(lang.get("gui_mod_click"), plugin.getConfig().getBoolean("packet-protection.auto-clicker.enabled", true),
                Collections.singletonList(lang.get("gui_mod_click_desc")), lang));
        inv.setItem(12, createToggleItem(lang.get("gui_mod_bot"), plugin.getConfig().getBoolean("anti-bot.enabled", true),
                Collections.singletonList(lang.get("gui_mod_bot_desc")), lang));
        inv.setItem(13, createToggleItem(lang.get("gui_mod_fastbreak"), plugin.getConfig().getBoolean("packet-protection.fast-interact.block-fast-break", true),
                Collections.singletonList(lang.get("gui_mod_fastbreak_desc")), lang));
        inv.setItem(14, createToggleItem(lang.get("gui_mod_lag"), plugin.getConfig().getBoolean("lag-protection.redstone-limiter.enabled", true),
                Collections.singletonList(lang.get("gui_mod_lag_desc")), lang));
        inv.setItem(15, createToggleItem(lang.get("gui_mod_forceop"), plugin.getConfig().getBoolean("force-op-protection.enabled", true),
                Collections.singletonList(lang.get("gui_mod_forceop_desc")), lang));
        inv.setItem(16, createToggleItem(lang.get("gui_mod_honeypot"), plugin.getConfig().getBoolean("honeypot.enabled", true),
                Collections.singletonList(lang.get("gui_mod_honeypot_desc")), lang));

        inv.setItem(19, createToggleItem(lang.get("gui_mod_nbt"), plugin.getConfig().getBoolean("nbt-protection.block-illegal-items", true),
                Collections.singletonList(lang.get("gui_mod_nbt_desc")), lang));
        inv.setItem(20, createToggleItem(lang.get("gui_mod_probe"), plugin.getConfig().getBoolean("command-protection.block-probe-commands", true),
                Collections.singletonList(lang.get("gui_mod_probe_desc")), lang));
        inv.setItem(21, createToggleItem(lang.get("gui_mod_reach"), plugin.getConfig().getBoolean("reach-protection.enabled", true),
                Collections.singletonList(lang.get("gui_mod_reach_desc")), lang));
        inv.setItem(22, createToggleItem(lang.get("gui_mod_smartlag"), plugin.getConfig().getBoolean("smart-lag.enabled", true),
                Collections.singletonList(lang.get("gui_mod_smartlag_desc")), lang));
        inv.setItem(23, createToggleItem(lang.get("gui_mod_proxy"), plugin.getConfig().getBoolean("proxy-bridge.enabled", true),
                Collections.singletonList(lang.get("gui_mod_proxy_desc")), lang));
        inv.setItem(24, createToggleItem(lang.get("gui_mod_webhook"), plugin.getConfig().getBoolean("discord-webhook.enabled", false),
                Collections.singletonList(lang.get("gui_mod_webhook_desc")), lang));
        inv.setItem(25, createToggleItem(lang.get("gui_mod_db"), plugin.getConfig().getBoolean("database.enabled", true),
                Collections.singletonList(lang.get("gui_mod_db_desc")), lang));

        List<String> recentLore = new ArrayList<>();
        recentLore.add(lang.get("gui_feed_header"));
        recentLore.add("");
        List<StatsManager.IncidentRecord> incidents = stats.getRecentIncidents();
        if (incidents.isEmpty()) {
            recentLore.add(lang.get("gui_feed_empty"));
        } else {
            int count = 0;
            for (StatsManager.IncidentRecord rec : incidents) {
                if (count++ >= 5) break;
                recentLore.add("§8[§e" + rec.getTime() + "§8] §c" + rec.getPlayerName() + " §8- §f" + rec.getType() + " §7(" + rec.getDetails() + ")");
            }
        }
        inv.setItem(38, createItem(Compatibility.getMaterial("PAPER", "EMPTY_MAP"), 1, (short) 0, lang.get("gui_feed_title"), recentLore));

        String curLang = lang.getCurrentLanguage();
        List<String> langLore = new ArrayList<>();
        langLore.add(lang.get("gui_active_lang").replace("{lang}", curLang.toUpperCase()));
        langLore.add("");
        langLore.add((curLang.equals("en") ? "§a▶ " : "§8▷ ") + "§fEnglish (en)");
        langLore.add((curLang.equals("tr") ? "§a▶ " : "§8▷ ") + "§fTürkçe (tr)");
        langLore.add((curLang.equals("es") ? "§a▶ " : "§8▷ ") + "§fEspañol (es)");
        langLore.add("");
        langLore.add(lang.get("gui_click_to_cycle"));
        inv.setItem(40, createItem(Compatibility.getMaterial("BOOK_AND_QUILL", "WRITABLE_BOOK", "BOOK"), 1, (short) 0, lang.get("gui_btn_lang"), langLore));

        inv.setItem(42, createItem(Compatibility.getMaterial("EMERALD", "SLIME_BALL"), 1, (short) 0, lang.get("gui_btn_reload"), Collections.singletonList(lang.get("gui_btn_reload_desc"))));

        inv.setItem(49, createItem(Compatibility.getMaterial("BARRIER", "REDSTONE"), 1, (short) 0, lang.get("gui_btn_reset"), Collections.singletonList(lang.get("gui_btn_reset_desc"))));

        player.openInventory(inv);
        Compatibility.playSound(player, 1.0f, 1.2f, "CHEST_OPEN", "BLOCK_CHEST_OPEN");
    }

    private ItemStack createToggleItem(String name, boolean enabled, List<String> desc, LanguageManager lang) {
        Material mat = Compatibility.getMaterial("INK_SACK", "DYE", "WOOL", "STAINED_CLAY");
        short data = enabled ? (short) 10 : (short) 8;

        List<String> lore = new ArrayList<>(desc);
        lore.add("");
        lore.add(lang.get("gui_state") + (enabled ? lang.get("gui_state_enabled") : lang.get("gui_state_disabled")));
        lore.add(lang.get("gui_click_to_toggle"));

        return createItem(mat, 1, data, name, lore);
    }

    private ItemStack createItem(Material mat, int amount, short data, String name, List<String> lore) {
        if (mat == null) mat = Material.STONE;
        ItemStack item = new ItemStack(mat, amount, data);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            if (lore != null) meta.setLore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getView() == null || event.getInventory() == null) return;
        if (!(event.getInventory().getHolder() instanceof GuardHolder)) return;

        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player)) return;
        Player player = (Player) event.getWhoClicked();

        LanguageManager lang = plugin.getConfigManager().getLanguageManager();

        if (!plugin.getAdminManager().isPlayerAdmin(player)) {
            player.closeInventory();
            return;
        }

        int slot = event.getRawSlot();
        if (slot < 0 || slot >= 54) return;

        if (slot == 10) {
            toggleConfig("packet-protection.netty-pipeline-shield", player);
        } else if (slot == 11) {
            toggleConfig("packet-protection.auto-clicker.enabled", player);
        } else if (slot == 12) {
            toggleConfig("anti-bot.enabled", player);
        } else if (slot == 13) {
            toggleConfig("packet-protection.fast-interact.block-fast-break", player);
        } else if (slot == 14) {
            toggleConfig("lag-protection.redstone-limiter.enabled", player);
        } else if (slot == 15) {
            toggleConfig("force-op-protection.enabled", player);
        } else if (slot == 16) {
            toggleConfig("honeypot.enabled", player);
        } else if (slot == 19) {
            toggleConfig("nbt-protection.block-illegal-items", player);
        } else if (slot == 20) {
            toggleConfig("command-protection.block-probe-commands", player);
        } else if (slot == 21) {
            toggleConfig("reach-protection.enabled", player);
        } else if (slot == 22) {
            toggleConfig("smart-lag.enabled", player);
        } else if (slot == 23) {
            toggleConfig("proxy-bridge.enabled", player);
        } else if (slot == 24) {
            toggleConfig("discord-webhook.enabled", player);
        } else if (slot == 25) {
            toggleConfig("database.enabled", player);
        } else if (slot == 40) {
            String curLang = plugin.getConfig().getString("language", "en").toLowerCase();
            String nextLang = "en";
            if (curLang.equals("en")) nextLang = "tr";
            else if (curLang.equals("tr")) nextLang = "es";
            else nextLang = "en";

            plugin.getConfig().set("language", nextLang);
            plugin.saveConfig();
            plugin.getConfigManager().saveLocalizedConfigFile(nextLang);
            plugin.getConfigManager().load();

            LanguageManager updatedLang = plugin.getConfigManager().getLanguageManager();
            player.sendMessage(updatedLang.get("lang_changed_msg").replace("{lang}", nextLang.toUpperCase()));
            Compatibility.playSound(player, 1.0f, 1.4f, "ORB_PICKUP", "ENTITY_EXPERIENCE_ORB_PICKUP");
            openGui(player);
        } else if (slot == 42) {
            plugin.getConfigManager().load();
            plugin.getAdminManager().load();
            plugin.getAntiBotManager().updatePattern();
            player.sendMessage(lang.get("config_reloaded"));
            Compatibility.playSound(player, 1.0f, 1.5f, "LEVEL_UP", "ENTITY_PLAYER_LEVELUP");
            openGui(player);
        } else if (slot == 49) {
            plugin.getThreatIntelligence().clearAll();
            player.sendMessage(lang.get("reset_quarantine_msg"));
            Compatibility.playSound(player, 1.0f, 1.3f, "ANVIL_USE", "BLOCK_ANVIL_USE");
            openGui(player);
        }
    }

    private void toggleConfig(String path, Player player) {
        boolean current = plugin.getConfig().getBoolean(path, true);
        plugin.getConfig().set(path, !current);
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                plugin.saveConfig();
            } catch (Throwable ignored) {}
        });
        plugin.getConfigManager().load();
        Compatibility.playSound(player, 1.0f, 1.2f, "CLICK", "UI_BUTTON_CLICK");
        openGui(player);
    }
}
