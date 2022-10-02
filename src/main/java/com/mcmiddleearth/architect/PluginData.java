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

import com.mcmiddleearth.pluginutil.FileUtil;
import com.mcmiddleearth.pluginutil.message.MessageUtil;
import com.mcmiddleearth.util.DevUtil;
import com.mcmiddleearth.util.TheGafferUtil;
import org.bukkit.Bukkit;
import org.bukkit.GameRule;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.io.File;
import java.io.IOException;
import java.util.*;

/**
 *
 * @author Eriol_Eandur
 */
public class PluginData {
    
    private static final Map<String,WorldConfig> worldConfigs = new HashMap<>();

    private static YamlConfiguration defaultWorldConfig = new YamlConfiguration();
    
    private static final String defaultKey = "-default";
                                       
    private static boolean overrideWeather = false;
    
    private static final MessageUtil messageUtil = new MessageUtil();
    
    private static final Set<UUID> afkPlayerList = new HashSet<>();
    
    private static int entityStandLimit = 1000;
    private static int entityLimitRadius = 80;

    private static final File switchStickDir = new File(ArchitectPlugin.getPluginInstance().getDataFolder() + File.separator + "SwitchStick");
    private static final File switckStickFile = new File(switchStickDir,"switchStickConfig.yml");
    private static final YamlConfiguration Stickconfig = YamlConfiguration.loadConfiguration(switckStickFile);;
    
    private final static String ENITIY_LIMIT_SECTION = "EntityLimit";

    static{
        if(!ArchitectPlugin.getPluginInstance().getDataFolder().exists()){
            ArchitectPlugin.getPluginInstance().getDataFolder().mkdirs();
        }

        if(!switchStickDir.exists()){
            switchStickDir.mkdirs();
            File switchStickConfig = new File(switchStickDir,"switchStickConfig.yml");
            try {
                switchStickConfig.createNewFile();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

    }
    
    public static boolean isModuleEnabled(World world, Modules modul) {
        WorldConfig config = getOrCreateWorldConfig(world.getName());
        DevUtil.log(40, "isEnabled? "+modul.getModuleKey());
        return config.isModuleEnabled(modul,true);
    }

    public static void setModuleEnabled(World world, Modules modul, boolean enable) {
        if(world==null) {
            worldConfigs.get(defaultKey).setModuleEnabled(modul,enable);
        } else {
            getOrCreateWorldConfig(world.getName()).setModuleEnabled(modul, enable);
        }
    }

    public static WorldConfig getOrCreateWorldConfig(String worldName) {
        WorldConfig config = worldConfigs.get(worldName);
        if(config == null) {
            config = new WorldConfig(worldName, defaultWorldConfig);
            worldConfigs.put(worldName, config);
        }
        return config;
    }
    
    public static void load(){
        ConfigurationSection entityConfig = ArchitectPlugin.getPluginInstance().getConfig()
                                                       .getConfigurationSection(ENITIY_LIMIT_SECTION);
        if(entityConfig==null) {
            entityConfig = ArchitectPlugin.getPluginInstance().getConfig().createSection(ENITIY_LIMIT_SECTION);
            entityConfig.set("number", 500);
            entityConfig.set("radius", 80);
            ArchitectPlugin.getPluginInstance().saveConfig();
        }
        entityStandLimit = entityConfig.getInt("number",500);
        entityLimitRadius = entityConfig.getInt("radius",80);
        worldConfigs.clear();
        WorldConfig config = new WorldConfig(WorldConfig.getDefaultWorldConfigName(), defaultWorldConfig);
        worldConfigs.put(defaultKey, config);
        defaultWorldConfig = config.getWorldConfig();
        File[] configFiles = WorldConfig.getWorldConfigDir().listFiles(FileUtil
                                        .getFileExtFilter(WorldConfig.getCfgExtension()));
        if(configFiles!=null) {
            for(File file: configFiles) {
                String worldName = FileUtil.getShortName(file);
                if(!worldName.equalsIgnoreCase(WorldConfig.getDefaultWorldConfigName())) {
                    getOrCreateWorldConfig(worldName);
                }
            }
        }
        for(World world: Bukkit.getWorlds()) {
            configureWorld(world);
        }
    }
    
    public static boolean hasPermission(CommandSender player, Permission perm) {
        return player.hasPermission(perm.getPermissionNode());
    }
    
    public static void configureWorld(World world) {
        getOrCreateWorldConfig(world.getName());
        boolean allowMonsters = true;
        boolean allowAnimals = !PluginData.isModuleEnabled(world, Modules.ANIMAL_SPAWN_BLOCKING);
        if (PluginData.isModuleEnabled(world, Modules.MONSTER_SPAWN_BLOCKING)) {
            allowMonsters = false;
        }
        world.setSpawnFlags(allowMonsters, allowAnimals);    
        world.setGameRule(GameRule.DO_FIRE_TICK, true);
        if (PluginData.isModuleEnabled(world, Modules.FIRE_SPREAD_BLOCKING)) {
            world.setGameRule(GameRule.DO_FIRE_TICK, false);
        }
        DevUtil.log(world.getName());
        DevUtil.log("fireTick "+world.getGameRuleValue(GameRule.DO_FIRE_TICK));
        DevUtil.log("animals "+world.getAllowAnimals());
        DevUtil.log("mobs "+world.getAllowMonsters());
        for(String rule: world.getGameRules()) {
            DevUtil.log(rule+" "+world.getGameRuleValue(rule));
        }
    }   
    
    public static InventoryAccess getInventoryAccess(Inventory inventory) {
        InventoryHolder holder = inventory.getHolder();
        Location loc = inventory.getLocation();
        if(loc == null) {
            return InventoryAccess.TRUE;
        }
        WorldConfig config = getOrCreateWorldConfig(loc.getWorld().getName());
        return config.getInventoryAccess(inventory);
    }

    public static boolean getNoInteraction(Block block) {
        WorldConfig config = getOrCreateWorldConfig(block.getWorld().getName());
        return config.getNoInteraction(block.getBlockData());
    }

    public static boolean getNoConnection(Block block) {
        WorldConfig config = getOrCreateWorldConfig(block.getWorld().getName());
        return config.getNoConnection(block.getBlockData());
    }

    public static int getItemBlockBaseLimit(Block block) {
        WorldConfig config = getOrCreateWorldConfig(block.getWorld().getName());
        return config.getItemBlockBaseLimit();
    }
    
    public static void setItemBlockBaseLimit(World world,int limit) {
        WorldConfig config = getOrCreateWorldConfig(world.getName());
        config.setItemBlockBaseLimit(limit);
    }
    
    public static boolean isEnchantmentAllowed(String enchantment, int level) {
        WorldConfig config = getOrCreateWorldConfig(Bukkit.getWorlds().get(0).getName());
        return config.isAllowedEnchantment(enchantment, level);
    }
    
    public static Set<String> getWorldNames() {
        Set<String> names = new HashSet<>();
        for(World world: Bukkit.getWorlds()) {
            names.add(world.getName());//getOrCreateWorldConfig(world.getName());
        }
        return names;//worldConfigs.keySet();
    }
    
    public static boolean setAFK(UUID player) {
        return afkPlayerList.add(player);
    }
    
    public static boolean isAFK(UUID player) {
        return afkPlayerList.contains(player);
    }
    
    public static boolean undoAFK(UUID player) {
        return afkPlayerList.remove(player);
    }
    
    public static boolean moreEntitiesAllowed(Block block) {
        Collection<Entity> entities = block.getWorld().getNearbyEntities(block.getLocation(), 
                                                                            entityLimitRadius, 
                                                                            200, 
                                                                            entityLimitRadius);
        return entities.size()<entityStandLimit;
    }
    
    public static int countNearbyEntities(Block block) {
        Collection<Entity> entities = block.getWorld().getNearbyEntities(block.getLocation(), 
                                                                            entityLimitRadius, 
                                                                            200, 
                                                                            entityLimitRadius);
        return entities.size();
    }
    
    public static boolean checkBuildPermissions(Player player, Location loc, Permission perm) {
        if(!hasPermission(player,perm)) {
            getMessageUtil().sendNoPermissionError(player);
            return false;
        } 
        return TheGafferUtil.checkGafferPermission(player,loc);
   }
    
   public static boolean isAllowedBlock(Player player, BlockData data) {
        WorldConfig config = getOrCreateWorldConfig(player.getWorld().getName());
        return config.isAllowedBlock(data);
   }

    public static String getDefaultKey() {
        return defaultKey;
    }

    public static boolean isOverrideWeather() {
        return overrideWeather;
    }

    public static void setOverrideWeather(boolean overrideWeather) {
        PluginData.overrideWeather = overrideWeather;
    }

    public static MessageUtil getMessageUtil() {
        return messageUtil;
    }

    public static Set<UUID> getAfkPlayerList() {
        return afkPlayerList;
    }

    public static int getEntityStandLimit() {
        return entityStandLimit;
    }

    public static int getEntityLimitRadius() {
        return entityLimitRadius;
    }

    public static File getSwitchStickDir() {
        return switchStickDir;
    }

    public static boolean isSwitchStick(String uuid){
        return Stickconfig.contains(uuid);
    }

    public static void saveStickEntry(String uuid, Object bool){
        if((Boolean) bool){
            Stickconfig.set(uuid,bool);
        }else{
            Stickconfig.set(uuid,null);
        }
        try {
            Stickconfig.save(switckStickFile);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
