package com.mcmiddleearth.architect.specialBlockHandling.specialBlocks;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.ListenerPriority;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.wrappers.BlockPosition;
import com.mcmiddleearth.architect.ArchitectPlugin;
import com.mcmiddleearth.architect.signEditor.SignEditorData;
import com.mcmiddleearth.architect.specialBlockHandling.SpecialBlockType;
import net.kyori.adventure.text.Component;
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
import org.bukkit.block.sign.Side;
import org.bukkit.block.sign.SignSide;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.scheduler.BukkitTask;

import java.util.List;
import java.util.logging.Logger;

public class SpecialBlockSign extends SpecialBlock {

    private final boolean hanging;
    private final BlockData data, dataWall;

    protected SpecialBlockSign(String id, BlockData data, BlockData dataWall, boolean hanging,
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
            case BlockFace.NORTH:
            case BlockFace.WEST:
            case BlockFace.SOUTH:
            case BlockFace.EAST:
                Directional sign = (Directional) dataWall;
                if(hanging) {
                    sign.setFacing(rotateBlockFace90(blockFace));
                } else {
                    sign.setFacing(blockFace);
                }
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

    @Override
    public void placeBlock(Block blockPlace, BlockFace blockFace, Block clicked, Location interactionPoint, Player player) {
        super.placeBlock(blockPlace, blockFace, clicked, interactionPoint, player);
        SpecialBlock instance = this;
        Bukkit.getScheduler().runTaskLater(ArchitectPlugin.getPluginInstance(), new Runnable() {
            @Override
            public void run() {
                org.bukkit.block.Sign sign = (org.bukkit.block.Sign) blockPlace.getState();
                sign.setWaxed(false);
                sign.update(true,false);
                /*Bukkit.getPluginManager().registerEvents(new SignListener(instance, blockPlace, blockFace, clicked,
                                                                                    interactionPoint, player),
                                                        ArchitectPlugin.getPluginInstance());*/
                sendSignEditorOpen(blockPlace, player, Side.FRONT);
                ProtocolManager protocolManager = ProtocolLibrary.getProtocolManager();
                protocolManager.addPacketListener(new SignPacketListener(blockPlace, player, Side.FRONT));
            }
        },3);
    }

    public void sendSignEditorOpen(Block blockPlace, Player player, Side side) {
        ProtocolManager protocolManager = ProtocolLibrary.getProtocolManager();
        BlockPosition blockPosition = new BlockPosition(blockPlace.getX(), blockPlace.getY(), blockPlace.getZ());
        PacketContainer packet = protocolManager.createPacket(PacketType.Play.Server.OPEN_SIGN_EDITOR);
        packet.getBlockPositionModifier().write(0, blockPosition);
        packet.getBooleans().write(0, side.equals(Side.FRONT));
        try {
            protocolManager.sendServerPacket(player, packet);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static class SignPacketListener extends PacketAdapter {

        private final SignPacketListener instance;
        private Block blockPlace;
        private Player player;
        private Side side;

        public SignPacketListener(Block blockPlace, Player player, Side side) {
            super(ArchitectPlugin.getPluginInstance(), ListenerPriority.NORMAL, PacketType.Play.Client.UPDATE_SIGN);
            this.instance = this;
            this.blockPlace = blockPlace;
            this.player = player;
            this.side = side;
            ProtocolManager protocolManager = ProtocolLibrary.getProtocolManager();
            Bukkit.getScheduler().runTaskLater(ArchitectPlugin.getPluginInstance(), new Runnable() {
                @Override
                public void run() {
                    protocolManager.removePacketListener(instance);
                }
            }, 6000);
        }

        @Override
        public void onPacketReceiving(PacketEvent event) {
            PacketContainer packet = event.getPacket();
            BlockPosition blockPosition = packet.getBlockPositionModifier().read(0);
            if(blockPlace.getX() == blockPosition.getX()
                    && blockPlace.getY() == blockPosition.getY()
                    && blockPlace.getZ() == blockPosition.getZ()
                    && player.equals(event.getPlayer())) {
                event.setCancelled(true);
                Bukkit.getScheduler().runTask(ArchitectPlugin.getPluginInstance(), new Runnable() {
                    @Override
                    public void run() {
                        ProtocolManager protocolManager = ProtocolLibrary.getProtocolManager();
                        String[] lines = packet.getStringArrays().read(0);
                        org.bukkit.block.Sign sign = (org.bukkit.block.Sign) blockPlace.getState();
                        for (int i = 0; i < lines.length; i++) {
                            sign.getSide(side).line(i, SignEditorData.parseLine(lines[i]));
                        }
                        sign.update(true, false);
                        /*if(side == Side.FRONT) {
                            sendSignEditorOpen(blockPlace, player, Side.BACK);
                            side = Side.BACK;
                        } else {*/
                        protocolManager.removePacketListener(instance);
                        //}
                    }
                });
            }
        }
    }

    /*public static class SignListener implements Listener {

        private final Block blockPlace, clicked;
        private final BlockFace blockFace;
        private final Location interactionPoint;
        private final Player player;
        private BukkitTask removalTask;
        private final SignListener instance;
        private final SpecialBlock specialBlock;

        public SignListener(SpecialBlock specialBlock, final Block blockPlace,
                            final BlockFace blockFace, final Block clicked,
                            final Location interactionPoint, final Player player) {
            this.blockPlace = blockPlace;
            this.blockFace = blockFace;
            this.clicked = clicked;
            this.interactionPoint = interactionPoint;
            this.player = player;
            this.specialBlock = specialBlock;
            instance = this;
            Bukkit.getScheduler().runTaskLater(ArchitectPlugin.getPluginInstance(), new Runnable() {
                @Override
                public void run() {
                    HandlerList.unregisterAll(instance);
                }
            }, 6000); // 20*60*5 = 6000 ticks = 5 minutes
        }

        @EventHandler
        public void onSignChange(SignChangeEvent event) {
//Logger.getGlobal().info("On Sign change!!");
            if(event.getBlock().equals(blockPlace) && player.equals(event.getPlayer())) {
                //specialBlock.placeBlock(blockPlace, blockFace, clicked, interactionPoint, player);
                Bukkit.getScheduler().runTaskLater(ArchitectPlugin.getPluginInstance(), new Runnable() {
                    @Override
                    public void run() {
                        List<Component> lines = event.lines();
                        org.bukkit.block.Sign sign = (org.bukkit.block.Sign) blockPlace.getState();
                        setLines(sign.getSide(Side.FRONT), lines);
                        setLines(sign.getSide(Side.BACK), lines);
                        sign.update(true, false);
                    }
                }, 3);
                HandlerList.unregisterAll(this);
            }
        }

        private void setLines(SignSide side, List<Component> lines) {
            for(int i = 0; i < 4; i++) {
                side.line(i, lines.get(i));
            }
        }

    }*/

    @Override
    public boolean matches(BlockData data) {
        return data.matches(this.data) || data.matches(this.dataWall);
    }

    @Override
    public boolean matches(Block block) {
        return matches(block.getBlockData());
    }
}


