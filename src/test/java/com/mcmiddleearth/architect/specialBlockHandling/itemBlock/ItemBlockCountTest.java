package com.mcmiddleearth.architect.specialBlockHandling.itemBlock;

import org.bukkit.Location;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.GlowItemFrame;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.Painting;
import org.bukkit.entity.Pig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.world.WorldMock;

import static org.junit.jupiter.api.Assertions.*;

// The one rule for what counts toward a chunk's item-block limit, shared by placing and the web map.
class ItemBlockCountTest {

    private WorldMock world;

    @BeforeEach
    void setUp() {
        world = MockBukkit.mock().addSimpleWorld("world");
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    private <T extends org.bukkit.entity.Entity> T spawn(Class<T> type) {
        return world.spawn(new Location(world, 0, 64, 0), type);
    }

    @Test
    void armorStandsItemFramesAndPaintingsCount() {
        assertEquals(ItemBlockCount.ARMOR_STAND, ItemBlockCount.of(spawn(ArmorStand.class)));
        assertEquals(ItemBlockCount.ITEM_FRAME, ItemBlockCount.of(spawn(ItemFrame.class)));
        assertEquals(ItemBlockCount.ITEM_FRAME, ItemBlockCount.of(spawn(GlowItemFrame.class)),
                "a glow frame is a frame");
        assertEquals(ItemBlockCount.PAINTING, ItemBlockCount.of(spawn(Painting.class)));
    }

    @Test
    void otherEntitiesDoNot() {
        assertNull(ItemBlockCount.of(spawn(Pig.class)));
        assertFalse(ItemBlockCount.countsTowardLimit(spawn(Pig.class)));
        assertTrue(ItemBlockCount.countsTowardLimit(spawn(ArmorStand.class)));
    }
}
