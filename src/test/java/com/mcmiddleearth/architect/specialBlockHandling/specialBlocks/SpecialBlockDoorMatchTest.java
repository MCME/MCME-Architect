package com.mcmiddleearth.architect.specialBlockHandling.specialBlocks;

import com.mcmiddleearth.architect.ArchitectPlugin;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Bisected;
import org.bukkit.block.data.type.Door;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.block.BlockMock;
import org.mockbukkit.mockbukkit.world.WorldMock;

import static org.junit.jupiter.api.Assertions.*;

// A door picked with flint or the pick-block key, or broken, is matched against the doors of the block inventory: its
// material, then its hinge and whether it is powered, as its upper half holds them. They are read from its block data:
// on Paper, the first read of a block's legacy data value builds the whole table of legacy materials on the main
// thread, which holds the server up for seconds. One mock/load per class, as in SpecialBlockPlaceTest.
class SpecialBlockDoorMatchTest {

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

    // A whole oak door at x, its lower half returned.
    private static Block door(int x, Door.Hinge hinge, boolean powered) {
        Door data = (Door) Material.OAK_DOOR.createBlockData();
        data.setHinge(hinge);
        data.setPowered(powered);
        Block lower = world.getBlockAt(x, 64, 0);
        lower.setType(Material.OAK_DOOR);
        data.setHalf(Bisected.Half.BOTTOM);
        lower.setBlockData(data.clone());
        Block upper = lower.getRelative(BlockFace.UP);
        upper.setType(Material.OAK_DOOR);
        data.setHalf(Bisected.Half.TOP);
        upper.setBlockData(data.clone());
        return lower;
    }

    // The upper half of that door, as a block whose legacy data value cannot be read.
    private static Block upperHalfWithoutLegacyData(Block lower) {
        Block upper = lower.getRelative(BlockFace.UP);
        BlockMock block = new BlockMock(Material.OAK_DOOR, upper.getLocation()) {
            @Override
            public byte getData() {
                throw new UnsupportedOperationException("the legacy data value was read");
            }
        };
        block.setBlockData(upper.getBlockData().clone());
        return block;
    }

    private static SpecialBlockThinWall thinWall(boolean rightHinge, boolean powered) {
        YamlConfiguration config = new YamlConfiguration();
        config.set("blockMaterial", "OAK_DOOR");
        config.set("rightHinge", rightHinge);
        config.set("powered", powered);
        return SpecialBlockThinWall.loadFromConfig(config, "test/thinwall");
    }

    private static SpecialBlockDoor specialDoor(boolean powered) {
        YamlConfiguration config = new YamlConfiguration();
        config.set("blockMaterial", "OAK_DOOR");
        config.set("powered", powered);
        return SpecialBlockDoor.loadFromConfig(config, "test/door");
    }

    @Test
    void aThinWallMatchesItsDoorWithoutItsLegacyDataValue() {
        Block lower = door(0, Door.Hinge.RIGHT, true);

        assertTrue(thinWall(true, true).matches(upperHalfWithoutLegacyData(lower)));
    }

    @Test
    void aDoorMatchesItsDoorWithoutItsLegacyDataValue() {
        Block lower = door(2, Door.Hinge.LEFT, false);

        assertTrue(specialDoor(false).matches(upperHalfWithoutLegacyData(lower)));
    }

    @Test
    void aThinWallMatchesOnlyADoorWithItsHingeAndPower() {
        SpecialBlockThinWall thinWall = thinWall(true, true);
        Block match = door(4, Door.Hinge.RIGHT, true);

        assertTrue(thinWall.matches(match), "clicked at the lower half");
        assertTrue(thinWall.matches(match.getRelative(BlockFace.UP)), "clicked at the upper half");
        assertFalse(thinWall.matches(door(6, Door.Hinge.LEFT, true)), "the other hinge");
        assertFalse(thinWall.matches(door(8, Door.Hinge.RIGHT, false)), "not powered");
    }

    @Test
    void aDoorMatchesOnlyADoorPoweredAsItIs() {
        Block unpowered = door(10, Door.Hinge.RIGHT, false);
        Block powered = door(12, Door.Hinge.RIGHT, true);

        assertTrue(specialDoor(false).matches(unpowered), "clicked at the lower half");
        assertTrue(specialDoor(false).matches(unpowered.getRelative(BlockFace.UP)), "clicked at the upper half");
        assertFalse(specialDoor(false).matches(powered), "powered");
        assertTrue(specialDoor(true).matches(powered), "for a powered one");
    }
}
