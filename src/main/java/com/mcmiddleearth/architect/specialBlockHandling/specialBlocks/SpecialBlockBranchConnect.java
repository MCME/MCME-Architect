package com.mcmiddleearth.architect.specialBlockHandling.specialBlocks;

import com.mcmiddleearth.architect.ArchitectPlugin;
import com.mcmiddleearth.architect.PluginData;
import com.mcmiddleearth.architect.blockData.BlockDataManager;
import com.mcmiddleearth.architect.blockData.attributes.Attribute;
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
import org.bukkit.scheduler.BukkitRunnable;

import java.util.logging.Logger;

public class SpecialBlockBranchConnect extends SpecialBlockOrientableVariants implements IBranch {

    private Material material;

    protected SpecialBlockBranchConnect(String id, BlockData[][] data, String[] variants,
                                        SpecialBlockOrientable.Orientation[] orientations) {
        super(id, data, variants, orientations, SpecialBlockType.BRANCH_CONNECT);
        material = data[0][0].getMaterial();
    }

    @Override
    public int getPriority() {
        return 10;
    }

    public static SpecialBlock loadFromConfig(ConfigurationSection config, String id) {
        BlockData data;
        try {
            String configData = config.getString("blockData", "");
            data = Bukkit.getServer().createBlockData(null,configData);
        } catch(IllegalArgumentException e) {
            return null;
        }
        return new SpecialBlockBranchConnect(id, new BlockData[][]{{data}}, new String[]{"base"},
                   new SpecialBlockOrientable.Orientation[]{new SpecialBlockOrientable.Orientation(BlockFace.UP,"blockData")});
    }

    @Override
    public Block getBlock(Block clicked, BlockFace blockFace, Location interactionPoint, Player player) {
        Block target = super.getBlock(clicked, blockFace, interactionPoint, player);
        return getBranchBlock(target, clicked, blockFace, interactionPoint,
                player, getBlockFace(player.getLocation().getYaw()));
    }

    @Override
    public boolean isThin(Block block, Player player, Location interactionPoint) {
        BlockData blockData = block.getBlockData();
        if(SpecialBlockInventoryData.getSpecialBlockDataFromBlock(block, player, SpecialBlockBranchConnect.class)!=null
                && blockData instanceof Wall wall) {
            Shift shift = getUpper(null, null, player, interactionPoint);
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

    /*@Override
    public void placeBlock(final Block blockPlace, final BlockFace blockFace, Block clicked,
                           final Location interactionPoint, final Player player) {
        if(!player.isSneaking()) {
            super.placeBlock(blockPlace,blockFace,clicked,interactionPoint,player);
        } else {
            if(matches(clicked.getBlockData())) {
                SpecialBlockDiagonalConnect.editDiagonal(blockPlace, clicked, player);
            }
        }
    }*/


    @Override
    protected void cycleVariant(Block blockPlace, Block clicked, Player player, Location interactionPoint) {
        SpecialBlockDiagonalConnect.editDiagonal(blockPlace,clicked,player);
//Logger.getGlobal().info("cycleVariant");
    /*    if(player.getLocation().getPitch()<0) return; // no edit when looking from below

        String searchFor = getBlockFaceFromShift(getUpper(null, player, interactionPoint)).name();
        BlockDataManager manager = new BlockDataManager();
        //String searchFor = getBlockFaceFromLoc(player.getLocation(),true).name();
        BlockData data = clicked.getBlockData();
        Attribute attrib = manager.getAttribute(data);
        int i = 0;
        if (attrib != null) {
            int countAttribs = manager.countAttributes(data);
            while(!attrib.getName().equalsIgnoreCase(searchFor) && i < countAttribs) {
                manager.nextAttribute(data);
                attrib = manager.getAttribute(data);
                i++;
            }
            attrib.cycleState();
            if(PluginData.isAllowedBlock(player, data)) {
                clicked.setBlockData(data, false);
                new BukkitRunnable() {
                    @Override
                    public void run() {
                        clicked.setBlockData(data, false);
                    }
                }.runTaskLater(ArchitectPlugin.getPluginInstance(), 1);
            }
        }*/
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
    public Shift getUpper(BlockFace orientation, Block clicked, Player player, Location interactionPoint) {
        Block block = clicked;
Logger.getGlobal().info("Get upper: " + block);
Logger.getGlobal().info("Interaction: " + interactionPoint);
        if(block!=null && block.getBlockData() instanceof Wall wall && !wall.isUp()) {
Logger.getGlobal().info("is Wall and no up");
            double xRelative = interactionPoint.getX()-interactionPoint.getBlockX();
            double zRelative = interactionPoint.getZ()-interactionPoint.getBlockZ();
            if(zRelative>=xRelative) {
                if(zRelative<1-xRelative) {
                    return new Shift(-1,0,0);
                } else {
                    return new Shift(0,0,1);
                }
            } else {
                if(zRelative<1-xRelative) {
                    return new Shift(0,0,-1);
                } else {
                    return new Shift(1,0,0);
                }
            }
        } else {
Logger.getGlobal().info("no Wall or has up");
            return new Shift(getPart(interactionPoint.getBlockX(), interactionPoint.getX()), 0,
                    getPart(interactionPoint.getBlockZ(), interactionPoint.getZ()));
        }
    }

    private int getPart(int blockCoordinate, double coordinate) {
        int part = (int)((coordinate- blockCoordinate)*4);
//Logger.getGlobal().info("Block: "+blockCoordinate+" Coord: "+coordinate+" Part: "+part);
        return (part>1?part-2:part-1);
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
        return block.getType().equals(material);
    }

    @Override
    public boolean matches(BlockData data) {
//Logger.getGlobal().info("SpecialBlockBranchConnect.matches: this:"+material);
//Logger.getGlobal().info("SpecialBlockBranchConnect.matches: that:"+data.getMaterial());
        return data.getMaterial().equals(material);}

}
