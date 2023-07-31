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
package com.mcmiddleearth.architect.noPhysicsEditor;

import com.destroystokyo.paper.event.block.BlockDestroyEvent;
import com.mcmiddleearth.architect.ArchitectPlugin;
import com.mcmiddleearth.architect.Modules;
import com.mcmiddleearth.architect.PluginData;
import com.mcmiddleearth.architect.watcher.WatchedListener;
import com.mcmiddleearth.pluginutil.BlockUtil;
import com.mcmiddleearth.util.DevUtil;
import com.mcmiddleearth.util.TheGafferUtil;
import io.papermc.paper.event.entity.EntityInsideBlockEvent;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.MultipleFacing;
import org.bukkit.block.data.Waterlogged;
import org.bukkit.block.data.type.*;
import org.bukkit.block.data.type.RedstoneWire.Connection;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPhysicsEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.BlockRedstoneEvent;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.logging.Logger;

/**
 *
 * @author Eriol_Eandur
 */
public class NoPhysicsListener extends WatchedListener{

    private static final BlockFace[] allowedWallFaces = new BlockFace[]{BlockFace.NORTH,BlockFace.WEST,BlockFace.SOUTH,BlockFace.EAST};

    @EventHandler
    private void blockDestroy(BlockDestroyEvent event) {
        if(PluginData.isModuleEnabled(event.getBlock().getWorld(), Modules.NO_PHYSICS_LIST_ENABLED)
                && NoPhysicsData.isNoPhysicsBlock(event.getBlock())
                && !NoPhysicsData.hasNoPhysicsException(event.getBlock())) {
            DevUtil.log(4,"no Destroy "+event.getBlock().getType().name()+" "+event.getBlock().getX()+" "+event.getBlock().getY()+" "+event.getBlock().getZ()+" "+event.getNewState());
            event.setCancelled(true);
        } else {
            DevUtil.log(4,"allow Destroy "+event.getBlock().getType().name()+" "+event.getBlock().getX()+" "+event.getBlock().getY()+" "+event.getBlock().getZ()+" From: "
                    +event.getBlock().getType()+" To: "+event.getNewState());
        }
    }

    @EventHandler
    private void noPlayerInteraction(EntityInsideBlockEvent event) {
        if(PluginData.isModuleEnabled(event.getBlock().getWorld(), Modules.NO_PHYSICS_LIST_ENABLED)
            && event.getBlock().getType().equals(Material.TRIPWIRE)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    private void noPhysicsList(BlockPhysicsEvent event) {
        if(PluginData.isModuleEnabled(event.getBlock().getWorld(), Modules.NO_PHYSICS_LIST_ENABLED)
                && NoPhysicsData.isNoPhysicsBlock(event.getBlock())
                && !NoPhysicsData.hasNoPhysicsException(event.getBlock())) {
            DevUtil.log(4,"no Physics "+event.getBlock().getType().name()+" "+event.getBlock().getX()+" "+event.getBlock().getY()+" "+event.getBlock().getZ()+" "+event.getChangedType());
            event.setCancelled(true);
            // following code causes the server to set the tripwire all the time and crashes
            //-> use EntityInsideBlockEvent!
            /*if(event.getBlock().getBlockData() instanceof Tripwire) {
                Block block = event.getBlock();
                BlockData data = block.getBlockData();
                DevUtil.log(4,"tripwire powered: "+((Tripwire)data).isPowered());
                new BukkitRunnable() {
                    public void run() {
                        event.getBlock().setBlockData(data,true); //no crash with false
                        DevUtil.log(4,"reset tripwire: "+event.getBlock().getType().name()+" "+event.getBlock().getX()+" "+event.getBlock().getY()+" "+event.getBlock().getZ()+" "+event.getChangedType());
                    }
                }.runTaskLater(ArchitectPlugin.getPluginInstance(),10);
            }*/
            //end server crashing code
        } else {
            DevUtil.log(4,"allow Physics "+event.getBlock().getType().name()+" "+event.getBlock().getX()+" "+event.getBlock().getY()+" "+event.getBlock().getZ()+" From: "
                    +event.getBlock().getType()+" To: "+event.getChangedType()+" Source: "+event.getSourceBlock().getType()+" "+event.getSourceBlock().getX()+" "+event.getSourceBlock().getY()+" "+event.getSourceBlock().getZ()+" ");
        }            
    }
    
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = false)
    private void onPlaceNoPhysicsListBlock(BlockPlaceEvent event) {
//Logger.getGlobal().info("Cancelled: "+event.isCancelled()+" can build: "+event.canBuild());
        if(event.canBuild() 
                && TheGafferUtil.hasGafferPermission(event.getPlayer(),event.getBlock().getLocation())) {
//Logger.getGlobal().info("connect");
            connectNoPhysicsBlocks(event.getBlock());
        }
    }

    @EventHandler
    private void onRedstoneChange(BlockRedstoneEvent event) {
        event.setNewCurrent(event.getOldCurrent());
    }

    public static void connectNoPhysicsBlocks(Block block) {
        /*if(PluginData.isModuleEnabled(block.getWorld(), Modules.NO_PHYSICS_LIST_ENABLED)
                && NoPhysicsData.isNoPhysicsBlock(block)
                && !NoPhysicsData.hasNoPhysicsException(block)) {*/
            DevUtil.log(4,"place no physics block"+block.getType().name()+" "+block.getX()+" "+block.getZ());
            if(PluginData.getNoConnection(block)) {
                return;
            }
            if(((block.getBlockData() instanceof Fence)
                    && PluginData.isModuleEnabled(block.getWorld(), Modules.NO_PHYSICS_CONNECT_FENCES))
               || ((block.getBlockData() instanceof GlassPane)
                    && PluginData.isModuleEnabled(block.getWorld(), Modules.NO_PHYSICS_CONNECT_GLASS))) {
                MultipleFacing data = (MultipleFacing) block.getBlockData();
                for (BlockFace face : data.getAllowedFaces()) {
                    Block neighbour = block.getRelative(face);
                    if (neighbour.getBlockData().getClass().equals(data.getClass())) {
                        MultipleFacing neighbourData = (MultipleFacing) neighbour.getBlockData();
                        neighbourData.setFace(BlockUtil.rotateBlockFace(face, 2), true);
                        neighbour.setBlockData(neighbourData, false);
                    }
                }
            } else if(((block.getBlockData() instanceof Wall)
                    && PluginData.isModuleEnabled(block.getWorld(), Modules.NO_PHYSICS_CONNECT_WALLS))) {
                for(BlockFace face: allowedWallFaces) {
                    Block neighbour = block.getRelative(face);
                    if(neighbour.getBlockData() instanceof Wall) {
                        Wall neighbourData = (Wall)neighbour.getBlockData();
                        neighbourData.setHeight(BlockUtil.rotateBlockFace(face,2), Wall.Height.LOW);
                        neighbourData.setUp(getVanillaUp(neighbourData));
                        neighbour.setBlockData(neighbourData,false);
                    }
                }
            } else if ((block.getBlockData() instanceof Chest)
                && PluginData.isModuleEnabled(block.getWorld(), Modules.NO_PHYSICS_CONNECT_CHESTS)) {
                Chest chest = (Chest) block.getBlockData();
                BlockFace face = chest.getFacing();
                Block neighbour = block.getRelative(rotateRight(face));
                if(canBecomeDoubleChest(neighbour, block.getType())) {
                        Chest neighbourChest = ((Chest)neighbour.getBlockData());
                        neighbourChest.setType(Chest.Type.RIGHT);
                        neighbour.setBlockData(neighbourChest, false);
                } else {
                    neighbour = block.getRelative(rotateLeft(face));
                    if (canBecomeDoubleChest(neighbour,block.getType())) {
                        Chest neighbourChest = ((Chest)neighbour.getBlockData());
                        neighbourChest.setType(Chest.Type.LEFT);
                        neighbour.setBlockData(neighbourChest, false);
                    }
                }
            } else if((block.getBlockData() instanceof Stairs) 
                    && PluginData.isModuleEnabled(block.getWorld(), Modules.NO_PHYSICS_CONNECT_STAIRS)) {
                Stairs stair = (Stairs) block.getBlockData();
                BlockFace face = stair.getFacing();
                Block left = block.getRelative(BlockUtil.rotateBlockFace(face,1));
                if(left.getBlockData() instanceof Stairs) {
                    Stairs leftStair = (Stairs) left.getBlockData();
                    if(leftStair.getFacing().equals(BlockUtil.rotateBlockFace(face,3))) {
                        leftStair.setShape(Stairs.Shape.OUTER_RIGHT);
                        left.setBlockData(leftStair,false);
                    } else if(leftStair.getFacing().equals(BlockUtil.rotateBlockFace(face,1))) {
                        leftStair.setShape(Stairs.Shape.INNER_LEFT);
                        left.setBlockData(leftStair,false);
                    }
                }
                Block right = block.getRelative(BlockUtil.rotateBlockFace(face,3));
                    if(right.getBlockData() instanceof Stairs) {
                    Stairs rightStair = (Stairs) right.getBlockData();
                    if(rightStair.getFacing().equals(BlockUtil.rotateBlockFace(face,3))) {
                        rightStair.setShape(Stairs.Shape.INNER_RIGHT);
                        right.setBlockData(rightStair,false);
                    } else if(rightStair.getFacing().equals(BlockUtil.rotateBlockFace(face,1))) {
                        rightStair.setShape(Stairs.Shape.OUTER_LEFT);
                        right.setBlockData(rightStair,false);
                    }
                }                    
            } else if(block.getType().equals(Material.CHORUS_PLANT)
                    && PluginData.isModuleEnabled(block.getWorld(), Modules.NO_PHYSICS_CONNECT_CHORUS)) {
                MultipleFacing data = (MultipleFacing) block.getBlockData();
                for(BlockFace face: data.getAllowedFaces()) {
                    Block neighbour = block.getRelative(face);
                    if(neighbour.getType().equals(Material.CHORUS_PLANT)) {
                        MultipleFacing blockData = (MultipleFacing)block.getBlockData();
                        MultipleFacing neighbourData = (MultipleFacing)neighbour.getBlockData();
                        switch(face) {
                            case UP:
                                neighbourData.setFace(BlockFace.DOWN, true);
                                break;
                            case DOWN:
                                neighbourData.setFace(BlockFace.UP, true);
                                break;
                            default:
                                neighbourData.setFace(BlockUtil.rotateBlockFace(face, 2), true);
                        }
                        neighbour.setBlockData(neighbourData,false);
                        blockData.setFace(face, true);
                        block.setBlockData(blockData,false);
                    }
                }
            } else if(((block.getBlockData() instanceof RedstoneWire) 
                    && PluginData.isModuleEnabled(block.getWorld(), Modules.NO_PHYSICS_CONNECT_REDSTONE_WIRE))) {
                RedstoneWire data = (RedstoneWire) block.getBlockData();
                int power = data.getPower();
                for(BlockFace face: data.getAllowedFaces()) {
                    Block neighbour = block.getRelative(face);
                    Block upperNeighbour = neighbour.getRelative(BlockFace.UP);
                    Block lowerNeighbour = neighbour.getRelative(BlockFace.DOWN);
                    if(neighbour.getBlockData().getClass().equals(data.getClass())
                            && ((RedstoneWire)neighbour.getBlockData()).getPower()==power) {
                        RedstoneWire neighbourData = (RedstoneWire)neighbour.getBlockData();
                        neighbourData.setFace(BlockUtil.rotateBlockFace(face,2), Connection.SIDE);
                        neighbour.setBlockData(neighbourData,false);
                        data.setFace(face, Connection.SIDE);
                    }
                    if(lowerNeighbour.getBlockData().getClass().equals(data.getClass())
                            && ((RedstoneWire)lowerNeighbour.getBlockData()).getPower()==power) {
                        RedstoneWire neighbourData = (RedstoneWire)lowerNeighbour.getBlockData();
                        neighbourData.setFace(BlockUtil.rotateBlockFace(face,2), Connection.UP);
                        lowerNeighbour.setBlockData(neighbourData,false);
                        data.setFace(face, Connection.SIDE);
                    }
                    if(upperNeighbour.getBlockData().getClass().equals(data.getClass())
                            && ((RedstoneWire)upperNeighbour.getBlockData()).getPower()==power) {
                        RedstoneWire neighbourData = (RedstoneWire)upperNeighbour.getBlockData();
                        neighbourData.setFace(BlockUtil.rotateBlockFace(face,2), Connection.SIDE);
                        upperNeighbour.setBlockData(neighbourData,false);
                        data.setFace(face, Connection.UP);
                    }
                    block.setBlockData(data, false);
                }
            }
        //}            
    }
    
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    private void onBreakNoPhysicsListBlock(BlockBreakEvent event) {
        if(!TheGafferUtil.hasGafferPermission(event.getPlayer(),event.getBlock().getLocation())) {
            return;
        }
        /*if(PluginData.isModuleEnabled(event.getBlock().getWorld(), Modules.NO_PHYSICS_LIST_ENABLED)
                && NoPhysicsData.isNoPhysicsBlock(event.getBlock())
                && !NoPhysicsData.hasNoPhysicsException(event.getBlock())) {*/
            DevUtil.log(4,"break no physics block"+event.getBlock().getType().name()+" "+event.getBlock().getX()+" "+event.getBlock().getZ());
            Block block = event.getBlock();
            if(PluginData.getNoConnection(block)) {
                return;
            }
            if((block.getBlockData() instanceof Slab)
                && ((Waterlogged) block.getBlockData()).isWaterlogged()
                && ((Slab) block.getBlockData()).getType().equals(Slab.Type.DOUBLE)
                && PluginData.isModuleEnabled(block.getWorld(), Modules.DRAIN_WATERLOGGED_DOUBLE_SLABS)) {
                    event.setCancelled(true);
                    block.setBlockData(Bukkit.createBlockData(Material.AIR), false);
            } else if((block.getBlockData() instanceof Waterlogged)
                && (((Waterlogged) block.getBlockData()).isWaterlogged())) {
                    event.setCancelled(true);
                    block.setBlockData(Bukkit.createBlockData(Material.WATER), false);
            } else if((block.getBlockData() instanceof Fence)
                    && PluginData.isModuleEnabled(block.getWorld(), Modules.NO_PHYSICS_CONNECT_FENCES)) {
                Fence fence = (Fence) block.getBlockData();
                for(BlockFace face: fence.getAllowedFaces()) {
                    Block neighbour = block.getRelative(face);
                    if(neighbour.getBlockData() instanceof Fence) {
                        Fence neighbourFence = (Fence) neighbour.getBlockData();
                        neighbourFence.setFace(BlockUtil.rotateBlockFace(face,2), false);
                        neighbour.setBlockData(neighbourFence,false);
                    }
                }
            } else if((block.getBlockData() instanceof Wall)
                    && PluginData.isModuleEnabled(block.getWorld(), Modules.NO_PHYSICS_CONNECT_WALLS)) {
                for(BlockFace face: allowedWallFaces) {
                    Block neighbour = block.getRelative(face);
                    if(neighbour.getBlockData() instanceof Wall) {
                        Wall neighbourWall = (Wall) neighbour.getBlockData();
                        neighbourWall.setHeight(BlockUtil.rotateBlockFace(face,2), Wall.Height.NONE);
                        neighbourWall.setUp(getVanillaUp(neighbourWall));
                        neighbour.setBlockData(neighbourWall,false);
                    }
                }
            } else if((block.getBlockData() instanceof Stairs)
                    && PluginData.isModuleEnabled(block.getWorld(), Modules.NO_PHYSICS_CONNECT_STAIRS)) {
                Stairs stair = (Stairs) block.getBlockData();
                BlockFace face = stair.getFacing();
                Block left = block.getRelative(BlockUtil.rotateBlockFace(face,1));
                if(left.getBlockData() instanceof Stairs) {
                    Stairs leftStair = (Stairs) left.getBlockData();
                    if(leftStair.getFacing().equals(BlockUtil.rotateBlockFace(face,3))
                            && leftStair.getShape().equals(Stairs.Shape.OUTER_RIGHT)) {
                        leftStair.setShape(Stairs.Shape.STRAIGHT);
                        left.setBlockData(leftStair,false);
                    } else if(leftStair.getFacing().equals(BlockUtil.rotateBlockFace(face,1))
                            &&leftStair.getShape().equals(Stairs.Shape.INNER_LEFT)) {
                        leftStair.setShape(Stairs.Shape.STRAIGHT);
                        left.setBlockData(leftStair,false);
                    }
                }
                Block right = block.getRelative(BlockUtil.rotateBlockFace(face,3));
                if(right.getBlockData() instanceof Stairs) {
                    Stairs rightStair = (Stairs) right.getBlockData();
                    if(rightStair.getFacing().equals(BlockUtil.rotateBlockFace(face,3))
                            && rightStair.getShape().equals(Stairs.Shape.INNER_RIGHT)) {
                        rightStair.setShape(Stairs.Shape.STRAIGHT);
                        right.setBlockData(rightStair,false);
                    } else if(rightStair.getFacing().equals(BlockUtil.rotateBlockFace(face,1))
                            && rightStair.getShape().equals(Stairs.Shape.OUTER_LEFT)) {
                        rightStair.setShape(Stairs.Shape.STRAIGHT);
                        right.setBlockData(rightStair,false);
                    }
                }
            } else if(block.getType().equals(Material.CHORUS_PLANT)
                    && PluginData.isModuleEnabled(block.getWorld(), Modules.NO_PHYSICS_CONNECT_CHORUS)) {
                MultipleFacing data = (MultipleFacing) block.getBlockData();
                for(BlockFace face: data.getAllowedFaces()) {
                    Block neighbour = block.getRelative(face);
                    if(neighbour.getType().equals(Material.CHORUS_PLANT)) {
                        MultipleFacing neighbourData = (MultipleFacing)neighbour.getBlockData();
                        neighbourData.setFace(BlockUtil.rotateBlockFace(face,2), false);
                        neighbour.setBlockData(neighbourData,false);
                    }
                }
            } else if((block.getType().equals(Material.CHEST)
                        || block.getType().equals(Material.TRAPPED_CHEST)) 
                    && PluginData.isModuleEnabled(block.getWorld(), Modules.NO_PHYSICS_CONNECT_CHESTS)) {
                Chest chest = (Chest) block.getBlockData();
                if(chest.getType().equals(Chest.Type.LEFT)) {
                    Block neighbour = block.getRelative(BlockUtil.rotateBlockFace(chest.getFacing(), 1));
                    BlockData neighbourData = neighbour.getBlockData();
                    if(neighbourData instanceof Chest
                            && ((Chest)neighbourData).getType().equals(Chest.Type.RIGHT)) {
                        ((Chest)neighbourData).setType(Chest.Type.SINGLE);
                        neighbour.setBlockData(neighbourData, false);
                    }
                } else if(chest.getType().equals(Chest.Type.RIGHT)) {
                    Block neighbour = block.getRelative(BlockUtil.rotateBlockFace(chest.getFacing(), 3));
                    BlockData neighbourData = neighbour.getBlockData();
                    if(neighbourData instanceof Chest
                            && ((Chest)neighbourData).getType().equals(Chest.Type.LEFT)) {
                        ((Chest)neighbourData).setType(Chest.Type.SINGLE);
                        neighbour.setBlockData(neighbourData, false);
                    }
                }
            } else if(((block.getBlockData() instanceof RedstoneWire) 
                    && PluginData.isModuleEnabled(block.getWorld(), Modules.NO_PHYSICS_CONNECT_REDSTONE_WIRE))) {
                RedstoneWire data = (RedstoneWire) block.getBlockData();
                int power = data.getPower();
                for(BlockFace face: data.getAllowedFaces()) {
                    Block neighbour = block.getRelative(face);
                    Block upperNeighbour = neighbour.getRelative(BlockFace.UP);
                    Block lowerNeighbour = block.getRelative(BlockFace.DOWN);
                    if(neighbour.getBlockData().getClass().equals(data.getClass())
                            && ((RedstoneWire)neighbour.getBlockData()).getPower()==power) {
                        RedstoneWire neighbourData = (RedstoneWire)neighbour.getBlockData();
                        neighbourData.setFace(BlockUtil.rotateBlockFace(face,2), Connection.NONE);
                        neighbour.setBlockData(neighbourData,false);
                    }
                    if(lowerNeighbour.getBlockData().getClass().equals(data.getClass())
                            && ((RedstoneWire)lowerNeighbour.getBlockData()).getPower()==power) {
                        RedstoneWire neighbourData = (RedstoneWire)lowerNeighbour.getBlockData();
                        neighbourData.setFace(BlockUtil.rotateBlockFace(face,2), Connection.NONE);
                        lowerNeighbour.setBlockData(neighbourData,false);
                    }
                    if(upperNeighbour.getBlockData().getClass().equals(data.getClass())
                            && ((RedstoneWire)upperNeighbour.getBlockData()).getPower()==power) {
                        RedstoneWire neighbourData = (RedstoneWire)upperNeighbour.getBlockData();
                        neighbourData.setFace(BlockUtil.rotateBlockFace(face,2), Connection.NONE);
                        upperNeighbour.setBlockData(neighbourData,false);
                    }
                }
            }
        //}            
    }

    private static boolean getVanillaUp(Wall data) {
        if(data.getHeight(BlockFace.EAST).equals(Wall.Height.NONE)
                && data.getHeight(BlockFace.WEST).equals(Wall.Height.NONE)
                && data.getHeight(BlockFace.NORTH).equals(Wall.Height.NONE)
                && data.getHeight(BlockFace.SOUTH).equals(Wall.Height.NONE)) {
            return true;
        }
        if(data.getHeight(BlockFace.EAST).equals(data.getHeight(BlockFace.WEST))
                && data.getHeight(BlockFace.NORTH).equals(Wall.Height.NONE)
                && data.getHeight(BlockFace.SOUTH).equals(Wall.Height.NONE)) {
            return false;
        }
        return !data.getHeight(BlockFace.NORTH).equals(data.getHeight(BlockFace.SOUTH))
                || !data.getHeight(BlockFace.EAST).equals(Wall.Height.NONE)
                || !data.getHeight(BlockFace.WEST).equals(Wall.Height.NONE);
    }

    private static boolean canBecomeDoubleChest(Block block, Material match) {
        return block.getBlockData() instanceof Chest
                && !block.getType().equals(Material.ENDER_CHEST)
                && block.getType().equals(match)
                && ((Chest)block.getBlockData()).getType().equals(Chest.Type.SINGLE);
    }
    
    private static BlockFace rotateLeft(BlockFace face) {
        switch(face) {
            case NORTH: return BlockFace.WEST;
            case EAST: return BlockFace.NORTH;
            case SOUTH: return BlockFace.EAST;
            case WEST: return BlockFace.SOUTH;
            default: return face;
        }
    }
    
    private static BlockFace rotateRight(BlockFace face) {
        switch(face) {
            case SOUTH: return BlockFace.WEST;
            case WEST: return BlockFace.NORTH;
            case NORTH: return BlockFace.EAST;
            case EAST: return BlockFace.SOUTH;
            default: return face;
        }
    }
    
}
