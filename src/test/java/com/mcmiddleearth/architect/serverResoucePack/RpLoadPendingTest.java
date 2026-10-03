package com.mcmiddleearth.architect.serverResoucePack;

import com.mcmiddleearth.architect.ArchitectPlugin;
import com.mcmiddleearth.architect.testsupport.TestConfig;
import com.mcmiddleearth.connect.events.PlayerConnectEvent;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.regions.CuboidRegion;
import com.sk89q.worldedit.world.NullWorld;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerResourcePackStatusEvent;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.io.File;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

// A player's settings are read from the RP database on another thread while they log in. Until that load has
// ended, nothing may make settings for them: defaults made meanwhile would choose their pack, and be saved over their
// row. RPSwitchTask, which serves every online player once a second, waits for the load instead; and the wait ends in
// every way a load can end. FakeMysql stands in for the database. One mock/load per class, as in LogFileTest.
class RpLoadPendingTest {

    // a pack with a light and a dark variant, for clients of 1.18.1 and later, as /rp server configures one
    private static final String PACK = "PendingTest";
    private static final String LIGHT = "https://example.invalid/PendingTest-light.zip";
    private static final String DARK = "https://example.invalid/PendingTest-dark.zip";
    private static final String SHA = "33bece3b361f804e0966271ceaf85a691fe6a11d";

    private static FakeMysql mysql;
    private static ServerMock server;
    private static ArchitectPlugin plugin;
    private static FakeMysql.Database shared; // the database of Architect's own connector, from the test's config
    private static int clients;

    @BeforeAll
    static void setUp() throws Exception {
        mysql = FakeMysql.install();
        server = MockBukkit.mock();
        File folder = TestConfig.withRpDatabase(server, "architect");
        plugin = MockBukkit.load(ArchitectPlugin.class);
        assertEquals(folder, plugin.getDataFolder(), "Architect reads the test's config.yml");
        for (String variant : List.of("light", "dark")) {
            String version = "ServerResourcePacks." + PACK + ".vanilla.16px." + variant + ".1_18_1";
            plugin.getConfig().set(version + ".url", variant.equals("light") ? LIGHT : DARK);
            plugin.getConfig().set(version + ".sha", SHA);
        }
        shared = mysql.database(plugin.getConfig().getString("rpSettingsDatabase.dbName"));
        FakeMysql.await(() -> !shared.columns().isEmpty(), "Architect's RP table");
        synchronized (Objects.requireNonNull(RpManager.getDbConnector())) {
            // the table check has ended
        }
        server.getScheduler().performTicks(500); // RPSwitchTask starts 500 ticks after Architect
    }

    @AfterAll
    static void tearDown() throws SQLException {
        if (MockBukkit.isMocked()) {
            MockBukkit.unmock();
        }
        mysql.uninstall();
    }

    // The player's row says dark. While it is being read, RPSwitchTask makes nothing for them, so no pack is chosen
    // from the defaults (light) and nothing is saved; once the row is read, their own pack is sent, and saved.
    @Test
    void whileTheLoadIsPendingNoPackIsChosenFromDefaults() throws Exception {
        Client client = new Client(UUID.randomUUID());
        String id = client.getUniqueId().toString();
        shared.insertRow(row(id, "dark"));
        CountDownLatch slow = shared.hold(id);
        RpRegion region = null;
        boolean madeWhileLoading;
        List<String> sentWhileLoading;
        try {
            server.addPlayer(client);
            FakeMysql.await(() -> shared.waiting(id), "the player's look-up to start");
            region = regionAround(client);
            RpManager.addRegion(region);
            server.getScheduler().performTicks(100); // RPSwitchTask runs every second

            assertTrue(RpManager.isLoadPending(client), "the player waits for their settings");
            madeWhileLoading = RpManager.hasPlayerDataLoaded(client);
            sentWhileLoading = List.copyOf(client.packs);
        } finally {
            slow.countDown();
        }
        try {
            FakeMysql.await(() -> shared.lookUps(id) > 0, "the look-up to end");
            synchronized (Objects.requireNonNull(RpManager.getDbConnector())) {
                // the row is read
            }
            server.getScheduler().performTicks(40); // RPSwitchTask serves the player
            FakeMysql.await(() -> shared.writes(id) >= 1, "the save of the pack sent");
            settle();

            assertAll(
                    () -> assertFalse(madeWhileLoading, "data made while the row was being read"),
                    () -> assertEquals(List.of(), sentWhileLoading, "the packs sent while the row was being read"),
                    () -> assertEquals(List.of(DARK), client.packs, "the packs sent, from the player's own settings"),
                    () -> assertEquals(1, shared.writes(id), "the saves: of the player's own pack only"),
                    () -> assertEquals("dark", shared.row(id).get("variant"), "the variant in the row"),
                    () -> assertEquals("SENT", shared.row(id).get("status"), "the status in the row"));
        } finally {
            RpManager.removeRegion(region.getName());
        }
    }

    // Every way in that could make data for the player, or send them a pack, waits too: a pack is not sent, data is
    // not kept, a status the client reports is not saved, and a read sees the defaults without keeping them.
    @Test
    void whileTheLoadIsPendingNoCallerMakesDataOrSendsAPack() throws Exception {
        Client client = new Client(UUID.randomUUID());
        String id = client.getUniqueId().toString();
        shared.insertRow(row(id, "dark"));
        Map<String, Object> stored = shared.row(id);
        CountDownLatch slow = shared.hold(id);
        RpRegion region = null;
        try {
            server.addPlayer(client);
            FakeMysql.await(() -> shared.waiting(id), "the player's look-up to start");
            region = regionAround(client);
            RpManager.addRegion(region);

            assertAll(
                    () -> assertFalse(RpManager.setRpRegion(client), "a region's pack sent"),
                    () -> assertFalse(RpManager.setRp(PACK, client, true), "a pack sent"),
                    () -> assertEquals("light", RpManager.getPlayerData(client).getVariant(), "the variant read"),
                    () -> assertEquals("", RpManager.getCurrentRpName(client), "the pack read"));
            server.getPluginManager().callEvent(new PlayerResourcePackStatusEvent(client, UUID.randomUUID(),
                    PlayerResourcePackStatusEvent.Status.SUCCESSFULLY_LOADED));
            assertAll(
                    () -> assertEquals(List.of(), client.packs, "the packs sent"),
                    () -> assertFalse(RpManager.hasPlayerDataLoaded(client), "data kept for the player"));
        } finally {
            slow.countDown();
            if (region != null) {
                RpManager.removeRegion(region.getName());
            }
        }
        FakeMysql.await(() -> shared.lookUps(id) > 0, "the look-up to end");
        synchronized (Objects.requireNonNull(RpManager.getDbConnector())) {
            // the row is read
        }
        settle();
        assertAll(
                () -> assertEquals(0, shared.writes(id), "the saves"),
                () -> assertEquals(stored, shared.row(id), "the player's row"),
                () -> assertEquals("dark", RpManager.getPlayerData(client).getVariant(), "the variant after the load"));
    }

    // A player who changes their settings, or picks a pack, while their settings are still being read is asked to
    // wait: the change would start from defaults that are not kept, and be lost.
    @Test
    void rpAsksThePlayerToWaitWhileTheSettingsLoad() throws Exception {
        Client client = new Client(UUID.randomUUID());
        String id = client.getUniqueId().toString();
        shared.insertRow(row(id, "dark"));
        Map<String, Object> stored = shared.row(id);
        CountDownLatch slow = shared.hold(id);
        List<String> told = new ArrayList<>();
        try {
            server.addPlayer(client);
            client.setOp(false);
            client.addAttachment(plugin, "architect.resourcePackSwitcher", true);
            FakeMysql.await(() -> shared.waiting(id), "the player's look-up to start");
            while (client.nextMessage() != null) {
                // what joining said
            }

            client.performCommand("rp variant dark");
            client.performCommand("rp " + PACK);
            for (String message = client.nextMessage(); message != null; message = client.nextMessage()) {
                told.add(message);
            }
        } finally {
            slow.countDown();
        }
        FakeMysql.await(() -> shared.lookUps(id) > 0, "the look-up to end");
        synchronized (Objects.requireNonNull(RpManager.getDbConnector())) {
            // the row is read
        }
        settle();
        assertAll(
                () -> assertEquals(2, told.stream().filter(message -> message.contains(
                        "Your resource pack settings are still loading, try again in a moment.")).count(),
                        "the answers that ask to wait, of " + told),
                () -> assertEquals(List.of(), client.packs, "the packs sent"),
                () -> assertEquals(0, shared.writes(id), "the saves"),
                () -> assertEquals(stored, shared.row(id), "the player's row"));
    }

    // A look-up that finds no row ends the wait with the defaults, so the player is served with them.
    @Test
    void aPlayerWithoutARowIsServedWithTheDefaults() throws Exception {
        Client client = new Client(UUID.randomUUID());
        String id = client.getUniqueId().toString();
        server.addPlayer(client);
        FakeMysql.await(() -> shared.lookUps(id) > 0, "the look-up");
        synchronized (Objects.requireNonNull(RpManager.getDbConnector())) {
            // the look-up has ended
        }
        FakeMysql.await(() -> !RpManager.isLoadPending(client), "the wait to end");
        RpRegion region = regionAround(client);
        RpManager.addRegion(region);
        try {
            server.getScheduler().performTicks(40);

            assertEquals(List.of(LIGHT), client.packs, "the packs sent, from the default settings");
        } finally {
            RpManager.removeRegion(region.getName());
        }
    }

    // A look-up that fails ends the wait with the defaults too. The connector then counts as disconnected until its
    // keep-alive connects again, which the test waits for, so that the class's other tests have their database.
    @Test
    void aPlayerWhoseLoadFailsIsServedWithTheDefaults() throws Exception {
        Client client = new Client(UUID.randomUUID());
        String id = client.getUniqueId().toString();
        shared.insertRow(row(id, "dark"));
        shared.refuseLookUp(id, new SQLException("refused by the test"));
        int connections = mysql.urls().size();
        RpRegion region = null;
        try {
            server.addPlayer(client);
            FakeMysql.await(() -> shared.lookUps(id) > 0, "the look-up");
            synchronized (Objects.requireNonNull(RpManager.getDbConnector())) {
                // the look-up has ended
            }
            FakeMysql.await(() -> !RpManager.isLoadPending(client), "the wait to end");
            region = regionAround(client);
            RpManager.addRegion(region);
            server.getScheduler().performTicks(40);

            assertEquals(List.of(LIGHT), client.packs, "the packs sent, from the default settings");
        } finally {
            if (region != null) {
                RpManager.removeRegion(region.getName());
            }
            server.getScheduler().performTicks(1200); // the keep-alive's next run
            FakeMysql.await(() -> mysql.urls().size() > connections, "the connector to connect again");
            synchronized (Objects.requireNonNull(RpManager.getDbConnector())) {
                // connected
            }
        }
    }

    // The join check waits for the player's own settings while RPSwitchTask makes none. When its 30 runs are used up,
    // it gives up waiting and goes on with the defaults, as it always has.
    @Test
    void theJoinCheckGoesOnWithTheDefaultsAtItsLimit() throws Exception {
        Client client = new Client(UUID.randomUUID());
        String id = client.getUniqueId().toString();
        shared.insertRow(row(id, "dark"));
        CountDownLatch slow = shared.hold(id);
        RpRegion region = null;
        try {
            server.addPlayer(client);
            FakeMysql.await(() -> shared.waiting(id), "the player's look-up to start");
            region = regionAround(client);
            RpManager.addRegion(region);
            server.getPluginManager().callEvent(new PlayerConnectEvent(client,
                    PlayerConnectEvent.ConnectReason.JOIN_PROXY));
            server.getScheduler().performTicks(400); // the join check's 30 runs and one more

            assertFalse(RpManager.isLoadPending(client), "the player still waits for their settings");
            assertEquals(List.of(LIGHT), client.packs, "the packs sent, from the default settings");
        } finally {
            slow.countDown();
            if (region != null) {
                RpManager.removeRegion(region.getName());
            }
        }
    }

    // A player who leaves while their row is being read waits for nothing any more, also after the load ends.
    @Test
    void aQuitDuringTheLoadLeavesNothingPending() throws Exception {
        Client client = new Client(UUID.randomUUID());
        String id = client.getUniqueId().toString();
        shared.insertRow(row(id, "dark"));
        CountDownLatch slow = shared.hold(id);
        try {
            server.addPlayer(client);
            FakeMysql.await(() -> shared.waiting(id), "the player's look-up to start");
            assertTrue(RpManager.isLoadPending(client), "the player waits for their settings");

            client.disconnect();

            assertFalse(RpManager.isLoadPending(client), "the player who left waits for their settings");
        } finally {
            slow.countDown();
        }
        FakeMysql.await(() -> shared.lookUps(id) > 0, "the look-up to end");
        synchronized (Objects.requireNonNull(RpManager.getDbConnector())) {
            // the load has ended
        }
        assertFalse(RpManager.isLoadPending(client), "the player who left waits for their settings, after the load");
    }

    // A player quits while their load is slow and logs in again at once. The first load ends after the quit and stores
    // the player's settings, while the second is still on its way. The join check waits for the second load instead of
    // taking the stored settings for it, as the sends it would make now are refused, and then sends the pack of the
    // player's last visit; there is no RP region to send another.
    @Test
    void aQuickRejoinWaitsForItsOwnLoad() throws Exception {
        UUID uuid = UUID.randomUUID();
        String id = uuid.toString();
        Map<String, Object> stored = row(id, "light");
        stored.put("currentURL", LIGHT);
        shared.insertRow(stored);
        Client first = new Client(uuid);
        CountDownLatch slow = shared.hold(id);
        server.addPlayer(first);
        FakeMysql.await(() -> shared.waiting(id), "the first look-up to start");
        first.disconnect();
        slow.countDown();
        FakeMysql.await(() -> shared.lookUps(id) > 0, "the first look-up to end");
        synchronized (Objects.requireNonNull(RpManager.getDbConnector())) {
            // the first load has stored the settings of the player who left
        }

        Client second = new Client(first.getName(), uuid);
        synchronized (Objects.requireNonNull(RpManager.getDbConnector())) {
            // the second load waits for the connector, which the test holds, as on a slow database
            server.addPlayer(second);
            assertTrue(RpManager.isLoadPending(second), "the second login waits for its load");
            assertTrue(RpManager.hasPlayerDataLoaded(second), "the first load's settings are stored");
            server.getPluginManager().callEvent(new PlayerConnectEvent(second,
                    PlayerConnectEvent.ConnectReason.JOIN_PROXY));
            server.getScheduler().performTicks(2); // the join check's first run
        }
        FakeMysql.await(() -> shared.lookUps(id) > 1, "the second look-up to end");
        synchronized (Objects.requireNonNull(RpManager.getDbConnector())) {
            // the second load has ended
        }
        server.getScheduler().performTicks(20); // the join check's next run

        assertEquals(List.of(LIGHT), second.packs, "the packs sent to the player who came back");
    }

    private static Map<String, Object> row(String id, String variant) {
        Map<String, Object> row = new HashMap<>(Map.of("uuid", id, "auto", true, "variant", variant,
                "resolution", 16, "client", "vanilla", "status", "SUCCESSFULLY_LOADED"));
        row.put("currentURL", null);
        return row;
    }

    // Time for a save that a send asked for to reach the table, before a test counts the saves.
    private static void settle() throws InterruptedException {
        Thread.sleep(200);
        synchronized (Objects.requireNonNull(RpManager.getDbConnector())) {
            // no save is running now
        }
    }

    // An RP region with the test pack around the player.
    private static RpRegion regionAround(Player player) {
        String world = player.getWorld().getName();
        NullWorld named = new NullWorld() {
            @Override
            public String getName() {
                return world;
            }
        };
        Location at = player.getLocation();
        RpRegion region = new RpRegion("Around" + player.getName(), new CuboidRegion(named,
                BlockVector3.at(at.getBlockX() - 8, at.getBlockY() - 8, at.getBlockZ() - 8),
                BlockVector3.at(at.getBlockX() + 8, at.getBlockY() + 8, at.getBlockZ() + 8)));
        region.setRp(PACK);
        return region;
    }

    // MockBukkit leaves the client's protocol, its brand and the packs sent to it to the test.
    private static final class Client extends PlayerMock {
        private final List<String> packs = new CopyOnWriteArrayList<>();

        Client(UUID id) {
            this("Pending" + (++clients), id);
        }

        Client(String name, UUID id) {
            super(RpLoadPendingTest.server, name, id);
        }

        @Override
        public int getProtocolVersion() {
            return 775;
        }

        @Override
        public String getClientBrandName() {
            return "vanilla";
        }

        @Override
        public void setResourcePack(String url, byte[] hash) {
            packs.add(url);
        }
    }
}
