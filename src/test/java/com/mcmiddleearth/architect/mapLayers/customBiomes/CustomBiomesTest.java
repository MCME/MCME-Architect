package com.mcmiddleearth.architect.mapLayers.customBiomes;

import com.mcmiddleearth.architect.ArchitectPlugin;
import com.mcmiddleearth.architect.mapLayers.MapLayerConfig;
import com.mcmiddleearth.architect.mapLayers.MapLayers;
import com.mcmiddleearth.architect.mapLayers.MapShape;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.command.ConsoleCommandSenderMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;

import static org.junit.jupiter.api.Assertions.*;

// The layer wires the parts of its package: /architect maplayers scan <world> puts a world on, its saved region files
// are read in the background, loaded chunks are read live, and the shapes follow, at most every 10 seconds. Architect
// is loaded for its chat messages, with its own map layers stopped: each test starts a layer of its own, with a fake
// chunk reader, fake biome facts and test region files, and with its shapes built in place unless a test needs the
// layer's own thread. The region files are copies of resources/customBiomes/r.-1.-1.mca, 36 chunks at x and z -6 to -1
// painted with cbc:111g380-5p and partly mcme:custom_003; as r.0.0.mca the same chunks lie one region further on.
class CustomBiomesTest {

    private static final String CBC = "cbc:111g380-5p";
    private static final String QUAY = "mcme:custom_003";
    private static final Set<String> IDS = Set.of(CBC, QUAY);
    private static final String FIRST = "r.-1.-1.mca";
    private static final String SECOND = "r.0.0.mca";
    private static final Duration WAIT = Duration.ofSeconds(30);
    private static final PaintScan.Sleeper NO_SLEEP = millis -> {
    };
    private static final List<String> THREADS = List.of(CustomBiomes.SHAPES_THREAD, CustomBiomes.WRITER_THREAD,
            PaintScan.THREAD_NAME);

    private static ServerMock server;
    private static ArchitectPlugin plugin;
    private static ConsoleCommandSenderMock console;

    @TempDir
    Path dir;

    private CustomBiomes layer;
    private Logged logged;
    private final Map<Spot, ChunkPaint> paints = new HashMap<>();
    private final Map<String, String> labels = new HashMap<>();
    private final Set<Player> builders = new HashSet<>();
    private final BiomeFacts facts = new BiomeFacts() {
        @Override
        public String label(String biome) {
            return labels.get(biome);
        }

        @Override
        public Settings settings(String biome) {
            return null;
        }
    };

    /** A chunk of a world, as the fake reader is asked about it. */
    private record Spot(String world, int x, int z) {}

    @BeforeAll
    static void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.load(ArchitectPlugin.class);
        MapLayers.stop(); // Architect's own layers, its Custom biomes layer among them: each test starts one of its own
        server.addSimpleWorld("world");
        console = server.getConsoleSender();
    }

    @AfterAll
    static void tearDown() {
        if (MockBukkit.isMocked()) {
            MockBukkit.unmock();
        }
    }

    @BeforeEach
    void emptyConsole() {
        replies();
    }

    @AfterEach
    void stopTheLayer() {
        if (layer != null) {
            layer.stop();
        }
        if (logged != null) {
            plugin.getLogger().removeHandler(logged);
        }
    }

    private CustomBiomes layer(YamlConfiguration config, PaintScan.Sleeper sleeper, Executor background) {
        layer = new CustomBiomes(new MapLayerConfig(config).layer(CustomBiomes.KEY, false), dir.toFile(), this::read,
                player -> Optional.empty(), () -> IDS, facts, sleeper, w -> regionFolder(w.getName()),
                builders::contains, background);
        return layer;
    }

    private CustomBiomes layer(PaintScan.Sleeper sleeper) {
        return layer(new YamlConfiguration(), sleeper, Runnable::run);
    }

    /** So slow that a fill always waits out its pace after its first file, where a {@link Gate} can hold it. */
    private static YamlConfiguration slowScan() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("mapLayers.customBiomes.scanMegabytesPerSecond", 0.001);
        return config;
    }

    private ChunkPaint read(World readWorld, int x, int z) {
        return paints.getOrDefault(new Spot(readWorld.getName(), x, z), ChunkPaint.EMPTY);
    }

    private Path regionFolder(String worldName) {
        return dir.resolve("worlds").resolve(worldName).resolve("region");
    }

    private Path records() {
        return dir.resolve("mapLayers").resolve("customBiomes");
    }

    /** Copies the fixture into the world's region folder under each name. */
    private void regionFiles(String worldName, String... names) throws IOException {
        Path folder = Files.createDirectories(regionFolder(worldName));
        for (String name : names) {
            Files.copy(fixture(), folder.resolve(name), StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static Path fixture() throws IOException {
        URL url = CustomBiomesTest.class.getResource("/customBiomes/r.-1.-1.mca");
        assertNotNull(url, "the fixture is a test resource");
        try {
            return Path.of(url.toURI());
        } catch (URISyntaxException e) {
            throw new IOException(e);
        }
    }

    /** The fixture's painted chunks as the reader gives them, moved on by whole regions as a copy of it lies. */
    private static Map<Long, ChunkPaint> fixturePaints(int regionsX, int regionsZ) throws IOException {
        Map<Long, ChunkPaint> found = new HashMap<>();
        new RegionFileReader(IDS).read(fixture(), (x, z, paint) -> {
            if (!paint.isEmpty()) {
                found.put(PaintRecord.chunkKey(x + 32 * regionsX, z + 32 * regionsZ), paint);
            }
        });
        return found;
    }

    private static PaintRecord recordOf(Map<Long, ChunkPaint> chunks, boolean done) {
        PaintRecord record = new PaintRecord();
        chunks.forEach((key, paint) -> record.put(PaintRecord.keyX(key), PaintRecord.keyZ(key), paint));
        record.fillDone(done);
        return record;
    }

    /** A record saved before the layer starts, as a restart finds it. */
    private void saved(String worldName, PaintRecord record) throws IOException {
        record.save(records().resolve(worldName + ".dat"));
    }

    private static ChunkPaint paint(String biome) {
        return ChunkPaint.of(Collections.nCopies(16, List.of(new BiomeSpan(biome, -64, 319))));
    }

    /**
     * 4,000 single-column regions of 400 biomes, each biome with a long name of its own, a column apart so that none
     * touch: far over the size target.
     */
    private Map<Long, ChunkPaint> crowded() {
        Random random = new Random(20261028L);
        Map<Long, List<List<BiomeSpan>>> columns = new HashMap<>();
        for (int i = 0; i < 4_000; i++) {
            String biome = String.format("cbc:b%03d", i % 400);
            if (i < 400) {
                StringBuilder label = new StringBuilder();
                for (int letter = 0; letter < 250; letter++) {
                    label.append((char) ('a' + random.nextInt(26)));
                }
                labels.put(biome, label.toString());
            }
            int x = 2 * i;
            List<List<BiomeSpan>> sixteen = columns.computeIfAbsent(PaintRecord.chunkKey(Math.floorDiv(x, 4), 0),
                    key -> {
                        List<List<BiomeSpan>> empty = new ArrayList<>();
                        for (int column = 0; column < 16; column++) {
                            empty.add(new ArrayList<>());
                        }
                        return empty;
                    });
            sixteen.get(Math.floorMod(x, 4)).add(new BiomeSpan(biome, 0, 63));
        }
        Map<Long, ChunkPaint> chunks = new HashMap<>();
        columns.forEach((key, sixteen) -> chunks.put(key, ChunkPaint.of(sixteen)));
        return chunks;
    }

    /** The marker ids the layer gives a world's paint: one area per biome of each region. */
    private static List<String> idsOf(String worldName, Map<Long, ChunkPaint> chunks) {
        List<String> ids = new ArrayList<>();
        for (PaintRegions.Region region : PaintRegions.of(chunks).regions()) {
            for (String biome : region.biomes()) {
                ids.add("cb." + worldName + "." + region.firstX() + "." + region.firstZ() + "." + biome);
            }
        }
        return ids;
    }

    private List<String> ids() {
        return layer.shapes().stream().map(MapShape::id).toList();
    }

    private static void ticks(int count) {
        server.getScheduler().performTicks(count);
    }

    /** Everything said to the console since the last call. */
    private static List<String> replies() {
        List<String> lines = new ArrayList<>();
        for (String line = console.nextMessage(); line != null; line = console.nextMessage()) {
            lines.add(line);
        }
        return lines;
    }

    /** The one reply since the last call, which ends in {@code text}: before it is Architect's prefix. */
    private static void assertReply(String text) {
        List<String> lines = replies();
        assertEquals(1, lines.size(), lines.toString());
        assertTrue(lines.get(0).endsWith(text), lines.get(0));
    }

    private void scan(String... args) {
        layer.scanCommand(console, args);
    }

    private Logged capture() {
        logged = new Logged();
        plugin.getLogger().addHandler(logged);
        return logged;
    }

    /** The layer's own threads, and the scan's, that are alive now. */
    private static List<Thread> layerThreads() {
        return Thread.getAllStackTraces().keySet().stream()
                .filter(thread -> THREADS.contains(thread.getName()) && thread.isAlive()).toList();
    }

    @Test
    void aScannedWorldIsOnTheLayer() throws IOException {
        regionFiles("world", FIRST);
        layer(NO_SLEEP).start(plugin);

        scan("world");

        assertReply("Reading the saved world files of world in the background: 1 region file."
                + " /architect maplayers scan shows the progress.");
        assertTrue(layer.awaitScanIdle(WAIT), "the scan read the file");
        ticks(1); // what the file held reaches the record
        ticks(21); // the change is reported within a second, and the shapes come back a tick later
        assertEquals(idsOf("world", fixturePaints(0, 0)), ids());
    }

    @Test
    void scanWithNoWorldListsTheWorldsThatAreOn() throws IOException {
        regionFiles("world", FIRST);
        layer(NO_SLEEP).start(plugin);
        scan();
        assertReply("No world is on the Custom biomes layer. Start one with /architect maplayers scan <world>.");
        scan("world");
        assertTrue(layer.awaitScanIdle(WAIT));
        ticks(1);
        replies();

        scan();

        List<String> lines = replies();
        assertEquals(2, lines.size(), lines.toString());
        assertTrue(lines.get(0).endsWith("Custom biomes layer:"), lines.get(0));
        assertEquals("world: 36 chunks with custom biomes, up to date", lines.get(1));
    }

    @Test
    void aWorldOverTheTargetIsListedAsNotDrawn() throws IOException {
        saved("crowded", recordOf(crowded(), true));
        layer(NO_SLEEP).start(plugin);
        ticks(1); // the shapes built at the start come back

        scan();

        assertEquals(List.of(), layer.shapes(), "nothing of it is drawn");
        List<String> lines = replies();
        assertEquals(2, lines.size(), lines.toString());
        String prefix = "crowded: 2000 chunks with custom biomes, not drawn: its markers would add ";
        assertTrue(lines.get(1).startsWith(prefix) && lines.get(1).endsWith(" KB, over the 512 KB target"),
                lines.get(1));
        String kb = lines.get(1).substring(prefix.length(), lines.get(1).indexOf(" KB,"));
        assertTrue(Integer.parseInt(kb) > 512, kb);
    }

    @Test
    void scanOffSetsTheRecordAsideAndClearsTheMap() throws IOException {
        saved("world", recordOf(fixturePaints(0, 0), true));
        layer(NO_SLEEP).start(plugin);
        ticks(1);
        assertEquals(idsOf("world", fixturePaints(0, 0)), ids(), "drawn from the record at the start");

        scan("world", "off");

        assertReply("world is off the Custom biomes layer. Its record was set aside as world.dat.off.");
        assertEquals(List.of(), layer.shapes());
        scan();
        assertReply("No world is on the Custom biomes layer. Start one with /architect maplayers scan <world>.");
        layer.stop(); // the file is moved by the writer, which the stop waits for
        assertTrue(Files.exists(records().resolve("world.dat.off")));
        assertFalse(Files.exists(records().resolve("world.dat")), "and not saved again at the stop");
    }

    @Test
    void anUnknownWorldIsRefused() {
        layer(NO_SLEEP).start(plugin);

        scan("nowhere");
        assertReply("No loaded world is named nowhere.");
        scan("nowhere", "off");
        assertReply("No loaded world is named nowhere.");
        scan("world", "of");
        assertReply("Usage: /architect maplayers scan [<world> [off]]");
    }

    // The first slow pass reads every loaded chunk at the first tick. Paint that appears afterwards is found by the
    // builder check at tick 200, reported within a second, and drawn a tick later.
    @Test
    void livePaintReachesTheShapesWithinTenSeconds() throws IOException {
        WorldMock live = server.addSimpleWorld("live");
        for (int x = -2; x <= 2; x++) {
            for (int z = -2; z <= 2; z++) {
                live.getChunkAt(x, z);
            }
        }
        saved("live", recordOf(Map.of(), true));
        PlayerMock builder = server.addPlayer();
        builder.teleport(new Location(live, 8, 64, 8));
        builders.add(builder);
        layer(NO_SLEEP).start(plugin);
        ticks(10);
        paints.put(new Spot("live", 1, 1), paint("cbc:live"));

        ticks(189); // tick 199
        assertEquals(List.of(), layer.shapes(), "not before the builder check at tick 200");
        ticks(22);

        assertEquals(idsOf("live", Map.of(PaintRecord.chunkKey(1, 1), paint("cbc:live"))), ids());
    }

    // A builder walking while a world is read keeps the chunks around them loaded for a minute or so. A chunk that is
    // loaded when its saved file reaches the record, and that unloads before the tracker gets to it and before any slow
    // pass, is read as it unloads, and so it is in the record.
    @Test
    void aChunkLoadedDuringTheFillAndUnloadedBeforeAnyPassEndsUpInTheRecord() throws IOException {
        WorldMock walked = server.addSimpleWorld("walked");
        regionFiles("walked", FIRST);
        layer(NO_SLEEP).start(plugin);
        ticks(1); // the first slow pass, while the world is off: the next is two minutes away
        scan("walked");
        assertTrue(layer.awaitScanIdle(WAIT), "the file is read, and waits for the main thread");
        Chunk passing = walked.getChunkAt(-3, -4); // a builder comes by
        paints.put(new Spot("walked", -3, -4), paint("cbc:live"));

        ticks(1); // the file reaches the record
        server.getPluginManager().callEvent(new ChunkUnloadEvent(passing, true)); // and the builder walks on
        walked.unloadChunk(-3, -4);
        ticks(30); // the change is reported within a second, and the shapes come back a tick later

        Map<Long, ChunkPaint> expected = fixturePaints(0, 0);
        expected.put(PaintRecord.chunkKey(-3, -4), paint("cbc:live"));
        assertEquals(idsOf("walked", expected), ids(), "the chunk as it was when it unloaded");
    }

    @Test
    void theLayerIsFiledUnderDebugAndHiddenByDefault() {
        CustomBiomes defaults = new CustomBiomes(new MapLayerConfig(new YamlConfiguration())
                .layer(CustomBiomes.KEY, false), dir.toFile());
        YamlConfiguration shown = new YamlConfiguration();
        shown.set("mapLayers.customBiomes.hidden", false);

        assertEquals("architect.debug.biomes", defaults.markerSetId());
        assertTrue(defaults.markerSetId().contains("debug"), "LiveAtlas files it under Debug");
        assertEquals("Custom biomes", defaults.label());
        assertTrue(defaults.hiddenByDefault());
        assertFalse(new CustomBiomes(new MapLayerConfig(shown).layer(CustomBiomes.KEY, false), dir.toFile())
                .hiddenByDefault());
    }

    @Test
    void stopSavesTheRecordsAndStopsTheFill() throws IOException, InterruptedException {
        regionFiles("world", FIRST, SECOND);
        Gate gate = new Gate();
        layer(slowScan(), gate, Runnable::run).start(plugin);
        scan("world");
        gate.awaitReached(); // the first file is read and handed over, and the fill waits out its pace
        ticks(1);

        layer.stop();

        gate.thread.join(WAIT.toMillis());
        assertFalse(gate.thread.isAlive(), "the fill stopped");
        PaintRecord saved = PaintRecord.load(records().resolve("world.dat"));
        assertEquals(fixturePaints(0, 0), saved.chunks());
        assertEquals(Set.of(FIRST), saved.finishedFiles(), "the next start goes on from the second file");
        assertFalse(saved.fillDone());
    }

    @Test
    void aWorldBeingReadOrWaitingIsLeftAsItIs() throws IOException {
        server.addSimpleWorld("other");
        regionFiles("world", FIRST, SECOND);
        regionFiles("other", FIRST);
        Gate gate = new Gate();
        layer(slowScan(), gate, Runnable::run).start(plugin);
        scan("world");
        gate.awaitReached();
        ticks(1);
        replies();

        scan("world");
        assertReply("world is being read already: 1 of 2 region files.");
        scan("other");
        assertReply("other will be read after the world being read now.");
        scan("other");
        assertReply("other will be read after the world being read now.");

        scan();
        List<String> lines = replies();
        assertEquals(3, lines.size(), lines.toString());
        assertEquals("other: 0 chunks with custom biomes, waiting to be read", lines.get(1));
        assertEquals("world: 36 chunks with custom biomes, reading 1 of 2 region files", lines.get(2),
                "what the first file held stays: the world was not put on again");
    }

    // Architect starts before the server loads its worlds, so a fill that a restart stopped goes on at the first tick.
    @Test
    void anUnfinishedFillGoesOnOnceTheWorldsAreLoaded() throws IOException {
        regionFiles("later", FIRST, SECOND);
        PaintRecord half = recordOf(fixturePaints(0, 0), false);
        half.finished(FIRST);
        saved("later", half);
        layer(NO_SLEEP).start(plugin);
        server.addSimpleWorld("later");

        ticks(1);
        assertTrue(layer.awaitScanIdle(WAIT));
        ticks(1);

        scan();
        List<String> lines = replies();
        assertEquals(2, lines.size(), lines.toString());
        assertEquals("later: 72 chunks with custom biomes, up to date", lines.get(1), "the second file was read");
    }

    // WorldEdit and FastAsyncWorldEdit enable after Architect, so they are looked up when a selection is first asked
    // for, after a WorldEdit command, and the log says once what was found.
    @Test
    void worldEditIsLookedUpWhenASelectionIsFirstAskedFor() {
        Logged log = capture();
        PlayerMock player = server.addPlayer();
        SelectionSource selections = new CustomBiomes.FirstUseSelections(plugin); // made at the start
        Plugin fawe = MockBukkit.createMockPlugin("FastAsyncWorldEdit");
        try {
            assertEquals(List.of(), log.lookups(), "nothing is looked up before it is needed");

            assertEquals(Optional.empty(), selections.chunks(player), "no WorldEdit session in this test");
            selections.chunks(player);

            assertEquals(List.of("Custom biomes layer: the selections of WorldEdit commands are read through "
                    + "FastAsyncWorldEdit."), log.lookups(), "looked up once");
        } finally {
            server.getPluginManager().disablePlugin(fawe);
        }
        new CustomBiomes.FirstUseSelections(plugin).chunks(player);
        assertEquals("Custom biomes layer: neither WorldEdit nor FastAsyncWorldEdit is enabled, so the selections of "
                + "WorldEdit commands are not read.", log.lookups().get(1));
    }

    @Test
    void aScanRateNotAboveZeroFallsBackToTwenty() throws IOException {
        Logged log = capture();
        regionFiles("world", FIRST);
        YamlConfiguration notANumber = new YamlConfiguration();
        notANumber.set("mapLayers.customBiomes.scanMegabytesPerSecond", Double.NaN);
        layer(notANumber, NO_SLEEP, Runnable::run).start(plugin);
        layer.stop();
        YamlConfiguration zero = new YamlConfiguration();
        zero.set("mapLayers.customBiomes.scanMegabytesPerSecond", 0);
        layer(zero, NO_SLEEP, Runnable::run).start(plugin);

        scan("world");

        assertReply("Reading the saved world files of world in the background: 1 region file."
                + " /architect maplayers scan shows the progress.");
        assertTrue(layer.awaitScanIdle(WAIT));
        assertEquals(List.of(
                "Custom biomes layer: scanMegabytesPerSecond must be above 0, not NaN, so the scan reads at most 20 MB "
                        + "a second.",
                "Custom biomes layer: scanMegabytesPerSecond must be above 0, not 0.0, so the scan reads at most 20 MB "
                        + "a second."), log.messages(Level.WARNING));
    }

    // The shapes thread, the writer and the scan thread all end with the layer, so that after /architect reload the
    // old layer never writes a file the new one uses, and nothing runs once Architect is disabled.
    @Test
    void theLayersThreadsEndWhenItStops() throws IOException, InterruptedException {
        regionFiles("world", FIRST);
        layer(new YamlConfiguration(), NO_SLEEP, null).start(plugin); // the shapes on a thread, as on a server
        scan("world");
        assertTrue(layer.awaitScanIdle(WAIT));
        long deadline = System.nanoTime() + WAIT.toNanos();
        while (layer.shapes().isEmpty()) {
            assertTrue(System.nanoTime() < deadline, "the shapes were built in time");
            ticks(1);
            Thread.sleep(5);
        }
        assertEquals(Set.of(CustomBiomes.SHAPES_THREAD, PaintScan.THREAD_NAME),
                Set.copyOf(layerThreads().stream().map(Thread::getName).toList()));

        layer.stop();

        for (Thread thread : layerThreads()) {
            thread.join(WAIT.toMillis());
            assertFalse(thread.isAlive(), thread.getName() + " is still running");
        }
        assertTrue(Files.exists(records().resolve("world.dat")), "saved by the writer, which the stop waited for");
    }

    @Test
    void aWorldThatIsOnButNotLoadedCanBeSwitchedOff() throws IOException {
        saved("gone", recordOf(fixturePaints(0, 0), true));
        layer(NO_SLEEP).start(plugin);

        scan("gone", "off");

        assertReply("gone is off the Custom biomes layer. Its record was set aside as gone.dat.off.");
        scan("gone", "off");
        assertReply("No loaded world is named gone.");
    }

    // A world is saved for the first time up to a minute after it was put on.
    @Test
    void aWorldSwitchedOffBeforeItsFirstSaveIsOffAllTheSame() {
        layer(NO_SLEEP).start(plugin);
        scan("world");
        replies();

        scan("world", "off");

        assertReply("world is off the Custom biomes layer.");
        scan("world", "off");
        assertReply("world is not on the Custom biomes layer.");
    }

    /** Holds the scan at its first sleep until released, or until a stop's interrupt ends the wait. */
    private static final class Gate implements PaintScan.Sleeper {

        private final CountDownLatch reached = new CountDownLatch(1);
        private final CountDownLatch released = new CountDownLatch(1);
        private volatile Thread thread;

        @Override
        public void sleep(long millis) throws InterruptedException {
            thread = Thread.currentThread();
            reached.countDown();
            released.await();
        }

        void awaitReached() {
            try {
                assertTrue(reached.await(WAIT.toSeconds(), TimeUnit.SECONDS), "the scan reached its first sleep");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                fail(e);
            }
        }
    }

    /** What Architect's logger was asked to log. */
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

        List<String> messages(Level level) {
            synchronized (seen) {
                return seen.stream().filter(record -> record.getLevel().equals(level)).map(LogRecord::getMessage)
                        .toList();
            }
        }

        /** The lines that say which WorldEdit was found. */
        List<String> lookups() {
            return messages(Level.INFO).stream().filter(message -> message.startsWith("Custom biomes layer:")
                    && message.contains("WorldEdit")).toList();
        }
    }
}
