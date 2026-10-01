package com.mcmiddleearth.architect.specialBlockHandling.listener;

import com.mcmiddleearth.architect.ArchitectPlugin;
import com.mcmiddleearth.util.FakeGaffer;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.block.BlockMock;
import org.mockbukkit.mockbukkit.block.state.BlockStateMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockbukkit.mockbukkit.world.WorldMock;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

// The old special blocks, items named "Half Door", "Half Bed", "Unlit Torch" or "Placeable ...", are set by Architect
// after the click, with no BlockPlaceEvent, so TheGaffer is told of each new block, as for the special blocks of the
// block inventories (SpecialBlockGafferTest). One mock/load per class, as in SpecialBlockGafferTest.
class LegacyPlaceGafferTest {

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
        player.setOp(true); // for the architect.place.* permissions
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
        FakeGaffer.builds.clear();
        FakeGaffer.allowed = location -> true;
        while (player.nextMessage() != null) {
            // what an earlier test was told
        }
    }

    @AfterEach
    void dropDelayedPlacements() {
        server.getScheduler().cancelTasks(ArchitectPlugin.getPluginInstance());
    }

    // These placements set legacy data values, which MockBukkit's block states do not take. This state ignores them.
    private static final class LegacyDataState extends BlockStateMock {

        LegacyDataState(Block block) {
            super(block);
        }

        @Override
        public byte getRawData() {
            return 0;
        }

        @Override
        public void setRawData(byte data) {
        }
    }

    private static Block withLegacyData(Block block) {
        ((BlockMock) block).setState(new LegacyDataState(block));
        return block;
    }

    private static Block ground(int x) {
        Block ground = world.getBlockAt(x, 64, 0);
        ground.setType(Material.DIRT);
        return ground;
    }

    private static void rightClick(Material item, String name, Block clicked, BlockFace face) {
        ItemStack stack = new ItemStack(item);
        ItemMeta meta = stack.getItemMeta();
        meta.setDisplayName(name);
        stack.setItemMeta(meta);
        player.getInventory().setItemInMainHand(stack);
        server.getPluginManager().callEvent(new PlayerInteractEvent(player, Action.RIGHT_CLICK_BLOCK, stack, clicked,
                face, EquipmentSlot.HAND));
    }

    private static List<FakeGaffer.Build> placed(Block block) {
        return List.of(new FakeGaffer.Build(player.getName(), block.getLocation(), true));
    }

    @Test
    void aHalfDoorIsReported() {
        Block ground = ground(0);
        Block door = withLegacyData(ground.getRelative(BlockFace.UP));

        rightClick(Material.OAK_DOOR, "Half Door", ground, BlockFace.UP);
        server.getScheduler().performTicks(1); // it is set a tick later

        assertEquals(Material.OAK_DOOR, door.getType(), "placed");
        assertEquals(placed(door), FakeGaffer.builds);
    }

    // At a job's edge, the clicked block may lie inside the job's area and the new one outside: TheGaffer is asked
    // about the new one. So in the tests below.
    @Test
    void aHalfDoorOutsideTheJobIsNotPlaced() {
        Block ground = ground(2);
        Block door = withLegacyData(ground.getRelative(BlockFace.UP));
        FakeGaffer.allowed = location -> !location.equals(door.getLocation());

        rightClick(Material.OAK_DOOR, "Half Door", ground, BlockFace.UP);
        server.getScheduler().performTicks(1);

        assertEquals(List.of(door.getLocation()), FakeGaffer.asked, "asked about the new block");
        assertEquals(Material.AIR, door.getType(), "not placed");
        assertEquals(List.of(), FakeGaffer.builds);
    }

    @Test
    void aHalfBedOutsideTheJobIsNotPlaced() {
        Block ground = ground(14);
        Block bed = withLegacyData(ground.getRelative(BlockFace.UP));
        FakeGaffer.allowed = location -> !location.equals(bed.getLocation());

        rightClick(Material.RED_BED, "Half Bed", ground, BlockFace.UP);
        server.getScheduler().performTicks(1);

        assertEquals(List.of(bed.getLocation()), FakeGaffer.asked, "asked about the new block");
        assertEquals(Material.AIR, bed.getType(), "not placed");
        assertEquals(List.of(), FakeGaffer.builds);
    }

    @Test
    void anUnlitTorchOutsideTheJobIsNotPlaced() {
        Block wall = ground(16);
        Block torch = withLegacyData(wall.getRelative(BlockFace.EAST));
        FakeGaffer.allowed = location -> !location.equals(torch.getLocation());

        rightClick(Material.REDSTONE_TORCH, "Unlit Torch", wall, BlockFace.EAST);
        server.getScheduler().performTicks(1);

        assertEquals(List.of(torch.getLocation()), FakeGaffer.asked, "asked about the new block");
        assertEquals(Material.AIR, torch.getType(), "not placed");
        assertEquals(List.of(), FakeGaffer.builds);
    }

    @Test
    void aPlaceablePlantOutsideTheJobIsNotPlaced() {
        Block ground = ground(18);
        Block plant = withLegacyData(ground.getRelative(BlockFace.UP));
        FakeGaffer.allowed = location -> !location.equals(plant.getLocation());

        rightClick(Material.RED_MUSHROOM, "Placeable Mushroom", ground, BlockFace.UP);

        assertEquals(List.of(plant.getLocation()), FakeGaffer.asked, "asked about the new block");
        assertEquals(Material.AIR, plant.getType(), "not placed");
        assertEquals(List.of(), FakeGaffer.builds);
    }

    @Test
    void aHalfBedIsReported() {
        Block ground = ground(4);
        Block bed = withLegacyData(ground.getRelative(BlockFace.UP));

        rightClick(Material.RED_BED, "Half Bed", ground, BlockFace.UP);

        assertEquals(Material.RED_BED, bed.getType(), "placed");
        assertEquals(placed(bed), FakeGaffer.builds);
    }

    @Test
    void anUnlitTorchIsReported() {
        Block wall = ground(6);
        Block torch = withLegacyData(wall.getRelative(BlockFace.EAST));

        rightClick(Material.REDSTONE_TORCH, "Unlit Torch", wall, BlockFace.EAST);
        server.getScheduler().performTicks(1); // it is set a tick later

        assertEquals(Material.REDSTONE_TORCH, torch.getType(), "placed");
        assertEquals(placed(torch), FakeGaffer.builds);
    }

    @Test
    void aPlaceablePlantIsReported() {
        Block ground = ground(8);
        Block plant = withLegacyData(ground.getRelative(BlockFace.UP));

        rightClick(Material.RED_MUSHROOM, "Placeable Mushroom", ground, BlockFace.UP);

        assertEquals(Material.RED_MUSHROOM, plant.getType(), "placed");
        assertEquals(placed(plant), FakeGaffer.builds);
    }

    // A click on a plant of the kind in hand changes that plant, and adds none.
    @Test
    void aPlaceablePlantClickedOnItsOwnKindIsNotReported() {
        Block mushroom = world.getBlockAt(10, 65, 0);
        mushroom.setType(Material.RED_MUSHROOM);
        withLegacyData(mushroom);
        Block above = withLegacyData(mushroom.getRelative(BlockFace.UP));

        rightClick(Material.RED_MUSHROOM, "Placeable Mushroom", mushroom, BlockFace.UP);

        assertEquals(Material.AIR, above.getType(), "nothing new");
        assertEquals(List.of(), FakeGaffer.builds);
    }

    // A plant goes only where there is air.
    @Test
    void aPlaceablePlantWhereABlockIsAlreadyIsNotReported() {
        Block ground = ground(12);
        Block above = ground.getRelative(BlockFace.UP);
        above.setType(Material.STONE);

        rightClick(Material.RED_MUSHROOM, "Placeable Mushroom", ground, BlockFace.UP);

        assertEquals(Material.STONE, above.getType(), "left as it was");
        assertEquals(List.of(), FakeGaffer.builds);
    }

    // A click that changes nothing asks TheGaffer nothing, so at a job's edge it is not refused either.
    @Test
    void aPlaceablePlantThatChangesNothingAsksNothing() {
        Block ground = ground(20);
        Block above = ground.getRelative(BlockFace.UP);
        above.setType(Material.STONE);
        FakeGaffer.allowed = location -> !location.equals(above.getLocation());

        rightClick(Material.RED_MUSHROOM, "Placeable Mushroom", ground, BlockFace.UP);

        assertEquals(List.of(), FakeGaffer.asked, "nothing asked");
        assertNull(player.nextMessage(), "nothing refused");
    }

    @Test
    void anUnlitTorchThatChangesNothingAsksNothing() {
        Block wall = ground(22);
        Block beside = wall.getRelative(BlockFace.EAST);
        beside.setType(Material.STONE);
        FakeGaffer.allowed = location -> !location.equals(beside.getLocation());

        rightClick(Material.REDSTONE_TORCH, "Unlit Torch", wall, BlockFace.EAST);
        server.getScheduler().performTicks(1);

        assertEquals(Material.STONE, beside.getType(), "left as it was");
        assertEquals(List.of(), FakeGaffer.asked, "nothing asked");
        assertNull(player.nextMessage(), "nothing refused");
    }
}
