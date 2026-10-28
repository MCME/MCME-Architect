package com.mcmiddleearth.architect.mapLayers.customBiomes;

import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.world.WorldMock;

import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;

import static org.junit.jupiter.api.Assertions.*;

// The first fill reads a world's saved region files on its own thread, one world at a time, and hands what it read to
// the main thread file by file. The region files are copies of resources/customBiomes/r.-1.-1.mca, the chunks x -6 to
// -1 and z -6 to -1 of the 26.2 test world, all painted with cbc:111g380-5p and partly with mcme:custom_003; as
// r.0.0.mca the same chunks lie one region further on, at x and z 26 to 31. A fill that must not get further than its
// first file is held by a Sleeper that blocks: at 0.1 MB/s the fill sleeps after its first file.
class PaintScanTest {

    private static final String CBC = "cbc:111g380-5p";
    private static final String QUAY = "mcme:custom_003";
    private static final Set<String> IDS = Set.of(CBC, QUAY);
    private static final String FIRST = "r.-1.-1.mca";
    private static final String SECOND = "r.0.0.mca";
    private static final Duration WAIT = Duration.ofSeconds(30);
    private static final PaintScan.Sleeper NO_SLEEP = millis -> {
    };

    @TempDir
    Path dir;

    private ServerMock server;
    private Plugin plugin;
    private WorldMock world;
    private PaintStore store;
    private PaintScan scan;
    private final List<String> changes = new ArrayList<>();
    /** The chunks handed to the live reads, in order. */
    private final List<Handed> handed = new ArrayList<>();
    private final Logged logged = new Logged();

    /** A chunk the fill handed to the live reads. */
    private record Handed(String world, int x, int z) {}

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin();
        world = server.addSimpleWorld("world");
        plugin.getLogger().setLevel(Level.ALL);
        plugin.getLogger().addHandler(logged);
        store = new PaintStore(dir.resolve("records"), plugin.getLogger(), Runnable::run);
    }

    @AfterEach
    void tearDown() {
        if (scan != null) {
            scan.stop();
        }
        MockBukkit.unmock();
    }

    private PaintScan scan(double megabytesPerSecond, PaintScan.Sleeper sleeper) {
        return new PaintScan(plugin, store, w -> regionFolder(w.getName()), () -> IDS, changes::add,
                (worldName, x, z) -> handed.add(new Handed(worldName, x, z)), megabytesPerSecond, sleeper);
    }

    private Path regionFolder(String worldName) {
        return dir.resolve(worldName).resolve("region");
    }

    /** Copies the fixture into the world's region folder under each name. */
    private void regionFiles(String worldName, String... names) throws IOException {
        Path folder = Files.createDirectories(regionFolder(worldName));
        for (String name : names) {
            Files.copy(fixture(), folder.resolve(name), StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static Path fixture() throws IOException {
        URL url = PaintScanTest.class.getResource("/customBiomes/r.-1.-1.mca");
        assertNotNull(url, "the fixture is a test resource");
        try {
            return Path.of(url.toURI());
        } catch (URISyntaxException e) {
            throw new IOException(e);
        }
    }

    /** The fixture's painted chunks as the reader gives them, moved on by whole regions as a copy of it lies. */
    private static Map<Long, ChunkPaint> fixturePaints(int regionsX, int regionsZ) throws IOException {
        Map<Long, ChunkPaint> paints = new HashMap<>();
        new RegionFileReader(IDS).read(fixture(), (x, z, paint) -> {
            if (!paint.isEmpty()) {
                paints.put(PaintRecord.chunkKey(x + 32 * regionsX, z + 32 * regionsZ), paint);
            }
        });
        return paints;
    }

    private static ChunkPaint paint(String biome) {
        return ChunkPaint.of(Collections.nCopies(16, List.of(new BiomeSpan(biome, -64, 319))));
    }

    private void ticks(int count) {
        server.getScheduler().performTicks(count);
    }

    private void awaitIdle() {
        assertTrue(scan.awaitIdle(WAIT), "the scan finished its work");
    }

    @Test
    void aFillReadsEveryRegionFileAndMarksTheWorldDone() throws IOException {
        regionFiles("world", FIRST, SECOND, "r.1.1.mca.bak");
        PaintRecord record = store.turnOn("world", key -> false);
        scan = scan(20, NO_SLEEP);

        assertEquals(PaintScan.Start.STARTED, scan.start(world));
        awaitIdle();
        ticks(1);

        Map<Long, ChunkPaint> expected = fixturePaints(0, 0);
        expected.putAll(fixturePaints(1, 1));
        assertEquals(expected, record.chunks());
        assertEquals(Set.of(CBC, QUAY), record.get(-3, -4).biomes());
        assertTrue(record.fillDone());
        assertEquals(Set.of(FIRST, SECOND), record.finishedFiles(), "only files named r.X.Z.mca are read");
        assertEquals(List.of("world", "world"), changes, "the map hears of each file");
        assertEquals(new PaintScan.Status(false, false, 0, 0), scan.status("world"));
        List<String> told = logged.messages(Level.INFO);
        assertEquals(1, told.size(), told.toString());
        assertTrue(told.get(0).endsWith("Custom biomes layer: read world's saved files: 72 chunks, 72 with custom "
                + "biomes."), told.get(0));
    }

    // The end of a fill comes after the pace that follows its last file, so a save may fall in between. The end
    // changes the record as well, and the next save must say the world is done.
    @Test
    void aFinishedFillIsSavedAsDone() throws IOException {
        regionFiles("world", FIRST);
        store.turnOn("world", key -> false);
        Gate gate = new Gate(false);
        scan = scan(0.1, gate);
        scan.start(world);
        gate.awaitReached();
        ticks(1);
        store.saveDirty();
        Path saved = dir.resolve("records").resolve("world.dat");
        assertFalse(PaintRecord.load(saved).fillDone());

        gate.release();
        awaitIdle();
        ticks(1);
        store.saveDirty();

        assertTrue(PaintRecord.load(saved).fillDone());
        assertEquals(fixturePaints(0, 0), PaintRecord.load(saved).chunks());
    }

    // A loaded chunk may be newer than its saved copy, so the fill hands it to the live reads, which read it soon, or as
    // it unloads. Until then the record keeps what it holds for it: a live read's paint stays, and a chunk the record
    // holds nothing for takes its saved copy, so it is not missing should the live read never come.
    @Test
    void aChunkLoadedAtMergeIsHandedToTheLiveReadsAndKeepsItsDiskCopyUntilThen() throws IOException {
        regionFiles("world", FIRST);
        world.getChunkAt(-3, -4);
        world.getChunkAt(-1, -1);
        PaintRecord record = store.turnOn("world", key -> false);
        ChunkPaint live = paint("cbc:live");
        record.put(-3, -4, live);
        scan = scan(20, NO_SLEEP);

        scan.start(world);
        awaitIdle();
        ticks(1);

        assertEquals(live, record.get(-3, -4), "what the live check read stays");
        Map<Long, ChunkPaint> expected = fixturePaints(0, 0);
        assertEquals(expected.get(PaintRecord.chunkKey(-1, -1)), record.get(-1, -1),
                "a loaded chunk the record held nothing for has its saved copy");
        expected.put(PaintRecord.chunkKey(-3, -4), live);
        assertEquals(expected, record.chunks());
        assertEquals(Set.of(new Handed("world", -3, -4), new Handed("world", -1, -1)), Set.copyOf(handed),
                "both loaded chunks are read live");
        assertEquals(2, handed.size(), "and only they: " + handed);
        assertTrue(record.fillDone());
    }

    @Test
    void aStoppedFillResumesWhereItLeftOff() throws IOException {
        regionFiles("world", FIRST, SECOND);
        PaintRecord record = store.turnOn("world", key -> false);
        Gate gate = new Gate(false);
        scan = scan(0.1, gate);
        scan.start(world);
        gate.awaitReached(); // the first file is read and handed over, and the fill waits out its pace
        ticks(1);

        scan.stop();
        ticks(1);
        assertEquals(Set.of(FIRST), record.finishedFiles());
        assertFalse(record.fillDone());
        record.clearExcept(key -> false); // so that a second read of the first file would show

        AtomicInteger sleeps = new AtomicInteger();
        scan = scan(0.1, millis -> sleeps.incrementAndGet());
        scan.resumeUnfinished();
        awaitIdle();
        assertEquals(new PaintScan.Status(true, false, 2, 2), scan.status("world"),
                "the file read before the stop counts as done");
        ticks(1);

        assertEquals(fixturePaints(1, 1), record.chunks(), "only the second file is read");
        assertEquals(1, sleeps.get(), "and paced, once");
        assertEquals(Set.of(FIRST, SECOND), record.finishedFiles());
        assertTrue(record.fillDone());
    }

    @Test
    void theFillIsPacedToTheConfiguredRate() throws IOException {
        regionFiles("world", FIRST);
        store.turnOn("world", key -> false);
        fixturePaints(0, 0); // read it once ahead, so the fill's own read is not slowed by loading the reader's classes
        AtomicLong slept = new AtomicLong();
        scan = scan(0.1, slept::addAndGet);

        scan.start(world);
        awaitIdle();

        double expected = Files.size(fixture()) / 100.0; // the bytes at 100,000 a second, in milliseconds
        assertEquals(expected, slept.get(), expected / 10, "slept " + slept.get() + " ms");
    }

    @Test
    void aSecondWorldWaitsForTheFirst() throws IOException {
        WorldMock other = server.addSimpleWorld("other");
        regionFiles("world", FIRST);
        regionFiles("other", FIRST);
        store.turnOn("world", key -> false);
        store.turnOn("other", key -> false);
        Gate gate = new Gate(false);
        scan = scan(0.1, gate);

        assertEquals(PaintScan.Start.STARTED, scan.start(world));
        assertEquals(PaintScan.Start.QUEUED, scan.start(other));
        gate.awaitReached();

        assertEquals(new PaintScan.Status(false, true, 0, 1), scan.status("other"));
        assertTrue(scan.status("world").running());
        assertEquals(PaintScan.Start.QUEUED, scan.start(other), "asked again, it is still waiting");
        gate.release();
        awaitIdle();
        ticks(1);
        assertTrue(store.record("world").fillDone());
        assertTrue(store.record("other").fillDone());
        assertEquals(fixturePaints(0, 0), store.record("other").chunks());
    }

    @Test
    void startingAWorldBeingReadSaysSo() throws IOException {
        regionFiles("world", FIRST);
        store.turnOn("world", key -> false);
        Gate gate = new Gate(false);
        scan = scan(0.1, gate);
        scan.start(world);
        gate.awaitReached();

        assertEquals(PaintScan.Start.ALREADY, scan.start(world));
        assertEquals(new PaintScan.Status(true, false, 1, 1), scan.status("world"));
    }

    @Test
    void cancellingAWorldStopsItsFill() throws IOException {
        regionFiles("world", FIRST, SECOND);
        PaintRecord record = store.turnOn("world", key -> false);
        Gate gate = new Gate(false);
        scan = scan(0.1, gate);
        scan.start(world);
        gate.awaitReached();
        ticks(1); // the first file is in the record

        scan.cancel("world");

        assertEquals(new PaintScan.Status(false, false, 0, 0), scan.status("world"));
        awaitIdle(); // the cancel cut the fill's sleep short
        ticks(1);
        assertFalse(record.fillDone());
        assertEquals(Set.of(FIRST), record.finishedFiles(), "the second file is not read");
        // the thread goes on to the next fill as if nothing had happened
        gate.release();
        assertEquals(PaintScan.Start.STARTED, scan.start(world));
        awaitIdle();
        ticks(1);
        assertTrue(record.fillDone());
        assertEquals(Set.of(FIRST, SECOND), record.finishedFiles());
    }

    // The world was switched off while its file was read: what was read must not reach any record.
    @Test
    void aWorldTurnedOffDuringAReadIsLeftOff() throws IOException {
        regionFiles("world", FIRST, SECOND);
        store.turnOn("world", key -> false);
        Gate gate = new Gate(false);
        scan = scan(0.1, gate);
        scan.start(world);
        gate.awaitReached(); // the first file's chunks wait for the main thread

        store.turnOff("world");
        ticks(1);

        assertNull(store.record("world"));
        assertEquals(List.of(), changes);
        assertFalse(scan.status("world").running(), "the fill stopped");
        awaitIdle();
    }

    // Switched off and on again while a file was read: the new record is a fill of its own.
    @Test
    void aRecordReplacedDuringAReadIsLeftAlone() throws IOException {
        regionFiles("world", FIRST, SECOND);
        store.turnOn("world", key -> false);
        Gate gate = new Gate(false);
        scan = scan(0.1, gate);
        scan.start(world);
        gate.awaitReached();

        store.turnOff("world");
        PaintRecord fresh = store.turnOn("world", key -> false);
        ticks(1);

        assertTrue(fresh.chunks().isEmpty(), "nothing of the old fill reaches the new record");
        assertTrue(fresh.finishedFiles().isEmpty());
        assertFalse(scan.status("world").running(), "the old fill stopped");
        awaitIdle();
        ticks(1);
        assertTrue(fresh.chunks().isEmpty());
        assertFalse(fresh.fillDone());
    }

    // The plugin may be disabled once the scan stops, and a disabled plugin cannot schedule tasks. This Sleeper takes
    // no notice of the stop's interrupt, so only the stop flag keeps the thread from reading on.
    @Test
    void aStoppedScanHandsNothingMoreToTheMainThread() throws IOException {
        regionFiles("world", FIRST, SECOND);
        PaintRecord record = store.turnOn("world", key -> false);
        Gate gate = new Gate(true);
        scan = scan(0.1, gate);
        scan.start(world);
        gate.awaitReached();
        ticks(1);
        Thread thread = gate.thread;
        assertEquals("Architect biome scan", thread.getName());
        assertTrue(thread.isDaemon());
        assertEquals(Thread.MIN_PRIORITY, thread.getPriority());

        scan.stop();

        assertFalse(thread.isAlive(), "the stop waited for the thread to end");
        assertEquals(List.of(), server.getScheduler().getPendingTasks());
        ticks(1);
        assertEquals(Set.of(FIRST), record.finishedFiles());
        assertFalse(record.fillDone());
    }

    @Test
    void theLogSaysWhatWasSkipped() throws IOException {
        Path folder = Files.createDirectories(regionFolder("world"));
        byte[] anything = {1, 2, 3};
        RegionFixtures.region(folder.resolve(SECOND), Map.of(0, anything, 1, anything), 4); // LZ4, which is not read
        Files.createDirectories(folder.resolve("r.1.1.mca")); // a folder where a file belongs
        store.turnOn("world", key -> false);
        scan = scan(20, NO_SLEEP);

        scan.start(world);
        awaitIdle();
        ticks(1);

        List<String> told = logged.messages(Level.INFO);
        assertEquals(1, told.size(), told.toString());
        assertTrue(told.get(0).contains("Custom biomes layer: read world's saved files: 2 chunks, 0 with custom "
                + "biomes. Skipped: LZ4 is not supported 2, unreadable region file: "), told.get(0));
        assertTrue(told.get(0).endsWith(" 1"), told.get(0));
        assertTrue(store.record("world").fillDone());
        assertEquals(Set.of(SECOND), store.record("world").finishedFiles(), "a file that was not read is not finished");
    }

    /**
     * Holds the scan at its first sleep until released, and lets every later one pass. An interrupt ends the wait: it
     * throws, as {@code Thread.sleep} does, or with {@code ignoresInterrupts} it returns as if the time had passed.
     */
    private static final class Gate implements PaintScan.Sleeper {

        private final boolean ignoresInterrupts;
        private final CountDownLatch reached = new CountDownLatch(1);
        private final CountDownLatch released = new CountDownLatch(1);
        private volatile Thread thread;

        Gate(boolean ignoresInterrupts) {
            this.ignoresInterrupts = ignoresInterrupts;
        }

        @Override
        public void sleep(long millis) throws InterruptedException {
            thread = Thread.currentThread();
            reached.countDown();
            try {
                released.await();
            } catch (InterruptedException e) {
                if (!ignoresInterrupts) {
                    throw e;
                }
            }
        }

        void awaitReached() {
            try {
                assertTrue(reached.await(WAIT.toSeconds(), TimeUnit.SECONDS), "the scan reached its first sleep");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                fail(e);
            }
        }

        void release() {
            released.countDown();
        }
    }

    /** What the plugin's logger was asked to log. */
    private static final class Logged extends Handler {
        private final List<LogRecord> seen = Collections.synchronizedList(new ArrayList<>());

        @Override
        public void publish(LogRecord record) {
            seen.add(record);
        }

        @Override
        public void flush() {
        }

        @Override
        public void close() {
        }

        /** The messages logged at exactly that level, with the plugin's name in front, as the server shows them. */
        List<String> messages(Level level) {
            synchronized (seen) {
                return seen.stream().filter(record -> record.getLevel().equals(level)).map(LogRecord::getMessage)
                        .toList();
            }
        }
    }
}
