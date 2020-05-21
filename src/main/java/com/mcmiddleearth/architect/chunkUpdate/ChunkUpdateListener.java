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
package com.mcmiddleearth.architect.chunkUpdate;

import com.mcmiddleearth.architect.ArchitectPlugin;
import java.util.logging.Logger;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.scheduler.BukkitRunnable;

/**
 *
 * @author Eriol_Eandur
 */
public class ChunkUpdateListener implements Listener {

    /*@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = false)
    public void playerInteract(PlayerInteractEvent event) {
        if(PluginData.isModuleEnabled(event.getPlayer().getWorld(),Modules.CHUNK_UPDATE_AUTO)
                && event.getClickedBlock()!=null) {
            NMSUtil.updatePlayerChunks(event.getClickedBlock().getLocation().add(new Vector(-rad,0,-rad)), 
                                       event.getClickedBlock().getLocation().add(new Vector(rad,0,rad)));
            DevUtil.log("InteractEvent, sending chunk updates.");
        }
    }*/
    
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = false)
    public void blockBreak(BlockBreakEvent event) {
        new BukkitRunnable() {
            @Override
            public void run() {
                ChunkUpdateUtil.sendUpdates(event.getBlock().getRelative(BlockFace.UP),event.getPlayer());
                ChunkUpdateUtil.sendUpdates(event.getBlock().getRelative(BlockFace.DOWN),event.getPlayer());
                ChunkUpdateUtil.sendUpdates(event.getBlock().getRelative(BlockFace.SOUTH),event.getPlayer());
                ChunkUpdateUtil.sendUpdates(event.getBlock().getRelative(BlockFace.WEST),event.getPlayer());
                ChunkUpdateUtil.sendUpdates(event.getBlock().getRelative(BlockFace.EAST),event.getPlayer());
                ChunkUpdateUtil.sendUpdates(event.getBlock().getRelative(BlockFace.NORTH),event.getPlayer());
            }
            
        }.runTaskLater(ArchitectPlugin.getPluginInstance(), 2);
    }
    
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = false)
    public void blockPlace(BlockPlaceEvent event) {
        final Block block = event.getBlock();
        final Player player = event.getPlayer();
        new BukkitRunnable() {
            @Override
            public void run() {
                ChunkUpdateUtil.sendUpdates(event.getBlock().getRelative(BlockFace.UP),event.getPlayer());
                ChunkUpdateUtil.sendUpdates(event.getBlock().getRelative(BlockFace.DOWN),event.getPlayer());
                ChunkUpdateUtil.sendUpdates(event.getBlock().getRelative(BlockFace.SOUTH),event.getPlayer());
                ChunkUpdateUtil.sendUpdates(event.getBlock().getRelative(BlockFace.WEST),event.getPlayer());
                ChunkUpdateUtil.sendUpdates(event.getBlock().getRelative(BlockFace.EAST),event.getPlayer());
                ChunkUpdateUtil.sendUpdates(event.getBlock().getRelative(BlockFace.NORTH),event.getPlayer());
            }
        }.runTaskLater(ArchitectPlugin.getPluginInstance(), 2);
    }
    
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = false)
    public void blockInteract(PlayerInteractEvent event) {
        final Block block = event.getClickedBlock();
        if(block==null) {
            return;
        }
        final Player player = event.getPlayer();
        new BukkitRunnable() {
            @Override
            public void run() {
                ChunkUpdateUtil.sendUpdates(block,player);
            }
        }.runTaskLater(ArchitectPlugin.getPluginInstance(), 2);
    }
    
}
