/*
 * Copyright (C) 2020 Eriol_Eandur
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
package com.mcmiddleearth.architect.specialBlockHandling.itemBlock;

import com.mcmiddleearth.architect.ArchitectPlugin;
import com.mcmiddleearth.architect.Permission;
import com.mcmiddleearth.architect.PluginData;
import com.mcmiddleearth.architect.serverResoucePack.RpManager;
import com.mcmiddleearth.util.DevUtil;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.*;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author Eriol_Eandur
 */
public class ItemBlockManager {
    
    private static final File regionFolder = new File(ArchitectPlugin.getPluginInstance().getDataFolder(),"itemBlockRegions");
    
    private static Map<String, ItemBlockRegion> regions = new HashMap<>();
    
    private static Map<Player, Integer> glowPlayer = new HashMap<>();
    private static List<Entity> glowEntities = new ArrayList<>();
    
    
    private static BukkitTask glowTask;
    
    public static void init() {
        if(!regionFolder.exists()) {
            regionFolder.mkdir();
        }
        regions.clear();
        for(File file: regionFolder.listFiles((File dir, String name) -> name.endsWith(".reg"))) {
            try {
                YamlConfiguration config = new YamlConfiguration();
                config.load(file);
                final ConfigurationSection section = config.getConfigurationSection("itemBlockRegion");
                new BukkitRunnable() {
                    int counter = 10;
                    @Override
                    public void run() {
                        ItemBlockRegion region = ItemBlockRegion.loadFromMap((Map<String,Object>)section.getValues(true));
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
    }
    
    public static boolean allowPlace(Block block, Player player) {
        if (player.hasPermission(Permission.ITEM_BLOCK_REGION_IGNORE.getPermissionNode())) {
            return true;
        }
        ItemBlockRegion region = getRegion(block.getLocation());
        int limit;
        if(region==null) {
            limit = PluginData.getItemBlockBaseLimit(block);
        } else {
            limit = region.getLimit();
        }
        return limit> Arrays.asList(block.getChunk().getEntities())
                                 .stream().filter(entity -> entity instanceof ArmorStand
                                                          || entity instanceof Painting
                                                          || entity instanceof ItemFrame).count();
    }
    
    public static int getLimit(Block block) {
        ItemBlockRegion region = getRegion(block.getLocation());
        return (region != null ? region.getLimit() : PluginData.getItemBlockBaseLimit(block));
    }
    
    public static ItemBlockRegion getRegion(Location loc) {
        ItemBlockRegion maxLimit = null;
        for(ItemBlockRegion region: regions.values()) {
            if((maxLimit==null || region.getLimit() > maxLimit.getLimit()) 
                    && region.contains(loc)) {
                maxLimit = region;
            }
        }
        return maxLimit;
    }
    
    public static ItemBlockRegion getRegion(String name) {
        return regions.get(name);
    }
    
    public static void updateDynmapRegions() {
        ItemBlockDynmapUtil.clearMarkers();
        regions.values().forEach((region) -> {
            ItemBlockDynmapUtil.createMarker(region);
        });
    }
    
    public static boolean removeRegion(String name) {
        ItemBlockRegion region = regions.get(name);
        if(region!=null) {
            regions.remove(name);
            new File(regionFolder,name+".reg").delete();
            updateDynmapRegions();
            return true;
        }
        return false;
    }
    
    public static void addRegion(ItemBlockRegion region) {
        regions.put(region.getName(), region);
        updateDynmapRegions();
    }
    
    public static void saveItemBlockRegion(ItemBlockRegion region) {
        try {
            YamlConfiguration config = new YamlConfiguration();
            config.set("itemBlockRegion", region.saveToMap());
            config.save(new File(regionFolder,region.getName()+".reg"));
        } catch (IOException ex) {
            Logger.getLogger(RpManager.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    public static void startEntityGlowTask() {
        if(glowTask!=null && !glowTask.isCancelled()) {
            glowTask.cancel();
        }
        glowTask = new BukkitRunnable() {
            @Override
            public void run() {
                glowEntities.forEach(entity -> entity.setGlowing(false));
                glowEntities.clear();
                glowPlayer.forEach((player,radius) -> { 
                    Collection<Entity> entities = player.getWorld()
                            .getNearbyEntities(player.getLocation(), radius, radius, radius, 
                                               entity -> entity instanceof ArmorStand);
                    entities.forEach(entity -> entity.setGlowing(true));
                    glowEntities.addAll(entities);
                });
            }
        }.runTaskTimer(ArchitectPlugin.getPluginInstance(), 400, 20);
    }

    public static void stopEntityGlowTask() {
        glowTask.cancel();
    }

    static void removeGlowPlayer(Player p) {
        glowPlayer.remove(p);
    }

    static void addGlowPlayer(Player p, int radius) {
        glowPlayer.put(p, radius);
    }

    public static Map<String, ItemBlockRegion> getRegions() {
        return regions;
    }
}
