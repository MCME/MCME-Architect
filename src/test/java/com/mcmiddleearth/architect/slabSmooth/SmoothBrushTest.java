package com.mcmiddleearth.architect.slabSmooth;

import com.mcmiddleearth.architect.ArchitectPlugin;
import com.mcmiddleearth.architect.Modules;
import com.mcmiddleearth.architect.Permission;
import com.mcmiddleearth.architect.PluginData;
import com.mcmiddleearth.architect.slabSmooth.core.Options;
import com.mcmiddleearth.architect.slabSmooth.core.Table;
import com.mcmiddleearth.architect.slabSmooth.core.TestTables;
import com.sk89q.worldedit.math.BlockVector3;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.permissions.PermissionAttachment;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockbukkit.mockbukkit.world.WorldMock;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Queue;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SmoothBrushTest {

    private static ServerMock server;
    private static ArchitectPlugin plugin;
    private static World on, other, off;
    private static Table terrainTable, allTable;

    /** A TableSource with two tables and a way to fail. */
    private static final class Tables implements TableSource {
        RuntimeException notReady;
        Error broken;
        final Map<Boolean, Integer> asked = Collections.synchronizedMap(new HashMap<>());
        String lastNamed;

        @Override
        public Packs.Choice choose(Player player, Location location, String named) {
            lastNamed = named;
            return new Packs.Choice(named != null ? named : "Human", named != null ? "set" : "default");
        }

        @Override
        public String regionPack(Location location) {
            return null;
        }

        @Override
        public Set<String> regionPacksIn(World world, BlockVector3 min, BlockVector3 max) {
            return Set.of();
        }

        @Override
        public Table table(Packs.Choice choice, boolean allBlocks) {
            if (broken != null) {
                throw broken;
            }
            if (notReady != null) {
                throw notReady;
            }
            asked.merge(allBlocks, 1, Integer::sum);
            return allBlocks ? allTable : terrainTable;
        }
    }

    private Tables tables;
    private PlayerOptions options;
    private ModuleSwitch module;
    private int maxRadius;
    private List<LinkageError> reported;
    private SmoothBrush.Context context;
    private PlayerMock player;
    private SmoothBrush brush;
    private Map<UUID, Player> online;

    @BeforeAll
    static void startTheServer() throws Exception {
        server = MockBukkit.mock();
        plugin = MockBukkit.load(ArchitectPlugin.class);
        on = addWorld("sb-on");
        other = addWorld("sb-other");
        off = addWorld("sb-off");
        terrainTable = TestTables.handMade();
        allTable = TestTables.handMade();
    }

    @AfterAll
    static void stopTheServer() {
        if (MockBukkit.isMocked()) {
            MockBukkit.unmock();
        }
    }

    private static World addWorld(String name) {
        WorldMock added = new WorldMock(Material.STONE, -64, 320, 0);
        added.setName(name);
        server.addWorld(added);
        return added;
    }

    @BeforeEach
    void setUp() {
        PluginData.setModuleEnabled(on, Modules.SLAB_SMOOTH, true);
        PluginData.setModuleEnabled(other, Modules.SLAB_SMOOTH, true);
        PluginData.setModuleEnabled(off, Modules.SLAB_SMOOTH, false);
        tables = new Tables();
        options = new PlayerOptions(new Options());
        module = new ModuleSwitch(Runnable::run);
        maxRadius = 12;
        reported = new ArrayList<>();
        context = new SmoothBrush.Context(tables, options, module, maxRadius, reported::add);
        player = server.addPlayer();
        online = new HashMap<>();
        online.put(player.getUniqueId(), player);
        brush = new SmoothBrush(player.getUniqueId(), () -> context, online::get);
    }

    private static String text(Component component) {
        return PlainTextComponentSerializer.plainText().serialize(component);
    }

    @Test
    void theRadiusIsClampedToTheLimit() {
        assertEquals(12, brush.plan(on, 0, 64, 0, 40).radius(), 0.0);
        assertEquals(5, brush.plan(on, 0, 64, 0, 5).radius(), 0.0, "below the limit it stays as asked");
        assertEquals(12, brush.plan(on, 0, 64, 0, 12).radius(), 0.0);
    }

    @Test
    void aWorldWithTheModuleOffDoesNothingAndSaysSo() {
        assertNull(brush.plan(off, 0, 64, 0, 5));
        assertEquals("SlabSmooth is off in this world.", text(player.nextActionBar()));
        assertTrue(tables.asked.isEmpty(), "no table was even looked up");
        assertNull(player.nextActionBar(), "one message");

        assertNotNull(brush.plan(on, 0, 64, 0, 5), "the same brush works in a world where it is on");
        assertNull(player.nextActionBar());
    }

    @Test
    void blocksAllNeedsThePermissionAtEveryClick() {
        options.set(player.getUniqueId(), "blocks", "all");

        SmoothBrush.Click without = brush.plan(on, 0, 64, 0, 5);
        assertSame(terrainTable, without.table());
        assertSame(terrainTable, without.tableAll(), "no hint for those who may not use it");
        assertEquals("terrain", without.options().blocks);

        PermissionAttachment attachment = player.addAttachment(plugin, Permission.SLAB_SMOOTH_ALL_BLOCKS.getPermissionNode(), true);
        SmoothBrush.Click with = brush.plan(on, 0, 64, 0, 5);
        assertSame(allTable, with.table());
        assertEquals("all", with.options().blocks);

        player.removeAttachment(attachment);
        SmoothBrush.Click taken = brush.plan(on, 0, 64, 0, 5);
        assertSame(terrainTable, taken.table(), "taken away, the next click is back to terrain");
        assertEquals("all", options.of(player.getUniqueId()).blocks, "the player's setting itself stays");
    }

    @Test
    void thoseWithThePermissionGetBothTablesForTheHint() {
        player.addAttachment(plugin, Permission.SLAB_SMOOTH_ALL_BLOCKS.getPermissionNode(), true);

        SmoothBrush.Click click = brush.plan(on, 0, 64, 0, 5);
        assertSame(terrainTable, click.table());
        assertSame(allTable, click.tableAll());
    }

    @Test
    void aPackBeingReadSaysTryAgainInTheActionBar() {
        tables.notReady = new IllegalStateException("The Human pack is still being read, try again in a moment.");

        assertNull(brush.plan(on, 0, 64, 0, 5));
        assertEquals("SlabSmooth: The Human pack is still being read, try again in a moment.", text(player.nextActionBar()));
    }

    @Test
    void aPlayerWhoCannotBeFoundGetsTheTerrainTableAndNoMessage() {
        options.set(player.getUniqueId(), "blocks", "all");
        online.clear();

        SmoothBrush.Click click = brush.plan(on, 0, 64, 0, 5);
        assertSame(terrainTable, click.table(), "not found: no permission to prove, so terrain");
        assertNull(player.nextActionBar());

        PluginData.setModuleEnabled(on, Modules.SLAB_SMOOTH, false);
        assertNull(brush.plan(on, 0, 64, 0, 5), "and the switch still holds");
        assertNull(player.nextActionBar(), "nobody to tell");
    }

    @Test
    void thePlayersChosenPackIsPassedOn() {
        options.namePack(player.getUniqueId(), "Elf");

        assertEquals("Elf", brush.plan(on, 0, 64, 0, 5).choice().pack());
        assertEquals("Elf", tables.lastNamed);
    }

    @Test
    void plansAreSafeFromManyThreads() throws Exception {
        assertTrue(module.enabledIn(on) && module.enabledIn(other), "the main thread has read both worlds");
        options.set(player.getUniqueId(), "band", "3");
        int threads = 8, each = 200;
        CountDownLatch start = new CountDownLatch(1);
        List<Throwable> failures = Collections.synchronizedList(new ArrayList<>());
        List<Thread> pool = new ArrayList<>();
        for (int t = 0; t < threads; t++) {
            int number = t;
            Thread thread = new Thread(() -> {
                try {
                    start.await();
                    for (int i = 0; i < each; i++) {
                        World world = (i + number) % 2 == 0 ? on : other;
                        double size = 1 + (i % 30);
                        SmoothBrush.Click click = brush.plan(world, i, 64, number, size);
                        assertNotNull(click);
                        assertEquals(Math.min(size, 12), click.radius(), 0.0);
                        assertSame(terrainTable, click.table());
                        assertEquals(3, click.options().band);
                        assertEquals("default", click.choice().why());
                    }
                } catch (Throwable e) {
                    failures.add(e);
                }
            }, "click-" + t);
            pool.add(thread);
            thread.start();
        }
        start.countDown();
        for (Thread thread : pool) {
            thread.join();
        }
        assertEquals(List.of(), failures);
        assertNull(player.nextActionBar(), "nobody was told anything");
    }

    /** A module switch whose main-thread reads wait in a queue, as they wait for the next tick on a server. */
    private Queue<Runnable> readsWaitForTheMainThread() {
        Queue<Runnable> mainQueue = new ConcurrentLinkedQueue<>();
        context = new SmoothBrush.Context(tables, options, new ModuleSwitch(mainQueue::add), maxRadius, reported::add);
        return mainQueue;
    }

    /** A click as FAWE runs it: on a thread of its own. */
    private SmoothBrush.Click planElsewhere(World world) throws InterruptedException {
        AtomicReference<SmoothBrush.Click> click = new AtomicReference<>();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Thread thread = new Thread(() -> {
            try {
                click.set(brush.plan(world, 0, 64, 0, 5));
            } catch (Throwable e) {
                failure.set(e);
            }
        }, "fawe-like");
        thread.start();
        thread.join();
        if (failure.get() != null) {
            throw new AssertionError("the click threw", failure.get());
        }
        return click.get();
    }

    // After every start and reload, and in a world loaded since, a click from FAWE's thread can come before the main
    // thread has read the world's switch. The world isn't off then.
    @Test
    void aFirstClickInAWorldNotYetReadSaysToClickAgain() throws Exception {
        Queue<Runnable> mainQueue = readsWaitForTheMainThread();

        assertNull(planElsewhere(on), "nothing is done before the switch is read");
        assertEquals("SlabSmooth is still checking this world; click again.", text(player.nextActionBar()));
        assertNull(planElsewhere(off));
        assertEquals("SlabSmooth is still checking this world; click again.", text(player.nextActionBar()),
                "a world not read yet isn't called off either");
        assertTrue(tables.asked.isEmpty(), "no table was looked up");
        assertEquals(2, mainQueue.size(), "the main thread is asked to read both");

        while (!mainQueue.isEmpty()) {
            mainQueue.remove().run(); // the next tick
        }
        assertNotNull(planElsewhere(on), "the next click works");
        assertNull(player.nextActionBar());
        assertNull(planElsewhere(off));
        assertEquals("SlabSmooth is off in this world.", text(player.nextActionBar()));
    }

    @Test
    void plansAreSafeFromManyThreadsBeforeTheWorldsAreRead() throws Exception {
        Queue<Runnable> mainQueue = readsWaitForTheMainThread();
        options.set(player.getUniqueId(), "band", "3");

        List<SmoothBrush.Click> early = planFromManyThreads();
        assertTrue(early.stream().allMatch(Objects::isNull), "nothing is done before the switches are read");
        List<String> said = new ArrayList<>();
        for (Component bar = player.nextActionBar(); bar != null; bar = player.nextActionBar()) {
            said.add(text(bar));
        }
        assertEquals(early.size(), said.size(), "every click is answered");
        assertEquals(Set.of("SlabSmooth is still checking this world; click again."), Set.copyOf(said), "never off");
        assertEquals(2, mainQueue.size(), "one read per world, however many clicks asked");

        while (!mainQueue.isEmpty()) {
            mainQueue.remove().run(); // the next tick
        }
        for (SmoothBrush.Click click : planFromManyThreads()) {
            assertNotNull(click);
            assertEquals(5, click.radius(), 0.0);
            assertSame(terrainTable, click.table());
            assertEquals(3, click.options().band);
            assertEquals("default", click.choice().why());
        }
        assertNull(player.nextActionBar(), "nobody was told anything");
    }

    /** 8 threads, started together, each planning 200 clicks that alternate between the two worlds where it is on. */
    private List<SmoothBrush.Click> planFromManyThreads() throws InterruptedException {
        int threads = 8, each = 200;
        CountDownLatch start = new CountDownLatch(1);
        List<SmoothBrush.Click> clicks = Collections.synchronizedList(new ArrayList<>());
        List<Throwable> failures = Collections.synchronizedList(new ArrayList<>());
        List<Thread> pool = new ArrayList<>();
        for (int t = 0; t < threads; t++) {
            int number = t;
            Thread thread = new Thread(() -> {
                try {
                    start.await();
                    for (int i = 0; i < each; i++) {
                        clicks.add(brush.plan((i + number) % 2 == 0 ? on : other, i, 64, number, 5));
                    }
                } catch (Throwable e) {
                    failures.add(e);
                }
            }, "click-" + t);
            pool.add(thread);
            thread.start();
        }
        start.countDown();
        for (Thread thread : pool) {
            thread.join();
        }
        assertEquals(List.of(), failures);
        assertEquals(threads * each, clicks.size());
        return clicks;
    }

    @Test
    void aBrushBoundBeforeAReloadUsesTheNewSettings() {
        AtomicReference<SmoothBrush.Context> current = new AtomicReference<>(context);
        SmoothBrush bound = new SmoothBrush(player.getUniqueId(), current::get, online::get);
        assertEquals(12, bound.plan(on, 0, 64, 0, 40).radius(), 0.0);
        assertEquals(2, bound.plan(on, 0, 64, 0, 40).options().band);

        Options changed = new Options();
        changed.band = 4;
        Tables reloaded = new Tables();
        current.set(new SmoothBrush.Context(reloaded, new PlayerOptions(changed), module, 30, reported::add));

        SmoothBrush.Click click = bound.plan(on, 0, 64, 0, 40);
        assertEquals(30, click.radius(), 0.0, "the new limit");
        assertEquals(4, click.options().band, "the new options");
        assertEquals(1, reloaded.asked.size(), "and the new tables");
    }

    @Test
    void aClickAfterDisableDoesNothing() {
        Supplier<SmoothBrush.Context> disabled = () -> null;
        SmoothBrush bound = new SmoothBrush(player.getUniqueId(), disabled, online::get);

        assertNull(bound.plan(on, 0, 64, 0, 5));
        assertNull(player.nextActionBar(), "the plugin is going away; there is nothing to say");
        assertTrue(tables.asked.isEmpty());
    }

    @Test
    void aLinkageErrorDuringAClickGoesToBroken() {
        NoSuchMethodError error = new NoSuchMethodError("Table.kind3");
        tables.broken = error;

        assertNull(brush.planReportingBreakage(on, 0, 64, 0, 5), "it is caught, so it never reaches WorldEdit's queue");
        assertEquals(List.of(error), reported);
        assertEquals("SlabSmooth has stopped working: ask an admin to check the server log.", text(player.nextActionBar()));
    }

    @Test
    void otherErrorsAreLeftAlone() {
        IllegalArgumentException bug = new IllegalArgumentException("a bug");
        tables.notReady = bug;

        assertSame(bug, org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> brush.planReportingBreakage(on, 0, 64, 0, 5)), "WorldEdit reports those itself");
        assertEquals(List.of(), reported);
    }
}
