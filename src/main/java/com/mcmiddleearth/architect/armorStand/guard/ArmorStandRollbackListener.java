/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.mcmiddleearth.architect.armorStand.guard;

import com.mcmiddleearth.architect.Modules;
import com.mcmiddleearth.architect.Permission;
import com.mcmiddleearth.architect.PluginData;
import com.mcmiddleearth.architect.armorStand.ArmorStandEditorCommand;
import com.mcmiddleearth.architect.armorStand.ArmorStandEditorMode;
import org.bukkit.Material;
import org.bukkit.entity.ArmorStand;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 *
 * @author Eriol_Eandur
 */
public class ArmorStandRollbackListener implements Listener {

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerInteractAtEntity(PlayerInteractAtEntityEvent event) {
        if(event.getRightClicked() instanceof ArmorStand) {
            if(PluginData.isModuleEnabled(event.getPlayer().getWorld(),Modules.ARMOR_STAND_ROLLBACK)
                    && PluginData.hasPermission(event.getPlayer(),Permission.ARMOR_STAND_ROLLBACK)
                    && event.getPlayer().getItemInHand().equals(Material.STICK)
                    && ArmorStandEditorCommand
                            .getPlayerConfig(event.getPlayer())
                                .getEditorMode()
                                .equals(ArmorStandEditorMode.ROLLBACK)) {
                event.setCancelled(true);
                ArmorStandGuard.startSingleRollback(event.getPlayer(),
                                                    (ArmorStand)event.getRightClicked());
            }
        }
    }
    
    @EventHandler
    public void onPlayerLeave(PlayerQuitEvent event) {
        ArmorStandGuard.removeRollback(event.getPlayer());
    }
    
 }