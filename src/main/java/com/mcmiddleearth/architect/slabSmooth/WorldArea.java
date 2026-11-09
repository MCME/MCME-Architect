package com.mcmiddleearth.architect.slabSmooth;

import com.mcmiddleearth.architect.slabSmooth.core.BlockStates;
import com.mcmiddleearth.architect.slabSmooth.core.Directional;
import com.mcmiddleearth.architect.slabSmooth.core.Options;
import com.mcmiddleearth.architect.slabSmooth.core.Smoother;
import com.mcmiddleearth.architect.slabSmooth.core.Table;
import com.mcmiddleearth.architect.slabSmooth.core.Tiles;
import com.mcmiddleearth.architect.slabSmooth.core.WorkBudget;
import com.sk89q.worldedit.MaxChangedBlocksException;
import com.sk89q.worldedit.extent.Extent;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.world.block.BlockState;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** A box of the world as the smoother sees it: blocks [y][z][x] and their palette (block state strings). */
final class WorldArea {

    /** Tile size (blocks) for big areas in "all" mode. */
    static final int TILE = 64;

    interface CellTest {
        boolean test(int x, int y, int z);
    }

    /** What a write did: the blocks it set, and the blocks it left alone because they changed after the read. */
    record Written(int blocks, int skipped) {
    }

    final BlockVector3 min;
    final int height, length, width;
    final int[] blocks;
    final List<String> palette = new ArrayList<>();
    private final Map<String, Integer> ids = new HashMap<>();

    private WorldArea(BlockVector3 min, int height, int length, int width) {
        this.min = min;
        this.height = height;
        this.length = length;
        this.width = width;
        this.blocks = new int[height * length * width];
    }

    /** An empty area for min..max (inclusive), clamped to worldMinY..worldMaxY (inclusive). Fill it with {@link #id}. */
    static WorldArea box(BlockVector3 min, BlockVector3 max, int worldMinY, int worldMaxY) {
        int minY = Math.max(min.y(), worldMinY);
        int maxY = Math.min(max.y(), worldMaxY);
        return new WorldArea(BlockVector3.at(min.x(), minY, min.z()), Math.max(0, maxY - minY + 1),
                max.z() - min.z() + 1, max.x() - min.x() + 1);
    }

    /** The palette index of a state, added to the palette if it is new. */
    int id(String state) {
        return ids.computeIfAbsent(state, s -> {
            palette.add(s);
            return palette.size() - 1;
        });
    }

    /**
     * Reads min..max (inclusive) through an extent, clamped to the world's height. For brushes, on the thread WorldEdit
     * runs the brush on: the main thread on WorldEdit, one of FAWE's own threads on FAWE.
     */
    static WorldArea read(Extent extent, com.sk89q.worldedit.world.World world, BlockVector3 min, BlockVector3 max) {
        WorldArea area = box(min, max, world.getMinY(), world.getMaxY());
        Map<BlockState, Integer> seen = new HashMap<>();
        int i = 0;
        for (int y = 0; y < area.height; y++) {
            for (int z = 0; z < area.length; z++) {
                for (int x = 0; x < area.width; x++) {
                    BlockState state = extent.getBlock(area.min.add(x, y, z));
                    area.blocks[i++] = seen.computeIfAbsent(state, s -> area.id(s.getAsString()));
                }
            }
        }
        return area;
    }

    /** Per block: whether its world position passes the test. */
    boolean[] cells(CellTest test) {
        boolean[] out = new boolean[blocks.length];
        int i = 0;
        for (int y = 0; y < height; y++) {
            for (int z = 0; z < length; z++) {
                for (int x = 0; x < width; x++) {
                    out[i++] = test.test(min.x() + x, min.y() + y, min.z() + z);
                }
            }
        }
        return out;
    }

    /** Per column: whether its world column passes the test (y is the area's bottom). */
    boolean[] columns(CellTest test) {
        boolean[] out = new boolean[length * width];
        for (int z = 0; z < length; z++) {
            for (int x = 0; x < width; x++) {
                out[z * width + x] = test.test(min.x() + x, min.y(), min.z() + z);
            }
        }
        return out;
    }

    /**
     * Smooths the area; null if it is outside the world's height. Works on this area's own arrays only, so it can
     * run on any thread. Stops with TooMuchWork when the budget runs out, and nothing has changed then.
     *
     * @param editable per block in "all" mode, per column in "top" mode
     */
    Smoother.Result smooth(Table table, Options opts, boolean[] editable, boolean tiled, WorkBudget budget) {
        if (height == 0) {
            return null;
        }
        if ("top".equals(opts.surfaces)) {
            return Smoother.smooth(blocks, height, length, width, palette, table, opts, editable, null, budget);
        }
        if (tiled) {
            return Tiles.smooth(blocks, height, length, width, palette, table, opts, editable, TILE,
                    Tiles.margin(opts), null, budget);
        }
        return Directional.smooth(blocks, height, length, width, palette, table, opts, editable, null, budget);
    }

    /**
     * Writes the blocks the result changed, and nothing else. With compareFirst, each of them is read from the target
     * first, and one that is no longer what this area read (a builder changed it while the job ran) is left alone and
     * counted as skipped. A brush reads and writes through the same edit session in one go, so it doesn't compare.
     * States are compared as BlockStates.normalize gives them, because Bukkit and WorldEdit may list a state's
     * properties in different orders. Any thread the target allows.
     */
    Written write(BlockTarget target, Smoother.Result result, boolean compareFirst) throws MaxChangedBlocksException {
        int[] out = result.blocks();
        Map<String, String> normalized = new HashMap<>(); // one entry per distinct state: few, even in a big area
        int written = 0, skipped = 0;
        for (int i = 0; i < out.length; i++) {
            if (out[i] == blocks[i]) {
                continue;
            }
            String state = result.palette().get(out[i]);
            String old = normalized.computeIfAbsent(palette.get(blocks[i]), BlockStates::normalize);
            if (normalized.computeIfAbsent(state, BlockStates::normalize).equals(old)) {
                continue;
            }
            int x = min.x() + i % width, z = min.z() + (i / width) % length, y = min.y() + i / (width * length);
            if (compareFirst && !normalized.computeIfAbsent(target.stateAt(x, y, z), BlockStates::normalize).equals(old)) {
                skipped++;
                continue;
            }
            target.set(x, y, z, state);
            written++;
        }
        return new Written(written, skipped);
    }
}
