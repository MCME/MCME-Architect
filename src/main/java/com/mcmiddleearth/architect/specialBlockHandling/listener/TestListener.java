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

import java.util.logging.Logger;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCreativeEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;


/**
 *
 * @author Eriol_Eandur
 */
public class TestListener implements Listener{
    
    /*@EventHandler
    public void dropItem(PlayerDropItemEvent event) {
        Logger.getGlobal().info("dropItem");
    }
    @EventHandler
    public void click(InventoryClickEvent event) {
        Logger.getGlobal().info("click "+event.getClick()+" "+event.getHotbarButton());
    }
    @EventHandler
    public void onInventoryClickEvent(InventoryClickEvent event) {
        Logger.getGlobal().info("InventoryClick action: "+event.getAction());
        Logger.getGlobal().info("InventoryClick clickType: "+event.getClick());
    }
    
    @EventHandler
    public void onInventoryCreativeEvent(InventoryCreativeEvent event) {
        Logger.getGlobal().info("InventoryClick action: "+event.getAction());
        Logger.getGlobal().info("InventoryClick clickType: "+event.getClick());
    }
    
    @EventHandler(priority=EventPriority.LOWEST)  
    public void openSpecialInventoryEvent(PlayerSwapHandItemsEvent event) {
        Logger.getGlobal().info("InventoryClick sneak: "+event.getPlayer().isSneaking());
        Logger.getGlobal().info("InventoryClick sprint: "+event.getPlayer().isSprinting());
    }*/
    
    
}
