package com.mcmiddleearth.architect.serverResoucePack;

import com.mcmiddleearth.architect.ArchitectPlugin;
import com.mcmiddleearth.architect.testsupport.TestConfig;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.io.File;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

// /rp dropdb drops the RP table, which the connector then makes again. It is for resource pack admins, who have
// architect.resourcePackAdmin, and only for them: anyone else gets nothing of it. FakeMysql stands in for the RP
// database that the test's config.yml gives Architect. One mock/load per class, as in LogFileTest.
class RpDropDbCommandTest {

    private static final String ADMIN = "architect.resourcePackAdmin";
    private static final String SWITCHER = "architect.resourcePackSwitcher";
    private static final List<String> COLUMNS_2_10_9 = List.of("uuid VARCHAR(50)", "auto BIT", "variant VARCHAR(30)",
            "resolution INT", "client VARCHAR(30)", "currentURL VARCHAR(100)", "status VARCHAR(30)");

    private static FakeMysql mysql;
    private static ServerMock server;
    private static ArchitectPlugin plugin;
    private static FakeMysql.Database database;

    @BeforeAll
    static void setUp() throws Exception {
        mysql = FakeMysql.install();
        server = MockBukkit.mock();
        File folder = TestConfig.withRpDatabase(server, "architect");
        plugin = MockBukkit.load(ArchitectPlugin.class);
        assertEquals(folder, plugin.getDataFolder(), "Architect reads the test's config.yml");
        database = mysql.database(plugin.getConfig().getString("rpSettingsDatabase.dbName"));
        awaitTable();
    }

    @AfterAll
    static void tearDown() throws SQLException {
        if (MockBukkit.isMocked()) {
            MockBukkit.unmock();
        }
        mysql.uninstall();
    }

    @Test
    void anAdminIsAskedToConfirm() {
        PlayerMock admin = player(ADMIN);
        int drops = database.sent("DROP TABLE");

        admin.performCommand("rp dropdb");

        assertTrue(messages(admin).stream().anyMatch(message -> message.contains("Confirm with: /rp dropdb confirm")),
                "an admin is asked to confirm");
        assertEquals(drops, database.sent("DROP TABLE"), "nothing is dropped before the admin confirms");
    }

    @Test
    void theTableIsMadeAgainWhenAnAdminConfirms() throws SQLException {
        PlayerMock admin = player(ADMIN);
        database.insertRow(Map.of("uuid", UUID.randomUUID().toString(), "auto", true, "variant", "light",
                "resolution", 16, "client", "vanilla", "status", "SUCCESSFULLY_LOADED"));
        int drops = database.sent("DROP TABLE");

        admin.performCommand("rp dropdb confirm");

        assertTrue(messages(admin).stream().anyMatch(message -> message.contains("Deleting RP table...")),
                "the admin is told the table is being deleted");
        awaitMessage(admin, "RP database table successfully deleted.");
        assertEquals(drops + 1, database.sent("DROP TABLE"), "the table is dropped once");
        awaitTable();
        assertEquals(COLUMNS_2_10_9, database.columns(), "the table is made again");
        assertEquals(List.of(), database.rows(), "the table made again is empty");
    }

    @Test
    void noOneElseCanDropTheTable() throws SQLException {
        database.insertRow(Map.of("uuid", UUID.randomUUID().toString(), "auto", false, "variant", "dark",
                "resolution", 32, "client", "sodium", "status", "DECLINED"));
        List<Map<String, Object>> rows = database.rows();
        int drops = database.sent("DROP TABLE");

        for (PlayerMock other : List.of(player(SWITCHER), player(null))) {
            other.performCommand("rp dropdb");
            other.performCommand("rp dropdb confirm");
            server.getScheduler().performTicks(5);

            List<String> told = messages(other);
            assertTrue(told.stream().noneMatch(message -> message.contains("dropdb confirm")
                            || message.contains("Deleting RP table") || message.contains("RP database table")),
                    "someone without " + ADMIN + " gets nothing of /rp dropdb, but got " + told);
        }
        assertEquals(drops, database.sent("DROP TABLE"), "nothing is dropped");
        assertEquals(rows, database.rows(), "the rows stay");
    }

    // On 3.x the RP code logs to Architect's own files, so that is where a failure must send the admin.
    @Test
    void aDropThatFailsSendsTheAdminToArchitectsLog() {
        PlayerMock admin = player(ADMIN);
        database.refuse("DROP TABLE", new SQLException("refused by the test"));

        admin.performCommand("rp dropdb confirm");

        String failure = awaitMessage(admin, "Error while deleting RP database table.");
        String log = "Architect's log, "
                + new File(new File(plugin.getDataFolder(), "logs"), "architect_*.log").getPath();
        assertTrue(failure.contains(log), "the admin is sent to " + log + ", but was told: " + failure);
    }

    @Test
    void anErrorTheDropDidNotExpectGoesToArchitectsLog() {
        PlayerMock admin = player(ADMIN);
        IllegalStateException error = new IllegalStateException("refused by the test");
        database.refuse("DROP TABLE", error);
        List<LogRecord> severe = new CopyOnWriteArrayList<>();
        Handler handler = new Handler() {
            @Override
            public void publish(LogRecord record) {
                if (record.getLevel() == Level.SEVERE) {
                    severe.add(record);
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
        try {
            admin.performCommand("rp dropdb confirm");
            awaitMessage(admin, "Error while deleting RP database table.");
        } finally {
            plugin.getLogger().removeHandler(handler);
        }
        assertTrue(severe.stream().anyMatch(record -> record.getThrown() == error),
                "the error is in Architect's log, with its stack trace; its errors were "
                        + severe.stream().map(LogRecord::getMessage).toList());
    }

    // A player who is not op, with this permission, or with none.
    private static PlayerMock player(String permission) {
        PlayerMock player = server.addPlayer();
        player.setOp(false);
        if (permission != null) {
            player.addAttachment(plugin, permission, true);
        }
        messages(player);
        return player;
    }

    // what the player has been told since the last call
    private static List<String> messages(PlayerMock player) {
        List<String> messages = new ArrayList<>();
        for (String message = player.nextMessage(); message != null; message = player.nextMessage()) {
            messages.add(message);
        }
        return messages;
    }

    // The drop runs on another thread, and its answer comes on a later tick.
    private static String awaitMessage(PlayerMock player, String text) {
        List<String> told = new ArrayList<>();
        long end = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (System.nanoTime() < end) {
            server.getScheduler().performOneTick();
            for (String message : messages(player)) {
                told.add(message);
                if (message.contains(text)) {
                    return message;
                }
            }
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        return fail("the player was not told '" + text + "', only " + told);
    }

    // Architect's connector makes its table on another thread, holding its own lock from the CREATE TABLE to the
    // last ALTER TABLE.
    private static void awaitTable() {
        FakeMysql.await(() -> !database.columns().isEmpty(), "Architect's RP table");
        synchronized (Objects.requireNonNull(RpManager.getDbConnector())) {
            // the table check has ended
        }
    }
}
