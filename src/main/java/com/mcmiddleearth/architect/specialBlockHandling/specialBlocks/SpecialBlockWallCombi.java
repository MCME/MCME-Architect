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

import com.mcmiddleearth.architect.specialBlockHandling.SpecialBlockType;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.data.BlockData;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

/**
 *
 * @author Eriol_Eandur
 */
public class SpecialBlockWallCombi extends SpecialBlockOrientable {
    
    private static final Orientation[] fourFaces = new Orientation[] {
        new Orientation(BlockFace.SOUTH,"NorthWest"),
        new Orientation(BlockFace.WEST,"SouthWest"),
        new Orientation(BlockFace.NORTH,"NorthEast"),
        new Orientation(BlockFace.EAST,"SouthEast")
    };

    private SpecialBlockWallCombi(String id, 
                        BlockData[] data) {
        super(id, data, SpecialBlockType.WALL_COMBI);
        orientations = fourFaces;
    }
    
    public static SpecialBlockWallCombi loadFromConfig(ConfigurationSection config, String id) {
        /* 1.13 removed
        Material material = matchMaterial(config.getString("blockMaterial",""));
        byte data = (byte) config.getInt("dataValue");
        Material[] materialAxis = new Material[4];
        byte[] dataAxis = new byte[4];
        materialAxis[0] =  matchMaterial(config.getString("blockMaterialNorthWest",""));
        materialAxis[1] =  matchMaterial(config.getString("blockMaterialSouthWest",""));
        materialAxis[2] =  matchMaterial(config.getString("blockMaterialNorthEast",""));
        materialAxis[3] =  matchMaterial(config.getString("blockMaterialSouthEast",""));
        for(int i=0; i<materialAxis.length;i++) {
            if(materialAxis[i]==null) {
                if(material==null) {
                    return null;
                }
                materialAxis[i]=material;
            }
        }
        dataAxis[0] = (config.isInt("dataValueNorthWest")?(byte) config.getInt("dataValueNorthWest"):data);
        dataAxis[1] = (config.isInt("dataValueSouthWest")?(byte) config.getInt("dataValueSouthWest"):data);
        dataAxis[2] = (config.isInt("dataValueNorthEast")?(byte) config.getInt("dataValueNorthEast"):data);
        dataAxis[3] = (config.isInt("dataValueSouthEast")?(byte) config.getInt("dataValueSouthEast"):data);*/
        BlockData[] data = loadBlockDataFromConfig(config, fourFaces);
        if(data==null) {
            return null;
        }
        return new SpecialBlockWallCombi(id, data);
    }
    
    @Override
    public BlockState getBlockState(Block blockPlace, Block clicked, BlockFace blockFace,
                                    Player player, Location interactionPoint) {
        final BlockState state = blockPlace.getState();
        float yaw = player.getLocation().getYaw();
        while(yaw>180)  yaw -= 360;
        while(yaw<-180) yaw += 360;
        if(yaw > 90) {
            if(blockFace.equals(BlockFace.UP)) {
                state.setBlockData(getBlockDatas()[3]);
            } else {
                state.setBlockData(getBlockDatas()[0]);
            }
        } else if(yaw > 0) {
            if(blockFace.equals(BlockFace.UP)) {
                state.setBlockData(getBlockDatas()[2]);
            } else {
                state.setBlockData(getBlockDatas()[1]);
            }
        } else if(yaw > -90) {
            if(blockFace.equals(BlockFace.UP)) {
                state.setBlockData(getBlockDatas()[0]);
            } else {
                state.setBlockData(getBlockDatas()[3]);
            }
        } else {
            if(blockFace.equals(BlockFace.UP)) {
                state.setBlockData(getBlockDatas()[1]);
            } else {
                state.setBlockData(getBlockDatas()[2]);
            }
        }
        return state;
    }
}
