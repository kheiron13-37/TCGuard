package net.tcgtest;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

public class Probe extends JavaPlugin implements org.bukkit.event.Listener {
    @Override
    public void onEnable() { getServer().getPluginManager().registerEvents(this, this); }

    @org.bukkit.event.EventHandler(priority = org.bukkit.event.EventPriority.MONITOR)
    public void onDispense(org.bukkit.event.block.BlockDispenseEvent e) { out("dispense y=" + e.getBlock().getY() + " cancelled=" + e.isCancelled()); }

    private void out(String s) { getLogger().info("[PROBE] " + s); }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] a) {
        try {
            switch (a[0]) {
                case "give": {
                    Player p = Bukkit.getPlayerExact(a[1]);
                    ItemStack it = new ItemStack(Material.valueOf(a[3]), 1);
                    if (a.length > 5) it.addUnsafeEnchantment(Enchantment.getByName(a[4]), Integer.parseInt(a[5]));
                    p.getInventory().setItem(Integer.parseInt(a[2]), it);
                    p.updateInventory();
                    out("gave " + it);
                    break;
                }
                case "item": {
                    Player p = Bukkit.getPlayerExact(a[1]);
                    out("item " + a[2] + "=" + p.getInventory().getItem(Integer.parseInt(a[2])));
                    break;
                }
                case "hide": { Player o = Bukkit.getPlayerExact(a[1]); Player t = Bukkit.getPlayerExact(a[2]); o.hidePlayer(t); out("hidden " + a[2] + " from " + a[1]); break; }
                case "cansee": { Player o = Bukkit.getPlayerExact(a[1]); Player t = Bukkit.getPlayerExact(a[2]); out("cansee " + a[1] + "->" + a[2] + "=" + o.canSee(t)); break; }
                case "tp": { Player p = Bukkit.getPlayerExact(a[1]); p.teleport(new Location(p.getWorld(), Double.parseDouble(a[2]), Double.parseDouble(a[3]), Double.parseDouble(a[4]))); out("tp " + a[1]); break; }
                case "setblock": { Player p = Bukkit.getPlayerExact(a[1]); Location l = p.getLocation(); l.getWorld().getBlockAt(l.getBlockX() + Integer.parseInt(a[2]), l.getBlockY() + Integer.parseInt(a[3]), l.getBlockZ() + Integer.parseInt(a[4])).setType(Material.valueOf(a[5])); out("setblock ok"); break; }
                case "fill": {
                    org.bukkit.World w = Bukkit.getWorld(a[1]);
                    int x1=Integer.parseInt(a[2]),y1=Integer.parseInt(a[3]),z1=Integer.parseInt(a[4]),x2=Integer.parseInt(a[5]),y2=Integer.parseInt(a[6]),z2=Integer.parseInt(a[7]);
                    for (int x=Math.min(x1,x2);x<=Math.max(x1,x2);x++) for (int y=Math.min(y1,y2);y<=Math.max(y1,y2);y++) for (int z=Math.min(z1,z2);z<=Math.max(z1,z2);z++) w.getBlockAt(x,y,z).setType(Material.valueOf(a[8]));
                    out("fill ok"); break; }
                case "perm": { Player p = Bukkit.getPlayerExact(a[1]); p.addAttachment(this, a[2], true); p.recalculatePermissions(); out("perm " + a[2] + " -> " + a[1]); break; }
                case "nbt": {
                    Player p = Bukkit.getPlayerExact(a[1]);
                    ItemStack it = p.getInventory().getItem(Integer.parseInt(a[2]));
                    if (it == null) { out("nbt " + a[2] + "=EMPTY"); break; }
                    String pkg = Bukkit.getServer().getClass().getPackage().getName();
                    Object nms = Class.forName(pkg + ".inventory.CraftItemStack").getMethod("asNMSCopy", ItemStack.class).invoke(null, it);
                    Object tag = nms.getClass().getMethod("getTag").invoke(nms);
                    out("nbt " + a[2] + "=" + it.getType() + " " + tag);
                    break;
                }
                case "tps": {
                    Object srv = Bukkit.getServer().getClass().getMethod("getServer").invoke(Bukkit.getServer());
                    double[] t = (double[]) srv.getClass().getField("recentTps").get(srv);
                    out(String.format(java.util.Locale.ROOT, "tps=%.2f", t[0]));
                    break;
                }
                case "online": out("online=" + Bukkit.getOnlinePlayers().size()); break;
                case "isop": out("isop " + a[1] + "=" + Bukkit.getOfflinePlayer(a[1]).isOp()); break;
                case "pos": { Player p = Bukkit.getPlayerExact(a[1]); out("pos " + a[1] + "=" + p.getLocation()); break; }
                case "gm": { Player p = Bukkit.getPlayerExact(a[1]); p.setGameMode(org.bukkit.GameMode.valueOf(a[2])); out("gm ok"); break; }
                case "kill": { Player p = Bukkit.getPlayerExact(a[1]); p.setHealth(0); out("killed " + a[1]); break; }
                case "dead": { Player p = Bukkit.getPlayerExact(a[1]); out("dead " + a[1] + "=" + (p == null ? "offline" : p.isDead())); break; }
                case "dispense": {
                    Player p = Bukkit.getPlayerExact(a[1]);
                    org.bukkit.block.Block b = p.getWorld().getBlockAt(p.getLocation().getBlockX(), Integer.parseInt(a[2]), p.getLocation().getBlockZ());
                    b.setTypeIdAndData(23, Byte.parseByte(a[3]), false);
                    ((org.bukkit.block.Dispenser) b.getState()).getInventory().addItem(new ItemStack(a.length > 4 ? Material.valueOf(a[4]) : Material.ARROW, 1));
                    if (a.length > 5) b.getRelative(0, a[3].equals("1") ? 1 : -1, 0).setType(Material.valueOf(a[5]), false);
                    b.getRelative(1, 0, 0).setType(Material.AIR);
                    b.getRelative(1, 0, 0).setType(Material.REDSTONE_BLOCK);
                    out("dispenser placed");
                    break;
                }
                case "chunks": out("chunks=" + Bukkit.getPlayerExact(a[1]).getWorld().getLoadedChunks().length); break;
                case "block": {
                    Player p = Bukkit.getPlayerExact(a[1]);
                    org.bukkit.block.Block b = p.getLocation().getBlock().getRelative(Integer.parseInt(a[2]), Integer.parseInt(a[3]), Integer.parseInt(a[4]));
                    if (a.length > 5) b.setType(Material.valueOf(a[5]), false);
                    out("block=" + b.getType() + " at " + b.getX() + "," + b.getY() + "," + b.getZ());
                    break;
                }
                case "meta": {
                    Player p = Bukkit.getPlayerExact(a[1]);
                    long start = System.nanoTime();
                    ItemStack it = p.getInventory().getItem(Integer.parseInt(a[2]));
                    String type = it == null ? "EMPTY" : it.getType() + " meta=" + (it.getItemMeta() != null);
                    out("meta " + type + " ms=" + (System.nanoTime() - start) / 1000000);
                    break;
                }
                case "grow": {
                    Player p = Bukkit.getPlayerExact(a[1]);
                    org.bukkit.block.Block base = p.getLocation().getBlock().getRelative(6, 60, 0);
                    for (int y = 0; y <= 5; y++) base.getRelative(0, y, 0).setType(Material.AIR, false);
                    if (a[3].equals("1")) base.getRelative(0, 2, 0).setType(Material.ENDER_PORTAL, false);
                    java.util.List<org.bukkit.block.BlockState> blocks = new java.util.ArrayList<org.bukkit.block.BlockState>();
                    for (int y = 1; y <= 4; y++) {
                        org.bukkit.block.BlockState st = base.getRelative(0, y, 0).getState();
                        st.setType(Material.HUGE_MUSHROOM_2);
                        blocks.add(st);
                    }
                    org.bukkit.event.world.StructureGrowEvent e = new org.bukkit.event.world.StructureGrowEvent(
                        base.getLocation(), org.bukkit.TreeType.valueOf(a[2]), true, p, blocks);
                    Bukkit.getPluginManager().callEvent(e);
                    for (int y = 0; y <= 5; y++) base.getRelative(0, y, 0).setType(Material.AIR, false);
                    out("grow cancelled=" + e.isCancelled());
                    break;
                }
                default: out("unknown");
            }
        } catch (Throwable t) { out("error " + t); }
        return true;
    }
}
