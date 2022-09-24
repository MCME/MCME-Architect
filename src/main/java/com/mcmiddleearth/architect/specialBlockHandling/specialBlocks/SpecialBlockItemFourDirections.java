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
import static com.mcmiddleearth.architect.specialBlockHandling.specialBlocks.SpecialBlock.getBlockFace;
import com.mcmiddleearth.architect.specialBlockHandling.specialBlocks.SpecialBlockOrientable.Orientation;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.configuration.ConfigurationSection;

import java.util.logging.Logger;

/**
 *
 * @author Eriol_Eandur
 */
public class SpecialBlockItemFourDirections extends SpecialBlockItemOrientable {
    
    //private final Material[] material;
    //private final byte[] dataValue;
    
    private static final Orientation[] fourFaces = new Orientation[] {
        new SpecialBlockOrientable.Orientation(BlockFace.SOUTH,"South"),
        new SpecialBlockOrientable.Orientation(BlockFace.WEST,"West"),
        new SpecialBlockOrientable.Orientation(BlockFace.NORTH,"North"),
        new SpecialBlockOrientable.Orientation(BlockFace.EAST,"East")
    };
    
    private SpecialBlockItemFourDirections(String id, 
                        BlockData[] data,
                        Material contentItem,
                        Integer[] contentDamage,
                        double contentHeight) {
        super(id, data, contentItem, contentDamage, 
                  contentHeight, SpecialBlockType.ITEM_BLOCK_FOUR_DIRECTIONS);
        orientations = fourFaces;
        //this.dataValue = blockDataValue;
        /*this.contentItem = contentItem;
        this.contentDamage = contentDamage;
        this.contentHeight = contentHeight;*/
    }

    public static SpecialBlockItemFourDirections loadFromConfig(ConfigurationSection config, String id) {
        /* 13. removed
        Material material = matchMaterial(config.getString("blockMaterial",""));
        byte data = (byte) config.getInt("dataValue");
        Material[] materialFaces = new Material[4];
        byte[] dataFaces = new byte[4];
        materialFaces[0] =  matchMaterial(config.getString("materialNorth",""));
        materialFaces[1] =  matchMaterial(config.getString("materialSouth",""));
        materialFaces[2] =  matchMaterial(config.getString("materialEast",""));
        materialFaces[3] =  matchMaterial(config.getString("materialWest",""));
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
        BlockData[] data = SpecialBlockOrientable.loadBlockDataFromConfig(config, fourFaces);
        Material materialContent = matchMaterial(config.getString("contentItem",""));
        Integer[] contentDamage;
        if (config.isSet("contentCmd")) {
            contentDamage = getContentDamage(config.getString("contentCmd","0"));
        } else {
            contentDamage = getContentDamage(config.getString("contentDamage", "0"));
        }
        double contentHeight = config.getDouble("contentHeight",0);
        return new SpecialBlockItemFourDirections(id, data, 
                                                     materialContent, 
                                                     contentDamage, 
                                                     contentHeight);
    }

    /* 1.13 removed
    @Override
    public BlockState getBlockState(Block blockPlace, BlockFace blockFace, Location playerLoc) {
        final BlockState state = blockPlace.getState();
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
        return state;
        final BlockState state = blockPlace.getState();
        for(Orientation orientation: orientations) {
            if(orientation.face.equals(blockFace)) {
                data = blockData[i];
        }
            
        state.setBlockData(getBlockData(blockFace));
        return state;
    }
    

    }
    */

    @Override
    protected Location getArmorStandLocation(Block blockPlace, BlockFace blockFace, Location playerLoc) {
        Location loc = super.getArmorStandLocation(blockPlace, blockFace, playerLoc);
        switch(getBlockFace(playerLoc.getYaw())) {
            case EAST:
                loc.setYaw(loc.getYaw()+90);
                break;
            case SOUTH:
                loc.setYaw(loc.getYaw()+180);
                break;
            case WEST:
                loc.setYaw(loc.getYaw()-90);
                break;
        }
//Logger.getGlobal().info(""+loc.getYaw());
        return loc;
    }
    
    /*
    @Override
    public boolean matches(Block block) {
        for(Material mat: material) {
            if(mat.equals(block.getType())) {
                for(byte data: dataValue) {
                    if(data == block.getData()) {
                        ArmorStand holder = getArmorStand(block.getLocation());
                        if(holder!=null) {
                            ItemStack content = holder.getHelmet();
                            if(content.getType().equals(contentItem)) {
                                for(int damage: contentDamage) {
                                    if(damage == content.getDurability()) {
                                        return true;
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        return false;
    }
    */
}
