package com.mcmiddleearth.architect;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.inventory.InventoryType;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.inventory.InventoryMock;
import org.mockbukkit.mockbukkit.world.WorldMock;

import java.io.File;

import static org.junit.jupiter.api.Assertions.*;

// A new defaultWorldConfig.yml protects smokers and blast furnaces as it protects furnaces: only builders open them.
// The default file is written fresh here. One mock/load per class.
class InventoryAccessDefaultsTest {

    private static WorldMock world;

    @BeforeAll
    static void setUp() {
        ServerMock server = MockBukkit.mock();
        MockBukkit.load(ArchitectPlugin.class);
        world = server.addSimpleWorld("world");
    }

    @AfterAll
    static void tearDown() {
        if (MockBukkit.isMocked()) {
            MockBukkit.unmock();
        }
    }

    @Test
    void smokersAndBlastFurnacesNeedBuildPermissionByDefault() {
        WorldConfig config = PluginData.getOrCreateWorldConfig(world.getName());

        // MockBukkit's smoker and blast furnace inventories report FURNACE: ask with inventories of the real types
        assertEquals(InventoryAccess.BUILDER, config.getInventoryAccess(new InventoryMock(null, InventoryType.SMOKER)));
        assertEquals(InventoryAccess.BUILDER,
                config.getInventoryAccess(new InventoryMock(null, InventoryType.BLAST_FURNACE)));
        YamlConfiguration onDisk = YamlConfiguration.loadConfiguration(new File(WorldConfig.getWorldConfigDir(),
                WorldConfig.getDefaultWorldConfigName() + "." + WorldConfig.getCfgExtension()));
        assertEquals("BUILDER", onDisk.getString("inventoryAccess.SMOKER"), "in defaultWorldConfig.yml");
        assertEquals("BUILDER", onDisk.getString("inventoryAccess.BLAST_FURNACE"), "in defaultWorldConfig.yml");
    }
}
