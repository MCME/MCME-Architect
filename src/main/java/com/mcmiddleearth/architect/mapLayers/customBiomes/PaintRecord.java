package com.mcmiddleearth.architect.mapLayers.customBiomes;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.LongPredicate;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * The custom biomes of one world: the paint of each chunk that holds any, and how far the first fill of the world's
 * saved files has come. A chunk without a custom biome is not kept.
 * <p>
 * Not thread-safe. Use it from one thread, and give another thread a {@link #copyOfChunks() copy}.
 * <p>
 * A saved record is gzip over big-endian data, in this order:
 * <ul>
 *     <li>int magic {@code 0x41434252}, short version (1);</li>
 *     <li>boolean fill done, int count of finished region files and each as UTF;</li>
 *     <li>int count of biome ids and each as UTF;</li>
 *     <li>int count of chunks and, for each: int x, int z, byte palette size; for each palette entry a byte span
 *     count and, for each span, short biome index, short minY and short maxY; then the 16 byte column indexes.</li>
 * </ul>
 * Counts of palette entries and spans are unsigned bytes, biome indexes unsigned shorts, and heights signed shorts,
 * which is more room than Minecraft needs.
 */
public final class PaintRecord {

    private static final int MAGIC = 0x41434252;
    private static final short VERSION = 1;
    private static final int BUFFER = 1 << 16;

    private final Map<Long, ChunkPaint> chunks = new HashMap<>();
    private final Map<Long, ChunkPaint> chunksView = Collections.unmodifiableMap(chunks);
    private final Set<String> finished = new TreeSet<>();
    private final Set<String> finishedView = Collections.unmodifiableSet(finished);
    private boolean fillDone;

    /** The key of a chunk, laid out as Paper's {@code Chunk.getChunkKey}: x in the low half, z in the high half. */
    public static long chunkKey(int x, int z) {
        return (x & 0xFFFFFFFFL) | ((long) z << 32);
    }

    public static int keyX(long key) {
        return (int) key;
    }

    public static int keyZ(long key) {
        return (int) (key >> 32);
    }

    /** Stores the chunk's paint, or forgets the chunk for an empty one. Returns whether what is stored changed. */
    public boolean put(int chunkX, int chunkZ, ChunkPaint paint) {
        long key = chunkKey(chunkX, chunkZ);
        if (paint.isEmpty()) {
            return chunks.remove(key) != null;
        }
        if (paint.equals(chunks.get(key))) {
            return false;
        }
        chunks.put(key, paint);
        return true;
    }

    /** The chunk's paint, or {@link ChunkPaint#EMPTY} where nothing is painted. */
    public ChunkPaint get(int chunkX, int chunkZ) {
        return chunks.getOrDefault(chunkKey(chunkX, chunkZ), ChunkPaint.EMPTY);
    }

    /** The painted chunks by {@link #chunkKey key}: a view that follows the record and cannot be changed. */
    public Map<Long, ChunkPaint> chunks() {
        return chunksView;
    }

    /** The painted chunks as they are now. Paints cannot change, so this is safe to read on another thread. */
    public Map<Long, ChunkPaint> copyOfChunks() {
        return new HashMap<>(chunks);
    }

    /** Whether the first fill has read all of the world's region files. */
    public boolean fillDone() {
        return fillDone;
    }

    public void fillDone(boolean done) {
        this.fillDone = done;
    }

    /** The region files the first fill has read, such as {@code r.0.-1.mca}, so that a stopped fill can go on. */
    public Set<String> finishedFiles() {
        return finishedView;
    }

    public void finished(String regionFileName) {
        finished.add(regionFileName);
    }

    /** Forgets how far the first fill has come, so the world is read again from the start. */
    public void clearFill() {
        fillDone = false;
        finished.clear();
    }

    /** Forgets every chunk whose key {@code keep} does not accept. The fill state stays. */
    public void clearExcept(LongPredicate keep) {
        chunks.keySet().removeIf(key -> !keep.test(key));
    }

    /**
     * Writes a temporary file, then moves it over the old one in one step, so a crash mid-write keeps the old file.
     * The file's folder is made when it is missing.
     */
    public void save(Path file) throws IOException {
        Path temp = file.resolveSibling(file.getFileName() + ".tmp");
        Path folder = file.toAbsolutePath().getParent();
        if (folder != null) {
            Files.createDirectories(folder);
        }
        try {
            write(temp);
            try {
                Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException | RuntimeException e) {
            // a full disk would otherwise keep the half-written file as well
            try {
                Files.deleteIfExists(temp);
            } catch (IOException cleanup) {
                e.addSuppressed(cleanup);
            }
            throw e;
        }
    }

    private void write(Path temp) throws IOException {
        // sorted, because a hash map's order depends on how it was filled and a file's should not
        List<Map.Entry<Long, ChunkPaint>> entries = new ArrayList<>(chunks.entrySet());
        entries.sort(Map.Entry.comparingByKey());
        Set<String> ids = new TreeSet<>();
        for (ChunkPaint paint : chunks.values()) {
            ids.addAll(paint.biomes());
        }
        Map<String, Integer> biomeIndex = new HashMap<>();
        for (String id : ids) {
            biomeIndex.put(id, biomeIndex.size());
        }
        try (DataOutputStream out = new DataOutputStream(new BufferedOutputStream(
                new GZIPOutputStream(Files.newOutputStream(temp), BUFFER), BUFFER))) {
            out.writeInt(MAGIC);
            out.writeShort(VERSION);
            out.writeBoolean(fillDone);
            out.writeInt(finished.size());
            for (String name : finished) {
                out.writeUTF(name);
            }
            out.writeInt(ids.size());
            for (String id : ids) {
                out.writeUTF(id);
            }
            out.writeInt(entries.size());
            for (Map.Entry<Long, ChunkPaint> entry : entries) {
                out.writeInt(keyX(entry.getKey()));
                out.writeInt(keyZ(entry.getKey()));
                ChunkPaint paint = entry.getValue();
                out.writeByte(paint.palette().size());
                for (List<BiomeSpan> column : paint.palette()) {
                    out.writeByte(column.size());
                    for (BiomeSpan span : column) {
                        out.writeShort(biomeIndex.get(span.biome()));
                        out.writeShort(span.minY());
                        out.writeShort(span.maxY());
                    }
                }
                out.write(paint.indexes());
            }
        }
    }

    /**
     * Reads a record saved by {@link #save}.
     *
     * @throws IOException when the file cannot be read, is not a record of this version, ends early, or is damaged
     */
    public static PaintRecord load(Path file) throws IOException {
        try (DataInputStream in = new DataInputStream(new BufferedInputStream(
                new GZIPInputStream(Files.newInputStream(file), BUFFER), BUFFER))) {
            return read(in, file);
        } catch (EOFException e) {
            throw new IOException(file.getFileName() + " ends too early", e);
        }
    }

    private static PaintRecord read(DataInputStream in, Path file) throws IOException {
        if (in.readInt() != MAGIC) {
            throw new IOException(file.getFileName() + " is not a custom biome record");
        }
        short version = in.readShort();
        if (version != VERSION) {
            throw new IOException(file.getFileName() + " is version " + version + ", and this reads version "
                    + VERSION);
        }
        PaintRecord record = new PaintRecord();
        record.fillDone = in.readBoolean();
        int finishedCount = in.readInt();
        for (int i = 0; i < finishedCount; i++) {
            record.finished.add(in.readUTF());
        }
        // grown as it is read: a damaged count must end in the end of the file, not in a huge array
        List<String> biomes = new ArrayList<>();
        int biomeCount = in.readInt();
        for (int i = 0; i < biomeCount; i++) {
            biomes.add(in.readUTF());
        }
        int chunkCount = in.readInt();
        for (int i = 0; i < chunkCount; i++) {
            int x = in.readInt();
            int z = in.readInt();
            record.put(x, z, readPaint(in, biomes, file));
        }
        // Reading on to the end also checks the gzip checksum, which finds damage that still parses.
        if (in.read() != -1) {
            throw new IOException(file.getFileName() + " has data after its last chunk");
        }
        return record;
    }

    private static ChunkPaint readPaint(DataInputStream in, List<String> biomes, Path file) throws IOException {
        int paletteSize = in.readUnsignedByte();
        List<List<BiomeSpan>> palette = new ArrayList<>(paletteSize);
        for (int i = 0; i < paletteSize; i++) {
            int spanCount = in.readUnsignedByte();
            List<BiomeSpan> spans = new ArrayList<>(spanCount);
            for (int j = 0; j < spanCount; j++) {
                int biome = in.readUnsignedShort();
                int minY = in.readShort();
                int maxY = in.readShort();
                if (biome >= biomes.size()) {
                    throw new IOException(file.getFileName() + " has a span of biome " + biome + ", but names only "
                            + biomes.size());
                }
                spans.add(new BiomeSpan(biomes.get(biome), minY, maxY));
            }
            palette.add(spans);
        }
        List<List<BiomeSpan>> columns = new ArrayList<>(ChunkPaint.COLUMNS);
        for (int i = 0; i < ChunkPaint.COLUMNS; i++) {
            int index = in.readUnsignedByte();
            if (index >= paletteSize) {
                throw new IOException(file.getFileName() + " has a column of palette entry " + index
                        + ", but the palette holds " + paletteSize);
            }
            columns.add(palette.get(index));
        }
        return ChunkPaint.of(columns);
    }
}
