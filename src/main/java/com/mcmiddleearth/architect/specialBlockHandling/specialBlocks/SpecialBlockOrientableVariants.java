/*
 * Copyright (C) 2017 MCME
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.mcmiddleearth.architect.specialBlockHandling.specialBlocks;

import com.mcmiddleearth.architect.ArchitectPlugin;
import com.mcmiddleearth.architect.chunkUpdate.ChunkUpdateUtil;
import com.mcmiddleearth.architect.specialBlockHandling.SpecialBlockType;
import com.mcmiddleearth.architect.specialBlockHandling.data.SpecialBlockInventoryData;
import com.mcmiddleearth.util.DevUtil;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.data.BlockData;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.logging.Logger;

/**
 *
 * @author Eriol_Eandur
 */
public abstract class SpecialBlockOrientableVariants extends SpecialBlock {

    private final SpecialBlockOrientable.Orientation[] orientations;

    private final BlockData[][] blockData; // [variant][orientation]

    private final String[] variants;

    protected SpecialBlockOrientableVariants(String id,
                                             //Material material,
                                             //byte dataValue,
                                             BlockData[][] data,
                                             String[] variants,
                                             SpecialBlockOrientable.Orientation[] orientations,
                                             SpecialBlockType type) {
        super(id,Material.AIR.createBlockData(),type);
        blockData = data;
        this.variants = variants;
        this.orientations = orientations;
    }

    protected abstract int getVariant(Block blockPlace, Block clicked, BlockFace blockFace,
                                      Player player, Location interactionPoint);

    @Override
    protected BlockState getBlockState(Block blockPlace, Block clicked, BlockFace blockFace,
                                       Player player, Location interactionPoint) {
        final BlockState state = blockPlace.getState();
        BlockData data = getBlockData(blockFace, getVariant(blockPlace, clicked, blockFace, player, interactionPoint));
        if(data!=null) {
            state.setBlockData(data);
        } else {
            DevUtil.log("No BlockData for: blockFace="+blockFace);
            DevUtil.log("Available data:");
            for(int i=0; i<orientations.length;i++) {
                for(int j = 0 ; j<blockData.length; j++) {
                    DevUtil.log("" + orientations[i].face + " - " + orientations[i].toString() + " - " + variants[j] + " - " + blockData[j][i]);
                }
            }
        }
        return state;
    }

    @Override
    public boolean isEditOnSneaking() { return true; }

    @Override
    public void placeBlock(final Block blockPlace, final BlockFace blockFace, final Block clicked,
                           final Location interactionPoint, final Player player) {
//Logger.getGlobal().info("Place variant oriented");
        if(player.isSneaking()) {
            //cycle block variant
//Logger.getGlobal().info("Cycle");
            SpecialBlock specialBlockData = SpecialBlockInventoryData.getSpecialBlockDataFromBlock(clicked, player,
                    SpecialBlockOrientableVariants.class);//this.getClass());
            /*if(specialBlockData==null) {
                specialBlockData = SpecialBlockInventoryData.getSpecialBlockDataFromBlock(clicked, player,
                        SpecialBlockOrientableVariants.class);
            }*/
//Logger.getGlobal().info("Block: "+(specialBlockData));
            if(specialBlockData instanceof SpecialBlockOrientableVariants) {
//Logger.getGlobal().info("Block VARIANT: "+((SpecialBlockOrientableVariants) specialBlockData).getVariantName(clicked));
                ((SpecialBlockOrientableVariants)specialBlockData).cycleVariant(blockPlace, clicked, player, interactionPoint);
            }
        } else {
            // place block
            final BlockState state = getBlockState(blockPlace, clicked, blockFace, player, interactionPoint);
            new BukkitRunnable() {
                @Override
                public void run() {
                    //state.update(true, false);
                    blockPlace.setBlockData(state.getBlockData(), false);
                    DevUtil.log("Special block place: ID " + state.getType() + " - DV " + state.getRawData());
                    final BlockState tempState = getBlockState(blockPlace, clicked, blockFace, player, interactionPoint);
                    new BukkitRunnable() {
                        @Override
                        public void run() {
                            DevUtil.log("Special block place x2: loc: " + tempState.getX() + " " + tempState.getY() + " " + tempState.getZ() + " - ID " + state.getType() + " - DV " + state.getRawData());
                            //tempState.update(true, false);
                            blockPlace.setBlockData(tempState.getBlockData(), false);
                            // We just want VANILLA block type to connect.
                            // NoPhysicsListener.connectNoPhysicsBlocks(blockPlace);
                            ChunkUpdateUtil.sendUpdates(blockPlace, player);
                            //new ClientUpdateUtil().sendBlockPlaceUpdates(blockPlace,player);
                        }
                    }.runTaskLater(ArchitectPlugin.getPluginInstance(), 5);
                }
            }.runTaskLater(ArchitectPlugin.getPluginInstance(), 1);
        }
    }

    public String getVariantName(Block block) {
        BlockData data = block.getBlockData();
        for(int i = 0; i < orientations.length; i++) {
            for(int j = 0; j<variants.length; j++) {
                if(data.matches(blockData[j][i])) {
                    return variants[j];
                }
            }
        }
        return "";
    }

    @Override
    public boolean matches(Block block) {
        for(BlockData[] variantBlockData: blockData)
        for(BlockData data: variantBlockData) {
            if(block.getBlockData().equals(data)) {
                return true;
            }
        }
        return false;
    }
    
    protected BlockData getBlockData(BlockFace face, int variant) {
        for(int i=0; i<orientations.length; i++) {
            if(orientations[i].face.equals(face)) {
                return blockData[variant][i];
            }
        }
        return null;
    }
    
    /*public BlockData[] getBlockDatas() {
        return blockData;
    }*/

    public static BlockData[][] loadBlockDataFromConfig(ConfigurationSection config,
                                                      SpecialBlockOrientable.Orientation[] orientations,
                                                      String[] variants) {
        BlockData[][] data = new BlockData[variants.length][orientations.length];
        if(!containsAllBlockData(config, variants, orientations)) {
            return null;
        }else {
            try {
                for(int i = 0; i<orientations.length; i++) {
                    for(int j = 0; j < variants.length; j++) {
                        data[j][i] = Bukkit.getServer().createBlockData(config.getString(
                                "blockData" + variants[j]+ orientations[i].configKey, ""));
                    }
                }
            } catch(IllegalArgumentException e) {
                return null;
            }
        }
        return data;
    }

    public static boolean containsAllBlockData(ConfigurationSection config,
                                               String[] variants,
                                               SpecialBlockOrientable.Orientation[] orientations) {
        for (SpecialBlockOrientable.Orientation orientation : orientations) {
            for(String variant: variants) {
                if (!config.contains("blockData" + variant + orientation.configKey)) {
                    return false;
                }
            }
        }
        return true;
    }

    public BlockFace getOrientation(Block block) {
        BlockData search = block.getBlockData();
        for(int i = 0; i<orientations.length; i++) {
            SpecialBlockOrientable.Orientation orientation = orientations[i];
            for(BlockData[] variantBlockData: blockData) {
                BlockData data = variantBlockData[i];
                if (search.matches(data)) {
                    return orientation.face;
                }
            }
        }
        return null;
    }

    protected void cycleVariant(Block blockPlace, Block clicked, Player player, Location interactionPoint) {
        BlockData searchData = clicked.getBlockData();
        for(int i = 0; i< orientations.length; i++) {
            for(int j = 0; j<variants.length; j++) {
                if(searchData.matches(blockData[j][i])) {
                    int nextVariant = (j+1==variants.length?0:j+1);
                    clicked.setBlockData(blockData[nextVariant][i],false);
//Logger.getGlobal().info("Set Variant: "+nextVariant + " "+blockData[nextVariant][i]);
                }
            }
        }

    }
}
