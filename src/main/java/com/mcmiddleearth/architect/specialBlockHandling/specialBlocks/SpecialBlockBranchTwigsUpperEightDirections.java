package com.mcmiddleearth.architect.specialBlockHandling.specialBlocks;

import com.mcmiddleearth.architect.specialBlockHandling.SpecialBlockType;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

@Deprecated
public class SpecialBlockBranchTwigsUpperEightDirections extends SpecialBlockEightFaces implements IBranch {

    /*protected SpecialBlockBranchTwigsUpperEightDirections(String id, BlockData[] data, SpecialBlockType type) {
        super(id, data, type);
    }*/

    protected SpecialBlockBranchTwigsUpperEightDirections(String id, BlockData[] data, SpecialBlockType type) {
        super(id, data, type);
    }

    public static SpecialBlockBranchTwigsUpperEightDirections loadFromConfig(ConfigurationSection config, String id) {
        BlockData[] data = loadBlockDataFromConfig(config, eightFaces);
        if(data==null) {
            return null;
        }
        return new SpecialBlockBranchTwigsUpperEightDirections(id, data, SpecialBlockType.BRANCH_TWIGS_UPPER_EIGHT_DIRECTIONS);
    }

    @Override
    public Block getBlock(Block clicked, BlockFace blockFace, Location interactionPoint, Player player) {
        Block target = super.getBlock(clicked, blockFace, interactionPoint, player);
        return getBranchBlock(target, clicked, blockFace, interactionPoint,
                              player, getBlockFace(player.getLocation().getYaw()));
    }

    @Override
    public Shift getUpper(BlockFace orientation, Block clicked, Player player, Location interactionPoint) {
        return new Shift(0,0,0);
    }

    @Override
    public Shift getLower(BlockFace orientation, Block clicked, Player player, Location interactionPoint) {
        return switch(orientation) {
            case SOUTH -> new Shift(0,-1,0);
            case EAST -> new Shift(0,-1,0);
            case NORTH -> new Shift(0,-1,0);
            case WEST -> new Shift(0,-1,0);
            default -> new Shift(0,0,0);
        };
    }

    @Override
    public boolean isDiagonal() { return false;}

    @Override
    public BlockFace getDownwardOrientation(BlockFace blockFace) {
        return blockFace.getOppositeFace();
    }

    @Override
    public boolean isThin(Block block, Player player, Location interactionPoint) {
        return true;
    }
}
