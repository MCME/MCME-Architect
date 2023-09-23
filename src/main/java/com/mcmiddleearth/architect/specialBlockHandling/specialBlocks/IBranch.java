package com.mcmiddleearth.architect.specialBlockHandling.specialBlocks;

import com.mcmiddleearth.architect.serverResoucePack.RpManager;
import com.mcmiddleearth.architect.specialBlockHandling.data.SpecialBlockInventoryData;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.logging.Logger;

public interface IBranch {

    Shift getLower(BlockFace orientation);
    Shift getUpper(BlockFace orientation);
    //BlockFace getOrientation(Location playerLoc, BlockFace clickedFace);

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
                if(isLowerHalf(clickedFace, interactionPoint)) {
                    //lower half clicked -> place at block below
                    target = target.getRelative(BlockFace.DOWN);
                    matchBelow = true;
                }
            }
            ItemStack item = SpecialBlockInventoryData.getItem(clicked, RpManager.getCurrentRpName(player));
            if (item != null) {
                SpecialBlock specialBlock = SpecialBlockInventoryData.getSpecialBlockDataFromItem(item);
                if (specialBlock instanceof IBranch branch) {
                    BlockFace otherOrientation = orientation;
                    if (branch instanceof SpecialBlockOrientable orientable) {
                        BlockFace temp = orientable.getOrientation(clicked);
                        if (temp != null) {
                            otherOrientation = temp;
                        }
                    }
                    Shift otherShift;
                    if(!matchBelow) {
                        otherShift = branch.getUpper(otherOrientation);
                    } else {
                        otherShift = branch.getLower(otherOrientation);
                    }
                    Shift thisShift = this.getLower(orientation);
                    return target.getRelative(otherShift.getX() - thisShift.getX(),
                            otherShift.getY() - thisShift.getY(),
                            otherShift.getZ() - thisShift.getZ());
                }
            }
            Shift thisShift = this.getLower(orientation);
            return target.getRelative(-thisShift.getX(),
                    -thisShift.getY(),
                    -thisShift.getZ());
        } else if(clickedFace.equals(BlockFace.DOWN)
                || (player.getLocation().getPitch()<0 && isSideFace(clickedFace))) {
            boolean matchAbove = false;
            if(!clickedFace.equals(BlockFace.DOWN)) {
                //side face clicked -> revert target adjustment of SpecialBlock.getBlock();
                target = target.getRelative(clickedFace.getOppositeFace())
                               .getRelative(BlockFace.DOWN);
                if(isUpperHalf(clickedFace, interactionPoint)) {
                    //upper half clicked -> place at block above
                    target = target.getRelative(BlockFace.UP);
                    matchAbove = true;
                }
            }
            ItemStack item = SpecialBlockInventoryData.getItem(clicked, RpManager.getCurrentRpName(player));
            orientation = orientation.getOppositeFace();
            if (item != null) {
                SpecialBlock specialBlock = SpecialBlockInventoryData.getSpecialBlockDataFromItem(item);
                if (specialBlock instanceof IBranch branch) {
                    BlockFace otherOrientation = orientation;
                    if (branch instanceof SpecialBlockOrientable orientable) {
                        BlockFace temp = orientable.getOrientation(clicked);
                        if (temp != null) {
                            otherOrientation = temp;
                        }
                    }
                    Shift otherShift;
                    if(!matchAbove) {
                        otherShift = branch.getLower(otherOrientation);
                    } else {
                        otherShift = branch.getUpper(otherOrientation);
                    }
                    Shift thisShift = this.getUpper(orientation);
                    return target.getRelative(otherShift.getX() - thisShift.getX(),
                            otherShift.getY() - thisShift.getY(),
                            otherShift.getZ() - thisShift.getZ());
                }
            }
            Shift thisShift = this.getUpper(orientation);
            return target.getRelative(-thisShift.getX(),
                    -thisShift.getY(),
                    -thisShift.getZ());
        } else {
            return target;
        }
    }

    private boolean isUpperHalf(BlockFace clickedFace, Location interactionPoint) {
        return isSideFace(clickedFace) && interactionPoint.getY()-interactionPoint.getBlockY() >= 0.5;
    }

    private boolean isLowerHalf(BlockFace clickedFace, Location interactionPoint) {
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

    public record Shift(int x, int y, int z) {

        public int getX() {
            return x;
        }

        public int getY() {
            return y;
        }

        public int getZ() {
            return z;
        }
    }
}
