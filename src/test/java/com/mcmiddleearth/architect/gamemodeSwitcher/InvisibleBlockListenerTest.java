package com.mcmiddleearth.architect.gamemodeSwitcher;

import com.mcmiddleearth.architect.ArchitectPlugin;
import com.mcmiddleearth.architect.Modules;
import com.mcmiddleearth.architect.PluginData;
import com.mcmiddleearth.architect.specialBlockHandling.data.SpecialBlockInventoryData;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.permissions.Permission;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockbukkit.mockbukkit.world.WorldMock;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

// Barrier, light and structure void are placed only with architect.invisibleBlocks: the game mode switcher shows
// builders the creative inventory's Operator Items tab, which holds them. The events go through every listener, as on
// a server, and nothing ticks, so no listener's later task runs. One mock/load per class, as Architect caches
// data-folder paths in static fields.
class InvisibleBlockListenerTest {

    private static final String REFUSAL = "You can't place invisible blocks (barrier, light, structure void).";

    private static ServerMock server;
    private static ArchitectPlugin plugin;
    private static WorldMock world;
    private static WorldMock worldWithoutSwitcher;
    private static int nextX;

    @BeforeAll
    static void setUp() throws IOException {
        server = MockBukkit.mock();
        plugin = MockBukkit.load(ArchitectPlugin.class);
        // MockBukkit does not register plugin.yml's permissions, as a server does; "default: op" needs them
        for (Permission permission : plugin.getDescription().getPermissions()) {
            if (server.getPluginManager().getPermission(permission.getName()) == null) {
                server.getPluginManager().addPermission(permission);
            }
        }
        world = server.addSimpleWorld("world");
        worldWithoutSwitcher = server.addSimpleWorld("noSwitcher");
        PluginData.setModuleEnabled(worldWithoutSwitcher, Modules.GAMEMODE_SWITCHER, false);
        // the entry the RP packs' /inv has in inventories/misc.yml
        Path rp = SpecialBlockInventoryData.configFolder.toPath().resolve("testrp");
        Files.createDirectories(rp);
        Files.writeString(rp.resolve("misc.yml"), """
                Items:
                  light:
                    itemMaterial: LIGHT
                    display: Light Block
                    type: VANILLA
                    blockData: minecraft:light
                    category: Misc
                """);
        SpecialBlockInventoryData.loadInventories();
    }

    @AfterAll
    static void tearDown() {
        if (MockBukkit.isMocked()) {
            MockBukkit.unmock();
        }
    }

    private static PlayerMock join() {
        PlayerMock player = server.addPlayer();
        while (player.nextMessage() != null) {
            // what joining said
        }
        return player;
    }

    private static BlockPlaceEvent place(PlayerMock player, WorldMock in, Material material, boolean cancelled) {
        return place(player, in, new ItemStack(material), cancelled);
    }

    /** Places the item's block as the server does: the event fires with the block already in the world. */
    private static BlockPlaceEvent place(PlayerMock player, WorldMock in, ItemStack item, boolean cancelled) {
        Block block = in.getBlockAt(nextX++, 64, 0);
        BlockState replaced = block.getState();
        block.setType(item.getType());
        player.getInventory().setItemInMainHand(item);
        BlockPlaceEvent event = new BlockPlaceEvent(block, replaced, block.getRelative(BlockFace.DOWN), item, player,
                true, EquipmentSlot.HAND);
        event.setCancelled(cancelled);
        server.getPluginManager().callEvent(event);
        return event;
    }

    @ParameterizedTest
    @EnumSource(value = Material.class, names = {"BARRIER", "LIGHT", "STRUCTURE_VOID"})
    void aPlayerWithoutThePermissionMayNotPlaceIt(Material invisible) {
        PlayerMock builder = join();

        BlockPlaceEvent event = place(builder, world, invisible, false);

        assertTrue(event.isCancelled(), invisible + " is refused");
        String message = builder.nextMessage();
        assertNotNull(message, "they are told");
        assertTrue(message.contains(REFUSAL), message);
        assertNull(builder.nextMessage(), "once");
    }

    @ParameterizedTest
    @EnumSource(value = Material.class, names = {"BARRIER", "LIGHT", "STRUCTURE_VOID"})
    void aPlayerWithThePermissionAndAnOpMayPlaceIt(Material invisible) {
        PlayerMock allowed = join();
        allowed.addAttachment(plugin, "architect.invisibleBlocks", true);
        PlayerMock op = join();
        op.setOp(true);

        for (PlayerMock player : List.of(allowed, op)) {
            BlockPlaceEvent event = place(player, world, invisible, false);

            assertFalse(event.isCancelled(), player.getName() + " places " + invisible);
            assertNull(player.nextMessage(), player.getName());
        }
    }

    // The RP packs' /inv has a Light Block of type VANILLA, which Architect leaves to vanilla to place, so its place
    // event comes here. Builders keep it; the plain item, from the Operator Items tab, pick-block or /give, they don't.
    @Test
    void theLightBlockFromArchitectsInventoryIsLetThrough() {
        ItemStack fromInventory = SpecialBlockInventoryData.getItem(SpecialBlockInventoryData.getSpecialBlock(
                "testrp/light"));
        assertNotNull(fromInventory, "the /inv item");
        assertEquals(Material.LIGHT, fromInventory.getType());
        PlayerMock builder = join();

        BlockPlaceEvent event = place(builder, world, fromInventory, false);

        assertFalse(event.isCancelled(), "placed");
        assertNull(builder.nextMessage(), "and nothing said");
        assertTrue(place(builder, world, new ItemStack(Material.LIGHT), false).isCancelled(), "the plain item");
    }

    @Test
    void stoneIsNeverTouched() {
        PlayerMock builder = join();

        BlockPlaceEvent event = place(builder, world, Material.STONE, false);

        assertFalse(event.isCancelled());
        assertNull(builder.nextMessage());
    }

    // The tab is the reason, but the rule holds in every world, whether the switcher is on there or not.
    @Test
    void alsoWhereTheSwitcherIsOff() {
        PlayerMock builder = join();

        assertTrue(place(builder, worldWithoutSwitcher, Material.BARRIER, false).isCancelled());
    }

    // A placement another plugin stopped first is left alone, with no second message.
    @Test
    void aPlacementAlreadyCancelledIsLeftAlone() {
        PlayerMock builder = join();

        place(builder, world, Material.BARRIER, true);

        assertNull(builder.nextMessage());
    }
}
