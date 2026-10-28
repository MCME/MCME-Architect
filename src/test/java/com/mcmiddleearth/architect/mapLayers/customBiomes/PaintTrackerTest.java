package com.mcmiddleearth.architect.mapLayers.customBiomes;

import com.mcmiddleearth.architect.mapLayers.customBiomes.SelectionSource.ChunkBox;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginIdentifiableCommand;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockbukkit.mockbukkit.world.WorldMock;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Predicate;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;

import static org.junit.jupiter.api.Assertions.*;

// The tracker keeps a world's record current from the chunks that are loaded: the chunks around builders every 10 s, a
// WorldEdit selection 2 s and 30 s after a command, every loaded chunk once every 2 minutes, and a watched chunk as it
// unloads. All of it fits in about half a millisecond of each tick. The fake reader counts the chunks it is asked about
// and moves a fake clock by what a read costs, so a tick's budget can be told in reads: at 0.2 ms a read, the third
// read reaches 0.5 ms, so a tick reads three chunks.
class PaintTrackerTest {

    /** A chunk of a world, as the fake reader is asked about it. */
    private record Spot(String world, int x, int z) {}

    private ServerMock server;
    private Plugin plugin;
    private WorldMock world;
    private PlayerMock player;
    private PaintTracker tracker;

    private final Map<String, PaintRecord> records = new HashMap<>(Map.of("world", new PaintRecord()));
    private final Map<Spot, ChunkPaint> paints = new HashMap<>();
    private final Set<Spot> unreadable = new HashSet<>();
    /** Every chunk the reader was asked about, in order, and how often. */
    private final List<Spot> readLog = new ArrayList<>();
    private final Map<Spot, Integer> readCounts = new HashMap<>();
    private final List<String> changes = new ArrayList<>();
    private final AtomicLong nanos = new AtomicLong();
    private long readCost = 200_000;
    private boolean builder = true;
    private ChunkBox selection = new ChunkBox("world", 0, 0, 1, 1);
    private int selectionsAsked;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin();
        world = server.addSimpleWorld("world");
        player = server.addPlayer();
        tracker = new PaintTracker(plugin, records::get, this::readChunk, this::selectionOf,
                message -> message.startsWith("//"), who -> builder, changes::add, nanos::get);
    }

    @AfterEach
    void tearDown() {
        tracker.stop();
        MockBukkit.unmock();
    }

    private ChunkPaint readChunk(World readWorld, int x, int z) {
        Spot spot = new Spot(readWorld.getName(), x, z);
        readLog.add(spot);
        readCounts.merge(spot, 1, Integer::sum);
        nanos.addAndGet(readCost);
        if (unreadable.contains(spot)) {
            throw new IllegalStateException("cannot read " + spot);
        }
        return paints.getOrDefault(spot, ChunkPaint.EMPTY);
    }

    private Optional<ChunkBox> selectionOf(Player who) {
        selectionsAsked++;
        return Optional.ofNullable(selection);
    }

    private static ChunkPaint paint(String biome) {
        return ChunkPaint.of(Collections.nCopies(16, List.of(new BiomeSpan(biome, -64, 319))));
    }

    private void load(int minX, int minZ, int maxX, int maxZ) {
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                world.getChunkAt(x, z);
            }
        }
    }

    private void builderIn(int chunkX, int chunkZ) {
        player.teleport(new Location(world, chunkX * 16 + 8, 64, chunkZ * 16 + 8));
    }

    private void ticks(int count) {
        server.getScheduler().performTicks(count);
    }

    private int reads(int x, int z) {
        return readCounts.getOrDefault(new Spot("world", x, z), 0);
    }

    private void assertAllRead(int minX, int minZ, int maxX, int maxZ, int times) {
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                assertEquals(times, reads(x, z), "reads of chunk " + x + ", " + z);
            }
        }
    }

    private void assertAllReadAtLeast(int minX, int minZ, int maxX, int maxZ, int times) {
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                assertTrue(reads(x, z) >= times, "reads of chunk " + x + ", " + z + ": " + reads(x, z));
            }
        }
    }

    private int chunksRead(int minX, int minZ, int maxX, int maxZ) {
        int read = 0;
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                if (reads(x, z) > 0) {
                    read++;
                }
            }
        }
        return read;
    }

    private int readsInTheNextTick() {
        int before = readLog.size();
        ticks(1);
        return readLog.size() - before;
    }

    private void forgetReads() {
        readLog.clear();
        readCounts.clear();
    }

    private void command(String message) {
        server.getPluginManager().callEvent(new PlayerCommandPreprocessEvent(player, message));
    }

    /** The unload event of a chunk, fired while the chunk is still loaded, as Paper does. */
    private void unloading(int x, int z) {
        server.getPluginManager().callEvent(new ChunkUnloadEvent(world.getChunkAt(x, z), true));
    }

    // 25 chunks around the builder: the first pass reads each once, three a tick. The builder check at tick 200 reads
    // them again, so paint that appears afterwards is in the record by tick 209, a long way before the next pass.
    @Test
    void aChunkNearABuilderIsReadAgainWithinTenSeconds() {
        load(-2, -2, 2, 2);
        builderIn(0, 0);
        tracker.start();
        ticks(10);
        assertAllRead(-2, -2, 2, 2, 1);
        paints.put(new Spot("world", 1, 1), paint("cbc:a"));

        ticks(189); // tick 199
        assertEquals(1, reads(1, 1), "not before the builder check at tick 200");
        assertTrue(changes.isEmpty());
        ticks(11); // tick 210
        assertEquals(2, reads(1, 1), "read again by the builder check, not by a pass: the next is at tick 2,401");
        assertEquals(paint("cbc:a"), records.get("world").get(1, 1));
        assertEquals(List.of("world"), changes);
    }

    @Test
    void playersWithoutBiomeTuningAreNotFollowed() {
        builder = false;
        load(-2, -2, 2, 2);
        builderIn(0, 0);
        tracker.start();
        ticks(10);
        paints.put(new Spot("world", 1, 1), paint("cbc:a"));

        ticks(2389); // tick 2,399
        assertEquals(1, reads(1, 1));
        assertTrue(changes.isEmpty());
        ticks(21); // tick 2,420, with the next slow pass done
        assertEquals(2, reads(1, 1), "only the slow pass finds it");
        assertEquals(List.of("world"), changes);
    }

    @Test
    void onlyChunksWithinTwoOfABuilderAreFollowed() {
        load(-3, -3, 3, 3);
        builderIn(0, 0);
        tracker.start();
        ticks(20);
        assertAllRead(-3, -3, 3, 3, 1);
        paints.put(new Spot("world", 2, 2), paint("cbc:a"));
        paints.put(new Spot("world", 3, 3), paint("cbc:b"));

        ticks(190); // tick 210
        assertEquals(2, reads(2, 2), "two chunks from the builder");
        assertEquals(1, reads(3, 3), "three chunks from the builder");
        assertEquals(paint("cbc:a"), records.get("world").get(2, 2));
        assertTrue(records.get("world").get(3, 3).isEmpty());
    }

    @Test
    void aNewSlowPassStartsAfterTwoMinutes() {
        builder = false;
        load(-2, -2, 2, 2);
        tracker.start();

        ticks(100);
        assertAllRead(-2, -2, 2, 2, 1);
        ticks(2299); // tick 2,399
        assertAllRead(-2, -2, 2, 2, 1);
        ticks(21); // tick 2,420
        assertAllRead(-2, -2, 2, 2, 2);
    }

    // A pass over more chunks than fit in two minutes is not started over: the next pass begins when it ends.
    @Test
    void aPassThatTakesLongerThanTwoMinutesIsNotCutShort() {
        builder = false;
        load(0, 0, 99, 99); // 10,000 chunks take 3,334 ticks
        tracker.start();

        ticks(3_300);
        assertTrue(chunksRead(0, 0, 99, 99) < 10_000, "still reading");
        ticks(100);

        assertEquals(10_000, chunksRead(0, 0, 99, 99), "every chunk was reached");
        assertTrue(readCounts.values().stream().allMatch(times -> times <= 2), "the next pass began only at the end");
    }

    @Test
    void aWorldEditCommandReadsTheSelectionTwice() {
        builder = false;
        load(0, 0, 1, 1);
        tracker.start();
        ticks(5); // the first pass
        forgetReads();

        command("//setbiome cbc:a");

        ticks(41);
        assertAllRead(0, 0, 1, 1, 1);
        ticks(560);
        assertAllRead(0, 0, 1, 1, 2);
    }

    @Test
    void anotherPluginsCommandReadsNothing() {
        builder = false;
        load(0, 0, 1, 1);
        tracker.start();
        ticks(5);
        forgetReads();

        command("/tp 0 0 0");

        ticks(700);
        assertTrue(readLog.isEmpty());
        assertEquals(0, selectionsAsked, "WorldEdit is only asked about the selection after its own commands");
    }

    @Test
    void aWorldEditCommandWithoutASelectionReadsNothing() {
        builder = false;
        load(0, 0, 1, 1);
        selection = null;
        tracker.start();
        ticks(5);
        forgetReads();

        command("//setbiome cbc:a");

        ticks(700);
        assertTrue(readLog.isEmpty());
    }

    @Test
    void aCancelledCommandReadsNothing() {
        builder = false;
        load(0, 0, 1, 1);
        tracker.start();
        ticks(5);
        forgetReads();

        PlayerCommandPreprocessEvent denied = new PlayerCommandPreprocessEvent(player, "//setbiome cbc:a");
        denied.setCancelled(true);
        server.getPluginManager().callEvent(denied);

        ticks(700);
        assertTrue(readLog.isEmpty(), "the command did not run");
        assertEquals(0, selectionsAsked);
    }

    // A chunk is watched from a command until it unloads, and is read then, at once and outside the tick's budget:
    // four chunks unload here, and a tick reads three.
    @Test
    void aWatchedChunkIsReadAsItUnloads() {
        builder = false;
        load(0, 0, 1, 1);
        tracker.start();
        ticks(5);
        forgetReads();
        command("//setbiome cbc:a");

        unloading(0, 0);
        unloading(1, 0);
        unloading(0, 1);
        unloading(1, 1);

        assertAllRead(0, 0, 1, 1, 1);
        unloading(0, 0);
        assertEquals(1, reads(0, 0), "a chunk leaves the watched set when it is read");
    }

    @Test
    void aSelectedChunkThatLoadsAfterTheCommandIsReadAsItUnloads() {
        builder = false;
        world.getChunkAt(0, 0);
        tracker.start();
        ticks(5);
        forgetReads();
        command("//setbiome cbc:a");

        unloading(1, 1); // WorldEdit loads it while it edits, and it goes again at once

        assertEquals(1, reads(1, 1));
    }

    // Watching every chunk of a selection of millions would cost more than the reading it saves.
    @Test
    void aSelectionTooBigToWatchInFullIsWatchedWhereItIsLoaded() {
        builder = false;
        selection = new ChunkBox("world", 0, 0, 199, 199);
        world.getChunkAt(0, 0);
        tracker.start();
        ticks(5);
        forgetReads();
        command("//setbiome cbc:a");

        unloading(1, 1);
        assertEquals(0, reads(1, 1), "not loaded when the command came");
        unloading(0, 0);
        assertEquals(1, reads(0, 0));
    }

    @Test
    void anUnwatchedChunkIsNotReadAsItUnloads() {
        builder = false;
        load(0, 0, 1, 1);
        tracker.start();
        ticks(5);
        forgetReads();
        unloading(0, 0);
        assertEquals(0, reads(0, 0), "nothing is watched yet");
        command("//setbiome cbc:a");

        unloading(9, 9);
        unloading(2, 2);

        assertTrue(readLog.isEmpty());
    }

    @Test
    void aWatchedChunkOfAWorldSwitchedOffIsNotRead() {
        builder = false;
        load(0, 0, 1, 1);
        tracker.start();
        ticks(5);
        forgetReads();
        command("//setbiome cbc:a");
        records.remove("world");

        unloading(0, 0);

        assertTrue(readLog.isEmpty());
    }

    // The builder check watches the chunks within 4 of a builder, so that the last paint reaches the record when the
    // builder walks away and the chunks go.
    @Test
    void aChunkNearABuilderIsReadAsItUnloads() {
        load(-5, -5, 5, 5);
        builderIn(0, 0);
        tracker.start();
        ticks(210);
        forgetReads();

        unloading(4, -4);
        unloading(-4, 0);
        unloading(5, 5);
        unloading(5, 0);

        assertEquals(1, reads(4, -4));
        assertEquals(1, reads(-4, 0));
        assertEquals(0, reads(5, 5), "five chunks from the builder");
        assertEquals(0, reads(5, 0));
    }

    @Test
    void theSlowPassReadsEveryLoadedChunkInTime() {
        builder = false;
        load(0, 0, 9, 9); // 100 chunks, three a tick
        tracker.start();

        ticks(50);

        assertAllRead(0, 0, 9, 9, 1);
        assertEquals(world.getLoadedChunks().length, readLog.size(), "each loaded chunk once, and no other");
    }

    @Test
    void oneTickSpendsAboutHalfAMillisecond() {
        builder = false;
        readCost = 300_000;
        load(0, 0, 9, 9);
        tracker.start();

        for (int tick = 0; tick < 5; tick++) {
            assertEquals(2, readsInTheNextTick(), "the second read reaches 0.6 ms, past the 0.5 ms budget");
        }
    }

    @Test
    void aReadSlowerThanTheBudgetStillGetsOneChunkATick() {
        builder = false;
        readCost = 800_000;
        load(0, 0, 9, 9);
        tracker.start();

        for (int tick = 0; tick < 5; tick++) {
            assertEquals(1, readsInTheNextTick());
        }
    }

    @Test
    void aTickWithNothingQueuedReadsNothing() {
        builder = false;
        load(0, 0, 1, 1);
        tracker.start();
        ticks(5);
        forgetReads();

        for (int tick = 0; tick < 100; tick++) {
            assertEquals(0, readsInTheNextTick());
        }
    }

    // 10,000 loaded chunks: a selection of 1,600 x 1,600 blocks, which takes 3,334 ticks to read.
    @Test
    void aHugeSelectionIsReadOverManyTicks() {
        builder = false;
        load(-50, -50, 49, 49);
        selection = new ChunkBox("world", -50, -50, 49, 49);
        tracker.start();
        command("//setbiome cbc:a");

        int mostInATick = 0;
        for (int tick = 1; tick <= 3_500; tick++) {
            mostInATick = Math.max(mostInATick, readsInTheNextTick());
        }

        assertEquals(3, mostInATick, "never more than three reads a tick, and three while there is a lot to read");
        assertAllReadAtLeast(-50, -50, 49, 49, 1);
    }

    // The slow pass has 400 chunks to read here, 134 ticks of work, when the selection falls due at tick 40.
    @Test
    void aSelectionIsReadBeforeTheSlowPass() {
        builder = false;
        load(-10, -10, 9, 9);
        tracker.start();
        command("//setbiome cbc:a");
        ticks(39);
        int before = readLog.size();

        ticks(2);

        List<Spot> reading = readLog.subList(before, readLog.size());
        assertEquals(6, reading.size());
        assertTrue(reading.subList(0, 4).stream().allMatch(spot -> spot.x() >= 0 && spot.x() <= 1
                && spot.z() >= 0 && spot.z() <= 1), "the selection's four chunks first: " + reading);
    }

    // Only the loaded chunks of a selection are read, and they are found from what is loaded, not by asking about
    // each of the 1.6 billion chunks of the box.
    @Test
    void aSelectionOfBillionsOfChunksIsReadWhereItIsLoaded() {
        builder = false;
        selection = new ChunkBox("world", -20_000, -20_000, 20_000, 20_000);
        world.getChunkAt(0, 0);
        world.getChunkAt(5, -3);
        world.getChunkAt(30_000, 0);
        tracker.start();
        ticks(5);
        forgetReads();

        command("//setbiome cbc:a");
        assertTimeout(Duration.ofSeconds(10), () -> ticks(41));

        assertEquals(1, reads(0, 0));
        assertEquals(1, reads(5, -3));
        assertEquals(0, reads(30_000, 0), "outside the selection");
    }

    @Test
    void worldsThatAreOffAreNotRead() {
        records.remove("world");
        WorldMock other = server.addSimpleWorld("other");
        records.put("other", new PaintRecord());
        other.getChunkAt(0, 0);
        load(0, 0, 3, 3);
        builderIn(0, 0);
        tracker.start();

        command("//setbiome cbc:a");
        tracker.readLoadedNow();
        ticks(700);
        unloading(0, 0);

        assertFalse(readLog.isEmpty(), "the world that is on is read");
        assertTrue(readLog.stream().allMatch(spot -> spot.world().equals("other")), "and only it: " + readLog);
        assertTrue(changes.isEmpty());
    }

    // The queue holds chunks that were loaded when it was filled. One that unloads before its turn must not be read:
    // the game would answer with the generator's biomes, and the record would get a wrong entry.
    @Test
    void aChunkThatUnloadsBeforeItsTurnIsNotRead() {
        builder = false;
        load(0, 0, 3, 3);
        tracker.start();
        tracker.readLoadedNow();

        world.unloadChunk(2, 2);
        ticks(20);

        assertEquals(0, reads(2, 2));
        assertEquals(15, chunksRead(0, 0, 3, 3), "the other chunks of the 4 x 4 are read");
    }

    @Test
    void aRefreshReadsEveryLoadedChunkAgain() {
        builder = false;
        load(-1, -1, 1, 1);
        tracker.start();
        ticks(5);
        assertAllRead(-1, -1, 1, 1, 1);
        paints.put(new Spot("world", 1, 0), paint("cbc:a"));

        tracker.readLoadedNow();
        ticks(5);

        assertAllRead(-1, -1, 1, 1, 2);
        assertEquals(paint("cbc:a"), records.get("world").get(1, 0));
        assertEquals(List.of("world"), changes);
    }

    @Test
    void aReadReportsItsWorldOnlyWhenTheRecordChanged() {
        builder = false;
        load(0, 0, 1, 0);
        paints.put(new Spot("world", 0, 0), paint("cbc:a"));
        tracker.start();
        ticks(5);
        assertEquals(List.of("world"), changes, "the painted chunk changed the record, the empty one did not");
        assertEquals(paint("cbc:a"), records.get("world").get(0, 0));

        tracker.readLoadedNow();
        ticks(5);
        assertEquals(1, changes.size(), "the same paint again is no change");

        paints.put(new Spot("world", 0, 0), ChunkPaint.EMPTY);
        tracker.readLoadedNow();
        ticks(5);
        assertEquals(List.of("world", "world"), changes, "the paint is gone");
        assertTrue(records.get("world").get(0, 0).isEmpty());
    }

    @Test
    void aReaderThatFailsIsLoggedOnceAndTheOtherChunksAreStillRead() {
        builder = false;
        Warnings warnings = new Warnings();
        plugin.getLogger().addHandler(warnings);
        unreadable.add(new Spot("world", 0, 0));
        unreadable.add(new Spot("world", 1, 1));
        load(-1, -1, 1, 1);
        tracker.start();

        ticks(10);

        assertEquals(1, reads(0, 0), "tried once");
        assertEquals(1, reads(-1, -1));
        assertEquals(1, reads(1, 0));
        assertEquals(1, warnings.seen.size(), "one warning for the two failures");
    }

    @Test
    void aStoppedTrackerReadsNothingAndHearsNothing() {
        builder = false;
        load(0, 0, 1, 1);
        tracker.start();
        ticks(5);
        command("//setbiome cbc:a");
        tracker.readLoadedNow();
        tracker.stop();
        forgetReads();

        command("//setbiome cbc:a");
        unloading(0, 0);
        ticks(3_000);

        assertTrue(readLog.isEmpty());
    }

    @Test
    void aTrackerCanBeStartedAgain() {
        builder = false;
        load(0, 0, 1, 1);
        tracker.start();
        ticks(5);
        tracker.stop();
        forgetReads();

        tracker.start();
        ticks(5);

        assertAllRead(0, 0, 1, 1, 1);
    }

    // The label is what follows the first slash and ends at the first space. WorldEdit's own commands are registered
    // with a slash in their name, so what a player types as //setbiome is the label /setbiome.
    @Test
    void worldEditCommandsAreFoundInTheCommandMap() {
        Plugin fawe = MockBukkit.createMockPlugin("FastAsyncWorldEdit");
        server.getCommandMap().register("fastasyncworldedit", new PluginOwnedCommand("/setbiome", fawe));

        Predicate<String> isWorldEditCommand = PaintTracker.worldEditCommands(server);

        assertTrue(isWorldEditCommand.test("//setbiome cbc:a"));
        assertFalse(isWorldEditCommand.test("/tp 0 0 0"));
    }

    @Test
    void theCommandsOfPlainWorldEditAreFoundToo() {
        Plugin worldEdit = MockBukkit.createMockPlugin("WorldEdit");
        server.getCommandMap().register("worldedit", new PluginOwnedCommand("/pos1", worldEdit));
        server.getCommandMap().register("worldedit", new PluginOwnedCommand("worldedit", worldEdit));

        Predicate<String> isWorldEditCommand = PaintTracker.worldEditCommands(server);

        assertTrue(isWorldEditCommand.test("//pos1"), "a command without arguments");
        assertTrue(isWorldEditCommand.test("//POS1"), "the command map ignores case");
        assertTrue(isWorldEditCommand.test("/worldedit reload"), "the plugin's own name for itself");
    }

    @Test
    void aCommandOfAnotherPluginIsNotWorldEdits() {
        Plugin essentials = MockBukkit.createMockPlugin("Essentials");
        server.getCommandMap().register("essentials", new PluginOwnedCommand("home", essentials));

        Predicate<String> isWorldEditCommand = PaintTracker.worldEditCommands(server);

        assertFalse(isWorldEditCommand.test("/home bed"));
        assertFalse(isWorldEditCommand.test("/nosuchcommand"));
        assertFalse(isWorldEditCommand.test("/"));
        assertFalse(isWorldEditCommand.test(""));
    }

    /** A command that belongs to a plugin, as WorldEdit's are. */
    private static final class PluginOwnedCommand extends Command implements PluginIdentifiableCommand {
        private final Plugin owner;

        PluginOwnedCommand(String name, Plugin owner) {
            super(name);
            this.owner = owner;
        }

        @Override
        public boolean execute(CommandSender sender, String label, String[] args) {
            return true;
        }

        @Override
        public Plugin getPlugin() {
            return owner;
        }
    }

    private static final class Warnings extends Handler {
        private final List<LogRecord> seen = new ArrayList<>();

        @Override
        public void publish(LogRecord record) {
            if (record.getLevel().intValue() >= Level.WARNING.intValue()) {
                seen.add(record);
            }
        }

        @Override
        public void flush() {
        }

        @Override
        public void close() {
        }
    }
}
