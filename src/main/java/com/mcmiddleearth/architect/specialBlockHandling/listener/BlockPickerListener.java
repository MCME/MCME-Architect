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
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.util.RayTraceResult;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 *
 * @author Eriol_Eandur
 */
public class BlockPickerListener implements Listener {

    private static final String placeholder = "#";
    private static final int HOTBAR_SIZE = 9;

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void pickBlock(PlayerPickItemEvent event) {
        if(!PluginData.isModuleEnabled(event.getPlayer().getWorld(), Modules.SPECIAL_BLOCKS_FLINT)) {
            return;
        }
        Player player = event.getPlayer();
        if(player.getInventory().getItemInMainHand().getType().equals(Material.FLINT)) {
            return;
        }
        FluidCollisionMode mode = player.isSneaking() ? FluidCollisionMode.ALWAYS : FluidCollisionMode.NEVER;
        RayTraceResult result = player.getWorld().rayTrace(player.getEyeLocation(),
                                player.getLocation().getDirection(),
                                6, mode,false,0.01,
                                entity -> entity instanceof Hanging,
                                block -> !block.isEmpty());
        if(result == null || isIgnoredBlock(result.getHitBlock())) {
            return;
        } else if (result.getHitBlock() != null) {
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
            if (getBlockItemPick(player, result.getHitBlock(), rpName, true)) {
                event.setCancelled(true);
            }
        } else if(result.getHitEntity() != null) {
            if (getEntityItemPick(player, result.getHitEntity(), true)) {
                event.setCancelled(true);
            }
        }
    }

    private boolean isIgnoredBlock(Block block) {
        return block != null
                && (block.getType().equals(Material.CHEST)
                || block.getType().equals(Material.TRAPPED_CHEST)
                || block.getType().equals(Material.ENDER_CHEST)
                || block.getType().equals(Material.CHEST));
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
        if(getBlockItemPick(event.getPlayer(), block, rpName, false)) {
            event.setCancelled(true);
        }
    }

    private boolean getEntityItemPick(Player player, Entity entity, boolean changeHandSlot) {
        ItemStack item = null;
        //Logger.getGlobal().info(entity.getAsString());
        if(entity instanceof Painting) {
            item = ItemStack.of(Material.PAINTING);
        } else if(entity instanceof ItemFrame itemFrame) {
            //Logger.getGlobal().info("Glow");
            if(!itemFrame.getItem().getType().equals(Material.AIR)) {
                item = itemFrame.getItem();
            } else if(itemFrame instanceof GlowItemFrame) {
                item = ItemStack.of(Material.GLOW_ITEM_FRAME);
            } else {
                item = ItemStack.of(Material.ITEM_FRAME);
            }
        }
        if(item != null) {
            placeItem(player, item, changeHandSlot);
            return true;
        }
        return false;
    }

    private boolean getBlockItemPick(Player player, Block block, String rpName, boolean changeHandSlot) {
        if(block.getType().equals(Material.PLAYER_HEAD)) {
            CustomHeadListener.getHead(player, block);
            return true;
        } else {
            ItemStack item = SpecialBlockInventoryData.getItem(block, rpName);
            if (item != null) {
                if (!player.isSneaking()) {
                    item = item.clone();
                    placeItem(player,item, changeHandSlot);
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

    private void placeItem(Player player, ItemStack item, boolean changeHandSlot) {
        ItemStack twoItems = item.clone();
        twoItems.setAmount(2);
        PlayerInventory inventory = player.getInventory();
        //todo: check if special block item already in hotbar!!! instead of simple inventory.first
        if(exactFirst(inventory, item) > -1 && exactFirst(inventory, item) < HOTBAR_SIZE) {
            //hotbar slot with just one item -> increase to two items and make active
            if(changeHandSlot) inventory.setHeldItemSlot(exactFirst(inventory, item));
            inventory.setItem(exactFirst(inventory, item),twoItems);
        } else if(exactFirst(inventory, twoItems) > -1 && exactFirst(inventory, twoItems) < HOTBAR_SIZE) {
            //hotbar slot with two items -> active slot
            if(changeHandSlot) inventory.setHeldItemSlot(exactFirst(inventory, twoItems));
        } else if(inventory.getItemInMainHand().isEmpty()) {
            //mainhand empty -> put there
            inventory.setItemInMainHand(twoItems);
        } else {
            //try to put in empty hotbar slot
            int firstEmpty = inventory.firstEmpty();
            if(firstEmpty > -1 && firstEmpty < HOTBAR_SIZE) {
                inventory.setItem(firstEmpty, twoItems);
                if(changeHandSlot) inventory.setHeldItemSlot(firstEmpty);
                return;
            }

            //replace item in main hand
            if(changeHandSlot) inventory.setItemInMainHand(twoItems);
        }
    }

    private int exactFirst(PlayerInventory inventory, ItemStack item) {
        for(int i = 0; i < HOTBAR_SIZE; i++) {
            ItemStack barItem = inventory.getItem(i);
            if(barItem!= null && item.getType().equals(barItem.getType())) {
                if(item.getAmount() != barItem.getAmount()) {
                    return -1;
                }
                ItemMeta itemMeta = item.getItemMeta();
                ItemMeta barItemMeta = barItem.getItemMeta();
                if(barItemMeta == null && itemMeta!= null) {
                    return i;
                } else if(barItemMeta != null && itemMeta != null) {
                    if(itemMeta.getAsString().equals(barItemMeta.getAsString())) {
                        return i;
                    }
                }
            }
        }
        return -1;
    }

    private Map<UUID, String> selectedBlockData = new HashMap<>();

    @EventHandler
    private void blockInfo(PlayerInteractEvent event) {
        if(!PluginData.isModuleEnabled(event.getPlayer().getWorld(), Modules.SPECIAL_BLOCKS_FLINT)) {
            return;
        }
        if((event.getAction().equals(Action.LEFT_CLICK_BLOCK) || event.getAction().equals(Action.LEFT_CLICK_AIR))
                && event.getHand().equals(EquipmentSlot.HAND)
                && event.getPlayer().getInventory().getItemInMainHand().getType().equals(Material.FLINT)) {
            event.setCancelled(true);
            Block block = (event.getClickedBlock()!=null?
                    event.getClickedBlock():event.getPlayer().getTargetBlock(null, 1000));
            Player player = event.getPlayer();
            if(player.isSneaking()) {
                if(event.getAction().equals(Action.LEFT_CLICK_BLOCK)) {
                    String previousSelection = selectedBlockData.get(player.getUniqueId());
                    if(previousSelection != null) {
                        selectedBlockData.put(player.getUniqueId(), previousSelection+","
                                +block.getBlockData().getAsString().replace("minecraft:",""));
                    } else {
                        selectedBlockData.put(player.getUniqueId(),
                                block.getBlockData().getAsString().replace("minecraft:",""));
                    }
                } else if(event.getAction().equals(Action.LEFT_CLICK_AIR)) {
                    String preSet = WeSelectCommand.getWeSelect(player.getUniqueId(),true);
                    String selection = selectedBlockData.get(event.getPlayer().getUniqueId());
                    if(selection == null) {
                        selection = block.getBlockData().getAsString();
                    }
                    if(preSet.contains(placeholder)){
                        preSet = preSet.replace(placeholder,selection);
                        FancyMessage message = new FancyMessage(MessageType.INFO, PluginData.getMessageUtil())
                                .addFancy(selection,preSet,"Click to suggest selected WE command!");
                        message.send(player);
                    } else if(!preSet.isEmpty()){
                        FancyMessage message = new FancyMessage(MessageType.INFO, PluginData.getMessageUtil())
                                .addFancy(selection, preSet+" "+selection,
                                        "Click to suggest selected WE command!");
                        message.send(player);
                    } else {
                        FancyMessage message = new FancyMessage(MessageType.INFO, PluginData.getMessageUtil())
                                .addFancy(selection, selection,"Click to copy to clipboard!");
                        message.send(player);
                    }
                    selectedBlockData.put(player.getUniqueId(),null);
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
