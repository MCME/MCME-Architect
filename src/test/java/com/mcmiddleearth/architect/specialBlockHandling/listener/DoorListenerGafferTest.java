package com.mcmiddleearth.architect.specialBlockHandling.listener;

import com.mcmiddleearth.architect.ArchitectPlugin;
import com.mcmiddleearth.util.FakeGaffer;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.data.Bisected;
import org.bukkit.block.data.type.Door;
import org.bukkit.event.block.BlockMultiPlaceEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockbukkit.mockbukkit.world.WorldMock;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

// DoorListener cancels a vanilla door's place and sets a powered door itself. Architect loads at STARTUP, so its
// listeners run before TheGaffer's at the same priority: TheGaffer sees the place already cancelled and never
// counts it, so DoorListener reports the door. One mock/load per class, as in SpecialBlockGafferTest.
class DoorListenerGafferTest {

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
        FakeGaffer.builds.clear();
        FakeGaffer.allowed = location -> true;
    }

    @AfterEach
    void dropTheDelayedDoor() {
        // the door is set a tick later through BlockState.getRawData, which MockBukkit does not implement
        server.getScheduler().cancelTasks(ArchitectPlugin.getPluginInstance());
    }

    // The event fires with the door already in the world, as vanilla does.
    private BlockMultiPlaceEvent placeOakDoor(Block ground) {
        ground.setType(Material.DIRT);
        Block lower = ground.getRelative(BlockFace.UP);
        Block upper = lower.getRelative(BlockFace.UP);
        List<BlockState> replaced = List.of(lower.getState(), upper.getState());
        Door bottom = (Door) Material.OAK_DOOR.createBlockData();
        bottom.setHalf(Bisected.Half.BOTTOM);
        Door top = (Door) Material.OAK_DOOR.createBlockData();
        top.setHalf(Bisected.Half.TOP);
        lower.setBlockData(bottom);
        upper.setBlockData(top);
        ItemStack door = new ItemStack(Material.OAK_DOOR);
        player.getInventory().setItemInMainHand(door);
        BlockMultiPlaceEvent event = new BlockMultiPlaceEvent(replaced, ground, door, player, true,
                EquipmentSlot.HAND);
        new DoorListener().vanillaDoorPlace(event);
        return event;
    }

    @Test
    void aVanillaDoorIsReportedOnceAtItsLowerHalf() {
        Block ground = world.getBlockAt(0, 64, 0);

        BlockMultiPlaceEvent event = placeOakDoor(ground);

        assertTrue(event.isCancelled(), "Architect places the door itself");
        assertEquals(List.of(new FakeGaffer.Build(player.getName(), ground.getRelative(BlockFace.UP).getLocation(),
                true)), FakeGaffer.builds);
    }

    @Test
    void aVanillaDoorTheGafferRefusesIsNotReported() {
        Block ground = world.getBlockAt(4, 64, 0);
        FakeGaffer.allowed = location -> false;

        BlockMultiPlaceEvent event = placeOakDoor(ground);

        assertFalse(FakeGaffer.asked.isEmpty(), "TheGaffer was asked");
        assertFalse(event.isCancelled(), "left for TheGaffer to refuse");
        assertEquals(List.of(), FakeGaffer.builds);
    }
}
