package net.tcguard;

import org.bukkit.Color;
import org.bukkit.FireworkEffect;
import org.bukkit.Material;
import org.bukkit.block.banner.Pattern;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCreativeEvent;
import org.bukkit.event.player.PlayerPickupItemEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BannerMeta;
import org.bukkit.inventory.meta.BookMeta;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.FireworkEffectMeta;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.potion.PotionEffect;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class ItemSanitizer implements Listener {
    private static final int NAME = 64, LORE_LINES = 32, LORE_LINE = 256, PAGES = 50, PAGE = 256,
            PATTERNS = 6, EFFECTS = 8, COLORS = 8, POTION_EFFECTS = 5, POTION_AMPLIFIER = 4, POTION_TICKS = 9600;

    private final TCGuard plugin;
    private Set<Material> blocked = EnumSet.noneOf(Material.class);
    private int maxEnchant;

    ItemSanitizer(TCGuard plugin) {
        this.plugin = plugin;
    }

    void reload() {
        FileConfiguration config = plugin.getConfig();
        maxEnchant = config.getInt("items.max-enchant-level");
        Set<Material> next = EnumSet.noneOf(Material.class);
        for (String name : config.getStringList("items.creative-blacklist")) {
            Material material = Material.matchMaterial(name);
            if (material != null) next.add(material);
        }
        blocked = next;
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onCreative(InventoryCreativeEvent event) {
        Player player = (Player) event.getWhoClicked();
        ItemStack item = event.getCursor();
        if (item == null || item.getType() == Material.AIR || plugin.bypasses(player)) return;

        if (blocked.contains(item.getType())) {
            event.setCancelled(true);
            event.setCursor(null);
            flag(player, "creative " + item.getType());
            return;
        }
        Set<String> removed = new LinkedHashSet<>();
        event.setCursor(rebuild(item, removed));
        if (!removed.isEmpty()) flag(player, "creative item with " + String.join(", ", removed));
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onClick(InventoryClickEvent event) {
        if (event instanceof InventoryCreativeEvent || plugin.bypasses((Player) event.getWhoClicked())) return;
        correct(event.getCurrentItem());
        correct(event.getCursor());
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onPickup(PlayerPickupItemEvent event) {
        if (plugin.bypasses(event.getPlayer())) return;
        ItemStack stack = event.getItem().getItemStack();
        if (correct(stack)) event.getItem().setItemStack(stack);
    }

    private void flag(Player player, String what) {
        plugin.count();
        plugin.violations().add(player, Violations.Category.ITEMS, 1, what);
    }

    boolean correct(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) return false;
        boolean changed = false;
        if (item.getAmount() > item.getMaxStackSize()) {
            item.setAmount(item.getMaxStackSize());
            changed = true;
        }
        for (Map.Entry<Enchantment, Integer> e : item.getEnchantments().entrySet()) {
            if (e.getValue() <= maxEnchant) continue;
            item.addUnsafeEnchantment(e.getKey(), e.getKey().getMaxLevel());
            changed = true;
        }
        return changed;
    }

    ItemStack rebuild(ItemStack in, Set<String> removed) {
        int amount = Math.max(1, Math.min(in.getAmount(), in.getMaxStackSize()));
        if (amount != in.getAmount()) removed.add("stack of " + in.getAmount());
        ItemStack out = new ItemStack(in.getType(), amount, in.getDurability());
        ItemMeta from = in.getItemMeta(), to = out.getItemMeta();
        if (from == null || to == null) return out;

        if (from.hasDisplayName()) {
            if (safe(from.getDisplayName(), NAME)) to.setDisplayName(from.getDisplayName());
            else removed.add("name");
        }
        if (from.hasLore()) {
            List<String> lore = new ArrayList<>();
            for (String line : from.getLore()) {
                if (lore.size() < LORE_LINES && safe(line, LORE_LINE)) lore.add(line);
                else removed.add("lore");
            }
            to.setLore(lore);
        }
        for (Map.Entry<Enchantment, Integer> e : from.getEnchants().entrySet()) {
            to.addEnchant(e.getKey(), level(e, removed), true);
        }
        copy(from, to, removed);
        out.setItemMeta(to);
        return out;
    }

    private void copy(ItemMeta from, ItemMeta to, Set<String> removed) {
        if (from instanceof BookMeta) {
            BookMeta a = (BookMeta) from, b = (BookMeta) to;
            for (String page : a.getPages()) {
                if (b.getPageCount() < PAGES && safe(page, PAGE)) b.addPage(page);
                else removed.add("book page");
            }
            if (a.hasTitle() && safe(a.getTitle(), 32)) b.setTitle(a.getTitle());
            if (a.hasAuthor() && safe(a.getAuthor(), 16)) b.setAuthor(a.getAuthor());
        } else if (from instanceof SkullMeta) {
            String owner = ((SkullMeta) from).getOwner();
            if (owner != null && owner.matches("\\w{1,16}")) ((SkullMeta) to).setOwner(owner);
        } else if (from instanceof LeatherArmorMeta) {
            ((LeatherArmorMeta) to).setColor(((LeatherArmorMeta) from).getColor());
        } else if (from instanceof BannerMeta) {
            BannerMeta a = (BannerMeta) from, b = (BannerMeta) to;
            b.setBaseColor(a.getBaseColor());
            List<Pattern> patterns = a.getPatterns();
            if (patterns.size() > PATTERNS) removed.add(patterns.size() + " banner patterns");
            b.setPatterns(new ArrayList<>(patterns.subList(0, Math.min(PATTERNS, patterns.size()))));
        } else if (from instanceof FireworkMeta) {
            FireworkMeta a = (FireworkMeta) from, b = (FireworkMeta) to;
            b.setPower(Math.max(0, Math.min(3, a.getPower())));
            for (FireworkEffect effect : a.getEffects()) {
                if (b.getEffectsSize() < EFFECTS) b.addEffect(bounded(effect));
                else removed.add("firework effect");
            }
        } else if (from instanceof FireworkEffectMeta) {
            FireworkEffect effect = ((FireworkEffectMeta) from).getEffect();
            if (effect != null) ((FireworkEffectMeta) to).setEffect(bounded(effect));
        } else if (from instanceof PotionMeta) {
            PotionMeta b = (PotionMeta) to;
            for (PotionEffect e : ((PotionMeta) from).getCustomEffects()) {
                boolean vanilla = e.getAmplifier() <= POTION_AMPLIFIER && e.getDuration() <= POTION_TICKS;
                if (vanilla && b.getCustomEffects().size() < POTION_EFFECTS) b.addCustomEffect(e, true);
                else removed.add("potion " + e.getType().getName() + " " + e.getAmplifier());
            }
        } else if (from instanceof EnchantmentStorageMeta) {
            for (Map.Entry<Enchantment, Integer> e : ((EnchantmentStorageMeta) from).getStoredEnchants().entrySet()) {
                ((EnchantmentStorageMeta) to).addStoredEnchant(e.getKey(), level(e, removed), true);
            }
        }
    }

    private int level(Map.Entry<Enchantment, Integer> enchant, Set<String> removed) {
        if (enchant.getValue() <= maxEnchant) return Math.max(1, enchant.getValue());
        removed.add(enchant.getKey().getName() + " " + enchant.getValue());
        return enchant.getKey().getMaxLevel();
    }

    private static FireworkEffect bounded(FireworkEffect effect) {
        List<Color> colors = effect.getColors(), fades = effect.getFadeColors();
        return FireworkEffect.builder().with(effect.getType()).flicker(effect.hasFlicker()).trail(effect.hasTrail())
                .withColor(colors.isEmpty() ? Collections.singletonList(Color.WHITE) : colors.subList(0, Math.min(COLORS, colors.size())))
                .withFade(fades.subList(0, Math.min(COLORS, fades.size())))
                .build();
    }

    private static boolean safe(String text, int max) {
        return text != null && text.length() <= max && !Log4jFilter.matches(text);
    }
}
