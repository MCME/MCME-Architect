package com.mcmiddleearth.architect.specialBlockHandling.specialBlocks;

import com.mcmiddleearth.architect.specialBlockHandling.SpecialBlockType;
import com.mcmiddleearth.architect.specialBlockHandling.data.SpecialBlockInventoryData;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.data.BlockData;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

public class SpecialBlockBranchDiagonal extends SpecialBlockBranchInclined {

    public SpecialBlockBranchDiagonal(String id, String[] variants, BlockData[][] data) {
        super(id, data, variants, SpecialBlockType.BRANCH_DIAGONAL);
    }

    public static SpecialBlockBranchDiagonal loadFromConfig(ConfigurationSection config, String id) {
        BlockData[][] data = loadBlockDataFromConfig(config, SpecialBlockEightFaces.eightFaces, variants);
        if(data==null) {
            return null;
        }
        return new SpecialBlockBranchDiagonal(id, variants, data);
    }

    @Override
    public Shift getLower(BlockFace orientation, Block clicked, Player player, Location interactionPoint) {
        return switch(orientation) {
            case SOUTH_EAST -> new Shift(1,0,1);
            case NORTH_EAST -> new Shift(1,0,-1);
            case NORTH_WEST -> new Shift(-1,0,-1);
            case SOUTH_WEST -> new Shift(-1,0,1);
            default -> new Shift(0,0,0);
        };
    }

    @Override
    public Shift getUpper(BlockFace orientation, Block clicked, Player player, Location interactionPoint) {
        return switch(orientation) {
            case SOUTH -> new Shift(0,0,-1);
            case EAST -> new Shift(-1,0,0);
            case NORTH -> new Shift(0,0,1);
            case WEST -> new Shift(1,0,0);
            default -> new Shift(0,0,0);
        };
    }

    @Override
    public boolean isDiagonal() { return true;}

    @Override
    public BlockFace getDownwardOrientation(BlockFace blockFace) {
        return blockFace.getOppositeFace();
    }


}
