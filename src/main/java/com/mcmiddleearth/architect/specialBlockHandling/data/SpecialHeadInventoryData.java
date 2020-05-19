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

import com.mcmiddleearth.architect.customHeadManager.CustomHeadCollection;
import com.mcmiddleearth.architect.customHeadManager.CustomHeadManagerData;
import com.mcmiddleearth.architect.specialBlockHandling.customInventories.CustomInventory;
import com.mcmiddleearth.architect.specialBlockHandling.customInventories.CustomInventoryState;
import com.mcmiddleearth.architect.specialBlockHandling.customInventories.SearchInventory;
import java.util.HashMap;
import java.util.Map;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 *
 * @author Eriol_Eandur
 */
public class SpecialHeadInventoryData {
    
    private static CustomInventory inventory;
    
    private static SearchInventory searchInventory;

    static {
        createInventories();
    }
    
    public static void loadInventory() {
        if(inventory!=null) {
            inventory.destroy();
        }
        if(searchInventory!=null) {
            searchInventory.destroy();
        }
        createInventories();
        for(String name: CustomHeadManagerData.getCollection().getSubCollections().keySet()) {
            CustomHeadCollection collection = CustomHeadManagerData.getCollection()
                                                                   .getSubCollection(name);
            Map<String, ItemStack> heads = new HashMap<>();
            collection.getAllHeadsIncludingSubCollections(heads);
//Logger.getGlobal().info("Loading head collection: "+name+ " count: "+heads.size());
            for(ItemStack head: heads.values()) {
                inventory.add(head, name);
                searchInventory.add(head);
            }
            if(!heads.isEmpty()) {
                ItemStack categoryItem = heads.values().iterator().next();
                inventory.setCategoryItems(name, null, true, 
                                           categoryItem.clone(), 
                                           new ItemStack(CustomInventoryState.pagingMaterial,1,
                                                 CustomInventoryState.pageDown));
            }
        }
    }
    
    
    public static void openInventory(Player p) {
        inventory.open(p,null);
    }
    
    public static void openSearchInventory(Player p, String search) {
        searchInventory.open(p, search);
    }
    
    private static void createInventories() {
        inventory = new CustomInventory(ChatColor.WHITE+"MCME Head Collection");
        searchInventory = new SearchInventory(ChatColor.WHITE+"heads");
    }
    
}
