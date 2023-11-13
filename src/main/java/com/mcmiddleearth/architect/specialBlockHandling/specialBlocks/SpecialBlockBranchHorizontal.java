package com.mcmiddleearth.architect.specialBlockHandling.specialBlocks;

import com.mcmiddleearth.architect.specialBlockHandling.SpecialBlockType;
import com.mcmiddleearth.architect.specialBlockHandling.data.SpecialBlockInventoryData;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

public class SpecialBlockBranchHorizontal extends SpecialBlockFourDirectionsVariants implements IBranch {

    protected SpecialBlockBranchHorizontal(String id, String[] variants, BlockData[][] data) {
        this(id, variants, data, SpecialBlockType.BRANCH_HORIZONTAL);
    }

    protected SpecialBlockBranchHorizontal(String id, String[] variants, BlockData[][] data, SpecialBlockType type) {
        super(id, variants, data, type);
    }

    public static SpecialBlockBranchHorizontal loadFromConfig(ConfigurationSection config, String id) {
        BlockData[][] data = loadBlockDataFromConfig(config, SpecialBlockFourDirections.fourFaces,
                                                             variants);
        if(data==null) {
            return null;
        }
        return new SpecialBlockBranchHorizontal(id, variants, data);
    }

    @Override
    protected int getVariant(Block blockPlace, Block clicked, BlockFace blockFace, Player player, Location interactionPoint) {
        SpecialBlock specialBlockData = SpecialBlockInventoryData.getSpecialBlockDataFromBlock(clicked, player, IBranch.class);
//Logger.getGlobal().info("GET VARIANT horizontal: "+specialBlockData);
        if(specialBlockData!=null) {
            return (((IBranch)specialBlockData).isThin(clicked, player, interactionPoint) ? 1 : 0); //0=Thick, 1=Thin
        } else {
            return 0;
        }
    }

    @Override
    public boolean isThin(Block block, Player player, Location interactionPoint) {
        return getVariantName(block).equals("Thin");
    }


    @Override
    public Block getBlock(Block clicked, BlockFace blockFace, Location interactionPoint, Player player) {
        Block target = super.getBlock(clicked, blockFace, interactionPoint, player);
        return getBranchBlock(target, clicked, blockFace, interactionPoint,
                              player, getBlockFace(player.getLocation().getYaw()));
    }

    public IBranch.Shift getUpper(BlockFace orientation, Block clicked, Player player, Location interactionPoint) {
        return new Shift(0,0,0);
    }

    public IBranch.Shift getPlacedUpper(BlockFace orientation, Block clicked, Player player, Location interactionPoint) {
        return new Shift(0,-1,0);
    }

    public IBranch.Shift getPlacedLower(BlockFace orientation, Block clicked, Player player, Location interactionPoint) {
        return new Shift(0,0,0);
    }

    @Override
    public IBranch.Shift getLower(BlockFace orientation, Block clicked, Player player, Location interactionPoint) {
        return switch(orientation) {
            case SOUTH -> new Shift(0,0,1);
            case EAST -> new Shift(1,0,0);
            case NORTH -> new Shift(0,0,-1);
            case WEST -> new Shift(-1,0,0);
            default -> new Shift(0,0,0);
        };
    }

    /*Override
    public IBranch.Shift getLower(BlockFace orientation, Block clicked, Player player, Location interactionPoint) {
        return new Shift(0,0,0);
    }

    @Override
    public IBranch.Shift getUpper(BlockFace orientation, Block clicked, Player player, Location interactionPoint) {
        return getLower(orientation, clicked, player, interactionPoint);
    }

    @Override
    public IBranch.Shift getPlacedUpper(BlockFace orientation, Block clicked, Player player, Location interactionPoint) {
        return switch(orientation) {
            case SOUTH -> new Shift(0,-1,-1);
            case EAST -> new Shift(-1,-1,0);
            case NORTH -> new Shift(0,-1,1);
            case WEST -> new Shift(1,-1,0);
            default -> new Shift(0,0,0);
        };
    }

    @Override
    public IBranch.Shift getPlacedLower(BlockFace blockFace, Block clicked, Player player, Location interactionPoint) {
        Shift shift = getPlacedUpper(blockFace, clicked, player, interactionPoint);
        shift.setY(0);
        return shift;
    }*/


    @Override
    public BlockFace getDownwardOrientation(BlockFace orientation) {
        return orientation;
    }

    @Override
    public boolean isDiagonal() { return false;}

}
