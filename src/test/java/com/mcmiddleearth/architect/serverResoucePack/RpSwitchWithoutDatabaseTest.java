package com.mcmiddleearth.architect.serverResoucePack;

import com.mcmiddleearth.architect.ArchitectPlugin;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.regions.CuboidRegion;
import com.sk89q.worldedit.world.NullWorld;
import org.bukkit.Location;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.sql.SQLException;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

// Without an RP database (the bundled config holds placeholders, which mean none), there is nothing to wait for: a
// player's defaults are there as soon as they log in, and RPSwitchTask serves them at its next run. FakeMysql is
// installed so that nothing could connect, and records nothing. One mock/load per class, as in LogFileTest.
class RpSwitchWithoutDatabaseTest {

    private static final String PACK = "NoDatabaseTest";
    private static final String LIGHT = "https://example.invalid/NoDatabaseTest-light.zip";

    private static FakeMysql mysql;
    private static ServerMock server;

    @BeforeAll
    static void setUp() throws SQLException {
        mysql = FakeMysql.install();
        server = MockBukkit.mock();
        ArchitectPlugin plugin = MockBukkit.load(ArchitectPlugin.class); // with the bundled config
        String version = "ServerResourcePacks." + PACK + ".vanilla.16px.light.1_18_1";
        plugin.getConfig().set(version + ".url", LIGHT);
        plugin.getConfig().set(version + ".sha", "33bece3b361f804e0966271ceaf85a691fe6a11d");
        server.getScheduler().performTicks(500); // RPSwitchTask starts 500 ticks after Architect
    }

    @AfterAll
    static void tearDown() throws SQLException {
        if (MockBukkit.isMocked()) {
            MockBukkit.unmock();
        }
        mysql.uninstall();
    }

    @Test
    void aPlayerIsServedAtOnce() {
        List<String> packs = new CopyOnWriteArrayList<>();
        PlayerMock player = new PlayerMock(server, "NoDatabase", UUID.randomUUID()) {
            @Override
            public int getProtocolVersion() {
                return 775;
            }

            @Override
            public void setResourcePack(String url, byte[] hash) {
                packs.add(url);
            }
        };
        server.addPlayer(player);
        assertTrue(RpManager.hasPlayerDataLoaded(player), "the player's defaults, as soon as they logged in");

        Location at = player.getLocation();
        NullWorld named = new NullWorld() {
            @Override
            public String getName() {
                return player.getWorld().getName();
            }
        };
        RpRegion region = new RpRegion("AroundNoDatabase", new CuboidRegion(named,
                BlockVector3.at(at.getBlockX() - 8, at.getBlockY() - 8, at.getBlockZ() - 8),
                BlockVector3.at(at.getBlockX() + 8, at.getBlockY() + 8, at.getBlockZ() + 8)));
        region.setRp(PACK);
        RpManager.addRegion(region);
        try {
            server.getScheduler().performTicks(20); // RPSwitchTask's next run

            assertEquals(List.of(LIGHT), packs, "the packs sent");
            assertEquals(List.of(), mysql.urls(), "the connections asked for");
        } finally {
            RpManager.removeRegion(region.getName());
        }
    }
}
