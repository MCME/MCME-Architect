package com.mcmiddleearth.architect.specialBlockHandling.specialBlocks;

import com.mcmiddleearth.architect.specialBlockHandling.SpecialBlockType;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

public class SpecialBlockBranchHorizontal extends SpecialBlockFourDirections implements IBranch {

    protected SpecialBlockBranchHorizontal(String id, BlockData[] data) {
        super(id, data);
    }

    protected SpecialBlockBranchHorizontal(String id, BlockData[] data, SpecialBlockType type) {
        super(id, data, type);
    }

    public static SpecialBlockBranchHorizontal loadFromConfig(ConfigurationSection config, String id) {
        BlockData[] data = loadBlockDataFromConfig(config, fourFaces);
        if(data==null) {
            return null;
        }
        return new SpecialBlockBranchHorizontal(id, data);
    }

    @Override
    public Block getBlock(Block clicked, BlockFace blockFace, Location interactionPoint, Player player) {
        Block target = super.getBlock(clicked, blockFace, interactionPoint, player);
        return getBranchBlock(target, clicked, blockFace, interactionPoint,
                              player, getBlockFace(player.getLocation().getYaw()));
    }

    @Override
    public IBranch.Shift getLower(BlockFace orientation) {
        return new Shift(0,0,0);
    }

    @Override
    public IBranch.Shift getUpper(BlockFace orientation) {
        return switch(orientation) {
            case SOUTH -> new Shift(0,-1,-1);
            case EAST -> new Shift(-1,-1,0);
            case NORTH -> new Shift(0,-1,1);
            case WEST -> new Shift(1,-1,0);
            default -> new Shift(0,0,0);
        };
    }


}
