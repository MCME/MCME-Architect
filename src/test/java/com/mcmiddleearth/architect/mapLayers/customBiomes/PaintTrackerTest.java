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
// unloads. The queue fits in about half a millisecond of each tick, and the reads at unload in an allowance of 2 ms.
// The fake reader counts the chunks it is asked about and moves a fake clock by what a read costs, so a tick's budget
// can be told in reads: at 0.2 ms a read, the third read reaches 0.5 ms, so a tick of the queue reads three chunks,
// and the allowance holds ten.
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

    /** Every chunk of the box unloads in the same tick: no tick passes between the events. */
    private void unloadAll(int minX, int minZ, int maxX, int maxZ) {
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                unloading(x, z);
            }
        }
    }

    private Logged capture() {
        Logged logged = new Logged();
        plugin.getLogger().setLevel(Level.ALL);
        plugin.getLogger().addHandler(logged);
        return logged;
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

    // The chunks of a selection are watched from the command on, and read as they unload, at once and outside the
    // queue's budget: four chunks unload here, and a tick of the queue reads three.
    @Test
    void aWatchedChunkIsReadAsItUnloads() {
        builder = false;
        load(0, 0, 1, 1);
        tracker.start();
        ticks(5);
        forgetReads();
        command("//setbiome cbc:a");

        unloadAll(0, 0, 1, 1);

        assertAllRead(0, 0, 1, 1, 1);
    }

    // A selection that is still watched watches its chunks every time they unload: an edit may load a chunk, change
    // it, let it go, and come back to it.
    @Test
    void aChunkOfAWatchedSelectionIsReadEachTimeItUnloads() {
        builder = false;
        load(0, 0, 1, 1);
        tracker.start();
        ticks(5);
        forgetReads();
        command("//setbiome cbc:a");

        unloading(0, 0);
        unloading(0, 0);

        assertEquals(2, reads(0, 0));
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

    // FAWE can take minutes over a big selection, loading its chunks long after the command and letting them go again.
    @Test
    void aSelectionOverUnloadedLandIsWatchedWhenItsChunksLoadAndGoWithinTenMinutes() {
        builder = false;
        selection = new ChunkBox("world", 40, 40, 43, 43); // nothing there is loaded
        tracker.start();
        ticks(5);
        command("//setbiome cbc:a");

        ticks(11_000);
        unloading(41, 42);

        assertEquals(1, reads(41, 42));
    }

    // A selection is a box, however big, so it costs one entry to watch it.
    @Test
    void aSelectionOfBillionsOfChunksIsWatchedWhereverItsChunksLoad() {
        builder = false;
        selection = new ChunkBox("world", -20_000, -20_000, 20_000, 20_000);
        world.getChunkAt(0, 0);
        tracker.start();
        ticks(5);
        forgetReads();
        command("//setbiome cbc:a");

        unloading(1, 1);
        unloading(-19_999, 20_000);
        unloading(30_000, 0);

        assertEquals(1, reads(1, 1), "not loaded when the command came");
        assertEquals(1, reads(-19_999, 20_000), "at the far corner");
        assertEquals(0, reads(30_000, 0), "outside the selection");
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
    // builder walks away and the chunks go. A chunk that has gone is not watched any more.
    @Test
    void aChunkNearABuilderIsReadAsItUnloads() {
        load(-5, -5, 5, 5);
        builderIn(0, 0);
        tracker.start();
        ticks(210);
        forgetReads();
        assertEquals(81, tracker.builderMarkCount(), "the 9 x 9 chunks within 4");

        unloading(4, -4);
        unloading(-4, 0);
        unloading(5, 5);
        unloading(5, 0);
        unloading(4, -4);

        assertEquals(1, reads(4, -4), "once: the second unload comes with no builder check in between");
        assertEquals(1, reads(-4, 0));
        assertEquals(0, reads(5, 5), "five chunks from the builder");
        assertEquals(0, reads(5, 0));
        assertEquals(79, tracker.builderMarkCount(), "the two chunks that went have left the watch");
    }

    // A builder who leaves is not followed for ever: the chunks watched at the last check stay watched for 12,000
    // ticks, 10 minutes, and no longer.
    @Test
    void aChunkNearABuilderIsWatchedForTenMinutes() {
        load(-5, -5, 5, 5);
        builderIn(0, 0);
        tracker.start();
        ticks(200); // the builder check marks the chunks within 4, until tick 12,200
        builder = false; // and no later check marks them again

        ticks(11_999); // tick 12,199
        forgetReads();
        unloading(4, 4);
        assertEquals(1, reads(4, 4), "one tick before the watch runs out");

        ticks(1); // tick 12,200
        forgetReads();
        unloading(-4, -4);
        assertEquals(0, reads(-4, -4), "12,000 ticks after the check");
    }

    @Test
    void thePositionsOfALeftBuilderAreDroppedWhenTheWatchHasRunOut() {
        load(-5, -5, 5, 5);
        builderIn(0, 0);
        tracker.start();
        ticks(200);
        builder = false;
        assertEquals(81, tracker.builderMarkCount());

        ticks(13_000); // tick 13,200: the housekeeping of the minute after the watch ran out at 12,200

        assertEquals(0, tracker.builderMarkCount());
    }

    // A selection is watched for 12,000 ticks from its command, 10 minutes, and no longer.
    @Test
    void aSelectionIsWatchedForTenMinutes() {
        builder = false;
        load(0, 0, 1, 1);
        tracker.start();
        ticks(5);
        command("//setbiome cbc:a"); // watched until tick 12,005

        ticks(11_999); // tick 12,004
        forgetReads();
        unloading(0, 0);
        assertEquals(1, reads(0, 0), "one tick before the watch runs out");

        ticks(1); // tick 12,005
        forgetReads();
        unloading(1, 1);
        assertEquals(0, reads(1, 1), "12,000 ticks after the command");
    }

    @Test
    void identicalSelectionsAreOneWatchAndTheLaterEndWins() {
        builder = false;
        world.getChunkAt(0, 0);
        tracker.start();
        ticks(5);
        command("//setbiome cbc:a"); // watched until tick 12,005
        ticks(6_000);
        command("//setbiome cbc:a"); // the same box: still one watch, now until tick 18,005
        assertEquals(1, tracker.watchedBoxCount());

        ticks(7_000); // tick 13,005, past the first end
        forgetReads();
        unloading(0, 0);

        assertEquals(1, reads(0, 0), "still watched, by the later end");
    }

    // 1,000 selections of 10,000 x 10,000 chunks each: a thousand entries, not billions of chunks, and none left once
    // they have run out and the minute's housekeeping has come.
    @Test
    void manyHugeSelectionsAreOneWatchEachAndAreDroppedWhenTheyHaveRunOut() {
        builder = false;
        tracker.start();
        ticks(5);
        for (int i = 0; i < 1_000; i++) {
            selection = new ChunkBox("world", i, i, i + 10_000, i + 10_000);
            command("//setbiome cbc:a");
        }
        assertEquals(1_000, tracker.watchedBoxCount(), "one entry for each, whatever its size");

        ticks(12_000); // tick 12,005: every watch has run out
        ticks(1_200); // and the housekeeping at tick 13,200 has dropped them

        assertEquals(0, tracker.watchedBoxCount());
    }

    // New selections keep coming, one every 20 ticks. A watch lasts 12,000 ticks and is dropped at the next full
    // minute, so at most 660 are ever held, not the 1,000 that came.
    @Test
    void watchesDoNotPileUpWhileSelectionsKeepComing() {
        builder = false;
        tracker.start();
        ticks(5);
        int most = 0;
        for (int i = 0; i < 1_000; i++) {
            selection = new ChunkBox("world", i, i, i + 10_000, i + 10_000);
            command("//setbiome cbc:a");
            most = Math.max(most, tracker.watchedBoxCount());
            ticks(20);
        }

        assertTrue(most <= 660, "most held at once: " + most);
        assertTrue(most >= 600, "they do add up until they run out: " + most);
    }

    // Unloads come in bursts: a builder who teleports lets go of dozens of watched chunks in one tick. Their reads get
    // an allowance of 2 ms a tick, ten reads of 0.2 ms here, and the other chunks unload unread.
    @Test
    void unloadReadsInOneTickStopAtTheirAllowance() {
        builder = false;
        selection = new ChunkBox("world", 0, 0, 4, 3);
        load(0, 0, 4, 3); // 20 chunks
        tracker.start();
        ticks(10);
        forgetReads();
        command("//setbiome cbc:a");

        unloadAll(0, 0, 4, 3);

        assertEquals(10, readLog.size(), "2 ms in reads of 0.2 ms");
        unloading(0, 0);
        assertEquals(10, readLog.size(), "and nothing more in the same tick");
    }

    @Test
    void theUnloadAllowanceIsBackAtTheNextTickAndIsNotTheQueuesBudget() {
        builder = false;
        selection = new ChunkBox("world", 0, 0, 4, 3);
        load(0, 0, 4, 3);
        tracker.start();
        ticks(10);
        forgetReads();
        command("//setbiome cbc:a");
        unloadAll(0, 0, 4, 3);
        assertEquals(10, readLog.size());
        forgetReads();

        tracker.readLoadedNow();
        assertEquals(3, readsInTheNextTick(), "the queue keeps its own budget: three reads of 0.2 ms");
        forgetReads();
        unloadAll(0, 0, 4, 3);

        assertEquals(10, readLog.size(), "the allowance began again with the tick, whatever the queue read");
    }

    @Test
    void skippedUnloadsAreCountedAndLoggedOnceAMinute() {
        builder = false;
        Logged logged = capture();
        selection = new ChunkBox("world", 0, 0, 4, 3);
        load(0, 0, 4, 3);
        tracker.start();
        ticks(10);
        command("//setbiome cbc:a");

        unloadAll(0, 0, 4, 3); // ten are read, ten unload unread
        unloading(9, 9); // not watched: not counted
        ticks(1);
        unloadAll(0, 0, 4, 3); // ten more
        ticks(1_100); // tick 1,111: the minute is not over
        assertEquals(List.of(), logged.messages(Level.INFO), "not at every skip, nor at every tick");

        ticks(100); // tick 1,211: the housekeeping of tick 1,200 has run
        List<String> told = logged.messages(Level.INFO);
        assertEquals(1, told.size(), told.toString());
        assertTrue(told.get(0).endsWith("Custom biomes layer: 20 chunks unloaded before they could be read; "
                + "/architect maplayers scan world catches them."), told.get(0));
        ticks(1_200); // nothing was skipped since, so there is nothing to say
        assertEquals(1, logged.messages(Level.INFO).size());
    }

    @Test
    void oneSkippedUnloadIsToldInTheSingular() {
        builder = false;
        Logged logged = capture();
        selection = new ChunkBox("world", 0, 0, 10, 0);
        load(0, 0, 10, 0); // 11 chunks, and the allowance holds ten reads
        tracker.start();
        ticks(10);
        command("//setbiome cbc:a");

        unloadAll(0, 0, 10, 0);
        ticks(1_200);

        List<String> told = logged.messages(Level.INFO);
        assertEquals(1, told.size(), told.toString());
        assertTrue(told.get(0).endsWith("Custom biomes layer: 1 chunk unloaded before it could be read; "
                + "/architect maplayers scan world catches it."), told.get(0));
    }

    // The allowance is shared by the worlds, and each world with skipped unloads is named in its own line.
    @Test
    void skippedUnloadsAreLoggedForEachWorld() {
        builder = false;
        Logged logged = capture();
        WorldMock other = server.addSimpleWorld("other");
        records.put("other", new PaintRecord());
        load(0, 0, 3, 2);
        for (int x = 0; x <= 3; x++) {
            for (int z = 0; z <= 2; z++) {
                other.getChunkAt(x, z);
            }
        }
        tracker.start();
        ticks(10);
        selection = new ChunkBox("world", 0, 0, 3, 2);
        command("//setbiome cbc:a");
        selection = new ChunkBox("other", 0, 0, 3, 2);
        command("//setbiome cbc:a");

        unloadAll(0, 0, 3, 2); // twelve chunks: ten are read, two unload unread
        for (int x = 0; x <= 3; x++) {
            for (int z = 0; z <= 2; z++) {
                server.getPluginManager().callEvent(new ChunkUnloadEvent(other.getChunkAt(x, z), true));
            }
        }
        ticks(1_200);

        List<String> told = logged.messages(Level.INFO);
        assertEquals(2, told.size(), told.toString());
        assertTrue(told.get(0).endsWith("Custom biomes layer: 12 chunks unloaded before they could be read; "
                + "/architect maplayers scan other catches them."), told.get(0));
        assertTrue(told.get(1).endsWith("Custom biomes layer: 2 chunks unloaded before they could be read; "
                + "/architect maplayers scan world catches them."), told.get(1));
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

    // A chunk handed over, such as one that was loaded while the first fill read its saved file, is read at the next
    // tick, ahead of the slow pass. One that unloads before its turn is read as it unloads, as a builder's chunk is.
    @Test
    void aChunkHandedOverIsReadAtOnceOrAsItUnloads() {
        builder = false;
        tracker.start();
        ticks(1); // the first slow pass, over no chunks: the next is two minutes away
        load(0, 0, 1, 1);
        paints.put(new Spot("world", 0, 0), paint("cbc:a"));
        paints.put(new Spot("world", 1, 1), paint("cbc:b"));

        tracker.readSoon("world", 0, 0);
        tracker.readSoon("world", 0, 0);
        ticks(1);
        assertEquals(1, reads(0, 0), "read at the next tick, once");
        assertEquals(paint("cbc:a"), records.get("world").get(0, 0));

        tracker.readSoon("world", 1, 1);
        unloading(1, 1); // before its turn
        world.unloadChunk(1, 1);
        ticks(1);

        assertEquals(1, reads(1, 1), "read once, as it unloaded");
        assertEquals(paint("cbc:b"), records.get("world").get(1, 1));
        assertEquals(2, readLog.size(), "and no other chunk: " + readLog);
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
        Logged logged = capture();
        unreadable.add(new Spot("world", 0, 0));
        unreadable.add(new Spot("world", 1, 1));
        load(-1, -1, 1, 1);
        tracker.start();

        ticks(10);

        assertEquals(1, reads(0, 0), "tried once");
        assertEquals(1, reads(-1, -1));
        assertEquals(1, reads(1, 0));
        assertEquals(1, logged.messages(Level.WARNING).size(), "one warning for the two failures");
    }

    @Test
    void aStoppedTrackerReadsNothingAndHearsNothing() {
        builder = false;
        load(0, 0, 1, 1);
        tracker.start();
        ticks(5);
        command("//setbiome cbc:a");
        tracker.readLoadedNow();
        assertEquals(1, tracker.watchedBoxCount());
        tracker.stop();
        forgetReads();
        assertEquals(0, tracker.watchedBoxCount(), "the watches are dropped");

        command("//setbiome cbc:a");
        unloading(0, 0);
        ticks(3_000);

        assertTrue(readLog.isEmpty());
        assertEquals(0, tracker.watchedBoxCount());
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

    /** What the plugin's logger was asked to log. */
    private static final class Logged extends Handler {
        private final List<LogRecord> seen = new ArrayList<>();

        @Override
        public void publish(LogRecord record) {
            seen.add(record);
        }

        @Override
        public void flush() {
        }

        @Override
        public void close() {
        }

        /** The messages logged at exactly that level, with the plugin's name in front, as the server shows them. */
        List<String> messages(Level level) {
            return seen.stream().filter(record -> record.getLevel().equals(level)).map(LogRecord::getMessage).toList();
        }
    }
}
