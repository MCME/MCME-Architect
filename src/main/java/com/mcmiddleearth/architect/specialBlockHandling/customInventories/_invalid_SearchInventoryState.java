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

import com.mcmiddleearth.pluginutil.ReflectionUtil;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.AnvilInventory;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

/**
 *
 * @author Eriol_Eandur
 */
public class _invalid_SearchInventoryState {
    
    private final List<ItemStack> items;

    private final List<ItemStack> foundItems = new ArrayList<>();
    
    private int upperLeftItem;

    private final Inventory inventory;
    
    private final Player player;
    
    private final ItemStack[] playerItems;
    
    //private final Inventory topInv;
    
    //private final Inventory bottomInv;
    
    public _invalid_SearchInventoryState(List<ItemStack> items, Inventory inventory, Player player) {
        this.items = items;
        this.upperLeftItem = 0;
        //this.topInv = inventory;
//Logger.getGlobal().info("Inventory 2a");
        //this.bottomInv = Bukkit.createInventory(null, InventoryType.CHEST);
//Logger.getGlobal().info("Inventory 2b");
        this.player = player;
        this.inventory = inventory;
        playerItems = player.getInventory().getStorageContents();
        for(int i=0;i<playerItems.length;i++) {
            ItemStack item = playerItems[i];
            if(item!=null) {
                playerItems[i]=item.clone();
            }
        }
//Logger.getGlobal().info("#items "+playerItems.length);
//for(int i=0; i<playerItems.length;i++) {
//    Logger.getGlobal().info("# "+playerItems[i]);
//}
    }
    
    private void clearInventory() {
       inventory.clear();
       ItemStack item = new ItemStack(Material.GOLD_NUGGET,1);
       ItemMeta meta = item.getItemMeta();
       meta.setDisplayName("ab");
       item.setItemMeta(meta);
       inventory.setItem(0, item.clone());
       //inventory.setItem(1, new ItemStack(Material.GOLD_NUGGET,2));
       inventory.setItem(2, item.clone());
    }
    
    public void resetSearch() {
        foundItems.clear();
        ReflectionUtil.showMethods(inventory);
        ReflectionUtil.showFields(inventory);
        //((AnvilInventory)inventory).setRenameText("");
    }
    
    public void search() {
        ItemStack searchItem = inventory.getItem(2);
Logger.getGlobal().info("search: "+searchItem);
        String search = "";
        if(searchItem.getItemMeta().hasDisplayName()) {
            search = searchItem.getItemMeta().getDisplayName();
        } 
Logger.getGlobal().info("search: "+search);
        foundItems.clear();
        upperLeftItem=0;
        for(ItemStack item:items) {
            if(item.getItemMeta().hasDisplayName() 
                    && item.getItemMeta().getDisplayName().contains(search)) {
                foundItems.add(item.clone());
           }
        }
Logger.getGlobal().info("found items: "+foundItems.size());
    }
    
    public void update()  {
        //bottomInv.clear();
        clearInventory();
        int slotIndex = 9;
        for (int i = upperLeftItem; i < upperLeftItem + visibleItemSlots()
                                  && i < foundItems.size(); i++) {
                if(slotIndex==inventory.getSize()+8 && !isFirstItemVisible()) { 
                    player.getInventory().setItem(slotIndex, newPagingItem(CustomInventoryState.pagingMaterial,
                                                               CustomInventoryState.pageUp, "page up"));
                } else {
                    player.getInventory().setItem(slotIndex, foundItems.get(i));
                }
                slotIndex++;
            }
        if(!isLastItemVisible()) {
            player.getInventory().setItem(slotIndex,
                              newPagingItem(CustomInventoryState.pagingMaterial,
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
            upperLeftItem -= visibleItemSlots();
            if(upperLeftItem<0) {
                upperLeftItem = 0 ;
            }
        }
    }
    
    public boolean isResetSlot(int slot) {
        return slot==0;
    }
    
    public boolean isSearchSlot(int slot) {
        return slot == 2;
    }
    public boolean isPageUpSlot(int slot) {
        return (slot == 11 && !isFirstItemVisible());
    }
    
    public boolean isPageDownSlot(int slot) {
        return (slot == inventory.getSize() + SearchInventory.ITEM_SLOTS - 1
                && !isLastItemVisible());
    }
        
    private int visibleItemSlots() {
        if(foundItems.size()<=SearchInventory.ITEM_SLOTS) {
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
        if(foundItems.size()<=SearchInventory.ITEM_SLOTS) {
            return true;
        } else {
            return foundItems.size() <= upperLeftItem + SearchInventory.ITEM_SLOTS-2;
        }
    }
        
    private ItemStack newPagingItem(Material material, int cmd, String display) {
        ItemStack item = new ItemStack(material,1);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(display);
        meta.setUnbreakable(true);
        meta.addItemFlags(ItemFlag.HIDE_UNBREAKABLE);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        meta.setCustomModelData(cmd);
        item.setItemMeta(meta);
        return item;
    }

    public void restorePlayerInventory() {
        //player.getInventory().setStorageContents(playerItems);
        for(int i=9;i<playerItems.length;i++) {
            player.getInventory().setItem(i, playerItems[i]);
//    Logger.getGlobal().info("### "+i+" "+playerItems[i]);
        }
        /*new BukkitRunnable() {
            @Override
            public void run() {
Logger.getGlobal().info("RESTET "+player.getName());
            }
        }.runTaskLater(ArchitectPlugin.getPluginInstance(), 2);*/
    }
/*    @Override
    public Inventory getTopInventory() {
        return topInv;
    }

    @Override
    public Inventory getBottomInventory() {
        return bottomInv;
    }

    @Override
    public HumanEntity getPlayer() {
        return player;
    }

    @Override
    public InventoryType getType() {
        return InventoryType.ANVIL;
    }*/
}