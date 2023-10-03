/*
 * Copyright (C) 2016 MCME
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
import com.mcmiddleearth.architect.specialBlockHandling.SpecialBlockType;
import com.mcmiddleearth.util.DevUtil;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.data.BlockData;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

/**
 *
 * @author Eriol_Eandur
 */
public class SpecialBlockOpenHalfDoor extends SpecialBlockFourDirections {
    
    protected SpecialBlockOpenHalfDoor(String id, 
                        BlockData[] data,
                        SpecialBlockType type) {
        super(id, data, type);
    }
    
    public static SpecialBlockOpenHalfDoor loadFromConfig(ConfigurationSection config, String id) {
        return null;
        /* 1.13 removed
        Material material = matchMaterial(config.getString("blockMaterial",""));
        byte data = (byte) config.getInt("blockDataValue");
        Material[] materialFaces = new Material[4];
        byte[] dataFaces = new byte[4];
        materialFaces[0] =  matchMaterial(config.getString("blockMaterialNorth",""));
        materialFaces[1] =  matchMaterial(config.getString("blockMaterialSouth",""));
        materialFaces[2] =  matchMaterial(config.getString("blockMaterialEast",""));
        materialFaces[3] =  matchMaterial(config.getString("blockMaterialWest",""));
        for(int i=0; i<materialFaces.length;i++) {
            if(materialFaces[i]==null) {
                if(material==null) {
                    return null;
                }
                materialFaces[i]=material;
            }
        }
        dataFaces[0] = (config.isInt("dataValueNorth")?(byte) config.getInt("dataValueNorth"):data);
        dataFaces[1] = (config.isInt("dataValueSouth")?(byte) config.getInt("dataValueSouth"):data);
        dataFaces[2] = (config.isInt("dataValueEast")?(byte) config.getInt("dataValueEast"):data);
        dataFaces[3] = (config.isInt("dataValueWest")?(byte) config.getInt("dataValueWest"):data);
        return new SpecialBlockOpenHalfDoor(id, materialFaces, dataFaces, SpecialBlockType.FOUR_DIRECTIONS);
        */
    }
    
    
    @Override
    public void placeBlock(final Block blockPlace, final BlockFace blockFace, Block clicked,
                           final Location interactionPoint, final Player player) {
        final Location playerLoc = player.getLocation();
        final BlockState state = getBlockState(blockPlace, clicked, blockFace, player, interactionPoint);
        new BukkitRunnable() {
            @Override
            public void run() {
                state.getBlock().setBlockData(state.getBlockData(),false);//.update(true, false);
                DevUtil.log("Special block place: ID "+state.getType()+" - DV "+state.getRawData());
                final BlockState tempState = getBlockState(blockPlace, clicked, blockFace, player, interactionPoint);
                new BukkitRunnable() {
                    @Override
                    public void run() {
                        DevUtil.log("Special block place x2: loc: "+tempState.getX()+" "+tempState.getY()+" "+tempState.getZ()+" - ID "+state.getType()+" - DV "+state.getRawData());
                        tempState.getBlock().setBlockData(tempState.getBlockData(),false);//.update(true, false);
                        new BukkitRunnable() {
                            @Override
                            public void run() {
                                DevUtil.log("open half door place loc: "+tempState.getX()+" "+tempState.getY()+" "+tempState.getZ()+" - ID "+state.getType()+" - DV "+state.getRawData());
                                state.getWorld().unloadChunkRequest(tempState.getChunk().getX(),
                                                                    tempState.getChunk().getZ());
                            }
                        }.runTaskLater(ArchitectPlugin.getPluginInstance(), 5);
                    }
                }.runTaskLater(ArchitectPlugin.getPluginInstance(), 5);
            }
        }.runTaskLater(ArchitectPlugin.getPluginInstance(), 1);
    }
    
}
