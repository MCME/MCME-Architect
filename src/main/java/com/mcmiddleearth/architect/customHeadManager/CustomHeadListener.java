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
package com.mcmiddleearth.architect.customHeadManager;

import com.mcmiddleearth.architect.Modules;
import com.mcmiddleearth.architect.Permission;
import com.mcmiddleearth.architect.PluginData;
import com.mcmiddleearth.pluginutil.EventUtil;
import com.mcmiddleearth.util.HeadUtil;
import org.bukkit.Material;
import org.bukkit.SkullType;
import org.bukkit.block.Skull;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;

/**
 *
 * @author Eriol_Eandur
 */
public class CustomHeadListener implements Listener {

    //Moved to BlockPickerListener
    /*@EventHandler
    public void playerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        if(!(player.getInventory().getItemInHand().getType().equals(Material.STICK) 
                && EventUtil.isMainHandEvent(event))) {
            return;
        }
        if(!(event.hasBlock() && event.getClickedBlock().getType().equals(Material.PLAYER_HEAD))) {
                              //1.13 remove && ((Skull)event.getClickedBlock().getState()).getSkullType().equals(SkullType.PLAYER))) {
            return;
        }
        if(!PluginData.isModuleEnabled(player.getWorld(), Modules.CUSTOM_HEAD_MANAGER)) {
            sendNotActivatedMessage(player);
            return;
        }
        if(!PluginData.hasPermission(player,Permission.CUSTOM_HEAD_USER)) {
            PluginData.getMessageUtil().sendNoPermissionError(player);
            return;
        }
        ItemStack head = HeadUtil.pickCustomHead((Skull) event.getClickedBlock().getState());
        player.getInventory().addItem(head);
        PluginData.getMessageUtil().sendInfoMessage(player,"Given head: "
                  +PluginData.getMessageUtil().STRESSED+head.getItemMeta().getDisplayName());
    }

    private void sendNotActivatedMessage(Player player) {
        PluginData.getMessageUtil().sendErrorMessage(player,"Custom Heads are not enabled for this world.");
    }*/

}
