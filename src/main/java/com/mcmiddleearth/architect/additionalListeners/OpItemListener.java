/*
 * Copyright (C) 2019 Eriol_Eandur
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
package com.mcmiddleearth.architect.additionalListeners;

import com.mcmiddleearth.architect.ArchitectPlugin;
import com.mcmiddleearth.architect.Modules;
import com.mcmiddleearth.architect.PluginData;
import com.mcmiddleearth.pluginutil.NBTTagUtil;
import com.mcmiddleearth.pluginutil.NMSUtil;
import java.util.Iterator;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerPickupArrowEvent;
import org.bukkit.event.player.PlayerPickupItemEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;

/**
 *
 * @author Eriol_Eandur
 */
public class OpItemListener implements Listener {
    
    @EventHandler
    public void onItemPickup(PlayerPickupItemEvent event) {
//Logger.getGlobal().info("PlayerPickupEvent!");
        //checkItems(event.getPlayer());
        checkItem(event.getItem().getItemStack());
    }
    
    @EventHandler
    public void onArrowPickup(PlayerPickupArrowEvent event) {
//Logger.getGlobal().info("PlayerPickupArrowEvent!");
        //checkItems(event.getPlayer());
        checkItem(event.getItem().getItemStack());
    }
    
    @EventHandler
    public void onItemDrop(PlayerDropItemEvent event) {
//Logger.getGlobal().info("PlayerDropItemEvent!");
        checkItem(event.getItemDrop().getItemStack());
    }
    
    @EventHandler
    public void onInventoryEvent(InventoryCloseEvent event) {
//Logger.getGlobal().info("InventoryCloseEvent!");
        checkItems(event.getInventory().iterator());
        checkItems(event.getView().getPlayer().getInventory().iterator());
    }
    
    @EventHandler
    public void onItemChange(PlayerItemHeldEvent event) {
//Logger.getGlobal().info("PlayerItemHeldEvent!");
        checkItem(event.getPlayer().getInventory().getItem(event.getPreviousSlot()));
        checkItem(event.getPlayer().getInventory().getItem(event.getNewSlot()));
    }
    
    @EventHandler
    public void onInventoryEvent(InventoryClickEvent event) {
//Logger.getGlobal().info("InventoryClickEvent!");
        //checkItem(event.getCurrentItem());
        //checkItem(event.getCursor());
        //int slot = event.getSlot();
        //Inventory inventory = event.getInventory();
        new BukkitRunnable() {
            @Override
            public void run() {
                checkItems(event.getWhoClicked().getInventory().iterator());
            }
        }.runTaskLater(ArchitectPlugin.getPluginInstance(),2);
        //checkItems(event.getInventory().iterator());
        //checkItems(event.getView().getPlayer().getInventory().iterator());
        //checkItems(event.getWhoClicked().getInventory().iterator());
    }
    
    private void checkItems(Player player) {
        Iterator<ItemStack> iterator = player.getInventory().iterator();
        checkItems(iterator);
    }
    
    private void checkItems(Iterator<ItemStack> iterator) {
        new BukkitRunnable() {
            @Override
            public void run() {
                while(iterator.hasNext()) {
                    checkItem(iterator.next());
                }
            }
        }.runTaskLater(ArchitectPlugin.getPluginInstance(),2);
    }
    private void checkItem(ItemStack item) {
        if(item==null) return;
        if(PluginData.isModuleEnabled(Bukkit.getWorlds().get(0), Modules.BLOCK_OP_ITEMS)) {
            try {
                //Class clazz = item.getClass();
                //Field field = clazz.getDeclaredField("handle");
                //field.setAccessible(true);
                //Object nmsItem = field.get(item);
                //Logger.getLogger(ArchitectPlugin.class.getName())
                //      .log(Level.INFO, "Check item: "+item.getType());
                Object nmsItem = NMSUtil.getCraftBukkitDeclaredField("inventory.CraftItemStack","handle",item);
                Object tag = NMSUtil.invokeNMS("world.item.ItemStack", "s", new Class[]{}, nmsItem);
                if(NBTTagUtil.hasKey(tag, "world.item.enchantment.Enchantments")) {
                    Object enchantments = NBTTagUtil.getTagList(tag, "Enchantments");
                    for(int i = 0;
                            i < (int) NMSUtil.invokeNMS("nbt.NBTTagList", "size", new Class[]{},
                                    enchantments); i++) {
                        Object enchant = NMSUtil.invokeNMS("nbt.NBTTagList", "getCompound",
                                new Class[]{int.class}, enchantments, i);
                        String name = NBTTagUtil.getString(enchant,"id");
                        int level = NBTTagUtil.getInt(enchant,"lvl");
                        if(!PluginData.isEnchantmentAllowed(name, level)) {
                            block(nmsItem);
                            return;
                        }
                    }
                }
                if(NBTTagUtil.hasKey(tag, "AttributeModifiers")) {
                    block(nmsItem);
                    return;
                }
            } catch (SecurityException | IllegalArgumentException ex) {
                Logger.getLogger(OpItemListener.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
    }
    
    private void block(Object nmsItem) {
        try {
            NMSUtil.invokeNMS("world.item.ItemStack", "setTag",
                    new Class[]{NMSUtil.getNMSClass("nbt.NBTTagCompound")}, nmsItem,(Object) null);
        } catch (ClassNotFoundException ex) {
            Logger.getLogger(OpItemListener.class.getName()).log(Level.SEVERE, null, ex);
        }
    }
    /*
    @EventHandler
    public void onInventoryEvent(InventoryDragEvent event) {
Logger.getGlobal().info("InventoryDragEvent!");
        checkItems(event.getInventory().iterator());
        checkItems(event.getView().getPlayer().getInventory().iterator());
    }
    
    @EventHandler
    public void onInventoryEvent(InventoryPickupItemEvent event) {
Logger.getGlobal().info("InventoryPickupItemEvent!");
        checkItems(event.getInventory().iterator());
    }
    
    @EventHandler
    public void onInventoryEvent(InventoryMoveItemEvent event) {
Logger.getGlobal().info("InventoryMoveItemEvent!");
        checkItems(event.getInitiator().iterator());
        checkItems(event.getDestination().iterator());
    }
    @EventHandler
    public void onInventoryEvent(InventoryInteractEvent event) {
Logger.getGlobal().info("InventoryInteractEvent!");
        checkItems(event.getInventory().iterator());
        checkItems(event.getView().getPlayer().getInventory().iterator());
    }
    
    @EventHandler
    public void onInventoryEvent(InventoryOpenEvent event) {
Logger.getGlobal().info("InventoryOpenEvent!");
        checkItems(event.getInventory().iterator());
        checkItems(event.getView().getPlayer().getInventory().iterator());
    }
    
    @EventHandler
    public void onInventoryEvent(InventoryEvent event) {
Logger.getGlobal().info("InventoryEvent!");
        checkItems(event.getInventory().iterator());
    }
    
    @EventHandler
    public void onInventoryEvent(InventoryCreativeEvent event) {
Logger.getGlobal().info("InventoryCreativeEvent!");
        //checkItems(event.getInventory().iterator());
        //checkItems(event.getView().getPlayer().getInventory().iterator());
        //checkItems(event.getWhoClicked().getInventory().iterator());
        checkItem(event.getCurrentItem());
        checkItem(event.getCursor());
        int slot = event.getSlot();
        Inventory inventory = event.getInventory();
        new BukkitRunnable() {
            @Override
            public void run() {
                checkItem(inventory.getItem(slot));
            }
        }.runTaskLater(ArchitectPlugin.getPluginInstance(),2);
    }
    
    */

}
