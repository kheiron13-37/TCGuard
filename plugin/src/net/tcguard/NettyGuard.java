package net.tcguard;

import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.util.AttributeKey;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

final class NettyGuard {
    private static final String ACCEPTOR = "tcguard_acceptor", GUARD = "tcguard", PEER = "tcguard_peer";

    private final TCGuard plugin;
    private final boolean legacy;
    private final Set<Channel> servers = ConcurrentHashMap.newKeySet();
    private final Map<Channel, Session> clients = new ConcurrentHashMap<>();
    private AttributeKey<Object> protocol;

    volatile FrameInspector.Limits limits = new FrameInspector.Limits();
    volatile int maxFrame;
    volatile Map<String, Double> weights = Collections.emptyMap();
    volatile double defaultWeight, byteWeight;
    volatile Set<InetAddress> trustedProxies = Collections.emptySet();
    volatile byte[] proxySecret;

    NettyGuard(TCGuard plugin, boolean legacy) {
        this.plugin = plugin;
        this.legacy = legacy;
    }

    void enable() throws ReflectiveOperationException {
        Object server = Bukkit.getServer().getClass().getMethod("getServer").invoke(Bukkit.getServer());
        if (legacy) protocol = protocolKey(Class.forName(server.getClass().getPackage().getName() + ".NetworkManager"));
        discover(server);
        if (servers.isEmpty()) {
            Bukkit.getScheduler().runTask(plugin, () -> {
                try {
                    discover(server);
                } catch (ReflectiveOperationException | RuntimeException e) {
                    plugin.getLogger().severe("Frame guard is off: " + e);
                }
                if (servers.isEmpty()) plugin.getLogger().severe("Frame guard is off: no listening channel found");
            });
        }
        for (Player player : Bukkit.getOnlinePlayers()) bind(player);
    }

    void disable() {
        for (Channel server : servers) remove(server);
        for (Channel client : clients.keySet()) client.eventLoop().execute(() -> remove(client));
        servers.clear();
        clients.clear();
    }

    int connections() {
        return clients.size();
    }

    void bind(Player player) {
        InetSocketAddress address = player.getAddress();
        for (Map.Entry<Channel, Session> client : clients.entrySet()) {
            if (ProxyHeader.address(client.getKey()).equals(address)) client.getValue().player = player;
        }
    }

    void unbind(Player player) {
        for (Session session : clients.values()) {
            if (session.player == player) session.closed = true;
        }
    }

    private void discover(Object server) throws IllegalAccessException {
        for (Object holder : values(server)) {
            if (holder.getClass().getClassLoader() != server.getClass().getClassLoader()) continue;
            List<Object> entries = new ArrayList<>();
            for (Object list : values(holder)) {
                if (!(list instanceof List)) continue;
                synchronized (list) {
                    entries.addAll((List<?>) list);
                }
            }
            boolean listener = false;
            for (Object entry : entries) {
                if (!(entry instanceof ChannelFuture)) continue;
                hook(((ChannelFuture) entry).channel());
                listener = true;
            }
            if (!listener) continue;
            for (Object entry : entries) {
                if (entry == null || entry instanceof ChannelFuture) continue;
                for (Object value : values(entry)) if (value instanceof Channel) attach((Channel) value);
            }
        }
    }

    private static List<Object> values(Object target) throws IllegalAccessException {
        List<Object> values = new ArrayList<>();
        for (Class<?> c = target.getClass(); c != null && c != Object.class; c = c.getSuperclass()) {
            for (Field f : c.getDeclaredFields()) {
                if (Modifier.isStatic(f.getModifiers()) || f.getType().isPrimitive()) continue;
                try {
                    f.setAccessible(true);
                } catch (RuntimeException e) {
                    continue;
                }
                Object value = f.get(target);
                if (value != null) values.add(value);
            }
        }
        return values;
    }

    @SuppressWarnings("unchecked")
    private static AttributeKey<Object> protocolKey(Class<?> networkManager) throws ReflectiveOperationException {
        for (Field f : networkManager.getDeclaredFields()) {
            if (Modifier.isStatic(f.getModifiers()) && f.getType() == AttributeKey.class) {
                f.setAccessible(true);
                return (AttributeKey<Object>) f.get(null);
            }
        }
        throw new NoSuchFieldException("protocol key in " + networkManager.getName());
    }

    private void hook(Channel server) {
        if (server.pipeline().get(ACCEPTOR) != null) return;
        server.pipeline().addFirst(ACCEPTOR, new ChannelInboundHandlerAdapter() {
            @Override
            public void channelRead(ChannelHandlerContext ctx, Object msg) {
                ((Channel) msg).pipeline().addLast(new ChannelInitializer<Channel>() {
                    @Override
                    protected void initChannel(Channel channel) {
                        byte[] secret = proxySecret;
                        if (secret != null || trustedProxies.contains(((InetSocketAddress) channel.remoteAddress()).getAddress())) {
                            channel.pipeline().addFirst(ProxyHeader.NAME, new ProxyHeader(secret));
                        }
                        channel.eventLoop().execute(() -> attach(channel));
                    }
                });
                ctx.fireChannelRead(msg);
            }
        });
        servers.add(server);
    }

    private void attach(Channel channel) {
        ChannelPipeline pipeline = channel.pipeline();
        String anchor = legacy ? "decoder" : "splitter";
        if (!channel.isOpen() || pipeline.get(anchor) == null || pipeline.get(GUARD) != null) return;
        Session session = new Session();
        if (legacy) pipeline.addBefore(anchor, GUARD, new Guard(session, false));
        else pipeline.addAfter(anchor, GUARD, new Guard(session, false));
        clients.put(channel, session);
        channel.closeFuture().addListener(f -> clients.remove(channel));
    }

    private static void remove(Channel channel) {
        ChannelPipeline pipeline = channel.pipeline();
        for (String name : new String[]{ACCEPTOR, GUARD, PEER, ProxyHeader.NAME}) {
            if (pipeline.get(name) != null) pipeline.remove(name);
        }
    }

    private static final class Session {
        volatile Player player;
        volatile boolean closed;
    }

    private final class Guard extends ChannelInboundHandlerAdapter {
        private final Session session;
        private final FrameInspector.Frame info = new FrameInspector.Frame();
        private boolean settled, compressed, play;

        Guard(Session session, boolean settled) {
            this.session = session;
            this.settled = settled;
        }

        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) {
            if (!(msg instanceof ByteBuf) || legacy && !play(ctx)) {
                ctx.fireChannelRead(msg);
                return;
            }
            ByteBuf frame = (ByteBuf) msg;
            Player player = session.player;
            if (session.closed || plugin.isKicking(player)) {
                frame.release();
                return;
            }
            if (!settled && ctx.pipeline().get("decompress") != null) settle(ctx);

            String reason = compressed ? inflate(frame) : inspect(frame);
            if (reason != null) {
                frame.release();
                session.closed = true;
                plugin.reject(player, ctx.channel(), reason);
                return;
            }
            if (!compressed && player != null && !plugin.bypasses(player) && overRate(player, frame)) {
                frame.release();
                return;
            }
            ctx.fireChannelRead(msg);
        }

        private String inspect(ByteBuf frame) {
            if (legacy) {
                info.id = -1;
                info.channel = null;
                return FrameInspector.inspect(frame, limits, info);
            }
            return frame.readableBytes() > maxFrame ? "frame of " + frame.readableBytes() + " bytes" : null;
        }

        private String inflate(ByteBuf frame) {
            int max = legacy ? limits.largestFrame() : maxFrame;
            try {
                ByteBuf in = frame.duplicate();
                int size = FrameInspector.readVarInt(in);
                if (size == 0) return in.readableBytes() > max ? "frame of " + in.readableBytes() + " bytes" : null;
                return size > max ? "compressed frame inflating to " + size + " bytes" : null;
            } catch (RuntimeException e) {
                return null;
            }
        }

        private boolean overRate(Player player, ByteBuf frame) {
            String name = legacy ? info.name() : "PACKET";
            double points = weights.getOrDefault(name, defaultWeight) + frame.readableBytes() * byteWeight;
            Violations violations = plugin.violations();
            violations.add(player, Violations.Category.PACKETS, points, name + " rate");
            return violations.cancels(player, Violations.Category.PACKETS);
        }

        private boolean play(ChannelHandlerContext ctx) {
            return play || (play = "PLAY".equals(String.valueOf(ctx.channel().attr(protocol).get())));
        }

        private void settle(ChannelHandlerContext ctx) {
            settled = true;
            ChannelPipeline pipeline = ctx.pipeline();
            List<String> names = pipeline.names();
            compressed = names.indexOf("decompress") > names.indexOf(ctx.name());
            Guard peer = new Guard(session, true);
            peer.compressed = !compressed;
            if (!compressed) pipeline.addBefore("decompress", PEER, peer);
            else if (legacy) pipeline.addBefore("decoder", PEER, peer);
            else pipeline.addAfter("decompress", PEER, peer);
        }
    }
}
