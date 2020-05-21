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
import static com.mcmiddleearth.architect.specialBlockHandling.specialBlocks.SpecialBlock.matchMaterial;
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

/**
 *
 * @author Eriol_Eandur
 */
public class SpecialBlockDoubleY extends SpecialBlock{
    
   /* 1.13 removed
    private final Material lowerMaterial;
   private final Material upperMaterial;
   
   private final byte lowerData;
   private final byte upperData;
    */
    
    private final BlockData lowerData;
    private final BlockData upperData;
    
    private SpecialBlockDoubleY(String id, 
                        //Material lowerMaterial, Material upperMaterial,
                        //byte lowerData, byte upperData
                        BlockData lowerData, BlockData upperData) {
        super(id, Material.AIR.createBlockData(), SpecialBlockType.DOUBLE_Y_BLOCK);
        this.lowerData = lowerData;
        this.upperData = upperData;
    }
    
    /* 1.13 removed
    private SpecialBlockDoubleY(String id, 
                        //Material lowerMaterial, Material upperMaterial
                        BlockData lowerData, BlockData upperData) {
        super(id, Material.AIR.createBlockData(), SpecialBlockType.DOOR);
        //this.lowerMaterial = lowerMaterial;
        //this.upperMaterial = upperMaterial;
        this.lowerData = 0;
        this.upperData = 0;
    }*/
    
    public static SpecialBlockDoubleY loadFromConfig(ConfigurationSection config, String id) {
        BlockData upperData, lowerData;
        //convert old data
        if(!(config.contains("blockDataUpper") && config.contains("blockDataLower"))) {
            Material lowerMaterial = matchMaterial(config.getString("lowerMaterial",""));
            Material upperMaterial = matchMaterial(config.getString("upperMaterial",""));
            if(lowerMaterial==null || upperMaterial==null) {
                return null;
            }
            byte lowerRawData = (config.isInt("lowerDataValue")?(byte) config.getInt("lowerDataValue"):0);
            byte upperRawData = (config.isInt("upperDataValue")?(byte) config.getInt("upperDataValue"):0);
            
            World world = Bukkit.getWorld("world");
            if(world==null) {
                return null;
            }
            BlockState lowerState = world.getBlockAt(0,0,10).getState();
            BlockState upperState = world.getBlockAt(0,0,10).getState();
            lowerState.setType(lowerMaterial);
            upperState.setType(upperMaterial);
            lowerState.setRawData(lowerRawData);
            upperState.setRawData(upperRawData);
            lowerData = lowerState.getBlockData();
            upperData = upperState.getBlockData();
            config.set("blockDataLower", lowerData.getAsString());
            config.set("blockDataUpper", lowerData.getAsString());
            config.set("blockMaterial", null);
            config.set("dataValue",null);
        // end convert old data
        }else {
            try {
                upperData = Bukkit.getServer().createBlockData(config.getString("blockDataUpper",""));
                lowerData = Bukkit.getServer().createBlockData(config.getString("blockDataLower",""));
            } catch(IllegalArgumentException e) {
                return null;
            }
        }
        return new SpecialBlockDoubleY(id, lowerData, upperData);
        
        //return new SpecialBlockDoubleY(id, lowerMaterial, upperMaterial, lowerData, upperData);
    }
    
    @Override
    public void placeBlock(final Block blockPlace, final BlockFace blockFace, final Player player) {
        final Location playerLoc = player.getLocation();
        super.placeBlock(blockPlace, BlockFace.DOWN,player);
        Block upper = blockPlace.getRelative(BlockFace.UP);
        if(upper.isEmpty()) {
            super.placeBlock(upper, BlockFace.UP,player);
        }
    }
    
    @Override
    protected BlockState getBlockState(Block blockPlace, BlockFace blockFace, Location playerLoc) {
        final BlockState state = blockPlace.getState();
        if(blockFace==BlockFace.UP) {
            state.setBlockData(upperData);
            //state.setType(upperMaterial);
            //state.setRawData(upperData);
        } else {
            state.setBlockData(lowerData);
            //state.setType(lowerMaterial);
            //state.setRawData(lowerData);
        }
        return state;
    }
    
   @Override
    public boolean matches(Block block) {
        /*return ((lowerMaterial.equals(block.getType())
                && lowerData == block.getData()
                && upperMaterial.equals(block.getRelative(BlockFace.UP).getType())
                && upperData == block.getRelative(BlockFace.UP).getData())
            || (upperMaterial.equals(block.getType())
                && upperData == block.getData()
                && lowerMaterial.equals(block.getRelative(BlockFace.DOWN).getType())
                && lowerData == block.getRelative(BlockFace.DOWN).getData()));*/
        return (block.getBlockData().matches(lowerData)
                || block.getBlockData().matches(upperData));
    }
}
