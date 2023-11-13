package com.mcmiddleearth.architect.specialBlockHandling.specialBlocks;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

public class SpecialBlockBranchTrunkConnectComplex extends SpecialBlockFourDirectionsComplex implements IBranch {

    private final boolean thinBranches;

    public SpecialBlockBranchTrunkConnectComplex(String id, BlockData[] data, EditData[] editData, boolean thinBranches) {
        super(id, data, editData);
        this.thinBranches = thinBranches;
    }

    public static SpecialBlockBranchTrunkConnectComplex loadFromConfig(ConfigurationSection config, String id) {
        BlockData data;
        try {
            String configData = config.getString("blockData", "");
            data = Bukkit.getServer().createBlockData(null,configData);
        } catch(IllegalArgumentException e) {
            return null;
        }
        BlockData[] blockData = new BlockData[]{data,data,data,data};
        EditData[] editData = loadEditDataFromConfig(config, blockData, fourFaces);
        if(editData==null) {
            return null;
        }
        boolean thinBranches = config.getBoolean("thinBranches", false);
        return new SpecialBlockBranchTrunkConnectComplex(id, blockData, editData, thinBranches);
    }

    @Override
    public Shift getLower(BlockFace orientation, Block clicked, Player player, Location interactionPoint) {
        return new Shift(0,0,0);
    }

    @Override
    public Shift getUpper(BlockFace orientation, Block clicked, Player player, Location interactionPoint) {
        double xRelative = interactionPoint.getX()-clicked.getX();
        double zRelative = interactionPoint.getZ()-clicked.getZ();
        if(clicked.getBlockData().equals(getBlockDatas()[0])) {
            return new Shift(0,0,0);
        }
        if(xRelative<0.1 && hasSide(clicked, getEditData()[0], EditData.WEST)) {
            return new Shift(-1,0,0);
        } else if(xRelative>0.9 && hasSide(clicked, getEditData()[0], EditData.EAST)) {
            return new Shift(1,0,0);
        } else if(zRelative<0.1 && hasSide(clicked, getEditData()[0], EditData.NORTH)) {
            return new Shift(0,0,-1);
        } else if(zRelative>0.9 && hasSide(clicked, getEditData()[0], EditData.SOUTH)) {
            return new Shift(0,0,1);
        } else {
            return new Shift(0,0,0);
        }
    }

    private boolean hasSide(Block clicked, EditData editData, int direction) {
        return editData.getIndicesFor(clicked.getBlockData())[direction] == 1;
    }

    @Override
    public boolean isDiagonal() {
        return false;
    }

    @Override
    public BlockFace getDownwardOrientation(BlockFace blockFace) {
        return null;
    }

    @Override
    public boolean isThin(Block block, Player player, Location interactionPoint) {
        double xRelative = interactionPoint.getX()-block.getX();
        double zRelative = interactionPoint.getZ()-block.getZ();
        if(xRelative>0.1 && xRelative<0.9 && zRelative>0.1 && zRelative<0.9) return false;
        return thinBranches;
    }
}
