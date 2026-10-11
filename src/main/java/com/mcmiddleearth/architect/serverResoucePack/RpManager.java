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

import com.google.gson.Gson;
import com.mcmiddleearth.architect.ArchitectPlugin;
import com.mcmiddleearth.architect.ConfigFile;
import com.mcmiddleearth.architect.Log;
import com.mcmiddleearth.architect.PluginData;
import com.mcmiddleearth.architect.mapLayers.MapLayers;
import com.mcmiddleearth.architect.mapLayers.RpRegionLayer;
import com.mcmiddleearth.architect.serverResoucePack.RegionEditConversation.RegionEditConversationFactory;
import com.mcmiddleearth.util.DevUtil;
import com.mcmiddleearth.util.ResourceUtil;
import com.viaversion.viaversion.api.Via;
import org.bukkit.Bukkit;
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
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URLConnection;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.function.Consumer;

/**
 *
 * @author Eriol_Eandur
 */
public class RpManager {
    
    private static final File playerFile = new File(ArchitectPlugin.getPluginInstance().getDataFolder(),"/playerRpData.dat");
    private static final File regionFolder = new File(ArchitectPlugin.getPluginInstance().getDataFolder(),"regions");

    private static final File versionFile = new File(ArchitectPlugin.getPluginInstance().getDataFolder(), "protocolVersions.yml");
    // in the order of the file, so versions with the same protocol keep that order
    private static final Map<String, Integer> protocolVersions = new LinkedHashMap<>();

    private static final String rpDatabaseConfig = "rpSettingsDatabase";

    private static final Map<String, RpRegion> regions = new HashMap<>();

    private static final Map<UUID,RpPlayerData> playerRpData = new java.util.concurrent.ConcurrentHashMap<>();

    // The players whose settings are being read from the RP database, each with the load that reads them.
    // Nothing makes data for them meanwhile: defaults made now would choose their pack, and be saved over
    // their row.
    private static final Map<UUID, Object> pendingLoads = new java.util.concurrent.ConcurrentHashMap<>();

    private static final Map<UUID, String> sodiumClients = new java.util.concurrent.ConcurrentHashMap<>();

    // The slots without a valid SHA-1 that were logged, by path and url: each is logged once, not for every player.
    private static final Set<String> unhashedSlots = java.util.concurrent.ConcurrentHashMap.newKeySet();

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
            Log.error("Failed to load protocol versions file " + versionFile.getName(), e);
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
                Log.error("Failed to load RP region file " + file.getName(), ex);
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
    
    /** The regions changed: the web map's RP-region layer is drawn again. */
    public static void updateDynmapRegions() {
        MapLayers.changed(RpRegionLayer.ID);
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
            if(isLoadPending(player)) {
                // The player's settings are still being read: these defaults are not kept, so nothing sends,
                // changes or saves them in place of the player's own.
                return data;
            }
            RpPlayerData stored = playerRpData.putIfAbsent(player.getUniqueId(), data);
            if(stored != null) {
                data = stored; // the load has stored the player's settings meanwhile
            }
        }
        return data;
    }
    
    public static boolean hasPlayerDataLoaded(Player player) {
        return playerRpData.containsKey(player.getUniqueId());
    }

    public static void removePlayerData(Player player) {
        pendingLoads.remove(player.getUniqueId());
        playerRpData.remove(player.getUniqueId());
    }

    /** Whether the player's settings are still being read from the RP database. */
    public static boolean isLoadPending(Player player) {
        return pendingLoads.containsKey(player.getUniqueId());
    }

    /**
     * Stops waiting for the player's settings, as the join check does when its time is up: from then on the
     * defaults are made for the player when needed. A load that ends later still stores the player's settings.
     */
    public static void stopWaitingForLoad(Player player) {
        pendingLoads.remove(player.getUniqueId());
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
    
    /**
     * The hash to send with the pack of rp to this player (null: default settings): the 20 bytes of the slot's SHA-1,
     * or null when the slot has no valid one. Paper sends a null hash as an empty one, and the client then loads the
     * pack without checking it; an all-zero hash, as before, made every client reject the pack.
     */
    public static byte[] getSHA(String rp, Player player) {
        ConfigurationSection section = getConfigSection(rp, player);
        if(section==null) {
            return null;
        }
        String sha = section.getString("sha");
        byte[] result = stringToSHA(sha);
        if(result==null && unhashedSlots.add(section.getCurrentPath() + " " + section.getString("url"))) {
            Log.warn("Resource pack " + describeSlot(section) + " has no valid SHA-1 in config.yml (sha: " + sha
                    + "), so its pack is sent without a hash, which clients do not check. /rp calcsha " + rp
                    + " all sets it.");
        }
        return result;
    }

    // "Mordor, variant vanilla.16px.light, version 26_2" for the slot ServerResourcePacks.Mordor.vanilla.16px.light.26_2
    static String describeSlot(ConfigurationSection slot) {
        String path = slot.getCurrentPath();
        String[] parts = path.split("[.]");
        if(parts.length == 6) {
            return parts[1] + ", variant " + parts[2] + "." + parts[3] + "." + parts[4] + ", version " + parts[5];
        }
        return path.substring(path.indexOf('.') + 1);
    }
    
    // The 20 bytes of a SHA-1 written as 40 hex digits; null when there is none, or it is no SHA-1.
    private static byte[] stringToSHA(String sha) {
        if(sha==null || !sha.trim().matches("[0-9a-fA-F]{40}")) {
            return null;
        }
        byte[] result = new BigInteger(sha.trim(),16).toByteArray();
        if(result.length>20) {
            result = Arrays.copyOfRange(result,1,21);
        } else if(result.length<20) {
            byte[] padded = new byte[20];
            System.arraycopy(result, 0, padded, 20 - result.length, result.length);
            result = padded;
        }
        return result;
    }
    
    /**
     * Returns the client's real protocol version. Uses ViaVersion when it is installed
     * (accurate for version-translated clients) and falls back to the Bukkit/Paper protocol
     * version otherwise. Guarded so the plugin does not hard-depend on ViaVersion (which is
     * only a softdepend); the {@code Via} reference is resolved lazily and never reached when
     * ViaVersion is absent.
     */
    public static int getClientProtocolVersion(Player player) {
        if(ArchitectPlugin.getPluginInstance().getServer().getPluginManager().getPlugin("ViaVersion") != null) {
            return Via.getAPI().getPlayerProtocolVersion(player.getUniqueId()).getVersion();
        }
        return player.getProtocolVersion();
    }

    private static ConfigurationSection getConfigSection(String rp, Player player) {
        RpPlayerData data;
        if (player != null) {
            data = getPlayerData(player);
            if(data.getProtocolVersion()==0) {
                data.setProtocolVersion(getClientProtocolVersion(player));
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
                    Integer protocolVersion = protocolVersions.get(version);
                    if(protocolVersion==null) {
                        // A slot under a key protocolVersions.yml lacks, such as 26_2release, matches no client: it is
                        // passed over, as in getLatestVersion, so the variant's other slots still serve.
                        continue;
                    }
                    ArchitectPlugin.getPluginInstance().getDevUtil().log(2,"Protocol: "+protocolVersion);
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

    /**
     * The Minecraft versions a pack can have a slot for, newest first: the keys of protocolVersions.yml, such as
     * 26_2 and 1_21_4. A slot under any other key matches no client's version.
     */
    public static List<String> getMcVersions() {
        return protocolVersions.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .map(Map.Entry::getKey)
                .toList();
    }

    public static boolean isMcVersion(String versionKey) {
        return protocolVersions.containsKey(versionKey);
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
        if(isLoadPending(player)) {
            return false; // a later run picks the player up, with their own settings
        }
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
        if(isLoadPending(player)) {
            return false; // no pack is chosen from defaults while the player's own settings are on their way
        }
        String url = getRpUrl(rpName, player);
        RpPlayerData data = getPlayerData(player);
        if(url!=null && data!=null && !url.equals("") && (force || !url.equals(data.getCurrentRpUrl()))) {
            data.setCurrentRpUrl(url);
            player.setResourcePack(url, getSHA(rpName, player));
            // Remember what the player's pack state was before this send, then mark the pack as
            // offered. Bukkit reports nothing until the client replies, so without this the
            // window between sending and the reply is indistinguishable from a refusal.
            data.setLastRpStatus(data.getCurrentRpStatus());
            data.setCurrentRpStatus(RpPlayerStatus.SENT);
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
            ConfigurationSection clientSection = getRpConfig().getConfigurationSection(rpName);
            for (String clientName : clientSection.getKeys(false)) {
                ConfigurationSection resolutionSection = clientSection.getConfigurationSection(clientName);
                for (String key : resolutionSection.getKeys(false)) {
                    ConfigurationSection pxSection = resolutionSection.getConfigurationSection(key);
                    for (String varKey : pxSection.getKeys(false)) {
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
        return getRpConfig(ArchitectPlugin.getPluginInstance().getConfig());
    }

    // the server resource packs in this config, such as the one an update of config.yml is given
    static ConfigurationSection getRpConfig(ConfigurationSection config) {
        return config.getConfigurationSection("ServerResourcePacks");
    }

    /**
     * What a command did to the slots of a pack, each named by its path in the pack, such as vanilla.16px.light.26_2.
     * Each slot in done was set. Each slot in failed was left as it was, for the reason given, which names its url. A
     * result with error set comes from work that failed: the error is in the log.
     */
    public record SlotResult(List<String> done, Map<String, String> failed, boolean error) {
        static SlotResult ofError() {
            return new SlotResult(List.of(), Map.of(), true);
        }
    }

    /**
     * Hashes the packs of a pack's slots again, as /rp calcsha does: with allVersions every slot, else the newest slot
     * of each variant. The slots are read on the main thread, and their packs hashed off it in a fixed order, by
     * variant and then by version; a pack that cannot be downloaded does not stop the others. Then, on the main
     * thread, config.yml is read again, each slot gets the sha of its pack, if its url is still the one that was
     * hashed, and config.yml is saved once. The result reaches callback on the main thread, and the log ends with each
     * slot that failed, and its url.
     */
    public static void refreshSHA(CommandSender cs, String rp, boolean allVersions, Consumer<SlotResult> callback) {
        ConfigurationSection pack = rp.isEmpty() ? null : getRpConfig().getConfigurationSection(rp);
        if(pack == null) {
            Log.warn("No server resource pack in config.yml is named '" + rp + "'.");
            callback.accept(SlotResult.ofError());
            return;
        }
        // by the path of the slot, so in the order of variant, then version
        Map<String, String> urls = new TreeMap<>();
        Map<String, String> failed = new TreeMap<>();
        for(String variant : variantPaths(pack)) {
            ConfigurationSection variantSection = pack.getConfigurationSection(variant);
            Map<String, ConfigurationSection> slots = new TreeMap<>();
            if(variantSection.contains("url")) {
                slots.put(variant, variantSection);
            } else if(allVersions) {
                for(String version : variantSection.getKeys(false)) {
                    if(variantSection.isConfigurationSection(version)) {
                        slots.put(variant + "." + version, variantSection.getConfigurationSection(version));
                    }
                }
            } else {
                String latest = getLatestVersion(variantSection.getKeys(false));
                if(latest.isEmpty()) {
                    failed.put(variant, "it has no slot for a version of protocolVersions.yml");
                } else {
                    slots.put(variant + "." + latest, variantSection.getConfigurationSection(latest));
                }
            }
            slots.forEach((path, slot) -> {
                if(slot == null || slot.getString("url") == null) {
                    failed.put(path, "it has no url");
                } else {
                    urls.put(path, slot.getString("url"));
                }
            });
        }
        ArchitectPlugin plugin = ArchitectPlugin.getPluginInstance();
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            Map<String, String> shas = new TreeMap<>();
            try {
                for(Map.Entry<String, String> slot : urls.entrySet()) {
                    try {
                        shas.put(slot.getKey(), sha1(slot.getValue(), () -> Bukkit.getScheduler().runTask(plugin,
                                () -> PluginData.getMessageUtil().sendInfoMessage(cs, "calculating ..."))));
                    } catch(IOException | IllegalArgumentException ex) {
                        failed.put(slot.getKey(), slot.getValue() + ": " + describe(ex));
                    }
                }
            } catch(Exception ex) {
                // Nothing in the background may fail silently: the error is logged, and the admin told.
                Log.error("Failed to recalculate the SHA of server resource pack " + rp, ex);
                Bukkit.getScheduler().runTask(plugin, () -> callback.accept(SlotResult.ofError()));
                return;
            }
            Bukkit.getScheduler().runTask(plugin, () -> callback.accept(setShas(rp, urls, shas, failed)));
        });
    }

    /** The versions a pack has slots for, in any of its variants, as keys of config.yml (26_2), sorted. */
    public static List<String> getSlotVersions(String rp) {
        ConfigurationSection pack = rp.isEmpty() ? null : getRpConfig().getConfigurationSection(rp);
        Set<String> versions = new TreeSet<>();
        for(String variant : pack == null ? List.<String>of() : variantPaths(pack)) {
            ConfigurationSection variantSection = pack.getConfigurationSection(variant);
            for(String version : variantSection.getKeys(false)) {
                if(variantSection.isConfigurationSection(version)) {
                    versions.add(version);
                }
            }
        }
        return List.copyOf(versions);
    }

    /**
     * Removes the slot for a version from every variant of a pack, as /rp server remove does, in config.yml as it is
     * on disk now. The result's done is the slots removed, by their path in the pack; none when no variant has a slot
     * for that version any more.
     */
    public static SlotResult removeSlots(String rp, String versionKey) {
        List<String> removed = new ArrayList<>();
        boolean saved = ConfigFile.update(config -> {
            ConfigurationSection packs = getRpConfig(config);
            ConfigurationSection pack = packs == null ? null : packs.getConfigurationSection(rp);
            for(String variant : pack == null ? List.<String>of() : variantPaths(pack)) {
                ConfigurationSection variantSection = pack.getConfigurationSection(variant);
                if(variantSection.isSet(versionKey)) {
                    variantSection.set(versionKey, null);
                    removed.add(variant + "." + versionKey);
                }
            }
        });
        if(!saved) {
            return SlotResult.ofError(); // ConfigFile logged why
        }
        removed.forEach(slot -> Log.info("Server resource pack " + rp + ", slot " + slot + ", was removed."));
        return new SlotResult(removed, Map.of(), false);
    }

    // The paths of a pack's variants: vanilla.16px.light and so on.
    private static List<String> variantPaths(ConfigurationSection pack) {
        List<String> paths = new ArrayList<>();
        for(String client : pack.getKeys(false)) {
            ConfigurationSection clientSection = pack.getConfigurationSection(client);
            for(String resolution : clientSection == null ? Set.<String>of() : clientSection.getKeys(false)) {
                ConfigurationSection resolutionSection = clientSection.getConfigurationSection(resolution);
                for(String variant : resolutionSection == null ? Set.<String>of() : resolutionSection.getKeys(false)) {
                    if(resolutionSection.isConfigurationSection(variant)) {
                        paths.add(client + "." + resolution + "." + variant);
                    }
                }
            }
        }
        return paths;
    }

    // On the main thread, in config.yml as it is now on disk: each slot gets the sha of its pack, if its url is still
    // the one that was hashed, and config.yml is saved once.
    private static SlotResult setShas(String rp, Map<String, String> urls, Map<String, String> shas,
                                      Map<String, String> failed) {
        try {
            List<String> done = new ArrayList<>();
            if(!shas.isEmpty() && !ConfigFile.update(config -> {
                ConfigurationSection packs = getRpConfig(config);
                ConfigurationSection pack = packs == null ? null : packs.getConfigurationSection(rp);
                for(Map.Entry<String, String> sha : shas.entrySet()) {
                    ConfigurationSection slot = pack == null ? null : pack.getConfigurationSection(sha.getKey());
                    String url = urls.get(sha.getKey());
                    if(slot != null && url.equals(slot.getString("url"))) {
                        slot.set("sha", sha.getValue());
                        done.add(sha.getKey());
                    } else {
                        // as when /rp server set it meanwhile, here or on another server, with the sha of its new url
                        failed.put(sha.getKey(), url + ": the slot changed while its pack was hashed");
                    }
                }
            })) {
                return SlotResult.ofError(); // ConfigFile logged why
            }
            failed.forEach((slot, reason) -> Log.warn("The SHA of server resource pack " + rp + ", slot " + slot
                    + ", was not recalculated: " + reason));
            return new SlotResult(done, failed, false);
        } catch(RuntimeException ex) {
            Log.error("Failed to recalculate the SHA of server resource pack " + rp, ex);
            return SlotResult.ofError();
        }
    }

    // How long a download of a pack may wait to connect, and then for its next bytes: a server that stops answering
    // ends the download with an error, instead of holding it, and the admin waiting for its answer, forever.
    private static final int CONNECT_TIMEOUT_MILLIS = 30_000;
    private static final int READ_TIMEOUT_MILLIS = 60_000;

    /**
     * Downloads the pack at url, and returns its SHA-1 as 40 hex digits; progress runs every 5 seconds meanwhile. Call
     * it off the main thread. A download that does not get the whole file throws; an HTTP answer other than 200,
     * such as 404 for a release that is gone, as an HttpStatusException.
     */
    static String sha1(String url, Runnable progress) throws IOException {
        URLConnection connection = URI.create(url).toURL().openConnection();
        connection.setConnectTimeout(CONNECT_TIMEOUT_MILLIS);
        connection.setReadTimeout(READ_TIMEOUT_MILLIS);
        if (connection instanceof HttpURLConnection http && http.getResponseCode() != HttpURLConnection.HTTP_OK) {
            HttpStatusException status = new HttpStatusException(http.getResponseCode(), http.getResponseMessage());
            http.disconnect();
            throw status;
        }
        MessageDigest sha1;
        try {
            sha1 = MessageDigest.getInstance("SHA-1");
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("This Java has no SHA-1", ex); // every Java has it
        }
        try (InputStream in = connection.getInputStream()) {
            byte[] data = new byte[8192];
            long time = System.currentTimeMillis();
            for (int read = in.read(data); read != -1; read = in.read(data)) {
                sha1.update(data, 0, read);
                if (System.currentTimeMillis() - time > 5000) {
                    time = System.currentTimeMillis();
                    progress.run();
                }
            }
        }
        return HexFormat.of().formatHex(sha1.digest());
    }

    // A download that got an HTTP answer other than 200, such as 404 for a release that is gone.
    static final class HttpStatusException extends IOException {
        HttpStatusException(int code, String message) {
            super("HTTP " + code + (message == null ? "" : " " + message));
        }
    }

    // What went wrong with a download, for an admin: "HTTP 404 Not Found", or the kind of error and its message.
    static String describe(Exception ex) {
        if (ex instanceof HttpStatusException) {
            return ex.getMessage();
        }
        return ex.getClass().getSimpleName() + (ex.getMessage() == null ? "" : ": " + ex.getMessage());
    }

    private static String getLatestVersion(Collection<String> versions) {
        int latestProtocol = 0;
        String latestVersion = "";
        for(String version : versions) {
            Integer boxed = protocolVersions.get(version);
            if(boxed == null) continue;
            int protocolVersion = boxed;
            if(protocolVersion > latestProtocol) {
                latestProtocol = protocolVersion;
                latestVersion = version;
            }
        }
        return latestVersion;
    }
    
    public static String getResolutionKey(int px) {
        return px+"px";
    }

    public static void savePlayerData(Player player) {
        RpPlayerData data = playerRpData.get(player.getUniqueId());
        if(data != null) { // data that was never kept, such as defaults during a load, is not saved
            dbConnector.saveRpSettings(player, data);
        }
    }
    
    public static void loadPlayerData(UUID uuid) {
        if(!dbConnector.isConfigured()) {
            playerRpData.putIfAbsent(uuid, new RpPlayerData()); // no database: the defaults, at once
            return;
        }
        // The player is waiting for this load until it has ended, whatever it stored: the connector runs the
        // callback even when the load failed. A newer load of the same player is not ended by this one.
        Object load = new Object();
        pendingLoads.put(uuid, load);
        try {
            dbConnector.loadRpSettings(uuid, playerRpData, () -> pendingLoads.remove(uuid, load));
        } catch (RuntimeException ex) {
            pendingLoads.remove(uuid, load);
            throw ex;
        }
    }
    
    public static void saveRpRegion(RpRegion region) {
        try {
            YamlConfiguration config = new YamlConfiguration();
            config.set("rpRegion", region.saveToMap());
            config.save(new File(regionFolder,region.getName()+".reg"));
        } catch (IOException ex) {
            Log.error("Failed to save RP region '" + region.getName() + "' to " + regionFolder, ex);
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
                Log.error("Failed to import RP region from resource file " + file.getName(), ex);
            }
        }
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
