package com.mcmiddleearth.architect.slabSmooth.core;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.SplittableRandom;
import java.util.function.IntPredicate;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What the smoother promises builders, on the golden scenes' inputs and on random hills: only blocks inside the
 * selection or brush change; only terrain, its pieces, and the air and plants next to it change; a plant goes with
 * the ground under it, or keeps it, as the plants option says; what is built keeps the blocks it stands on and hangs
 * from; and the same seed gives the same result.
 */
class AlgorithmPropertiesTest {

    private static final String AIR = "minecraft:air";
    /** A two-high plant from the table's decor list, and a one-high one. */
    private static final String PLANT_LOWER = "minecraft:large_fern[half=lower]", PLANT_UPPER = "minecraft:large_fern[half=upper]",
            POPPY = "minecraft:poppy";
    /** Built blocks: something standing on the ground, and something hanging under it. */
    private static final String CHEST = "minecraft:chest[facing=north,type=single,waterlogged=false]",
            LANTERN = "minecraft:lantern[hanging=true,waterlogged=false]";
    /** A block entity, a log, water, a waterlogged stair and bedrock: never changed. */
    private static final List<String> LEFT_ALONE = List.of(CHEST,
            "minecraft:oak_log[axis=y]", "minecraft:water[level=0]",
            "minecraft:stone_stairs[facing=east,half=bottom,shape=straight,waterlogged=true]", "minecraft:bedrock");
    /** The golden tiles run's tile size and margin. */
    private static final int TILE = 16, MARGIN = 5;

    private static Table table;

    @BeforeAll
    static void loadTable() throws Exception {
        table = TestTables.handMade();
    }

    /** Below a mask a block's colour can change, and above it a plant's top half can go; neither may happen. */
    @Test
    void nothingChangesOutsideTheMask() throws IOException {
        List<GoldenScenes.Scene> scenes = new ArrayList<>(scenes());
        scenes.addAll(scenes.stream().map(AlgorithmPropertiesTest::withTallPlants).toList());
        List<String> outside = new ArrayList<>();
        int changed = 0;
        for (GoldenScenes.Scene s : scenes) {
            for (Map.Entry<String, boolean[]> mask : List.of(Map.entry("box", box(s)), Map.entry("sphere", sphere(s)))) {
                boolean[] inside = mask.getValue();
                for (String mode : List.of("directional", "tiles")) {
                    Smoother.Result r = run(s, mode, new Options(), inside);
                    changed += r.report().changedColumns();
                    outside.addAll(changes(s, r, i -> !inside[i], s.name() + "/" + mode + "/" + mask.getKey()));
                }
            }
            boolean[] columns = disc(s);
            Smoother.Result r = run(s, "top", new Options(), columns);
            changed += r.report().changedColumns();
            outside.addAll(changes(s, r, i -> !columns[i % (s.length() * s.width())], s.name() + "/top"));
        }
        assertTrue(changed > 0, "nothing changed at all");
        assertTrue(outside.isEmpty(), outside.size() + " blocks changed outside the mask, such as " + first(outside));
    }

    @Test
    void aPlantWhoseTopIsOutsideTheMaskStaysWhole() throws IOException {
        assertEquals(Table.DECOR, table.kind3(PLANT_LOWER).category());
        assertEquals(Table.DECOR, table.kind3(PLANT_UPPER).category());
        List<String> broken = new ArrayList<>();
        int changed = 0;
        for (GoldenScenes.Scene ground : scenes()) {
            GoldenScenes.Scene s = withTallPlants(ground);
            int lower = s.palette().indexOf(PLANT_LOWER), upper = s.palette().indexOf(PLANT_UPPER);
            // Every column up to the block above its ground: the mask ends between a plant's halves.
            int[] tops = tops(ground);
            int cols = s.length() * s.width();
            boolean[] mask = new boolean[s.blocks().length];
            for (int i = 0; i < mask.length; i++) {
                mask[i] = i / cols <= tops[i % cols] + 1;
            }
            for (String mode : List.of("directional", "tiles")) {
                Smoother.Result r = run(s, mode, new Options(), mask);
                changed += r.report().changedColumns();
                broken.addAll(changes(s, r, i -> s.blocks()[i] == lower || s.blocks()[i] == upper, s.name() + "/" + mode));
            }
        }
        assertTrue(changed > 0, "nothing changed at all");
        assertTrue(broken.isEmpty(), broken.size() + " plant halves changed, such as " + first(broken));
    }

    /**
     * Plants follow the plants option in every path. On each golden scene with a poppy on every block of grass with
     * air above it: with plants remove, no poppy is left on ground that changed; with plants keep, no ground under a
     * poppy changes. A wall pass cutting the top half of the grass under a poppy breaks both.
     */
    @Test
    void plantsFollowThePlantsOptionInEveryPath() throws IOException {
        assertEquals(Table.DECOR, table.kind3(POPPY).category());
        Options keep = new Options();
        keep.set("plants", "keep");
        List<String> floating = new ArrayList<>(), changedUnderAPoppy = new ArrayList<>();
        int poppies = 0, removed = 0;
        for (String name : GoldenScenes.NAMES) {
            GoldenScenes.Scene s = withPoppiesOnGrass(GoldenScenes.scene(name));
            int cols = s.length() * s.width(), poppy = s.palette().indexOf(POPPY);
            IntPredicate underAPoppy = i -> i + cols < s.blocks().length && s.blocks()[i + cols] == poppy;
            poppies += count(s.blocks().length, underAPoppy);
            for (String mode : List.of("directional", "tiles", "top")) {
                Smoother.Result r = run(s, mode, new Options(), null);
                removed += r.report().plantsRemoved();
                IntPredicate poppyStays = i -> underAPoppy.test(i) && POPPY.equals(r.palette().get(r.blocks()[i + cols]));
                floating.addAll(changes(s, r, poppyStays, s.name() + "/" + mode));
                changedUnderAPoppy.addAll(changes(s, run(s, mode, keep, null), underAPoppy, s.name() + "/" + mode));
            }
        }
        int poppyCount = poppies, removedCount = removed;
        assertTrue(poppyCount > 0 && removedCount > 0, poppyCount + " poppies, " + removedCount + " removed");
        assertAll(
                () -> assertTrue(floating.isEmpty(), "plants remove: " + floating.size()
                        + " poppies left on ground that changed, such as " + first(floating)),
                () -> assertTrue(changedUnderAPoppy.isEmpty(), "plants keep: " + changedUnderAPoppy.size()
                        + " blocks under a poppy changed, such as " + first(changedUnderAPoppy)));
    }

    @Test
    void onlyTerrainPiecesAirAndPlantsChange() throws IOException {
        List<String> changed = new ArrayList<>();
        int changedColumns = 0;
        for (GoldenScenes.Scene ground : scenes()) {
            GoldenScenes.Scene s = withBlocksToLeaveAlone(ground);
            for (String mode : List.of("directional", "tiles", "top")) {
                Smoother.Result r = run(s, mode, new Options(), null);
                changedColumns += r.report().changedColumns();
                changed.addAll(changes(s, r, i -> LEFT_ALONE.contains(s.palette().get(s.blocks()[i])), s.name() + "/" + mode));
            }
        }
        assertTrue(changedColumns > 0, "nothing changed at all");
        assertTrue(changed.isEmpty(), changed.size() + " blocks changed that aren't terrain, pieces, air or plants, such as "
                + first(changed));
    }

    /**
     * What is built keeps its support, in every path. With chests on the tops of every third column of each golden
     * scene, no block under a chest changes. With hanging lanterns under every block of ground with air under it in
     * every third column of each golden scene, and in place of the block under the top of every third column of the
     * random hills, every block over a lantern keeps its four bottom octants: the tops may still smooth it. The
     * top-only smoother sees a top over a lantern, one block of ground, only with min_thickness 1, so it runs that way
     * too.
     */
    @Test
    void whatIsBuiltKeepsItsSupportInEveryPath() throws IOException {
        Options thin = new Options();
        thin.set("min_thickness", "1");
        List<Map.Entry<String, Options>> paths = List.of(Map.entry("directional", new Options()),
                Map.entry("tiles", new Options()), Map.entry("top", new Options()), Map.entry("top", thin));
        List<String> underChests = new ArrayList<>(), overLanterns = new ArrayList<>();
        List<GoldenScenes.Scene> withLanterns = new ArrayList<>();
        int chests = 0, lanterns = 0, changedColumns = 0, smoothedOverALantern = 0;
        for (String name : GoldenScenes.NAMES) {
            GoldenScenes.Scene ground = GoldenScenes.scene(name), s = withChestsOnTops(ground);
            int cols = s.length() * s.width(), chest = s.palette().indexOf(CHEST);
            IntPredicate underAChest = i -> i + cols < s.blocks().length && s.blocks()[i + cols] == chest;
            chests += count(s.blocks().length, underAChest);
            for (Map.Entry<String, Options> path : paths.subList(0, 3)) {
                Smoother.Result r = run(s, path.getKey(), path.getValue(), null);
                changedColumns += r.report().changedColumns();
                underChests.addAll(changes(s, r, underAChest, s.name() + "/" + path.getKey()));
            }
            withLanterns.add(withLanternsUnder(ground));
        }
        for (long seed = 1; seed <= 3; seed++) {
            withLanterns.add(withLanternsUnderTops(randomHills(seed)));
        }
        for (GoldenScenes.Scene s : withLanterns) {
            int cols = s.length() * s.width(), lantern = s.palette().indexOf(LANTERN);
            IntPredicate overALantern = i -> i >= cols && s.blocks()[i - cols] == lantern;
            lanterns += count(s.blocks().length, overALantern);
            for (Map.Entry<String, Options> path : paths) {
                Smoother.Result r = run(s, path.getKey(), path.getValue(), null);
                String label = s.name() + "/" + path.getKey() + (path.getValue() == thin ? " min_thickness 1" : "");
                List<String> changed = changes(s, r, overALantern, label);
                smoothedOverALantern += changed.size();
                overLanterns.addAll(changes(s, r, overALantern.and(i -> !wholeBottom(r, i)), label));
            }
        }
        int chestCount = chests, lanternCount = lanterns, smoothed = smoothedOverALantern;
        assertTrue(chestCount > 0 && lanternCount > 0,
                chestCount + " blocks under chests, " + lanternCount + " over lanterns");
        assertTrue(changedColumns > 0, "nothing changed at all");
        assertAll(
                () -> assertTrue(underChests.isEmpty(), underChests.size() + " changes to the " + chestCount
                        + " blocks under a chest, over the paths, such as " + first(underChests)),
                () -> assertTrue(overLanterns.isEmpty(), overLanterns.size() + " blocks over a lantern lost part of "
                        + "their bottom, such as " + first(overLanterns)),
                () -> assertTrue(smoothed > 0, "the tops still smooth blocks over a lantern"));
    }

    /** Whether the block at i in the result fills its four bottom octants. */
    private static boolean wholeBottom(Smoother.Result r, int i) {
        return (table.kind3(r.palette().get(r.blocks()[i])).mask() & Octants.SLAB) == Octants.SLAB;
    }

    @Test
    void theSameSeedGivesTheSameResult() throws IOException {
        Options opts = new Options();
        opts.set("seed", "7");
        for (GoldenScenes.Scene s : scenes()) {
            for (String mode : List.of("directional", "tiles", "top")) {
                Smoother.Result first = run(s, mode, opts, null), again = run(s, mode, opts.copy(), null);
                assertArrayEquals(first.blocks(), again.blocks(), s.name() + "/" + mode);
                assertEquals(first.palette(), again.palette(), s.name() + "/" + mode);
            }
        }
    }

    // ---------------------------------------------------------------------------------------------------------

    /** The golden scenes' inputs and three random hills. */
    private static List<GoldenScenes.Scene> scenes() throws IOException {
        List<GoldenScenes.Scene> out = new ArrayList<>();
        for (String name : GoldenScenes.NAMES) {
            out.add(GoldenScenes.scene(name));
        }
        for (long seed = 1; seed <= 3; seed++) {
            out.add(randomHills(seed));
        }
        return out;
    }

    /**
     * Hills in steps of two blocks, 32 x 32 and 24 high: stone, one to three blocks of dirt, and on top grass, or
     * stone in 8-block patches. Where a stone top at a step is cut to a vertical slab, the dirt under it shows, so
     * it takes the stone's colour.
     */
    private static GoldenScenes.Scene randomHills(long seed) {
        int h = 24, l = 32, w = 32;
        SplittableRandom rng = new SplittableRandom(seed);
        double[][] waves = new double[3][];
        for (int k = 0; k < waves.length; k++) {
            waves[k] = new double[] {1 + 2 * rng.nextDouble(), 0.6 * (rng.nextDouble() - 0.5), 0.6 * (rng.nextDouble() - 0.5),
                    2 * Math.PI * rng.nextDouble()};
        }
        int[] blocks = new int[h * l * w];
        for (int z = 0; z < l; z++) {
            for (int x = 0; x < w; x++) {
                double y = 10;
                for (double[] wave : waves) {
                    y += wave[0] * Math.sin(wave[1] * x + wave[2] * z + wave[3]);
                }
                int top = Math.clamp(2 * Math.round(y / 2), 4, h - 4);
                int dirt = 1 + rng.nextInt(3);
                boolean stoneTop = (x / 8 + z / 8) % 2 == 0;
                for (int yy = 0; yy <= top; yy++) {
                    blocks[(yy * l + z) * w + x] = yy == top ? (stoneTop ? 1 : 3) : yy >= top - dirt ? 2 : 1;
                }
            }
        }
        return new GoldenScenes.Scene("hills" + seed, h, l, w,
                List.of(AIR, "minecraft:stone", "minecraft:dirt", "minecraft:grass_block"), blocks);
    }

    /** Large ferns on a third of the tops that have room for them. */
    private static GoldenScenes.Scene withTallPlants(GoldenScenes.Scene s) {
        List<String> palette = new ArrayList<>(s.palette());
        int lower = palette.size(), upper = lower + 1;
        palette.add(PLANT_LOWER);
        palette.add(PLANT_UPPER);
        int[] blocks = s.blocks().clone(), tops = tops(s);
        int cols = s.length() * s.width();
        SplittableRandom rng = new SplittableRandom(11);
        for (int c = 0; c < cols; c++) {
            int top = tops[c];
            if (top >= 0 && top + 2 < s.height() && rng.nextInt(3) == 0
                    && table.kind3(s.palette().get(s.blocks()[top * cols + c])).category() == Table.TERRAIN
                    && AIR.equals(s.palette().get(s.blocks()[(top + 1) * cols + c]))
                    && AIR.equals(s.palette().get(s.blocks()[(top + 2) * cols + c]))) {
                blocks[(top + 1) * cols + c] = lower;
                blocks[(top + 2) * cols + c] = upper;
            }
        }
        return new GoldenScenes.Scene(s.name() + "+plants", s.height(), s.length(), s.width(), palette, blocks);
    }

    /** A poppy on every block of grass with air above it. */
    private static GoldenScenes.Scene withPoppiesOnGrass(GoldenScenes.Scene s) {
        List<String> palette = new ArrayList<>(s.palette());
        int poppy = palette.size(), grass = s.palette().indexOf("minecraft:grass_block");
        palette.add(POPPY);
        int[] blocks = s.blocks().clone();
        int cols = s.length() * s.width();
        for (int i = 0; i + cols < blocks.length; i++) {
            if (s.blocks()[i] == grass && AIR.equals(s.palette().get(s.blocks()[i + cols]))) {
                blocks[i + cols] = poppy;
            }
        }
        return new GoldenScenes.Scene(s.name() + "+poppies", s.height(), s.length(), s.width(), palette, blocks);
    }

    /** The blocks of LEFT_ALONE on a quarter of the tops: chests and water on them, logs and waterlogged stairs in
     *  their place, bedrock under them. */
    private static GoldenScenes.Scene withBlocksToLeaveAlone(GoldenScenes.Scene s) {
        List<String> palette = new ArrayList<>(s.palette());
        int first = palette.size();
        palette.addAll(LEFT_ALONE);
        int[] blocks = s.blocks().clone(), tops = tops(s);
        int cols = s.length() * s.width();
        int[] dy = {1, 0, 1, 0, -1}; // where each goes, from the top
        SplittableRandom rng = new SplittableRandom(13);
        for (int c = 0; c < cols; c++) {
            int top = tops[c], kind = rng.nextInt(LEFT_ALONE.size());
            if (top >= 1 && top + 1 < s.height() && rng.nextInt(4) == 0) {
                blocks[(top + dy[kind]) * cols + c] = first + kind;
            }
        }
        return new GoldenScenes.Scene(s.name() + "+others", s.height(), s.length(), s.width(), palette, blocks);
    }

    /** A chest on the top of every third column: on its highest block of ground with air above it. */
    private static GoldenScenes.Scene withChestsOnTops(GoldenScenes.Scene s) {
        List<String> palette = new ArrayList<>(s.palette());
        int chest = palette.size();
        palette.add(CHEST);
        int[] blocks = s.blocks().clone();
        int cols = s.length() * s.width();
        for (int c = 0; c < cols; c++) {
            if (!everyThird(s, c)) {
                continue;
            }
            for (int y = s.height() - 2; y >= 0; y--) {
                int i = y * cols + c;
                if (isGround(s, i) && AIR.equals(s.palette().get(s.blocks()[i + cols]))) {
                    blocks[i + cols] = chest;
                    break;
                }
            }
        }
        return new GoldenScenes.Scene(s.name() + "+chests", s.height(), s.length(), s.width(), palette, blocks);
    }

    /** A hanging lantern under every block of ground with air under it, in every third column. */
    private static GoldenScenes.Scene withLanternsUnder(GoldenScenes.Scene s) {
        List<String> palette = new ArrayList<>(s.palette());
        int lantern = palette.size();
        palette.add(LANTERN);
        int[] blocks = s.blocks().clone();
        int cols = s.length() * s.width();
        for (int c = 0; c < cols; c++) {
            if (!everyThird(s, c)) {
                continue;
            }
            for (int y = 1; y < s.height(); y++) {
                int i = y * cols + c;
                if (isGround(s, i) && AIR.equals(s.palette().get(s.blocks()[i - cols]))) {
                    blocks[i - cols] = lantern;
                }
            }
        }
        return new GoldenScenes.Scene(s.name() + "+lanterns", s.height(), s.length(), s.width(), palette, blocks);
    }

    /** A hanging lantern in place of the block under the top of every third column, so the top hangs over it. */
    private static GoldenScenes.Scene withLanternsUnderTops(GoldenScenes.Scene s) {
        List<String> palette = new ArrayList<>(s.palette());
        int lantern = palette.size();
        palette.add(LANTERN);
        int[] blocks = s.blocks().clone(), tops = tops(s);
        int cols = s.length() * s.width();
        for (int c = 0; c < cols; c++) {
            if (everyThird(s, c) && tops[c] >= 1) {
                blocks[(tops[c] - 1) * cols + c] = lantern;
            }
        }
        return new GoldenScenes.Scene(s.name() + "+lanterns", s.height(), s.length(), s.width(), palette, blocks);
    }

    /** One column in three, in a diagonal pattern. */
    private static boolean everyThird(GoldenScenes.Scene s, int column) {
        return (column % s.width() + 2 * (column / s.width())) % 3 == 0;
    }

    private static boolean isGround(GoldenScenes.Scene s, int i) {
        int category = table.kind3(s.palette().get(s.blocks()[i])).category();
        return category == Table.TERRAIN || category == Table.PIECE;
    }

    private static int count(int n, IntPredicate where) {
        int count = 0;
        for (int i = 0; i < n; i++) {
            count += where.test(i) ? 1 : 0;
        }
        return count;
    }

    /** Per column, the height of the highest block that is neither air nor a plant, or -1. */
    private static int[] tops(GoldenScenes.Scene s) {
        int cols = s.length() * s.width();
        int[] tops = new int[cols];
        for (int c = 0; c < cols; c++) {
            tops[c] = -1;
            for (int y = s.height() - 1; y >= 0; y--) {
                int category = table.kind3(s.palette().get(s.blocks()[y * cols + c])).category();
                if (category != Table.AIR && category != Table.DECOR) {
                    tops[c] = y;
                    break;
                }
            }
        }
        return tops;
    }

    /** A selection over the middle half of the area, from the median top there up to six blocks higher. */
    private static boolean[] box(GoldenScenes.Scene s) {
        int l = s.length(), w = s.width();
        int[] tops = tops(s);
        List<Integer> heights = new ArrayList<>();
        for (int z = l / 4; z < 3 * l / 4; z++) {
            for (int x = w / 4; x < 3 * w / 4; x++) {
                heights.add(tops[z * w + x]);
            }
        }
        Collections.sort(heights);
        int y0 = heights.get(heights.size() / 2);
        boolean[] inside = new boolean[s.blocks().length];
        for (int i = 0; i < inside.length; i++) {
            int x = i % w, z = (i / w) % l, y = i / (w * l);
            inside[i] = x >= w / 4 && x < 3 * w / 4 && z >= l / 4 && z < 3 * l / 4 && y >= y0 && y <= y0 + 6;
        }
        return inside;
    }

    /** A brush of radius 10 clicked on the top in the middle of the area. */
    private static boolean[] sphere(GoldenScenes.Scene s) {
        int l = s.length(), w = s.width(), cx = w / 2, cz = l / 2, cy = tops(s)[cz * w + cx];
        boolean[] inside = new boolean[s.blocks().length];
        for (int i = 0; i < inside.length; i++) {
            int x = i % w, z = (i / w) % l, y = i / (w * l);
            inside[i] = (x - cx) * (x - cx) + (y - cy) * (y - cy) + (z - cz) * (z - cz) <= 100;
        }
        return inside;
    }

    /** The tops-only brush: the columns within 10 of the middle of the area. */
    private static boolean[] disc(GoldenScenes.Scene s) {
        int l = s.length(), w = s.width(), cx = w / 2, cz = l / 2;
        boolean[] inside = new boolean[l * w];
        for (int c = 0; c < inside.length; c++) {
            int x = c % w, z = c / w;
            inside[c] = (x - cx) * (x - cx) + (z - cz) * (z - cz) <= 100;
        }
        return inside;
    }

    private static Smoother.Result run(GoldenScenes.Scene s, String mode, Options opts, boolean[] editable) {
        return switch (mode) {
            case "directional" -> Directional.smooth(s.blocks(), s.height(), s.length(), s.width(), s.palette(), table, opts,
                    editable, null);
            case "tiles" -> Tiles.smooth(s.blocks(), s.height(), s.length(), s.width(), s.palette(), table, opts, editable,
                    TILE, MARGIN, null);
            case "top" -> Smoother.smooth(s.blocks(), s.height(), s.length(), s.width(), s.palette(), table, opts, editable, null);
            default -> throw new IllegalArgumentException(mode);
        };
    }

    /** The blocks where says whose state the run changed, as "label x,y,z: before -> after". */
    private static List<String> changes(GoldenScenes.Scene s, Smoother.Result r, IntPredicate where, String label) {
        List<String> out = new ArrayList<>();
        int l = s.length(), w = s.width();
        for (int i = 0; i < s.blocks().length; i++) {
            if (!where.test(i)) {
                continue;
            }
            String before = BlockStates.normalize(s.palette().get(s.blocks()[i]));
            String after = BlockStates.normalize(r.palette().get(r.blocks()[i]));
            if (!before.equals(after)) {
                out.add(label + " " + i % w + "," + i / (w * l) + "," + (i / w) % l + ": " + before + " -> " + after);
            }
        }
        return out;
    }

    private static String first(List<String> lines) {
        return String.join("; ", lines.subList(0, Math.min(5, lines.size())));
    }
}
