package com.mcmiddleearth.architect.mapLayers.customBiomes;

import com.mcmiddleearth.architect.ArchitectPlugin;
import com.mcmiddleearth.architect.Permission;
import com.mcmiddleearth.architect.entityLogging.FakeDynmap;
import org.bukkit.command.Command;
import org.dynmap.markers.MarkerSet;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.command.ConsoleCommandSenderMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

// The layer in Architect, end to end: registered from config.yml, answering /architect maplayers scan for those with
// the map-layers permission, and stopped with Architect. One mock/load per class, as Architect caches data-folder
// paths in static fields. The fake dynmap is loaded after Architect, as on the server.
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class CustomBiomesWiringTest {

    private static ServerMock server;
    private static ArchitectPlugin plugin;
    private static FakeDynmap dynmap;
    private static ConsoleCommandSenderMock console;

    @BeforeAll
    static void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.load(ArchitectPlugin.class);
        dynmap = MockBukkit.loadWith(FakeDynmap.class, FakeDynmap.description());
        server.addSimpleWorld("world");
        server.getScheduler().performOneTick();
        console = server.getConsoleSender();
    }

    @AfterAll
    static void tearDown() {
        if (MockBukkit.isMocked()) {
            MockBukkit.unmock();
        }
    }

    private static MarkerSet set() {
        return dynmap.getMarkerAPI().getMarkerSet(CustomBiomes.ID);
    }

    /** Everything said to the console since the last call. */
    private static List<String> replies() {
        List<String> lines = new ArrayList<>();
        for (String line = console.nextMessage(); line != null; line = console.nextMessage()) {
            lines.add(line);
        }
        return lines;
    }

    private static List<String> complete(org.bukkit.command.CommandSender sender, String... args) {
        Command architect = server.getCommandMap().getCommand("architect");
        return architect.tabComplete(sender, "architect", args);
    }

    @Test
    @Order(1)
    void theLayerIsRegisteredFromConfigAndStartsHidden() {
        assertNotNull(set(), "on by default");
        assertTrue(set().getHideByDefault(), "hidden by default");
        assertEquals("Custom biomes", set().getMarkerSetLabel());
        assertTrue(CustomBiomes.active().isPresent());
    }

    @Test
    @Order(2)
    void theScanCommandNeedsTheMapLayersPermission() {
        PlayerMock builder = server.addPlayer();
        builder.performCommand("architect maplayers scan world");
        assertTrue(builder.nextMessage().contains("Sorry, you don't have permission."));
        replies();
        server.dispatchCommand(console, "architect maplayers scan");
        List<String> lines = replies();
        assertEquals(1, lines.size(), lines.toString());
        assertTrue(lines.get(0).endsWith("No world is on the Custom biomes layer. Start one with /architect maplayers "
                + "scan <world>."), "the builder put no world on: " + lines.get(0));

        PlayerMock mapper = server.addPlayer();
        mapper.addAttachment(plugin, Permission.MAP_LAYERS.getPermissionNode(), true);
        mapper.performCommand("architect maplayers scan");
        assertTrue(mapper.nextMessage().contains("No world is on the Custom biomes layer."),
                "architect.maplayers alone is enough");
        mapper.performCommand("architect maplayers");
        assertTrue(mapper.nextMessage().contains("Usage: /architect maplayers refresh | scan [<world> [off]]"));
    }

    @Test
    @Order(3)
    void tabCompletionOffersScanWorldsAndOff() {
        assertEquals(List.of("refresh", "scan"), complete(console, "maplayers", ""));
        assertEquals(List.of("scan"), complete(console, "maplayers", "s"));
        assertTrue(complete(console, "maplayers", "scan", "").contains("world"));
        assertEquals(List.of("world"), complete(console, "maplayers", "scan", "wor"));
        assertEquals(List.of("off"), complete(console, "maplayers", "scan", "world", ""));
        assertEquals(List.of(), complete(console, "maplayers", "refresh", ""));
        assertEquals(List.of(), complete(server.addPlayer(), "maplayers", ""), "only for those who may use it");
    }

    @Test
    @Order(4)
    void aDisabledLayerAnswersTheScanCommandWithWhy() {
        plugin.getConfig().set("mapLayers.customBiomes", false);
        plugin.saveConfig();
        try {
            server.dispatchCommand(console, "architect reload");
            server.getScheduler().performOneTick();
            replies();

            server.dispatchCommand(console, "architect maplayers scan world");

            List<String> lines = replies();
            assertEquals(1, lines.size(), lines.toString());
            assertTrue(lines.get(0).endsWith("The Custom biomes layer is not running: mapLayers.customBiomes is "
                    + "switched off in config.yml, or Architect's map layers are not running."), lines.get(0));
            assertNull(set(), "taken off the map");
            assertEquals(List.of(), complete(console, "maplayers", "scan", ""), "nothing to offer");
        } finally {
            plugin.getConfig().set("mapLayers.customBiomes", null);
            plugin.saveConfig();
            server.dispatchCommand(console, "architect reload");
            server.getScheduler().performOneTick();
        }
        assertTrue(CustomBiomes.active().isPresent(), "on again");
        assertNotNull(set());
    }

    @Test
    @Order(5)
    void disablingArchitectStopsTheLayer() {
        server.getPluginManager().disablePlugin(plugin);

        assertTrue(CustomBiomes.active().isEmpty());
        assertNull(set());
    }
}
