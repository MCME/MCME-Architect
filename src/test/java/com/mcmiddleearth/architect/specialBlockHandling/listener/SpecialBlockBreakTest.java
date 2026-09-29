package com.mcmiddleearth.architect.specialBlockHandling.listener;

import com.mcmiddleearth.architect.ArchitectPlugin;
import com.mcmiddleearth.architect.serverResoucePack.RpManager;
import com.mcmiddleearth.architect.specialBlockHandling.data.SpecialBlockInventoryData;
import com.mcmiddleearth.util.FakeGaffer;
import org.bukkit.block.Block;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.plugin.Plugin;
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
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

// For a special block, breakSpecialBlock asks TheGaffer first, then schedules handleBlockBreak, which sets a
// waterlogged one to air six ticks later. A break another plugin cancelled first, at LOWEST, is no break: TheGaffer is
// not asked and nothing is scheduled. (MockBukkit's block states lose their block data type, so the air itself cannot
// be seen here.) The player's RP is Human, from the default config.yml. One mock/load per class, as in
// SpecialBlockGafferTest.
class SpecialBlockBreakTest {

    private static final String STAIRS = "minecraft:oak_stairs[facing=north,half=bottom,shape=straight,waterlogged=true]";

    private static ServerMock server;
    private static WorldMock world;
    private static PlayerMock player;
    private static Plugin other;

    @BeforeAll
    static void setUp() throws Exception {
        server = MockBukkit.mock();
        MockBukkit.load(ArchitectPlugin.class);
        MockBukkit.loadWith(FakeGaffer.class, FakeGaffer.description());
        other = MockBukkit.createMockPlugin();
        world = server.addSimpleWorld("world");
        player = server.addPlayer();
        Path rp = SpecialBlockInventoryData.configFolder.toPath().resolve("Human");
        Files.createDirectories(rp);
        // With no category the block would be in no inventory, and the break could not find it.
        Files.writeString(rp.resolve("blocks.yml"), """
                Items:
                  stairs:
                    type: BLOCK
                    blockData: %s
                    itemMaterial: OAK_STAIRS
                    category: Stairs
                """.formatted(STAIRS));
        SpecialBlockInventoryData.loadInventories();
        RpManager.getPlayerData(player)
                .setCurrentRpUrl("https://github.com/MCME/RP-Human/releases/download/v3.0.4/Human.zip");
        assertEquals("Human", RpManager.getCurrentRpName(player), "the player's RP");
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
        FakeGaffer.allowed = location -> true;
    }

    private static Block waterloggedStairs(int x) {
        Block block = world.getBlockAt(x, 64, 0);
        block.setBlockData(server.createBlockData(STAIRS));
        return block;
    }

    // Another plugin cancels the break at the given priority, or not at all.
    private static BlockBreakEvent breakIt(Block block, EventPriority cancelledAt) {
        Listener canceller = new Listener() { };
        if (cancelledAt != null) {
            server.getPluginManager().registerEvent(BlockBreakEvent.class, canceller, cancelledAt,
                    (listener, event) -> ((BlockBreakEvent) event).setCancelled(true), other);
        }
        try {
            BlockBreakEvent event = new BlockBreakEvent(block, player);
            server.getPluginManager().callEvent(event);
            return event;
        } finally {
            HandlerList.unregisterAll(canceller);
        }
    }

    // Shows the break reaches the special-block handling: it asks TheGaffer, which refuses here.
    @Test
    void aBreakTheGafferRefusesIsCancelled() {
        Block block = waterloggedStairs(0);
        FakeGaffer.allowed = location -> false;

        BlockBreakEvent event = breakIt(block, null);

        assertTrue(event.isCancelled());
        assertEquals(List.of(block.getLocation()), FakeGaffer.asked);
    }

    @Test
    void aBreakAnotherPluginCancelledFirstIsNotHandled() {
        Block block = waterloggedStairs(2);

        breakIt(block, EventPriority.LOWEST);

        assertEquals(List.of(), FakeGaffer.asked, "not handled as a break");
    }
}
