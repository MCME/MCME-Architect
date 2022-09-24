package com.mcmiddleearth.architect.viewDistance;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import com.mcmiddleearth.architect.Modules;
import com.mcmiddleearth.architect.PluginData;
import org.bukkit.plugin.Plugin;

public class ViewDistanceListener extends PacketAdapter {

    public ViewDistanceListener(Plugin plugin) {
        super(plugin, PacketType.Play.Server.LOGIN, PacketType.Play.Server.UNLOAD_CHUNK, PacketType.Play.Server.VIEW_DISTANCE);
    }

    @Override
    public void onPacketSending(PacketEvent event) {
        if(!PluginData.isModuleEnabled(event.getPlayer().getWorld(), Modules.VIEW_DISTANCE)) {
            return;
        }
        PacketContainer packet = event.getPacket();
        if(packet.getType().equals(PacketType.Play.Server.LOGIN)) {
            packet.getIntegers().write(2, ViewDistanceManager.getViewDistance(event.getPlayer()));
        } else if(packet.getType().equals(PacketType.Play.Server.UNLOAD_CHUNK)) {
            event.setCancelled(true);
        } else if(packet.getType().equals(PacketType.Play.Server.VIEW_DISTANCE)) {
            packet.getIntegers().write(0, ViewDistanceManager.getViewDistance(event.getPlayer()));
        }

        /*if(!PluginData.isModuleEnabled(event.getPlayer().getWorld(), Modules.VIEW_DISTANCE)
                || !ViewDistanceManager.isViewdistanceSet(event.getPlayer())) {
            return;
        }
        PacketContainer packet = event.getPacket();
        if(packet.getType().equals(PacketType.Play.Server.LOGIN)) {
            packet.getIntegers().write(2, ViewDistanceManager.getViewDistance(event.getPlayer()));
        } else if(packet.getType().equals(PacketType.Play.Server.UNLOAD_CHUNK)) {
            event.setCancelled(true);
        } else if(packet.getType().equals(PacketType.Play.Server.VIEW_DISTANCE)) {
            packet.getIntegers().write(0,ViewDistanceManager.getViewDistance(event.getPlayer()));
        }*/
    }
}
