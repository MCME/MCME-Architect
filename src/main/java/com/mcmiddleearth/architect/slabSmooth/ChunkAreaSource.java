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
 * Reads an area from chunk snapshots. It never asks Paper to generate a chunk, and it refuses an area with any
 * ungenerated chunk before it holds anything. A load has two phases:
 * <ol>
 *   <li>It asks Paper for every chunk of the area, and holds none. Paper loads the ones that aren't loaded in the
 *       background, and hands over a chunk that hasn't been generated as null, which refuses the load. It doesn't ask
 *       isChunkGenerated, because on Paper that reads each unloaded chunk from disk on the main thread.</li>
 *   <li>Once every chunk has answered, it holds each one with a plugin chunk ticket, and copies it. Paper keeps a chunk
 *       it has just loaded for a while (delay-chunk-unloads-by, 10 seconds by default), so most are still loaded. One it
 *       has let go is asked for again and held when it arrives, because a ticket on a chunk that isn't loaded loads it
 *       from disk, on the main thread. One that isn't generated any more refuses the load too. The tickets stay until
 *       release, so writing the result doesn't load the chunks again.</li>
 * </ol>
 * Holding the area's chunks makes Paper load the land up to two chunks around them, and generate it where needed, as a
 * player standing there would. That is why nothing is held before the whole area has answered: held while the rest
 * was still asked for, chunks next to an ungenerated part of the area would have Paper generate it, and the load would
 * then read it. Next to ungenerated land around the area, Paper may still generate that ring.
 * <p>
 * Everything a load does on the main thread happens in nextTick runs of about SLICE_NANOS each: asking Paper for the
 * chunks, holding them, and copying them. A big area spreads over several ticks instead of stalling one. MAX_IN_FLIGHT
 * paces the load's own requests and holds: at most that many requests wait for Paper's answer, and a run holds at
 * most that many chunks, however much of its time is used up: holding a loaded chunk is quick next to copying it, and
 * Paper may let go of a chunk that isn't held yet. Each held chunk also makes Paper load the chunks up to two around
 * it.
 * <p>
 * Loads must not overlap. A plugin has one ticket per chunk, so the first release would let the other load's chunks go;
 * the jobs run one at a time.
 */
final class ChunkAreaSource implements AreaSource {

    /** How long one load spends on the main thread per run, give or take its last call. */
    static final long SLICE_NANOS = 10_000_000L;

    /**
     * The most requests of a load that wait for Paper's answer, and the most chunks one run holds. Paper loads a chunk
     * in the background but finishes it on the main thread: asked for a whole big area at once, it finished its chunks
     * in a few heavy ticks. A chunk held makes Paper load the land around it too.
     */
    static final int MAX_IN_FLIGHT = 16;

    private final Plugin plugin;
    private final Executor nextTick;
    private final LongSupplier nanoClock;

    /**
     * @param nextTick  runs a task on the main thread in a later tick, never the current one, as
     *                  {@link SlabSmooth#nextTick} does. A run that waits for chunks hands itself on to it, and Paper
     *                  hands chunks over only once a tick has ended: run again in the same tick, as Paper runs a task
     *                  given no delay, the load would never let its tick end.
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
        /** Chunk i is chunk (cx0 + i / chunksZ, cz0 + i % chunksZ). Its latest request; phase one adds them in order. */
        private final List<CompletableFuture<Chunk>> requested = new ArrayList<>();
        /** Whether chunk i's latest request waits: asked for, and its answer not looked at yet. */
        private final boolean[] unanswered;
        private final Chunk[] held;
        private final ChunkSnapshot[] copies;
        private final ArrayDeque<Integer> toCopy = new ArrayDeque<>();
        private int waiting; // how many requests wait, at most MAX_IN_FLIGHT
        private int firstNotAnswered, firstNotHeld, copied;
        private boolean allAnswered; // phase two: every chunk of the area has answered
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
            unanswered = new boolean[chunks];
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
                if (!allAnswered) {
                    ask(start);
                    allAnswered = requested.size() == held.length && waiting == 0;
                }
                if (allAnswered) {
                    hold(); // whatever the clock says
                    while (!toCopy.isEmpty() && nanoClock.getAsLong() - start < SLICE_NANOS) {
                        int i = toCopy.poll();
                        copies[i] = held[i].getChunkSnapshot(false, false, false, false); // blocks only, no light
                        copied++;
                    }
                }
                if (copied < copies.length) {
                    nextTick.execute(this);
                } else if (!result.complete(new Snapshots(copies, cx0, cz0, chunksZ, min, max, minY, maxY, this::release))) {
                    release();
                }
            } catch (Throwable e) {
                Throwable failure = e instanceof CompletionException && e.getCause() != null ? e.getCause() : e;
                try {
                    release();
                } catch (Throwable releasing) { // as in a world Paper has unloaded; nothing may reach the scheduler
                    if (releasing != failure) {
                        failure.addSuppressed(releasing);
                    }
                }
                result.completeExceptionally(failure);
            }
        }

        /**
         * Phase one: looks at what Paper has answered since the last run, and asks for more chunks while the run has
         * time and fewer than MAX_IN_FLIGHT wait. Holds none. Throws at the first chunk that isn't generated, or whose
         * load failed.
         */
        private void ask(long start) {
            for (int i = firstNotAnswered; i < requested.size(); i++) {
                if (unanswered[i] && requested.get(i).isDone()) {
                    answer(i);
                }
            }
            while (requested.size() < held.length && waiting < MAX_IN_FLIGHT
                    && nanoClock.getAsLong() - start < SLICE_NANOS) {
                int i = requested.size();
                requested.add(world.getChunkAtAsync(cx0 + i / chunksZ, cz0 + i % chunksZ, false));
                unanswered[i] = true;
                waiting++;
                if (requested.get(i).isDone()) { // as for a chunk Paper has loaded already
                    answer(i);
                }
            }
            while (firstNotAnswered < requested.size() && !unanswered[firstNotAnswered]) {
                firstNotAnswered++;
            }
        }

        /** Looks at the answer to chunk i's latest request: the chunk, or a throw if it isn't generated or failed. */
        private Chunk answer(int i) {
            unanswered[i] = false;
            waiting--;
            Chunk chunk = requested.get(i).join();
            if (chunk == null) {
                throw new Refusal("Part of the area (chunk " + (cx0 + i / chunksZ) + ", " + (cz0 + i % chunksZ)
                        + ") hasn't been generated; SlabSmooth only smooths existing terrain.");
            }
            return chunk;
        }

        /**
         * Phase two: holds up to MAX_IN_FLIGHT chunks that are loaded, the first ones in the area's order that this load
         * doesn't hold yet. A chunk Paper has let go is asked for again, with fewer than MAX_IN_FLIGHT waiting, and held
         * in a later run: a ticket on a chunk that isn't loaded loads it from disk at once. Throws for a chunk that isn't
         * generated any more, or whose load failed.
         */
        private void hold() {
            int holds = 0;
            for (int i = firstNotHeld; i < held.length && holds < MAX_IN_FLIGHT; i++) {
                if (held[i] != null || unanswered[i] && !requested.get(i).isDone()) {
                    continue;
                }
                Chunk chunk = unanswered[i] ? answer(i) : requested.get(i).join();
                int cx = cx0 + i / chunksZ, cz = cz0 + i % chunksZ;
                if (world.isChunkLoaded(cx, cz)) {
                    chunk.addPluginChunkTicket(plugin);
                    held[i] = chunk;
                    holds++;
                    toCopy.add(i);
                } else if (waiting < MAX_IN_FLIGHT) {
                    requested.set(i, world.getChunkAtAsync(cx, cz, false));
                    unanswered[i] = true;
                    waiting++;
                }
            }
            while (firstNotHeld < held.length && held[firstNotHeld] != null) {
                firstNotHeld++;
            }
        }

        /**
         * Removes this load's tickets, every one of them even if one can't be removed, as in a world Paper has unloaded.
         * Then the first failure is thrown, with any later ones kept with it, for the caller to log. A second call does
         * nothing.
         */
        private void release() {
            if (released) {
                return;
            }
            released = true;
            Throwable failed = null;
            for (Chunk chunk : held) {
                if (chunk != null) {
                    try {
                        chunk.removePluginChunkTicket(plugin);
                    } catch (RuntimeException | Error e) {
                        if (failed == null) {
                            failed = e;
                        } else if (failed != e) {
                            failed.addSuppressed(e);
                        }
                    }
                }
            }
            if (failed instanceof RuntimeException e) {
                throw e;
            }
            if (failed instanceof Error e) {
                throw e;
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
