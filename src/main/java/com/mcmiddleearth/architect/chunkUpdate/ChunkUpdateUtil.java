/*
 * Copyright (C) 2018 Eriol_Eandur
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

import com.mcmiddleearth.architect.Modules;
import com.mcmiddleearth.architect.PluginData;
//import com.mcmiddleearth.pluginutil.NMSUtil;
import com.mcmiddleearth.util.DevUtil;
import java.util.HashSet;
import java.util.Set;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.MultipleFacing;
import org.bukkit.block.data.type.Gate;
import org.bukkit.entity.Player;

/**
 *
 * @author Eriol_Eandur
 */
public class ChunkUpdateUtil {
    
    private final static int updateRadius = 5;
    
    //private static final Map<Material,BlockFace[]> blockPlaceUpdateSpread = new HashMap<>();
    
    private static final int maxStep = 16*Bukkit.getServer().getViewDistance();
    private static final Set<Chunk> finishedChunks = new HashSet<>();
    private static final Set<Block> visitedBlocks = new HashSet<>();
    
    public static synchronized void sendUpdates(Block block, Player player) {
        Location loc = block.getLocation();
        if(!PluginData.isModuleEnabled(loc.getWorld(),Modules.CHUNK_UPDATE_AUTO)) {
            return;
        }
        if(block.getBlockData() instanceof Gate) {
            DevUtil.log("Sending block specific chunk updates.");
            BlockFace[] directions;
            Gate gate = (Gate) block.getBlockData();
            if(gate.getFacing().equals(BlockFace.NORTH)
                    || gate.getFacing().equals(BlockFace.SOUTH)) {
                directions = new BlockFace[]{BlockFace.EAST,BlockFace.WEST};
            } else {
                directions = new BlockFace[]{BlockFace.NORTH,BlockFace.SOUTH};
            }
            player.sendBlockChange(loc, gate);
            for(BlockFace direction: directions) {
                Block current = block.getRelative(direction);
                while(current.getBlockData() instanceof Gate) {
                    player.sendBlockChange(current.getLocation(), current.getBlockData());
                    current = current.getRelative(direction);
                }
            }
        }
        /*    visitedBlocks.clear();
            finishedChunks.clear();
            floodFillUpdate(player, blockPlace,0,specialUpdateMaterials.get(blockPlace.getType()));
        } else {
            DevUtil.log("Sending local chunk updates.");
            for(int i = -1; i<2; i++) {
                for(int j = -1; j<2; j++) {
                    Block block = blockPlace.getRelative(BlockFace.EAST,i).getRelative(BlockFace.SOUTH,j);
                    player.sendBlockChange(block.getLocation(),block.getBlockData());
                }
            }
        }*/
    }
    
    private static synchronized void floodFillUpdate(Player player, Block block, int step, boolean condition) {
        if(!(block.getBlockData() instanceof MultipleFacing) 
                || visitedBlocks.contains(block) 
                || step == maxStep) {
            return;
        }
        player.sendBlockChange(block.getLocation(), block.getBlockData());
        visitedBlocks.add(block);
        MultipleFacing data = (MultipleFacing) block.getBlockData();
        for(BlockFace face: data.getAllowedFaces()) {
            if(data.hasFace(face) == condition) {
                Block neighbour = block.getRelative(face);
                if(neighbour.getType().equals(block.getType())) {
                    floodFillUpdate(player,neighbour,step+1,condition);
                }        
            }
        }
    }
}



            /* 3D flood fill
            faces = blockPlaceUpdateSpread.get(blockPlace.getType());
            done = new BlockMap();
            sendBlockPlaceUpdates(blockPlace.getType(), blockPlace,player,0);
            
            /* -invalid
            Set<Block> start = new HashSet<>();
            start.add(blockPlace);
            chunkQueue.put(blockPlace.getChunk(), start);
            while(!chunkQueue.isEmpty()) {
                Chunk next = chunkQueue.keySet().iterator().next();
            }
        }
    }
    
    //private final Map<Chunk,Set<Block>> chunkQueue = new HashMap<>();
    
    //private final Set<Chunk> finshedChunks = new HashSet<>();
    
    private interface BlockChart  {
        public void add(Block block);
        public boolean contains(Block block);
    }
    
    private class BlockMap implements BlockChart {
        
        private final Map<Integer,Map<Integer,Set<Integer>>> done = new HashMap<>();
    
        @Override
        public void add(Block block) {
            int x = block.getX();
            int y = block.getY();
            int z = block.getZ();
            if(!done.containsKey(x)) {
                done.put(x, new HashMap<>());
            }
            Map<Integer,Set<Integer>> doneX = done.get(x);
            if(!doneX.containsKey(y)) {
                doneX.put(y, new HashSet<>());
            }
            Set<Integer> doneY = doneX.get(y);
            if(!doneY.contains(z)) {
                doneY.add(z);
            }
        }
        
        @Override
        public boolean contains(Block block) {
            int x = block.getX();
            if(!done.containsKey(x)) {
                return false;
            }
            Map<Integer,Set<Integer>> doneX = done.get(x);
            int y = block.getY();
            if(!doneX.containsKey(y)) {
                return false;
            }
            Set<Integer> doneY = doneX.get(y);
            return doneY.contains(block.getZ());
        }
    }
    
    BlockChart done;
    
    BlockFace[] faces;
    private void sendBlockPlaceUpdates(Material mat, Block block, Player player, int step) {
        DevUtil.log(2,"Update: "+block.getX()+" "+block.getY()+" "+block.getZ()+ " - "+step);
        if(block.getChunk().isLoaded() && !done.contains(block) 
                                       && block.getType().equals(mat) &&step < maxStep) {
            sendBlockUpdate(block, player);
            done.add(block);
            for(BlockFace face: faces) {
                sendBlockPlaceUpdates(mat, block.getRelative(face),player, step+1);
            }
        }
    }

    private void sendBlockUpdate(Block block, Player player) {
        player.sendBlockChange(block.getLocation(), block.getBlockData());
    }
    
    
}*/
