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
import com.mcmiddleearth.architect.PluginData;
import com.mcmiddleearth.architect.blockData.BlockDataManager;
import com.mcmiddleearth.architect.blockData.attributes.Attribute;
import com.mcmiddleearth.architect.chunkUpdate.ChunkUpdateUtil;
import com.mcmiddleearth.architect.specialBlockHandling.SpecialBlockType;
import com.mcmiddleearth.architect.specialBlockHandling.data.SpecialBlockInventoryData;
import com.mcmiddleearth.util.DevUtil;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.data.BlockData;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.logging.Logger;

/**
 *
 * @author Eriol_Eandur
 */
public class SpecialBlockDiagonalConnect extends SpecialBlockOrientable {

    protected static final Orientation[] fiveFaces = new Orientation[] {
        new Orientation(BlockFace.UP,"Up"),
        new Orientation(BlockFace.SOUTH,"South"),
        new Orientation(BlockFace.WEST,"West"),
        new Orientation(BlockFace.NORTH,"North"),
        new Orientation(BlockFace.EAST,"East")
    };

    protected SpecialBlockDiagonalConnect(String id,
                                          BlockData[] data) {
        super(id, data, SpecialBlockType.DIAGONAL_CONNECT);
        orientations = fiveFaces;
    }

    protected SpecialBlockDiagonalConnect(String id,
                                          BlockData[] data, SpecialBlockType type) {
        super(id, data, type);
    }
    
    public static SpecialBlockDiagonalConnect loadFromConfig(ConfigurationSection config, String id) {
        BlockData[] data = loadBlockDataFromConfig(config, fiveFaces);
        if(data==null) {
            return null;
        }
        return new SpecialBlockDiagonalConnect(id, data);
    }
    
    @Override
    public BlockState getBlockState(Block blockPlace, Block clicked, BlockFace blockFace,
                                    Player player, Location interactionPoint) {
        final BlockState state = blockPlace.getState();
        BlockFace face = getBlockFaceFromLoc(player.getLocation(),false);
        state.setBlockData(getBlockData(face));
        return state;
    }

    @Override
    public Block getBlock(Block clicked, BlockFace blockFace,
                          Location interactionPoint, Player player) {
        /*if(Math.abs(player.getLocation().getPitch())>45) {
            return super.getBlock(clicked,blockFace,player);
        } else {*/
            return getBlockFromLocation(clicked, player.getLocation());
        //}
    }

    private static Block getBlockFromLocation(Block clicked, Location loc) {
        Block result;
        if(loc.getPitch()>0) {
            result = clicked.getRelative(BlockFace.UP);
        } else {
            result = clicked.getRelative(BlockFace.DOWN);
        }
        if(Math.abs(loc.getPitch())<45) {
            result = result.getRelative(getBlockFace(loc.getYaw() + 180));
        }
        return result;
    }

    @Override
    public boolean isEditOnSneaking() { return true; }

    @Override
    public void placeBlock(final Block blockPlace, final BlockFace blockFace, Block clicked,
                           final Location interactionPoint, final Player player) {
        if(!player.isSneaking()) {
            super.placeBlock(blockPlace,blockFace,clicked,interactionPoint,player);
        } else {
            Location loc = player.getLocation().clone();
            loc.setPitch(-loc.getPitch());
            loc.setYaw(loc.getYaw()+180);
            //Block clicked = getBlockFromLocation(blockPlace, loc);
            //String blockId = SpecialBlockInventoryData.getSpecialBlockDataFromItem(
            //        SpecialBlockInventoryData.getItem(clicked, SpecialBlockInventoryData.rpName(getId()))).getId();
//Logger.getGlobal().info("block id: "+blockId);
            //if(getId().equals(SpecialBlockInventoryData.getSpecialBlockDataFromItem(
            //        SpecialBlockInventoryData.getItem(clicked, SpecialBlockInventoryData.rpName(getId()))).getId())) {
            if(getBlockDatas()[0].getMaterial().equals(clicked.getType())) {
                editDiagonal(blockPlace, clicked, player, this);
            }
        }
    }

    public static void editDiagonal(Block blockPlace, Block clicked, Player player, SpecialBlock specialBlock) {
        BlockDataManager manager = new BlockDataManager();
        String searchFor = getBlockFaceFromLoc(player.getLocation(),true).name();
        BlockData data = clicked.getBlockData();
        Attribute attrib = manager.getAttribute(data);
        int i = 0;
        if (attrib != null) {
            int countAttribs = manager.countAttributes(data);
            while(!attrib.getName().equalsIgnoreCase(searchFor) && i < countAttribs) {
                manager.nextAttribute(data);
                attrib = manager.getAttribute(data);
                i++;
            }
            attrib.cycleState();
            if(PluginData.isAllowedBlock(player, data) && specialBlock.isAllowedBlockData(data)) {
                executeCycle(clicked,data);
            } else {
                attrib.cycleState();
                if(PluginData.isAllowedBlock(player, data) && specialBlock.isAllowedBlockData(data)) {
                    executeCycle(clicked, data);
                }
            }
        }
    }

    private static void executeCycle(Block clicked, BlockData data){
        clicked.setBlockData(data, false);
        new BukkitRunnable() {
            @Override
            public void run() {
                clicked.setBlockData(data, false);
            }
        }.runTaskLater(ArchitectPlugin.getPluginInstance(), 1);
    }

    private static BlockFace getBlockFaceFromLoc(Location loc, boolean alwaysOpposite) {
        BlockFace face;
        if(Math.abs(loc.getPitch())>45) {
            face = BlockFace.UP;
        } else {
            if(loc.getPitch()>0 && !alwaysOpposite) {
                face = getBlockFace(loc.getYaw());
            } else {
                face = getBlockFace(loc.getYaw() + 180);
            }
        }
        return face;
    }

}
