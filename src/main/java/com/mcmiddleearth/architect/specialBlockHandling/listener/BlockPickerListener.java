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
import com.mcmiddleearth.architect.additionalCommands.WeSelectCommand;
import com.mcmiddleearth.architect.blockData.BlockDataManager;
import com.mcmiddleearth.architect.chunkUpdate.ChunkUpdateUtil;
import com.mcmiddleearth.architect.customHeadManager.CustomHeadListener;
import com.mcmiddleearth.architect.serverResoucePack.RpManager;
import com.mcmiddleearth.architect.specialBlockHandling.data.SpecialBlockInventoryData;
import com.mcmiddleearth.pluginutil.EventUtil;
import com.mcmiddleearth.pluginutil.message.FancyMessage;
import com.mcmiddleearth.pluginutil.message.MessageType;
import io.papermc.paper.event.player.PlayerPickItemEvent;
import org.bukkit.ChatColor;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.RayTraceResult;

import java.util.List;
import java.util.logging.Logger;

/**
 *
 * @author Eriol_Eandur
 */
public class BlockPickerListener implements Listener {

    private static final String placeholder = "#";

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void pickBlock(PlayerPickItemEvent event) {
        if(!PluginData.isModuleEnabled(event.getPlayer().getWorld(), Modules.SPECIAL_BLOCKS_FLINT)) {
            return;
        }
        Player player = event.getPlayer();
        FluidCollisionMode mode = player.isSneaking() ? FluidCollisionMode.ALWAYS : FluidCollisionMode.NEVER;
        RayTraceResult result = player.getWorld().rayTrace(player.getEyeLocation(),
                                player.getLocation().getDirection(),
                                4, mode,false,0.01,
                                entity -> entity instanceof Hanging,
                                block -> !block.isEmpty());
        if(result != null && result.getHitBlock() != null) {
            String rpName = RpManager.getCurrentRpName(event.getPlayer());
            if (!player.getInventory().getItemInMainHand().isEmpty()) {
                String rpItemName = RpManager.getCurrentRpName(event.getPlayer());
                if (!rpItemName.isEmpty()) {
                    rpName = rpItemName;
                }
            }
            if (rpName.isEmpty()) {
                PluginData.getMessageUtil().sendErrorMessage(event.getPlayer(), "Your resource pack could not be determined. You might not get correct block picks.");
            }
            if (getBlockItemPick(player, result.getHitBlock(), rpName)) {
                event.setCancelled(true);
            }
        } else if(result != null && result.getHitEntity() != null) {
            if (getEntityItemPick(player, result.getHitEntity())) {
                event.setCancelled(true);
            }
        }
    }

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
                     || !SpecialBlockInventoryData.getRpName(event.getPlayer().getInventory().getItemInMainHand()).isEmpty())
                || !EventUtil.isMainHandEvent(event)) {
            return;
        }
        Block block =  (event.getClickedBlock()!=null?
                        event.getClickedBlock():event.getPlayer().getTargetBlock(null, 1000));
        ItemStack handItem = event.getPlayer().getInventory().getItemInMainHand();
        String rpName = "";
        if(handItem.getType().equals(Material.FLINT)) {
            rpName = RpManager.getCurrentRpName(event.getPlayer());
            if(rpName.isEmpty()) {
                PluginData.getMessageUtil().sendErrorMessage(event.getPlayer(),"Your resource pack could not be determined. If you clicked on a special MCME block you will get a block from mc creative inventory instead.");
            }
        } else {
            rpName = SpecialBlockInventoryData.getRpName(handItem);
        }
        if(getBlockItemPick(event.getPlayer(), block, rpName)) {
            event.setCancelled(true);
        }
    }

    private boolean getEntityItemPick(Player player, Entity entity) {
        ItemStack item = null;
        //Logger.getGlobal().info(entity.getAsString());
        if(entity instanceof Painting) {
            item = ItemStack.of(Material.PAINTING);
        } else if(entity instanceof GlowItemFrame) {
            //Logger.getGlobal().info("Glow");
            item = ItemStack.of(Material.GLOW_ITEM_FRAME);
        } else if(entity instanceof ItemFrame) {
            //Logger.getGlobal().info("item");
            item = ItemStack.of(Material.ITEM_FRAME);
        }
        if(item != null) {
            if (player.getInventory().getItemInMainHand().isEmpty()) {
                player.getInventory().setItemInMainHand(item);
            } else {
                player.getInventory().addItem(item);
            }
            return true;
        }
        return false;
    }

    private boolean getBlockItemPick(Player player, Block block, String rpName) {
        if(block.getType().equals(Material.PLAYER_HEAD)) {
            CustomHeadListener.getHead(player, block);
            return true;
        } else {
            ItemStack item = SpecialBlockInventoryData.getItem(block, rpName);
            if (item != null) {
                if (!player.isSneaking()) {
                    item = item.clone();
                    item.setAmount(2);
                    if(player.getInventory().getItemInMainHand().isEmpty()) {
                        player.getInventory().setItemInMainHand(item);
                    } else {
                        player.getInventory().addItem(item);
                    }
                } else if (item.hasItemMeta()) {
                    if (!SpecialBlockInventoryData.openInventory(player, item)) {
                        InventoryListener.sendNoInventoryError(player, rpName);
                    }
                }
                return true;
            }
        }
        return false;
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
                String preSet = WeSelectCommand.getWeSelect(player.getUniqueId(),true);
                if(preSet.contains(placeholder)){
                    preSet = preSet.replace(placeholder,block.getBlockData().getAsString());
                    FancyMessage message = new FancyMessage(MessageType.INFO, PluginData.getMessageUtil())
                            .addClickable(block.getBlockData().getAsString(),preSet);
                    message.send(player);
                }else{
                    FancyMessage message = new FancyMessage(MessageType.INFO, PluginData.getMessageUtil())
                            .addClickable(block.getBlockData().getAsString(), preSet+" "+block.getBlockData().getAsString());
                    message.send(player);
                }
            } else {
                String preSet = WeSelectCommand.getWeSelect(player.getUniqueId(),false);
                List<String> info = new BlockDataManager().getBlockInfo(block.getBlockData(),block.getData());
                PluginData.getMessageUtil().sendInfoMessage(player, "Data for block at ("+ChatColor.GREEN
                        +block.getLocation().getBlockX()+", "
                        +block.getLocation().getBlockY()+", "
                        +block.getLocation().getBlockZ()+ChatColor.AQUA+")");
                if(preSet.contains("#")){
                    preSet = preSet.replace(placeholder,block.getBlockData().getAsString());
                    for(String line: info) {
                        new FancyMessage(MessageType.INFO,PluginData.getMessageUtil())
                                .addClickable(line, preSet)
                                .send(player);
                    }
                }else{
                    for(String line: info) {
                        new FancyMessage(MessageType.INFO,PluginData.getMessageUtil())
                                .addClickable(line, preSet+" "+block.getBlockData().getAsString())
                                .send(player);
                    }
                }
            }
            ChunkUpdateUtil.sendUpdates(block, player);
        }
    }
}
