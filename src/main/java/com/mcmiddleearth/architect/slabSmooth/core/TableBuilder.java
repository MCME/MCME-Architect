package com.mcmiddleearth.architect.slabSmooth.core;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Predicate;

/**
 * Builds a material table from a pack's inventory and its models, with no hand-made recipe:
 * <ul>
 * <li>terrain is what the inventory files under the terrain categories place, plus the blocks listed as terrain;</li>
 * <li>a state is a full block or a piece by the shape of its model (octants), never by its name;</li>
 * <li>a full block uses the pieces whose texture, on the side that faces out of the surface, is the same as the
 * full block's on that side, or failing that has nearly the same colour.</li>
 * </ul>
 * Full blocks that end up with no pieces aren't terrain, so they are left alone.
 *
 * <p>Full blocks of the other categories (bricks, roof tiles, but also rock kept under a building category) that
 * have pieces of their own become materials marked {@code "terrain": false}: smoothed only when a builder asks for
 * every block ({@code blocks all}). They take only pieces with the same texture or named after them, never pieces
 * that merely look alike.
 */
public final class TableBuilder {

    private TableBuilder() {
    }

    public static final class Settings {
        /** Inventory categories that hold terrain. */
        public List<String> terrainCategories = List.of("Terra");
        /** Inventory categories of plants: removed or kept with the ground under them, never in the way. */
        public List<String> decorCategories = List.of("Plant", "Crop");
        /** Largest colour difference (RGB distance, 0-441) for pieces whose texture isn't the full block's, such as
         *  "grass on mud" using the grass slab. 0: only identical textures. */
        public double colorTolerance = 10;
        /** Inventory keys never used. */
        public Set<String> exclude = Set.of();
        /** Full block key -> piece entry keys it always uses (before any matched by texture). */
        public Map<String, List<String>> pairs = Map.of();
        /** Inventory keys of full blocks that are terrain although their category isn't a terrain category. */
        public Set<String> terrain = Set.of();
        /** Whether the server knows a block state. One it doesn't is left out of the table, with a line in the report. */
        public Predicate<String> validState = state -> true;
        /** Whether a block state has a block entity (a barrel, a chest). Such a full block never becomes a material;
         *  such a piece (a comparator slab) still can. */
        public Predicate<String> hasBlockEntity = state -> false;
    }

    /** materials: terrain materials; others: materials of other blocks, smoothed only with "blocks all". */
    public record Result(JsonObject table, List<String> report, int materials, int others) {
    }

    static final List<String> VANILLA_DECOR = List.of(
            "short_grass", "tall_grass", "fern", "large_fern", "dead_bush", "dandelion", "poppy", "blue_orchid",
            "allium", "azure_bluet", "red_tulip", "orange_tulip", "white_tulip", "pink_tulip", "oxeye_daisy",
            "cornflower", "lily_of_the_valley", "sunflower", "lilac", "rose_bush", "peony", "sweet_berry_bush",
            "pink_petals", "wildflowers", "leaf_litter", "bush", "firefly_bush", "short_dry_grass", "tall_dry_grass",
            "brown_mushroom", "red_mushroom", "torchflower", "pitcher_plant", "snow");

    private record Member(String state, int mask, int dir) {
    }

    /** terra: the pieces of a terrain category, which terrain may also use by look. */
    private record Group(Inventory.Entry entry, List<Member> members, String note, boolean terra) {
    }

    /** terra: in a terrain category (pieces by look too); terrain: that or listed as terrain. */
    private record Full(String state, Inventory.Entry entry, boolean terra, boolean terrain) {
    }

    private record MaterialKey(Map<Integer, String> shapes, boolean terrain) {
    }

    public static Result build(String name, List<Inventory.Entry> entries, Models models, Settings settings) {
        Map<Integer, Integer> pieceMasks = Directional.pieceMasks();
        List<String> report = new ArrayList<>();
        Set<String> terrainCats = lower(settings.terrainCategories), decorCats = lower(settings.decorCategories);

        // 1. Every block state, as a full block or a piece, by its shape. Terrain categories first, so a state listed
        //    twice belongs to terrain.
        Map<String, Full> fulls = new LinkedHashMap<>();
        List<Group> groups = new ArrayList<>();
        Set<String> seenPieces = new HashSet<>();
        List<String> leftOutNotes = new ArrayList<>();
        List<Inventory.Entry> ordered = new ArrayList<>(entries);
        ordered.sort(Comparator.comparing((Inventory.Entry e) -> !terrainCats.contains(e.category().toLowerCase(Locale.ROOT))));
        for (Inventory.Entry e : ordered) {
            String category = e.category().toLowerCase(Locale.ROOT);
            if (decorCats.contains(category) || settings.exclude.contains(e.key())) {
                continue;
            }
            boolean terra = terrainCats.contains(category);
            List<Member> members = new ArrayList<>();
            String note = "";
            Map<String, List<String>> leftOut = new LinkedHashMap<>();
            if (e.vanilla() && e.block().endsWith("_stairs")) {
                int wrong = 0;
                for (int high = 1; high < Shapes.ALL; high++) {
                    if (Integer.bitCount(high) == 2 && Shapes.sideName(high) == null) {
                        continue;
                    }
                    String[] fs = Shapes.stairProperties(high);
                    for (boolean top : new boolean[] {false, true}) {
                        String state = BlockStates.format(e.block(), Map.of("facing", fs[0], "half", top ? "top" : "bottom",
                                "shape", fs[1], "waterlogged", "false"));
                        int expected = Octants.stairs(high, top);
                        int mask = shape(state, models, settings, leftOut);
                        if (mask == expected) {
                            members.add(new Member(state, expected, pieceMasks.get(expected)));
                        } else if (mask != LEFT_OUT) {
                            wrong++;
                        }
                    }
                }
                if (wrong > 0) {
                    note = wrong + " stair shapes don't look like stairs";
                }
            } else {
                List<String> states = new ArrayList<>();
                if (e.vanilla() && e.block().endsWith("_slab")) {
                    states.add(BlockStates.format(e.block(), Map.of("type", "bottom", "waterlogged", "false")));
                    states.add(BlockStates.format(e.block(), Map.of("type", "top", "waterlogged", "false")));
                } else if (e.vanilla()) {
                    states.add(e.block());
                } else {
                    states.addAll(e.states().values());
                }
                for (String state : states) {
                    if ("true".equals(BlockStates.parse(state).props().get("waterlogged"))) {
                        continue; // would place water
                    }
                    int mask = shape(state, models, settings, leftOut);
                    if (mask == NO_SHAPE || mask == LEFT_OUT) {
                        continue; // not a shape the smoother knows, or left out: never touched
                    }
                    if (mask == Octants.FULL) {
                        if (!settings.hasBlockEntity.test(state)) { // cutting a barrel or a chest would empty it
                            fulls.putIfAbsent(state, new Full(state, e, terra, terra || settings.terrain.contains(e.key())));
                        }
                    } else if (pieceMasks.containsKey(mask) && seenPieces.add(state)) {
                        members.add(new Member(state, mask, pieceMasks.get(mask)));
                    }
                }
            }
            if (!members.isEmpty()) {
                groups.add(new Group(e, members, terra ? note : "", terra));
            }
            noteLeftOut(e, leftOut, leftOutNotes);
        }

        // 2. Each full block's pieces: forced pairs first, then the same texture, then the same name, then (terrain
        //    category blocks, with their category's pieces) nearly the same look.
        Map<String, Map<Integer, String>> shapesOf = new LinkedHashMap<>();
        Map<String, List<String>> sourcesOf = new HashMap<>();
        for (Full f : fulls.values()) {
            List<Group> forced = new ArrayList<>();
            for (String key : settings.pairs.getOrDefault(f.entry().key(), List.of())) {
                groups.stream().filter(g -> g.entry().key().equals(key)).findFirst().ifPresent(forced::add);
            }
            Map<Group, Match> matches = new HashMap<>();
            for (Group g : groups) {
                Match m = f.terra() && !g.terra() ? null : match(models, f, g, settings);
                matches.put(g, m != null && (f.terra() || m.tier() <= 1) ? m : null);
            }
            List<Group> ranked = new ArrayList<>(groups);
            ranked.removeIf(g -> matches.get(g) == null || forced.contains(g));
            ranked.sort(Comparator.comparingInt((Group g) -> matches.get(g).tier()).thenComparingDouble(g -> matches.get(g).distance()));
            Map<Integer, String> shapes = new TreeMap<>();
            List<String> sources = new ArrayList<>();
            for (Group g : forced) {
                take(g, shapes, sources, "config");
            }
            for (Group g : ranked) {
                take(g, shapes, sources, matches.get(g).how());
            }
            if (!shapes.isEmpty()) {
                shapesOf.put(f.state(), shapes);
                sourcesOf.put(f.state(), sources);
            }
        }

        // 3. Full blocks with the same pieces make one material; terrain and other blocks never share one.
        Map<MaterialKey, List<Full>> materials = new LinkedHashMap<>();
        for (Full f : fulls.values()) {
            Map<Integer, String> shapes = shapesOf.get(f.state());
            if (shapes != null) {
                materials.computeIfAbsent(new MaterialKey(shapes, f.terrain()), k -> new ArrayList<>()).add(f);
            }
        }
        JsonObject mats = new JsonObject();
        Set<String> terrainStates = new HashSet<>();
        List<String> otherLines = new ArrayList<>();
        int terrainCount = 0;
        for (boolean terrain : new boolean[] {true, false}) { // terrain first: it wins a piece both use
            for (Map.Entry<MaterialKey, List<Full>> m : materials.entrySet()) {
                if (m.getKey().terrain() != terrain) {
                    continue;
                }
                Map<Integer, String> shapes = m.getKey().shapes();
                String matName = unique(mats, m.getValue().get(0).entry().key());
                JsonObject json = material(m.getValue(), shapes);
                json.addProperty("label", label(m.getValue().get(0).entry()));
                if (!terrain) {
                    json.addProperty("terrain", false);
                }
                mats.add(matName, json);
                m.getValue().forEach(f -> terrainStates.add(BlockStates.normalize(f.state())));
                shapes.values().forEach(s -> terrainStates.add(BlockStates.normalize(s)));
                Full first = m.getValue().get(0);
                String line = matName + ": " + m.getValue().stream().map(f -> label(f.entry())).distinct().toList()
                        + " with " + String.join(", ", sourcesOf.get(first.state())) + " (" + shapes.size() + " pieces)"
                        + (terrain && !first.terra() ? " (terrain by config)" : "");
                if (terrain) {
                    report.add(line);
                    terrainCount++;
                } else {
                    otherLines.add(line);
                }
            }
        }
        for (Group g : groups) {
            if (!g.note().isEmpty()) {
                report.add("note: " + label(g.entry()) + ": " + g.note());
            }
        }
        report.addAll(leftOutNotes);
        List<Full> unused = fulls.values().stream().filter(f -> f.terrain() && !shapesOf.containsKey(f.state())).toList();
        if (!unused.isEmpty()) {
            report.add(unused.size() + " terrain full blocks have no matching pieces and are left alone: "
                    + unused.stream().map(f -> label(f.entry())).distinct().toList());
        }
        Set<String> fullKeys = new HashSet<>();
        fulls.values().forEach(f -> fullKeys.add(f.entry().key()));
        settings.terrain.stream().filter(k -> !fullKeys.contains(k)).sorted()
                .forEach(k -> report.add("note: " + k + " is listed as terrain but is no full block in the inventory"));
        if (!otherLines.isEmpty()) {
            report.add("");
            report.add("Other blocks with pieces of their own. Not terrain: smoothed only for builders who use /sls set blocks all.");
            report.add("To make one terrain for everyone, list its key under packs.overrides." + name + ".terrain in config.yml.");
            report.add("");
            report.addAll(otherLines);
        }

        // 4. Plants.
        Set<String> decor = new LinkedHashSet<>();
        VANILLA_DECOR.forEach(d -> decor.add("minecraft:" + d));
        for (Inventory.Entry e : entries) {
            if (decorCats.contains(e.category().toLowerCase(Locale.ROOT)) && !settings.exclude.contains(e.key())) {
                if (e.vanilla()) {
                    decor.add(e.block());
                } else {
                    decor.addAll(e.states().values());
                }
            }
        }
        JsonArray decorJson = new JsonArray();
        decor.stream().map(BlockStates::normalize).filter(s -> !terrainStates.contains(s)).distinct().sorted().forEach(decorJson::add);

        JsonObject table = new JsonObject();
        table.addProperty("name", name);
        JsonArray air = new JsonArray();
        List.of("minecraft:air", "minecraft:cave_air", "minecraft:void_air").forEach(air::add);
        table.add("air", air);
        table.add("decor", decorJson);
        table.add("materials", mats);
        return new Result(table, report, terrainCount, mats.size() - terrainCount);
    }

    /** {@link #shape}: the state's model isn't a clean shape ({@link Models#cleanMask} is null). */
    private static final int NO_SHAPE = -1;
    /** {@link #shape}: the state is left out of the table and noted in the report. */
    private static final int LEFT_OUT = -2;

    /**
     * The octants of a state's model, NO_SHAPE, or LEFT_OUT if the server doesn't know the state or its model can't
     * be read (a broken pack file); such states are listed in leftOut under the reason.
     */
    private static int shape(String state, Models models, Settings settings, Map<String, List<String>> leftOut) {
        if (!settings.validState.test(state)) {
            leftOut.computeIfAbsent("the server doesn't know", why -> new ArrayList<>()).add(state);
            return LEFT_OUT;
        }
        Integer mask;
        try {
            mask = models.cleanMask(state);
        } catch (RuntimeException e) {
            leftOut.computeIfAbsent("can't read the model (" + e + ") of", why -> new ArrayList<>()).add(state);
            return LEFT_OUT;
        }
        return mask == null ? NO_SHAPE : mask;
    }

    /** One report line per reason: "note: Rock: the server doesn't know minecraft:x[...] and 3 more, left out". */
    private static void noteLeftOut(Inventory.Entry e, Map<String, List<String>> leftOut, List<String> notes) {
        leftOut.forEach((why, states) -> notes.add("note: " + label(e) + ": " + why + " " + states.get(0)
                + (states.size() > 1 ? " and " + (states.size() - 1) + " more" : "") + ", left out"));
    }

    private static void take(Group g, Map<Integer, String> shapes, List<String> sources, String how) {
        boolean any = false;
        for (Member m : g.members()) {
            if (!shapes.containsKey(m.mask())) {
                shapes.put(m.mask(), m.state());
                any = true;
            }
        }
        if (any) {
            sources.add(label(g.entry()) + " by " + how);
        }
    }

    /** How well a piece group fits a full block: tier 0 same texture, 1 same name, 2 nearly the same look. */
    private record Match(int tier, double distance, String how) {
    }

    /** Largest difference for pieces named after the full block ("Continental Sand Slab" for "Continental Sand"). */
    static final double NAME_TOLERANCE = 40;

    /** Compares each piece's outward side with the same side of the full block; null if they don't fit. */
    private static Match match(Models models, Full full, Group g, Settings settings) {
        double sum = 0;
        int n = 0;
        boolean same = true;
        Map<Integer, Models.Face> fullFaces = new HashMap<>();
        for (Member m : g.members()) {
            Models.Face pf = models.face(m.state(), m.dir());
            Models.Face ff = fullFaces.computeIfAbsent(m.dir(), d -> models.face(full.state(), d));
            if (pf == null || ff == null) {
                return null;
            }
            n++;
            if (pf.texture().equals(ff.texture())) {
                continue;
            }
            same = false;
            if (pf.rgb() < 0 || ff.rgb() < 0) {
                return null;
            }
            sum += difference(pf, ff);
        }
        if (same) {
            return new Match(0, 0, "texture");
        }
        double d = sum / n;
        if (baseName(g.entry().display()).equals(baseName(full.entry().display())) && !baseName(full.entry().display()).isEmpty()
                && d <= Math.max(NAME_TOLERANCE, settings.colorTolerance)) {
            return new Match(1, d, "name");
        }
        if (d <= settings.colorTolerance) {
            return new Match(2, d, String.format(Locale.ROOT, "look %.0f", d));
        }
        return null;
    }

    /** Difference between two sides: average colour and contrast (a gravel and a smooth stone can share a colour). */
    static double difference(Models.Face a, Models.Face b) {
        double dc = 2 * (a.contrast() - b.contrast());
        return Math.sqrt(Math.pow(distance(a.rgb(), b.rgb()), 2) + dc * dc);
    }

    /** "Dirt Slab (lower)" and "Dirt Block" -> "dirt". */
    static String baseName(String display) {
        String s = display.toLowerCase(Locale.ROOT).replaceAll("\\(.*?\\)", " ");
        s = s.replaceAll("\\b(slab|slabs|stairs|stair|vertical|lower|upper|block|full)\\b", " ");
        return s.replaceAll("[^a-z0-9]+", " ").trim();
    }

    static double distance(int a, int b) {
        int dr = (a >> 16 & 255) - (b >> 16 & 255), dg = (a >> 8 & 255) - (b >> 8 & 255), db = (a & 255) - (b & 255);
        return Math.sqrt(dr * dr + dg * dg + db * db);
    }

    private static JsonObject material(List<Full> fulls, Map<Integer, String> shapes) {
        JsonObject m = new JsonObject();
        JsonArray full = new JsonArray();
        fulls.forEach(f -> full.add(f.state()));
        m.add("full", full);
        // The top-only smoother's pieces.
        if (shapes.containsKey(Octants.SLAB)) {
            m.addProperty("slab", shapes.get(Octants.SLAB));
        }
        String stairsBlock = null;
        boolean oneBlock = true;
        JsonObject stairsBySide = new JsonObject(), vslab = new JsonObject(), vstairs = new JsonObject();
        for (int high = 1; high < Shapes.ALL; high++) {
            if (Integer.bitCount(high) == 2 && Shapes.sideName(high) == null) {
                continue;
            }
            for (boolean top : new boolean[] {false, true}) {
                String s = shapes.get(Octants.stairs(high, top));
                String block = s == null ? null : BlockStates.parse(s).name();
                if (block == null || (stairsBlock != null && !stairsBlock.equals(block))) {
                    oneBlock = false;
                } else {
                    stairsBlock = block;
                }
            }
            String bottom = shapes.get(Octants.stairs(high, false));
            if (bottom != null && Shapes.sideName(high) != null && Integer.bitCount(high) == 2) {
                stairsBySide.addProperty(Shapes.sideName(high), bottom);
            }
        }
        if (oneBlock && stairsBlock != null) {
            m.addProperty("stairs", stairsBlock);
        } else if (!stairsBySide.isEmpty()) {
            m.add("stairs", stairsBySide);
        }
        String[] quadrants = {"NW", "NE", "SW", "SE"};
        for (Map.Entry<Integer, String> e : shapes.entrySet()) {
            int mask = e.getKey(), lower = mask & 0x0F;
            if (mask >> 4 != lower) {
                continue;
            }
            if (Integer.bitCount(lower) == 2 && Shapes.sideName(lower) != null) {
                vslab.addProperty(Shapes.sideName(lower), e.getValue());
            } else if (Integer.bitCount(lower) == 3) {
                vstairs.addProperty(quadrants[Integer.numberOfTrailingZeros(Shapes.ALL & ~lower)], e.getValue());
            }
        }
        if (!vslab.isEmpty()) {
            m.add("vslab", vslab);
        }
        if (!vstairs.isEmpty()) {
            m.add("vstairs", vstairs);
        }
        // Every piece, checked against the models, for the all-surfaces smoother.
        JsonObject shapesJson = new JsonObject();
        shapes.forEach((mask, s) -> shapesJson.addProperty(String.valueOf(mask), s));
        m.add("shapes", shapesJson);
        return m;
    }

    private static String label(Inventory.Entry e) {
        return e.display().isEmpty() ? e.key() : e.display();
    }

    private static String unique(JsonObject existing, String base) {
        String name = base;
        for (int i = 2; existing.has(name); i++) {
            name = base + "_" + i;
        }
        return name;
    }

    private static Set<String> lower(List<String> list) {
        Set<String> out = new HashSet<>();
        list.forEach(s -> out.add(s.toLowerCase(Locale.ROOT)));
        return out;
    }
}
