package com.mcmiddleearth.architect.specialBlockHandling.specialBlocks;

import com.mcmiddleearth.architect.specialBlockHandling.SpecialBlockType;
import com.mcmiddleearth.architect.specialBlockHandling.data.SpecialBlockInventoryData;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.Wall;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.logging.Logger;

public class SpecialBlockBranchConnect extends SpecialBlockOrientableVariants implements IBranch {

    private final Material material;

    private final BlockData[] allowedBlockData;

    private final boolean limited;

    protected SpecialBlockBranchConnect(String id, BlockData[][] data, String[] variants,
                                        SpecialBlockOrientable.Orientation[] orientations, boolean limited) {
        super(id, data, variants, orientations, SpecialBlockType.BRANCH_CONNECT);
        material = data[0][0].getMaterial();
        this.limited = limited;
        allowedBlockData = new BlockData[] {
                Bukkit.createBlockData(material, "[north=none,west=none,south=none,east=none,up=true,waterlogged=false]"),
                Bukkit.createBlockData(material, "[north=tall,west=none,south=none,east=none,up=false,waterlogged=false]"),
                Bukkit.createBlockData(material, "[north=tall,west=none,south=none,east=none,up=true,waterlogged=false]"),
                Bukkit.createBlockData(material, "[north=low,west=none,south=none,east=none,up=false,waterlogged=false]"),
                Bukkit.createBlockData(material, "[north=low,west=none,south=tall,east=none,up=false,waterlogged=false]"),
                Bukkit.createBlockData(material, "[north=tall,west=none,south=tall,east=none,up=true,waterlogged=false]"),
                Bukkit.createBlockData(material, "[north=tall,west=none,south=tall,east=none,up=false,waterlogged=false]"),
                Bukkit.createBlockData(material, "[north=tall,west=low,south=none,east=none,up=false,waterlogged=false]"),
                Bukkit.createBlockData(material, "[north=tall,west=tall,south=none,east=none,up=false,waterlogged=false]"),
                Bukkit.createBlockData(material, "[north=tall,west=tall,south=none,east=none,up=true,waterlogged=false]"),
                Bukkit.createBlockData(material, "[north=low,west=tall,south=none,east=none,up=false,waterlogged=false]"),
                Bukkit.createBlockData(material, "[north=tall,west=tall,south=none,east=tall,up=false,waterlogged=false]"),
                Bukkit.createBlockData(material, "[north=tall,west=tall,south=none,east=tall,up=true,waterlogged=false]"),
                Bukkit.createBlockData(material, "[north=low,west=tall,south=none,east=tall,up=false,waterlogged=false]"),
                Bukkit.createBlockData(material, "[north=tall,west=low,south=none,east=tall,up=false,waterlogged=false]"),
                Bukkit.createBlockData(material, "[north=tall,west=tall,south=none,east=low,up=false,waterlogged=false]"),
                Bukkit.createBlockData(material, "[north=tall,west=tall,south=tall,east=tall,up=false,waterlogged=false]"),
                Bukkit.createBlockData(material, "[north=tall,west=tall,south=tall,east=tall,up=true,waterlogged=false]"),
                Bukkit.createBlockData(material, "[north=low,west=tall,south=tall,east=tall,up=false,waterlogged=false]")
        };
    }

    @Override
    public int getPriority() {
        return 10;
    }

    public static SpecialBlock loadFromConfig(ConfigurationSection config, String id) {
        BlockData data;
        boolean limited;
        try {
            String configData = config.getString("blockData", "");
            data = Bukkit.getServer().createBlockData(null,configData);
            limited = config.getBoolean("limited",false);
        } catch(IllegalArgumentException e) {
            return null;
        }
        return new SpecialBlockBranchConnect(id, new BlockData[][]{{data}}, new String[]{"base"},
                   new SpecialBlockOrientable.Orientation[]{new SpecialBlockOrientable.Orientation(BlockFace.UP,"blockData")},
                   limited);
    }

    @Override
    public Block getBlock(Block clicked, BlockFace blockFace, Location interactionPoint, Player player) {
        Block target = super.getBlock(clicked, blockFace, interactionPoint, player);
        return getBranchBlock(target, clicked, blockFace, interactionPoint,
                player, getBlockFace(player.getLocation().getYaw()));
    }

    @Override
    public boolean isThin(Block clicked, Player player, Location interactionPoint) {
        BlockData blockData = clicked.getBlockData();
        if(SpecialBlockInventoryData.getSpecialBlockDataFromBlock(clicked, player, SpecialBlockBranchConnect.class)!=null
                && blockData instanceof Wall wall) {
            Shift shift = getUpper(null, clicked, player, interactionPoint);
            if(shift.getX()==-1 && shift.getZ()==0) {
                return wall.getHeight(BlockFace.WEST).equals(Wall.Height.TALL);
            } else if(shift.getX()==1 && shift.getZ()==0) {
                return wall.getHeight(BlockFace.EAST).equals(Wall.Height.TALL);
            } else if(shift.getX()==0 && shift.getZ()==-1) {
                return wall.getHeight(BlockFace.NORTH).equals(Wall.Height.TALL);
            } else if(shift.getX()==0 && shift.getZ()==1) {
                return wall.getHeight(BlockFace.SOUTH).equals(Wall.Height.TALL);
            }
        }
        return false;
    }

    @Override
    protected void cycleVariant(Block blockPlace, Block clicked, Player player, Location interactionPoint) {
        SpecialBlockDiagonalConnect.editDiagonal(blockPlace,clicked,player,this);
    }

    private BlockFace getBlockFaceFromShift(Shift shift) {
        if(shift.getX()>0) {
            return BlockFace.EAST;
        } else if(shift.getX()<0) {
            return BlockFace.WEST;
        } else if(shift.getZ()>0) {
            return BlockFace.SOUTH;
        } else if(shift.getZ()<0) {
            return BlockFace.NORTH;
        } else {
            return BlockFace.UP;
        }
    }

    @Override
    protected int getVariant(Block blockPlace, Block clicked, BlockFace blockFace, Player player, Location interactionPoint) {
//Logger.getGlobal().info("GET VARIANT connect ");
        return 0;
    }

    @Override
    public Shift getLower(BlockFace orientation, Block clicked, Player player, Location interactionPoint) {
        return new Shift(0,0,0);
    }

    @Override
    public Shift getUpper(BlockFace orientation, @NotNull Block clicked, Player player, Location interactionPoint) {
//Logger.getGlobal().info("Get upper!");
//Logger.getGlobal().info("Clicked: " + clicked.getBlockData().getMaterial()+ " loc: ("+ clicked.getLocation().getBlockX()+" "+ clicked.getLocation().getBlockY()+" "+ clicked.getLocation().getBlockZ()+")");
//Logger.getGlobal().info("Interaction: " + interactionPoint.getX()+" "+interactionPoint.getY()+" "+interactionPoint.getZ());
//Logger.getGlobal().info("Interaction block: " + clicked.getX()+ " "+clicked.getY()+" "+clicked.getZ());
//if(orientation!=null) Logger.getGlobal().info("Orientation: " + orientation.name());
        if(clicked.getBlockData() instanceof Wall wall && !wall.isUp()) {
//Logger.getGlobal().info("is Wall and no up");
            double xRelative = interactionPoint.getX()-clicked.getX();
            double zRelative = interactionPoint.getZ()-clicked.getZ();
            if(zRelative>=xRelative) {
                if(zRelative<1-xRelative) {
                    return new Shift((!wall.getHeight(BlockFace.WEST).equals(Wall.Height.NONE)?-1:0),0,
                                     (!wall.getHeight(BlockFace.WEST).equals(Wall.Height.NONE)?0:(zRelative>0.5?1:-1)));
                            //(-1,0,0)
                } else {
                    return new Shift((!wall.getHeight(BlockFace.SOUTH).equals(Wall.Height.NONE)?0:(xRelative>0.5?1:-1)),0,
                                     (!wall.getHeight(BlockFace.SOUTH).equals(Wall.Height.NONE)?1:0));
                            //(0,0,1);
                }
            } else {
                if(zRelative<1-xRelative) {
                    return new Shift((!wall.getHeight(BlockFace.NORTH).equals(Wall.Height.NONE)?0:(xRelative>0.5?1:-1)),0,
                                     (!wall.getHeight(BlockFace.NORTH).equals(Wall.Height.NONE)?-1:0));
                            //(0,0,-1);
                } else {
                    return new Shift((!wall.getHeight(BlockFace.EAST).equals(Wall.Height.NONE)?1:0),0,
                                     (!wall.getHeight(BlockFace.EAST).equals(Wall.Height.NONE)?0:(zRelative>0.5?1:-1)));
                            //(1,0,0);
                }
            }
        /*} else if(clicked.getBlockData() instanceof Wall wall) {
            int partX = getPart(clicked.getX(), interactionPoint.getX());
            int partZ = getPart(clicked.getZ(), interactionPoint.getZ());*/

//Logger.getGlobal().info("Wall and has up")
        } else {
            return new Shift(getPart(clicked.getX(), interactionPoint.getX()), 0,
                    getPart(clicked.getZ(), interactionPoint.getZ()));
        }
    }

    private int getPart(int blockCoordinate, double coordinate) {
        double part = ((coordinate- blockCoordinate)*4);
//Logger.getGlobal().info("Block: "+blockCoordinate+" Coord: "+coordinate+" Part: "+part);
        return (part>3?1:(part>=1?0:-1));
    }

    @Override
    public boolean isDiagonal() {
        return true;
    }

    @Override
    public BlockFace getDownwardOrientation(BlockFace blockFace) {
        return blockFace;
    }

    public boolean matches(Block block) {
//Logger.getGlobal().info("SpecialBlockBranchConnect.matches(block): this:"+material);
//Logger.getGlobal().info("SpecialBlockBranchConnect.matches: that:"+block.getType());
        return block.getType().equals(material) && isAllowedBlockData(block.getBlockData());
    }

    @Override
    public boolean matches(BlockData data) {
//Logger.getGlobal().info("SpecialBlockBranchConnect.matches: this:"+material);
//Logger.getGlobal().info("SpecialBlockBranchConnect.matches: that:"+data.getMaterial());
            return data.getMaterial().equals(material) && isAllowedBlockData(data);
    }

    @Override
    public boolean isAllowedBlockData(BlockData blockData) {
//Logger.getGlobal().info("Limited: "+limited);
        if(!limited) return true;
        if(blockData instanceof Wall) {
            Wall search = (Wall) blockData.clone();
            for (int i = 0; i < 4; i++) {
                for (BlockData allowed : allowedBlockData) {
                    if(search.equals(allowed)) {
                        return true;
                    }
                }
                Wall rotated = (Wall) search.clone();
                rotated.setHeight(BlockFace.NORTH,search.getHeight(BlockFace.EAST));
                rotated.setHeight(BlockFace.EAST,search.getHeight(BlockFace.SOUTH));
                rotated.setHeight(BlockFace.SOUTH,search.getHeight(BlockFace.WEST));
                rotated.setHeight(BlockFace.WEST,search.getHeight(BlockFace.NORTH));
                search = rotated;
            }
        }
        return false;
    }


}
