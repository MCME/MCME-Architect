package com.mcmiddleearth.architect.specialBlockHandling.listener;

import com.mcmiddleearth.architect.ArchitectPlugin;
import com.mcmiddleearth.architect.Modules;
import com.mcmiddleearth.architect.PluginData;
import com.mcmiddleearth.architect.serverResoucePack.RpManager;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.data.type.Slab;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockbukkit.mockbukkit.world.WorldMock;

import static org.junit.jupiter.api.Assertions.*;

// One mock/load per class (Architect caches getDataFolder()-derived paths in static fields at
// class-load); surefire reuseForks=false gives a fresh JVM per class. Mirrors LoadGuardTest.
class SpecialBlockListenerTest {
    private static ServerMock server;

    @BeforeAll static void setUp() { server = MockBukkit.mock(); MockBukkit.load(ArchitectPlugin.class); }
    @AfterAll  static void tearDown() { if (MockBukkit.isMocked()) MockBukkit.unmock(); }

    // Stacking a slab onto a half slab makes a DOUBLE slab. avoidDoubleSlab swaps it for the full block
    // configured for the player's RP or, when the player has none, for the RP of the region at the block.
    // With neither there is nothing to swap to: the double slab must stay as placed, not NPE.
    @Test void doubleSlabWithNoRpAndNoRpRegionIsLeftAsPlaced() {
        WorldMock world = server.addSimpleWorld("world");
        PlayerMock player = server.addPlayer();
        Block block = world.getBlockAt(0, 64, 0);

        Slab bottom = (Slab) Material.STONE_SLAB.createBlockData();
        bottom.setType(Slab.Type.BOTTOM);
        block.setBlockData(bottom);
        BlockState replaced = block.getState();
        Slab doubleSlab = (Slab) Material.STONE_SLAB.createBlockData();
        doubleSlab.setType(Slab.Type.DOUBLE);
        block.setBlockData(doubleSlab);

        // Preconditions, so this can't pass by returning before the RP-region lookup.
        assertTrue(PluginData.isModuleEnabled(player.getWorld(), Modules.SPECIAL_BLOCKS_PLACE),
                "the handler only runs with SPECIAL_BLOCKS_PLACE enabled");
        assertEquals("", RpManager.getCurrentRpName(player), "player must have no RP");
        assertNull(RpManager.getRegion(block.getLocation()), "there must be no RP region at the block");

        BlockPlaceEvent event = new BlockPlaceEvent(block, replaced, block.getRelative(BlockFace.DOWN),
                new ItemStack(Material.STONE_SLAB), player, true, EquipmentSlot.HAND);

        assertDoesNotThrow(() -> new SpecialBlockListener().avoidDoubleSlab(event));
        assertEquals(doubleSlab, block.getBlockData(), "with no RP there is no replacement to place");
    }
}
