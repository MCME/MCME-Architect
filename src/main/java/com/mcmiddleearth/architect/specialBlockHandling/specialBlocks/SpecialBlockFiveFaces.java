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
public class SpecialBlockFiveFaces extends SpecialBlockOrientable {
    
    protected static final Orientation[] fiveFaces = new Orientation[] {
            new Orientation(BlockFace.SOUTH,"South"),
            new Orientation(BlockFace.WEST,"West"),
            new Orientation(BlockFace.NORTH,"North"),
            new Orientation(BlockFace.EAST,"East"),
            new Orientation(BlockFace.UP,"Up")
        };

    protected SpecialBlockFiveFaces(String id,
                                    BlockData[] data, SpecialBlockType type) {
        super(id, data, type);
        orientations = fiveFaces;
        //this.material = material;
        //this.dataValue = dataValue;
    }

    private SpecialBlockFiveFaces(String id,
                BlockData[] data) {
        this(id, data, SpecialBlockType.FIVE_FACES);
    }
    
    public static SpecialBlockFiveFaces loadFromConfig(ConfigurationSection config, String id) {
        /* 1.13 removed
        Material material = matchMaterial(config.getString("blockMaterial",""));
        byte data = (byte) config.getInt("dataValue");
        Material[] materialFaces = new Material[5];
        byte[] dataFaces = new byte[5];
        materialFaces[0] =  matchMaterial(config.getString("blockMaterialNorth",""));
        materialFaces[1] =  matchMaterial(config.getString("blockMaterialSouth",""));
        materialFaces[2] =  matchMaterial(config.getString("blockMaterialEast",""));
        materialFaces[3] =  matchMaterial(config.getString("blockMaterialWest",""));
        materialFaces[4] =  matchMaterial(config.getString("blockMaterialDown",""));
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
        dataFaces[3] = (config.isInt("dataValueWest")?(byte) config.getInt("dataValueWest"):data);
        dataFaces[4] = (config.isInt("dataValueDown")?(byte) config.getInt("dataValueDown"):data);*/
        BlockData[] data = loadBlockDataFromConfig(config, fiveFaces);
        if(data==null) {
            return null;
        }
        return new SpecialBlockFiveFaces(id, data);
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
            default:
                state.setType(material[4]);
                state.setRawData(dataValue[4]);
                break;
        }
        return state;
    }*/
    
}
