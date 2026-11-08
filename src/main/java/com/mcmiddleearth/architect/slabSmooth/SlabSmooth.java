package com.mcmiddleearth.architect.slabSmooth;

import com.mcmiddleearth.architect.slabSmooth.core.Options;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.TileState;
import org.bukkit.block.data.BlockData;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Supplier;
import java.util.logging.Level;

/**
 * Wiring for terrain smoothing, Toti's SlabSmooth: the settings, the packs' tables, each player's options, the module
 * switch, the jobs and their worker, and what a brush needs at a click. It starts inside a guard, as biome tuning
 * does: a failure, a LinkageError from a WorldEdit build this wasn't made for included, switches off only this
 * feature, says why once in the log, and leaves /sls answering that it is unavailable. A LinkageError later, from a
 * command, a job or a brush, switches it off the same way, until the next restart.
 *
 * <p>Nothing starts where no world has the module on: the worker's thread starts with the first job, and a pack is
 * read when it is first used, or one tick after a start or a reload if a loaded world has the module on.
 *
 * <p>A reload ({@code /architect reload}) builds new settings, packs, player options and module switch, so each
 * player's options and pack are forgotten, as Toti's reload did. The jobs stay: they hold the server-wide lock and the
 * running job, and read the new parts when the next job starts. A brush asks for its context at every click, so one
 * bound before a reload uses the new parts, and one bound before a disable does nothing.
 */
public final class SlabSmooth {

    /** The job worker's thread. The packs' worker's name starts the same. */
    static final String WORKER = "Architect SlabSmooth";
    private static final List<String> NOT_RUNNING = List.of("it is not running");

    /** What SlabSmooth works through. On the server: Architect's packs, WorldEdit and Paper's chunks; tests use fakes. */
    record Services(PackSources sources, SmoothWorldEdit worldEdit, AreaSource areas) {
    }

    /** What a reload replaces, all at once. defaults: the options each player starts with. */
    record Parts(SlabSmoothSettings settings, Options defaults, Packs packs, PlayerOptions options, ModuleSwitch module,
                 SmoothBrush.Context brush) {
    }

    /** Whether a material's blocks hold a block entity, which is the same for all its states. */
    private static final Map<Material, Boolean> BLOCK_ENTITIES = new ConcurrentHashMap<>();

    // Changed on the main thread only. plugin, parts and problems are read on other threads too: FAWE's brush clicks,
    // the packs' worker.
    private static volatile JavaPlugin plugin;
    private static Services services;
    private static ExecutorService worker;
    private static SmoothJobs jobs;
    private static volatile Parts parts;
    private static volatile List<String> problems = NOT_RUNNING;

    private SlabSmooth() {
    }

    public static void enable(JavaPlugin owner) {
        enable(owner, () -> new Services(new ArchitectPacks(owner, owner.getLogger()), new WorldEditSmoothing(),
                new ChunkAreaSource(owner, task -> Bukkit.getScheduler().runTask(owner, task), System::nanoTime)));
    }

    /**
     * The services are made inside the guard: making them can be what fails. A RuntimeException or LinkageError while
     * starting switches SlabSmooth off and says why, once. Architect's own onEnable must never see it: Paper would then
     * disable all of Architect. Other errors, such as running out of memory, pass through on purpose.
     */
    static void enable(JavaPlugin owner, Supplier<Services> given) {
        try {
            start(owner, given.get());
        } catch (RuntimeException | LinkageError e) {
            disable();
            switchOff(owner, "could not start", e);
        }
    }

    private static void start(JavaPlugin owner, Services given) {
        plugin = owner;
        services = given;
        worker = Executors.newSingleThreadExecutor(task -> { // a plain one: its thread starts with the first job
            Thread thread = new Thread(task, WORKER);
            thread.setDaemon(true);
            thread.setPriority(Thread.MIN_PRIORITY);
            return thread;
        });
        parts = parts(owner, given);
        jobs = new SmoothJobs(owner.getLogger(), () -> parts.packs(), () -> parts.settings(), given.areas(),
                given.worldEdit(), task -> Bukkit.getScheduler().runTask(owner, task), worker, System::nanoTime,
                Bukkit::getPlayer, Bukkit::getWorld, SlabSmooth::fail);
        problems = List.of();
        owner.getLogger().info(describe(parts));
        prewarmSoon(owner);
    }

    /**
     * Main thread. The jobs are closed, so a job under way writes nothing more, then their worker is stopped, which
     * stops a smoothing at its next look at its work budget, and the packs are closed. Never throws: Architect's
     * onDisable goes on after it.
     */
    public static void disable() {
        JavaPlugin owner = plugin;
        SmoothJobs oldJobs = jobs;
        ExecutorService oldWorker = worker;
        Parts oldParts = parts;
        plugin = null;
        services = null;
        jobs = null;
        worker = null;
        parts = null;
        problems = NOT_RUNNING;
        if (oldJobs != null) {
            quietly(owner, oldJobs::close);
        }
        if (oldWorker != null) {
            oldWorker.shutdownNow();
        }
        if (oldParts != null) {
            quietly(owner, oldParts.packs()::close);
        }
    }

    /**
     * From {@link com.mcmiddleearth.architect.ArchitectPlugin#loadData()}, after config.yml was read again: new
     * settings, packs, player options and module switch. Does nothing before enable (onEnable's first loadData comes
     * before it) or while SlabSmooth is off. Main thread.
     */
    public static void reload() {
        JavaPlugin owner = plugin;
        Parts old = parts;
        if (owner == null || old == null || !problems.isEmpty()) {
            return;
        }
        try {
            parts = parts(owner, services);
            owner.getLogger().info(describe(parts));
            prewarmSoon(owner);
        } catch (RuntimeException | LinkageError e) {
            switchOff(owner, "could not reload", e);
        }
        quietly(owner, old.packs()::close); // a job under way keeps the tables it started with
    }

    /** Whether SlabSmooth is running, and nothing has switched it off. Any thread. */
    public static boolean isAvailable() {
        return parts != null && problems.isEmpty();
    }

    /** Why SlabSmooth is unavailable; empty while it is available. */
    public static List<String> problems() {
        return problems;
    }

    /**
     * A LinkageError: WorldEdit or the server is not what this was built for. SlabSmooth switches off until the next
     * restart, and the log says why, once. A job under way ends by itself. Any thread: a brush click runs on FAWE's.
     */
    static void fail(LinkageError error) {
        JavaPlugin owner;
        synchronized (SlabSmooth.class) {
            owner = plugin;
            if (owner == null || !problems.isEmpty()) {
                return; // not running, or off and said why already
            }
            problems = List.of("it stopped working: " + reason(error));
        }
        owner.getLogger().log(Level.WARNING, "SlabSmooth stopped working on " + owner.getServer().getVersion()
                + ", so it is off until the next restart", error);
    }

    /** What the command and brushes use now; null while SlabSmooth isn't running. */
    static Parts parts() {
        return parts;
    }

    /** The jobs; null while SlabSmooth isn't running. Main thread. */
    static SmoothJobs jobs() {
        return jobs;
    }

    /** WorldEdit as SlabSmooth calls it; null while SlabSmooth isn't running. Main thread. */
    static SmoothWorldEdit worldEdit() {
        Services current = services;
        return current == null ? null : current.worldEdit();
    }

    /** What a brush click needs, or null while SlabSmooth is off: the click then does nothing. Any thread. */
    static SmoothBrush.Context brushContext() {
        Parts current = parts;
        return current != null && problems.isEmpty() ? current.brush() : null;
    }

    /**
     * Whether WorldEdit or FastAsyncWorldEdit is running. Asked each time it matters, not at start: Architect starts
     * before either.
     */
    static boolean worldEditRunning() {
        PluginManager plugins = Bukkit.getPluginManager();
        return plugins.isPluginEnabled("WorldEdit") || plugins.isPluginEnabled("FastAsyncWorldEdit");
    }

    /** Whether the server knows a block state. Any thread: Paper's createBlockData has no main-thread check. */
    static boolean validState(String state) {
        try {
            Bukkit.createBlockData(state);
            return true;
        } catch (IllegalArgumentException unknown) {
            return false;
        }
    }

    /** Whether a block state holds a block entity, as a chest does; false for a state the server doesn't know. Any thread. */
    static boolean hasBlockEntity(String state) {
        BlockData data;
        try {
            data = Bukkit.createBlockData(state);
        } catch (IllegalArgumentException unknown) {
            return false;
        }
        return BLOCK_ENTITIES.computeIfAbsent(data.getMaterial(), material -> data.createBlockState() instanceof TileState);
    }

    // ---------------------------------------------------------------------------------------------------------

    private static Parts parts(JavaPlugin owner, Services given) {
        SlabSmoothSettings settings = SlabSmoothSettings.from(owner.getConfig().getConfigurationSection("slabSmooth"),
                warning -> owner.getLogger().warning("slabSmooth in config.yml: " + warning));
        Options defaults = new Options();
        settings.defaults().forEach(defaults::set); // each was checked when the settings were read
        Packs packs = new Packs(owner.getLogger(), owner.getDataFolder().toPath().resolve("slabSmooth"), settings,
                given.sources(), new Packs.Versions(Bukkit.getMinecraftVersion(), owner.getPluginMeta().getVersion()),
                SlabSmooth::validState, SlabSmooth::hasBlockEntity);
        PlayerOptions options = new PlayerOptions(defaults);
        ModuleSwitch module = new ModuleSwitch(task -> Bukkit.getScheduler().runTask(owner, task));
        return new Parts(settings, defaults, packs, options, module,
                new SmoothBrush.Context(packs, options, module, settings.maxBrushRadius(), SlabSmooth::fail));
    }

    private static String describe(Parts current) {
        int known = current.packs().names().size();
        return "SlabSmooth: default pack " + current.settings().defaultPack() + ", " + known
                + (known == 1 ? " pack" : " packs") + " known.";
    }

    /**
     * One tick from now, once every plugin and world is up, every loaded world's switch is read, on the main thread as
     * ModuleSwitch must read them, so a brush click from FAWE's thread knows them all. Then the default pack is read,
     * unless no loaded world has SlabSmooth on.
     */
    private static void prewarmSoon(JavaPlugin owner) {
        Bukkit.getScheduler().runTask(owner, () -> {
            Parts current = parts;
            if (current == null || !problems.isEmpty()) {
                return;
            }
            try {
                boolean anyOn = false;
                for (World world : Bukkit.getWorlds()) {
                    anyOn |= current.module().enabledIn(world); // every world, not only up to the first that is on
                }
                if (anyOn) {
                    current.packs().prewarm();
                }
            } catch (LinkageError e) {
                fail(e);
            } catch (RuntimeException e) {
                owner.getLogger().log(Level.WARNING, "SlabSmooth could not start reading the default pack", e);
            }
        });
    }

    private static void switchOff(JavaPlugin owner, String what, Throwable cause) {
        owner.getLogger().log(Level.WARNING, "SlabSmooth " + what + " on " + owner.getServer().getVersion()
                + ", so it is off", cause);
        problems = List.of("it " + what + ": " + reason(cause));
    }

    /** One step of stopping: a failure is logged, and the steps after it, and the rest of Architect's, still run. */
    private static void quietly(JavaPlugin owner, Runnable step) {
        try {
            step.run();
        } catch (RuntimeException | LinkageError e) {
            if (owner != null) {
                owner.getLogger().log(Level.WARNING, "Stopping SlabSmooth went wrong", e);
            }
        }
    }

    /**
     * The innermost cause's type and message: a wrapper such as ExceptionInInitializerError hides the reason. At most 16
     * causes deep, in case a chain of causes loops. As BiomeTuning's.
     */
    private static String reason(Throwable e) {
        Throwable root = e;
        for (int depth = 0; depth < 16 && root.getCause() != null && root.getCause() != root; depth++) {
            root = root.getCause();
        }
        return root.getClass().getSimpleName() + (root.getMessage() == null ? "" : ": " + root.getMessage());
    }
}
