/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.mcmiddleearth.architect.paintingEditor;

import com.mcmiddleearth.architect.Modules;
import com.mcmiddleearth.architect.Permission;
import com.mcmiddleearth.architect.PluginData;
import com.mcmiddleearth.pluginutil.EventUtil;
import org.bukkit.Art;
import org.bukkit.Material;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Painting;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.hanging.HangingBreakByEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;

/**
 *
 * @author Eriol_Eandur
 */
public class PaintingListener implements Listener {
    
    @EventHandler
    public void playerInteractEntity(PlayerInteractEntityEvent event) {
        
        Player player = event.getPlayer();
        Entity entity = event.getRightClicked();
        if(!(entity instanceof Painting 
                && player.getInventory().getItemInHand().getType().equals(Material.STICK)
                && EventUtil.isMainHandEvent(event))) {
            return;
        }
        if(!PluginData.isModuleEnabled(entity.getWorld(),Modules.PAINTING_EDITOR)) {
            sendNotEnabledErrorMessage(player);
            return;
        }   
        if(PluginData.checkBuildPermissions(player,entity.getLocation(),
                                        Permission.PAINTING_EDITOR)) {
            Painting painting = (Painting) entity;
            int id = painting.getArt().getId();
            if(id<Art.values().length-1) {
                id++;
                painting.setArt(Art.getById(id));
            }
        }
        event.setCancelled(true);
    }

    @EventHandler
    public void hangingBreakByEntity(HangingBreakByEntityEvent event) {
        Entity damager = event.getRemover();
        Entity entity = event.getEntity();
        if(!(damager instanceof Player && entity instanceof Painting) ) {
            return;
        }
        Player player = (Player) damager;
        if(!PluginData.isModuleEnabled(entity.getWorld(),Modules.PAINTING_EDITOR)) {
            sendNotEnabledErrorMessage(player);
            return;
        }   
        if(!player.getItemInHand().getType().equals(Material.STICK)) {
            return;
        }
        if(PluginData.checkBuildPermissions(player,entity.getLocation(),
                                       Permission.PAINTING_EDITOR)) {
            Painting painting = (Painting) entity;
            int id = painting.getArt().getId();
            if(id>0) {
                id--;
                painting.setArt(Art.getById(id));
            }
        }
        event.setCancelled(true);
    }

    private void sendNotEnabledErrorMessage(Player player) {
        PluginData.getMessageUtil().sendErrorMessage(player, "Painting editor is not enabled for this world.");
    }
        
}