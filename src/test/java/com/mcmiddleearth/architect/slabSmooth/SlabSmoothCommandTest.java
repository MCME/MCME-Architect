package com.mcmiddleearth.architect.slabSmooth;

import com.mcmiddleearth.architect.ArchitectPlugin;
import com.mcmiddleearth.architect.Modules;
import com.mcmiddleearth.architect.Permission;
import com.mcmiddleearth.architect.PluginData;
import com.mcmiddleearth.architect.slabSmooth.core.Options;
import com.mcmiddleearth.architect.slabSmooth.core.Tiles;
import com.mcmiddleearth.pluginutil.message.MessageUtil;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.math.Vector3;
import com.sk89q.worldedit.regions.CuboidRegion;
import com.sk89q.worldedit.regions.EllipsoidRegion;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.inventory.ItemStack;
import org.bukkit.permissions.PermissionAttachment;
import org.bukkit.plugin.PluginDescriptionFile;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.command.ConsoleCommandSenderMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockbukkit.mockbukkit.world.WorldMock;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.mcmiddleearth.architect.Permission.SLAB_SMOOTH;
import static com.mcmiddleearth.architect.Permission.SLAB_SMOOTH_ADMIN;
import static com.mcmiddleearth.architect.Permission.SLAB_SMOOTH_ALL_BLOCKS;
import static com.mcmiddleearth.architect.Permission.SLAB_SMOOTH_AREA;
import static com.mcmiddleearth.architect.Permission.SLAB_SMOOTH_BRUSH;
import static com.mcmiddleearth.architect.Permission.SLAB_SMOOTH_SELECTION;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.junit.jupiter.params.provider.Arguments.arguments;

/**
 * /slabsmooth (/sls) on MockBukkit, with FakeSmoothWorldEdit, FakeAreaSource and the tiny real pack of
 * FakePackSources. A mock plugin named WorldEdit is enabled, so the edit commands find WorldEdit. SlabSmooth is on in
 * world and off in other. The terrain is bricks up to y 60, and up to 61 from x 5 on: one step, which only blocks all
 * smooths, since bricks aren't terrain in the pack. One mock and load per class: Architect keeps its data folder in
 * static fields.
 */
class SlabSmoothCommandTest {

    private static final String NO_PERMISSION = "error: Sorry, you don't have permission.";
    private static final String HOLD = "error: Hold a tool or another item that isn't a block, such as a feather or a "
            + "bone, to bind the brush to.";
    private static final String RADIUS = "error: The radius must be a number from 1 to 12.";
    private static final Set<Permission> NODES = EnumSet.of(SLAB_SMOOTH, SLAB_SMOOTH_SELECTION, SLAB_SMOOTH_BRUSH,
            SLAB_SMOOTH_AREA, SLAB_SMOOTH_ALL_BLOCKS, SLAB_SMOOTH_ADMIN);

    @TempDir
    static Path dir;
    private static ServerMock server;
    private static ArchitectPlugin plugin;
    private static WorldMock world, other;
    private static FakePackSources sources;
    private static final List<String> tellraw = new ArrayList<>();

    private FakeSmoothWorldEdit worldEdit;
    private FakeAreaSource areas;
    private ConsoleCommandSenderMock console;

    @BeforeAll
    static void startTheServer() throws IOException {
        server = MockBukkit.mock();
        plugin = MockBukkit.load(ArchitectPlugin.class);
        // PluginUtils sends the lines of /architect help as /tellraw from the console. MockBukkit has none.
        server.getCommandMap().register("minecraft", new Command("tellraw") {
            @Override
            public boolean execute(CommandSender sender, String label, String[] args) {
                tellraw.add(String.join(" ", args));
                return true;
            }
        });
        world = addWorld("world");
        other = addWorld("other");
        MockBukkit.createMockPlugin("WorldEdit");
        sources = new FakePackSources(dir, plugin.getDataFolder().toPath());
        plugin.getConfig().set("slabSmooth.download", false);
        plugin.getConfig().set("slabSmooth.vanilla.clientJar", sources.clientJar.toString());
        PluginData.setModuleEnabled(world, Modules.SLAB_SMOOTH, true);
    }

    @AfterAll
    static void stopTheServer() {
        if (MockBukkit.isMocked()) {
            MockBukkit.unmock();
        }
    }

    private static WorldMock addWorld(String name) {
        WorldMock added = new WorldMock(Material.STONE, -64, 320, 0);
        added.setName(name);
        server.addWorld(added);
        return added;
    }

    @BeforeEach
    void freshFakes() {
        worldEdit = new FakeSmoothWorldEdit();
        worldEdit.base = SlabSmoothCommandTest::terrain;
        areas = new FakeAreaSource(worldEdit::stateAt);
        SlabSmooth.disable();
        SlabSmooth.enable(plugin, () -> new SlabSmooth.Services(sources, worldEdit, areas));
        console = server.getConsoleSender();
        while (console.nextMessage() != null) {
            // start with an empty console
        }
        tellraw.clear();
    }

    /** A read a test started (packs rebuild) would be cut short by the next test's disable, with a warning. */
    @AfterEach
    void letThePacksFinishReading() {
        SlabSmooth.Parts parts = SlabSmooth.parts();
        if (parts != null) {
            await(() -> parts.packs().status().stream().noneMatch(line -> line.contains("reading...")),
                    "the packs are read: " + parts.packs().status());
        }
    }

    private static String terrain(BlockVector3 p) {
        return p.y() <= (p.x() < 5 ? 60 : 61) ? "minecraft:bricks" : "minecraft:air";
    }

    private static CuboidRegion box() {
        return new CuboidRegion(BlockVector3.at(0, 55, 0), BlockVector3.at(9, 65, 9));
    }

    /** A player in a world, with these nodes and no others. */
    private static PlayerMock player(World in, Permission... nodes) {
        PlayerMock player = server.addPlayer();
        player.teleport(new Location(in, 4, 70, 4));
        for (Permission node : nodes) {
            player.addAttachment(plugin, node.getPermissionNode(), true);
        }
        return player;
    }

    private static PlayerMock holding(PlayerMock player, Material item) {
        player.getInventory().setItemInMainHand(new ItemStack(item));
        return player;
    }

    private static Options options(PlayerMock player) {
        return SlabSmooth.parts().options().of(player.getUniqueId());
    }

    /** What a player was told since last asked: "info: ..." or "error: ...", without Architect's prefix or indent. */
    private static List<String> told(PlayerMock player) {
        MessageUtil util = PluginData.getMessageUtil();
        String error = String.valueOf(util.ERROR), info = String.valueOf(util.INFO);
        List<String> out = new ArrayList<>();
        for (String message = player.nextMessage(); message != null; message = player.nextMessage()) {
            String kind = message.startsWith(error) ? "error: " : message.startsWith(info) ? "info: " : "unknown: ";
            String text = message.replaceAll(ChatColor.COLOR_CHAR + ".", "");
            for (String start : List.of(util.getPREFIX(), util.getNOPREFIX())) {
                if (!start.isEmpty() && text.startsWith(start)) {
                    text = text.substring(start.length());
                    break;
                }
            }
            out.add(kind + text);
        }
        return out;
    }

    /** What a command answered at once. */
    private static List<String> answer(PlayerMock player, String command) {
        told(player);
        player.performCommand(command);
        return told(player);
    }

    /** Runs ticks, and lets the job's worker run, until the player is told something; that is the one thing said. */
    private static String awaitToldOnce(PlayerMock player) {
        long end = System.nanoTime() + TimeUnit.SECONDS.toNanos(20);
        while (System.nanoTime() < end) {
            server.getScheduler().performOneTick();
            List<String> lines = told(player);
            if (!lines.isEmpty()) {
                assertEquals(1, lines.size(), lines.toString());
                return lines.get(0);
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

    /** Asks for the pack's tables until they are there: the first asks only start reading it. */
    private static void awaitTables() {
        await(() -> {
            try {
                Packs packs = SlabSmooth.parts().packs();
                Packs.Choice human = new Packs.Choice(FakePackSources.PACK, "default");
                return packs.table(human, false) != null && packs.table(human, true) != null;
            } catch (Refusal notYet) {
                return false;
            }
        }, "the tables of the tiny pack: " + SlabSmooth.parts().packs().status());
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    private static List<String> complete(CommandSender sender, String... args) {
        return server.getCommandMap().getCommand("slabsmooth").tabComplete(sender, "sls", args);
    }

    // ---------------------------------------------------------------------------------------------------------
    // Permissions (Rule 10)

    static Stream<Arguments> subcommands() {
        return Stream.of(
                arguments("sls selection", SLAB_SMOOTH_SELECTION),
                arguments("sls sel", SLAB_SMOOTH_SELECTION),
                arguments("sls brush", SLAB_SMOOTH_BRUSH),
                arguments("sls area 0 999 0 9 999 9", SLAB_SMOOTH_AREA), // refused for its height, after the permission
                arguments("sls packs rebuild", SLAB_SMOOTH_ADMIN),
                arguments("sls set band 3", SLAB_SMOOTH),
                arguments("sls options", SLAB_SMOOTH),
                arguments("sls reset", SLAB_SMOOTH),
                arguments("sls pack", SLAB_SMOOTH),
                arguments("sls packs", SLAB_SMOOTH),
                arguments("sls help", SLAB_SMOOTH),
                arguments("sls", SLAB_SMOOTH));
    }

    @ParameterizedTest
    @MethodSource("subcommands")
    void everySubcommandChecksItsPermission(String command, Permission needed) {
        assertEquals(List.of(NO_PERMISSION), answer(player(world), command), "without any node");

        Set<Permission> others = EnumSet.copyOf(NODES);
        others.remove(needed);
        if (needed == SLAB_SMOOTH_SELECTION || needed == SLAB_SMOOTH_BRUSH) {
            others.remove(SLAB_SMOOTH); // which includes them
        }
        assertEquals(List.of(NO_PERMISSION), answer(player(world, others.toArray(Permission[]::new)), command),
                "with every node but " + needed);

        List<String> allowed = answer(player(world, needed), command);
        assertFalse(allowed.isEmpty(), "an answer");
        assertFalse(allowed.contains(NO_PERMISSION), "with " + needed + ": " + allowed);
    }

    @Test
    void theBaseNodeIncludesSelectionAndBrushButNotAllBlocks() throws Exception {
        PluginDescriptionFile description;
        try (InputStream in = ArchitectPlugin.class.getClassLoader().getResourceAsStream("plugin.yml")) {
            assertNotNull(in, "plugin.yml is not on the class path");
            description = new PluginDescriptionFile(in);
        }
        org.bukkit.permissions.Permission base = description.getPermissions().stream()
                .filter(p -> p.getName().equalsIgnoreCase(SLAB_SMOOTH.getPermissionNode())).findFirst().orElseThrow();
        Map<String, Boolean> children = base.getChildren().entrySet().stream()
                .collect(Collectors.toMap(e -> e.getKey().toLowerCase(Locale.ROOT), Map.Entry::getValue, (a, b) -> a, TreeMap::new));
        assertEquals(Map.of("architect.slabsmooth.brush", true, "architect.slabsmooth.selection", true), children);

        PlayerMock builder = player(world, SLAB_SMOOTH);
        assertTrue(PluginData.hasPermission(builder, SLAB_SMOOTH_SELECTION));
        assertTrue(PluginData.hasPermission(builder, SLAB_SMOOTH_BRUSH));
        assertFalse(PluginData.hasPermission(builder, SLAB_SMOOTH_ALL_BLOCKS));
        assertFalse(PluginData.hasPermission(builder, SLAB_SMOOTH_AREA));
        assertFalse(PluginData.hasPermission(builder, SLAB_SMOOTH_ADMIN));
    }

    @Test
    void setBlocksAllNeedsItsOwnPermission() {
        PlayerMock builder = player(world, SLAB_SMOOTH);

        assertEquals(List.of("error: blocks all needs a permission of its own; blocks terrain is yours to use."),
                answer(builder, "sls set blocks all"));
        assertEquals("terrain", options(builder).blocks, "nothing changed");

        PermissionAttachment all = builder.addAttachment(plugin, SLAB_SMOOTH_ALL_BLOCKS.getPermissionNode(), true);
        assertEquals(List.of("info: blocks = all, for your selections, areas and brush."), answer(builder, "sls set blocks all"));
        assertEquals("all", options(builder).blocks);

        builder.removeAttachment(all);
        assertEquals(List.of("info: blocks = terrain, for your selections, areas and brush."),
                answer(builder, "sls set blocks terrain"), "terrain needs no more than the command");
    }

    @Test
    void aRevokedAllBlocksPermissionIsIgnoredAtStart() {
        awaitTables();
        PlayerMock builder = player(world, SLAB_SMOOTH);
        PermissionAttachment all = builder.addAttachment(plugin, SLAB_SMOOTH_ALL_BLOCKS.getPermissionNode(), true);
        answer(builder, "sls set blocks all");
        worldEdit.selection = box();

        builder.performCommand("sls selection");
        String withIt = awaitToldOnce(builder);
        assertTrue(withIt.startsWith("info: Smoothed "), "with the permission, blocks all smooths the bricks: " + withIt);
        assertFalse(worldEdit.sets.isEmpty());

        worldEdit.blocks.clear(); // the step as it was
        worldEdit.sets.clear();
        builder.removeAttachment(all);
        builder.performCommand("sls selection");
        String without = awaitToldOnce(builder);

        assertTrue(without.startsWith("info: Nothing to change: 0 surfaces could change, none needed to."),
                "taken away, the job smooths terrain only, and bricks aren't terrain: " + without);
        assertEquals(Map.of(), worldEdit.sets);
        assertEquals("all", options(builder).blocks, "the player's setting itself stays");
    }

    // ---------------------------------------------------------------------------------------------------------
    // The module, the console and the area

    @Test
    void theModuleOffInAWorldAnswersThatItIsOff() {
        worldEdit.selection = box();
        PlayerMock there = holding(player(other, SLAB_SMOOTH, SLAB_SMOOTH_AREA), Material.STICK);

        assertEquals(List.of("error: SlabSmooth is off in this world."), answer(there, "sls selection"));
        assertEquals(List.of("error: SlabSmooth is off in this world."), answer(there, "sls brush 4"));
        assertEquals(List.of(), worldEdit.brushes, "no brush is bound");
        assertEquals(List.of("error: SlabSmooth is off in other."), answer(there, "sls area 0 55 0 9 65 9"));
        List<String> elsewhere = answer(there, "sls area 0 55 0 9 65 9 world Nonsense");
        assertTrue(elsewhere.size() == 1 && elsewhere.get(0).startsWith("error: No pack Nonsense"),
                "the named world's switch is the one that counts, and in world it is on: " + elsewhere);

        PlayerMock here = player(world, SLAB_SMOOTH_AREA);
        assertEquals(List.of("error: SlabSmooth is off in other."), answer(here, "sls area 0 55 0 9 65 9 other"));
        assertEquals(List.of(), areas.loads(), "nothing was read");
    }

    @Test
    void theConsoleCannotUseArea() {
        server.dispatchCommand(console, "sls area 0 55 0 9 65 9 world");

        String reply = console.nextMessage();
        assertNotNull(reply);
        assertTrue(reply.contains("Only a player can smooth an area, so that //undo can revert it."), reply);
        assertNull(console.nextMessage());
        assertEquals(List.of(), areas.loads(), "nothing was read");
    }

    @Test
    void anAreaIsReadInItsNamedWorldWhereverThePlayerStands() {
        awaitTables();
        PlayerMock staff = player(other, SLAB_SMOOTH_AREA);

        staff.performCommand("sls area 9 65 9 0 55 0 world Human");
        String summary = awaitToldOnce(staff);

        assertTrue(summary.startsWith("info: Nothing to change: 0 surfaces could change") && summary.contains(" [Human pack] "),
                summary);
        assertEquals(1, areas.loads().size());
        FakeAreaSource.Load load = areas.loads().get(0);
        assertSame(world, load.world(), "the world it names, not the one the player is in");
        int margin = Tiles.margin(new Options());
        assertEquals(BlockVector3.at(-margin, 55 - margin, -margin), load.min(), "the corners in any order, and the margin");
        assertEquals(BlockVector3.at(9 + margin, 65 + margin, 9 + margin), load.max());
    }

    @Test
    void anAreaMustBeInsideTheWorld() {
        PlayerMock staff = player(world, SLAB_SMOOTH_AREA);
        for (String corners : List.of("30000001 55 0 9 65 9", "0 55 0 9 65 -30000001", "2147483647 55 0 9 65 9")) {
            assertEquals(List.of("error: x and z must be between -30000000 and 30000000."),
                    answer(staff, "sls area " + corners), corners);
        }
        for (String corners : List.of("0 -65 0 9 65 9", "0 55 0 9 320 9")) {
            assertEquals(List.of("error: y must be between -64 and 319 in world."), answer(staff, "sls area " + corners), corners);
        }
        assertEquals(List.of("error: Coordinates are whole numbers; sixty isn't one."), answer(staff, "sls area 0 sixty 0 9 65 9"));
        assertEquals(List.of("error: Usage: /sls area <x1> <y1> <z1> <x2> <y2> <z2> [world] [pack]"),
                answer(staff, "sls area 0 55 0 9 65"));
        assertEquals(List.of("error: There is no world named Nowhere."), answer(staff, "sls area 0 55 0 9 65 9 Nowhere"));
        assertEquals(List.of(), areas.loads(), "nothing was read");
    }

    // ---------------------------------------------------------------------------------------------------------
    // Tab completion and help

    @Test
    void tabCompletionListsOnlyWhatThePlayerMayUse() {
        assertEquals(List.of(), complete(player(world), ""), "no node, nothing");

        PlayerMock builder = player(world, SLAB_SMOOTH);
        assertEquals(List.of("selection", "brush", "set", "options", "reset", "pack", "packs", "help"), complete(builder, ""));
        assertEquals(List.of("selection", "set"), complete(builder, "se"));
        assertEquals(Options.NAMES, complete(builder, "set", ""));
        assertEquals(List.of("terrain"), complete(builder, "set", "blocks", ""), "all needs its own node");
        assertEquals(List.of("cut"), complete(builder, "set", "mode", "c"));
        assertEquals(List.of("Human", "auto"), complete(builder, "pack", ""));
        assertEquals(List.of(), complete(builder, "packs", ""), "rebuild is for admins");
        assertEquals(List.of(), complete(builder, "area", "0", "55", "0", "9", "65", "9", ""), "area isn't theirs");

        PlayerMock staff = player(world, SLAB_SMOOTH, SLAB_SMOOTH_AREA, SLAB_SMOOTH_ALL_BLOCKS, SLAB_SMOOTH_ADMIN);
        assertEquals(List.of("selection", "brush", "area", "set", "options", "reset", "pack", "packs", "help"),
                complete(staff, ""));
        assertEquals(List.of("terrain", "all"), complete(staff, "set", "blocks", ""));
        assertEquals(List.of("rebuild"), complete(staff, "packs", ""));
        assertEquals(List.of("Human"), complete(staff, "packs", "rebuild", ""));
        assertEquals(Set.of("world", "other"), Set.copyOf(complete(staff, "area", "0", "55", "0", "9", "65", "9", "")));
        assertEquals(List.of("world"), complete(staff, "area", "0", "55", "0", "9", "65", "9", "wo"));
        assertEquals(List.of("Human"), complete(staff, "area", "0", "55", "0", "9", "65", "9", "world", ""));
    }

    @Test
    void theHelpListsWhatThePlayerMayUse() {
        String builder = String.join("\n", answer(player(world, SLAB_SMOOTH), "slabsmooth"));
        assertTrue(builder.contains("/slabsmooth selection: ") && builder.contains("/slabsmooth brush [radius]: "), builder);
        assertTrue(builder.contains("also where it is part of a building: near buildings, use //mask"),
                "it says that buildings of terrain blocks are smoothed too: " + builder);
        assertFalse(builder.contains(" area ") || builder.contains("packs rebuild"), builder);

        String staff = String.join("\n", answer(player(world, SLAB_SMOOTH, SLAB_SMOOTH_AREA, SLAB_SMOOTH_ADMIN), "sls help"));
        assertTrue(staff.contains("/sls area <x1> <y1> <z1> <x2> <y2> <z2> [world] [pack]: ") && staff.contains("/sls packs rebuild"),
                staff);

        PlayerMock settingsOnly = player(world, SLAB_SMOOTH);
        settingsOnly.addAttachment(plugin, SLAB_SMOOTH_SELECTION.getPermissionNode(), false);
        settingsOnly.addAttachment(plugin, SLAB_SMOOTH_BRUSH.getPermissionNode(), false);
        String settings = String.join("\n", answer(settingsOnly, "sls"));
        assertTrue(settings.contains("/sls set <option> <value>, options, reset: "), settings);
        assertFalse(settings.contains("selection:") || settings.contains("part of a building"),
                "no edits, so neither they nor the caution: " + settings);
    }

    @Test
    void itShowsInArchitectHelp() {
        assertTrue(ArchitectPlugin.getCommandList().contains("slabsmooth"));

        assertTrue(helpLines(player(world, SLAB_SMOOTH)).stream().anyMatch(line -> line.contains("/slabsmooth")),
                "listed for those who may use it");
        assertTrue(helpLines(player(world)).stream().noneMatch(line -> line.contains("/slabsmooth")),
                "and only for them");
    }

    /** Every page of /architect help, as the JSON of its clickable lines. */
    private static List<String> helpLines(PlayerMock player) {
        List<String> lines = new ArrayList<>();
        for (int page = 1; page <= 6; page++) {
            tellraw.clear();
            player.performCommand("architect help " + page);
            lines.addAll(tellraw);
            told(player);
        }
        return lines;
    }

    // ---------------------------------------------------------------------------------------------------------
    // Packs

    @Test
    void aPackThatDoesNotExistIsRefusedByPack() {
        PlayerMock builder = player(world, SLAB_SMOOTH);

        assertEquals(List.of("error: No pack Nonsense; packs: Human."), answer(builder, "sls pack Nonsense"));
        assertNull(SlabSmooth.parts().options().namedPack(builder.getUniqueId()), "still automatic");

        assertEquals(List.of("info: Pack: Human. Here: Human (set)."), answer(builder, "sls pack human"));
        assertEquals("Human", SlabSmooth.parts().options().namedPack(builder.getUniqueId()), "by its own name");
        assertEquals(List.of("info: Pack: automatic (the RP region's, else your pack, else Human). Here: Human (default)."),
                answer(builder, "sls pack auto"));
        assertNull(SlabSmooth.parts().options().namedPack(builder.getUniqueId()));
    }

    @Test
    void packsRebuildSaysSoWhenACheckIsRunningAlready() throws Exception {
        PlayerMock admin = player(world, SLAB_SMOOTH, SLAB_SMOOTH_ADMIN);
        sources.entered = new CountDownLatch(1);
        sources.gate = new CountDownLatch(1);
        try {
            assertEquals(List.of("info: Reading Human again; /sls packs shows how it goes."), answer(admin, "sls packs rebuild"));
            assertTrue(sources.entered.await(20, TimeUnit.SECONDS), "the check has begun");

            assertEquals(List.of("error: Human is being checked already; try again in a moment."),
                    answer(admin, "sls packs rebuild Human"));
        } finally {
            sources.gate.countDown();
            sources.gate = null;
        }
        List<String> packs = answer(admin, "sls packs");
        assertEquals("info: Default pack: Human. The packs and their tables:", packs.get(0));
        assertTrue(packs.size() == 2 && packs.get(1).startsWith("info: Human: "), packs.toString());
    }

    // ---------------------------------------------------------------------------------------------------------
    // Failures

    static Stream<Arguments> unexpectedFailures() {
        return Stream.of(arguments(new RuntimeException("test: a failure")),
                arguments(new IllegalArgumentException("test: words for the log, not for a player")),
                arguments(new IllegalStateException("test: words for the log, not for a player")),
                arguments(new Refusal(null))); // a refusal must say why; without words it is a mistake
    }

    // Anything but SlabSmooth's own refusals, such as an exception from WorldEdit or Bukkit: the player is told that it
    // went wrong, and the log gets it with its stack trace.
    @ParameterizedTest
    @MethodSource("unexpectedFailures")
    void anUnexpectedFailureIsLoggedAndNotShown(RuntimeException failure) {
        worldEdit.failures.put("selection", failure);
        PlayerMock builder = player(world, SLAB_SMOOTH);
        List<LogRecord> logged = Collections.synchronizedList(new ArrayList<>());
        Handler handler = new Handler() {
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
        plugin.getLogger().addHandler(handler);
        try {
            assertEquals(List.of("error: Something went wrong; the server log has the details."), answer(builder, "sls selection"));
        } finally {
            plugin.getLogger().removeHandler(handler);
        }
        assertEquals(1, logged.stream().filter(r -> r.getThrown() == failure && r.getLevel() == Level.WARNING).count(),
                "one log line, with the stack trace: " + logged.stream().map(r -> r.getLevel() + " " + r.getMessage()).toList());
    }

    @Test
    void theJobsOwnRefusalsReachThePlayerAsTheyAre() {
        PlayerMock builder = player(world, SLAB_SMOOTH);
        answer(builder, "sls set surfaces top");
        worldEdit.selection = new EllipsoidRegion(BlockVector3.at(4, 60, 4), Vector3.at(4, 4, 4));

        assertEquals(List.of("error: With surfaces top, select a box, polygon or cylinder."), answer(builder, "sls selection"));
        assertEquals(List.of(), areas.loads());
    }

    // ---------------------------------------------------------------------------------------------------------
    // The brush

    @Test
    void theBrushNeedsANonBlockItem() {
        PlayerMock builder = player(world, SLAB_SMOOTH);

        assertEquals(List.of(HOLD), answer(builder, "sls brush"), "an empty hand");
        assertEquals(List.of(HOLD), answer(holding(builder, Material.STONE), "sls brush"), "a block");
        assertEquals(List.of(), worldEdit.brushes);

        assertEquals(List.of("info: SlabSmooth brush (radius 5) bound to stick. Right-click terrain to smooth it; "
                + "//mask and //undo work as usual."), answer(holding(builder, Material.STICK), "sls brush"));
        assertEquals(1, worldEdit.brushes.size());
    }

    @Test
    void brushRefusesTheWorldEditWand() {
        worldEdit.wandItem = Material.WOODEN_AXE;
        PlayerMock builder = holding(player(world, SLAB_SMOOTH), Material.WOODEN_AXE);

        assertEquals(List.of("error: That's your WorldEdit wand; hold another item that isn't a block, such as a "
                + "feather, to bind the brush to."), answer(builder, "sls brush"));
        assertEquals(List.of(), worldEdit.calls("bindBrush"), "WorldEdit's sessions are never asked");
        assertEquals(List.of(), worldEdit.brushes);
    }

    @Test
    void brushRefusesTheWorldEditNavigationWand() {
        worldEdit.navigationWandItem = Material.COMPASS;
        PlayerMock builder = holding(player(world, SLAB_SMOOTH), Material.COMPASS);

        assertEquals(List.of("error: That's your WorldEdit navigation wand; hold another item that isn't a block, "
                + "such as a feather, to bind the brush to."), answer(builder, "sls brush"));
        assertEquals(List.of(), worldEdit.calls("bindBrush"), "WorldEdit's sessions are never asked");
    }

    // /tool selwand and /tool navwand move a wand to another item: the player's session knows, the configuration doesn't
    @Test
    void brushRefusesTheWandsTheSessionHasOnTheItem() {
        worldEdit.sessionWands.put(Material.BLAZE_ROD, "WorldEdit wand");
        worldEdit.sessionWands.put(Material.ARROW, "WorldEdit navigation wand");
        PlayerMock builder = player(world, SLAB_SMOOTH);

        assertEquals(List.of("error: That's your WorldEdit wand; hold another item that isn't a block, such as a "
                + "feather, to bind the brush to."), answer(holding(builder, Material.BLAZE_ROD), "sls brush"));
        assertEquals(List.of("error: That's your WorldEdit navigation wand; hold another item that isn't a block, "
                + "such as a feather, to bind the brush to."), answer(holding(builder, Material.ARROW), "sls brush"));
        assertEquals(2, worldEdit.calls("boundWand").size(), "the session was asked about each item");
        assertEquals(List.of(), worldEdit.calls("bindBrush"), "and nothing was bound");
    }

    @Test
    void anotherItemStillBindsBesideTheWands() {
        worldEdit.wandItem = Material.WOODEN_AXE;
        worldEdit.navigationWandItem = Material.COMPASS;
        worldEdit.sessionWands.put(Material.BLAZE_ROD, "WorldEdit wand");

        answer(holding(player(world, SLAB_SMOOTH), Material.FEATHER), "sls brush");
        assertEquals(1, worldEdit.brushes.size());
    }

    @Test
    void theRadiusMustBeBetweenOneAndTheLimit() {
        PlayerMock builder = holding(player(world, SLAB_SMOOTH), Material.STICK);
        for (String radius : List.of("0", "0.5", "13", "-1", "NaN", "Infinity", "five")) {
            assertEquals(List.of(RADIUS), answer(builder, "sls brush " + radius), radius);
        }
        assertEquals(List.of(), worldEdit.brushes);

        answer(builder, "sls brush 12");
        answer(builder, "sls brush 1");
        answer(builder, "sls brush 2.5");
        assertEquals(List.of(12.0, 1.0, 2.5), worldEdit.brushes.stream().map(FakeSmoothWorldEdit.Bound::radius).toList());
    }

    @Test
    void theBrushNodeIsCheckedAtEveryClick() {
        PlayerMock builder = holding(player(world, SLAB_SMOOTH), Material.STICK);

        answer(builder, "sls brush 4");

        assertEquals(1, worldEdit.brushes.size());
        FakeSmoothWorldEdit.Bound bound = worldEdit.brushes.get(0);
        assertEquals("architect.slabSmooth.brush", bound.permission(), "WorldEdit checks this node at every click");
        assertSame(builder, bound.player());
        assertEquals(4.0, bound.radius());
        assertInstanceOf(SmoothBrush.class, bound.brush());
    }

    @Test
    void aBoundBrushFollowsSlabSmooth() {
        awaitTables();
        PlayerMock builder = holding(player(world, SLAB_SMOOTH), Material.STICK);
        answer(builder, "sls brush 4");
        SmoothBrush brush = (SmoothBrush) worldEdit.brushes.get(0).brush();
        assertNotNull(brush.plan(world, 4, 61, 4, 4), "it smooths with SlabSmooth's packs and settings");

        SlabSmooth.disable();

        assertNull(brush.plan(world, 4, 61, 4, 4), "after a disable, a click does nothing");
        assertNull(builder.nextActionBar(), "and says nothing");
    }
}
