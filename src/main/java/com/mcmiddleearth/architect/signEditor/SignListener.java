/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.mcmiddleearth.architect.signEditor;

import com.mcmiddleearth.architect.Modules;
import com.mcmiddleearth.architect.Permission;
import com.mcmiddleearth.architect.PluginData;
import com.mcmiddleearth.pluginutil.EventUtil;
import com.mcmiddleearth.pluginutil.message.FancyMessage;
import com.mcmiddleearth.pluginutil.message.MessageType;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.Sign;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;

/**
 *
 * @author Eriol_Eandur
 */
public class SignListener implements Listener {
    
    @EventHandler
    public void playerInteract(PlayerInteractEvent event) {
        if(!event.getAction().equals(Action.LEFT_CLICK_BLOCK)) {
            return;
        }
        Player player = event.getPlayer();
        Block block = event.getClickedBlock();
        if(!(block.getState() instanceof Sign 
                && player.getInventory().getItemInHand().getType().equals(Material.STICK)
                && EventUtil.isMainHandEvent(event))) {
            return;
        }
        if(!PluginData.isModuleEnabled(block.getWorld(),Modules.SIGN_EDITOR)) {
            sendNotEnabledErrorMessage(player);
            return;
        }   
        if(PluginData.checkBuildPermissions(player,block.getLocation(),
                                        Permission.SIGN_EDITOR)) {
            SignEditorData.putEditor(event.getPlayer(),block);
            SignEditorData.sendSignMessage(event.getPlayer());
        }
        event.setCancelled(true);
    }

    private void sendNotEnabledErrorMessage(Player player) {
        PluginData.getMessageUtil().sendErrorMessage(player, "Sign editor is not enabled for this world.");
    }
        
}