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

import com.mcmiddleearth.architect.specialBlockHandling.SpecialBlockType;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

/**
 *
 * @author Eriol_Eandur
 */
public class SpecialBlockBranchTwigs extends SpecialBlockFourDirectionsVariants implements IBranch {

    private static final String[] variants = new String[]{"Lower","Upper"};

    protected SpecialBlockBranchTwigs(String id, String[] variants,
                                      BlockData[][] data) {
        super(id,variants, data, SpecialBlockType.BRANCH_TWIGS);
    }

    @Override
    public Block getBlock(Block clicked, BlockFace blockFace, Location interactionPoint, Player player) {
        Block target = super.getBlock(clicked, blockFace, interactionPoint, player);
        return getBranchBlock(target, clicked, blockFace, interactionPoint,
                player, getBlockFace(player.getLocation().getYaw()));
    }

    @Override
    public Shift getLower(BlockFace orientation, Block clicked, Player player, Location interactionPoint) {
        if(getVariant(null, null, null, player, interactionPoint)==1) {
            return new Shift(0,1,0); //1 = upper
        } else {
            return switch(orientation) { //0 = lower
                case SOUTH -> new Shift(0,0,1);
                case EAST -> new Shift(1,0,0);
                case NORTH -> new Shift(0,0,-1);
                case WEST -> new Shift(-1,0,0);
                default -> new Shift(0,0,0);
            };
        }
    }

    @Override
    public Shift getUpper(BlockFace orientation, Block clicked, Player player, Location interactionPoint) {
        return getLower(orientation, clicked, player, interactionPoint);
    }

    @Override
    public boolean isDiagonal() {
        return false;
    }

    @Override
    public BlockFace getDownwardOrientation(BlockFace orientation) {
        return orientation;
    }

    public static SpecialBlockBranchTwigs loadFromConfig(ConfigurationSection config, String id) {
        BlockData[][] data = loadBlockDataFromConfig(config, SpecialBlockFourDirections.fourFaces,
                                                             variants);
        if (data == null) {
            return null;
        }
        return new SpecialBlockBranchTwigs(id, variants, data);
    }

    @Override
    public int getVariant(Block blockPlace, Block clicked, BlockFace blockFace, Player player, Location interactionPoint) {
        return (player.getLocation().getPitch()>=0?0:1); //0=Lower, 1=Upper
    }

    @Override
    public boolean isThin(Block block, Player player, Location interactionPoint) {
        return true;
    }

    /*BlockData[] dataLower = new BlockData[orientations.length];
        BlockData[] dataUpper = new BlockData[orientations.length];
        if(!containsAllBlockData(config, orientations)) {
            return null;
        }else {
            try {
                for(int i = 0; i<orientations.length; i++) {
                    dataLower[i] = Bukkit.getServer().createBlockData(config.getString(
                            "blockDataLower"+orientations[i].configKey,""));
                    dataUpper[i] = Bukkit.getServer().createBlockData(config.getString(
                            "blockDataUpper"+orientations[i].configKey,""));
                }
            } catch(IllegalArgumentException e) {
                return null;
            }
        }
        return new SpecialBlockBranchTwigs(id,dataUpper, dataLower, SpecialBlockType.BRANCH_TWIGS);
    }
    @Override
    protected BlockState getBlockState(Block blockPlace, BlockFace blockFace, Location playerLoc) {
        final BlockState state = blockPlace.getState();
        placeLower = playerLoc.getPitch()<0;
        BlockData data = getBlockData(blockFace);
        if(data!=null) {
            state.setBlockData(data);
        } else {
            BlockData[] blockData = (placeLower?blockDataLower:blockDataUpper);
            DevUtil.log("No BlockData for: blockFace="+blockFace);
            DevUtil.log("Available data:");
            for(int i=0; i<orientations.length;i++) {
                DevUtil.log(""+orientations[i].face+" - "+orientations[i].toString()+" - "+blockData[i]);
            }
        }
        return state;
    }
    
    @Override
    public boolean matches(Block block) {
        for(BlockData data: blockDataUpper) {
            if(block.getBlockData().equals(data)) {
                return true;
            }
        }
        for(BlockData data: blockDataLower) {
            if(block.getBlockData().equals(data)) {
                return true;
            }
        }
        return false;
    }
    
    protected BlockData getBlockData(BlockFace face) {
        for(int i=0; i<orientations.length; i++) {
            if(orientations[i].face.equals(face)) {
                return (placeLower?blockDataLower[i]:blockDataUpper[i]);
            }
        }
        return null;
    }
    
    public BlockData[] getBlockDatas() {
        List<BlockData> dataList =  Arrays.asList(blockDataLower);
        dataList.addAll(Arrays.asList(blockDataUpper));
        return dataList.toArray(new BlockData[0]);
    }*/

    /*public static BlockData[] loadBlockDataFromConfig(ConfigurationSection config) {
        BlockData[] dataLower = new BlockData[orientations.length];
        BlockData[] dataUpper = new BlockData[orientations.length];
        //convert old data
        if(!containsAllBlockData(config, orientations)) {
            return null;
        }else {
            try {
                for(int i = 0; i<orientations.length; i++) {
                    dataLower[i] = Bukkit.getServer().createBlockData(config.getString(
                            "blockDataLower"+orientations[i].configKey,""));
                    dataUpper[i] = Bukkit.getServer().createBlockData(config.getString(
                            "blockDataUpper"+orientations[i].configKey,""));
                }
            } catch(IllegalArgumentException e) {
                return null;
            }
        }
        return data;
    }

    public static boolean containsAllBlockData(ConfigurationSection config, SpecialBlockBranchTwigs.Orientation[] orientations) {
        for (SpecialBlockBranchTwigs.Orientation orientation : orientations) {
            if (!config.contains("blockDataLower" + orientation.configKey)
              || !config.contains("blockDataUpper" + orientation.configKey)) {
                return false;
            }
        }
        return true;
    }

    public BlockFace getOrientation(Block block) {
        BlockData search = block.getBlockData();
        for(int i = 0; i<orientations.length; i++) {
            Orientation orientation = orientations[i];
            BlockData dataUpper = blockDataUpper[i];
            BlockData dataLower = blockDataLower[i];
            if(search.matches(dataUpper) || search.matches(dataLower)) {
                return orientation.face;
            }
        }
        return null;
    }*/
    
}
