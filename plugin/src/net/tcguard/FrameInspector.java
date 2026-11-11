package net.tcguard;

import io.netty.buffer.ByteBuf;

import java.nio.charset.StandardCharsets;

final class FrameInspector {
    private static final int CHAT = 0x01, BLOCK_PLACE = 0x08, STEER_VEHICLE = 0x0C, WINDOW_CLICK = 0x0E,
            CREATIVE_SLOT = 0x10, UPDATE_SIGN = 0x12, TAB_COMPLETE = 0x14, CUSTOM_PAYLOAD = 0x17;
    private static final int WRITTEN_BOOK = 387;

    static final String[] NAMES = {"KEEP_ALIVE", "CHAT", "USE_ENTITY", "FLYING", "POSITION", "LOOK", "POSITION_LOOK",
            "BLOCK_DIG", "BLOCK_PLACE", "HELD_ITEM_SLOT", "ARM_ANIMATION", "ENTITY_ACTION", "STEER_VEHICLE",
            "CLOSE_WINDOW", "WINDOW_CLICK", "TRANSACTION", "SET_CREATIVE_SLOT", "ENCHANT_ITEM", "UPDATE_SIGN",
            "ABILITIES", "TAB_COMPLETE", "SETTINGS", "CLIENT_COMMAND", "CUSTOM_PAYLOAD", "SPECTATE", "RESOURCE_PACK_STATUS"};

    static final class Limits {
        int frame = 8192;
        int itemFrame = 65536;
        int payload = 32767;
        int nbtDepth = 16;
        int nbtTags = 1024;
        int nbtList = 512;
        int nbtArray = 65536;
        int nbtString = 8192;
        int bookPage = 800;
        int skullTexture = 1024;
        int signLine = 384;
        int tabComplete = 256;

        int largestFrame() {
            return Math.max(frame, Math.max(itemFrame, payload + 64));
        }
    }

    static final class Frame {
        int id = -1;
        String channel;

        String name() {
            return channel != null ? channel : id >= 0 && id < NAMES.length ? NAMES[id] : "OTHER";
        }
    }

    private static final class Rejected extends RuntimeException {
        Rejected(String reason) {
            super(reason, null, false, false);
        }
    }

    private FrameInspector() {}

    static String inspect(ByteBuf frame, Limits limits, Frame out) {
        int size = frame.readableBytes();
        ByteBuf in = frame.duplicate();
        try {
            int id = out.id = readVarInt(in);
            boolean item = id == BLOCK_PLACE || id == WINDOW_CLICK || id == CREATIVE_SLOT;
            int max = item ? limits.itemFrame : id == CUSTOM_PAYLOAD ? limits.payload + 64 : limits.frame;
            if (size > max) return "frame of " + size + " bytes (packet 0x" + Integer.toHexString(id) + ")";

            switch (id) {
                case CHAT:
                    if (Log4jFilter.matches(string(in, limits.frame))) return "log4j lookup in chat";
                    break;
                case STEER_VEHICLE: {
                    float sideways = in.readFloat(), forward = in.readFloat();
                    if (Float.isNaN(sideways + forward) || Float.isInfinite(sideways + forward)) return "vehicle input " + sideways + "/" + forward;
                    break;
                }
                case WINDOW_CLICK: {
                    in.skipBytes(1);
                    int slot = in.readShort(), button = in.readByte();
                    in.skipBytes(2);
                    int mode = in.readByte();
                    if (slot < -1 && slot != -999 || slot > 127 || button < 0 || button > 10 || mode < 0 || mode > 6) {
                        return "window click slot " + slot + " button " + button + " mode " + mode;
                    }
                    item(in, limits);
                    break;
                }
                case CREATIVE_SLOT:
                    in.skipBytes(2);
                    item(in, limits);
                    break;
                case BLOCK_PLACE:
                    in.skipBytes(9);
                    item(in, limits);
                    break;
                case UPDATE_SIGN:
                    in.skipBytes(8);
                    for (int line = 0; line < 4; line++) {
                        int length = readVarInt(in);
                        if (length > limits.signLine) return "sign line of " + length + " bytes";
                        String text = in.toString(in.readerIndex(), length, StandardCharsets.UTF_8);
                        in.skipBytes(length);
                        if (translates(text)) return "sign line with a translate component";
                        if (Log4jFilter.matches(text)) return "log4j lookup on a sign";
                    }
                    break;
                case TAB_COMPLETE: {
                    int length = readVarInt(in);
                    if (length > limits.tabComplete) return "tab-complete of " + length + " bytes";
                    if (Log4jFilter.matches(in.toString(in.readerIndex(), length, StandardCharsets.UTF_8))) return "log4j lookup in tab-complete";
                    break;
                }
                case CUSTOM_PAYLOAD: {
                    int length = readVarInt(in);
                    if (length < 0 || length > 20) return "payload channel of " + length + " bytes";
                    String channel = out.channel = in.toString(in.readerIndex(), length, StandardCharsets.UTF_8);
                    in.skipBytes(length);
                    if (channel.equals("MC|BEdit") || channel.equals("MC|BSign")) item(in, limits);
                    break;
                }
                default:
            }
            return null;
        } catch (Rejected e) {
            return e.getMessage();
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static String string(ByteBuf in, int max) {
        int length = readVarInt(in);
        if (length < 0 || length > max) throw new IllegalArgumentException("string");
        String text = in.toString(in.readerIndex(), length, StandardCharsets.UTF_8);
        in.skipBytes(length);
        return text;
    }

    static boolean translates(String json) {
        if (json.indexOf("translate") < 0 && json.indexOf("\\u") < 0) return false;
        StringBuilder plain = new StringBuilder(json.length());
        for (int i = 0; i < json.length(); i++) {
            char c = json.charAt(i);
            if (c == '\\' && i + 5 < json.length() && json.charAt(i + 1) == 'u') {
                try {
                    plain.append((char) Integer.parseInt(json.substring(i + 2, i + 6), 16));
                    i += 5;
                    continue;
                } catch (NumberFormatException e) {
                }
            }
            plain.append(c);
        }
        String key = "\"translate\"";
        for (int at = plain.indexOf(key); at >= 0; at = plain.indexOf(key, at + 1)) {
            if (at > 0 && plain.charAt(at - 1) == '\\') continue;
            int next = at + key.length();
            while (next < plain.length() && Character.isWhitespace(plain.charAt(next))) next++;
            if (next < plain.length() && plain.charAt(next) == ':') return true;
        }
        return false;
    }

    static int readVarInt(ByteBuf in) {
        int value = 0;
        for (int shift = 0; shift < 35; shift += 7) {
            byte b = in.readByte();
            value |= (b & 0x7F) << shift;
            if (b >= 0) return value;
        }
        throw new IllegalArgumentException("varint");
    }

    private static void item(ByteBuf in, Limits limits) {
        int id = in.readShort();
        if (id < 0) return;
        in.skipBytes(3);
        byte root = in.readByte();
        if (root == 0) return;
        if (root != 10) throw new Rejected("item nbt root type " + root);
        in.skipBytes(in.readUnsignedShort());
        new Nbt(in, limits, id == WRITTEN_BOOK).compound(1);
    }

    private static final class Nbt {
        private final ByteBuf in;
        private final Limits limits;
        private final boolean writtenBook;
        private int tags;

        Nbt(ByteBuf in, Limits limits, boolean writtenBook) {
            this.in = in;
            this.limits = limits;
            this.writtenBook = writtenBook;
        }

        void compound(int depth) {
            if (depth > limits.nbtDepth) throw new Rejected("nbt deeper than " + limits.nbtDepth);
            for (byte type; (type = in.readByte()) != 0; ) {
                int length = in.readUnsignedShort();
                if (length > 256) throw new Rejected("nbt key of " + length + " bytes");
                String key = in.toString(in.readerIndex(), length, StandardCharsets.UTF_8);
                in.skipBytes(length);
                value(type, depth, key);
            }
        }

        private void value(byte type, int depth, String key) {
            if (++tags > limits.nbtTags) throw new Rejected("more than " + limits.nbtTags + " nbt tags");
            switch (type) {
                case 1: in.skipBytes(1); break;
                case 2: in.skipBytes(2); break;
                case 3: case 5: in.skipBytes(4); break;
                case 4: case 6: in.skipBytes(8); break;
                case 7: in.skipBytes(array(1)); break;
                case 8: string(key); break;
                case 9: {
                    byte element = in.readByte();
                    int count = in.readInt();
                    if (count < 0 || count > limits.nbtList) throw new Rejected("nbt list of " + count);
                    if (depth + 1 > limits.nbtDepth) throw new Rejected("nbt deeper than " + limits.nbtDepth);
                    for (int i = 0; i < count; i++) {
                        if (element == 10) {
                            if (++tags > limits.nbtTags) throw new Rejected("more than " + limits.nbtTags + " nbt tags");
                            compound(depth + 2);
                        } else {
                            value(element, depth + 1, key);
                        }
                    }
                    break;
                }
                case 10: compound(depth + 1); break;
                case 11: in.skipBytes(array(4)); break;
                default: throw new Rejected("nbt tag type " + type);
            }
        }

        private int array(int width) {
            int count = in.readInt();
            if (count < 0 || count > limits.nbtArray / width) throw new Rejected("nbt array of " + count);
            return count * width;
        }

        private void string(String key) {
            int length = in.readUnsignedShort();
            int max = key.equals("pages") ? limits.bookPage : key.equals("Value") ? limits.skullTexture : limits.nbtString;
            if (length > max) throw new Rejected("nbt string '" + key + "' of " + length + " bytes");
            if (writtenBook && key.equals("pages") && translates(in.toString(in.readerIndex(), length, StandardCharsets.UTF_8))) {
                throw new Rejected("book page with a translate component");
            }
            in.skipBytes(length);
        }
    }
}
