/*
 * Copyright (C) 2018 MCME
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

import com.mcmiddleearth.architect.Modules;
import com.mcmiddleearth.architect.PluginData;
import com.mcmiddleearth.architect.blockData.BlockDataManager;
import com.mcmiddleearth.architect.chunkUpdate.ChunkUpdateUtil;
import com.mcmiddleearth.architect.serverResoucePack.RpManager;
import com.mcmiddleearth.architect.specialBlockHandling.data.SpecialBlockInventoryData;
import com.mcmiddleearth.pluginutil.EventUtil;
import com.mcmiddleearth.pluginutil.message.FancyMessage;
import com.mcmiddleearth.pluginutil.message.MessageType;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.util.List;

/**
 *
 * @author Eriol_Eandur
 */
public class BlockPickerListener implements Listener {
    
    /**
     * If module SPECIAL_BLOCK_FLINT is enabled in world config file
     * gives a player a block in inventory when right-clicking the corresponding
     * block with stick in hand.
     * @param event 
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false) 
    public void flintBlock(PlayerInteractEvent event) {
        if(!PluginData.isModuleEnabled(event.getPlayer().getWorld(), Modules.SPECIAL_BLOCKS_FLINT)
                || !(event.getAction().equals(Action.RIGHT_CLICK_BLOCK)
                        || event.getAction().equals(Action.RIGHT_CLICK_AIR))
                || !(event.getPlayer().getInventory().getItemInMainHand().getType().equals(Material.FLINT)
                     || !SpecialBlockInventoryData.getRpName(event.getPlayer().getInventory().getItemInMainHand()).equals(""))
                || !EventUtil.isMainHandEvent(event)) {
            return;
        }
        Block block =  (event.getClickedBlock()!=null?
                        event.getClickedBlock():event.getPlayer().getTargetBlock(null, 1000));
        ItemStack handItem = event.getPlayer().getInventory().getItemInMainHand();
        String rpName = "";
        if(handItem.getType().equals(Material.FLINT)) {
            rpName = RpManager.getCurrentRpName(event.getPlayer());
            if(rpName.equals("")) {
                PluginData.getMessageUtil().sendErrorMessage(event.getPlayer(),"Your resource pack could not be determined. If you clicked on a special MCME block you will get a block from mc creative inventory instead.");
            }
        } else {
            rpName = SpecialBlockInventoryData.getRpName(handItem);
        }
        ItemStack item = SpecialBlockInventoryData.getItem(block, rpName);
        if(item!=null) {
            event.setCancelled(true);
            if(!event.getPlayer().isSneaking()) {
                item = item.clone();
                item.setAmount(2);
                event.getPlayer().getInventory().addItem(item);
            } else if(item.hasItemMeta()){
                if(!SpecialBlockInventoryData.openInventory(event.getPlayer(), item)) {
                    InventoryListener.sendNoInventoryError(event.getPlayer(),rpName);
                }
            }
        }
    }

    @EventHandler
    private void blockInfo(PlayerInteractEvent event) {
        if(!PluginData.isModuleEnabled(event.getPlayer().getWorld(), Modules.SPECIAL_BLOCKS_FLINT)) {
            return;
        }
        if((event.getAction().equals(Action.LEFT_CLICK_BLOCK) || event.getAction().equals(Action.LEFT_CLICK_AIR))
                && event.getHand().equals(EquipmentSlot.HAND)
                && event.getPlayer().getInventory().getItemInMainHand().getType().equals(Material.FLINT)) {
            event.setCancelled(true);
            Block block =  (event.getClickedBlock()!=null?
                    event.getClickedBlock():event.getPlayer().getTargetBlock(null, 1000));
            Player player = event.getPlayer();
            if(player.isSneaking()) {
                //PluginData.getMessageUtil().sendInfoMessage(player, block.getBlockData().getAsString());
                FancyMessage message = new FancyMessage(MessageType.INFO, PluginData.getMessageUtil())
                        .addClickable(block.getBlockData().getAsString(), block.getBlockData().getAsString());
                message.send(player);
            } else {
                List<String> info = new BlockDataManager().getBlockInfo(block.getBlockData(),block.getData());
                PluginData.getMessageUtil().sendInfoMessage(player, "Data for block at ("+ChatColor.GREEN
                                                    +block.getLocation().getBlockX()+", "
                                                    +block.getLocation().getBlockY()+", "
                                                    +block.getLocation().getBlockZ()+ChatColor.AQUA+")");
                for(String line: info) {
                    new FancyMessage(PluginData.getMessageUtil())
                            .addClickable(PluginData.getMessageUtil().infoNoPrefix()+line, block.getBlockData().getAsString())
                            .send(player);
                    //PluginData.getMessageUtil().sendIndentedInfoMessage(player, line);
                }
            }
            ChunkUpdateUtil.sendUpdates(block, player);
        }
    }

}
