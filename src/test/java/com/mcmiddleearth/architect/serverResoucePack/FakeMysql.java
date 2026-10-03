package com.mcmiddleearth.architect.serverResoucePack;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.Driver;
import java.sql.DriverManager;
import java.sql.DriverPropertyInfo;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.fail;

/**
 * A stand-in for the MySQL server behind the RP database, so that a test can see what RpDatabaseConnector does to
 * its table without a database. It knows the statements the connector sends: CREATE TABLE IF NOT EXISTS, ALTER TABLE
 * ... ADD COLUMN, DROP TABLE IF EXISTS, and the INSERT, UPDATE and SELECT of the row of one uuid. Like MySQL, it
 * refuses a column that the table lacks and a column that is added twice, so a statement that would fail on the
 * real table fails here too. Each database name in a jdbc:mysql: URL is a database of its own.
 */
final class FakeMysql implements Driver {

    private final Map<String, Database> databases = new ConcurrentHashMap<>();
    private final List<Driver> displaced = new ArrayList<>();
    private final List<String> urls = new CopyOnWriteArrayList<>();

    /**
     * Registers a fake that answers every jdbc:mysql: URL. The real MySQL driver is on the test class path, and is
     * taken off the list until {@link #uninstall()}, so that no test opens a connection to a real server.
     */
    static FakeMysql install() throws SQLException {
        FakeMysql fake = new FakeMysql();
        for (Driver driver : Collections.list(DriverManager.getDrivers())) {
            if (driver.acceptsURL("jdbc:mysql://localhost:3306/test")) {
                DriverManager.deregisterDriver(driver);
                fake.displaced.add(driver);
            }
        }
        DriverManager.registerDriver(fake);
        return fake;
    }

    void uninstall() throws SQLException {
        DriverManager.deregisterDriver(this);
        for (Driver driver : displaced) {
            DriverManager.registerDriver(driver);
        }
    }

    Database database(String name) {
        return databases.computeIfAbsent(name, key -> new Database());
    }

    /** Every URL a connection was asked for, in order. */
    List<String> urls() {
        return List.copyOf(urls);
    }

    /** Waits up to ten seconds for something the connector does on another thread. */
    static void await(BooleanSupplier condition, String what) {
        long end = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (!condition.getAsBoolean()) {
            if (System.nanoTime() > end) {
                fail("timed out waiting for " + what);
            }
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                fail("interrupted while waiting for " + what);
            }
        }
    }

    /** One database: the architect_rp table, if it exists, and every statement sent to it. */
    static final class Database {
        private static final String TABLE = "architect_rp";

        // column name in lower case -> the column as declared, such as "currentURL VARCHAR(100)"; null: no table
        private LinkedHashMap<String, String> columns;
        private final List<String> keys = new ArrayList<>();
        private final List<Map<String, Object>> rows = new ArrayList<>();
        private final List<String> statements = new ArrayList<>();
        private final List<Map.Entry<String, Exception>> refusals = new ArrayList<>();
        // uuid -> how often its row was looked up, and written by an INSERT or UPDATE
        private final Map<String, Integer> lookUps = new HashMap<>();
        private final Map<String, Integer> writes = new HashMap<>();

        /** The columns of the table as declared, in their order; empty when there is no table. */
        synchronized List<String> columns() {
            return columns == null ? List.of() : List.copyOf(columns.values());
        }

        synchronized List<String> keys() {
            return List.copyOf(keys);
        }

        /** The rows of the table, each column (in lower case) with its value. */
        synchronized List<Map<String, Object>> rows() {
            List<Map<String, Object>> copy = new ArrayList<>();
            for (Map<String, Object> row : rows) {
                copy.add(Collections.unmodifiableMap(new HashMap<>(row)));
            }
            return copy;
        }

        /** The row whose uuid is the given one, or null. */
        synchronized Map<String, Object> row(String uuid) {
            for (Map<String, Object> row : rows) {
                if (uuid.equals(row.get("uuid"))) {
                    return Collections.unmodifiableMap(new HashMap<>(row));
                }
            }
            return null;
        }

        /** How many statements that start with this text (in any case) have been sent. */
        synchronized int sent(String start) {
            String wanted = start.toUpperCase(Locale.ROOT);
            return (int) statements.stream()
                    .filter(statement -> statement.toUpperCase(Locale.ROOT).startsWith(wanted))
                    .count();
        }

        /** How many SELECTs looked up the row of this uuid, so a test can wait for a player's own load. */
        synchronized int lookUps(String uuid) {
            return lookUps.getOrDefault(uuid, 0);
        }

        /** How many INSERTs and UPDATEs were sent for the row of this uuid, so a test can wait for its last save. */
        synchronized int writes(String uuid) {
            return writes.getOrDefault(uuid, 0);
        }

        /**
         * The next CREATE, ALTER or DROP statement that starts with this text (in any case) fails with this
         * exception: an SQLException, as MySQL would throw, or a RuntimeException, as no driver should.
         */
        synchronized void refuse(String start, Exception failure) {
            refusals.add(Map.entry(start.toUpperCase(Locale.ROOT), failure));
        }

        /** Puts a row in as it is, as another server would have; each column must exist. */
        synchronized void insertRow(Map<String, Object> values) throws SQLException {
            requireColumns(List.copyOf(values.keySet()));
            Map<String, Object> row = emptyRow();
            values.forEach((column, value) -> row.put(lower(column), value));
            rows.add(row);
        }

        /** Runs a statement that is not prepared: CREATE TABLE, ALTER TABLE or DROP TABLE. */
        synchronized void execute(String sql) throws SQLException {
            String statement = squeeze(sql);
            statements.add(statement);
            String upper = statement.toUpperCase(Locale.ROOT);
            for (Iterator<Map.Entry<String, Exception>> it = refusals.iterator(); it.hasNext(); ) {
                Map.Entry<String, Exception> refusal = it.next();
                if (upper.startsWith(refusal.getKey())) {
                    it.remove();
                    if (refusal.getValue() instanceof SQLException failure) {
                        throw failure;
                    }
                    throw (RuntimeException) refusal.getValue();
                }
            }
            if (upper.startsWith("CREATE TABLE IF NOT EXISTS ")) {
                int open = statement.indexOf('(');
                requireTableName(statement.substring("CREATE TABLE IF NOT EXISTS ".length(), open));
                if (columns != null) {
                    return; // it exists: nothing to do
                }
                LinkedHashMap<String, String> made = new LinkedHashMap<>();
                List<String> madeKeys = new ArrayList<>();
                for (String part : topLevelParts(statement.substring(open + 1, statement.lastIndexOf(')')))) {
                    if (part.toUpperCase(Locale.ROOT).startsWith("KEY(")) {
                        madeKeys.add(part.substring("KEY(".length(), part.length() - 1).trim());
                    } else {
                        String name = part.substring(0, part.indexOf(' '));
                        if (made.put(lower(name), part) != null) {
                            throw new SQLException("Duplicate column name '" + name + "'", "42S21", 1060);
                        }
                    }
                }
                columns = made;
                keys.addAll(madeKeys);
            } else if (upper.startsWith("ALTER TABLE ")) {
                String[] words = statement.split(" ", 7);
                if (words.length < 7 || !words[3].equalsIgnoreCase("ADD") || !words[4].equalsIgnoreCase("COLUMN")) {
                    throw new UnsupportedOperationException("the fake MySQL does not know: " + statement);
                }
                requireTableName(words[2]);
                requireTable();
                String name = words[5];
                if (columns.containsKey(lower(name))) {
                    throw new SQLException("Duplicate column name '" + name + "'", "42S21", 1060);
                }
                columns.put(lower(name), name + " " + words[6]);
                for (Map<String, Object> row : rows) {
                    row.put(lower(name), null);
                }
            } else if (upper.startsWith("DROP TABLE IF EXISTS ")) {
                requireTableName(statement.substring("DROP TABLE IF EXISTS ".length()));
                columns = null;
                keys.clear();
                rows.clear();
            } else {
                throw new UnsupportedOperationException("the fake MySQL does not know: " + statement);
            }
        }

        // INSERT INTO architect_rp (a, b) VALUES (?,?)
        synchronized int insert(String sql, Map<Integer, Object> parameters) throws SQLException {
            String statement = squeeze(sql);
            statements.add(statement);
            int open = statement.indexOf('(');
            requireTableName(statement.substring("INSERT INTO ".length(), open));
            List<String> names = names(statement.substring(open + 1, statement.indexOf(')')));
            String values = statement.substring(statement.indexOf('(', statement.indexOf(" VALUES ")) + 1,
                    statement.lastIndexOf(')'));
            if (names(values).size() != names.size()) {
                throw new SQLException("Column count doesn't match value count at row 1", "21S01", 1136);
            }
            requireColumns(names);
            Map<String, Object> row = emptyRow();
            for (int index = 0; index < names.size(); index++) {
                row.put(lower(names.get(index)), parameters.get(index + 1));
            }
            rows.add(row);
            if (row.get("uuid") != null) {
                writes.merge(String.valueOf(row.get("uuid")), 1, Integer::sum);
            }
            return 1;
        }

        // UPDATE architect_rp SET a=?, b=? WHERE uuid = ?
        synchronized int update(String sql, Map<Integer, Object> parameters) throws SQLException {
            String statement = squeeze(sql);
            statements.add(statement);
            int set = statement.indexOf(" SET ");
            int where = statement.indexOf(" WHERE ");
            requireTableName(statement.substring("UPDATE ".length(), set));
            List<String> names = new ArrayList<>();
            for (String assignment : names(statement.substring(set + " SET ".length(), where))) {
                names.add(assignment.substring(0, assignment.indexOf('=')).trim());
            }
            String key = whereColumn(statement.substring(where + " WHERE ".length()));
            List<String> used = new ArrayList<>(names);
            used.add(key);
            requireColumns(used);
            Object value = parameters.get(names.size() + 1);
            if (key.equalsIgnoreCase("uuid")) {
                writes.merge(String.valueOf(value), 1, Integer::sum);
            }
            int changed = 0;
            for (Map<String, Object> row : rows) {
                if (Objects.equals(row.get(lower(key)), value)) {
                    for (int index = 0; index < names.size(); index++) {
                        row.put(lower(names.get(index)), parameters.get(index + 1));
                    }
                    changed++;
                }
            }
            return changed;
        }

        // SELECT a, b FROM architect_rp WHERE uuid = ?
        synchronized List<Map<String, Object>> select(String sql, Map<Integer, Object> parameters)
                throws SQLException {
            String statement = squeeze(sql);
            statements.add(statement);
            int from = statement.indexOf(" FROM ");
            int where = statement.indexOf(" WHERE ");
            requireTableName(statement.substring(from + " FROM ".length(), where));
            List<String> names = names(statement.substring("SELECT ".length(), from));
            String key = whereColumn(statement.substring(where + " WHERE ".length()));
            List<String> used = new ArrayList<>(names);
            used.add(key);
            requireColumns(used);
            if (key.equalsIgnoreCase("uuid")) {
                lookUps.merge(String.valueOf(parameters.get(1)), 1, Integer::sum);
            }
            List<Map<String, Object>> result = new ArrayList<>();
            for (Map<String, Object> row : rows) {
                if (Objects.equals(row.get(lower(key)), parameters.get(1))) {
                    Map<String, Object> selected = new HashMap<>();
                    for (String name : names) {
                        selected.put(lower(name), row.get(lower(name)));
                    }
                    result.add(selected);
                }
            }
            return result;
        }

        private Map<String, Object> emptyRow() {
            Map<String, Object> row = new HashMap<>();
            for (String column : columns.keySet()) {
                row.put(column, null);
            }
            return row;
        }

        private void requireTable() throws SQLException {
            if (columns == null) {
                throw new SQLException("Table '" + TABLE + "' doesn't exist", "42S02", 1146);
            }
        }

        private void requireColumns(List<String> names) throws SQLException {
            requireTable();
            for (String name : names) {
                if (!columns.containsKey(lower(name))) {
                    throw new SQLException("Unknown column '" + name + "' in 'field list'", "42S22", 1054);
                }
            }
        }

        private static void requireTableName(String name) {
            if (!TABLE.equals(name.trim())) {
                throw new UnsupportedOperationException("the fake MySQL only has the table " + TABLE + ", not " + name);
            }
        }

        private static String whereColumn(String condition) {
            return condition.substring(0, condition.indexOf('=')).trim();
        }

        private static List<String> names(String list) {
            List<String> names = new ArrayList<>();
            for (String name : list.split(",")) {
                names.add(name.trim());
            }
            return names;
        }

        // splits at the commas that are not inside parentheses: "a VARCHAR(5), KEY(a)" -> "a VARCHAR(5)", "KEY(a)"
        private static List<String> topLevelParts(String list) {
            List<String> parts = new ArrayList<>();
            int depth = 0;
            int start = 0;
            for (int index = 0; index < list.length(); index++) {
                char c = list.charAt(index);
                if (c == '(') {
                    depth++;
                } else if (c == ')') {
                    depth--;
                } else if (c == ',' && depth == 0) {
                    parts.add(list.substring(start, index).trim());
                    start = index + 1;
                }
            }
            parts.add(list.substring(start).trim());
            return parts;
        }
    }

    // ------------------------------------------------------------------------------------------------------ JDBC

    @Override
    public Connection connect(String url, Properties info) {
        if (!acceptsURL(url)) {
            return null;
        }
        urls.add(url);
        // jdbc:mysql://host:port/name?options
        String path = url.substring(url.indexOf('/', "jdbc:mysql://".length()) + 1);
        Database database = database(path.contains("?") ? path.substring(0, path.indexOf('?')) : path);
        return proxy(Connection.class, (method, args) -> switch (method) {
            case "prepareStatement" -> preparedStatement(database, (String) args[0]);
            case "createStatement" -> statement(database);
            case "isValid" -> true;
            case "isClosed" -> false;
            case "close" -> null;
            default -> throw unsupported("Connection", method);
        });
    }

    private static Statement statement(Database database) {
        return proxy(Statement.class, (method, args) -> switch (method) {
            case "execute" -> {
                database.execute((String) args[0]);
                yield false;
            }
            case "setQueryTimeout", "close" -> null;
            default -> throw unsupported("Statement", method);
        });
    }

    // Binds parameters as Connector/J does: an index must be one of the statement's ? marks, a null is bound as SQL
    // NULL, and each mark must be bound when the statement runs.
    private static PreparedStatement preparedStatement(Database database, String sql) {
        int marks = (int) sql.chars().filter(c -> c == '?').count();
        Map<Integer, Object> parameters = Collections.synchronizedMap(new HashMap<>());
        return proxy(PreparedStatement.class, (method, args) -> switch (method) {
            case "setString", "setBoolean", "setInt" -> {
                int index = (Integer) args[0];
                if (index < 1 || index > marks) {
                    throw new SQLException("Parameter index out of range (" + index
                            + " > number of parameters, which is " + marks + ").", "S1009");
                }
                parameters.put(index, args[1]);
                yield null;
            }
            case "executeQuery" -> {
                requireBound(parameters, marks);
                yield resultSet(database.select(sql, parameters));
            }
            case "executeUpdate" -> {
                requireBound(parameters, marks);
                yield sql.trim().toUpperCase(Locale.ROOT).startsWith("INSERT")
                        ? database.insert(sql, parameters)
                        : database.update(sql, parameters);
            }
            case "clearParameters" -> {
                parameters.clear();
                yield null;
            }
            case "setQueryTimeout", "close" -> null;
            default -> throw unsupported("PreparedStatement", method);
        });
    }

    private static void requireBound(Map<Integer, Object> parameters, int marks) throws SQLException {
        for (int index = 1; index <= marks; index++) {
            if (!parameters.containsKey(index)) {
                throw new SQLException("No value specified for parameter " + index, "07001");
            }
        }
    }

    private static ResultSet resultSet(List<Map<String, Object>> rows) {
        int[] cursor = {-1};
        return proxy(ResultSet.class, (method, args) -> {
            switch (method) {
                case "next":
                    cursor[0]++;
                    return cursor[0] < rows.size();
                case "close":
                    return null;
                case "getString":
                case "getBoolean":
                case "getInt": {
                    Map<String, Object> row = rows.get(cursor[0]);
                    String column = lower((String) args[0]);
                    if (!row.containsKey(column)) {
                        throw new SQLException("Column '" + args[0] + "' not found.", "S0022");
                    }
                    Object value = row.get(column);
                    if (method.equals("getString")) {
                        return value == null ? null : String.valueOf(value);
                    } else if (method.equals("getBoolean")) {
                        return value != null && (Boolean) value;
                    } else {
                        return value == null ? 0 : (Integer) value;
                    }
                }
                default:
                    throw unsupported("ResultSet", method);
            }
        });
    }

    @Override
    public boolean acceptsURL(String url) {
        return url != null && url.startsWith("jdbc:mysql:");
    }

    @Override
    public DriverPropertyInfo[] getPropertyInfo(String url, Properties info) {
        return new DriverPropertyInfo[0];
    }

    @Override
    public int getMajorVersion() {
        return 1;
    }

    @Override
    public int getMinorVersion() {
        return 0;
    }

    @Override
    public boolean jdbcCompliant() {
        return false;
    }

    @Override
    public Logger getParentLogger() throws SQLFeatureNotSupportedException {
        throw new SQLFeatureNotSupportedException();
    }

    @FunctionalInterface
    private interface Answer {
        Object answer(String method, Object[] args) throws SQLException;
    }

    @SuppressWarnings("unchecked")
    private static <T> T proxy(Class<T> type, Answer answer) {
        InvocationHandler handler = (self, method, args) -> switch (method.getName()) {
            case "toString" -> "fake MySQL " + type.getSimpleName();
            case "hashCode" -> System.identityHashCode(self);
            case "equals" -> self == args[0];
            default -> answer.answer(method.getName(), args == null ? new Object[0] : args);
        };
        return (T) Proxy.newProxyInstance(FakeMysql.class.getClassLoader(), new Class<?>[]{type}, handler);
    }

    private static UnsupportedOperationException unsupported(String type, String method) {
        return new UnsupportedOperationException("the fake MySQL has no " + type + "." + method);
    }

    private static String lower(String name) {
        return name.toLowerCase(Locale.ROOT);
    }

    // one space between words, none at the ends
    private static String squeeze(String sql) {
        StringBuilder squeezed = new StringBuilder();
        boolean space = false;
        for (char c : sql.trim().toCharArray()) {
            if (Character.isWhitespace(c)) {
                space = true;
            } else {
                if (space) {
                    squeezed.append(' ');
                    space = false;
                }
                squeezed.append(c);
            }
        }
        return squeezed.toString();
    }
}
