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
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.mcmiddleearth.connect.ConnectPlugin;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.MemoryConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.mariadb.jdbc.MySQLDataSource;

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
    
    private final MySQLDataSource dataBase;
    
    private Connection dbConnection;
    
    private PreparedStatement insertPlayerRpSettings;
    private PreparedStatement updatePlayerRpSettings;
    private PreparedStatement selectPlayerRpSettings;
    
    private ExecutorService executor = Executors.newSingleThreadExecutor();
    
    private BukkitTask keepAliveTask;
    
    private boolean connected; 
    
    public RpDatabaseConnector(ConfigurationSection config) {
        if(config==null) {
            config = new MemoryConfiguration();
        }
        dbUser = config.getString("user","development");
        dbPassword = config.getString("password","development");
        dbName = config.getString("dbName","development");
        dbIp = config.getString("ip", "localhost");
        port = config.getInt("port",3306);
        dataBase = new MySQLDataSource(dbIp,port,dbName);
        connect();
        keepAliveTask = new BukkitRunnable() {
            @Override
            public void run() {
                checkConnection();
                Logger.getGlobal().info("ArchitectTasks: " + Bukkit.getScheduler().getPendingTasks().stream().filter(task -> task.getOwner().equals(ArchitectPlugin.getPluginInstance())).count());
                Logger.getGlobal().info("ArchitectWorker: " + Bukkit.getScheduler().getActiveWorkers().stream().filter(task -> task.getOwner().equals(ArchitectPlugin.getPluginInstance())).count());
            }
        }.runTaskTimer(ArchitectPlugin.getPluginInstance(),0,1200);
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
        try {
            if(connected && dbConnection.isValid(5)) {
                ArchitectPlugin.getPluginInstance().getLogger().log(Level.INFO, 
                        "Successfully checked connection to rp database.");
            } else {
                if(dbConnection!=null) {
                    dbConnection.close();
                }
                new BukkitRunnable() {
                    @Override
                    public void run() {
                        connect();
                        ArchitectPlugin.getPluginInstance().getLogger().log(Level.INFO, "Reconnecting to rp database.");
                    }
                }.runTaskAsynchronously(ConnectPlugin.getInstance());
            }
        } catch (SQLException ex) {
            Logger.getLogger(RpDatabaseConnector.class.getName()).log(Level.SEVERE, null, ex);
            connected = false;
        }
    }
    
    private synchronized void connect() {
        try {
            dbConnection = dataBase.getConnection(dbUser, dbPassword);
            
            checkTables();
            
            insertPlayerRpSettings = dbConnection.prepareStatement("INSERT INTO architect_rp (uuid, auto, variant, resolution, currentURL) "
                                                                  +"VALUES (?,?,?,?,?)");
            updatePlayerRpSettings = dbConnection.prepareStatement("UPDATE architect_rp SET auto=?, variant=?, resolution=?, currentURL=? "
                                                                  +"WHERE uuid = ?");
            selectPlayerRpSettings = dbConnection.prepareStatement("SELECT auto, variant, resolution, currentURL FROM architect_rp "
                                                                 + "WHERE uuid = ?");

            connected = true;
        } catch (SQLException ex) {
            Logger.getLogger(RpDatabaseConnector.class.getName()).log(Level.SEVERE, null, ex);
            connected = false;
        }
    }
    
    public synchronized void disconnect() {
        connected = false;
        if(keepAliveTask!=null) {
            keepAliveTask.cancel();
        }
        if(dbConnection!=null) {
            try {
                dbConnection.close();
            } catch (SQLException ex) {
                Logger.getLogger(RpDatabaseConnector.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
    }
    
    private synchronized void checkTablesSync(){
        try {
            Logger.getLogger(ArchitectPlugin.class.getName()).info("checking tables...");
            String statement = "CREATE TABLE IF NOT EXISTS architect_rp (uuid VARCHAR(50), "
                             + "auto BIT, variant VARCHAR(30), resolution INT, currentURL VARCHAR(100), KEY(uuid))";
            dbConnection.createStatement().execute(statement);
        } catch (SQLException ex) {
            Logger.getLogger(RpDatabaseConnector.class.getName()).log(Level.SEVERE, null, ex);
        }
    }
    
    private void checkTables(){
        executeAsync(player -> checkTablesSync(),null);
    }
    
    public synchronized void loadRpSettings(UUID uuid, Map<UUID,RpPlayerData> dataMap) {
        new BukkitRunnable() {
            @Override
            public void run() {
                try {
                    selectPlayerRpSettings.setString(1, uuid.toString());
                    ResultSet result = selectPlayerRpSettings.executeQuery();
                    if(result.next()) {
                        try {
                            RpPlayerData data = new RpPlayerData();
                            data.setAutoRp(result.getBoolean("auto"));
                            data.setCurrentRpUrl(result.getString("currentURL"));
                            data.setVariant(result.getString("variant"));
                            data.setResolution(result.getInt("resolution"));
                            dataMap.put(uuid,data);
                        } catch (SQLException ex) {
                            Logger.getLogger(RpDatabaseConnector.class.getName()).log(Level.SEVERE, null, ex);
                            dataMap.put(uuid,null);
                        }
                    }
                } catch (SQLException ex) {
                    Logger.getLogger(RpDatabaseConnector.class.getName()).log(Level.SEVERE, null, ex);
                    dataMap.put(uuid,null);
                    connected = false;
                }
            }
        }.runTaskAsynchronously(ArchitectPlugin.getPluginInstance());
    }
    
    
    public synchronized void saveRpSettings(Player player, RpPlayerData data) {
        new BukkitRunnable() {
            @Override
            public void run() {
                try {
                    selectPlayerRpSettings.setString(1, player.getUniqueId().toString());
                    ResultSet result = selectPlayerRpSettings.executeQuery();
                    if(result.next()) {
                        updateRpSettings(player, data);
                    } else {
                        insertRpSettings(player, data);
                    }
                } catch (SQLException ex) {
                    Logger.getLogger(RpDatabaseConnector.class.getName()).log(Level.SEVERE, null, ex);
                    connected = false;
                }
            }
        }.runTaskAsynchronously(ArchitectPlugin.getPluginInstance());
    }
    
    
    private synchronized void updateRpSettings(Player player, RpPlayerData data) throws SQLException {
        updatePlayerRpSettings.setBoolean(1, data.isAutoRp());
        updatePlayerRpSettings.setString(2, data.getVariant());
        updatePlayerRpSettings.setInt(3, data.getResolution());
        updatePlayerRpSettings.setString(4, data.getCurrentRpUrl());
        updatePlayerRpSettings.setString(5, player.getUniqueId().toString());
        updatePlayerRpSettings.executeUpdate();
    }
    
    private synchronized void insertRpSettings(Player player, RpPlayerData data) throws SQLException {
        insertPlayerRpSettings.setString(1, player.getUniqueId().toString());
        insertPlayerRpSettings.setBoolean(2, data.isAutoRp());
        insertPlayerRpSettings.setString(3, data.getVariant());
        insertPlayerRpSettings.setInt(4, data.getResolution());
        insertPlayerRpSettings.setString(5, data.getCurrentRpUrl());
        insertPlayerRpSettings.executeUpdate();
    }
    
}
