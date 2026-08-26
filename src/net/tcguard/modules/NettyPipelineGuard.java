package net.tcguard.modules;

import io.netty.channel.Channel;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPipeline;
import net.tcguard.TCGuard;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class NettyPipelineGuard implements Listener {

    private final TCGuard plugin;
    private final Map<UUID, Channel> injectedChannels = new ConcurrentHashMap<>();
    private final Map<String, Long> lastWarningMap = new ConcurrentHashMap<>();
    private static final String HANDLER_NAME = "tcguard_pipeline_handler";

    public NettyPipelineGuard(TCGuard plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerJoin(PlayerJoinEvent event) {
        if (!plugin.getConfig().getBoolean("packet-protection.netty-pipeline-shield", true)) return;
        injectPlayer(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        uninjectPlayer(event.getPlayer());
    }

    public void injectPlayer(Player player) {
        if (player == null) return;
        try {
            Channel channel = getPlayerChannel(player);
            if (channel == null || !channel.isOpen()) return;

            ChannelPipeline pipeline = channel.pipeline();
            if (pipeline == null || pipeline.get(HANDLER_NAME) != null) return;

            ChannelDuplexHandler duplexHandler = new ChannelDuplexHandler() {
                @Override
                public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
                    try {
                        super.channelRead(ctx, msg);
                    } catch (Throwable t) {
                        handleNettyException(player, ctx, t);
                    }
                }

                @Override
                public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
                    handleNettyException(player, ctx, cause);
                }
            };

            if (pipeline.get("packet_handler") != null) {
                pipeline.addBefore("packet_handler", HANDLER_NAME, duplexHandler);
            } else {
                pipeline.addLast(HANDLER_NAME, duplexHandler);
            }

            injectedChannels.put(player.getUniqueId(), channel);
        } catch (Exception e) {
            plugin.getLogger().fine("[NettyGuard] Error injecting " + player.getName() + ": " + e.getMessage());
        }
    }

    public void uninjectPlayer(Player player) {
        if (player == null) return;
        UUID uuid = player.getUniqueId();
        Channel channel = injectedChannels.remove(uuid);
        lastWarningMap.remove(player.getName().toLowerCase());

        if (channel != null && channel.isOpen()) {
            channel.eventLoop().execute(() -> {
                try {
                    ChannelPipeline pipeline = channel.pipeline();
                    if (pipeline != null && pipeline.get(HANDLER_NAME) != null) {
                        pipeline.remove(HANDLER_NAME);
                    }
                } catch (Exception ignored) {}
            });
        }
    }

    public void uninjectAll() {
        for (Map.Entry<UUID, Channel> entry : injectedChannels.entrySet()) {
            Channel channel = entry.getValue();
            if (channel != null && channel.isOpen()) {
                try {
                    ChannelPipeline pipeline = channel.pipeline();
                    if (pipeline != null && pipeline.get(HANDLER_NAME) != null) {
                        pipeline.remove(HANDLER_NAME);
                    }
                } catch (Exception ignored) {}
            }
        }
        injectedChannels.clear();
        lastWarningMap.clear();
    }

    private void handleNettyException(Player player, ChannelHandlerContext ctx, Throwable t) {
        if (t == null) return;
        String name = t.getClass().getSimpleName();
        String msg = t.getMessage() != null ? t.getMessage() : "";

        if (msg.contains("Connection reset") || msg.contains("forcibly closed")
                || name.contains("ClosedChannelException") || name.contains("QuietException")
                || msg.contains("Broken pipe")) {
            if (ctx != null && ctx.channel().isOpen()) {
                ctx.close();
            }
            return;
        }

        String playerName = (player != null && player.getName() != null) ? player.getName() : "Unknown";
        long now = System.currentTimeMillis();
        Long lastWarn = lastWarningMap.get(playerName.toLowerCase());

        if (lastWarn == null || (now - lastWarn) > 10000L) {
            lastWarningMap.put(playerName.toLowerCase(), now);
            plugin.getLogger().warning("[NettyGuard] Corrupt frame exception intercepted for " + playerName + " (" + name + ": " + msg + ")");
            plugin.getStatsManager().recordIncident(playerName, "NETTY_FRAME", "Corrupt Frame: " + name);
        }

        if (ctx != null && ctx.channel().isOpen()) {
            ctx.close();
        }
    }

    private Channel getPlayerChannel(Player player) {
        if (player == null) return null;
        try {
            Method getHandleMethod = player.getClass().getMethod("getHandle");
            Object entityPlayer = getHandleMethod.invoke(player);
            if (entityPlayer == null) return null;

            Object playerConnection = findFieldValueByTypeOrName(entityPlayer, "PlayerConnection", "connection", "playerConnection", "c", "b");
            if (playerConnection == null) return null;

            Object networkManager = findFieldValueByTypeOrName(playerConnection, "NetworkManager", "networkManager", "connection", "h", "e");
            if (networkManager == null) return null;

            for (Class<?> cl = networkManager.getClass(); cl != null && cl != Object.class; cl = cl.getSuperclass()) {
                for (Field f : cl.getDeclaredFields()) {
                    if (Channel.class.isAssignableFrom(f.getType())) {
                        f.setAccessible(true);
                        Object ch = f.get(networkManager);
                        if (ch instanceof Channel) {
                            return (Channel) ch;
                        }
                    }
                }
            }
        } catch (Throwable ignored) {}
        return null;
    }

    private Object findFieldValueByTypeOrName(Object target, String typeKeyword, String... nameKeywords) {
        if (target == null) return null;
        for (Class<?> cl = target.getClass(); cl != null && cl != Object.class; cl = cl.getSuperclass()) {
            for (Field f : cl.getDeclaredFields()) {
                if (typeKeyword != null && f.getType().getSimpleName().contains(typeKeyword)) {
                    try {
                        f.setAccessible(true);
                        Object val = f.get(target);
                        if (val != null) return val;
                    } catch (Throwable ignored) {}
                }
                for (String name : nameKeywords) {
                    if (f.getName().equalsIgnoreCase(name)) {
                        try {
                            f.setAccessible(true);
                            Object val = f.get(target);
                            if (val != null) return val;
                        } catch (Throwable ignored) {}
                    }
                }
            }
        }
        return null;
    }
}
