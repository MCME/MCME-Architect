package com.mcmiddleearth.architect.slabSmooth;

import com.sk89q.worldedit.math.BlockVector3;
import org.bukkit.Chunk;
import org.bukkit.ChunkSnapshot;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;
import org.bukkit.plugin.Plugin;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;
import java.util.function.LongSupplier;

/**
 * Reads an area from chunk snapshots. Paper loads the chunks that aren't loaded in the background, and never generates
 * one: a chunk Paper hands over as null hasn't been generated, and the load fails. It doesn't ask isChunkGenerated
 * first, because on Paper that reads each unloaded chunk from disk on the main thread.
 * <p>
 * Everything a load does on the main thread happens in nextTick runs of about SLICE_NANOS each: asking Paper for the
 * chunks, holding each one with a plugin chunk ticket, and copying it. A big area spreads over several ticks instead of
 * stalling one. Every run holds all that Paper has handed over so far, however much of the run's time is used up,
 * because Paper keeps a chunk it has just loaded for only about a tick. Holding is quick next to copying. The tickets
 * stay until release, so writing the result doesn't load the chunks again.
 * <p>
 * Loads must not overlap. A plugin has one ticket per chunk, so the first release would let the other load's chunks go;
 * the jobs run one at a time.
 */
final class ChunkAreaSource implements AreaSource {

    /** How long one load spends on the main thread per run, give or take its last call. */
    static final long SLICE_NANOS = 10_000_000L;

    private final Plugin plugin;
    private final Executor nextTick;
    private final LongSupplier nanoClock;

    /**
     * @param nextTick  runs a task on the main thread in a later tick, such as the scheduler's runTask
     * @param nanoClock the clock the runs are timed on, such as System::nanoTime
     */
    ChunkAreaSource(Plugin plugin, Executor nextTick, LongSupplier nanoClock) {
        this.plugin = plugin;
        this.nextTick = nextTick;
        this.nanoClock = nanoClock;
    }

    @Override
    public CompletableFuture<LoadedArea> load(World world, BlockVector3 min, BlockVector3 max) {
        Load load = new Load(world, min, max);
        nextTick.execute(load);
        return load.result;
    }

    /** One area on its way in. Main thread only, apart from the futures Paper completes. */
    private final class Load implements Runnable {
        final CompletableFuture<LoadedArea> result = new CompletableFuture<>();
        private final World world;
        private final BlockVector3 min, max;
        private final int minY, maxY, cx0, cz0, chunksZ;
        /** Chunk i is chunk (cx0 + i / chunksZ, cz0 + i % chunksZ). */
        private final List<CompletableFuture<Chunk>> requested = new ArrayList<>();
        private final Chunk[] held;
        private final ChunkSnapshot[] copies;
        private final ArrayDeque<Integer> toCopy = new ArrayDeque<>();
        private int firstNotHeld, copied;
        private boolean released;

        Load(World world, BlockVector3 min, BlockVector3 max) {
            this.world = world;
            this.min = min;
            this.max = max;
            minY = world.getMinHeight();
            maxY = world.getMaxHeight() - 1;
            cx0 = min.x() >> 4;
            cz0 = min.z() >> 4;
            chunksZ = (max.z() >> 4) - cz0 + 1;
            int chunks = ((max.x() >> 4) - cx0 + 1) * chunksZ;
            held = new Chunk[chunks];
            copies = new ChunkSnapshot[chunks];
        }

        @Override
        public void run() {
            long start = nanoClock.getAsLong();
            try {
                if (result.isDone()) { // cancelled by whoever asked for it
                    release();
                    return;
                }
                while (requested.size() < held.length && nanoClock.getAsLong() - start < SLICE_NANOS) {
                    int i = requested.size();
                    requested.add(world.getChunkAtAsync(cx0 + i / chunksZ, cz0 + i % chunksZ, false));
                }
                hold(); // whatever the clock says
                while (!toCopy.isEmpty() && nanoClock.getAsLong() - start < SLICE_NANOS) {
                    int i = toCopy.poll();
                    copies[i] = held[i].getChunkSnapshot(false, false, false, false); // blocks only, no light
                    copied++;
                }
                if (copied < copies.length) {
                    nextTick.execute(this);
                } else if (!result.complete(new Snapshots(copies, cx0, cz0, chunksZ, min, max, minY, maxY, this::release))) {
                    release();
                }
            } catch (Throwable e) {
                try {
                    release();
                } finally {
                    result.completeExceptionally(e instanceof CompletionException && e.getCause() != null ? e.getCause() : e);
                }
            }
        }

        /** Holds every chunk Paper has handed over and this load hasn't held yet; throws for one that isn't generated. */
        private void hold() {
            for (int i = firstNotHeld; i < requested.size(); i++) {
                CompletableFuture<Chunk> request = requested.get(i);
                if (held[i] == null && request.isDone()) {
                    Chunk chunk = request.join();
                    if (chunk == null) {
                        throw new IllegalArgumentException("Part of the area (chunk " + (cx0 + i / chunksZ) + ", "
                                + (cz0 + i % chunksZ) + ") hasn't been generated; SlabSmooth only smooths existing terrain.");
                    }
                    chunk.addPluginChunkTicket(plugin);
                    held[i] = chunk;
                    toCopy.add(i);
                }
            }
            while (firstNotHeld < requested.size() && held[firstNotHeld] != null) {
                firstNotHeld++;
            }
        }

        /** Removes this load's tickets. A second call does nothing. */
        private void release() {
            if (released) {
                return;
            }
            released = true;
            for (Chunk chunk : held) {
                if (chunk != null) {
                    chunk.removePluginChunkTicket(plugin);
                }
            }
        }
    }

    /** The copied chunks of a finished load. */
    private static final class Snapshots implements LoadedArea {
        private final ChunkSnapshot[] chunks; // chunk (cx, cz) at (cx - cx0) * chunksZ + cz - cz0
        private final int cx0, cz0, chunksZ;
        private final BlockVector3 min, max;
        private final int minY, maxY;
        private final Runnable release;

        Snapshots(ChunkSnapshot[] chunks, int cx0, int cz0, int chunksZ, BlockVector3 min, BlockVector3 max, int minY,
                  int maxY, Runnable release) {
            this.chunks = chunks;
            this.cx0 = cx0;
            this.cz0 = cz0;
            this.chunksZ = chunksZ;
            this.min = min;
            this.max = max;
            this.minY = minY;
            this.maxY = maxY;
            this.release = release;
        }

        @Override
        public WorldArea read() {
            WorldArea a = WorldArea.box(min, max, minY, maxY);
            Map<BlockData, Integer> seen = new HashMap<>();
            int i = 0;
            for (int y = 0; y < a.height; y++) {
                int wy = a.min.y() + y;
                for (int z = 0; z < a.length; z++) {
                    int wz = a.min.z() + z;
                    for (int x = 0; x < a.width; x++) {
                        int wx = a.min.x() + x;
                        ChunkSnapshot chunk = chunks[((wx >> 4) - cx0) * chunksZ + (wz >> 4) - cz0];
                        a.blocks[i++] = seen.computeIfAbsent(chunk.getBlockData(wx & 15, wy, wz & 15), d -> a.id(d.getAsString()));
                    }
                }
            }
            return a;
        }

        @Override
        public void release() {
            release.run();
        }
    }
}
