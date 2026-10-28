package com.mcmiddleearth.architect.mapLayers.customBiomes;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.GZIPInputStream;
import java.util.zip.InflaterInputStream;

/**
 * Reads the custom biomes out of a world's saved region files ({@code r.X.Z.mca}), without loading a chunk into the
 * game. It only reads: a world's files are never written.
 * <p>
 * A region file is 32 x 32 chunks. Its first sector of 4096 bytes says, for each chunk, where in the file its data
 * starts and how many sectors it takes. A chunk's data is its length, a byte for how it is compressed, and the
 * compressed NBT. A chunk too big for the file keeps its NBT in {@code c.<chunk x>.<chunk z>.mcc} beside it.
 * <p>
 * A chunk that cannot be read is skipped and counted, never fatal: one damaged chunk must not stop the first fill of a
 * world. Safe to use from several threads, as a read keeps nothing in the reader.
 */
public final class RegionFileReader {

    /** The file name gives the region's place; up to six digits keep the chunk numbers inside an int. */
    private static final Pattern REGION_FILE = Pattern.compile("r\\.(-?\\d{1,6})\\.(-?\\d{1,6})\\.mca");

    private static final int SECTOR = 4096;
    /** Chunks along a region's side. */
    private static final int SIDE = 32;
    private static final int CHUNKS = SIDE * SIDE;
    /** What starts a stored chunk: its length in 4 bytes, then its compression type in 1. */
    private static final int PREFIX = 5;
    /** The compression type's top bit says the NBT is in a {@code .mcc} file. */
    private static final int EXTERNAL = 0x80;

    private static final int GZIP = 1;
    private static final int ZLIB = 2;
    private static final int NONE = 3;
    private static final int LZ4 = 4;

    private static final String PAST_THE_END = "past the end of the file";
    private static final String BAD_BIOME_DATA = "biome data of unexpected length";
    /** Why a chunk is skipped that says it is another chunk of its file: see {@link #namesAnotherSlot}. */
    private static final String MOVED = "moved while being read";

    /**
     * What a read found.
     *
     * @param chunks  the chunks the file stores
     * @param parsed  those whose NBT was read in full: the ones with a custom namespace in them that could be read
     * @param painted those among them with a custom biome
     * @param skipped how often something could not be read, by the reason: a chunk as a whole, or one section of it
     */
    public record Stats(int chunks, int parsed, int painted, Map<String, Integer> skipped) {

        public Stats {
            skipped = Collections.unmodifiableMap(new TreeMap<>(skipped));
        }
    }

    /** Takes the paint of the chunks of a file, one call each, on the thread that reads. */
    @FunctionalInterface
    public interface ChunkSink {

        void accept(int chunkX, int chunkZ, ChunkPaint paint);
    }

    /** Counts while a file is read. */
    private static final class Tally {
        int chunks;
        int parsed;
        int painted;
        final Map<String, Integer> skipped = new TreeMap<>();

        void skip(String reason) {
            skipped.merge(reason, 1, Integer::sum);
        }

        Stats stats() {
            return new Stats(chunks, parsed, painted, skipped);
        }
    }

    private final Set<String> customIds;
    /** Each custom namespace with its colon, as the bytes an id starts with in the NBT: {@code cbc:}. */
    private final byte[][] needles;

    /**
     * @param customIds the custom biome ids the server defines, such as {@code cbc:111g380-5p}. Any other id in a
     *                  chunk is left out of its paint, as it loads as plains in the game
     */
    public RegionFileReader(Set<String> customIds) {
        this.customIds = Set.copyOf(customIds);
        Set<String> namespaces = new TreeSet<>();
        for (String id : this.customIds) {
            int colon = id.indexOf(':');
            // an id without a namespace is saved as minecraft:, which is never custom
            if (colon > 0) {
                namespaces.add(id.substring(0, colon + 1));
            }
        }
        this.needles = namespaces.stream().map(space -> space.getBytes(StandardCharsets.UTF_8)).toArray(byte[][]::new);
    }

    /**
     * Reads every chunk a region file stores and hands its paint to the sink: {@link ChunkPaint#EMPTY} for a chunk
     * with no custom biome, so that a new fill clears what an old one found there, and also for one that was skipped.
     * The file's name, {@code r.X.Z.mca}, gives the chunks' coordinates.
     * <p>
     * A chunk is parsed only when its NBT holds one of the custom namespaces followed by a colon, such as
     * {@code cbc:}. So the chunks of a world without custom biomes cost a decompression, and no more. A parsed chunk
     * that says it is another chunk of the file is skipped and counted: the server wrote it there while the file was
     * read.
     *
     * @throws IOException when the file is not named like a region file, cannot be opened or fails to read. A chunk
     *                     whose data is damaged, or in a form this does not read, is skipped and counted instead
     */
    public Stats read(Path regionFile, ChunkSink sink) throws IOException {
        Matcher name = REGION_FILE.matcher(String.valueOf(regionFile.getFileName()));
        if (!name.matches()) {
            throw new IOException(regionFile.getFileName()
                    + " is not named r.X.Z.mca, so where its chunks lie is unknown");
        }
        int firstChunkX = Integer.parseInt(name.group(1)) * SIDE;
        int firstChunkZ = Integer.parseInt(name.group(2)) * SIDE;
        Tally tally = new Tally();
        try (FileChannel channel = FileChannel.open(regionFile, StandardOpenOption.READ)) {
            ByteBuffer locations = ByteBuffer.allocate(SECTOR);
            readAt(channel, locations, 0); // a file shorter than this has no chunks where it ends
            for (int index = 0; index < CHUNKS; index++) {
                int location = locations.getInt(index * 4);
                int sector = location >>> 8;
                int sectors = location & 0xFF;
                // as in the game, a start inside the header, or no sectors, is no chunk
                if (sector < 2 || sectors == 0) {
                    continue;
                }
                tally.chunks++;
                int chunkX = firstChunkX + index % SIDE;
                int chunkZ = firstChunkZ + index / SIDE;
                sink.accept(chunkX, chunkZ, readChunk(channel, sector, sectors, chunkX, chunkZ, regionFile, tally));
            }
        }
        return tally.stats();
    }

    private ChunkPaint readChunk(FileChannel channel, int sector, int sectors, int chunkX, int chunkZ, Path regionFile,
                                 Tally tally) throws IOException {
        ByteBuffer stored = ByteBuffer.allocate(sectors * SECTOR);
        // outside the try below: a disk that fails ends the read, and only what is in the chunk's data is skipped
        int got = readAt(channel, stored, (long) sector * SECTOR);
        if (got < PREFIX) {
            return skipped(tally, PAST_THE_END);
        }
        try {
            int length = stored.getInt(0);
            if (length < 1 || length > stored.capacity() - 4) {
                throw new IOException("A chunk of " + length + " bytes does not fit its " + sectors + " sectors");
            }
            if (length > got - 4) {
                return skipped(tally, PAST_THE_END);
            }
            int compression = stored.get(4) & 0xFF;
            int type = compression & ~EXTERNAL;
            if (type == LZ4) {
                return skipped(tally, "LZ4 is not supported");
            }
            if (type != GZIP && type != ZLIB && type != NONE) {
                return skipped(tally, "unknown compression " + type);
            }
            byte[] nbt;
            if ((compression & EXTERNAL) == 0) {
                nbt = inflate(type, stored.array(), PREFIX, length - 1);
            } else {
                byte[] outside = Files.readAllBytes(regionFile.resolveSibling("c." + chunkX + "." + chunkZ + ".mcc"));
                nbt = inflate(type, outside, 0, outside.length);
            }
            return paintFromNbt(nbt, chunkX, chunkZ, tally);
        } catch (IOException | RuntimeException e) {
            return skipped(tally, reasonOf(e));
        }
    }

    /** Reads up to the buffer's size from the position on; less only where the file ends. Returns how much it read. */
    private static int readAt(FileChannel channel, ByteBuffer buffer, long position) throws IOException {
        int total = 0;
        while (buffer.hasRemaining()) {
            int read = channel.read(buffer, position + total);
            if (read < 0) {
                break;
            }
            total += read;
        }
        return total;
    }

    private static byte[] inflate(int type, byte[] bytes, int offset, int length) throws IOException {
        if (type == NONE) {
            return Arrays.copyOfRange(bytes, offset, offset + length);
        }
        InputStream raw = new ByteArrayInputStream(bytes, offset, length);
        try (InputStream in = type == GZIP ? new GZIPInputStream(raw) : new InflaterInputStream(raw)) {
            return in.readAllBytes();
        }
    }

    private ChunkPaint paintFromNbt(byte[] nbt, int chunkX, int chunkZ, Tally tally) throws IOException {
        if (!mentionsACustomNamespace(nbt)) {
            return ChunkPaint.EMPTY;
        }
        ChunkNbt.Chunk chunk = ChunkNbt.parse(nbt);
        if (namesAnotherSlot(chunk, chunkX, chunkZ)) {
            return skipped(tally, MOVED);
        }
        tally.parsed++;
        ChunkPaint paint = paintOf(chunk, tally);
        if (!paint.isEmpty()) {
            tally.painted++;
        }
        return paint;
    }

    /**
     * Whether the chunk says it lies in another slot of its region than the one it was found in. The file's table is
     * read first and the chunks after it, and the server may save in between: a chunk that outgrew its sectors moves,
     * and another chunk of the file may be written where it was. What is found there then names that other chunk, and
     * its paint is not this chunk's. Only the place within the region is compared. A chunk that names the same slot of
     * another region, as in a region file copied from elsewhere, is loaded by the game where it lies, and so it is read
     * there; one that names no place is read where it lies too.
     */
    private static boolean namesAnotherSlot(ChunkNbt.Chunk chunk, int chunkX, int chunkZ) {
        if (chunk.xPos() == null || chunk.zPos() == null) {
            return false;
        }
        return Math.floorMod(chunk.xPos() - chunkX, SIDE) != 0 || Math.floorMod(chunk.zPos() - chunkZ, SIDE) != 0;
    }

    private boolean mentionsACustomNamespace(byte[] nbt) {
        for (byte[] needle : needles) {
            if (contains(nbt, needle)) {
                return true;
            }
        }
        return false;
    }

    private static boolean contains(byte[] bytes, byte[] needle) {
        byte first = needle[0];
        int last = bytes.length - needle.length;
        search:
        for (int i = 0; i <= last; i++) {
            if (bytes[i] != first) {
                continue;
            }
            for (int j = 1; j < needle.length; j++) {
                if (bytes[i + j] != needle[j]) {
                    continue search;
                }
            }
            return true;
        }
        return false;
    }

    /**
     * The paint of a chunk: in each column, each custom biome from the bottom of its lowest cell to the top of its
     * highest, as {@link ChunkPaintReader} gives it for a loaded chunk. The cells are those the sections hold, whatever
     * the world's height.
     */
    private ChunkPaint paintOf(ChunkNbt.Chunk chunk, Tally tally) {
        // for each custom biome, per column: its lowest and its highest block so far; low above high where unseen
        Map<String, int[]> heights = new HashMap<>();
        for (ChunkNbt.Section section : chunk.sections()) {
            List<String> palette = section.palette();
            boolean[] custom = new boolean[palette.size()];
            boolean any = false;
            for (int i = 0; i < custom.length; i++) {
                custom[i] = customIds.contains(palette.get(i));
                any |= custom[i];
            }
            if (!any) {
                continue;
            }
            int[] cells;
            try {
                cells = ChunkNbt.cells(palette, section.data());
            } catch (IOException e) {
                tally.skip(BAD_BIOME_DATA);
                continue;
            }
            int bottom = section.y() * 16;
            for (int cell = 0; cell < ChunkNbt.CELLS; cell++) {
                int entry = cells == null ? 0 : cells[cell];
                if (!custom[entry]) {
                    continue;
                }
                int[] range = heights.computeIfAbsent(palette.get(entry), id -> unseen());
                int column = cell & 15; // z * 4 + x, the low half of the cell's number
                int low = bottom + (cell >> 4) * 4; // the row of cells, then 4 blocks to a cell
                range[2 * column] = Math.min(range[2 * column], low);
                range[2 * column + 1] = Math.max(range[2 * column + 1], low + 3);
            }
        }
        List<List<BiomeSpan>> columns = new ArrayList<>(ChunkPaint.COLUMNS);
        for (int column = 0; column < ChunkPaint.COLUMNS; column++) {
            columns.add(new ArrayList<>());
        }
        heights.forEach((id, range) -> {
            for (int column = 0; column < ChunkPaint.COLUMNS; column++) {
                if (range[2 * column] <= range[2 * column + 1]) {
                    columns.get(column).add(new BiomeSpan(id, range[2 * column], range[2 * column + 1]));
                }
            }
        });
        return ChunkPaint.of(columns);
    }

    private static int[] unseen() {
        int[] range = new int[2 * ChunkPaint.COLUMNS];
        for (int column = 0; column < ChunkPaint.COLUMNS; column++) {
            range[2 * column] = Integer.MAX_VALUE;
            range[2 * column + 1] = Integer.MIN_VALUE;
        }
        return range;
    }

    private static String reasonOf(Exception e) {
        if (ChunkNbt.OLD_LAYOUT.equals(e.getMessage())) {
            return ChunkNbt.OLD_LAYOUT;
        }
        return "unreadable: " + e.getClass().getSimpleName();
    }

    /** Counts a skipped chunk, and gives the paint it is handed on with. */
    private static ChunkPaint skipped(Tally tally, String reason) {
        tally.skip(reason);
        return ChunkPaint.EMPTY;
    }
}
