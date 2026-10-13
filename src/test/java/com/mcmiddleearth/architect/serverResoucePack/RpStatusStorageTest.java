package com.mcmiddleearth.architect.serverResoucePack;

import com.mcmiddleearth.architect.ArchitectPlugin;
import com.mcmiddleearth.architect.biomeTuning.BiomeTuning;
import com.mcmiddleearth.architect.biomeTuning.BiomeTuningOnAFakeServer;
import com.mcmiddleearth.architect.biomeTuning.RefreshCoordinator;
import com.mcmiddleearth.architect.testsupport.TestConfig;
import com.mcmiddleearth.connect.events.PlayerConnectEvent;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.regions.CuboidRegion;
import com.sk89q.worldedit.world.NullWorld;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.configuration.MemoryConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerResourcePackStatusEvent;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.io.File;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

// A player's resource pack status as 2.10.9 keeps it, across servers. Each status the client reports goes into the
// status column of the shared architect_rp table at once, by its name, and the server the player switches to reads it
// back: it knows that the client has the pack, and a pack it sends then is SENT with SUCCESSFULLY_LOADED as the last
// status, which is how MCME-Introduction tells a pack that is already loaded from one still on its way. A player who
// joins the proxy has no pack, whatever the table says, so the status starts again from NOT_SENT. RpPlayerStatusTest
// pins the enum itself. FakeMysql stands in for the database. One mock/load per class, as in LogFileTest.
class RpStatusStorageTest {

    private static final String CREATE_2_10_9 = "CREATE TABLE IF NOT EXISTS architect_rp (uuid VARCHAR(50), "
            + "auto BIT, variant VARCHAR(30), resolution INT, client VARCHAR(30), currentURL VARCHAR(100), KEY(uuid))";
    private static final String ALTER_2_10_9 = "ALTER TABLE architect_rp ADD COLUMN status VARCHAR(30)";

    // a pack for clients of 1.18.1 and later, as /rp server configures one
    private static final String PACK = "StatusTest";
    private static final String PACK_URL = "https://example.invalid/StatusTest.zip";

    private static FakeMysql mysql;
    private static ServerMock server;
    private static ArchitectPlugin plugin;
    private static FakeMysql.Database shared; // the database of Architect's own connector, from the test's config
    private static int clients;
    private static int reads;

    @BeforeAll
    static void setUp() throws Exception {
        mysql = FakeMysql.install();
        server = MockBukkit.mock();
        File folder = TestConfig.withRpDatabase(server, "architect");
        plugin = MockBukkit.load(ArchitectPlugin.class);
        assertEquals(folder, plugin.getDataFolder(), "Architect reads the test's config.yml");
        plugin.getConfig().set("ServerResourcePacks." + PACK + ".vanilla.16px.light.1_18_1.url", PACK_URL);
        plugin.getConfig().set("ServerResourcePacks." + PACK + ".vanilla.16px.light.1_18_1.sha",
                "33bece3b361f804e0966271ceaf85a691fe6a11d");
        shared = mysql.database(plugin.getConfig().getString("rpSettingsDatabase.dbName"));
        FakeMysql.await(() -> !shared.columns().isEmpty(), "Architect's RP table");
        synchronized (Objects.requireNonNull(RpManager.getDbConnector())) {
            // the table check has ended
        }
    }

    @AfterAll
    static void tearDown() throws SQLException {
        if (MockBukkit.isMocked()) {
            MockBukkit.unmock();
        }
        mysql.uninstall();
    }

    @Test
    void theSettingsAndTheStatusGoInto2109sTable() throws SQLException {
        FakeMysql.Database database = tableOf2109("written");
        RpDatabaseConnector connector = connect("written");
        try {
            PlayerMock player = server.addPlayer();
            String id = player.getUniqueId().toString();
            RpPlayerData data = new RpPlayerData();
            data.setAutoRp(false);
            data.setVariant("dark");
            data.setResolution(32);
            data.setClient("sodium");
            data.setCurrentRpUrl("https://example.invalid/Human.zip");
            data.setCurrentRpStatus(RpPlayerStatus.SUCCESSFULLY_LOADED);

            connector.saveRpSettings(player, data);
            FakeMysql.await(() -> database.row(id) != null, "the player's row");
            assertEquals(Map.of("uuid", id, "auto", false, "variant", "dark", "resolution", 32, "client", "sodium",
                    "currenturl", "https://example.invalid/Human.zip", "status", "SUCCESSFULLY_LOADED"),
                    database.row(id), "the row written");

            data.setVariant("light");
            data.setCurrentRpStatus(RpPlayerStatus.FAILED_RELOAD);
            connector.saveRpSettings(player, data);
            FakeMysql.await(() -> "light".equals(database.row(id).get("variant")), "the row to change");
            assertEquals("FAILED_RELOAD", database.row(id).get("status"), "the status written over");
            assertEquals(1, database.rows().size(), "the row is changed, not written again");
        } finally {
            connector.disconnect();
        }
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"NOT_SENT", "SENT", "DECLINED", "ACCEPTED", "DISCARDED", "DOWNLOADED", "FAILED_DOWNLOAD",
            "FAILED_RELOAD", "INVALID_URL", "SUCCESSFULLY_LOADED"})
    void eachStatusThat2109StoresIsReadBack(String status) throws SQLException {
        assertEquals(status, read(status).getCurrentRpStatus().name());
    }

    @Test
    void aRowWithoutAStatusIsReadAsNotSent() throws SQLException {
        assertEquals(RpPlayerStatus.NOT_SENT, read(null).getCurrentRpStatus());
    }

    // A newer Architect sharing the table may store a status that this one does not know, and so may a hand edit.
    // The player's other settings still load.
    @ParameterizedTest(name = "\"{0}\"")
    @ValueSource(strings = {"SOMETHING_NEW", "", "   "})
    void aStatusThisArchitectDoesNotKnowIsReadAsNotSent(String status) throws SQLException {
        assertEquals(RpPlayerStatus.NOT_SENT, read(status).getCurrentRpStatus());
    }

    @Test
    void eachUnknownStatusIsLoggedOnce() throws Exception {
        FakeMysql.Database database = tableOf2109("unknown");
        Map<UUID, String> statuses = new LinkedHashMap<>();
        statuses.put(UUID.randomUUID(), "SOMETHING_NEW");
        statuses.put(UUID.randomUUID(), "SOMETHING_NEW");
        statuses.put(UUID.randomUUID(), "SOMETHING_ELSE");
        for (Map.Entry<UUID, String> entry : statuses.entrySet()) {
            database.insertRow(Map.of("uuid", entry.getKey().toString(), "auto", true, "variant", "light",
                    "resolution", 16, "client", "vanilla", "status", entry.getValue()));
        }
        List<String> warnings = new CopyOnWriteArrayList<>();
        Handler handler = new Handler() {
            @Override
            public void publish(LogRecord record) {
                if (record.getLevel() == Level.WARNING) {
                    warnings.add(record.getMessage());
                }
            }

            @Override
            public void flush() {
            }

            @Override
            public void close() {
            }
        };
        plugin.getLogger().addHandler(handler);
        RpDatabaseConnector connector = connect("unknown");
        try {
            Map<UUID, RpPlayerData> loaded = new ConcurrentHashMap<>();
            for (UUID id : statuses.keySet()) {
                connector.loadRpSettings(id, loaded);
            }
            FakeMysql.await(() -> loaded.keySet().containsAll(statuses.keySet()), "the rows to be read");
            List<Long> counts = Stream.of("'SOMETHING_NEW'", "'SOMETHING_ELSE'")
                    .map(status -> warnings.stream().filter(warning -> warning.contains(status)).count())
                    .toList();
            assertEquals(List.of(1L, 1L), counts,
                    "the warnings about SOMETHING_NEW and SOMETHING_ELSE, of " + warnings);
        } finally {
            plugin.getLogger().removeHandler(handler);
            connector.disconnect();
        }
    }

    @Test
    void eachStatusTheClientReportsIsStoredAtOnce() throws SQLException {
        Client client = join(null);
        String id = client.getUniqueId().toString();
        UUID pack = UUID.randomUUID();

        for (PlayerResourcePackStatusEvent.Status reported : List.of(PlayerResourcePackStatusEvent.Status.ACCEPTED,
                PlayerResourcePackStatusEvent.Status.DOWNLOADED,
                PlayerResourcePackStatusEvent.Status.SUCCESSFULLY_LOADED)) {
            server.getPluginManager().callEvent(new PlayerResourcePackStatusEvent(client, pack, reported));
            FakeMysql.await(() -> shared.row(id) != null && reported.name().equals(shared.row(id).get("status")),
                    "the status " + reported + " in the table");
        }
        assertEquals(RpPlayerStatus.SUCCESSFULLY_LOADED, RpManager.getPlayerData(client).getCurrentRpStatus());
    }

    @Test
    void theServerAPlayerSwitchesToKnowsTheirClientHasThePack() throws SQLException {
        Client client = join("SUCCESSFULLY_LOADED");

        server.getPluginManager().callEvent(new PlayerConnectEvent(client, PlayerConnectEvent.ConnectReason.COMMAND));
        server.getScheduler().performTicks(2);

        RpPlayerData data = RpManager.getPlayerData(client);
        assertEquals(RpPlayerStatus.SUCCESSFULLY_LOADED, data.getCurrentRpStatus(), "the status the table holds");
        assertTrue(RpManager.setRp(PACK, client, true), "a pack is sent");
        assertEquals(List.of(PACK_URL), client.packs, "the packs sent");
        assertEquals(RpPlayerStatus.SENT, data.getCurrentRpStatus(), "the status after sending");
        assertEquals(RpPlayerStatus.SUCCESSFULLY_LOADED, data.getLastRpStatus(), "the status before sending");
        String id = client.getUniqueId().toString();
        FakeMysql.await(() -> shared.writes(id) >= 1, "the save of the pack sent");
        assertEquals("SENT", shared.row(id).get("status"), "the status in the table");
    }

    @Test
    void aPlayerWhoJoinsTheProxyStartsWithoutAPack() throws SQLException {
        Client client = join("SUCCESSFULLY_LOADED");
        String id = client.getUniqueId().toString();

        server.getPluginManager().callEvent(new PlayerConnectEvent(client,
                PlayerConnectEvent.ConnectReason.JOIN_PROXY));
        server.getScheduler().performTicks(2); // the join check runs on the next tick

        RpPlayerData data = RpManager.getPlayerData(client);
        assertEquals(List.of(PACK_URL), client.packs, "the pack of the last visit is sent again");
        assertEquals(RpPlayerStatus.SENT, data.getCurrentRpStatus(), "the status after sending");
        assertEquals(RpPlayerStatus.NOT_SENT, data.getLastRpStatus(), "the status before sending");
        FakeMysql.await(() -> shared.writes(id) >= 2, "the join's two saves, of the reset and of the pack sent");
        assertEquals("SENT", shared.row(id).get("status"), "the status in the table");
    }

    // The reset to NOT_SENT at a proxy join is saved at once. With no pack to send, it is the join's only save.
    @Test
    void aProxyJoinThatSendsNoPackStoresNotSent() throws Exception {
        Client client = join("SUCCESSFULLY_LOADED", null);
        String id = client.getUniqueId().toString();

        server.getPluginManager().callEvent(new PlayerConnectEvent(client,
                PlayerConnectEvent.ConnectReason.JOIN_PROXY));
        server.getScheduler().performOneTick();

        assertEquals(List.of(), client.packs, "the packs sent");
        assertEquals(RpPlayerStatus.NOT_SENT, RpManager.getPlayerData(client).getCurrentRpStatus(), "the status");
        FakeMysql.await(() -> shared.writes(id) >= 1, "the join's save");
        assertEquals("NOT_SENT", shared.row(id).get("status"), "the status in the table");
    }

    // The join check runs on the tick after the join. A player without a row, as a new one, gets the defaults at
    // once, so the check need not wait for a row that does not come.
    @Test
    void aPlayerWithoutARowGetsTheirPackAtTheFirstJoinCheck() throws Exception {
        Client client = join(null);
        String id = client.getUniqueId().toString();
        RpRegion region = regionAround(client);
        RpManager.addRegion(region);
        try {
            server.getPluginManager().callEvent(new PlayerConnectEvent(client,
                    PlayerConnectEvent.ConnectReason.JOIN_PROXY));
            server.getScheduler().performOneTick();

            assertEquals(List.of(PACK_URL), client.packs, "the packs sent at the first join check");
            FakeMysql.await(() -> shared.writes(id) >= 2, "the join's two saves, of the reset and of the pack sent");
            assertEquals("SENT", shared.row(id).get("status"), "the status in the player's new row");
        } finally {
            RpManager.removeRegion(region.getName());
        }
    }

    // Saving decides between INSERT and UPDATE by looking for the row, not by whether the player's data was loaded.
    @Test
    void theFirstStatusReportOfAPlayerWithoutARowMakesTheirRow() throws Exception {
        Client client = join(null);
        String id = client.getUniqueId().toString();
        assertTrue(RpManager.hasPlayerDataLoaded(client), "the look-up that found no row has loaded the defaults");

        server.getPluginManager().callEvent(new PlayerResourcePackStatusEvent(client, UUID.randomUUID(),
                PlayerResourcePackStatusEvent.Status.DECLINED));

        FakeMysql.await(() -> shared.row(id) != null, "the player's new row");
        assertEquals("DECLINED", shared.row(id).get("status"), "the status in the new row");
        assertEquals(1, shared.rows().stream().filter(row -> id.equals(row.get("uuid"))).count(),
                "the player's rows");
    }

    // A look-up that finds no row may end after something has made the player's data, such as the pack of the RP
    // region they joined in. It must keep that data rather than put the defaults over it.
    @Test
    void aLookUpThatFindsNoRowKeepsDataMadeMeanwhile() throws SQLException {
        FakeMysql.Database database = tableOf2109("kept");
        RpDatabaseConnector connector = connect("kept");
        try {
            UUID id = UUID.randomUUID();
            RpPlayerData meanwhile = new RpPlayerData();
            meanwhile.setCurrentRpUrl(PACK_URL);
            meanwhile.setCurrentRpStatus(RpPlayerStatus.SENT);
            Map<UUID, RpPlayerData> loaded = new ConcurrentHashMap<>(Map.of(id, meanwhile));

            connector.loadRpSettings(id, loaded);
            FakeMysql.await(() -> database.lookUps(id.toString()) > 0, "the look-up");
            synchronized (connector) {
                // the load has ended
            }

            assertSame(meanwhile, loaded.get(id), "the player's data made meanwhile");
        } finally {
            connector.disconnect();
        }
    }

    // A player who leaves while their row is still being read. The join check must stop then: going on, it would
    // make data for someone who is not there and save it over the row that the server they went to has written.
    @Test
    void theJoinCheckStopsForAPlayerWhoLeft() throws Exception {
        Client client = new Client(UUID.randomUUID());
        String id = client.getUniqueId().toString();
        shared.insertRow(Map.of("uuid", id, "auto", true, "variant", "dark", "resolution", 32, "client", "vanilla",
                "currentURL", PACK_URL, "status", "SUCCESSFULLY_LOADED"));
        Map<String, Object> row = shared.row(id);
        CountDownLatch slow = shared.hold(id);
        boolean dataMade;
        try {
            server.addPlayer(client);
            FakeMysql.await(() -> shared.waiting(id), "the player's look-up to start");
            server.getPluginManager().callEvent(new PlayerConnectEvent(client,
                    PlayerConnectEvent.ConnectReason.JOIN_PROXY));
            server.getScheduler().performOneTick(); // the first join check, while the row is still being read
            client.disconnect();
            server.getScheduler().performTicks(400); // longer than the join check waits
            dataMade = RpManager.hasPlayerDataLoaded(client); // before the slow look-up ends and stores the row
        } finally {
            slow.countDown();
        }
        FakeMysql.await(() -> shared.lookUps(id) > 0, "the slow look-up to end");
        Thread.sleep(200); // time for a save that waited for the look-up
        synchronized (Objects.requireNonNull(RpManager.getDbConnector())) {
            // no save is running now
        }
        assertAll(
                () -> assertFalse(dataMade, "data made for the player who left"),
                () -> assertEquals(List.of(), client.packs, "the packs sent"),
                () -> assertEquals(0, shared.writes(id), "the saves for the player who left"),
                () -> assertEquals(row, shared.row(id), "the row the other server wrote"));
    }

    // A biome refresh, such as a publish, which refreshes every player, is a quit and a rejoin as a new Player. When
    // it comes while the join check still waits for the player's row, the check waits through it, and then carries
    // on with the Player who came back.
    @Test
    void theJoinCheckCarriesOnThroughABiomeRefresh(@TempDir Path datapacks) throws Exception {
        BiomeTuningOnAFakeServer.enable(plugin, datapacks);
        Client client = new Client(UUID.randomUUID());
        String id = client.getUniqueId().toString();
        shared.insertRow(Map.of("uuid", id, "auto", true, "variant", "light", "resolution", 16, "client", "vanilla",
                "currentURL", PACK_URL, "status", "SUCCESSFULLY_LOADED"));
        CountDownLatch slow = shared.hold(id);
        try {
            server.addPlayer(client);
            FakeMysql.await(() -> shared.waiting(id), "the player's look-up to start");
            server.getPluginManager().callEvent(new PlayerConnectEvent(client,
                    PlayerConnectEvent.ConnectReason.JOIN_PROXY));
            server.getScheduler().performOneTick(); // the first join check, while the row is still being read

            // the refresh: the player leaves the server, for as long as two join checks take
            assertEquals(RefreshCoordinator.Outcome.STARTED, BiomeTuning.refresher().refresh(client, false, player -> {
                ((PlayerMock) player).disconnect();
                return null;
            }));
            slow.countDown();
            FakeMysql.await(() -> shared.lookUps(id) > 0, "the look-up to end");
            synchronized (Objects.requireNonNull(RpManager.getDbConnector())) {
                // the row is read
            }
            server.getScheduler().performTicks(20);
            assertEquals(0, shared.writes(id), "the saves while the player was away");

            // and comes back as a new Player, as after a reconfiguration
            Client back = new Client(client.getName(), client.getUniqueId());
            server.getPlayerList().addPlayer(back);
            server.getPluginManager().callEvent(new PlayerJoinEvent(back, Component.text("back")));
            server.getScheduler().performTicks(10); // the next join check

            assertEquals(List.of(), client.packs, "the packs sent to the Player who left");
            assertEquals(List.of(PACK_URL), back.packs, "the packs sent to the Player who came back");
            FakeMysql.await(() -> shared.writes(id) >= 2, "the join's two saves, of the reset and of the pack sent");
            assertEquals(Map.of("uuid", id, "auto", true, "variant", "light", "resolution", 16, "client", "vanilla",
                    "currenturl", PACK_URL, "status", "SENT"), shared.row(id), "the player's row");
        } finally {
            slow.countDown();
            BiomeTuning.disable();
        }
    }

    // A row as 2.10.9 writes it, with this status, read by a connector to a database of its own.
    private static RpPlayerData read(String status) throws SQLException {
        String name = "read-" + (++reads);
        FakeMysql.Database database = tableOf2109(name);
        UUID id = UUID.randomUUID();
        Map<String, Object> row = new HashMap<>(Map.of("uuid", id.toString(), "auto", false, "variant", "dark",
                "resolution", 32, "client", "sodium", "currentURL", "https://example.invalid/Human.zip"));
        row.put("status", status);
        database.insertRow(row);

        RpDatabaseConnector connector = connect(name);
        try {
            Map<UUID, RpPlayerData> loaded = new ConcurrentHashMap<>();
            connector.loadRpSettings(id, loaded);
            FakeMysql.await(() -> loaded.containsKey(id), "the row to be read");
            RpPlayerData data = loaded.get(id);
            assertEquals(Arrays.asList(false, "dark", 32, "sodium", "https://example.invalid/Human.zip"),
                    Arrays.asList(data.isAutoRp(), data.getVariant(), data.getResolution(), data.getClient(),
                            data.getCurrentRpUrl()), "the settings read");
            return data;
        } finally {
            connector.disconnect();
        }
    }

    // A database with the table as 2.10.9 makes it.
    private static FakeMysql.Database tableOf2109(String name) throws SQLException {
        FakeMysql.Database database = mysql.database(name);
        database.execute(CREATE_2_10_9);
        database.execute(ALTER_2_10_9);
        return database;
    }

    // A connector to a database of its own. It checks the table on another thread, holding its own lock from the
    // CREATE TABLE to the last ALTER TABLE.
    private static RpDatabaseConnector connect(String name) {
        FakeMysql.Database database = mysql.database(name);
        int creates = database.sent("CREATE TABLE");
        MemoryConfiguration config = new MemoryConfiguration();
        config.set("dbName", name);
        RpDatabaseConnector connector = new RpDatabaseConnector(config);
        FakeMysql.await(() -> database.sent("CREATE TABLE") > creates, "the connector to check its table");
        synchronized (Objects.requireNonNull(connector)) {
            // the table check has ended
        }
        return connector;
    }

    // A player joins this server. With a status, another server has stored that status for them first, after it
    // sent them the test pack.
    private static Client join(String status) throws SQLException {
        return join(status, PACK_URL);
    }

    // As join(status), with lastPack as the pack the other server sent, or none. Joining looks up the player's row
    // on another thread, under the connector's lock; this waits for that look-up of this player, not for any
    // SELECT, which a save another test left running could send.
    private static Client join(String status, String lastPack) throws SQLException {
        Client client = new Client(UUID.randomUUID());
        String id = client.getUniqueId().toString();
        if (status != null) {
            Map<String, Object> row = new HashMap<>(Map.of("uuid", id, "auto", true, "variant", "light",
                    "resolution", 16, "client", "vanilla", "status", status));
            row.put("currentURL", lastPack);
            shared.insertRow(row);
        }
        server.addPlayer(client);
        FakeMysql.await(() -> shared.lookUps(id) > 0, "the player's row to be looked up");
        synchronized (Objects.requireNonNull(RpManager.getDbConnector())) {
            // the look-up has ended
        }
        assertTrue(RpManager.hasPlayerDataLoaded(client), "the player's data is loaded");
        return client;
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
            this("Client" + (++clients), id);
        }

        Client(String name, UUID id) {
            super(RpStatusStorageTest.server, name, id);
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
