package com.mcmiddleearth.architect.slabSmooth;

import com.mcmiddleearth.architect.ArchitectPlugin;
import com.mcmiddleearth.architect.Modules;
import com.mcmiddleearth.architect.PluginData;
import com.mcmiddleearth.architect.slabSmooth.core.Options;
import com.mcmiddleearth.pluginutil.message.MessageUtil;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.regions.CuboidRegion;
import org.bukkit.ChatColor;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.command.ConsoleCommandSenderMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockbukkit.mockbukkit.world.WorldMock;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import java.util.logging.Handler;
import java.util.logging.LogRecord;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * SlabSmooth in Architect, end to end: its guard, what starts and when, /architect reload and disable. WorldEdit and the
 * areas are fakes; the packs are a tiny real one, read by the real Packs on its own thread. One mock and load per
 * class: Architect keeps its data folder in static fields.
 */
class SlabSmoothWiringTest {

    private static final String JOB_WORKER = "Architect SlabSmooth", PACKS_WORKER = "Architect SlabSmooth packs";
    private static final String FAILED = "Something went wrong; the server log has the details.";

    @TempDir
    static Path dir;
    private static ServerMock server;
    private static ArchitectPlugin plugin;
    private static WorldMock world;
    private static FakePackSources sources;

    private final List<LogRecord> logged = Collections.synchronizedList(new ArrayList<>());
    private final Handler handler = new Handler() {
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
    };
    private FakeSmoothWorldEdit worldEdit;
    private FakeAreaSource areas;
    private ConsoleCommandSenderMock console;

    @BeforeAll
    static void startTheServer() throws IOException {
        server = MockBukkit.mock();
        plugin = MockBukkit.load(ArchitectPlugin.class);
        world = new WorldMock(Material.STONE, -64, 320, 0);
        world.setName("world");
        server.addWorld(world);
        sources = new FakePackSources(dir, plugin.getDataFolder().toPath());
        plugin.getConfig().set("slabSmooth.download", false);
        plugin.getConfig().set("slabSmooth.vanilla.clientJar", sources.clientJar.toString());
        plugin.saveConfig(); // /architect reload reads config.yml again
    }

    @AfterAll
    static void stopTheServer() {
        if (MockBukkit.isMocked()) {
            MockBukkit.unmock();
        }
    }

    @BeforeEach
    void freshFakes() {
        worldEdit = new FakeSmoothWorldEdit();
        worldEdit.base = p -> p.y() <= 60 ? "minecraft:bricks" : "minecraft:air";
        areas = new FakeAreaSource(worldEdit::stateAt);
        sources.asked.clear();
        console = server.getConsoleSender();
        while (console.nextMessage() != null) {
            // start with an empty console
        }
        plugin.getLogger().addHandler(handler);
    }

    @AfterEach
    void backToTheRealServices() {
        plugin.getLogger().removeHandler(handler);
        PluginData.setModuleEnabled(world, Modules.SLAB_SMOOTH, false);
        SlabSmooth.disable();
        SlabSmooth.enable(plugin);
    }

    /** SlabSmooth again, on the fakes. */
    private void useFakes() {
        SlabSmooth.disable();
        SlabSmooth.enable(plugin, () -> new SlabSmooth.Services(sources, worldEdit, areas));
    }

    private static PlayerMock op() {
        PlayerMock player = server.addPlayer();
        player.teleport(new Location(world, 4, 70, 4));
        player.setOp(true);
        return player;
    }

    private static CuboidRegion box() {
        return new CuboidRegion(BlockVector3.at(0, 55, 0), BlockVector3.at(9, 65, 9));
    }

    /** What a player was told since last asked: "info: ..." or "error: ...", without Architect's prefix. */
    private static List<String> told(PlayerMock player) {
        MessageUtil util = PluginData.getMessageUtil();
        String error = String.valueOf(util.ERROR), info = String.valueOf(util.INFO), prefix = util.getPREFIX();
        List<String> out = new ArrayList<>();
        for (String message = player.nextMessage(); message != null; message = player.nextMessage()) {
            String kind = message.startsWith(error) ? "error: " : message.startsWith(info) ? "info: " : "unknown: ";
            String text = message.replaceAll(ChatColor.COLOR_CHAR + ".", "");
            out.add(kind + (text.startsWith(prefix) ? text.substring(prefix.length()) : text));
        }
        return out;
    }

    /** Runs ticks, and lets the worker run, until the player is told something. */
    private static List<String> awaitTold(PlayerMock player) {
        long end = System.nanoTime() + TimeUnit.SECONDS.toNanos(20);
        while (System.nanoTime() < end) {
            server.getScheduler().performOneTick();
            List<String> lines = told(player);
            if (!lines.isEmpty()) {
                return lines;
            }
            sleep(10);
        }
        return fail("nobody told " + player.getName() + " anything");
    }

    private static void await(BooleanSupplier condition, String what) {
        long end = System.nanoTime() + TimeUnit.SECONDS.toNanos(20);
        while (!condition.getAsBoolean()) {
            if (System.nanoTime() > end) {
                fail("waited 20 s for this: " + what);
            }
            sleep(10);
        }
    }

    /** Asks for the default pack's table until it is there: the first asks only start reading it. */
    private static void awaitTable() {
        await(() -> {
            try {
                return SlabSmooth.parts().packs().table(new Packs.Choice(FakePackSources.PACK, "default"), false) != null;
            } catch (Refusal notYet) {
                return false;
            }
        }, "the table of the tiny pack: " + SlabSmooth.parts().packs().status());
    }

    /** The live threads SlabSmooth started: its job worker, and its packs' worker, whose name starts the same. */
    private static List<String> threads() {
        return Thread.getAllStackTraces().keySet().stream().filter(Thread::isAlive).map(Thread::getName)
                .filter(name -> name.startsWith(JOB_WORKER)).sorted().toList();
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    /** Each call the scheduler was given: its method, its plugin and its delay. */
    private static List<String> described(FakeScheduler paper) {
        return paper.calls().stream().map(call -> call.method() + " for " + call.plugin().getName() + ", delay "
                + call.delay()).toList();
    }

    /** A world whose chunks Paper is still reading: none of them is handed over during the test. */
    private static World stillLoading() {
        return (World) Proxy.newProxyInstance(SlabSmoothWiringTest.class.getClassLoader(), new Class<?>[] {World.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getMinHeight" -> 0;
                    case "getMaxHeight" -> 256;
                    case "getChunkAtAsync" -> new CompletableFuture<Chunk>();
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    case "toString" -> "a world whose chunks are loading";
                    default -> throw new AssertionError("unexpected call: " + method);
                });
    }

    // ---------------------------------------------------------------------------------------------------------

    @Test
    void aFailureWhileStartingSwitchesItOffAndArchitectKeepsRunning() {
        SlabSmooth.disable();
        NoClassDefFoundError missing = new NoClassDefFoundError("com/sk89q/worldedit/WorldEdit");

        assertDoesNotThrow(() -> SlabSmooth.enable(plugin, () -> {
            throw missing;
        }), "it must not reach Architect's onEnable, which would disable all of Architect");

        assertTrue(plugin.isEnabled());
        assertFalse(SlabSmooth.isAvailable());
        assertEquals(List.of("it could not start: NoClassDefFoundError: com/sk89q/worldedit/WorldEdit"), SlabSmooth.problems());
        assertNull(SlabSmooth.brushContext(), "a brush bound before does nothing");
        assertEquals(1, logged.stream().filter(r -> r.getThrown() == missing).count(), "said once, in the log");
        server.dispatchCommand(console, "sls packs");
        String reply = console.nextMessage();
        assertNotNull(reply);
        assertTrue(reply.contains("SlabSmooth is unavailable: it could not start: NoClassDefFoundError"), reply);
    }

    @Test
    void withoutWorldEditItStartsAndTheEditCommandsSaySo() {
        assertFalse(server.getPluginManager().isPluginEnabled("WorldEdit"));
        assertFalse(server.getPluginManager().isPluginEnabled("FastAsyncWorldEdit"));
        SlabSmooth.disable();

        assertDoesNotThrow(() -> SlabSmooth.enable(plugin), "the real services");

        assertTrue(SlabSmooth.isAvailable(), SlabSmooth.problems().toString());
        PluginData.setModuleEnabled(world, Modules.SLAB_SMOOTH, true);
        PlayerMock builder = op();
        builder.getInventory().setItemInMainHand(new ItemStack(Material.STICK));
        for (String command : List.of("sls selection", "sls brush 4", "sls area 0 55 0 9 65 9")) {
            builder.performCommand(command);
            assertEquals(List.of("error: WorldEdit isn't available on this server, so SlabSmooth can't edit."), told(builder),
                    command);
        }
        builder.performCommand("sls packs");
        List<String> packs = told(builder);
        assertFalse(packs.isEmpty());
        assertTrue(packs.stream().allMatch(line -> line.startsWith("info: ")), "what needs no WorldEdit still works: " + packs);
    }

    @Test
    void aLinkageErrorOnFirstUseSwitchesItOff() {
        Plugin worldEditPlugin = MockBukkit.createMockPlugin("WorldEdit");
        try {
            useFakes();
            PluginData.setModuleEnabled(world, Modules.SLAB_SMOOTH, true);
            NoSuchMethodError changed = new NoSuchMethodError("'com.sk89q.worldedit.regions.Region "
                    + "com.sk89q.worldedit.LocalSession.getSelection(com.sk89q.worldedit.world.World)'");
            worldEdit.failures.put("selection", changed);
            PlayerMock builder = op();
            logged.clear();

            builder.performCommand("sls selection");
            assertEquals(List.of("error: " + FAILED), told(builder));
            builder.performCommand("sls packs");
            List<String> next = told(builder);
            assertEquals(1, next.size(), next.toString());
            assertTrue(next.get(0).startsWith("error: SlabSmooth is unavailable: it stopped working: NoSuchMethodError"),
                    next.get(0));
            SlabSmooth.fail(new NoSuchMethodError("another one"));

            assertEquals(1, logged.size(), "one log line: " + logged.stream().map(LogRecord::getMessage).toList());
            assertSame(changed, logged.get(0).getThrown(), "with the cause");
            assertFalse(SlabSmooth.isAvailable());
            assertNull(SlabSmooth.brushContext(), "brushes bound before do nothing");
            SlabSmooth.reload();
            assertFalse(SlabSmooth.isAvailable(), "a reload doesn't switch it on again: the cause is still there");
        } finally {
            server.getPluginManager().disablePlugin(worldEditPlugin);
        }
    }

    @Test
    void nothingStartsWhereNoWorldHasIt() {
        SlabSmooth.disable();
        await(() -> threads().isEmpty(), "the threads of an earlier test end: " + threads());

        useFakes();
        server.getScheduler().performOneTick();
        sleep(200); // a worker that was given something would have asked by now

        assertEquals(List.of(), sources.asked, "no pack is read");
        assertEquals(List.of(), threads(), "no thread is started");
    }

    @Test
    void theDefaultPackIsReadSoonAfterStartWhereAWorldHasIt() {
        PluginData.setModuleEnabled(world, Modules.SLAB_SMOOTH, true);

        useFakes();
        assertEquals(List.of(), sources.asked, "nothing is read while the server starts");
        server.getScheduler().performOneTick();

        await(() -> SlabSmooth.parts().packs().status().stream().anyMatch(line -> line.startsWith("Human: ready")),
                "the default pack is read without anyone asking: " + SlabSmooth.parts().packs().status());
        assertFalse(sources.asked.isEmpty());
        assertTrue(sources.asked.stream().allMatch(call -> call.endsWith(" on " + PACKS_WORKER)), sources.asked.toString());
    }

    // A brush's first click comes from FAWE's thread, which can't read a world's switch itself. So one tick after a start
    // or a reload, every loaded world's switch is read, not only those up to the first one that is on.
    @Test
    void oneTickAfterStartEveryLoadedWorldIsKnown() throws Exception {
        for (String name : List.of("wiring-second", "wiring-third")) {
            if (server.getWorld(name) == null) {
                WorldMock added = new WorldMock(Material.STONE, -64, 320, 0);
                added.setName(name);
                server.addWorld(added);
            }
        }
        PluginData.setModuleEnabled(world, Modules.SLAB_SMOOTH, true); // the first world the server lists

        useFakes();
        server.getScheduler().performOneTick();

        ModuleSwitch module = SlabSmooth.parts().module();
        Map<String, ModuleSwitch.Answer> known = new TreeMap<>();
        Thread elsewhere = new Thread(() -> server.getWorlds().forEach(w -> known.put(w.getName(), module.answerIn(w))),
                "fawe-like");
        elsewhere.start();
        elsewhere.join();
        assertEquals(Map.of("world", ModuleSwitch.Answer.ON, "wiring-second", ModuleSwitch.Answer.OFF,
                "wiring-third", ModuleSwitch.Answer.OFF), known);
    }

    @Test
    void reloadReadsTheSettingsAgainAndForgetsPlayerOptions() {
        useFakes();
        PlayerMock builder = op();
        builder.performCommand("sls set band 3");
        assertEquals(List.of("info: band = 3, for your selections, areas and brush."), told(builder));
        SmoothJobs jobs = SlabSmooth.jobs();
        Packs before = SlabSmooth.parts().packs();
        plugin.getConfig().set("slabSmooth.brush.maxRadius", 20);
        plugin.getConfig().set("slabSmooth.defaults.band", 1);
        plugin.saveConfig();
        try {
            server.dispatchCommand(console, "architect reload");

            assertTrue(SlabSmooth.isAvailable());
            assertEquals(20, SlabSmooth.parts().settings().maxBrushRadius(), "the settings are read again");
            assertEquals(1, SlabSmooth.parts().options().of(builder.getUniqueId()).band,
                    "the player's own band is forgotten, and the new default applies");
            builder.performCommand("sls options");
            List<String> options = told(builder);
            assertEquals(1, options.size(), options.toString());
            assertTrue(options.get(0).contains(" band=1,"), options.get(0));
            assertSame(jobs, SlabSmooth.jobs(), "the jobs stay: they hold the server-wide lock");
            assertNotSame(before, SlabSmooth.parts().packs(), "the packs are read again with the new settings");
            assertFalse(before.rebuild(FakePackSources.PACK), "the old packs are closed");
        } finally {
            plugin.getConfig().set("slabSmooth.brush", null);
            plugin.getConfig().set("slabSmooth.defaults", null);
            plugin.saveConfig();
            server.dispatchCommand(console, "architect reload");
        }
    }

    @Test
    void aReloadBeforeEnableDoesNothing() {
        SlabSmooth.disable();

        assertDoesNotThrow(SlabSmooth::reload, "loadData runs once before onEnable gets to SlabSmooth.enable");

        assertFalse(SlabSmooth.isAvailable());
        assertNull(SlabSmooth.parts());
        assertNull(SlabSmooth.jobs());
    }

    @Test
    void disableClosesTheJobsAndPacks() {
        PluginData.setModuleEnabled(world, Modules.SLAB_SMOOTH, true);
        useFakes();
        awaitTable();
        PlayerMock builder = op();
        SlabSmooth.jobs().start(builder, world, box(), null, true, new Options()); // to its end: the worker has started
        assertFalse(awaitTold(builder).isEmpty());
        assertTrue(threads().contains(JOB_WORKER), threads().toString());
        areas.hold = true;
        SlabSmooth.jobs().start(builder, world, box(), null, true, new Options()); // still reading its area
        CompletableFuture<AreaSource.LoadedArea> reading = areas.loads().getLast().future();
        Packs packs = SlabSmooth.parts().packs();
        int sets = worldEdit.sets.size();

        SlabSmooth.disable();

        assertTrue(reading.isCancelled(), "the jobs are closed: the read is given up");
        assertFalse(packs.rebuild(FakePackSources.PACK), "the packs are closed");
        assertFalse(SlabSmooth.isAvailable());
        assertNull(SlabSmooth.brushContext(), "a brush bound before does nothing");
        await(() -> threads().isEmpty(), "the job worker and the packs' worker stop: " + threads());
        server.getScheduler().performTicks(3);
        assertEquals(List.of(), told(builder), "a closed job says nothing");
        assertEquals(sets, worldEdit.sets.size(), "and writes nothing");
    }

    // Paper runs a task that a running task hands to runTask, with no delay, in the same tick, and a chunk finishes
    // loading only once a tick has ended. On the server a read waiting for its chunks, and the job watching it, so ran
    // again and again in one tick, until the watchdog stopped the server. MockBukkit runs every task a tick later at the
    // earliest, which hid it; FakeScheduler runs tasks as Paper does, and stops a tick that would never end.
    @Test
    void mainThreadExecutorsRunInALaterTick() {
        FakeScheduler paper = new FakeScheduler();
        Runnable task = () -> {
        };

        SlabSmooth.nextTick(plugin, paper.scheduler).execute(task);

        assertEquals(List.of(new FakeScheduler.Call("runTaskLater", plugin, task, 1L)), paper.calls(),
                "runTaskLater with a delay of one tick, never runTask");
    }

    @Test
    void theJobsTheSwitchAndThePrewarmRunTheirTasksInALaterTick() throws Exception {
        FakeScheduler paper = new FakeScheduler();
        PluginData.setModuleEnabled(world, Modules.SLAB_SMOOTH, true);
        areas.hold = true; // the read waits for Paper, so the job watches it every tick
        SlabSmooth.disable();

        SlabSmooth.enable(plugin, paper.scheduler, () -> new SlabSmooth.Services(sources, worldEdit, areas));
        assertEquals(1, paper.tick(), "the prewarm");
        ModuleSwitch module = SlabSmooth.parts().module();
        Thread elsewhere = new Thread(() -> module.answerIn(world), "fawe-like");
        elsewhere.start();
        elsewhere.join();
        assertEquals(1, paper.tick(), "the switch's read of the world, asked for by a brush click");
        awaitTable();
        PlayerMock builder = op();
        SlabSmooth.jobs().start(builder, world, box(), null, true, new Options());
        for (int tick = 0; tick < 3; tick++) {
            assertEquals(1, paper.tick(), "the job watches its read once a tick");
        }
        areas.finishLoading();
        await(() -> paper.waiting() == 2, "the smoothing hands its result to the main thread");
        assertEquals(2, paper.tick(), "the watch, which ends now, and the check, which writes");

        List<String> said = told(builder);
        assertEquals(1, said.size(), said.toString());
        assertTrue(said.get(0).startsWith("info: "), said.get(0));
        assertEquals(0, paper.waiting(), "the job has ended");
        assertEquals(Collections.nCopies(7, "runTaskLater for " + plugin.getName() + ", delay 1"), described(paper),
                "the prewarm, the switch's read, the watch four times and the check");
    }

    @Test
    void theChunkReaderOnTheServerRunsInALaterTick() {
        FakeScheduler paper = new FakeScheduler();
        AreaSource chunks = SlabSmooth.Services.onTheServer(plugin, paper.scheduler).areas();

        CompletableFuture<AreaSource.LoadedArea> reading = chunks.load(stillLoading(), BlockVector3.at(0, 60, 0),
                BlockVector3.at(31, 70, 31));
        for (int tick = 0; tick < 3; tick++) {
            assertEquals(1, paper.tick(), "one run a tick while Paper reads the chunks");
        }
        reading.cancel(false);
        assertEquals(1, paper.tick(), "the next run finds the read given up, and ends");

        assertEquals(0, paper.waiting());
        assertEquals(Collections.nCopies(4, "runTaskLater for " + plugin.getName() + ", delay 1"), described(paper));
    }

    @Test
    void theServerSaysWhichStatesItKnowsAndWhichHaveABlockEntity() {
        assertTrue(SlabSmooth.validState("minecraft:stone"));
        assertTrue(SlabSmooth.validState("minecraft:purpur_slab[type=top]"));
        assertFalse(SlabSmooth.validState("minecraft:no_such_block"), "false, not an exception");
        assertTrue(SlabSmooth.hasBlockEntity("minecraft:chest"));
        assertTrue(SlabSmooth.hasBlockEntity("minecraft:chest"), "asked again, from what it knows of chests");
        assertFalse(SlabSmooth.hasBlockEntity("minecraft:stone"));
        assertFalse(SlabSmooth.hasBlockEntity("minecraft:no_such_block"), "false, not an exception");
    }

    /**
     * config.yml shows the section commented out, with every key at its default: a live section there would be the
     * default of every server's config, and fill in what a server's own section leaves out.
     */
    @Test
    void theSettingsShownInConfigYmlAreTheDefaults() throws Exception {
        String text;
        try (InputStream in = ArchitectPlugin.class.getClassLoader().getResourceAsStream("config.yml")) {
            assertNotNull(in, "config.yml is not on the class path");
            text = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        YamlConfiguration whole = new YamlConfiguration();
        whole.loadFromString(text);
        assertFalse(whole.contains("slabSmooth"), "commented out");
        List<String> lines = text.lines().toList();
        int start = lines.indexOf("#slabSmooth:");
        assertTrue(start >= 0, "config.yml shows the slabSmooth section");
        StringBuilder section = new StringBuilder(lines.get(start).substring(1)).append('\n');
        for (int i = start + 1; i < lines.size() && lines.get(i).startsWith("#    "); i++) {
            section.append(lines.get(i).substring(1)).append('\n');
        }
        YamlConfiguration shown = new YamlConfiguration();
        shown.loadFromString(section.toString());
        List<String> warnings = new ArrayList<>();

        assertEquals(SlabSmoothSettings.DEFAULTS, SlabSmoothSettings.from(shown.getConfigurationSection("slabSmooth"), warnings::add));
        assertEquals(List.of(), warnings);
    }
}
