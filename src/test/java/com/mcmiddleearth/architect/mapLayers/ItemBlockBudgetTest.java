package com.mcmiddleearth.architect.mapLayers;

import com.destroystokyo.paper.event.entity.EntityAddToWorldEvent;
import com.destroystokyo.paper.event.entity.EntityRemoveFromWorldEvent;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.Painting;
import org.bukkit.entity.Pig;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.event.world.EntitiesUnloadEvent;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.world.WorldMock;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

class ItemBlockBudgetTest {

    @TempDir
    Path dir;
    private ServerMock server;
    private Plugin plugin;
    private WorldMock world;
    private final AtomicInteger changes = new AtomicInteger();
    private final AtomicLong clock = new AtomicLong(1_000);
    private ItemBlockBudget budget;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin();
        world = server.addSimpleWorld("world");
        budget = newBudget();
        budget.start(plugin, entry -> true);
    }

    @AfterEach
    void tearDown() {
        budget.stop();
        MockBukkit.unmock();
    }

    private ItemBlockBudget newBudget() {
        return newBudget(1); // reports every second, so each step below is seen at once
    }

    private ItemBlockBudget newBudget(int reportSeconds) {
        return new ItemBlockBudget(new File(dir.toFile(), "itemBlockBudget.yml"), changes::incrementAndGet,
                reportSeconds, clock::get);
    }

    private static ItemBlockBudget.ChunkKey key(Chunk chunk) {
        return new ItemBlockBudget.ChunkKey(chunk.getWorld().getName(), chunk.getX(), chunk.getZ());
    }

    private <T extends Entity> T spawn(Chunk chunk, Class<T> type) {
        return world.spawn(new Location(world, chunk.getX() * 16 + 8, 64, chunk.getZ() * 16 + 8), type);
    }

    private void load(Chunk chunk, Entity... entities) {
        server.getPluginManager().callEvent(new EntitiesLoadEvent(chunk, List.of(entities)));
    }

    @Test
    void aLoadedChunkIsCountedByKind() {
        Chunk chunk = world.getChunkAt(3, -2);

        load(chunk, spawn(chunk, ArmorStand.class), spawn(chunk, ArmorStand.class), spawn(chunk, ItemFrame.class),
                spawn(chunk, Pig.class));

        ItemBlockBudget.Count count = budget.counts().get(key(chunk));
        assertEquals(2, count.armorStands());
        assertEquals(1, count.itemFrames());
        assertEquals(0, count.paintings());
        assertEquals(3, count.total(), "pigs do not count");
        assertEquals(1_000, count.changedAt());
        assertEquals(0, changes.get(), "chunks load all the time, so the map hears once a second");
        server.getScheduler().performTicks(20);
        assertEquals(1, changes.get());
    }

    // Each report costs a redraw, and dynmap a rebuild of the world's marker file, so on the server the budget
    // reports at most every 10 s. A change after a quiet spell still shows within a second.
    @Test
    void aBusyMapHearsAtMostOncePerReportInterval() {
        budget.stop();
        budget = newBudget(10);
        budget.start(plugin, entry -> true);
        Chunk chunk = world.getChunkAt(0, 0);

        load(chunk, spawn(chunk, ArmorStand.class));
        server.getScheduler().performTicks(20);
        assertEquals(1, changes.get(), "the first change after a quiet spell, within a second");

        load(chunk, spawn(chunk, ArmorStand.class), spawn(chunk, ArmorStand.class));
        server.getScheduler().performTicks(9 * 20);
        assertEquals(2, budget.counts().get(key(chunk)).total(), "counted at once");
        assertEquals(1, changes.get(), "but not reported within 10 s of the last report");
        server.getScheduler().performTicks(20);
        assertEquals(2, changes.get(), "10 s after it");
    }

    @Test
    void theSameCountAgainIsNoChange() {
        Chunk chunk = world.getChunkAt(0, 0);
        ArmorStand stand = spawn(chunk, ArmorStand.class);
        load(chunk, stand);
        server.getScheduler().performTicks(20);
        clock.set(5_000);

        load(chunk, stand);
        server.getScheduler().performTicks(20);

        assertEquals(1, changes.get(), "nothing to redraw");
        assertEquals(1_000, budget.counts().get(key(chunk)).changedAt(), "the time says since when");
    }

    @Test
    void anotherKindWithTheSameTotalIsAChangeAndMovesTheTime() {
        Chunk chunk = world.getChunkAt(0, 0);
        load(chunk, spawn(chunk, ArmorStand.class));
        server.getScheduler().performTicks(20);
        clock.set(5_000);

        load(chunk, spawn(chunk, Painting.class));
        server.getScheduler().performTicks(20);

        assertEquals(new ItemBlockBudget.Count(0, 0, 1, 5_000), budget.counts().get(key(chunk)),
                "a painting instead of a stand, since 5000");
        assertEquals(2, changes.get());
    }

    @Test
    void anEntityThatComesOrGoesIsRecountedWithinASecond() {
        Chunk chunk = world.getChunkAt(-1, -1);
        load(chunk, spawn(chunk, ArmorStand.class));
        ArmorStand placed = spawn(chunk, ArmorStand.class);

        server.getPluginManager().callEvent(new EntityAddToWorldEvent(placed, world));
        assertEquals(1, budget.counts().get(key(chunk)).total(), "not yet: recounts come in batches");
        server.getScheduler().performTicks(20);

        assertEquals(2, budget.counts().get(key(chunk)).total());
        placed.remove();
        server.getPluginManager().callEvent(new EntityRemoveFromWorldEvent(placed, world));
        server.getScheduler().performTicks(20);
        assertEquals(1, budget.counts().get(key(chunk)).total());
    }

    @Test
    void aRefreshCountsLoadedChunksNoEventAnnounced() {
        Chunk chunk = world.getChunkAt(5, 5);
        spawn(chunk, ArmorStand.class);
        spawn(chunk, ItemFrame.class);

        budget.recountLoaded();

        assertEquals(2, budget.counts().get(key(chunk)).total());
        server.getScheduler().performTicks(20);
        assertEquals(1, changes.get(), "and the map hears of it");
    }

    @Test
    void aRefreshCountsEveryWorld() {
        WorldMock other = server.addSimpleWorld("other");
        Chunk chunk = other.getChunkAt(1, 1);
        other.spawn(new Location(other, 24, 64, 24), ArmorStand.class);

        budget.recountLoaded();

        assertNotNull(budget.counts().get(key(chunk)), "a chunk in the second world is counted too");
        assertEquals(1, budget.counts().get(key(chunk)).total());
    }

    @Test
    void aChunkLeftWithNothingThatCountsIsForgotten() {
        Chunk chunk = world.getChunkAt(0, 0);
        load(chunk, spawn(chunk, ArmorStand.class));

        load(chunk, spawn(chunk, Pig.class));
        server.getScheduler().performTicks(20);

        assertNull(budget.counts().get(key(chunk)));
        assertEquals(1, changes.get(), "both changes in one report");
        load(chunk, spawn(chunk, ArmorStand.class));
        server.getScheduler().performTicks(20);
        load(chunk, spawn(chunk, Pig.class));
        server.getScheduler().performTicks(20);
        assertEquals(3, changes.get(), "a chunk that is forgotten is reported too: its tile must go");
    }

    // Paper 26.2's order: a loading chunk's entities enter the world first, then EntitiesLoadEvent lists them all.
    @Test
    void aChunkIsCountedOnceAsItLoads() {
        Chunk chunk = world.getChunkAt(0, 0);
        ArmorStand stand = spawn(chunk, ArmorStand.class);
        server.getPluginManager().callEvent(new EntityAddToWorldEvent(stand, world));
        load(chunk, stand);

        stand.teleport(new Location(world, 24, 64, 8)); // a move fires no event: only a second count would see it
        server.getScheduler().performTicks(20);

        assertEquals(1, budget.counts().get(key(chunk)).total(), "the event's own list was the count");
    }

    // Paper builds EntitiesLoadEvent's list before any handler runs, so an entity another plugin adds from its own
    // handler is missing from it. Its add event marks the chunk; that mark must outlive the count at load.
    @Test
    void anEntityAnotherPluginAddsAsTheChunkLoadsIsCountedToo() {
        Chunk chunk = world.getChunkAt(0, 0);
        ArmorStand stand = spawn(chunk, ArmorStand.class);
        server.getPluginManager().registerEvents(new Listener() {
            @EventHandler
            public void onLoad(EntitiesLoadEvent event) {
                ArmorStand hologram = spawn(chunk, ArmorStand.class);
                server.getPluginManager().callEvent(new EntityAddToWorldEvent(hologram, world));
            }
        }, plugin);

        load(chunk, stand);
        server.getScheduler().performTicks(20);

        assertEquals(2, budget.counts().get(key(chunk)).total());
    }

    @Test
    void anUnloadedChunkKeepsItsLastCount() {
        Chunk chunk = world.getChunkAt(0, 0);
        ArmorStand stand = spawn(chunk, ArmorStand.class);
        load(chunk, stand);

        server.getPluginManager().callEvent(new EntitiesUnloadEvent(chunk, List.of(stand)));
        stand.remove(); // unloaded with its chunk
        world.unloadChunk(0, 0);
        // A late mark, e.g. a stand that moved into the unloaded chunk and left the world there.
        server.getPluginManager().callEvent(new EntityRemoveFromWorldEvent(stand, world));
        server.getScheduler().performTicks(20);

        assertEquals(1, budget.counts().get(key(chunk)).total(), "an unloaded chunk is not recounted as empty");
    }

    // Paper 26.2's order: a chunk's entities leave the world first, then EntitiesUnloadEvent lists them.
    @Test
    void anUnloadingChunkIsCountedFromTheEventsOwnList() {
        Chunk chunk = world.getChunkAt(0, 0);
        ArmorStand kept = spawn(chunk, ArmorStand.class);
        ArmorStand broken = spawn(chunk, ArmorStand.class);
        load(chunk, kept, broken);
        broken.remove();
        server.getPluginManager().callEvent(new EntityRemoveFromWorldEvent(broken, world));

        server.getPluginManager().callEvent(new EntityRemoveFromWorldEvent(kept, world));
        server.getPluginManager().callEvent(new EntitiesUnloadEvent(chunk, List.of(kept)));
        kept.remove();
        world.unloadChunk(0, 0);
        server.getScheduler().performTicks(20);

        assertEquals(1, budget.counts().get(key(chunk)).total(), "the stand broken just before the unload is gone");
    }

    @Test
    void aStoppedBudgetHearsAndReportsNothing() {
        Chunk chunk = world.getChunkAt(0, 0);
        load(chunk, spawn(chunk, ArmorStand.class)); // counted, not yet reported
        budget.stop();

        load(chunk, spawn(chunk, ArmorStand.class), spawn(chunk, ArmorStand.class));
        server.getScheduler().performTicks(40);

        assertEquals(1, budget.counts().get(key(chunk)).total(), "no longer listening");
        assertEquals(0, changes.get(), "and its timers are gone");
    }

    @Test
    void theCountsAreSavedOnceAMinuteAndAForgottenChunkLeavesTheFile() throws Exception {
        Chunk chunk = world.getChunkAt(0, 0);
        Path file = dir.resolve("itemBlockBudget.yml");
        load(chunk, spawn(chunk, ArmorStand.class));
        server.getScheduler().performTicks(1200);
        assertTrue(Files.readString(file).contains("world;0;0;1;0;0;1000"), "saved without waiting for stop");

        load(chunk, spawn(chunk, Pig.class));
        server.getScheduler().performTicks(1200);

        assertFalse(Files.readString(file).contains("world;0;0;"), "a forgotten chunk leaves the file too");
    }

    @Test
    void theCountsSurviveARestart() throws Exception {
        Chunk kept = world.getChunkAt(1, 1);
        Chunk dropped = world.getChunkAt(2, 2);
        load(kept, spawn(kept, ArmorStand.class));
        load(dropped, spawn(dropped, ItemFrame.class));
        budget.stop();

        ItemBlockBudget restarted = newBudget();
        restarted.start(plugin, entry -> entry.getKey().x() == 1);
        budget = restarted;

        assertEquals(1, restarted.counts().get(key(kept)).armorStands());
        assertEquals(1_000, restarted.counts().get(key(kept)).changedAt());
        restarted.stop();
        assertFalse(Files.readString(dir.resolve("itemBlockBudget.yml")).contains("world;2;2"),
                "only chunks the layer shows are kept");
    }

    // The new file is written next to the old one first: a crash or a full disk mid-write keeps the old one whole.
    @Test
    void aSaveThatFailsKeepsTheOldFileAndIsTriedAgain() throws Exception {
        Chunk chunk = world.getChunkAt(0, 0);
        Path file = dir.resolve("itemBlockBudget.yml");
        load(chunk, spawn(chunk, ArmorStand.class));
        server.getScheduler().performTicks(1200);
        Path inTheWay = Files.createDirectory(dir.resolve("itemBlockBudget.yml.tmp"));

        load(chunk, spawn(chunk, ArmorStand.class), spawn(chunk, ArmorStand.class));
        server.getScheduler().performTicks(1200);
        assertTrue(Files.readString(file).contains("world;0;0;1;0;0;1000"), "the old file, as it was");

        Files.delete(inTheWay);
        server.getScheduler().performTicks(1200);
        assertTrue(Files.readString(file).contains("world;0;0;2;0;0;1000"), "saved a minute later");
    }

    @Test
    void anUnreadableLineIsSkipped() throws Exception {
        budget.stop();
        Files.writeString(dir.resolve("itemBlockBudget.yml"), "chunks:\n- not;a;count\n- world;0;0;3;2;1;500\n");

        ItemBlockBudget restarted = newBudget();
        restarted.start(plugin, entry -> true);
        budget = restarted;

        assertEquals(1, restarted.counts().size(), "the bad line is skipped, the good one kept");
        assertEquals(new ItemBlockBudget.Count(3, 2, 1, 500),
                restarted.counts().get(new ItemBlockBudget.ChunkKey("world", 0, 0)));
        restarted.stop();
        assertTrue(Files.readString(dir.resolve("itemBlockBudget.yml")).contains("world;0;0;3;2;1;500"),
                "written back as it was read");
    }
}
