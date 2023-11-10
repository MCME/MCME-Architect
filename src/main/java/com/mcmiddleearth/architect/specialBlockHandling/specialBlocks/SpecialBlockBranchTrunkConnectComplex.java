package com.mcmiddleearth.architect.specialBlockHandling.specialBlocks;

import com.mcmiddleearth.architect.specialBlockHandling.SpecialBlockType;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

public class SpecialBlockBranchTrunkConnectComplex extends SpecialBlockFourDirectionsComplex implements IBranch {

    public SpecialBlockBranchTrunkConnectComplex(String id, BlockData[] data, EditData[] editData) {
        super(id, data, editData);
    }

    public static SpecialBlockBranchTrunkConnectComplex loadFromConfig(ConfigurationSection config, String id) {

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
