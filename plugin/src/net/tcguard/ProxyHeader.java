package net.tcguard;

import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.lang.reflect.Field;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

final class ProxyHeader extends ChannelInboundHandlerAdapter {
    static final String NAME = "tcguard_proxy";
    private static final byte[] SIGNATURE = {0x0D, 0x0A, 0x0D, 0x0A, 0x00, 0x0D, 0x0A, 0x51, 0x55, 0x49, 0x54, 0x0A};
    private static final int HEADER = 16, TLV_SIGNATURE = 0xE0, MAX_LENGTH = 512;
    private static final long MAX_AGE_MILLIS = 30_000;
    private static final Map<ByteBuffer, Long> SEEN = new ConcurrentHashMap<>();

    private final byte[] secret;
    private ByteBuf buffer;

    ProxyHeader(byte[] secret) {
        this.secret = secret;
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        ByteBuf in = (ByteBuf) msg;
        if (buffer == null) buffer = ctx.alloc().buffer(HEADER + 36);
        buffer.writeBytes(in);
        in.release();
        if (buffer.readableBytes() < HEADER) return;

        for (int i = 0; i < SIGNATURE.length; i++) {
            if (buffer.getByte(i) != SIGNATURE[i]) {
                ctx.close();
                return;
            }
        }
        int length = buffer.getUnsignedShort(14);
        if ((buffer.getByte(12) & 0xF0) != 0x20 || length > MAX_LENGTH) {
            ctx.close();
            return;
        }
        if (buffer.readableBytes() < HEADER + length) return;

        try {
            boolean proxied = (buffer.getByte(12) & 0x0F) == 1;
            int address = proxied ? addressLength(buffer.getByte(13), length) : 0;
            if (secret != null && !signed(buffer, address, length, System.currentTimeMillis())) {
                ctx.close();
                return;
            }
            if (proxied) setAddress(ctx.channel(), source(buffer, address));
        } catch (ReflectiveOperationException | GeneralSecurityException | UnknownHostException | IllegalArgumentException e) {
            ctx.close();
            return;
        }
        buffer.skipBytes(HEADER + length);
        ByteBuf rest = buffer;
        buffer = null;
        ctx.pipeline().remove(this);
        if (rest.isReadable()) ctx.fireChannelRead(rest);
        else rest.release();
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        if (buffer != null) buffer.release();
        buffer = null;
        ctx.fireChannelInactive();
    }

    private static int addressLength(byte family, int length) {
        int size = family == 0x11 ? 12 : family == 0x21 ? 36 : 0;
        if (size == 0 || length < size) throw new IllegalArgumentException("address family " + family);
        return size;
    }

    private static InetSocketAddress source(ByteBuf b, int addressLength) throws UnknownHostException {
        byte[] ip = new byte[(addressLength - 4) / 2];
        b.getBytes(HEADER, ip);
        return new InetSocketAddress(InetAddress.getByAddress(ip), b.getUnsignedShort(HEADER + ip.length * 2));
    }

    boolean signed(ByteBuf b, int addressLength, int length, long now) throws GeneralSecurityException {
        int end = HEADER + length;
        for (int at = HEADER + addressLength; at + 3 <= end; ) {
            int type = b.getUnsignedByte(at), size = b.getUnsignedShort(at + 1);
            if (at + 3 + size > end) return false;
            if (type != TLV_SIGNATURE) {
                at += 3 + size;
                continue;
            }
            if (size != 40) return false;
            long stamp = b.getLong(at + 3);
            if (Math.abs(now - stamp) > MAX_AGE_MILLIS) return false;

            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            byte[] covered = new byte[addressLength + 8];
            b.getBytes(HEADER, covered, 0, addressLength);
            b.getBytes(at + 3, covered, addressLength, 8);
            byte[] sent = new byte[32];
            b.getBytes(at + 11, sent);
            if (!MessageDigest.isEqual(mac.doFinal(covered), sent)) return false;

            SEEN.values().removeIf(seen -> now - seen > MAX_AGE_MILLIS * 2);
            return SEEN.putIfAbsent(ByteBuffer.wrap(sent), now) == null;
        }
        return false;
    }

    static void setAddress(Channel channel, SocketAddress address) throws ReflectiveOperationException {
        socketField(channel).set(channel.pipeline().get("packet_handler"), address);
    }

    static SocketAddress address(Channel channel) {
        try {
            return (SocketAddress) socketField(channel).get(channel.pipeline().get("packet_handler"));
        } catch (ReflectiveOperationException | RuntimeException e) {
            return channel.remoteAddress();
        }
    }

    private static Field socketField(Channel channel) throws NoSuchFieldException {
        ChannelHandler manager = channel.pipeline().get("packet_handler");
        if (manager == null) throw new NoSuchFieldException("packet_handler");
        for (Field f : manager.getClass().getDeclaredFields()) {
            if (f.getType() == SocketAddress.class) {
                f.setAccessible(true);
                return f;
            }
        }
        throw new NoSuchFieldException("SocketAddress in " + manager.getClass().getName());
    }
}
