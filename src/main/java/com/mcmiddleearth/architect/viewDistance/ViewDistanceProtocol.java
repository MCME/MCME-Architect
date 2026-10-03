package com.mcmiddleearth.architect.viewDistance;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.events.PacketContainer;
import com.mcmiddleearth.architect.Log;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

/**
 * All ProtocolLib interaction for the view-distance feature is isolated here.
 *
 * ProtocolLib is a soft dependency: the rest of the plugin must never reference ProtocolLib
 * types directly, or class-loading the referencing class fails when ProtocolLib is absent
 * (e.g. under MockBukkit, or on a server without it). Callers must therefore only invoke the
 * methods below AFTER checking that the "ProtocolLib" plugin is present.
 *
 * @author Eriol_Eandur
 */
public final class ViewDistanceProtocol {

    private ViewDistanceProtocol() {
    }

    /** Register the packet adapter (unload-chunk suppression + view-distance spoofing). */
    public static void register(Plugin plugin) {
        ProtocolLibrary.getProtocolManager().addPacketListener(new ViewDistanceListener(plugin));
    }

    /** Push a new client-side view distance to a single player via a Set-Chunk-Cache-Radius packet. */
    public static void sendViewDistance(Player player, int viewDistance) {
        PacketContainer packet = new PacketContainer(PacketType.Play.Server.VIEW_DISTANCE);
        packet.getIntegers().write(0, viewDistance);
        try {
            ProtocolLibrary.getProtocolManager().sendServerPacket(player, packet);
        } catch (Exception e) {
            Log.error("Failed to send view distance packet to " + player.getName(), e);
        }
    }
}
