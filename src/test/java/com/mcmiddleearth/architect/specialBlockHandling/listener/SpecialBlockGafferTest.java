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
// stats would miss it: Architect reports each new special block to TheGaffer. A sneak-edit changes the clicked block
// instead, so it is not a new block, and TheGaffer is asked about the clicked block. One mock/load per class, as in
// SpecialBlockListenerTest.
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
    }

    // The item as the block inventory makes it: the tag, then the special block's id.
    private void rightClick(String specialBlock, Block clicked, BlockFace face) {
        ItemStack item = new ItemStack(Material.STONE);
        ItemMeta meta = item.getItemMeta();
        meta.setLore(List.of(SpecialBlockInventoryData.SPECIAL_BLOCK_TAG, "testrp/" + specialBlock));
        item.setItemMeta(meta);
        assertNotNull(SpecialBlockInventoryData.getSpecialBlockDataFromItem(item), "a special block item");
        player.getInventory().setItemInMainHand(item);
        new SpecialBlockListener().placeSpecialBlock(
                new PlayerInteractEvent(player, Action.RIGHT_CLICK_BLOCK, item, clicked, face, EquipmentSlot.HAND));
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
}
