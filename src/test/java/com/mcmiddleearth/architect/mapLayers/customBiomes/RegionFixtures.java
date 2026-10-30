package com.mcmiddleearth.architect.mapLayers.customBiomes;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.GZIPOutputStream;

/**
 * Chunks and region files in the formats the game saves them, for the tests of the readers. They are written from the
 * format, not from the code under test: the packing of biome data is done here on its own.
 */
final class RegionFixtures {

    // NBT tag types
    static final int END = 0;
    static final int BYTE = 1;
    static final int SHORT = 2;
    static final int INT = 3;
    static final int LONG = 4;
    static final int FLOAT = 5;
    static final int DOUBLE = 6;
    static final int BYTE_ARRAY = 7;
    static final int STRING = 8;
    static final int LIST = 9;
    static final int COMPOUND = 10;
    static final int INT_ARRAY = 11;
    static final int LONG_ARRAY = 12;

    /** A region file is made of sectors this long, and its header takes the first two. */
    static final int SECTOR = 4096;

    /** Writes tags. */
    @FunctionalInterface
    interface Body {
        void write(DataOutputStream out) throws IOException;
    }

    private RegionFixtures() {
    }

    /** The bytes a body writes. */
    static byte[] nbt(Body body) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(bytes)) {
            body.write(out);
        } catch (IOException e) {
            throw new UncheckedIOException(e); // writing to memory does not fail
        }
        return bytes.toByteArray();
    }

    /** The start of a tag: its type, then its name as two bytes of length and modified UTF-8, which writeUTF writes. */
    static void tag(DataOutputStream out, int type, String name) throws IOException {
        out.writeByte(type);
        out.writeUTF(name);
    }

    /**
     * A chunk as the game saves it, before it is compressed. {@code sections} maps a section's Y to its 64 biome ids,
     * ordered {@code y * 16 + z * 4 + x} inside the section, where y, z and x count cells of 4 blocks. The chunk holds
     * what a real one does around the biomes, before the sections and after them, so a reader has to step over it.
     */
    static byte[] chunk(int dataVersion, String status, Map<Integer, List<String>> sections) {
        List<Body> bodies = new ArrayList<>();
        new TreeMap<>(sections).forEach((y, ids) -> bodies.add(out -> section(out, y, ids)));
        return chunkOf(dataVersion, status, bodies);
    }

    /** Like {@link #chunk}, with the chunk's place saved in it as the game saves it: {@code xPos} and {@code zPos}. */
    static byte[] chunkAt(int chunkX, int chunkZ, int dataVersion, String status, Map<Integer, List<String>> sections) {
        List<Body> bodies = new ArrayList<>();
        new TreeMap<>(sections).forEach((y, ids) -> bodies.add(out -> section(out, y, ids)));
        return chunkOf(new int[] {chunkX, chunkZ}, dataVersion, status, bodies);
    }

    /** Like {@link #chunk}, with the tags inside each section written by a body. */
    static byte[] chunkOf(int dataVersion, String status, List<Body> sections) {
        return chunkOf(null, dataVersion, status, sections);
    }

    /** A chunk whose {@code place} is its x and z, or null for one that names no place: an {@code xPos} of 0 alone. */
    private static byte[] chunkOf(int[] place, int dataVersion, String status, List<Body> sections) {
        return nbt(out -> {
            out.writeByte(COMPOUND);
            out.writeUTF("");
            tag(out, INT, "xPos");
            out.writeInt(place == null ? 0 : place[0]);
            if (place != null) {
                tag(out, INT, "zPos");
                out.writeInt(place[1]);
            }
            tag(out, LIST, "block_ticks");
            out.writeByte(END);
            out.writeInt(0);
            tag(out, LONG, "InhabitedTime");
            out.writeLong(0);
            tag(out, LIST, "sections");
            out.writeByte(COMPOUND);
            out.writeInt(sections.size());
            for (Body section : sections) {
                section.write(out);
                out.writeByte(END);
            }
            tag(out, BYTE, "isLightOn");
            out.writeByte(0);
            tag(out, STRING, "Status");
            out.writeUTF(status);
            tag(out, INT, "DataVersion");
            out.writeInt(dataVersion);
            tag(out, COMPOUND, "Heightmaps");
            tag(out, LONG_ARRAY, "MOTION_BLOCKING");
            out.writeInt(37);
            for (int i = 0; i < 37; i++) {
                out.writeLong(0x0123456789ABCDEFL);
            }
            out.writeByte(END);
            tag(out, LIST, "block_entities");
            out.writeByte(COMPOUND);
            out.writeInt(1);
            tag(out, STRING, "id");
            out.writeUTF("minecraft:chest");
            tag(out, INT, "x");
            out.writeInt(5);
            out.writeByte(END);
            out.writeByte(END);
        });
    }

    /** The tags of one section: its Y, its blocks and light, and its biomes. */
    static void section(DataOutputStream out, int y, List<String> biomeIds) throws IOException {
        tag(out, BYTE, "Y");
        out.writeByte(y);
        tag(out, COMPOUND, "block_states");
        tag(out, LIST, "palette");
        out.writeByte(COMPOUND);
        out.writeInt(2);
        tag(out, STRING, "Name");
        out.writeUTF("minecraft:stone");
        out.writeByte(END);
        tag(out, STRING, "Name");
        out.writeUTF("minecraft:oak_log");
        tag(out, COMPOUND, "Properties");
        tag(out, STRING, "axis");
        out.writeUTF("y");
        out.writeByte(END);
        out.writeByte(END);
        tag(out, LONG_ARRAY, "data");
        out.writeInt(256);
        for (int i = 0; i < 256; i++) {
            out.writeLong(0x0123456789ABCDEFL);
        }
        out.writeByte(END);
        biomes(out, biomeIds);
        tag(out, BYTE_ARRAY, "SkyLight");
        out.writeInt(2048);
        out.write(new byte[2048]);
    }

    /**
     * The {@code biomes} tag for 64 ids: a palette of the distinct ids in the order they first appear, and their
     * indexes packed into longs. A palette of one id has no data, as in the game.
     */
    static void biomes(DataOutputStream out, List<String> ids) throws IOException {
        if (ids.size() != 64) {
            throw new IllegalArgumentException("A section has 64 biome cells, not " + ids.size());
        }
        List<String> palette = new ArrayList<>(new LinkedHashSet<>(ids));
        int[] layout = ids.stream().mapToInt(palette::indexOf).toArray();
        biomesWith(out, palette, palette.size() == 1 ? null : pack(layout, palette.size()));
    }

    /** The {@code biomes} tag with the palette and the data it is given, as they are. No data tag for null. */
    static void biomesWith(DataOutputStream out, List<String> palette, long[] data) throws IOException {
        tag(out, COMPOUND, "biomes");
        tag(out, LIST, "palette");
        out.writeByte(STRING);
        out.writeInt(palette.size());
        for (String id : palette) {
            out.writeUTF(id);
        }
        if (data != null) {
            tag(out, LONG_ARRAY, "data");
            out.writeInt(data.length);
            for (long value : data) {
                out.writeLong(value);
            }
        }
        out.writeByte(END);
    }

    /**
     * The longs the game saves for palette indexes. An entry takes the fewest bits that hold the largest index of a
     * palette of this size, and at least one. The first entry is the lowest bits of the first long, and an entry that
     * does not fit in what is left of a long starts the next one.
     */
    static long[] pack(int[] layout, int paletteSize) {
        int bits = 1;
        while ((1 << bits) < paletteSize) {
            bits++;
        }
        int perLong = 64 / bits;
        long[] data = new long[(layout.length + perLong - 1) / perLong];
        for (int i = 0; i < layout.length; i++) {
            data[i / perLong] |= (long) layout[i] << ((i % perLong) * bits);
        }
        return data;
    }

    /**
     * A chunk in the layout of 2020, which keeps everything in a {@code Level} tag. One of its tags holds an id of the
     * namespace {@code cbc}, so that a reader that only parses chunks with a custom namespace in them still gets to it.
     */
    static byte[] levelChunk() {
        return nbt(out -> {
            out.writeByte(COMPOUND);
            out.writeUTF("");
            tag(out, INT, "DataVersion");
            out.writeInt(2586);
            tag(out, COMPOUND, "Level");
            tag(out, INT, "xPos");
            out.writeInt(0);
            tag(out, INT, "zPos");
            out.writeInt(0);
            tag(out, STRING, "Status");
            out.writeUTF("full");
            tag(out, INT_ARRAY, "Biomes"); // numbers then, not ids
            out.writeInt(1024);
            for (int i = 0; i < 1024; i++) {
                out.writeInt(1);
            }
            tag(out, LIST, "TileEntities");
            out.writeByte(COMPOUND);
            out.writeInt(1);
            tag(out, STRING, "id");
            out.writeUTF("cbc:sign");
            out.writeByte(END);
            out.writeByte(END);
            out.writeByte(END);
        });
    }

    /**
     * Writes a region file with the given chunks, each as NBT compressed the way {@code compression} says: 1 gzip,
     * 2 zlib, 3 none. Any other number is written as the type, with the NBT left as it is: a reader that refuses a
     * type never looks at the bytes. A chunk's place in the region is {@code x + z * 32}.
     */
    static void region(Path file, Map<Integer, byte[]> chunksByIndex, int compression) throws IOException {
        region(file, chunksByIndex, compression, 0);
    }

    /**
     * Like {@link #region(Path, Map, int)}, with each chunk saved at {@code savedAt}, in epoch seconds: the time the
     * game writes into the header's second table at every save.
     */
    static void region(Path file, Map<Integer, byte[]> chunksByIndex, int compression, long savedAt)
            throws IOException {
        Map<Integer, byte[]> stored = new TreeMap<>();
        for (Map.Entry<Integer, byte[]> chunk : chunksByIndex.entrySet()) {
            stored.put(chunk.getKey(), storedChunk(compress(chunk.getValue(), compression), compression));
        }
        write(file, stored, savedAt);
    }

    /** Sets the time a region file says its chunk at {@code index} ({@code x + z * 32}) was saved, in epoch seconds. */
    static void savedAt(Path file, int index, long epochSecond) throws IOException {
        byte[] bytes = Files.readAllBytes(file);
        ByteBuffer.wrap(bytes).putInt(SECTOR + index * 4, (int) epochSecond);
        Files.write(file, bytes);
    }

    /**
     * Sets the compression type a region file says its chunk at {@code index} is stored with: the byte after the
     * chunk's length, where its sectors start. Its data is left as it is.
     */
    static void compressionOf(Path file, int index, int type) throws IOException {
        byte[] bytes = Files.readAllBytes(file);
        int sector = ByteBuffer.wrap(bytes).getInt(index * 4) >>> 8;
        bytes[sector * SECTOR + 4] = (byte) type;
        Files.write(file, bytes);
    }

    /** The place of a chunk in its region file: {@code x + z * 32}, with x and z counted inside the region. */
    static int indexOf(int chunkX, int chunkZ) {
        return Math.floorMod(chunkX, 32) + Math.floorMod(chunkZ, 32) * 32;
    }

    /**
     * Writes a region file with one chunk too big to keep in it. As the game does, the file holds a stub of one sector
     * and the chunk's zlib data is in {@code c.<chunk x>.<chunk z>.mcc} beside the file.
     */
    static void regionWithOversizedChunk(Path file, int index, byte[] nbt) throws IOException {
        String[] name = file.getFileName().toString().split("\\."); // r, X, Z, mca
        int chunkX = Integer.parseInt(name[1]) * 32 + index % 32;
        int chunkZ = Integer.parseInt(name[2]) * 32 + index / 32;
        Files.write(file.resolveSibling("c." + chunkX + "." + chunkZ + ".mcc"), compress(nbt, 2));
        write(file, Map.of(index, storedChunk(new byte[0], 0x80 | 2)), 0);
    }

    private static byte[] compress(byte[] nbt, int compression) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        switch (compression) {
            case 1 -> {
                try (OutputStream out = new GZIPOutputStream(bytes)) {
                    out.write(nbt);
                }
            }
            case 2 -> {
                try (OutputStream out = new DeflaterOutputStream(bytes)) {
                    out.write(nbt);
                }
            }
            default -> bytes.writeBytes(nbt);
        }
        return bytes.toByteArray();
    }

    /** A chunk as it lies in the file: its length, its compression type and its data, filled up to whole sectors. */
    private static byte[] storedChunk(byte[] payload, int compressionByte) {
        int sectors = (payload.length + 5 + SECTOR - 1) / SECTOR;
        ByteBuffer stored = ByteBuffer.allocate(sectors * SECTOR);
        stored.putInt(payload.length + 1).put((byte) compressionByte).put(payload);
        return stored.array();
    }

    /**
     * Writes the stored chunks one sector run after another behind a header that says where each one is, and that each
     * was saved at {@code savedAt}.
     */
    private static void write(Path file, Map<Integer, byte[]> storedByIndex, long savedAt) throws IOException {
        ByteBuffer header = ByteBuffer.allocate(2 * SECTOR);
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        int sector = 2;
        for (Map.Entry<Integer, byte[]> chunk : new TreeMap<>(storedByIndex).entrySet()) {
            int sectors = chunk.getValue().length / SECTOR;
            header.putInt(chunk.getKey() * 4, sector << 8 | sectors);
            header.putInt(SECTOR + chunk.getKey() * 4, (int) savedAt);
            body.writeBytes(chunk.getValue());
            sector += sectors;
        }
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        bytes.writeBytes(header.array());
        bytes.writeBytes(body.toByteArray());
        Files.write(file, bytes.toByteArray());
    }
}
