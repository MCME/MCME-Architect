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
import java.nio.charset.StandardCharsets;
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
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;

import static org.junit.jupiter.api.Assertions.*;

// The first fill reads a world's saved region files on its own thread, one world at a time, and hands what it read to
// the main thread file by file. The region files are copies of resources/customBiomes/r.-1.-1.mca, the chunks x -6 to
// -1 and z -6 to -1 of the 26.2 test world, all painted with cbc:111g380-5p and partly with mcme:custom_003; as
// r.0.0.mca the same chunks lie one region further on, at x and z 26 to 31. A fill that must not get further than its
// first file is held by a Sleeper that blocks: at 0.1 MB/s the fill sleeps after its first file.
// The same thread reads the saved copies of chunks that unloaded, and of selections. Their looks are timed on a fake
// clock that a test moves on; the time each chunk was saved is set in its region file's header.
class PaintScanTest {

    private static final String CBC = "cbc:111g380-5p";
    private static final String QUAY = "mcme:custom_003";
    private static final Set<String> IDS = Set.of(CBC, QUAY);
    private static final String FIRST = "r.-1.-1.mca";
    private static final String SECOND = "r.0.0.mca";
    private static final Duration WAIT = Duration.ofSeconds(30);
    private static final PaintScan.Sleeper NO_SLEEP = millis -> {
    };
    /** The second a chunk unloaded at, or a WorldEdit command came, in the saved-read tests. */
    private static final long UNLOADED = 1_800_000_000L;
    /** The milliseconds from a saved read's request to each of its looks: 2 s, then 2, 5, 15 and 30 s more. */
    private static final long[] LOOKS = {2_000, 2_000, 5_000, 15_000, 30_000};

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
    /** The clock the looks at saved copies are timed on, in nanoseconds. */
    private final AtomicLong clock = new AtomicLong();

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
                (worldName, x, z) -> handed.add(new Handed(worldName, x, z)), megabytesPerSecond, sleeper, clock::get);
    }

    /** Moves the clock of the looks at saved copies on. */
    private void later(long millis) {
        clock.addAndGet(millis * 1_000_000);
    }

    /** Waits for the scan thread to make the looks that are due, and lets the main thread take what they found. */
    private void look() {
        awaitIdle();
        ticks(1);
    }

    /** Every look at a saved read, from its request to its last. */
    private void allLooks() {
        for (long wait : LOOKS) {
            later(wait);
            look();
        }
    }

    /** The world's region file that a test sets the save times in: a copy of the fixture. */
    private Path firstFile(String worldName) {
        return regionFolder(worldName).resolve(FIRST);
    }

    /** Sets the time the chunk's copy in the fixture was saved, as the game would. */
    private void savedAt(String worldName, int chunkX, int chunkZ, long epochSecond) throws IOException {
        RegionFixtures.savedAt(firstFile(worldName), RegionFixtures.indexOf(chunkX, chunkZ), epochSecond);
    }

    /** The fixture's paint of a chunk. */
    private static ChunkPaint saved(int chunkX, int chunkZ) throws IOException {
        return fixturePaints(0, 0).get(PaintRecord.chunkKey(chunkX, chunkZ));
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
        assertEquals(2, told.size(), told.toString());
        assertTrue(told.get(0).endsWith("Custom biomes layer: reading world's saved files: 1 of 2 region files."),
                told.get(0));
        assertTrue(told.get(1).endsWith("Custom biomes layer: read world's saved files: 72 chunks, 72 with custom "
                + "biomes."), told.get(1));
    }

    // A big world takes a long while to read, so the log tells how far its fill is at each tenth of its region files,
    // and the end once. Of twelve files, the first and the seventh reach no new tenth.
    @Test
    void theLogTellsHowFarAFillIsAtEachTenthOfItsFiles() throws IOException {
        String[] names = new String[12];
        for (int i = 0; i < names.length; i++) {
            names[i] = "r." + i + ".0.mca";
        }
        regionFiles("world", names);
        store.turnOn("world", key -> false);
        scan = scan(20, NO_SLEEP);

        scan.start(world);
        awaitIdle();
        ticks(1);

        List<String> expected = new ArrayList<>();
        for (int done : new int[] {2, 3, 4, 5, 6, 8, 9, 10, 11}) {
            expected.add("Custom biomes layer: reading world's saved files: " + done + " of 12 region files.");
        }
        expected.add("Custom biomes layer: read world's saved files: 432 chunks, 432 with custom biomes.");
        List<String> told = logged.messages(Level.INFO);
        assertEquals(expected.size(), told.size(), told.toString());
        for (int i = 0; i < expected.size(); i++) {
            assertTrue(told.get(i).endsWith(expected.get(i)), told.get(i));
        }
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
        assertEquals(2, told.size(), told.toString());
        assertTrue(told.get(0).endsWith("Custom biomes layer: reading world's saved files: 1 of 2 region files."),
                told.get(0));
        assertTrue(told.get(1).contains("Custom biomes layer: read world's saved files: 2 chunks, 0 with custom "
                + "biomes. Skipped: LZ4 is not supported 2, unreadable region file: "), told.get(1));
        assertTrue(told.get(1).endsWith(" 1"), told.get(1));
        assertTrue(store.record("world").fillDone());
        assertEquals(Set.of(SECOND), store.record("world").finishedFiles(), "a file that was not read is not finished");
    }

    // An Error on the scan thread, such as running out of memory over a region file, must not end the fills for good.
    // The fill it struck stops with a warning and is dropped, so that its world is not left reading, and the next
    // world's fill still runs. The Error comes from the sleeper, which the fill calls on the scan thread between files,
    // as it calls the reader.
    @Test
    void aFillThatFailsWithAnErrorIsDroppedAndTheNextFillStillRuns() throws IOException {
        WorldMock other = server.addSimpleWorld("other");
        regionFiles("world", FIRST, SECOND);
        regionFiles("other", FIRST);
        PaintRecord failing = store.turnOn("world", key -> false);
        store.turnOn("other", key -> false);
        AtomicBoolean thrown = new AtomicBoolean();
        scan = scan(0.1, millis -> {
            if (thrown.compareAndSet(false, true)) {
                throw new OutOfMemoryError("thrown by the test");
            }
        });

        assertEquals(PaintScan.Start.STARTED, scan.start(world));
        assertEquals(PaintScan.Start.QUEUED, scan.start(other));
        awaitIdle();
        ticks(1);

        assertTrue(store.record("other").fillDone(), "the next fill ran");
        assertEquals(fixturePaints(0, 0), store.record("other").chunks());
        assertEquals(new PaintScan.Status(false, false, 0, 0), scan.status("world"), "not left reading");
        assertEquals(Set.of(FIRST), failing.finishedFiles(), "what reached the record stays");
        assertFalse(failing.fillDone());
        List<LogRecord> warnings = logged.records(Level.WARNING);
        assertEquals(1, warnings.size(), warnings.toString());
        assertTrue(warnings.get(0).getMessage().endsWith("Custom biomes layer: reading the saved files of world "
                + "failed, so its read stopped; /architect maplayers scan world reads it again from the start."),
                warnings.get(0).getMessage());
        assertInstanceOf(OutOfMemoryError.class, warnings.get(0).getThrown(), "with the stack trace");

        assertEquals(PaintScan.Start.STARTED, scan.start(world), "the world can be read again");
        awaitIdle();
        ticks(1);
        assertTrue(failing.fillDone());
    }

    // Should the scan thread die all the same, as when an Error strikes again while the first one is logged, the next
    // start makes a new one, so that later fills do not wait for a reload.
    @Test
    void aStartAfterTheScanThreadDiedMakesANewOne() throws IOException, InterruptedException {
        WorldMock other = server.addSimpleWorld("other");
        regionFiles("world", FIRST, SECOND);
        regionFiles("other", FIRST);
        store.turnOn("world", key -> false);
        store.turnOn("other", key -> false);
        Handler failingAgain = new Handler() {
            @Override
            public void publish(LogRecord record) {
                if (record.getLevel() == Level.WARNING) {
                    throw new OutOfMemoryError("thrown by the test, while the first one is logged");
                }
            }

            @Override
            public void flush() {
            }

            @Override
            public void close() {
            }
        };
        plugin.getLogger().addHandler(failingAgain);
        AtomicReference<Thread> first = new AtomicReference<>();
        scan = scan(0.1, millis -> {
            if (first.compareAndSet(null, Thread.currentThread())) {
                throw new OutOfMemoryError("thrown by the test");
            }
        });
        scan.start(world);
        awaitIdle();
        first.get().join(WAIT.toMillis());
        plugin.getLogger().removeHandler(failingAgain);
        assertFalse(first.get().isAlive(), "the scan thread died");

        assertEquals(PaintScan.Start.STARTED, scan.start(other));
        awaitIdle();
        ticks(1);

        assertTrue(store.record("other").fillDone(), "a new thread read it");
    }

    // A watched chunk that unloads has its saved copy read, off the main thread, 2 seconds later: the game writes a
    // chunk that changed as it unloads, and stamps the second it wrote it at in the region file's header. A copy
    // stamped at the unload's second or later goes into the record. An empty one does too: it takes a stale entry out.
    @Test
    void aSavedCopyWrittenAfterTheUnloadIsRecorded() throws IOException {
        regionFiles("world", FIRST);
        savedAt("world", -3, -4, UNLOADED + 1);
        savedAt("world", -1, -1, UNLOADED);
        Path plain = regionFolder("world").resolve(SECOND); // chunk (0, 0), saved without a custom biome
        RegionFixtures.region(plain, Map.of(0, RegionFixtures.chunk(4903, "minecraft:full",
                Map.of(0, Collections.nCopies(64, "minecraft:plains")))), 2, UNLOADED + 2);
        PaintRecord record = store.turnOn("world", key -> false);
        record.put(-3, -4, paint("cbc:before"));
        record.put(0, 0, paint("cbc:stale"));
        store.saveDirty();
        scan = scan(20, NO_SLEEP);

        scan.unloaded("world", -3, -4, UNLOADED);
        scan.unloaded("world", -1, -1, UNLOADED);
        scan.unloaded("world", 0, 0, UNLOADED);
        later(1_999);
        look();
        assertEquals(paint("cbc:before"), record.get(-3, -4), "not looked at before 2 seconds have passed");
        later(1);
        look();

        assertEquals(saved(-3, -4), record.get(-3, -4), "the copy written as it unloaded");
        assertEquals(saved(-1, -1), record.get(-1, -1), "a copy written in the second of the unload counts");
        assertTrue(record.get(0, 0).isEmpty(), "an empty copy takes the stale entry out");
        assertEquals(2, record.chunks().size());
        assertEquals(List.of("world", "world"), changes, "the map hears of each region file's at once");
        assertEquals(List.of(), handed);
        store.saveDirty();
        assertEquals(record.chunks(), PaintRecord.load(dir.resolve("records").resolve("world.dat")).chunks(),
                "the record was marked to be saved");
    }

    // The game writes a chunk that unloads a moment later, on a thread of its own. A copy stamped before the unload is
    // the one from before: it is not taken, and the chunk is looked at again.
    @Test
    void aSavedCopyNotYetWrittenIsTriedAgain() throws IOException {
        regionFiles("world", FIRST);
        savedAt("world", -3, -4, UNLOADED - 60);
        PaintRecord record = store.turnOn("world", key -> false);
        scan = scan(20, NO_SLEEP);

        scan.unloaded("world", -3, -4, UNLOADED);
        later(2_000);
        look();
        assertTrue(record.chunks().isEmpty(), "the copy from before the unload is not taken");

        savedAt("world", -3, -4, UNLOADED + 3); // the game's write lands
        later(1_999);
        look();
        assertTrue(record.chunks().isEmpty(), "the second look is 2 seconds after the first");
        later(1);
        look();

        assertEquals(saved(-3, -4), record.get(-3, -4), "taken at the second look");
        assertEquals(List.of("world"), changes);
    }

    // A copy that is still older than the unload at the last look, a minute after it, is never taken: the record keeps
    // what a live read found there, which differs from it. The chunk is counted, and told once, at the next minute.
    @Test
    void aSavedReadThatNeverLandsIsCountedAndLoggedOnce() throws IOException {
        regionFiles("world", FIRST);
        savedAt("world", -3, -4, UNLOADED - 60);
        PaintRecord record = store.turnOn("world", key -> false);
        ChunkPaint live = paint("cbc:live");
        record.put(-3, -4, live);
        scan = scan(20, NO_SLEEP);

        scan.unloaded("world", -3, -4, UNLOADED);
        for (int i = 0; i < LOOKS.length - 1; i++) {
            later(LOOKS[i]);
            look();
            scan.logLate();
        }
        assertEquals(List.of(), logged.messages(Level.INFO), "not counted before the last look");
        later(LOOKS[LOOKS.length - 1]);
        look();
        assertEquals(live, record.get(-3, -4), "an older copy is never taken");

        scan.logLate(); // the minute's line
        later(60_000);
        look();
        scan.logLate(); // and nothing new to tell a minute later

        List<String> told = logged.messages(Level.INFO);
        assertEquals(1, told.size(), told.toString());
        assertTrue(told.get(0).endsWith("Custom biomes layer: 1 chunk unloaded, but its saved copy was not written in "
                + "time to be read; /architect maplayers scan world catches it."), told.get(0));
        assertEquals(List.of(), changes);
    }

    // A chunk whose region file holds no copy of it, or whose region file is not there at all, at the last look is
    // counted as well: so a folder that is not where the game saves is seen in the log.
    @Test
    void aChunkWithNoSavedCopyAtTheLastLookIsCounted() throws IOException {
        regionFiles("world", FIRST);
        store.turnOn("world", key -> false);
        scan = scan(20, NO_SLEEP);

        scan.unloaded("world", -10, -10, UNLOADED); // r.-1.-1.mca holds no copy of it
        scan.unloaded("world", 40, 40, UNLOADED); // r.1.1.mca is not there
        allLooks();
        scan.logLate();

        List<String> told = logged.messages(Level.INFO);
        assertEquals(1, told.size(), told.toString());
        assertTrue(told.get(0).endsWith("Custom biomes layer: 2 chunks unloaded, but their saved copies were not "
                + "written in time to be read; /architect maplayers scan world catches them."), told.get(0));
    }

    // The game writes only a chunk that changed since it was last saved. One that a builder only walked past keeps its
    // older copy, and the record agrees with it already: there was nothing to miss, so it is not counted.
    @Test
    void anUnchangedChunkThatTheGameDoesNotWriteAgainIsNotCounted() throws IOException {
        regionFiles("world", FIRST);
        savedAt("world", -3, -4, UNLOADED - 600);
        PaintRecord record = store.turnOn("world", key -> false);
        record.put(-3, -4, saved(-3, -4)); // what a live read found while it was loaded
        scan = scan(20, NO_SLEEP);

        scan.unloaded("world", -3, -4, UNLOADED);
        allLooks();
        scan.logLate();

        assertEquals(List.of(), logged.messages(Level.INFO));
        assertEquals(saved(-3, -4), record.get(-3, -4));
        assertEquals(List.of(), changes);
    }

    // A player may load the chunk again before its copy is looked at. Its saved copy may be older than what the game
    // holds now, so the chunk goes to the live reads, and the record is left as it is.
    @Test
    void aChunkLoadedAgainBeforeItsSavedReadIsReadLive() throws IOException {
        regionFiles("world", FIRST);
        savedAt("world", -3, -4, UNLOADED + 1);
        PaintRecord record = store.turnOn("world", key -> false);
        ChunkPaint live = paint("cbc:live");
        record.put(-3, -4, live);
        scan = scan(20, NO_SLEEP);

        scan.unloaded("world", -3, -4, UNLOADED);
        world.getChunkAt(-3, -4); // loaded again
        later(2_000);
        look();

        assertEquals(List.of(new Handed("world", -3, -4)), handed, "the live reads take it");
        assertEquals(live, record.get(-3, -4), "the record is left as it is");
        assertEquals(List.of(), changes);
    }

    // A saved read asked for before the world was switched off, or before a scan of it started over, does not reach
    // the record: the world is off, or read again from the start.
    @Test
    void aSavedReadOfAWorldSwitchedOffOrScannedAgainIsDropped() throws IOException {
        regionFiles("world", FIRST);
        savedAt("world", -3, -4, UNLOADED + 1);
        store.turnOn("world", key -> false);
        scan = scan(20, NO_SLEEP);
        scan.unloaded("world", -3, -4, UNLOADED);
        store.turnOff("world");
        PaintRecord again = store.turnOn("world", key -> false);
        later(2_000);
        look();
        assertTrue(again.chunks().isEmpty(), "asked for under the record of before");
        assertEquals(List.of(), changes, "and nothing changed");

        scan.unloaded("world", -3, -4, UNLOADED);
        assertEquals(PaintScan.Start.STARTED, scan.start(world)); // the scan starts over
        awaitIdle();
        ticks(1);
        again.clearExcept(key -> false); // what the new fill read, so that the saved read would show
        later(2_000);
        look();

        assertTrue(again.chunks().isEmpty(), "asked for before the new fill started");
    }

    // A stop drops the saved reads asked for, and what a look found that waits for the main thread when it comes.
    @Test
    void savedReadsStopWithTheLayer() throws IOException {
        regionFiles("world", FIRST);
        savedAt("world", -3, -4, UNLOADED + 1);
        savedAt("world", -1, -1, UNLOADED + 1);
        PaintRecord record = store.turnOn("world", key -> false);
        scan = scan(20, NO_SLEEP);
        scan.unloaded("world", -3, -4, UNLOADED);
        later(2_000);
        awaitIdle(); // looked at: its copy waits for the main thread
        scan.unloaded("world", -1, -1, UNLOADED); // not looked at yet

        scan.stop();
        later(60_000);
        ticks(1);
        scan.unloaded("world", -1, -1, UNLOADED); // asked for after the stop
        later(60_000);
        ticks(1);

        assertTrue(record.chunks().isEmpty());
        assertEquals(List.of(), changes);
        assertEquals(List.of(), server.getScheduler().getPendingTasks());
    }

    // At the second read of a selection, 30 seconds after the command, the saved copies of its chunks that the game
    // wrote at the command's second or later are read: the edit's. A chunk that is loaded goes to the live reads, once.
    // Most WorldEdit commands change nothing, and the game writes only chunks that changed, so a copy that stays older
    // is looked at again, then left alone, and not counted.
    @Test
    void aSelectionTakesTheSavedCopiesWrittenSinceItsCommand() throws IOException {
        regionFiles("world", FIRST);
        for (int x = -6; x <= -1; x++) {
            for (int z = -6; z <= -1; z++) {
                savedAt("world", x, z, UNLOADED - 60);
            }
        }
        savedAt("world", -3, -4, UNLOADED + 5);
        savedAt("world", -2, -2, UNLOADED);
        savedAt("world", -1, -1, UNLOADED + 5); // outside the box
        world.getChunkAt(-2, -2);
        PaintRecord record = store.turnOn("world", key -> false);
        scan = scan(20, NO_SLEEP);

        scan.selected(new SelectionSource.ChunkBox("world", -4, -4, -2, -2), UNLOADED);
        later(2_000);
        look();
        assertEquals(Map.of(PaintRecord.chunkKey(-3, -4), saved(-3, -4)), record.chunks());
        assertEquals(List.of(new Handed("world", -2, -2)), handed);

        savedAt("world", -4, -4, UNLOADED + 40); // the edit reaches another chunk
        for (int i = 1; i < LOOKS.length; i++) {
            later(LOOKS[i]);
            look();
        }
        scan.logLate();

        assertEquals(Map.of(PaintRecord.chunkKey(-3, -4), saved(-3, -4), PaintRecord.chunkKey(-4, -4), saved(-4, -4)),
                record.chunks(), "a copy written after the first look is taken at a later one");
        assertEquals(List.of(new Handed("world", -2, -2)), handed, "not handed over again at every look");
        assertEquals(List.of(), logged.messages(Level.INFO), "the copies that stayed older are not counted");
    }

    // A selection may cover thousands of region files' worth of land: only the files that are there are read.
    @Test
    void aSelectionOfBillionsOfChunksReadsTheFilesThatAreThere() throws IOException {
        regionFiles("world", FIRST);
        savedAt("world", -3, -4, UNLOADED + 5);
        PaintRecord record = store.turnOn("world", key -> false);
        scan = scan(20, NO_SLEEP);

        scan.selected(new SelectionSource.ChunkBox("world", -20_000, -20_000, 20_000, 20_000), UNLOADED);
        later(2_000);
        assertTimeout(Duration.ofSeconds(10), this::look);

        assertEquals(Map.of(PaintRecord.chunkKey(-3, -4), saved(-3, -4)), record.chunks());
    }

    // The saved reads share the scan thread with the fills. They are small, so they go first: between two region files
    // of a fill, not after its last.
    @Test
    void savedCopiesAreReadBetweenTheFilesOfAFill() throws IOException {
        server.addSimpleWorld("other");
        regionFiles("world", FIRST, SECOND);
        regionFiles("other", FIRST);
        savedAt("other", -3, -4, UNLOADED + 1);
        store.turnOn("world", key -> false);
        PaintRecord other = store.turnOn("other", key -> false);
        Gate gate = new Gate(false);
        scan = scan(0.1, gate);
        scan.start(world);
        gate.awaitReached(); // the first file is read, and the fill waits out its pace
        ticks(1);
        assertEquals(List.of("world"), changes);

        scan.unloaded("other", -3, -4, UNLOADED);
        later(2_000);
        gate.release();
        awaitIdle();
        ticks(1);

        assertEquals(List.of("world", "other", "world"), changes, "the saved copy before the fill's second file");
        assertEquals(saved(-3, -4), other.get(-3, -4));
        assertTrue(store.record("world").fillDone());
    }

    // While a world's fill runs, the record holds nothing yet from the region files it has still to read. An unchanged
    // chunk of such a file that unloads keeps its older copy, which differs from that empty record, though nothing was
    // missed: the fill puts that copy when it gets to the file. It is not counted. The fill here goes one file at a
    // time, and each look at the copy comes between two of its files; the chunk lies in the last of six.
    @Test
    void anUnchangedChunkWhoseFileTheFillHasNotReadIsNotCounted() throws IOException {
        Steps steps = sixFileFill();
        PaintRecord record = store.record("world");
        // the fixture's chunk (-3, -4), as it lies in r.5.0.mca, saved long before it unloads
        RegionFixtures.savedAt(regionFolder("world").resolve("r.5.0.mca"), RegionFixtures.indexOf(189, 28),
                UNLOADED - 600);

        scan.unloaded("world", 189, 28, UNLOADED);
        looksBetweenFiles(steps);
        scan.logLate();

        assertEquals(List.of(), late(), "not counted while the fill runs");
        steps.releaseAll();
        awaitIdle();
        ticks(1);
        assertEquals(fixturePaints(6, 1).get(PaintRecord.chunkKey(189, 28)), record.get(189, 28),
                "the fill put the copy");
        assertTrue(record.fillDone());
    }

    // The fill's exception holds only for a region file that the fill has still to put into the record. Once it has
    // put a file, an older copy there that differs from the record, as from a live read whose save never came, is
    // counted again, while the fill goes on with its other files.
    @Test
    void aChunkOfAFileTheFillHasPutIsCountedWhileTheFillRuns() throws IOException {
        Steps steps = sixFileFill();
        PaintRecord record = store.record("world");
        ticks(1); // r.0.0.mca, read first, reaches the record
        record.put(29, 28, paint("cbc:live")); // the fixture's chunk (-3, -4) there, as a live read found it
        RegionFixtures.savedAt(regionFolder("world").resolve("r.0.0.mca"), RegionFixtures.indexOf(29, 28),
                UNLOADED - 600);

        scan.unloaded("world", 29, 28, UNLOADED);
        looksBetweenFiles(steps);
        scan.logLate();

        assertTrue(scan.status("world").running(), "the fill still runs");
        assertEquals(1, late().size(), "counted: the fill has put that file already");
    }

    // Nor does it hold for a region file that came after the fill started: it is not among the files the fill reads.
    @Test
    void aChunkOfARegionFileNewerThanTheFillIsCounted() throws IOException {
        Steps steps = sixFileFill();
        regionFiles("world", "r.9.9.mca");
        // the fixture's chunk (-3, -4), as it lies in r.9.9.mca, saved long before it unloads
        RegionFixtures.savedAt(regionFolder("world").resolve("r.9.9.mca"), RegionFixtures.indexOf(317, 316),
                UNLOADED - 600);

        scan.unloaded("world", 317, 316, UNLOADED);
        looksBetweenFiles(steps);
        scan.logLate();

        assertTrue(scan.status("world").running(), "the fill still runs");
        assertEquals(1, late().size(), "counted: the fill does not read that file");
    }

    /**
     * Puts the world on, and starts its fill of six region files, the fixture under each name, which the fill reads
     * one at a time as {@link Steps} lets it: it has read r.0.0.mca and waits.
     */
    private Steps sixFileFill() throws IOException {
        regionFiles("world", "r.0.0.mca", "r.1.0.mca", "r.2.0.mca", "r.3.0.mca", "r.4.0.mca", "r.5.0.mca");
        store.turnOn("world", key -> false);
        Steps steps = new Steps();
        scan = scan(0.1, steps);
        scan.start(world);
        steps.awaitReached(); // the first file is read, and the fill waits out its pace
        return steps;
    }

    /** Each look at a saved read comes due in turn, and is made before the held fill's next file. */
    private void looksBetweenFiles(Steps steps) {
        for (long wait : LOOKS) {
            later(wait);
            steps.step();
            steps.awaitReached();
            ticks(1);
        }
    }

    // A region file that cannot be read, here a folder where the file belongs, is told once in the log with the error,
    // while it lasts, however many of its chunks are looked at how often. Its chunks are not counted as copies not
    // written in time: they are not late, and a scan could not read them either.
    @Test
    void aRegionFileThatCannotBeReadIsWarnedOnceAndNotCounted() throws IOException {
        Files.createDirectories(regionFolder("world").resolve(SECOND));
        store.turnOn("world", key -> false);
        scan = scan(20, NO_SLEEP);

        scan.unloaded("world", 0, 0, UNLOADED);
        scan.unloaded("world", 1, 0, UNLOADED);
        allLooks();
        scan.logLate();

        List<LogRecord> warnings = logged.records(Level.WARNING);
        assertEquals(1, warnings.size(), warnings.toString());
        assertTrue(warnings.get(0).getMessage().endsWith("Custom biomes layer: could not read the saved chunks of "
                + "world in r.0.0.mca, so they are not brought up to date from it. This is logged once while it "
                + "lasts."), warnings.get(0).getMessage());
        assertNotNull(warnings.get(0).getThrown(), "with the error");
        assertEquals(List.of(), late());
    }

    // A chunk saved in a form that is not read, here LZ4, stays unreadable until the game saves it anew: it is given
    // up at once, and told once a world and reason. One that cannot be read for another reason may be mid-write, so
    // it is looked at again, and told only if it still cannot be read at its last look. Neither is counted as late.
    @Test
    void aSavedChunkThatCannotBeReadIsToldOnceAndNotCounted() throws IOException {
        Path folder = Files.createDirectories(regionFolder("world"));
        byte[] anything = {1, 2, 3};
        RegionFixtures.region(folder.resolve(SECOND), Map.of(0, anything, 1, anything), 4, UNLOADED + 1);
        RegionFixtures.region(folder.resolve("r.1.0.mca"),
                Map.of(0, "cbc: this is no NBT".getBytes(StandardCharsets.UTF_8)), 2, UNLOADED + 1);
        PaintRecord record = store.turnOn("world", key -> false);
        record.put(0, 0, paint("cbc:before"));
        scan = scan(20, NO_SLEEP);

        scan.unloaded("world", 0, 0, UNLOADED);
        scan.unloaded("world", 1, 0, UNLOADED);
        scan.unloaded("world", 32, 0, UNLOADED);
        later(LOOKS[0]);
        look();
        assertEquals(1, warnings().size(), "told once for both chunks of that form: " + warnings());
        assertTrue(warnings().get(0).endsWith("Custom biomes layer: the saved copy of chunk 0, 0 of world cannot be"
                + " read (LZ4 is not supported), so the chunk is not brought up to date from it. This is logged once"
                + " while it lasts."), warnings().get(0));
        for (int i = 1; i < LOOKS.length - 1; i++) {
            later(LOOKS[i]);
            look();
        }
        assertEquals(1, warnings().size(), "the other chunk is not told before its last look");
        later(LOOKS[LOOKS.length - 1]);
        look();
        scan.logLate();

        assertEquals(2, warnings().size(), warnings().toString());
        assertTrue(warnings().get(1).endsWith("Custom biomes layer: the saved copy of chunk 32, 0 of world cannot be "
                + "read (unreadable: IOException), so the chunk is not brought up to date from it. This is logged once "
                + "while it lasts."), warnings().get(1));
        assertEquals(List.of(), late());
        assertEquals(paint("cbc:before"), record.get(0, 0), "the record keeps what it holds");
    }

    // A cancel interrupts the thread while it reads a fill. Should the interrupt still be pending when the looks
    // between two of its files come, it must not cut them short: the look reads the copy, and the interrupt is left
    // for the fill.
    @Test
    void anInterruptLeftForTheFillDoesNotCutALookShort() throws IOException {
        server.addSimpleWorld("other");
        regionFiles("world", FIRST, SECOND);
        regionFiles("other", FIRST);
        savedAt("other", -3, -4, UNLOADED + 1);
        store.turnOn("world", key -> false);
        PaintRecord other = store.turnOn("other", key -> false);
        AtomicBoolean once = new AtomicBoolean();
        scan = scan(0.1, millis -> {
            if (once.compareAndSet(false, true)) {
                later(2_000); // the look comes due while the fill reads its first file
                Thread.currentThread().interrupt(); // as a cancel's interrupt that came after that file was read
            }
        });
        scan.unloaded("other", -3, -4, UNLOADED);

        scan.start(world);
        awaitIdle();
        ticks(1);

        assertEquals(saved(-3, -4), other.get(-3, -4), "the look read the copy");
        assertEquals(List.of(), warnings());
    }

    // A look hands what it found to the main thread one region file at a time, as a fill does its files, so that a
    // selection with thousands of changed chunks never lands in one task of the main thread: two files, two tasks.
    @Test
    void aLookHandsOverWhatItFoundOneRegionFileAtATime() throws IOException {
        regionFiles("world", FIRST, SECOND);
        savedAt("world", -3, -4, UNLOADED + 1);
        RegionFixtures.savedAt(regionFolder("world").resolve(SECOND), RegionFixtures.indexOf(29, 28), UNLOADED + 1);
        PaintRecord record = store.turnOn("world", key -> false);
        scan = scan(20, NO_SLEEP);

        scan.unloaded("world", -3, -4, UNLOADED);
        scan.unloaded("world", 29, 28, UNLOADED); // the same chunk of the fixture, in r.0.0.mca
        later(2_000);
        awaitIdle();

        assertEquals(2, server.getScheduler().getPendingTasks().size(), "a task for each region file");
        ticks(1);
        assertEquals(List.of("world", "world"), changes);
        assertEquals(saved(-3, -4), record.get(-3, -4));
        assertEquals(saved(-3, -4), record.get(29, 28));
    }

    /** The log's lines about saved copies not written in time. */
    private List<String> late() {
        return logged.messages(Level.INFO).stream().filter(line -> line.contains("not written in time")).toList();
    }

    /** The log's warnings, as they read. */
    private List<String> warnings() {
        return logged.messages(Level.WARNING);
    }

    /** Holds the scan at every sleep until the test lets it go on, one sleep at a time, or all of them. */
    private static final class Steps implements PaintScan.Sleeper {

        private final Semaphore reached = new Semaphore(0);
        private final Semaphore allowed = new Semaphore(0);

        @Override
        public void sleep(long millis) throws InterruptedException {
            reached.release();
            allowed.acquire();
        }

        /** Waits until the scan sleeps again. */
        void awaitReached() {
            try {
                assertTrue(reached.tryAcquire(WAIT.toSeconds(), TimeUnit.SECONDS), "the scan reached a sleep");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                fail(e);
            }
        }

        void step() {
            allowed.release();
        }

        void releaseAll() {
            allowed.release(1_000);
        }
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
            return records(level).stream().map(LogRecord::getMessage).toList();
        }

        /** What was logged at exactly that level. */
        List<LogRecord> records(Level level) {
            synchronized (seen) {
                return seen.stream().filter(record -> record.getLevel().equals(level)).toList();
            }
        }
    }
}
