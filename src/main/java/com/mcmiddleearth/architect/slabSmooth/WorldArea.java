package com.mcmiddleearth.architect.slabSmooth;

import com.mcmiddleearth.architect.slabSmooth.core.BlockStates;
import com.mcmiddleearth.architect.slabSmooth.core.Directional;
import com.mcmiddleearth.architect.slabSmooth.core.Options;
import com.mcmiddleearth.architect.slabSmooth.core.Smoother;
import com.mcmiddleearth.architect.slabSmooth.core.Table;
import com.mcmiddleearth.architect.slabSmooth.core.Tiles;
import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.MaxChangedBlocksException;
import com.sk89q.worldedit.extent.Extent;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.util.SideEffect;
import com.sk89q.worldedit.world.block.BlockState;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.ChunkSnapshot;
import org.bukkit.World;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;
import java.util.function.Function;

/** A box of the world as the smoother sees it: blocks [y][z][x] and their palette (block state strings). */
final class WorldArea {

    /** Tile size (blocks) for big areas in "all" mode. */
    static final int TILE = 64;

    interface CellTest {
        boolean test(int x, int y, int z);
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

    private static WorldArea box(BlockVector3 min, BlockVector3 max, int worldMinY, int worldMaxY) {
        int minY = Math.max(min.y(), worldMinY);
        int maxY = Math.min(max.y(), worldMaxY);
        return new WorldArea(BlockVector3.at(min.x(), minY, min.z()), Math.max(0, maxY - minY + 1),
                max.z() - min.z() + 1, max.x() - min.x() + 1);
    }

    private int id(String state) {
        return ids.computeIfAbsent(state, s -> {
            palette.add(s);
            return palette.size() - 1;
        });
    }

    /** Reads min..max (inclusive) through an extent, clamped to the world's height. Main thread; for brushes. */
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

    /**
     * Chunk snapshots covering min..max, read later on any thread with {@link Snapshots#read()}. Chunks that aren't
     * loaded are loaded by Paper in the background, and held with a plugin chunk ticket until copied; chunks are
     * never generated, so an area reaching into ungenerated land fails. The copy (quick, per chunk) is made on the
     * main thread, and the future completes there. Call on the main thread.
     */
    static CompletableFuture<Snapshots> snapshot(Plugin plugin, World world, BlockVector3 min, BlockVector3 max) {
        int cx0 = min.x() >> 4, cx1 = max.x() >> 4, cz0 = min.z() >> 4, cz1 = max.z() >> 4;
        for (int cx = cx0; cx <= cx1; cx++) {
            for (int cz = cz0; cz <= cz1; cz++) {
                if (!world.isChunkGenerated(cx, cz)) {
                    return CompletableFuture.failedFuture(new IllegalArgumentException("Part of the area (chunk " + cx + ", " + cz
                            + ") hasn't been generated; SlabSmooth only smooths existing terrain."));
                }
            }
        }
        List<CompletableFuture<Chunk>> loads = new ArrayList<>();
        for (int cx = cx0; cx <= cx1; cx++) {
            for (int cz = cz0; cz <= cz1; cz++) {
                int x = cx, z = cz;
                loads.add(world.getChunkAtAsync(cx, cz, false).thenApply(c -> {
                    if (c == null) {
                        throw new IllegalArgumentException("Chunk " + x + ", " + z + " couldn't be loaded.");
                    }
                    c.addPluginChunkTicket(plugin); // keeps it loaded until the copy below
                    return c;
                }));
            }
        }
        Executor mainThread = task -> {
            if (Bukkit.isPrimaryThread()) {
                task.run();
            } else {
                Bukkit.getScheduler().runTask(plugin, task);
            }
        };
        return CompletableFuture.allOf(loads.toArray(CompletableFuture[]::new)).handleAsync((ignored, error) -> {
            try {
                if (error != null) {
                    throw error instanceof CompletionException ce && ce.getCause() != null ? ce.getCause() : error;
                }
                long start = System.nanoTime();
                ChunkSnapshot[][] chunks = new ChunkSnapshot[cx1 - cx0 + 1][cz1 - cz0 + 1];
                for (CompletableFuture<Chunk> load : loads) {
                    Chunk c = load.join();
                    chunks[c.getX() - cx0][c.getZ() - cz0] = c.getChunkSnapshot(false, false, false);
                }
                return new Snapshots(chunks, cx0, cz0, box(min, max, world.getMinHeight(), world.getMaxHeight() - 1),
                        (System.nanoTime() - start) / 1_000_000, () -> release(plugin, loads));
            } catch (Throwable e) {
                release(plugin, loads);
                throw e instanceof RuntimeException re ? re : new CompletionException(e);
            }
        }, mainThread);
    }

    private static void release(Plugin plugin, List<CompletableFuture<Chunk>> loads) {
        for (CompletableFuture<Chunk> load : loads) {
            Chunk c = load.getNow(null);
            if (c != null) {
                c.removePluginChunkTicket(plugin);
            }
        }
    }

    /**
     * copyMs: how long the copy took on the main thread. The chunks stay loaded (so writing the result back doesn't
     * load them again on the main thread) until {@code release} runs, on the main thread.
     */
    record Snapshots(ChunkSnapshot[][] chunks, int cx0, int cz0, WorldArea area, long copyMs, Runnable release) {
        /** Fills the area from the snapshots. Any thread. */
        WorldArea read() {
            WorldArea a = area;
            Map<org.bukkit.block.data.BlockData, Integer> seen = new HashMap<>();
            int i = 0;
            for (int y = 0; y < a.height; y++) {
                int wy = a.min.y() + y;
                for (int z = 0; z < a.length; z++) {
                    int wz = a.min.z() + z;
                    for (int x = 0; x < a.width; x++) {
                        int wx = a.min.x() + x;
                        ChunkSnapshot chunk = chunks[(wx >> 4) - cx0][(wz >> 4) - cz0];
                        org.bukkit.block.data.BlockData data = chunk.getBlockData(wx & 15, wy, wz & 15);
                        a.blocks[i++] = seen.computeIfAbsent(data, d -> a.id(d.getAsString()));
                    }
                }
            }
            return a;
        }
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
     * run on any thread.
     *
     * @param editable per block in "all" mode, per column in "top" mode
     */
    Smoother.Result smooth(Table table, Options opts, boolean[] editable, boolean tiled) {
        if (height == 0) {
            return null;
        }
        if ("top".equals(opts.surfaces)) {
            return Smoother.smooth(blocks, height, length, width, palette, table, opts, editable, null);
        }
        if (tiled) {
            return Tiles.smooth(blocks, height, length, width, palette, table, opts, editable, TILE,
                    SlabSmoothPlugin.margin(opts), null);
        }
        return Directional.smooth(blocks, height, length, width, palette, table, opts, editable, null);
    }

    /**
     * Writes the blocks that changed, through the edit session (so masks, limits and //undo apply), without block
     * updates (like /setblock ... strict): stairs keep the shape the smoother chose, pieces that need support
     * (MCME's sugar cane grass slab) stay put, and MCME's repeater and comparator slabs don't switch off a tick
     * later and reshape their neighbours. Main thread. Returns the number of blocks set.
     */
    int write(EditSession session, Smoother.Result result, Function<String, BlockState> parse) throws MaxChangedBlocksException {
        session.setSideEffectApplier(session.getSideEffectApplier()
                .with(SideEffect.NEIGHBORS, SideEffect.State.OFF)
                .with(SideEffect.UPDATE, SideEffect.State.OFF));
        int[] out = result.blocks();
        Map<Long, Boolean> same = new HashMap<>();
        int changed = 0;
        for (int i = 0; i < out.length; i++) {
            if (out[i] == blocks[i]) {
                continue;
            }
            String state = result.palette().get(out[i]);
            String old = palette.get(blocks[i]);
            if (same.computeIfAbsent((long) out[i] << 32 | blocks[i], k -> BlockStates.normalize(state).equals(BlockStates.normalize(old)))) {
                continue;
            }
            int x = i % width, z = (i / width) % length, y = i / (width * length);
            session.setBlock(min.add(x, y, z), parse.apply(state));
            changed++;
        }
        return changed;
    }
}
