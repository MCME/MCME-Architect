package com.mcmiddleearth.architect.serverResoucePack;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

// FakeMysql binds parameters as Connector/J does, and counts an INSERT's values as MySQL does, so that a statement
// which real MySQL refuses fails in the tests too, such as an INSERT with more ? marks than columns.
class FakeMysqlTest {

    private static FakeMysql mysql;
    private static Connection connection;

    @BeforeAll
    static void setUp() throws SQLException {
        mysql = FakeMysql.install();
        mysql.database("binding").execute("CREATE TABLE IF NOT EXISTS architect_rp (uuid VARCHAR(50), auto BIT, "
                + "variant VARCHAR(30), KEY(uuid))");
        connection = DriverManager.getConnection("jdbc:mysql://localhost:3306/binding", "architect", "architect");
    }

    @AfterAll
    static void tearDown() throws SQLException {
        mysql.uninstall();
    }

    @Test
    void aMarkWithoutAValueFailsTheStatement() throws SQLException {
        PreparedStatement insert = connection.prepareStatement(
                "INSERT INTO architect_rp (uuid, auto, variant) VALUES (?,?,?,?)");
        insert.setString(1, "a");
        insert.setBoolean(2, true);
        insert.setString(3, "light");

        SQLException refused = assertThrows(SQLException.class, insert::executeUpdate);
        assertEquals("No value specified for parameter 4", refused.getMessage());
    }

    @Test
    void moreValuesThanColumnsFailTheInsert() throws SQLException {
        PreparedStatement insert = connection.prepareStatement(
                "INSERT INTO architect_rp (uuid, auto) VALUES (?,?,?)");
        insert.setString(1, "a");
        insert.setBoolean(2, true);
        insert.setString(3, "light");

        SQLException refused = assertThrows(SQLException.class, insert::executeUpdate);
        assertEquals("Column count doesn't match value count at row 1", refused.getMessage());
    }

    @Test
    void anIndexBeyondTheMarksIsRefused() throws SQLException {
        PreparedStatement select = connection.prepareStatement("SELECT auto FROM architect_rp WHERE uuid = ?");

        SQLException refused = assertThrows(SQLException.class, () -> select.setString(2, "a"));
        assertEquals("Parameter index out of range (2 > number of parameters, which is 1).", refused.getMessage());
    }

    @Test
    void aNullIsBoundAsSqlNull() throws SQLException {
        PreparedStatement insert = connection.prepareStatement(
                "INSERT INTO architect_rp (uuid, auto, variant) VALUES (?,?,?)");
        insert.setString(1, "with-null");
        insert.setBoolean(2, false);
        insert.setString(3, null);

        assertEquals(1, insert.executeUpdate());
        Map<String, Object> row = mysql.database("binding").row("with-null");
        assertTrue(row.containsKey("variant") && row.get("variant") == null, "variant is NULL in " + row);
    }
}
