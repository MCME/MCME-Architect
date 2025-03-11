package com.mcmiddleearth.architect.specialBlockHandling.specialBlocks;

import com.mcmiddleearth.architect.ArchitectPlugin;
import com.mcmiddleearth.architect.PluginData;
import com.mcmiddleearth.architect.chunkUpdate.ChunkUpdateUtil;
import com.mcmiddleearth.architect.specialBlockHandling.SpecialBlockType;
import com.mcmiddleearth.util.DevUtil;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Directional;
import org.bukkit.block.data.MultipleFacing;
import org.bukkit.block.data.Waterlogged;
import org.bukkit.block.data.type.Fence;
import org.bukkit.block.data.type.Wall;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.logging.Logger;

public class SpecialBlockBranch extends SpecialBlock {

    private static final SpecialBlockOrientable.Orientation[] eightFaces = new SpecialBlockOrientable.Orientation[] {
            new SpecialBlockOrientable.Orientation(BlockFace.SOUTH,"South"),
            new SpecialBlockOrientable.Orientation(BlockFace.SOUTH_WEST,"South_West"),
            new SpecialBlockOrientable.Orientation(BlockFace.WEST,"West"),
            new SpecialBlockOrientable.Orientation(BlockFace.NORTH_WEST,"North_West"),
            new SpecialBlockOrientable.Orientation(BlockFace.NORTH,"North"),
            new SpecialBlockOrientable.Orientation(BlockFace.NORTH_EAST,"North_East"),
            new SpecialBlockOrientable.Orientation(BlockFace.EAST,"East"),
            new SpecialBlockOrientable.Orientation(BlockFace.SOUTH_EAST,"South_East")
    };
    private static final SpecialBlockOrientable.Orientation[] fourFaces = new SpecialBlockOrientable.Orientation[] {
            new SpecialBlockOrientable.Orientation(BlockFace.SOUTH,"South"),
            new SpecialBlockOrientable.Orientation(BlockFace.WEST,"West"),
            new SpecialBlockOrientable.Orientation(BlockFace.NORTH,"North"),
            new SpecialBlockOrientable.Orientation(BlockFace.EAST,"East")
    };

    private static final int thin = 0;
    private static final int thick = 1;
    private static final int steep = 0;
    private static final int diagonal = 1;

    private static final int vertical = 2;
    private static final int horizontal = 3;


    private final BlockData[][][] blockDataSloped;
    private final BlockData[][] blockDataHorizontal;
    private final BlockData blockDataWall;
    private final BlockData blockDataVerticalThin;

    private int width;
    private int slope;

    public static SpecialBlockBranch loadFromConfig(ConfigurationSection config, String id) {
        BlockData[][] horizontal = new BlockData[2][fourFaces.length];
        BlockData[][][] sloped = new BlockData[2][2][eightFaces.length];
        for(int j = 0; j < fourFaces.length; j++) {
            BlockData data = Bukkit.createBlockData(Material.ACACIA_FENCE);
            for(int k = 0; k<4; k++) {
                ((Fence) data).setFace(fourFaces[k].face, k == j);
            }
            horizontal[thin][j] = data;
            data = Bukkit.createBlockData(Material.TUBE_CORAL_WALL_FAN);
            ((Waterlogged)data).setWaterlogged(false);
            ((Directional) data).setFacing(fourFaces[j].face);
            horizontal[thick][j] = data;
        }
        BlockData straightData = Bukkit.createBlockData("minecraft:prismarine_wall[east=none,north=none,south=tall,up=false,waterlogged=false,west=none]");
        BlockData diagonalData = Bukkit.createBlockData("minecraft:prismarine_wall[east=none,north=none,south=tall,up=false,waterlogged=false,west=tall]");
        for(int k = 0; k < eightFaces.length; k+=2) {
            sloped[thin][steep][k] = straightData;
            sloped[thin][steep][k+1] = diagonalData;
            straightData = rotateData(straightData);
            diagonalData = rotateData(diagonalData);
        }
        straightData = Bukkit.createBlockData("minecraft:andesite_wall[east=none,north=none,south=tall,up=false,waterlogged=false,west=none]");
        diagonalData = Bukkit.createBlockData("minecraft:prismarine_wall[east=none,north=none,south=low,up=false,waterlogged=false,west=none]");
        for(int k = 0; k < eightFaces.length; k+=2) {
            sloped[thin][diagonal][k] = straightData;
            sloped[thin][diagonal][k+1] = diagonalData;
            straightData = rotateData(straightData);
            diagonalData = rotateData(diagonalData);
        }
        straightData = Bukkit.createBlockData("minecraft:prismarine_wall[east=none,north=none,south=low,up=true,waterlogged=false,west=none]");
        diagonalData = Bukkit.createBlockData("minecraft:prismarine_wall[east=none,north=none,south=tall,up=true,waterlogged=false,west=tall]");
        for(int k = 0; k < eightFaces.length; k+=2) {
            sloped[thick][steep][k] = straightData;
            sloped[thick][steep][k+1] = diagonalData;
            straightData = rotateData(straightData);
            diagonalData = rotateData(diagonalData);
        }
        straightData = Bukkit.createBlockData("minecraft:andesite_wall[east=none,north=none,south=low,up=false,waterlogged=false,west=none]");
        diagonalData = Bukkit.createBlockData("minecraft:prismarine_wall[east=none,north=none,south=tall,up=false,waterlogged=false,west=low]");
        for(int k = 0; k < eightFaces.length; k+=2) {
            sloped[thick][diagonal][k] = straightData;
            sloped[thick][diagonal][k+1] = diagonalData;
            straightData = rotateData(straightData);
            diagonalData = rotateData(diagonalData);
        }

        BlockData wall = Bukkit.createBlockData("minecraft:andesite_wall");
        BlockData verticalThin = Bukkit.createBlockData(Material.ACACIA_FENCE);
        int width = config.getInt("width", -1);
        int slope = config.getInt("slope", -1);
        return new SpecialBlockBranch(id, horizontal, sloped, wall, verticalThin, width, slope, SpecialBlockType.BRANCH);
    }

    protected SpecialBlockBranch(String id, BlockData[][] horizontal, BlockData[][][] sloped, BlockData wall,
                                 BlockData verticalThin, int width, int slope, SpecialBlockType type) {
        super(id, Material.AIR.createBlockData(),type);
        blockDataWall = wall;
        blockDataHorizontal = horizontal;
        blockDataSloped = sloped;
        blockDataVerticalThin = verticalThin;
        this.slope = slope;
        this.width = width;
    }

    private BlockState getBlockState(Block blockPlace, BlockFace playerFace, int width, int slope) {
        BlockData blockData;
        if(slope == horizontal) {
            blockData = getBlockData(fourFaces, blockDataHorizontal[width], playerFace);
        } else if(slope == vertical) {
            blockData = blockDataWall;
        } else {
            blockData = getBlockData(eightFaces, blockDataSloped[width][slope], playerFace);
        }
        final BlockState state = blockPlace.getState();
        if(blockData!=null) {
            state.setBlockData(blockData);
        } else {
            DevUtil.log("No BlockData for: blockFace="+playerFace);
        }
        return state;
    }

    @Override
    public Block getBlock(Block clicked, BlockFace blockFace,
                          Location interactionPoint, Player player) {
        Block block = clicked;
        int slope = getSlope(player.getLocation());
        BlockFace playerFace = getPlayerFace(player.getLocation());
        if(slope != vertical) {
            block = block.getRelative(playerFace);
        }
Logger.getGlobal().info("Interaction Point: "+interactionPoint);
        if(slope != horizontal || interactionPoint.getY()-interactionPoint.getBlockY()>0.5) {
            block = block.getRelative(BlockFace.UP);
        }
        if(slope == steep) {
            block = block.getRelative(BlockFace.UP);
            BlockFace diagonal = getDiagonalSlopedMainOrientation(clicked.getBlockData());
            if(diagonal!=null) {
                block = block.getRelative(diagonal);
            }
        } else if(slope == diagonal) {
            BlockFace steep = getSteepSlopedMainOrientation(clicked.getBlockData());
            if(steep!=null) {
                block = block.getRelative(playerFace.getOppositeFace());
            }
        }
        return block;
    }

    /*private Block getClicked(Block blockPlace, Location interactionPoint, Player player) {
        Block clicked = blockPlace;
        int slope = getSlope(player.getLocation());
        if(slope != vertical) {
            clicked = clicked.getRelative(getPlayerFace(player.getLocation()).getOppositeFace(),1);
        }
        if(slope != horizontal || interactionPoint.getY()-interactionPoint.getBlockY()>0.5) {
            clicked = clicked.getRelative(BlockFace.DOWN);
        }
        if(slope == steep) {
            clicked = clicked.getRelative(BlockFace.DOWN);
        }
        return clicked;
    }*/

    @Override
    public void handleBlockBreak(BlockState state) {
        Logger.getGlobal().info("Handle block break!");
        Block block = state.getBlock();
        if(!state.getBlockData().equals(block.getBlockData())) {
Logger.getGlobal().info("Found Branch block break!");
            // find out if we break a block with vertical part
            if(isVertical(state.getBlockData())) {
                Block connection = block.getRelative(BlockFace.DOWN, 1);
                if (connection.getBlockData().matches(blockDataWall)) {
Logger.getGlobal().info("Found vertical connection: " + connection.getLocation());
                    Wall wall = (Wall) connection.getBlockData();
                    wall.setUp(false);
                    if(PluginData.getOrCreateWorldConfig(connection.getWorld().getName()).isAllowedBlock(wall)) {
                        connection.setBlockData(wall, false);
                    }
                }
            } else {
                //find out if we break a block with diagonal slope and main orientation (north, east, south or west)
                BlockFace direction = getDiagonalSlopedMainOrientation(state.getBlockData());
                if (direction != null) {
Logger.getGlobal().info("Direction: " + direction.name());
                    Block connection = block.getRelative(direction.getOppositeFace(), 1).getRelative(BlockFace.DOWN, 1);
                    if (connection.getBlockData().matches(blockDataWall)) {
Logger.getGlobal().info("Found connection: " + connection.getLocation());
                        Wall wall = (Wall) connection.getBlockData();
                        wall.setHeight(direction, Wall.Height.NONE);
                        if(PluginData.getOrCreateWorldConfig(connection.getWorld().getName()).isAllowedBlock(wall)) {
                            connection.setBlockData(wall, false);
                        }
                    }
                }
            }
        }
    }

    private boolean isVertical(BlockData data) {
        if (data.matches(blockDataWall)) {
            Wall wall = (Wall) data;
            return wall.isUp();
        }
        return false;
    }

    private BlockFace getDiagonalSlopedMainOrientation(BlockData search) {
        for(int i = thin; i <=thick; i++) {
            for (int j = 0; j < eightFaces.length; j += 2) {
                if(search.matches(blockDataSloped[i][diagonal][j])) {
                    return eightFaces[j].face;
                }
            }
        }
        return null;
    }

    private BlockFace getSteepSlopedMainOrientation(BlockData search) {
        for(int i = thin; i <=thick; i++) {
            for (int j = 0; j < eightFaces.length; j += 2) {
                if(search.matches(blockDataSloped[i][steep][j])) {
                    return eightFaces[j].face;
                }
            }
        }
        return null;
    }

    @Override
    public boolean isEditOnSneaking() { return width>=0; }

    @Override
    public void placeBlock(final Block blockPlace, final BlockFace blockFace, Block clicked,
                           final Location interactionPoint, final Player player) {
        //Block clicked = getClicked(blockPlace, interactionPoint, player);
        if(width >= 0 && player.isSneaking()) {
            if(clicked.getBlockData().matches(blockDataWall)) {
                SpecialBlockDiagonalConnect.editDiagonal(blockPlace, clicked, player, this);
            }
        } else {
            Location playerLoc = player.getLocation();
            BlockFace playerFace = getPlayerFace(playerLoc);
            Logger.getGlobal().info("blockFace: " + blockFace.name() + " playerFace: " + playerFace.name());
            Logger.getGlobal().info("player loc: " + player.getLocation());
            Logger.getGlobal().info("BlockPlace: " + blockPlace.getLocation());
            Logger.getGlobal().info("Clicked: " + clicked.getLocation());
            int width = this.width;
            if (width < 0) {
                if (isThin(clicked, playerFace) || player.isSneaking()) {
                    width = thin;
                } else {
                    width = thick;
                }
            }
            int slope = getSlope(playerLoc);

            final BlockState state = getBlockState(blockPlace, playerFace, width, slope);
            final int finalWidth = width;
            final int finalSlope = slope;
            Logger.getGlobal().info("config Width: " + this.width + " ConfigSlope: " + this.slope);
            Logger.getGlobal().info("Width: " + width + " Slope: " + slope);
            new BukkitRunnable() {
                @Override
                public void run() {
                    blockPlace.setBlockData(state.getBlockData(), false);
                    final BlockState tempState = getBlockState(blockPlace, playerFace, finalWidth, finalSlope);
                    new BukkitRunnable() {
                        @Override
                        public void run() {
                            blockPlace.setBlockData(tempState.getBlockData(), false);
                            ChunkUpdateUtil.sendUpdates(blockPlace, player);
                        }
                    }.runTaskLater(ArchitectPlugin.getPluginInstance(), 1);
                }
            }.runTaskLater(ArchitectPlugin.getPluginInstance(), 1);

            if (clicked.getBlockData().matches(blockDataWall)) {
                Logger.getGlobal().info("Detected fork!");
                Wall wall = (Wall) clicked.getBlockData();
                Wall.Height height = (width == thin ? Wall.Height.TALL : Wall.Height.LOW);
                if (slope == diagonal && isMainDirection(playerFace)) {
                    wall.setHeight(playerFace, height);
                    if (PluginData.getOrCreateWorldConfig(clicked.getWorld().getName()).isAllowedBlock(wall)) {
                        clicked.setBlockData(wall, false);
                    }
                } else if (slope == steep && isMainDirection(playerFace)) {
                    wall.setHeight(playerFace, Wall.Height.NONE);
                    if (PluginData.getOrCreateWorldConfig(clicked.getWorld().getName()).isAllowedBlock(wall)) {
                        clicked.setBlockData(wall, false);
                    }
                } else if (slope == vertical) {
                    wall.setUp(true);
                    if (PluginData.getOrCreateWorldConfig(clicked.getWorld().getName()).isAllowedBlock(wall)) {
                        clicked.setBlockData(wall, false);
                    }
                }
            }
        }
    }

    private int getSlope(Location playerLoc) {
        int slope = this.slope;
        if (Math.abs(playerLoc.getPitch()) < 22.5) {
            slope = horizontal;
        } else if (Math.abs(playerLoc.getPitch()) > 80) {
            slope = vertical;
        } else if (slope < 0) {
            if (Math.abs(playerLoc.getPitch()) > 67.5) {
                slope = steep;
            } else {
                slope = diagonal;
            }
        }
        return slope;
    }

    private boolean isThin(Block block, BlockFace blockFace) {
        BlockData search = block.getBlockData();
        if(search.matches(blockDataVerticalThin)) return true;
        if(search.matches(blockDataWall)) {
            Wall wall = (Wall) search;
            if(wall.isUp()) return false;
            if(wall.getHeight(BlockFace.NORTH).equals(Wall.Height.LOW)) return false;
            if(wall.getHeight(BlockFace.EAST).equals(Wall.Height.LOW)) return false;
            if(wall.getHeight(BlockFace.SOUTH).equals(Wall.Height.LOW)) return false;
            if(wall.getHeight(BlockFace.WEST).equals(Wall.Height.LOW)) return false;
            return true;
        }
        for(BlockData data: blockDataHorizontal[thin]) {
            if(search.equals(data)) return true;
        }
        for(int j = steep; j <= diagonal; j++) {
            for(BlockData data: blockDataSloped[thin][j]) {
                if (search.equals(data)) return true;
            }
        }
        return false;
    }

    @Override
    public boolean matches(Block block) {
        BlockData search = block.getBlockData();
        if(search.matches(blockDataVerticalThin)) return true;
        if(search.matches(blockDataWall)) return true;
        for(int i = thin; i<= thick; i++) {
            for(BlockData data: blockDataHorizontal[i]) {
                if(search.equals(data)) return true;
            }
            for(int j = steep; j <= diagonal; j++) {
                for(BlockData data: blockDataSloped[i][j]) {
                    if (search.equals(data)) return true;
                }
            }
        }
        return false;
    }

    private static boolean isMainDirection(BlockFace face) {
        switch(face) {
            case EAST:
            case WEST:
            case SOUTH:
            case NORTH:
                return true;
            default:
                return false;
        }
    }

    private static BlockData rotateData(BlockData data) {
        if(data instanceof MultipleFacing) {
            MultipleFacing multi = (MultipleFacing) data;
            MultipleFacing result = (MultipleFacing) data.clone();
            result.setFace(BlockFace.NORTH, multi.hasFace(BlockFace.WEST));
            result.setFace(BlockFace.EAST, multi.hasFace(BlockFace.NORTH));
            result.setFace(BlockFace.SOUTH, multi.hasFace(BlockFace.EAST));
            result.setFace(BlockFace.WEST, multi.hasFace(BlockFace.SOUTH));
            return result;
        } else if(data instanceof Wall) {
            Wall multi = (Wall) data;
            Wall result = (Wall) data.clone();
            result.setHeight(BlockFace.NORTH, multi.getHeight(BlockFace.WEST));
            result.setHeight(BlockFace.EAST, multi.getHeight(BlockFace.NORTH));
            result.setHeight(BlockFace.SOUTH, multi.getHeight(BlockFace.EAST));
            result.setHeight(BlockFace.WEST, multi.getHeight(BlockFace.SOUTH));
            return result;
        }
        return data;
    }

    private BlockFace rotateFace(BlockFace face) {
        switch(face) {
            case NORTH:
                return BlockFace.EAST;
            case EAST:
                return BlockFace.SOUTH;
            case SOUTH:
                return BlockFace.WEST;
            default:
                return BlockFace.NORTH;
        }
    }

    private BlockData getBlockData(SpecialBlockOrientable.Orientation[] orientations, BlockData[] blockDatas, BlockFace face) {
        for(int i=0; i<orientations.length; i++) {
            if(orientations[i].face.equals(face)) {
                return blockDatas[i];
            }
        }
        return null;
    }

    private BlockFace getPlayerFace(Location playerLoc) {
        BlockFace playerFace;
        if(playerLoc.getPitch()>22.5 && playerLoc.getPitch()<-22.5) {
            playerFace = getBlockFace(playerLoc.getYaw());
        } else {
            playerFace = getBlockFaceFine(playerLoc.getYaw());
        }
        return playerFace.getOppositeFace();
    }


}
