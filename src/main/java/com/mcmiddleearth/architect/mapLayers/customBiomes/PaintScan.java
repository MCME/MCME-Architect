package com.mcmiddleearth.architect.mapLayers.customBiomes;

import com.mcmiddleearth.architect.mapLayers.customBiomes.SelectionSource.ChunkBox;
import org.bukkit.World;
import org.bukkit.plugin.Plugin;

import java.io.IOException;
import java.nio.file.DirectoryIteratorException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.StringJoiner;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.LongSupplier;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The first fill of a world: the custom biomes of its saved region files, read into its record without loading a chunk
 * into the game. Fills run on one thread of their own, {@value #THREAD_NAME}, at the lowest priority, one world at a
 * time; a world started meanwhile waits its turn. After each file the thread sleeps until the bytes read since the
 * fill started, over the time since then, come to at most the configured rate. A fill that stops, for a restart, goes
 * on from the files its record has not finished.
 * <p>
 * The same thread reads the saved copies of chunks for the live checks, as {@link #unloaded} and {@link #selected}
 * say: of a watched chunk that unloaded, once the game has written it, and of a selection's chunks after a WorldEdit
 * command. These reads are small, so they come first, between the files of a fill, and they are not paced.
 * <p>
 * Which thread owns what:
 * <ul>
 *     <li><b>The main thread</b> owns the records, as the store does, and the fills that are waiting or being read, by
 *     world. Every public method runs there. What a file held goes into the record there. A chunk that is loaded at
 *     that moment may be newer than its saved copy, so the copy goes in only where the record holds nothing for it,
 *     and the chunk is handed to the live checks, which read it soon, or as it unloads. What a file held goes in only
 *     while the fill is on and the world's record is the one the fill started with: a fill whose world was switched
 *     off, or off and on again, is stopped there instead. What a look at saved copies found goes in there too, and the
 *     copies that were not written in time are counted there.</li>
 *     <li><b>The scan thread</b> reads the files and the saved copies, and hands what it read to the main thread
 *     through the scheduler: one task a file, one for each tenth of the files read, which the log tells, one at the end,
 *     and one for each round of looks at saved copies. It never touches a record. A fill that fails there with an
 *     Error, such as running out of memory, is logged with its stack trace and dropped on the main thread, and the
 *     next fill runs.</li>
 *     <li><b>Both</b> use the queue of waiting fills, the fill being read, the saved reads waiting for their next look
 *     and the stop flag, under this object's lock. A cancel interrupts the thread only while the fill it ends is the
 *     one being read and no saved copies are, and the thread clears any interrupt before it takes the next fill, so a
 *     cancel never cuts another world's fill short.</li>
 *     <li>Once {@link #stop} has begun, nothing more is handed to the main thread: the plugin may be disabled by then,
 *     and a disabled plugin cannot schedule tasks. What was handed over and has not run yet is dropped, and so are the
 *     saved reads waiting for a look.</li>
 * </ul>
 */
public final class PaintScan implements PaintTracker.SavedReads {

    /** Sleeps on the scan thread, as {@code Thread::sleep} does in production. */
    @FunctionalInterface
    public interface Sleeper {
        void sleep(long millis) throws InterruptedException;
    }

    /** Has a loaded chunk read live soon, and as it unloads if that comes first: {@link PaintTracker#readSoon}. */
    @FunctionalInterface
    public interface LiveReads {
        void readSoon(String world, int chunkX, int chunkZ);
    }

    /** What {@link #start} did: started the world's fill, queued it behind another world's, or found it being read. */
    public enum Start { STARTED, QUEUED, ALREADY }

    /**
     * A world's fill.
     *
     * @param running whether its files are being read, until the last of them has reached the record
     * @param queued  whether it waits for another world's fill
     * @param done    the world's region files read, those read before a restart included
     * @param total   the world's region files
     */
    public record Status(boolean running, boolean queued, int done, int total) {
    }

    static final String THREAD_NAME = "Architect biome scan";
    /**
     * The milliseconds from a saved read's request to its first look, and from each look to the next: the game writes
     * a chunk that unloads a moment later, and a busy disk may hold the write up. The last look is 54 seconds after the
     * request.
     */
    static final long[] LOOK_MILLIS = {2_000, 2_000, 5_000, 15_000, 30_000};
    /** The ticks between two lines about saved copies that were not written in time: a minute. */
    static final long LATE_TICKS = 1200;

    /** How long {@link #stop} waits for the thread to end. */
    private static final long STOP_MILLIS = 5_000;
    /** A region file's name, as the reader takes it, with the region's x and z. */
    private static final Pattern REGION_FILE = Pattern.compile("r\\.(-?\\d{1,6})\\.(-?\\d{1,6})\\.mca");
    /** Chunks along a region's side. */
    private static final int REGION = 32;
    /** A saved read over more regions than this lists the folder for the files that are there. */
    private static final int NAMED_REGIONS = 16;
    private static final Status NO_FILL = new Status(false, false, 0, 0);

    /** One world's fill: made on the main thread, and handed to the scan thread through the queue. */
    private static final class Fill {
        final String world;
        /** The record the fill started with. Only the main thread uses it, to compare and to fill. */
        final PaintRecord record;
        final RegionFileReader reader;
        final Path folder;
        /** The region files to read, in order. */
        final List<String> files;
        final int total;
        /** Counted up by the scan thread. */
        volatile int done;
        /** Set on the main thread when the fill is cancelled, or stopped because its record went. */
        volatile boolean cancelled;

        Fill(String world, PaintRecord record, RegionFileReader reader, Path folder, List<String> files, int total) {
            this.world = world;
            this.record = record;
            this.reader = reader;
            this.folder = folder;
            this.files = List.copyOf(files);
            this.total = total;
            this.done = total - files.size();
        }
    }

    /** A chunk of a region file, and what it holds. */
    private record Read(int x, int z, ChunkPaint paint) {
    }

    /**
     * A read of saved copies: the chunks of a box of one world, whose copies are taken if the game wrote them at
     * {@code since} or later, in epoch seconds. A chunk that unloaded is a box of one. Made on the main thread, and
     * looked at on the scan thread.
     */
    private static final class SavedRead {
        final String world;
        /** The world's record and fill generation when it was asked for, which only the main thread compares. */
        final PaintRecord record;
        final int generation;
        final Path folder;
        final RegionFileReader reader;
        final ChunkBox box;
        final long since;
        /** A chunk that unloaded, which is counted when its copy never comes; else a selection, which is not. */
        final boolean unload;
        /** Scan thread: the chunks whose copy was taken at an earlier look, by key, which later looks leave alone. */
        final Set<Long> taken = new HashSet<>();
        /** Under the lock: the looks made so far, and when the next is due on the clock. */
        int looks;
        long dueNanos;

        SavedRead(String world, PaintRecord record, int generation, Path folder, RegionFileReader reader, ChunkBox box,
                  long since, boolean unload) {
            this.world = world;
            this.record = record;
            this.generation = generation;
            this.folder = folder;
            this.reader = reader;
            this.box = box;
            this.since = since;
            this.unload = unload;
        }

        boolean lastLook() {
            return looks == LOOK_MILLIS.length - 1;
        }

        /** Whether this look still wants the chunk's copy: it lies in the box, and no earlier look took it. */
        boolean wants(int chunkX, int chunkZ) {
            return chunkX >= box.minX() && chunkX <= box.maxX() && chunkZ >= box.minZ() && chunkZ <= box.maxZ()
                    && !taken.contains(PaintRecord.chunkKey(chunkX, chunkZ));
        }

        /** Whether an older copy is read too: at the last look of a chunk that unloaded, for the main thread to judge. */
        boolean takesOlder() {
            return unload && lastLook();
        }
    }

    /** What a saved read's look found of a chunk's copy. */
    private enum Copy {
        /** Written at the read's second or later: it goes into the record. */
        NEW,
        /** At the last look, only a copy from before: the record keeps what it holds. */
        OLDER,
        /** At the last look, no copy that could be read. */
        NONE
    }

    /** A chunk a look found, for the main thread; no paint for {@link Copy#NONE}. */
    private record Found(SavedRead read, int x, int z, ChunkPaint paint, Copy copy) {
    }

    /** What the scan thread does next: the looks at saved copies that are due, or a fill. */
    private record Step(List<SavedRead> due, Fill fill) {
    }

    private final Plugin plugin;
    private final PaintStore store;
    private final Function<World, Path> regionFolder;
    private final Supplier<Set<String>> customIds;
    private final Consumer<String> changed;
    private final LiveReads liveReads;
    private final double bytesPerSecond;
    private final Sleeper sleeper;
    private final LongSupplier nanoClock;

    /** Main thread: the fills waiting or being read, by world, until their end has reached the record. */
    private final Map<String, Fill> fills = new HashMap<>();
    /**
     * Main thread: how many fills each world has started. A saved read asked for before a world's latest fill started
     * is dropped: the world is read again from the start.
     */
    private final Map<String, Integer> generations = new HashMap<>();
    /** Main thread: the chunks whose saved copy was not written in time, by world, since the last line in the log. */
    private final Map<String, Integer> late = new TreeMap<>();
    /** Main thread: reads the saved copies, made at the first saved read with the server's custom biome ids. */
    private RegionFileReader savedReader;
    /** Under the lock: the fills waiting, in turn. */
    private final Deque<Fill> queue = new ArrayDeque<>();
    /** Under the lock: the saved reads waiting for their next look, the soonest first. */
    private final PriorityQueue<SavedRead> savedReads = new PriorityQueue<>(
            (one, other) -> Long.compare(one.dueNanos - other.dueNanos, 0));
    /** Under the lock: the fill being read. */
    private Fill current;
    /** Under the lock: whether the thread is looking at saved copies. */
    private boolean serving;
    /** Under the lock: made at the first need, and made again by a need that finds it dead. */
    private Thread thread;
    /** Set under the lock, read also without it. */
    private volatile boolean stopped;

    /**
     * @param regionFolder       the folder of a world's region files
     * @param customIds          the custom biome ids the server defines; asked on the main thread, once a fill, and once
     *                           for the reads of saved copies
     * @param changed            hears, on the main thread, the name of a world whose record took in a file
     * @param liveReads          is handed, on the main thread, each chunk of a file, or of a look at saved copies, that is
     *                           loaded when what was read goes into the record
     * @param megabytesPerSecond the most the fills read on average, in millions of bytes a second
     * @param sleeper            sleeps on the scan thread: {@code Thread::sleep}
     * @param nanoClock          nanoseconds on a clock that never goes back, which times the looks at saved copies:
     *                           {@code System::nanoTime}
     */
    public PaintScan(Plugin plugin, PaintStore store, Function<World, Path> regionFolder,
                     Supplier<Set<String>> customIds, Consumer<String> changed, LiveReads liveReads,
                     double megabytesPerSecond, Sleeper sleeper, LongSupplier nanoClock) {
        if (!(megabytesPerSecond > 0)) {
            throw new IllegalArgumentException("The scan's megabytes a second must be above 0, not "
                    + megabytesPerSecond);
        }
        this.plugin = plugin;
        this.store = store;
        this.regionFolder = regionFolder;
        this.customIds = customIds;
        this.changed = changed;
        this.liveReads = liveReads;
        this.bytesPerSecond = megabytesPerSecond * 1_000_000;
        this.sleeper = sleeper;
        this.nanoClock = nanoClock;
    }

    /**
     * Main thread: reads the saved copy of a watched chunk that unloaded at {@code since}, once the game has written it.
     * The game writes a chunk that changed as it unloads, a moment later and on a thread of its own, and stamps the
     * second in the region file's header. So the copy is first looked at 2 seconds from now. A copy stamped before
     * {@code since} is the one from before, and the chunk is looked at again, 2, 5, 15 and 30 seconds later. A copy
     * that never comes is counted, unless the record agrees with the older copy: the game does not write a chunk again
     * that did not change, and then nothing was missed. Nor is it counted while the world's fill has still to read the
     * chunk's region file, which puts that copy. A chunk loaded again by then is left to the live reads.
     */
    @Override
    public void unloaded(String world, int chunkX, int chunkZ, long since) {
        ask(new ChunkBox(world, chunkX, chunkZ, chunkX, chunkZ), since, true);
    }

    /**
     * Main thread: reads the saved copies of the box's chunks that the game wrote at {@code since}, a WorldEdit
     * command's second, or later, with the same looks. A chunk that is loaded is left to the live reads. A copy that
     * stays older is left alone and not counted: most WorldEdit commands change nothing, and the game writes only
     * chunks that changed. Only the region files that are there are read, however big the box.
     */
    @Override
    public void selected(ChunkBox box, long since) {
        ask(box, since, false);
    }

    /** Main thread: queues a saved read for its first look, if its world is on and loaded and the scan runs. */
    private void ask(ChunkBox box, long since, boolean unload) {
        PaintRecord record = store.record(box.world());
        World world = plugin.getServer().getWorld(box.world());
        if (stopped || record == null || world == null) {
            return;
        }
        if (savedReader == null) {
            // the ids come from the server's biome registry, which is asked on the main thread
            savedReader = new RegionFileReader(customIds.get());
        }
        SavedRead read = new SavedRead(box.world(), record, generation(box.world()), regionFolder.apply(world),
                savedReader, box, since, unload);
        synchronized (this) {
            if (stopped) {
                return;
            }
            read.dueNanos = nanoClock.getAsLong() + LOOK_MILLIS[0] * 1_000_000;
            savedReads.add(read);
            startThread();
            notifyAll();
        }
    }

    /**
     * Main thread: logs one line for each world with chunks whose saved copy was not written in time since the last
     * call, and starts counting again. The layer calls it once a minute.
     */
    void logLate() {
        late.forEach((world, count) -> plugin.getLogger().info(lateMessage(world, count)));
        late.clear();
    }

    private static String lateMessage(String world, int count) {
        String scan = "; /architect maplayers scan " + world + " catches ";
        return count == 1
                ? "Custom biomes layer: 1 chunk unloaded, but its saved copy was not written in time to be read" + scan
                        + "it."
                : "Custom biomes layer: " + count + " chunks unloaded, but their saved copies were not written in time"
                        + " to be read" + scan + "them.";
    }

    private int generation(String world) {
        return generations.getOrDefault(world, 0);
    }

    /**
     * Starts the world's fill, or queues it behind the one being read. The fill reads the world's region files that
     * its record has not finished, so a fill that a restart stopped goes on where it was. A world waiting already
     * stays where it is in the queue. Main thread only.
     *
     * @throws IllegalStateException when the world is off, or the scan has stopped
     */
    public Start start(World world) {
        String name = world.getName();
        Fill known = fills.get(name);
        if (known != null) {
            return isQueued(known) ? Start.QUEUED : Start.ALREADY;
        }
        PaintRecord record = store.record(name);
        if (record == null) {
            throw new IllegalStateException(name + " is not on the Custom biomes layer");
        }
        Path folder = regionFolder.apply(world);
        List<String> all = regionFiles(name, folder);
        List<String> toRead = new ArrayList<>();
        for (String file : all) {
            if (!record.finishedFiles().contains(file)) {
                toRead.add(file);
            }
        }
        // the ids come from the server's biome registry, which is asked on the main thread
        Fill fill = new Fill(name, record, new RegionFileReader(customIds.get()), folder, toRead, all.size());
        synchronized (this) {
            if (stopped) {
                throw new IllegalStateException("The biome scan has stopped");
            }
            boolean behind = (current != null && !current.cancelled) || !queue.isEmpty();
            queue.add(fill);
            fills.put(name, fill);
            generations.merge(name, 1, Integer::sum);
            startThread();
            notifyAll();
            return behind ? Start.QUEUED : Start.STARTED;
        }
    }

    /** Under the lock: makes the scan thread at the first need, and again if it ever died. */
    private void startThread() {
        if (thread == null || !thread.isAlive()) {
            thread = new Thread(this::work, THREAD_NAME);
            thread.setDaemon(true);
            thread.setPriority(Thread.MIN_PRIORITY);
            thread.start();
        }
    }

    /** The names of the region files in the folder, sorted; none when there is no folder. */
    private List<String> regionFiles(String world, Path folder) {
        List<String> names = new ArrayList<>();
        if (!Files.isDirectory(folder)) {
            return names;
        }
        try (DirectoryStream<Path> files = Files.newDirectoryStream(folder)) {
            for (Path file : files) {
                String name = file.getFileName().toString();
                if (REGION_FILE.matcher(name).matches()) {
                    names.add(name);
                }
            }
        } catch (IOException | DirectoryIteratorException e) {
            plugin.getLogger().log(Level.WARNING, "Custom biomes layer: could not list the saved files of " + world
                    + " in " + folder, e);
        }
        Collections.sort(names);
        return names;
    }

    /** Starts the fill of every loaded world on the layer whose record is not done. Main thread only. */
    public void resumeUnfinished() {
        store.records().forEach((name, record) -> {
            World world = plugin.getServer().getWorld(name);
            if (world != null && !record.fillDone()) {
                start(world);
            }
        });
    }

    /**
     * Drops the world's fill from the queue, or stops it when it is being read. What reached the record stays, and the
     * record is not done. Main thread only.
     */
    public void cancel(String world) {
        Fill fill = fills.remove(world);
        if (fill == null) {
            return;
        }
        fill.cancelled = true;
        synchronized (this) {
            queue.remove(fill);
            if (current == fill && !serving) {
                thread.interrupt(); // cuts its read or its sleep short; after a look at saved copies, it stops anyway
            }
            notifyAll();
        }
    }

    /**
     * Stops every fill for good, drops the saved reads, and waits up to 5 seconds for the thread to end. What reached
     * the records stays, and they are not done, so the next start goes on from there. Main thread only.
     */
    public void stop() {
        Thread worker;
        synchronized (this) {
            stopped = true;
            queue.clear();
            savedReads.clear();
            worker = thread;
            notifyAll();
        }
        fills.clear();
        if (worker == null) {
            return;
        }
        worker.interrupt();
        try {
            worker.join(STOP_MILLIS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /** The world's fill, or neither running nor queued when it has none. Main thread only. */
    public Status status(String world) {
        Fill fill = fills.get(world);
        if (fill == null) {
            return NO_FILL;
        }
        boolean queued = isQueued(fill);
        return new Status(!queued, queued, fill.done, fill.total);
    }

    private synchronized boolean isQueued(Fill fill) {
        return queue.contains(fill);
    }

    /**
     * For tests: waits until the scan thread has no fill to read and none waiting, and no look at saved copies to make
     * that is due by the clock, so that everything it read has been handed to the main thread. The thread is woken
     * first, to look at the clock again: a test may have moved it on.
     *
     * @return false when the time ran out first
     */
    synchronized boolean awaitIdle(Duration timeout) {
        notifyAll();
        long deadline = System.nanoTime() + timeout.toNanos();
        while (current != null || !queue.isEmpty() || serving || lookDue()) {
            long left = deadline - System.nanoTime();
            if (left <= 0) {
                return false;
            }
            try {
                TimeUnit.NANOSECONDS.timedWait(this, left);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        return true;
    }

    /** The scan thread: the looks at saved copies as they come due, and fill after fill, until stopped. */
    private void work() {
        try {
            for (Step step = next(); step != null; step = next()) {
                if (step.due() != null) {
                    serve(step.due());
                    continue;
                }
                Fill fill = step.fill();
                try {
                    read(fill);
                } catch (InterruptedException e) {
                    // cancelled or stopped: what was handed over stays, and the rest waits for another fill
                } catch (Throwable e) {
                    // an Error such as running out of memory: this fill stops, and the next one still runs
                    failed(fill, e);
                }
            }
        } finally {
            synchronized (this) {
                current = null;
                serving = false;
                notifyAll();
            }
        }
    }

    /**
     * Scan thread: says why the fill stopped, with the stack trace, and drops it on the main thread, so that its world
     * is not left reading. What was handed over stays, and the record is not done.
     */
    private void failed(Fill fill, Throwable e) {
        plugin.getLogger().log(Level.WARNING, "Custom biomes layer: reading the saved files of " + fill.world
                + " failed, so its read stopped; /architect maplayers scan " + fill.world + " reads it again from the"
                + " start.", e);
        handOver(() -> drop(fill));
    }

    /** Main thread: forgets the fill, unless a newer one of its world has taken its place. */
    private void drop(Fill fill) {
        fills.remove(fill.world, fill);
    }

    /**
     * Waits for the next work and takes it: the looks at saved copies that are due, which are small, before the next
     * fill, which then is the one being read. Null once the scan has stopped.
     */
    private synchronized Step next() {
        current = null;
        serving = false;
        notifyAll();
        Thread.interrupted(); // an interrupt that cancelled the last fill must not cut the next one short
        while (!stopped) {
            List<SavedRead> due = takeDue();
            if (!due.isEmpty()) {
                serving = true;
                return new Step(due, null);
            }
            if (!queue.isEmpty()) {
                current = queue.poll();
                return new Step(null, current);
            }
            try {
                wait(millisToNextLook());
            } catch (InterruptedException e) {
                // only a stop interrupts the thread while no fill is being read
            }
        }
        return null;
    }

    /** Under the lock: takes the saved reads whose next look is due by the clock. */
    private List<SavedRead> takeDue() {
        List<SavedRead> due = new ArrayList<>();
        long now = nanoClock.getAsLong();
        while (!savedReads.isEmpty() && savedReads.peek().dueNanos - now <= 0) {
            due.add(savedReads.poll());
        }
        return due;
    }

    /** Under the lock: whether a look at saved copies is due by the clock. */
    private boolean lookDue() {
        return !savedReads.isEmpty() && savedReads.peek().dueNanos - nanoClock.getAsLong() <= 0;
    }

    /** Under the lock: how long to wait for the next look, at least a millisecond; 0, for as long as it takes, if none. */
    private long millisToNextLook() {
        if (savedReads.isEmpty()) {
            return 0;
        }
        return Math.max(1, Math.ceilDiv(savedReads.peek().dueNanos - nanoClock.getAsLong(), 1_000_000));
    }

    /** Scan thread, between two files of a fill: the looks at saved copies that are due, which are small, come first. */
    private void serveDue() {
        List<SavedRead> due;
        synchronized (this) {
            due = takeDue();
            if (due.isEmpty()) {
                return;
            }
            serving = true;
        }
        serve(due);
    }

    /**
     * Scan thread: one look at each saved read given, then says it is done. An Error, such as running out of memory,
     * drops these reads with a warning and its stack trace, and the thread goes on.
     */
    private void serve(List<SavedRead> due) {
        try {
            look(due);
        } catch (Throwable e) {
            Set<String> worlds = new TreeSet<>();
            due.forEach(read -> worlds.add(read.world));
            plugin.getLogger().log(Level.WARNING, "Custom biomes layer: reading the saved copies of chunks of "
                    + String.join(", ", worlds) + " failed, so they were not read; /architect maplayers scan with the"
                    + " world catches them.", e);
        } finally {
            synchronized (this) {
                serving = false;
                notifyAll();
            }
        }
    }

    /**
     * Scan thread: one look at each saved read that is due. A region file is opened once a look, however many of its
     * chunks are asked for: a teleport unloads hundreds at once. What the look found goes to the main thread in one
     * hand-over. A read that still waits for a copy is looked at again, until its last look: then a chunk that unloaded
     * and has only an older copy is handed over with it, and one with no copy that can be read is handed over as such.
     */
    private void look(List<SavedRead> due) {
        Map<Path, List<SavedRead>> byFile = new TreeMap<>();
        Map<Path, List<String>> listings = new HashMap<>();
        for (SavedRead read : due) {
            for (Path file : regionFilesOf(read, listings)) {
                byFile.computeIfAbsent(file, key -> new ArrayList<>()).add(read);
            }
        }
        List<Found> found = new ArrayList<>();
        // the reads with a chunk in a region file whose copy has not come, or could not be read
        Set<SavedRead> waiting = new HashSet<>();
        for (Map.Entry<Path, List<SavedRead>> file : byFile.entrySet()) {
            if (stopped) {
                return;
            }
            List<SavedRead> reads = file.getValue();
            if (!Files.isRegularFile(file.getKey())) {
                continue;
            }
            try {
                reads.get(0).reader.readSaved(file.getKey(), (x, z, savedAt) -> {
                    boolean wanted = false;
                    for (SavedRead read : reads) {
                        if (read.wants(x, z)) {
                            if (savedAt >= read.since || read.takesOlder()) {
                                wanted = true;
                            } else {
                                waiting.add(read);
                            }
                        }
                    }
                    return wanted;
                }, (x, z, savedAt, paint) -> {
                    for (SavedRead read : reads) {
                        if (!read.wants(x, z)) {
                            continue;
                        }
                        if (paint == null) {
                            waiting.add(read); // the game may be writing it
                        } else if (savedAt >= read.since || read.takesOlder()) {
                            read.taken.add(PaintRecord.chunkKey(x, z));
                            found.add(new Found(read, x, z, paint, savedAt >= read.since ? Copy.NEW : Copy.OLDER));
                        }
                    }
                });
            } catch (IOException | RuntimeException e) {
                if (stopped) {
                    return; // the interrupt of the stop closed the file
                }
                waiting.addAll(reads); // looked at again, as a copy that has not come
            }
        }
        List<SavedRead> again = new ArrayList<>();
        for (SavedRead read : due) {
            if (read.unload) {
                if (!read.taken.isEmpty()) {
                    continue;
                }
                if (read.lastLook()) {
                    found.add(new Found(read, read.box.minX(), read.box.minZ(), null, Copy.NONE));
                } else {
                    again.add(read);
                }
            } else if (waiting.contains(read) && !read.lastLook()) {
                again.add(read);
            }
        }
        if (!found.isEmpty() && !handOver(() -> mergeSaved(found))) {
            return;
        }
        synchronized (this) {
            if (stopped) {
                return;
            }
            long now = nanoClock.getAsLong();
            for (SavedRead read : again) {
                read.looks++;
                read.dueNanos = now + LOOK_MILLIS[read.looks] * 1_000_000;
                savedReads.add(read);
            }
        }
    }

    /**
     * The region files of the read's world that hold chunks of its box. A box over a few regions names their files,
     * which may not be there; a bigger one takes the files that are there from a listing of the folder, once a look, so
     * that a selection of millions of chunks costs only the files of the world. A folder that cannot be listed has no
     * files.
     */
    private static List<Path> regionFilesOf(SavedRead read, Map<Path, List<String>> listings) {
        int minX = Math.floorDiv(read.box.minX(), REGION);
        int maxX = Math.floorDiv(read.box.maxX(), REGION);
        int minZ = Math.floorDiv(read.box.minZ(), REGION);
        int maxZ = Math.floorDiv(read.box.maxZ(), REGION);
        List<Path> files = new ArrayList<>();
        if ((long) (maxX - minX + 1) * (maxZ - minZ + 1) <= NAMED_REGIONS) {
            for (int x = minX; x <= maxX; x++) {
                for (int z = minZ; z <= maxZ; z++) {
                    files.add(read.folder.resolve("r." + x + "." + z + ".mca"));
                }
            }
            return files;
        }
        for (String name : listings.computeIfAbsent(read.folder, PaintScan::listRegionFiles)) {
            Matcher place = REGION_FILE.matcher(name);
            if (place.matches()) {
                int x = Integer.parseInt(place.group(1));
                int z = Integer.parseInt(place.group(2));
                if (x >= minX && x <= maxX && z >= minZ && z <= maxZ) {
                    files.add(read.folder.resolve(name));
                }
            }
        }
        return files;
    }

    /** The names in a folder; none when it cannot be listed. */
    private static List<String> listRegionFiles(Path folder) {
        List<String> names = new ArrayList<>();
        try (DirectoryStream<Path> files = Files.newDirectoryStream(folder)) {
            for (Path file : files) {
                names.add(file.getFileName().toString());
            }
        } catch (IOException | DirectoryIteratorException e) {
            // not there, or not readable: nothing to read in it
        }
        return names;
    }

    /**
     * Main thread: what the looks found goes into the records, unless the scan has stopped, or the world is off, or on
     * with another record, or read again from the start since the read was asked for. A chunk loaded again goes to the
     * live reads instead, as its copy may be older than what the game holds now, and the record is left as it is. A copy
     * written at the read's second or later goes in, an empty one taking the chunk out. An older copy, from a chunk's
     * last look, never goes in: the chunk is counted when the record differs from it, as then something may be missed,
     * and not when they agree, as the game writes again only a chunk that changed. Nor while the world's fill has still
     * to put the chunk's region file into the record, which holds nothing from it until then: the fill puts that copy.
     * A chunk with no copy is counted.
     */
    private void mergeSaved(List<Found> found) {
        if (stopped) {
            return;
        }
        Set<String> changedWorlds = new TreeSet<>();
        for (Found one : found) {
            SavedRead read = one.read();
            if (store.record(read.world) != read.record || generation(read.world) != read.generation) {
                continue;
            }
            World world = plugin.getServer().getWorld(read.world);
            if (world != null && world.isChunkLoaded(one.x(), one.z())) {
                liveReads.readSoon(read.world, one.x(), one.z());
                continue;
            }
            switch (one.copy()) {
                case NEW -> {
                    if (read.record.put(one.x(), one.z(), one.paint())) {
                        changedWorlds.add(read.world);
                    }
                }
                case OLDER -> {
                    if (!read.record.get(one.x(), one.z()).equals(one.paint())
                            && !fillWillPut(read.world, one.x(), one.z())) {
                        late.merge(read.world, 1, Integer::sum);
                    }
                }
                case NONE -> late.merge(read.world, 1, Integer::sum);
            }
        }
        for (String world : changedWorlds) {
            store.dirty(world);
            changed.accept(world);
        }
    }

    /**
     * Main thread: whether the world's fill, waiting or being read, has the chunk's region file among its files and has
     * not yet put it into the record.
     */
    private boolean fillWillPut(String world, int chunkX, int chunkZ) {
        Fill fill = fills.get(world);
        if (fill == null) {
            return false;
        }
        String file = "r." + Math.floorDiv(chunkX, REGION) + "." + Math.floorDiv(chunkZ, REGION) + ".mca";
        return fill.files.contains(file) && !fill.record.finishedFiles().contains(file);
    }

    /**
     * Reads the fill's files, each handed to the main thread and paced, then hands over the end. Each tenth of the
     * world's files that is reached, but the last, is handed over as a line for the log. Scan thread.
     */
    private void read(Fill fill) throws InterruptedException {
        long started = System.nanoTime();
        long bytes = 0;
        Totals totals = new Totals();
        int tenthsTold = tenths(fill.done, fill.total); // a fill that a restart stopped told the ones before
        for (String name : fill.files) {
            serveDue();
            if (fill.cancelled || stopped) {
                return;
            }
            List<Read> reads = new ArrayList<>();
            RegionFileReader.Stats stats = null;
            try {
                Path file = fill.folder.resolve(name);
                long size = Files.size(file);
                stats = fill.reader.read(file, (x, z, paint) -> reads.add(new Read(x, z, paint)));
                bytes += size;
            } catch (IOException | RuntimeException e) {
                if (fill.cancelled || stopped) {
                    return; // the interrupt of a cancel or a stop closed the file
                }
                totals.skip("unreadable region file: " + e.getClass().getSimpleName());
            }
            if (stats != null) {
                totals.add(stats);
                if (!handOver(() -> merge(fill, name, reads))) {
                    return;
                }
            }
            fill.done++;
            int tenths = tenths(fill.done, fill.total);
            if (tenths > tenthsTold && fill.done < fill.total) {
                tenthsTold = tenths;
                int done = fill.done;
                if (!handOver(() -> progress(fill, done))) {
                    return;
                }
            }
            pace(started, bytes);
        }
        String summary = totals.summary(fill.world);
        handOver(() -> finish(fill, summary));
    }

    /** How many whole tenths of the files are read. */
    private static int tenths(int done, int total) {
        return total == 0 ? 0 : (int) (10L * done / total);
    }

    /** Sleeps until the bytes read since the fill started, over the time since then, come to at most the rate. */
    private void pace(long startedNanos, long bytes) throws InterruptedException {
        long dueNanos = (long) (bytes / bytesPerSecond * 1e9);
        long aheadNanos = dueNanos - (System.nanoTime() - startedNanos);
        if (aheadNanos > 0) {
            sleeper.sleep(Math.ceilDiv(aheadNanos, 1_000_000)); // whole milliseconds, rounded up
        }
    }

    /**
     * Runs the task on the main thread, unless the scan has stopped. {@link #stop} sets its flag under the same lock,
     * so nothing is handed over once it has begun. A scheduler that refuses the task, as it does once the plugin is
     * disabled, stops the scan as well.
     *
     * @return whether the task was handed over
     */
    private synchronized boolean handOver(Runnable task) {
        if (stopped) {
            return false;
        }
        try {
            plugin.getServer().getScheduler().runTask(plugin, task);
            return true;
        } catch (RuntimeException e) {
            stopped = true;
            queue.clear();
            return false;
        }
    }

    /**
     * Main thread: what a file held goes into the record. A chunk loaded now may be newer than its saved copy: the copy
     * goes in only where the record holds nothing for the chunk, so that it never overwrites a live read, and the
     * chunk is handed to the live reads.
     */
    private void merge(Fill fill, String file, List<Read> reads) {
        if (!stillOn(fill)) {
            return;
        }
        World world = plugin.getServer().getWorld(fill.world);
        for (Read read : reads) {
            if (world == null || !world.isChunkLoaded(read.x(), read.z())) {
                fill.record.put(read.x(), read.z(), read.paint());
                continue;
            }
            if (!fill.record.chunks().containsKey(PaintRecord.chunkKey(read.x(), read.z()))) {
                fill.record.put(read.x(), read.z(), read.paint());
            }
            liveReads.readSoon(fill.world, read.x(), read.z());
        }
        fill.record.finished(file);
        store.dirty(fill.world);
        changed.accept(fill.world);
    }

    /** Main thread: the log says how far the fill is, while it is still on. */
    private void progress(Fill fill, int done) {
        if (stillOn(fill)) {
            plugin.getLogger().info("Custom biomes layer: reading " + fill.world + "'s saved files: " + done + " of "
                    + fill.total + " region files.");
        }
    }

    /** Main thread: the fill has read every file. */
    private void finish(Fill fill, String summary) {
        if (!stillOn(fill)) {
            return;
        }
        fills.remove(fill.world);
        fill.record.fillDone(true);
        store.dirty(fill.world);
        plugin.getLogger().info(summary);
    }

    /**
     * Main thread: whether the fill's results may still go into its record. Not once the fill was cancelled or the
     * scan stopped; and a fill whose record is not the world's record any more, as the world was switched off, or off
     * and on again, is stopped here.
     */
    private boolean stillOn(Fill fill) {
        if (fills.get(fill.world) != fill) {
            return false;
        }
        if (store.record(fill.world) != fill.record) {
            cancel(fill.world);
            return false;
        }
        return true;
    }

    /** What a fill's files held, for the log line at its end. Scan thread only. */
    private static final class Totals {
        private long chunks;
        private long painted;
        private final Map<String, Integer> skipped = new TreeMap<>();

        void add(RegionFileReader.Stats stats) {
            chunks += stats.chunks();
            painted += stats.painted();
            stats.skipped().forEach((reason, count) -> skipped.merge(reason, count, Integer::sum));
        }

        void skip(String reason) {
            skipped.merge(reason, 1, Integer::sum);
        }

        String summary(String world) {
            String text = "Custom biomes layer: read " + world + "'s saved files: " + chunks + " chunks, " + painted
                    + " with custom biomes.";
            if (skipped.isEmpty()) {
                return text;
            }
            StringJoiner reasons = new StringJoiner(", ", " Skipped: ", "");
            skipped.forEach((reason, count) -> reasons.add(reason + " " + count));
            return text + reasons;
        }
    }
}
