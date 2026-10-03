package com.mcmiddleearth.architect.serverResoucePack;

import com.mcmiddleearth.architect.ArchitectPlugin;
import org.bukkit.configuration.MemoryConfiguration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;

// All servers share the architect_rp table, and production's was made by 2.10.9, perhaps made again by its
// /rp dropdb. The table this connector makes must be that table, column for column and in the same order, and a
// table that 2.10.9 made must stay as it is. FakeMysql stands in for the database. One mock/load per class, as in
// LogFileTest.
class RpTableSchemaTest {

    // what 2.10.9 sends to make the table: its CREATE TABLE, then its ALTER TABLE for the status column
    private static final String CREATE_2_10_9 = "CREATE TABLE IF NOT EXISTS architect_rp (uuid VARCHAR(50), "
            + "auto BIT, variant VARCHAR(30), resolution INT, client VARCHAR(30), currentURL VARCHAR(100), KEY(uuid))";
    private static final String ALTER_2_10_9 = "ALTER TABLE architect_rp ADD COLUMN status VARCHAR(30)";

    // the table those two statements leave
    private static final List<String> COLUMNS_2_10_9 = List.of("uuid VARCHAR(50)", "auto BIT", "variant VARCHAR(30)",
            "resolution INT", "client VARCHAR(30)", "currentURL VARCHAR(100)", "status VARCHAR(30)");

    private static FakeMysql mysql;

    @BeforeAll
    static void setUp() throws SQLException {
        mysql = FakeMysql.install();
        MockBukkit.mock();
        MockBukkit.load(ArchitectPlugin.class);
    }

    @AfterAll
    static void tearDown() throws SQLException {
        if (MockBukkit.isMocked()) {
            MockBukkit.unmock();
        }
        mysql.uninstall();
    }

    @Test
    void aNewDatabaseGetsTheTableThat2109Makes() {
        RpDatabaseConnector connector = connect("new");
        try {
            FakeMysql.Database database = mysql.database("new");
            assertEquals(COLUMNS_2_10_9, database.columns(), "the columns of a new architect_rp");
            assertEquals(List.of("uuid"), database.keys(), "the keys of a new architect_rp");
        } finally {
            connector.disconnect();
        }
    }

    @Test
    void aTableThat2109MadeStaysAsItIs() throws SQLException {
        FakeMysql.Database database = mysql.database("made-by-2-10-9");
        database.execute(CREATE_2_10_9);
        database.execute(ALTER_2_10_9);
        database.insertRow(Map.of("uuid", "6f8a1c2e-0b7d-4e57-9b1a-3c5d7e9f0a1b", "auto", true, "variant", "dark",
                "resolution", 32, "client", "sodium", "currentURL", "https://example.invalid/Human.zip",
                "status", "SUCCESSFULLY_LOADED"));
        List<Map<String, Object>> rows = database.rows();

        RpDatabaseConnector connector = connect("made-by-2-10-9");
        try {
            assertEquals(COLUMNS_2_10_9, database.columns(), "the columns of 2.10.9's architect_rp");
            assertEquals(List.of("uuid"), database.keys(), "the keys of 2.10.9's architect_rp");
            assertEquals(rows, database.rows(), "the rows of 2.10.9's architect_rp");
        } finally {
            connector.disconnect();
        }
    }

    // A connector to a database of its own. It checks the table on another thread, holding its own lock from the
    // CREATE TABLE to the last ALTER TABLE, so the table is as the connector leaves it once that lock is free.
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
}
