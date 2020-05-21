/*
 * Copyright (C) 2017 MCME
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
package com.mcmiddleearth.architect.specialBlockHandling.listener;

import com.mcmiddleearth.architect.ArchitectPlugin;
import com.mcmiddleearth.architect.Modules;
import com.mcmiddleearth.architect.PluginData;
import com.mcmiddleearth.architect.watcher.WatchedListener;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.Furnace;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.inventory.FurnaceSmeltEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.FurnaceInventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;

/**
 *
 * @author Eriol_Eandur
 */
public class FurnaceListener extends WatchedListener{
    
    /**
     * If module BURNING_FURNACE is enabled in world config file
     * prolongs buring time of furnaces every time a smelting item is finished.
     * Also a new smelting item is placed in the furnace. This keeps furnaces burning
     * until the smelting item is taken out of the furnace by a player.
     * @param event 
     */
    @EventHandler
    public void furnaceProlongBurning(FurnaceSmeltEvent event) {
        if (!PluginData.isModuleEnabled(event.getBlock().getWorld(), Modules.BURNING_FURNACE)) {
            return;
        }
        final Block block = event.getBlock();
        final Material smelting = ((Furnace) block.getState()).getInventory().getSmelting().getType();
        final Furnace furnace = (Furnace) block.getState();
        furnace.setBurnTime(Short.MAX_VALUE);
        furnace.getBlock().setBlockData(furnace.getBlockData(), false);//.update(true, false);
//Logger.getLogger(this.getClass().getName()).info("smelting "+ smelting.toString());
        new BukkitRunnable() {
            @Override
            public void run() {
                FurnaceInventory inventory = furnace.getInventory();
                inventory.setResult(new ItemStack(Material.AIR));
                inventory.setSmelting(new ItemStack(smelting));
                inventory.setFuel(new ItemStack(Material.COAL));
                new BukkitRunnable() {
                    @Override
                    public void run() {
//Logger.getLogger(this.getClass().getName()).info("burnTime "+ ((Furnace)block.getState()).getBurnTime());
                    }
                }.runTaskLater(ArchitectPlugin.getPluginInstance(), 1);
            }
        }.runTaskLater(ArchitectPlugin.getPluginInstance(), 1);
    }
    
    @EventHandler
    public void furnaceOnOff(InventoryClickEvent event) {
        Location loc = event.getInventory().getLocation();
        if (loc == null || !PluginData.isModuleEnabled(loc.getWorld(), Modules.BURNING_FURNACE)) {
            return;
        }
        InventoryHolder holder = event.getInventory().getHolder();
        if(holder instanceof Furnace && event.getSlot()==0 && event.getResult().equals(Event.Result.ALLOW)) {
            event.setCancelled(true);
            final Furnace furnace = (Furnace) holder;
            ItemStack current = event.getCurrentItem();
            ItemStack cursor = event.getCursor();
            ItemStack smelting = new ItemStack(Material.COD);
            ItemStack fuel = new ItemStack(Material.COAL);
            if(current.getType().equals(Material.AIR)) {
                //event.setCurrentItem(new ItemStack(Material.RAW_FISH));
                furnace.setBurnTime(Short.MAX_VALUE);
                if(isSmeltingItem(cursor)) {
                    smelting = cursor;
                }
                //furnace.getInventory().setSmelting(new ItemStack(Material.RAW_FISH));
                //furnace.getInventory().setFuel(new ItemStack(Material.COAL));
            } else {
                //event.setCurrentItem(new ItemStack(Material.AIR));
                furnace.setBurnTime((short)-1);
                
                smelting = new ItemStack(Material.AIR);
                fuel = new ItemStack(Material.AIR);
            }
            furnace.getBlock().setBlockData(furnace.getBlockData(), false);//.update(true,false);
            final ItemStack finalSmelting = smelting;
            final ItemStack finalFuel = fuel;
            new BukkitRunnable() {
                @Override
                public void run() {
                    furnace.getInventory().setSmelting(finalSmelting);
                    furnace.getInventory().setFuel(finalFuel);
                }
            }.runTaskLater(ArchitectPlugin.getPluginInstance(), 1);
//Logger.getLogger(this.getClass().getName()).info("burn "+ furnace.getBurnTime());
//Logger.getGlobal().info("burn "+ furnace.getBurnTime());
/*Logger.getGlobal().info("slot type "+ event.getSlotType());            
Logger.getGlobal().info("slot  "+ event.getSlot());            
Logger.getGlobal().info("slot raw "+ event.getRawSlot());            
Logger.getGlobal().info("click "+ event.getClick());            
Logger.getGlobal().info("result "+ event.getResult());            
Logger.getGlobal().info("action "+ event.getAction());            
Logger.getGlobal().info("current "+ event.getCurrentItem());            
Logger.getGlobal().info("cursor "+ event.getCursor());
*/
        }
    }
    
    private boolean isSmeltingItem(ItemStack item) {
        switch(item.getType()) {
            case COD:
            case TROPICAL_FISH:
            case SALMON:
            case CHICKEN:
            case RABBIT:
            case PORKCHOP:
            case MUTTON:
                return true;
            default: 
                return false;
       }
    }
}
