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
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

import java.util.logging.Logger;

/**
 *
 * @author Eriol_Eandur
 */
public class SpecialBlockBranchTwigsEightDirections extends SpecialBlockEightFaces implements IBranch {

    protected SpecialBlockBranchTwigsEightDirections(String id,
                                                     BlockData[] data) {
        super(id,data, SpecialBlockType.BRANCH_TWIGS_EIGHT_DIRECTIONS);
    }

    @Override
    public Block getBlock(Block clicked, BlockFace blockFace, Location interactionPoint, Player player) {
        Block target = super.getBlock(clicked, blockFace, interactionPoint, player);
Logger.getGlobal().info("EightFaceTwig: "+target);
        BlockFace face = getBlockFaceFine(player.getLocation().getYaw());
Logger.getGlobal().info("Face: "+face.name());
        return getBranchBlock(target, clicked, blockFace, interactionPoint,
                player, face);
    }

    @Override
    public Shift getLower(BlockFace orientation, Block clicked, Player player, Location interactionPoint) {
        return switch(orientation) { //0 = lower
            case SOUTH -> new Shift(0,0,1);
            case SOUTH_EAST -> new Shift(1,0,1);
            case EAST -> new Shift(1,0,0);
            case NORTH_EAST -> new Shift(1,0,-1);
            case NORTH -> new Shift(0,0,-1);
            case NORTH_WEST -> new Shift(-1,0,-1);
            case WEST -> new Shift(-1,0,0);
            case SOUTH_WEST -> new Shift(-1,0,1);
            default -> new Shift(0,0,0);
        };
    }

    @Override
    public Shift getUpper(BlockFace orientation, Block clicked, Player player, Location interactionPoint) {
        return getLower(orientation, clicked, player, interactionPoint);
    }

    @Override
    public boolean isDiagonal() {
        return false;
    }

    @Override
    public BlockFace getDownwardOrientation(BlockFace orientation) {
        return orientation;
    }

    public static SpecialBlockBranchTwigsEightDirections loadFromConfig(ConfigurationSection config, String id) {
        BlockData[] data = loadBlockDataFromConfig(config, eightFaces);
Logger.getGlobal().info("Data: "+data);
        if(data==null) {
            return null;
        }
        return new SpecialBlockBranchTwigsEightDirections(id, data);
    }

    @Override
    public boolean isThin(Block block, Player player, Location interactionPoint) {
        return true;
    }


}
