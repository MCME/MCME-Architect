package com.mcmiddleearth.architect.specialBlockHandling.listener;

import com.mcmiddleearth.architect.ArchitectPlugin;
import com.mcmiddleearth.architect.specialBlockHandling.data.SpecialBlockInventoryData;
import com.mcmiddleearth.util.FakeGaffer;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.MultipleFacing;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.ItemFrame;
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
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockbukkit.mockbukkit.world.WorldMock;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

// Architect places a special block itself, after cancelling the click, so no BlockPlaceEvent fires and TheGaffer's job
// stats would miss it: Architect reports each new special block to TheGaffer, and nothing for a click that places no
// block. A sneak-edit changes the clicked block instead, so it is not a new block, and TheGaffer is asked about the
// clicked block. One mock/load per class, as in SpecialBlockListenerTest.
class SpecialBlockGafferTest {

    private static ServerMock server;
    private static WorldMock world;
    private static PlayerMock player;

    @BeforeAll
    static void setUp() throws Exception {
        server = MockBukkit.mock();
        MockBukkit.load(ArchitectPlugin.class);
        MockBukkit.loadWith(FakeGaffer.class, FakeGaffer.description());
        world = server.addSimpleWorld("world");
        player = server.addPlayer();
        Path rp = SpecialBlockInventoryData.configFolder.toPath().resolve("testrp");
        Files.createDirectories(rp);
        Files.writeString(rp.resolve("blocks.yml"), """
                Items:
                  stone:
                    type: BLOCK
                    blockData: minecraft:stone
                    itemMaterial: STONE
                  fence:
                    type: MULTI_FACE
                    blockData: minecraft:oak_fence
                    itemMaterial: OAK_FENCE
                  itemblock:
                    type: ITEM_BLOCK
                    blockData: minecraft:barrier
                    contentItem: DIAMOND_HOE
                    contentDamage: '1'
                    itemMaterial: DIAMOND_HOE
                  frame:
                    type: ITEM_FRAME
                    itemMaterial: PAPER
                  axis:
                    type: TWO_AXIS
                    blockDataX: minecraft:oak_log[axis=x]
                    blockDataZ: minecraft:oak_log[axis=z]
                    itemMaterial: OAK_LOG
                  faces:
                    type: FIVE_FACES
                    blockDataSouth: minecraft:furnace[facing=south]
                    blockDataWest: minecraft:furnace[facing=west]
                    blockDataNorth: minecraft:furnace[facing=north]
                    blockDataEast: minecraft:furnace[facing=east]
                    blockDataUp: minecraft:stone
                    itemMaterial: FURNACE
                  spawner:
                    type: MOB_SPAWNER_BLOCK
                    contentItem: DIAMOND_HOE
                    contentDamage: '1'
                    itemMaterial: SPAWNER
                  double:
                    type: DOUBLE_Y_BLOCK
                    blockDataLower: minecraft:oak_planks
                    blockDataUpper: minecraft:spruce_planks
                    itemMaterial: OAK_PLANKS
                  door3:
                    type: DOOR_THREE_BLOCKS
                    blockMaterial: OAK_DOOR
                    itemMaterial: OAK_DOOR
                  door4:
                    type: DOOR_FOUR_BLOCKS
                    blockMaterial: SPRUCE_DOOR
                    itemMaterial: SPRUCE_DOOR
                  door:
                    type: DOOR
                    blockMaterial: BIRCH_DOOR
                    itemMaterial: BIRCH_DOOR
                  thinwall:
                    type: THIN_WALL
                    blockMaterial: JUNGLE_DOOR
                    itemMaterial: JUNGLE_DOOR
                  upshift:
                    type: UPSHIFT
                    blockData: minecraft:stone
                    itemMaterial: STONE
                  signpost:
                    type: SIGN_POST
                    itemMaterial: OAK_SIGN
                """);
        SpecialBlockInventoryData.loadInventories();
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
        player.setSneaking(false);
        while (player.nextMessage() != null) {
            // what an earlier test was told
        }
    }

    @AfterEach
    void dropDelayedPlacements() {
        // doors and some other special blocks are set a tick later, when the test is over
        server.getScheduler().cancelTasks(ArchitectPlugin.getPluginInstance());
    }

    // The item as the block inventory makes it: the tag, then the special block's id.
    private static ItemStack specialItem(String specialBlock) {
        ItemStack item = new ItemStack(Material.STONE);
        ItemMeta meta = item.getItemMeta();
        meta.setLore(List.of(SpecialBlockInventoryData.SPECIAL_BLOCK_TAG, "testrp/" + specialBlock));
        item.setItemMeta(meta);
        assertNotNull(SpecialBlockInventoryData.getSpecialBlockDataFromItem(item), "a special block item");
        return item;
    }

    private void rightClick(String specialBlock, Block clicked, BlockFace face) {
        ItemStack item = specialItem(specialBlock);
        player.getInventory().setItemInMainHand(item);
        new SpecialBlockListener().placeSpecialBlock(
                new PlayerInteractEvent(player, Action.RIGHT_CLICK_BLOCK, item, clicked, face, EquipmentSlot.HAND));
    }

    // Through the server, where every listener hears the click.
    private void rightClickThroughTheServer(String specialBlock, Block clicked, BlockFace face) {
        ItemStack item = specialItem(specialBlock);
        player.getInventory().setItemInMainHand(item);
        server.getPluginManager().callEvent(
                new PlayerInteractEvent(player, Action.RIGHT_CLICK_BLOCK, item, clicked, face, EquipmentSlot.HAND));
    }

    private static long refusals() {
        long refusals = 0;
        for (String message = player.nextMessage(); message != null; message = player.nextMessage()) {
            if (message.contains("You are not in the job's area.")) {
                refusals++;
            }
        }
        return refusals;
    }

    private static List<FakeGaffer.Build> placed(Block... blocks) {
        return Arrays.stream(blocks).map(block -> new FakeGaffer.Build(player.getName(), block.getLocation(), true))
                .toList();
    }

    // A click that sets no block is no place, although it got as far as asking TheGaffer about the block it aimed at.
    private static void assertNothingPlacedOrReported(Block target) {
        assertEquals(List.of(target.getLocation()), FakeGaffer.asked, "the click got as far as asking TheGaffer");
        assertEquals(Material.AIR, target.getType(), "nothing placed");
        assertEquals(List.of(), FakeGaffer.builds);
    }

    private static boolean hasFace(Block fence, BlockFace face) {
        return ((MultipleFacing) fence.getBlockData()).hasFace(face);
    }

    @Test
    void aPlacedSpecialBlockIsReportedToTheGaffer() {
        Block ground = world.getBlockAt(0, 64, 0);
        ground.setType(Material.DIRT);

        rightClick("stone", ground, BlockFace.UP);

        Block placed = ground.getRelative(BlockFace.UP);
        assertEquals(Material.STONE, placed.getType(), "placed");
        assertEquals(List.of(new FakeGaffer.Build(player.getName(), placed.getLocation(), true)), FakeGaffer.builds);
    }

    @Test
    void aPlacementTheGafferRefusesIsNotReported() {
        Block ground = world.getBlockAt(2, 64, 0);
        ground.setType(Material.DIRT);
        Block target = ground.getRelative(BlockFace.UP);
        FakeGaffer.allowed = location -> !location.equals(target.getLocation());

        rightClick("stone", ground, BlockFace.UP);

        assertEquals(List.of(target.getLocation()), FakeGaffer.asked, "asked about the new block");
        assertEquals(Material.AIR, target.getType(), "not placed");
        assertEquals(List.of(), FakeGaffer.builds);
        String message = player.nextMessage();
        assertTrue(message != null && message.contains("You are not in the job's area."), "told why: " + message);
    }

    // The click a special block uses is its own: the protection of signs and redstone wire, which asks TheGaffer about
    // the clicked block, keeps out of it, so a refused placement is answered once.
    @Test
    void aRefusedPlacementOnASignIsAnsweredOnce() {
        Block sign = world.getBlockAt(50, 64, 0);
        sign.setType(Material.OAK_SIGN);
        FakeGaffer.allowed = location -> false;

        rightClickThroughTheServer("stone", sign, BlockFace.EAST);

        assertEquals(1, refusals());
    }

    @Test
    void aRefusedPlacementOnRedstoneWireIsAnsweredOnce() {
        Block wire = world.getBlockAt(54, 64, 0);
        wire.setType(Material.REDSTONE_WIRE);
        FakeGaffer.allowed = location -> false;

        rightClickThroughTheServer("stone", wire, BlockFace.UP);

        assertEquals(1, refusals());
    }

    @Test
    void aSneakEditIsNotANewBlock() {
        Block fence = world.getBlockAt(4, 64, 0);
        fence.setType(Material.OAK_FENCE);
        player.setSneaking(true);

        rightClick("fence", fence, BlockFace.EAST);

        assertTrue(hasFace(fence, BlockFace.WEST), "the edit joined the fence on the clicked side's opposite");
        assertEquals(List.of(), FakeGaffer.builds, "an edit adds no block");
    }

    @Test
    void aSneakEditIsCheckedAtTheBlockItChanges() {
        Block fence = world.getBlockAt(6, 64, 0);
        fence.setType(Material.OAK_FENCE);
        FakeGaffer.allowed = location -> !location.equals(fence.getLocation()); // its neighbour is in the job
        player.setSneaking(true);

        rightClick("fence", fence, BlockFace.EAST);

        assertEquals(List.of(fence.getLocation()), FakeGaffer.asked, "asked about the fence, not its neighbour");
        assertFalse(hasFace(fence, BlockFace.WEST), "refused: the fence itself is outside the job's area");
    }

    private static long armorStands(Block block) {
        return Arrays.stream(block.getChunk().getEntities()).filter(entity -> entity instanceof ArmorStand).count();
    }

    @Test
    void aPlacedItemBlockIsReported() {
        Block ground = world.getBlockAt(8, 64, 0);
        ground.setType(Material.DIRT);

        rightClick("itemblock", ground, BlockFace.UP);
        server.getScheduler().performTicks(3); // the armor stand gets its item two ticks later

        Block placed = ground.getRelative(BlockFace.UP);
        assertEquals(Material.BARRIER, placed.getType(), "placed");
        assertEquals(List.of(new FakeGaffer.Build(player.getName(), placed.getLocation(), true)), FakeGaffer.builds);
    }

    // TheGaffer counts no item frame, placed or broken: they are entities. So a special one is not counted either.
    @Test
    void anItemFrameIsNotCounted() {
        Block wall = world.getBlockAt(12, 64, 0);
        wall.setType(Material.STONE);
        Block target = wall.getRelative(BlockFace.EAST);

        rightClick("frame", wall, BlockFace.EAST);
        server.getScheduler().performTicks(3); // the frame is hung three ticks later, in this test and not the next

        assertEquals(List.of(target.getLocation()), FakeGaffer.asked, "asked about the frame's block");
        assertEquals(1, Arrays.stream(target.getChunk().getEntities()).filter(entity -> entity instanceof ItemFrame)
                .count(), "the frame was hung");
        assertEquals(List.of(), FakeGaffer.builds);
    }

    // An item block is refused in a chunk that holds too many entities already (5 by default): no new block.
    @Test
    void anItemBlockRefusedForTooManyEntitiesIsNotReported() {
        Block ground = world.getBlockAt(40, 64, 0); // a chunk of its own
        ground.setType(Material.DIRT);
        for (int i = 0; i < 5; i++) {
            world.spawnEntity(new Location(world, 40.5 + i, 70, 8.5), EntityType.ARMOR_STAND);
        }
        assertEquals(5, armorStands(ground), "the chunk is at its limit");

        rightClick("itemblock", ground, BlockFace.UP);

        String message = player.nextMessage();
        assertTrue(message != null && message.contains("Too many entities"), "refused: " + message);
        assertEquals(Material.AIR, ground.getRelative(BlockFace.UP).getType(), "not placed");
        assertEquals(List.of(), FakeGaffer.builds);
    }

    // TWO_AXIS has block data for the four sides only.
    @Test
    void aTwoAxisBlockClickedOnATopPlacesNothing() {
        Block ground = world.getBlockAt(16, 64, 0);
        ground.setType(Material.DIRT);

        rightClick("axis", ground, BlockFace.UP);

        assertNothingPlacedOrReported(ground.getRelative(BlockFace.UP));
    }

    // FIVE_FACES has block data for the four sides and the top.
    @Test
    void aFiveFacesBlockClickedOnABottomPlacesNothing() {
        Block ceiling = world.getBlockAt(18, 70, 0);
        ceiling.setType(Material.DIRT);

        rightClick("faces", ceiling, BlockFace.DOWN);

        assertNothingPlacedOrReported(ceiling.getRelative(BlockFace.DOWN));
    }

    // A new fence joins the block it is placed against, and a fence joins nothing up or down.
    @Test
    void aMultiFaceBlockOnAFaceItCannotTakePlacesNothing() {
        Block ground = world.getBlockAt(20, 64, 0);
        ground.setType(Material.DIRT);

        rightClick("fence", ground, BlockFace.UP);

        assertNothingPlacedOrReported(ground.getRelative(BlockFace.UP));
    }

    // Placed against a fence of its kind, it joins that fence: no new block.
    @Test
    void aMultiFaceBlockJoiningOneOfItsKindIsNoNewBlock() {
        Block wall = world.getBlockAt(44, 64, 0);
        wall.setType(Material.STONE);
        Block fence = wall.getRelative(BlockFace.EAST);
        fence.setType(Material.OAK_FENCE);

        rightClick("fence", wall, BlockFace.EAST);

        assertTrue(hasFace(fence, BlockFace.WEST), "joined towards the clicked block");
        assertEquals(List.of(), FakeGaffer.builds);
    }

    // An UPSHIFT block goes one block higher than where it was aimed.
    @Test
    void anUpshiftBlockIsReportedWhereItGoes() {
        Block ground = world.getBlockAt(46, 64, 0);
        ground.setType(Material.DIRT);

        rightClick("upshift", ground, BlockFace.UP);

        Block higher = ground.getRelative(BlockFace.UP, 2);
        assertEquals(Material.STONE, higher.getType(), "placed");
        assertEquals(placed(higher), FakeGaffer.builds);
    }

    // A SIGN_POST has block data for the top and the bottom only. Its sign editor opens three ticks after a sign is
    // placed, and would fail on air.
    @Test
    void aSignClickedOnAFaceItHasNoDataForPlacesNothingAndOpensNoEditor() {
        Block wall = world.getBlockAt(48, 64, 0);
        wall.setType(Material.STONE);

        rightClick("signpost", wall, BlockFace.EAST);
        assertDoesNotThrow(() -> server.getScheduler().performTicks(3), "no editor for a sign that is not there");

        assertNothingPlacedOrReported(wall.getRelative(BlockFace.EAST));
    }

    // Placing a MOB_SPAWNER_BLOCK is switched off.
    @Test
    void aMobSpawnerBlockPlacesNothing() {
        Block ground = world.getBlockAt(26, 64, 0);
        ground.setType(Material.DIRT);

        rightClick("spawner", ground, BlockFace.UP);

        assertNothingPlacedOrReported(ground.getRelative(BlockFace.UP));
    }

    // TheGaffer counts a break for each block a player breaks, and in a no-physics world, which a new world config
    // makes, every block is broken on its own: even the two halves of a door, as neither takes the other along. So
    // each block a placement sets counts as a place. The doors below are set a tick later, so only their report is
    // checked.
    @Test
    void aDoubleYBlockCountsAsTwoPlaces() {
        Block ground = world.getBlockAt(28, 64, 0);
        ground.setType(Material.DIRT);

        rightClick("double", ground, BlockFace.UP);

        Block lower = ground.getRelative(BlockFace.UP);
        Block upper = lower.getRelative(BlockFace.UP);
        assertEquals(Material.OAK_PLANKS, lower.getType(), "the lower block");
        assertEquals(Material.SPRUCE_PLANKS, upper.getType(), "the upper block");
        assertEquals(placed(lower, upper), FakeGaffer.builds);
    }

    @Test
    void aDoorCountsAsTwoPlaces() {
        Block ground = world.getBlockAt(34, 64, 0);
        ground.setType(Material.DIRT);

        rightClick("door", ground, BlockFace.UP);

        Block door = ground.getRelative(BlockFace.UP);
        assertEquals(placed(door, door.getRelative(BlockFace.UP)), FakeGaffer.builds);
    }

    // A THIN_WALL is a door too.
    @Test
    void aThinWallCountsAsTwoPlaces() {
        Block ground = world.getBlockAt(36, 64, 0);
        ground.setType(Material.DIRT);

        rightClick("thinwall", ground, BlockFace.UP);

        Block door = ground.getRelative(BlockFace.UP);
        assertEquals(placed(door, door.getRelative(BlockFace.UP)), FakeGaffer.builds);
    }

    // A door with a half door on top.
    @Test
    void aThreeBlockDoorCountsAsThreePlaces() {
        Block ground = world.getBlockAt(30, 64, 0);
        ground.setType(Material.DIRT);

        rightClick("door3", ground, BlockFace.UP);

        Block door = ground.getRelative(BlockFace.UP);
        assertEquals(placed(door, door.getRelative(BlockFace.UP), door.getRelative(BlockFace.UP, 2)),
                FakeGaffer.builds);
    }

    // A door on a door.
    @Test
    void aFourBlockDoorCountsAsFourPlaces() {
        Block ground = world.getBlockAt(32, 64, 0);
        ground.setType(Material.DIRT);

        rightClick("door4", ground, BlockFace.UP);

        Block door = ground.getRelative(BlockFace.UP);
        assertEquals(placed(door, door.getRelative(BlockFace.UP), door.getRelative(BlockFace.UP, 2),
                door.getRelative(BlockFace.UP, 3)), FakeGaffer.builds);
    }
}
