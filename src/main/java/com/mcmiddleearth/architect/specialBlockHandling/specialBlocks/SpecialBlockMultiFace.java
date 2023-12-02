package com.mcmiddleearth.architect.specialBlockHandling.specialBlocks;

import com.mcmiddleearth.architect.ArchitectPlugin;
import com.mcmiddleearth.architect.chunkUpdate.ChunkUpdateUtil;
import com.mcmiddleearth.architect.specialBlockHandling.SpecialBlockType;
import com.mcmiddleearth.util.DevUtil;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.MultipleFacing;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

public class SpecialBlockMultiFace extends SpecialBlock {
    protected SpecialBlockMultiFace(String id, BlockData data) {
        super(id, data, SpecialBlockType.MULTI_FACE);
    }

    public static SpecialBlock loadFromConfig(ConfigurationSection config, String id) {
        BlockData data;
        try {
            String configData = config.getString("blockData", "");
            data = Bukkit.getServer().createBlockData(null,configData);
            if(data instanceof MultipleFacing multiData) {
                /*for(BlockFace face: multiData.getAllowedFaces()) {
                    multiData.setFace(face,false);
                }*/
                return new SpecialBlockMultiFace(id, data);
            } else {
                return null;
            }
        } catch(IllegalArgumentException e) {
            return null;
        }
    }

    @Override
    protected BlockState getBlockState(Block blockPlace, Block clicked, BlockFace blockFace,
                                       Player player, Location interactionPoint) {
        BlockData data = blockPlace.getBlockData();
        if(!data.getMaterial().equals(blockPlace.getType())) {
            data = getBlockData().clone();
        }
        if(data instanceof MultipleFacing multiData) {
            if(multiData.hasFace(blockFace)) {
                multiData.setFace(blockFace, true);
            }
        }
        final BlockState state = blockPlace.getState();
        state.setBlockData(data);
        return state;
    }

    @Override
    public boolean canPlace(Block blockPlace) {
        return super.canPlace(blockPlace)
                || blockPlace.getType().equals(getBlockData().getMaterial());
    }

}
