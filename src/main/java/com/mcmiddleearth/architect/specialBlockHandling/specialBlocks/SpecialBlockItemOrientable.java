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
import com.mcmiddleearth.architect.specialBlockHandling.specialBlocks.SpecialBlockOrientable.Orientation;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 *
 * @author Eriol_Eandur
 */
public abstract class SpecialBlockItemOrientable extends SpecialBlockItemBlock {
    
    /* 1.13 removed
    protected Material[] material;
    
    protected byte[] dataValue;*/
    
    protected Orientation[] orientations;

    private final BlockData[] blockData;
    
    protected SpecialBlockItemOrientable(String id, 
                        BlockData[] blockData,
                        Material contentItem,
                        Integer[] contentDamage,
                        double contentHeight,
                        SpecialBlockType type) {
        super(id, Material.AIR.createBlockData(), contentItem, contentDamage, contentHeight,
                type);
        this.blockData = blockData;
    }
    

    @Override
    protected BlockState getBlockState(Block blockPlace, Block clicked, BlockFace blockFace,
                                       Player player, Location interactionPoint) {
        final BlockState state = blockPlace.getState();
        state.setBlockData(getBlockData(getBlockFace(player.getLocation().getYaw())));
        return state;
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
    
    @Override
    public boolean matches(Block block) {
        for(BlockData data: blockData) {
            if(block.getBlockData().equals(data)) {
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
        return false;
    }

}
