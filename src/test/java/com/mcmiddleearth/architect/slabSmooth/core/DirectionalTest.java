package com.mcmiddleearth.architect.slabSmooth.core;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DirectionalTest {

    private static final String AIR = "minecraft:air", GRASS = "minecraft:grass_block", STONE = "minecraft:stone",
            CHEST = "minecraft:chest[facing=north,type=single,waterlogged=false]",
            LANTERN = "minecraft:lantern[hanging=true,waterlogged=false]";
    /** The small scenes below: W x H x L blocks (x, y, z), as indices into SCENE. */
    private static final int W = 14, H = 12, L = 9;
    private static final List<String> SCENE = List.of(AIR, GRASS, STONE, CHEST, LANTERN);

    /** octants.json: masks of the table's states (and derived top slabs and upside-down stairs), read from the
     *  RP-Human pack and the 26.2 client jar by the prototype's model probe. */
    @Test
    void everyPieceHasTheShapeOfItsModel() throws Exception {
        JsonObject probed;
        try (Reader r = new InputStreamReader(getClass().getResourceAsStream("/slabSmooth/octants.json"), StandardCharsets.UTF_8)) {
            probed = JsonParser.parseReader(r).getAsJsonObject();
        }
        Map<String, Integer> byState = new java.util.HashMap<>();
        probed.entrySet().forEach(e -> byState.put(BlockStates.normalize(e.getKey()), e.getValue().getAsInt()));
        Table table = TestTables.handMade();
        int checked = 0;
        for (Table.Material m : table.materials) {
            for (Map.Entry<Integer, String> e : m.shapes.entrySet()) {
                Integer expected = byState.get(BlockStates.normalize(e.getValue()));
                assertNotNull(expected, "not probed: " + e.getValue());
                assertEquals(expected.intValue(), e.getKey().intValue(), m.name + " " + e.getValue());
                assertEquals(e.getKey().intValue(), table.kind3(e.getValue()).mask(), "reads back: " + e.getValue());
                checked++;
            }
        }
        assertTrue(checked > 100, "checked " + checked);
    }

    @Test
    void framesPointOutOfTheirSurfaces() {
        int w = 5, h = 7, l = 6;
        for (int dir = 0; dir < 6; dir++) {
            Directional.Frame f = new Directional.Frame(dir, w, h, l);
            int wd = f.worldDirection();
            int step = Octants.DX[wd] + Octants.DY[wd] * w * l + Octants.DZ[wd] * w;
            assertEquals(step, f.world(1, 2, 1) - f.world(1, 1, 1), "block up, dir " + dir);
            // The frame's upper octant layer lies on the world side "up" points to.
            int upperFrame = 0xF0, upperWorld = f.toWorld(upperFrame);
            int expected = switch (wd) {
                case 0 -> 0xAA;  // east half
                case 1 -> 0x55;
                case 2 -> 0xCC;  // south half
                case 3 -> 0x33;
                case 4 -> 0xF0;
                default -> 0x0F;
            };
            assertEquals(expected, upperWorld, "octants up, dir " + dir);
            for (int m = 0; m < 256; m++) {
                assertEquals(m, f.toFrame(f.toWorld(m)));
            }
        }
    }

    /**
     * Brush-style run on the golden hillside, with large ferns on its grass: only blocks in the sphere change, in
     * every direction. Where the sphere ends between a fern's halves, the fern stays whole.
     */
    @Test
    void sphereLimitsChanges() throws Exception {
        GoldenScenes.Scene hillside = GoldenScenes.scene("hillside");
        int h = hillside.height(), l = hillside.length(), w = hillside.width();
        int[] blocks = hillside.blocks().clone();
        List<String> palette = new ArrayList<>(hillside.palette());
        // The brush clicked on the ground in the middle of the slope.
        int cx = w / 2, cz = l / 2, cy = surface(blocks, h, l, w, cx, cz);
        int grass = palette.indexOf("minecraft:grass_block"), lower = palette.size(), upper = lower + 1;
        palette.add("minecraft:large_fern[half=lower]");
        palette.add("minecraft:large_fern[half=upper]");
        for (int z = 0; z < l; z++) {
            for (int x = 0; x < w; x++) {
                int top = surface(blocks, h, l, w, x, z);
                if (blocks[(top * l + z) * w + x] == grass && top + 2 < h) {
                    blocks[((top + 1) * l + z) * w + x] = lower;
                    blocks[((top + 2) * l + z) * w + x] = upper;
                }
            }
        }
        boolean[] inside = new boolean[blocks.length];
        for (int i = 0; i < blocks.length; i++) {
            int x = i % w, z = (i / w) % l, y = i / (w * l);
            inside[i] = (x - cx) * (x - cx) + (y - cy) * (y - cy) + (z - cz) * (z - cz) <= 100;
        }
        Smoother.Result r = Directional.smooth(blocks, h, l, w, palette, TestTables.handMade(), new Options(), inside, null);
        assertTrue(r.report().changedColumns() > 0, "something changed");
        for (int i = 0; i < blocks.length; i++) {
            if (r.blocks()[i] != blocks[i] && !BlockStates.normalize(r.palette().get(r.blocks()[i]))
                    .equals(BlockStates.normalize(palette.get(blocks[i])))) {
                int x = i % w, z = (i / w) % l, y = i / (w * l);
                assertTrue(inside[i], "changed outside the sphere at " + x + "," + y + "," + z);
            }
        }
    }

    /**
     * A one-block crevice that zig-zags sideways and with height, in stone (which has every piece): nothing may
     * close it. Only a plain slab on its floor may go in; no stairs or vertical slabs reaching across.
     */
    @Test
    void tightCrevicesStayOpen() throws Exception {
        int w = 24, h = 26, l = 28;
        List<String> palette = List.of("minecraft:air", "minecraft:stone");
        int[] blocks = new int[w * h * l];
        int[] gap = new int[blocks.length];
        for (int y = 0; y < h; y++) {
            for (int z = 0; z < l; z++) {
                int cx = 11 + (int) Math.round(1.5 * Math.sin(z / 3.0) + 0.8 * Math.sin(y / 2.5));
                for (int x = 0; x < w; x++) {
                    int i = (y * l + z) * w + x;
                    boolean crevice = y >= 4 && y <= 21 && x == cx;
                    blocks[i] = y <= 21 && !crevice ? 1 : 0;
                    gap[i] = crevice ? 1 : 0;
                }
            }
        }
        Smoother.Result r = Directional.smooth(blocks, h, l, w, palette, TestTables.handMade(), new Options(), null, null);
        assertTrue(r.report().changedColumns() > 0);
        Table table = TestTables.handMade();
        for (int i = 0; i < blocks.length; i++) {
            if (gap[i] == 1 && r.blocks()[i] != 0) {
                Table.Kind3 k = table.kind3(r.palette().get(r.blocks()[i]));
                int below = i - w * l;
                assertEquals(Octants.SLAB, k.mask(), "only floor slabs in the crevice: " + r.palette().get(r.blocks()[i]));
                assertTrue(table.kind3(r.palette().get(r.blocks()[below])).mask() == Octants.FULL, "slab on solid ground");
            }
        }
    }

    /**
     * Seen in game: a chest on the edge of a two-block step of grass, with air west of the grass block it stands on.
     * The cell above that block isn't open, so the tops pass leaves the block alone; the west walls pass took it and
     * cut its top half, and the chest floated half a block. A block that holds up something built belongs to no
     * direction, so no pass changes it.
     */
    @Test
    void aBlockUnderABuiltBlockKeepsItsShapeAtACliffEdge() throws Exception {
        Table table = TestTables.handMade();
        int edge = at(5, 6, 4), above = at(5, 7, 4);
        int[] withChest = step();
        withChest[above] = SCENE.indexOf(CHEST);
        Smoother.Result r = smooth(withChest, table);
        assertEquals(GRASS, state(r, edge), "the block under the chest");
        assertEquals(CHEST, state(r, above));
        assertEquals(-1, owners(withChest, table)[edge], "the direction of the block under the chest");

        // Control: without the chest, the same block does change; the tops pass cuts it to a slab.
        assertNotEquals(GRASS, state(smooth(step(), table), edge), "the same block without the chest");
        // And the west walls pass, given the block as it was before this rule, cuts its top half: a side pass would
        // have cut it.
        Directional.World world = new Directional.World(withChest, H, L, W, SCENE, table);
        byte[] owner = Directional.owners(world, new Options());
        owner[edge] = Directional.WEST;
        new Directional.Pass(world, new Directional.Frame(Directional.WEST, W, H, L), new Options(), null, owner, false,
                WorkBudget.NONE).run(null, new Directional.Totals());
        assertEquals(Octants.SLAB, table.kind3(world.palette.get(world.blocks[edge])).mask(),
                "the block under the chest, after the west walls pass owning it");
    }

    /**
     * A lantern hanging under the front of an overhang, on a stone cliff that faces west. A wall pass can cut a
     * block's lower half (or its west half), which would leave the lantern hanging under air, so no wall pass gets a
     * block with something built under it. The tops pass still may.
     */
    @Test
    void aBlockOverABuiltBlockIsNotCutFromTheSide() throws Exception {
        Table table = TestTables.handMade();
        int front = at(3, 7, 4);
        byte[] thinFront = owners(overhang(true), table), thickFront = owners(overhang(false), table);
        assertAll(
                () -> assertEquals(Directional.UP, thinFront[front], "open above and west: the tops pass may have it"),
                () -> assertEquals(-1, thickFront[front], "open only to the west: no pass may have it"));
    }

    /**
     * A stone step two blocks down, with a row of lanterns hanging under its lip. Only the tops may have the lip, and
     * a vertical slab there would empty its west half down to its bottom, which holds the lanterns up at its centre.
     * In every path the lip keeps its four bottom octants, while its top may still be smoothed. The top-only smoother
     * sees the lip, a run of one block of ground, only with min_thickness 1, and in this scene it would rather make a
     * slab of it, so the shapes it may choose there are checked as well.
     */
    @Test
    void aBlockOverABuiltBlockKeepsItsBottomInEveryPath() throws Exception {
        Table table = TestTables.handMade();
        int[] blocks = lip();
        Options thin = new Options();
        thin.set("min_thickness", "1");
        Map<String, Smoother.Result> runs = Map.of(
                "directional", smooth(blocks, table),
                "tiles", Tiles.smooth(blocks, H, L, W, SCENE, table, new Options(), null, 4, Tiles.margin(new Options()), null),
                "top", Smoother.smooth(blocks, H, L, W, SCENE, table, thin, null, null));
        List<String> cut = new ArrayList<>();
        runs.forEach((path, r) -> {
            for (int z = 0; z < L; z++) {
                String lip = state(r, at(5, 6, z));
                if ((table.kind3(lip).mask() & Octants.SLAB) != Octants.SLAB) {
                    cut.add(path + " z " + z + ": " + lip);
                }
            }
        });
        Smoother.Terrain terrain = new Smoother.Terrain(blocks, H, L, W, SCENE, table, thin, null);
        Smoother.Problem problem = new Smoother.Problem(terrain, thin, WorkBudget.NONE);
        List<String> mayEmpty = new ArrayList<>();
        int free = 0;
        for (int z = 0; z < L; z++) {
            int c = z * W + 5;
            if (terrain.status[c] != Smoother.FREE) {
                continue; // at the area's border: kept, and given no energies
            }
            free++;
            for (int i = 0; i < problem.k; i++) {
                if (problem.configs.get(i).hasZero() && i != problem.currentChoice[c]
                        && problem.unary[c * problem.k + i] != Double.POSITIVE_INFINITY) {
                    mayEmpty.add("z " + z + ": " + Arrays.toString(problem.configs.get(i).d()));
                }
            }
        }
        int freeLips = free;
        assertAll(
                () -> assertTrue(cut.isEmpty(), cut.size() + " lips lost part of their bottom: " + cut),
                () -> assertTrue(freeLips > 0, "the top-only smoother sees the lip as a top it may change"),
                () -> assertTrue(mayEmpty.isEmpty(), "the top-only smoother may empty a quadrant of the lip: "
                        + mayEmpty.subList(0, Math.min(4, mayEmpty.size()))),
                () -> assertTrue(IntStream.range(0, L).anyMatch(z -> !STONE.equals(state(runs.get("directional"), at(5, 6, z)))),
                        "the lip's top is smoothed"));
    }

    /**
     * That overhang without the lantern, and a chest on its lower layer: on the thin front, or on the block behind it
     * in place of the overhang's upper block, where only the underside is open. The undersides pass may not have
     * either block: no pass changes a block with something built on it.
     */
    @Test
    void aBlockUnderABuiltBlockIsNotCutFromBelow() throws Exception {
        Table table = TestTables.handMade();
        int[] onTheFront = overhang(true), behindIt = overhang(true);
        onTheFront[at(3, 6, 4)] = SCENE.indexOf(AIR);
        onTheFront[at(3, 8, 4)] = SCENE.indexOf(CHEST);
        behindIt[at(3, 6, 4)] = SCENE.indexOf(AIR);
        behindIt[at(4, 8, 4)] = SCENE.indexOf(CHEST);
        byte[] front = owners(onTheFront, table), behind = owners(behindIt, table);
        assertAll(
                () -> assertEquals(-1, front[at(3, 7, 4)], "a chest on it, open below and to the west"),
                () -> assertEquals(-1, behind[at(4, 7, 4)], "a chest on it, open only below"));
    }

    /**
     * A selection inside a mountain: ground in the area's top layer, over a cave. What is above the area is unknown,
     * so it counts as nothing built, and nothing outside the area is read.
     */
    @Test
    void groundInTheTopLayerIsSmoothedWithoutReadingAboveTheArea() throws Exception {
        Table table = TestTables.handMade();
        int[] blocks = cave();
        assertEquals(SCENE.indexOf(STONE), blocks[at(5, H - 1, 4)], "ground in the top layer");
        assertDoesNotThrow(() -> owners(blocks, table));
        Smoother.Result r = assertDoesNotThrow(() -> smooth(blocks, table));
        assertTrue(r.report().changedColumns() > 0, "the cave floor's step is smoothed");
    }

    /**
     * Grass on a two-block step, the same at every z: west of x 5 its top is at y 4; from x 5 east it is at y 6 and
     * one block higher every two blocks. The step's edge is the grass block at x 5, y 6.
     */
    private static int[] step() {
        int[] blocks = new int[W * H * L];
        for (int z = 0; z < L; z++) {
            for (int x = 0; x < W; x++) {
                int top = x < 5 ? 4 : 6 + (x - 5) / 2;
                for (int y = 0; y <= top; y++) {
                    blocks[at(x, y, z)] = SCENE.indexOf(GRASS);
                }
            }
        }
        return blocks;
    }

    /**
     * Stone on a two-block step, the same at every z: west of x 5 its top is at y 4, from x 5 east at y 6. The lip,
     * at x 5 and y 6, has air above it and to its west, and a lantern hangs under it in place of the stone at y 5.
     */
    private static int[] lip() {
        int[] blocks = new int[W * H * L];
        for (int z = 0; z < L; z++) {
            for (int x = 0; x < W; x++) {
                for (int y = 0; y <= (x < 5 ? 4 : 6); y++) {
                    blocks[at(x, y, z)] = SCENE.indexOf(STONE);
                }
            }
            blocks[at(5, 5, z)] = SCENE.indexOf(LANTERN);
        }
        return blocks;
    }

    /**
     * Stone: a floor at y 0 to 2, a cliff facing west from x 6 east, up to y 9, and an overhang at y 7 and 8 that
     * reaches out to x 3, the same at every z. With a thin front, the overhang is one block thick at x 3 (y 7 only).
     * A lantern hangs under the overhang's front at (3, 6, 4).
     */
    private static int[] overhang(boolean thinFront) {
        int[] blocks = new int[W * H * L];
        for (int y = 0; y < H; y++) {
            for (int z = 0; z < L; z++) {
                for (int x = 0; x < W; x++) {
                    boolean stone = y <= 2 || x >= 6 && y <= 9
                            || x >= 3 && x < 6 && (y == 7 || y == 8 && (!thinFront || x > 3));
                    blocks[at(x, y, z)] = SCENE.indexOf(stone ? STONE : AIR);
                }
            }
        }
        blocks[at(3, 6, 4)] = SCENE.indexOf(LANTERN);
        return blocks;
    }

    /** Stone filling the whole area, its top layer included, around a cave at x 2 to 11, y 3 to 8 and z 1 to 7, whose
     *  floor steps up a block from x 7. */
    private static int[] cave() {
        int[] blocks = new int[W * H * L];
        for (int y = 0; y < H; y++) {
            for (int z = 0; z < L; z++) {
                for (int x = 0; x < W; x++) {
                    boolean air = x >= 2 && x <= 11 && y >= 3 && y <= 8 && z >= 1 && z <= 7 && !(x >= 7 && y == 3);
                    blocks[at(x, y, z)] = SCENE.indexOf(air ? AIR : STONE);
                }
            }
        }
        return blocks;
    }

    private static int at(int x, int y, int z) {
        return (y * L + z) * W + x;
    }

    private static Smoother.Result smooth(int[] blocks, Table table) {
        return Directional.smooth(blocks, H, L, W, SCENE, table, new Options(), null, null);
    }

    private static byte[] owners(int[] blocks, Table table) {
        return Directional.owners(new Directional.World(blocks, H, L, W, SCENE, table), new Options());
    }

    private static String state(Smoother.Result r, int i) {
        return r.palette().get(r.blocks()[i]);
    }

    private static int surface(int[] blocks, int h, int l, int w, int x, int z) {
        for (int y = h - 1; y >= 0; y--) {
            if (blocks[(y * l + z) * w + x] != 0) {
                return y;
            }
        }
        return 0;
    }
}
