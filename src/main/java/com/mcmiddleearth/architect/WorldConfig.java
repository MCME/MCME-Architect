/*
 * Copyright (C) 2016 MCME
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
package com.mcmiddleearth.architect;

import com.mcmiddleearth.pluginutil.NumericUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.ShulkerBox;
import org.bukkit.block.data.BlockData;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author Eriol_Eandur
 */
public class WorldConfig {

    private static final File worldConfigDir = new File(ArchitectPlugin.getPluginInstance().getDataFolder()
            + File.separator + "WorldConfig");
    private static final String cfgExtension = "yml";
    private static final String defaultWorldConfigName = "defaultWorldConfig";

    private static final File defaultConfigFile = new File(worldConfigDir + File.separator
            + defaultWorldConfigName + "." + cfgExtension);

    private static final String NO_PHYSICS_LIST = "noPhysicsList";
    private static final String INVENTORY_ACCESS = "inventoryAccess";
    private static final String ALLOWED_ENCHANTS = "allowedEnchantments";
    private static final String NO_INTERACTION = "noInteraction";
    private static final String NO_CONNECTION = "noConnection";
    private static final String DOUBLE_SLAB_REPLACEMENTS = "doubleSlabReplacements";
    private static final String DISABLED_BLOCK_STATES = "disabledBlockStates";
    private static final String VIEW_DISTANCE = "viewDistance";
    private static final String ARMOR_STAND_CONFIG = "armorStand";
    private static final String ARMOR_STAND_MIN_SCALE = "minScale";
    private static final String ARMOR_STAND_MAX_SCALE = "maxScale";

    private static final int defaultItemBlockBaseLimit = 5;
    
    private final String worldName;

    private final YamlConfiguration worldConfig;

    private YamlConfiguration defaultConfig;

    private final List<BlockData> noInteraction = new ArrayList<>();

    private final List<BlockData> noConnection = new ArrayList<>();

    private final List<BlockData> disabledBlockStates = new ArrayList<>();
    
    private final Map<String,Integer> allowedEnchants = new HashMap<>();

    private int viewDistance = 255;
    private double armorStandMinScale = 0.0625;
    private double armorStandMaxScale = 16;

    private final Map<String,Map<BlockData,BlockData>> doubleSlabReplacements = new HashMap<>();
    
    static {
        if (!worldConfigDir.exists()) {
            if(!worldConfigDir.mkdirs()) {
                Logger.getLogger(WorldConfig.class.getName()).log(Level.SEVERE, "Can't create world config folder.");
            }
        }
    }

    public WorldConfig(String worldName, YamlConfiguration defaultConfig) {
        this.defaultConfig = defaultConfig;
        this.worldName = worldName;
        if (!worldConfigDir.exists()) {
            if(!worldConfigDir.mkdirs()) {
                Logger.getLogger(WorldConfig.class.getName()).log(Level.SEVERE, "Can't create world config folder.");
            }
        }
        if (!defaultConfigFile.exists()) {
            Logger.getGlobal().info("*****************************************************************");
            Logger.getGlobal().info("missing default world config file --- creating defaultWorldConfig");
            this.defaultConfig = createDefaultConfig();
            saveDefaultConfig();
        }
        File configFile = getConfigFile();
        if (!configFile.exists()) {
            worldConfig = new YamlConfiguration();
            worldConfig.set("WorldConfiguration", "Values in this file will override values in defaultWorldConfig.yml file.");
            try {
                worldConfig.save(configFile);
            } catch (IOException ex) {
                Logger.getLogger(WorldConfig.class.getName()).log(Level.SEVERE, null, ex);
            }
        } else {
            worldConfig = YamlConfiguration.loadConfiguration(configFile);
        }
        convertNoPhysicsList();
        loadNoInteraction();
        loadNoConnection();
        loadViewDistance();
        loadArmorStandConfig();
        loadAllowedEnchants();
        loadDoubleSlabReplacements();
        loadDisabledBlockStates();
    }

    private File getConfigFile() {
        return new File(worldConfigDir + File.separator + worldName + "." + cfgExtension);
    }

    private YamlConfiguration createDefaultConfig() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("DefaultConfiguration", "Values in this file will be used for all worlds if not overriden in <worldName>.yml configuration file.");
        for (Modules modul : Modules.values()) {
            config.set(modul.getModuleKey(), true);
        }
        config.set(NO_PHYSICS_LIST, new ArrayList<>());
        config.set(VIEW_DISTANCE,255);
        ConfigurationSection armorConfig = config.createSection(ARMOR_STAND_CONFIG);
        armorConfig.set(ARMOR_STAND_MAX_SCALE, armorStandMaxScale);
        armorConfig.set(ARMOR_STAND_MIN_SCALE, armorStandMinScale);
        createInventoryAccess(config);
        createNoInteraction(config);
        createNoConnection(config);
        createAllowedEnchants(config);
        createDoubleSlabReplacements(config);
        createDisabledBlockStates(config);
        return config;
    }

    private void saveDefaultConfig() {
        try {
            defaultConfig.save(defaultConfigFile);
        } catch (IOException ex) {
            Logger.getLogger(WorldConfig.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    private void saveWorldConfig() {
        try {
            worldConfig.save(getConfigFile());
        } catch (IOException ex) {
            Logger.getLogger(WorldConfig.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    public boolean isModuleEnabled(Modules module, boolean defaultValue) {
        if (worldConfig.contains(module.getModuleKey())) {
            return worldConfig.getBoolean(module.getModuleKey());
        }
        if (defaultConfig.contains(module.getModuleKey())) {
            return defaultConfig.getBoolean(module.getModuleKey());
        } else {
            defaultConfig.set(module.getModuleKey(), defaultValue);
            saveDefaultConfig();
        }
        return defaultValue;
    }

    public void setModuleEnabled(Modules module, boolean enable) {
        worldConfig.set(module.getModuleKey(), enable);
        saveWorldConfig();
    }

    private void convertNoPhysicsList() {
        List<String> npList = worldConfig.getStringList(NO_PHYSICS_LIST);
        List<String> convertedList = new ArrayList<>();
        boolean convert = false;
        for (String input : npList) {
            if (NumericUtil.isInt(input)) {
                /*Material mat = LegacyMaterialUtil.getMaterial(NumericUtil.getInt(input));
                if (mat != null) {
                    convertedList.add(mat.getKey().toString());
                    convert = true;
                }*/
                convert = true;
            }
        }
        if (convert) {
            worldConfig.set(NO_PHYSICS_LIST, convertedList);
            saveWorldConfig();
        }
    }

    public List<String> getNoPhysicsListAsStrings() {
        if (worldConfig.contains(NO_PHYSICS_LIST)) {
            return worldConfig.getStringList(NO_PHYSICS_LIST);
        } else if (defaultConfig.contains(NO_PHYSICS_LIST)) {
            return defaultConfig.getStringList(NO_PHYSICS_LIST);
        } else {
            //npList = new ArrayList<>();
            return new ArrayList<>();
        }
    }

    public boolean addToNpList(String blockId) {
        List<String> npList = getWorldNoPhysicsList();
        if (npList.contains(blockId)) {
            return false;
        } else {
            npList.add(blockId);
            worldConfig.set(NO_PHYSICS_LIST, npList);
            saveWorldConfig();
            checkDeleteWorldNPConfig();
            return true;
        }
    }

    public boolean removeFromNpList(String blockId) {
        List<String> npList = getWorldNoPhysicsList();
        if (!npList.contains(blockId)) {
            return false;
        } else {
            npList.remove(blockId);
            worldConfig.set(NO_PHYSICS_LIST, npList);
            saveWorldConfig();
            checkDeleteWorldNPConfig();
            return true;
        }
    }

    private List<String> getWorldNoPhysicsList() {
        if (worldConfig.contains(NO_PHYSICS_LIST)) {
            return worldConfig.getStringList(NO_PHYSICS_LIST);
        } else {
            return new ArrayList<>();
        }
    }

    public int getItemBlockBaseLimit() {
        return worldConfig.getInt("itemBlock.baseLimit",defaultItemBlockBaseLimit);
    }
    
    public void setItemBlockBaseLimit(int limit) {
        worldConfig.set("itemBlock.baseLimit",limit);
        saveWorldConfig();
    }
    
    private void checkDeleteWorldNPConfig() {
        Set<String> worldNP = new HashSet<>(worldConfig.getStringList(NO_PHYSICS_LIST));
        Set<String> defaultNP = new HashSet<>(defaultConfig.getStringList(NO_PHYSICS_LIST));
        for (String search : worldNP) {
            if (!defaultNP.contains(search)) {
                return;
            }
        }
        for (String search : defaultNP) {
            if (!worldNP.contains(search)) {
                return;
            }
        }
        worldConfig.set(NO_PHYSICS_LIST, null);
        saveWorldConfig();
    }

    public InventoryAccess getInventoryAccess(Inventory inventory) {
        boolean defaultValue = false;
        ConfigurationSection section = worldConfig.getConfigurationSection(INVENTORY_ACCESS);
        if (section == null) {
            defaultValue = true;
            section = defaultConfig.getConfigurationSection(INVENTORY_ACCESS);
            if (section == null) {
                return InventoryAccess.TRUE;
            }
        }
        if (inventory.getType().name().equals("SHULKER_BOX")) {
            InventoryAccess result = getShulkerAccess(section, inventory);
            if (result == null && !defaultValue) {
                section = defaultConfig.getConfigurationSection(INVENTORY_ACCESS);
                if (section == null) {
                    return InventoryAccess.TRUE;
                }
                result = getShulkerAccess(section, inventory);
            }
            return result == null ? InventoryAccess.TRUE : result;
        }
        String configValue = section.getString(inventory.getType().name());
        if (configValue == null) {
            section = defaultConfig.getConfigurationSection(INVENTORY_ACCESS);
            if (section == null) {
                return InventoryAccess.TRUE;
            }
            configValue = section.getString(inventory.getType().name());
            if (configValue == null) {
                return createNewInventoryConfigEntry(section, inventory.getType().name());
            }
        }
        return InventoryAccess.valueOf(configValue);
    }

    private InventoryAccess getShulkerAccess(ConfigurationSection section, Inventory inventory) {
        String key = inventory.getType().name();
        String configValue;
        if (section.isConfigurationSection(key)) {
            ConfigurationSection shulkerConfig
                    = section.getConfigurationSection(key);
            assert shulkerConfig != null;
            configValue = null;//shulkerConfig.getString("id" + ((ShulkerBox) Objects.requireNonNull(inventory.getHolder()))
                    //.getType().getId());
            if (configValue == null) {
                configValue = shulkerConfig.getString("default");
            }
        } else {
            configValue = section.getString(key);
        }
        return configValue == null ? null : InventoryAccess.valueOf(configValue);
    }

    private InventoryAccess createNewInventoryConfigEntry(ConfigurationSection section, String key) {
        section.set(key, InventoryAccess.TRUE.name());
        saveDefaultConfig();
        return InventoryAccess.TRUE;
    }

    private void createInventoryAccess(ConfigurationSection config) {
        ConfigurationSection section = config.createSection(INVENTORY_ACCESS);
        section.set(InventoryType.ANVIL.name(), InventoryAccess.TRUE.name());
        section.set(InventoryType.BEACON.name(), InventoryAccess.EXCEPTION.name());
        section.set(InventoryType.BREWING.name(), InventoryAccess.BUILDER.name());
        section.set(InventoryType.CHEST.name(), InventoryAccess.TRUE.name());
        section.set(InventoryType.CRAFTING.name(), InventoryAccess.TRUE.name());
        section.set(InventoryType.CREATIVE.name(), InventoryAccess.TRUE.name());
        section.set(InventoryType.DISPENSER.name(), InventoryAccess.EXCEPTION.name());
        section.set(InventoryType.DROPPER.name(), InventoryAccess.EXCEPTION.name());
        section.set(InventoryType.ENCHANTING.name(), InventoryAccess.EXCEPTION.name());
        section.set(InventoryType.ENDER_CHEST.name(), InventoryAccess.TRUE.name());
        section.set(InventoryType.FURNACE.name(), InventoryAccess.BUILDER.name());
        section.set(InventoryType.HOPPER.name(), InventoryAccess.EXCEPTION.name());
        section.set(InventoryType.PLAYER.name(), InventoryAccess.TRUE.name());
        section.set(InventoryType.WORKBENCH.name(), InventoryAccess.TRUE.name());
        section.set(InventoryType.MERCHANT.name(), InventoryAccess.TRUE.name());
        ConfigurationSection shulker = section.createSection("SHULKER_BOX");
        shulker.set("default", InventoryAccess.TRUE.name());
    }

    public boolean getNoInteraction(BlockData data) {
        return noInteraction.stream().anyMatch(data::matches);
    }
    
    private void loadNoInteraction() {
        List<String> data;
        if(worldConfig.contains(NO_INTERACTION)) {
            data = worldConfig.getStringList(NO_INTERACTION);
        } else {
            if (!defaultConfig.contains(NO_INTERACTION)) {
                createNoInteraction(defaultConfig);
                saveDefaultConfig();
            }
            data = defaultConfig.getStringList(NO_INTERACTION);
        }
        noInteraction.clear();
        for(String entry: data) {
            try {
                BlockData blockData = Bukkit.createBlockData(entry);
                noInteraction.add(blockData);
            } catch(IllegalArgumentException ex) {
                Logger.getLogger(ArchitectPlugin.class.getName()).log(Level.WARNING,"Illegal block data for no interaction block.");
            }
        }
    }
    
    private void createNoInteraction(ConfigurationSection config) {
        List<String> list = new ArrayList<>();
        list.add("minecraft:acacia_fence_gate");
        list.add("minecraft:birch_fence_gate[powered=true]");
        list.add("minecraft:jungle_fence_gate");
        list.add("minecraft:dark_oak_fence_gate");
        list.add("minecraft:spruce_fence_gate");
        list.add("minecraft:acacia_door[powered=false]");
        list.add("minecraft:jungle_door[powered=false]");
        list.add("minecraft:dark_oak_door[powered=false]");
        list.add("minecraft:spruce_door[powered=false]");
        list.add("minecraft:repeater");
        list.add("minecraft:comparator");
        config.set(NO_INTERACTION, list);
    }

    public boolean getNoConnection(BlockData data) {
        return noConnection.stream().anyMatch(data::matches);
    }

    private void loadNoConnection() {
        List<String> data;
        if(worldConfig.contains(NO_CONNECTION)) {
            data = worldConfig.getStringList(NO_CONNECTION);
        } else {
            if (!defaultConfig.contains(NO_CONNECTION)) {
                createNoConnection(defaultConfig);
                saveDefaultConfig();
            }
            data = defaultConfig.getStringList(NO_CONNECTION);
        }
        noConnection.clear();
        for(String entry: data) {
            try {
                BlockData blockData = Bukkit.createBlockData(entry);
                noConnection.add(blockData);
            } catch(IllegalArgumentException ex) {
                Logger.getLogger(ArchitectPlugin.class.getName()).log(Level.WARNING,"Illegal block data for no connection block.");
            }
        }
    }

    private void createNoConnection(ConfigurationSection config) {
        List<String> list = new ArrayList<>();
        list.add("minecraft:red_sandstone_wall");
        list.add("minecraft:andesite_wall");
        list.add("minecraft:prismarine_wall");
        config.set(NO_CONNECTION, list);
    }

    public boolean isAllowedEnchantment(String enchantment, int level) {
//Logger.getGlobal().info("Ench Test: "+enchantment+" "+level);
        Integer allowedLevel = allowedEnchants.get(enchantment);
        return allowedLevel != null && level<=allowedLevel;
    }
    
    private void loadAllowedEnchants() {
        ConfigurationSection data;
        if(worldConfig.contains(ALLOWED_ENCHANTS)) {
            data = worldConfig.getConfigurationSection(ALLOWED_ENCHANTS);
        } else {
            if (!defaultConfig.contains(ALLOWED_ENCHANTS)) {
                createAllowedEnchants(defaultConfig);
                saveDefaultConfig();
            }
            data = defaultConfig.getConfigurationSection(ALLOWED_ENCHANTS);
        }
        allowedEnchants.clear();
        assert data != null;
        for(String key: data.getKeys(false)) {
            allowedEnchants.put("minecraft:"+key.toLowerCase(),data.getInt(key));
        }
    }

    private void createAllowedEnchants(ConfigurationSection config) {
        ConfigurationSection section = config.createSection(ALLOWED_ENCHANTS);
        section.set("Durability", 1);
    }
    
    public BlockData getDoubleSlabReplacement(BlockData replace, String rp) {
        Map<BlockData,BlockData> replacements = doubleSlabReplacements.get(rp.toLowerCase());
        if(replacements!=null) {
            return replacements.get(replace);
        }
        return null;
    }
    
    private void loadDoubleSlabReplacements() {
        ConfigurationSection data;
        if(worldConfig.contains(DOUBLE_SLAB_REPLACEMENTS)) {
            data = worldConfig.getConfigurationSection(DOUBLE_SLAB_REPLACEMENTS);
        } else {
            if (!defaultConfig.contains(DOUBLE_SLAB_REPLACEMENTS)) {
                createDoubleSlabReplacements(defaultConfig);
                saveDefaultConfig();
            }
            data = defaultConfig.getConfigurationSection(DOUBLE_SLAB_REPLACEMENTS);
        }
        doubleSlabReplacements.clear();
        assert data != null;
        for(String key: data.getKeys(false)) {
            Map<BlockData,BlockData> replacements = new HashMap<>();
            ConfigurationSection section = data.getConfigurationSection(key);
            assert section != null;
            for(String replacementKey: section.getKeys(false)) {
                ConfigurationSection replacement = section.getConfigurationSection(replacementKey);
                assert replacement != null;
                BlockData from = Bukkit.createBlockData(Objects.requireNonNull(replacement.getString("from")));
                BlockData to = Bukkit.createBlockData(Objects.requireNonNull(replacement.getString("to")));
                replacements.put(from, to);
            }
            doubleSlabReplacements.put(key,replacements);
        }
    }
    
    private void createDoubleSlabReplacements(ConfigurationSection config) {
        ConfigurationSection section = config.createSection(DOUBLE_SLAB_REPLACEMENTS);
        ConfigurationSection rp = section.createSection("gondor");
        ConfigurationSection eg = rp.createSection("Example");
        eg.set("from", Bukkit.createBlockData(Material.ACACIA_SLAB).getAsString());
        eg.set("to", Bukkit.createBlockData(Material.ACACIA_PLANKS).getAsString());
    }
    
    public boolean isAllowedBlock(BlockData data) {
        for(BlockData search : disabledBlockStates) {
            if(data.matches(search)) {
                return false;
            }
        }
        return true;
    }

    private void loadDisabledBlockStates() {
        List<String> data;
        if(worldConfig.contains(DISABLED_BLOCK_STATES)) {
            data = worldConfig.getStringList(DISABLED_BLOCK_STATES);
        } else {
            if (!defaultConfig.contains(DISABLED_BLOCK_STATES)) {
                createDisabledBlockStates(defaultConfig);
                saveDefaultConfig();
            }
            data = defaultConfig.getStringList(DISABLED_BLOCK_STATES);
        }
        disabledBlockStates.clear();
//Logger.getLogger(ArchitectPlugin.class.getName()).log(Level.INFO,"interactionDAta: "+data.size());
        for(String entry: data) {
            try {
                BlockData blockData = Bukkit.createBlockData(entry);
                disabledBlockStates.add(blockData);
            } catch(IllegalArgumentException ex) {
                Logger.getLogger(ArchitectPlugin.class.getName()).log(Level.WARNING,"Illegal block data for disabled block states.");
            }
        }
//Logger.getLogger(ArchitectPlugin.class.getName()).log(Level.INFO,blockData.getAsString());
    }
    
    private void createDisabledBlockStates(ConfigurationSection config) {
        List<String> list = new ArrayList<>();
        list.add("minecraft:blue_stained_glass_pane[east=false,north=false,south=false,west=false]");
        config.set(DISABLED_BLOCK_STATES, list);
    }

    public int getViewDistance() {
        return viewDistance;
    }

    private void loadViewDistance() {
        if(worldConfig.contains(VIEW_DISTANCE)) {
            viewDistance = worldConfig.getInt(VIEW_DISTANCE);
        } else {
            if (!defaultConfig.contains(VIEW_DISTANCE)) {
                defaultConfig.set(VIEW_DISTANCE, 255);
                saveDefaultConfig();
            }
            viewDistance = defaultConfig.getInt(VIEW_DISTANCE);
        }
    }

    public double getArmorStandMinScale() {
        return armorStandMinScale;
    }

    public double getArmorStandMaxScale() {
        return armorStandMaxScale;
    }

    public void loadArmorStandConfig() {
        if(worldConfig.contains(ARMOR_STAND_CONFIG)) {
            ConfigurationSection armorStandConfig = worldConfig.getConfigurationSection(ARMOR_STAND_CONFIG);
            armorStandMaxScale = armorStandConfig.getDouble(ARMOR_STAND_MAX_SCALE);
            armorStandMinScale = armorStandConfig.getDouble(ARMOR_STAND_MIN_SCALE);
        } else {
            if (!defaultConfig.contains(ARMOR_STAND_CONFIG)) {
                ConfigurationSection armorStandConfig = defaultConfig.createSection(ARMOR_STAND_CONFIG);
                armorStandConfig.set(ARMOR_STAND_MIN_SCALE, armorStandMinScale);
                armorStandConfig.set(ARMOR_STAND_MAX_SCALE, armorStandMaxScale);
                saveDefaultConfig();
            }
            ConfigurationSection armorStandConfig = defaultConfig.getConfigurationSection(ARMOR_STAND_CONFIG);
            armorStandMaxScale = armorStandConfig.getDouble(ARMOR_STAND_MAX_SCALE);
            armorStandMinScale = armorStandConfig.getDouble(ARMOR_STAND_MIN_SCALE);
        }
    }

    public static File getWorldConfigDir() {
        return worldConfigDir;
    }

    public static String getCfgExtension() {
        return cfgExtension;
    }

    public static String getDefaultWorldConfigName() {
        return defaultWorldConfigName;
    }

    public YamlConfiguration getWorldConfig() {
        return worldConfig;
    }
}
