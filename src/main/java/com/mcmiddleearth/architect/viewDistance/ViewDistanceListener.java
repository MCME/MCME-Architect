package com.mcmiddleearth.architect.viewDistance;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import com.mcmiddleearth.architect.Modules;
import com.mcmiddleearth.architect.PluginData;
import org.bukkit.plugin.Plugin;

/**
 * ProtocolLib packet adapter that gives a player a client-side view distance larger than the
 * server's, WITHOUT forcing the server to eagerly load/send the whole radius:
 *   - cancels UNLOAD_CHUNK so the client keeps chunks it has already received
 *     (players accumulate loaded chunks by moving around), and
 *   - rewrites the view-distance field of the LOGIN and VIEW_DISTANCE packets to the player's setting.
 *
 * NOTE (26.2 migration): the integer field indices below (LOGIN field 2, VIEW_DISTANCE field 0)
 * were written for an older protocol. The UNLOAD_CHUNK cancellation is index-free and safe, but the
 * two field writes MUST be validated on a live 26.2 server before release — the LOGIN (join) packet
 * layout in particular may have shifted, and writing the wrong field could corrupt it.
 *
 * @author Eriol_Eandur
 */
public class ViewDistanceListener extends PacketAdapter {

    public ViewDistanceListener(Plugin plugin) {
        super(plugin, PacketType.Play.Server.LOGIN,
                      PacketType.Play.Server.UNLOAD_CHUNK,
                      PacketType.Play.Server.VIEW_DISTANCE);
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
    }
}
