package com.mcmiddleearth.architect.serverResoucePack;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Delivers the chat lines of /rp release, on the main thread. A player is reached under their current connection, so
 * one who left and came back during a release still gets its lines. The closing message for a releaser who isn't on
 * the server is held, and shown when they next join it; a server restart loses it, while Architect's log keeps every
 * line.
 */
public final class RpReleaseFeedback implements Listener {

    /** How long after a join held results come: after what joining says, about 2 seconds. */
    static final long DELIVERY_DELAY_TICKS = 40;

    private final Plugin plugin;
    private final String prefix;
    private final Map<UUID, List<List<Component>>> held = new HashMap<>();

    public RpReleaseFeedback(Plugin plugin, String prefix) {
        this.plugin = plugin;
        this.prefix = prefix;
    }

    /** Sends a line to the sender if they can be reached now. A start or step line is never held. */
    public void tell(CommandSender sender, Component line) {
        CommandSender now = current(sender);
        if (now != null) {
            now.sendMessage(line);
        }
    }

    /** Sends the closing message now, or holds it for a player who isn't on the server. */
    public void finish(CommandSender sender, List<Component> lines) {
        CommandSender now = current(sender);
        if (now != null) {
            lines.forEach(now::sendMessage);
        } else if (sender instanceof Player player) {
            held.computeIfAbsent(player.getUniqueId(), id -> new ArrayList<>()).add(List.copyOf(lines));
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        if (held.containsKey(id)) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> deliver(id), DELIVERY_DELAY_TICKS);
        }
    }

    // A player who left again before the delivery keeps their results for their next join
    private void deliver(UUID id) {
        Player player = Bukkit.getPlayer(id);
        if (player == null) {
            return;
        }
        List<List<Component>> results = held.remove(id);
        if (results == null) {
            return;
        }
        player.sendMessage(RpReleaseChat.awayHeader(prefix));
        results.forEach(lines -> lines.forEach(player::sendMessage));
    }

    // The sender as they can be reached now: a player under their current connection, or null when they aren't on
    // the server; anyone else, such as the console, as they are
    private static CommandSender current(CommandSender sender) {
        return sender instanceof Player player ? Bukkit.getPlayer(player.getUniqueId()) : sender;
    }
}
