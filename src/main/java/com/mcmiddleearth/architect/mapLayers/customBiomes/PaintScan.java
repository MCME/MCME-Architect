package com.mcmiddleearth.architect.mapLayers.customBiomes;

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
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.StringJoiner;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.regex.Pattern;

/**
 * The first fill of a world: the custom biomes of its saved region files, read into its record without loading a chunk
 * into the game. Fills run on one thread of their own, {@value #THREAD_NAME}, at the lowest priority, one world at a
 * time; a world started meanwhile waits its turn. After each file the thread sleeps until the bytes read since the
 * fill started, over the time since then, come to at most the configured rate. A fill that stops, for a restart, goes
 * on from the files its record has not finished.
 * <p>
 * Which thread owns what:
 * <ul>
 *     <li><b>The main thread</b> owns the records, as the store does, and the fills that are waiting or being read, by
 *     world. Every public method runs there. What a file held goes into the record there. A chunk that is loaded at
 *     that moment may be newer than its saved copy, so the copy goes in only where the record holds nothing for it,
 *     and the chunk is handed to the live checks, which read it soon, or as it unloads. What a file held goes in only
 *     while the fill is on and the world's record is the one the fill started with: a fill whose world was switched
 *     off, or off and on again, is stopped there instead.</li>
 *     <li><b>The scan thread</b> reads the files and hands what it read to the main thread through the scheduler, one
 *     task a file, one for each tenth of the files read, which the log tells, and one at the end. It never touches a
 *     record.</li>
 *     <li><b>Both</b> use the queue of waiting fills, the fill being read and the stop flag, under this object's lock.
 *     A cancel interrupts the thread only while the fill it ends is the one being read, and the thread clears any
 *     interrupt before it takes the next fill, so a cancel never cuts another world's fill short.</li>
 *     <li>Once {@link #stop} has begun, nothing more is handed to the main thread: the plugin may be disabled by then,
 *     and a disabled plugin cannot schedule tasks. What was handed over and has not run yet is dropped.</li>
 * </ul>
 */
public final class PaintScan {

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

    /** How long {@link #stop} waits for the thread to end. */
    private static final long STOP_MILLIS = 5_000;
    /** A region file's name, as the reader takes it. */
    private static final Pattern REGION_FILE = Pattern.compile("r\\.-?\\d{1,6}\\.-?\\d{1,6}\\.mca");
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

    private final Plugin plugin;
    private final PaintStore store;
    private final Function<World, Path> regionFolder;
    private final Supplier<Set<String>> customIds;
    private final Consumer<String> changed;
    private final LiveReads liveReads;
    private final double bytesPerSecond;
    private final Sleeper sleeper;

    /** Main thread: the fills waiting or being read, by world, until their end has reached the record. */
    private final Map<String, Fill> fills = new HashMap<>();
    /** Under the lock: the fills waiting, in turn. */
    private final Deque<Fill> queue = new ArrayDeque<>();
    /** Under the lock: the fill being read. */
    private Fill current;
    /** Under the lock: made at the first start. */
    private Thread thread;
    /** Set under the lock, read also without it. */
    private volatile boolean stopped;

    /**
     * @param regionFolder       the folder of a world's region files
     * @param customIds          the custom biome ids the server defines; asked on the main thread, once a fill
     * @param changed            hears, on the main thread, the name of a world whose record took in a file
     * @param liveReads          is handed, on the main thread, each chunk of a file that is loaded when the file goes
     *                           into the record
     * @param megabytesPerSecond the most the fills read on average, in millions of bytes a second
     * @param sleeper            sleeps on the scan thread: {@code Thread::sleep}
     */
    public PaintScan(Plugin plugin, PaintStore store, Function<World, Path> regionFolder,
                     Supplier<Set<String>> customIds, Consumer<String> changed, LiveReads liveReads,
                     double megabytesPerSecond, Sleeper sleeper) {
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
            if (thread == null) {
                thread = new Thread(this::work, THREAD_NAME);
                thread.setDaemon(true);
                thread.setPriority(Thread.MIN_PRIORITY);
                thread.start();
            }
            notifyAll();
            return behind ? Start.QUEUED : Start.STARTED;
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
            if (current == fill) {
                thread.interrupt(); // cuts its read or its sleep short
            }
            notifyAll();
        }
    }

    /**
     * Stops every fill for good, and waits up to 5 seconds for the thread to end. What reached the records stays, and
     * they are not done, so the next start goes on from there. Main thread only.
     */
    public void stop() {
        Thread worker;
        synchronized (this) {
            stopped = true;
            queue.clear();
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
     * For tests: waits until the scan thread has no fill to read and none waiting, so that everything it read has been
     * handed to the main thread.
     *
     * @return false when the time ran out first
     */
    synchronized boolean awaitIdle(Duration timeout) {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (current != null || !queue.isEmpty()) {
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

    /** The scan thread: fill after fill, until stopped. */
    private void work() {
        try {
            for (Fill fill = next(); fill != null; fill = next()) {
                try {
                    read(fill);
                } catch (InterruptedException e) {
                    // cancelled or stopped: what was handed over stays, and the rest waits for another fill
                }
            }
        } finally {
            synchronized (this) {
                current = null;
                notifyAll();
            }
        }
    }

    /** Waits for the next fill and makes it the one being read; null once the scan has stopped. */
    private synchronized Fill next() {
        current = null;
        notifyAll();
        Thread.interrupted(); // an interrupt that cancelled the last fill must not cut the next one short
        while (!stopped && queue.isEmpty()) {
            try {
                wait();
            } catch (InterruptedException e) {
                // only a stop interrupts the thread while no fill is being read
            }
        }
        if (stopped) {
            return null;
        }
        current = queue.poll();
        return current;
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
