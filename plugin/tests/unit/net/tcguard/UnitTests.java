package net.tcguard;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.PluginDescriptionFile;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

public final class UnitTests {
    private static int passed, failed;

    public static void main(String[] args) throws Exception {
        log4j();
        frames();
        commands();
        proxyHeader();
        originLock();
        resources();
        System.out.println(passed + "/" + (passed + failed) + " passed");
        if (failed > 0) System.exit(1);
    }

    private static void check(String name, boolean ok) {
        if (ok) passed++;
        else failed++;
        if (!ok) System.out.println("FAIL " + name);
    }

    private static void log4j() {
        for (String attack : new String[]{
                "${jndi:ldap://x/a}", "${JNDI:LDAP://x/a}", "${${lower:j}ndi:ldap://x}", "${j${::-n}di:rmi://x}",
                "$%7Bjndi:ldap://x%7D", "\\u0024{jndi:ldap://x}", "${ jndi : ldap://x }", "${env:AWS_SECRET}",
                "abc ${j-n-d-i:ldap://x}", "${date:yyyy}${jndi:dns://x}"}) {
            check("log4j detects " + attack, Log4jFilter.matches(attack));
        }
        for (String harmless : new String[]{"merhaba", "fiyat 5$ {indirim}", "%50 indirim", "{jndi:ldap}", "${}", "$x{jndi:x}", "100% dogru: evet"}) {
            check("log4j ignores " + harmless, !Log4jFilter.matches(harmless));
        }
        StringBuilder worst = new StringBuilder();
        for (int i = 0; i < 8000; i++) worst.append("${j");
        long best = Long.MAX_VALUE;
        for (int run = 0; run < 3; run++) {
            long start = System.nanoTime();
            Log4jFilter.matches(worst.toString());
            best = Math.min(best, System.nanoTime() - start);
        }
        check("log4j scan of 24 KB stays under 50 ms", best < 50_000_000L);
    }

    private static String inspect(ByteBuf frame) {
        return FrameInspector.inspect(frame, new FrameInspector.Limits(), new FrameInspector.Frame());
    }

    private static ByteBuf click(int slot, int button, int mode) {
        return packet(0x0E).writeByte(0).writeShort(slot).writeByte(button).writeShort(1).writeByte(mode).writeShort(-1);
    }

    private static void frames() {
        check("small chat passes", inspect(packet(0x01).writeBytes(new byte[40])) == null);
        check("9 KB chat is refused", inspect(packet(0x01).writeBytes(new byte[9000])) != null);
        check("empty frame passes to the decoder", inspect(Unpooled.buffer()) == null);
        check("truncated item passes to the decoder", inspect(packet(0x10).writeShort(0).writeShort(1)) == null);

        ByteBuf plain = creative();
        plain.writeByte(0);
        check("item without nbt passes", inspect(plain) == null);

        ByteBuf deep = creative();
        deep.writeByte(10).writeShort(0);
        for (int i = 0; i < 40; i++) deep.writeByte(10).writeShort(1).writeByte('a');
        for (int i = 0; i < 41; i++) deep.writeByte(0);
        check("40-level nbt is refused", String.valueOf(inspect(deep)).contains("deeper"));

        ByteBuf list = creative();
        list.writeByte(10).writeShort(0).writeByte(9).writeShort(1).writeByte('l').writeByte(1).writeInt(5000).writeBytes(new byte[5000]).writeByte(0);
        check("list of 5000 is refused", String.valueOf(inspect(list)).contains("list"));

        ByteBuf negative = creative();
        negative.writeByte(10).writeShort(0).writeByte(7).writeShort(1).writeByte('b').writeInt(-1).writeByte(0);
        check("negative array length is refused", String.valueOf(inspect(negative)).contains("array"));

        check("book page of 250 bytes passes", inspect(book(50, 250)) == null);
        check("book page of 900 bytes is refused", String.valueOf(inspect(book(1, 900))).contains("pages"));

        ByteBuf sign = packet(0x12).writeLong(0);
        sign.writeByte(0x90).writeByte(0x03).writeBytes(new byte[400]).writeByte(0).writeByte(0).writeByte(0);
        check("sign line of 400 bytes is refused", String.valueOf(inspect(sign)).contains("sign"));

        ByteBuf translated = packet(0x12).writeLong(0);
        byte[] line = "{\"translate\":\"%1$s%1$s\",\"with\":[\"a\"]}".getBytes(StandardCharsets.UTF_8);
        translated.writeByte(line.length).writeBytes(line).writeByte(0).writeByte(0).writeByte(0);
        check("sign line with a translate component is refused", String.valueOf(inspect(translated)).contains("translate"));
        check("translate key is found", FrameInspector.translates("{\"translate\" : \"a\"}"));
        check("escaped translate key is found", FrameInspector.translates("{\"\\u0074ranslate\":\"a\"}"));
        check("the word translate in text is ignored", !FrameInspector.translates("{\"text\":\"translate this\"}"));
        check("a quoted translate inside text is ignored", !FrameInspector.translates("{\"text\":\"\\\"translate\\\":\"}"));

        check("ordinary window click passes", inspect(click(12, 0, 0)) == null);
        check("click outside the window passes", inspect(click(-999, 0, 0)) == null);
        check("window click on slot 30000 is refused", String.valueOf(inspect(click(30000, 0, 0))).contains("slot 30000"));
        check("window click with mode 9 is refused", String.valueOf(inspect(click(3, 0, 9))).contains("mode 9"));
        check("vehicle input of NaN is refused", inspect(packet(0x0C).writeFloat(Float.NaN).writeFloat(0).writeByte(0)) != null);
        check("vehicle input of 0.98 passes", inspect(packet(0x0C).writeFloat(0.98f).writeFloat(0).writeByte(0)) == null);

        byte[] lookup = "${jndi:ldap://x/a}".getBytes(StandardCharsets.UTF_8);
        check("log4j lookup in chat is refused", String.valueOf(inspect(packet(0x01).writeByte(lookup.length).writeBytes(lookup))).contains("log4j"));
        check("payload channel of 30 bytes is refused", inspect(packet(0x17).writeByte(30).writeBytes(new byte[30])) != null);
        FrameInspector.Frame frame = new FrameInspector.Frame();
        FrameInspector.inspect(packet(0x17).writeByte(8).writeBytes("MC|Brand".getBytes(StandardCharsets.UTF_8)).writeBytes(new byte[8]), new FrameInspector.Limits(), frame);
        check("payload is counted under its channel", frame.name().equals("MC|Brand"));
        FrameInspector.inspect(packet(0x0A), new FrameInspector.Limits(), frame = new FrameInspector.Frame());
        check("arm animation is counted by name", frame.name().equals("ARM_ANIMATION"));

        ByteBuf original = book(3, 100);
        int reader = original.readerIndex(), writer = original.writerIndex();
        inspect(original);
        check("inspection leaves the buffer untouched", original.readerIndex() == reader && original.writerIndex() == writer);
    }

    private static ByteBuf packet(int id) {
        return Unpooled.buffer().writeByte(id);
    }

    private static ByteBuf creative() {
        return packet(0x10).writeShort(36).writeShort(1).writeByte(1).writeShort(0);
    }

    private static ByteBuf book(int pages, int bytes) {
        ByteBuf buf = creative();
        buf.writeByte(10).writeShort(0).writeByte(9).writeShort(5).writeBytes("pages".getBytes(StandardCharsets.UTF_8)).writeByte(8).writeInt(pages);
        for (int i = 0; i < pages; i++) buf.writeShort(bytes).writeBytes(new byte[bytes]);
        return buf.writeByte(0);
    }

    private static void commands() {
        check("namespace is stripped", CommandFilter.normalize("/WorldEdit:/calc  1+1").equals("//calc 1+1"));
        check("colon in arguments is kept", CommandFilter.normalize("/msg Ali 12:30").equals("/msg ali 12:30"));
        check("exact entry matches with arguments", CommandFilter.matches("//calc 1+1", "//calc"));
        check("exact entry does not match a longer label", !CommandFilter.matches("//calculator", "//calc"));
        check("prefix entry matches", CommandFilter.matches("/mv ^(.*)*$", "/mv ^*"));
        check("prefix entry does not match other subcommands", !CommandFilter.matches("/mv list", "/mv ^*"));
    }

    public static final class NetworkManager extends io.netty.channel.ChannelInboundHandlerAdapter {
        java.net.SocketAddress address;
        final java.util.List<Object> received = new java.util.ArrayList<>();

        @Override
        public void channelRead(io.netty.channel.ChannelHandlerContext ctx, Object msg) {
            received.add(msg);
        }
    }

    private static io.netty.channel.embedded.EmbeddedChannel proxied(NetworkManager manager) {
        io.netty.channel.embedded.EmbeddedChannel channel = new io.netty.channel.embedded.EmbeddedChannel(new io.netty.channel.ChannelInboundHandlerAdapter());
        channel.pipeline().addFirst("packet_handler", manager).addFirst(ProxyHeader.NAME, new ProxyHeader(null));
        return channel;
    }

    private static ByteBuf v2(int command, int family, byte[] body) {
        ByteBuf b = Unpooled.buffer().writeBytes(new byte[]{0x0D, 0x0A, 0x0D, 0x0A, 0x00, 0x0D, 0x0A, 0x51, 0x55, 0x49, 0x54, 0x0A});
        return b.writeByte(0x20 | command).writeByte(family).writeShort(body.length).writeBytes(body);
    }

    private static ByteBuf signedHeader(String secret, long stamp, byte[] address) throws Exception {
        javax.crypto.Mac mac = javax.crypto.Mac.getInstance("HmacSHA256");
        mac.init(new javax.crypto.spec.SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        mac.update(address);
        byte[] time = java.nio.ByteBuffer.allocate(8).putLong(stamp).array();
        byte[] body = Unpooled.buffer().writeBytes(address).writeByte(0xE0).writeShort(40).writeBytes(time).writeBytes(mac.doFinal(time)).array();
        return v2(address.length == 0 ? 0 : 1, address.length == 0 ? 0 : 0x11, java.util.Arrays.copyOf(body, address.length + 43));
    }

    private static boolean accepts(String secret, ByteBuf header) {
        NetworkManager manager = new NetworkManager();
        io.netty.channel.embedded.EmbeddedChannel channel = new io.netty.channel.embedded.EmbeddedChannel(new io.netty.channel.ChannelInboundHandlerAdapter());
        channel.pipeline().addFirst("packet_handler", manager).addFirst(ProxyHeader.NAME, new ProxyHeader(secret.getBytes(StandardCharsets.UTF_8)));
        channel.writeInbound(header);
        return channel.isOpen();
    }

    private static void originLock() throws Exception {
        byte[] ipv4 = {(byte) 203, 0, 113, 7, 10, 0, 0, 1, (byte) 0xC7, 0x38, 0x63, (byte) 0xDD};
        long now = System.currentTimeMillis();
        check("signed header is accepted", accepts("key", signedHeader("key", now, ipv4)));
        check("signed LOCAL header is accepted", accepts("key", signedHeader("key", now + 1, new byte[0])));
        check("unsigned header is refused", !accepts("key", v2(1, 0x11, ipv4)));
        check("header signed with another secret is refused", !accepts("key", signedHeader("other", now + 2, ipv4)));
        check("signature older than 30 s is refused", !accepts("key", signedHeader("key", now - 31_000, ipv4)));
        check("signature from the future is refused", !accepts("key", signedHeader("key", now + 31_000, ipv4)));
        ByteBuf replay = signedHeader("key", now + 3, ipv4);
        check("first use of a signature is accepted", accepts("key", replay.copy()));
        check("replayed signature is refused", !accepts("key", replay));
        ByteBuf tampered = signedHeader("key", now + 4, ipv4);
        tampered.setByte(16, 198);
        check("signature does not fit a changed address", !accepts("key", tampered));
        check("no header at all is refused", !accepts("key", Unpooled.buffer().writeBytes(new byte[]{16, 0, 47, 9, 'l', 'o', 'c', 'a', 'l', 'h', 'o', 's', 't', 0x63, (byte) 0xDD, 2})));
    }

    private static void proxyHeader() {
        byte[] ipv4 = {(byte) 203, 0, 113, 7, 10, 0, 0, 1, (byte) 0xC7, 0x38, 0x63, (byte) 0xDD};
        NetworkManager manager = new NetworkManager();
        io.netty.channel.embedded.EmbeddedChannel channel = proxied(manager);
        ByteBuf header = v2(1, 0x11, ipv4).writeBytes(new byte[]{5, 0, 47});
        channel.writeInbound(header.readBytes(10));
        channel.writeInbound(header);
        check("proxy header sets the client address", "/203.0.113.7:51000".equals(String.valueOf(manager.address)));
        check("proxy header removes itself", channel.pipeline().get(ProxyHeader.NAME) == null);
        check("bytes after the header reach the server", manager.received.size() == 1 && ((ByteBuf) manager.received.get(0)).readableBytes() == 3);

        NetworkManager local = new NetworkManager();
        io.netty.channel.embedded.EmbeddedChannel health = proxied(local);
        health.writeInbound(v2(0, 0, new byte[0]));
        check("LOCAL header keeps the connection address", local.address == null && health.isOpen());

        io.netty.channel.embedded.EmbeddedChannel spoof = proxied(new NetworkManager());
        spoof.writeInbound(Unpooled.buffer().writeBytes(new byte[]{16, 0, 47, 9, 'l', 'o', 'c', 'a', 'l', 'h', 'o', 's', 't', 0x63, (byte) 0xDD, 2}));
        check("trusted source without a header is closed", !spoof.isOpen());

        io.netty.channel.embedded.EmbeddedChannel unix = proxied(new NetworkManager());
        unix.writeInbound(v2(1, 0x31, new byte[216]));
        check("unsupported address family is closed", !unix.isOpen());
    }

    private static void resources() throws Exception {
        YamlConfiguration config = YamlConfiguration.loadConfiguration(
                new InputStreamReader(UnitTests.class.getResourceAsStream("/config.yml"), StandardCharsets.UTF_8));
        check("config has packet actions", config.getStringList("violations.packets.actions").size() == 3);
        check("config has packet weights", config.getConfigurationSection("violations.packets.weights").getKeys(false).contains("MC|BEdit"));
        check("config has blocked commands", config.getStringList("commands.blocked").contains("//calc"));
        check("op guard is off by default", config.getStringList("op-guard.allowed").isEmpty());
        check("proxy protocol is off by default", config.getStringList("proxy-protocol.trusted").isEmpty());
        PluginDescriptionFile description = new PluginDescriptionFile(UnitTests.class.getResourceAsStream("/plugin.yml"));
        check("plugin.yml loads", description.getMain().equals("net.tcguard.TCGuard") && description.getDepend().isEmpty());
        check("config has the frame limit for newer versions", config.getInt("frames.modern-max-bytes") > 0);
    }
}
