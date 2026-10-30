package com.mcmiddleearth.architect.mapLayers.customBiomes;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

// Reading a chunk asks the sampler about one block in each 4 x 4 x 4 cell and keeps, in each column, every custom
// biome's lowest and highest cell. The Paper side (World#getBiome and the biome registry) has no test here: MockBukkit
// has no datapack biomes.
class ChunkPaintReaderTest {

    /** One question put to the sampler. */
    private record Cell(int x, int y, int z) {}

    @Test
    void aUniformCustomChunkIsOneSpanFromBottomToTop() {
        ChunkPaint paint = ChunkPaintReader.read((x, y, z) -> "cbc:a", -64, 320);

        assertEquals(List.of(new BiomeSpan("cbc:a", -64, 319)), paint.column(2, 1));
        assertEquals(1, paint.paletteSize());
    }

    @Test
    void vanillaCellsAreLeftOut() {
        assertTrue(ChunkPaintReader.read((x, y, z) -> null, -64, 320).isEmpty());
    }

    @Test
    void aCaveBiomeUnderAForestIsTwoSpansInEachColumn() {
        ChunkPaint paint = ChunkPaintReader.read((x, y, z) -> y < 0 ? "cbc:cave" : "cbc:forest", -64, 320);

        assertEquals(List.of(new BiomeSpan("cbc:forest", 0, 319), new BiomeSpan("cbc:cave", -64, -1)),
                paint.column(0, 0));
    }

    // The cells asked about are the ones at y = -64 and y = 300, so the spans end at -61 and 303: a cell counts whole.
    @Test
    void aBiomeInTwoSeparateHeightBandsSpansFromItsLowestToItsHighestCell() {
        ChunkPaint paint = ChunkPaintReader.read(
                (x, y, z) -> (y >= -64 && y < -60) || (y >= 300 && y < 304) ? "cbc:a" : null, -64, 320);

        assertEquals(List.of(new BiomeSpan("cbc:a", -64, 303)), paint.column(0, 0));
    }

    @Test
    void aTallWorldIsReadToItsTop() {
        assertEquals(751, ChunkPaintReader.read((x, y, z) -> "cbc:a", -64, 752).column(0, 0).get(0).maxY());
    }

    @Test
    void aWorldThatStartsAtZeroIsReadFromItsFloor() {
        ChunkPaint paint = ChunkPaintReader.read((x, y, z) -> "cbc:a", 0, 256);

        assertEquals(List.of(new BiomeSpan("cbc:a", 0, 255)), paint.column(3, 3));
    }

    @Test
    void onlyEveryFourthBlockIsSampled() {
        List<Cell> asked = new ArrayList<>();

        ChunkPaintReader.read((x, y, z) -> {
            asked.add(new Cell(x, y, z));
            return null;
        }, -64, 320);

        assertEquals(16 * (384 / 4), asked.size(), "16 columns, a cell every 4 blocks up");
        assertEquals(asked.size(), new HashSet<>(asked).size(), "no cell asked twice");
        for (Cell cell : asked) {
            assertTrue(cell.x() >= 0 && cell.x() < 16 && cell.x() % 4 == 0, "x of " + cell);
            assertTrue(cell.z() >= 0 && cell.z() < 16 && cell.z() % 4 == 0, "z of " + cell);
            assertTrue(cell.y() >= -64 && cell.y() < 320 && (cell.y() + 64) % 4 == 0, "y of " + cell);
        }
    }

    @Test
    void eachColumnIsReadOnItsOwn() {
        // cbc:b fills the blocks x 4 to 7 and z 8 to 11, which is the column at (1, 2); the rest is cbc:a
        ChunkPaint paint = ChunkPaintReader.read(
                (x, y, z) -> x >= 4 && x < 8 && z >= 8 && z < 12 ? "cbc:b" : "cbc:a", -64, 320);

        for (int z = 0; z < 4; z++) {
            for (int x = 0; x < 4; x++) {
                String biome = x == 1 && z == 2 ? "cbc:b" : "cbc:a";
                assertEquals(List.of(new BiomeSpan(biome, -64, 319)), paint.column(x, z), "column " + x + ", " + z);
            }
        }
        assertEquals(2, paint.paletteSize());
    }
}
