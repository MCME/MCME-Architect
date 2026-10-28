package com.mcmiddleearth.architect.mapLayers.customBiomes;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;

// Each world on the layer has a record, kept in <world>.dat. It must survive a restart; a file that cannot be read
// must not stop the layer, and its world is read again; a world switched off keeps its file, set aside. Files are
// written by the writer, one after another, from copies taken on the main thread. Here the writer runs each write at
// once, except where a test holds the writes back to run them by hand.
class PaintStoreTest {

    private static final ChunkPaint FOREST = paint("cbc:forest");

    @TempDir
    Path dir;

    private final List<LogRecord> logged = new ArrayList<>();
    private final Logger logger = Logger.getAnonymousLogger();
    private PaintStore store;

    @BeforeEach
    void setUp() {
        logger.setUseParentHandlers(false);
        logger.addHandler(new Handler() {
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
        store = new PaintStore(dir, logger, Runnable::run);
    }

    private static ChunkPaint paint(String biome) {
        return ChunkPaint.of(Collections.nCopies(16, List.of(new BiomeSpan(biome, -64, 319))));
    }

    private PaintStore restarted() {
        PaintStore restarted = new PaintStore(dir, logger, Runnable::run);
        restarted.load();
        return restarted;
    }

    private List<String> warnings() {
        return logged.stream().filter(record -> record.getLevel() == Level.WARNING).map(LogRecord::getMessage)
                .toList();
    }

    @Test
    void recordsComeBackAfterARestart() {
        PaintRecord record = store.turnOn("world", key -> false);
        record.put(3, -4, FOREST);
        record.finished("r.0.-1.mca");
        store.turnOn("another", key -> false);
        store.saveAll();

        PaintStore restarted = restarted();

        assertEquals(Map.of(PaintRecord.chunkKey(3, -4), FOREST), restarted.record("world").chunks());
        assertEquals(Set.of("r.0.-1.mca"), restarted.record("world").finishedFiles());
        assertFalse(restarted.record("world").fillDone());
        assertEquals(List.of("another", "world"), List.copyOf(restarted.records().keySet()), "sorted by world");
        assertThrows(UnsupportedOperationException.class, () -> restarted.records().remove("world"));
        assertNull(restarted.record("nowhere"), "a world that is off has no record");
        assertEquals(List.of(), warnings());
    }

    @Test
    void anUnreadableRecordIsSetAsideAndItsWorldFilledAgain() throws IOException {
        Files.writeString(dir.resolve("world.dat"), "not a record");
        Files.writeString(dir.resolve("world.dat.bak"), "an older one");

        store.load();

        assertTrue(Files.exists(dir.resolve("world.dat.bak")));
        assertEquals("not a record", Files.readString(dir.resolve("world.dat.bak")), "the older one is replaced");
        assertNotNull(store.record("world"));
        assertFalse(store.record("world").fillDone());
        assertTrue(store.record("world").chunks().isEmpty());
        assertEquals(List.of("Custom biomes layer: " + dir.resolve("world.dat") + " could not be read, so it was set "
                + "aside as world.dat.bak; world is read again."), warnings());
        // and it stays on: the new record is saved, so a restart before the fill gets anywhere still has the world
        store.saveDirty();
        assertNotNull(restarted().record("world"));
        assertFalse(restarted().record("world").fillDone());
    }

    @Test
    void turningAWorldOffSetsItsRecordAside() throws IOException {
        store.turnOn("world", key -> false).put(0, 0, FOREST);
        store.saveAll();

        Path off = store.turnOff("world");

        assertEquals(dir.resolve("world.dat.off"), off);
        assertEquals(Map.of(PaintRecord.chunkKey(0, 0), FOREST), PaintRecord.load(off).chunks());
        assertFalse(Files.exists(dir.resolve("world.dat")));
        assertNull(store.record("world"));
        assertNull(restarted().record("world"), "it stays off after a restart");
        assertNull(store.turnOff("world"), "no file is left to set aside");
        // on and off again: the newer record replaces the one set aside before
        store.turnOn("world", key -> false).put(5, 5, FOREST);
        store.saveDirty();
        assertEquals(off, store.turnOff("world"));
        assertEquals(Map.of(PaintRecord.chunkKey(5, 5), FOREST), PaintRecord.load(off).chunks());
    }

    @Test
    void aReRunKeepsOnlyLoadedChunks() {
        PaintRecord record = store.turnOn("world", key -> false);
        record.put(0, 0, FOREST);
        record.put(1, 0, FOREST);
        record.fillDone(true);
        record.finished("r.0.0.mca");

        PaintRecord again = store.turnOn("world", key -> key == PaintRecord.chunkKey(0, 0));

        assertSame(record, again);
        assertEquals(Set.of(PaintRecord.chunkKey(0, 0)), again.chunks().keySet());
        assertFalse(again.fillDone(), "read again from the start");
        assertTrue(again.finishedFiles().isEmpty());
    }

    @Test
    void onlyDirtyRecordsAreSaved() throws IOException {
        store.turnOn("a", key -> false);
        store.turnOn("b", key -> false);
        store.saveAll();
        FileTime old = FileTime.fromMillis(946_684_800_000L); // 2000-01-01
        Files.setLastModifiedTime(dir.resolve("a.dat"), old);
        Files.setLastModifiedTime(dir.resolve("b.dat"), old);

        store.saveDirty();
        assertEquals(old, Files.getLastModifiedTime(dir.resolve("a.dat")), "saveAll left nothing dirty");
        store.record("a").put(0, 0, FOREST);
        store.dirty("a");
        store.saveDirty();

        assertNotEquals(old, Files.getLastModifiedTime(dir.resolve("a.dat")));
        assertEquals(old, Files.getLastModifiedTime(dir.resolve("b.dat")));
        assertEquals(Map.of(PaintRecord.chunkKey(0, 0), FOREST), PaintRecord.load(dir.resolve("a.dat")).chunks());
        Files.setLastModifiedTime(dir.resolve("a.dat"), old);
        store.saveDirty();
        assertEquals(old, Files.getLastModifiedTime(dir.resolve("a.dat")), "a save leaves the record clean");
    }

    // The writer saves a copy taken when the save was asked for: the record goes on changing meanwhile.
    @Test
    void aSaveWritesTheRecordAsItWasWhenItWasAskedFor() throws IOException {
        Deque<Runnable> writes = new ArrayDeque<>();
        PaintStore held = new PaintStore(dir, logger, writes::add);
        PaintRecord record = held.turnOn("world", key -> false);
        record.put(0, 0, FOREST);

        held.saveDirty();
        record.put(1, 1, FOREST);
        record.fillDone(true);
        assertFalse(Files.exists(dir.resolve("world.dat")), "the writer has not run yet");
        runAll(writes);

        PaintRecord saved = PaintRecord.load(dir.resolve("world.dat"));
        assertEquals(Set.of(PaintRecord.chunkKey(0, 0)), saved.chunks().keySet());
        assertFalse(saved.fillDone());
    }

    // A world switched off right after a save was asked for: the save waits in the writer, and the move to .off is
    // queued behind it, so the save cannot bring <world>.dat back after the move.
    @Test
    void aWorldTurnedOffWhileItsSaveWaitsLeavesNoRecordBehind() throws IOException {
        Deque<Runnable> writes = new ArrayDeque<>();
        PaintStore held = new PaintStore(dir, logger, writes::add);
        held.turnOn("world", key -> false).put(0, 0, FOREST);
        held.saveDirty();

        Path off = held.turnOff("world");

        assertEquals(dir.resolve("world.dat.off"), off, "the save that waits counts as a file");
        assertNull(held.record("world"), "dropped at once");
        runAll(writes);
        assertFalse(Files.exists(dir.resolve("world.dat")));
        assertEquals(Map.of(PaintRecord.chunkKey(0, 0), FOREST), PaintRecord.load(off).chunks());
        assertEquals(List.of(), warnings());
    }

    @Test
    void aSaveThatFailsIsLoggedWithItsFile() throws IOException {
        Files.createDirectories(dir.resolve("world.dat").resolve("inside")); // a folder where the file belongs
        store.turnOn("world", key -> false).put(0, 0, FOREST);

        store.saveDirty();

        assertEquals(List.of("Custom biomes layer: could not save " + dir.resolve("world.dat")), warnings());
        assertFalse(Files.exists(dir.resolve("world.dat.tmp")));
    }

    private static void runAll(Deque<Runnable> writes) {
        while (!writes.isEmpty()) {
            writes.poll().run();
        }
    }
}
