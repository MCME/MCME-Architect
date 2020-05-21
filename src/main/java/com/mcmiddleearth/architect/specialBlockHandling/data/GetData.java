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
package com.mcmiddleearth.architect.specialBlockHandling.data;

import com.mcmiddleearth.architect.ArchitectPlugin;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;

/**
 *
 * @author Eriol_Eandur
 */
public class GetData {

    private static class ItemSet {
        
        boolean isPrivate;
        UUID owner;
        String description;
        ItemStack[] items;
        
        ItemSet(ItemStack[] items, UUID owner, boolean isPrivate, String description) {
            this.isPrivate = isPrivate;
            this.description = description;
            this.owner = owner;
            this.items = items;
        }
    }
    
    private static Map<String, ItemSet> sets = new HashMap<>();
    
    private static final File dataFile = new File(ArchitectPlugin.getPluginInstance()
                                                       .getDataFolder(),"itemSets.yml");

    private static final long STORAGE_TIME = 100*24*3600*1000; //100 days
    
    public static void saveItemSet(Player player, String name, String description, boolean isPrivate) {
        ItemStack[] items = new ItemStack[9];
        for(int i=0; i<9;i++) {
            ItemStack item = player.getInventory().getItem(i);
            if(item==null) {
                items[i]=null;
            } else {
                items[i]=item.clone();
            }
        }
        sets.put(name, new ItemSet(items, player.getUniqueId(), isPrivate, description));
//Logger.getGlobal().info("save");
        save();
    }
    
    public static boolean exists(String arg) {
        return sets.containsKey(arg);
    }

    public static boolean isOwn(Player p, String arg) {
        return sets.containsKey(arg) && sets.get(arg).owner.equals(p.getUniqueId());
    }

    public static boolean isPrivate(String arg) {
        return sets.containsKey(arg) && sets.get(arg).isPrivate;
    }

    public static void delete(String arg) {
        sets.remove(arg);
        save();
    }

    public static void publish(String arg) {
        if(sets.containsKey(arg)) {
            sets.get(arg).isPrivate=false;
            save();
        }
    }

    public static void unpublish(String arg) {
        if(sets.containsKey(arg)) {
            sets.get(arg).isPrivate=true;
            save();
        }
    }

    public static int countPrivate(Player p) {
        int sum = 0;
        for(ItemSet search:sets.values()) {
            if(search.owner.equals(p.getUniqueId()) && search.isPrivate) {
                sum++;
            }
        }
        return sum;
    }
    
    public static ItemStack[] getItems(String name) {
        if(!sets.containsKey(name)) {
            return null;
        } else {
            return sets.get(name).items;
        }
    }
    
    private static void save() {
        YamlConfiguration config = new YamlConfiguration();
        for(String name:sets.keySet()) {
            ItemSet itemSet = sets.get(name);
            OfflinePlayer player = Bukkit.getOfflinePlayer(itemSet.owner);
//Logger.getGlobal().info("time "+System.currentTimeMillis());
//Logger.getGlobal().info("last "+player.getLastPlayed());
            if(!itemSet.isPrivate 
                    ||System.currentTimeMillis()-player.getLastPlayed()<STORAGE_TIME) {
                ConfigurationSection section = config.createSection(name);
                section.set("owner", itemSet.owner.toString());
                section.set("isPrivate", itemSet.isPrivate);
                section.set("description", itemSet.description);
                section.set("items", itemSet.items);
            }
        }
        try {
            config.save(dataFile);
//Logger.getGlobal().info("saved");
        } catch (IOException ex) {
            Logger.getLogger(GetData.class.getName()).log(Level.SEVERE, null, ex);
        }
    }
    
    public static void load() {
        YamlConfiguration config = new YamlConfiguration();
        try {
            config.load(dataFile);
        } catch (IOException | InvalidConfigurationException ex) {
            Logger.getLogger(GetData.class.getName()).log(Level.WARNING, "Item set file not found.");
        }
        for(String name: config.getKeys(false)) {
            ConfigurationSection section = config.getConfigurationSection(name);
            ItemSet itemSet = new ItemSet(null,null,false,null);
            itemSet.owner = UUID.fromString(section.getString("owner"));
            itemSet.description = section.getString("description","no description");
            itemSet.isPrivate = section.getBoolean("isPrivate",false);
            itemSet.items = section.getList("items", new ArrayList<ItemStack>())
                                   .toArray(new ItemStack[0]);
            sets.put(name, itemSet);
        }
        new BukkitRunnable() {
            @Override
            public void run() {
                save();
            }
            
        }.runTaskLater(ArchitectPlugin.getPluginInstance(),2000);
    }
    
    public static List<String> getVisibleNames(Player p) {
        List<String> result = new ArrayList<>();
        for(String name:sets.keySet()) {
            ItemSet itemset = sets.get(name);
            if(!itemset.isPrivate || itemset.owner.equals(p.getUniqueId())) {
                result.add(name);
            }
        }
        return result;
    }

    public static UUID getOwner(String name) {
        return sets.get(name).owner;
    }

    public static String getDescription(String name) {
        return sets.get(name).description;
    }

}
