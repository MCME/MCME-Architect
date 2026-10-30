package com.mcmiddleearth.architect.mapLayers.customBiomes;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

// A chunk's paint is a value: the same columns always make the same paint, whatever order the spans came in.
class ChunkPaintTest {

    /** The same column sixteen times: a chunk painted one way all over. */
    static List<List<BiomeSpan>> sixteen(List<BiomeSpan> each) {
        return Collections.nCopies(16, each);
    }

    /** Sixteen vanilla columns, except the one at (x, z), which holds {@code spans}. */
    private static List<List<BiomeSpan>> onlyAt(int x, int z, List<BiomeSpan> spans) {
        List<List<BiomeSpan>> columns = new ArrayList<>(sixteen(List.of()));
        columns.set(z * 4 + x, spans);
        return columns;
    }

    @Test
    void aChunkPaintedOneWayIsOnePaletteEntry() {
        ChunkPaint paint = ChunkPaint.of(sixteen(List.of(new BiomeSpan("cbc:a", -64, 319))));

        assertEquals(1, paint.paletteSize());
        assertEquals(List.of(new BiomeSpan("cbc:a", -64, 319)), paint.column(3, 3));
    }

    @Test
    void spansAreOrderedFromTheTopDown() {
        ChunkPaint paint = ChunkPaint.of(sixteen(List.of(new BiomeSpan("cbc:cave", -64, -1),
                new BiomeSpan("cbc:forest", 0, 319))));

        assertEquals("cbc:forest", paint.column(0, 0).get(0).biome());
        assertEquals(List.of(new BiomeSpan("cbc:forest", 0, 319), new BiomeSpan("cbc:cave", -64, -1)),
                paint.column(0, 0));
    }

    // A tie on height is broken by id, so the same spans always list the same way.
    @Test
    void spansEndingAtTheSameHeightAreOrderedById() {
        ChunkPaint paint = ChunkPaint.of(sixteen(List.of(new BiomeSpan("cbc:b", 0, 63),
                new BiomeSpan("cbc:a", 0, 63))));

        assertEquals(List.of(new BiomeSpan("cbc:a", 0, 63), new BiomeSpan("cbc:b", 0, 63)), paint.column(1, 1));
    }

    @Test
    void anAllVanillaChunkIsEmpty() {
        ChunkPaint paint = ChunkPaint.of(sixteen(List.of()));

        assertTrue(paint.isEmpty());
        assertEquals(ChunkPaint.EMPTY, paint);
        assertTrue(paint.biomes().isEmpty());
    }

    @Test
    void equalPaintsAreEqual() {
        ChunkPaint one = ChunkPaint.of(onlyAt(2, 1, List.of(new BiomeSpan("cbc:a", 0, 63))));
        ChunkPaint two = ChunkPaint.of(onlyAt(2, 1, List.of(new BiomeSpan("cbc:a", 0, 63))));

        assertEquals(one, two);
        assertEquals(one.hashCode(), two.hashCode());
        assertNotEquals(one, ChunkPaint.of(onlyAt(1, 2, List.of(new BiomeSpan("cbc:a", 0, 63)))),
                "the same span in another column");
        assertNotEquals(one, ChunkPaint.of(onlyAt(2, 1, List.of(new BiomeSpan("cbc:a", 0, 67)))),
                "the same column with another height");
        assertNotEquals(one, ChunkPaint.EMPTY);
    }

    @Test
    void theOrderTheSpansAreGivenInDoesNotMatter() {
        ChunkPaint one = ChunkPaint.of(sixteen(List.of(new BiomeSpan("cbc:a", 0, 63), new BiomeSpan("cbc:b", 64, 127))));
        ChunkPaint two = ChunkPaint.of(sixteen(List.of(new BiomeSpan("cbc:b", 64, 127), new BiomeSpan("cbc:a", 0, 63))));

        assertEquals(one, two);
        assertEquals(one.hashCode(), two.hashCode());
        assertEquals(1, two.paletteSize(), "so the sixteen columns are one palette entry");
    }

    @Test
    void ofRejectsAnythingButSixteenColumns() {
        assertThrows(IllegalArgumentException.class, () -> ChunkPaint.of(List.of(List.of())));
        assertThrows(IllegalArgumentException.class, () -> ChunkPaint.of(Collections.nCopies(17, List.of())));
    }

    @Test
    void columnsAreCountedAlongXThenZ() {
        List<List<BiomeSpan>> columns = new ArrayList<>();
        for (int i = 0; i < 16; i++) {
            columns.add(List.of(new BiomeSpan("cbc:" + i, 0, 3)));
        }

        ChunkPaint paint = ChunkPaint.of(columns);

        for (int z = 0; z < 4; z++) {
            for (int x = 0; x < 4; x++) {
                assertEquals("cbc:" + (z * 4 + x), paint.column(x, z).get(0).biome(), "column " + x + ", " + z);
            }
        }
        assertEquals(16, paint.paletteSize(), "sixteen different columns");
    }

    @Test
    void aVanillaColumnIsAPaletteEntryToo() {
        ChunkPaint paint = ChunkPaint.of(onlyAt(0, 0, List.of(new BiomeSpan("cbc:a", 0, 63))));

        assertFalse(paint.isEmpty());
        assertEquals(2, paint.paletteSize());
        assertEquals(List.of(), paint.column(1, 0));
    }

    @Test
    void biomesListsEachBiomeOnceAcrossTheChunk() {
        List<List<BiomeSpan>> columns = new ArrayList<>(sixteen(List.of(new BiomeSpan("cbc:a", 0, 63))));
        columns.set(5, List.of(new BiomeSpan("cbc:b", 0, 63), new BiomeSpan("cbc:a", 64, 127)));

        assertEquals(Set.of("cbc:a", "cbc:b"), ChunkPaint.of(columns).biomes());
    }

    @Test
    void ofCopiesItsInputAndAColumnCannotBeChanged() {
        List<BiomeSpan> spans = new ArrayList<>(List.of(new BiomeSpan("cbc:a", 0, 63)));
        List<List<BiomeSpan>> columns = new ArrayList<>(sixteen(spans));
        ChunkPaint paint = ChunkPaint.of(columns);

        spans.add(new BiomeSpan("cbc:b", 64, 127));
        columns.set(0, List.of());

        assertEquals(List.of(new BiomeSpan("cbc:a", 0, 63)), paint.column(0, 0), "the paint keeps its own copy");
        assertThrows(UnsupportedOperationException.class,
                () -> paint.column(0, 0).add(new BiomeSpan("cbc:b", 64, 127)));
    }

    // The file keeps a column's span count in one unsigned byte.
    @Test
    void aColumnHoldsAtMost255Spans() {
        List<BiomeSpan> spans = new ArrayList<>();
        for (int i = 0; i < 255; i++) {
            spans.add(new BiomeSpan("cbc:a", i * 4, i * 4 + 3));
        }
        assertEquals(255, ChunkPaint.of(sixteen(spans)).column(0, 0).size());

        spans.add(new BiomeSpan("cbc:a", 255 * 4, 255 * 4 + 3));
        assertThrows(IllegalArgumentException.class, () -> ChunkPaint.of(sixteen(spans)));
    }

    // Past the edge of a row, x would otherwise read a column of the next row.
    @Test
    void aColumnOutsideTheChunkIsRefused() {
        ChunkPaint paint = ChunkPaint.of(sixteen(List.of()));

        assertThrows(IndexOutOfBoundsException.class, () -> paint.column(4, 0));
        assertThrows(IndexOutOfBoundsException.class, () -> paint.column(0, 4));
        assertThrows(IndexOutOfBoundsException.class, () -> paint.column(-1, 0));
        assertThrows(IndexOutOfBoundsException.class, () -> paint.column(0, -1));
    }

    @Test
    void aSpanNeedsItsBiome() {
        assertThrows(NullPointerException.class, () -> new BiomeSpan(null, 0, 3));
    }
}
