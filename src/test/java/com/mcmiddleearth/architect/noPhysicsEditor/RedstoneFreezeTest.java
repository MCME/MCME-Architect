package com.mcmiddleearth.architect.noPhysicsEditor;

import com.mcmiddleearth.architect.ArchitectPlugin;
import com.mcmiddleearth.architect.Modules;
import com.mcmiddleearth.architect.PluginData;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.event.block.BlockRedstoneEvent;
import org.bukkit.util.Vector;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.world.WorldMock;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

// Pins the server-wide redstone freeze (kept on purpose: the RP reuses redstone states as decoration).
// One mock/load per class, as Architect caches data-folder paths in static fields.
class RedstoneFreezeTest {

    private static ServerMock server;
    private static WorldMock world;

    @BeforeAll
    static void setUp() {
        server = MockBukkit.mock();
        MockBukkit.load(ArchitectPlugin.class);
        world = server.addSimpleWorld("world");
    }

    @AfterAll
    static void tearDown() {
        if (MockBukkit.isMocked()) {
            MockBukkit.unmock();
        }
    }

    // As on a server, the event fires on a redstone part: an exception area frees only the parts it lists.
    private static int currentAfter(Material part, int x, int oldCurrent, int newCurrent) {
        Block block = world.getBlockAt(x, 64, 0);
        block.setType(part);
        BlockRedstoneEvent event = new BlockRedstoneEvent(block, oldCurrent, newCurrent);
        server.getPluginManager().callEvent(event);
        return event.getNewCurrent();
    }

    @Test
    void redstoneCurrentNeverChanges() {
        for (Material part : List.of(Material.REDSTONE_WIRE, Material.REPEATER, Material.COMPARATOR,
                Material.REDSTONE_TORCH, Material.OBSERVER, Material.REDSTONE_LAMP, Material.LEVER,
                Material.OAK_DOOR)) {
            assertEquals(0, currentAfter(part, 0, 0, 15), part + ": power does not come on");
            assertEquals(15, currentAfter(part, 0, 15, 0), part + ": and does not go off");
        }
        assertEquals(3, currentAfter(Material.REDSTONE_WIRE, 0, 3, 4), "nor moves between wire levels");

        for (Modules module : Modules.values()) {
            PluginData.setModuleEnabled(world, module, false);
        }
        try {
            assertEquals(0, currentAfter(Material.REPEATER, 0, 0, 15), "whatever the world's modules say");
        } finally {
            for (Modules module : Modules.values()) {
                PluginData.setModuleEnabled(world, module, true);
            }
        }
    }

    @Test
    void notEvenInsideARedstoneExceptionArea() {
        NoPhysicsData.getExceptionAreas().put("Mill",
                new RedstoneCircuitArea(world.getUID(), new Vector(90, 0, -10), new Vector(110, 255, 10)));
        try {
            assertEquals(0, currentAfter(Material.REDSTONE_WIRE, 100, 0, 15),
                    "the area frees block physics, not power");
            assertTrue(NoPhysicsData.hasNoPhysicsException(world.getBlockAt(100, 64, 0)),
                    "the wire is one of the parts the area frees");
        } finally {
            NoPhysicsData.getExceptionAreas().remove("Mill");
        }
    }
}
