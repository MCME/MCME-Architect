package com.mcmiddleearth.architect.specialBlockHandling.specialBlocks;

import com.mcmiddleearth.architect.specialBlockHandling.SpecialBlockType;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Directional;
import org.bukkit.block.data.Waterlogged;
import org.bukkit.block.data.type.HangingSign;
import org.bukkit.block.data.type.Sign;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

public class SpecialBlockSign extends SpecialBlock {

    private final boolean hanging;
    private final BlockData data, dataWall;

    private SpecialBlockSign(String id, BlockData data, BlockData dataWall, boolean hanging,
                             SpecialBlockType type) {
        super(id, Material.AIR.createBlockData(), type);
        this.data = data;
        this.dataWall = dataWall;
        this.hanging = hanging;
    }

    public static SpecialBlock loadFromConfig(ConfigurationSection config, String id) {
        boolean hanging = config.getBoolean("hanging", false);
        boolean attached = config.getBoolean("attached", false);
        String material = config.getString("material", "oak");
        BlockData data, wallData;
        try {
            if(hanging) {
                data = Bukkit.createBlockData("minecraft:"+material.toLowerCase()+"_hanging_sign");
                ((HangingSign)data).setAttached(attached);
                wallData = Bukkit.createBlockData("minecraft:"+material.toLowerCase()+"_wall_hanging_sign");
            } else {
                data = Bukkit.createBlockData("minecraft:"+material.toLowerCase()+"_sign");
                wallData = Bukkit.createBlockData("minecraft:"+material.toLowerCase()+"_wall_sign");
            }
        } catch (IllegalArgumentException ex) {
            return null;
        }
        return new SpecialBlockSign(id, data, wallData, hanging, SpecialBlockType.SIGN);
    }

    @Override
    protected BlockState getBlockState(Block blockPlace, Block clicked, BlockFace blockFace, Player player,
                                       Location interactionPoint) {
        Waterlogged placeData = null;
        switch(blockFace) {
            case BlockFace.UP:
                if(!hanging) {
                    Sign sign = (Sign) data;
                    sign.setRotation(getBlockFaceSuperFine(player.getYaw()));
                    placeData = sign;
                }
                break;
            case BlockFace.DOWN:
                if(hanging) {
                    HangingSign sign = (HangingSign) data;
                    sign.setRotation(getBlockFaceSuperFine(player.getYaw()));
                    placeData = sign;
                }
                break;
            case BlockFace.NORTH:
            case BlockFace.WEST:
            case BlockFace.SOUTH:
            case BlockFace.EAST:
                Directional sign = (Directional) dataWall;
                sign.setFacing(blockFace);
                placeData = (Waterlogged) sign;
                break;
        }
        if(placeData != null) {
            placeData.setWaterlogged(blockPlace.getBlockData() instanceof Waterlogged waterlogged
                    && waterlogged.isWaterlogged());
            BlockState state = blockPlace.getState();
            state.setBlockData(placeData);
            return state;
        } else {
            return null;
        }
    }
}
