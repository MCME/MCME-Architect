package com.mcmiddleearth.architect.specialBlockHandling.listener;

import com.mcmiddleearth.architect.ArchitectPlugin;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.block.BlockMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockbukkit.mockbukkit.world.WorldMock;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

// With flint, sneak + left-click on blocks collects their states, and sneak + left-click in the air prints them as one
// clickable line. With no WorldEdit preset, clicking it copies the list to the clipboard. PluginUtils sends clickable
// lines as /tellraw from the console, as in NoPhysicsCommandTest, so this sees the click Architect asks for, not what a
// client makes of it: that is for an in-game check. One mock/load per class, as in LogFileTest.
class BlockPickerListenerTest {

    private static ServerMock server;
    private static WorldMock world;
    private static PlayerMock player;
    private static final List<String> tellraw = new ArrayList<>();

    @BeforeAll
    static void setUp() {
        server = MockBukkit.mock();
        MockBukkit.load(ArchitectPlugin.class);
        server.getCommandMap().register("minecraft", new Command("tellraw") {
            @Override
            public boolean execute(CommandSender sender, String label, String[] args) {
                tellraw.add(String.join(" ", args));
                return true;
            }
        });
        world = server.addSimpleWorld("world");
        // A click in the air picks the block looked at, which MockBukkit cannot find.
        player = new PlayerMock(server, "Designer") {
            @Override
            public Block getTargetBlock(Set<Material> transparent, int maxDistance) {
                return world.getBlockAt(0, 80, 0);
            }
        };
        server.addPlayer(player);
    }

    @AfterAll
    static void tearDown() {
        if (MockBukkit.isMocked()) {
            MockBukkit.unmock();
        }
    }

    @BeforeEach
    void reset() {
        tellraw.clear();
        player.setSneaking(false);
    }

    private static void leftClick(Action action, Block block) {
        server.getPluginManager().callEvent(new PlayerInteractEvent(player, action,
                player.getInventory().getItemInMainHand(), block, BlockFace.UP, EquipmentSlot.HAND));
    }

    // On Paper, the first read of a block's legacy data value builds the whole table of legacy materials, on the main
    // thread, which once held a server up for more than ten seconds. This block's legacy data value cannot be read.
    private static Block withoutLegacyData(Material material, int x) {
        return new BlockMock(material, new Location(world, x, 64, 0)) {
            @Override
            public byte getData() {
                throw new UnsupportedOperationException("the legacy data value was read");
            }
        };
    }

    // One part of a clickable line, as PluginUtils writes it for /tellraw.
    private static String part(String text, String color, String suggestion) {
        return "{\"text\":\"" + text + "\",\"color\":\"" + color
                + "\",\"clickEvent\":{\"action\":\"suggest_command\",\"value\":\"" + suggestion + "\"}}";
    }

    // Without sneaking, a left-click on a block shows its material, then each of its attributes, the values in green.
    // Each line suggests the block's data when clicked; with no WorldEdit preset, the data alone. MockBukkit has no
    // legacy data values either: asked for one, it throws an exception that would only skip the test.
    @Test
    void theDataOfABlockIsShownWithoutItsLegacyDataValue() {
        Block stairs = withoutLegacyData(Material.OAK_STAIRS, 4);
        player.getInventory().setItemInMainHand(new ItemStack(Material.FLINT));

        assertDoesNotThrow(() -> leftClick(Action.LEFT_CLICK_BLOCK, stairs), "nothing legacy is read");

        String suggestion = " " + stairs.getBlockData().getAsString();
        String lines = String.join("\n", tellraw);
        assertTrue(tellraw.stream().anyMatch(line -> line.endsWith(part("Material: ", "aqua", suggestion) + ","
                + part("OAK_STAIRS", "green", suggestion) + "]")), lines);
        assertTrue(tellraw.stream().anyMatch(line -> line.endsWith(part("Waterlogged: ", "aqua", suggestion) + ","
                + part("false", "green", suggestion) + "]")), lines);
        assertTrue(tellraw.stream().noneMatch(line -> line.contains("old(")), lines);
    }

    @Test
    void theSneakListIsCopiedToTheClipboardWhenThereIsNoPreset() {
        Block stairs = world.getBlockAt(0, 64, 0);
        stairs.setType(Material.OAK_STAIRS);
        player.getInventory().setItemInMainHand(new ItemStack(Material.FLINT));
        player.setSneaking(true);

        leftClick(Action.LEFT_CLICK_BLOCK, stairs);
        leftClick(Action.LEFT_CLICK_AIR, null);

        assertEquals(1, tellraw.size(), tellraw.toString());
        String line = tellraw.get(0);
        assertTrue(line.contains("oak_stairs"), line);
        assertTrue(line.contains("\"action\":\"copy_to_clipboard\""), line);
    }
}
