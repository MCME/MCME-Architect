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

import java.util.*;
import java.util.logging.Logger;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
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
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.ShapelessRecipe;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.Plugin;

import javax.xml.stream.events.Namespace;

public class SearchInventory implements Listener {
 
    public static final int ITEM_SLOTS = 54;
    
    private final String name, rpName;
   
    private final List<ItemStack> items = new ArrayList<>();

    private final Map<NamespacedKey,Recipe> recipes = new HashMap<>();
    
    private final Map<Inventory,SearchInventoryState> openInventories = new HashMap<>();
    
    public SearchInventory(String name, String rpName) {
        this.name = name;
        this.rpName = rpName;
        Plugin plugin = ArchitectPlugin.getPluginInstance();
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        /*for(Material type: Material.values()) {
            ItemStack item = new ItemStack(type);
            if(!item.getType().equals(Material.AIR)) {
                items.add(new ItemStack(type));
            }
        }*/
    }
   
    public SearchInventory add(ItemStack item) {//, String name, String... info) {
        items.add(item);
        String key = (item.getItemMeta().getDisplayName()+rpName+recipes.size()).replaceAll("[^0-9a-z/._-]","");
        NamespacedKey namespacedKey = new NamespacedKey(ArchitectPlugin.getPluginInstance(),key);
        ShapelessRecipe recipe = new ShapelessRecipe(namespacedKey,item);
        recipe.setGroup(""+recipes.size());
        recipe.addIngredient(item);
        recipes.put(namespacedKey,recipe);
        //Bukkit.addRecipe(recipe);
        return this;
    }
   
    private List<ItemStack> search(String search) {
        List<ItemStack> foundItems = new ArrayList<>();
        for(ItemStack item:items) {
//Logger.getGlobal().info("1");
            String name = item.getType().toString();
//Logger.getGlobal().info("2 "+item.getType().name());
            if(item.hasItemMeta() && item.getItemMeta().hasDisplayName()) {
//Logger.getGlobal().info("3");
                name = item.getItemMeta().getDisplayName();
            } 
//Logger.getGlobal().info("4");
            if(name.toLowerCase().contains(search.toLowerCase())) {
//Logger.getGlobal().info("5");
                foundItems.add(item.clone());
           }
        }
        return foundItems;
    }
    
    public void open(Player player, String search) {
        int size = ITEM_SLOTS;
        List<ItemStack> foundItems = search(search);
        Inventory inventory = Bukkit.createInventory(player, size, 
                                     ChatColor.YELLOW+""+foundItems.size()+" "+name+" found for *"+search+"*");
//Logger.getGlobal().info("Inventory "+items.size());
        SearchInventoryState state = new SearchInventoryState(foundItems, search, inventory, player);
        openInventories.put(inventory, state);
//Logger.getGlobal().info("Inventory 2c");
        state.update();
//Logger.getGlobal().info("Inventory 2d");
        //fillInventory(inventory, startCategory, 0);
        
        player.openInventory(inventory);
        //player.openInventory(Bukkit.createInventory(player, InventoryType.CHEST));
//Logger.getGlobal().info("Inventory 2");
    }
    
    public void destroy() {
        HandlerList.unregisterAll(this);
        items.clear();
    }
   
    @EventHandler(priority=EventPriority.HIGHEST)
    void onInventoryDrag(final InventoryDragEvent event) {
        if (openInventories.containsKey(event.getInventory())) {//.getTitle().equals(name)) {
            for(int slot : event.getRawSlots()) {
                if(slot<event.getInventory().getSize()) {
                    event.setCancelled(true);
                }
            }
        }
    }
            
    @EventHandler(priority=EventPriority.HIGHEST, ignoreCancelled=true)
    void onInventoryClick(final InventoryClickEvent event) {
//Logger.getGlobal().info("click size "+event.getInventory().getSize());
//Logger.getGlobal().info("click slot "+event.getRawSlot());
        if (openInventories.containsKey(event.getInventory())) { //.getTitle.equals(name)) {
            if(event.getSlotType().equals(InventoryType.SlotType.OUTSIDE)
                    || event.getRawSlot() >= event.getInventory().getSize()) {//items.size()/9+1)*9 
                return;
            }
            SearchInventoryState state = openInventories.get(event.getInventory());
            event.setCancelled(true);
//Logger.getGlobal().info("click "+event.getRawSlot());
            if(state.isPageUpSlot(event.getRawSlot())) {
//Logger.getGlobal().info("click at up");
                state.pageUp();
                state.update();
                return;
            }
            if(state.isPageDownSlot(event.getRawSlot())) {
//Logger.getGlobal().info("click at down");
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
            SearchInventoryState state = openInventories.get(event.getInventory());
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
//Logger.getGlobal().info("Close");
        if(openInventories.containsKey(event.getInventory())) {
            SearchInventoryState state = openInventories.get(event.getInventory());
//Logger.getGlobal().info("Close2");
            openInventories.remove(event.getInventory());
        }
    }

    public boolean isEmpty() {
        return items.size()>0;
    }

    public ItemStack getItem(String id) {
//Logger.getGlobal().info("search getItem: "+id);
        return items.stream().filter(item -> {
            if(item.hasItemMeta() && item.getItemMeta().hasLore()) {
                return item.getItemMeta().getLore().get(1).equals(id);
//            } else {
//                Logger.getGlobal().warning("Invalid item in search inventory: "+item);
            }
            return false;
        }).findFirst().orElse(null);
    }

    public Set<NamespacedKey> getRecipeKeys() {
        return recipes.keySet();
    }

    public Recipe getRecipe(NamespacedKey key) {
        return recipes.get(key);
    }

    /*public void setRecipes() {
        for(int i = 0 ; i<items.size();i++) {
            ItemStack item = items.get(i);
            String key = item.getItemMeta().getDisplayName().replaceAll("[^0-9a-z/._-]","")+i;
//Logger.getGlobal().info(key);
            //key = key.split("\\[")[0];
//Logger.getGlobal().info(key);
            ShapelessRecipe recipe = new ShapelessRecipe(new NamespacedKey(ArchitectPlugin.getPluginInstance(),key),item);
            recipe.setGroup(name);
            recipe.addIngredient(item);
            Bukkit.addRecipe(recipe);
        }
    }*/
}
