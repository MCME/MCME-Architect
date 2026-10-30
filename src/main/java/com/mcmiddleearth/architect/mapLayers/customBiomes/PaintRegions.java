package com.mcmiddleearth.architect.mapLayers.customBiomes;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * A world's custom biomes as map regions. A region is a 4-neighbour connected run of columns that hold exactly the
 * same biomes, outlined on the 4-block biome grid as one ring of points, which a map area can draw. Regions never
 * overlap.
 * <p>
 * A plain computation for a background thread. It takes time in proportion to the painted columns.
 */
public final class PaintRegions {

    private static final Comparator<Touch> BY_NAMES = Comparator.comparing(Touch::a).thenComparing(Touch::b);

    private PaintRegions() {
    }

    /**
     * One region. Its ring has no holes, since a map area cannot: each hole is joined to the outline by a seam, a cut
     * out to the hole and back along one line, so the map still shows through the hole and a click in it reaches the
     * region there. The lists, map and arrays are copied in and out, and compared by value.
     *
     * @param biomes  top first: by the highest block each reaches in the region, then by id
     * @param heights each biome's lowest and highest block in the region, as {@code {minY, maxY}}, top first
     * @param columns the number of columns
     * @param corners the corners of the outline, holes included, before seams
     * @param holes   whether the outline has holes, and so seams
     * @param firstX  the x of the region's north-west-most column, the one with the least z and then the least x, in
     *                columns; with {@code firstZ} it names the region
     * @param firstZ  that column's z
     * @param x       the ring's x, in blocks, from the north-west corner of that column
     * @param z       the ring's z
     */
    public record Region(List<String> biomes, Map<String, int[]> heights, int columns, int corners, boolean holes,
                         int firstX, int firstZ, double[] x, double[] z) {

        public Region {
            biomes = List.copyOf(biomes);
            heights = copy(heights);
            if (x.length != z.length) {
                throw new IllegalArgumentException("A ring needs as many x as z, not " + x.length + " and "
                        + z.length);
            }
            x = x.clone();
            z = z.clone();
        }

        private static Map<String, int[]> copy(Map<String, int[]> heights) {
            Map<String, int[]> copy = new LinkedHashMap<>();
            heights.forEach((biome, range) -> copy.put(Objects.requireNonNull(biome, "biome"), range.clone()));
            return Collections.unmodifiableMap(copy);
        }

        @Override
        public Map<String, int[]> heights() {
            return copy(heights);
        }

        @Override
        public double[] x() {
            return x.clone();
        }

        @Override
        public double[] z() {
            return z.clone();
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof Region region && columns == region.columns && corners == region.corners
                    && holes == region.holes && firstX == region.firstX && firstZ == region.firstZ
                    && biomes.equals(region.biomes) && heights.size() == region.heights.size()
                    && heights.entrySet().stream().allMatch(
                            entry -> Arrays.equals(entry.getValue(), region.heights.get(entry.getKey())))
                    && Arrays.equals(x, region.x) && Arrays.equals(z, region.z);
        }

        @Override
        public int hashCode() {
            // without the heights: equal regions still hash alike
            return Objects.hash(biomes, columns, corners, holes, firstX, firstZ, Arrays.hashCode(x),
                    Arrays.hashCode(z));
        }

        @Override
        public String toString() {
            StringBuilder text = new StringBuilder("Region[").append(biomes).append(" at column ").append(firstX)
                    .append(", ").append(firstZ).append(": ").append(columns).append(" columns, ").append(corners)
                    .append(" corners").append(holes ? " with holes" : "").append(", Y");
            heights.forEach((biome, range) -> text.append(' ').append(range[0]).append("..").append(range[1]));
            return text.append(", x ").append(Arrays.toString(x)).append(", z ").append(Arrays.toString(z))
                    .append(']').toString();
        }
    }

    /** Two biomes in one column, or in columns side by side, named in id order. */
    public record Touch(String a, String b) {

        public Touch {
            if (a.compareTo(b) >= 0) {
                throw new IllegalArgumentException("A touch names two biomes in id order, not " + a + " and " + b);
            }
        }
    }

    /**
     * Every region of a world, with what the map needs besides: which biomes touch, for their colours, and each
     * biome's places and chunks, for its window.
     *
     * @param regions  north to south, then west to east, by their first column
     * @param touching each pair of biomes in one column or in 4-neighbour columns
     * @param places   for each biome, the number of regions it is in
     * @param chunks   for each biome, the number of chunks that hold it
     */
    public record Geometry(List<Region> regions, Set<Touch> touching, Map<String, Integer> places,
                           Map<String, Integer> chunks) {

        public Geometry {
            regions = List.copyOf(regions);
            Set<Touch> sorted = new TreeSet<>(BY_NAMES);
            sorted.addAll(touching);
            touching = Collections.unmodifiableSet(sorted);
            places = Collections.unmodifiableMap(new TreeMap<>(places));
            chunks = Collections.unmodifiableMap(new TreeMap<>(chunks));
        }
    }

    /**
     * Groups the painted columns of the chunks into regions, and outlines each one.
     *
     * @param chunks paint by {@link PaintRecord#chunkKey chunk key}, as {@link PaintRecord#copyOfChunks} gives it
     */
    public static Geometry of(Map<Long, ChunkPaint> chunks) {
        return new Grouping(chunks).geometry();
    }

    /**
     * A ring as traced: its north-west-most corner, its number of corners, and its area in columns, positive for an
     * outer ring and negative for a hole. A hole's north-west corner starts its westernmost top edge.
     */
    private record Ring(int northWest, int corners, long area) {
    }

    /*
     * How a region is outlined, in columns: column (x, z) covers [x, x + 1] by [z, z + 1], x east, z south.
     *
     * Each side of a column that faces a column outside the region is an edge, pointed so that the region is on its
     * left: a north side runs west, a west side south, a south side east and an east side north. Joined head to tail,
     * the edges close into rings. The outer ring runs counterclockwise on the map and each hole's the other way, so
     * a hole stays a hole whichever fill rule draws it. Where two of the region's columns touch only at a corner, two
     * edges leave that point. Taking the one that turns left keeps to the column the edge came along, so the outline
     * pinches there and never crosses itself: the two columns stay apart, as regions join only through sides, and
     * gaps that touch at a corner make one hole. Dropping the points along straight runs leaves the corners the
     * measurement of the live worlds counted. The outer ring encloses the holes, so it is the one with the largest
     * area.
     *
     * A map area is one ring, so each hole is joined to the outer ring by a seam: a cut out to the hole and back
     * along the same line. The two passes add no area, and a line from any point beside the seam crosses both, so
     * the seam changes no point's inside or outside. A seam leaves the middle of the hole's north-west top edge and
     * runs north through the region's columns to the first edge it meets, so it crosses nothing. Holes are joined
     * from north to south, so that edge, on the outer ring or on a hole further north, is part of the ring by then.
     * In blocks the seams run through the middles of columns, and never along an edge.
     */
    private static final class Grouping {

        /** A column's sides, in the order a lone column's outline takes them, so a left turn is the next side. */
        private static final int NORTH = 0;
        private static final int WEST = 1;
        private static final int SOUTH = 2;
        private static final int EAST = 3;
        /** For touching, each two neighbouring columns are compared once: across these sides of the first. */
        private static final int[] ONWARD = {SOUTH, EAST};
        /** The step to the column across each side. */
        private static final int[] ACROSS_X = {0, -1, 0, 1};
        private static final int[] ACROSS_Z = {-1, 0, 1, 0};
        /** Where each side's edge ends, from the column's north-west corner. */
        private static final int[] END_X = {0, 0, 1, 1};
        private static final int[] END_Z = {0, 1, 1, 0};
        /** Blocks along a column's side, and columns along a chunk's. */
        private static final int BLOCKS = 4;
        private static final int CHUNK_SIDE = 4;

        private final Map<String, Integer> chunkCounts = new TreeMap<>();
        /** The distinct sets of biome ids, each sorted. */
        private final List<List<String>> sets = new ArrayList<>();
        private final Map<Long, Integer> index;
        private final int[] columnX;
        private final int[] columnZ;
        private final int[] columnSet;
        private final List<List<BiomeSpan>> columnSpans = new ArrayList<>();
        private int columns;
        /** For each column and side, the column across it, or -1. */
        private int[] across;
        private int[] regionOf;

        /** The rings' points, in blocks, and the point after each. */
        private int[] nodeX = new int[256];
        private int[] nodeZ = new int[256];
        private int[] next = new int[256];
        private int nodes;

        Grouping(Map<Long, ChunkPaint> chunks) {
            int room = chunks.size() * ChunkPaint.COLUMNS;
            columnX = new int[room];
            columnZ = new int[room];
            columnSet = new int[room];
            index = HashMap.newHashMap(room);
            Map<List<BiomeSpan>, Integer> setOfSpans = new HashMap<>();
            Map<List<String>, Integer> setIds = new HashMap<>();
            for (Map.Entry<Long, ChunkPaint> entry : chunks.entrySet()) {
                ChunkPaint paint = entry.getValue();
                for (String biome : paint.biomes()) {
                    chunkCounts.merge(biome, 1, Integer::sum);
                }
                int chunkX = PaintRecord.keyX(entry.getKey());
                int chunkZ = PaintRecord.keyZ(entry.getKey());
                for (int z = 0; z < CHUNK_SIDE; z++) {
                    for (int x = 0; x < CHUNK_SIDE; x++) {
                        List<BiomeSpan> spans = paint.column(x, z);
                        if (spans.isEmpty()) {
                            continue;
                        }
                        Integer set = setOfSpans.get(spans);
                        if (set == null) {
                            List<String> ids = spans.stream().map(BiomeSpan::biome).distinct().sorted().toList();
                            set = setIds.computeIfAbsent(ids, key -> {
                                sets.add(key);
                                return sets.size() - 1;
                            });
                            setOfSpans.put(spans, set);
                        }
                        int column = columns++;
                        columnX[column] = chunkX * CHUNK_SIDE + x;
                        columnZ[column] = chunkZ * CHUNK_SIDE + z;
                        columnSet[column] = set;
                        columnSpans.add(spans);
                        index.put(key(columnX[column], columnZ[column]), column);
                    }
                }
            }
        }

        Geometry geometry() {
            across = new int[columns * 4];
            for (int column = 0; column < columns; column++) {
                for (int side = 0; side < 4; side++) {
                    across[column * 4 + side] = index.getOrDefault(
                            key(columnX[column] + ACROSS_X[side], columnZ[column] + ACROSS_Z[side]), -1);
                }
            }
            List<Region> regions = regions();
            Map<String, Integer> places = new TreeMap<>();
            for (Region region : regions) {
                for (String biome : region.biomes()) {
                    places.merge(biome, 1, Integer::sum);
                }
            }
            return new Geometry(regions, touching(), places, chunkCounts);
        }

        private static long key(int x, int z) {
            return (x & 0xFFFFFFFFL) | ((long) z << 32);
        }

        private boolean inRegion(int column, int region) {
            return column >= 0 && regionOf[column] == region;
        }

        /** Floods each region from a column not yet in one, then outlines it. */
        private List<Region> regions() {
            regionOf = new int[columns];
            Arrays.fill(regionOf, -1);
            int[] order = new int[columns];
            byte[] traced = new byte[columns];
            List<Region> regions = new ArrayList<>();
            int filled = 0;
            for (int seed = 0; seed < columns; seed++) {
                if (regionOf[seed] >= 0) {
                    continue;
                }
                int region = regions.size();
                int start = filled;
                regionOf[seed] = region;
                order[filled++] = seed;
                int first = seed;
                for (int head = start; head < filled; head++) {
                    int column = order[head];
                    if (columnZ[column] < columnZ[first]
                            || (columnZ[column] == columnZ[first] && columnX[column] < columnX[first])) {
                        first = column;
                    }
                    for (int side = 0; side < 4; side++) {
                        int neighbour = across[column * 4 + side];
                        if (neighbour >= 0 && regionOf[neighbour] < 0
                                && columnSet[neighbour] == columnSet[column]) {
                            regionOf[neighbour] = region;
                            order[filled++] = neighbour;
                        }
                    }
                }
                regions.add(build(region, order, start, filled, first, traced));
            }
            regions.sort(Comparator.comparingInt(Region::firstZ).thenComparingInt(Region::firstX));
            return regions;
        }

        /** The region of columns {@code order[start]} to {@code order[end - 1]}: its rings, seams and heights. */
        private Region build(int region, int[] order, int start, int end, int first, byte[] traced) {
            List<Ring> rings = new ArrayList<>();
            for (int i = start; i < end; i++) {
                int column = order[i];
                for (int side = 0; side < 4; side++) {
                    if ((traced[column] & (1 << side)) == 0 && !inRegion(across[column * 4 + side], region)) {
                        rings.add(trace(region, column, side, traced));
                    }
                }
            }
            Ring outer = rings.get(0);
            int corners = 0;
            for (Ring ring : rings) {
                corners += ring.corners();
                if (Math.abs(ring.area()) > Math.abs(outer.area())) {
                    outer = ring;
                }
            }
            if (rings.size() > 1) {
                join(region, rings, outer);
            }
            double[] x = new double[corners + 4 * (rings.size() - 1)];
            double[] z = new double[x.length];
            int node = outer.northWest();
            for (int i = 0; i < x.length; i++) {
                x[i] = nodeX[node];
                z[i] = nodeZ[node];
                node = next[node];
            }
            if (node != outer.northWest()) {
                throw new IllegalStateException("The ring of the region at column " + columnX[first] + ", "
                        + columnZ[first] + " does not close after its " + x.length + " points");
            }

            Map<String, int[]> heights = new HashMap<>();
            for (int i = start; i < end; i++) {
                for (BiomeSpan span : columnSpans.get(order[i])) {
                    int[] range = heights.computeIfAbsent(span.biome(),
                            biome -> new int[]{Integer.MAX_VALUE, Integer.MIN_VALUE});
                    range[0] = Math.min(range[0], span.minY());
                    range[1] = Math.max(range[1], span.maxY());
                }
            }
            List<String> biomes = new ArrayList<>(heights.keySet());
            biomes.sort(Comparator.comparingInt((String biome) -> heights.get(biome)[1]).reversed()
                    .thenComparing(Comparator.naturalOrder()));
            Map<String, int[]> topFirst = new LinkedHashMap<>();
            for (String biome : biomes) {
                topFirst.put(biome, heights.get(biome));
            }
            return new Region(biomes, topFirst, end - start, corners, rings.size() > 1, columnX[first],
                    columnZ[first], x, z);
        }

        /**
         * Follows the edges from the given side of a column until they close, adding a point at each turn, and
         * marks each side it passes as traced.
         */
        private Ring trace(int region, int startColumn, int startSide, byte[] traced) {
            int first = nodes;
            int column = startColumn;
            int side = startSide;
            do {
                traced[column] |= (byte) (1 << side);
                // the columns past the edge's end: on the region's side, and on the other
                int ahead = across[column * 4 + ((side + 1) & 3)];
                if (!inRegion(ahead, region)) {
                    corner(column, side);
                    side = (side + 1) & 3;
                } else {
                    int diagonal = across[ahead * 4 + side];
                    if (!inRegion(diagonal, region)) {
                        column = ahead;
                    } else {
                        corner(column, side);
                        column = diagonal;
                        side = (side + 3) & 3;
                    }
                }
            } while (column != startColumn || side != startSide);

            int northWest = first;
            long area = 0;
            for (int node = first; node < nodes; node++) {
                int after = node + 1 < nodes ? node + 1 : first;
                next[node] = after;
                if (nodeZ[node] < nodeZ[northWest] || (nodeZ[node] == nodeZ[northWest]
                        && nodeX[node] < nodeX[northWest])) {
                    northWest = node;
                }
                // in columns, from the first point, so a far-off region adds no size to the sums
                if (nodeZ[after] == nodeZ[node]) {
                    area += (long) ((nodeX[after] - nodeX[node]) / BLOCKS) * ((nodeZ[node] - nodeZ[first]) / BLOCKS);
                }
            }
            return new Ring(northWest, nodes - first, area);
        }

        /** A point where the edge along this side of the column ends. */
        private void corner(int column, int side) {
            node((columnX[column] + END_X[side]) * BLOCKS, (columnZ[column] + END_Z[side]) * BLOCKS);
        }

        private int node(int x, int z) {
            if (nodes == next.length) {
                int size = nodes * 2;
                nodeX = Arrays.copyOf(nodeX, size);
                nodeZ = Arrays.copyOf(nodeZ, size);
                next = Arrays.copyOf(next, size);
            }
            nodeX[nodes] = x;
            nodeZ[nodes] = z;
            return nodes++;
        }

        /** Joins each hole to the outer ring by a seam, from north to south. */
        private void join(int region, List<Ring> rings, Ring outer) {
            List<Ring> holes = new ArrayList<>(rings);
            holes.remove(outer);
            holes.sort(Comparator.comparingInt((Ring ring) -> nodeZ[ring.northWest()])
                    .thenComparingInt(ring -> nodeX[ring.northWest()]));
            // A seam always meets the north side of a column, an edge running west. These are the rings' edges
            // running west, by row and then by west end; a seam that meets an edge cuts it, and the cuts on each
            // edge are kept by x, with the point that goes on west from each.
            TreeMap<Long, Integer> westward = new TreeMap<>();
            for (Ring ring : rings) {
                int node = ring.northWest();
                do {
                    int after = next[node];
                    if (nodeZ[after] == nodeZ[node] && nodeX[after] < nodeX[node]) {
                        westward.put(rowKey(nodeZ[after], nodeX[after]), node);
                    }
                    node = after;
                } while (node != ring.northWest());
            }
            Map<Integer, TreeMap<Integer, Integer>> cuts = new HashMap<>();
            for (Ring hole : holes) {
                int corner = hole.northWest();
                int seamX = nodeX[corner] + BLOCKS / 2;
                int holeZ = nodeZ[corner];
                // north through the region from the column above the hole's top edge, to the first edge
                Integer above = index.get(key(nodeX[corner] / BLOCKS, holeZ / BLOCKS - 1));
                if (above == null || !inRegion(above, region)) {
                    throw new IllegalStateException("The hole at " + nodeX[corner] + ", " + holeZ
                            + " has no column of its region above its top edge");
                }
                int column = above;
                while (inRegion(across[column * 4 + NORTH], region)) {
                    column = across[column * 4 + NORTH];
                }
                int hitZ = columnZ[column] * BLOCKS;
                Map.Entry<Long, Integer> edge = westward.floorEntry(rowKey(hitZ, seamX));
                if (edge == null || nodeZ[edge.getValue()] != hitZ || nodeX[edge.getValue()] <= seamX) {
                    throw new IllegalStateException("No edge of the region at x " + seamX + ", z " + hitZ
                            + " for the seam of the hole at " + nodeX[corner] + ", " + holeZ);
                }
                TreeMap<Integer, Integer> onEdge = cuts.computeIfAbsent(edge.getValue(), start -> new TreeMap<>());
                Map.Entry<Integer, Integer> cut = onEdge.higherEntry(seamX);
                int from = cut == null ? edge.getValue() : cut.getValue();
                int to = next[from];
                int down = node(seamX, hitZ);
                int in = node(seamX, holeZ);
                int out = node(seamX, holeZ);
                int up = node(seamX, hitZ);
                next[from] = down;
                next[down] = in;
                next[in] = next[corner];
                next[corner] = out;
                next[out] = up;
                next[up] = to;
                onEdge.put(seamX, up);
            }
        }

        /** Orders points by z, then x. */
        private static long rowKey(int z, int x) {
            return ((long) z << 32) + ((long) x - Integer.MIN_VALUE);
        }

        /** Each set's own pairs, and the pairs across each two sets that meet side by side. */
        private Set<Touch> touching() {
            Set<Touch> touching = new TreeSet<>(BY_NAMES);
            for (List<String> set : sets) {
                for (int i = 0; i < set.size(); i++) {
                    for (int j = i + 1; j < set.size(); j++) {
                        touching.add(new Touch(set.get(i), set.get(j)));
                    }
                }
            }
            Set<Long> met = new HashSet<>();
            for (int column = 0; column < columns; column++) {
                for (int side : ONWARD) {
                    int neighbour = across[column * 4 + side];
                    if (neighbour < 0 || columnSet[neighbour] == columnSet[column]) {
                        continue;
                    }
                    int low = Math.min(columnSet[column], columnSet[neighbour]);
                    int high = Math.max(columnSet[column], columnSet[neighbour]);
                    if (met.add(((long) low << 32) | high)) {
                        for (String one : sets.get(low)) {
                            for (String two : sets.get(high)) {
                                int order = one.compareTo(two);
                                if (order != 0) {
                                    touching.add(order < 0 ? new Touch(one, two) : new Touch(two, one));
                                }
                            }
                        }
                    }
                }
            }
            return touching;
        }
    }
}
