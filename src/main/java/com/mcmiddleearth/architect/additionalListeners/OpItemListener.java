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
import com.mcmiddleearth.pluginutil.nms.AccessInventory;
import com.mcmiddleearth.pluginutil.nms.AccessNBT;
import org.bukkit.Bukkit;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.enchantments.Enchantment;
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
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.Iterator;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

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
//Logger.getGlobal().info("check item 1");
        if(item==null || item.getItemMeta() == null) return;
//Logger.getGlobal().info("check item 1a");
        ItemMeta itemMeta = item.getItemMeta();
        if(PluginData.isModuleEnabled(Bukkit.getWorlds().getFirst(), Modules.BLOCK_OP_ITEMS)) {
            Iterator<Map.Entry<Enchantment,Integer>> enchantments = itemMeta.getEnchants().entrySet().iterator();
            while(enchantments.hasNext()) {
                Map.Entry<Enchantment,Integer> entry = enchantments.next();
                String name = entry.getKey().getKey().getKey();
                int level = entry.getValue();
                if(!PluginData.isEnchantmentAllowed(name, level)) {
                    Logger.getGlobal().info("not allowed! "+name+" "+level);
                    itemMeta.removeEnchant(entry.getKey());
                }
            }
            if(itemMeta.getAttributeModifiers()!=null) {
                Iterator<Map.Entry<Attribute, AttributeModifier>> modifiers = itemMeta.getAttributeModifiers().entries().iterator();
                while(modifiers.hasNext()) {
                    modifiers.next();
                    modifiers.remove();
                }
            }
            item.setItemMeta(itemMeta);
            /*try {
                //Object nmsItem = NMSUtil.getCraftBukkitDeclaredField("inventory.CraftItemStack","handle",item);
                Object tag = AccessInventory.getItemNBT(item);//NMSUtil.invokeNMS("world.item.ItemStack", "t", new Class[]{}, nmsItem);
                if(AccessNBT.hasKey(tag, "Enchantments")) {
//Logger.getGlobal().info("Has enchantment");
                    Object enchantments = AccessNBT.getNBTBaseList(tag, "Enchantments"); //getTagList
                    for(int i = 0;
                            i < AccessNBT.size(//(int) NMSUtil.invokeNMS("nbt.NBTTagList", "size", new Class[]{},
                                    enchantments); i++) {
                        Object enchant = AccessNBT.getCompoundFromList(enchantments, i);//NMSUtil.invokeNMS("nbt.NBTTagList", "a"/*getCompound*,
                                //new Class[]{int.class}, enchantments, i);
                        String name = AccessNBT.getString(enchant,"id");
                        int level = AccessNBT.getInt(enchant,"lvl");
                        if(!PluginData.isEnchantmentAllowed(name, level)) {
                            Logger.getGlobal().info("not allowed! "+name+" "+level);
                            block(item);
                            return;
                        }
                    }
                }
                if(AccessNBT.hasKey(tag, "AttributeModifiers")) {
                    block(item);
                    return;
                }
            } catch (SecurityException | IllegalArgumentException | ClassNotFoundException ex) {
                Logger.getLogger(OpItemListener.class.getName()).log(Level.SEVERE, null, ex);
            }*/
        }
    }
    
    /*private void block(ItemStack item) {//Object nmsItem) {
        try {
            item.getItemMeta().getA
            NMSUtil.invokeNMS("world.item.ItemStack", "c"/*setTag*,
                    new Class[]{NMSUtil.getNMSClass("nbt.NBTTagCompound")}, nmsItem,(Object) null);
        } catch (ClassNotFoundException ex) {
            Logger.getLogger(OpItemListener.class.getName()).log(Level.SEVERE, null, ex);
        }
    }*/

}
