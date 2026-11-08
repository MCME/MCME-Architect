package com.mcmiddleearth.architect.slabSmooth.core;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mcmiddleearth.architect.slabSmooth.core.Shapes.Piece;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A material table: which blocks are terrain, and which state is each piece of it. Same JSON as the Python
 * prototype's tables (see SlabSmooth/slabsmooth/materials.py).
 */
public final class Table {

    public static final int AIR = 0, DECOR = 1, TERRAIN = 2, PIECE = 3, OTHER = 4;

    /** How the smoother sees a block state. material: index into materials (or -1). piece: for partial blocks. */
    public record Kind(int category, int material, Piece piece) {
    }

    private static final Kind AIR_KIND = new Kind(AIR, -1, null);
    private static final Kind DECOR_KIND = new Kind(DECOR, -1, null);
    private static final Kind OTHER_KIND = new Kind(OTHER, -1, null);
    private static final List<String> DEFAULT_AIR = List.of("minecraft:air", "minecraft:cave_air", "minecraft:void_air");

    public final String name;
    public final List<Material> materials = new ArrayList<>();
    private final Set<String> air = new HashSet<>();
    private final List<BlockStates.Parsed> decor = new ArrayList<>();
    private final Map<String, Kind> exact = new HashMap<>();
    private final Map<String, Integer> stairBlocks = new HashMap<>();
    private final Map<String, Kind> cache = new ConcurrentHashMap<>(); // used by brushes and background jobs at once

    /** The terrain materials only. */
    public Table(JsonObject spec) {
        this(spec, false);
    }

    /** allBlocks: also the materials marked {@code "terrain": false} (blocks of other categories with pieces). */
    public Table(JsonObject spec, boolean allBlocks) {
        name = spec.has("name") ? spec.get("name").getAsString() : "";
        for (Map.Entry<String, JsonElement> e : spec.getAsJsonObject("materials").entrySet()) {
            JsonObject m = e.getValue().getAsJsonObject();
            if (allBlocks || !m.has("terrain") || m.get("terrain").getAsBoolean()) {
                materials.add(new Material(e.getKey(), m));
            }
        }
        if (spec.has("air")) {
            spec.getAsJsonArray("air").forEach(s -> air.add(BlockStates.parse(s.getAsString()).name()));
        } else {
            DEFAULT_AIR.forEach(s -> air.add(BlockStates.parse(s).name()));
        }
        if (spec.has("decor")) {
            spec.getAsJsonArray("decor").forEach(s -> decor.add(BlockStates.parse(s.getAsString())));
        }
        for (int index = 0; index < materials.size(); index++) {
            Material material = materials.get(index);
            for (Map.Entry<String, Piece> ps : material.pieceStates().entrySet()) {
                exact.putIfAbsent(BlockStates.normalize(ps.getKey()), new Kind(PIECE, index, ps.getValue()));
            }
            if (material.stairsBlock != null) {
                stairBlocks.putIfAbsent(BlockStates.parse(material.stairsBlock).name(), index);
            }
            for (Map.Entry<Integer, String> e : material.shapes.entrySet()) {
                exact3.putIfAbsent(BlockStates.normalize(e.getValue()), new Kind3(PIECE, index, e.getKey()));
            }
        }
    }

    public static Table load(Reader reader) {
        return new Table(JsonParser.parseReader(reader).getAsJsonObject());
    }

    public static Table load(Path path) throws IOException {
        try (Reader r = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            return load(r);
        }
    }

    /** How the 3D smoother sees a block state: category, material index (or -1), and the octants it fills
     *  (bit ox + 2*oz + 4*oy; 0xFF for full terrain). Upside-down stairs and top slabs are pieces here. */
    public record Kind3(int category, int material, int mask) {
    }

    private final Map<String, Kind3> exact3 = new HashMap<>();
    private final Map<String, Kind3> cache3 = new ConcurrentHashMap<>();

    public Kind3 kind3(String state) {
        Kind3 kind = cache3.get(state);
        if (kind == null) {
            kind = classify3(state);
            cache3.put(state, kind);
        }
        return kind;
    }

    private Kind3 classify3(String state) {
        BlockStates.Parsed parsed = BlockStates.parse(state);
        if (inWater(parsed)) {
            return new Kind3(OTHER, -1, 0);
        }
        Kind kind = kind(state);
        if (kind.category() == AIR || kind.category() == DECOR || kind.category() == TERRAIN) {
            return new Kind3(kind.category(), kind.material(), kind.category() == TERRAIN ? 0xFF : 0);
        }
        Kind3 hit = exact3.get(BlockStates.format(parsed));
        if (hit != null) {
            return hit;
        }
        Integer stairMaterial = stairBlocks.get(parsed.name());
        if (stairMaterial != null) {
            Map<String, String> props = parsed.props();
            String half = props.getOrDefault("half", "bottom");
            int high = Shapes.stairHigh(props.getOrDefault("facing", "north"), props.getOrDefault("shape", "straight"), "bottom");
            if (high >= 0 && (half.equals("bottom") || half.equals("top"))) {
                return new Kind3(PIECE, stairMaterial, Octants.stairs(high, half.equals("top")));
            }
        }
        if (kind.category() == PIECE) {
            return new Kind3(PIECE, kind.material(), Octants.of(kind.piece()));
        }
        return new Kind3(OTHER, -1, 0);
    }

    public Kind kind(String state) {
        Kind kind = cache.get(state);
        if (kind == null) {
            kind = classify(state);
            cache.put(state, kind);
        }
        return kind;
    }

    private Kind classify(String state) {
        BlockStates.Parsed parsed = BlockStates.parse(state);
        String id = parsed.name();
        Map<String, String> props = parsed.props();
        if (inWater(parsed)) {
            return OTHER_KIND;
        }
        if (air.contains(id)) {
            return AIR_KIND;
        }
        Kind hit = exact.get(BlockStates.format(parsed));
        if (hit != null) {
            return hit;
        }
        Integer stairMaterial = stairBlocks.get(id);
        if (stairMaterial != null) {
            int high = Shapes.stairHigh(props.getOrDefault("facing", "north"), props.getOrDefault("shape", "straight"),
                    props.getOrDefault("half", "bottom"));
            if (high < 0) {
                return OTHER_KIND; // upside-down stairs don't fit a height field
            }
            return new Kind(PIECE, stairMaterial, new Piece(Shapes.Kind.STAIRS, high));
        }
        // The pattern naming the most properties wins, so "mycelium[snowy=true]" can be a different material
        // from plain "mycelium".
        int best = -1, bestProps = -1;
        for (int index = 0; index < materials.size(); index++) {
            for (BlockStates.Parsed pattern : materials.get(index).full) {
                if (matches(id, props, pattern) && pattern.props().size() > bestProps) {
                    best = index;
                    bestProps = pattern.props().size();
                }
            }
        }
        if (best >= 0) {
            return new Kind(TERRAIN, best, null);
        }
        for (BlockStates.Parsed pattern : decor) {
            if (matches(id, props, pattern)) {
                return DECOR_KIND;
            }
        }
        return OTHER_KIND;
    }

    /** Water, and blocks that only exist in water but have no waterlogged property. */
    private static final Set<String> WATER = Set.of("minecraft:water", "minecraft:bubble_column", "minecraft:seagrass",
            "minecraft:tall_seagrass", "minecraft:kelp", "minecraft:kelp_plant");

    /**
     * A block in water is left alone: any piece or air put in its place would lose the water. That is a waterlogged
     * state (a pattern without properties, such as a coral or a stair block, matches it too), water, and the blocks
     * that only exist in water, such as seagrass and kelp.
     */
    static boolean inWater(BlockStates.Parsed parsed) {
        return "true".equals(parsed.props().get("waterlogged")) || WATER.contains(parsed.name());
    }

    private static boolean matches(String id, Map<String, String> props, BlockStates.Parsed pattern) {
        if (!id.equals(pattern.name())) {
            return false;
        }
        for (Map.Entry<String, String> e : pattern.props().entrySet()) {
            if (!e.getValue().equals(props.get(e.getKey()))) {
                return false;
            }
        }
        return true;
    }

    public static final class Material {
        public final String name;
        /** For players: the inventory name of its first full block, else the material name. */
        public final String label;
        final List<BlockStates.Parsed> full = new ArrayList<>();
        public final String fullState;
        final String slab;
        final String stairsBlock;                       // a vanilla stair block: every shape
        final Map<String, String> stairsBySide = new LinkedHashMap<>();
        final Map<String, String> vslab = new LinkedHashMap<>();
        final Map<String, String> vstairs = new LinkedHashMap<>();

        Material(String name, JsonObject spec) {
            this.name = name;
            this.label = spec.has("label") ? spec.get("label").getAsString() : name;
            if (spec.has("full")) {
                spec.getAsJsonArray("full").forEach(s -> full.add(BlockStates.parse(s.getAsString())));
            }
            fullState = full.isEmpty() ? null : BlockStates.format(full.get(0));
            slab = string(spec, "slab");
            JsonElement stairs = spec.get("stairs");
            if (stairs != null && stairs.isJsonPrimitive()) {
                stairsBlock = stairs.getAsString();
            } else {
                stairsBlock = null;
                if (stairs != null && stairs.isJsonObject()) {
                    stairs.getAsJsonObject().entrySet().forEach(e -> stairsBySide.put(e.getKey(), e.getValue().getAsString()));
                }
            }
            readMap(spec, "vslab", vslab);
            readMap(spec, "vstairs", vstairs);
            JsonElement explicit = spec.get("shapes");
            if (explicit != null && explicit.isJsonObject()) {
                explicit.getAsJsonObject().entrySet().forEach(e -> shapes.put(Integer.parseInt(e.getKey()), e.getValue().getAsString()));
            } else {
                buildShapes();
            }
        }

        /** Every piece this material can place, by octant mask (for the 3D smoother). Tables built from a pack
         *  ({@link TableBuilder}) list them, each checked against its model. Otherwise top slabs and upside-down
         *  stairs are derived from vanilla slab and stair blocks; custom pieces (repeater slabs and the like) have
         *  no upside-down form. */
        public final Map<Integer, String> shapes = new LinkedHashMap<>();

        private void buildShapes() {
            if (slab != null) {
                shapes.put(Octants.SLAB, slab);
                BlockStates.Parsed p = BlockStates.parse(slab);
                if ("bottom".equals(p.props().get("type"))) {
                    p.props().put("type", "top");
                    shapes.put(Octants.TOP_SLAB, BlockStates.format(p));
                }
            }
            if (stairsBlock != null) {
                for (int high = 1; high < Shapes.ALL; high++) {
                    int n = Integer.bitCount(high);
                    if (n == 2 && Shapes.sideName(high) == null) {
                        continue; // opposite corners: no such stair
                    }
                    String[] fs = Shapes.stairProperties(high);
                    for (boolean top : new boolean[] {false, true}) {
                        shapes.put(Octants.stairs(high, top), BlockStates.format(stairsBlock, Map.of("facing", fs[0],
                                "half", top ? "top" : "bottom", "shape", fs[1], "waterlogged", "false")));
                    }
                }
            }
            stairsBySide.forEach((side, s) -> shapes.put(Octants.stairs(Shapes.side(side), false), s));
            vslab.forEach((side, s) -> shapes.put(Octants.column(Shapes.side(side)), s));
            vstairs.forEach((q, s) -> shapes.put(Octants.column(Shapes.ALL & ~(1 << quadrant(q))), s));
        }

        private static String string(JsonObject spec, String key) {
            JsonElement e = spec.get(key);
            return e == null || e.isJsonNull() ? null : e.getAsString();
        }

        private static void readMap(JsonObject spec, String key, Map<String, String> into) {
            JsonElement e = spec.get(key);
            if (e != null && e.isJsonObject()) {
                e.getAsJsonObject().entrySet().forEach(x -> into.put(x.getKey(), x.getValue().getAsString()));
            }
        }

        public boolean has(Piece piece) {
            return switch (piece.kind()) {
                case AIR, FULL -> true;
                case SLAB -> slab != null;
                case STAIRS -> stairsBlock != null
                        || (Integer.bitCount(piece.high()) == 2 && stairsBySide.containsKey(Shapes.sideName(piece.high())));
                case VSLAB -> vslab.containsKey(Shapes.sideName(piece.high()));
                case VSTAIRS -> vstairs.containsKey(missing(piece.high()));
            };
        }

        /** The block state for a piece of this material (null for air). */
        public String state(Piece piece) {
            return switch (piece.kind()) {
                case AIR -> null;
                case FULL -> fullState;
                case SLAB -> slab;
                case STAIRS -> {
                    if (stairsBlock == null) {
                        yield stairsBySide.get(Shapes.sideName(piece.high()));
                    }
                    String[] fs = Shapes.stairProperties(piece.high());
                    yield BlockStates.format(stairsBlock, Map.of("facing", fs[0], "half", "bottom", "shape", fs[1],
                            "waterlogged", "false"));
                }
                case VSLAB -> vslab.get(Shapes.sideName(piece.high()));
                case VSTAIRS -> vstairs.get(missing(piece.high()));
            };
        }

        /** Every (state, piece) this material can place, besides vanilla stair blocks. */
        Map<String, Piece> pieceStates() {
            Map<String, Piece> out = new LinkedHashMap<>();
            if (slab != null) {
                out.putIfAbsent(slab, Shapes.SLAB);
            }
            stairsBySide.forEach((side, s) -> out.putIfAbsent(s, new Piece(Shapes.Kind.STAIRS, Shapes.side(side))));
            vslab.forEach((side, s) -> out.putIfAbsent(s, new Piece(Shapes.Kind.VSLAB, Shapes.side(side))));
            vstairs.forEach((q, s) -> out.putIfAbsent(s, new Piece(Shapes.Kind.VSTAIRS, Shapes.ALL & ~(1 << quadrant(q)))));
            return out;
        }

        private static int quadrant(String name) {
            return switch (name) {
                case "NW" -> Shapes.NW;
                case "NE" -> Shapes.NE;
                case "SW" -> Shapes.SW;
                case "SE" -> Shapes.SE;
                default -> throw new IllegalArgumentException("unknown quadrant " + name);
            };
        }

        private static String missing(int high) {
            int rest = Shapes.ALL & ~high;
            return Integer.bitCount(rest) == 1 ? new String[] {"NW", "NE", "SW", "SE"}[Integer.numberOfTrailingZeros(rest)] : null;
        }
    }
}
