/*
 * Copyright (C) 2018 Eriol_Eandur
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

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.ListenerPriority;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketEvent;
import com.google.gson.Gson;
import com.mcmiddleearth.architect.ArchitectPlugin;
import com.mcmiddleearth.architect.PluginData;
import com.mcmiddleearth.architect.serverResoucePack.RegionEditConversation.RegionEditConversationFactory;
import com.mcmiddleearth.util.DevUtil;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.net.URL;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author Eriol_Eandur
 */
public class RpManager {
    
    private static final File playerFile = new File(ArchitectPlugin.getPluginInstance().getDataFolder(),"/playerRpData.dat");
    private static final File regionFolder = new File(ArchitectPlugin.getPluginInstance().getDataFolder(),"regions");
    
    private static final String rpDatabaseConfig = "rpSettingsDatabase";
    
    private static final Map<String, RpRegion> regions = new HashMap<>();
    
    private static final Map<UUID,RpPlayerData> playerRpData = new HashMap<>();
    
    private static final RpDatabaseConnector dbConnector = new RpDatabaseConnector(ArchitectPlugin.getPluginInstance().getConfig().getConfigurationSection(rpDatabaseConfig));
    
    private static final RegionEditConversationFactory regionEditConversationFactory
            = new RegionEditConversationFactory(ArchitectPlugin.getPluginInstance());
    
    public static void init() {
        if(!regionFolder.exists()) {
            regionFolder.mkdir();
        }
        regions.clear();
        for(File file: regionFolder.listFiles((File dir, String name) -> name.endsWith(".reg"))) {
            try {
                YamlConfiguration config = new YamlConfiguration();
                config.load(file);
                final ConfigurationSection section = config.getConfigurationSection("rpRegion");
                new BukkitRunnable() {
                    int counter = 10;
                    @Override
                    public void run() {
                        RpRegion region = RpRegion.loadFromMap((Map<String,Object>)section.getValues(true));
                        if(region!=null) {
                            DevUtil.log("loaded region: "+region.getName());
                            addRegion(region);  
                            cancel();
                        } else {
                            counter--;
                            DevUtil.log("failed to load region: "+region+" tries left: "+counter);
                            if(counter<1) {
                                cancel();
                            }
                        }
                    }
                }.runTaskTimer(ArchitectPlugin.getPluginInstance(), 200, 20);
            } catch (IOException | InvalidConfigurationException ex) {
                Logger.getLogger(RpManager.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
        importResourceRegions();
    }
    
    public static RpRegion getRegion(Location loc) {
        RpRegion maxWeight = null;
        for(RpRegion region: regions.values()) {
            if((maxWeight==null || region.getWeight() > maxWeight.getWeight()) 
                    && region.contains(loc)) {
                maxWeight = region;
            }
        }
        return maxWeight;
    }
    
    public static RpRegion getRegion(String name) {
        return regions.get(name);
    }
    
    public static void updateDynmapRegions() {
        RpDynmapUtil.clearMarkers();
        regions.values().stream().sorted(Comparator.comparingInt(RpRegion::getWeight))
               .forEach(RpDynmapUtil::createMarker);
    }
    
    public static boolean removeRegion(String name) {
        RpRegion region = regions.get(name);
        if(region!=null) {
            regions.remove(name);
            new File(regionFolder,name+".reg").delete();
            updateDynmapRegions();
            return true;
        }
        return false;
    }
    
    public static void addRegion(RpRegion region) {
        regions.put(region.getName(), region);
        updateDynmapRegions();
    }
    
    public static RpPlayerData getPlayerData(Player player) {
        RpPlayerData data = playerRpData.get(player.getUniqueId());
        if(data == null) {
            data = new RpPlayerData();
            playerRpData.put(player.getUniqueId(), data);
        }
        return data;
    }
    
    public static boolean hasPlayerDataLoaded(Player player) {
        return playerRpData.containsKey(player.getUniqueId());
    }
    
    /**
     * Gets the first configured URL of the given rp name.
     * @param rp name of the RP
     * @param player player to use rp setting from, may be null for default settings
     * @return first confiured URL in config.yml
     */
    public static String getRpUrl(String rp, Player player) {
        ConfigurationSection section = getConfigSection(rp, player);
        if(section!=null) {
            return section.getString("url");
        }
        return "";
    }
    
    public static byte[] getSHA(String rp, Player player) {
        byte[] result = new byte[20];
        ConfigurationSection section = getConfigSection(rp, player);
        if(section!=null) {
            String sha = section.getString("sha");
            return stringToSHA(sha);
        }
        return result;
    }
    
    private static byte[] stringToSHA(String sha) {
        byte[] result = new byte[20];
        if(sha==null) {
            return result;
        }
        result = new BigInteger(sha.trim(),16).toByteArray();
        if(result.length>20) {
            result = Arrays.copyOfRange(result,1,21);
        }
        return result;
    }
    
    private static ConfigurationSection getConfigSection(String rp, Player player) {
        RpPlayerData data;
        if(player!=null) {
            data = getPlayerData(player);
        } else {
            data = new RpPlayerData();
        }
        ConfigurationSection config = getRpConfig().getConfigurationSection(rp);
        if(config != null) {
            ConfigurationSection section = config.getConfigurationSection(getResolutionKey(data.getResolution()));
            if(section==null) {
                section = config.getConfigurationSection(config.getKeys(false).iterator().next());
            }
            ConfigurationSection varSection = section.getConfigurationSection(data.getVariant());
            String url;
            if(varSection==null) {
                return section.getConfigurationSection(section.getKeys(false).iterator().next());
            } else {
                return varSection;
            }
        }
        return null;
    }
        
    public static String getCurrentRpName(Player player) {
        return getRpForUrl(getPlayerData(player).getCurrentRpUrl());
    }

    public static String matchRpName(String rpKey) {
        for(String search: getRpConfig().getKeys(false)) {
            if(search.toLowerCase().startsWith(rpKey.toLowerCase())) {
                return search;
            }
        }
        return "";
    }
    
    public static boolean setRpRegion(Player player){
        RpPlayerData data = RpManager.getPlayerData(player);
        if(data.isAutoRp()) {
            RpRegion newRegion = RpManager.getRegion(player.getLocation());
            if(newRegion != data.getCurrentRegion()) {
                data.setCurrentRegion(newRegion);
                if(newRegion!=null) {
                    setRp(newRegion.getRp(), player, false);
                    return true;
                }
            }
        }
        return false;
    }
    
    public static boolean setRp(String rpName, Player player, boolean force) {
        String url = getRpUrl(rpName, player);
        RpPlayerData data = getPlayerData(player);
        if(url!=null && data!=null && !url.equals("") && (force || !url.equals(data.getCurrentRpUrl()))) {
            data.setCurrentRpUrl(url);
            player.setResourcePack(url, getSHA(rpName, player));
            savePlayerData(player);
            return true;
        }
        return false;
    }
    
    public static byte[] getSHAForUrl(String url) {
        for(String rpName: getRpConfig().getKeys(false)) {
            ConfigurationSection section = getRpConfig().getConfigurationSection(rpName);
            for(String key: section.getKeys(false)) {
                ConfigurationSection pxSection = section.getConfigurationSection(key);
                for(String varKey: pxSection.getKeys(false)) {
                    ConfigurationSection varSection = pxSection.getConfigurationSection(varKey);
                    if(varSection.getString("url").equals(url)) {
                        return stringToSHA(varSection.getString("sha"));
                    }
                }
            }
        }
        return new byte[20];
    }
    
    public static String getRpForUrl(String url) {
        for(String rpName: getRpConfig().getKeys(false)) {
            ConfigurationSection section = getRpConfig().getConfigurationSection(rpName);
            for(String key: section.getKeys(false)) {
                ConfigurationSection pxSection = section.getConfigurationSection(key);
                for(String varKey: pxSection.getKeys(false)) {
                    ConfigurationSection varSection = pxSection.getConfigurationSection(varKey);
                    if(varSection.getString("url").equals(url)) {
                        return rpName;
                    }
                }
            }
        }
        return "";
    }
    
    public static boolean searchRpKey(String key) {
        return getRpConfig().getKeys(true).stream().anyMatch((search) -> (search.endsWith(key)));
    }
    
    private static ConfigurationSection getRpConfig() {
        return ArchitectPlugin.getPluginInstance().getConfig().getConfigurationSection("ServerResourcePacks");
    }

    public static boolean refreshSHA(CommandSender cs, String rp) {
        ConfigurationSection config = getRpConfig().getConfigurationSection(rp);
        if(config!=null) {
            for(String resolutionKey: config.getKeys(false)) {
                ConfigurationSection resolutionSection = config.getConfigurationSection(resolutionKey);
                for(String variantKey: resolutionSection.getKeys(false)) {
                    try {
                        ConfigurationSection variantSection = resolutionSection.getConfigurationSection(variantKey);
                        URL url = new URL(variantSection.getString("url"));
                        InputStream fis = url.openStream();
                        MessageDigest sha1 = MessageDigest.getInstance("SHA1");

                        byte[] data = new byte[1024];
                        int read = 0;
                        long time = System.currentTimeMillis();
                        while ((read = fis.read(data)) != -1) {
                            sha1.update(data, 0, read);
                            if(System.currentTimeMillis()-time>5000) {
                                time = System.currentTimeMillis();
                                PluginData.getMessageUtil().sendInfoMessage(cs, "calculating ...");
                            }
                        }
                        byte[] hashBytes = sha1.digest();
                        StringBuilder sb = new StringBuilder();
                        for (byte b : hashBytes) {
                            sb.append(String.format("%02x", b));
                        }
                        String hashString = sb.toString();
                        variantSection.set("sha", hashString);
                        ArchitectPlugin.getPluginInstance().saveConfig();
                    } catch (IOException | NoSuchAlgorithmException ex) {
                        Logger.getLogger(RpManager.class.getName()).log(Level.SEVERE, null, ex);
                        return false;
                    }
                }
            }
            return true;
        }
        return false;
    }
    
    public static String getResolutionKey(int px) {
        return px+"px";
    }

    public static void savePlayerData(Player player) {
        dbConnector.saveRpSettings(player, playerRpData.get(player.getUniqueId()));
    }
    
    public static void loadPlayerData(UUID uuid) {
        dbConnector.loadRpSettings(uuid,playerRpData);
    }
    
    public static void saveRpRegion(RpRegion region) {
        try {
            YamlConfiguration config = new YamlConfiguration();
            config.set("rpRegion", region.saveToMap());
            config.save(new File(regionFolder,region.getName()+".reg"));
        } catch (IOException ex) {
            Logger.getLogger(RpManager.class.getName()).log(Level.SEVERE, null, ex);
        }
    }
    
    private static void importResourceRegions() {
        for(File file: regionFolder.listFiles((File dir, String name) -> name.endsWith(".json"))) {
            Gson gson = new Gson();
            try(FileReader fr = new FileReader(file)) {
                ResourceRegionData data = new ResourceRegionData();
                data = gson.fromJson(fr, data.getClass());
                final ConfigurationSection section = data.getConfig().getConfigurationSection("rpRegion");
                new BukkitRunnable() {
                    int counter = 10;
                    @Override
                    public void run() {
                        RpRegion region = RpRegion.loadFromMap((Map<String,Object>)section.getValues(true));
                        if(region!=null) {
                            DevUtil.log("imported region: "+region.getName());
                            addRegion(region);  
                            saveRpRegion(region);
                            file.delete();
                            cancel();
                        } else {
                            counter--;
                            DevUtil.log("failed to import region: "+region+" tries left: "+counter);
                            if(counter<1) {
                                cancel();
                            }
                        }
                    }
                }.runTaskTimer(ArchitectPlugin.getPluginInstance(), 200, 20);
            } catch (IOException ex) {
                Logger.getLogger(RpManager.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
    }
    
    private static class ResourceRegionData {
        public String name;
        public String packUrl;
        public int weight;
        public String worldName;
        public int n;
        public int[] xpoints;
        public int[] zpoints;
        
        public YamlConfiguration getConfig() {
            YamlConfiguration config = new YamlConfiguration();
            ConfigurationSection rpSection = config.createSection("rpRegion");
            rpSection.set("name",name);
            rpSection.set("weight",weight);
            rpSection.set("rp",RpManager.getRpForUrl(packUrl));
            ConfigurationSection regionSection = rpSection.createSection("region");
            regionSection.set("world", worldName);
            regionSection.set("minY",0);
            regionSection.set("maxY",255);
            regionSection.set("type","Polygonal2DRegion");
            List<String> points = new ArrayList<>();
            for(int i = 0; i<n; i++) {
                points.add(xpoints[i]+","+zpoints[i]);
            }
            regionSection.set("points",points);
            return config;
        }
    }
    
    private static void addPacketListener() {
        Logger.getLogger(ArchitectPlugin.class.getName()).log(Level.WARNING,"Adding RP packet listener");
        ProtocolManager protocolManager = protocolManager = ProtocolLibrary.getProtocolManager();
        protocolManager.addPacketListener(
                new PacketAdapter(ArchitectPlugin.getPluginInstance(), ListenerPriority.NORMAL,
                        PacketType.Play.Server.RESOURCE_PACK_SEND) {
                    @Override
                    public void onPacketSending(PacketEvent event) {
                        // Item packets (id: 0x29)
                        if (event.getPacketType() ==
                                PacketType.Play.Server.RESOURCE_PACK_SEND) {
                            Logger.getLogger(ArchitectPlugin.class.getName())
                                    .log(Level.WARNING, "Sending RP to player " + event.getPlayer());
                        }
                    }
                });
    }

    public static Map<String, RpRegion> getRegions() {
        return regions;
    }

    public static RpDatabaseConnector getDbConnector() {
        return dbConnector;
    }

    public static RegionEditConversationFactory getRegionEditConversationFactory() {
        return regionEditConversationFactory;
    }
}
