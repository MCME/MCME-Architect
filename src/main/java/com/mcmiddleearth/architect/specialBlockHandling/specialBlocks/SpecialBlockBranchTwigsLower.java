package com.mcmiddleearth.architect.specialBlockHandling.specialBlocks;

import com.mcmiddleearth.architect.specialBlockHandling.SpecialBlockType;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

public class SpecialBlockBranchTwigsLower extends SpecialBlockFourDirections implements IBranch {

    protected SpecialBlockBranchTwigsLower(String id, BlockData[] data) {
        super(id, data);
    }

    protected SpecialBlockBranchTwigsLower(String id, BlockData[] data, SpecialBlockType type) {
        super(id, data, type);
    }

    public static SpecialBlockBranchTwigsLower loadFromConfig(ConfigurationSection config, String id) {
        BlockData[] data = loadBlockDataFromConfig(config, fourFaces);
        if(data==null) {
            return null;
        }
        return new SpecialBlockBranchTwigsLower(id, data);
    }

    @Override
    public Block getBlock(Block clicked, BlockFace blockFace, Location interactionPoint, Player player) {
        Block target = super.getBlock(clicked, blockFace, interactionPoint, player);
        return getBranchBlock(target, clicked, blockFace, interactionPoint,
                              player, getBlockFace(player.getLocation().getYaw()));
    }

    @Override
    public Shift getUpper(BlockFace orientation, Player player) {
        return new Shift(0,0,0);
    }

    @Override
    public Shift getLower(BlockFace orientation, Player player) {
        return switch(orientation) {
            case SOUTH -> new Shift(0,0,-1);
            case EAST -> new Shift(-1,0,0);
            case NORTH -> new Shift(0,0,1);
            case WEST -> new Shift(1,0,0);
            default -> new Shift(0,0,0);
        };
    }

    @Override
    public boolean isDiagonal() { return false;}

    @Override
    public BlockFace getDownwardOrientation(BlockFace blockFace) {
        return blockFace.getOppositeFace();
    }

}
