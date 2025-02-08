package com.mcmiddleearth.architect.specialBlockHandling.specialBlocks;

import com.mcmiddleearth.architect.specialBlockHandling.SpecialBlockType;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.data.BlockData;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

public class SpecialBlockNone extends SpecialBlock {

    protected SpecialBlockNone(String id, BlockData data, SpecialBlockType type) {
        super(id, data, type);
    }

    public static SpecialBlock loadFromConfig(ConfigurationSection config, String id) {
        return new SpecialBlockNone(id, Bukkit.createBlockData(Material.AIR), SpecialBlockType.NONE);
    }

    @Override
    public void placeBlock(Block blockPlace, BlockFace blockFace, Block clicked, Location interactionPoint, Player player) {
        //nothing here
    }

    @Override
    public boolean canPlace(Block blockPlace) {
        return false;
    }

    @Override
    protected BlockState getBlockState(Block blockPlace, Block clicked, BlockFace blockFace, Player player, Location interactionPoint) {
        return blockPlace.getState();
    }
}
