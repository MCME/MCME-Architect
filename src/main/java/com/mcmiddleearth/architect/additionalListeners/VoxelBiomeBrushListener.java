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
package com.mcmiddleearth.architect.additionalListeners;

import com.mcmiddleearth.architect.ArchitectPlugin;
import com.mcmiddleearth.architect.Modules;
import com.mcmiddleearth.architect.PluginData;
import com.mcmiddleearth.pluginutil.VoxelUtil;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

/**
 *
 * @author Eriol_Eandur
 */
public class VoxelBiomeBrushListener implements Listener{
    boolean started = false;
    
    @EventHandler
    public void biomeChangeByVoxel(PlayerInteractEvent event) {
        if(!PluginData.isModuleEnabled(event.getPlayer().getWorld(), Modules.VOXEL_BIOME_BRUSH_FIX)) {
            return;
        }
        final Player player = event.getPlayer();
        if((event.getAction().equals(Action.RIGHT_CLICK_AIR) || event.getAction().equals(Action.RIGHT_CLICK_BLOCK))
                && VoxelUtil.getVoxelSniperPlugin()!=null
                && VoxelUtil.hasToolInHand(player) && VoxelUtil.isCurrentBrush(player,"biome")) {
            int brushSize=VoxelUtil.getBrushSize(player);
            final Location loc;
            if(event.hasBlock()) {
                loc = event. getClickedBlock().getLocation();
            }
            else {
                //loc = getViewTargetLocation(player, VoxelUtil.getRange(player));
                int maxDistance = VoxelUtil.getRange(player);
                if(maxDistance<0) {
                    maxDistance = Integer.MAX_VALUE;
                }
                loc = player.getTargetBlock((HashSet<Material>)null, maxDistance).getLocation();
            }
            //loc.getWorld().getBlockAt(loc).setType(Material.GLOWSTONE);
            final List<Chunk> chunkList = new ArrayList<>();
            for(int i = loc.getBlockX()-brushSize-8;i<loc.getBlockX()+brushSize+8;i+=16) {
                for(int j = loc.getBlockZ()-brushSize-8; j<loc.getBlockZ()+brushSize+8;j+=16) {
                    chunkList.add(loc.getWorld().getBlockAt(i,0,j).getChunk());
                }
            }
            new BukkitRunnable() {
                @Override
                public void run() {
                    refreshChunks(chunkList, player);
                }
            }.runTaskLater(ArchitectPlugin.getPluginInstance(), 1);
        }
    }
    
    private Location getViewTargetLocation(Player player, int range) {
        World world = player.getLocation().getWorld();
        Vector ray = player.getEyeLocation().toVector();
        double cos = Math.cos(Math.toRadians(player.getLocation().getPitch()));
        double dy = -Math.sin(Math.toRadians(player.getLocation().getPitch()));
        double dx = -cos*Math.sin(Math.toRadians(player.getLocation().getYaw()));
        double dz = cos*Math.cos(Math.toRadians(player.getLocation().getYaw()));
        Vector direction = new Vector(dx,dy,dz).normalize();
        int distance = 0;
        while(distance < Bukkit.getViewDistance()*16
                && (distance < range || range == -1)
                && world.getBlockAt(ray.toLocation(world)).isEmpty()
                && ray.getBlockY()>0
                && ray.getBlockY()<world.getMaxHeight()) {
            ray = ray.add(direction);
            distance++;
        }
        return ray.toLocation(world);
    }
    
    private void refreshChunks(List<Chunk> chunkList, Player player) {
        /*int bulkSize = 4;
        if(!ProtocolLibUtil.isInitiated()) {
            DevUtil.log("Protokol not init");
            return;
        }
        for(int i = 0; i < chunkList.size();i+=bulkSize) {
            List<Chunk> sublist = new ArrayList<>();
            for(int j = i; j < i+bulkSize && j < chunkList.size();j++) {
                sublist.add(chunkList.get(j));
            }
            ProtocolLibUtil.sendChunks(player, sublist);
        }
        /*final Player play = player;
        final List<Chunk> cList = chunkList;
        new BukkitRunnable() {
            @Override
            public void run() {
                for(Chunk chunk:cList) {
                    ProtocolLibUtil.sendEntityPacket(play, chunk);
                }
            }}.runTaskLater(ArchitectPlugin.getPluginInstance(), 25);*/
    }
}
