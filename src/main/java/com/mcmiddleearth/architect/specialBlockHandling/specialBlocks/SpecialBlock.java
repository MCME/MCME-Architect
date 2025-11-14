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
import com.mcmiddleearth.architect.chunkUpdate.ChunkUpdateUtil;
import com.mcmiddleearth.architect.noPhysicsEditor.NoPhysicsListener;
import com.mcmiddleearth.architect.specialBlockHandling.SpecialBlockType;
import com.mcmiddleearth.architect.specialBlockHandling.data.SpecialBlockInventoryData;
import com.mcmiddleearth.connect.log.Log;
import com.mcmiddleearth.pluginutil.LegacyMaterialUtil;
import com.mcmiddleearth.pluginutil.NumericUtil;
import com.mcmiddleearth.util.DevUtil;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Waterlogged;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author Eriol_Eandur
 */
public class SpecialBlock {
    
    private final String id;
    private final BlockData blockData;
    protected final SpecialBlockType type;
    private String nextBlockId;
    private int priority;
    
    private final Map<String,String> collection = new HashMap<>();

    public static SpecialBlock createSpecialBlock(SpecialBlockType type, ConfigurationSection section, String fullName) {
        return switch (type) {
            case BLOCK -> SpecialBlock.loadFromConfig(section, fullName);
            case BLOCK_ON_WATER -> SpecialBlockOnWater.loadFromConfig(section, fullName);
            case BLOCK_ON_WATER_CONNECT -> SpecialBlockOnWaterConnect.loadFromConfig(section, fullName);
            case BRANCH -> SpecialBlockBranch2.loadFromConfig(section, fullName);
            case BRANCH_HORIZONTAL -> SpecialBlockBranchHorizontal.loadFromConfig(section, fullName);
            case BRANCH_DIAGONAL -> SpecialBlockBranchDiagonal.loadFromConfig(section, fullName);
            case BRANCH_STEEP -> SpecialBlockBranchSteep.loadFromConfig(section, fullName);
            case BRANCH_TWIGS -> SpecialBlockBranchTwigs.loadFromConfig(section, fullName);
            case BRANCH_TWIGS_EIGHT_DIRECTIONS ->
                    SpecialBlockBranchTwigsEightDirections.loadFromConfig(section, fullName);
                        /*case BRANCH_TWIGS_UPPER:
                            blockData = SpecialBlockBranchTwigsUpper.loadFromConfig(section, fullName);
                            break;
                        case BRANCH_TWIGS_LOWER:
                            blockData = SpecialBlockBranchTwigsLower.loadFromConfig(section, fullName);
                            break;
                        case BRANCH_TWIGS_UPPER_EIGHT_DIRECTIONS:
                            blockData = SpecialBlockBranchTwigsUpperEightDirections.loadFromConfig(section, fullName);
                            break;
                        case BRANCH_TWIGS_LOWER_EIGHT_DIRECTIONS:
                            blockData = SpecialBlockBranchTwigsLowerEightDirections.loadFromConfig(section, fullName);
                            break;*/
            case BRANCH_CONNECT -> SpecialBlockBranchConnect.loadFromConfig(section, fullName);
            case BRANCH_TRUNK_CONNECT -> SpecialBlockBranchTrunkConnect.loadFromConfig(section, fullName);
            case BRANCH_TRUNK_CONNECT_COMPLEX ->
                    SpecialBlockBranchTrunkConnectComplex.loadFromConfig(section, fullName);
            case BLOCK_CONNECT ->
//Logger.getGlobal().info("Block connect:"+itemKey);
                    SpecialBlockConnect.loadFromConfig(section, fullName);
            case DIAGONAL_CONNECT -> SpecialBlockDiagonalConnect.loadFromConfig(section, fullName);
            case BISECTED -> SpecialBlockBisected.loadFromConfig(section, fullName);
            case THREE_AXIS -> SpecialBlockThreeAxis.loadFromConfig(section, fullName);
            case TWO_AXIS -> SpecialBlockTwoAxis.loadFromConfig(section, fullName);
            case FIVE_FACES -> SpecialBlockFiveFaces.loadFromConfig(section, fullName);
            case SIX_FACES -> SpecialBlockSixFaces.loadFromConfig(section, fullName);
            case EIGHT_FACES -> SpecialBlockEightFaces.loadFromConfig(section, fullName);
            case SIX_FACES_XZ -> SpecialBlockSixFacesXZ.loadFromConfig(section, fullName);
            case FOUR_DIRECTIONS -> SpecialBlockFourDirections.loadFromConfig(section, fullName);
            case FOUR_DIRECTIONS_COMPLEX -> SpecialBlockFourDirectionsComplex.loadFromConfig(section, fullName);
            case OPEN_HALF_DOOR -> SpecialBlockOpenHalfDoor.loadFromConfig(section, fullName);
            case MATCH_ORIENTATION -> SpecialBlockMatchOrientation.loadFromConfig(section, fullName);
            case WALL_COMBI -> SpecialBlockWallCombi.loadFromConfig(section, fullName);
            case DOOR -> SpecialBlockDoor.loadFromConfig(section, fullName);
            case THIN_WALL -> SpecialBlockThinWall.loadFromConfig(section, fullName);
            case DOOR_VANILLA -> SpecialBlockVanillaDoor.loadFromConfig(section, fullName);
            case DOOR_FOUR_BLOCKS -> SpecialBlockDoorFourBlocks.loadFromConfig(section, fullName);
            case DOOR_THREE_BLOCKS -> SpecialBlockDoorThreeBlocks.loadFromConfig(section, fullName);
            case ITEM_BLOCK -> SpecialBlockItemBlock.loadFromConfig(section, fullName);
            case ITEM_BLOCK_TWO_DIRECTIONS -> SpecialBlockItemTwoDirections.loadFromConfig(section, fullName);
            case ITEM_BLOCK_FOUR_DIRECTIONS -> SpecialBlockItemFourDirections.loadFromConfig(section, fullName);
            case MOB_SPAWNER_BLOCK -> SpecialBlockMobSpawnerBlock.loadFromConfig(section, fullName);
            case BURNING_FURNACE -> SpecialBlockBurningFurnace.loadFromConfig(section, fullName);
            case DOUBLE_Y_BLOCK -> SpecialBlockDoubleY.loadFromConfig(section, fullName);
            case UPSHIFT -> SpecialBlockUpshift.loadFromConfig(section, fullName);
            case MULTI_FACE -> SpecialBlockMultiFace.loadFromConfig(section, fullName);
            case VANILLA -> SpecialBlockVanilla.loadFromConfig(section, fullName);
            case NONE -> SpecialBlockNone.loadFromConfig(section, fullName);
            case ITEM_FRAME -> SpecialBlockItemFrame.loadFromConfig(section, fullName);
            case SIGN -> SpecialBlockSign.loadFromConfig(section, fullName);
            case SIGN_POST -> SpecialBlockSignPost.loadFromConfig(section, fullName);
            case SIGN_WALL -> SpecialBlockSignWall.loadFromConfig(section, fullName);
            default -> null;
        };
    }

    private SpecialBlock(String id, BlockData data) {
        this(id, data, SpecialBlockType.BLOCK);
    }
    
    protected SpecialBlock(String id, BlockData data, SpecialBlockType type) {
        this.id = id;
        this.blockData = data;
        this.type = type;
    }

    public static SpecialBlock loadFromConfig(ConfigurationSection config, String id) {
        BlockData data;
        //convert old data
        if(!config.contains("blockData")) {
            Material blockMat =  Material.matchMaterial(config.getString("blockMaterial",""));
            byte rawData = (byte) config.getInt("dataValue", 0);
            data = LegacyMaterialUtil.getBlockData(blockMat, rawData);
            if(data == null) {
                return null;
            }
            config.set("blockData", data.getAsString());
            config.set("blockMaterial", null);
            config.set("dataValue",null);
        // end convert old data
        }else {
            try {
                String configData = config.getString("blockData", "");
                data = Bukkit.getServer().createBlockData(null,configData);
            } catch(IllegalArgumentException e) {
                return null;
            }
        }
        return new SpecialBlock(id, data);
    }

    public void loadNextBlock(ConfigurationSection config, String rpName) {
        nextBlockId = SpecialBlockInventoryData.fullName(rpName,config.getString("nextBlock",null));
    }

    public void loadPriority(ConfigurationSection config) {
        priority = config.getInt("priority",1);
    }

    public void loadBlockCollection(ConfigurationSection config, String rpName) {
        try {
            ConfigurationSection section = config.getConfigurationSection("collection");
            if(section != null) {
                section.getValues(false).forEach((key,entry)
                     -> collection.put(key, SpecialBlockInventoryData.fullName(rpName,(String) entry)));
            }
        } catch(ClassCastException ex) {
            Logger.getLogger(ArchitectPlugin.class.getName()).log(Level.WARNING, "Error while loading special block collection!", ex);
        }
    }
    
    public boolean hasIndirectCollection() {
        return collection.containsKey("indirect");
    }
    
    public SpecialBlock getCollectionBase() {
        return SpecialBlockInventoryData.getSpecialBlock(collection.get("indirect"));
    }
    
    public boolean hasCollection() {
        return !collection.isEmpty();
    }

    public boolean hasNextBlock() {
        return nextBlockId!=null && SpecialBlockInventoryData.getSpecialBlock(nextBlockId)!=null;
    }

    public SpecialBlock getNextBlock() {
        return SpecialBlockInventoryData.getSpecialBlock(nextBlockId);
    }

    public Block getBlock(Block clicked, BlockFace blockFace,
                          Location interactionPoint, Player player) {
        return clicked.getRelative(blockFace);
    }

    public void placeBlock(final Block blockPlace, final BlockFace blockFace, final Block clicked,
                           final Location interactionPoint, final Player player) {
        final BlockState state = getBlockState(blockPlace, clicked, blockFace, player, interactionPoint);
        /*new BukkitRunnable() {
            @Override
            public void run() {*/
                state.update(true, false);
                blockPlace.setBlockData(state.getBlockData(), false);
                //DevUtil.log("Special block place: ID "+state.getType()+" - DV "+state.getRawData());

                ChunkUpdateUtil.sendUpdates(blockPlace, player);
                /*final BlockState tempState = getBlockState(blockPlace, clicked, blockFace, player, interactionPoint);
                new BukkitRunnable() {
                    @Override
                    public void run() {
                        DevUtil.log("Special block place x2: loc: "+tempState.getX()+" "+tempState.getY()+" "+tempState.getZ()+" - ID "+state.getType()+" - DV "+state.getRawData());
                        //tempState.update(true, false);
                        blockPlace.setBlockData(tempState.getBlockData(),false);
                        // We just want VANILLA block type to connect.
                        // NoPhysicsListener.connectNoPhysicsBlocks(blockPlace);
                        ChunkUpdateUtil.sendUpdates(blockPlace, player);
                        //new ClientUpdateUtil().sendBlockPlaceUpdates(blockPlace,player);
                    }
                }.runTaskLater(ArchitectPlugin.getPluginInstance(), 1);
            }
        }.runTaskLater(ArchitectPlugin.getPluginInstance(), 1);*/
    }

    public void handleBlockBreak(BlockState state) {
        //Logger.getGlobal().info("BlockBreak: "+state.getBlockData());
        //Logger.getGlobal().info("BlockBreak: "+(state instanceof Waterlogged waterlogged));
        //Logger.getGlobal().info("BlockBreak: "+(((Waterlogged)state.getBlockData()).isWaterlogged()));
        if(state.getBlockData() instanceof Waterlogged waterlogged && waterlogged.isWaterlogged()) {
            state.setType(Material.AIR);
            state.update(true, false);
        }
    }

    private boolean isUnderwater(Block block) {
        return block.getType().equals(Material.WATER)
                || (block.getBlockData() instanceof Waterlogged waterlogged && waterlogged.isWaterlogged());
    }
    
    protected BlockState getBlockState(Block blockPlace, Block clicked, BlockFace blockFace,
                                       Player player, Location interactionPoint) {
        final BlockState state = blockPlace.getState();
        state.setBlockData(blockData);
        return state;
    }

    public boolean canPlace(Block blockPlace) {
        return blockPlace.isEmpty()
                || blockPlace.getType().equals(Material.SHORT_GRASS)
                || blockPlace.getType().equals(Material.FIRE)
                || blockPlace.getType().equals(Material.LAVA)
                || blockPlace.getType().equals(Material.WATER);
    }

    protected static Material matchMaterial(String identifier) {
        if(NumericUtil.isInt(identifier)) {
            return LegacyMaterialUtil.getMaterial(NumericUtil.getInt(identifier));
        } else {
            return Material.matchMaterial(identifier);
        }
    }
    
    protected static BlockFace getBlockFace(float yaw) {
        while(yaw>180) {
            yaw -=360;
        }
        while(yaw<-180) {
            yaw +=360;
        }
        if (yaw >= 135 || yaw < -135) {
            return BlockFace.NORTH;
        } else if (yaw >= 45) {
            return BlockFace.WEST;
        } else if (yaw >= -45) {
            return BlockFace.SOUTH;
        } else if (yaw >= -135) {
            return BlockFace.EAST;
        } else {
            return BlockFace.NORTH;
        }
    }

    protected static BlockFace getBlockFaceFine(float yaw) {
        while(yaw>180) {
            yaw -=360;
        }
        while(yaw<-180) {
            yaw +=360;
        }
        if ((yaw >= 157.5 || yaw < -157.5)) {
            return BlockFace.NORTH;
        } else if (yaw >= 112.5) {
            return BlockFace.NORTH_WEST;
        } else if (yaw >= 67.5) {
            return BlockFace.WEST;
        } else if (yaw >= 22.5) {
            return BlockFace.SOUTH_WEST;
        } else if (yaw >= -22.5) {
            return BlockFace.SOUTH;
        } else if (yaw >= -67.5) {
            return BlockFace.SOUTH_EAST;
        } else if (yaw >= -112.5) {
            return BlockFace.EAST;
        } else {
            return BlockFace.NORTH_EAST;
        }
    }

    protected static BlockFace getBlockFaceSuperFine(float yaw) {
        while(yaw>180) {
            yaw -=360;
        }
        while(yaw<-180) {
            yaw +=360;
        }
        if ((yaw >= 168.75)) {
            return BlockFace.NORTH;
        } else if (yaw >= 146.25) {
            return BlockFace.NORTH_NORTH_WEST;
        } else if (yaw >= 123.75) {
            return BlockFace.NORTH_WEST;
        } else if (yaw >= 101.25) {
            return BlockFace.WEST_NORTH_WEST;
        } else if (yaw >= 78.75) {
            return BlockFace.WEST;
        } else if (yaw >= 56.25) {
            return BlockFace.WEST_SOUTH_WEST;
        } else if (yaw >= 33.75) {
            return BlockFace.SOUTH_WEST;
        } else if (yaw >= 11.25) {
            return BlockFace.SOUTH_SOUTH_WEST;
        } else if (yaw >= -11.25) {
            return BlockFace.SOUTH;
        } else if (yaw >= -33.75) {
            return BlockFace.SOUTH_SOUTH_EAST;
        } else if (yaw >= -56.25) {
            return BlockFace.SOUTH_EAST;
        } else if (yaw >= -78.75) {
            return BlockFace.EAST_SOUTH_EAST;
        } else if (yaw >= -101.25) {
            return BlockFace.EAST;
        } else if (yaw >= -123.75) {
            return BlockFace.EAST_NORTH_EAST;
        } else if (yaw >= -146.25) {
            return BlockFace.NORTH_EAST;
        } else if (yaw >= -168.75) {
            return BlockFace.NORTH_NORTH_EAST;
        } else {
            return BlockFace.NORTH;
        }
    }

    public static BlockFace rotateBlockFace90(BlockFace blockFace) {
        return switch (blockFace) {
            case NORTH -> BlockFace.EAST;
            case EAST -> BlockFace.SOUTH;
            case SOUTH -> BlockFace.WEST;
            case WEST -> BlockFace.NORTH;
            case NORTH_EAST -> BlockFace.SOUTH_EAST;
            case NORTH_WEST -> BlockFace.NORTH_EAST;
            case SOUTH_EAST -> BlockFace.SOUTH_WEST;
            case SOUTH_WEST -> BlockFace.NORTH_WEST;
            case WEST_NORTH_WEST -> BlockFace.NORTH_NORTH_EAST;
            case NORTH_NORTH_WEST -> BlockFace.EAST_NORTH_EAST;
            case NORTH_NORTH_EAST -> BlockFace.EAST_SOUTH_EAST;
            case EAST_NORTH_EAST -> BlockFace.SOUTH_SOUTH_EAST;
            case EAST_SOUTH_EAST -> BlockFace.SOUTH_SOUTH_WEST;
            case SOUTH_SOUTH_EAST -> BlockFace.WEST_SOUTH_WEST;
            case SOUTH_SOUTH_WEST -> BlockFace.WEST_NORTH_WEST;
            case WEST_SOUTH_WEST -> BlockFace.NORTH_NORTH_WEST;
            default -> blockFace;
        };
    }

    public boolean matches(Block block) {
        return block.getBlockData().matches(blockData);
    }

    public boolean matches(BlockData data) { return data.matches(blockData);}

    public String getId() {
        return id;
    }

    public BlockData getBlockData() {
        return blockData;
    }

    public SpecialBlockType getType() {
        return type;
    }

    public Map<String, String> getCollection() {
        return collection;
    }

    public int getPriority() {
        return priority;
    }

    public boolean isAllowedBlockData(BlockData data) {
        return true;
    }

    public boolean isEditOnSneaking() {
        return false;
    }
}
