package com.mcmiddleearth.architect.armorStand;

import com.mcmiddleearth.architect.ArchitectPlugin;
import com.mcmiddleearth.util.FakeGaffer;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityPlaceEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockbukkit.mockbukkit.world.WorldMock;

import static org.junit.jupiter.api.Assertions.*;

// An armor stand is checked with TheGaffer where it ends up: where /armor paste and the stick's paste put it, where a
// move takes it, and where a vanilla armor stand item lands. FakeGaffer allows blocks by their X here, as a job area
// would. One mock/load per class.
class ArmorStandBuildCheckTest {

    private static ServerMock server;
    private static WorldMock world;
    private static PlayerMock player;

    @BeforeAll
    static void setUp() {
        server = MockBukkit.mock();
        MockBukkit.load(ArchitectPlugin.class);
        MockBukkit.loadWith(FakeGaffer.class, FakeGaffer.description());
        world = server.addSimpleWorld("world"); // before the first player config, which reads the first world
        player = server.addPlayer();
        player.setOp(true);
    }

    @AfterAll
    static void tearDown() {
        if (MockBukkit.isMocked()) {
            MockBukkit.unmock();
        }
    }

    @BeforeEach
    void reset() {
        world.getEntities().stream().filter(ArmorStand.class::isInstance).forEach(Entity::remove);
        FakeGaffer.asked.clear();
        FakeGaffer.builds.clear();
        FakeGaffer.allowed = location -> true;
        player.getInventory().setItemInMainHand(new ItemStack(Material.STICK));
        player.teleport(new Location(world, 0.5, 64, 0.5));
        drainChat();
    }

    private static long stands() {
        return world.getEntities().stream().filter(entity -> entity instanceof ArmorStand && entity.isValid()).count();
    }

    private static void mode(ArmorStandEditorMode mode) {
        ArmorStandEditorCommand.getPlayerConfig(player).setEditorMode(mode);
    }

    private static void step(int step) {
        ArmorStandEditorCommand.getPlayerConfig(player).setRotationStep(step);
    }

    private static Block stone(int x, int y, int z) {
        Block block = world.getBlockAt(x, y, z);
        block.setType(Material.STONE);
        return block;
    }

    private static void click(Action action, Block block) {
        new ArmorStandListener().PlayerInteract(new PlayerInteractEvent(player, action,
                player.getInventory().getItemInMainHand(), block, BlockFace.UP, EquipmentSlot.HAND));
    }

    // A right-click: every EntityDamageByEntityEvent constructor, the left-click, is deprecated for removal.
    private static void rightClick(ArmorStand stand) {
        new ArmorStandListener().PlayerInteractAtEntity(
                new PlayerInteractAtEntityEvent(player, stand, new Vector(), EquipmentSlot.HAND));
    }

    private static EntityPlaceEvent place(ArmorStand stand) {
        EntityPlaceEvent event = new EntityPlaceEvent(stand, player,
                stand.getLocation().getBlock().getRelative(BlockFace.DOWN), BlockFace.UP, EquipmentSlot.HAND);
        new ArmorStandListener().placeVanillaArmorStand(event);
        return event;
    }

    private static String drainChat() {
        StringBuilder chat = new StringBuilder();
        for (String message = player.nextMessage(); message != null; message = player.nextMessage()) {
            chat.append(message).append('\n');
        }
        return chat.toString();
    }

    // A guard: inside the area, /armor paste places a stand at the player, as before.
    @Test
    void aPasteInsideTheAreaPlacesAStand() {
        player.teleport(new Location(world, 10.5, 64, 0.5));

        player.performCommand("armor paste");

        assertEquals(1, stands());
    }

    // A guard: inside the area, the stick's right-click paste places a stand above the clicked block, as before.
    @Test
    void aStickPasteInsideTheAreaPlacesAStand() {
        mode(ArmorStandEditorMode.PASTE);

        click(Action.RIGHT_CLICK_BLOCK, stone(12, 63, 0));

        assertEquals(1, stands());
    }

    @Test
    void aPastedStandIsCheckedWhereItAppears() {
        player.teleport(new Location(world, 30.5, 64, 0.5));
        FakeGaffer.allowed = location -> location.getBlockX() < 30;

        player.performCommand("armor paste");

        assertEquals(0, stands(), "no stand outside the area");
        assertTrue(drainChat().contains("You are not in the job's area."), "told why");
    }

    @Test
    void aLeftClickPasteIsCheckedAtThePlayer() {
        mode(ArmorStandEditorMode.PASTE);
        player.teleport(new Location(world, 30.5, 64, 0.5));
        FakeGaffer.allowed = location -> location.getBlockX() < 30;

        click(Action.LEFT_CLICK_BLOCK, stone(26, 63, 0)); // the clicked block is inside, the player is not

        assertEquals(0, stands(), "no stand at the player, outside the area");
    }

    @Test
    void aRightClickPasteIsCheckedOneBlockAbove() {
        mode(ArmorStandEditorMode.PASTE);
        FakeGaffer.allowed = location -> !(location.getBlockX() == 20 && location.getBlockY() == 64
                && location.getBlockZ() == 0);

        click(Action.RIGHT_CLICK_BLOCK, stone(20, 63, 0));

        assertEquals(0, stands(), "no stand on the refused block");
        assertTrue(FakeGaffer.asked.stream().anyMatch(location -> location.getBlockX() == 20
                && location.getBlockY() == 64), "asked about the block above: " + FakeGaffer.asked);
    }

    @Test
    void aMoveCannotTakeAStandOutOfTheArea() {
        ArmorStand stand = world.spawn(new Location(world, 20.5, 64, 0.5), ArmorStand.class);
        mode(ArmorStandEditorMode.XMOVE);
        step(100); // one block per click
        FakeGaffer.allowed = location -> location.getBlockX() <= 20;

        rightClick(stand);

        assertEquals(20.5, stand.getLocation().getX(), 1e-9, "the stand stays");
        assertTrue(drainChat().contains("You are not in the job's area."), "told why");
    }

    // A guard: a move inside the area works as before.
    @Test
    void aMoveInsideTheAreaStillWorks() {
        ArmorStand stand = world.spawn(new Location(world, 20.5, 64, 0.5), ArmorStand.class);
        mode(ArmorStandEditorMode.XMOVE);
        step(100);

        rightClick(stand);

        assertEquals(21.5, stand.getLocation().getX(), 1e-9);
    }

    @Test
    void aVanillaStandIsCheckedWhereItLands() {
        FakeGaffer.allowed = location -> location.getBlockX() < 30;

        EntityPlaceEvent event = place(world.spawn(new Location(world, 30.5, 64, 0.5), ArmorStand.class));

        assertTrue(event.isCancelled(), "not placed outside the area");
    }

    // A guard: a vanilla stand inside the area is placed as before.
    @Test
    void aVanillaStandInsideTheAreaIsPlaced() {
        FakeGaffer.allowed = location -> location.getBlockX() < 30;

        EntityPlaceEvent event = place(world.spawn(new Location(world, 20.5, 64, 0.5), ArmorStand.class));

        assertFalse(event.isCancelled());
    }
}
