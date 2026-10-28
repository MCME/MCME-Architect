package com.mcmiddleearth.architect.mapLayers.customBiomes;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import static com.mcmiddleearth.architect.mapLayers.customBiomes.RegionFixtures.BYTE;
import static com.mcmiddleearth.architect.mapLayers.customBiomes.RegionFixtures.SECTOR;
import static com.mcmiddleearth.architect.mapLayers.customBiomes.RegionFixtures.biomes;
import static com.mcmiddleearth.architect.mapLayers.customBiomes.RegionFixtures.biomesWith;
import static com.mcmiddleearth.architect.mapLayers.customBiomes.RegionFixtures.tag;
import static org.junit.jupiter.api.Assertions.*;

// The reader is tried on region files written here, one feature of the format each, and on one real file. The fixture
// resources/customBiomes/r.-1.-1.mca is the chunks x -6 to -1 and z -6 to -1 of the 26.2 test world's file of that
// name: its 36 stored sectors, cut out as saved, all zlib. The world's generator biome cbc:111g380-5p is everywhere in
// them. The Quay patch, mcme:custom_003, covers blocks x -80 to -17, z -84 to -21 and Y -64 to -1, so it lies in chunks
// -5 to -2 and reaches part of the rows -6 and -2. Chunk (-6, -6) also has mcme:custom_002 in a corner of it.
class RegionFileReaderTest {

    private static final String CBC = "cbc:111g380-5p";
    private static final String QUAY = "mcme:custom_003";
    private static final String A = "cbc:a";

    /** What a vanilla-height world's chunk, painted from bottom to top with one biome, reads as in each column. */
    private static final List<BiomeSpan> A_EVERYWHERE = List.of(new BiomeSpan(A, -64, 319));

    /** What a read gave: the counts, and the paint each chunk reached the sink with. */
    private record Result(RegionFileReader.Stats stats, Map<Long, ChunkPaint> paints) {

        ChunkPaint at(int chunkX, int chunkZ) {
            return paints.get(PaintRecord.chunkKey(chunkX, chunkZ));
        }
    }

    private static Result read(Path file, Set<String> ids) throws IOException {
        Map<Long, ChunkPaint> paints = new LinkedHashMap<>();
        RegionFileReader.Stats stats = new RegionFileReader(ids).read(file, (x, z, paint) ->
                assertNull(paints.put(PaintRecord.chunkKey(x, z), paint),
                        "chunk " + x + ", " + z + " reached the sink twice"));
        return new Result(stats, paints);
    }

    private static Path fixture() throws IOException {
        URL url = RegionFileReaderTest.class.getResource("/customBiomes/r.-1.-1.mca");
        assertNotNull(url, "the fixture is a test resource");
        try {
            return Path.of(url.toURI());
        } catch (URISyntaxException e) {
            throw new IOException(e);
        }
    }

    private static Map<Long, ChunkPaint> readFixture(Set<String> ids) throws IOException {
        return read(fixture(), ids).paints();
    }

    private static List<String> uniform(String id) {
        return Collections.nCopies(64, id);
    }

    /** A chunk of a vanilla-height world, sections -4 to 19, every cell of it the one biome. */
    private static byte[] paintedChunk(int dataVersion, String id) {
        Map<Integer, List<String>> sections = new TreeMap<>();
        for (int y = -4; y < 20; y++) {
            sections.put(y, uniform(id));
        }
        return RegionFixtures.chunk(dataVersion, "minecraft:full", sections);
    }

    /** Like {@link #paintedChunk}, with the chunk's place saved in it, as the game saves it. */
    private static byte[] paintedChunkAt(int chunkX, int chunkZ, String id) {
        Map<Integer, List<String>> sections = new TreeMap<>();
        for (int y = -4; y < 20; y++) {
            sections.put(y, uniform(id));
        }
        return RegionFixtures.chunkAt(chunkX, chunkZ, 4903, "minecraft:full", sections);
    }

    /** Changes four bytes of the region file at the given place. */
    private static void patch(Path file, int at, int value) throws IOException {
        byte[] bytes = Files.readAllBytes(file);
        ByteBuffer.wrap(bytes).putInt(at, value);
        Files.write(file, bytes);
    }

    @Test
    void theQuayPatchFromThe26_2TestWorldReadsAsPainted() throws IOException {
        Map<Long, ChunkPaint> got = readFixture(Set.of(CBC, QUAY));
        // block (-48, -52) is the patch centre: chunk (-3, -4), column (0, 3)
        assertEquals(List.of(new BiomeSpan(CBC, 0, 319), new BiomeSpan(QUAY, -64, -1)),
                got.get(PaintRecord.chunkKey(-3, -4)).column(0, 3));
        // block (-8, -8): chunk (-1, -1), column (2, 2), outside the patch
        assertEquals(List.of(new BiomeSpan(CBC, -64, 319)), got.get(PaintRecord.chunkKey(-1, -1)).column(2, 2));
    }

    @Test
    void everyChunkOfTheFixtureReachesTheSinkAndReadsWithoutASkip() throws IOException {
        Result result = read(fixture(), Set.of(CBC, QUAY));

        assertEquals(new RegionFileReader.Stats(36, 36, 36, Map.of()), result.stats());
        Set<Long> expected = new HashSet<>();
        for (int x = -6; x <= -1; x++) {
            for (int z = -6; z <= -1; z++) {
                expected.add(PaintRecord.chunkKey(x, z));
            }
        }
        assertEquals(expected, result.paints().keySet());
    }

    // The patch ends at block z -21, in the cell row of the columns at z 3 of chunk row -2: those three rows of columns
    // hold the patch and the last does not. The sections there have two ids, so their data is one bit to an entry.
    @Test
    void aPartlyPaintedChunkReadsItsPackedBiomesColumnByColumn() throws IOException {
        ChunkPaint paint = readFixture(Set.of(CBC, QUAY)).get(PaintRecord.chunkKey(-5, -2));

        for (int x = 0; x < 4; x++) {
            for (int z = 0; z < 3; z++) {
                assertEquals(List.of(new BiomeSpan(CBC, 0, 319), new BiomeSpan(QUAY, -64, -1)), paint.column(x, z),
                        "column " + x + ", " + z);
            }
            assertEquals(List.of(new BiomeSpan(CBC, -64, 319)), paint.column(x, 3), "column " + x + ", 3");
        }
    }

    @Test
    void idsTheServerDoesNotDefineAreSkipped() throws IOException {
        Map<Long, ChunkPaint> got = readFixture(Set.of(CBC));
        assertEquals(Set.of(CBC), got.get(PaintRecord.chunkKey(-3, -4)).biomes());
        // the cells of the patch are left out, and they do not stretch the generator biome's span down
        assertEquals(List.of(new BiomeSpan(CBC, 0, 319)), got.get(PaintRecord.chunkKey(-3, -4)).column(0, 3));
    }

    // Chunk (-6, -6) has mcme:custom_002 in the columns x 0 and 1, z 0 to 2, from the floor to the ceiling, and the
    // generator biome elsewhere.
    @Test
    void aColumnPaintedOnlyWithAnIdTheServerDoesNotDefineReadsAsVanilla() throws IOException {
        ChunkPaint without = readFixture(Set.of(CBC, QUAY)).get(PaintRecord.chunkKey(-6, -6));
        ChunkPaint with = readFixture(Set.of(CBC, QUAY, "mcme:custom_002")).get(PaintRecord.chunkKey(-6, -6));

        assertEquals(List.of(), without.column(0, 0));
        assertEquals(List.of(new BiomeSpan(CBC, -64, 319)), without.column(2, 0));
        assertEquals(List.of(new BiomeSpan("mcme:custom_002", -64, 319)), with.column(0, 0));
        assertEquals(List.of(new BiomeSpan(CBC, -64, 319)), with.column(2, 0));
    }

    @Test
    void everyStoredChunkReachesTheSinkEvenWithoutPaint(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("r.0.0.mca");
        RegionFixtures.region(file, Map.of(
                0, paintedChunk(4903, "minecraft:plains"), // not a custom namespace in it, so not even parsed
                1, paintedChunk(4903, A),
                2, paintedChunk(4903, "cbc:other"), // a custom namespace, but an id the server does not define
                33, paintedChunk(4903, "minecraft:desert")), 2);

        Result result = read(file, Set.of(A));

        assertEquals(Set.of(PaintRecord.chunkKey(0, 0), PaintRecord.chunkKey(1, 0), PaintRecord.chunkKey(2, 0),
                PaintRecord.chunkKey(1, 1)), result.paints().keySet());
        assertEquals(ChunkPaint.EMPTY, result.at(0, 0));
        assertEquals(A_EVERYWHERE, result.at(1, 0).column(0, 0));
        assertEquals(ChunkPaint.EMPTY, result.at(2, 0));
        assertEquals(ChunkPaint.EMPTY, result.at(1, 1));
        assertEquals(new RegionFileReader.Stats(4, 2, 1, Map.of()), result.stats());
    }

    @Test
    void gzipAndUncompressedChunksReadLikeZlib(@TempDir Path dir) throws IOException {
        byte[] nbt = paintedChunk(4903, A);
        Map<Integer, ChunkPaint> byCompression = new TreeMap<>();
        for (int compression : new int[] {1, 2, 3}) {
            Path file = dir.resolve("r." + compression + ".0.mca");
            RegionFixtures.region(file, Map.of(0, nbt), compression);

            Result result = read(file, Set.of(A));

            assertEquals(new RegionFileReader.Stats(1, 1, 1, Map.of()), result.stats(), "compression " + compression);
            byCompression.put(compression, result.at(compression * 32, 0));
        }

        assertEquals(A_EVERYWHERE, byCompression.get(2).column(0, 0));
        assertEquals(byCompression.get(2), byCompression.get(1));
        assertEquals(byCompression.get(2), byCompression.get(3));
    }

    @Test
    void anOversizedChunkIsReadFromItsMccFile(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("r.-2.1.mca");
        RegionFixtures.regionWithOversizedChunk(file, 7, paintedChunk(4903, A));

        Result result = read(file, Set.of(A));

        // the chunk is (-2 * 32 + 7, 1 * 32) = (-57, 32), and its data is in c.-57.32.mcc beside the file
        assertTrue(Files.exists(dir.resolve("c.-57.32.mcc")));
        assertEquals(A_EVERYWHERE, result.at(-57, 32).column(3, 3));
        assertEquals(new RegionFileReader.Stats(1, 1, 1, Map.of()), result.stats());
    }

    @Test
    void aMissingMccFileIsSkippedAndCounted(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("r.-2.1.mca");
        RegionFixtures.regionWithOversizedChunk(file, 7, paintedChunk(4903, A));
        Files.delete(dir.resolve("c.-57.32.mcc"));

        Result result = read(file, Set.of(A));

        assertEquals(Map.of("unreadable: NoSuchFileException", 1), result.stats().skipped());
        assertEquals(ChunkPaint.EMPTY, result.at(-57, 32));
    }

    @Test
    void anLz4ChunkIsSkippedAndCounted(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("r.0.0.mca");
        // the fixture does not compress with LZ4: the bytes stay as they are, and the reader must not look at them
        RegionFixtures.region(file, Map.of(0, paintedChunk(4903, A), 1, paintedChunk(4903, A)), 4);

        Result result = read(file, Set.of(A));

        assertEquals(2, result.stats().skipped().get("LZ4 is not supported"));
        assertEquals(new RegionFileReader.Stats(2, 0, 0, Map.of("LZ4 is not supported", 2)), result.stats());
        assertEquals(ChunkPaint.EMPTY, result.at(0, 0));
        assertEquals(ChunkPaint.EMPTY, result.at(1, 0));
    }

    @Test
    void aCompressionTypeNobodyKnowsIsSkippedAndCounted(@TempDir Path dir) throws IOException {
        for (int type : new int[] {0, 9, 127}) {
            Path file = dir.resolve("r." + type + ".0.mca");
            RegionFixtures.region(file, Map.of(0, paintedChunk(4903, A)), type);

            Result result = read(file, Set.of(A));

            assertEquals(Map.of("unknown compression " + type, 1), result.stats().skipped());
            assertEquals(ChunkPaint.EMPTY, result.at(type * 32, 0));
        }
    }

    @Test
    void anOldLevelLayoutChunkIsSkippedAndCounted(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("r.0.0.mca");
        RegionFixtures.region(file, Map.of(0, RegionFixtures.levelChunk(), 1, paintedChunk(4903, A)), 2);

        Result result = read(file, Set.of(A));

        assertEquals(1, result.stats().skipped().get("old chunk layout (Level)"));
        assertEquals(new RegionFileReader.Stats(2, 1, 1, Map.of("old chunk layout (Level)", 1)), result.stats());
        assertEquals(ChunkPaint.EMPTY, result.at(0, 0));
        assertEquals(A_EVERYWHERE, result.at(1, 0).column(0, 0), "the chunk beside it still reads");
    }

    @Test
    void aChunkPastTheEndOfItsFileIsSkippedAndCounted(@TempDir Path dir) throws IOException {
        // the file ends 50 bytes into the second chunk
        Path cut = dir.resolve("r.0.0.mca");
        RegionFixtures.region(cut, Map.of(0, paintedChunk(4903, A), 1, paintedChunk(4903, A)), 2);
        byte[] bytes = Files.readAllBytes(cut);
        int secondSector = ByteBuffer.wrap(bytes).getInt(4) >>> 8;
        Files.write(cut, Arrays.copyOf(bytes, secondSector * SECTOR + 50));

        Result truncated = read(cut, Set.of(A));

        assertEquals(1, truncated.stats().skipped().get("past the end of the file"));
        assertEquals(new RegionFileReader.Stats(2, 1, 1, Map.of("past the end of the file", 1)), truncated.stats());
        assertEquals(A_EVERYWHERE, truncated.at(0, 0).column(0, 0), "the whole one before it still reads");
        assertEquals(ChunkPaint.EMPTY, truncated.at(1, 0));

        // a header that points where the file does not reach
        Path headerOnly = dir.resolve("r.1.0.mca");
        Files.write(headerOnly, Arrays.copyOf(bytes, 2 * SECTOR));

        Result missing = read(headerOnly, Set.of(A));

        assertEquals(new RegionFileReader.Stats(2, 0, 0, Map.of("past the end of the file", 2)), missing.stats());
        assertEquals(ChunkPaint.EMPTY, missing.at(32, 0));
        assertEquals(ChunkPaint.EMPTY, missing.at(33, 0));
    }

    @Test
    void sectionsAboveY320AreRead(@TempDir Path dir) throws IOException {
        Map<Integer, List<String>> tall = new TreeMap<>();
        for (int y = -4; y <= 46; y++) {
            tall.put(y, uniform(A));
        }
        Path file = dir.resolve("r.0.0.mca");
        RegionFixtures.region(file, Map.of(
                0, RegionFixtures.chunk(4903, "minecraft:full", tall),
                1, RegionFixtures.chunk(4903, "minecraft:full", Map.of(46, uniform(A)))), 2);

        Result result = read(file, Set.of(A));

        // section 46 is the blocks 736 to 751
        assertEquals(List.of(new BiomeSpan(A, -64, 751)), result.at(0, 0).column(0, 0));
        assertEquals(List.of(new BiomeSpan(A, 736, 751)), result.at(1, 0).column(0, 0));
    }

    @Test
    void olderDataVersionsReadTheSame(@TempDir Path dir) throws IOException {
        // the DataVersions that production's worlds hold, and the 26.2 test world's
        int[] versions = {2860, 2975, 3337, 3955, 4189, 4903};
        Map<Integer, byte[]> chunks = new TreeMap<>();
        for (int i = 0; i < versions.length; i++) {
            chunks.put(i, paintedChunk(versions[i], A));
        }
        Path file = dir.resolve("r.0.0.mca");
        RegionFixtures.region(file, chunks, 2);

        Result result = read(file, Set.of(A));

        assertEquals(new RegionFileReader.Stats(6, 6, 6, Map.of()), result.stats());
        for (int i = 0; i < versions.length; i++) {
            assertEquals(A_EVERYWHERE, result.at(i, 0).column(0, 0), "DataVersion " + versions[i]);
            assertEquals(result.at(0, 0), result.at(i, 0), "DataVersion " + versions[i]);
        }
    }

    @Test
    void aSectionWithBiomeDataOfTheWrongLengthIsSkippedAndTheRestOfTheChunkStillCounts(@TempDir Path dir)
            throws IOException {
        byte[] nbt = RegionFixtures.chunkOf(4903, "minecraft:full", List.of(
                out -> {
                    tag(out, BYTE, "Y");
                    out.writeByte(0);
                    biomes(out, uniform(A));
                },
                out -> {
                    tag(out, BYTE, "Y");
                    out.writeByte(1);
                    biomesWith(out, List.of(A, "cbc:b", "minecraft:plains"), new long[1]); // 3 ids need 2 longs
                }));
        Path file = dir.resolve("r.0.0.mca");
        RegionFixtures.region(file, Map.of(0, nbt), 2);

        Result result = read(file, Set.of(A, "cbc:b"));

        assertEquals(new RegionFileReader.Stats(1, 1, 1, Map.of("biome data of unexpected length", 1)), result.stats());
        assertEquals(List.of(new BiomeSpan(A, 0, 15)), result.at(0, 0).column(0, 0), "section 0 is all that reads");
    }

    @Test
    void aChunkThatCannotBeInflatedIsSkippedAndCounted(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("r.0.0.mca");
        RegionFixtures.region(file, Map.of(0, paintedChunk(4903, A), 1, paintedChunk(4903, A)), 2);
        // the first chunk starts at sector 2: its length, its type, then the two bytes of the zlib header
        byte[] bytes = Files.readAllBytes(file);
        bytes[2 * SECTOR + 5] = (byte) 0xFF;
        bytes[2 * SECTOR + 6] = (byte) 0xFF;
        Files.write(file, bytes);

        Result result = read(file, Set.of(A));

        assertEquals(new RegionFileReader.Stats(2, 1, 1, Map.of("unreadable: ZipException", 1)), result.stats());
        assertEquals(ChunkPaint.EMPTY, result.at(0, 0));
        assertEquals(A_EVERYWHERE, result.at(1, 0).column(0, 0), "the chunk beside it still reads");
    }

    @Test
    void aChunkThatIsNotNbtIsSkippedAndCounted(@TempDir Path dir) throws IOException {
        byte[] rubbish = "cbc: this is no NBT".getBytes(StandardCharsets.UTF_8);
        Path file = dir.resolve("r.0.0.mca");
        RegionFixtures.region(file, Map.of(0, rubbish, 1, paintedChunk(4903, A)), 2);

        Result result = read(file, Set.of(A));

        assertEquals(new RegionFileReader.Stats(2, 1, 1, Map.of("unreadable: IOException", 1)), result.stats());
        assertEquals(ChunkPaint.EMPTY, result.at(0, 0));
    }

    @Test
    void aChunkLongerThanItsSectorsIsSkippedAndCounted(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("r.0.0.mca");
        RegionFixtures.region(file, Map.of(0, paintedChunk(4903, A)), 2);
        patch(file, 2 * SECTOR, 5_000_000); // the length, which only fills one sector

        Result result = read(file, Set.of(A));

        assertEquals(Map.of("unreadable: IOException", 1), result.stats().skipped());
        assertEquals(ChunkPaint.EMPTY, result.at(0, 0));
    }

    @Test
    void aColumnKeepsEachBiomeFromItsLowestToItsHighestCell(@TempDir Path dir) throws IOException {
        // sections -4: a, 0 and 1: b, 2: a again, so a has b between its cells
        Map<Integer, List<String>> sections = new TreeMap<>();
        sections.put(-4, uniform(A));
        sections.put(0, uniform("cbc:b"));
        sections.put(1, uniform("cbc:b"));
        sections.put(2, uniform(A));
        Path file = dir.resolve("r.0.0.mca");
        RegionFixtures.region(file, Map.of(0, RegionFixtures.chunk(4903, "minecraft:full", sections)), 2);

        Result result = read(file, Set.of(A, "cbc:b"));

        // highest first: a reaches up to block 47, b to 31
        assertEquals(List.of(new BiomeSpan(A, -64, 47), new BiomeSpan("cbc:b", 0, 31)), result.at(0, 0).column(1, 2));
    }

    @Test
    void aBiomeInOneRowOfCellsOfASectionSpansThatRowOnly(@TempDir Path dir) throws IOException {
        // the cells y * 16 + z * 4 + x with y = 2 are the blocks 8 to 11 above the section's bottom
        List<String> ids = new ArrayList<>(Collections.nCopies(64, "minecraft:plains"));
        for (int i = 32; i < 48; i++) {
            ids.set(i, A);
        }
        Path file = dir.resolve("r.0.0.mca");
        RegionFixtures.region(file, Map.of(0, RegionFixtures.chunk(4903, "minecraft:full", Map.of(5, ids))), 2);

        Result result = read(file, Set.of(A));

        // section 5 starts at block 80
        assertEquals(List.of(new BiomeSpan(A, 88, 91)), result.at(0, 0).column(3, 3));
    }

    @Test
    void eachCellLandsInTheColumnOfItsXAndZ(@TempDir Path dir) throws IOException {
        // cell 9 = z 2, x 1, in the lowest row
        List<String> ids = new ArrayList<>(Collections.nCopies(64, "minecraft:plains"));
        ids.set(9, A);
        Path file = dir.resolve("r.0.0.mca");
        RegionFixtures.region(file, Map.of(0, RegionFixtures.chunk(4903, "minecraft:full", Map.of(0, ids))), 2);

        ChunkPaint paint = read(file, Set.of(A)).at(0, 0);

        for (int z = 0; z < 4; z++) {
            for (int x = 0; x < 4; x++) {
                List<BiomeSpan> expected = x == 1 && z == 2 ? List.of(new BiomeSpan(A, 0, 3)) : List.of();
                assertEquals(expected, paint.column(x, z), "column " + x + ", " + z);
            }
        }
    }

    @Test
    void chunkCoordinatesComeFromTheFileNameAndTheChunksPlaceInIt(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("r.3.-2.mca");
        byte[] nbt = paintedChunk(4903, A);
        RegionFixtures.region(file, Map.of(0, nbt, 31, nbt, 32, nbt, 5 + 7 * 32, nbt, 1023, nbt), 2);

        Result result = read(file, Set.of(A));

        // the region's first chunk is (3 * 32, -2 * 32): x runs along a row of 32, z down the rows
        assertEquals(Set.of(PaintRecord.chunkKey(96, -64), PaintRecord.chunkKey(127, -64),
                PaintRecord.chunkKey(96, -63), PaintRecord.chunkKey(101, -57), PaintRecord.chunkKey(127, -33)),
                result.paints().keySet());
    }

    // The server may save a chunk again while its file is read: a chunk that outgrew its sectors moves, and another
    // chunk of the file may be written where it was. The file's table, read first, still points there, so what is
    // found there says it is another chunk of the file: it is skipped, and counted. A chunk that names the same slot
    // in another region, as in a region file copied from elsewhere, is read where it lies, as the game loads it there.
    @Test
    void aChunkThatSaysItIsAnotherChunkOfItsFileIsSkippedAndCounted(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("r.1.0.mca"); // chunks x 32 to 63, z 0 to 31
        RegionFixtures.region(file, Map.of(
                5, paintedChunkAt(39, 3, A), // in the slot of chunk (37, 0)
                6, paintedChunkAt(38, 0, A),
                7, paintedChunkAt(7, 32, A)), 2); // the slot of (39, 0), in another region

        Result result = read(file, Set.of(A));

        assertEquals(ChunkPaint.EMPTY, result.at(37, 0), "not taken for chunk 37, 0");
        assertEquals(A_EVERYWHERE, result.at(38, 0).column(0, 0));
        assertEquals(A_EVERYWHERE, result.at(39, 0).column(0, 0), "a chunk of a copied file reads where it lies");
        assertEquals(new RegionFileReader.Stats(3, 2, 2, Map.of("moved while being read", 1)), result.stats());
    }

    // As in the game, a start inside the header, or a run of no sectors, is no chunk at all: not read, and not counted.
    @Test
    void aLocationInsideTheHeaderOrWithoutSectorsIsNoChunk(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("r.0.0.mca");
        RegionFixtures.region(file, Map.of(0, paintedChunk(4903, A)), 2);
        patch(file, 4, 0x0000_0101); // chunk 1: sector 1, one sector, which is the header's second half
        patch(file, 8, 0x0000_0001); // chunk 2: sector 0
        patch(file, 12, 0x0000_0500); // chunk 3: sector 5, but no sectors

        Result result = read(file, Set.of(A));

        assertEquals(new RegionFileReader.Stats(1, 1, 1, Map.of()), result.stats());
        assertEquals(Set.of(PaintRecord.chunkKey(0, 0)), result.paints().keySet());
    }

    @Test
    void aFileWithoutChunksReadsAsNothing(@TempDir Path dir) throws IOException {
        // an empty file, one that is only a header, and one shorter than that
        int[] sizes = {0, 2 * SECTOR, 100};
        for (int size : sizes) {
            Path file = dir.resolve("r." + size + ".0.mca");
            Files.write(file, new byte[size]);

            Result result = read(file, Set.of(A));

            assertEquals(new RegionFileReader.Stats(0, 0, 0, Map.of()), result.stats(), size + " bytes");
            assertTrue(result.paints().isEmpty());
        }
    }

    @Test
    void aFileThatIsNotNamedLikeARegionFileIsRefused(@TempDir Path dir) throws IOException {
        for (String name : List.of("region.mca", "r.0.mca", "r.a.b.mca", "r.0.0.mca.bak", "r.0.0.mcc", "x.0.0.mca")) {
            Path file = dir.resolve(name);
            Files.write(file, new byte[2 * SECTOR]);

            assertThrows(IOException.class, () -> read(file, Set.of(A)), name);
        }
    }

    @Test
    void aFileThatIsNotThereFailsTheRead(@TempDir Path dir) {
        assertThrows(NoSuchFileException.class, () -> read(dir.resolve("r.0.0.mca"), Set.of(A)));
    }

    @Test
    void theCountsOfSkippedChunksCannotBeChanged(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("r.0.0.mca");
        RegionFixtures.region(file, Map.of(0, paintedChunk(4903, A)), 4);

        Map<String, Integer> skipped = read(file, Set.of(A)).stats().skipped();

        assertThrows(UnsupportedOperationException.class, () -> skipped.put("anything", 1));
    }

    @Test
    void aReaderWithoutIdsFindsNothingAndParsesNothing(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("r.0.0.mca");
        RegionFixtures.region(file, Map.of(0, paintedChunk(4903, A)), 2);

        Result result = read(file, Set.of());

        assertEquals(new RegionFileReader.Stats(1, 0, 0, Map.of()), result.stats());
        assertEquals(ChunkPaint.EMPTY, result.at(0, 0));
    }

    // A read of saved copies is offered each chunk the file stores, with the second its header says the game last
    // saved it at, and reads only the chunks it chooses. A chunk that cannot be read, or that says it is another chunk
    // of the file, reaches the sink as null, not as a chunk without paint: the game may be writing it.
    @Test
    void aSavedReadSeesWhenEachChunkWasSavedAndReadsOnlyTheChosenOnes(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("r.1.2.mca"); // chunks x 32 to 63, z 64 to 95
        byte[] rubbish = "cbc: this is no NBT".getBytes(StandardCharsets.UTF_8);
        RegionFixtures.region(file, Map.of(
                0, paintedChunk(4903, A),
                1, paintedChunk(4903, A),
                2, rubbish,
                3, paintedChunk(4903, "minecraft:plains"),
                4, paintedChunkAt(32, 64, A)), 2, 1_800_000_000L); // in the slot of chunk (36, 64)
        RegionFixtures.savedAt(file, 1, 1_700_000_000L);
        Map<Long, Long> offered = new LinkedHashMap<>();
        Map<Long, ChunkPaint> read = new LinkedHashMap<>();

        new RegionFileReader(Set.of(A)).readSaved(file, (x, z, savedAt) -> {
            offered.put(PaintRecord.chunkKey(x, z), savedAt);
            return savedAt >= 1_800_000_000L;
        }, (x, z, savedAt, paint) -> {
            assertEquals(1_800_000_000L, savedAt);
            assertFalse(read.containsKey(PaintRecord.chunkKey(x, z)), "chunk " + x + ", " + z + " read twice");
            read.put(PaintRecord.chunkKey(x, z), paint);
        });

        assertEquals(Map.of(PaintRecord.chunkKey(32, 64), 1_800_000_000L, PaintRecord.chunkKey(33, 64), 1_700_000_000L,
                PaintRecord.chunkKey(34, 64), 1_800_000_000L, PaintRecord.chunkKey(35, 64), 1_800_000_000L,
                PaintRecord.chunkKey(36, 64), 1_800_000_000L), offered);
        assertEquals(Set.of(PaintRecord.chunkKey(32, 64), PaintRecord.chunkKey(34, 64), PaintRecord.chunkKey(35, 64),
                PaintRecord.chunkKey(36, 64)), read.keySet(), "the chunk saved before the second chosen is not read");
        assertEquals(A_EVERYWHERE, read.get(PaintRecord.chunkKey(32, 64)).column(0, 0));
        assertNull(read.get(PaintRecord.chunkKey(34, 64)), "a chunk that cannot be read");
        assertEquals(ChunkPaint.EMPTY, read.get(PaintRecord.chunkKey(35, 64)), "a chunk without custom biomes");
        assertNull(read.get(PaintRecord.chunkKey(36, 64)), "a chunk that says it is another of the file");
    }

    @Test
    void aSavedReadOfAFileThatIsNotThereFails(@TempDir Path dir) {
        assertThrows(NoSuchFileException.class, () -> new RegionFileReader(Set.of(A))
                .readSaved(dir.resolve("r.0.0.mca"), (x, z, savedAt) -> true, (x, z, savedAt, paint) -> { }));
    }
}
