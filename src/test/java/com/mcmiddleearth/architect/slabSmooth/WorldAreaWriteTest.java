package com.mcmiddleearth.architect.slabSmooth;

import com.mcmiddleearth.architect.slabSmooth.core.Smoother;
import com.sk89q.worldedit.math.BlockVector3;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * WorldArea.write on a 3 x 2 x 2 area (width x length x height) at 10, 64, -5, through a target that records what is
 * read and set. Blocks are listed in the area's order: x fastest, then z, then y.
 */
class WorldAreaWriteTest {

    private static final String STONE = "minecraft:stone", AIR = "minecraft:air", GRASS = "minecraft:grass_block[snowy=false]";
    private static final String SLAB = "minecraft:stone_slab[type=bottom,waterlogged=false]";
    private static final String STAIRS = "minecraft:stone_stairs[facing=east,half=bottom,shape=straight,waterlogged=false]";
    /** STAIRS with its properties in another order. */
    private static final String STAIRS_REORDERED = "minecraft:stone_stairs[waterlogged=false,shape=straight,half=bottom,facing=east]";
    private static final String PLANKS = "minecraft:oak_planks";

    private static final String[] BEFORE = {
            STONE, STONE, STONE, STONE, STONE, STONE, // y 64
            AIR, AIR, AIR, AIR, GRASS, AIR};          // y 65

    /** A target holding the world as it is now: the area as read, unless a test changes it. */
    private static final class Recording implements BlockTarget {
        final Map<BlockVector3, String> world = new HashMap<>();
        final List<BlockVector3> reads = new ArrayList<>();
        final Map<BlockVector3, String> sets = new LinkedHashMap<>();
        boolean mayRead = true, closed;

        Recording(WorldArea area) {
            for (int i = 0; i < area.blocks.length; i++) {
                world.put(at(i), area.palette.get(area.blocks[i]));
            }
        }

        @Override
        public String stateAt(int x, int y, int z) {
            if (!mayRead) {
                throw new AssertionError("a brush reads and writes through the same edit session, so it doesn't compare");
            }
            BlockVector3 position = BlockVector3.at(x, y, z);
            reads.add(position);
            return world.get(position);
        }

        @Override
        public void set(int x, int y, int z, String state) {
            sets.put(BlockVector3.at(x, y, z), state);
            world.put(BlockVector3.at(x, y, z), state);
        }

        @Override
        public void close() {
            closed = true;
        }
    }

    /** The world position of the area's block i. */
    private static BlockVector3 at(int i) {
        return BlockVector3.at(10 + i % 3, 64 + i / 6, -5 + (i / 3) % 2);
    }

    private static WorldArea area(String... states) {
        WorldArea area = WorldArea.box(BlockVector3.at(10, 64, -5), BlockVector3.at(12, 65, -4), -64, 319);
        for (int i = 0; i < states.length; i++) {
            area.blocks[i] = area.id(states[i]);
        }
        return area;
    }

    /** A smoothing result for the area: its palette extended with the new states, as the smoother's is. */
    private static Smoother.Result result(WorldArea area, String... states) {
        List<String> palette = new ArrayList<>(area.palette);
        int[] blocks = new int[states.length];
        for (int i = 0; i < states.length; i++) {
            if (!palette.contains(states[i])) {
                palette.add(states[i]);
            }
            blocks[i] = palette.indexOf(states[i]);
        }
        return new Smoother.Result(blocks, palette, null);
    }

    /** BEFORE with some blocks changed: pairs of a block's number and its new state. */
    private static String[] with(Object... changes) {
        String[] states = BEFORE.clone();
        for (int c = 0; c < changes.length; c += 2) {
            states[(int) changes[c]] = (String) changes[c + 1];
        }
        return states;
    }

    @Test
    void itWritesOnlyWhatChanged() throws Exception {
        WorldArea area = area(BEFORE);
        Recording target = new Recording(area);

        WorldArea.Written written = area.write(target, result(area, with(4, SLAB, 9, STAIRS)), true);

        assertEquals(new WorldArea.Written(2, 0), written);
        assertEquals(Map.of(BlockVector3.at(11, 64, -4), SLAB, BlockVector3.at(10, 65, -4), STAIRS), target.sets);
        assertEquals(List.of(BlockVector3.at(11, 64, -4), BlockVector3.at(10, 65, -4)), target.reads,
                "only the blocks it is about to set are read");
        assertFalse(target.closed, "closing is up to the caller");
    }

    @Test
    void aBlockChangedSinceTheReadIsLeftAloneAndCounted() throws Exception {
        WorldArea area = area(BEFORE);
        Recording target = new Recording(area);
        target.world.put(BlockVector3.at(11, 64, -4), PLANKS); // a builder placed it while the job ran

        WorldArea.Written written = area.write(target, result(area, with(1, SLAB, 4, SLAB, 9, STAIRS)), true);

        assertEquals(new WorldArea.Written(2, 1), written);
        assertEquals(Map.of(BlockVector3.at(11, 64, -5), SLAB, BlockVector3.at(10, 65, -4), STAIRS), target.sets);
        assertEquals(PLANKS, target.world.get(BlockVector3.at(11, 64, -4)));
    }

    @Test
    void theSameStateInAnotherOrderIsNotAChange() throws Exception {
        WorldArea area = area(with(7, STAIRS, 9, STAIRS));
        Recording target = new Recording(area);
        target.world.put(BlockVector3.at(10, 65, -4), STAIRS_REORDERED); // block 9, as WorldEdit might spell it

        WorldArea.Written written = area.write(target, result(area, with(7, STAIRS_REORDERED, 9, SLAB)), true);

        assertEquals(new WorldArea.Written(1, 0), written, "block 7 is the same stairs; block 9 hasn't changed");
        assertEquals(Map.of(BlockVector3.at(10, 65, -4), SLAB), target.sets);
        assertEquals(List.of(BlockVector3.at(10, 65, -4)), target.reads);
    }

    @Test
    void withoutCompareFirstNothingIsRead() throws Exception {
        WorldArea area = area(BEFORE);
        Recording target = new Recording(area);
        target.mayRead = false;
        target.world.put(BlockVector3.at(11, 64, -4), PLANKS);

        WorldArea.Written written = area.write(target, result(area, with(4, SLAB, 9, STAIRS)), false);

        assertEquals(new WorldArea.Written(2, 0), written);
        assertEquals(Map.of(BlockVector3.at(11, 64, -4), SLAB, BlockVector3.at(10, 65, -4), STAIRS), target.sets);
    }
}
