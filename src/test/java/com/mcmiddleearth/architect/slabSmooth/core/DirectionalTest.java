package com.mcmiddleearth.architect.slabSmooth.core;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DirectionalTest {

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

    /** Brush-style run on the user's terrain: only blocks in the sphere change, in every direction. */
    @Test
    void sphereLimitsChanges() throws Exception {
        var fx = TestTables.fixture("toti");
        int h = fx.get("height").getAsInt(), l = fx.get("length").getAsInt(), w = fx.get("width").getAsInt();
        List<String> palette = new ArrayList<>();
        fx.getAsJsonArray("palette").forEach(e -> palette.add(e.getAsString()));
        int[] blocks = new int[fx.getAsJsonArray("blocks").size()];
        for (int i = 0; i < blocks.length; i++) {
            blocks[i] = fx.getAsJsonArray("blocks").get(i).getAsInt();
        }
        // A spot on a cliff face (found from the data: the steepest column step near the middle).
        int cx = 48, cz = 48, cy = surface(blocks, h, l, w, cx, cz) - 3;
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
                // The grass rule may recolour the block just under a changed top, and tall plants lose their top.
                boolean nextToInside = inside[i] || (y > 0 && inside[i - w * l]) || (y + 1 < h && inside[i + w * l])
                        || (y > 1 && inside[i - 2 * w * l]);
                assertTrue(nextToInside, "changed outside the sphere at " + x + "," + y + "," + z);
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

    private static int surface(int[] blocks, int h, int l, int w, int x, int z) {
        for (int y = h - 1; y >= 0; y--) {
            if (blocks[(y * l + z) * w + x] != 0) {
                return y;
            }
        }
        return 0;
    }
}
