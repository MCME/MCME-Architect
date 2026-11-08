package com.mcmiddleearth.architect.slabSmooth;

import com.sk89q.worldedit.math.BlockVector3;
import org.bukkit.Chunk;
import org.bukkit.ChunkSnapshot;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.lang.reflect.Proxy;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * ChunkAreaSource on fakes of World, Chunk, ChunkSnapshot and BlockData (proxies, so no server is needed), a queue
 * standing in for the scheduler's next tick, and a clock the fakes move. The fake world has every chunk from -4 to 4 on
 * both axes, and is 64 blocks high (y 0 to 63).
 */
class ChunkAreaSourceTest {

    private static final long MS = 1_000_000L;
    private static final int MAX_Y = 63;

    /** How a fake answers a method, by its name; Object's methods are answered for it. */
    private interface Answer {
        Object call(String method, Object[] args) throws Throwable;
    }

    private static <T> T fake(Class<T> type, String label, Answer answer) {
        return type.cast(Proxy.newProxyInstance(ChunkAreaSourceTest.class.getClassLoader(), new Class<?>[] {type},
                (proxy, method, args) -> switch (method.getName()) {
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    case "toString" -> label;
                    default -> answer.call(method.getName(), args == null ? new Object[0] : args);
                }));
    }

    private static AssertionError unexpected(String method) {
        return new AssertionError("unexpected call: " + method);
    }

    private static long key(int x, int z) {
        return (long) x << 32 | (z & 0xffffffffL);
    }

    /** What the fake world holds at a block: its position, so a test can tell where each block was read from. */
    private static String block(int x, int y, int z) {
        return "block " + x + " " + y + " " + z;
    }

    /** One chunk of the fake world, and what was asked of it. */
    private final class FakeChunk {
        final int x, z;
        final Chunk chunk;
        int asked, tickets, removed, copies, blockingLoads;
        boolean generated = true; // false: Paper hands over null for it
        boolean loaded = true; // false: Paper has let it go
        Throwable copyFailure; // thrown by its copy, if set
        Throwable removeFailure; // thrown when its ticket is removed, if set

        FakeChunk(int x, int z) {
            this.x = x;
            this.z = z;
            chunk = fake(Chunk.class, "chunk " + x + ", " + z, (method, args) -> switch (method) {
                case "getX" -> x;
                case "getZ" -> z;
                case "addPluginChunkTicket" -> {
                    assertSame(plugin, args[0]);
                    onlyInARun("a ticket");
                    if (!loaded) { // Paper's ticket call then loads the chunk at once, from disk, on the main thread
                        blockingLoads++;
                        loaded = true;
                    }
                    tickets++;
                    yield true;
                }
                case "removePluginChunkTicket" -> {
                    assertSame(plugin, args[0]);
                    removed++;
                    if (removeFailure != null) {
                        throw removeFailure;
                    }
                    yield true;
                }
                case "getChunkSnapshot" -> {
                    counted("a copy");
                    clock.addAndGet(copyCost);
                    copies++;
                    if (copyFailure != null) {
                        throw copyFailure;
                    }
                    yield snapshot();
                }
                default -> throw unexpected(method);
            });
        }

        private ChunkSnapshot snapshot() {
            return fake(ChunkSnapshot.class, "snapshot " + x + ", " + z, (method, args) -> switch (method) {
                case "getX" -> x;
                case "getZ" -> z;
                case "getBlockData" -> {
                    int lx = (int) args[0], y = (int) args[1], lz = (int) args[2];
                    assertTrue(lx >= 0 && lx < 16 && lz >= 0 && lz < 16 && y >= 0 && y <= MAX_Y, lx + " " + y + " " + lz);
                    yield blockData(block(x * 16 + lx, y, z * 16 + lz));
                }
                default -> throw unexpected(method);
            });
        }
    }

    private final Plugin plugin = fake(Plugin.class, "Architect", (method, args) -> {
        throw unexpected(method);
    });
    private final Map<Long, FakeChunk> chunks = new HashMap<>();
    /** Answers to a chunk's first request that differ from the world's: a load Paper hasn't finished, or one that failed. */
    private final Map<Long, CompletableFuture<Chunk>> answers = new HashMap<>();
    private final Map<String, BlockData> blockData = new HashMap<>();
    private final AtomicLong clock = new AtomicLong();
    private long requestCost, copyCost; // how far the clock moves per request and per copy

    private final ArrayDeque<Runnable> ticks = new ArrayDeque<>();
    private int ticksAllowed = Integer.MAX_VALUE; // how many more tasks the next tick takes
    private final IllegalStateException tickRefusal = new IllegalStateException("the plugin is disabled");
    private boolean inRun, askedWhetherGenerated;
    private int requests, callsThisRun, mostCallsInARun;
    private final List<String> outsideRuns = new ArrayList<>();
    private final World world;

    ChunkAreaSourceTest() {
        for (int x = -4; x <= 4; x++) {
            for (int z = -4; z <= 4; z++) {
                chunks.put(key(x, z), new FakeChunk(x, z));
            }
        }
        world = fake(World.class, "world", (method, args) -> switch (method) {
            case "getMinHeight" -> 0;
            case "getMaxHeight" -> MAX_Y + 1;
            case "isChunkLoaded" -> {
                assertEquals(2, args.length, "isChunkLoaded(x, z)");
                yield chunks.get(key((int) args[0], (int) args[1])).loaded;
            }
            case "isChunkGenerated" -> {
                askedWhetherGenerated = true;
                throw new AssertionError("isChunkGenerated waits for a chunk read on the main thread");
            }
            case "getChunkAtAsync" -> {
                assertEquals(3, args.length, "getChunkAtAsync(x, z, gen)");
                assertEquals(false, args[2], "chunks are never generated");
                counted("a request");
                clock.addAndGet(requestCost);
                requests++;
                long key = key((int) args[0], (int) args[1]);
                FakeChunk chunk = chunks.get(key);
                chunk.asked++;
                chunk.loaded = true; // by the time Paper hands it over
                CompletableFuture<Chunk> answer = answers.remove(key); // for the first request only
                yield answer != null ? answer : CompletableFuture.completedFuture(chunk.generated ? chunk.chunk : null);
            }
            default -> throw unexpected(method);
        });
    }

    private BlockData blockData(String state) {
        return blockData.computeIfAbsent(state, s -> fake(BlockData.class, s, (method, args) -> switch (method) {
            case "getAsString" -> s;
            default -> throw unexpected(method);
        }));
    }

    private FakeChunk chunk(int x, int z) {
        return chunks.get(key(x, z));
    }

    private List<FakeChunk> chunksIn(int x0, int x1, int z0, int z1) {
        List<FakeChunk> in = new ArrayList<>();
        for (int x = x0; x <= x1; x++) {
            for (int z = z0; z <= z1; z++) {
                in.add(chunk(x, z));
            }
        }
        return in;
    }

    /** A chunk call that takes main-thread time: counted per run, and only allowed in one. */
    private void counted(String what) {
        onlyInARun(what);
        callsThisRun++;
    }

    private void onlyInARun(String what) {
        if (!inRun) {
            outsideRuns.add(what);
        }
    }

    private ChunkAreaSource source() {
        return new ChunkAreaSource(plugin, task -> {
            if (ticksAllowed-- <= 0) {
                throw tickRefusal;
            }
            ticks.add(task);
        }, clock::get);
    }

    private CompletableFuture<AreaSource.LoadedArea> load(int x0, int y0, int z0, int x1, int y1, int z1) {
        return source().load(world, BlockVector3.at(x0, y0, z0), BlockVector3.at(x1, y1, z1));
    }

    /** Runs what waits for the next tick: one queued task. */
    private void runOneTick() {
        Runnable task = ticks.poll();
        assertTrue(task != null, "nothing waits for the next tick");
        callsThisRun = 0;
        inRun = true;
        try {
            task.run();
        } finally {
            inRun = false;
        }
        mostCallsInARun = Math.max(mostCallsInARun, callsThisRun);
    }

    /** Runs ticks until nothing waits for the next one. */
    private void runTicks() {
        for (int runs = 1; !ticks.isEmpty(); runs++) {
            assertTrue(runs <= 1_000, "it keeps asking for another tick");
            runOneTick();
        }
    }

    private static AreaSource.LoadedArea loaded(CompletableFuture<AreaSource.LoadedArea> load) throws Exception {
        assertTrue(load.isDone(), "the load has finished");
        return load.get();
    }

    private static Throwable failure(CompletableFuture<AreaSource.LoadedArea> load) {
        assertTrue(load.isDone(), "the load has finished");
        try {
            load.get();
        } catch (ExecutionException e) {
            return e.getCause();
        } catch (CancellationException e) {
            return e;
        } catch (InterruptedException e) {
            throw new AssertionError(e);
        }
        return fail("the load should have failed");
    }

    @Test
    void itNeverAsksWhetherAChunkIsGenerated() throws Exception {
        CompletableFuture<AreaSource.LoadedArea> load = load(0, 0, 0, 47, 63, 47);
        runTicks();

        assertFalse(askedWhetherGenerated);
        assertEquals(9, requests);
        loaded(load).read();
    }

    @Test
    void anUngeneratedChunkIsRefusedWithItsCoordinates() {
        chunk(1, -3).generated = false;

        CompletableFuture<AreaSource.LoadedArea> load = load(0, 0, -64, 47, 63, -1);
        runTicks();

        Refusal refusal = assertInstanceOf(Refusal.class, failure(load));
        assertTrue(refusal.getMessage().contains("hasn't been generated"), refusal.getMessage());
        assertTrue(refusal.getMessage().contains("1, -3"), refusal.getMessage());
    }

    @Test
    void requestingAndCopyingSpreadOverTicks() throws Exception {
        requestCost = 6 * MS;
        copyCost = 6 * MS;

        CompletableFuture<AreaSource.LoadedArea> load = load(0, 0, 0, 47, 63, 31);
        assertEquals(0, requests, "load itself asks Paper for nothing; that waits for the next tick");
        boolean[] finishedInARun = {false};
        load.whenComplete((area, error) -> finishedInARun[0] = inRun);
        runTicks();

        assertEquals(2, mostCallsInARun, "two calls of 6 ms fit in a run, and no more");
        assertEquals(6, requests);
        for (FakeChunk chunk : chunksIn(0, 2, 0, 1)) {
            assertEquals(1, chunk.copies, chunk.chunk.toString());
        }
        assertEquals(List.of(), outsideRuns, "every request, ticket and copy happens in a run");
        assertTrue(finishedInARun[0], "the future completes in a run, on the main thread");
        loaded(load).read();
    }

    @Test
    void aChunkIsHeldInTheFirstRunAfterPaperHandsItOver() {
        requestCost = 6 * MS; // the requests use up each run's time
        copyCost = 6 * MS;
        CompletableFuture<Chunk> late = new CompletableFuture<>();
        answers.put(key(0, 0), late);

        load(0, 0, 0, 47, 63, 31);
        runOneTick();
        runOneTick();
        late.complete(chunk(0, 0).chunk);
        runOneTick();

        assertEquals(6, requests);
        for (FakeChunk chunk : chunksIn(0, 2, 0, 1)) {
            assertEquals(1, chunk.tickets, "Paper lets a chunk it has just loaded go at the world's next ticket purge: "
                    + chunk.chunk);
        }
    }

    @Test
    void aChunkThatUnloadedBeforeItWasHeldIsAskedForAgain() throws Exception {
        CompletableFuture<Chunk> late = new CompletableFuture<>();
        answers.put(key(1, 1), late);

        CompletableFuture<AreaSource.LoadedArea> load = load(0, 0, 0, 31, 63, 31);
        runOneTick();
        late.complete(chunk(1, 1).chunk); // Paper hands it over after that run...
        chunk(1, 1).loaded = false;       // ...and lets it go before the next one
        runTicks();

        assertEquals(0, chunk(1, 1).blockingLoads, "a ticket on a chunk that isn't loaded loads it on the main thread");
        assertEquals(2, chunk(1, 1).asked, "asked for again");
        assertEquals(1, chunk(1, 1).tickets);
        assertEquals(List.of(), outsideRuns);
        loaded(load).read();
    }

    @Test
    void eachChunkIsHeldUntilRelease() throws Exception {
        CompletableFuture<Chunk> late = new CompletableFuture<>();
        answers.put(key(1, 1), late);

        CompletableFuture<AreaSource.LoadedArea> load = load(0, 0, 0, 31, 63, 31);
        runOneTick();
        runOneTick();
        assertFalse(load.isDone(), "it waits for Paper to hand over the last chunk");
        assertEquals(0, chunk(1, 1).tickets);
        late.complete(chunk(1, 1).chunk);
        runTicks();
        AreaSource.LoadedArea area = loaded(load);

        for (FakeChunk chunk : chunksIn(0, 1, 0, 1)) {
            assertEquals(1, chunk.tickets, chunk.chunk.toString());
            assertEquals(0, chunk.removed, chunk.chunk.toString());
        }
        assertEquals(List.of(), outsideRuns);
        area.release();
        for (FakeChunk chunk : chunksIn(0, 1, 0, 1)) {
            assertEquals(1, chunk.removed, chunk.chunk.toString());
        }
        area.release();
        for (FakeChunk chunk : chunksIn(0, 1, 0, 1)) {
            assertEquals(1, chunk.removed, "a second release removes nothing more");
        }
        assertEquals(0, chunk(2, 0).tickets, "nothing outside the area");
    }

    enum Failure { NOT_GENERATED, LOAD_FAILED, COPY_FAILED, COPY_OUT_OF_MEMORY, CANCELLED, NEXT_TICK_REFUSED }

    @ParameterizedTest
    @EnumSource(Failure.class)
    void aFailedLoadReleasesWhatItHeld(Failure failure) {
        requestCost = 6 * MS; // two calls per run, so it goes wrong part way through
        copyCost = 6 * MS;
        RuntimeException broken = new IllegalStateException("a broken region file");
        OutOfMemoryError memory = new OutOfMemoryError("test");
        switch (failure) {
            case NOT_GENERATED -> chunk(1, 1).generated = false;
            case LOAD_FAILED -> answers.put(key(1, 1), CompletableFuture.failedFuture(broken));
            case COPY_FAILED -> chunk(1, 1).copyFailure = broken;
            case COPY_OUT_OF_MEMORY -> chunk(1, 1).copyFailure = memory;
            case NEXT_TICK_REFUSED -> ticksAllowed = 1; // the first run, then no more
            case CANCELLED -> {
            }
        }

        CompletableFuture<AreaSource.LoadedArea> load = load(0, 0, 0, 31, 63, 31);
        runOneTick();
        if (failure == Failure.CANCELLED) {
            load.cancel(false);
        }
        runTicks();

        Throwable cause = failure(load);
        switch (failure) {
            case NOT_GENERATED -> assertInstanceOf(Refusal.class, cause);
            case LOAD_FAILED, COPY_FAILED -> assertSame(broken, cause);
            case COPY_OUT_OF_MEMORY -> assertSame(memory, cause);
            case CANCELLED -> {
                assertInstanceOf(CancellationException.class, cause);
                assertEquals(2, requests, "it stops in the next run");
            }
            case NEXT_TICK_REFUSED -> assertSame(tickRefusal, cause);
        }
        int held = 0;
        for (FakeChunk chunk : chunksIn(0, 1, 0, 1)) {
            assertEquals(chunk.tickets, chunk.removed, "every ticket is removed once: " + chunk.chunk);
            held += chunk.tickets;
        }
        assertTrue(held > 0, "it held some chunks before it went wrong");
    }

    // Removing a ticket can fail, as in a world Paper has unloaded: on a load that failed, that failure is kept with the
    // load's own, and nothing reaches the scheduler.
    @Test
    void aReleaseThatFailsAfterAFailedLoadEscapesNothing() {
        requestCost = 6 * MS; // two calls per run, so it goes wrong part way through
        copyCost = 6 * MS;
        RuntimeException broken = new IllegalStateException("a broken region file");
        RuntimeException gone = new IllegalStateException("test: the world is unloaded");
        chunk(1, 1).copyFailure = broken;
        chunksIn(0, 1, 0, 1).forEach(chunk -> chunk.removeFailure = gone);

        CompletableFuture<AreaSource.LoadedArea> load = load(0, 0, 0, 31, 63, 31);
        assertDoesNotThrow(this::runTicks, "nothing escapes a run");

        Throwable cause = failure(load);
        assertSame(broken, cause, "the load fails with what went wrong");
        assertEquals(List.of(gone), List.of(cause.getSuppressed()), "and the failed release is kept with it");
    }

    // One ticket that can't be removed mustn't keep the area's other chunks loaded for good: every chunk is let go, and
    // the failure is passed on afterwards, for the job to log.
    @Test
    void everyChunkIsLetGoWhenOneTicketCannotBeRemoved() throws Exception {
        RuntimeException gone = new IllegalStateException("test: that chunk's world is unloaded");
        chunk(0, 0).removeFailure = gone; // the first chunk the area holds
        CompletableFuture<AreaSource.LoadedArea> load = load(0, 0, 0, 31, 63, 31);
        runTicks();
        AreaSource.LoadedArea area = loaded(load);

        assertSame(gone, assertThrows(IllegalStateException.class, area::release), "passed on, for the job to log");
        for (FakeChunk chunk : chunksIn(0, 1, 0, 1)) {
            assertEquals(1, chunk.removed, "every chunk's ticket is removed, or its removal tried: " + chunk.chunk);
        }
        assertDoesNotThrow(area::release, "a second release does nothing");
        for (FakeChunk chunk : chunksIn(0, 1, 0, 1)) {
            assertEquals(1, chunk.removed, "nothing more is removed: " + chunk.chunk);
        }
    }

    @Test
    void aChunkArrivingAfterAFailureIsNotHeld() {
        CompletableFuture<Chunk> late = new CompletableFuture<>();
        answers.put(key(0, 0), late);
        chunk(1, 1).generated = false;

        CompletableFuture<AreaSource.LoadedArea> load = load(0, 0, 0, 31, 63, 31);
        runTicks();
        assertInstanceOf(Refusal.class, failure(load));
        late.complete(chunk(0, 0).chunk);
        runTicks();

        assertEquals(0, chunk(0, 0).tickets);
    }

    // ---------------------------------------------------------------------------------------------------------
    // On Paper, a task that a running task hands to runTask, with no delay, runs in the same tick, and Paper hands chunks
    // over only between ticks. FakeScheduler runs tasks as Paper does; MockBukkit runs every task a tick later.

    /** Paper hands each chunk of the area 0..31, 0..31 over only when the test runs what this returns, between ticks. */
    private List<Runnable> chunksArriveBetweenTicks() {
        List<Runnable> arrivals = new ArrayList<>();
        for (FakeChunk chunk : chunksIn(0, 1, 0, 1)) {
            CompletableFuture<Chunk> later = new CompletableFuture<>();
            answers.put(key(chunk.x, chunk.z), later);
            arrivals.add(() -> later.complete(chunk.chunk));
        }
        return arrivals;
    }

    // Handed on with no delay, as the wiring once did, a load waiting for its chunks runs again and again in its first
    // tick, and the chunks can only arrive once that tick ends: on the server, the watchdog stopped it.
    @Test
    void aLoadHandedOnWithNoDelayNeverLetsItsTickEnd() {
        FakeScheduler paper = new FakeScheduler();
        chunksArriveBetweenTicks();
        CompletableFuture<AreaSource.LoadedArea> load = new ChunkAreaSource(plugin,
                task -> paper.scheduler.runTask(plugin, task), clock::get)
                .load(world, BlockVector3.at(0, 0, 0), BlockVector3.at(31, 63, 31));

        assertThrows(FakeScheduler.TickNeverEnds.class, paper::tick);
        assertFalse(load.isDone(), "it was still waiting for the chunks");
    }

    @Test
    void withSlabSmoothsNextTickALoadRunsOnceATickUntilTheChunksArrive() throws Exception {
        FakeScheduler paper = new FakeScheduler();
        List<Runnable> arrivals = chunksArriveBetweenTicks();
        CompletableFuture<AreaSource.LoadedArea> load = new ChunkAreaSource(plugin,
                SlabSmooth.nextTick(plugin, paper.scheduler), clock::get)
                .load(world, BlockVector3.at(0, 0, 0), BlockVector3.at(31, 63, 31));

        assertEquals(1, paper.tick(), "one run, which asks for the chunks, and the tick ends");
        assertEquals(1, paper.tick(), "one run a tick while Paper reads them");
        assertFalse(load.isDone());
        arrivals.forEach(Runnable::run); // between ticks
        assertEquals(1, paper.tick(), "the next run holds and copies them");

        assertTrue(load.isDone());
        assertEquals(0, paper.waiting(), "nothing is left for a later tick");
        for (FakeChunk chunk : chunksIn(0, 1, 0, 1)) {
            assertEquals(1, chunk.tickets, chunk.chunk.toString());
        }
        loaded(load).read();
    }

    @Test
    void theAreaIsReadFromTheRightChunksWithinTheWorldsHeight() throws Exception {
        CompletableFuture<AreaSource.LoadedArea> load = load(-20, 60, -5, 5, 70, 12);
        runTicks();
        WorldArea area = loaded(load).read();

        assertEquals(BlockVector3.at(-20, 60, -5), area.min, "y 64 to 70 is above the world");
        assertEquals(4, area.height);
        assertEquals(18, area.length);
        assertEquals(26, area.width);
        int i = 0;
        for (int y = 60; y <= MAX_Y; y++) {
            for (int z = -5; z <= 12; z++) {
                for (int x = -20; x <= 5; x++) {
                    assertEquals(block(x, y, z), area.palette.get(area.blocks[i++]));
                }
            }
        }
    }
}
