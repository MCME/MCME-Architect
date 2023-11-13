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

import java.util.logging.Logger;

/**
 *
 * @author Eriol_Eandur
 */
public class SpecialBlockEightFaces extends SpecialBlockOrientable {
    
    public static final Orientation[] eightFaces = new Orientation[] {
            new Orientation(BlockFace.SOUTH,"South"),
            new Orientation(BlockFace.SOUTH_WEST,"SouthWest"),
            new Orientation(BlockFace.WEST,"West"),
            new Orientation(BlockFace.NORTH_WEST,"NorthWest"),
            new Orientation(BlockFace.NORTH,"North"),
            new Orientation(BlockFace.NORTH_EAST,"NorthEast"),
            new Orientation(BlockFace.EAST,"East"),
            new Orientation(BlockFace.SOUTH_EAST,"SouthEast")
        };
    
    protected SpecialBlockEightFaces(String id,
                        //Material[] material, 
                        //byte[] dataValue,
                        BlockData[] data, SpecialBlockType type) {
        super(id, data, type);
        orientations = eightFaces;
    }
    
    public static SpecialBlockEightFaces loadFromConfig(ConfigurationSection config, String id) {
        BlockData[] data = loadBlockDataFromConfig(config, eightFaces);
        if(data==null) {
            return null;
        }
        return new SpecialBlockEightFaces(id,data,SpecialBlockType.EIGHT_FACES);
    }
    /* 1.13 removed
        Material material = matchMaterial(config.getString("blockMaterial",""));
        byte data = (byte) config.getInt("dataValue");
        Material[] materialAxis = new Material[8];
        byte[] dataAxis = new byte[8];
        materialAxis[0] =  matchMaterial(config.getString("blockMaterialSouth",""));
        materialAxis[1] =  matchMaterial(config.getString("blockMaterialSouthWest",""));
        materialAxis[2] =  matchMaterial(config.getString("blockMaterialWest",""));
        materialAxis[3] =  matchMaterial(config.getString("blockMaterialNorthWest",""));
        materialAxis[4] =  matchMaterial(config.getString("blockMaterialNorth",""));
        materialAxis[5] =  matchMaterial(config.getString("blockMaterialNorthEast",""));
        materialAxis[6] =  matchMaterial(config.getString("blockMaterialEast",""));
        materialAxis[7] =  matchMaterial(config.getString("blockMaterialSouthEast",""));
        for(int i=0; i<materialAxis.length;i++) {
            if(materialAxis[i]==null) {
                if(material==null) {
                    return null;
                }
                materialAxis[i]=material;
            }
        }
        dataAxis[0] = (config.isInt("dataValueSouth")?(byte) config.getInt("dataValueSouth"):data);
        dataAxis[1] = (config.isInt("dataValueSouthWest")?(byte) config.getInt("dataValueSouthWest"):data);
        dataAxis[2] = (config.isInt("dataValueWest")?(byte) config.getInt("dataValueWest"):data);
        dataAxis[3] = (config.isInt("dataValueNorthWest")?(byte) config.getInt("dataValueNorthWest"):data);
        dataAxis[4] = (config.isInt("dataValueNorth")?(byte) config.getInt("dataValueNorth"):data);
        dataAxis[5] = (config.isInt("dataValueNorthEast")?(byte) config.getInt("dataValueNorthEast"):data);
        dataAxis[6] = (config.isInt("dataValueEast")?(byte) config.getInt("dataValueEast"):data);
        dataAxis[7] = (config.isInt("dataValueSouthEast")?(byte) config.getInt("dataValueSouthEast"):data);
        return new SpecialBlockEightFaces(id, materialAxis, dataAxis);
    }*/
    
    @Override
    protected BlockState getBlockState(Block blockPlace, Block clicked, BlockFace blockFace,
                                       Player player, Location interactionPoint) {
        //BlockState state = blockPlace.getState();
        BlockFace blockFaceFromYaw = getBlockFaceFine(player.getLocation().getYaw());
Logger.getGlobal().info("EightFace: "+blockFaceFromYaw.name());
        BlockState state = super.getBlockState(blockPlace, clicked, blockFaceFromYaw, player, interactionPoint);
Logger.getGlobal().info("state: "+state);
        return state;
        /* 1.13 removed
        switch(blockFaceFromYaw) {
            case SOUTH:
                //state.setType(material[0]);
                //state.setRawData(dataValue[0]);
                state.setBlockData(blockData[0]);
                break;
            case SOUTH_WEST:
                state.setBlockData(blockData[1]);
                break;
            case WEST:
                state.setBlockData(blockData[2]);
                break;
            case NORTH_WEST:
                state.setBlockData(blockData[3]);
                break;
            case NORTH:
                state.setBlockData(blockData[4]);
                break;
            case NORTH_EAST:
                state.setBlockData(blockData[5]);
                break;
            case EAST:
                state.setBlockData(blockData[6]);
                break;
            default:
                state.setBlockData(blockData[7]);
                break;
        }
        return state;*/
    }
    
}
