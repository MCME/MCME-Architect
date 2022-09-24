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
public class SpecialBlockItemTwoDirections extends SpecialBlockItemOrientable {
    
    private static final SpecialBlockOrientable.Orientation[] twoAxis = new SpecialBlockOrientable.Orientation[] {
        new SpecialBlockOrientable.Orientation(BlockFace.SOUTH,"Z"),
        new SpecialBlockOrientable.Orientation(BlockFace.WEST,"X"),
        new SpecialBlockOrientable.Orientation(BlockFace.NORTH,"Z"),
        new SpecialBlockOrientable.Orientation(BlockFace.EAST,"X")
    };
    //private final Material[] material;
    //private final byte[] dataValue;
    
    /*private final Material contentItem;
    private final short contentDamage;
    
    private final double contentHeight;*/
    
    private SpecialBlockItemTwoDirections(String id, 
                        BlockData[] data,
                        Material contentItem,
                        Integer[] contentDamage,
                        double contentHeight) {
        super(id, data, contentItem, contentDamage, 
                  contentHeight, SpecialBlockType.ITEM_BLOCK_TWO_DIRECTIONS);
        orientations = twoAxis;
        //this.material = blockMaterial;
        //this.dataValue = blockDataValue;
        /*this.contentItem = contentItem;
        this.contentDamage = contentDamage;
        this.contentHeight = contentHeight;*/
    }

    public static SpecialBlockItemTwoDirections loadFromConfig(ConfigurationSection config, String id) {
        /* 1.13 remove 
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
        dataAxis[1] = (config.isInt("dataValueZ")?(byte) config.getInt("dataValueZ"):data);
        //return new SpecialBlockTwoAxis(id, materialAxis, dataAxis);*/
        BlockData[] data = SpecialBlockOrientable.loadBlockDataFromConfig(config, twoAxis);
        Material materialContent = matchMaterial(config.getString("contentItem",""));
        Integer[] contentDamage;
        if (config.isSet("contentCmd")) {
            contentDamage = getContentDamage(config.getString("contentCmd","0"));
        } else {
            contentDamage = getContentDamage(config.getString("contentDamage", "0"));
        }
            /*Material[] materialContentAxis = new Material[2];
        materialContentAxis[0] = matchMaterial(config.getString("contentItemX",""));
        materialContentAxis[1] = matchMaterial(config.getString("contentItemZ",""));
        for(int i=0; i<materialContentAxis.length;i++) {
            if(materialContentAxis[i]==null) {
                if(materialContent==null) {
                    return null;
                }
                materialContentAxis[i]=materialContent;
            }
        }
        short[] contentDamageAxis = new short[2];
        contentDamageAxis[0] = (short) config.getInt("contentDamageX",contentDamage);
        contentDamageAxis[1] = (short) config.getInt("contentDamageZ",contentDamage);*/
        double contentHeight = config.getDouble("contentHeight",0);
        return new SpecialBlockItemTwoDirections(id, data, 
                                                     materialContent, 
                                                     contentDamage, contentHeight);
    }
    
    /*@Override
    public void placeBlock(final Block blockPlace, final BlockFace blockFace, final Location playerLoc) {
        super.placeBlock(blockPlace, blockFace, playerLoc);
        Location loc = new Location(blockPlace.getWorld(), blockPlace.getX()+0.5, 
                                    blockPlace.getY()-2+contentHeight, blockPlace.getZ()+0.5);
        final ArmorStand armor = (ArmorStand) blockPlace.getWorld().spawnEntity(loc, EntityType.ARMOR_STAND);
        armor.setVisible(false);
        armor.setGravity(false);
        armor.setCustomName("iBE_"+blockPlace.getX()+"_"+blockPlace.getY()+"_"+blockPlace.getZ());
        new BukkitRunnable() {
            @Override
            public void run() {
                ItemStack item = new ItemStack(contentItem,1,contentDamage);
                armor.setHelmet(item);
            }
        }.runTaskLater(ArchitectPlugin.getPluginInstance(), 1);
    }

    @Override
    public BlockState getBlockState(Block blockPlace, BlockFace blockFace, Location playerLoc) {
        final BlockState state = blockPlace.getState();
        switch(getBlockFace(playerLoc.getYaw())) {
            case NORTH:
            case SOUTH:
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

    @Override
    protected Location getArmorStandLocation(Block blockPlace, BlockFace blockFace, Location playerLoc) {
        Location loc = super.getArmorStandLocation(blockPlace, blockFace, playerLoc);
        switch(getBlockFace(playerLoc.getYaw())) {
            case WEST:
            case EAST:
                loc.setYaw(loc.getYaw()-90);
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
    }*/

}
