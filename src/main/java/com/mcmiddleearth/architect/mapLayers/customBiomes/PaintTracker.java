package com.mcmiddleearth.architect.mapLayers.customBiomes;

import com.mcmiddleearth.architect.mapLayers.customBiomes.SelectionSource.ChunkBox;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.command.PluginIdentifiableCommand;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayDeque;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.PriorityQueue;
import java.util.Queue;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.LongSupplier;
import java.util.function.Predicate;
import java.util.logging.Level;

/**
 * Keeps the custom biome records current from the chunks that are loaded, in about half a millisecond of main-thread
 * time a tick. In the worlds that are on, it reads:
 * <ul>
 *     <li>every 10 seconds, the chunks within 2 of each builder (a player passing {@code isBuilder});</li>
 *     <li>2 seconds and again 30 seconds after a WorldEdit command, the loaded chunks of the player's selection;</li>
 *     <li>once every 2 minutes, every loaded chunk: the slow pass;</li>
 *     <li>as it unloads, a watched chunk: one within 4 of a builder, or in a selection.</li>
 * </ul>
 * Only watched chunks are checked as they unload. Reading biomes costs about 0.1 to 0.3 ms a chunk, and a server
 * unloads many chunks a tick. Paint elsewhere is still found by the slow pass while its chunk stays loaded, and always
 * by a new scan.
 * <p>
 * Each tick reads queued chunks, those of builders, selections and refreshes before those of the slow pass, until
 * {@link #TICK_BUDGET_NANOS} have passed on the clock, but at least one chunk if any is queued. The slow pass starts a
 * new round only when the last one is done and 2 minutes have passed since it began, so an idle server spends nothing.
 * <p>
 * A chunk is read only if it is loaded when its turn comes, not when it was queued: the game answers about an unloaded
 * chunk with the biomes its generator would make, which would write a wrong entry. The check at unload runs inside
 * the event, while the chunk is still loaded.
 * <p>
 * Main thread only.
 */
public final class PaintTracker implements Listener {

    /** Reads the custom biomes of a loaded chunk. */
    public interface ChunkReader {
        ChunkPaint read(World world, int chunkX, int chunkZ);
    }

    /** What one tick may spend on reading, on the {@code nanoClock}: half a millisecond. */
    static final long TICK_BUDGET_NANOS = 500_000;
    /** The ticks between two looks at the builders: 10 seconds. */
    static final int BUILDER_TICKS = 200;
    /** The chunks this near a builder, in each direction, are read at each look. */
    static final int BUILDER_RADIUS = 2;
    /** The chunks this near a builder, in each direction, are watched at each look. */
    static final int WATCH_RADIUS = 4;
    /** The ticks from the start of a slow pass to the earliest start of the next: 2 minutes. */
    static final int PASS_TICKS = 2400;
    /** A selection is read this many ticks after the WorldEdit command that may have changed it... */
    static final int SELECTION_READ_TICKS = 40;
    /** ...and again this many ticks after it, for an edit that is not finished by then. */
    static final int SELECTION_REREAD_TICKS = 600;
    /**
     * A selection of more chunks than this is watched only where it is loaded when the command comes. Watching each
     * chunk of a selection that spans a continent would cost more than the reading it saves.
     */
    static final long WATCH_IN_FULL_CHUNKS = 16_384;

    private static final String WORLD_EDIT = "WorldEdit";
    private static final String FAST_ASYNC_WORLD_EDIT = "FastAsyncWorldEdit";

    /** A chunk, by the name of its world, which is how the records and the selections name it. */
    private record ChunkRef(String world, int x, int z) {}

    /** A selection to read once {@code tick} has come. */
    private record DueRead(long tick, ChunkBox box) {}

    private final Plugin plugin;
    private final Function<String, PaintRecord> recordOf;
    private final ChunkReader reader;
    private final SelectionSource selections;
    private final Predicate<String> isWorldEditCommand;
    private final Predicate<Player> isBuilder;
    private final Consumer<String> changed;
    private final LongSupplier nanoClock;

    /** Builders', selections' and refreshed chunks, in the order they were queued. Read before the slow pass. */
    private final Set<ChunkRef> priority = new LinkedHashSet<>();
    /** The chunks the running slow pass has still to read. */
    private final Deque<ChunkRef> pass = new ArrayDeque<>();
    /** Chunks to read as they unload. */
    private final Set<ChunkRef> watched = new HashSet<>();
    /** Selections to queue, the soonest first. */
    private final Queue<DueRead> dueReads = new PriorityQueue<>(Comparator.comparingLong(DueRead::tick));
    private BukkitTask task;
    /** Counts the ticks since {@link #start}, so that the timers need no tasks of their own. */
    private long tick;
    private long passStartedAt;
    private boolean warned;

    /**
     * @param recordOf           the record of a world by its name, or null for a world that is off
     * @param reader             reads a loaded chunk
     * @param selections         where a player's WorldEdit selection lies
     * @param isWorldEditCommand whether a command as typed, such as {@code //setbiome cbc:a}, is one of WorldEdit's;
     *                           see {@link #worldEditCommands}
     * @param isBuilder          whether a player is followed: the chunks around the player are read often
     * @param changed            hears the name of a world whose record changed
     * @param nanoClock          nanoseconds on a clock that never goes back; {@code System::nanoTime} in production
     */
    public PaintTracker(Plugin plugin, Function<String, PaintRecord> recordOf, ChunkReader reader,
                        SelectionSource selections, Predicate<String> isWorldEditCommand, Predicate<Player> isBuilder,
                        Consumer<String> changed, LongSupplier nanoClock) {
        this.plugin = plugin;
        this.recordOf = recordOf;
        this.reader = reader;
        this.selections = selections;
        this.isWorldEditCommand = isWorldEditCommand;
        this.isBuilder = isBuilder;
        this.changed = changed;
        this.nanoClock = nanoClock;
    }

    /**
     * Whether a command is one of WorldEdit's or FastAsyncWorldEdit's: the label, which ends at the first space and
     * leaves out one leading slash, is looked up in the server's command map, and the command must belong to either
     * plugin. WorldEdit registers its commands with a slash in the name, so {@code //setbiome} has the label
     * {@code /setbiome}.
     */
    public static Predicate<String> worldEditCommands(Server server) {
        return typed -> {
            String command = typed.startsWith("/") ? typed.substring(1) : typed;
            int space = command.indexOf(' ');
            String label = space < 0 ? command : command.substring(0, space);
            return server.getCommandMap().getCommand(label) instanceof PluginIdentifiableCommand owned
                    && isWorldEdit(owned.getPlugin());
        };
    }

    private static boolean isWorldEdit(Plugin plugin) {
        if (plugin == null) {
            return false;
        }
        String name = plugin.getName();
        return WORLD_EDIT.equals(name) || FAST_ASYNC_WORLD_EDIT.equals(name);
    }

    /** Starts listening, and reading each tick. The first slow pass begins at the first tick. */
    public void start() {
        if (task != null) {
            return;
        }
        tick = 0;
        passStartedAt = -PASS_TICKS;
        warned = false;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        task = plugin.getServer().getScheduler().runTaskTimer(plugin, this::everyTick, 1, 1);
    }

    /** Stops listening and reading, and forgets what was queued and watched. */
    public void stop() {
        if (task == null) {
            return;
        }
        HandlerList.unregisterAll(this);
        task.cancel();
        task = null;
        priority.clear();
        pass.clear();
        watched.clear();
        dueReads.clear();
    }

    /** Queues every loaded chunk of the worlds that are on, ahead of the slow pass. */
    public void readLoadedNow() {
        forEachLoadedChunkOfWorldsThatAreOn(priority::add);
    }

    private void everyTick() {
        tick++;
        if (tick % BUILDER_TICKS == 0) {
            followBuilders();
        }
        while (!dueReads.isEmpty() && dueReads.peek().tick() <= tick) {
            queueSelection(dueReads.poll().box());
        }
        if (pass.isEmpty() && tick - passStartedAt >= PASS_TICKS) {
            passStartedAt = tick;
            forEachLoadedChunkOfWorldsThatAreOn(pass::add);
        }
        readQueued();
    }

    /** Queues the loaded chunks near each builder and watches the ones a little further out, in worlds that are on. */
    private void followBuilders() {
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            World world = player.getWorld();
            if (recordOf.apply(world.getName()) == null || !isBuilder.test(player)) {
                continue;
            }
            Location at = player.getLocation();
            int chunkX = at.getBlockX() >> 4;
            int chunkZ = at.getBlockZ() >> 4;
            for (int x = chunkX - WATCH_RADIUS; x <= chunkX + WATCH_RADIUS; x++) {
                for (int z = chunkZ - WATCH_RADIUS; z <= chunkZ + WATCH_RADIUS; z++) {
                    if (!world.isChunkLoaded(x, z)) {
                        continue;
                    }
                    ChunkRef ref = new ChunkRef(world.getName(), x, z);
                    watched.add(ref);
                    if (Math.abs(x - chunkX) <= BUILDER_RADIUS && Math.abs(z - chunkZ) <= BUILDER_RADIUS) {
                        priority.add(ref);
                    }
                }
            }
        }
    }

    /** Queues the loaded chunks of a selection, if its world is still on. */
    private void queueSelection(ChunkBox box) {
        World world = plugin.getServer().getWorld(box.world());
        if (world != null && recordOf.apply(box.world()) != null) {
            forEachLoadedIn(world, box, priority::add);
        }
    }

    /**
     * Watches the chunks of a selection, loaded or not: a WorldEdit edit may load a chunk, change it and let it go
     * again before the selection is read. A selection too big for that is watched where it is loaded.
     */
    private void watch(ChunkBox box) {
        if (area(box) <= WATCH_IN_FULL_CHUNKS) {
            for (int x = box.minX(); x <= box.maxX(); x++) {
                for (int z = box.minZ(); z <= box.maxZ(); z++) {
                    watched.add(new ChunkRef(box.world(), x, z));
                }
            }
            return;
        }
        World world = plugin.getServer().getWorld(box.world());
        if (world != null) {
            forEachLoadedIn(world, box, watched::add);
        }
    }

    private static long area(ChunkBox box) {
        return ((long) box.maxX() - box.minX() + 1) * ((long) box.maxZ() - box.minZ() + 1);
    }

    /**
     * Hands {@code action} each loaded chunk of the box. It asks about the box's chunks one by one, or lists what is
     * loaded in the world, whichever is the shorter: a box can span millions of chunks and a world has a few thousand.
     */
    private static void forEachLoadedIn(World world, ChunkBox box, Consumer<ChunkRef> action) {
        String name = world.getName();
        if (area(box) <= world.getChunkCount()) {
            for (int x = box.minX(); x <= box.maxX(); x++) {
                for (int z = box.minZ(); z <= box.maxZ(); z++) {
                    if (world.isChunkLoaded(x, z)) {
                        action.accept(new ChunkRef(name, x, z));
                    }
                }
            }
            return;
        }
        for (Chunk chunk : world.getLoadedChunks()) {
            if (chunk.getX() >= box.minX() && chunk.getX() <= box.maxX()
                    && chunk.getZ() >= box.minZ() && chunk.getZ() <= box.maxZ()) {
                action.accept(new ChunkRef(name, chunk.getX(), chunk.getZ()));
            }
        }
    }

    private void forEachLoadedChunkOfWorldsThatAreOn(Consumer<ChunkRef> action) {
        for (World world : plugin.getServer().getWorlds()) {
            String name = world.getName();
            if (recordOf.apply(name) == null) {
                continue;
            }
            for (Chunk chunk : world.getLoadedChunks()) {
                action.accept(new ChunkRef(name, chunk.getX(), chunk.getZ()));
            }
        }
    }

    /**
     * Reads queued chunks until the clock shows the tick's budget spent, but at least one, so the queues drain whatever
     * a read costs. A queued chunk that is skipped, because its world is off or it has unloaded, does not count as a
     * read and does not end the tick: the next one is tried.
     */
    private void readQueued() {
        if (priority.isEmpty() && pass.isEmpty()) {
            return;
        }
        long started = nanoClock.getAsLong();
        boolean readOne = false;
        for (ChunkRef ref = next(); ref != null; ref = next()) {
            readOne |= read(ref);
            if (readOne && nanoClock.getAsLong() - started >= TICK_BUDGET_NANOS) {
                return;
            }
        }
    }

    private ChunkRef next() {
        Iterator<ChunkRef> first = priority.iterator();
        if (first.hasNext()) {
            ChunkRef ref = first.next();
            first.remove();
            return ref;
        }
        return pass.poll();
    }

    /**
     * Reads the chunk into its world's record if the world is on and the chunk is loaded now, and tells {@code changed}
     * when that changed the record.
     *
     * @return whether the reader was asked, which is what a tick's budget is spent on
     */
    private boolean read(ChunkRef ref) {
        PaintRecord record = recordOf.apply(ref.world());
        World world = plugin.getServer().getWorld(ref.world());
        if (record == null || world == null || !world.isChunkLoaded(ref.x(), ref.z())) {
            return false;
        }
        ChunkPaint paint;
        try {
            paint = reader.read(world, ref.x(), ref.z());
        } catch (RuntimeException e) {
            // one warning, not one for every chunk of a pass: a reader that fails here fails for most chunks
            if (!warned) {
                warned = true;
                plugin.getLogger().log(Level.WARNING, "Custom biomes layer: could not read the biomes of chunk "
                        + ref.x() + ", " + ref.z() + " in " + ref.world() + ". Other chunks are still read, and "
                        + "failures are logged once until the next start.", e);
            }
            return true;
        }
        if (record.put(ref.x(), ref.z(), paint)) {
            changed.accept(ref.world());
        }
        return true;
    }

    /** A WorldEdit command may change the biomes of the selection: it is read twice, and watched until it unloads. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        if (!isWorldEditCommand.test(event.getMessage())) {
            return;
        }
        selections.chunks(event.getPlayer()).ifPresent(box -> {
            if (recordOf.apply(box.world()) != null) {
                watch(box);
                dueReads.add(new DueRead(tick + SELECTION_READ_TICKS, box));
                dueReads.add(new DueRead(tick + SELECTION_REREAD_TICKS, box));
            }
        });
    }

    /** A watched chunk is read at once, whatever the tick has spent, and is watched no more. */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onChunkUnload(ChunkUnloadEvent event) {
        if (watched.isEmpty()) {
            return;
        }
        Chunk chunk = event.getChunk();
        ChunkRef ref = new ChunkRef(chunk.getWorld().getName(), chunk.getX(), chunk.getZ());
        if (watched.remove(ref)) {
            read(ref);
        }
    }
}
