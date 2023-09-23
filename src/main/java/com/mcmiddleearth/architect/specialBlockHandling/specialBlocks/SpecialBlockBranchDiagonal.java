package com.mcmiddleearth.architect.specialBlockHandling.specialBlocks;

import com.mcmiddleearth.architect.specialBlockHandling.SpecialBlockType;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.data.BlockData;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

import java.util.logging.Logger;

public class SpecialBlockBranchDiagonal extends SpecialBlockEightFaces implements IBranch {

    public SpecialBlockBranchDiagonal(String id, BlockData[] data) {
        super(id, data);
    }

    public static SpecialBlockBranchDiagonal loadFromConfig(ConfigurationSection config, String id) {
        BlockData[] data = loadBlockDataFromConfig(config, eightFaces);
        if(data==null) {
            return null;
        }
        return new SpecialBlockBranchDiagonal(id,data);
    }

    @Override
    public Block getBlock(Block clicked, BlockFace blockFace, Location interactionPoint, Player player) {
        Block target = super.getBlock(clicked, blockFace, interactionPoint, player);
        return getBranchBlock(target, clicked, blockFace, interactionPoint,
                              player, getBlockFaceFine(player.getLocation().getYaw()));
    }

    @Override
    protected BlockState getBlockState(Block blockPlace, BlockFace blockFace, Location playerLoc) {
        return super.getBlockState(blockPlace, blockFace, getBranchOrientation(playerLoc));
    }

    @Override
    public Shift getLower(BlockFace orientation) {
        return switch(orientation) {
            case SOUTH_EAST -> new Shift(1,0,1);
            case NORTH_EAST -> new Shift(1,0,-1);
            case NORTH_WEST -> new Shift(-1,0,-1);
            case SOUTH_WEST -> new Shift(-1,0,1);
            default -> new Shift(0,0,0);
        };
    }

    @Override
    public Shift getUpper(BlockFace orientation) {
        return switch(orientation) {
            case SOUTH -> new Shift(0,0,-1);
            case EAST -> new Shift(-1,0,0);
            case NORTH -> new Shift(0,0,1);
            case WEST -> new Shift(1,0,0);
            default -> new Shift(0,0,0);
        };
    }


}
