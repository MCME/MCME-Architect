package com.mcmiddleearth.architect.entityLogging;

import com.mcmiddleearth.architect.ArchitectPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Pig;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.event.world.EntitiesUnloadEvent;
import org.bukkit.inventory.ItemStack;
import org.dynmap.markers.MarkerSet;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.command.ConsoleCommandSenderMock;
import org.mockbukkit.mockbukkit.world.WorldMock;

import java.io.File;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

// One mock/load per class (Architect caches getDataFolder()-derived paths in static fields at
// class-load); surefire reuseForks=false gives a fresh JVM per class. Mirrors LoadGuardTest.
class EntityLoggerTest {

    // The logger's first dump comes this many ticks after logging is switched on, then one
    // every DUMP_PERIOD_TICKS.
    private static final long FIRST_DUMP_TICKS = 500;
    private static final long DUMP_PERIOD_TICKS = 2000;
    private static final String HEADER = "world;x;z;ArmorStand;Boat;Arrow;Item;Painting;ItemFrame;all";

    private static ServerMock server;
    private static ArchitectPlugin plugin;
    private static FakeDynmap dynmap;
    private static WorldMock worldA;
    private static WorldMock worldB;

    @BeforeAll
    static void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.load(ArchitectPlugin.class);
        dynmap = MockBukkit.loadWith(FakeDynmap.class, FakeDynmap.description());
        worldA = server.addSimpleWorld("world_a");
        worldB = server.addSimpleWorld("world_b");
    }

    @AfterAll
    static void tearDown() { if (MockBukkit.isMocked()) MockBukkit.unmock(); }

    @BeforeEach
    void deleteOldLog() { logFile().delete(); }

    @AfterEach
    void stopLogging() { EntityLogger.stop(); }

    // Counts the entities EntitiesLoadEvent carries, per type and in all.
    @Test
    void countsTheEntitiesThatEntitiesLoadEventDelivers() throws Exception {
        EntityLogger.start();
        Chunk chunk = worldA.getChunkAt(2, -3);
        loadEntities(chunk, spawn(chunk, ArmorStand.class), spawn(chunk, ArmorStand.class),
                drop(chunk), spawn(chunk, Pig.class));

        server.getScheduler().performTicks(FIRST_DUMP_TICKS);

        // world;x;z (the chunk's first block), then ArmorStand;Boat;Arrow;Item;Painting;ItemFrame;all
        awaitLog(lines -> lines.contains("world_a;32;-48;2;0;0;1;0;0;4"));
    }

    // A chunk's entities change while it is loaded; EntitiesUnloadEvent carries them as they are
    // saved, so the log keeps the latest count.
    @Test
    void recountsAChunkWhenItsEntitiesUnload() throws Exception {
        EntityLogger.start();
        Chunk chunk = worldA.getChunkAt(2, -3);
        loadEntities(chunk, spawn(chunk, ArmorStand.class));
        unloadEntities(chunk, spawn(chunk, ArmorStand.class), spawn(chunk, ArmorStand.class),
                spawn(chunk, ArmorStand.class));

        server.getScheduler().performTicks(FIRST_DUMP_TICKS);

        awaitLog(lines -> lines.equals(List.of(HEADER, "world_a;32;-48;3;0;0;0;0;0;3")));
    }

    // Once a chunk's entities are all gone it leaves the log and the map, instead of keeping its
    // last count there for good.
    @Test
    void forgetsAChunkWhoseEntitiesAreAllGone() throws Exception {
        EntityLogger.start();
        Chunk chunk = worldA.getChunkAt(2, -3);
        loadEntities(chunk, spawn(chunk, ArmorStand.class));
        firstDump(lines -> lines.contains("world_a;32;-48;1;0;0;0;0;0;1"));
        assertEquals(Set.of("world_a 32 -48"), markers(), "the first dump should have drawn the chunk");

        unloadEntities(chunk);
        server.getScheduler().performTicks(DUMP_PERIOD_TICKS);

        awaitLog(lines -> lines.equals(List.of(HEADER)));
        assertEquals(Set.of(), markers());
    }

    // Chunk (2,-3) exists in every world: one world's counts must not overwrite another's.
    @Test
    void keepsTheSameChunkInTwoWorldsApart() throws Exception {
        EntityLogger.start();
        Chunk inA = worldA.getChunkAt(2, -3);
        Chunk inB = worldB.getChunkAt(2, -3);
        loadEntities(inA, spawn(inA, ArmorStand.class));
        loadEntities(inB, spawn(inB, Pig.class), spawn(inB, Pig.class));

        server.getScheduler().performTicks(FIRST_DUMP_TICKS);

        List<String> lines = awaitLog(l -> l.contains("world_a;32;-48;1;0;0;0;0;0;1")
                                        && l.contains("world_b;32;-48;0;0;0;0;0;0;2"));
        assertEquals(HEADER, lines.get(0));
    }

    // Each chunk is drawn in its own world, not in the world of whoever switched logging on.
    @Test
    void drawsEachChunkInItsOwnWorld() throws Exception {
        EntityLogger.start();
        Chunk inA = worldA.getChunkAt(2, -3);
        Chunk inB = worldB.getChunkAt(2, -3);
        loadEntities(inA, spawn(inA, ArmorStand.class));
        loadEntities(inB, spawn(inB, Pig.class));

        firstDump(lines -> lines.size() == 3);

        assertEquals(Set.of("world_a 32 -48", "world_b 32 -48"), markers());
    }

    // A dump draws the map from the snapshot it logs: a chunk counted after the dump shows on
    // neither until the next one. (The old timer drew the markers a tick after writing the log,
    // from the live counts and against the log's max.)
    @Test
    void drawsTheMapFromTheSnapshotItLogs() throws Exception {
        EntityLogger.start();
        Chunk early = worldA.getChunkAt(2, -3);
        loadEntities(early, spawn(early, ArmorStand.class), spawn(early, ArmorStand.class));
        server.getScheduler().performTicks(FIRST_DUMP_TICKS);
        awaitLog(lines -> lines.contains("world_a;32;-48;2;0;0;0;0;0;2"));

        Chunk late = worldA.getChunkAt(5, 5);
        loadEntities(late, spawn(late, Pig.class));
        server.getScheduler().performOneTick();

        assertEquals(Set.of("world_a 32 -48"), markers());
    }

    // The log must not depend on the map. When dynmap fails in the middle of a dump (an
    // incompatible build, say), the file is still written.
    @Test
    void writesTheLogEvenWhenTheMapFails() throws Exception {
        EntityLogger.start();
        Chunk chunk = worldA.getChunkAt(2, -3);
        loadEntities(chunk, spawn(chunk, ArmorStand.class));

        dynmap.failDrawing(true);
        try {
            // MockBukkit hands a task's exception back to performTicks; Paper would log it.
            assertThrows(NoSuchMethodError.class,
                         () -> server.getScheduler().performTicks(FIRST_DUMP_TICKS));
        } finally {
            dynmap.failDrawing(false);
        }

        awaitLog(lines -> lines.contains("world_a;32;-48;1;0;0;0;0;0;1"));
    }

    @Test
    void stoppingClearsTheMap() throws Exception {
        EntityLogger.start();
        Chunk chunk = worldA.getChunkAt(2, -3);
        loadEntities(chunk, spawn(chunk, ArmorStand.class));
        firstDump(lines -> lines.contains("world_a;32;-48;1;0;0;0;0;0;1"));
        assertTrue(markers().contains("world_a 32 -48"), "the dump should have drawn the chunk: " + markers());

        EntityLogger.stop();

        assertEquals(Set.of(), markers());
    }

    @Test
    void stoppingForgetsTheCounts() throws Exception {
        EntityLogger.start();
        Chunk before = worldA.getChunkAt(2, -3);
        loadEntities(before, spawn(before, ArmorStand.class));
        EntityLogger.stop();

        EntityLogger.start();
        Chunk after = worldA.getChunkAt(5, 5);
        loadEntities(after, spawn(after, Pig.class));
        List<String> lines = firstDump(l -> l.contains("world_a;80;80;0;0;0;0;0;0;1"));

        assertEquals(2, lines.size(), "only the header and the chunk counted after the restart: " + lines);
    }

    // A second "on" used to register a second listener and timer and overwrite the references to
    // the first pair, which then could never be stopped.
    @Test
    void startingTwiceThenStoppingLeavesNothingRunning() {
        long tasksBefore = architectTasks();

        EntityLogger.start();
        EntityLogger.start();
        EntityLogger.stop();

        assertEquals(0, entityLogListeners(), "an entity-log listener is still registered");
        assertEquals(tasksBefore, architectTasks(), "an entity-log timer is still scheduled");
    }

    @Test
    void startAndStopSayWhetherTheyChangedAnything() {
        assertTrue(EntityLogger.start(), "first start");
        assertFalse(EntityLogger.start(), "logging was already on");
        assertTrue(EntityLogger.stop(), "first stop");
        assertFalse(EntityLogger.stop(), "logging was already off");
    }

    // The command used to cast its sender to Player for a world, so the console got a
    // ClassCastException.
    @Test
    void theConsoleCanSwitchEntityLoggingOnAndOff() {
        ConsoleCommandSenderMock console = server.getConsoleSender();
        while (console.nextComponentMessage() != null) { } // drop anything sent during enable

        assertDoesNotThrow(() -> server.dispatchCommand(console, "architect eLog true"));
        assertDoesNotThrow(() -> server.dispatchCommand(console, "architect eLog true"));
        assertDoesNotThrow(() -> server.dispatchCommand(console, "architect eLog false"));
        assertDoesNotThrow(() -> server.dispatchCommand(console, "architect eLog false"));

        assertEquals(List.of("Entity logging on!", "Entity logging is already on.",
                             "Entity logging off!", "Entity logging is already off."),
                     List.of(nextMessage(console), nextMessage(console),
                             nextMessage(console), nextMessage(console)));
    }

    // ---- helpers ----

    private static long architectTasks() {
        return server.getScheduler().getPendingTasks().stream()
                     .filter(task -> task.getOwner() == plugin && !task.isCancelled()).count();
    }

    private static long entityLogListeners() {
        return Arrays.stream(EntitiesLoadEvent.getHandlerList().getRegisteredListeners())
                     .filter(registered -> registered.getListener() instanceof EntityLogger.ELogListener)
                     .count();
    }

    // The next chat line as plain text, without MessageUtil's prefix and colours.
    private static String nextMessage(ConsoleCommandSenderMock console) {
        Component message = console.nextComponentMessage();
        assertNotNull(message, "no further message");
        String text = PlainTextComponentSerializer.plainText().serialize(message);
        return text.substring(text.indexOf("Entity logging"));
    }

    private static void loadEntities(Chunk chunk, Entity... entities) {
        server.getPluginManager().callEvent(new EntitiesLoadEvent(chunk, List.of(entities)));
    }

    private static void unloadEntities(Chunk chunk, Entity... entities) {
        server.getPluginManager().callEvent(new EntitiesUnloadEvent(chunk, List.of(entities)));
    }

    private static <T extends Entity> T spawn(Chunk chunk, Class<T> type) {
        return chunk.getWorld().spawn(inside(chunk), type);
    }

    private static Entity drop(Chunk chunk) {
        return chunk.getWorld().dropItem(inside(chunk), new ItemStack(Material.STONE));
    }

    private static Location inside(Chunk chunk) {
        return new Location(chunk.getWorld(), chunk.getX() * 16 + 8, 64, chunk.getZ() * 16 + 8);
    }

    // Runs the first dump and waits until its log is written. The map is drawn in the dump's own
    // tick, so it is up to date as soon as this returns.
    private static List<String> firstDump(Predicate<List<String>> ready) throws Exception {
        server.getScheduler().performTicks(FIRST_DUMP_TICKS);
        return awaitLog(ready);
    }

    // The entity layer's area markers, as "world label" (the label is the chunk's first block).
    private static Set<String> markers() {
        MarkerSet set = dynmap.getMarkerAPI().getMarkerSet("entities.markerset");
        if (set == null) return Set.of();
        return set.getAreaMarkers().stream()
                  .map(marker -> marker.getWorld() + " " + marker.getLabel())
                  .collect(Collectors.toSet());
    }

    private static File logFile() { return new File(plugin.getDataFolder(), "entityLog.dat"); }

    // The log is written off the main thread, so wait (up to 5 s) for a version that passes.
    private static List<String> awaitLog(Predicate<List<String>> ready) throws Exception {
        long deadline = System.currentTimeMillis() + 5000;
        List<String> lines = List.of();
        while (System.currentTimeMillis() < deadline) {
            if (logFile().exists()) {
                lines = Files.readAllLines(logFile().toPath());
                if (ready.test(lines)) return lines;
            }
            Thread.sleep(20);
        }
        return fail("entity log never matched; last read: " + lines);
    }
}
