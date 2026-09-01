package net.tcguard.modules;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.ListenerPriority;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.wrappers.WrappedChatComponent;
import net.tcguard.TCGuard;
import net.tcguard.api.Compatibility;
import net.tcguard.config.ConfigManager;
import org.bukkit.GameMode;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class PacketGuard {

    private final TCGuard guard;
    private final ProtocolManager protocolManager;

    public PacketGuard(TCGuard guard) {
        this.guard = guard;
        this.protocolManager = ProtocolLibrary.getProtocolManager();
    }

    public void register() {
        List<PacketType> watched = new ArrayList<>();

        try {
            for (PacketType type : PacketType.Play.Client.getInstance().values()) {
                if (type != null && type.isSupported()) {
                    watched.add(type);
                }
            }
        } catch (Exception e) {
            addPacketIfAvailable(watched, "CHAT");
            addPacketIfAvailable(watched, "POSITION");
            addPacketIfAvailable(watched, "POSITION_LOOK");
            addPacketIfAvailable(watched, "FLYING");
            addPacketIfAvailable(watched, "LOOK");
            addPacketIfAvailable(watched, "CUSTOM_PAYLOAD");
            addPacketIfAvailable(watched, "WINDOW_CLICK");
            addPacketIfAvailable(watched, "BLOCK_PLACE");
            addPacketIfAvailable(watched, "BLOCK_DIG");
            addPacketIfAvailable(watched, "TAB_COMPLETE");
            addPacketIfAvailable(watched, "SET_CREATIVE_SLOT");
            addPacketIfAvailable(watched, "ARM_ANIMATION");
            addPacketIfAvailable(watched, "HELD_ITEM_SLOT");
            addPacketIfAvailable(watched, "USE_ENTITY");
            addPacketIfAvailable(watched, "SETTINGS");
            addPacketIfAvailable(watched, "STEER_VEHICLE");
            addPacketIfAvailable(watched, "TRANSACTION");
            addPacketIfAvailable(watched, "ENTITY_ACTION");
            addPacketIfAvailable(watched, "ABILITIES");
            addPacketIfAvailable(watched, "UPDATE_SIGN");
            addPacketIfAvailable(watched, "CLIENT_COMMAND");
            addPacketIfAvailable(watched, "CLOSE_WINDOW");
            addPacketIfAvailable(watched, "ENCHANT_ITEM");
        }

        // Register handshake packet listener for ultra-fast bot timing analysis
        try {
            if (PacketType.Handshake.Client.SET_PROTOCOL != null && PacketType.Handshake.Client.SET_PROTOCOL.isSupported()) {
                protocolManager.addPacketListener(new PacketAdapter(guard, ListenerPriority.LOWEST, PacketType.Handshake.Client.SET_PROTOCOL) {
                    @Override
                    public void onPacketReceiving(PacketEvent event) {
                        try {
                            if (event.getPlayer() != null && event.getPlayer().getAddress() != null && guard.getThreatIntelligence() != null) {
                                guard.getThreatIntelligence().recordHandshake(event.getPlayer().getAddress());
                            }
                        } catch (Throwable ignored) {}
                    }
                });
            }
        } catch (Throwable ignored) {}

        // Register early chat listener at LOWEST priority to intercept Log4J before Spigot server command logging
        try {
            if (PacketType.Play.Client.CHAT != null && PacketType.Play.Client.CHAT.isSupported()) {
                protocolManager.addPacketListener(new PacketAdapter(guard, ListenerPriority.LOWEST, PacketType.Play.Client.CHAT) {
                    @Override
                    public void onPacketReceiving(PacketEvent event) {
                        Player player = event.getPlayer();
                        if (player == null) return;
                        try {
                            String chatMessage = event.getPacket().getStrings().readSafely(0);
                            if (chatMessage != null && Log4jShield.containsLog4jExploit(chatMessage)) {
                                event.setCancelled(true);
                                guard.kickPlayer(player, guard.getConfigManager().getKickMessageExploit());
                                guard.alertAdmins("§4[LOG4J] §c" + player.getName() + " Pre-Log JNDI attack blocked: " + sanitizeForLog(chatMessage));
                                guard.getStatsManager().recordIncident(player.getName(), "LOG4J", "ProtocolLib Pre-Log JNDI: " + sanitizeForLog(chatMessage));
                                guard.getDiscordWebhook().sendAlert("Log4J Attack Intercepted", player.getName(),
                                        "ProtocolLib Pre-Log Drop (CVE-2021-44228)",
                                        "Message: " + sanitizeForLog(chatMessage), 0xFF0000);
                            }
                        } catch (Throwable ignored) {}
                    }
                });
            }
        } catch (Throwable ignored) {}

        protocolManager.addPacketListener(new PacketAdapter(
                guard, ListenerPriority.HIGHEST, watched) {

            @Override
            public void onPacketReceiving(PacketEvent event) {
                if (event.isCancelled()) return;

                Player player = event.getPlayer();
                if (player == null) return;

                if (BypassManager.isBypassed(player)) return;

                ConfigManager cfg = guard.getConfigManager();
                RateLimiter rateLimiter = guard.getRateLimiter();
                NbtValidator nbtValidator = guard.getNbtValidator();

                PacketType type = event.getPacketType();
                PacketContainer packet = event.getPacket();
                String typeName = type.name().toUpperCase();

                if (typeName.equals("CHAT")) {
                    try {
                        String chatMessage = packet.getStrings().readSafely(0);
                        if (chatMessage != null && nbtValidator.containsLog4jExploit(chatMessage)) {
                            event.setCancelled(true);
                            guard.kickPlayer(player, cfg.getKickMessageExploit());
                            guard.alertAdmins("§4[LOG4J] §c" + player.getName() + " Log4J/JNDI attack blocked: " + sanitizeForLog(chatMessage));
                            guard.getStatsManager().recordIncident(player.getName(), "LOG4J", "JNDI Injection: " + sanitizeForLog(chatMessage));
                            guard.getDiscordWebhook().sendAlert("Log4J Attack Blocked", player.getName(),
                                    "JNDI Injection (CVE-2021-44228)",
                                    "Message: " + sanitizeForLog(chatMessage), 0xFF0000);
                            return;
                        }
                    } catch (Throwable ignored) {}
                }

                if (player.isDead() && type != PacketType.Play.Client.CLIENT_COMMAND) {
                    event.setCancelled(true);
                    return;
                }

                if (type == PacketType.Play.Client.HELD_ITEM_SLOT) {
                    try {
                        int slot = packet.getIntegers().readSafely(0);
                        if (slot < 0 || slot > 8) {
                            event.setCancelled(true);
                            guard.kickPlayer(player, cfg.getKickMessageExploit());
                            guard.alertAdmins(player.getName() + " invalid held item slot (" + slot + ") exploit blocked - KICK");
                            guard.getStatsManager().recordIncident(player.getName(), "CRASH", "Invalid Held Item Slot: " + slot);
                            return;
                        }
                    } catch (Throwable ignored) {}
                }

                if (type == PacketType.Play.Client.BLOCK_PLACE) {
                    if (guard.getFastInteractGuard().checkFastPlace(player)) {
                        event.setCancelled(true);
                        return;
                    }
                    try {
                        int direction = packet.getIntegers().readSafely(0);
                        if (direction < 0 || (direction > 5 && direction != 255)) {
                            event.setCancelled(true);
                            return;
                        }
                    } catch (Throwable ignored) {}
                }

                if (type == PacketType.Play.Client.BLOCK_DIG) {
                    try {
                        if (packet.getPlayerDigTypes() != null && packet.getPlayerDigTypes().size() > 0) {
                            com.comphenix.protocol.wrappers.EnumWrappers.PlayerDigType digType = packet.getPlayerDigTypes().readSafely(0);
                            if (digType != null) {
                                if (digType == com.comphenix.protocol.wrappers.EnumWrappers.PlayerDigType.START_DESTROY_BLOCK) {
                                    guard.getFastInteractGuard().recordDigStart(player);
                                } else if (digType == com.comphenix.protocol.wrappers.EnumWrappers.PlayerDigType.STOP_DESTROY_BLOCK) {
                                    if (packet.getBlockPositionModifier() != null && packet.getBlockPositionModifier().size() > 0) {
                                        com.comphenix.protocol.wrappers.BlockPosition pos = packet.getBlockPositionModifier().readSafely(0);
                                        if (pos != null && player.getWorld() != null) {
                                            org.bukkit.block.Block b = player.getWorld().getBlockAt(pos.getX(), pos.getY(), pos.getZ());
                                            if (guard.getFastInteractGuard().checkFastBreak(player, b)) {
                                                event.setCancelled(true);
                                                return;
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    } catch (Throwable ignored) {}
                }

                if (type == PacketType.Play.Client.ARM_ANIMATION) {
                    if (guard.getClickAnalysisGuard().analyzeClick(player, "ARM_ANIMATION")) {
                        event.setCancelled(true);
                        return;
                    }
                }

                if (rateLimiter.checkGlobalPacketRate(player.getUniqueId(), cfg.getMaxPacketsPerSecond())) {
                    event.setCancelled(true);
                    guard.kickPlayer(player, cfg.getKickMessagePacketFlood());
                    guard.alertAdmins(player.getName() + " packet flood limit reached.");
                    guard.getStatsManager().recordIncident(player.getName(), "PACKET_FLOOD", "Global Limit (" + cfg.getMaxPacketsPerSecond() + "+/s)");
                    guard.getDiscordWebhook().sendAlert("Packet Flood Blocked", player.getName(), "Excessive Packet Rate", "Limit: " + cfg.getMaxPacketsPerSecond() + "/s", 0xFF0000);
                    return;
                }

                Map<String, Integer> specificLimits = cfg.getPacketSpecificLimits();
                if (specificLimits.containsKey(typeName)) {
                    int limit = specificLimits.get(typeName);
                    if (rateLimiter.checkSpecificPacketRate(player.getUniqueId(), typeName, limit)) {
                        event.setCancelled(true);
                        guard.alertAdmins(player.getName() + " excessive " + typeName + " packets.");
                        guard.getStatsManager().recordIncident(player.getName(), "PACKET_FLOOD", "Micro Limit: " + typeName);
                        return;
                    }
                }

                if (cfg.isCheckInvalidPositions() && isPositionPacket(type)) {
                    if (checkInvalidMovement(packet, type)) {
                        event.setCancelled(true);
                        guard.kickPlayer(player, cfg.getKickMessageExploit());
                        guard.alertAdmins(player.getName() + " invalid coordinates/angles (Crash exploit blocked)");
                        guard.getStatsManager().recordIncident(player.getName(), "CRASH", "NaN/Infinity Position Exploit");
                        guard.getDiscordWebhook().sendAlert("Crash Exploit Blocked", player.getName(), "Invalid Coordinate/Angle Packet", "Packet: " + typeName, 0xFF0000);
                        return;
                    }
                }

                if (cfg.isCheckVehicleMovement() && (typeName.equals("VEHICLE_MOVE") || typeName.equals("STEER_VEHICLE"))) {
                    if (checkVehicleMovement(packet)) {
                        event.setCancelled(true);
                        guard.alertAdmins(player.getName() + " invalid vehicle movement packet.");
                        guard.getStatsManager().recordIncident(player.getName(), "CRASH", "Invalid Vehicle Packet");
                        return;
                    }
                }

                if (type == PacketType.Play.Client.CUSTOM_PAYLOAD) {
                    if (checkCustomPayload(packet, player, cfg, nbtValidator)) {
                        event.setCancelled(true);
                        guard.getStatsManager().recordIncident(player.getName(), "NBT_EXPLOIT", "Payload/Book Overflow");
                        return;
                    }
                }

                if (typeName.equals("UPDATE_SIGN")) {
                    if (checkUpdateSign(packet, player, cfg, nbtValidator)) {
                        event.setCancelled(true);
                        guard.getStatsManager().recordIncident(player.getName(), "NBT_EXPLOIT", "Sign Crash Packet");
                        return;
                    }
                }

                if (type == PacketType.Play.Client.TAB_COMPLETE && cfg.isBlockTabComplete()) {
                    event.setCancelled(true);
                    return;
                }

                if (type == PacketType.Play.Client.SET_CREATIVE_SLOT && cfg.isBlockCreativePackets()) {
                    if (player.getGameMode() != GameMode.CREATIVE) {
                        event.setCancelled(true);
                        guard.kickPlayer(player, cfg.getKickMessageExploit());
                        guard.alertAdmins(player.getName() + " Creative packet injection (Survival mode)");
                        guard.getStatsManager().recordIncident(player.getName(), "NBT_EXPLOIT", "Creative Slot Injection");
                        guard.getDiscordWebhook().sendAlert("Illegal Item Injection", player.getName(), "Survival Mode Creative Packet", "Packet: SET_CREATIVE_SLOT", 0xFF5500);
                        return;
                    }
                    try {
                        if (packet.getItemModifier() != null && packet.getItemModifier().size() > 0) {
                            ItemStack item = packet.getItemModifier().readSafely(0);
                            if (item != null && nbtValidator.isIllegalItem(item)) {
                                event.setCancelled(true);
                                guard.alertAdmins(player.getName() + " Creative illegal NBT item blocked.");
                                guard.getStatsManager().recordIncident(player.getName(), "NBT_EXPLOIT", "Creative Illegal Item");
                                return;
                            }
                        }
                    } catch (Throwable ignored) {}
                }

                if (type == PacketType.Play.Client.WINDOW_CLICK && cfg.isCheckWindowClicks()) {
                    if (checkInvalidWindowClick(packet, nbtValidator)) {
                        event.setCancelled(true);
                        guard.kickPlayer(player, cfg.getKickMessageExploit());
                        guard.alertAdmins(player.getName() + " invalid window click packet - KICK");
                        guard.getStatsManager().recordIncident(player.getName(), "CRASH", "Invalid Window Click");
                        return;
                    }
                }

                if (type == PacketType.Play.Client.USE_ENTITY) {
                    if (guard.getClickAnalysisGuard().analyzeClick(player, "USE_ENTITY")) {
                        event.setCancelled(true);
                        return;
                    }
                    try {
                        int targetId = packet.getIntegers().readSafely(0);
                        if (targetId == player.getEntityId()) {
                            event.setCancelled(true);
                            return;
                        }

                        Entity target = packet.getEntityModifier(event).readSafely(0);
                        if (target != null) {
                            if (!target.getWorld().equals(player.getWorld()) || player.getLocation().distanceSquared(target.getLocation()) > 49.0) {
                                event.setCancelled(true);
                                guard.alertAdmins(player.getName() + " excessive distance entity interaction.");
                                guard.getStatsManager().recordIncident(player.getName(), "CRASH", "Distant USE_ENTITY");
                                return;
                            }
                        }
                    } catch (Throwable ignored) {}
                }

                if (typeName.equals("USE_ITEM") || typeName.equals("BLOCK_PLACE_HELD")) {
                    if (guard.getClickAnalysisGuard().analyzeClick(player, "USE_ITEM")) {
                        event.setCancelled(true);
                    }
                }
            }
        });
    }

    private void addPacketIfAvailable(List<PacketType> list, String name) {
        try {
            java.lang.reflect.Field field = PacketType.Play.Client.class.getField(name);
            PacketType type = (PacketType) field.get(null);
            if (type != null && type.isSupported()) {
                list.add(type);
            }
        } catch (Throwable ignored) {}
    }

    private boolean isPositionPacket(PacketType type) {
        return type == PacketType.Play.Client.POSITION
                || type == PacketType.Play.Client.POSITION_LOOK
                || type == PacketType.Play.Client.FLYING
                || type == PacketType.Play.Client.LOOK;
    }

    private boolean checkInvalidMovement(PacketContainer packet, PacketType type) {
        try {
            if (type == PacketType.Play.Client.POSITION || type == PacketType.Play.Client.POSITION_LOOK) {
                double x = packet.getDoubles().readSafely(0);
                double y = packet.getDoubles().readSafely(1);
                double z = packet.getDoubles().readSafely(2);

                if (Double.isNaN(x) || Double.isNaN(y) || Double.isNaN(z)
                        || Double.isInfinite(x) || Double.isInfinite(y) || Double.isInfinite(z)) {
                    return true;
                }

                double limit = 3.0E7;
                if (Math.abs(x) > limit || Math.abs(y) > limit || Math.abs(z) > limit) {
                    return true;
                }
            }

            if (type == PacketType.Play.Client.LOOK || type == PacketType.Play.Client.POSITION_LOOK) {
                float yaw = packet.getFloat().readSafely(0);
                float pitch = packet.getFloat().readSafely(1);

                if (Float.isNaN(yaw) || Float.isNaN(pitch) || Float.isInfinite(yaw) || Float.isInfinite(pitch)) {
                    return true;
                }

                if (Math.abs(pitch) > 90.0f) {
                    return true;
                }
            }
        } catch (Throwable e) {
            return true;
        }
        return false;
    }

    private boolean checkVehicleMovement(PacketContainer packet) {
        try {
            double x = packet.getDoubles().readSafely(0);
            double y = packet.getDoubles().readSafely(1);
            double z = packet.getDoubles().readSafely(2);

            if (Double.isNaN(x) || Double.isNaN(y) || Double.isNaN(z)
                    || Double.isInfinite(x) || Double.isInfinite(y) || Double.isInfinite(z)) {
                return true;
            }

            double limit = 3.0E7;
            if (Math.abs(x) > limit || Math.abs(y) > limit || Math.abs(z) > limit) {
                return true;
            }
        } catch (Throwable ignored) {}
        return false;
    }

    private boolean checkCustomPayload(PacketContainer packet, Player player, ConfigManager cfg, NbtValidator nbtValidator) {
        try {
            String channel = packet.getStrings().readSafely(0);
            if (channel == null) return false;

            if (channel.contains("..") || channel.length() > 128) {
                guard.alertAdmins(player.getName() + " suspicious payload channel: " + channel);
                return true;
            }

            byte[] data = null;
            try {
                Object rawData = packet.getByteArrays().readSafely(0);
                if (rawData != null) data = (byte[]) rawData;
            } catch (Throwable ignored) {}

            if (data != null) {
                if (data.length > cfg.getMaxCustomPayloadSize()) {
                    guard.kickPlayer(player, cfg.getKickMessageExploit());
                    guard.alertAdmins(player.getName() + " payload overflow (" + data.length + " bytes, channel: " + channel + ")");
                    return true;
                }

                if (cfg.isBlockTranslationCrash() && data.length < 4096) {
                    String payloadStr = new String(data, java.nio.charset.StandardCharsets.UTF_8);
                    if (nbtValidator.containsTranslationCrash(payloadStr)) {
                        guard.kickPlayer(player, cfg.getKickMessageExploit());
                        guard.alertAdmins(player.getName() + " payload translation crash attack");
                        return true;
                    }
                }
            }

            String lower = channel.toLowerCase();
            if (lower.contains("bedit") || lower.contains("bsign") || lower.contains("bopen")) {
                ItemStack hand = Compatibility.getItemInHand(player);
                if (hand == null || !hand.getType().name().contains("BOOK")) {
                    guard.alertAdmins(player.getName() + " sent book payload without holding a book (" + channel + ")");
                    return true;
                }
                if (data != null && data.length > 8192) {
                    guard.kickPlayer(player, cfg.getKickMessageExploit());
                    guard.alertAdmins(player.getName() + " Book/Lectern exploit (" + data.length + " bytes)");
                    return true;
                }
            }

            if (lower.contains("advcdm") && !guard.getAdminManager().isPlayerAdmin(player)) {
                guard.alertAdmins(player.getName() + " unauthorized command block payload (" + channel + ")");
                return true;
            }

            if (lower.contains("item_name") || lower.contains("lectern")) {
                if (data != null && data.length > 8192) {
                    guard.kickPlayer(player, cfg.getKickMessageExploit());
                    guard.alertAdmins(player.getName() + " Lectern/Item exploit (" + data.length + " bytes)");
                    return true;
                }
            }
        } catch (Throwable e) {
            return true;
        }
        return false;
    }

    private boolean checkUpdateSign(PacketContainer packet, Player player, ConfigManager cfg, NbtValidator nbtValidator) {
        try {
            if (packet.getChatComponentArrays() != null && packet.getChatComponentArrays().size() > 0) {
                WrappedChatComponent[] lines = packet.getChatComponentArrays().readSafely(0);
                if (lines != null) {
                    for (WrappedChatComponent line : lines) {
                        if (line != null) {
                            String json = line.getJson();
                            if (json != null) {
                                if (json.length() > 256 || (cfg.isBlockTranslationCrash() && nbtValidator.containsTranslationCrash(json))) {
                                    guard.alertAdmins(player.getName() + " invalid sign packet (UPDATE_SIGN blocked)");
                                    return true;
                                }

                                if (nbtValidator.containsLog4jExploit(json)) {
                                    guard.kickPlayer(player, cfg.getKickMessageExploit());
                                    guard.alertAdmins(player.getName() + " sign Log4J attack blocked.");
                                    return true;
                                }

                                String lower = json.toLowerCase();
                                if (lower.contains("clickevent") && lower.contains("run_command") && !guard.getAdminManager().isPlayerAdmin(player)) {
                                    guard.alertAdmins(player.getName() + " unauthorized 'run_command' JSON injection blocked.");
                                    return true;
                                }
                            }
                        }
                    }
                }
            }
        } catch (Throwable ignored) {}
        return false;
    }

    private boolean checkInvalidWindowClick(PacketContainer packet, NbtValidator nbtValidator) {
        try {
            int slot = packet.getIntegers().readSafely(1);
            if (slot < -999 || slot > 256) {
                return true;
            }

            int button = packet.getIntegers().readSafely(2);
            int mode = packet.getIntegers().readSafely(3);
            if (mode < 0 || mode > 6 || button < 0 || button > 40) {
                return true;
            }

            if (packet.getItemModifier() != null && packet.getItemModifier().size() > 0) {
                ItemStack item = packet.getItemModifier().readSafely(0);
                if (item != null && nbtValidator.isIllegalItem(item)) {
                    return true;
                }
            }
        } catch (Throwable ignored) {}
        return false;
    }

    private static String sanitizeForLog(String text) {
        if (text == null) return "null";
        String safe = text.replace("$", "＄").replace("{", "｛").replace("}", "｝");
        if (safe.length() > 100) safe = safe.substring(0, 100) + "...";
        return safe;
    }
}
