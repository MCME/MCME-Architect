package com.mcmiddleearth.architect.specialBlockHandling.specialBlocks;

import com.mcmiddleearth.architect.specialBlockHandling.SpecialBlockType;
import com.mcmiddleearth.architect.specialBlockHandling.data.SpecialBlockInventoryData;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

public class SpecialBlockBranchTrunkConnect extends SpecialBlockFiveFaces implements IBranch {

    private final boolean thinBranches;

    public SpecialBlockBranchTrunkConnect(String id, BlockData[] data, boolean thinBranches) {
        super(id, data, SpecialBlockType.BRANCH_TRUNK_CONNECT);
        this.thinBranches = thinBranches;
    }

    public static SpecialBlockBranchTrunkConnect loadFromConfig(ConfigurationSection config, String id) {
        BlockData[] data = loadBlockDataFromConfig(config, fiveFaces);
        if(data==null) {
            return null;
        }
        boolean thinBranches = config.getBoolean("thinBranches", false);
        return new SpecialBlockBranchTrunkConnect(id, data, thinBranches);
    }

    @Override
    public boolean isEditOnSneaking() { return true; }

    @Override
    public void placeBlock(final Block blockPlace, final BlockFace blockFace, final Block clicked,
                           final Location interactionPoint, final Player player) {
        if(player.isSneaking()) {
            //edit side branches
            SpecialBlock specialBlockData = SpecialBlockInventoryData.getSpecialBlockDataFromBlock(clicked, player,
                    SpecialBlockBranchTrunkConnect.class);//this.getClass());
//Logger.getGlobal().info("Class: "+specialBlockData.getClass().getSimpleName());
            if(specialBlockData instanceof SpecialBlockBranchTrunkConnect) {
                BlockData data = clicked.getBlockData();
//Logger.getGlobal().info("data: "+data);
                if(data.matches(getBlockData(BlockFace.UP))) {
                    //add side branch
//Logger.getGlobal().info("add side: " + blockFace.name());
                    switch(blockFace) {
                        case NORTH,SOUTH,EAST,WEST -> {
//Logger.getGlobal().info("set: "+getBlockData(blockFace));
                            clicked.setBlockData(getBlockData(blockFace),true);
                        }
                    }
                } else {
                    //remove side branch
//Logger.getGlobal().info("remove side: " + getOrientation(clicked) + "==" + blockFace.name());
                    if(getOrientation(clicked).equals(blockFace)) {
                        clicked.setBlockData(getBlockData(BlockFace.UP),true);
                    }
                }
            }
        } else {
            // place block
            super.placeBlock(blockPlace,BlockFace.UP,clicked,interactionPoint,player);
        }
    }

    @Override
    public Shift getLower(BlockFace orientation, Block clicked, Player player, Location interactionPoint) {
        return new Shift(0,0,0);
    }

    @Override
    public Shift getUpper(BlockFace orientation, Block clicked, Player player, Location interactionPoint) {
        double xRelative = interactionPoint.getX()-clicked.getX();
        double zRelative = interactionPoint.getZ()-clicked.getZ();
        if(getOrientation(clicked).equals(BlockFace.UP)) {
            return new Shift(0,0,0);
        }
        if(xRelative<0.1 && getOrientation(clicked).equals(BlockFace.WEST)) {
            return new Shift(-1,0,0);
        } else if(xRelative>0.9 && getOrientation(clicked).equals(BlockFace.EAST)) {
            return new Shift(1,0,0);
        } else if(zRelative<0.1 && getOrientation(clicked).equals(BlockFace.NORTH)) {
            return new Shift(0,0,-1);
        } else if(zRelative>0.9 && getOrientation(clicked).equals(BlockFace.SOUTH)) {
            return new Shift(0,0,1);
        } else {
            return new Shift(0,0,0);
        }
    }

    @Override
    public boolean isDiagonal() {
        return true;
    }

    @Override
    public BlockFace getDownwardOrientation(BlockFace blockFace) {
        return blockFace;
    }

    @Override
    public boolean isThin(Block block, Player player, Location interactionPoint) {
        double xRelative = interactionPoint.getX()-block.getX();
        double zRelative = interactionPoint.getZ()-block.getZ();
        if(xRelative>0.1 && xRelative<0.9 && zRelative>0.1 && zRelative<0.9) return false;
        return thinBranches;
    }
}
