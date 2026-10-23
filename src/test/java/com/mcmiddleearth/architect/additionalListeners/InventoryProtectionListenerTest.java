package com.mcmiddleearth.architect.additionalListeners;

import com.mcmiddleearth.architect.ArchitectPlugin;
import com.mcmiddleearth.architect.Modules;
import com.mcmiddleearth.architect.PluginData;
import com.mcmiddleearth.util.FakeGaffer;
import io.papermc.paper.event.player.PlayerPickBlockEvent;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.Container;
import org.bukkit.block.DoubleChest;
import org.bukkit.entity.Donkey;
import org.bukkit.entity.minecart.StorageMinecart;
import org.bukkit.event.Event;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.permissions.PermissionAttachment;
import org.junit.jupiter.api.AfterAll;
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

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

// Where protection.chestInventory is on, a player who may not build at a storage container can look into it but
// cannot move an item into or out of it, and closing it changes nothing. Windows that are not world storage are left
// to vanilla. MockBukkit never moves items for a click or a drag, so those tests check that the event is cancelled.
// FakeGaffer refuses every location unless a test says otherwise. One mock/load per class.
class InventoryProtectionListenerTest {

    private static ServerMock server;
    private static ArchitectPlugin plugin;
    private static WorldMock world;
    private static PlayerMock player;
    private static int nextX = 0;

    @BeforeAll
    static void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.load(ArchitectPlugin.class);
        MockBukkit.loadWith(FakeGaffer.class, FakeGaffer.description());
        world = server.addSimpleWorld("world");
        player = server.addPlayer(); // not an op, so without the bypass node
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
        FakeGaffer.allowed = location -> false; // no build permission anywhere
    }

    // A storage block of this type with 5 diamonds in its first slot; the inventory's holder is the block's state.
    private static Inventory storage(Material type) {
        Block block = world.getBlockAt(nextX++ * 2, 64, 0);
        block.setType(type);
        Inventory inventory = ((Container) block.getState()).getInventory();
        inventory.setItem(0, new ItemStack(Material.DIAMOND, 5));
        return inventory;
    }

    // The window as the player sees it. Whether it may open is InventoryListener's business, not this listener's.
    private static InventoryView storageView(Material type) {
        return new PlayerInventoryViewMock(player, storage(type));
    }

    private static InventoryClickEvent click(InventoryView view, int raw, ClickType type, InventoryAction action) {
        InventoryClickEvent event = new InventoryClickEvent(view, InventoryType.SlotType.CONTAINER, raw, type, action);
        event.setResult(Event.Result.ALLOW); // as Paper fires a click
        server.getPluginManager().callEvent(event);
        return event;
    }

    private static InventoryDragEvent drag(InventoryView view, Integer... raws) {
        Map<Integer, ItemStack> slots = new HashMap<>();
        for (int raw : raws) {
            slots.put(raw, new ItemStack(Material.STONE));
        }
        InventoryDragEvent event = new InventoryDragEvent(view, null, new ItemStack(Material.STONE, 2), false, slots);
        server.getPluginManager().callEvent(event);
        return event;
    }

    private static PlayerPickBlockEvent pick(Block block, boolean includeData) {
        PlayerPickBlockEvent event = new PlayerPickBlockEvent(player, block, new ItemStack(block.getType()),
                includeData, 0, -1);
        new InventoryProtectionListener().keepCopiedStorageEmpty(event);
        return event;
    }

    @ParameterizedTest
    @EnumSource(value = InventoryAction.class, names = {"PICKUP_ALL", "PICKUP_HALF", "PLACE_ALL", "SWAP_WITH_CURSOR",
            "MOVE_TO_OTHER_INVENTORY", "HOTBAR_SWAP", "DROP_ONE_SLOT", "CLONE_STACK", "COLLECT_TO_CURSOR",
            "PICKUP_ALL_INTO_BUNDLE", "PLACE_FROM_BUNDLE", "UNKNOWN"})
    void anItemInAStorageContainerStaysWhereItIs(InventoryAction action) {
        assertTrue(click(storageView(Material.CHEST), 0, ClickType.LEFT, action).isCancelled(), action.name());
    }

    // From the player's own side, these two still reach into the container.
    @Test
    void shiftAndDoubleClicksFromThePlayersSideAreRefused() {
        InventoryView view = storageView(Material.CHEST);
        int top = view.getTopInventory().getSize();

        assertTrue(click(view, top, ClickType.SHIFT_LEFT, InventoryAction.MOVE_TO_OTHER_INVENTORY).isCancelled());
        assertTrue(click(view, top, ClickType.DOUBLE_CLICK, InventoryAction.COLLECT_TO_CURSOR).isCancelled());
    }

    // A guard: the player's own slots, and a click outside the window, work as in vanilla.
    @Test
    void thePlayersOwnSlotsStillWork() {
        InventoryView view = storageView(Material.CHEST);
        int top = view.getTopInventory().getSize();
        for (InventoryAction action : List.of(InventoryAction.PICKUP_ALL, InventoryAction.PLACE_ALL,
                InventoryAction.HOTBAR_SWAP)) {
            assertFalse(click(view, top, ClickType.LEFT, action).isCancelled(), action.name());
        }
        InventoryClickEvent outside = new InventoryClickEvent(view, InventoryType.SlotType.OUTSIDE, -999,
                ClickType.LEFT, InventoryAction.DROP_ALL_CURSOR);
        outside.setResult(Event.Result.ALLOW);
        server.getPluginManager().callEvent(outside);
        assertFalse(outside.isCancelled(), "a click outside the window");
    }

    @Test
    void aDragIntoTheContainerIsRefused() {
        InventoryView view = storageView(Material.CHEST);
        int top = view.getTopInventory().getSize();

        assertTrue(drag(view, 0, top).isCancelled(), "a drag that covers a container slot");
        assertFalse(drag(view, top, top + 1).isCancelled(), "a drag over the player's own slots only");
    }

    // The container's last slot is clicked: a furnace's first slot is FurnaceListener's switch, which cancels too.
    @ParameterizedTest
    @EnumSource(value = Material.class, names = {"CHEST", "TRAPPED_CHEST", "BARREL", "SHULKER_BOX", "HOPPER",
            "DROPPER", "DISPENSER", "CRAFTER", "FURNACE", "SMOKER", "BLAST_FURNACE", "BREWING_STAND", "LECTERN"})
    void everyKindOfStorageBlockIsProtected(Material type) {
        InventoryView view = storageView(type);
        int last = view.getTopInventory().getSize() - 1;

        assertTrue(click(view, last, ClickType.LEFT, InventoryAction.PICKUP_ALL).isCancelled(), type.name());
    }

    @Test
    void storageEntitiesAndLargeChestsAreProtected() {
        Location at = new Location(world, 100, 64, 0);
        Inventory cart = world.spawn(at, StorageMinecart.class).getInventory();
        Inventory donkey = server.createInventory(world.spawn(at, Donkey.class), 18);
        for (Inventory inventory : List.of(cart, donkey)) {
            assertTrue(click(new PlayerInventoryViewMock(player, inventory), 0, ClickType.LEFT,
                    InventoryAction.PICKUP_ALL).isCancelled(), String.valueOf(inventory.getHolder()));
        }
        assertTrue(InventoryProtectionListener.isWorldStorage(new DoubleChest(null)), "a large chest");
    }

    // A guard: the player's ender chest, workstations and plugin windows are left to vanilla.
    @Test
    void windowsThatAreNotWorldStorageAreLeftToVanilla() {
        for (Inventory inventory : List.of(player.getEnderChest(), server.createInventory(null, InventoryType.WORKBENCH),
                server.createInventory(player, 54, "Blocks"))) {
            assertFalse(click(new PlayerInventoryViewMock(player, inventory), 0, ClickType.LEFT,
                    InventoryAction.PICKUP_ALL).isCancelled(), inventory.getType().name());
        }
    }

    // A guard: a player who may build there moves items in every way, as before.
    @ParameterizedTest
    @EnumSource(value = InventoryAction.class, names = {"PICKUP_ALL", "PICKUP_HALF", "PLACE_ALL", "SWAP_WITH_CURSOR",
            "MOVE_TO_OTHER_INVENTORY", "HOTBAR_SWAP", "DROP_ONE_SLOT", "CLONE_STACK", "COLLECT_TO_CURSOR",
            "PICKUP_ALL_INTO_BUNDLE", "PLACE_FROM_BUNDLE", "UNKNOWN"})
    void aPlayerWhoMayBuildThereMovesItemsFreely(InventoryAction action) {
        FakeGaffer.allowed = location -> true;

        assertFalse(click(storageView(Material.CHEST), 0, ClickType.LEFT, action).isCancelled(), action.name());
        assertFalse(FakeGaffer.asked.isEmpty(), "TheGaffer was asked");
    }

    // A guard: the bypass node, and the module switched off, lift the protection.
    @Test
    void theBypassNodeAndTheModuleSwitchLiftTheProtection() {
        PermissionAttachment bypass = player.addAttachment(plugin, "architect.inventory.ignoreProtection", true);
        assertFalse(click(storageView(Material.CHEST), 0, ClickType.LEFT, InventoryAction.PICKUP_ALL).isCancelled(),
                "with the bypass node");
        player.removeAttachment(bypass);

        PluginData.setModuleEnabled(player.getWorld(), Modules.CHEST_INVENTORY_PROTECTION, false);
        try {
            assertFalse(click(storageView(Material.CHEST), 0, ClickType.LEFT, InventoryAction.PICKUP_ALL)
                    .isCancelled(), "with the module off");
        } finally {
            PluginData.setModuleEnabled(player.getWorld(), Modules.CHEST_INVENTORY_PROTECTION, true);
        }
    }

    @Test
    void closingAProtectedChestChangesNothing() {
        Inventory chest = storage(Material.CHEST);
        assertNotNull(player.openInventory(chest), "the window opens");

        chest.setItem(0, null);
        player.closeInventory();

        assertNull(chest.getItem(0), "nothing put back on close");
    }

    @Test
    void anEnderChestKeepsWhatThePlayerTook() {
        Inventory ender = player.getEnderChest();
        ender.setItem(0, new ItemStack(Material.DIAMOND));
        assertNotNull(player.openInventory(ender), "the window opens");

        ender.setItem(0, null);
        player.closeInventory();

        assertNull(ender.getItem(0), "nothing put back on close");
    }

    // In creative, picking a block with its data copies a container with its contents, without opening it.
    @Test
    void aCreativeCopyWithContentsOfProtectedStorageIsRefused() {
        Block chest = world.getBlockAt(200, 64, 0);
        chest.setType(Material.CHEST);
        Block stone = world.getBlockAt(202, 64, 0);
        stone.setType(Material.STONE);

        assertTrue(pick(chest, true).isCancelled(), "a chest with its contents");
        assertFalse(pick(chest, false).isCancelled(), "an empty chest");
        assertFalse(pick(stone, true).isCancelled(), "a block that is no storage");
        FakeGaffer.allowed = location -> true;
        assertFalse(pick(chest, true).isCancelled(), "where the player may build");
    }
}
