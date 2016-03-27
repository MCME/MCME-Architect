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
package com.mcmiddleearth.architect.additionalListeners;

import com.mcmiddleearth.architect.Modules;
import com.mcmiddleearth.architect.Permission;
import com.mcmiddleearth.architect.PluginData;
import com.mcmiddleearth.util.CommonMessages;
import java.util.logging.Logger;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.hanging.HangingBreakByEntityEvent;
import org.bukkit.event.hanging.HangingPlaceEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;

/**
 *
 * @author Eriol_Eandur
 */
public class HangingEntityProtectionListener implements Listener{
    
    @EventHandler(priority=EventPriority.HIGH, ignoreCancelled = true)
    public void HangingBreak(HangingBreakByEntityEvent event) {
        if(!(event.getRemover() instanceof Player) 
                || (!PluginData.isModuleEnabled(event.getEntity().getWorld(),Modules.HANGING_ENTITY_PROTECTION))) {
            return;
        }  
        Player player = (Player) event.getRemover();
        if(!PluginData.hasPermission(player,Permission.HANGING_ENTITY_EDITOR)) {
            event.setCancelled(true);
            CommonMessages.sendNoPermissionError(player);
        }
    }
    
    @EventHandler(priority=EventPriority.HIGH, ignoreCancelled = true)
    public void HangingPlace(HangingPlaceEvent event) {
        if((!PluginData.isModuleEnabled(event.getEntity().getWorld(),Modules.HANGING_ENTITY_PROTECTION))) {
            return;
        }  
        Player player = (Player) event.getPlayer();
        if(!PluginData.hasPermission(player,Permission.HANGING_ENTITY_EDITOR)) {
            event.setCancelled(true);
            CommonMessages.sendNoPermissionError(player);
        }
    }
    
    @EventHandler(priority=EventPriority.HIGH, ignoreCancelled = true)
    public void PlayerInteractEntity(PlayerInteractEntityEvent event) {
        if(event.getRightClicked() instanceof ItemFrame) {
            if((!PluginData.isModuleEnabled(event.getPlayer().getWorld(),Modules.HANGING_ENTITY_PROTECTION))) {
                return;
            }  
            Player player = (Player) event.getPlayer();
            if(!PluginData.hasPermission(player,Permission.HANGING_ENTITY_EDITOR)) {
                event.setCancelled(true);
                CommonMessages.sendNoPermissionError(player);
            }
        }
    }
    
    @EventHandler(priority=EventPriority.HIGH, ignoreCancelled = true)
    public void EntityDamage(EntityDamageByEntityEvent event) {
        if(event.getEntity() instanceof ItemFrame) {
            if(!(event.getDamager() instanceof Player) 
                    || (!PluginData.isModuleEnabled(event.getEntity().getWorld(),Modules.HANGING_ENTITY_PROTECTION))) {
                return;
            }  
            Player player = (Player) event.getDamager();
            if(!PluginData.hasPermission(player,Permission.HANGING_ENTITY_EDITOR)) {
                event.setCancelled(true);
                CommonMessages.sendNoPermissionError(player);
            }
        }
    }
    
}