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
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.configuration.ConfigurationSection;

/**
 *
 * @author Eriol_Eandur
 */
public class SpecialBlockSixFaces extends SpecialBlockOrientable {
    
    private static Orientation[] sixFaces = new Orientation[] {
        new Orientation(BlockFace.SOUTH,"South"),
        new Orientation(BlockFace.WEST,"West"),
        new Orientation(BlockFace.NORTH,"North"),
        new Orientation(BlockFace.EAST,"East"),
        new Orientation(BlockFace.DOWN,"Down"),
        new Orientation(BlockFace.UP,"Up")
    };

    private SpecialBlockSixFaces(String id, 
                        BlockData[] data) {
        super(id, data, SpecialBlockType.SIX_FACES);
        orientations = sixFaces;
    }
    
    public static SpecialBlockSixFaces loadFromConfig(ConfigurationSection config, String id) {
        /* 1.13 removed
        Material material = matchMaterial(config.getString("blockMaterial",""));
        byte data = (byte) config.getInt("dataValue");
        Material[] materialAxis = new Material[6];
        byte[] dataAxis = new byte[6];
        materialAxis[0] =  matchMaterial(config.getString("blockMaterialNorth",""));
        materialAxis[1] =  matchMaterial(config.getString("blockMaterialSouth",""));
        materialAxis[2] =  matchMaterial(config.getString("blockMaterialEast",""));
        materialAxis[3] =  matchMaterial(config.getString("blockMaterialWest",""));
        materialAxis[4] =  matchMaterial(config.getString("blockMaterialUp",""));
        materialAxis[5] =  matchMaterial(config.getString("blockMaterialDown",""));
        for(int i=0; i<materialAxis.length;i++) {
            if(materialAxis[i]==null) {
                if(material==null) {
                    return null;
                }
                materialAxis[i]=material;
            }
        }
        dataAxis[0] = (config.isInt("dataValueNorth")?(byte) config.getInt("dataValueNorth"):data);
        dataAxis[1] = (config.isInt("dataValueSouth")?(byte) config.getInt("dataValueSouth"):data);
        dataAxis[2] = (config.isInt("dataValueEast")?(byte) config.getInt("dataValueEast"):data);
        dataAxis[3] = (config.isInt("dataValueWest")?(byte) config.getInt("dataValueWest"):data);
        dataAxis[4] = (config.isInt("dataValueUp")?(byte) config.getInt("dataValueUp"):data);
        dataAxis[5] = (config.isInt("dataValueDown")?(byte) config.getInt("dataValueDown"):data);*/
        BlockData[] data = loadBlockDataFromConfig(config, sixFaces);
        if(data==null) {
            return null;
        }
        return new SpecialBlockSixFaces(id, data);
    }
    
    /* 1.13 removed
    @Override
    public BlockState getBlockState(Block blockPlace, BlockFace blockFace, Location playerLoc) {
        final BlockState state = blockPlace.getState();
        switch(blockFace) {
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
            case WEST:
                state.setType(material[3]);
                state.setRawData(dataValue[3]);
                break;
            case UP:
                state.setType(material[4]);
                state.setRawData(dataValue[4]);
                break;
            default:
                state.setType(material[5]);
                state.setRawData(dataValue[5]);
                break;
        }
        return state;
    }*/
    
}
