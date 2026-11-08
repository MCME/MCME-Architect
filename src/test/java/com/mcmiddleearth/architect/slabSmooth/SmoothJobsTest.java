package com.mcmiddleearth.architect.slabSmooth;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mcmiddleearth.architect.ArchitectPlugin;
import com.mcmiddleearth.architect.Permission;
import com.mcmiddleearth.architect.PluginData;
import com.mcmiddleearth.architect.slabSmooth.core.GoldenScenes;
import com.mcmiddleearth.architect.slabSmooth.core.Options;
import com.mcmiddleearth.architect.slabSmooth.core.Table;
import com.mcmiddleearth.architect.slabSmooth.core.TestTables;
import com.mcmiddleearth.architect.slabSmooth.core.Tiles;
import com.mcmiddleearth.pluginutil.message.MessageUtil;
import com.sk89q.worldedit.MaxChangedBlocksException;
import com.sk89q.worldedit.math.BlockVector2;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.math.Vector2;
import com.sk89q.worldedit.math.Vector3;
import com.sk89q.worldedit.regions.CuboidRegion;
import com.sk89q.worldedit.regions.CylinderRegion;
import com.sk89q.worldedit.regions.EllipsoidRegion;
import com.sk89q.worldedit.regions.Polygonal2DRegion;
import com.sk89q.worldedit.regions.Region;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockbukkit.mockbukkit.world.WorldMock;

import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * SmoothJobs on MockBukkit worlds and players, with FakeSmoothWorldEdit, a fake AreaSource and fake tables. The main
 * thread and the worker are queues the test runs: main-thread tasks on the test's own thread (MockBukkit's main
 * thread), worker tasks on a thread named {@link #WORKER}. The terrain is the hillside golden scene, on stone, under
 * air; the clock only moves when a test moves it. One mock and load per class: Architect keeps its data folder in
 * static fields.
 */
class SmoothJobsTest {

    private static final String WORKER = "SmoothJobsTest worker";
    private static final String STONE = "minecraft:stone", AIR = "minecraft:air", PLANKS = "minecraft:oak_planks";
    private static final long MS = 1_000_000L, SECOND = 1_000_000_000L;
    /** Where the hillside's lowest corner is. */
    private static final int OX = 100, OY = 60, OZ = -40;
    private static final String FAILED = "Something went wrong; the server log has the details.";
    private static final String BUDGET = "This area is too much work to smooth, so nothing was changed. Try a smaller selection.";
    private static final String SECOND_JOB = "Another smoothing is running; try again in a moment.";

    private static ServerMock server;
    private static ArchitectPlugin plugin;
    private static WorldMock world, elsewhere;
    private static GoldenScenes.Scene hillside;
    private static Table handMade;
    /** The margin a job with the default options reads around its selection. */
    private static int margin;

    private final ArrayDeque<Runnable> mainQueue = new ArrayDeque<>(), workerQueue = new ArrayDeque<>();
    private volatile boolean mainRefuses;
    private final AtomicLong clock = new AtomicLong(5 * SECOND);
    /** How far the clock moves each time it is read on the worker, where the algorithm reads it. */
    private volatile long workerClockStep;
    /** Thrown by the next read of the clock on the worker: the algorithm's first look at its work budget. */
    private volatile Throwable workerClockFailure;
    private final List<LogRecord> logged = Collections.synchronizedList(new ArrayList<>());
    private final List<LinkageError> broken = new ArrayList<>();
    private final List<String> wrongThread = Collections.synchronizedList(new ArrayList<>());
    private final Map<UUID, Player> online = new HashMap<>();
    private final Set<UUID> unloaded = new HashSet<>();
    private Thread mainThread;
    private FakeSmoothWorldEdit worldEdit;
    private Areas areas;
    private Tables tables;
    private SlabSmoothSettings settings;
    private PlayerMock builder;
    private SmoothJobs jobs;

    @BeforeAll
    static void startTheServer() throws Exception {
        server = MockBukkit.mock();
        plugin = MockBukkit.load(ArchitectPlugin.class);
        world = addWorld("slabsmooth");
        elsewhere = addWorld("elsewhere");
        hillside = GoldenScenes.scene("hillside");
        handMade = TestTables.handMade();
        margin = Tiles.margin(new Options());
    }

    @AfterAll
    static void stopTheServer() {
        if (MockBukkit.isMocked()) {
            MockBukkit.unmock();
        }
    }

    private static WorldMock addWorld(String name) {
        WorldMock added = new WorldMock(Material.STONE, -64, 320, 0);
        added.setName(name);
        server.addWorld(added);
        return added;
    }

    @BeforeEach
    void setUp() {
        mainThread = Thread.currentThread();
        tables = new Tables().with("Human", handMade, handMade);
        settings = SlabSmoothSettings.DEFAULTS;
        builder = newPlayer(world);
        fresh();
    }

    @AfterEach
    void everythingRanOnItsThread() {
        assertEquals(List.of(), wrongThread, "calls made on the wrong thread");
    }

    /** New fakes and new jobs, on the untouched hillside. */
    private void fresh() {
        worldEdit = new FakeSmoothWorldEdit();
        worldEdit.base = SmoothJobsTest::terrain;
        areas = new Areas();
        mainQueue.clear();
        workerQueue.clear();
        logged.clear();
        Logger log = Logger.getAnonymousLogger();
        log.setUseParentHandlers(false);
        log.addHandler(new Handler() {
            @Override
            public void publish(LogRecord record) {
                logged.add(record);
            }

            @Override
            public void flush() {
            }

            @Override
            public void close() {
            }
        });
        jobs = new SmoothJobs(log, () -> tables, () -> settings, areas, worldEdit,
                task -> {
                    if (mainRefuses) {
                        throw new RejectedExecutionException("the plugin is disabled");
                    }
                    mainQueue.add(task);
                },
                workerQueue::add, this::now,
                player -> {
                    onTheMainThread("looking up a player");
                    return online.get(player);
                },
                uid -> {
                    onTheMainThread("looking up a world");
                    return unloaded.contains(uid) ? null : server.getWorld(uid);
                },
                error -> {
                    onTheMainThread("reporting a broken WorldEdit");
                    broken.add(error);
                });
    }

    private PlayerMock newPlayer(World in) {
        PlayerMock player = server.addPlayer();
        player.teleport(new Location(in, OX + 20, OY + 30, OZ + 20));
        online.put(player.getUniqueId(), player);
        return player;
    }

    private void onTheMainThread(String what) {
        if (Thread.currentThread() != mainThread) {
            wrongThread.add(what + " on " + Thread.currentThread().getName());
        }
    }

    private long now() {
        if (Thread.currentThread().getName().equals(WORKER)) {
            Throwable failure = workerClockFailure;
            if (failure != null) {
                workerClockFailure = null;
                throw unchecked(failure);
            }
            return clock.addAndGet(workerClockStep);
        }
        return clock.get();
    }

    private static RuntimeException unchecked(Throwable failure) {
        if (failure instanceof RuntimeException e) {
            throw e;
        }
        if (failure instanceof Error e) {
            throw e;
        }
        throw new AssertionError("a fake can only throw unchecked failures", failure);
    }

    // ---------------------------------------------------------------------------------------------------------
    // The terrain, the fakes and running the queues

    /** The test terrain: the hillside golden scene with its lowest corner at OX, OY, OZ, on stone and under air. */
    private static String terrain(BlockVector3 p) {
        int x = p.x() - OX, y = p.y() - OY, z = p.z() - OZ;
        if (y < 0) {
            return STONE;
        }
        if (y >= hillside.height() || x < 0 || z < 0 || x >= hillside.width() || z >= hillside.length()) {
            return AIR;
        }
        return hillside.palette().get(hillside.blocks()[(y * hillside.length() + z) * hillside.width() + x]);
    }

    /** The hillside's full height, with the margin reaching its sides: the job reads all of it, the stone below and the air above. */
    private static CuboidRegion selection() {
        return new CuboidRegion(BlockVector3.at(OX + margin, OY, OZ + margin),
                BlockVector3.at(OX + hillside.width() - 1 - margin, OY + hillside.height() - 1, OZ + hillside.length() - 1 - margin));
    }

    /** Reads areas from the fake WorldEdit's blocks. A load ends when the test says Paper has read the chunks. */
    private final class Areas implements AreaSource {
        CompletableFuture<LoadedArea> pending;
        World world;
        BlockVector3 min, max;
        int loads, releases;
        Throwable failAtOnce, failRead;

        @Override
        public CompletableFuture<LoadedArea> load(World world, BlockVector3 min, BlockVector3 max) {
            onTheMainThread("starting a load");
            loads++;
            if (failAtOnce != null) {
                throw unchecked(failAtOnce);
            }
            this.world = world;
            this.min = min;
            this.max = max;
            return pending = new CompletableFuture<>();
        }

        /** Paper has read the chunks: the load completes, on the main thread as ChunkAreaSource's does. */
        void finishLoading() {
            pending.complete(new Loaded(world, min, max));
        }

        private final class Loaded implements LoadedArea {
            private final World world;
            private final BlockVector3 min, max;

            Loaded(World world, BlockVector3 min, BlockVector3 max) {
                this.world = world;
                this.min = min;
                this.max = max;
            }

            @Override
            public WorldArea read() {
                if (failRead != null) {
                    throw unchecked(failRead);
                }
                WorldArea area = WorldArea.box(min, max, world.getMinHeight(), world.getMaxHeight() - 1);
                int i = 0;
                for (int y = 0; y < area.height; y++) {
                    for (int z = 0; z < area.length; z++) {
                        for (int x = 0; x < area.width; x++) {
                            area.blocks[i++] = area.id(worldEdit.stateAt(area.min.add(x, y, z)));
                        }
                    }
                }
                return area;
            }

            @Override
            public void release() {
                onTheMainThread("releasing the chunks");
                releases++;
                worldEdit.events.add("release");
            }
        }
    }

    /** Tables by pack, and RP regions by location. Where no region has a pack, the normal choice is the default, Human. */
    private static final class Tables implements TableSource {
        final Map<String, Table> terrain = new HashMap<>(), all = new HashMap<>();
        Function<Location, String> regions = location -> null;
        String normal = "Human";
        IllegalStateException notReady;
        final List<String> asked = new ArrayList<>();

        Tables with(String pack, Table terrainTable, Table allTable) {
            terrain.put(pack, terrainTable);
            all.put(pack, allTable);
            return this;
        }

        @Override
        public Packs.Choice choose(Player player, Location location, String named) {
            if (named != null) {
                return new Packs.Choice(named, "set");
            }
            String region = location == null ? null : regions.apply(location);
            return region != null ? new Packs.Choice(region, "region") : new Packs.Choice(normal, "default");
        }

        @Override
        public String regionPack(Location location) {
            return regions.apply(location);
        }

        @Override
        public Table table(Packs.Choice choice, boolean allBlocks) {
            asked.add(choice.pack() + (allBlocks ? " with blocks all" : ""));
            if (notReady != null) {
                throw notReady;
            }
            Table table = (allBlocks ? all : terrain).get(choice.pack());
            if (table == null) {
                throw new IllegalStateException("The " + choice.pack() + " pack (" + choice.why() + ") can't be used: no such pack");
            }
            return table;
        }
    }

    private static SlabSmoothSettings limits(int maxColumns, long maxCells, long maxSeconds) {
        SlabSmoothSettings d = SlabSmoothSettings.DEFAULTS;
        return new SlabSmoothSettings(d.defaultPack(), d.download(), d.terrainCategories(), d.plantCategories(),
                d.lookTolerance(), d.clientJar(), d.overrides(), d.maxBrushRadius(), maxColumns, maxCells, maxSeconds,
                d.defaults());
    }

    /** Runs what the main thread has been given so far, as one tick does. */
    private void tick() {
        for (int n = mainQueue.size(); n > 0; n--) {
            mainQueue.poll().run();
        }
    }

    /** Runs what the worker has been given, on a thread of its own. */
    private void work() throws InterruptedException {
        AtomicReference<Throwable> escaped = new AtomicReference<>();
        Thread worker = startWorker(escaped);
        worker.join();
        if (escaped.get() != null) {
            throw new AssertionError("a worker task let this through", escaped.get());
        }
    }

    private Thread startWorker(AtomicReference<Throwable> escaped) {
        Thread worker = new Thread(() -> {
            for (Runnable task; (task = workerQueue.poll()) != null; ) {
                task.run();
            }
        }, WORKER);
        worker.setUncaughtExceptionHandler((thread, e) -> escaped.set(e));
        worker.start();
        return worker;
    }

    /** Runs the main thread and the worker in turn until the job has ended and nothing is left to run. */
    private void runOut() throws InterruptedException {
        for (int round = 0; round < 10 && (jobs.running() || !mainQueue.isEmpty() || !workerQueue.isEmpty()); round++) {
            tick();
            work();
        }
        assertFalse(jobs.running(), "the job has ended");
        assertTrue(mainQueue.isEmpty() && workerQueue.isEmpty(), "nothing is left to run");
    }

    /** Paper reads the area, and the job runs to its end. */
    private void finish() throws InterruptedException {
        areas.finishLoading();
        runOut();
    }

    /** What a job of this region changes on the untouched hillside. The fakes and the jobs start again afterwards. */
    private Map<BlockVector3, String> setsOfAJob(Region region) throws Exception {
        jobs.start(builder, world, region, null, true, new Options());
        finish();
        Map<BlockVector3, String> sets = new LinkedHashMap<>(worldEdit.sets);
        assertFalse(sets.isEmpty(), "the job changed something");
        told(builder);
        fresh();
        return sets;
    }

    /** What a player was told since last asked: "info: ..." or "error: ...", without Architect's prefix. */
    private static List<String> told(PlayerMock player) {
        MessageUtil util = PluginData.getMessageUtil();
        String error = String.valueOf(util.ERROR), info = String.valueOf(util.INFO), prefix = util.getPREFIX();
        List<String> out = new ArrayList<>();
        for (String message = player.nextMessage(); message != null; message = player.nextMessage()) {
            String kind = message.startsWith(error) ? "error: " : message.startsWith(info) ? "info: " : "unknown: ";
            String text = message.replaceAll(ChatColor.COLOR_CHAR + ".", "");
            out.add(kind + (text.startsWith(prefix) ? text.substring(prefix.length()) : text));
        }
        return out;
    }

    /** The one thing a player was told since last asked. */
    private static String toldOnce(PlayerMock player) {
        List<String> lines = told(player);
        assertEquals(1, lines.size(), "one message: " + lines);
        return lines.get(0);
    }

    private void assertNoStackTrace() {
        assertEquals(List.of(), logged.stream().filter(r -> r.getThrown() != null).map(r -> r.getMessage() + ": " + r.getThrown()).toList(),
                "logged with a stack trace");
    }

    private void assertLoggedWithItsCause(Throwable failure) {
        assertTrue(logged.stream().anyMatch(r -> r.getLevel().intValue() >= Level.WARNING.intValue() && r.getThrown() == failure),
                "a warning with the cause: "
                        + logged.stream().map(r -> r.getLevel() + " " + r.getMessage() + " " + r.getThrown()).toList());
    }

    private List<String> threads(String method) {
        return worldEdit.calls(method).stream().map(FakeSmoothWorldEdit.Call::thread).toList();
    }

    // ---------------------------------------------------------------------------------------------------------
    // Rule 1: a job writes only to the world it read

    @Test
    void aSelectionWritesToTheWorldItRead() throws Exception {
        jobs.start(builder, world, selection(), null, true, new Options());
        builder.teleport(new Location(elsewhere, 0, 70, 0)); // away and back before it writes: only where they are then counts
        areas.finishLoading();
        work();
        builder.teleport(new Location(world, OX, OY + 30, OZ));
        runOut();

        assertSame(world, areas.world, "it read the world it was given");
        List<FakeSmoothWorldEdit.Call> opened = worldEdit.calls("open");
        assertEquals(1, opened.size());
        assertSame(world, opened.get(0).world(), "it wrote to the world it read");
        assertFalse(worldEdit.sets.isEmpty(), "the hillside has steps to smooth");
        String summary = toldOnce(builder);
        assertTrue(summary.startsWith("info: Smoothed ") && summary.endsWith(" [Human pack] (0 ms). //undo reverts it."), summary);
    }

    @Test
    void aSelectionIsDroppedIfThePlayerLeftThatWorld() throws Exception {
        jobs.start(builder, world, selection(), null, true, new Options());
        areas.finishLoading();
        work();
        builder.teleport(new Location(elsewhere, 0, 70, 0));
        runOut();

        assertEquals(List.of("error: You left the world before the smoothing finished, so nothing was changed."), told(builder));
        assertEquals(List.of(), worldEdit.calls("open"), "nothing was written, in either world");
        assertEquals(1, areas.releases);
    }

    @Test
    void anAreaJobWritesToItsNamedWorldWhereverThePlayerStands() throws Exception {
        builder.teleport(new Location(elsewhere, 0, 70, 0));
        jobs.start(builder, world, selection(), null, false, new Options());
        finish();

        List<FakeSmoothWorldEdit.Call> opened = worldEdit.calls("open");
        assertEquals(1, opened.size());
        assertSame(world, opened.get(0).world());
        assertSame(builder, opened.get(0).player(), "the edit is the player's, so their //undo reverts it");
        assertFalse(worldEdit.sets.isEmpty());
        assertTrue(toldOnce(builder).startsWith("info: Smoothed "));
    }

    // ---------------------------------------------------------------------------------------------------------
    // Rule 2: a job never overwrites what changed after it read

    @Test
    void aBlockChangedDuringTheJobIsLeftAloneAndTheSummarySaysSo() throws Exception {
        List<BlockVector3> changes = new ArrayList<>(setsOfAJob(selection()).keySet());
        BlockVector3 first = changes.get(0), last = changes.get(changes.size() - 1);
        jobs.start(builder, world, selection(), null, true, new Options());
        areas.finishLoading();
        work(); // read and smoothed
        worldEdit.blocks.put(first, PLANKS); // a builder places two blocks where the job is about to write
        worldEdit.blocks.put(last, PLANKS);
        runOut();

        assertEquals(PLANKS, worldEdit.stateAt(first));
        assertEquals(PLANKS, worldEdit.stateAt(last));
        assertFalse(worldEdit.sets.containsKey(first) || worldEdit.sets.containsKey(last));
        assertEquals(changes.size() - 2, worldEdit.sets.size(), "everything else was written");
        String summary = toldOnce(builder);
        assertTrue(summary.endsWith("ms). //undo reverts it. 2 blocks changed while it ran were left alone."), summary);
    }

    // ---------------------------------------------------------------------------------------------------------
    // Rule 3: no lag; on FAWE the write goes off the main thread

    @Test
    void onFaweOpenWriteAndCloseRunOnTheWorker() throws Exception {
        worldEdit.offTheMainThread = true;
        jobs.start(builder, world, selection(), null, true, new Options());
        finish();

        assertEquals(List.of(WORKER), threads("open"));
        assertEquals(List.of(WORKER), threads("close"));
        assertEquals(Set.of(WORKER), worldEdit.setThreads);
        assertEquals(Set.of(WORKER), worldEdit.readThreads);
        assertTrue(toldOnce(builder).startsWith("info: Smoothed "));
    }

    @Test
    void onPlainWorldEditTheyRunOnTheMainThread() throws Exception {
        jobs.start(builder, world, selection(), null, true, new Options());
        finish();

        String main = mainThread.getName();
        assertEquals(List.of(main), threads("open"));
        assertEquals(List.of(main), threads("close"));
        assertEquals(Set.of(main), worldEdit.setThreads);
        assertEquals(Set.of(main), worldEdit.readThreads);
        assertTrue(toldOnce(builder).startsWith("info: Smoothed "));
    }

    @ParameterizedTest(name = "off the main thread: {0}")
    @ValueSource(booleans = {true, false})
    void chunksAreReleasedOnTheMainThreadAfterClose(boolean fawe) throws Exception {
        worldEdit.offTheMainThread = fawe;
        jobs.start(builder, world, selection(), null, true, new Options());
        finish();

        assertEquals(List.of("open", "close", "release"), worldEdit.events, "what WorldEdit buffered is written before the chunks go");
        assertEquals(1, areas.releases); // on the main thread: Areas checks
    }

    // ---------------------------------------------------------------------------------------------------------
    // Rule 4: memory and work stay bounded

    @Test
    void aSecondJobIsRefusedUntilTheFirstEnds() throws Exception {
        jobs.start(builder, world, selection(), null, true, new Options());
        PlayerMock other = newPlayer(world);
        for (PlayerMock who : List.of(builder, other)) {
            IllegalStateException refused = assertThrows(IllegalStateException.class,
                    () -> jobs.start(who, world, selection(), null, true, new Options()));
            assertEquals(SECOND_JOB, refused.getMessage());
        }
        assertEquals(1, areas.loads, "a refused job reads nothing");
        finish();

        jobs.start(other, world, selection(), null, true, new Options());
        assertTrue(jobs.running());
        assertEquals(2, areas.loads);
        finish();
        assertTrue(toldOnce(other).startsWith("info: Smoothed "));
    }

    @Test
    void tooManyColumnsAreRefusedBeforeReading() {
        int columns = hillside.width() * hillside.length(); // with the margin, the selection reads the whole scene
        settings = limits(columns - 1, SlabSmoothSettings.DEFAULTS.maxCells(), 120);

        IllegalArgumentException refused = assertThrows(IllegalArgumentException.class,
                () -> jobs.start(builder, world, selection(), null, true, new Options()));

        assertEquals("Selection too big: " + columns + " columns with the margin, max " + (columns - 1) + ".", refused.getMessage());
        assertEquals(0, areas.loads);
        assertFalse(jobs.running());
        settings = limits(columns, SlabSmoothSettings.DEFAULTS.maxCells(), 120);
        jobs.start(builder, world, selection(), null, true, new Options());
        assertEquals(1, areas.loads, "exactly at the limit is allowed");
    }

    @Test
    void anAreaTooWideToCountInAnIntIsStillRefused() {
        // /sls area -2000000000 60 0 2000000000 60 0: 4,000,000,001 blocks wide, more columns than an int can count
        Region wide = new CuboidRegion(BlockVector3.at(-2_000_000_000, 60, 0), BlockVector3.at(2_000_000_000, 60, 0));
        long columns = (4_000_000_001L + 2 * margin) * (1 + 2 * margin);

        IllegalArgumentException refused = assertThrows(IllegalArgumentException.class,
                () -> jobs.start(builder, world, wide, null, false, new Options()));

        assertEquals("Selection too big: " + columns + " columns with the margin, max " + settings.maxColumns() + ".",
                refused.getMessage());
        assertEquals(0, areas.loads);
    }

    @Test
    void tooManyCellsAreRefusedBeforeReading() {
        // one column from far below the world to far above it: 11 x 11 columns with the margin, and only the world's 384 layers
        Region tall = new CuboidRegion(BlockVector3.at(OX, -1000, OZ), BlockVector3.at(OX, 1000, OZ));
        int side = 1 + 2 * margin;
        long cells = (long) side * side * (world.getMaxHeight() - world.getMinHeight());
        settings = limits(SlabSmoothSettings.DEFAULTS.maxColumns(), cells - 1, 120);

        IllegalArgumentException refused = assertThrows(IllegalArgumentException.class,
                () -> jobs.start(builder, world, tall, null, true, new Options()));

        assertEquals("Selection too big: " + cells + " blocks with the margin, max " + (cells - 1) + ".", refused.getMessage());
        assertEquals(0, areas.loads);
        assertFalse(jobs.running());
        settings = limits(SlabSmoothSettings.DEFAULTS.maxColumns(), cells, 120);
        jobs.start(builder, world, tall, null, true, new Options());
        assertEquals(1, areas.loads, "exactly at the limit is allowed");
    }

    @Test
    void aSelectionAboveTheWorldIsRefusedBeforeReading() {
        Region high = new CuboidRegion(BlockVector3.at(OX, 400, OZ), BlockVector3.at(OX + 10, 410, OZ + 10));

        IllegalArgumentException refused = assertThrows(IllegalArgumentException.class,
                () -> jobs.start(builder, world, high, null, true, new Options()));

        assertEquals("The selection is outside the world's height.", refused.getMessage());
        assertEquals(0, areas.loads);
        assertFalse(jobs.running());
    }

    @ParameterizedTest(name = "surfaces {0}")
    @ValueSource(strings = {"top", "all"})
    void aJobOverItsWorkBudgetChangesNothingAndSaysSo(String surfaces) throws Exception {
        Options options = new Options();
        options.set("surfaces", surfaces);
        Region region;
        int columns;
        if (surfaces.equals("top")) {
            // flat ground under 310 x 310 columns, 320 x 320 with the margin: the top smoother counts every column
            worldEdit.base = p -> p.y() <= 65 ? STONE : AIR;
            region = new CuboidRegion(BlockVector3.at(0, 64, 0), BlockVector3.at(309, 66, 309));
            columns = 320 * 320;
        } else {
            // stone in layers of five with air between: a tile's upward pass has some 30 surfaces per column
            worldEdit.base = p -> Math.floorMod(p.y(), 6) == 5 ? AIR : STONE;
            region = new CuboidRegion(BlockVector3.at(0, 0, 0), BlockVector3.at(63, 179, 63));
            columns = 74 * 74;
        }
        settings = limits(200_000, SlabSmoothSettings.DEFAULTS.maxCells(), 120);
        jobs.start(builder, world, region, null, true, options);
        finish();

        assertEquals(List.of("info: Smoothing " + columns + " columns...", "error: " + BUDGET), told(builder));
        assertEquals(List.of(), worldEdit.calls("open"), "nothing changed");
        assertEquals(1, areas.releases);
        assertNoStackTrace();
        assertTrue(logged.stream().anyMatch(r -> r.getMessage().contains("more than " + SmoothJobs.MAX_SURFACES_PER_PASS + " surfaces")),
                "the log says which limit: " + logged.stream().map(LogRecord::getMessage).toList());
    }

    @Test
    void aJobOverItsTimeChangesNothingAndSaysSo() throws Exception {
        settings = limits(SlabSmoothSettings.DEFAULTS.maxColumns(), SlabSmoothSettings.DEFAULTS.maxCells(), 5);
        workerClockStep = SECOND; // every look at the clock while smoothing finds another second gone
        jobs.start(builder, world, selection(), null, true, new Options());
        finish();

        assertEquals(List.of("error: " + BUDGET), told(builder));
        assertEquals(List.of(), worldEdit.calls("open"), "nothing changed");
        assertEquals(1, areas.releases);
        assertNoStackTrace();
        assertTrue(logged.stream().anyMatch(r -> r.getMessage().contains("time limit")),
                "the log says which limit: " + logged.stream().map(LogRecord::getMessage).toList());
    }

    @Test
    void aLoadThatNeverCompletesEndsAtTheDeadlineAndReleasesTheLock() throws Exception {
        jobs.start(builder, world, selection(), null, true, new Options());
        for (int i = 0; i < 5; i++) {
            tick();
            clock.addAndGet(SECOND);
        }
        assertTrue(jobs.running(), "within its time it waits for Paper");
        clock.addAndGet(TimeUnit.SECONDS.toNanos(settings.maxSeconds()));
        tick();

        assertTrue(areas.pending.isCancelled(), "the load is given up, so ChunkAreaSource lets its chunks go");
        assertFalse(jobs.running());
        assertEquals(List.of("error: " + BUDGET), told(builder));
        assertEquals(List.of(), worldEdit.calls("open"));
        assertNoStackTrace();
        tick();
        assertTrue(mainQueue.isEmpty(), "nothing is left watching");
        jobs.start(builder, world, selection(), null, true, new Options());
        assertEquals(2, areas.loads, "the next job may start");
    }

    // ---------------------------------------------------------------------------------------------------------
    // Rule 5: nothing here can take Architect down

    enum Stage { LOAD, LOAD_REFUSED_AT_ONCE, READ, SMOOTH, OPEN, SET, CLOSE }

    static Stream<Arguments> stagesAndFailures() {
        List<Arguments> out = new ArrayList<>();
        for (Stage stage : Stage.values()) {
            boolean writing = stage == Stage.OPEN || stage == Stage.SET || stage == Stage.CLOSE;
            for (boolean fawe : writing ? List.of(true, false) : List.of(false)) {
                out.add(Arguments.of(stage, fawe, new RuntimeException("test failure")));
                out.add(Arguments.of(stage, fawe, new NoSuchMethodError("test failure")));
                out.add(Arguments.of(stage, fawe, new OutOfMemoryError("test failure")));
            }
        }
        return out.stream();
    }

    @ParameterizedTest(name = "{0}, off the main thread {1}: {2}")
    @MethodSource("stagesAndFailures")
    void aFailureInEachStageReleasesLockAndChunks(Stage stage, boolean fawe, Throwable failure) throws Exception {
        worldEdit.offTheMainThread = fawe;
        switch (stage) {
            case LOAD -> { }
            case LOAD_REFUSED_AT_ONCE -> areas.failAtOnce = failure; // ChunkAreaSource's first run refused by the scheduler
            case READ -> areas.failRead = failure;
            case SMOOTH -> workerClockFailure = failure;
            case OPEN -> worldEdit.failures.put("open", failure);
            case SET -> {
                worldEdit.failures.put("set", failure);
                worldEdit.setsBeforeFailure = 3;
            }
            case CLOSE -> worldEdit.failures.put("close", failure);
        }
        try {
            jobs.start(builder, world, selection(), null, true, new Options());
            if (stage == Stage.LOAD) {
                areas.pending.completeExceptionally(failure);
            } else if (stage != Stage.LOAD_REFUSED_AT_ONCE) {
                areas.finishLoading();
            }
            runOut();
        } catch (Throwable e) {
            if (e == failure) { // reported as a failure of this test: an OutOfMemoryError reaching JUnit ends the whole run
                throw new AssertionError("the job let the failure through", e);
            }
            throw e;
        }

        assertEquals(List.of("error: " + FAILED), told(builder));
        assertLoggedWithItsCause(failure);
        assertFalse(jobs.running());
        boolean handedOver = stage != Stage.LOAD && stage != Stage.LOAD_REFUSED_AT_ONCE;
        assertEquals(handedOver ? 1 : 0, areas.releases, "the chunks it held are let go");
        if (stage == Stage.SET || stage == Stage.CLOSE) {
            assertEquals(1, worldEdit.calls("close").size(), "closed once, so //undo reverts what got through");
        }
        assertEquals(failure instanceof LinkageError ? List.of(failure) : List.of(), broken);
    }

    @Test
    void aLinkageErrorIsPassedToBroken() throws Exception {
        NoClassDefFoundError missing = new NoClassDefFoundError("com/sk89q/worldedit/EditSession");
        worldEdit.failures.put("open", missing);
        jobs.start(builder, world, selection(), null, true, new Options());
        finish();

        assertEquals(List.of(missing), broken); // on the main thread: the fake checks
        assertEquals(List.of("error: " + FAILED), told(builder));
        assertLoggedWithItsCause(missing);
    }

    @Test
    void aLoadOfUngeneratedTerrainSaysWhere() throws Exception {
        String where = "Part of the area (chunk 6, -3) hasn't been generated; SlabSmooth only smooths existing terrain.";
        jobs.start(builder, world, selection(), null, true, new Options());
        areas.pending.completeExceptionally(new IllegalArgumentException(where));
        runOut();

        assertEquals(List.of("error: " + where), told(builder));
        assertNoStackTrace();
    }

    @Test
    void aMainThreadThatRefusesEndsQuietly() throws Exception {
        jobs.start(builder, world, selection(), null, true, new Options());
        areas.finishLoading();
        mainRefuses = true; // the plugin is being disabled: the smoothing can't hand back
        work();

        assertFalse(jobs.running(), "the lock is open again");
        assertEquals(List.of(), told(builder));
        assertEquals(List.of(), logged.stream().map(LogRecord::getMessage).toList());
        assertEquals(List.of(), worldEdit.calls("open"));
    }

    // ---------------------------------------------------------------------------------------------------------
    // Rule 7: every change can be undone

    @Test
    void aJobIsDroppedIfThePlayerLeftTheServer() throws Exception {
        jobs.start(builder, world, selection(), null, true, new Options());
        areas.finishLoading();
        work();
        online.remove(builder.getUniqueId()); // they left the server, or went to another one
        runOut();

        assertEquals(List.of(), worldEdit.calls("open"), "nothing is written where they can't undo it");
        assertEquals(List.of(), told(builder));
        assertEquals(1, areas.releases);
        assertTrue(logged.stream().anyMatch(r -> r.getLevel() == Level.INFO && r.getMessage().contains(builder.getName())),
                logged.stream().map(LogRecord::getMessage).toList().toString());
        assertNoStackTrace();
    }

    @Test
    void aWriteCutShortByTheLimitIsStillClosedForUndo() throws Exception {
        worldEdit.failures.put("set", new MaxChangedBlocksException(25));
        worldEdit.setsBeforeFailure = 25;
        jobs.start(builder, world, selection(), null, true, new Options());
        finish();

        assertEquals(25, worldEdit.sets.size());
        assertEquals(List.of("open", "close", "release"), worldEdit.events, "closed after what got through, so //undo reverts it");
        assertEquals(List.of("error: Too many blocks for your WorldEdit limit; partly done, //undo reverts it."), told(builder));
        assertNoStackTrace();
    }

    @Test
    void aFailureWhileClosingIsLoggedEvenAfterTheLimit() throws Exception {
        RuntimeException closing = new RuntimeException("test failure while closing");
        worldEdit.failures.put("set", new MaxChangedBlocksException(5));
        worldEdit.setsBeforeFailure = 5;
        worldEdit.failures.put("close", closing);
        jobs.start(builder, world, selection(), null, true, new Options());
        finish();

        assertEquals(List.of("error: " + FAILED), told(builder), "the unexpected failure decides what is said");
        assertLoggedWithItsCause(closing);
        assertEquals(1, closing.getSuppressed().length, "the limit goes to the log with it");
        assertEquals(1, worldEdit.calls("close").size());
        assertEquals(1, areas.releases);
    }

    @Test
    void aReplacedPlayerObjectIsFoundByUuid() throws Exception {
        jobs.start(builder, world, selection(), null, true, new Options());
        areas.finishLoading();
        work();
        PlayerMock replaced = new PlayerMock(server, builder.getName(), builder.getUniqueId());
        replaced.teleport(builder.getLocation());
        online.put(builder.getUniqueId(), replaced); // a biome preview replaces the Player object
        runOut();

        assertSame(replaced, worldEdit.calls("open").get(0).player());
        assertEquals(List.of(), told(builder));
        assertTrue(toldOnce(replaced).startsWith("info: Smoothed "));
    }

    // ---------------------------------------------------------------------------------------------------------
    // Rule 9: one pack per job

    @Test
    void aSelectionAcrossABorderIsRefusedNamingThePacks() {
        tables.regions = l -> l.getBlockX() < OX + 20 ? "Dwarven" : null; // a Dwarven RP region over the west half

        IllegalArgumentException refused = assertThrows(IllegalArgumentException.class,
                () -> jobs.start(builder, world, selection(), null, true, new Options()));

        assertEquals("This selection covers more than one pack (Dwarven, Human). Pick one with /sls pack <name>, or select less.",
                refused.getMessage());
        assertEquals(0, areas.loads);
        assertFalse(jobs.running());
        assertEquals(List.of(), tables.asked, "no table is asked for");
    }

    @Test
    void aRegionAtTheCentreBetweenTheSamplesCounts() {
        // 4 x 4 columns around the centre, between the samples at 5, 21 and 34 blocks from the scene's corner
        tables.regions = l -> l.getBlockX() >= OX + 17 && l.getBlockX() <= OX + 20 && l.getBlockZ() >= OZ + 17
                && l.getBlockZ() <= OZ + 20 ? "Elvish" : null;

        IllegalArgumentException refused = assertThrows(IllegalArgumentException.class,
                () -> jobs.start(builder, world, selection(), null, true, new Options()));

        assertEquals("This selection covers more than one pack (Elvish, Human). Pick one with /sls pack <name>, or select less.",
                refused.getMessage());
    }

    @Test
    void aNamedPackLetsItRun() throws Exception {
        tables.regions = l -> l.getBlockX() < OX + 20 ? "Dwarven" : null;
        jobs.start(builder, world, selection(), "Human", true, new Options());
        finish();

        assertEquals(List.of("Human"), tables.asked);
        assertFalse(worldEdit.sets.isEmpty());
        assertTrue(toldOnce(builder).contains(" [Human pack] "));
    }

    // ---------------------------------------------------------------------------------------------------------
    // Rule 10: blocks all needs its own permission

    /** The hand-made table with every material marked as not terrain: only blocks all smooths the hillside. */
    private static Table nothingIsTerrain(boolean allBlocks) throws Exception {
        JsonObject spec;
        try (Reader r = new InputStreamReader(SmoothJobsTest.class.getResourceAsStream("/slabSmooth/table.json"), StandardCharsets.UTF_8)) {
            spec = JsonParser.parseReader(r).getAsJsonObject();
        }
        for (Map.Entry<String, JsonElement> material : spec.getAsJsonObject("materials").entrySet()) {
            material.getValue().getAsJsonObject().addProperty("terrain", false);
        }
        return new Table(spec, allBlocks);
    }

    static Stream<Arguments> blocksAllFrom() {
        Options set = new Options();
        set.set("blocks", "all");
        YamlConfiguration config = new YamlConfiguration();
        config.set("slabSmooth.defaults.blocks", "all");
        SlabSmoothSettings fromConfig = SlabSmoothSettings.from(config.getConfigurationSection("slabSmooth"), w -> fail(w));
        Options defaults = new Options();
        fromConfig.defaults().forEach(defaults::set); // as every player's options start
        return Stream.of(Arguments.of("the player's setting", set), Arguments.of("the config's defaults", defaults));
    }

    @ParameterizedTest(name = "blocks all from {0}")
    @MethodSource("blocksAllFrom")
    void blocksAllWithoutThePermissionUsesTheTerrainTable(String from, Options options) throws Exception {
        assertEquals("all", options.blocks);
        tables = new Tables().with("Human", nothingIsTerrain(false), nothingIsTerrain(true));
        jobs.start(builder, world, selection(), null, true, options);
        finish();

        assertEquals(List.of("Human"), tables.asked, "only the terrain table");
        assertEquals(Map.of(), worldEdit.sets);
        String summary = toldOnce(builder);
        assertTrue(summary.startsWith("info: Nothing to change: ") && summary.endsWith(" ms)."), summary);
    }

    @Test
    void blocksAllWithThePermissionUsesTheFullTable() throws Exception {
        builder.addAttachment(plugin, Permission.SLAB_SMOOTH_ALL_BLOCKS.getPermissionNode(), true);
        tables = new Tables().with("Human", nothingIsTerrain(false), nothingIsTerrain(true));
        Options options = new Options();
        options.set("blocks", "all");
        jobs.start(builder, world, selection(), null, true, options);
        finish();

        assertEquals(List.of("Human with blocks all"), tables.asked);
        assertFalse(worldEdit.sets.isEmpty(), "the full table smooths the hillside");
        assertTrue(toldOnce(builder).startsWith("info: Smoothed "));
    }

    @Test
    void theHintOnlyGoesToThoseWhoMayUseIt() throws Exception {
        tables = new Tables().with("Human", nothingIsTerrain(false), nothingIsTerrain(true));
        PlayerMock mayUseIt = builder;
        mayUseIt.addAttachment(plugin, Permission.SLAB_SMOOTH_ALL_BLOCKS.getPermissionNode(), true);
        PlayerMock mayNot = newPlayer(world);

        jobs.start(mayUseIt, world, selection(), null, true, new Options());
        finish();
        String hinted = toldOnce(mayUseIt);
        jobs.start(mayNot, world, selection(), null, true, new Options());
        finish();
        String plain = toldOnce(mayNot);

        assertTrue(hinted.startsWith("info: Nothing to change: ")
                && hinted.endsWith(" ms). stone isn't terrain in this pack; /sls set blocks all smooths it too."), hinted);
        assertTrue(plain.startsWith("info: Nothing to change: ") && plain.endsWith(" ms)."), plain);
    }

    // ---------------------------------------------------------------------------------------------------------
    // Other

    @Test
    void aPackBeingReadSaysTryAgainWithoutTakingTheLock() {
        tables.notReady = new IllegalStateException("Reading the Human pack (default) for the first time; try again in a few seconds.");

        IllegalStateException refused = assertThrows(IllegalStateException.class,
                () -> jobs.start(builder, world, selection(), null, true, new Options()));

        assertSame(tables.notReady, refused);
        assertFalse(jobs.running());
        assertEquals(0, areas.loads);
        tables.notReady = null;
        jobs.start(builder, world, selection(), null, true, new Options());
        assertEquals(1, areas.loads, "once the pack is read, the next try runs");
    }

    @Test
    void aBigJobSaysItIsWorkingAndLogsItsTimeWithTheClose() throws Exception {
        // 60 x 60 columns over the hillside and past its edges, 70 x 70 with the margin: more than 4096
        Region big = new CuboidRegion(BlockVector3.at(OX - 10, OY, OZ - 10), BlockVector3.at(OX + 49, OY + 23, OZ + 49));
        worldEdit.offTheMainThread = true;
        worldEdit.onClose = () -> clock.addAndGet(300 * MS); // FAWE writes what it buffered when the edit closes
        jobs.start(builder, world, big, null, true, new Options());
        assertEquals(List.of("info: Smoothing 4900 columns..."), told(builder));
        finish();

        String summary = toldOnce(builder);
        assertTrue(summary.startsWith("info: Smoothed ") && summary.contains(" [Human pack] (300 ms)"), summary);
        assertEquals(List.of(builder.getName() + " smoothed 4900 columns: " + worldEdit.sets.size() + " blocks in 300 ms"),
                logged.stream().filter(r -> r.getLevel() == Level.INFO).map(LogRecord::getMessage).toList());
    }

    // ---------------------------------------------------------------------------------------------------------
    // Review focus

    enum Refusal { AT_THE_FIRST_READ, AT_THE_FIRST_SET, AFTER_SOME_SETS, AT_CLOSE, AT_OPEN }

    @ParameterizedTest
    @EnumSource(Refusal.class)
    void aRefusalFromFaweIsToldWithoutAStackTraceAndStillClosed(Refusal when) throws Exception {
        worldEdit.offTheMainThread = true;
        BlockTarget.Refused refused = new BlockTarget.Refused("No allowed region");
        switch (when) {
            case AT_THE_FIRST_READ -> worldEdit.failures.put("stateAt", refused); // the compare before the first set
            case AT_THE_FIRST_SET -> worldEdit.failures.put("set", refused);
            case AFTER_SOME_SETS -> {
                worldEdit.failures.put("set", refused);
                worldEdit.setsBeforeFailure = 10;
            }
            case AT_CLOSE -> worldEdit.failures.put("close", refused);
            case AT_OPEN -> worldEdit.failures.put("open", refused);
        }
        jobs.start(builder, world, selection(), null, true, new Options());
        finish();

        boolean wrote = when == Refusal.AFTER_SOME_SETS || when == Refusal.AT_CLOSE;
        assertEquals(List.of("error: FastAsyncWorldEdit refused the edit: No allowed region"
                + (wrote ? " //undo reverts what was written." : "")), told(builder));
        assertEquals(when == Refusal.AT_OPEN ? 0 : 1, worldEdit.calls("close").size(), "an edit that was opened is closed, once");
        assertNoStackTrace();
        assertFalse(jobs.running());
        assertEquals(1, areas.releases);
        assertEquals(List.of(), broken);
    }

    @Test
    void aPolygonSelectionOnlyChangesInsideIt() throws Exception {
        CuboidRegion box = selection();
        BlockVector3 lo = box.getMinimumPoint(), hi = box.getMaximumPoint();
        // a triangle whose bounding box is the usual selection
        Polygonal2DRegion triangle = new Polygonal2DRegion(null,
                List.of(BlockVector2.at(lo.x(), lo.z()), BlockVector2.at(hi.x(), lo.z()), BlockVector2.at(lo.x(), hi.z())), lo.y(), hi.y());
        Map<BlockVector3, String> inTheBox = setsOfAJob(box);
        assertTrue(inTheBox.keySet().stream().anyMatch(p -> !triangle.contains(p)), "the box changes blocks outside the triangle");

        jobs.start(builder, world, triangle, null, true, new Options());
        finish();

        assertFalse(worldEdit.sets.isEmpty());
        assertEquals(List.of(), worldEdit.sets.keySet().stream().filter(p -> !triangle.contains(p)).toList(),
                "changed outside the triangle");
    }

    /** An ellipsoid inscribed in the usual selection: a shape whose cross-section changes with height. */
    private static EllipsoidRegion ellipsoid() {
        CuboidRegion box = selection();
        BlockVector3 lo = box.getMinimumPoint(), hi = box.getMaximumPoint();
        return new EllipsoidRegion(BlockVector3.at((lo.x() + hi.x()) / 2, (lo.y() + hi.y()) / 2, (lo.z() + hi.z()) / 2),
                Vector3.at((hi.x() - lo.x()) / 2.0, (hi.y() - lo.y()) / 2.0, (hi.z() - lo.z()) / 2.0));
    }

    private static Options topSurfaces() {
        Options top = new Options();
        top.set("surfaces", "top");
        return top;
    }

    // The top smoother finds each column's surface at any height and masks per column, so it would change blocks
    // outside a shape that is narrower above or below.
    @Test
    void aShapeThatChangesWithHeightIsRefusedForTopSurfaces() {
        IllegalArgumentException refused = assertThrows(IllegalArgumentException.class,
                () -> jobs.start(builder, world, ellipsoid(), null, true, topSurfaces()));

        assertEquals("With surfaces top, select a box, polygon or cylinder.", refused.getMessage());
        assertEquals(0, areas.loads, "nothing is read");
        assertFalse(jobs.running(), "no lock is taken");
        assertEquals(List.of(), tables.asked);
    }

    @Test
    void anEllipsoidWithAllSurfacesOnlyChangesInsideIt() throws Exception {
        EllipsoidRegion ellipsoid = ellipsoid();
        jobs.start(builder, world, ellipsoid, null, true, new Options());
        finish();

        assertFalse(worldEdit.sets.isEmpty());
        assertEquals(List.of(), worldEdit.sets.keySet().stream().filter(p -> !ellipsoid.contains(p)).toList(),
                "changed outside the ellipsoid");
    }

    @Test
    void aCylinderWithTopSurfacesRunsAndOnlyChangesInsideIt() throws Exception {
        CuboidRegion box = selection();
        CylinderRegion cylinder = new CylinderRegion(BlockVector3.at(OX + 19, OY, OZ + 19), Vector2.at(14, 14),
                box.getMinimumPoint().y(), box.getMaximumPoint().y());
        jobs.start(builder, world, cylinder, null, true, topSurfaces());
        finish();

        assertFalse(worldEdit.sets.isEmpty(), "a cylinder may use surfaces top");
        assertEquals(List.of(), worldEdit.sets.keySet().stream().filter(p -> !cylinder.contains(p)).toList(),
                "changed outside the cylinder");
        assertTrue(toldOnce(builder).startsWith("info: Smoothed "));
    }

    @Test
    void aChangedSelectionDoesNotAffectARunningJob() throws Exception {
        Map<BlockVector3, String> expected = setsOfAJob(selection());
        CuboidRegion region = selection();
        jobs.start(builder, world, region, null, true, new Options());
        region.setPos1(BlockVector3.at(OX + 18, OY, OZ + 18)); // the player selects something else while it runs
        region.setPos2(BlockVector3.at(OX + 20, OY + 3, OZ + 20));
        finish();

        assertEquals(expected, worldEdit.sets);
    }

    @Test
    void reloadDuringAJobLetsItFinishAndKeepsTheLock() throws Exception {
        Tables before = tables;
        jobs.start(builder, world, selection(), null, true, new Options());
        // /architect reload: new settings and packs, the same jobs
        tables = new Tables().with("Elvish", handMade, handMade);
        tables.normal = "Elvish";
        settings = limits(SlabSmoothSettings.DEFAULTS.maxColumns(), SlabSmoothSettings.DEFAULTS.maxCells(), 1);
        PlayerMock other = newPlayer(world);

        IllegalStateException refused = assertThrows(IllegalStateException.class,
                () -> jobs.start(other, world, selection(), null, true, new Options()));
        assertEquals(SECOND_JOB, refused.getMessage());
        clock.addAndGet(10 * SECOND); // past the new time limit, well inside the one it started with
        finish();

        String summary = toldOnce(builder);
        assertTrue(summary.startsWith("info: Smoothed ") && summary.contains(" [Human pack] "), summary);
        assertEquals(List.of("Human"), before.asked, "the job kept the tables it started with");
        assertEquals(List.of("Elvish"), tables.asked, "only the refused job asked the new ones");
        assertEquals(1, areas.loads);
    }

    enum Moment { LOADING, SMOOTHING, CHECKING, WRITING, TELLING }

    @ParameterizedTest
    @EnumSource(Moment.class)
    void closingDuringAJobWritesNothingAndStaysQuiet(Moment moment) throws Exception {
        worldEdit.offTheMainThread = true;
        jobs.start(builder, world, selection(), null, true, new Options());
        int setsAtClose;
        switch (moment) {
            case LOADING -> {
                jobs.close();
                setsAtClose = 0;
                assertTrue(areas.pending.isCancelled(), "the load is given up, so ChunkAreaSource lets its chunks go");
            }
            case SMOOTHING -> {
                areas.finishLoading();
                jobs.close();
                setsAtClose = 0;
            }
            case CHECKING -> {
                areas.finishLoading();
                work();
                jobs.close();
                setsAtClose = 0;
            }
            case WRITING -> {
                areas.finishLoading();
                work();
                tick(); // hands the write to the worker
                CountDownLatch writing = new CountDownLatch(1), closed = new CountDownLatch(1);
                worldEdit.afterSet = () -> {
                    if (writing.getCount() > 0) {
                        writing.countDown();
                        try {
                            assertTrue(closed.await(10, TimeUnit.SECONDS));
                        } catch (InterruptedException e) {
                            throw new AssertionError(e);
                        }
                    }
                };
                AtomicReference<Throwable> escaped = new AtomicReference<>();
                Thread writer = startWorker(escaped);
                assertTrue(writing.await(10, TimeUnit.SECONDS), "the write has begun");
                jobs.close(); // on the main thread, while the worker is in the middle of the write
                setsAtClose = worldEdit.sets.size();
                closed.countDown();
                writer.join();
                assertEquals(null, escaped.get());
            }
            case TELLING -> {
                areas.finishLoading();
                work();
                tick();
                work(); // written; the end is handed to the main thread
                jobs.close();
                setsAtClose = worldEdit.sets.size();
            }
            default -> throw new AssertionError(moment);
        }
        assertEquals(moment == Moment.LOADING ? 0 : 1, areas.releases, "close() lets the chunks go at once");
        if (!areas.pending.isDone()) {
            areas.finishLoading();
        }
        runOut();

        assertEquals(List.of(), told(builder), "nothing is said");
        assertEquals(List.of(), logged.stream().map(r -> r.getLevel() + " " + r.getMessage()).toList(), "nothing is logged");
        assertEquals(setsAtClose, worldEdit.sets.size(), "nothing is set after close");
        assertEquals(worldEdit.calls("open").size(), worldEdit.calls("close").size(), "an edit that was opened is closed");
        assertEquals(moment == Moment.LOADING ? 0 : 1, areas.releases, "the chunks are let go, once");
        assertEquals(List.of(), broken);
    }

    /** When the world unloads. Paper may fail the read, never finish it, or have copied the chunks already. */
    enum Gone { WHEN_THE_LOAD_FAILS, WHEN_THE_LOAD_NEVER_ENDS, WHEN_THE_LOAD_COMPLETES, AFTER_THE_SMOOTHING }

    @ParameterizedTest
    @EnumSource(Gone.class)
    void aWorldUnloadedDuringTheJobChangesNothing(Gone when) throws Exception {
        jobs.start(builder, world, selection(), null, true, new Options());
        switch (when) {
            case WHEN_THE_LOAD_FAILS -> {
                unloaded.add(world.getUID());
                areas.pending.completeExceptionally(new IllegalStateException("test: the world was unloaded"));
            }
            case WHEN_THE_LOAD_NEVER_ENDS -> {
                unloaded.add(world.getUID());
                tick();
                clock.addAndGet(TimeUnit.SECONDS.toNanos(settings.maxSeconds()) + SECOND);
            }
            case WHEN_THE_LOAD_COMPLETES -> {
                unloaded.add(world.getUID());
                areas.finishLoading();
            }
            case AFTER_THE_SMOOTHING -> {
                areas.finishLoading();
                work();
                unloaded.add(world.getUID());
            }
        }
        runOut();

        assertEquals(List.of("error: That world is gone, so nothing was changed."), told(builder));
        assertEquals(List.of(), worldEdit.calls("open"));
        assertEquals(List.of(), logged.stream().filter(r -> r.getLevel().intValue() >= Level.WARNING.intValue())
                .map(LogRecord::getMessage).toList(), "no warning");
        assertNoStackTrace();
        assertEquals(0, areas.releases, "no ticket is removed in a world that is gone: Paper drops them itself");
        assertFalse(jobs.running());
    }

    @Test
    void aNamedPackThatIsGoneSaysSoBeforeReading() {
        IllegalStateException refused = assertThrows(IllegalStateException.class,
                () -> jobs.start(builder, world, selection(), "Gone", true, new Options()));

        assertEquals("The Gone pack (set) can't be used: no such pack", refused.getMessage());
        assertEquals(0, areas.loads);
        assertFalse(jobs.running());
    }
}
