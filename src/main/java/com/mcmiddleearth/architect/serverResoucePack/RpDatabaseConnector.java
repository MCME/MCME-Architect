/*
 * Copyright (C) 2020 MCME
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.mcmiddleearth.architect.serverResoucePack;

import com.mcmiddleearth.architect.ArchitectPlugin;
import com.mcmiddleearth.architect.Log;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.MemoryConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.sql.*;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 *
 * @author Eriol_Eandur
 */
public class RpDatabaseConnector {
    private final String dbUser;
    private final String dbPassword;
    private final String dbName;
    private final String dbIp;
    private final int port;

    private Connection dbConnection;

    private PreparedStatement insertPlayerRpSettings;
    private PreparedStatement updatePlayerRpSettings;
    private PreparedStatement selectPlayerRpSettings;

    private final BukkitTask keepAliveTask;

    private boolean connected;

    private final boolean dbConfigured;

    // The stored statuses this Architect does not know, each logged once rather than once for every player.
    private final Set<String> unknownStatuses = ConcurrentHashMap.newKeySet();

    // The bundled config.yml ships rpSettingsDatabase with this as database and user. A server that still has it
    // has no RP database: trying to connect would only fail, and say so, at every start and every minute.
    private static final String PLACEHOLDER = "xxx";

    // A database that does not answer must not hold a thread for ever, nor this connector's lock, which the main
    // thread needs at start-up, when it connects, and in onDisable, when disconnect() waits for it. A reachable
    // database takes milliseconds to accept a connection, so 10 seconds only ends a wait that cannot succeed.
    // A read gets 60 seconds, well above the 10-second query timeout of the statements, so a slow query still
    // ends as before and only a connection that has gone silent is cut.
    private static final int CONNECT_TIMEOUT_MILLIS = 10_000;
    private static final int SOCKET_TIMEOUT_MILLIS = 60_000;

    public RpDatabaseConnector(ConfigurationSection config) {
        boolean present = (config != null);
        if(config==null) {
            config = new MemoryConfiguration();
        }
        dbUser = config.getString("user","development");
        dbPassword = config.getString("password","development");
        dbName = config.getString("dbName","development");
        dbIp = config.getString("ip", "localhost");
        port = config.getInt("port",3306);
        boolean placeholder = PLACEHOLDER.equals(dbName) || PLACEHOLDER.equals(dbUser);
        dbConfigured = present && !placeholder;
        if(dbConfigured) {
            connect();
            keepAliveTask = new BukkitRunnable() {
                @Override
                public void run() {
                    checkConnection();
                }
            }.runTaskTimerAsynchronously(ArchitectPlugin.getPluginInstance(),0,1200);
        } else if(placeholder) {
            Log.info("The RP database settings (rpSettingsDatabase) are the placeholders config.yml ships with; "
                    + "RP settings will not be persisted.");
            keepAliveTask = null;
        } else {
            Log.info("No RP database configured; RP settings will not be persisted.");
            keepAliveTask = null;
        }
    }

    private void executeAsync(Consumer<Player> method, Player player) {
        new BukkitRunnable() {
            @Override
            public void run() {
                if(!connected) {
                    connect();
                }
                method.accept(player);
            }
        }.runTaskAsynchronously(ArchitectPlugin.getPluginInstance());
    }

    private synchronized void checkConnection() {
        if(!dbConfigured) {
            return;
        }
        try {
            if(connected && dbConnection.isValid(5)) {
                // connection healthy, nothing to do
            } else {
                if(dbConnection!=null) {
                    dbConnection.close();
                }
                connect();
                Log.info("Reconnecting to RP database " + dbName + " at " + dbIp + ":" + port);
            }
        } catch (SQLException ex) {
            Log.error("Failed to check/reconnect RP database connection to " + dbName + " at " + dbIp + ":" + port, ex);
            connected = false;
        }
    }

    private synchronized void connect() {
        try {
            dbConnection = DriverManager.getConnection(url(), dbUser, dbPassword);

            checkTables();

            insertPlayerRpSettings = dbConnection.prepareStatement("INSERT INTO architect_rp (uuid, auto, variant, resolution, client, currentURL, status) "
                                                                  +"VALUES (?,?,?,?,?,?,?)");
            updatePlayerRpSettings = dbConnection.prepareStatement("UPDATE architect_rp SET auto=?, variant=?, resolution=?, client=?, currentURL=?, status=? "
                                                                  +"WHERE uuid = ?");
            selectPlayerRpSettings = dbConnection.prepareStatement("SELECT auto, variant, resolution, client, currentURL, status FROM architect_rp "
                                                                 + "WHERE uuid = ?");
            insertPlayerRpSettings.setQueryTimeout(10);
            updatePlayerRpSettings.setQueryTimeout(10);
            selectPlayerRpSettings.setQueryTimeout(10);

            connected = true;
        } catch (SQLException ex) {
            Log.error("Failed to connect to RP database " + dbName + " at " + dbIp + ":" + port + " as user " + dbUser, ex);
            connected = false;
        }
    }

    // jdbc:mysql://ip:port/dbName with the timeouts. Options that dbName already carries are kept, and a timeout
    // it sets itself is the operator's: ours is added only for a timeout it leaves out. Connector/J reads option
    // names as they are written, so a name in another case, such as connecttimeout, sets nothing, and ours is added.
    private String url() {
        Set<String> given = optionNames(dbName);
        StringBuilder url = new StringBuilder("jdbc:mysql://" + dbIp + ":" + port + "/" + dbName);
        char separator = dbName.contains("?") ? '&' : '?';
        if(!given.contains("connectTimeout")) {
            url.append(separator).append("connectTimeout=").append(CONNECT_TIMEOUT_MILLIS);
            separator = '&';
        }
        if(!given.contains("socketTimeout")) {
            url.append(separator).append("socketTimeout=").append(SOCKET_TIMEOUT_MILLIS);
        }
        return url.toString();
    }

    // The names of the options in "name?a=1&b=2", as they are written.
    private static Set<String> optionNames(String dbName) {
        Set<String> names = new HashSet<>();
        int query = dbName.indexOf('?');
        if(query >= 0) {
            for(String option : dbName.substring(query + 1).split("&")) {
                int equals = option.indexOf('=');
                names.add(equals < 0 ? option : option.substring(0, equals));
            }
        }
        return names;
    }

    public synchronized void disconnect() {
        connected = false;
        if(keepAliveTask!=null) {
            keepAliveTask.cancel();
        }
        if(dbConnection!=null) {
            try {
                if(insertPlayerRpSettings!=null) insertPlayerRpSettings.close();
                if(updatePlayerRpSettings!=null) updatePlayerRpSettings.close();
                if(selectPlayerRpSettings!=null) selectPlayerRpSettings.close();
                dbConnection.close();
            } catch (SQLException ex) {
                Log.error("Failed to close RP database connection to " + dbName + " at " + dbIp + ":" + port, ex);
            }
        }
    }

    private synchronized void checkTablesSync(){
        try {
            Log.debug("Checking RP database tables exist on " + dbName);
            String statement = "CREATE TABLE IF NOT EXISTS architect_rp (uuid VARCHAR(50), "
                             + "auto BIT, variant VARCHAR(30), resolution INT, client VARCHAR(30), "
                             + "currentURL VARCHAR(100), KEY(uuid))";
            dbConnection.createStatement().execute(statement);
            // Bring pre-existing tables (created before the client column existed) up to date.
            try {
                dbConnection.createStatement().execute("ALTER TABLE architect_rp ADD COLUMN client VARCHAR(30)");
                Log.info("Added missing 'client' column to architect_rp on RP database " + dbName);
            } catch (SQLException alterEx) {
                // Expected when the column already exists (duplicate column) — nothing to do.
                Log.debug("architect_rp already has the 'client' column on " + dbName);
            }
            // The status column holds each player's resource pack status, for the next server the player joins
            // to read. Tables created before it existed get it here.
            try {
                dbConnection.createStatement().execute("ALTER TABLE architect_rp ADD COLUMN status VARCHAR(30)");
                Log.info("Added missing 'status' column to architect_rp on RP database " + dbName);
            } catch (SQLException alterEx) {
                // Expected when the column already exists (duplicate column) — nothing to do.
                Log.debug("architect_rp already has the 'status' column on " + dbName);
            }
        } catch (SQLException ex) {
            Log.error("Failed to create/verify architect_rp table on RP database " + dbName, ex);
        }
    }

    private void checkTables(){
        executeAsync(player -> checkTablesSync(),null);
    }

    /** Whether an RP database is configured. Without one nothing is read or saved, and players have defaults. */
    public boolean isConfigured() {
        return dbConfigured;
    }

    public void loadRpSettings(UUID uuid, Map<UUID,RpPlayerData> dataMap) {
        loadRpSettings(uuid, dataMap, () -> {});
    }

    // As above; ended runs once the load has ended, whatever it stored, and also when it failed.
    public void loadRpSettings(UUID uuid, Map<UUID,RpPlayerData> dataMap, Runnable ended) {
        new BukkitRunnable() {
            @Override
            public void run() {
                try {
                    loadRpSettingsSync(uuid, dataMap);
                } finally {
                    ended.run();
                }
            }
        }.runTaskAsynchronously(ArchitectPlugin.getPluginInstance());
    }

    private synchronized void loadRpSettingsSync(UUID uuid, Map<UUID, RpPlayerData> dataMap) {
        if(!connected || selectPlayerRpSettings==null) {
            // No reachable DB: seed a default so hasPlayerDataLoaded() is true and the join flow
            // proceeds immediately with defaults instead of polling ~15s for a load that won't come.
            dataMap.put(uuid, new RpPlayerData());
            return;
        }
        try {
            selectPlayerRpSettings.setString(1, uuid.toString());
            try (ResultSet result = selectPlayerRpSettings.executeQuery()) {
                if(result.next()) {
                    RpPlayerData data = new RpPlayerData();
                    data.setAutoRp(result.getBoolean("auto"));
                    data.setCurrentRpUrl(result.getString("currentURL"));
                    data.setVariant(result.getString("variant"));
                    data.setResolution(result.getInt("resolution"));
                    data.setClient(result.getString("client"));
                    if(data.getClient()==null) data.setClient("vanilla");
                    data.setCurrentRpStatus(storedStatus(result.getString("status")));
                    dataMap.put(uuid,data);
                } else {
                    // No row yet, as for a new player: the defaults, which the first save writes as the
                    // player's row. Without them the join check would wait its whole time for a load that
                    // cannot come. Data that something made for the player meanwhile is kept.
                    dataMap.putIfAbsent(uuid, new RpPlayerData());
                }
            }
        } catch (SQLException ex) {
            Log.error("Failed to load RP settings for player " + uuid + " from database " + dbName, ex);
            dataMap.put(uuid,new RpPlayerData()); // load-failed marker (default; ConcurrentHashMap forbids null)
            connected = false;
        }
    }


    // The status stored for a player. None, as in rows from before the status column, is NOT_SENT, and so is a
    // name this Architect does not know, which a newer one sharing the table may have written: the player's
    // other settings still load.
    private RpPlayerStatus storedStatus(String name) {
        if(name == null || name.isBlank()) {
            return RpPlayerStatus.NOT_SENT;
        }
        try {
            return RpPlayerStatus.valueOf(name);
        } catch (IllegalArgumentException ex) {
            if(unknownStatuses.add(name)) {
                Log.warn("Unknown RP status '" + name + "' in architect_rp on RP database " + dbName
                        + "; players with it are read as " + RpPlayerStatus.NOT_SENT + ".");
            }
            return RpPlayerStatus.NOT_SENT;
        }
    }

    public void saveRpSettings(Player player, RpPlayerData data) {
        new BukkitRunnable() {
            @Override
            public void run() {
                saveRpSettingsSync(player, data);
            }
        }.runTaskAsynchronously(ArchitectPlugin.getPluginInstance());
    }

    private synchronized void saveRpSettingsSync(Player player, RpPlayerData data) {
        if(!connected || selectPlayerRpSettings==null) {
            return; // no reachable DB: nothing to persist
        }
        try {
            selectPlayerRpSettings.setString(1, player.getUniqueId().toString());
            boolean exists;
            try (ResultSet result = selectPlayerRpSettings.executeQuery()) {
                exists = result.next();
            }
            if(exists) {
                updateRpSettings(player, data);
            } else {
                insertRpSettings(player, data);
            }
        } catch (SQLException ex) {
            Log.error("Failed to save RP settings for player " + player.getName() + " (" + player.getUniqueId() + ") to database " + dbName, ex);
            connected = false;
        }
    }

    private synchronized void updateRpSettings(Player player, RpPlayerData data) throws SQLException {
        updatePlayerRpSettings.setBoolean(1, data.isAutoRp());
        updatePlayerRpSettings.setString(2, data.getVariant());
        updatePlayerRpSettings.setInt(3, data.getResolution());
        updatePlayerRpSettings.setString(4, data.getClient());
        updatePlayerRpSettings.setString(5, data.getCurrentRpUrl());
        updatePlayerRpSettings.setString(6, data.getCurrentRpStatus().name());
        updatePlayerRpSettings.setString(7, player.getUniqueId().toString());
        updatePlayerRpSettings.executeUpdate();
    }

    private synchronized void insertRpSettings(Player player, RpPlayerData data) throws SQLException {
        insertPlayerRpSettings.setString(1, player.getUniqueId().toString());
        insertPlayerRpSettings.setBoolean(2, data.isAutoRp());
        insertPlayerRpSettings.setString(3, data.getVariant());
        insertPlayerRpSettings.setInt(4, data.getResolution());
        insertPlayerRpSettings.setString(5, data.getClient());
        insertPlayerRpSettings.setString(6, data.getCurrentRpUrl());
        insertPlayerRpSettings.setString(7, data.getCurrentRpStatus().name());
        insertPlayerRpSettings.executeUpdate();
    }

    public synchronized boolean dropTable() {
        try {
            checkConnection();
            if (!connected || dbConnection == null) {
                Log.error("No database connection");
                return false;
            }
            String statement = "DROP TABLE IF EXISTS architect_rp";
            dbConnection.createStatement().execute(statement);
            Log.info("architect_rp successfully deleted");
            checkTables();
            return true;

        } catch (SQLException ex) {
            Log.error("Error while deleting architect_rp table.", ex);
            connected = false;
            return false;
        }
    }

}
