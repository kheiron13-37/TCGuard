package net.tcguard;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

import java.util.Random;

public final class FrameFuzz {
    private static final FrameInspector.Limits LIMITS = new FrameInspector.Limits();

    public static void main(String[] args) {
        int iterations = args.length > 0 ? Integer.parseInt(args[0]) : 20000;
        long seed = args.length > 1 ? Long.parseLong(args[1]) : System.nanoTime();
        Random random = new Random(seed);
        long slowest = 0;
        int rejectedValid = 0;

        for (int i = 0; i < iterations; i++) {
            byte[] valid = vanillaFrame(random);
            String reason = FrameInspector.inspect(Unpooled.wrappedBuffer(valid), LIMITS, new FrameInspector.Frame());
            if (reason != null) {
                rejectedValid++;
                if (rejectedValid <= 5) System.out.println("valid frame rejected: " + reason);
            }
            byte[] mutated = mutate(valid, random);
            long start = System.nanoTime();
            try {
                FrameInspector.inspect(Unpooled.wrappedBuffer(mutated), LIMITS, new FrameInspector.Frame());
            } catch (RuntimeException e) {
                System.out.println("seed " + seed + " iteration " + i + ": " + e);
                System.exit(1);
            }
            slowest = Math.max(slowest, System.nanoTime() - start);
        }
        System.out.printf("fuzz: %d frames, seed %d, valid rejected %d, slowest mutated frame %.2f ms%n",
                iterations, seed, rejectedValid, slowest / 1e6);
        if (rejectedValid > 0 || slowest > 20_000_000L) System.exit(1);
    }

    private static byte[] vanillaFrame(Random r) {
        ByteBuf b = Unpooled.buffer();
        switch (r.nextInt(3)) {
            case 0: b.writeByte(0x10).writeShort(36 + r.nextInt(9)); break;
            case 1: b.writeByte(0x0E).writeByte(0).writeShort(r.nextInt(45)).writeByte(0).writeShort(r.nextInt(100)).writeByte(0); break;
            default: b.writeByte(0x08).writeLong(r.nextLong()).writeByte(r.nextInt(6));
        }
        b.writeShort(1 + r.nextInt(400)).writeByte(1 + r.nextInt(64)).writeShort(r.nextInt(16));
        if (r.nextInt(4) == 0) {
            b.writeByte(0);
        } else {
            b.writeByte(10).writeShort(0);
            compound(b, r, 1, new int[]{0});
        }
        byte[] out = new byte[b.readableBytes()];
        b.readBytes(out);
        return out;
    }

    private static void compound(ByteBuf b, Random r, int depth, int[] tags) {
        int entries = r.nextInt(depth > 6 ? 2 : 8);
        for (int i = 0; i < entries && tags[0] < 300; i++) {
            int type = depth > 8 ? 1 + r.nextInt(8) : 1 + r.nextInt(11);
            b.writeByte(type);
            name(b, r);
            value(b, r, type, depth, tags);
        }
        b.writeByte(0);
    }

    private static void value(ByteBuf b, Random r, int type, int depth, int[] tags) {
        tags[0]++;
        switch (type) {
            case 1: b.writeByte(r.nextInt()); break;
            case 2: b.writeShort(r.nextInt()); break;
            case 3: b.writeInt(r.nextInt()); break;
            case 4: b.writeLong(r.nextLong()); break;
            case 5: b.writeFloat(r.nextFloat()); break;
            case 6: b.writeDouble(r.nextDouble()); break;
            case 7: { int n = r.nextInt(64); b.writeInt(n).writeBytes(new byte[n]); break; }
            case 8: { int n = r.nextInt(200); b.writeShort(n).writeBytes(new byte[n]); break; }
            case 9: {
                int element = 1 + r.nextInt(depth > 6 ? 8 : 10), n = tags[0] > 250 ? 0 : r.nextInt(20);
                b.writeByte(element).writeInt(n);
                for (int i = 0; i < n; i++) {
                    if (element == 10) {
                        tags[0]++;
                        compound(b, r, depth + 2, tags);
                    } else {
                        value(b, r, element, depth + 1, tags);
                    }
                }
                break;
            }
            case 10: compound(b, r, depth + 1, tags); break;
            default: { int n = r.nextInt(16); b.writeInt(n).writeBytes(new byte[n * 4]); }
        }
    }

    private static void name(ByteBuf b, Random r) {
        int n = 1 + r.nextInt(12);
        b.writeShort(n);
        for (int i = 0; i < n; i++) b.writeByte('a' + r.nextInt(26));
    }

    private static byte[] mutate(byte[] in, Random r) {
        byte[] out = in.clone();
        switch (r.nextInt(4)) {
            case 0:
                for (int i = 0, n = 1 + r.nextInt(8); i < n; i++) out[r.nextInt(out.length)] = (byte) r.nextInt();
                return out;
            case 1:
                return java.util.Arrays.copyOf(out, r.nextInt(out.length));
            case 2:
                int at = r.nextInt(out.length);
                out[at] = (byte) 0xFF;
                if (at + 1 < out.length) out[at + 1] = (byte) 0x7F;
                return out;
            default:
                byte[] longer = java.util.Arrays.copyOf(out, out.length + 64);
                r.nextBytes(java.util.Arrays.copyOfRange(longer, out.length, longer.length));
                return longer;
        }
    }
}
