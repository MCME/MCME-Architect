package com.mcmiddleearth.architect.mapLayers.customBiomes;

import com.mcmiddleearth.architect.mapLayers.customBiomes.PaintRegions.Geometry;
import com.mcmiddleearth.architect.mapLayers.customBiomes.PaintRegions.Region;
import com.mcmiddleearth.architect.mapLayers.customBiomes.PaintRegions.Touch;
import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;

// The map draws each region as one ring with no holes, so the ring must be exact: every column inside its own region
// and no other, a hole outside, and the corners the live worlds were measured with.
class PaintRegionsTest {

    /** Chunk paints with {@code biome} in each column, given as x, z pairs in columns. */
    private static Map<Long, ChunkPaint> cells(String biome, int... xz) {
        return cells(new BiomeSpan(biome, 0, 63), xz);
    }

    private static Map<Long, ChunkPaint> cells(BiomeSpan span, int... xz) {
        Map<Long, List<List<BiomeSpan>>> chunks = new HashMap<>();
        for (int i = 0; i < xz.length; i += 2) {
            column(chunks, xz[i], xz[i + 1]).add(span);
        }
        return paints(chunks);
    }

    /** Paints laid over each other, column by column. */
    @SafeVarargs
    private static Map<Long, ChunkPaint> together(Map<Long, ChunkPaint>... layers) {
        Map<Long, List<List<BiomeSpan>>> chunks = new HashMap<>();
        for (Map<Long, ChunkPaint> layer : layers) {
            layer.forEach((key, paint) -> {
                for (int z = 0; z < 4; z++) {
                    for (int x = 0; x < 4; x++) {
                        column(chunks, PaintRecord.keyX(key) * 4 + x, PaintRecord.keyZ(key) * 4 + z)
                                .addAll(paint.column(x, z));
                    }
                }
            });
        }
        return paints(chunks);
    }

    /** The spans of world column (x, z), in the chunk it falls in. */
    private static List<BiomeSpan> column(Map<Long, List<List<BiomeSpan>>> chunks, int x, int z) {
        List<List<BiomeSpan>> sixteen = chunks.computeIfAbsent(
                PaintRecord.chunkKey(Math.floorDiv(x, 4), Math.floorDiv(z, 4)), key -> {
                    List<List<BiomeSpan>> columns = new ArrayList<>();
                    for (int i = 0; i < 16; i++) {
                        columns.add(new ArrayList<>());
                    }
                    return columns;
                });
        return sixteen.get(Math.floorMod(z, 4) * 4 + Math.floorMod(x, 4));
    }

    private static Map<Long, ChunkPaint> paints(Map<Long, List<List<BiomeSpan>>> chunks) {
        Map<Long, ChunkPaint> paints = new HashMap<>();
        chunks.forEach((key, columns) -> paints.put(key, ChunkPaint.of(columns)));
        return paints;
    }

    private static Region only(Geometry geometry) {
        assertEquals(1, geometry.regions().size(), () -> "regions: " + geometry.regions());
        return geometry.regions().get(0);
    }

    /** The one region holding exactly these biomes. */
    private static Region regionOf(Geometry geometry, String... biomes) {
        Set<String> wanted = Set.of(biomes);
        List<Region> found = geometry.regions().stream()
                .filter(region -> new HashSet<>(region.biomes()).equals(wanted)).toList();
        assertEquals(1, found.size(), () -> "regions of " + wanted + ": " + geometry.regions());
        return found.get(0);
    }

    private static List<Double> pt(double x, double z) {
        return List.of(x, z);
    }

    private static Set<List<Double>> points(Region region) {
        double[] x = region.x();
        double[] z = region.z();
        Set<List<Double>> points = new HashSet<>();
        for (int i = 0; i < x.length; i++) {
            points.add(pt(x[i], z[i]));
        }
        return points;
    }

    /** The ring's area in square blocks, by the shoelace formula. */
    private static double area(Region region) {
        double[] x = region.x();
        double[] z = region.z();
        double twice = 0;
        for (int i = 0, j = x.length - 1; i < x.length; j = i++) {
            twice += x[j] * z[i] - x[i] * z[j];
        }
        return Math.abs(twice) / 2;
    }

    /** Whether the point is inside the ring by the even-odd rule, the rule the map fills areas with. */
    private static boolean inside(Region region, double px, double pz) {
        double[] x = region.x();
        double[] z = region.z();
        boolean in = false;
        for (int i = 0, j = x.length - 1; i < x.length; j = i++) {
            if ((z[i] > pz) != (z[j] > pz) && px < x[i] + (pz - z[i]) * (x[j] - x[i]) / (z[j] - z[i])) {
                in = !in;
            }
        }
        return in;
    }

    @Test
    void aSingleColumnIsASquareOfFourBlocks() {
        Region r = only(PaintRegions.of(cells("cbc:a", 0, 0)));

        assertEquals(16.0, area(r));
        assertEquals(4, r.corners());
        assertEquals(Set.of(pt(0, 0), pt(4, 0), pt(4, 4), pt(0, 4)), points(r));
        assertEquals(1, r.columns());
        assertFalse(r.holes());
        assertEquals(List.of("cbc:a"), r.biomes());
    }

    @Test
    void anLShapeHasSixCorners() {
        Region r = only(PaintRegions.of(cells("cbc:a", 0, 0, 1, 0, 0, 1)));

        assertEquals(6, r.corners());
        assertEquals(48.0, area(r));
        assertEquals(6, r.x().length);
    }

    @Test
    void straightRunsKeepOnlyTheirEnds() {
        Region r = only(PaintRegions.of(cells("cbc:a", 0, 0, 1, 0, 2, 0)));

        assertEquals(Set.of(pt(0, 0), pt(12, 0), pt(12, 4), pt(0, 4)), points(r));
        assertEquals(4, r.x().length);
    }

    @Test
    void columnsWithTheSameBiomesAcrossChunksAreOneRegion() {
        // column 3 is the last of chunk 0, column 4 the first of chunk 1
        Region r = only(PaintRegions.of(cells("cbc:a", 3, 0, 4, 0)));

        assertEquals(2, r.columns());
        assertEquals(Set.of(pt(12, 0), pt(20, 0), pt(20, 4), pt(12, 4)), points(r));
    }

    @Test
    void twoColumnsTouchingOnlyAtACornerAreTwoRegions() {
        Geometry geometry = PaintRegions.of(cells("cbc:a", 10, 10, 11, 11));

        assertEquals(2, geometry.regions().size());
        assertEquals(1, geometry.regions().get(0).columns());
        assertEquals(1, geometry.regions().get(1).columns());
    }

    @Test
    void aStackedSpotIsItsOwnRegion() {
        Geometry geometry = PaintRegions.of(together(cells("cbc:a", 0, 0, 1, 0),
                cells(new BiomeSpan("cbc:b", 64, 127), 1, 0)));

        assertEquals(2, geometry.regions().size());
        assertEquals(List.of("cbc:a"), geometry.regions().get(0).biomes());
        assertEquals(List.of("cbc:b", "cbc:a"), geometry.regions().get(1).biomes(), "top first");
    }

    @Test
    void biomesReachingTheSameHeightAreOrderedById() {
        Region r = only(PaintRegions.of(together(cells(new BiomeSpan("cbc:b", 0, 63), 0, 0),
                cells(new BiomeSpan("cbc:a", 40, 63), 0, 0))));

        assertEquals(List.of("cbc:a", "cbc:b"), r.biomes());
    }

    @Test
    void heightsSpanTheBiomeOverTheWholeRegion() {
        Region r = only(PaintRegions.of(together(cells(new BiomeSpan("cbc:a", 0, 63), 0, 0),
                cells(new BiomeSpan("cbc:a", -32, 20), 1, 0),
                cells(new BiomeSpan("cbc:a", 200, 260), 1, 0))));

        assertArrayEquals(new int[]{-32, 260}, r.heights().get("cbc:a"));
        assertEquals(Set.of("cbc:a"), r.heights().keySet());
    }

    @Test
    void aRingAroundAnotherBiomeIsOneRegionWithAHoleJoinedByASeam() {
        Geometry geometry = PaintRegions.of(together(
                cells("cbc:a", 0, 0, 1, 0, 2, 0, 0, 1, 2, 1, 0, 2, 1, 2, 2, 2),
                cells("cbc:b", 1, 1)));
        Region a = regionOf(geometry, "cbc:a");

        assertTrue(a.holes());
        assertEquals(8, a.corners());
        assertEquals(8 * 16.0, area(a));
        assertFalse(inside(a, 6, 6));   // the hole's middle
        assertTrue(inside(a, 2, 2));    // a column of the ring
        assertTrue(inside(regionOf(geometry, "cbc:b"), 6, 6));
        // the outline, then a seam from the middle of the top edge down to the hole, around it and back up
        assertArrayEquals(new double[]{0, 0, 12, 12, 6, 6, 8, 8, 4, 4, 6, 6}, a.x());
        assertArrayEquals(new double[]{0, 12, 12, 0, 0, 4, 4, 8, 8, 4, 4, 0}, a.z());
    }

    @Test
    void twoHolesOneAboveTheOtherAreBothJoined() {
        Map<Long, ChunkPaint> paint = cells("cbc:a", 0, 0, 1, 0, 2, 0, 0, 1, 2, 1, 0, 2, 1, 2, 2, 2, 0, 3, 2, 3,
                0, 4, 1, 4, 2, 4);
        Region a = only(PaintRegions.of(paint));

        assertEquals(13 * 16.0, area(a));
        assertFalse(inside(a, 6, 6));
        assertFalse(inside(a, 6, 14));
        assertTrue(inside(a, 6, 10), "the column between the holes, which the lower seam runs through");
        assertEquals(12, a.corners());
        // the lower hole's seam lands on the upper hole, which was joined first
        assertArrayEquals(new double[]{0, 0, 12, 12, 6, 6, 8, 8, 6, 6, 8, 8, 4, 4, 6, 6, 4, 4, 6, 6}, a.x());
        assertArrayEquals(new double[]{0, 20, 20, 0, 0, 4, 4, 8, 8, 12, 12, 16, 16, 12, 12, 8, 8, 4, 4, 0}, a.z());
    }

    @Test
    void holesSideBySideAreJoinedToTheSameEdge() {
        // a 7 x 5 field with gaps at x 1, 3 and 5 of row 1, and at x 2 of row 3: four seams up to the one top edge,
        // the last one between two of the others
        Set<List<Integer>> gaps = Set.of(List.of(1, 1), List.of(3, 1), List.of(5, 1), List.of(2, 3));
        List<Integer> xz = new ArrayList<>();
        for (int z = 0; z < 5; z++) {
            for (int x = 0; x < 7; x++) {
                if (!gaps.contains(List.of(x, z))) {
                    xz.add(x);
                    xz.add(z);
                }
            }
        }
        Region a = only(PaintRegions.of(cells("cbc:a", xz.stream().mapToInt(Integer::intValue).toArray())));

        assertEquals(31 * 16.0, area(a));
        assertEquals(4 + 4 * 4, a.corners());
        assertEquals(a.corners() + 4 * 4, a.x().length, "four seam points a hole");
        for (List<Integer> gap : gaps) {
            assertFalse(inside(a, gap.get(0) * 4 + 2, gap.get(1) * 4 + 2), "gap " + gap);
            assertFalse(inside(a, gap.get(0) * 4 + 1, gap.get(1) * 4 + 3), "gap " + gap + " off its middle");
        }
        for (int seam : new int[]{1, 2, 3, 5}) {
            assertTrue(inside(a, seam * 4 + 2, 2), "the column a seam runs through at x " + seam);
        }
        // the top edge runs west, cut by each seam in turn, out and back at the same point
        List<Double> top = new ArrayList<>();
        for (int i = 0; i < a.x().length; i++) {
            if (a.z()[i] == 0) {
                top.add(a.x()[i]);
            }
        }
        assertEquals(List.of(0.0, 28.0, 22.0, 22.0, 14.0, 14.0, 10.0, 10.0, 6.0, 6.0), top);
    }

    // Two gaps touching at a corner are one hole, as they look on the map; the hole's ring passes that corner twice.
    @Test
    void gapsTouchingAtACornerAreOneHole() {
        List<Integer> xz = new ArrayList<>();
        for (int z = 0; z < 4; z++) {
            for (int x = 0; x < 4; x++) {
                if (!(x == 1 && z == 1) && !(x == 2 && z == 2)) {
                    xz.add(x);
                    xz.add(z);
                }
            }
        }
        Region a = only(PaintRegions.of(cells("cbc:a", xz.stream().mapToInt(Integer::intValue).toArray())));

        assertTrue(a.holes());
        assertEquals(4 + 8, a.corners());
        assertEquals(a.corners() + 4, a.x().length, "one seam");
        assertEquals(14 * 16.0, area(a));
        assertFalse(inside(a, 6, 6));
        assertFalse(inside(a, 10, 10));
        assertTrue(inside(a, 10, 6));
        assertTrue(inside(a, 6, 10));
    }

    // A gap open to the outside only through a corner is no hole: the outline pinches at that corner and goes round it.
    @Test
    void aBayOpenOnlyAtACornerIsOutside() {
        Region a = only(PaintRegions.of(cells("cbc:a", 0, 0, 1, 0, 2, 0, 0, 1, 2, 1, 0, 2, 1, 2)));

        assertFalse(a.holes());
        assertEquals(10, a.corners());
        assertEquals(10, a.x().length);
        assertEquals(7 * 16.0, area(a));
        assertFalse(inside(a, 6, 6), "the bay");
        assertFalse(inside(a, 10, 10), "the gap at its corner");
        assertTrue(inside(a, 10, 6));
        assertTrue(inside(a, 6, 10));
    }

    /** A column of a measured shape: x, z and the id of its set of biomes. */
    private record Cell(int x, int z, int set) {}

    /** A square of one set, whose second column on its second row holds {@code hole}, or nothing for 0. */
    private static List<Cell> square(int x0, int z0, int size, int set, int hole) {
        List<Cell> cells = new ArrayList<>();
        for (int x = x0; x < x0 + size; x++) {
            for (int z = z0; z < z0 + size; z++) {
                boolean middle = x == x0 + 1 && z == z0 + 1;
                if (!(middle && hole == 0)) {
                    cells.add(new Cell(x, z, middle ? hole : set));
                }
            }
        }
        return cells;
    }

    private static List<Cell> shape(int set, int... xz) {
        List<Cell> cells = new ArrayList<>();
        for (int i = 0; i < xz.length; i += 2) {
            cells.add(new Cell(xz[i], xz[i + 1], set));
        }
        return cells;
    }

    /** Each region as "set columns corners", sorted, as the measurement lists them. */
    private static List<String> summary(List<Cell> cells) {
        Map<Long, List<List<BiomeSpan>>> chunks = new HashMap<>();
        for (Cell cell : cells) {
            column(chunks, cell.x(), cell.z()).add(new BiomeSpan("cbc:" + cell.set(), 0, 63));
        }
        return PaintRegions.of(paints(chunks)).regions().stream()
                .map(region -> region.biomes().get(0).substring(4) + " " + region.columns() + " " + region.corners())
                .sorted().toList();
    }

    // The shapes the corner count of the live worlds' measurement was checked on, with its numbers. Columns 127 and
    // 128 sit on either side of a region file's edge, and so of a chunk's.
    @Test
    void theMeasuredShapesKeepTheirCornerCounts() {
        Map<String, List<Cell>> shapes = new HashMap<>();
        Map<String, List<String>> want = new HashMap<>();
        shapes.put("a bar across a file's east edge", shape(1, 126, 5, 127, 5, 128, 5));
        want.put("a bar across a file's east edge", List.of("1 3 4"));
        shapes.put("two cells touching at a corner", shape(1, 10, 10, 11, 11));
        want.put("two cells touching at a corner", List.of("1 1 4", "1 1 4"));
        shapes.put("an L", shape(1, 0, 0, 1, 0, 0, 1));
        want.put("an L", List.of("1 3 6"));
        shapes.put("a ring", square(20, 20, 3, 1, 0));
        want.put("a ring", List.of("1 8 8"));
        shapes.put("a ring around another biome", square(40, 40, 3, 1, 2));
        want.put("a ring around another biome", List.of("1 8 8", "2 1 4"));
        shapes.put("a square on four files' corner", shape(1, 127, 127, 128, 127, 127, 128, 128, 128));
        want.put("a square on four files' corner", List.of("1 4 4"));
        shapes.put("a diagonal over a painted corner file", shape(1, 127, 127, 128, 128));
        want.put("a diagonal over a painted corner file", List.of("1 1 4", "1 1 4"));
        shapes.put("a diagonal over an empty corner file", shape(1, 127, 128, 128, 127));
        want.put("a diagonal over an empty corner file", List.of("1 1 4", "1 1 4"));
        List<Cell> sideBySide = new ArrayList<>(shape(1, 0, 0));
        sideBySide.addAll(shape(2, 1, 0));
        shapes.put("two biomes side by side", sideBySide);
        want.put("two biomes side by side", List.of("1 1 4", "2 1 4"));
        shapes.put("negative coordinates across an edge", shape(1, -1, 0, 0, 0));
        want.put("negative coordinates across an edge", List.of("1 2 4"));
        shapes.put("a ring over four files", square(127, 127, 3, 1, 0));
        want.put("a ring over four files", List.of("1 8 8"));
        shapes.put("a U with a notch on a file edge", shape(1, 126, 0, 126, 1, 127, 1, 128, 1, 128, 0));
        want.put("a U with a notch on a file edge", List.of("1 5 8"));

        assertEquals(12, shapes.size());
        shapes.forEach((name, cells) -> assertEquals(want.get(name), summary(cells), name));
    }

    @Test
    void firstColumnIsTheNorthWestMostColumn() {
        Region r = only(PaintRegions.of(cells("cbc:a", 1, 0, 0, 1, 1, 1)));

        assertEquals(1, r.firstX());
        assertEquals(0, r.firstZ());
        assertEquals(4.0, r.x()[0], "the ring starts at that column's north-west corner");
        assertEquals(0.0, r.z()[0]);
    }

    @Test
    void regionsAreListedFromNorthToSouthThenWestToEast() {
        Geometry geometry = PaintRegions.of(cells("cbc:a", 9, 5, 2, 7, 4, 5, -3, 6));

        assertEquals(List.of(List.of(4, 5), List.of(9, 5), List.of(-3, 6), List.of(2, 7)), geometry.regions().stream()
                .map(region -> List.of(region.firstX(), region.firstZ())).toList());
    }

    @Test
    void aBiomeInThreeRegionsIsInThreePlaces() {
        Geometry geometry = PaintRegions.of(together(cells("cbc:a", 0, 0, 10, 10, 20, 20),
                cells(new BiomeSpan("cbc:b", 100, 120), 20, 20)));

        assertEquals(3, geometry.places().get("cbc:a"), "a stacked region counts for each of its biomes");
        assertEquals(1, geometry.places().get("cbc:b"));
    }

    @Test
    void chunksCountEveryChunkHoldingTheBiome() {
        // columns 0 and 1 are in chunk (0, 0), column 4 in chunk (1, 0)
        Geometry geometry = PaintRegions.of(cells("cbc:a", 0, 0, 1, 0, 4, 0));

        assertEquals(2, geometry.chunks().get("cbc:a"));
    }

    @Test
    void stackedAndSideBySideBiomesTouch() {
        Geometry geometry = PaintRegions.of(together(
                cells("cbc:a", 0, 0),
                cells(new BiomeSpan("cbc:b", 64, 127), 0, 0),
                cells("cbc:c", 1, 0),
                cells("cbc:d", 10, 10),
                cells("cbc:e", 11, 11)));

        assertEquals(Set.of(new Touch("cbc:a", "cbc:b"), new Touch("cbc:a", "cbc:c"), new Touch("cbc:b", "cbc:c")),
                geometry.touching(), "columns touching only at a corner do not touch");
    }

    @Test
    void aTouchNamesItsBiomesInOrder() {
        assertThrows(IllegalArgumentException.class, () -> new Touch("cbc:b", "cbc:a"));
        assertThrows(IllegalArgumentException.class, () -> new Touch("cbc:a", "cbc:a"));
    }

    @Test
    void noPaintIsNoRegion() {
        Geometry geometry = PaintRegions.of(Map.of(PaintRecord.chunkKey(0, 0), ChunkPaint.EMPTY));

        assertTrue(geometry.regions().isEmpty());
        assertTrue(geometry.touching().isEmpty());
        assertTrue(geometry.places().isEmpty());
        assertTrue(geometry.chunks().isEmpty());
    }

    // Random patches of three kinds of column and gaps, placed across chunk edges, are checked against what the
    // columns alone say: each region's area, corners and holes (the gaps it closes in, gaps touching at a corner
    // counted as one), the column sides its outline runs along, each once, and which ring each point of every column
    // falls in.
    @Test
    void randomShapesPutEveryColumnInItsOwnRegionOnly() {
        Random random = new Random(20261028L);
        List<List<String>> kinds = List.of(List.of(), List.of("cbc:a"), List.of("cbc:b"), List.of("cbc:a", "cbc:b"));
        for (int round = 0; round < 400; round++) {
            // every other round, a field of one kind with scattered gaps: many holes, and seams sharing edges
            boolean field = round % 2 == 1;
            int width = 2 + random.nextInt(field ? 13 : 9);
            int depth = 2 + random.nextInt(field ? 13 : 9);
            int originX = random.nextInt(17) - 8;
            int originZ = random.nextInt(17) - 8;
            double gaps = field ? 0.05 + 0.15 * random.nextDouble() : 0.1 + 0.4 * random.nextDouble();
            int[][] kind = new int[width][depth];
            Map<Long, List<List<BiomeSpan>>> chunks = new HashMap<>();
            for (int x = 0; x < width; x++) {
                for (int z = 0; z < depth; z++) {
                    double pick = random.nextDouble();
                    kind[x][z] = random.nextDouble() < gaps ? 0
                            : field ? (pick < 0.95 ? 1 : 2) : pick < 0.6 ? 1 : pick < 0.85 ? 2 : 3;
                    for (String biome : kinds.get(kind[x][z])) {
                        column(chunks, originX + x, originZ + z).add(new BiomeSpan(biome, 0, 63));
                    }
                }
            }
            String where = "round " + round;
            Geometry geometry = PaintRegions.of(paints(chunks));

            int[][] label = labels(kind);
            int labels = 1 + maxOf(label);
            assertEquals(labels, geometry.regions().size(), where);
            Region[] regionOfLabel = new Region[labels];
            for (Region region : geometry.regions()) {
                int x = region.firstX() - originX;
                int z = region.firstZ() - originZ;
                regionOfLabel[label[x][z]] = region;
            }
            for (int l = 0; l < labels; l++) {
                Region region = regionOfLabel[l];
                Supplier<String> which = () -> where + ", " + region;
                int columns = count(label, l);
                int holes = holes(label, l);
                assertEquals(columns, region.columns(), which);
                assertEquals(16.0 * columns, area(region), which);
                assertEquals(corners(label, l), region.corners(), which);
                assertEquals(holes > 0, region.holes(), which);
                assertEquals(region.corners() + 4 * holes, region.x().length, which);
                assertEquals(4.0 * region.firstX(), region.x()[0], which);
                assertEquals(4.0 * region.firstZ(), region.z()[0], which);
                assertEquals(boundary(label, l, originX, originZ), outline(region, label, l, originX, originZ, which),
                        which);
                double[] x = region.x();
                double[] z = region.z();
                for (int i = 0, j = x.length - 1; i < x.length; j = i++) {
                    int point = i;
                    assertTrue((x[i] == x[j]) != (z[i] == z[j]),
                            () -> which.get() + ": a step along one axis at point " + point);
                }
            }
            for (int x = -1; x <= width; x++) {
                for (int z = -1; z <= depth; z++) {
                    boolean painted = x >= 0 && z >= 0 && x < width && z < depth && kind[x][z] != 0;
                    int blockX = 4 * (originX + x);
                    int blockZ = 4 * (originZ + z);
                    // the middle, where a seam may run, and two points off every line
                    double[][] samples = {{blockX + 2, blockZ + 2}, {blockX + 1, blockZ + 3}, {blockX + 3, blockZ + 1}};
                    for (double[] point : samples) {
                        for (int l = 0; l < labels; l++) {
                            Region region = regionOfLabel[l];
                            int columnX = originX + x;
                            int columnZ = originZ + z;
                            assertEquals(painted && label[x][z] == l, inside(region, point[0], point[1]),
                                    () -> where + ": column " + columnX + ", " + columnZ + " at " + point[0] + ", "
                                            + point[1] + " in " + region);
                        }
                    }
                }
            }
        }
    }

    /** Labels the grid's 4-neighbour runs of one kind 0, 1 and so on, in the order they are met; gaps are -1. */
    private static int[][] labels(int[][] kind) {
        int width = kind.length;
        int depth = kind[0].length;
        int[][] label = new int[width][depth];
        for (int[] row : label) {
            Arrays.fill(row, -1);
        }
        int next = 0;
        for (int x = 0; x < width; x++) {
            for (int z = 0; z < depth; z++) {
                if (kind[x][z] == 0 || label[x][z] >= 0) {
                    continue;
                }
                ArrayDeque<int[]> queue = new ArrayDeque<>();
                label[x][z] = next;
                queue.add(new int[]{x, z});
                while (!queue.isEmpty()) {
                    int[] at = queue.poll();
                    for (int[] step : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
                        int nx = at[0] + step[0];
                        int nz = at[1] + step[1];
                        if (nx >= 0 && nz >= 0 && nx < width && nz < depth && label[nx][nz] < 0
                                && kind[nx][nz] == kind[x][z]) {
                            label[nx][nz] = next;
                            queue.add(new int[]{nx, nz});
                        }
                    }
                }
                next++;
            }
        }
        return label;
    }

    private static int maxOf(int[][] grid) {
        int max = -1;
        for (int[] row : grid) {
            for (int value : row) {
                max = Math.max(max, value);
            }
        }
        return max;
    }

    private static int count(int[][] label, int l) {
        int count = 0;
        for (int[] row : label) {
            for (int value : row) {
                count += value == l ? 1 : 0;
            }
        }
        return count;
    }

    private static int at(int[][] label, int x, int z) {
        return x < 0 || z < 0 || x >= label.length || z >= label[0].length ? -1 : label[x][z];
    }

    /** The measurement's rule: at each grid point, a region on 1 or 3 of the 4 columns there turns once, on 2
     * diagonal columns twice. */
    private static int corners(int[][] label, int l) {
        int corners = 0;
        for (int vx = 0; vx <= label.length; vx++) {
            for (int vz = 0; vz <= label[0].length; vz++) {
                boolean nw = at(label, vx - 1, vz - 1) == l;
                boolean ne = at(label, vx, vz - 1) == l;
                boolean sw = at(label, vx - 1, vz) == l;
                boolean se = at(label, vx, vz) == l;
                int hits = (nw ? 1 : 0) + (ne ? 1 : 0) + (sw ? 1 : 0) + (se ? 1 : 0);
                if (hits == 1 || hits == 3) {
                    corners++;
                } else if (hits == 2 && nw == se) {
                    corners += 2;
                }
            }
        }
        return corners;
    }

    /**
     * Each side of the region's columns that faces a column outside it, pointed with the region on its left, in steps
     * of 2 blocks: the steps the outline must take, once each.
     */
    private static Map<List<Integer>, Integer> boundary(int[][] label, int l, int originX, int originZ) {
        Map<List<Integer>, Integer> steps = new HashMap<>();
        for (int x = 0; x < label.length; x++) {
            for (int z = 0; z < label[0].length; z++) {
                if (label[x][z] != l) {
                    continue;
                }
                int west = 4 * (originX + x);
                int north = 4 * (originZ + z);
                if (at(label, x, z - 1) != l) {
                    halves(steps, west + 4, north, west, north);
                }
                if (at(label, x - 1, z) != l) {
                    halves(steps, west, north, west, north + 4);
                }
                if (at(label, x, z + 1) != l) {
                    halves(steps, west, north + 4, west + 4, north + 4);
                }
                if (at(label, x + 1, z) != l) {
                    halves(steps, west + 4, north + 4, west + 4, north);
                }
            }
        }
        return steps;
    }

    /**
     * The ring's steps along the column grid, in steps of 2 blocks. The seams, the steps along the middle of a column,
     * are checked here instead: each must come back the way it went, through its own region's columns only.
     */
    private static Map<List<Integer>, Integer> outline(Region region, int[][] label, int l, int originX, int originZ,
                                                       Supplier<String> which) {
        double[] x = region.x();
        double[] z = region.z();
        Map<List<Integer>, Integer> steps = new HashMap<>();
        Map<List<Integer>, Integer> seams = new HashMap<>();
        for (int i = 0; i < x.length; i++) {
            int j = (i + 1) % x.length;
            int x1 = (int) x[i];
            int z1 = (int) z[i];
            int x2 = (int) x[j];
            int z2 = (int) z[j];
            if (x1 == x2 && Math.floorMod(x1, 4) == 2) {
                seams.merge(List.of(x1, z1, x2, z2), 1, Integer::sum);
                for (int k = Math.min(z1, z2); k < Math.max(z1, z2); k += 4) {
                    assertEquals(l, at(label, Math.floorDiv(x1, 4) - originX, Math.floorDiv(k, 4) - originZ), which);
                }
            } else {
                halves(steps, x1, z1, x2, z2);
            }
        }
        seams.forEach((seam, count) -> assertEquals(count,
                seams.get(List.of(seam.get(2), seam.get(3), seam.get(0), seam.get(1))), which));
        return steps;
    }

    /** Adds the step from (x1, z1) to (x2, z2), along one axis, as steps of 2 blocks. */
    private static void halves(Map<List<Integer>, Integer> steps, int x1, int z1, int x2, int z2) {
        int dx = 2 * Integer.signum(x2 - x1);
        int dz = 2 * Integer.signum(z2 - z1);
        for (int x = x1, z = z1; x != x2 || z != z2; x += dx, z += dz) {
            steps.merge(List.of(x, z, x + dx, z + dz), 1, Integer::sum);
        }
    }

    /** The runs of other columns that the region closes in, joined across corners as well as sides. */
    private static int holes(int[][] label, int l) {
        // a margin of one column all round is outside, and joins everything that reaches the edge
        int width = label.length + 2;
        int depth = label[0].length + 2;
        boolean[][] seen = new boolean[width][depth];
        int runs = 0;
        for (int x = 0; x < width; x++) {
            for (int z = 0; z < depth; z++) {
                if (seen[x][z] || at(label, x - 1, z - 1) == l) {
                    continue;
                }
                runs++;
                ArrayDeque<int[]> queue = new ArrayDeque<>();
                seen[x][z] = true;
                queue.add(new int[]{x, z});
                while (!queue.isEmpty()) {
                    int[] here = queue.poll();
                    for (int dx = -1; dx <= 1; dx++) {
                        for (int dz = -1; dz <= 1; dz++) {
                            int nx = here[0] + dx;
                            int nz = here[1] + dz;
                            if (nx >= 0 && nz >= 0 && nx < width && nz < depth && !seen[nx][nz]
                                    && at(label, nx - 1, nz - 1) != l) {
                                seen[nx][nz] = true;
                                queue.add(new int[]{nx, nz});
                            }
                        }
                    }
                }
            }
        }
        return runs - 1;
    }
}
