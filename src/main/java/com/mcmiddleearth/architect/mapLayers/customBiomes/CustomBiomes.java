package com.mcmiddleearth.architect.mapLayers.customBiomes;

import com.mcmiddleearth.architect.Permission;
import com.mcmiddleearth.architect.PluginData;
import com.mcmiddleearth.architect.mapLayers.MapLayer;
import com.mcmiddleearth.architect.mapLayers.MapLayerConfig;
import com.mcmiddleearth.architect.mapLayers.MapLayers;
import com.mcmiddleearth.architect.mapLayers.MapShape;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.IllegalPluginAccessException;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * The Custom biomes layer: where each custom biome is painted, on the worlds an admin put on the layer with
 * {@code /architect maplayers scan <world>}. It owns the parts of this package and wires them together: the records and
 * their files ({@link PaintStore}), the first fill from a world's saved region files ({@link PaintScan}), the live
 * checks of loaded chunks ({@link PaintTracker}) and the shapes ({@link BiomeShapes}). A record that changes is marked,
 * and the worlds marked are drawn again at most every 10 seconds; one change after a quiet spell is drawn within a
 * second.
 * <p>
 * A start loads the records, draws the worlds that are on, and starts the live checks. Two things wait for later,
 * because Architect starts before the server loads its worlds and before WorldEdit starts: a fill that a restart
 * stopped goes on at the first tick, and WorldEdit is looked up when a selection is first asked for.
 * <p>
 * Which thread owns what:
 * <ul>
 *     <li><b>The main thread</b> runs every method here. It owns the parts, the worlds whose change is still to be
 *     drawn, and the timers.</li>
 *     <li><b>The shapes thread</b>, {@value #SHAPES_THREAD}, builds the shapes, one world at a time, as
 *     {@link BiomeShapes} says.</li>
 *     <li><b>The writer</b>, {@value #WRITER_THREAD}, writes the record files, one at a time, as {@link PaintStore}
 *     says.</li>
 *     <li><b>The scan thread</b> reads saved region files, as {@link PaintScan} says.</li>
 * </ul>
 * A stop ends all three and waits for them, for a few seconds at most, before it returns. So after
 * {@code /architect reload} the old layer never writes a file while the new one reads or writes it, and nothing runs on
 * once Architect is disabled.
 */
public final class CustomBiomes implements MapLayer {

    public static final String ID = "architect.debug.biomes";
    /** The layer's key in the mapLayers section of config.yml. */
    public static final String KEY = "customBiomes";

    static final String SHAPES_THREAD = "Architect custom biomes";
    static final String WRITER_THREAD = "Architect custom biomes writer";

    /**
     * Changes reach the map at most this often. Each report builds shapes again, and each changed marker makes dynmap
     * rewrite its world's whole marker file on the main thread.
     */
    private static final int REPORT_SECONDS = 10;
    private static final long SECOND_TICKS = 20;
    /** How often the biomes' names and settings are compared with what the windows show: every 10 seconds. */
    private static final long FACTS_TICKS = 200;
    private static final double DEFAULT_MEGABYTES_PER_SECOND = 20;
    private static final long SHAPES_STOP_SECONDS = 5;
    private static final long WRITER_STOP_SECONDS = 10;

    /** Main thread: the layer that is running, if one is. */
    private static CustomBiomes active;

    private final MapLayerConfig.Layer config;
    private final File dataFolder;
    /** A part given for tests, or null for the server's own, made at each start. */
    private final PaintTracker.ChunkReader givenReader;
    private final SelectionSource givenSelections;
    private final BiomeFacts givenFacts;
    private final Executor givenBackground;
    private final Supplier<Set<String>> customIds;
    private final PaintScan.Sleeper sleeper;
    private final Function<World, Path> regionFolder;
    private final Predicate<Player> isBuilder;

    /** The worlds whose record changed since the last report, to be drawn again at the next. */
    private final Set<String> pending = new LinkedHashSet<>();
    private final List<BukkitTask> timers = new ArrayList<>();
    private Plugin plugin;
    /** The server's custom biome ids, made once a start: it remembers its answers, so it is cheap for every cell. */
    private Function<Biome, String> customIdOf;
    /** The shapes thread, when the layer made it; null when a test gave its own executor. */
    private ExecutorService shapesThread;
    private ExecutorService writer;
    private PaintStore store;
    private BiomeShapes shapes;
    private ChangeReporter reporter;
    private PaintTracker tracker;
    private PaintScan scan;

    /** With the server's parts: Paper's chunks and biome registry, WorldEdit's selections, biome tuning's facts. */
    public CustomBiomes(MapLayerConfig.Layer config, File dataFolder) {
        this(config, dataFolder, null, null, ChunkPaintReader::customIdsOnThisServer, null, Thread::sleep,
                world -> world.getWorldPath().resolve("region"),
                player -> PluginData.hasPermission(player, Permission.BIOME_TUNE), null);
    }

    /**
     * With parts of a test's own. A null reader, selections, facts or background means the server's own, which each
     * start makes anew: the reader through Paper's biome registry, WorldEdit's selections looked up at their first use,
     * biome tuning's facts, and a shapes thread of the layer's own.
     *
     * @param reader       reads a loaded chunk's custom biomes
     * @param selections   where a player's WorldEdit selection lies
     * @param customIds    the custom biome ids the server defines, for the first fill
     * @param facts        the biomes' names and settings, for the windows
     * @param sleeper      sleeps on the scan thread, to pace it
     * @param regionFolder the folder of a world's saved region files
     * @param isBuilder    whether the chunks around a player are read often
     * @param background   builds the shapes: one thread, which runs what it is given in that order
     */
    CustomBiomes(MapLayerConfig.Layer config, File dataFolder, PaintTracker.ChunkReader reader,
                 SelectionSource selections, Supplier<Set<String>> customIds, BiomeFacts facts,
                 PaintScan.Sleeper sleeper, Function<World, Path> regionFolder, Predicate<Player> isBuilder,
                 Executor background) {
        this.config = config;
        this.dataFolder = dataFolder;
        this.givenReader = reader;
        this.givenSelections = selections;
        this.customIds = customIds;
        this.givenFacts = facts;
        this.sleeper = sleeper;
        this.regionFolder = regionFolder;
        this.isBuilder = isBuilder;
        this.givenBackground = background;
    }

    /** The layer that is running, for the scan command; none while the layer is switched off or not started. */
    public static Optional<CustomBiomes> active() {
        return Optional.ofNullable(active);
    }

    @Override
    public String markerSetId() {
        return ID;
    }

    @Override
    public String label() {
        return "Custom biomes";
    }

    @Override
    public boolean hiddenByDefault() {
        return config.hidden();
    }

    /** A start that fails stops what it had started, so that no thread or timer of it is left. */
    @Override
    public void start(Plugin plugin) {
        this.plugin = plugin;
        try {
            startParts();
        } catch (RuntimeException | LinkageError e) {
            stop();
            throw e;
        }
        active = this;
    }

    private void startParts() {
        BukkitScheduler scheduler = plugin.getServer().getScheduler();
        Path folder = new File(dataFolder, "mapLayers/customBiomes").toPath();
        writer = Executors.newSingleThreadExecutor(threads(WRITER_THREAD));
        store = new PaintStore(folder, plugin.getLogger(), writer);
        store.load();
        BiomeColours colours = new BiomeColours(folder.resolve("colours.txt"));
        Executor background = givenBackground;
        if (background == null) {
            shapesThread = Executors.newSingleThreadExecutor(threads(SHAPES_THREAD));
            background = shapesThread;
        }
        BiomeFacts facts = givenFacts != null ? givenFacts : TuningFacts.fromBiomeTuning();
        shapes = new BiomeShapes(config, colours, facts, background, mainThread(plugin), plugin.getLogger(),
                () -> MapLayers.changed(ID));
        reporter = new ChangeReporter(this::drawPending, REPORT_SECONDS);
        PaintTracker.ChunkReader reader = givenReader;
        if (reader == null) {
            customIdOf = ChunkPaintReader.customIds();
            reader = (world, chunkX, chunkZ) -> ChunkPaintReader.read(world, chunkX, chunkZ, customIdOf);
        }
        SelectionSource selections = givenSelections != null ? givenSelections : new FirstUseSelections(plugin);
        tracker = new PaintTracker(plugin, store::record, reader, selections,
                PaintTracker.worldEditCommands(plugin.getServer()), isBuilder, this::dirty, System::nanoTime);
        scan = new PaintScan(plugin, store, regionFolder, customIds, this::dirty, megabytesPerSecond(), sleeper);
        timers.add(scheduler.runTaskTimer(plugin, reporter::everySecond, SECOND_TICKS, SECOND_TICKS));
        timers.add(scheduler.runTaskTimer(plugin, store::saveDirty, PaintStore.SAVE_TICKS, PaintStore.SAVE_TICKS));
        timers.add(scheduler.runTaskTimer(plugin, this::drawAllIfFactsChanged, FACTS_TICKS, FACTS_TICKS));
        tracker.start();
        // the first tick comes once the server has loaded its worlds, which a fill needs
        timers.add(scheduler.runTask(plugin, scan::resumeUnfinished));
        drawAll();
    }

    /** A rate that is not above 0, NaN included, would stop the layer from starting: the default stands in. */
    private double megabytesPerSecond() {
        double rate = config.number("scanMegabytesPerSecond", DEFAULT_MEGABYTES_PER_SECOND);
        if (rate > 0) {
            return rate;
        }
        plugin.getLogger().warning("Custom biomes layer: scanMegabytesPerSecond must be above 0, not " + rate
                + ", so the scan reads at most " + (int) DEFAULT_MEGABYTES_PER_SECOND + " MB a second.");
        return DEFAULT_MEGABYTES_PER_SECOND;
    }

    /** The main thread, at the next tick. Once Architect is disabled, nothing is drawn any more, so it drops the task. */
    private static Executor mainThread(Plugin plugin) {
        return task -> {
            try {
                plugin.getServer().getScheduler().runTask(plugin, task);
            } catch (IllegalPluginAccessException e) {
                // disabled: the layer has stopped
            }
        };
    }

    /** Daemon threads at the lowest priority, so that they never hold up a shutdown or the game. */
    private static ThreadFactory threads(String name) {
        return task -> {
            Thread thread = new Thread(task, name);
            thread.setDaemon(true);
            thread.setPriority(Thread.MIN_PRIORITY);
            return thread;
        };
    }

    /**
     * Stops the fill and the live checks, waits for the shapes thread, saves every record and waits for the writer.
     * A second stop does nothing more.
     */
    @Override
    public void stop() {
        if (scan != null) {
            scan.stop();
        }
        if (tracker != null) {
            tracker.stop();
        }
        timers.forEach(BukkitTask::cancel);
        timers.clear();
        if (shapesThread != null) {
            shapesThread.shutdownNow(); // a build waiting is dropped: its world is built again at the next start
            awaitEnd(shapesThread, SHAPES_STOP_SECONDS, "building the shapes");
        }
        if (store != null) {
            store.saveAll();
        }
        if (writer != null) {
            writer.shutdown(); // the saves just handed over still run
            awaitEnd(writer, WRITER_STOP_SECONDS, "saving the records");
        }
        if (active == this) {
            active = null;
        }
        pending.clear();
        scan = null;
        tracker = null;
        shapesThread = null;
        store = null;
        writer = null;
        shapes = null;
        reporter = null;
    }

    /** Waits for the executor's thread to end, at most {@code seconds}; past that, the stop goes on without it. */
    private void awaitEnd(ExecutorService executor, long seconds, String what) {
        try {
            if (!executor.awaitTermination(seconds, TimeUnit.SECONDS)) {
                plugin.getLogger().warning("Custom biomes layer: " + what + " did not finish within " + seconds
                        + " seconds of the stop, so the stop went on without it.");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @Override
    public List<MapShape> shapes() {
        return shapes == null ? List.of() : shapes.shapes();
    }

    /** For {@code /architect maplayers refresh}: every loaded chunk is read again, and every world drawn again. */
    @Override
    public void refresh() {
        if (tracker == null) {
            return;
        }
        tracker.readLoadedNow();
        drawAll();
    }

    /** The world's record changed: it is saved at the next save, and drawn again at the next report. */
    private void dirty(String world) {
        store.dirty(world);
        pending.add(world);
        reporter.mark();
    }

    /** The reporter's report: each world that changed is drawn again from its record. */
    private void drawPending() {
        for (String world : pending) {
            PaintRecord record = store.record(world);
            if (record != null) {
                shapes.rebuild(world, record.copyOfChunks());
            }
        }
        pending.clear();
    }

    private void drawAll() {
        store.records().forEach((world, record) -> shapes.rebuild(world, record.copyOfChunks()));
        pending.clear();
    }

    /** A biome's name or settings changed in biome tuning: its windows are behind, so every world is drawn again. */
    private void drawAllIfFactsChanged() {
        if (shapes.factsChanged()) {
            drawAll();
        }
    }

    /**
     * {@code /architect maplayers scan [<world> [off]]}, with the arguments that follow {@code scan}: with none, it
     * lists the worlds that are on; with a world, it puts the world on and reads its saved files; with a world and
     * {@code off}, it takes the world off. Main thread only.
     */
    public void scanCommand(CommandSender sender, String[] args) {
        if (args.length == 0) {
            list(sender);
            return;
        }
        boolean off = args.length == 2 && args[1].equalsIgnoreCase("off");
        if (args.length > 2 || (args.length == 2 && !off)) {
            error(sender, "Usage: /architect maplayers scan [<world> [off]]");
            return;
        }
        World world = plugin.getServer().getWorld(args[0]);
        if (off && (world != null || store.record(args[0]) != null)) {
            off(sender, world != null ? world.getName() : args[0]); // a world that is on may have been unloaded
        } else if (world == null) {
            error(sender, "No loaded world is named " + args[0] + ".");
        } else {
            scan(sender, world);
        }
    }

    private void list(CommandSender sender) {
        Map<String, PaintRecord> records = store.records();
        if (records.isEmpty()) {
            info(sender, "No world is on the Custom biomes layer. Start one with /architect maplayers scan <world>.");
            return;
        }
        Map<String, Integer> oversize = shapes.oversize();
        info(sender, "Custom biomes layer:");
        records.forEach((world, record) -> PluginData.getMessageUtil().sendNoPrefixInfoMessage(sender, world + ": "
                + plural(record.chunks().size(), "chunk") + " with custom biomes, " + state(world, record, oversize)));
    }

    /**
     * How far the world's fill has come, or why the world is not drawn. A world that is not read in full and not
     * waiting in the queue either, such as one that was not loaded at the start, waits for the next start or for
     * {@code scan <world>}.
     */
    private String state(String world, PaintRecord record, Map<String, Integer> oversize) {
        PaintScan.Status fill = scan.status(world);
        if (fill.running()) {
            return "reading " + fill.done() + " of " + plural(fill.total(), "region file");
        }
        if (fill.queued()) {
            return "waiting to be read";
        }
        Integer bytes = oversize.get(world);
        if (bytes != null) {
            return "not drawn: its markers would add " + Math.round(bytes / 1000.0) + " KB, over the "
                    + MarkerSize.TARGET / 1000 + " KB target";
        }
        return record.fillDone() ? "up to date" : "waiting to be read";
    }

    /**
     * Puts the world on and reads its saved files, unless they are being read or waiting already. A world that is on
     * already keeps what its loaded chunks hold, and is read again from the start.
     */
    private void scan(CommandSender sender, World world) {
        String name = world.getName();
        PaintScan.Status fill = scan.status(name);
        if (fill.running()) {
            info(sender, readingAlready(name, fill));
            return;
        }
        if (fill.queued()) {
            info(sender, waiting(name));
            return;
        }
        store.turnOn(name, key -> world.isChunkLoaded(PaintRecord.keyX(key), PaintRecord.keyZ(key)));
        tracker.readLoadedNow(); // the fill leaves the loaded chunks to the live checks
        PaintScan.Start started = scan.start(world);
        PaintScan.Status now = scan.status(name);
        info(sender, switch (started) {
            case STARTED -> "Reading the saved world files of " + name + " in the background: "
                    + plural(now.total(), "region file") + ". /architect maplayers scan shows the progress.";
            case QUEUED -> waiting(name);
            case ALREADY -> readingAlready(name, now);
        });
    }

    private static String readingAlready(String world, PaintScan.Status fill) {
        return world + " is being read already: " + fill.done() + " of " + plural(fill.total(), "region file") + ".";
    }

    private static String waiting(String world) {
        return world + " will be read after the world being read now.";
    }

    /** Takes the world off: its fill stops, its areas leave the map, and its file is set aside. */
    private void off(CommandSender sender, String world) {
        if (store.record(world) == null) {
            error(sender, world + " is not on the Custom biomes layer.");
            return;
        }
        scan.cancel(world);
        Path file = store.turnOff(world);
        shapes.forget(world);
        pending.remove(world);
        MapLayers.changed(ID);
        // a world put on less than a minute ago may have no file yet
        info(sender, world + " is off the Custom biomes layer."
                + (file == null ? "" : " Its record was set aside as " + file.getFileName() + "."));
    }

    /** For tab completion, with the arguments that follow {@code scan}: the loaded worlds, then {@code off}. */
    public List<String> completeScan(String[] args) {
        List<String> options = switch (args.length) {
            case 1 -> plugin.getServer().getWorlds().stream().map(World::getName).toList();
            case 2 -> List.of("off");
            default -> List.of();
        };
        String typed = args.length == 0 ? "" : args[args.length - 1].toLowerCase(Locale.ROOT);
        return options.stream().filter(option -> option.toLowerCase(Locale.ROOT).startsWith(typed)).toList();
    }

    /** For tests: waits until the scan has handed all it read to the main thread. False when the time ran out first. */
    boolean awaitScanIdle(Duration timeout) {
        return scan.awaitIdle(timeout);
    }

    private static String plural(int count, String noun) {
        return count + " " + noun + (count == 1 ? "" : "s");
    }

    private static void info(CommandSender sender, String message) {
        PluginData.getMessageUtil().sendInfoMessage(sender, message);
    }

    private static void error(CommandSender sender, String message) {
        PluginData.getMessageUtil().sendErrorMessage(sender, message);
    }

    /**
     * WorldEdit's selections, looked up when one is first asked for, after a WorldEdit command: Architect starts before
     * WorldEdit and FastAsyncWorldEdit do, so a look-up at its start would find neither. The log says once what was
     * found. Main thread only.
     */
    static final class FirstUseSelections implements SelectionSource {

        private static final String FAST_ASYNC_WORLD_EDIT = "FastAsyncWorldEdit";

        private final Plugin plugin;
        private SelectionSource found;

        FirstUseSelections(Plugin plugin) {
            this.plugin = plugin;
        }

        @Override
        public Optional<ChunkBox> chunks(Player player) {
            if (found == null) {
                found = WorldEditSelections.lookup(plugin);
                if (found == NONE) {
                    plugin.getLogger().info("Custom biomes layer: neither WorldEdit nor FastAsyncWorldEdit is enabled,"
                            + " so the selections of WorldEdit commands are not read.");
                } else {
                    // FastAsyncWorldEdit also answers to the name WorldEdit, so it is asked about first
                    String name = plugin.getServer().getPluginManager().isPluginEnabled(FAST_ASYNC_WORLD_EDIT)
                            ? FAST_ASYNC_WORLD_EDIT : "WorldEdit";
                    plugin.getLogger().info("Custom biomes layer: the selections of WorldEdit commands are read"
                            + " through " + name + ".");
                }
            }
            return found.chunks(player);
        }
    }
}
