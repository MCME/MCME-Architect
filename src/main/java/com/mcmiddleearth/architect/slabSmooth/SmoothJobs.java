package com.mcmiddleearth.architect.slabSmooth;

import com.mcmiddleearth.architect.Permission;
import com.mcmiddleearth.architect.PluginData;
import com.mcmiddleearth.architect.slabSmooth.core.Options;
import com.mcmiddleearth.architect.slabSmooth.core.Smoother;
import com.mcmiddleearth.architect.slabSmooth.core.Table;
import com.mcmiddleearth.architect.slabSmooth.core.Tiles;
import com.mcmiddleearth.architect.slabSmooth.core.TooMuchWork;
import com.mcmiddleearth.architect.slabSmooth.core.WorkBudget;
import com.sk89q.worldedit.MaxChangedBlocksException;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.math.Vector3;
import com.sk89q.worldedit.regions.CuboidRegion;
import com.sk89q.worldedit.regions.CylinderRegion;
import com.sk89q.worldedit.regions.Polygonal2DRegion;
import com.sk89q.worldedit.regions.Region;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.LongSupplier;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Runs /sls selection and /sls area as jobs, one at a time on the whole server. A job has five stages:
 * <ol>
 *   <li>main thread, in {@link #start}: the limits, the pack and its tables are checked, the lock is taken, and the
 *       area starts being read (the AreaSource spreads that over ticks);</li>
 *   <li>worker: the smoothing, within the job's work budget;</li>
 *   <li>main thread: the player and the world are looked up again by their ids. The job is dropped if the player has
 *       left the server, if the world is gone, or for a selection if the player has left that world;</li>
 *   <li>the worker on FAWE, which writes off the main thread, else the main thread: the write, as one //undo step of
 *       the player's, leaving alone every block that changed after it was read;</li>
 *   <li>main thread: the chunks are let go, the lock opens and the player is told.</li>
 * </ol>
 * Every stage catches everything, errors included, and ends the job the same way, so the lock and the chunks are
 * always let go. A job keeps the settings and tables it started with, so a reload lets it finish; the next job reads
 * the new ones. The time limit covers reading the area too: a read Paper never finishes is given up then. After
 * {@link #close} no more blocks are set and nothing is said; an edit already open is still closed, so what it set
 * stays one //undo step.
 */
final class SmoothJobs {

    /** The most surfaces one pass of the smoothing may have; more would take too much memory and time. */
    static final int MAX_SURFACES_PER_PASS = 100_000;
    /** A job over this many columns says that it has started, and is logged with its time. */
    private static final long BIG = 4096;

    private static final String FAILED = "Something went wrong; the server log has the details.";
    private static final String TOO_MUCH_WORK = "This area is too much work to smooth, so nothing was changed. Try a smaller selection.";
    private static final String WORLD_GONE = "That world is gone, so nothing was changed.";

    private final Logger log;
    private final Supplier<TableSource> tables;
    private final Supplier<SlabSmoothSettings> settings;
    private final AreaSource areas;
    private final SmoothWorldEdit worldEdit;
    private final Executor mainThread, worker;
    private final LongSupplier nanoClock;
    private final Function<UUID, Player> online;
    private final Function<UUID, World> worlds;
    private final Consumer<LinkageError> broken;
    /** Held by the one job the server runs. */
    private final AtomicBoolean busy = new AtomicBoolean();
    private volatile boolean closed;
    private volatile Job current;

    /**
     * @param tables     the packs' tables as they are now; asked once per job, when it starts
     * @param settings   the settings as they are now; asked once per job, when it starts
     * @param mainThread runs a task on the main thread in a later tick; it may refuse once the plugin is disabled
     * @param worker     runs the smoothing, and on FAWE the write
     * @param nanoClock  the clock the time limit and the job's time are measured on, such as System::nanoTime
     * @param online     the player with this id, or null if they aren't on this server; main thread
     * @param worlds     the world with this id, or null if it isn't loaded; main thread
     * @param broken     told of a LinkageError (WorldEdit or the server not being what this was built for); main thread
     */
    SmoothJobs(Logger log, Supplier<TableSource> tables, Supplier<SlabSmoothSettings> settings, AreaSource areas,
               SmoothWorldEdit worldEdit, Executor mainThread, Executor worker, LongSupplier nanoClock,
               Function<UUID, Player> online, Function<UUID, World> worlds, Consumer<LinkageError> broken) {
        this.log = log;
        this.tables = tables;
        this.settings = settings;
        this.areas = areas;
        this.worldEdit = worldEdit;
        this.mainThread = mainThread;
        this.worker = worker;
        this.nanoClock = nanoClock;
        this.online = online;
        this.worlds = worlds;
        this.broken = broken;
    }

    /**
     * Starts smoothing region in world for player, with a margin around it read but left as it is. Main thread.
     *
     * @param namedPack     the pack to use, or null for the normal choice, which must then be the only pack the region
     *                      covers
     * @param followsPlayer true for a selection, which is dropped if its player has left the world when it writes;
     *                      false for an area, which names its world
     * @param options       with surfaces top, region must be a box, polygon or cylinder: the same shape at every height
     * @throws IllegalArgumentException or IllegalStateException, with a message for the player, when the job is refused;
     *                                  nothing has been read then, and the lock isn't taken
     */
    void start(Player player, World world, Region region, String namedPack, boolean followsPlayer, Options options) {
        Region shape = region.clone(); // the player may change their selection while this runs
        Options opts = options.copy();
        SlabSmoothSettings limits = settings.get();
        TableSource source = tables.get();
        boolean all = !"top".equals(opts.surfaces);
        if (!all && !(shape instanceof CuboidRegion || shape instanceof Polygonal2DRegion || shape instanceof CylinderRegion)) {
            // the top smoother finds a column's surface at any height and keeps to the selection per column, so the
            // shape must be the same at every height
            throw new IllegalArgumentException("With surfaces top, select a box, polygon or cylinder.");
        }
        int margin = Tiles.margin(opts), up = all ? margin : 0;
        BlockVector3 lo = shape.getMinimumPoint(), hi = shape.getMaximumPoint();
        long columns = ((long) hi.x() - lo.x() + 1 + 2L * margin) * ((long) hi.z() - lo.z() + 1 + 2L * margin);
        if (columns > limits.maxColumns()) {
            throw new IllegalArgumentException("Selection too big: " + columns + " columns with the margin, max "
                    + limits.maxColumns() + ".");
        }
        int bottom = world.getMinHeight(), top = world.getMaxHeight() - 1;
        long layers = Math.min((long) hi.y() + up, top) - Math.max((long) lo.y() - up, bottom) + 1;
        if (layers <= 0) {
            throw new IllegalArgumentException("The selection is outside the world's height.");
        }
        if (columns * layers > limits.maxCells()) {
            throw new IllegalArgumentException("Selection too big: " + columns * layers + " blocks with the margin, max "
                    + limits.maxCells() + ".");
        }
        Packs.Choice choice = pack(source, player, world, shape, namedPack);
        boolean mayUseAll = PluginData.hasPermission(player, Permission.SLAB_SMOOTH_ALL_BLOCKS);
        boolean everyBlock = mayUseAll && "all".equals(opts.blocks); // also when blocks: all comes from the config
        Table table = source.table(choice, everyBlock); // throws with a message while the pack is read the first time
        Table tableAll = everyBlock ? table : mayUseAll ? source.table(choice, true) : null; // null: no blocks all hint
        long now = nanoClock.getAsLong();
        Job job = new Job(player, world, followsPlayer, shape, opts, all, columns, choice, table, tableAll, now,
                now + TimeUnit.SECONDS.toNanos(limits.maxSeconds()));
        if (!busy.compareAndSet(false, true)) {
            throw new IllegalStateException("Another smoothing is running; try again in a moment.");
        }
        current = job;
        job.begin(player, world, lo.add(-margin, -up, -margin), hi.add(margin, up, margin));
    }

    /** Whether a job is running, which keeps any other from starting. */
    boolean running() {
        return busy.get();
    }

    /**
     * Main thread; for when the feature is switched off. The running job, if any, sets no more blocks and says
     * nothing: a read still going is given up, and chunks already held are let go now. A write under way stops at its
     * next block, and its edit is still closed, so on FAWE what it set before is written then, as one //undo step.
     */
    void close() {
        closed = true;
        Job job = current;
        if (job != null) {
            job.stop();
        }
    }

    /**
     * The named pack; else the pack the normal choice gives at the region's centre, as Toti's plugin did, if that is
     * the only pack the region covers (Rule 9). The centre counts as one of the samples of its footprint.
     */
    private static Packs.Choice pack(TableSource source, Player player, World world, Region shape, String named) {
        Vector3 centre = shape.getCenter();
        Packs.Choice choice = source.choose(player, new Location(world, centre.x(), centre.y(), centre.z()), named);
        if (named == null) {
            BlockVector3 lo = shape.getMinimumPoint(), hi = shape.getMaximumPoint();
            int bottom = world.getMinHeight(), top = world.getMaxHeight() - 1; // samples above or below the world find no region
            Set<String> packs = new TreeSet<>(PackFootprint.packsIn(world, lo.withY(Math.clamp(lo.y(), bottom, top)),
                    hi.withY(Math.clamp(hi.y(), bottom, top)), player, source));
            packs.add(choice.pack());
            if (packs.size() > 1) {
                throw new IllegalArgumentException("This selection covers more than one pack (" + String.join(", ", packs)
                        + "). Pick one with /sls pack <name>, or select less.");
            }
        }
        return choice;
    }

    /** One job. Its fields are set on the main thread before the stages that read them are handed on. */
    private final class Job {
        private final UUID playerId, worldId;
        private final String playerName;
        private final boolean followsPlayer, all;
        private final Region shape;
        private final Options options;
        private final long columns, started, deadline;
        private final Packs.Choice choice;
        private final Table table, tableAll;
        private final WorkBudget budget;
        private final AtomicBoolean ended = new AtomicBoolean();
        /** Set in stage 1, on the main thread. */
        private CompletableFuture<AreaSource.LoadedArea> load;
        /** Set when the read has ended, on the main thread. */
        private AreaSource.LoadedArea area;
        private boolean released; // main thread

        Job(Player player, World world, boolean followsPlayer, Region shape, Options options, boolean all, long columns,
            Packs.Choice choice, Table table, Table tableAll, long started, long deadline) {
            this.playerId = player.getUniqueId();
            this.playerName = player.getName();
            this.worldId = world.getUID();
            this.followsPlayer = followsPlayer;
            this.shape = shape;
            this.options = options;
            this.all = all;
            this.columns = columns;
            this.choice = choice;
            this.table = table;
            this.tableAll = tableAll;
            this.started = started;
            this.deadline = deadline;
            this.budget = new WorkBudget(nanoClock, deadline, MAX_SURFACES_PER_PASS);
        }

        /** Stage 1, main thread: starts reading min..max, and watches that it ends in time. */
        void begin(Player player, World world, BlockVector3 min, BlockVector3 max) {
            try {
                if (columns > BIG) {
                    PluginData.getMessageUtil().sendInfoMessage(player, "Smoothing " + columns + " columns...");
                }
                load = areas.load(world, min, max);
            } catch (Throwable e) { // such as the scheduler refusing the reader's first run
                fail(e);
                return;
            }
            load.whenComplete(this::loaded);
            if (!load.isDone()) {
                onMain(this::watch);
            }
        }

        /** Main thread, every tick while the area is read: a read that hasn't ended by the deadline is given up. */
        private void watch() {
            if (load.isDone()) {
                return;
            }
            if (nanoClock.getAsLong() - deadline > 0) {
                load.cancel(false); // the reader lets its chunks go at its next run; loaded() ends the job
                return;
            }
            onMain(this::watch);
        }

        /** Main thread, when the read has ended: the smoothing goes to the worker. */
        private void loaded(AreaSource.LoadedArea loaded, Throwable error) {
            if (error == null) {
                area = loaded;
                if (ended.get()) { // ended meanwhile: only its chunks are left to let go
                    letGo();
                    return;
                }
                try {
                    worker.execute(this::smooth);
                } catch (Throwable e) {
                    ended(e);
                }
                return;
            }
            Throwable cause = unwrapped(error);
            if (worlds.apply(worldId) == null) { // unloaded while it was read: Paper may fail the read, or never end it
                finish(() -> tellError(WORLD_GONE));
            } else if (cause instanceof CancellationException) { // given up at the deadline, or by close()
                finish(() -> {
                    log.info(playerName + "'s smoothing of " + columns
                            + " columns stopped: reading the area took longer than the time limit.");
                    tellError(TOO_MUCH_WORK);
                });
            } else if (cause instanceof IllegalArgumentException) { // part of the area hasn't been generated
                finish(() -> tellError(cause.getMessage()));
            } else {
                fail(cause);
            }
        }

        /** Stage 2, worker: reads the copied chunks and smooths them. */
        private void smooth() {
            try {
                if (closed) {
                    onMain(() -> finish(null));
                    return;
                }
                WorldArea read = area.read();
                BlockVector3 lo = shape.getMinimumPoint(), hi = shape.getMaximumPoint();
                WorldArea.CellTest inside = shape instanceof CuboidRegion // most selections: no lookup per block
                        ? (x, y, z) -> x >= lo.x() && x <= hi.x() && y >= lo.y() && y <= hi.y() && z >= lo.z() && z <= hi.z()
                        : (x, y, z) -> shape.contains(BlockVector3.at(x, y, z));
                int midY = (int) (((long) lo.y() + hi.y()) / 2);
                boolean[] editable = all ? read.cells(inside) : read.columns((x, y, z) -> inside.test(x, midY, z));
                Smoother.Result result = read.smooth(table, options, editable, all, budget);
                onMain(() -> check(read, result));
            } catch (Throwable e) {
                onMain(() -> ended(e));
            }
        }

        /** Stage 3, main thread: whether it may write (Rules 1 and 7), then the write, where WorldEdit wants it. */
        private void check(WorldArea read, Smoother.Result result) {
            try {
                if (closed) {
                    finish(null);
                    return;
                }
                Player player = online.apply(playerId);
                if (player == null) { // left the server, or went to another one, where they can't undo it
                    finish(() -> log.info(playerName + " left before their smoothing finished; nothing was changed."));
                    return;
                }
                World world = worlds.apply(worldId);
                if (world == null) {
                    finish(() -> tellError(WORLD_GONE));
                } else if (followsPlayer && !worldId.equals(player.getWorld().getUID())) {
                    finish(() -> tellError("You left the world before the smoothing finished, so nothing was changed."));
                } else if (result == null) {
                    finish(() -> tellError("The selection is outside the world's height."));
                } else if (worldEdit.writesOffTheMainThread()) {
                    worker.execute(() -> write(world, player, read, result, true));
                } else {
                    write(world, player, read, result, false);
                }
            } catch (Throwable e) {
                ended(e);
            }
        }

        /**
         * Stage 4, on the thread the edit belongs to: opens the player's edit of the world, writes what changed (but
         * not over blocks changed since the read), and closes it, once, whatever happened, so what got through is one
         * //undo step.
         */
        private void write(World world, Player player, WorldArea read, Smoother.Result result, boolean offMain) {
            if (closed) {
                handBack(offMain, () -> finish(null));
                return;
            }
            Counting target = null;
            WorldArea.Written written = null;
            Throwable failure = null;
            try {
                target = new Counting(worldEdit.open(world, player));
                written = read.write(target, result, true);
            } catch (Throwable e) {
                failure = e;
            } finally {
                if (target != null) {
                    try {
                        target.close(); // writes what WorldEdit buffered, and keeps it for //undo
                    } catch (Throwable e) {
                        failure = worse(failure, e);
                    }
                }
            }
            long ms = (nanoClock.getAsLong() - started) / 1_000_000; // with close(), which can take much of a write
            int sets = target == null ? 0 : target.sets;
            WorldArea.Written done = written;
            Throwable outcome = failure;
            handBack(offMain, () -> wrote(read, result, done, outcome, sets, ms));
        }

        /** Stage 5, main thread: the player is told how the write went. */
        private void wrote(WorldArea read, Smoother.Result result, WorldArea.Written written, Throwable failure, int sets, long ms) {
            if (failure == null) {
                finish(() -> {
                    tellInfo(SmoothSummary.summary(result.report(), written.blocks(), all) + " [" + choice.pack() + " pack] ("
                            + ms + " ms)" + (written.blocks() > 0 ? ". //undo reverts it." : ".")
                            + (written.skipped() > 0 ? " " + written.skipped() + " blocks changed while it ran were left alone." : "")
                            + (written.blocks() == 0 && written.skipped() == 0 && tableAll != null
                            ? SmoothSummary.blocksHint(read.palette, table, tableAll) : ""));
                    if (columns > BIG) {
                        log.info(playerName + " smoothed " + columns + " columns: " + written.blocks() + " blocks in " + ms + " ms");
                    }
                });
            } else if (failure instanceof BlockTarget.Refused) {
                finish(() -> tellError("FastAsyncWorldEdit refused the edit: " + failure.getMessage()
                        + (sets > 0 ? " //undo reverts what was written." : "")));
            } else if (failure instanceof MaxChangedBlocksException) {
                finish(() -> tellError("Too many blocks for your WorldEdit limit; partly done, //undo reverts it."));
            } else {
                fail(failure);
            }
        }

        /** Main thread: ends the job after stage 2 or 3 threw. */
        private void ended(Throwable e) {
            Throwable cause = unwrapped(e);
            if (cause instanceof TooMuchWork stop) { // nothing has changed: the algorithm only returns blocks at its end
                finish(() -> {
                    log.info(playerName + "'s smoothing of " + columns + " columns stopped: " + stop.getMessage() + ".");
                    tellError(TOO_MUCH_WORK);
                });
            } else {
                fail(cause);
            }
        }

        /**
         * Main thread: ends the job after an unexpected failure. It is logged first, so the cause is there even if
         * telling the player fails; a LinkageError also goes to broken.
         */
        private void fail(Throwable cause) {
            finish(() -> {
                log.log(Level.WARNING, "Smoothing for " + playerName + " failed", cause);
                if (cause instanceof LinkageError linkage) {
                    broken.accept(linkage);
                }
                tellError(FAILED);
            });
        }

        /** Main thread: lets the chunks go and opens the lock, then reports, unless the jobs are closed. Once. */
        private void finish(Runnable report) {
            if (!ended.compareAndSet(false, true)) {
                return;
            }
            Throwable releasing = null;
            try {
                letGo();
            } catch (Throwable e) {
                releasing = e;
            } finally {
                unlock();
            }
            if (closed) {
                return;
            }
            try {
                if (releasing != null) {
                    log.log(Level.WARNING, "Letting the chunks of " + playerName + "'s smoothing go failed", releasing);
                }
                if (report != null) {
                    report.run();
                }
            } catch (Throwable e) {
                log.log(Level.WARNING, "Ending " + playerName + "'s smoothing failed", e);
            }
        }

        /**
         * Any thread: the main thread refused a task, so the plugin is being disabled. The job ends without a word. Its
         * chunks can only be let go on the main thread; Paper drops the tickets of a disabled plugin.
         */
        private void abandon() {
            if (!ended.compareAndSet(false, true)) {
                return;
            }
            CompletableFuture<AreaSource.LoadedArea> reading = load;
            if (reading != null) {
                reading.cancel(false);
            }
            unlock();
        }

        /** Main thread, from close(): gives up a read still going, and lets go of chunks already held. */
        private void stop() {
            if (load != null && !load.isDone()) {
                load.cancel(false); // loaded() ends the job, without a word since the jobs are closed
            }
            letGo();
        }

        /** Main thread: lets the area's chunks go, once; not in a world that is gone, whose tickets Paper drops itself. */
        private void letGo() {
            if (area != null && !released) {
                released = true;
                if (worlds.apply(worldId) != null) {
                    area.release();
                }
            }
        }

        private void unlock() {
            if (current == this) {
                current = null;
            }
            busy.set(false);
        }

        /** Hands a task to the main thread; if it refuses, the job ends without a word. */
        private void onMain(Runnable task) {
            try {
                mainThread.execute(task);
            } catch (Throwable refused) {
                abandon();
            }
        }

        private void handBack(boolean offMain, Runnable task) {
            if (offMain) {
                onMain(task);
            } else {
                task.run();
            }
        }

        /** Main thread: the player is looked up again, as they may have a new Player object or have left. */
        private void tellInfo(String message) {
            Player player = online.apply(playerId);
            if (player != null) {
                PluginData.getMessageUtil().sendInfoMessage(player, message);
            }
        }

        private void tellError(String message) {
            Player player = online.apply(playerId);
            if (player != null) {
                PluginData.getMessageUtil().sendErrorMessage(player, message);
            }
        }
    }

    /**
     * The edit, counting the blocks set, so a refusal can say whether //undo has anything to revert. Once the jobs are
     * closed it stops the write at the next block: no block is set after close().
     */
    private final class Counting implements BlockTarget {
        private final BlockTarget target;
        private int sets;

        Counting(BlockTarget target) {
            this.target = target;
        }

        @Override
        public String stateAt(int x, int y, int z) {
            stopIfClosed();
            return target.stateAt(x, y, z);
        }

        @Override
        public void set(int x, int y, int z, String state) throws MaxChangedBlocksException {
            stopIfClosed();
            target.set(x, y, z, state);
            sets++;
        }

        @Override
        public void close() {
            target.close();
        }

        private void stopIfClosed() {
            if (closed) {
                throw new Stopped();
            }
        }
    }

    /** Stops a write once the jobs are closed. Only thrown then, so it is never reported. */
    private static final class Stopped extends RuntimeException {
        Stopped() {
            super("the smoothing jobs were closed", null, false, false);
        }
    }

    /**
     * When both the write and closing the edit failed: the failure that decides what the player is told, with the other
     * kept as suppressed. An unexpected failure comes first (it must reach the log), then FAWE refusing the edit, then
     * the WorldEdit limit.
     */
    private static Throwable worse(Throwable first, Throwable second) {
        if (first == null || first == second) {
            return second;
        }
        Throwable worse = rank(second) > rank(first) ? second : first;
        worse.addSuppressed(worse == first ? second : first);
        return worse;
    }

    private static int rank(Throwable failure) {
        return failure instanceof MaxChangedBlocksException ? 0 : failure instanceof BlockTarget.Refused ? 1 : 2;
    }

    private static Throwable unwrapped(Throwable e) {
        while ((e instanceof CompletionException || e instanceof ExecutionException) && e.getCause() != null) {
            e = e.getCause();
        }
        return e;
    }
}
