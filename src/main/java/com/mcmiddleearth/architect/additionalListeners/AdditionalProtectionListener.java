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
import com.mcmiddleearth.architect.watcher.WatchedListener;
import com.mcmiddleearth.util.TheGafferUtil;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.data.type.Sign;
import org.bukkit.block.data.type.WallSign;
import org.bukkit.entity.Boat;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.hanging.HangingBreakByEntityEvent;
import org.bukkit.event.hanging.HangingPlaceEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;

/**
 *
 * @author Eriol_Eandur
 */
public class AdditionalProtectionListener extends WatchedListener{
    
    @EventHandler(priority=EventPriority.HIGH, ignoreCancelled = true)
    public void HangingBreak(HangingBreakByEntityEvent event) {
        if((!PluginData.isModuleEnabled(event.getEntity().getWorld(),Modules.HANGING_ENTITY_PROTECTION))) {
            return;
        }  
        if(!(event.getRemover() instanceof Player)) {
            event.setCancelled(true);
            return;
        }
        Player player = (Player) event.getRemover();
        if(!PluginData.checkBuildPermissions(player,event.getEntity().getLocation(),
                                       Permission.HANGING_ENTITY_EDITOR)) {
            event.setCancelled(true);
        }
    }
    
    @EventHandler(priority=EventPriority.HIGH, ignoreCancelled = true)
    public void HangingPlace(HangingPlaceEvent event) {
        if((!PluginData.isModuleEnabled(event.getEntity().getWorld(),Modules.HANGING_ENTITY_PROTECTION))) {
            return;
        }  
        Player player = event.getPlayer();
        if(!PluginData.checkBuildPermissions(player,event.getEntity().getLocation(),
                                       Permission.HANGING_ENTITY_EDITOR)) {
            event.setCancelled(true);
        }
    }
    
    @EventHandler(priority=EventPriority.HIGH, ignoreCancelled = true)
    public void PlayerInteractEntity(PlayerInteractEntityEvent event) {
        if(event.getRightClicked() instanceof ItemFrame) {
            if((!PluginData.isModuleEnabled(event.getPlayer().getWorld(),Modules.HANGING_ENTITY_PROTECTION))) {
                return;
            }  
            Player player = (Player) event.getPlayer();
            if(!PluginData.checkBuildPermissions(player,event.getRightClicked().getLocation(),
                                           Permission.HANGING_ENTITY_EDITOR)) {
                event.setCancelled(true);
            }
        }
    }
    
    @EventHandler(priority=EventPriority.HIGH, ignoreCancelled = true)
    public void EntityDamage(EntityDamageByEntityEvent event) {
        if(event.getEntity() instanceof ItemFrame) {
            if((!PluginData.isModuleEnabled(event.getEntity().getWorld(),Modules.HANGING_ENTITY_PROTECTION))) {
                return;
            }  
            if(!(event.getDamager() instanceof Player)) {
                event.setCancelled(true);
                return;
            }
            Player player = (Player) event.getDamager();
            if(!PluginData.checkBuildPermissions(player,event.getEntity().getLocation(),
                                         Permission.HANGING_ENTITY_EDITOR)) {
                event.setCancelled(true);
            }
        }
    }
    
   @EventHandler(priority=EventPriority.HIGH, ignoreCancelled = true)
    public void flowerPotProtection(PlayerInteractEvent event) {
        if(!isFlowerPot(event.getClickedBlock().getType())) {
            return;
        }
        Player player = event.getPlayer();
        Block block = event.getClickedBlock();
        if(!TheGafferUtil.checkGafferPermission(player, block.getLocation())) {
            event.setCancelled(true);
        }
    }
    
    private boolean isFlowerPot(Material type) {
        return type.equals(Material.FLOWER_POT) || type.name().startsWith("POTTED");
    }
    
   @EventHandler(priority=EventPriority.HIGH)
    public void dyeSignProtection(PlayerInteractEvent event) {
        if(event.getClickedBlock()!= null 
                && (event.getClickedBlock().getBlockData() instanceof Sign
                    || event.getClickedBlock().getBlockData() instanceof WallSign)) {
            Player player = event.getPlayer();
            Block block = event.getClickedBlock();
            if(!TheGafferUtil.checkGafferPermission(player, block.getLocation())) {
                event.setCancelled(true);
            }
        }
    }
    
    @EventHandler(priority=EventPriority.HIGH)
    public void blockTrampling(PlayerInteractEvent event) {
        if(event.getAction().equals(Action.PHYSICAL)
                && event.hasBlock()
                && event.getClickedBlock().getType().equals(Material.TURTLE_EGG)) {
            event.setCancelled(true);
        }
    }
    
    @EventHandler(priority=EventPriority.NORMAL) 
    public void entityInteract(EntityChangeBlockEvent event) {
        if(event.getEntity() instanceof Boat) {
            if((PluginData.isModuleEnabled(event.getEntity().getWorld(),Modules.LILY_PAD_PROTECTION))) {
                event.setCancelled(true);
            }  
        }
    }
    
    
}
