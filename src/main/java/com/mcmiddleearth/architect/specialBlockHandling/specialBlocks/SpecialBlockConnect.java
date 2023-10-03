/*
 * Copyright (C) 2020 MCME
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
import com.mcmiddleearth.architect.noPhysicsEditor.NoPhysicsListener;
import com.mcmiddleearth.architect.specialBlockHandling.SpecialBlockType;
import com.mcmiddleearth.util.DevUtil;
import org.bukkit.Bukkit;
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
public class SpecialBlockConnect extends SpecialBlock {

    private SpecialBlockConnect(String id, BlockData data) {
        super(id, data, SpecialBlockType.BLOCK_CONNECT);
    }

    protected SpecialBlockConnect(String id, BlockData data, SpecialBlockType type) {
        super(id, data, type);
    }

    public static SpecialBlockConnect loadFromConfig(ConfigurationSection config, String id) {
        BlockData data;
        try {
            data = Bukkit.getServer().createBlockData(config.getString("blockData",""));
            return new SpecialBlockConnect(id, data);
        } catch(IllegalArgumentException e) {
            return null;
        }
    }

    @Override
    public void placeBlock(final Block blockPlace, final BlockFace blockFace, final Block clicked,
                           final Location interactionPoint, final Player player) {
        final BlockState state = getBlockState(blockPlace, clicked, blockFace, player, interactionPoint);
        new BukkitRunnable() {
            @Override
            public void run() {
                //state.update(true, false);
                blockPlace.setBlockData(state.getBlockData(), false);
                DevUtil.log("Special block connect place: ID "+state.getType()+" - DV "+state.getRawData());
                final BlockState tempState = getBlockState(blockPlace, clicked, blockFace, player, interactionPoint);
                new BukkitRunnable() {
                    @Override
                    public void run() {
                        DevUtil.log("Special block connect place x2: loc: "+tempState.getX()+" "+tempState.getY()+" "+tempState.getZ()+" - ID "+state.getType()+" - DV "+state.getRawData());
                        //tempState.update(true, false);
                        blockPlace.setBlockData(tempState.getBlockData(),false);
                        // We just want VANILLA block type to connect.
                        NoPhysicsListener.connectNoPhysicsBlocks(blockPlace);
                        ChunkUpdateUtil.sendUpdates(blockPlace, player);
                        //new ClientUpdateUtil().sendBlockPlaceUpdates(blockPlace,player);
                    }
                }.runTaskLater(ArchitectPlugin.getPluginInstance(), 5);
            }
        }.runTaskLater(ArchitectPlugin.getPluginInstance(), 1);
    }


}
