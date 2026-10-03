package com.mcmiddleearth.architect.serverResoucePack;

import com.mcmiddleearth.architect.ArchitectPlugin;
import org.bukkit.configuration.MemoryConfiguration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockbukkit.mockbukkit.MockBukkit;

import java.sql.SQLException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

// The bundled config.yml ships rpSettingsDatabase with the placeholders xxx. A server, or a test, that still has them
// has no RP database, and Architect must not try to reach one. A configured database is connected as before, with a
// connect and a socket timeout in the URL, so that a database that does not answer cannot hold a thread, and the
// connector's lock, for ever. FakeMysql records the URLs it is asked for. One mock/load per class, as in LogFileTest.
class RpDatabaseConnectionTest {

    private static FakeMysql mysql;
    private static List<String> urlsWhileLoading;

    @BeforeAll
    static void setUp() throws SQLException {
        mysql = FakeMysql.install();
        MockBukkit.mock();
        MockBukkit.load(ArchitectPlugin.class); // with the bundled config
        urlsWhileLoading = mysql.urls();
    }

    @AfterAll
    static void tearDown() throws SQLException {
        if (MockBukkit.isMocked()) {
            MockBukkit.unmock();
        }
        mysql.uninstall();
    }

    @Test
    void theBundledPlaceholdersMeanNoDatabase() {
        assertEquals(List.of(), urlsWhileLoading, "the connections Architect asked for with the bundled config");
    }

    // Either placeholder alone is enough: a server that filled in only one of them has no database to reach either.
    @ParameterizedTest(name = "user {0}, database {1}")
    @CsvSource({"xxx, architect", "architect, xxx"})
    void aPlaceholderUserOrDatabaseAloneMeansNoDatabase(String user, String dbName) {
        MemoryConfiguration config = new MemoryConfiguration();
        config.set("user", user);
        config.set("password", "secret");
        config.set("dbName", dbName);
        config.set("ip", "db.example");
        config.set("port", 3306);
        int before = mysql.urls().size();

        new RpDatabaseConnector(config).disconnect();

        assertEquals(List.of(), mysql.urls().subList(before, mysql.urls().size()), "the connections asked for");
    }

    @Test
    void aConfiguredDatabaseIsConnectedWithTimeouts() {
        assertEquals("jdbc:mysql://db.example:3307/architect?connectTimeout=10000&socketTimeout=60000",
                connect("db.example", 3307, "architect"));
    }

    @Test
    void optionsInTheDatabaseNameAreKept() {
        assertEquals("jdbc:mysql://localhost:3306/options?useSSL=false&connectTimeout=10000&socketTimeout=60000",
                connect("localhost", 3306, "options?useSSL=false"));
    }

    // A timeout that the operator set in dbName is theirs: only the other one is added.
    @Test
    void aConnectTimeoutInTheDatabaseNameIsKept() {
        assertEquals("jdbc:mysql://localhost:3306/own-connect?connectTimeout=3000&socketTimeout=60000",
                connect("localhost", 3306, "own-connect?connectTimeout=3000"));
    }

    @Test
    void aSocketTimeoutInTheDatabaseNameIsKept() {
        assertEquals("jdbc:mysql://localhost:3306/own-socket?socketTimeout=120000&useSSL=false&connectTimeout=10000",
                connect("localhost", 3306, "own-socket?socketTimeout=120000&useSSL=false"));
    }

    // Connector/J reads option names as they are written, so connecttimeout is not its connectTimeout: the driver
    // ignores it, and ours is still needed.
    @Test
    void aTimeoutNamedInAnotherCaseIsNotTheOperators() {
        assertEquals("jdbc:mysql://localhost:3306/other-case?connecttimeout=3000"
                        + "&connectTimeout=10000&socketTimeout=60000",
                connect("localhost", 3306, "other-case?connecttimeout=3000"));
    }

    // The URL a connector with these settings asks for. It connects as before: it makes its table.
    private static String connect(String ip, int port, String dbName) {
        MemoryConfiguration config = new MemoryConfiguration();
        config.set("user", "architect");
        config.set("password", "secret");
        config.set("dbName", dbName);
        config.set("ip", ip);
        config.set("port", port);
        int before = mysql.urls().size();
        RpDatabaseConnector connector = new RpDatabaseConnector(config);
        try {
            FakeMysql.Database database = mysql.database(dbName.split("[?]")[0]);
            FakeMysql.await(() -> !database.columns().isEmpty(), "the connector to make its table");
            return mysql.urls().get(before);
        } finally {
            connector.disconnect();
        }
    }
}
