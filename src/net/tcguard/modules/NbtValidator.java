package net.tcguard.modules;

import com.comphenix.protocol.wrappers.nbt.NbtBase;
import com.comphenix.protocol.wrappers.nbt.NbtCompound;
import com.comphenix.protocol.wrappers.nbt.NbtFactory;
import com.comphenix.protocol.wrappers.nbt.NbtList;
import net.tcguard.TCGuard;
import net.tcguard.config.ConfigManager;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BookMeta;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;
import java.util.regex.Pattern;

public class NbtValidator {

    private final TCGuard plugin;
    private static final Pattern TRANSLATION_EXPLOIT_PATTERN = Pattern.compile("(?i)(%[0-9]*\\$[sdefgxnb]|%[sdefgxnb]){4,}");

    private static final Pattern LOG4J_PATTERN = Pattern.compile(
            "(?i)" +
            "(" +
                "\\$\\{\\s*j\\s*n\\s*d\\s*i\\s*:" +
                "|" +
                "\\$\\{[^}]*\\$\\{[^}]*(lower|upper|env|sys|java|date|bundle|ctx|base64|url|main|marker|spring)" +
                "|" +
                "\\$\\{\\s*(env|sys|java|date|bundle|ctx|main|marker|spring)\\s*:" +
                "|" +
                "\\$\\{.*?(j|\\%6a|\\u006a).*(n|\\%6e|\\u006e).*(d|\\%64|\\u0064).*(i|\\%69|\\u0069)\\s*:" +
            ")"
    );

    public NbtValidator(TCGuard plugin) {
        this.plugin = plugin;
    }

    public boolean containsLog4jExploit(String text) {
        if (text == null || text.isEmpty()) return false;
        if (!text.contains("$")) return false;
        return LOG4J_PATTERN.matcher(text).find();
    }

    public boolean containsTranslationCrash(String text) {
        if (text == null || text.isEmpty()) return false;
        if (text.length() > 5000) return true;
        return TRANSLATION_EXPLOIT_PATTERN.matcher(text).find();
    }

    public boolean containsMaliciousText(String text) {
        return containsLog4jExploit(text) || containsTranslationCrash(text);
    }

    public boolean isIllegalItem(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) return false;
        ConfigManager cfg = plugin.getConfigManager();

        if (item.getAmount() > 64 || item.getAmount() < 0) {
            return true;
        }

        if (cfg.isBlockIllegalItems()) {
            String typeName = item.getType().name();
            if (typeName.contains("COMMAND") || typeName.equals("BARRIER") || typeName.equals("BEDROCK")
                    || typeName.contains("STRUCTURE") || typeName.contains("JIGSAW")
                    || typeName.equals("DEBUG_STICK") || typeName.equals("LIGHT")) {
                return true;
            }
        }

        if (item.hasItemMeta()) {
            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                if (meta.hasDisplayName()) {
                    String name = meta.getDisplayName();
                    if (name.length() > cfg.getMaxItemNameLength() || (cfg.isBlockTranslationCrash() && containsTranslationCrash(name))) {
                        return true;
                    }
                }

                if (meta.hasLore()) {
                    List<String> lore = meta.getLore();
                    if (lore != null) {
                        if (lore.size() > cfg.getMaxItemLoreLines()) return true;
                        for (String line : lore) {
                            if (line != null && (line.length() > 256 || (cfg.isBlockTranslationCrash() && containsTranslationCrash(line)))) {
                                return true;
                            }
                        }
                    }
                }

                if (item.getEnchantments().size() > cfg.getMaxItemEnchants()) {
                    return true;
                }

                if (meta instanceof BookMeta) {
                    if (isIllegalBook((BookMeta) meta)) {
                        return true;
                    }
                }

                if (meta instanceof org.bukkit.inventory.meta.FireworkMeta) {
                    org.bukkit.inventory.meta.FireworkMeta fMeta = (org.bukkit.inventory.meta.FireworkMeta) meta;
                    try {
                        if (fMeta.getPower() > 3) return true;
                        if (fMeta.getEffectsSize() > 12) return true;
                        for (org.bukkit.FireworkEffect effect : fMeta.getEffects()) {
                            if (effect.getColors().size() > 12 || effect.getFadeColors().size() > 12) {
                                return true;
                            }
                        }
                    } catch (Exception ignored) {}
                }

                if (meta instanceof org.bukkit.inventory.meta.BlockStateMeta) {
                    org.bukkit.inventory.meta.BlockStateMeta bMeta = (org.bukkit.inventory.meta.BlockStateMeta) meta;
                    if (bMeta.hasBlockState()) {
                        org.bukkit.block.BlockState bs = bMeta.getBlockState();
                        if (bs instanceof org.bukkit.inventory.InventoryHolder) {
                            org.bukkit.inventory.InventoryHolder holder = (org.bukkit.inventory.InventoryHolder) bs;
                            for (ItemStack subItem : holder.getInventory().getContents()) {
                                if (subItem != null && isIllegalItem(subItem)) {
                                    return true;
                                }
                            }
                        }
                    }
                }
            }
        }

        try {
            NbtCompound compound = (NbtCompound) NbtFactory.fromItemTag(item);
            if (compound != null) {
                if (checkNbtDepth(compound, 0, cfg.getMaxNbtDepth())) {
                    return true;
                }
            }
        } catch (Exception ignored) {}

        return false;
    }

    public boolean isIllegalBook(BookMeta book) {
        if (book == null) return false;
        ConfigManager cfg = plugin.getConfigManager();

        if (book.getPageCount() > cfg.getMaxBookPages()) {
            return true;
        }

        for (int i = 1; i <= book.getPageCount(); i++) {
            String page = book.getPage(i);
            if (page != null) {
                if (page.length() > cfg.getMaxBookPageLength()) return true;
                if (cfg.isBlockTranslationCrash() && containsTranslationCrash(page)) return true;
            }
        }

        if (book.hasTitle() && book.getTitle() != null && book.getTitle().length() > 64) {
            return true;
        }

        if (book.hasAuthor() && book.getAuthor() != null && book.getAuthor().length() > 32) {
            return true;
        }

        return false;
    }

    private boolean checkNbtDepth(NbtBase<?> tag, int currentDepth, int maxDepth) {
        if (currentDepth > maxDepth) return true;

        if (tag instanceof NbtCompound) {
            NbtCompound compound = (NbtCompound) tag;
            for (String key : compound.getKeys()) {
                if (key.length() > 128) return true;
                if (plugin.getConfigManager().isBlockTranslationCrash() && containsTranslationCrash(key)) return true;
                
                NbtBase<?> child = compound.getValue(key);
                if (child != null && child.getValue() instanceof String) {
                    String strVal = (String) child.getValue();
                    if (strVal.length() > 2048 || (plugin.getConfigManager().isBlockTranslationCrash() && containsTranslationCrash(strVal))) {
                        return true;
                    }
                }
                if (checkNbtDepth(child, currentDepth + 1, maxDepth)) {
                    return true;
                }
            }
        } else if (tag instanceof NbtList) {
            NbtList<?> list = (NbtList<?>) tag;
            if (list.size() > 1000) return true;
            for (Object elem : list.getValue()) {
                if (elem instanceof NbtBase) {
                    if (checkNbtDepth((NbtBase<?>) elem, currentDepth + 1, maxDepth)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }
}
