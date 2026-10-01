package com.mcmiddleearth.architect.gamemodeSwitcher;

import com.mcmiddleearth.architect.ArchitectPlugin;
import com.mcmiddleearth.architect.Modules;
import com.mcmiddleearth.architect.PluginData;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockbukkit.mockbukkit.world.WorldMock;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

// The levels are given again where no join, respawn or world change does it: when the switcher starts with players
// online, after /architect reload, and when Architect stops. One mock/load per class, as Architect caches data-folder
// paths in static fields; the tests run in order, as the last one stops Architect.
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class GamemodeSwitcherResyncTest {

    private static ServerMock server;
    private static ArchitectPlugin plugin;
    private static WorldMock world;
    private static PlayerMock builder;
    /** Made op just before Architect stops. */
    private static PlayerMock promoted;
    /** What the level sender was asked to send, as "player level". */
    private static final List<String> sent = new ArrayList<>();

    @BeforeAll
    static void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.load(ArchitectPlugin.class);
        world = server.addSimpleWorld("world");
        builder = server.addPlayer();
        builder.addAttachment(plugin, "architect.gamemodeSwitcher.creative", true);
        promoted = server.addPlayer();
        promoted.addAttachment(plugin, "architect.gamemodeSwitcher.creative", true);
        server.addPlayer(); // a player the switcher is not for
        tick();
    }

    @AfterAll
    static void tearDown() {
        if (MockBukkit.isMocked()) {
            MockBukkit.unmock();
        }
    }

    private static void tick() {
        server.getScheduler().performOneTick();
    }

    @Test
    @Order(1)
    void startingGivesThePlayersOnlineTheirLevelATickLater() {
        new GamemodeSwitcher(plugin, (player, level) -> sent.add(player.getName() + " " + level)).start();

        assertEquals(List.of(), sent);
        tick();
        assertEquals(List.of(builder.getName() + " 2", promoted.getName() + " 2"), sent);
    }

    // An admin may have turned the module off or on in the world configs, which the reload reads.
    @Test
    @Order(2)
    void architectReloadGivesThePlayersOnlineTheirLevelAgain() {
        sent.clear();
        PluginData.setModuleEnabled(world, Modules.GAMEMODE_SWITCHER, false);
        plugin.loadData(); // what /architect reload runs
        tick();
        assertEquals(List.of(builder.getName() + " 0", promoted.getName() + " 0"), sent, "the module is off now");

        sent.clear();
        PluginData.setModuleEnabled(world, Modules.GAMEMODE_SWITCHER, true);
        plugin.loadData();
        tick();
        assertEquals(List.of(builder.getName() + " 2", promoted.getName() + " 2"), sent, "and on again");
    }

    // An op change sends the op's own level, which Architect must not take back.
    @Test
    @Order(3)
    void stoppingArchitectTakesTheSwitcherBackAtOnce() {
        sent.clear();
        promoted.setOp(true);

        server.getPluginManager().disablePlugin(plugin);

        assertEquals(List.of(builder.getName() + " 0"), sent, "at once, as Architect may schedule nothing then");
    }
}
