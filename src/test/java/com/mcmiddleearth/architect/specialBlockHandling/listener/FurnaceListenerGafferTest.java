package com.mcmiddleearth.architect.specialBlockHandling.listener;

import com.mcmiddleearth.architect.ArchitectPlugin;
import com.mcmiddleearth.util.FakeGaffer;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.Furnace;
import org.bukkit.event.Event;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockbukkit.mockbukkit.inventory.PlayerInventoryViewMock;
import org.mockbukkit.mockbukkit.world.WorldMock;

import static org.junit.jupiter.api.Assertions.*;

// The burning-furnace click lights or puts out a furnace, smoker or blast furnace only where the player may build,
// and only on the furnace's first slot. The handler is called directly: through the server, the container protection
// would refuse the click first. One mock/load per class.
class FurnaceListenerGafferTest {

    private static ServerMock server;
    private static WorldMock world;
    private static PlayerMock player;
    private static int nextX = 0;

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
        player.getInventory().clear();
    }

    @AfterEach
    void dropDelayedTasks() {
        server.getScheduler().cancelTasks(ArchitectPlugin.getPluginInstance());
    }

    // A lit furnace of this type, its live state, with something to smelt (MockBukkit reads an empty slot as null).
    private static Furnace litFurnace(Material type) {
        Block block = world.getBlockAt(nextX++ * 2, 64, 0);
        block.setType(type);
        Furnace furnace = (Furnace) block.getState();
        furnace.getInventory().setItem(0, new ItemStack(Material.COD));
        furnace.setBurnTime((short) 200);
        return furnace;
    }

    private static InventoryClickEvent click(Furnace furnace, int raw) {
        InventoryClickEvent click = new InventoryClickEvent(new PlayerInventoryViewMock(player, furnace.getInventory()),
                raw == 0 ? InventoryType.SlotType.CONTAINER : InventoryType.SlotType.QUICKBAR, raw, ClickType.LEFT,
                InventoryAction.PICKUP_ALL);
        click.setResult(Event.Result.ALLOW); // as Paper fires a click
        return click;
    }

    @ParameterizedTest
    @EnumSource(value = Material.class, names = {"FURNACE", "SMOKER", "BLAST_FURNACE"})
    void aFurnaceOutsideTheBuildAreaIsNotSwitched(Material type) {
        FakeGaffer.allowed = location -> false;
        Furnace furnace = litFurnace(type);

        new FurnaceListener().furnaceOnOff(click(furnace, 0));

        assertEquals(200, furnace.getBurnTime(), "not switched");
        assertFalse(FakeGaffer.asked.isEmpty(), "TheGaffer was asked");
    }

    // A guard: where the player may build, the click puts a lit furnace out, as before.
    @Test
    void aFurnaceInsideTheBuildAreaIsSwitched() {
        Furnace furnace = litFurnace(Material.FURNACE);

        new FurnaceListener().furnaceOnOff(click(furnace, 0));

        assertEquals(-1, furnace.getBurnTime(), "put out");
    }

    @Test
    void theFirstHotbarSlotDoesNotSwitchAFurnace() {
        player.getInventory().setItem(0, new ItemStack(Material.STICK));
        Furnace furnace = litFurnace(Material.FURNACE);

        new FurnaceListener().furnaceOnOff(click(furnace, 30)); // raw 30: hotbar slot 0, below the furnace's 3 slots

        assertEquals(200, furnace.getBurnTime(), "not switched");
    }
}
