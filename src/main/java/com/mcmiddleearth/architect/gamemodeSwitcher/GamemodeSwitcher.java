package com.mcmiddleearth.architect.gamemodeSwitcher;

import com.mcmiddleearth.architect.Log;
import com.mcmiddleearth.architect.Modules;
import com.mcmiddleearth.architect.Permission;
import com.mcmiddleearth.architect.PluginData;
import net.kyori.adventure.text.Component;
import org.bukkit.GameMode;
import org.bukkit.GameRules;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerCommandSendEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.plugin.Plugin;

import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Lets builders who are not op switch game mode with the vanilla switcher (F3+F4, and F3+N for spectator), with a
 * permission per mode, in worlds where the gamemodeSwitcher module is on. Ops are left to vanilla. Holders of
 * minecraft.command.gamemode, whom the server lets switch anyway, get the switcher there too, and the server answers
 * their requests itself.
 * <p>
 * The client opens the switcher only at permission level 2 or more. The server sends each player their real level
 * when they join, respawn or change worlds, and when their op status changes, each time followed by the command tree.
 * So a builder is told level 2 a tick after each of these events and after each command tree, and level 0 again once
 * they no longer qualify. Paper's Player#sendOpLevel tells the client a level as vanilla does, with no command tree
 * after it. The server refuses a builder's request from the switcher, so {@link #onSwitchRequest} takes it first,
 * and the mode is set here with the Bukkit API. {@link GamemodeSwitcherProtocol} hands each request over, with
 * ProtocolLib.
 */
public final class GamemodeSwitcher implements Listener {

    /** The level at which the client opens the switcher. */
    private static final int SWITCHER_LEVEL = 2;
    /** The level of every player who is not op. */
    private static final int PLAYER_LEVEL = 0;
    /** Paper lets holders of this permission use the switcher, as it does ops. */
    private static final String GAMEMODE_COMMAND = "minecraft.command.gamemode";

    /** The switcher Architect runs, once ProtocolLib has let it start. Main thread only. */
    private static GamemodeSwitcher running;

    /** Tells a player's client its permission level, 0 to 4. */
    @FunctionalInterface
    public interface LevelSender {
        void send(Player player, int level);
    }

    private final Plugin plugin;
    private final LevelSender levelSender;
    /** Players whose client was told the switcher's level. Written on the main thread, read on the network thread. */
    private final Set<UUID> withSwitcher = ConcurrentHashMap.newKeySet();
    /** Each builder's latest request not yet answered, so that a tick answers a builder once. */
    private final Map<UUID, GameMode> pending = new ConcurrentHashMap<>();

    public GamemodeSwitcher(Plugin plugin, LevelSender levelSender) {
        this.plugin = plugin;
        this.levelSender = levelSender;
    }

    /** The switcher for a server, where Paper tells each client its level. */
    public static GamemodeSwitcher forServer(Plugin plugin) {
        return new GamemodeSwitcher(plugin, (player, level) -> player.sendOpLevel((byte) level));
    }

    /**
     * Starts listening, and gives the players online their level a tick later: they may have joined before the
     * switcher started, as when plugins are reloaded.
     */
    public void start() {
        stopRunning();
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        running = this;
        updateAllLater();
    }

    /** Gives the players online their level again a tick later: /architect reload may have changed the modules. */
    public static void updateRunning() {
        if (running != null) {
            running.updateAllLater();
        }
    }

    /**
     * Architect is stopping, and nothing will answer the switcher's requests: the players it gave the switcher to get
     * level 0 back at once, as nothing may be scheduled then.
     */
    public static void stopRunning() {
        GamemodeSwitcher switcher = running;
        running = null;
        if (switcher == null) {
            return;
        }
        HandlerList.unregisterAll(switcher);
        for (UUID id : switcher.withSwitcher) {
            Player player = switcher.plugin.getServer().getPlayer(id);
            if (player != null && !player.isOp()) {
                switcher.levelSender.send(player, PLAYER_LEVEL);
            }
        }
        switcher.withSwitcher.clear();
        switcher.pending.clear();
    }

    private void updateAllLater() {
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            updateLevelLater(player);
        }
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
        UUID id = player.getUniqueId();
        if (getsSwitcher(player)) {
            withSwitcher.add(id);
            levelSender.send(player, SWITCHER_LEVEL);
        } else if (withSwitcher.remove(id) && !player.isOp()) {
            // an op's level came with the op change, and Bukkit does not tell what it is
            levelSender.send(player, PLAYER_LEVEL);
        }
    }

    /**
     * A request from the switcher, passed on by the packet listener on the network thread. Returns true when
     * Architect takes the request, and the packet must go no further. It takes only the requests of builders it gave
     * the switcher to, whom the server would refuse: every other packet, holders of minecraft.command.gamemode
     * included, goes on to the server, which counts it against its packet limit and answers it itself. A builder's
     * requests within a tick get one answer, on the main thread, for the latest: the mode is set if they may use it
     * in their world, or else they are told they can't switch to it there.
     * <p>
     * isOp and hasPermission are only read here, off the main thread, and either outcome is checked again on the main
     * thread: by Architect's answer, or by the server's own handler.
     */
    public boolean onSwitchRequest(Player player, GameMode mode) {
        UUID id = player.getUniqueId();
        if (!withSwitcher.contains(id) || vanillaLetsSwitch(player)) {
            return false;
        }
        if (pending.put(id, mode) == null) {
            plugin.getServer().getScheduler().runTask(plugin, () -> answer(player));
        }
        return true;
    }

    private void answer(Player player) {
        GameMode mode = pending.remove(player.getUniqueId());
        if (mode == null || !player.isOnline()) {
            return;
        }
        String name = mode.name().toLowerCase(Locale.ROOT);
        if (!moduleOn(player) || !PluginData.hasPermission(player, permission(mode))) {
            player.sendMessage(Component.text("You can't switch to " + name + " mode here."));
            Log.info(player.getName() + " was refused " + name + " mode by the game mode switcher in world "
                    + player.getWorld().getName() + ".");
            return;
        }
        if (player.getGameMode() == mode) {
            return;
        }
        player.setGameMode(mode);
        if (player.getGameMode() != mode) {
            return; // another plugin cancelled the change
        }
        // as vanilla, which tells the player only where the world's sendCommandFeedback is on
        if (!Boolean.FALSE.equals(player.getWorld().getGameRuleValue(GameRules.SEND_COMMAND_FEEDBACK))) {
            player.sendMessage(Component.translatable("commands.gamemode.success.self",
                    Component.translatable("gameMode." + name)));
        }
        Log.info(player.getName() + " switched to " + name + " mode with the game mode switcher.");
    }

    /** The server's own check: vanilla's (op), or Paper's permission. */
    private static boolean vanillaLetsSwitch(Player player) {
        return player.isOp() || player.hasPermission(GAMEMODE_COMMAND);
    }

    /**
     * Whether the switcher opens for the player here: for builders with a mode's permission, and for holders of
     * minecraft.command.gamemode, whom the server lets switch itself. Ops have their own level.
     */
    private static boolean getsSwitcher(Player player) {
        if (player.isOp() || !moduleOn(player)) {
            return false;
        }
        if (player.hasPermission(GAMEMODE_COMMAND)) {
            return true;
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
