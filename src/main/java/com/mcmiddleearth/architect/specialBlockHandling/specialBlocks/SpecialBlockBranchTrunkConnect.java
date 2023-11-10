package com.mcmiddleearth.architect.specialBlockHandling.specialBlocks;

import com.mcmiddleearth.architect.specialBlockHandling.SpecialBlockType;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

public class SpecialBlockBranchTrunkConnect extends SpecialBlockFiveFaces implements IBranch {

    private boolean thinBranches;

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
    public Shift getLower(BlockFace orientation, Block clicked, Player player, Location interactionPoint) {
        return null;
    }

    @Override
    public Shift getUpper(BlockFace orientation, Block clicked, Player player, Location interactionPoint) {
        return null;
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
        return false;
    }
}
