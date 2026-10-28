package com.mcmiddleearth.architect.mapLayers.customBiomes;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.GZIPOutputStream;

import static org.junit.jupiter.api.Assertions.*;

// A record is what a world's custom biomes look like on the map, and what survives a restart: what is saved must come
// back as it was, and a file that is not whole must be refused, not half read.
class PaintRecordTest {

    private static final int MAGIC = 0x41434252;

    private final ChunkPaint forest = painted("cbc:forest", -64, 319);

    private static ChunkPaint painted(String biome, int minY, int maxY) {
        return ChunkPaint.of(ChunkPaintTest.sixteen(List.of(new BiomeSpan(biome, minY, maxY))));
    }

    /** Fifty chunks that each differ, so a file of them does not shrink to nothing. */
    private static PaintRecord manyChunks() {
        PaintRecord record = new PaintRecord();
        for (int i = 0; i < 50; i++) {
            record.put(i, -i, painted("cbc:biome" + i, i, i + 100));
        }
        return record;
    }

    @Test
    void putReportsAChangeOnlyWhenThePaintDiffers() {
        PaintRecord record = new PaintRecord();

        assertTrue(record.put(1, 2, forest));
        assertFalse(record.put(1, 2, forest));
        assertFalse(record.put(1, 2, painted("cbc:forest", -64, 319)), "an equal paint is no change");
        assertTrue(record.put(1, 2, painted("cbc:forest", -64, 255)), "another one is");
        assertEquals(1, record.chunks().size());
    }

    @Test
    void anEmptyPaintRemovesTheChunk() {
        PaintRecord record = new PaintRecord();
        record.put(1, 2, forest);

        assertTrue(record.put(1, 2, ChunkPaint.EMPTY));
        assertTrue(record.chunks().isEmpty());
        assertFalse(record.put(1, 2, ChunkPaint.EMPTY));
    }

    @Test
    void getGivesEmptyWhereNothingIsPainted() {
        PaintRecord record = new PaintRecord();
        record.put(3, 4, forest);

        assertEquals(forest, record.get(3, 4));
        assertEquals(ChunkPaint.EMPTY, record.get(4, 3), "x and z are not mixed up");
        assertEquals(ChunkPaint.EMPTY, record.get(-3, -4));
    }

    @Test
    void aChunkKeyHoldsItsCoordinates() {
        int[][] chunks = {{0, 0}, {-217, 135}, {1, -1}, {-1, 1}, {Integer.MIN_VALUE, Integer.MAX_VALUE},
                {Integer.MAX_VALUE, Integer.MIN_VALUE}};
        for (int[] chunk : chunks) {
            long key = PaintRecord.chunkKey(chunk[0], chunk[1]);
            assertEquals(chunk[0], PaintRecord.keyX(key), "x of " + Arrays.toString(chunk));
            assertEquals(chunk[1], PaintRecord.keyZ(key), "z of " + Arrays.toString(chunk));
        }
        assertNotEquals(PaintRecord.chunkKey(1, 2), PaintRecord.chunkKey(2, 1));
    }

    @Test
    void theChunkViewIsReadOnlyAndACopyIsASnapshot() {
        PaintRecord record = new PaintRecord();
        record.put(0, 0, forest);
        Map<Long, ChunkPaint> view = record.chunks();
        Map<Long, ChunkPaint> copy = record.copyOfChunks();

        record.put(1, 1, forest);

        assertEquals(2, view.size(), "the view follows the record");
        assertEquals(1, copy.size(), "a copy does not");
        assertThrows(UnsupportedOperationException.class, () -> view.remove(PaintRecord.chunkKey(0, 0)));
        assertThrows(UnsupportedOperationException.class, () -> view.put(PaintRecord.chunkKey(5, 5), forest));
    }

    @Test
    void clearExceptKeepsOnlyTheKeptChunks() {
        PaintRecord record = new PaintRecord();
        record.put(0, 0, forest);
        record.put(1, 0, forest);
        record.put(-5, 7, forest);
        record.fillDone(true);
        record.finished("r.0.0.mca");

        record.clearExcept(key -> key == PaintRecord.chunkKey(0, 0));

        assertEquals(Set.of(PaintRecord.chunkKey(0, 0)), record.chunks().keySet());
        assertTrue(record.fillDone(), "the fill is not about which chunks are kept");
        assertEquals(Set.of("r.0.0.mca"), record.finishedFiles());
    }

    @Test
    void theFillStateIsKeptUntilItIsCleared() {
        PaintRecord record = new PaintRecord();
        record.put(0, 0, forest);
        assertFalse(record.fillDone());
        assertTrue(record.finishedFiles().isEmpty());

        record.fillDone(true);
        record.finished("r.0.0.mca");
        record.finished("r.0.0.mca");
        record.finished("r.-1.0.mca");

        assertTrue(record.fillDone());
        assertEquals(Set.of("r.0.0.mca", "r.-1.0.mca"), record.finishedFiles());
        assertThrows(UnsupportedOperationException.class, () -> record.finishedFiles().add("r.5.5.mca"));

        record.clearFill();

        assertFalse(record.fillDone());
        assertTrue(record.finishedFiles().isEmpty());
        assertEquals(1, record.chunks().size(), "what was read stays");
    }

    @Test
    void aSavedRecordLoadsBackTheSame(@TempDir Path dir) throws IOException {
        PaintRecord record = new PaintRecord();
        List<List<BiomeSpan>> mixed = new ArrayList<>(ChunkPaintTest.sixteen(List.of(
                new BiomeSpan("cbc:sky", 500, 751), new BiomeSpan("cbc:forest", 0, 319),
                new BiomeSpan("cbc:cave", -64, -1))));
        mixed.set(9, List.of());
        mixed.set(10, List.of(new BiomeSpan("cbc:forest", 0, 319)));
        record.put(-217, 135, ChunkPaint.of(mixed));
        record.put(0, 0, forest);
        record.finished("r.0.0.mca");

        record.save(dir.resolve("world.dat"));
        PaintRecord back = PaintRecord.load(dir.resolve("world.dat"));

        assertEquals(record.chunks(), back.chunks());
        assertFalse(back.fillDone());
        assertEquals(Set.of("r.0.0.mca"), back.finishedFiles());
        assertFalse(Files.exists(dir.resolve("world.dat.tmp")));
    }

    @Test
    void aDoneFillIsSavedToo(@TempDir Path dir) throws IOException {
        PaintRecord record = new PaintRecord();
        record.put(2, 3, forest);
        record.fillDone(true);
        record.finished("r.0.0.mca");
        record.finished("r.-1.-1.mca");

        record.save(dir.resolve("world.dat"));
        PaintRecord back = PaintRecord.load(dir.resolve("world.dat"));

        assertTrue(back.fillDone());
        assertEquals(Set.of("r.0.0.mca", "r.-1.-1.mca"), back.finishedFiles());
        assertEquals(record.chunks(), back.chunks());
    }

    @Test
    void aRecordWithNothingInItSavesAndLoadsBack(@TempDir Path dir) throws IOException {
        new PaintRecord().save(dir.resolve("world.dat"));

        PaintRecord back = PaintRecord.load(dir.resolve("world.dat"));

        assertTrue(back.chunks().isEmpty());
        assertFalse(back.fillDone());
        assertTrue(back.finishedFiles().isEmpty());
    }

    @Test
    void savingReplacesTheOldFileAndMakesItsFolder(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("customBiomes").resolve("world.dat");
        PaintRecord first = new PaintRecord();
        first.put(0, 0, forest);
        first.save(file);
        PaintRecord second = new PaintRecord();
        second.put(7, 7, painted("cbc:other", 0, 63));

        second.save(file);

        assertEquals(second.chunks(), PaintRecord.load(file).chunks());
        assertFalse(Files.exists(dir.resolve("customBiomes").resolve("world.dat.tmp")));
    }

    // A full disk would otherwise keep the half-written file as well.
    @Test
    void aSaveThatFailsLeavesNoHalfWrittenFile(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("world.dat");
        Files.createDirectories(file.resolve("inside")); // a folder where the file belongs, so the move cannot work

        assertThrows(IOException.class, () -> manyChunks().save(file));

        assertFalse(Files.exists(dir.resolve("world.dat.tmp")));
    }

    // The chunks sit in a hash map, so their order depends on how they were added. The file must not.
    @Test
    void equalRecordsSaveToTheSameBytes(@TempDir Path dir) throws IOException {
        ChunkPaint other = painted("cbc:other", 0, 63);
        PaintRecord one = new PaintRecord();
        one.put(1, 2, forest);
        one.put(2, 1, other); // a key in the same hash bucket as (1, 2)
        one.finished("r.0.0.mca");
        one.finished("r.1.0.mca");
        PaintRecord two = new PaintRecord();
        two.put(2, 1, other);
        two.put(1, 2, forest);
        two.finished("r.1.0.mca");
        two.finished("r.0.0.mca");

        one.save(dir.resolve("one.dat"));
        two.save(dir.resolve("two.dat"));

        assertArrayEquals(Files.readAllBytes(dir.resolve("one.dat")), Files.readAllBytes(dir.resolve("two.dat")));
    }

    @Test
    void aThousandUniformChunksSaveSmall(@TempDir Path dir) throws IOException {
        PaintRecord record = new PaintRecord();
        for (int i = 0; i < 1000; i++) {
            record.put(i % 40 - 20, i / 40 - 10, forest);
        }
        Path file = dir.resolve("world.dat");

        record.save(file);

        assertTrue(Files.size(file) < 30_000, "1,000 uniform chunks took " + Files.size(file) + " bytes");
    }

    @Test
    void aTruncatedFileFailsToLoad(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("world.dat");
        manyChunks().save(file);
        byte[] bytes = Files.readAllBytes(file);
        Files.write(file, Arrays.copyOf(bytes, bytes.length / 2));

        assertThrows(IOException.class, () -> PaintRecord.load(file));
    }

    @Test
    void aFileOfAnotherKindFailsToLoad(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("world.dat");
        Files.writeString(file, "chunks: []");

        assertThrows(IOException.class, () -> PaintRecord.load(file));
    }

    @Test
    void anEmptyFileFailsToLoad(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("world.dat");
        Files.write(file, new byte[0]);

        assertThrows(IOException.class, () -> PaintRecord.load(file));
    }

    // The format is the contract with the files already on disk, so one is written here by hand.
    @Test
    void aFileWrittenByHandInTheDocumentedFormatLoads(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("world.dat");
        gzipped(file, out -> {
            header(out, MAGIC, 1);
            chunk(out, 0, 1);
        });

        PaintRecord record = PaintRecord.load(file);

        assertTrue(record.fillDone());
        assertEquals(Set.of("r.0.0.mca"), record.finishedFiles());
        assertEquals(1, record.chunks().size());
        ChunkPaint paint = record.get(5, -6);
        assertEquals(List.of(new BiomeSpan("cbc:a", -64, 319)), paint.column(0, 0));
        assertEquals(List.of(new BiomeSpan("cbc:a", -64, 319)), paint.column(3, 3));
        assertEquals(List.of(), paint.column(1, 0));
    }

    @Test
    void aFileWithAnotherMagicFailsToLoad(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("world.dat");
        gzipped(file, out -> {
            header(out, MAGIC + 1, 1);
            chunk(out, 0, 1);
        });

        assertThrows(IOException.class, () -> PaintRecord.load(file));
    }

    // Whole and sound in every other way, so only the version can be what is refused.
    @Test
    void aFileOfAnotherVersionFailsToLoad(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("world.dat");
        gzipped(file, out -> {
            header(out, MAGIC, 2);
            chunk(out, 0, 1);
        });

        assertThrows(IOException.class, () -> PaintRecord.load(file));
    }

    @Test
    void aChunkPointingPastItsPaletteFailsToLoad(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("world.dat");
        gzipped(file, out -> {
            header(out, MAGIC, 1);
            chunk(out, 0, 2); // the palette has entries 0 and 1
        });

        assertThrows(IOException.class, () -> PaintRecord.load(file));
    }

    @Test
    void aSpanPointingPastTheBiomeTableFailsToLoad(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("world.dat");
        gzipped(file, out -> {
            header(out, MAGIC, 1);
            chunk(out, 1, 1); // the table names one biome, number 0
        });

        assertThrows(IOException.class, () -> PaintRecord.load(file));
    }

    @Test
    void aFileWithDataAfterItsLastChunkFailsToLoad(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("world.dat");
        gzipped(file, out -> {
            header(out, MAGIC, 1);
            chunk(out, 0, 1);
            out.writeByte(0);
        });

        assertThrows(IOException.class, () -> PaintRecord.load(file));
    }

    // Deflate has no check of its own: damage that still parses is only found by the checksum at the end.
    @Test
    void aFileWhoseChecksumIsWrongFailsToLoad(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("world.dat");
        manyChunks().save(file);
        byte[] bytes = Files.readAllBytes(file);
        bytes[bytes.length - 8] ^= 1; // the gzip trailer starts with the CRC-32 of the data
        Files.write(file, bytes);

        assertThrows(IOException.class, () -> PaintRecord.load(file));
    }

    @FunctionalInterface
    private interface Body {
        void write(DataOutputStream out) throws IOException;
    }

    private static void gzipped(Path file, Body body) throws IOException {
        try (DataOutputStream out = new DataOutputStream(new GZIPOutputStream(Files.newOutputStream(file)))) {
            body.write(out);
        }
    }

    /** The start of a file in the documented format: a done fill with one finished file, one biome, one chunk. */
    private static void header(DataOutputStream out, int magic, int version) throws IOException {
        out.writeInt(magic);
        out.writeShort(version);
        out.writeBoolean(true);
        out.writeInt(1);
        out.writeUTF("r.0.0.mca");
        out.writeInt(1);
        out.writeUTF("cbc:a");
        out.writeInt(1);
    }

    /**
     * The chunk at (5, -6), with a palette of two entries: no span, and one span of the table's biome number
     * {@code biomeIndex} from -64 to 319. Column 0 uses the second entry, the last column uses entry
     * {@code lastColumn}, and the rest use the first.
     */
    private static void chunk(DataOutputStream out, int biomeIndex, int lastColumn) throws IOException {
        out.writeInt(5);
        out.writeInt(-6);
        out.writeByte(2);
        out.writeByte(0);
        out.writeByte(1);
        out.writeShort(biomeIndex);
        out.writeShort(-64);
        out.writeShort(319);
        byte[] indexes = new byte[16];
        indexes[0] = 1;
        indexes[15] = (byte) lastColumn;
        out.write(indexes);
    }
}
