package com.mcmiddleearth.architect.specialBlockHandling.specialBlocks;

import com.mcmiddleearth.architect.specialBlockHandling.SpecialBlockType;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.MultipleFacing;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

public class SpecialBlockMultiFace extends SpecialBlock {

    protected SpecialBlockMultiFace(String id, BlockData data) {
        super(id, data, SpecialBlockType.MULTI_FACE);
    }

    public static SpecialBlock loadFromConfig(ConfigurationSection config, String id) {
        BlockData data;
        try {
            String configData = config.getString("blockData", "");
            data = Bukkit.getServer().createBlockData(null,configData);
//Logger.getGlobal().info("configData: "+configData);
//Logger.getGlobal().info("LoadData: "+data.getClass().getSimpleName()+ " "+(data instanceof MultipleFacing));
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
        boolean newBlock = false;
//Logger.getGlobal().info("BlockData1: "+data);
        if(!data.getMaterial().equals(getBlockData().getMaterial())) {
            data = getBlockData().clone();
            newBlock = true;
        }
//Logger.getGlobal().info("BlockData2: "+data);
        if(data instanceof MultipleFacing multiData) {
            BlockFace opposite = blockFace.getOppositeFace();
//Logger.getGlobal().info("Edit data: "+opposite.name());
            if(multiData.getAllowedFaces().contains(opposite)) {
//Logger.getGlobal().info("set true");
                multiData.setFace(opposite, true);
            } else {
                if(newBlock) {
                    data = Bukkit.getServer().createBlockData(Material.AIR);
                }
            }
//Logger.getGlobal().info("MultiData: "+multiData);
        }
        final BlockState state = blockPlace.getState();
        state.setBlockData(data);
//Logger.getGlobal().info("BlockData3: "+data);
        return state;
    }

    @Override
    public void placeBlock(final Block blockPlace, final BlockFace blockFace, final Block clicked,
                           final Location interactionPoint, final Player player) {
        if(player.isSneaking()) {
//Logger.getGlobal().info("Sneak!");
            BlockData data = clicked.getBlockData();
//Logger.getGlobal().info("Data: "+data.getMaterial()+" "+(data instanceof MultipleFacing));
            if(data.getMaterial().equals(getBlockData().getMaterial())
                    && data instanceof MultipleFacing multiData) {
                BlockFace opposite = blockFace.getOppositeFace();
//Logger.getGlobal().info("Opposite: "+opposite.name());
                if(multiData.getAllowedFaces().contains(opposite)) {
//Logger.getGlobal().info("Set: "+!multiData.hasFace(opposite));
                    multiData.setFace(opposite,!multiData.hasFace(opposite));
                    clicked.setBlockData(multiData,true);
                }
            }
        } else {
            super.placeBlock(blockPlace,blockFace,clicked,interactionPoint,player);
        }
    }

    @Override
    public boolean isEditOnSneaking() {
        return true;
    }

    @Override
    public boolean canPlace(Block blockPlace) {
        return super.canPlace(blockPlace)
                || blockPlace.getType().equals(getBlockData().getMaterial());
    }

}
