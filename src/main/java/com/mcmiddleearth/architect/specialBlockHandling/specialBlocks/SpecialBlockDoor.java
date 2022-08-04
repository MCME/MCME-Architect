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
package com.mcmiddleearth.architect.specialBlockHandling.specialBlocks;

import com.mcmiddleearth.architect.ArchitectPlugin;
import com.mcmiddleearth.architect.specialBlockHandling.SpecialBlockType;
import com.mcmiddleearth.util.DevUtil;
import com.mcmiddleearth.util.DoorUtil;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.data.Bisected;
import org.bukkit.block.data.type.Door;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
// 1.13 remove import org.bukkit.material.Door;
import org.bukkit.scheduler.BukkitRunnable;

/**
 *
 * @author Eriol_Eandur
 */
public class SpecialBlockDoor extends SpecialBlock {
    
   private final boolean powered;
    private final boolean hingeRight;
    
    private SpecialBlockDoor(String id, 
                        Material material, boolean powered, boolean hingeRight) {
        super(id, material.createBlockData(), SpecialBlockType.DOOR);
        this.powered = powered;
        this.hingeRight = hingeRight;
    }
    
    protected SpecialBlockDoor(String id, 
                        Material material, boolean powered, boolean hingeRight,
                        SpecialBlockType type) {
        super(id, material.createBlockData(), type);
        this.powered = powered;
        this.hingeRight = hingeRight;
    }
    
    protected SpecialBlockDoor(String id, 
                        Material material, 
                        byte dataValue,
                        SpecialBlockType type) {
        super(id, material.createBlockData(), SpecialBlockType.DOOR);
        powered = false;
        hingeRight = false;
    }
    
    public static SpecialBlockDoor loadFromConfig(ConfigurationSection config, String id) {
        Material material = matchMaterial(config.getString("blockMaterial",""));
        if(material==null) {
            return null;
        }
        boolean powered = config.getBoolean("powered", false);
        boolean hingeRight = config.getBoolean("hingeRight", false);
        return new SpecialBlockDoor(id, material, powered, hingeRight);
    }
    
    @Override
    public void placeBlock(final Block blockPlace, final BlockFace blockFace, Block clicked,
                           final Location interactionPoint, final Player player) {
        final Location playerLoc = player.getLocation();
        placeDoor(blockPlace, playerLoc, getBlockData().getMaterial(), powered, false, hingeRight, false);
    }
    
    public void placeBlock(final Block blockPlace, final BlockFace blockFace, 
                           final Player player, boolean hingeRight) {
        final Location playerLoc = player.getLocation();
        placeDoor(blockPlace, playerLoc, getBlockData().getMaterial(), powered, false, hingeRight, false);
    }
    
    protected void placeDoor(final Block blockPlace, final Location playerLoc,
                             Material material, final boolean powered,
                             final boolean fixedHinge, final boolean hingeRight,
                             final boolean open) {
        final BlockState lowerState = blockPlace.getState();
        lowerState.setType(material);
        final BlockState upperState = blockPlace.getRelative(BlockFace.UP).getState();
        upperState.setType(material);
        new BukkitRunnable() {
            @Override
            public void run() {
                lowerState.getBlock().setBlockData(lowerState.getBlockData(),false);//update(true, false);
                upperState.getBlock().setBlockData(upperState.getBlockData(),false);//.update(true, false);
                DevUtil.log("4 door block place: ID "+lowerState.getType()+" - DV "+lowerState.getRawData());
                DevUtil.log("4 door block place: ID "+upperState.getType()+" - DV "+upperState.getRawData());
                final BlockState tempLowerState = lowerState.getBlock().getState();
                final BlockState tempUpperState = upperState.getBlock().getState();
                if(tempLowerState.getBlockData() instanceof Door && tempUpperState.getBlockData() instanceof Door) {
                    Door lowerDoorData = (Door) tempLowerState.getBlockData();
                    Door upperDoorData = (Door) tempUpperState.getBlockData();
                    upperDoorData.setHalf(Bisected.Half.TOP);
                    lowerDoorData.setHalf(Bisected.Half.BOTTOM);
                    float yaw = playerLoc.getYaw();
                    if(open) {
                        if(hingeRight) {
                            yaw -= 90;
                        } else {
                            yaw += 90;
                        }
                    }
                    final BlockFace facing = getBlockFace(yaw);//getOppositeBlockFace(getBlockFace(yaw));
                    lowerDoorData.setFacing(facing);
                    upperDoorData.setFacing(facing);
                    lowerDoorData.setOpen(open);
                    upperDoorData.setOpen(open);
                    lowerDoorData.setPowered(powered);
                    upperDoorData.setPowered(powered);
                    Door.Hinge hinge = (hingeRight?Door.Hinge.RIGHT:Door.Hinge.LEFT);
                    if(!fixedHinge) {
                        hinge = (checkForHingeRightSide(tempUpperState.getBlock(),facing, hingeRight)
                                 ?Door.Hinge.RIGHT:Door.Hinge.LEFT);
                    }
                    lowerDoorData.setHinge(hinge);
                    upperDoorData.setHinge(hinge);
                    //upperDoorData.setHinge(!hinge);
                    /* 1.13 removed if(powered && hinge) {
                        upperDoorData.setData((byte)10);
                    } else if(powered && !hinge) {
                        upperDoorData.setData((byte)11);
                    } else if(!powered && hinge) {
                        upperDoorData.setData((byte)8);
                    } else { 
                        upperDoorData.setData((byte)9);
                    }*/
                    tempLowerState.setBlockData(lowerDoorData);
                    tempUpperState.setBlockData(upperDoorData);
                    new BukkitRunnable() {
                        @Override
                        public void run() {
                            DevUtil.log("4 door block place x2: ID "+tempLowerState.getType()+" - DV "+tempLowerState.getRawData());
                            DevUtil.log("4 door block place x2: ID "+tempUpperState.getType()+" - DV "+tempUpperState.getRawData());
                            tempLowerState.getBlock().setBlockData(tempLowerState.getBlockData(),false);//.update(true, false);
                            tempUpperState.getBlock().setBlockData(tempUpperState.getBlockData(),false);//.update(true, false);
                        }
                    }.runTaskLater(ArchitectPlugin.getPluginInstance(), 1);
                }
            }
        }.runTaskLater(ArchitectPlugin.getPluginInstance(), 1);
    }
 
    private boolean checkForHingeRightSide(Block block, BlockFace facing, boolean hingeRight) {
//Logger.getGlobal().info("DoorBlockPlace hinge side right: "+hingeRight);
        Block leftBlock, rightBlock;
        switch(facing) {
            case NORTH: rightBlock = block.getRelative(BlockFace.EAST);
                     leftBlock = block.getRelative(BlockFace.WEST);break;
            case EAST: rightBlock = block.getRelative(BlockFace.SOUTH);
                     leftBlock = block.getRelative(BlockFace.NORTH); break;
            case SOUTH: rightBlock = block.getRelative(BlockFace.WEST);
                     leftBlock = block.getRelative(BlockFace.EAST); break;
            default: rightBlock = block.getRelative(BlockFace.NORTH);
                     leftBlock = block.getRelative(BlockFace.SOUTH);
        }
        if(hingeRight) {
            BlockState checkState = leftBlock.getState();
//    Logger.getGlobal().info("DoorBlockPlace leftState id "+checkState.getType());
            if(checkState.getBlockData() instanceof Door 
                    && ((Door)checkState.getBlockData()).getHinge().equals(Door.Hinge.RIGHT)) {
//   Logger.getGlobal().info("DoorBlockPlace leftState return true");
                return true;
            }
            checkState = rightBlock.getState();
//    Logger.getGlobal().info("DoorBlockPlace rightState id "+checkState.getType());
            if(checkState.getBlockData() instanceof Door 
                    && ((Door)checkState.getBlockData()).getHinge().equals(Door.Hinge.LEFT)) {
//  Logger.getGlobal().info("DoorBlockPlace rightState return false");
                return false;
            }
        }
        else {
            BlockState checkState = rightBlock.getState();
//  Logger.getGlobal().info("DoorBlockPlace rightState id "+checkState.getType());
            if(checkState.getBlockData() instanceof Door 
                    && ((Door)checkState.getBlockData()).getHinge().equals(Door.Hinge.RIGHT)) {
//  Logger.getGlobal().info("DoorBlockPlace rightState return false");
                return false;
            }
            checkState = leftBlock.getState();
//  Logger.getGlobal().info("DoorBlockPlace leftState id "+checkState.getType());
            if(checkState.getBlockData() instanceof Door 
                    && ((Door)checkState.getBlockData()).getHinge().equals(Door.Hinge.LEFT)) {
//  Logger.getGlobal().info("DoorBlockPlace leftState return true");
                return true;
            }
        }
//gger.getGlobal().info("DoorBlockPlace leftState return default.");
        return hingeRight;
    }
    
   @Override
    public boolean matches(Block block) {
        if(getBlockData().getMaterial().equals(block.getType())) {
           if(DoorUtil.isLowerDoorBlock(block)) {
               block = block.getRelative(BlockFace.UP);
               if(!DoorUtil.isUpperDoorBlock(block)) {
                   return false;
               }
           } else {
               if(!DoorUtil.isLowerDoorBlock(block.getRelative(BlockFace.DOWN))) {
                   return false;
               }
           }
           return powered == (block.getData()>9);
        }
        return false;
    }
    
}
