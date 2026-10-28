package com.mcmiddleearth.architect.mapLayers.customBiomes;

import java.io.EOFException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Takes what the custom biome layer needs out of a chunk's NBT: its DataVersion and Status, and each section's Y and
 * biomes. Every other tag is stepped over by its size, which is far quicker than building the whole tree, and most of a
 * chunk is block data.
 * <p>
 * A reader over a byte array with an index. It reads the layout the game has saved since 1.18, and refuses the one of
 * 2020 that keeps everything in a {@code Level} tag.
 */
final class ChunkNbt {

    /** Why a chunk in the layout of 2020 is refused. */
    static final String OLD_LAYOUT = "old chunk layout (Level)";

    /** The biome cells of a section: 4 x 4 x 4. */
    static final int CELLS = 64;

    /** The game reads NBT no deeper than this, so nothing it saved goes deeper; more would overflow the stack. */
    private static final int MAX_DEPTH = 512;

    private static final int TAG_END = 0;
    private static final int TAG_BYTE = 1;
    private static final int TAG_SHORT = 2;
    private static final int TAG_INT = 3;
    private static final int TAG_LONG = 4;
    private static final int TAG_FLOAT = 5;
    private static final int TAG_DOUBLE = 6;
    private static final int TAG_BYTE_ARRAY = 7;
    private static final int TAG_STRING = 8;
    private static final int TAG_LIST = 9;
    private static final int TAG_COMPOUND = 10;
    private static final int TAG_INT_ARRAY = 11;
    private static final int TAG_LONG_ARRAY = 12;

    /**
     * One section of a chunk. {@code y} counts sections of 16 blocks, so its lowest block is {@code y * 16}. The
     * {@code palette} lists the biome ids the section uses, and {@code data} says, packed into longs, which of them
     * each of its {@link #CELLS cells} holds; see {@link #cells}. It is null where the game left it out, which it does
     * for a palette of one id.
     */
    record Section(int y, List<String> palette, long[] data) {
    }

    /** The parts of a chunk that are read. A tag the chunk lacks gives null; no sections give an empty list. */
    record Chunk(Integer dataVersion, String status, List<Section> sections) {
    }

    private record Biomes(List<String> palette, long[] data) {
    }

    private final byte[] bytes;
    private int at;
    /** How many compounds and lists the tag being skipped lies in. */
    private int depth;

    private ChunkNbt(byte[] bytes) {
        this.bytes = bytes;
    }

    /**
     * Reads a chunk's NBT, uncompressed.
     *
     * @throws IOException with the message {@link #OLD_LAYOUT} for a chunk in the layout of 2020, and for NBT that is
     *                     cut short, nests too deep or is not made of the tags there are
     */
    static Chunk parse(byte[] nbt) throws IOException {
        return new ChunkNbt(nbt).chunk();
    }

    /**
     * The palette index of each of a section's 64 biome cells, numbered {@code y * 16 + z * 4 + x} where y, z and x
     * count cells inside the section. Null where all cells have the one palette entry, which is when the palette
     * holds a single id.
     * <p>
     * An entry takes {@code ceil(log2(palette size))} bits, the first one is the lowest bits of the first long, and an
     * entry that does not fit in what is left of a long starts the next one. So a long holds {@code 64 / bits}
     * entries, and the data is {@code ceil(64 / (64 / bits))} longs.
     *
     * @throws IOException when the data is not of that length, or an entry points outside the palette
     */
    static int[] cells(List<String> palette, long[] data) throws IOException {
        int size = palette.size();
        if (size == 1) {
            return null;
        }
        if (size == 0) {
            throw new IOException("A biome palette without an id");
        }
        int bits = 32 - Integer.numberOfLeadingZeros(size - 1); // 1 or more, as size is 2 or more
        int perLong = 64 / bits;
        int longs = (CELLS + perLong - 1) / perLong;
        if (data == null || data.length != longs) {
            throw new IOException("Biome data of " + (data == null ? "no" : data.length) + " longs for a palette of "
                    + size + ", which takes " + longs);
        }
        long mask = (1L << bits) - 1;
        int[] cells = new int[CELLS];
        for (int i = 0; i < CELLS; i++) {
            int entry = (int) ((data[i / perLong] >>> ((i % perLong) * bits)) & mask);
            if (entry >= size) {
                throw new IOException("A biome cell of palette entry " + entry + ", but the palette holds " + size);
            }
            cells[i] = entry;
        }
        return cells;
    }

    private Chunk chunk() throws IOException {
        if (u8() != TAG_COMPOUND) {
            throw new IOException("The chunk's root is not a compound");
        }
        int rootNameLength = u16();
        jump(rootNameLength);
        Integer dataVersion = null;
        String status = null;
        List<Section> sections = List.of();
        while (true) {
            int tag = u8();
            if (tag == TAG_END) {
                return new Chunk(dataVersion, status, sections);
            }
            String name = string();
            if (tag == TAG_INT && name.equals("DataVersion")) {
                dataVersion = i32();
            } else if (tag == TAG_STRING && name.equals("Status")) {
                status = string();
            } else if (tag == TAG_LIST && name.equals("sections")) {
                sections = sections();
            } else if (tag == TAG_COMPOUND && name.equals("Level")) {
                throw new IOException(OLD_LAYOUT);
            } else {
                skip(tag);
            }
        }
    }

    private List<Section> sections() throws IOException {
        int kind = u8();
        int count = i32();
        if (kind != TAG_COMPOUND) {
            skipElements(kind, count);
            return List.of();
        }
        // grown as it is read: a damaged count must end in the end of the data, not in a huge array
        List<Section> sections = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            Section section = section();
            if (section != null) {
                sections.add(section);
            }
        }
        return Collections.unmodifiableList(sections);
    }

    /** One section, or null where it has no Y or no biome palette, as the sections the light engine adds have not. */
    private Section section() throws IOException {
        Integer y = null;
        Biomes biomes = null;
        while (true) {
            int tag = u8();
            if (tag == TAG_END) {
                break;
            }
            String name = string();
            if (tag == TAG_BYTE && name.equals("Y")) {
                y = (int) (byte) u8(); // a signed byte: sections run below the world's floor too
            } else if (tag == TAG_COMPOUND && name.equals("biomes")) {
                biomes = biomes();
            } else {
                skip(tag);
            }
        }
        if (y == null || biomes == null || biomes.palette().isEmpty()) {
            return null;
        }
        return new Section(y, biomes.palette(), biomes.data());
    }

    private Biomes biomes() throws IOException {
        List<String> palette = List.of();
        long[] data = null;
        while (true) {
            int tag = u8();
            if (tag == TAG_END) {
                return new Biomes(palette, data);
            }
            String name = string();
            if (tag == TAG_LIST && name.equals("palette")) {
                int kind = u8();
                int count = i32();
                if (kind == TAG_STRING) {
                    List<String> ids = new ArrayList<>();
                    for (int i = 0; i < count; i++) {
                        ids.add(string());
                    }
                    palette = List.copyOf(ids);
                } else {
                    skipElements(kind, count);
                }
            } else if (tag == TAG_LONG_ARRAY && name.equals("data")) {
                data = longs();
            } else {
                skip(tag);
            }
        }
    }

    /** Steps over the payload of a tag whose type has been read. */
    private void skip(int tag) throws IOException {
        switch (tag) {
            case TAG_BYTE -> jump(1);
            case TAG_SHORT -> jump(2);
            case TAG_INT, TAG_FLOAT -> jump(4);
            case TAG_LONG, TAG_DOUBLE -> jump(8);
            case TAG_BYTE_ARRAY -> {
                int length = i32();
                jump(length);
            }
            case TAG_STRING -> {
                int length = u16();
                jump(length);
            }
            case TAG_LIST -> {
                descend();
                int kind = u8();
                int count = i32();
                skipElements(kind, count);
                depth--;
            }
            case TAG_COMPOUND -> {
                descend();
                while (true) {
                    int inner = u8();
                    if (inner == TAG_END) {
                        break;
                    }
                    int nameLength = u16();
                    jump(nameLength);
                    skip(inner);
                }
                depth--;
            }
            case TAG_INT_ARRAY -> {
                int length = i32();
                jump(4L * length);
            }
            case TAG_LONG_ARRAY -> {
                int length = i32();
                jump(8L * length);
            }
            default -> throw new IOException("Unknown NBT tag " + tag + " before byte " + at);
        }
    }

    /** Steps over a list's elements. A count of zero or less is an empty list, as in the game. */
    private void skipElements(int kind, int count) throws IOException {
        if (count <= 0) {
            return;
        }
        int size = switch (kind) {
            case TAG_BYTE -> 1;
            case TAG_SHORT -> 2;
            case TAG_INT, TAG_FLOAT -> 4;
            case TAG_LONG, TAG_DOUBLE -> 8;
            default -> 0; // each element carries its own length, or is not a tag at all
        };
        if (size > 0) {
            jump((long) size * count);
            return;
        }
        for (int i = 0; i < count; i++) {
            skip(kind);
        }
    }

    private void descend() throws IOException {
        if (++depth > MAX_DEPTH) {
            throw new IOException("The NBT nests deeper than " + MAX_DEPTH + " levels");
        }
    }

    private long[] longs() throws IOException {
        int count = i32();
        need(8L * count);
        long[] values = new long[count];
        ByteBuffer.wrap(bytes, at, 8 * count).asLongBuffer().get(values);
        at += 8 * count;
        return values;
    }

    /** Fails unless that many bytes are left. A negative count is damage, so it fails too. */
    private void need(long count) throws IOException {
        if (count < 0 || count > bytes.length - at) {
            throw new EOFException("A tag at byte " + at + " reaches past the end of the NBT, at byte " + bytes.length);
        }
    }

    private int u8() throws IOException {
        need(1);
        return bytes[at++] & 0xFF;
    }

    private int u16() throws IOException {
        need(2);
        int value = (bytes[at] & 0xFF) << 8 | (bytes[at + 1] & 0xFF);
        at += 2;
        return value;
    }

    private int i32() throws IOException {
        need(4);
        int value = (bytes[at] & 0xFF) << 24 | (bytes[at + 1] & 0xFF) << 16 | (bytes[at + 2] & 0xFF) << 8
                | (bytes[at + 3] & 0xFF);
        at += 4;
        return value;
    }

    /**
     * Steps over bytes. A length is always read into a local first and passed in: {@code at += u16()} would take
     * {@code at} before {@code u16()} moves it, and so lose the two bytes of the length.
     */
    private void jump(long count) throws IOException {
        need(count);
        at += (int) count;
    }

    private String string() throws IOException {
        int length = u16();
        need(length);
        String text = new String(bytes, at, length, StandardCharsets.UTF_8);
        at += length;
        return text;
    }
}
