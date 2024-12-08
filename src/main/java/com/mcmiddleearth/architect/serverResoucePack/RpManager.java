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
import com.mcmiddleearth.connect.log.Log;
import com.mcmiddleearth.util.DevUtil;
import com.mcmiddleearth.util.ResourceUtil;
import com.viaversion.viaversion.api.Via;
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
import java.util.stream.Collectors;

/**
 *
 * @author Eriol_Eandur
 */
public class RpManager {
    
    private static final File playerFile = new File(ArchitectPlugin.getPluginInstance().getDataFolder(),"/playerRpData.dat");
    private static final File regionFolder = new File(ArchitectPlugin.getPluginInstance().getDataFolder(),"regions");

    private static final File versionFile = new File(ArchitectPlugin.getPluginInstance().getDataFolder(), "protocolVersions.yml");
    private static final Map<String, Integer> protocolVersions = new HashMap<>();

    private static final String rpDatabaseConfig = "rpSettingsDatabase";

    private static final Map<String, RpRegion> regions = new HashMap<>();

    private static final Map<UUID,RpPlayerData> playerRpData = new HashMap<>();

    private static final Map<UUID, String> sodiumClients = new HashMap<>();

    private static final RpDatabaseConnector dbConnector = new RpDatabaseConnector(ArchitectPlugin.getPluginInstance().getConfig().getConfigurationSection(rpDatabaseConfig));
    
    private static final RegionEditConversationFactory regionEditConversationFactory
            = new RegionEditConversationFactory(ArchitectPlugin.getPluginInstance());
    
    public static void init() {
        if(!versionFile.exists()) {
            ResourceUtil.saveResourceToFile(versionFile.getName(),versionFile);
        }
        YamlConfiguration versionConfig = new YamlConfiguration();
        try {
            versionConfig.load(versionFile);
            for(String version: versionConfig.getKeys(false)) {
                protocolVersions.put(version, versionConfig.getInt(version));
            }
        } catch (IOException | InvalidConfigurationException e) {
            e.printStackTrace();
        }
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
            data.setProtocolVersion(player.getProtocolVersion());
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
        if (player != null) {
            data = getPlayerData(player);
            if(data.getProtocolVersion()==0) {
                data.setProtocolVersion(Via.getAPI().getPlayerProtocolVersion(player.getUniqueId()).getVersion());
            }
        } else {
            data = new RpPlayerData();
        }
        ConfigurationSection rpSection = getRpConfig().getConfigurationSection(rp);
        if (rpSection != null) {
            if (rpSection.contains("url")) {
                return rpSection;
            } else {
                if (rpSection.contains(data.getClient())) {
                    return searchInClientSection(rpSection.getConfigurationSection(data.getClient()), data);
                } else {
                    ConfigurationSection search = searchInClientSection(rpSection, data);
                    if (search != null) {
                        return search;
                    } else {
                        return searchInClientSection(rpSection.getConfigurationSection(rpSection.getKeys(false).iterator().next()), data);
                    }
                }
            }
        }
        return null;
    }

    private static ConfigurationSection searchInClientSection(ConfigurationSection clientSection, RpPlayerData data) {
        if (clientSection != null) {
            if (clientSection.contains("url")) {
                return clientSection;
            } else {
                if (clientSection.contains(getResolutionKey(data.getResolution()))) {
                    return searchInResolutionSection(clientSection.getConfigurationSection(getResolutionKey(data.getResolution())), data);
                } else {
                    ConfigurationSection search = searchInResolutionSection(clientSection, data);
                    if (search != null) {
                        return search;
                    } else {
                        return searchInResolutionSection(clientSection.getConfigurationSection(clientSection.getKeys(false).iterator().next()), data);
                    }
                }
            }
        }
        return null;
    }

    private static ConfigurationSection searchInResolutionSection(ConfigurationSection resolutionSection, RpPlayerData data) {
        if (resolutionSection != null) {
            if (resolutionSection.contains("url")) {
                return resolutionSection;
            } else {
                if (resolutionSection.contains(data.getVariant())) {
                    return searchInVariantSection(resolutionSection.getConfigurationSection(data.getVariant()), data);
                } else {
                    ConfigurationSection search = searchInVariantSection(resolutionSection, data);
                    if (search != null) {
                        return search;
                    } else {
                        return searchInVariantSection(resolutionSection.getConfigurationSection(resolutionSection.getKeys(false).iterator().next()), data);
                    }
                }
            }
        }
        return null;
    }

    private static ConfigurationSection searchInVariantSection(ConfigurationSection variantSection, RpPlayerData data) {
        if (variantSection != null) {
            if (variantSection.contains("url")) {
                return variantSection;
            } else {
                String versionResult = "0.1";
                int protocolResult = 0;
                String versionMin = "unknown";
                int protocolMin = Integer.MAX_VALUE;
                for(String version: variantSection.getKeys(false)) {
//Logger.getGlobal().info("Version: "+version);
                    Integer protocolVersion = protocolVersions.get(version);
                    if(protocolVersion==null) {
                        return null;
                    }
//Logger.getGlobal().info("Protocol: "+protocolVersion);
                    if(protocolResult < protocolVersion && protocolVersion <= data.getProtocolVersion()) {
                        versionResult = version;
                        protocolResult = protocolVersion;
                    }
                    if(protocolMin > protocolVersion) {
                        protocolMin = protocolVersion;
                        versionMin = version;
                    }
                }
                if(protocolResult > 0) {
                    return variantSection.getConfigurationSection(versionResult);
                } else {
                    return variantSection.getConfigurationSection(versionMin);
                }
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
//Logger.getGlobal().info("Sending to "+player.getName()+"("+getPlayerData(player).getProtocolVersion()+") RP: "+url);
            player.setResourcePack(url, getSHA(rpName, player));
            savePlayerData(player);
            return true;
        }
        return false;
    }
    
    /*public static byte[] getSHAForUrl(String url) {
        for(String rpName: getRpConfig().getKeys(false)) {
            ConfigurationSection clientSection = getRpConfig().getConfigurationSection(rpName);
            for (String clientName : clientSection.getKeys(false)) {
                ConfigurationSection section = clientSection.getConfigurationSection(clientName);
                for (String key : section.getKeys(false)) {
                    ConfigurationSection pxSection = section.getConfigurationSection(key);
                    for (String varKey : pxSection.getKeys(false)) {
                        ConfigurationSection varSection = pxSection.getConfigurationSection(varKey);
                        if (varSection.getString("url").equals(url)) {
                            return stringToSHA(varSection.getString("sha"));
                        }
                    }
                }
            }
        }
        return new byte[20];
    }*/
    
    public static String getRpForUrl(String url) {
        for(String rpName: getRpConfig().getKeys(false)) {
//Logger.getGlobal().info("RP: "+rpName);
            ConfigurationSection clientSection = getRpConfig().getConfigurationSection(rpName);
            for (String clientName : clientSection.getKeys(false)) {
//Logger.getGlobal().info("Client: "+clientName);
                ConfigurationSection resolutionSection = clientSection.getConfigurationSection(clientName);
                for (String key : resolutionSection.getKeys(false)) {
//Logger.getGlobal().info("Resolution: "+key);
                    ConfigurationSection pxSection = resolutionSection.getConfigurationSection(key);
                    for (String varKey : pxSection.getKeys(false)) {
//Logger.getGlobal().info("Variant: "+varKey);
                        ConfigurationSection varSection = pxSection.getConfigurationSection(varKey);
                        if (varSection.contains("url")) {
                            if(varSection.getString("url").equals(url)) {
                                return rpName;
                            }
                        } else {
                            for(String versionKey: varSection.getKeys(false)) {
                                ConfigurationSection versionSection = varSection.getConfigurationSection(versionKey);
                                if (versionSection.getString("url").equals(url)) {
                                    return rpName;
                                }
                            }
                        }
                    }
                }
            }
        }
        return "";
    }
    
    public static boolean searchRpKey(String key) {
        return getRpConfig().getKeys(true).stream().anyMatch((search) -> (search.endsWith(key)));
    }
    
    public static ConfigurationSection getRpConfig() {
        return ArchitectPlugin.getPluginInstance().getConfig().getConfigurationSection("ServerResourcePacks");
    }

    public static boolean refreshSHA(CommandSender cs, String rp, boolean allVersions) {
        ConfigurationSection config = getRpConfig().getConfigurationSection(rp);
        if(config!=null) {
            for(String clientKey: config.getKeys(false)) {
//Logger.getGlobal().info("ClientKey: "+clientKey);
                ConfigurationSection clientSection = config.getConfigurationSection(clientKey);
                for(String resolutionKey: clientSection.getKeys(false)) {
//Logger.getGlobal().info("ResolutionKey: "+resolutionKey);
                    ConfigurationSection resolutionSection = clientSection.getConfigurationSection(resolutionKey);
                    for (String variantKey : resolutionSection.getKeys(false)) {
//Logger.getGlobal().info("VariantKey: "+variantKey);
                        try {
                            ConfigurationSection variantSection = resolutionSection.getConfigurationSection(variantKey);
                            List<ConfigurationSection> sections = new LinkedList<>();
                            if (variantSection.contains("url")) {
//Logger.getGlobal().info("DirectURL: "+variantSection.getString("url"));
                                sections.add(variantSection);
                            } else {
                                if (allVersions) {
//Logger.getGlobal().info("All versions");
                                    sections.addAll(variantSection.getKeys(false).stream()
                                            .map(variantSection::getConfigurationSection).collect(Collectors.toSet()));
                                } else {
//Logger.getGlobal().info("Latest versions");
                                    sections.add(variantSection
                                            .getConfigurationSection(getLatestVersion(variantSection.getKeys(false))));
                                }
                            }
//for(ConfigurationSection section: sections) Logger.getGlobal().info("URL: "+section.getString("url"));
                            for (ConfigurationSection section : sections) {
                                URL url = new URL(section.getString("url"));
                                InputStream fis = url.openStream();
                                MessageDigest sha1 = MessageDigest.getInstance("SHA1");

                                byte[] data = new byte[1024];
                                int read = 0;
                                long time = System.currentTimeMillis();
                                while ((read = fis.read(data)) != -1) {
                                    sha1.update(data, 0, read);
                                    if (System.currentTimeMillis() - time > 5000) {
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
                                section.set("sha", hashString);
                                ArchitectPlugin.getPluginInstance().saveConfig();
                            }
                        } catch(IOException | NoSuchAlgorithmException ex){
                            Logger.getLogger(RpManager.class.getName()).log(Level.SEVERE, null, ex);
                            return false;
                        }
                    }
                }
            }
            return true;
        }
        return false;
    }

    private static String getLatestVersion(Collection<String> versions) {
        int latestProtocol = 0;
        String latestVersion = "";
        for(String version : versions) {
//protocolVersions.forEach((key, protocol) -> Logger.getGlobal().info(key+" "+protocol));
//Logger.getGlobal().info("getLatestVersion: version "+ version+ " protcolVersions: "+protocolVersions.size());
            int protocolVersion = protocolVersions.get(version);
            if(protocolVersion > latestProtocol) {
                latestProtocol = protocolVersion;
                latestVersion = version;
            }
        }
//Logger.getGlobal().info("LatestVersion: "+latestVersion);
        return latestVersion;
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

    public static void addSodiumClient(Player player, String sodiumVersion) {
        sodiumClients.put(player.getUniqueId(), sodiumVersion);
    }

    public static void removeSodiumClient(Player player) {
        sodiumClients.remove(player.getUniqueId());
    }

    public static boolean isSodiumClient(Player player) {
        return sodiumClients.containsKey(player.getUniqueId());
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


}
