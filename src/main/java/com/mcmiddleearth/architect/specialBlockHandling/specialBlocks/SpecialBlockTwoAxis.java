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
public class SpecialBlockTwoAxis extends SpecialBlockOrientable {
    
    private static Orientation[] twoAxis = new Orientation[] {
        new Orientation(BlockFace.SOUTH,"Z"),
        new Orientation(BlockFace.WEST,"X"),
        new Orientation(BlockFace.NORTH,"Z"),
        new Orientation(BlockFace.EAST,"X")
    };

    private SpecialBlockTwoAxis(String id, 
                        BlockData[] data) {
        super(id, data, SpecialBlockType.TWO_AXIS);
        orientations = twoAxis;
    }
    
    public static SpecialBlockTwoAxis loadFromConfig(ConfigurationSection config, String id) {
        /* 1.13 removed
        Material material = matchMaterial(config.getString("blockMaterial",""));
        byte data = (byte) config.getInt("dataValue");
        Material[] materialAxis = new Material[2];
        byte[] dataAxis = new byte[2];
        materialAxis[0] =  matchMaterial(config.getString("blockMaterialX",""));
        materialAxis[1] =  matchMaterial(config.getString("blockMaterialZ",""));
        for(int i=0; i<materialAxis.length;i++) {
            if(materialAxis[i]==null) {
                if(material==null) {
                    return null;
                }
                materialAxis[i]=material;
            }
        }
        dataAxis[0] = (config.isInt("dataValueX")?(byte) config.getInt("dataValueX"):data);
        dataAxis[1] = (config.isInt("dataValueZ")?(byte) config.getInt("dataValueZ"):data);*/
        BlockData[] data = loadBlockDataFromConfig(config, twoAxis);
        if(data==null) {
            return null;
        }
        return new SpecialBlockTwoAxis(id, data);
    }
    
    /* 1.13 removed
    @Override
    public BlockState getBlockState(Block blockPlace, BlockFace blockFace, Location playerLoc) {
        final BlockState state = blockPlace.getState();
        switch(blockFace) {
            case WEST:
            case EAST:
            case UP:
                state.setType(material[0]);
                state.setRawData(dataValue[0]);
                break;
            default:
                state.setType(material[1]);
                state.setRawData(dataValue[1]);
                break;
        }
        return state;
    }*/
}
