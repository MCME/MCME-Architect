package com.mcmiddleearth.architect.specialBlockHandling.specialBlocks;

import com.mcmiddleearth.architect.specialBlockHandling.SpecialBlockType;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Waterlogged;
import org.bukkit.block.data.type.HangingSign;
import org.bukkit.block.data.type.Sign;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

public class SpecialBlockSignPost extends SpecialBlockSign {

    private final boolean hanging;
    private final BlockData data;

    protected SpecialBlockSignPost(String id, BlockData data, boolean hanging, SpecialBlockType type) {
        super(id, data, data, hanging, type);
        this.hanging = hanging;
        this.data = data;
    }

    public static SpecialBlock loadFromConfig(ConfigurationSection config, String id) {
        boolean hanging = config.getBoolean("hanging", false);
        boolean attached = config.getBoolean("attached", false);
        String material = config.getString("material", "oak");
        BlockData data;
        try {
            if(hanging) {
                data = Bukkit.createBlockData("minecraft:"+material.toLowerCase()+"_hanging_sign");
                ((HangingSign)data).setAttached(attached);
            } else {
                data = Bukkit.createBlockData("minecraft:"+material.toLowerCase()+"_sign");
            }
        } catch (IllegalArgumentException ex) {
            return null;
        }
        return new SpecialBlockSignPost(id, data, hanging, SpecialBlockType.SIGN_POST);
    }

    @Override
    protected BlockState getBlockState(Block blockPlace, Block clicked, BlockFace blockFace, Player player,
                                       Location interactionPoint) {
        Waterlogged placeData = null;
        switch(blockFace) {
            case BlockFace.UP:
            case BlockFace.DOWN:
                if(!hanging) {
                    Sign sign = (Sign) data;
                    sign.setRotation(getBlockFaceSuperFine(player.getYaw()).getOppositeFace());
                    placeData = sign;
                } else {
                    HangingSign sign = (HangingSign) data;
                    sign.setRotation(getBlockFaceSuperFine(player.getYaw()).getOppositeFace());
                    placeData = sign;
                }
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
