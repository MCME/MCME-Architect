package com.mcmiddleearth.architect.gamemodeSwitcher;

import com.mcmiddleearth.architect.Log;
import com.mcmiddleearth.architect.Modules;
import com.mcmiddleearth.architect.Permission;
import com.mcmiddleearth.architect.PluginData;
import net.kyori.adventure.text.Component;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerCommandSendEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.plugin.Plugin;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * Lets builders who are not op switch game mode with the vanilla switcher (F3+F4, and F3+N for spectator), with a
 * permission per mode, in worlds where the gamemodeSwitcher module is on. Players the server lets switch anyway, ops
 * and holders of minecraft.command.gamemode, are left to vanilla.
 * <p>
 * The client opens the switcher only at permission level 2 or more. The server sends each player their real level
 * when they join, respawn or change worlds, and when their op status changes, each time followed by the command tree.
 * So a builder is told level 2 a tick after each of these events and after each command tree, and level 0 again once
 * they no longer qualify. The server refuses a non-op's request from the switcher, so {@link #onSwitchRequest} takes
 * it first, and the mode is set here with the Bukkit API. {@link GamemodeSwitcherProtocol} does both packet jobs
 * with ProtocolLib.
 */
public final class GamemodeSwitcher implements Listener {

    /** The level at which the client opens the switcher. */
    private static final int SWITCHER_LEVEL = 2;
    /** The level of every player who is not op. */
    private static final int PLAYER_LEVEL = 0;

    /** Tells a player's client its permission level, 0 to 4. */
    @FunctionalInterface
    public interface LevelSender {
        void send(Player player, int level);
    }

    private final Plugin plugin;
    private final LevelSender levelSender;
    /** Players whose client was told the switcher's level. Main thread only. */
    private final Set<UUID> withSwitcher = new HashSet<>();

    public GamemodeSwitcher(Plugin plugin, LevelSender levelSender) {
        this.plugin = plugin;
        this.levelSender = levelSender;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        updateLevelLater(event.getPlayer());
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        updateLevelLater(event.getPlayer());
    }

    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent event) {
        updateLevelLater(event.getPlayer());
    }

    @EventHandler
    public void onCommandSend(PlayerCommandSendEvent event) {
        updateLevelLater(event.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        withSwitcher.remove(event.getPlayer().getUniqueId());
    }

    /** A tick later, after the level the server sends in the tick of the event. */
    private void updateLevelLater(Player player) {
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> updateLevel(player), 1);
    }

    private void updateLevel(Player player) {
        if (!player.isOnline()) {
            return;
        }
        if (qualifies(player)) {
            withSwitcher.add(player.getUniqueId());
            levelSender.send(player, SWITCHER_LEVEL);
        } else if (withSwitcher.remove(player.getUniqueId()) && !player.isOp()) {
            // an op's level came with the op change, and Bukkit does not tell what it is
            levelSender.send(player, PLAYER_LEVEL);
        }
    }

    /**
     * A request from the switcher, passed on by the packet listener off the main thread. Returns false for a player
     * the server lets switch: the packet goes on to vanilla. Otherwise Architect takes the request, and the packet
     * must go no further: on the main thread, the mode is set if the player may use it in their world, or else they
     * are told they can't switch to it there.
     */
    public boolean onSwitchRequest(Player player, GameMode mode) {
        if (vanillaLetsSwitch(player)) {
            return false;
        }
        plugin.getServer().getScheduler().runTask(plugin, () -> switchMode(player, mode));
        return true;
    }

    private void switchMode(Player player, GameMode mode) {
        if (!player.isOnline()) {
            return;
        }
        String name = mode.name().toLowerCase(Locale.ROOT);
        if (!moduleOn(player) || !PluginData.hasPermission(player, permission(mode))) {
            player.sendMessage(Component.text("You can't switch to " + name + " mode here."));
            return;
        }
        if (player.getGameMode() == mode) {
            return;
        }
        player.setGameMode(mode);
        if (player.getGameMode() == mode) { // another plugin may cancel the change
            player.sendMessage(Component.translatable("commands.gamemode.success.self",
                    Component.translatable("gameMode." + name)));
            Log.info(player.getName() + " switched to " + name + " mode with the game mode switcher.");
        }
    }

    /** The server's own check: vanilla's (op), or Paper's permission. */
    private static boolean vanillaLetsSwitch(Player player) {
        return player.isOp() || player.hasPermission("minecraft.command.gamemode");
    }

    private static boolean qualifies(Player player) {
        if (vanillaLetsSwitch(player) || !moduleOn(player)) {
            return false;
        }
        for (GameMode mode : GameMode.values()) {
            if (PluginData.hasPermission(player, permission(mode))) {
                return true;
            }
        }
        return false;
    }

    private static boolean moduleOn(Player player) {
        return PluginData.isModuleEnabled(player.getWorld(), Modules.GAMEMODE_SWITCHER);
    }

    private static Permission permission(GameMode mode) {
        return switch (mode) {
            case CREATIVE -> Permission.GAMEMODE_SWITCHER_CREATIVE;
            case SURVIVAL -> Permission.GAMEMODE_SWITCHER_SURVIVAL;
            case ADVENTURE -> Permission.GAMEMODE_SWITCHER_ADVENTURE;
            case SPECTATOR -> Permission.GAMEMODE_SWITCHER_SPECTATOR;
        };
    }
}
