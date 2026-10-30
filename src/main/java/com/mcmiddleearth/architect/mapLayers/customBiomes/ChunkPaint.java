package com.mcmiddleearth.architect.mapLayers.customBiomes;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

/**
 * The custom biomes in one chunk, column by column. A column is 4 x 4 blocks wide, the grid biomes are stored on, so a
 * chunk has 16 of them. Immutable, and equal when its columns are.
 * <p>
 * Most chunks are painted the same way over a wide stretch, so the distinct columns are kept once, in a palette, and
 * each of the 16 columns only says which entry it is.
 */
public final class ChunkPaint {

    /** Columns along one side of a chunk, and in all. */
    private static final int SIDE = 4;
    static final int COLUMNS = SIDE * SIDE;
    /** The file keeps a column's span count in one unsigned byte. */
    private static final int MAX_SPANS = 255;
    /** Highest first. The last key makes the order total, so the same spans always list the same way. */
    private static final Comparator<BiomeSpan> TOP_FIRST = Comparator.comparingInt(BiomeSpan::maxY).reversed()
            .thenComparing(BiomeSpan::biome).thenComparingInt(BiomeSpan::minY);

    /** A chunk without a custom biome. */
    public static final ChunkPaint EMPTY = new ChunkPaint(List.of(List.of()), new byte[COLUMNS]);

    private final List<List<BiomeSpan>> palette;
    private final byte[] indexes;

    private ChunkPaint(List<List<BiomeSpan>> palette, byte[] indexes) {
        this.palette = palette;
        this.indexes = indexes;
    }

    /**
     * The paint of 16 columns, ordered {@code z * 4 + x}, where x and z (0 to 3) count the columns inside the chunk.
     * The spans are copied and ordered top first. A column without spans is vanilla; columns that are alike share a
     * palette entry.
     *
     * @throws IllegalArgumentException for anything but 16 columns, or a column with more than 255 spans
     */
    public static ChunkPaint of(List<List<BiomeSpan>> columns) {
        if (columns.size() != COLUMNS) {
            throw new IllegalArgumentException("A chunk has " + COLUMNS + " columns, not " + columns.size());
        }
        List<List<BiomeSpan>> palette = new ArrayList<>();
        Map<List<BiomeSpan>, Integer> placeInPalette = new HashMap<>();
        byte[] indexes = new byte[COLUMNS];
        for (int i = 0; i < COLUMNS; i++) {
            List<BiomeSpan> spans = new ArrayList<>(columns.get(i));
            if (spans.size() > MAX_SPANS) {
                throw new IllegalArgumentException("Column " + i + " holds " + spans.size() + " spans, and "
                        + MAX_SPANS + " is the most a file can keep");
            }
            spans.sort(TOP_FIRST);
            List<BiomeSpan> column = List.copyOf(spans);
            Integer place = placeInPalette.get(column);
            if (place == null) {
                place = palette.size();
                placeInPalette.put(column, place);
                palette.add(column);
            }
            indexes[i] = place.byteValue();
        }
        if (palette.size() == 1 && palette.get(0).isEmpty()) {
            return EMPTY;
        }
        return new ChunkPaint(List.copyOf(palette), indexes);
    }

    /** The spans of the column at x and z (each 0 to 3), top first; none for a vanilla column. Unmodifiable. */
    public List<BiomeSpan> column(int x, int z) {
        // checked one by one: an x past the row would otherwise read a column of the next row
        return palette.get(indexes[Objects.checkIndex(z, SIDE) * SIDE + Objects.checkIndex(x, SIDE)]);
    }

    /** Whether no column holds a custom biome. */
    public boolean isEmpty() {
        return palette.size() == 1 && palette.get(0).isEmpty();
    }

    /** The number of distinct columns, a vanilla one included. */
    public int paletteSize() {
        return palette.size();
    }

    /** Every biome id in the chunk, each once. */
    public Set<String> biomes() {
        Set<String> ids = new TreeSet<>();
        for (List<BiomeSpan> column : palette) {
            for (BiomeSpan span : column) {
                ids.add(span.biome());
            }
        }
        return Collections.unmodifiableSet(ids);
    }

    /** The distinct columns in the order they first appear, for the file format. */
    List<List<BiomeSpan>> palette() {
        return palette;
    }

    /** For each of the 16 columns, its place in the {@link #palette()}, for the file format. A copy. */
    byte[] indexes() {
        return indexes.clone();
    }

    @Override
    public boolean equals(Object other) {
        // the palette always lists the columns in the order they first appear, so equal columns give equal parts
        return this == other
                || (other instanceof ChunkPaint paint && palette.equals(paint.palette)
                && Arrays.equals(indexes, paint.indexes));
    }

    @Override
    public int hashCode() {
        return 31 * palette.hashCode() + Arrays.hashCode(indexes);
    }

    @Override
    public String toString() {
        return isEmpty() ? "ChunkPaint[empty]" : "ChunkPaint[palette " + palette + ", columns "
                + Arrays.toString(indexes) + "]";
    }
}
