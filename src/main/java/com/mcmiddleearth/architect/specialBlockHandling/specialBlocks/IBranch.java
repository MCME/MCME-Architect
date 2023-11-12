package com.mcmiddleearth.architect.specialBlockHandling.specialBlocks;

import com.mcmiddleearth.architect.specialBlockHandling.data.SpecialBlockInventoryData;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;

public interface IBranch {

    String[] variants = new String[]{"Thick", "Thin"};

    Shift getLower(BlockFace orientation, Block clicked, Player player, Location interactionPoint);
    Shift getUpper(BlockFace orientation, Block clicked, Player player, Location interactionPoint);
    boolean isDiagonal();

    BlockFace getDownwardOrientation(BlockFace blockFace);

    //BlockFace getOrientation(Location playerLoc, BlockFace clickedFace);

    default Shift getPlacedLower(BlockFace orientation, Block clicked, Player player, Location interactionPoint) {
        return getLower(orientation, clicked, player, interactionPoint);
    }

    default Shift getPlacedUpper(BlockFace orientation, Block clicked, Player player, Location interactionPoint) {
        return getUpper(orientation, clicked, player, interactionPoint);
    }

    default Location getBranchOrientation(Location playerLoc) {
        if(playerLoc.getPitch()>=0) {
            return playerLoc;
        } else {
            Location opposite = playerLoc.clone();
            opposite.setYaw(opposite.getYaw()+180);
            return opposite;
        }
    }

    default Block getBranchBlock(Block target, Block clicked, BlockFace clickedFace, Location interactionPoint,
                                 Player player, BlockFace orientation) {
        if(clickedFace.equals(BlockFace.UP)
                || (player.getLocation().getPitch()>=0 && isSideFace(clickedFace))) {
            boolean matchBelow = false;
            if(!clickedFace.equals(BlockFace.UP)) {
                //side face clicked -> revert target adjustment of SpecialBlock.getBlock();
                target = target.getRelative(clickedFace.getOppositeFace())
                               .getRelative(BlockFace.UP);
                if(isDiagonal() && isLowerHalf(clickedFace, interactionPoint)) {
                    //lower half clicked -> place at block below
                    target = target.getRelative(BlockFace.DOWN);
                    matchBelow = true;
                }
            }
//Logger.getGlobal().info("Target: "+target.getLocation());
            SpecialBlock specialBlock = getSpecialBlockForPlacement(clicked,player);
//Logger.getGlobal().info("Clicked: "+specialBlock);
//Logger.getGlobal().info("is Inclined branch block: "+(specialBlock instanceof SpecialBlockBranchInclined));
//Logger.getGlobal().info("is variant block: "+(specialBlock instanceof SpecialBlockOrientableVariants));
//Logger.getGlobal().info("is IBranch: "+(specialBlock instanceof IBranch));
            if (specialBlock instanceof IBranch branch) {
                BlockFace otherOrientation = orientation;
//Logger.getGlobal().info("This or: "+otherOrientation);
                if (branch instanceof SpecialBlockOrientableVariants orientable) {
                    BlockFace temp = orientable.getOrientation(clicked);
//Logger.getGlobal().info("Other or: "+temp);
                    if (temp != null) {
                        otherOrientation = temp;
                    }
                }
                Shift otherShift;
                if(!matchBelow) {
                    otherShift = branch.getPlacedUpper(otherOrientation, clicked, player, interactionPoint);
                } else {
                    otherShift = branch.getPlacedLower(otherOrientation, clicked, player, interactionPoint);
                }
                Shift thisShift = this.getLower(orientation, clicked, player, interactionPoint);
//Logger.getGlobal().info("This shift: "+thisShift);
//Logger.getGlobal().info("Other shift: "+otherShift);
                target = target.getRelative(otherShift.getX() - thisShift.getX(),
                        otherShift.getY() - thisShift.getY(),
                        otherShift.getZ() - thisShift.getZ());
//Logger.getGlobal().info("Target shifted connect: "+target.getLocation());
                return target;
            }
            Shift thisShift = this.getLower(orientation, clicked, player, interactionPoint);
            target = target.getRelative(-thisShift.getX(),
                    -thisShift.getY(),
                    -thisShift.getZ());
//Logger.getGlobal().info("Target shifted unconnected: "+target.getLocation());
            return target;
        } else if(clickedFace.equals(BlockFace.DOWN)
                || (player.getLocation().getPitch()<0 && isSideFace(clickedFace))) {
            boolean matchAbove = false;
            if(!clickedFace.equals(BlockFace.DOWN)) {
                //side face clicked -> revert target adjustment of SpecialBlock.getBlock();
                target = target.getRelative(clickedFace.getOppositeFace())
                               .getRelative(BlockFace.DOWN);
                if(isDiagonal() && isUpperHalf(clickedFace, interactionPoint)) {
                    //upper half clicked -> place at block above
                    target = target.getRelative(BlockFace.UP);
                    matchAbove = true;
                }
            }
            if(!isDiagonal()) {
                target = target.getRelative(BlockFace.UP);
            }
//Logger.getGlobal().info("Target: "+target.getLocation());
            SpecialBlock specialBlock = getSpecialBlockForPlacement(clicked,player);
//Logger.getGlobal().info("Clicked: "+specialBlock);
            orientation = getDownwardOrientation(orientation);
            if (specialBlock instanceof IBranch branch) {
                BlockFace otherOrientation = orientation;
//Logger.getGlobal().info("This or: "+otherOrientation);
                if (branch instanceof SpecialBlockOrientableVariants orientable) {
                    BlockFace temp = orientable.getOrientation(clicked);
//Logger.getGlobal().info("Other or: "+temp);
                    if (temp != null) {
                        otherOrientation = temp;
                    }
                }
                Shift otherShift;
                if(!matchAbove) {
                    otherShift = branch.getPlacedLower(otherOrientation, clicked, player, interactionPoint);
                } else {
                    otherShift = branch.getPlacedUpper(otherOrientation, clicked, player, interactionPoint);
                }
                Shift thisShift = this.getUpper(orientation, clicked, player, interactionPoint);
                target = target.getRelative(otherShift.getX() - thisShift.getX(),
                        otherShift.getY() - thisShift.getY(),
                        otherShift.getZ() - thisShift.getZ());
//Logger.getGlobal().info("Target shifted connect: "+target.getLocation());
                return target;
            }
            Shift thisShift = this.getUpper(orientation, clicked, player, interactionPoint);
            target = target.getRelative(-thisShift.getX(),
                    -thisShift.getY(),
                    -thisShift.getZ());
//Logger.getGlobal().info("Target shifted unconnected: "+target.getLocation());
            return target;
        } else {
//Logger.getGlobal().info("return original Target: "+target.getLocation());
            return target;
        }
    }

    boolean isThin(Block block, Player player, Location interactionPoint);
    /*default boolean isThin(Block block, Player player, Location interactionPoint) {
        SpecialBlock specialBlockData = SpecialBlockInventoryData.getSpecialBlockDataFromBlock(block, player, IBranch.class);
        if(specialBlockData instanceof IBranch && specialBlockData instanceof SpecialBlockOrientableVariants) {
            return ((SpecialBlockOrientableVariants)specialBlockData).getVariantName(block).equals("Thin");
        }
        return false;
    }*/

    private boolean isUpperHalf(BlockFace clickedFace, Location interactionPoint) {
        if(interactionPoint==null) return true;
        return isSideFace(clickedFace) && interactionPoint.getY()-interactionPoint.getBlockY() >= 0.5;
    }

    private boolean isLowerHalf(BlockFace clickedFace, Location interactionPoint) {
        if(interactionPoint==null) return false;
        return isSideFace(clickedFace) && interactionPoint.getY()-interactionPoint.getBlockY() < 0.5;
    }

    private boolean isSideFace(BlockFace blockFace) {
        return blockFace.equals(BlockFace.NORTH)
                || blockFace.equals(BlockFace.NORTH_NORTH_EAST)
                || blockFace.equals(BlockFace.NORTH_EAST)
                || blockFace.equals(BlockFace.EAST_NORTH_EAST)
                || blockFace.equals(BlockFace.EAST)
                || blockFace.equals(BlockFace.EAST_SOUTH_EAST)
                || blockFace.equals(BlockFace.SOUTH_EAST)
                || blockFace.equals(BlockFace.SOUTH_SOUTH_EAST)
                || blockFace.equals(BlockFace.SOUTH)
                || blockFace.equals(BlockFace.SOUTH_SOUTH_WEST)
                || blockFace.equals(BlockFace.SOUTH_WEST)
                || blockFace.equals(BlockFace.WEST_SOUTH_WEST)
                || blockFace.equals(BlockFace.WEST)
                || blockFace.equals(BlockFace.WEST_NORTH_WEST)
                || blockFace.equals(BlockFace.NORTH_WEST)
                || blockFace.equals(BlockFace.NORTH_NORTH_WEST);
    }

    private SpecialBlock getSpecialBlockForPlacement(Block clicked, Player player) {
        SpecialBlock result = SpecialBlockInventoryData.getSpecialBlockDataFromBlock(clicked, player,SpecialBlockBranchInclined.class);
        if(result==null) {
            result = SpecialBlockInventoryData.getSpecialBlockDataFromBlock(clicked, player,SpecialBlockBranchHorizontal.class);
        }
        if(result==null) {
            result = SpecialBlockInventoryData.getSpecialBlockDataFromBlock(clicked, player,IBranch.class);
        }
        return result;
    }

    public class Shift {

        private int x,y,z;

        public Shift(int x, int y, int z) {
            this.x = x;
            this.y = y;
            this.z = z;
        }

        public int getX() {
            return x;
        }

        public int getY() {
            return y;
        }

        public int getZ() {
            return z;
        }

        public void setY(int y) {
            this.y = y;
        }

        public void setX(int x) {
            this.x = x;
        }

        public void setZ(int z) {
            this.z = z;
        }

        @Override
        public String toString() {
            return "("+getX()+"|"+getY()+"|"+getZ()+")";
        }
    }
}
