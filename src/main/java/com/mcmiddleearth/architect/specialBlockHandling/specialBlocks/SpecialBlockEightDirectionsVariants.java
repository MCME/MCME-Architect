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
import org.bukkit.entity.Player;

/**
 *
 * @author Eriol_Eandur
 */
public abstract class SpecialBlockEightDirectionsVariants extends SpecialBlockOrientableVariants {

    protected SpecialBlockEightDirectionsVariants(String id, String[] variants,
                                                  BlockData[][] data, SpecialBlockType type) {
        super(id, data, variants, SpecialBlockEightFaces.eightFaces, type);
    }

    /*removed as abstract class
    public static SpecialBlockFourDirectionsVariants loadFromConfig(ConfigurationSection config, String id) {
        BlockData[] data = loadBlockDataFromConfig(config, SpecialBlockFourDirections.fourFaces);
        if(data==null) {
            return null;
        }
        return new SpecialBlockFourDirectionsVariants(id, data);
    }*/
    
    @Override
    public BlockState getBlockState(Block blockPlace, Block clicked, BlockFace blockFace,
                                    Player player, Location interactionPoint) {
        final BlockState state = blockPlace.getState();
        BlockFace blockFaceFromYaw = getBlockFaceFine(player.getLocation().getYaw());
        //state.setBlockData(getBlockData(getBlockFace(playerLoc.getYaw()),getVariant(blockPlace,blockFace,playerLoc)));
        return super.getBlockState(blockPlace,clicked,blockFaceFromYaw,player, interactionPoint);
    }
    
}
