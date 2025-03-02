package com.mcmiddleearth.architect.specialBlockHandling.specialBlocks;

import com.mcmiddleearth.architect.specialBlockHandling.SpecialBlockType;
import com.mcmiddleearth.util.DevUtil;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.data.BlockData;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

public class SpecialBlockSixFacesXZ extends SpecialBlockOrientable {
    private static final Orientation[] sixFacesXZ = new Orientation[] {
            new Orientation(BlockFace.SOUTH,"South"),
            new Orientation(BlockFace.WEST,"West"),
            new Orientation(BlockFace.NORTH,"North"),
            new Orientation(BlockFace.EAST,"East"),
            new Orientation(BlockFace.DOWN,"DownX"),
            new Orientation(BlockFace.DOWN,"DownZ"),
            new Orientation(BlockFace.UP,"UpX"),
            new Orientation(BlockFace.UP,"UpZ")
    };

    private SpecialBlockSixFacesXZ(String id,
                                 BlockData[] data) {
        super(id, data, SpecialBlockType.SIX_FACES_XZ);
        orientations = sixFacesXZ;
    }

    public static SpecialBlockSixFacesXZ loadFromConfig(ConfigurationSection config, String id) {
        BlockData[] data = loadBlockDataFromConfig(config, sixFacesXZ);
        if(data==null) {
            return null;
        }
        return new SpecialBlockSixFacesXZ(id, data);
    }

    @Override
    protected BlockState getBlockState(Block blockPlace, Block clicked, BlockFace blockFace,
                                       Player player, Location interactionPoint) {
        final BlockState state = blockPlace.getState();
        int faceIndex = getFaceIndex(blockFace);
        if(blockFace.equals(BlockFace.UP) || blockFace.equals(BlockFace.DOWN)) {
            BlockFace playerFace = getBlockFace(player.getLocation().getYaw());
            if(playerFace.equals(BlockFace.WEST) || playerFace.equals(BlockFace.EAST)) {
                faceIndex++;
            }
        }
        BlockData data = getBlockDatas()[faceIndex];
        if(data!=null) {
            state.setBlockData(data);
        } else {
            DevUtil.log("No BlockData for: blockFace="+blockFace);
            DevUtil.log("Available data:");
            for(int i=0; i<orientations.length;i++) {
                DevUtil.log(""+orientations[i].face+" - "+orientations[i].toString());
            }
        }
        return state;
    }

    protected int getFaceIndex(BlockFace face) {
        for(int i=0; i<orientations.length; i++) {
            if(orientations[i].face.equals(face)) {
                return i;
            }
        }
        return 0;
    }



}
