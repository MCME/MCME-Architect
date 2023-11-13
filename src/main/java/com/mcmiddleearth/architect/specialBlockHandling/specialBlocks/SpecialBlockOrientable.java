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
package com.mcmiddleearth.architect.specialBlockHandling.specialBlocks;

import com.mcmiddleearth.architect.specialBlockHandling.SpecialBlockType;
import com.mcmiddleearth.util.DevUtil;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.data.BlockData;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

import java.util.logging.Logger;

/**
 *
 * @author Eriol_Eandur
 */
public abstract class SpecialBlockOrientable extends SpecialBlock {
    
    protected static class Orientation {
        public BlockFace face;
        public String configKey;
        public Orientation(BlockFace face, String configKey) {
            this.face = face;
            this.configKey = configKey;
        }
    }
    /* 1.13 removed
    protected Material[] material;
    
    protected byte[] dataValue;*/
    
    protected Orientation[] orientations;

    private final BlockData[] blockData;
    
    protected SpecialBlockOrientable(String id, 
                        //Material material, 
                        //byte dataValue,
                        BlockData[] data,
                        SpecialBlockType type) {
        super(id,Material.AIR.createBlockData(),type);
        blockData = data;
    }
    
    @Override
    protected BlockState getBlockState(Block blockPlace, Block clicked, BlockFace blockFace,
                                       Player player, Location interactionPoint) {
        final BlockState state = blockPlace.getState();
        BlockData data = getBlockData(blockFace);
        if(data!=null) {
            state.setBlockData(data);
        } else {
            DevUtil.log("No BlockData for: blockFace="+blockFace);
            DevUtil.log("Available data:");
            for(int i=0; i<orientations.length;i++) {
                DevUtil.log(""+orientations[i].face+" - "+orientations[i].toString()+" - "+blockData[i]);
            }
        }
        return state;
    }
    
    @Override
    public boolean matches(Block block) {
        return matches(block.getBlockData());
    }

    @Override
    public boolean matches(BlockData search) {
        for(BlockData data: blockData) {
            if(search.equals(data)) {
                return true;
            }
        }
        return false;
    }
    
    protected BlockData getBlockData(BlockFace face) {
        for(int i=0; i<orientations.length; i++) {
            if(orientations[i].face.equals(face)) {
                return blockData[i];
            }
        }
        return null;
    }
    
    public BlockData[] getBlockDatas() {
        return blockData;
    }
    
    public static BlockData[] loadBlockDataFromConfig(ConfigurationSection config, SpecialBlockOrientable.Orientation[] orientations) {
        BlockData[] data = new BlockData[orientations.length];
        //convert old data
        if(!containsAllBlockData(config, orientations)) {
            Logger.getGlobal().info("Incomplete data! Trying conversion of 1.12 data.");
            for(int i = 0; i<orientations.length; i++) {
                Material blockMat =  Material.matchMaterial(config
                                             .getString("blockMaterial"+orientations[i].configKey,""));
                if(blockMat==null) {
                    blockMat =  Material.matchMaterial(config.getString("blockMaterial",""));
                    if(blockMat==null) {
                        return null;
                    }
                } 
                byte rawData;
                if(config.contains("dataValue"+orientations[i].configKey)) {
                    rawData = (byte) config.getInt("dataValue"+orientations[i].configKey,(byte)0);
                } else {
                    rawData = (byte) config.getInt("dataValue");
                }
                World world = Bukkit.getWorld("world");
                if(world==null) {
                    return null;
                }
                BlockState state = world.getBlockAt(0,0,10).getState();
                state.setType(blockMat);
                state.setRawData(rawData);
                data[i] = state.getBlockData();
                config.set("blockData"+orientations[i].configKey, data[i].getAsString());
                config.set("blockMaterial"+orientations[i].configKey, null);
                config.set("dataValue"+orientations[i].configKey,null);
            }
            config.set("blockMaterial", null);
            config.set("dataValue",null);
        // end convert old data
        }else {
            try {
                for(int i = 0; i<orientations.length; i++) {
                    data[i] = Bukkit.getServer().createBlockData(config.getString(
                                                    "blockData"+orientations[i].configKey,""));
                }
            } catch(IllegalArgumentException e) {
                return null;
            }
        }
        return data;
    }

    public static boolean containsAllBlockData(ConfigurationSection config, SpecialBlockOrientable.Orientation[] orientations) {
        for (SpecialBlockOrientable.Orientation orientation : orientations) {
            if (!config.contains("blockData" + orientation.configKey)) {
                return false;
            }
        }
        return true;
    }

    public BlockFace getOrientation(Block block) {
        BlockData search = block.getBlockData();
        for(int i = 0; i<orientations.length; i++) {
            Orientation orientation = orientations[i];
            BlockData data = blockData[i];
            if(search.matches(data)) {
                return orientation.face;
            }
        }
        return null;
    }
    
}
