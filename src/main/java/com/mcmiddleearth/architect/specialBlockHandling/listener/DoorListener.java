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

import com.mcmiddleearth.architect.Modules;
import com.mcmiddleearth.architect.PluginData;
import com.mcmiddleearth.architect.specialBlockHandling.data.SpecialBlockInventoryData;
import com.mcmiddleearth.architect.specialBlockHandling.specialBlocks.SpecialBlock;
import com.mcmiddleearth.architect.specialBlockHandling.specialBlocks.SpecialBlockVanillaDoor;
import com.mcmiddleearth.architect.watcher.WatchedListener;
import com.mcmiddleearth.util.DevUtil;
import com.mcmiddleearth.util.DoorUtil;
import com.mcmiddleearth.util.TheGafferUtil;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.data.type.Door;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.MemoryConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockMultiPlaceEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.util.Objects;

/**
 *
 * @author Eriol_Eandur
 */
public class DoorListener extends WatchedListener{
    
    /**
     * If module HALF_DOORS is enabled in world config file
     * prevents players from opening half doors.
     * @param event 
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void noOpenHalfDoors(PlayerInteractEvent event) {
        if(event.getAction().equals(Action.RIGHT_CLICK_BLOCK)) {
            Player p = event.getPlayer();
            Material blockType = Objects.requireNonNull(event.getClickedBlock()).getType();
            if (DoorUtil.isDoor(blockType)) {
                if(PluginData.isModuleEnabled(event.getClickedBlock().getWorld(), Modules.HALF_DOORS)) {
                    DevUtil.log(2,"noOpenHalfDoors fired cancelled: " + event.isCancelled());
                    if(event.isCancelled()) {
                        return;
                    }
                    Block above = event.getClickedBlock().getRelative(BlockFace.UP);
                    if(!DoorUtil.isUpperDoorBlock(event.getClickedBlock()) && !(DoorUtil.isDoor(above.getType()) && DoorUtil.isUpperDoorBlock(above))){
                        event.setCancelled(true);
                    }
                }
            }
        }
    }
    
    /**
     * Opens double doors simulaneously.
     * @param event 
     */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void interactAdjacentDoors(PlayerInteractEvent event) {
        if(event.getAction().equals(Action.RIGHT_CLICK_BLOCK)
                && event.getHand().equals(EquipmentSlot.HAND)
                && DoorUtil.isDoorBlock(event.getClickedBlock())) {
            Block block = event.getClickedBlock();
            if(DoorUtil.isThinWall(block)) {
                return;
            }
            event.setCancelled(true);
            if(DoorUtil.isUpperDoorBlock(block)) {              //click at upper door part
                block = block.getRelative(BlockFace.DOWN);      //check if clicked block is part of full door
                if(!DoorUtil.isDoorBlock(block)) {
                    return;
                }
            } else {                                                           //click at lower door part
                if(!DoorUtil.isUpperDoorBlock(block.getRelative(BlockFace.UP))//check for half door
                    && !DoorUtil.isFullDoorAbove(block)) {                    //check for 3 block high door
                    return;
                }
            }
            if(DoorUtil.isFullDoorAbove(block)) {
                block = block.getRelative(BlockFace.UP);   //
            }
            boolean hingeRightOfClickedSide = ((Door)block.getRelative(BlockFace.UP)
                                               .getBlockData()).getHinge().equals(Door.Hinge.RIGHT);
            DoorUtil.toggleDoor(block);
            DoorUtil.toggleDoor(block.getRelative(BlockFace.UP));
            DoorUtil.toggleHalfDoor(block.getRelative(BlockFace.DOWN),
                                    hingeRightOfClickedSide,
                                    ((Door)block.getState().getBlockData()).isOpen());

            Block block2Lower = DoorUtil.getSecondHalf(block);
            Block block2Upper = block2Lower.getRelative(BlockFace.UP);
            BlockState block2LowerState = block2Lower.getState();
            BlockState block2UpperState = block2Upper.getState();
            if(DoorUtil.isDoorBlock(block2Lower) && DoorUtil.isDoorBlock(block2Upper) 
                && (((Door)block2LowerState.getBlockData()).getFacing()
                            .equals(((Door)block.getBlockData()).getFacing())) //check for same facing
                && (((Door)block2UpperState.getBlockData()).getHinge()           //check for opposite hinge
                       !=(((Door)block.getRelative(BlockFace.UP).getBlockData()).getHinge()))) {
                DoorUtil.toggleDoor(block2Lower);
                DoorUtil.toggleDoor(block2Upper);
                DoorUtil.toggleHalfDoor(block2Lower.getRelative(BlockFace.DOWN),
                                        !hingeRightOfClickedSide,
                                        ((Door)block2Lower.getBlockData()).isOpen());
            }
        }
    }
    
    /**
     * place powered doors for creative inventory door items
     * @param event
     */
    @EventHandler
    public void vanillaDoorPlace(BlockMultiPlaceEvent event) {
        if(!PluginData.isModuleEnabled(event.getPlayer().getWorld(), Modules.USE_POWERED_DOORS)
                || !event.getHand().equals(EquipmentSlot.HAND) 
                || event.getPlayer().getInventory().getItemInMainHand()==null
                || event.getPlayer().getInventory().getItemInMainHand().getType().equals(Material.AIR)) {
            return;
        }
        final Player player = event.getPlayer();
        for(BlockState state: event.getReplacedBlockStates()) {
            if(!TheGafferUtil.hasGafferPermission(player,state.getLocation())) {
                return;
            }
        }
        ItemStack handItem = player.getInventory().getItemInMainHand();
        SpecialBlock data = SpecialBlockInventoryData.getSpecialBlockDataFromItem(handItem);
        if(data==null) {
            Material material=null;
            boolean powered = true;
            switch(handItem.getType()) {
                case ACACIA_DOOR:
                    material = Material.ACACIA_DOOR;
                    break;
                case DARK_OAK_DOOR:
                    material = Material.DARK_OAK_DOOR;
                    break;
                case JUNGLE_DOOR:
                    material = Material.JUNGLE_DOOR;
                    break;
                case SPRUCE_DOOR:
                    material = Material.SPRUCE_DOOR;
                    break;
                case BIRCH_DOOR:
                    material = Material.BIRCH_DOOR;
                    powered = false;
                    break;
                case OAK_DOOR:
                    material = Material.OAK_DOOR;
                    break;
                case IRON_DOOR:
                    material = Material.IRON_DOOR;
                    powered = false;
                    break;
            }
            if(material==null) {
                return;
            }
            ConfigurationSection config = new MemoryConfiguration();
            config.set("blockMaterial", material.name());
            config.set("powered", powered);
            data = SpecialBlockVanillaDoor.loadFromConfig(config,"temp");
        }
        if(!(data instanceof SpecialBlockVanillaDoor)) {
            return;
        }
        event.setCancelled(true);
        Block blockPlace = event.getBlock();
        for(BlockState state: event.getReplacedBlockStates()) {
            if(state.getY()<blockPlace.getY()) {
                blockPlace = state.getBlock();
            }
        }
        
        boolean hingeRight = ((Door)event.getBlock()
                             .getBlockData()).getHinge().equals(Door.Hinge.RIGHT);
        ((SpecialBlockVanillaDoor)data).placeBlock(blockPlace, BlockFace.SELF, 
                                                   player, hingeRight);
    }
    
}
