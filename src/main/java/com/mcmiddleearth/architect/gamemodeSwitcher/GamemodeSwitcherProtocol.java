package com.mcmiddleearth.architect.gamemodeSwitcher;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.wrappers.EnumWrappers;
import com.mcmiddleearth.architect.Log;
import org.bukkit.GameMode;
import org.bukkit.plugin.Plugin;

/**
 * All ProtocolLib interaction for the game mode switcher: it hands each request from the switcher to Architect. As
 * with ViewDistanceProtocol, this class cannot even be linked without ProtocolLib, so it is called only once the
 * "ProtocolLib" plugin is known to be present.
 * <p>
 * ProtocolLib 5.3.0, which Architect compiles against, predates the switcher's packet, so its type is looked up at
 * run time from the server's own class. The server runs a ProtocolLib dev build (5.5.0+), which knows every packet the
 * server registers.
 */
public final class GamemodeSwitcherProtocol {

    /** The packet the switcher sends, for F3+F4 and F3+N. */
    private static final String CHANGE_GAME_MODE_PACKET =
            "net.minecraft.network.protocol.game.ServerboundChangeGameModePacket";

    private GamemodeSwitcherProtocol() {
    }

    /**
     * Starts the switcher for builders, with the packet listener first, so no client is told the switcher's level
     * while nothing answers its requests. If ProtocolLib cannot give the packet's type, the switcher stays off.
     */
    public static void register(Plugin plugin) {
        GamemodeSwitcher switcher = GamemodeSwitcher.forServer(plugin);
        try {
            ProtocolLibrary.getProtocolManager().addPacketListener(
                    new SwitchRequestListener(plugin, changeGameModeType(plugin), switcher));
        } catch (ClassNotFoundException | RuntimeException | LinkageError e) {
            Log.warn("The game mode switcher for builders is off: ProtocolLib cannot listen for its packet ("
                    + e + ").");
            return;
        }
        switcher.start();
        Log.info("The game mode switcher for builders is on.");
    }

    /**
     * The switcher packet's type, looked up from the server's class. It must be a packet the client sends, with the
     * one game mode the request carries: anything else would be read wrongly, so the lookup counts as failed.
     */
    private static PacketType changeGameModeType(Plugin plugin) throws ClassNotFoundException {
        Class<?> packetClass = Class.forName(CHANGE_GAME_MODE_PACKET, false,
                plugin.getServer().getClass().getClassLoader());
        PacketType type = PacketType.fromClass(packetClass);
        if (!type.isClient()) {
            throw new IllegalStateException(type + " is not a packet the client sends");
        }
        int gameModes = new PacketContainer(type).getGameModes().size();
        if (gameModes != 1) {
            throw new IllegalStateException(type + " has " + gameModes + " game mode fields, not one");
        }
        return type;
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
