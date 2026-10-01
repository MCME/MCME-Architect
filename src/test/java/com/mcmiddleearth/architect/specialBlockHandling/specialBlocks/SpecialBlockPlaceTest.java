package com.mcmiddleearth.architect.specialBlockHandling.specialBlocks;

import com.mcmiddleearth.architect.ArchitectPlugin;
import com.mcmiddleearth.architect.specialBlockHandling.SpecialBlockType;
import com.mcmiddleearth.util.FakeGaffer;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.Wall;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockbukkit.mockbukkit.world.WorldMock;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

// placeBlock returns the blocks it placed, and Architect reports those to TheGaffer (SpecialBlockGafferTest); a block
// it changes besides is checked with TheGaffer here. BRANCH and BRANCH_CONNECT keep walls in their block data, which
// MockBukkit cannot make from text, so they cannot be loaded from a blocks.yml: blocks of the same kinds, with
// stand-in block data, are placed directly. One mock/load per class, as in SpecialBlockGafferTest.
class SpecialBlockPlaceTest {

    private static ServerMock server;
    private static WorldMock world;
    private static PlayerMock player;

    @BeforeAll
    static void setUp() {
        server = MockBukkit.mock();
        MockBukkit.load(ArchitectPlugin.class);
        MockBukkit.loadWith(FakeGaffer.class, FakeGaffer.description());
        world = server.addSimpleWorld("world");
        player = server.addPlayer();
    }

    @AfterAll
    static void tearDown() {
        if (MockBukkit.isMocked()) {
            MockBukkit.unmock();
        }
    }

    @BeforeEach
    void reset() {
        FakeGaffer.asked.clear();
        FakeGaffer.allowed = location -> true;
        player.setRotation(0, 0);
    }

    @AfterEach
    void dropDelayedPlacements() {
        // these blocks are set a tick later, when the test is over
        server.getScheduler().cancelTasks(ArchitectPlugin.getPluginInstance());
    }

    private static Block ground(int x) {
        Block ground = world.getBlockAt(x, 64, 0);
        ground.setType(Material.DIRT);
        return ground;
    }

    // A BRANCH with stone for every piece but its wall. With a width of -1 it is as thin or thick as what it is placed
    // on; a width of its own keeps it from reading the sides of a wall, which MockBukkit's walls do not have.
    private static SpecialBlockBranch2 branch(BlockData wall, int width) {
        BlockData stone = Material.STONE.createBlockData();
        BlockData[][] level = new BlockData[2][4];
        BlockData[][][] sloped = new BlockData[2][2][8];
        Arrays.stream(level).forEach(faces -> Arrays.fill(faces, stone));
        Arrays.stream(sloped).flatMap(Arrays::stream).forEach(faces -> Arrays.fill(faces, stone));
        return new SpecialBlockBranch2("test/branch", level, sloped, wall, stone, width, -1, SpecialBlockType.BRANCH);
    }

    private static SpecialBlockBranch2 stoneBranch() {
        return branch(Material.STONE.createBlockData(), -1);
    }

    // Its wall is andesite, as BRANCH's is.
    private static SpecialBlockBranch2 thickBranchOnAndesiteWalls() {
        return branch(server.createBlockData("minecraft:andesite_wall"), 1);
    }

    private static Block wallWithoutPost(int x) {
        Block wall = world.getBlockAt(x, 64, 0);
        wall.setBlockData(server.createBlockData("minecraft:andesite_wall[up=false]"));
        return wall;
    }

    // A branch laid level lies north, east, south or west.
    @Test
    void aBranchLaidLevelFacingADiagonalPlacesNothing() {
        SpecialBlockBranch2 branch = stoneBranch();
        Block ground = ground(0);
        Block target = ground.getRelative(BlockFace.NORTH_EAST).getRelative(BlockFace.UP);

        player.setRotation(45, 0); // level, looking south-west: the branch would face north-east
        assertEquals(List.of(), branch.placeBlock(target, BlockFace.UP, ground, null, player), "facing north-east");

        player.setRotation(0, 0); // level, looking south: it faces north
        assertEquals(List.of(target), branch.placeBlock(target, BlockFace.UP, ground, null, player), "facing north");
    }

    // BRANCH_CONNECT has block data for the top face only, as this block has.
    @Test
    void aVariantBlockClickedOnAFaceItHasNoDataForPlacesNothing() {
        SpecialBlockOrientableVariants topOnly = new SpecialBlockOrientableVariants("test/top",
                new BlockData[][]{{Material.STONE.createBlockData()}}, new String[]{"base"},
                new SpecialBlockOrientable.Orientation[]{new SpecialBlockOrientable.Orientation(BlockFace.UP, "")},
                SpecialBlockType.BRANCH_CONNECT) {
            @Override
            protected int getVariant(Block blockPlace, Block clicked, BlockFace blockFace, Player player,
                                     Location interactionPoint) {
                return 0;
            }
        };
        Block ground = ground(4);
        Block target = ground.getRelative(BlockFace.UP);

        assertEquals(List.of(), topOnly.placeBlock(target, BlockFace.EAST, ground, null, player), "clicked on a side");
        assertEquals(List.of(target), topOnly.placeBlock(target, BlockFace.UP, ground, null, player), "on the top");
    }

    // A branch set on top of a wall, looking straight down, raises the wall's post: it changes the wall too. The
    // listener asks TheGaffer about the new block only.
    @Test
    void aBranchIsNotPlacedWhereItWouldChangeAWallOutsideTheJob() {
        SpecialBlockBranch2 branch = thickBranchOnAndesiteWalls();
        Block wall = wallWithoutPost(8);
        Block target = wall.getRelative(BlockFace.UP);
        FakeGaffer.allowed = location -> !location.equals(wall.getLocation()); // the job's area ends above the wall
        player.setRotation(0, 90);

        assertEquals(List.of(), branch.placeBlock(target, BlockFace.UP, wall, null, player), "nothing placed");
        server.getScheduler().performTicks(7);

        assertEquals(List.of(wall.getLocation()), FakeGaffer.asked, "asked about the wall");
        assertFalse(((Wall) wall.getBlockData()).isUp(), "the wall is left as it was");
        assertEquals(Material.AIR, target.getType(), "no branch");
    }

    @Test
    void aBranchChangesAWallInsideTheJob() {
        SpecialBlockBranch2 branch = thickBranchOnAndesiteWalls();
        Block wall = wallWithoutPost(10);
        Block target = wall.getRelative(BlockFace.UP);
        player.setRotation(0, 90);

        assertEquals(List.of(target), branch.placeBlock(target, BlockFace.UP, wall, null, player), "placed");

        assertEquals(List.of(wall.getLocation()), FakeGaffer.asked, "asked about the wall");
        assertTrue(((Wall) wall.getBlockData()).isUp(), "the wall's post is raised");
    }

    // A wall that has its post already is left as it is, so its place in the job does not matter.
    @Test
    void aBranchLeavingAWallOutsideTheJobAsItIsIsPlaced() {
        SpecialBlockBranch2 branch = thickBranchOnAndesiteWalls();
        Block wall = world.getBlockAt(12, 64, 0);
        wall.setBlockData(server.createBlockData("minecraft:andesite_wall[up=true]"));
        Block target = wall.getRelative(BlockFace.UP);
        FakeGaffer.allowed = location -> !location.equals(wall.getLocation());
        player.setRotation(0, 90);

        assertEquals(List.of(target), branch.placeBlock(target, BlockFace.UP, wall, null, player), "placed");
        assertEquals(List.of(), FakeGaffer.asked, "nothing asked about the wall");
    }
}
