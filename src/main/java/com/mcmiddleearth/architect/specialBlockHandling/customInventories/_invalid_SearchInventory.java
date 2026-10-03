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

/**
 *
 * @author Eriol_Eandur
 */

import com.mcmiddleearth.architect.ArchitectPlugin;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
 
public class _invalid_SearchInventory implements Listener {
 
    public static final int ITEM_SLOTS = 27;
    
    private final String name;
   
    private final List<ItemStack> items = new ArrayList<>();
    
    private final Map<Inventory,_invalid_SearchInventoryState> openInventories = new HashMap<>();
    
    public _invalid_SearchInventory(String name) {
        this.name = name;
        Plugin plugin = ArchitectPlugin.getPluginInstance();
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }
   
    public _invalid_SearchInventory add(ItemStack item) {//, String name, String... info) {
        //categories.get("search").addItem(item);
        items.add(item);
        return this;
    }
   
    /*public void setCategoryItem(String category, ItemStack item) {
        createCategoryIfNotExists(category, item, item);
    }*/
        
    public void open(Player player) {
        int size = ITEM_SLOTS;
        Inventory inventory = Bukkit.createInventory(null, InventoryType.ANVIL);//, name);
        _invalid_SearchInventoryState state = new _invalid_SearchInventoryState(items, inventory, player);
        openInventories.put(inventory, state);
        state.update();
        //fillInventory(inventory, startCategory, 0);

        player.openInventory(inventory);
        //player.openInventory(Bukkit.createInventory(player, InventoryType.CHEST));
    }
    
    public void destroy() {
        HandlerList.unregisterAll(this);
        items.clear();
    }
   
    @EventHandler(priority=EventPriority.HIGHEST)
    void onInventoryDrag(final InventoryDragEvent event) {
        if (openInventories.containsKey(event.getInventory())) {//.getTitle().equals(name)) {
            for(int slot : event.getRawSlots()) {
                if(slot<event.getInventory().getSize()+ITEM_SLOTS) {
                    event.setCancelled(true);
                }
            }
        }
    }
            
    @EventHandler(priority=EventPriority.HIGHEST, ignoreCancelled=true)
    void onInventoryClick(final InventoryClickEvent event) {
        if (openInventories.containsKey(event.getInventory())) { //.getTitle.equals(name)) {
            if(event.getSlotType().equals(InventoryType.SlotType.OUTSIDE)
                    || event.getRawSlot() >= event.getInventory().getSize()+ ITEM_SLOTS) {//items.size()/9+1)*9 
                return;
            }
            _invalid_SearchInventoryState state = openInventories.get(event.getInventory());
            event.setCancelled(true);
            if(state.isSearchSlot(event.getRawSlot())) {
                /*final Player player = (Player) event.getViewers().get(0);
                event.setC
                new BukkitRunnable() {
                    @Override
                    public void run() {
                  */      
                        state.search();
                        state.update();
                  /*  }
                }.runTaskLater(ArchitectPlugin.getPluginInstance(), 1);*/
                return;
            }
            if(state.isResetSlot(event.getRawSlot())) {
                state.resetSearch();
                state.update();
                return;
            }
            if(event.getRawSlot()<event.getInventory().getSize()) {
                return;
            }
            if(state.isPageUpSlot(event.getRawSlot())) {
                state.pageUp();
                state.update();
                return;
            }
            if(state.isPageDownSlot(event.getRawSlot())) {
                state.pageDown();
                state.update();
                return;
            }
            if(event.getCursor().getType().equals(Material.AIR)) {
                event.setCursor(new ItemStack(event.getCurrentItem()));
            } else if(event.getCursor().isSimilar(event.getCurrentItem())) { 
                if(event.getCursor().getMaxStackSize()>event.getCursor().getAmount()) {
                    event.getCursor().setAmount(event.getCursor().getAmount()+1);
                }
            } else {
                event.setCursor(new ItemStack(Material.AIR));
            }
        }
    }
        
    @EventHandler(priority=EventPriority.HIGHEST, ignoreCancelled=true)
    void onScroll(final InventoryClickEvent event) {
        if(openInventories.containsKey(event.getInventory())) {
            //int first = inventoryFirstItems.get(event.getInventory());
            //String category = inventoryCategories.get(event.getInventory());t
            _invalid_SearchInventoryState state = openInventories.get(event.getInventory());
            if(event.getClick().equals(ClickType.SHIFT_RIGHT)) {
                state.pageDown();
                state.update();
                /*if(first<items.get(category).size()-event.getInventory().getSize()) {
                    first+=event.getInventory().getSize();
                    inventoryFirstItems.put(event.getInventory(), first);
                    event.getInventory().clear();
                    fillInventory(event.getInventory(), category, first);
                }*/
                event.setCancelled(true);
            } else if(event.getClick().equals(ClickType.SHIFT_LEFT)) {
                /*if(first>0) {
                    first-=event.getInventory().getSize();
                    if(first<0) first = 0;
                    inventoryFirstItems.put(event.getInventory(), first);
                    event.getInventory().clear();
                    fillInventory(event.getInventory(), category, first);
                }*/
                state.pageUp();
                state.update();
                event.setCancelled(true);
            }
        }
    }
    
    @EventHandler(priority=EventPriority.HIGHEST, ignoreCancelled=true)
    void onClose(final InventoryCloseEvent event) {
        if(openInventories.containsKey(event.getInventory())) {
            _invalid_SearchInventoryState state = openInventories.get(event.getInventory());
            state.restorePlayerInventory();
            openInventories.remove(event.getInventory());
        }
    }

}
