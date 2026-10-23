package com.mcmiddleearth.architect.additionalListeners;

import com.mcmiddleearth.architect.ArchitectPlugin;
import com.mcmiddleearth.util.FakeGaffer;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Lectern;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerEditBookEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerTakeLecternBookEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockbukkit.mockbukkit.world.WorldMock;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

// A powered lectern is left to vanilla. Placing a book on a lectern and taking one off work as before. One mock/load
// per class.
class LecternProtectionListenerTest {

    private static ServerMock server;
    private static WorldMock world;

    // Records the books it is shown; MockBukkit's openBook is not implemented and would skip the test.
    private static final class Reader extends PlayerMock {
        final List<ItemStack> opened = new ArrayList<>();

        Reader(ServerMock server, String name) {
            super(server, name);
        }

        @Override
        public void openBook(ItemStack book) {
            opened.add(book);
        }
    }

    @BeforeAll
    static void setUp() {
        server = MockBukkit.mock();
        MockBukkit.load(ArchitectPlugin.class);
        MockBukkit.loadWith(FakeGaffer.class, FakeGaffer.description());
        world = server.addSimpleWorld("world");
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

    // A lectern at (x, 64, 0), powered or not, with this book on it or none.
    private static Block lectern(int x, boolean powered, ItemStack book) {
        Block block = world.getBlockAt(x, 64, 0);
        block.setType(Material.LECTERN);
        org.bukkit.block.data.type.Lectern data = (org.bukkit.block.data.type.Lectern) block.getBlockData();
        data.setPowered(powered);
        data.setHasBook(book != null);
        block.setBlockData(data);
        if (book != null) {
            ((Lectern) block.getState()).getInventory().setItem(0, book);
        }
        return block;
    }

    @Test
    void aPoweredLecternIsLeftToVanilla() {
        Reader reader = new Reader(server, "Reader");
        server.addPlayer(reader);
        Block block = lectern(0, true, new ItemStack(Material.WRITTEN_BOOK));
        int hooks = PlayerEditBookEvent.getHandlerList().getRegisteredListeners().length;
        PlayerInteractEvent click = new PlayerInteractEvent(reader, Action.RIGHT_CLICK_BLOCK, null, block,
                BlockFace.UP, EquipmentSlot.HAND);

        new LecternProtectionListener().placeBookLectern(click);

        assertFalse(click.isCancelled(), "left to vanilla");
        assertEquals(List.of(), reader.opened, "no book opened");
        assertEquals(hooks, PlayerEditBookEvent.getHandlerList().getRegisteredListeners().length, "no edit hook");
    }

    // A guard: a builder places one book on an empty lectern, as before.
    @Test
    void aBookIsPlacedOnAnEmptyLectern() {
        PlayerMock builder = server.addPlayer();
        builder.setGameMode(GameMode.SURVIVAL);
        ItemStack books = new ItemStack(Material.WRITABLE_BOOK, 2);
        builder.getInventory().setItemInMainHand(books);
        Block block = lectern(2, false, null);
        PlayerInteractEvent click = new PlayerInteractEvent(builder, Action.RIGHT_CLICK_BLOCK, books, block,
                BlockFace.UP, EquipmentSlot.HAND);

        new LecternProtectionListener().placeBookLectern(click);

        assertTrue(click.isCancelled());
        ItemStack placed = ((Lectern) block.getState()).getInventory().getItem(0);
        assertNotNull(placed, "a book on the lectern");
        assertEquals(Material.WRITABLE_BOOK, placed.getType());
        assertEquals(1, books.getAmount(), "one book left the stack in survival");
    }

    // A guard: taking a book where the player may not build gives a copy and leaves the book, as before.
    @Test
    void takingABookOutsideTheBuildAreaGivesACopy() {
        FakeGaffer.allowed = location -> false;
        PlayerMock visitor = server.addPlayer();
        Block block = lectern(4, false, new ItemStack(Material.WRITTEN_BOOK));
        Lectern state = (Lectern) block.getState();

        new LecternProtectionListener().lecternProtection(new PlayerTakeLecternBookEvent(visitor, state));

        assertTrue(visitor.getInventory().contains(Material.WRITTEN_BOOK), "a copy for the player");
        assertNotNull(state.getInventory().getItem(0), "the book stays");
    }
}
