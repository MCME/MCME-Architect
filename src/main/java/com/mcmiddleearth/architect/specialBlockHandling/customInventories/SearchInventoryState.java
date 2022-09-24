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
package com.mcmiddleearth.architect.specialBlockHandling.customInventories;

import java.util.List;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

/**
 *
 * @author Eriol_Eandur
 */
public class SearchInventoryState {
    
    private final List<ItemStack> items;

    //private List<ItemStack> foundItems = new ArrayList<>();
    
    private int upperLeftItem;

    private final Inventory inventory;
    
    private final Player player;
    
    
    public SearchInventoryState(List<ItemStack> items, String search, Inventory inventory, Player player) {
        this.items = items;
        this.upperLeftItem = 0;
        this.player = player;
        this.inventory = inventory;
//Logger.getGlobal().info("search items "+items.size());
//Logger.getGlobal().info("found items "+foundItems.size()+ " for "+search);
    }
    
    public void update()  {
        //bottomInv.clear();
        inventory.clear();
        int slotIndex = 0;
        for (int i = upperLeftItem; i < upperLeftItem + visibleItemSlots()
                                  && i < items.size(); i++) {
                if(slotIndex==8 && !isFirstItemVisible()) { 
                    slotIndex++;
                }
                inventory.setItem(slotIndex, items.get(i));
//Logger.getGlobal().info("Item set "+items.get(i));
                //} else { 
                    //inventory.setItem(slotIndex, newPagingItem(CustomInventoryState.pagingMaterial,
                    //                                           CustomInventoryState.pageUp, "page up"));
                //}
                slotIndex++;
            }
        if(!isFirstItemVisible()) {
            //inventory.setItem(slotIndex, newPagingItem(pagingMaterial,pageUp, "page up"));
            inventory.setItem(8,
                              CustomInventoryState.newPagingItem(CustomInventoryState.pagingMaterial,
                                            CustomInventoryState.pageUp, "page up"));
        }
        if(!isLastItemVisible()) {
            inventory.setItem(SearchInventory.ITEM_SLOTS-1,
                              CustomInventoryState.newPagingItem(CustomInventoryState.pagingMaterial,
                                            CustomInventoryState.pageDown, "page down"));
            slotIndex++;
        }
    }
    
    
    public void pageDown() {
        if(!isLastItemVisible()) {
            upperLeftItem += visibleItemSlots();
        }
    }
    
    public void pageUp() {
        if(!isFirstItemVisible()) {
            if(isLastItemVisible()) {
                upperLeftItem -=visibleItemSlots()-1;
            } else {
                upperLeftItem -= visibleItemSlots();
            }
            if(upperLeftItem==1) {
                upperLeftItem=0;
            }
            if(upperLeftItem<0) {
                upperLeftItem = 0 ;
            }
        }
    }
    
    public boolean isPageUpSlot(int slot) {
        return (slot == 8 && !isFirstItemVisible());
    }
    
    public boolean isPageDownSlot(int slot) {
        return (slot == SearchInventory.ITEM_SLOTS - 1
                && !isLastItemVisible());
    }
        
    private int visibleItemSlots() {
        if(items.size()<=SearchInventory.ITEM_SLOTS) {
            return SearchInventory.ITEM_SLOTS;
        } else  if(isFirstItemVisible() || isLastItemVisible()) {
            return SearchInventory.ITEM_SLOTS-1;
        } else {
            return SearchInventory.ITEM_SLOTS-2;
        }
    }
    
    private boolean isFirstItemVisible() {
        return upperLeftItem == 0;
    }
    
    private boolean isLastItemVisible() {
        if(items.size()<=SearchInventory.ITEM_SLOTS) {
            return true;
        } else {
            return items.size() <= upperLeftItem + SearchInventory.ITEM_SLOTS-2;
        }
    }
        
    /*public static ItemStack newPagingItem(Material material, int cmd, String display) {
        ItemStack item = new ItemStack(material,1);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(display);
        meta.setUnbreakable(true);
        meta.addItemFlags(ItemFlag.HIDE_UNBREAKABLE);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        meta.setCustomModelData(cmd);
        item.setItemMeta(meta);
        return item;
    }*/

}