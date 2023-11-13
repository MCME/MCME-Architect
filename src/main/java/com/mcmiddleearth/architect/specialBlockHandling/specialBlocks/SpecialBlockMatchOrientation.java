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

import com.mcmiddleearth.architect.specialBlockHandling.SpecialBlockType;
import com.mcmiddleearth.architect.specialBlockHandling.data.BlockRawData;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.data.BlockData;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

/**
 *
 * @author Eriol_Eandur
 */
public class SpecialBlockMatchOrientation extends SpecialBlockFourDirections {
    
    List<List<BlockData>> matches = new ArrayList<>();

    private static final Orientation[] fourFaces = new Orientation[] {
        new Orientation(BlockFace.NORTH,"North"),
        new Orientation(BlockFace.SOUTH,"South"),
        new Orientation(BlockFace.EAST,"East"),
        new Orientation(BlockFace.WEST,"West")
    };

    private SpecialBlockMatchOrientation(String id, 
                        BlockData[] data,
                        List<List<BlockData>> matches) {
        super(id, data, SpecialBlockType.MATCH_ORIENTATION);
        orientations = fourFaces;
        this.matches = matches;
    }
    
    public static SpecialBlockMatchOrientation loadFromConfig(ConfigurationSection config, String id) {
        BlockData[] data = loadBlockDataFromConfig(config, fourFaces);
        if(data==null) {
            return null;
        }
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
        dataFaces[3] = (config.isInt("dataValueWest")?(byte) config.getInt("dataValueWest"):data);*/
        List<List<BlockData>> matches = new ArrayList<>();
        for(int i=0;i<4;i++) matches.add(new ArrayList<>());
        getMatches(config,"blockMatchNorth",matches.get(0));
        getMatches(config,"blockMatchSouth",matches.get(1));
        getMatches(config,"blockMatchEast",matches.get(2));
        getMatches(config,"blockMatchWest",matches.get(3));
        return new SpecialBlockMatchOrientation(id, data, matches);
    }
    
    private static void getMatches(ConfigurationSection config, String key, List<BlockData> matches) {
//Logger.getGlobal().info(input);
        String input = config.getString(key);
        if(input!=null && !input.equals("")) {
            if(!input.startsWith("minecraft")) {
                // 1.13 convert old data
                Scanner scanner = new Scanner(input);
                scanner.useDelimiter(",");
                String newData = "";
                while(scanner.hasNext()) {
                    BlockData match = new BlockRawData(scanner.next()).getBlockData();
                    matches.add(match);
                    if(newData.equals("")) {
                        newData = match.getAsString();
                    } else {
                        newData = newData + ";" + match.getAsString();
                    }
                }
                config.set(key, newData);
            } else {
                Scanner scanner = new Scanner(input);
                scanner.useDelimiter(";");
                while(scanner.hasNext()) {
                    matches.add(Bukkit.getServer().createBlockData(scanner.next()));
                }
            }
        }
    }
    
    @Override
    public BlockState getBlockState(Block blockPlace, Block clicked, BlockFace blockFace,
                                    Player player, Location interactionPoint) {
        // 1.13 removed BlockState state = blockPlace.getState();
        int[] score = new int[4];
        score[0] = getScore(blockPlace, BlockFace.SOUTH);
        score[1] = getScore(blockPlace, BlockFace.EAST);
        score[2] = getScore(blockPlace, BlockFace.NORTH);
        score[3] = getScore(blockPlace, BlockFace.WEST);
//Logger.getGlobal().info("0 "+score[0]);
//Logger.getGlobal().info("1 "+score[1]);
//Logger.getGlobal().info("2 "+score[2]);
//Logger.getGlobal().info("3 "+score[3]);
        int max = 0;
        int maxIndex = 0;
        for(int i=0;i<4;i++) {
            if(score[i]>max) {
                max = score[i];
                maxIndex = i;
            }
        }
//Logger.getGlobal().info("maxIndex "+maxIndex);
//Logger.getGlobal().info("max "+max);
        if(max>0) {
            Location loc = player.getLocation().clone();
            loc.setYaw(-90*maxIndex);
            return super.getBlockState(blockPlace, clicked, blockFace, player, interactionPoint);
        } else {
            return super.getBlockState(blockPlace, clicked, blockFace, player, interactionPoint);
        }
    }
    
    private int getScore(Block blockPlace, BlockFace face) {
        int score = 0;
        score += isMatch(matches.get(0),blockPlace.getRelative(face).getState())?1:0;
        score += isMatch(matches.get(1),blockPlace.getRelative(opposite(face)).getState())?1:0;
        score += isMatch(matches.get(2),blockPlace.getRelative(right(face)).getState())?1:0;
        score += isMatch(matches.get(3),blockPlace.getRelative(left(face)).getState())?1:0;
        return score;
    }

    private boolean isMatch(List<BlockData> matchList, BlockState state) {
//Logger.getGlobal().info("match list "+matchList.size());
        for(BlockData data:matchList) {
//Logger.getGlobal().info("data "+data.getId()+" "+data.getDV());
//Logger.getGlobal().info("state "+state.getTypeId()+" "+state.getRawData());
            /* 1.13 removed
            if(data.getId()==state.getType().getId()) {
                if(data.allDV() || data.getDV()==state.getRawData()) {
                    return true;
                }
            }*/
            if(state.getBlockData().matches(data)) {
                return true;
            }
        }
        return false;
    }
    
    //private BlockFace[] faces = new BlockFace[]{BlockFace.SOUTH, BlockFace.EAST, BlockFace.NORTH, BlockFace.WEST};

    private BlockFace opposite(BlockFace face) {
        switch(face) {
            case NORTH: return BlockFace.SOUTH;
            case SOUTH: return BlockFace.NORTH;
            case EAST: return BlockFace.WEST;
            case WEST: return BlockFace.EAST;
        }
        return BlockFace.UP;
    }
    
    private BlockFace left(BlockFace face) {
        switch(face) {
            case NORTH: return BlockFace.WEST;
            case SOUTH: return BlockFace.EAST;
            case EAST: return BlockFace.NORTH;
            case WEST: return BlockFace.SOUTH;
        }
        return BlockFace.UP;
    }
    
    private BlockFace right(BlockFace face) {
        switch(face) {
            case NORTH: return BlockFace.EAST;
            case SOUTH: return BlockFace.WEST;
            case EAST: return BlockFace.SOUTH;
            case WEST: return BlockFace.NORTH;
        }
        return BlockFace.UP;
    }
}
