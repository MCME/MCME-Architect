package com.mcmiddleearth.architect.gamemodeSwitcher;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.wrappers.EnumWrappers;
import com.mcmiddleearth.architect.Log;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

/**
 * All ProtocolLib interaction for the game mode switcher. As with ViewDistanceProtocol, this class cannot even be
 * linked without ProtocolLib, so it is called only once the "ProtocolLib" plugin is known to be present.
 * <p>
 * ProtocolLib 5.3.0, which Architect compiles against, predates the switcher's packet, so its type is looked up at
 * run time from the server's own class. The server runs a ProtocolLib dev build (5.5.0+), which knows every packet the
 * server registers.
 */
public final class GamemodeSwitcherProtocol {

    /** The packet the switcher sends, for F3+F4 and F3+N. */
    private static final String CHANGE_GAME_MODE_PACKET =
            "net.minecraft.network.protocol.game.ServerboundChangeGameModePacket";
    /** Entity event statuses 24 to 28 tell a client its own permission level, 0 to 4. */
    private static final int LEVEL_ZERO_STATUS = 24;

    private GamemodeSwitcherProtocol() {
    }

    /**
     * Starts the switcher for builders, with the packet listener first, so no client is told the switcher's level
     * while nothing answers its requests. If ProtocolLib cannot give the packet's type, the switcher stays off.
     */
    public static void register(Plugin plugin) {
        GamemodeSwitcher switcher = new GamemodeSwitcher(plugin, GamemodeSwitcherProtocol::sendLevel);
        try {
            Class<?> packetClass = Class.forName(CHANGE_GAME_MODE_PACKET, false,
                    plugin.getServer().getClass().getClassLoader());
            PacketType changeGameMode = PacketType.fromClass(packetClass);
            ProtocolLibrary.getProtocolManager().addPacketListener(
                    new SwitchRequestListener(plugin, changeGameMode, switcher));
        } catch (ClassNotFoundException | RuntimeException | LinkageError e) {
            Log.warn("The game mode switcher for builders is off: ProtocolLib cannot listen for its packet ("
                    + e + ").");
            return;
        }
        plugin.getServer().getPluginManager().registerEvents(switcher, plugin);
        Log.info("The game mode switcher for builders is on.");
    }

    /** Tells the client its permission level, with the entity event the server itself sends for it. */
    private static void sendLevel(Player player, int level) {
        try {
            PacketContainer packet = new PacketContainer(PacketType.Play.Server.ENTITY_STATUS);
            packet.getIntegers().write(0, player.getEntityId());
            packet.getBytes().write(0, (byte) (LEVEL_ZERO_STATUS + level));
            ProtocolLibrary.getProtocolManager().sendServerPacket(player, packet);
        } catch (Exception e) {
            Log.error("Failed to send the permission level packet to " + player.getName(), e);
        }
    }

    /** Hands each request from the switcher to Architect, and stops the packet when Architect takes it. */
    private static final class SwitchRequestListener extends PacketAdapter {

        private final GamemodeSwitcher switcher;

        SwitchRequestListener(Plugin plugin, PacketType changeGameMode, GamemodeSwitcher switcher) {
            super(plugin, changeGameMode);
            this.switcher = switcher;
        }

        @Override
        public void onPacketReceiving(PacketEvent event) {
            EnumWrappers.NativeGameMode requested = event.getPacket().getGameModes().readSafely(0);
            GameMode mode = requested == null ? null : requested.toBukkit();
            if (mode != null && switcher.onSwitchRequest(event.getPlayer(), mode)) {
                event.setCancelled(true);
            }
        }
    }
}
