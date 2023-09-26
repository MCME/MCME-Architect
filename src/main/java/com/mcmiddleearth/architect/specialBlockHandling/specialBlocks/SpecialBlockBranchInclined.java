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
import com.mcmiddleearth.architect.specialBlockHandling.data.SpecialBlockInventoryData;
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
public abstract class SpecialBlockBranchInclined extends SpecialBlockOrientableVariants implements IBranch {

    protected SpecialBlockBranchInclined(String id, BlockData[][] data, String[] variants, SpecialBlockType type) {
        super(id, data, variants, SpecialBlockEightFaces.eightFaces, type);
    }

    @Override
    protected BlockState getBlockState(Block blockPlace, Block clicked, BlockFace blockFace, Player player) {
        BlockFace blockFaceFromYaw = getBlockFaceFine(getBranchOrientation(player.getLocation()).getYaw());
        return super.getBlockState(blockPlace, clicked, blockFaceFromYaw, player);
    }

    @Override
    protected int getVariant(Block blockPlace, Block clicked, BlockFace blockFace, Player player) {
        return (isThin(clicked, player)?1:0); //0=Thick, 1=Thin
    }

    @Override
    public Block getBlock(Block clicked, BlockFace blockFace, Location interactionPoint, Player player) {
        Block target = super.getBlock(clicked, blockFace, interactionPoint, player);
        return getBranchBlock(target, clicked, blockFace, interactionPoint,
                player, getBlockFaceFine(player.getLocation().getYaw()));
    }


}
