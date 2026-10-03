package com.mcmiddleearth.architect.serverResoucePack;

import com.mcmiddleearth.architect.ArchitectPlugin;
import com.mcmiddleearth.architect.biomeTuning.BiomeTuning;
import com.mcmiddleearth.architect.biomeTuning.BiomeTuningOnAFakeServer;
import com.mcmiddleearth.architect.biomeTuning.RefreshCoordinator;
import com.mcmiddleearth.architect.testsupport.TestConfig;
import com.mcmiddleearth.connect.events.PlayerConnectEvent;
import net.kyori.adventure.text.Component;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.scheduler.BukkitTask;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockbukkit.mockbukkit.scheduler.ScheduledTask;

import java.io.File;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

// The join check that runs after a player joins the proxy, where it must end. Bukkit runs a repeating task again
// unless it is cancelled, so the tests look at whether the check's task is cancelled: MockBukkit drops a task that
// threw instead of running it again. FakeMysql stands in for the database. One mock/load per class, as in
// LogFileTest.
class RpJoinCheckTest {

    // a pack for clients of 1.18.1 and later, as /rp server configures one
    private static final String PACK = "JoinCheckTest";
    private static final String PACK_URL = "https://example.invalid/JoinCheckTest.zip";

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
        plugin.getConfig().set("ServerResourcePacks." + PACK + ".vanilla.16px.light.1_18_1.url", PACK_URL);
        plugin.getConfig().set("ServerResourcePacks." + PACK + ".vanilla.16px.light.1_18_1.sha",
                "33bece3b361f804e0966271ceaf85a691fe6a11d");
        shared = mysql.database(plugin.getConfig().getString("rpSettingsDatabase.dbName"));
        FakeMysql.await(() -> !shared.columns().isEmpty(), "Architect's RP table");
        synchronized (Objects.requireNonNull(RpManager.getDbConnector())) {
            // the table check has ended
        }
        // RPSwitchTask makes default settings for every online player once a second, and the join check would take
        // them for the settings it waits for. These tests need no pack switching while players walk.
        server.getScheduler().getPendingTasks().stream()
                .filter(task -> task instanceof ScheduledTask scheduled
                        && scheduled.getRunnable() instanceof RPSwitchTask)
                .forEach(BukkitTask::cancel);
    }

    @AfterAll
    static void tearDown() throws SQLException {
        if (MockBukkit.isMocked()) {
            MockBukkit.unmock();
        }
        mysql.uninstall();
    }

    // Once the check has reached its send, it ends, also when the send throws: a repeat would send and save again
    // every half second. getRpForUrl walks every pack in the config, and throws on an entry that is no section.
    @Test
    void aSendThatThrowsStillEndsTheCheck() throws Exception {
        Client client = join(null);
        plugin.getConfig().set("ServerResourcePacks.Broken", "not a pack");
        try {
            Set<Integer> before = server.getScheduler().getPendingTasks().stream()
                    .map(BukkitTask::getTaskId).collect(Collectors.toSet());
            server.getPluginManager().callEvent(new PlayerConnectEvent(client,
                    PlayerConnectEvent.ConnectReason.JOIN_PROXY));
            List<BukkitTask> checks = server.getScheduler().getPendingTasks().stream()
                    .filter(task -> task.isSync() && !before.contains(task.getTaskId())).toList();
            assertEquals(1, checks.size(), "the join check's task");

            assertThrows(NullPointerException.class, () -> server.getScheduler().performOneTick(), "the send");

            assertTrue(checks.get(0).isCancelled(), "the join check's task is cancelled");
        } finally {
            plugin.getConfig().set("ServerResourcePacks.Broken", null);
        }
    }

    // The check logs that it timed out, and goes on with defaults, when the player's settings did not load in its
    // runs. It says so once.
    @Test
    void aTimeoutIsLoggedOnceWhenTheSettingsDidNotLoad() throws Exception {
        Client client = new Client(UUID.randomUUID());
        String id = client.getUniqueId().toString();
        CountDownLatch slow = shared.hold(id); // the look-up does not end while the check runs
        List<String> timeouts = timeoutWarnings(id);
        try {
            server.addPlayer(client);
            FakeMysql.await(() -> shared.waiting(id), "the player's look-up to start");
            server.getPluginManager().callEvent(new PlayerConnectEvent(client,
                    PlayerConnectEvent.ConnectReason.JOIN_PROXY));
            server.getScheduler().performTicks(400); // the whole check

            assertEquals(1, timeouts.size(), "the timeout warnings, " + timeouts);
        } finally {
            slow.countDown();
        }
    }

    // When the check's 30 runs are used up during a biome refresh, it waits for the player, and goes on when they
    // are back. If the settings loaded meanwhile, it goes on with them, and there is no timeout to log.
    @Test
    void noTimeoutIsLoggedWhenTheSettingsLoadedDuringARefresh(@TempDir Path datapacks) throws Exception {
        BiomeTuningOnAFakeServer.enable(plugin, datapacks);
        Client client = new Client(UUID.randomUUID());
        String id = client.getUniqueId().toString();
        Map<String, Object> row = new HashMap<>(Map.of("uuid", id, "auto", true, "variant", "light",
                "resolution", 16, "client", "vanilla", "currentURL", PACK_URL, "status", "SUCCESSFULLY_LOADED"));
        shared.insertRow(row);
        CountDownLatch slow = shared.hold(id);
        List<String> timeouts = timeoutWarnings(id);
        try {
            server.addPlayer(client);
            FakeMysql.await(() -> shared.waiting(id), "the player's look-up to start");
            server.getPluginManager().callEvent(new PlayerConnectEvent(client,
                    PlayerConnectEvent.ConnectReason.JOIN_PROXY));
            server.getScheduler().performOneTick(); // the first run, while the row is still being read
            assertEquals(RefreshCoordinator.Outcome.STARTED, BiomeTuning.refresher().refresh(client, false, player -> {
                ((PlayerMock) player).disconnect();
                return null;
            }));
            slow.countDown(); // the settings load during the refresh
            FakeMysql.await(() -> shared.lookUps(id) > 0, "the look-up to end");
            synchronized (Objects.requireNonNull(RpManager.getDbConnector())) {
                // the row is read
            }
            server.getScheduler().performTicks(400); // the refresh outlasts the check's 30 runs

            Client back = new Client(client.getName(), client.getUniqueId());
            server.getPlayerList().addPlayer(back);
            server.getPluginManager().callEvent(new PlayerJoinEvent(back, Component.text("back")));
            server.getScheduler().performTicks(10); // the run on return

            assertEquals(List.of(PACK_URL), back.packs, "the pack of the loaded settings");
            assertEquals(List.of(), timeouts, "the timeout warnings");
        } finally {
            slow.countDown();
            BiomeTuning.disable();
        }
    }

    // A player joins this server, after another server stored their settings, with lastPack as the pack it sent
    // them, or none. Joining looks up the player's row on another thread, under the connector's lock.
    private static Client join(String lastPack) throws SQLException {
        Client client = new Client(UUID.randomUUID());
        String id = client.getUniqueId().toString();
        Map<String, Object> row = new HashMap<>(Map.of("uuid", id, "auto", true, "variant", "light",
                "resolution", 16, "client", "vanilla", "status", "SUCCESSFULLY_LOADED"));
        row.put("currentURL", lastPack);
        shared.insertRow(row);
        server.addPlayer(client);
        FakeMysql.await(() -> shared.lookUps(id) > 0, "the player's row to be looked up");
        synchronized (Objects.requireNonNull(RpManager.getDbConnector())) {
            // the look-up has ended
        }
        assertTrue(RpManager.hasPlayerDataLoaded(client), "the player's data is loaded");
        return client;
    }

    // The warnings, from now on, that the join check timed out waiting for the settings of the player with this id.
    private static List<String> timeoutWarnings(String id) {
        List<String> timeouts = new CopyOnWriteArrayList<>();
        plugin.getLogger().addHandler(new Handler() {
            @Override
            public void publish(LogRecord record) {
                if (record.getLevel() == Level.WARNING && record.getMessage().contains("Timed out waiting")
                        && record.getMessage().contains(id)) {
                    timeouts.add(record.getMessage());
                }
            }

            @Override
            public void flush() {
            }

            @Override
            public void close() {
            }
        });
        return timeouts;
    }

    // MockBukkit leaves the client's protocol, its brand and the packs sent to it to the test.
    private static final class Client extends PlayerMock {
        private final List<String> packs = new CopyOnWriteArrayList<>();

        Client(UUID id) {
            this("Client" + (++clients), id);
        }

        Client(String name, UUID id) {
            super(RpJoinCheckTest.server, name, id);
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
