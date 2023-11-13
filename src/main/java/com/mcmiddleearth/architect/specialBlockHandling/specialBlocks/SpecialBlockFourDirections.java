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
public class SpecialBlockFourDirections extends SpecialBlockOrientable {
    
    public static final Orientation[] fourFaces = new Orientation[] {
        new Orientation(BlockFace.SOUTH,"South"),
        new Orientation(BlockFace.WEST,"West"),
        new Orientation(BlockFace.NORTH,"North"),
        new Orientation(BlockFace.EAST,"East")
    };
    
    protected SpecialBlockFourDirections(String id, 
                        BlockData[] data) {
        super(id, data, SpecialBlockType.FOUR_DIRECTIONS);
        orientations = fourFaces;
    }
    
    protected SpecialBlockFourDirections(String id, 
                        BlockData[] data, SpecialBlockType type) {
        super(id, data, type);
    }
    
    public static SpecialBlockFourDirections loadFromConfig(ConfigurationSection config, String id) {
        /* 1.13 removed
        Material material = matchMaterial(config.getString("blockMaterial",""));
        byte data = (byte) config.getInt("blockDataValue");
        Material[] materialFaces = new Material[4];
        byte[] dataFaces = new byte[4];
        materialFaces[0] =  matchMaterial(config.getString("blockMaterialNorth",""));
        materialFaces[1] =  matchMaterial(config.getString("blockMaterialSouth",""));
        materialFaces[2] =  matchMaterial(config.getString("blockMaterialEast",""));
        materialFaces[3] =  matchMaterial(config.getString("blockMaterialWest",""));
        for(int i=0; i<materialFaces.length;i++) {
            if(materialFaces[i]==null) {
                if(material==null) {
                    return null;
                }
                materialFaces[i]=material;
            }
        }
        dataFaces[0] = (config.isInt("dataValueNorth")?(byte) config.getInt("dataValueNorth"):data);
        dataFaces[1] = (config.isInt("dataValueSouth")?(byte) config.getInt("dataValueSouth"):data);
        dataFaces[2] = (config.isInt("dataValueEast")?(byte) config.getInt("dataValueEast"):data);
        dataFaces[3] = (config.isInt("dataValueWest")?(byte) config.getInt("dataValueWest"):data);*/
        BlockData[] data = loadBlockDataFromConfig(config, fourFaces);
        if(data==null) {
            return null;
        }
        return new SpecialBlockFourDirections(id, data);
    }
    
    @Override
    public BlockState getBlockState(Block blockPlace, Block clicked, BlockFace blockFace,
                                    Player player, Location interactionPoint) {
        final BlockState state = blockPlace.getState();
        state.setBlockData(getBlockData(getBlockFace(player.getLocation().getYaw())));
        return state;
        /* 1.13 removed
        switch(getBlockFace(playerLoc.getYaw())) {
            case NORTH:
                state.setType(material[0]);
                state.setRawData(dataValue[0]);
                break;
            case SOUTH:
                state.setType(material[1]);
                state.setRawData(dataValue[1]);
                break;
            case EAST:
                state.setType(material[2]);
                state.setRawData(dataValue[2]);
                break;
            default:
                state.setType(material[3]);
                state.setRawData(dataValue[3]);
                break;
        }
        return state;*/
    }
    
}
