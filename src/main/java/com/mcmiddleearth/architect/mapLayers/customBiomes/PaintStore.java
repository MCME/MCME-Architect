package com.mcmiddleearth.architect.mapLayers.customBiomes;

import java.io.IOException;
import java.nio.file.DirectoryIteratorException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.function.LongPredicate;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * The records of the worlds on the Custom biomes layer, and their files: {@code <world>.dat} in one folder. A world is
 * on while it has a record. A file that cannot be read is set aside as {@code <world>.dat.bak} and its world is read
 * again; a world switched off has its file set aside as {@code <world>.dat.off}.
 * <p>
 * Which thread owns what:
 * <ul>
 *     <li><b>The main thread</b> owns the records and which of them changed since their last save. Every method runs
 *     there.</li>
 *     <li><b>The writer</b> owns the files once {@link #load} has read them. A save writes a copy of the record taken
 *     on the main thread when the save was asked for, so the record may go on changing meanwhile. Saves and the move
 *     of a switched-off world's file run in the order they were asked for, so two writes of one file never overlap,
 *     and a file set aside is not written again by a save that was still waiting. That needs {@code writer} to run
 *     what it is given one at a time, in that order.</li>
 * </ul>
 */
public final class PaintStore {

    /** The ticks between two saves of the records that changed: a minute. */
    static final long SAVE_TICKS = 1200;

    private static final String DAT = ".dat";

    private final Path folder;
    private final Logger logger;
    private final Executor writer;

    /** Main thread: the record of each world that is on, by name. */
    private final Map<String, PaintRecord> records = new TreeMap<>();
    /** Main thread: the worlds whose record changed since it was last handed to the writer. */
    private final Set<String> dirty = new HashSet<>();
    /** Each world's saves handed to the writer and not yet done: counted up on the main thread, down on the writer. */
    private final Map<String, Integer> saving = new ConcurrentHashMap<>();

    /**
     * @param folder where the files are, made at the first save
     * @param logger where a file that cannot be read, saved or set aside is reported
     * @param writer runs the saves and moves, one at a time and in the order given; a single thread in production
     */
    public PaintStore(Path folder, Logger logger, Executor writer) {
        this.folder = folder;
        this.logger = logger;
        this.writer = writer;
    }

    /**
     * Reads each {@code <world>.dat} in the folder as that world's record. A file that cannot be read is moved to
     * {@code <world>.dat.bak}, replacing an older one, and its world gets a new record that is not done, so its fill
     * runs again. Nothing here stops the layer from starting. Call it once, at the start, before anything is saved.
     */
    public void load() {
        if (!Files.isDirectory(folder)) {
            return;
        }
        List<Path> files = new ArrayList<>();
        try (DirectoryStream<Path> found = Files.newDirectoryStream(folder, "*" + DAT)) {
            found.forEach(files::add);
        } catch (IOException | DirectoryIteratorException e) {
            logger.log(Level.WARNING, "Custom biomes layer: could not list the records in " + folder, e);
            return;
        }
        Collections.sort(files);
        for (Path file : files) {
            String name = file.getFileName().toString();
            String world = name.substring(0, name.length() - DAT.length());
            if (world.isEmpty() || !Files.isRegularFile(file)) {
                continue;
            }
            try {
                records.put(world, PaintRecord.load(file));
            } catch (IOException | RuntimeException e) {
                setAside(world, file, e);
            }
        }
    }

    private void setAside(String world, Path file, Exception cause) {
        Path bak = file.resolveSibling(file.getFileName() + ".bak");
        try {
            Files.move(file, bak, StandardCopyOption.REPLACE_EXISTING);
            logger.log(Level.WARNING, "Custom biomes layer: " + file + " could not be read, so it was set aside as "
                    + bak.getFileName() + "; " + world + " is read again.", cause);
        } catch (IOException e) {
            e.addSuppressed(cause);
            logger.log(Level.WARNING, "Custom biomes layer: " + file + " could not be read, nor set aside as "
                    + bak.getFileName() + "; " + world + " is read again.", e);
        }
        // saved at the next save, so the world stays on even if it restarts before its fill finds anything
        records.put(world, new PaintRecord());
        dirty.add(world);
    }

    /** The world's record, or null when the world is off. */
    public PaintRecord record(String world) {
        return records.get(world);
    }

    /** The record of each world that is on, sorted by world: a copy that cannot be changed. */
    public Map<String, PaintRecord> records() {
        return Collections.unmodifiableMap(new TreeMap<>(records));
    }

    /**
     * Puts the world on, to be filled from the start. A world that is on already keeps only the chunks
     * {@code loadedChunk} accepts, which the live checks keep current, and forgets how far its fill came; a new world
     * gets an empty record.
     *
     * @param loadedChunk whether a chunk, by {@link PaintRecord#chunkKey key}, is loaded
     */
    public PaintRecord turnOn(String world, LongPredicate loadedChunk) {
        PaintRecord record = records.get(world);
        if (record == null) {
            record = new PaintRecord();
            records.put(world, record);
        } else {
            record.clearExcept(loadedChunk);
            record.clearFill();
        }
        dirty.add(world);
        return record;
    }

    /**
     * Takes the world off: its record goes at once, and its file is moved to {@code <world>.dat.off}, replacing an
     * older one, after any save of it that is still waiting.
     *
     * @return where the file goes, or null when the world has no file and no save of it is waiting
     */
    public Path turnOff(String world) {
        records.remove(world);
        dirty.remove(world);
        Path file = fileOf(world);
        if (!saving.containsKey(world) && !Files.exists(file)) {
            return null;
        }
        Path off = file.resolveSibling(file.getFileName() + ".off");
        writer.execute(() -> {
            try {
                Files.move(file, off, StandardCopyOption.REPLACE_EXISTING);
            } catch (NoSuchFileException e) {
                // the save it waited for failed, and said so: there is nothing to set aside
            } catch (IOException | RuntimeException e) {
                logger.log(Level.WARNING, "Custom biomes layer: could not set " + file + " aside as "
                        + off.getFileName(), e);
            }
        });
        return off;
    }

    /** Marks the world's record as changed, so the next {@link #saveDirty} saves it. A world that is off is ignored. */
    public void dirty(String world) {
        if (records.containsKey(world)) {
            dirty.add(world);
        }
    }

    /** Hands the writer a copy of each record that changed since its last save. */
    public void saveDirty() {
        for (String world : dirty) {
            save(world, records.get(world));
        }
        dirty.clear();
    }

    /** Hands the writer a copy of every record. */
    public void saveAll() {
        records.forEach(this::save);
        dirty.clear();
    }

    private void save(String world, PaintRecord record) {
        PaintRecord copy = record.copy();
        Path file = fileOf(world);
        saving.merge(world, 1, Integer::sum);
        writer.execute(() -> {
            try {
                copy.save(file);
            } catch (IOException | RuntimeException e) {
                logger.log(Level.WARNING, "Custom biomes layer: could not save " + file, e);
            } finally {
                saving.computeIfPresent(world, (name, count) -> count > 1 ? count - 1 : null);
            }
        });
    }

    private Path fileOf(String world) {
        return folder.resolve(world + DAT);
    }
}
