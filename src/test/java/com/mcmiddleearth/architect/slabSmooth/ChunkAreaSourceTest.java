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
import java.util.Set;
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
                    ticketsThisRun++;
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
    /** Answers to a chunk's next request that differ from the world's: a load Paper hasn't finished, or one that failed. */
    private final Map<Long, CompletableFuture<Chunk>> answers = new HashMap<>();
    /** When set, Paper answers a request that answers has nothing for only when the test calls paperHandsOver. */
    private boolean answersByHand;
    private final Map<String, BlockData> blockData = new HashMap<>();
    private final AtomicLong clock = new AtomicLong();
    private long requestCost, copyCost; // how far the clock moves per request and per copy

    private final ArrayDeque<Runnable> ticks = new ArrayDeque<>();
    private int ticksAllowed = Integer.MAX_VALUE; // how many more tasks the next tick takes
    private final IllegalStateException tickRefusal = new IllegalStateException("the plugin is disabled");
    private boolean inRun, askedWhetherGenerated;
    private int requests, callsThisRun, mostCallsInARun, ticketsThisRun, mostTicketsInARun;
    /** The chunks asked for that Paper hasn't answered yet, each with its request, and the most there were at once. */
    private final Map<FakeChunk, CompletableFuture<Chunk>> waiting = new HashMap<>();
    private int mostWaiting;
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
                CompletableFuture<Chunk> given = answers.remove(key); // for this request only
                CompletableFuture<Chunk> answer = given != null ? given : answersByHand ? new CompletableFuture<>()
                        : CompletableFuture.completedFuture(chunk.generated ? chunk.chunk : null);
                if (!answer.isDone()) { // a chunk Paper has to load first
                    waiting.put(chunk, answer);
                    mostWaiting = Math.max(mostWaiting, waiting.size());
                    answer.whenComplete((handedOver, failed) -> waiting.remove(chunk, answer));
                }
                yield answer;
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
        ticketsThisRun = 0;
        inRun = true;
        try {
            task.run();
        } finally {
            inRun = false;
        }
        mostCallsInARun = Math.max(mostCallsInARun, callsThisRun);
        mostTicketsInARun = Math.max(mostTicketsInARun, ticketsThisRun);
    }

    /** Between ticks: Paper hands over the chunk its waiting request is for, or null if it isn't generated. */
    private void paperHandsOver(FakeChunk chunk) {
        CompletableFuture<Chunk> request = waiting.get(chunk);
        assertTrue(request != null, "no request waits for " + chunk.chunk);
        request.complete(chunk.generated ? chunk.chunk : null);
    }

    /** Between ticks: Paper lets a chunk go, as it does a while after it has loaded one that nothing holds. */
    private void paperLetsGo(FakeChunk chunk) {
        assertEquals(chunk.removed, chunk.tickets, "Paper lets a chunk go only while it has no ticket: " + chunk.chunk);
        chunk.loaded = false;
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

    // A ticket makes Paper load the land up to two chunks around the chunk it holds, and generate it where needed. Holding
    // chunks while the rest of the area was still asked for, a load would have Paper generate an ungenerated part of the
    // area before it got there, and then smooth it.
    @Test
    void anUngeneratedChunkIsRefusedBeforeAnyChunkIsHeld() {
        List<FakeChunk> area = chunksIn(-4, 4, -4, 4); // the whole fake world
        assertTrue(area.size() > ChunkAreaSource.MAX_IN_FLIGHT, "more chunks than may wait at once");
        chunk(4, 4).generated = false; // the last chunk the load asks for

        CompletableFuture<AreaSource.LoadedArea> load = load(-64, 0, -64, 79, 0, 79);
        runTicks();

        Refusal refusal = assertInstanceOf(Refusal.class, failure(load));
        assertTrue(refusal.getMessage().contains("hasn't been generated"), refusal.getMessage());
        assertTrue(refusal.getMessage().contains("4, 4"), refusal.getMessage());
        for (FakeChunk chunk : area) {
            assertEquals(0, chunk.tickets, "no ticket was ever added: " + chunk.chunk);
        }
        assertEquals(area.size(), requests, "each chunk was asked for once");
    }

    // Paper may answer a chunk that isn't generated only in a later run, while other requests still wait. Its null
    // refuses the load in the run it arrives in, before any chunk is held, as a null Paper gives at once does.
    @Test
    void anUngeneratedChunkAnsweredInALaterRunIsRefusedBeforeAnyChunkIsHeld() {
        List<FakeChunk> area = chunksIn(-4, 4, -4, 4); // the whole fake world
        CompletableFuture<Chunk> stillLoading = new CompletableFuture<>();
        CompletableFuture<Chunk> notGenerated = new CompletableFuture<>();
        answers.put(key(4, 3), stillLoading); // Paper hasn't answered it by the end of the test
        answers.put(key(4, 4), notGenerated); // the last chunk the load asks for

        CompletableFuture<AreaSource.LoadedArea> load = load(-64, 0, -64, 79, 0, 79);
        runOneTick(); // asks for the whole area; all but (4, 3) and (4, 4) answer at once
        runOneTick(); // both still wait
        assertFalse(load.isDone(), "it waits for (4, 3) and (4, 4)");
        notGenerated.complete(null); // between ticks, Paper answers that (4, 4) isn't generated
        runOneTick();

        assertTrue(load.isDone(), "refused in the run the null arrived in");
        Refusal refusal = assertInstanceOf(Refusal.class, failure(load));
        assertTrue(refusal.getMessage().contains("hasn't been generated"), refusal.getMessage());
        assertTrue(refusal.getMessage().contains("4, 4"), refusal.getMessage());
        assertTrue(ticks.isEmpty(), "and no later run is asked for, though (4, 3) still waits");
        for (FakeChunk chunk : area) {
            assertEquals(0, chunk.tickets, "no ticket was ever added: " + chunk.chunk);
        }
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

    // Paper loads a chunk in the background but finishes it on the main thread. Asked for all of a big area's chunks at
    // once, it finished them in a few heavy ticks, up to 184 ms on the server. Each chunk held makes Paper load the land
    // around it as well, so one run holds no more chunks than may wait at once.
    @Test
    void noMoreThanSixteenChunksAreWaitingAtOnce() throws Exception {
        List<FakeChunk> area = chunksIn(-4, 4, -4, 4); // the whole fake world
        assertTrue(area.size() > ChunkAreaSource.MAX_IN_FLIGHT, "more chunks than may wait at once");
        // Paper lets these go before the load holds them: the first chunks it asks for, more than may wait at once, so
        // once the whole area has answered, the load has more chunks to ask for again than may wait.
        Set<FakeChunk> letGo = Set.copyOf(chunksIn(-4, -3, -4, 4));
        assertTrue(letGo.size() > ChunkAreaSource.MAX_IN_FLIGHT, "more chunks are let go than may wait at once");
        answersByHand = true;

        CompletableFuture<AreaSource.LoadedArea> load = load(-64, 0, -64, 79, 0, 79);
        for (int runs = 1; !load.isDone(); runs++) {
            assertTrue(runs <= 1_000, "it keeps asking for another tick");
            runOneTick();
            // Between ticks, Paper hands over five of the chunks that wait, not in the order they were asked for. Those
            // in letGo it lets go again at once, the first time it hands them over, as it does a chunk nothing holds.
            for (FakeChunk chunk : area.reversed().stream().filter(waiting::containsKey).limit(5).toList()) {
                paperHandsOver(chunk);
                if (letGo.contains(chunk) && chunk.asked == 1) {
                    paperLetsGo(chunk);
                }
            }
        }

        assertTrue(mostWaiting <= ChunkAreaSource.MAX_IN_FLIGHT, mostWaiting + " chunks were waiting at once");
        assertEquals(ChunkAreaSource.MAX_IN_FLIGHT, mostWaiting, "and the load used all of that room");
        assertTrue(mostTicketsInARun <= ChunkAreaSource.MAX_IN_FLIGHT, mostTicketsInARun + " chunks were held in one run");
        for (FakeChunk chunk : area) {
            assertEquals(letGo.contains(chunk) ? 2 : 1, chunk.asked,
                    (letGo.contains(chunk) ? "let go before it was held, so asked for again: " : "asked for once: ")
                            + chunk.chunk);
            assertEquals(1, chunk.tickets, chunk.chunk.toString());
            assertEquals(0, chunk.blockingLoads, "never held while it wasn't loaded: " + chunk.chunk);
            assertEquals(1, chunk.copies, chunk.chunk.toString());
        }
        assertEquals(List.of(), outsideRuns);
        WorldArea read = loaded(load).read();
        int i = 0;
        for (int z = -64; z <= 79; z++) {
            for (int x = -64; x <= 79; x++) {
                assertEquals(block(x, 0, z), read.palette.get(read.blocks[i++]));
            }
        }
    }

    // Phase two starts in the run in which the area's last answer arrives, and that run holds the area however much of
    // its time is used up.
    @Test
    void theRunInWhichTheLastAnswerArrivesHoldsTheArea() {
        requestCost = 6 * MS; // the requests use up each run's time
        copyCost = 6 * MS;
        CompletableFuture<Chunk> late = new CompletableFuture<>();
        answers.put(key(0, 0), late);

        load(0, 0, 0, 47, 63, 31);
        runOneTick();
        runOneTick();
        assertEquals(0, chunksIn(0, 2, 0, 1).stream().mapToInt(chunk -> chunk.tickets).sum(),
                "nothing is held before the whole area has answered");
        late.complete(chunk(0, 0).chunk);
        runOneTick();

        assertEquals(6, requests);
        for (FakeChunk chunk : chunksIn(0, 2, 0, 1)) {
            assertEquals(1, chunk.tickets, "held in the run in which the last answer arrived, its time used up: "
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

    // The chunks are held once the whole area has answered. Paper keeps a chunk it has just loaded for a while, so most
    // are still loaded then; one it has let go meanwhile is asked for again.
    @Test
    void phaseTwoHoldsTheChunksPaperKept() throws Exception {
        CompletableFuture<Chunk> late = new CompletableFuture<>();
        answers.put(key(1, 1), late); // the area's last chunk to answer

        CompletableFuture<AreaSource.LoadedArea> load = load(0, 0, 0, 31, 63, 31);
        runOneTick(); // asks for the whole area; Paper hands three of its chunks over at once
        late.complete(chunk(1, 1).chunk); // between ticks, Paper hands the last one over...
        paperLetsGo(chunk(0, 1)); // ...and has let (0, 1) go meanwhile
        runTicks();

        for (FakeChunk kept : List.of(chunk(0, 0), chunk(1, 0), chunk(1, 1))) {
            assertEquals(1, kept.asked, "still loaded, so held without being asked for again: " + kept.chunk);
        }
        assertEquals(2, chunk(0, 1).asked, "let go, so asked for again");
        for (FakeChunk chunk : chunksIn(0, 1, 0, 1)) {
            assertEquals(1, chunk.tickets, "held once: " + chunk.chunk);
            assertEquals(0, chunk.blockingLoads, "never held while it wasn't loaded: " + chunk.chunk);
            assertEquals(1, chunk.copies, "copied once: " + chunk.chunk);
        }
        assertEquals(List.of(), outsideRuns);
        WorldArea read = loaded(load).read();
        assertEquals(block(0, 0, 16), read.palette.get(read.blocks[16 * read.width]), "(0, 1) is read too");
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

    // Until the whole area has answered, a load holds nothing, so whatever ends it then has no ticket to remove.
    @ParameterizedTest
    @EnumSource(value = Failure.class, names = {"NOT_GENERATED", "LOAD_FAILED", "CANCELLED", "NEXT_TICK_REFUSED"})
    void aLoadThatEndsBeforeTheWholeAreaHasAnsweredHoldsNothing(Failure failure) {
        requestCost = 6 * MS; // two requests per run, so it ends part way through asking
        RuntimeException broken = new IllegalStateException("a broken region file");
        switch (failure) {
            case NOT_GENERATED -> chunk(1, 1).generated = false;
            case LOAD_FAILED -> answers.put(key(1, 1), CompletableFuture.failedFuture(broken));
            case NEXT_TICK_REFUSED -> ticksAllowed = 1; // the first run, then no more
            default -> {
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
            case LOAD_FAILED -> assertSame(broken, cause);
            case CANCELLED -> {
                assertInstanceOf(CancellationException.class, cause);
                assertEquals(2, requests, "it stops in the next run");
            }
            case NEXT_TICK_REFUSED -> assertSame(tickRefusal, cause);
            default -> fail(failure.name());
        }
        for (FakeChunk chunk : chunksIn(0, 1, 0, 1)) {
            assertEquals(0, chunk.tickets, "nothing is held before the whole area has answered: " + chunk.chunk);
        }
    }

    // Each failure comes once the whole area has answered and the load holds chunks. NOT_GENERATED is then a chunk that
    // Paper let go and that was gone when it was asked for again, as when it was deleted meanwhile.
    @ParameterizedTest
    @EnumSource(Failure.class)
    void aFailedLoadReleasesWhatItHeld(Failure failure) {
        requestCost = 6 * MS; // two calls per run, so it goes wrong part way through
        copyCost = 6 * MS;
        RuntimeException broken = new IllegalStateException("a broken region file");
        OutOfMemoryError memory = new OutOfMemoryError("test");
        CompletableFuture<Chunk> late = new CompletableFuture<>();
        answers.put(key(1, 1), late); // the area's last chunk to answer

        CompletableFuture<AreaSource.LoadedArea> load = load(0, 0, 0, 31, 63, 31);
        runOneTick();
        runOneTick(); // the whole area is asked for, and all but (1, 1) has answered
        late.complete(chunk(1, 1).chunk);
        switch (failure) {
            case NOT_GENERATED -> {
                paperLetsGo(chunk(1, 1));
                chunk(1, 1).generated = false;
            }
            case LOAD_FAILED -> {
                paperLetsGo(chunk(1, 1));
                answers.put(key(1, 1), CompletableFuture.failedFuture(broken));
            }
            case COPY_FAILED -> chunk(1, 1).copyFailure = broken;
            case COPY_OUT_OF_MEMORY -> chunk(1, 1).copyFailure = memory;
            case NEXT_TICK_REFUSED -> ticksAllowed = 0; // the run already waiting, which holds the chunks, then no more
            case CANCELLED -> {
            }
        }
        runOneTick(); // holds the chunks Paper still has
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
                assertEquals(2, chunksIn(0, 1, 0, 1).stream().mapToInt(chunk -> chunk.copies).sum(),
                        "it stops in the next run");
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
