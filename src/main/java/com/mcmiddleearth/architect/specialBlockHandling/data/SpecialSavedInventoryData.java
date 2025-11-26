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
import com.mcmiddleearth.architect.specialBlockHandling.customInventories.CustomInventory;
import com.mcmiddleearth.architect.specialBlockHandling.customInventories.CustomInventoryCategory;
import com.mcmiddleearth.architect.specialBlockHandling.customInventories.CustomInventoryState;
import com.mcmiddleearth.pluginutil.FileUtil;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

/**
 *
 * @author Eriol_Eandur
 */
public class SpecialSavedInventoryData {
    
    private static final Map<String,CustomInventory> inventories = new HashMap<>();

    private static final File configFolder = new File(ArchitectPlugin.getPluginInstance()
                                                       .getDataFolder(),"inventories/saved");
    
    static {
        if(!configFolder.exists()) {
            configFolder.mkdirs();
        }
    }
    public static void loadInventories() {
        if(!inventories.isEmpty()) {
            for(CustomInventory inv:inventories.values()) {
                inv.destroy();
            }
            inventories.clear();
        }
        File[] files = configFolder.listFiles(FileUtil.getDirFilter());
        if(files!=null) {
            for(File file: files) {
                createBlockInventory(file);
            }
        }
    }
    
    private static void createBlockInventory(File folder) {
        String rpName = folder.getName();
        File[] files = folder.listFiles(FileUtil.getFileExtFilter("yml"));
        CustomInventory inventory = new CustomInventory(ChatColor.WHITE+"Saved Inventories - "+rpName);
        inventories.put(rpName, inventory);
        for(File file: files) {
            loadFromFile(inventory, rpName, file);
        }
    }
    
    private static void loadFromFile(CustomInventory inventory, String rpName, File file) {
        Logger.getGlobal().info("Loading items into to inventory for resource pack "+rpName+" from "+file.getName());
        YamlConfiguration config = new YamlConfiguration();
        try {
            config.load(file);
        } catch (IOException | InvalidConfigurationException ex) {
            Logger.getLogger(SpecialSavedInventoryData.class.getName()).log(Level.SEVERE, null, ex);
        }
        String categoryName = file.getName().substring(0,file.getName().length()-4);
        ConfigurationSection categoryConfig = config.getConfigurationSection("category");
        ItemStack item = (ItemStack) categoryConfig.get("item");
        UUID owner = UUID.fromString(categoryConfig.getString("owner"));
        boolean isPrivate = categoryConfig.getBoolean("isPrivate");
        inventory.setCategoryItems(categoryName, owner, isPrivate, item,
                CustomInventoryState.newPagingItem(CustomInventoryState.pagingMaterial,
                                                 CustomInventoryState.pageDown, "page down"), false, new ArrayList<>(), new HashMap<>());
        List<?> itemConfig = config.getList("items");
        for(Object itemData: itemConfig) {
            inventory.add((ItemStack)itemData, categoryName, null, false);
        }
    }
    
    public static boolean saveInventory(Player player, String categoryName, String rpName, boolean isPrivate) {
        File folder = new File(configFolder,rpName);
        if(!folder.exists()) {
            folder.mkdir();
        }
        File file = new File(folder,categoryName+".yml");
        YamlConfiguration config = new YamlConfiguration();
        ConfigurationSection categoryConfig = config.createSection("category");
        categoryConfig.set("isPrivate", isPrivate);
        categoryConfig.set("owner", player.getUniqueId().toString());
        ItemStack categoryItem = player.getInventory().getItemInOffHand();
        if(categoryItem==null || categoryItem.getType().equals(Material.AIR)){
            categoryItem = new ItemStack(Material.GOLD_NUGGET,1);
        }
        categoryConfig.set("item", categoryItem);
        //ConfigurationSection itemsConfig = config.createSection("items");
        List<ItemStack> items = new ArrayList<>();
        PlayerInventory inventory = player.getInventory();
        for(ItemStack item:inventory.getStorageContents()) {
            if(item!=null) {
                items.add(item);
            }
        }
        config.set("items", items);
        try {
            config.save(file);
            CustomInventory customInv = inventories.get(rpName);
            if(customInv==null) {
                customInv = new CustomInventory("Custom Inventory");
                inventories.put(rpName, customInv);
            }
            loadFromFile(customInv,rpName,file);
            return true;
        } catch (IOException ex) {
            Logger.getLogger(SpecialSavedInventoryData.class.getName()).log(Level.SEVERE, null, ex);
            return false;
        }
    }
    
    public static void deleteInventory(String categoryName, String rpName) {
        File folder = new File(configFolder,rpName);
        File file = new File(folder,categoryName+".yml");
        if(file.exists()) {
            file.delete();
        }
        CustomInventory customInv = inventories.get(rpName);
        if(customInv!=null)  {
            customInv.deleteCategory(categoryName);
        }
    }
    
    public static boolean categoryExists(String categoryName, String rpName) {
        File folder = new File(configFolder,rpName);
        if(!folder.exists()) {
            return false;
        }
        File file = new File(folder,categoryName+".yml");
        return file.exists();
    }
    
    public static CustomInventoryCategory getCategory(String categoryName, String rpName) {
        return inventories.get(rpName).getCategory(categoryName);
    }
    
    public static void openInventory(Player p, String resourcePack) {
        CustomInventory inv = inventories.get(resourcePack);
//Logger.getGlobal().info("savedInv: "+inventories.size());
//Logger.getGlobal().info("savedInv: "+inventories.keySet().iterator().next());

        if(inv==null) {
            inv = inventories.get("Human");
        }
        if(inv!=null) {
            inv.open(p,null);
        }
    }
    
}
