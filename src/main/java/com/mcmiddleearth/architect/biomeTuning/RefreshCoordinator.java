package com.mcmiddleearth.architect.biomeTuning;

import io.papermc.paper.connection.PlayerConfigurationConnection;
import io.papermc.paper.connection.PlayerConnection;
import io.papermc.paper.connection.PlayerGameConnection;
import io.papermc.paper.event.connection.configuration.PlayerConnectionReconfigureEvent;
import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.LongSupplier;
import java.util.logging.Logger;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * Refreshes clients so they re-read biome definitions. It calls reenterConfiguration(); then, on the
 * reconfigure event, it queues vanilla's full registry sync and completes. On Paper 26.2 this is a quit and rejoin
 * on the server, so the quit and join messages of refreshing players are suppressed. It also spaces out previews
 * and publishes.
 */
public final class RefreshCoordinator implements Listener {

    public enum Outcome { STARTED, ALREADY_REFRESHING, COOLDOWN, REFUSED }

    private record QueuedRefresh(UUID player, Component announcement) {
    }

    private final NmsBridge bridge;
    private final LongSupplier clock;
    private final Logger logger;
    private final Function<Player, PlayerConnection> defaultReenter;
    private final BiomeTuningSettings settings;
    private final RefreshTracker tracker = new RefreshTracker();
    /** The last connection seen per refreshing player, so a stuck refresh can be disconnected. */
    private final Map<UUID, PlayerConnection> connections = new HashMap<>();
    private final Map<UUID, Long> lastPreview = new HashMap<>();
    private final Deque<QueuedRefresh> publishQueue = new ArrayDeque<>();
    private long lastPublish = -1;

    public RefreshCoordinator(NmsBridge bridge, LongSupplier clock, Logger logger,
                              Function<Player, PlayerConnection> defaultReenter, BiomeTuningSettings settings) {
        this.bridge = bridge;
        this.clock = clock;
        this.logger = logger;
        this.defaultReenter = defaultReenter;
        this.settings = settings;
    }

    /** Paper's API route. Paper refuses it for a player who logged in while any plugin listened to PlayerLoginEvent. */
    public static PlayerConnection reenter(Player player) {
        PlayerGameConnection connection = player.getConnection();
        connection.reenterConfiguration();
        return connection;
    }

    public Outcome refresh(Player player, boolean inject) {
        return refresh(player, inject, defaultReenter);
    }

    public Outcome refresh(Player player, boolean inject, Function<Player, PlayerConnection> reenter) {
        UUID id = player.getUniqueId();
        if (!tracker.begin(id, inject, clock.getAsLong())) {
            return Outcome.ALREADY_REFRESHING;
        }
        logger.info("biometune: refreshing " + player.getName() + " (inject=" + inject + ")");
        PlayerConnection connection;
        try {
            connection = reenter.apply(player); // the player's PlayerQuitEvent fires inside this call
        } catch (RuntimeException e) {
            tracker.forget(id);
            throw e;
        }
        if (!tracker.hasLeftWorld(id)) {
            tracker.forget(id);
            logger.warning("biometune: Paper did not reconfigure " + player.getName()
                    + "; a plugin probably listens to PlayerLoginEvent (see Paper's log line about it)");
            return Outcome.REFUSED;
        }
        if (connection != null) {
            connections.put(id, connection);
        }
        return Outcome.STARTED;
    }

    /** Milliseconds until {@code player} may preview again; 0 when they may. */
    public long previewCooldownLeft(UUID player) {
        Long last = lastPreview.get(player);
        return last == null ? 0 : Math.max(0, settings.refreshCooldownMillis() - (clock.getAsLong() - last));
    }

    /** A builder's own preview: at most one per cooldown. */
    public Outcome preview(Player player) {
        if (previewCooldownLeft(player.getUniqueId()) > 0) {
            return Outcome.COOLDOWN;
        }
        Outcome outcome = refresh(player, true);
        if (outcome == Outcome.STARTED) {
            lastPreview.put(player.getUniqueId(), clock.getAsLong());
        }
        return outcome;
    }

    /** Queues {@code players} for a refresh; false while the publish cooldown runs. */
    public boolean publish(Collection<? extends Player> players, Component announcement) {
        long now = clock.getAsLong();
        if (lastPublish >= 0 && now - lastPublish < settings.publishCooldownMillis()) {
            return false;
        }
        lastPublish = now;
        for (Player player : players) {
            publishQueue.add(new QueuedRefresh(player.getUniqueId(), announcement));
        }
        return true;
    }

    /** Called once a second: announces and refreshes up to publishPlayersPerSecond queued players. */
    public void drainPublishQueue(Function<UUID, Player> online) {
        int budget = settings.publishPlayersPerSecond();
        while (budget > 0 && !publishQueue.isEmpty()) {
            QueuedRefresh next = publishQueue.poll();
            Player player = online.apply(next.player());
            if (player == null || tracker.isPending(next.player())) {
                continue;
            }
            player.sendMessage(next.announcement()); // before the refresh: chat cannot reach a reconfiguring player
            refresh(player, true);
            budget--;
        }
    }

    public boolean isRefreshing(UUID player) {
        return tracker.isPending(player);
    }

    @EventHandler
    public void onReconfigure(PlayerConnectionReconfigureEvent event) {
        PlayerConfigurationConnection connection = event.getConnection();
        UUID id = connection.getProfile().getId();
        RefreshTracker.Entry entry = id == null ? null : tracker.markReconfiguring(id, clock.getAsLong());
        if (entry == null) {
            return; // not a refresh we started
        }
        connections.put(id, connection);
        if (entry.inject()) {
            try {
                bridge.injectRegistrySync(connection);
            } catch (BiomeTuningException e) {
                logger.warning("biometune: registry sync for " + id + " failed, completing without it: " + e.getMessage());
            }
        }
        logger.info("biometune: " + id + " acknowledged after " + (entry.reconfiguredAt() - entry.startedAt())
                + " ms (injected=" + entry.inject() + ")");
        connection.completeReconfiguration();
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onQuit(PlayerQuitEvent event) {
        if (tracker.markLeftWorld(event.getPlayer().getUniqueId())) {
            event.quitMessage(null);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onJoin(PlayerJoinEvent event) {
        RefreshTracker.Entry entry = tracker.complete(event.getPlayer().getUniqueId());
        if (entry == null) {
            return;
        }
        connections.remove(entry.player());
        event.joinMessage(null);
        logger.info("biometune: " + event.getPlayer().getName() + " back in the world after "
                + (clock.getAsLong() - entry.startedAt()) + " ms");
    }

    /** Called once a second. A refresh that has not finished in time ends with a clear disconnect, not an endless loading screen. */
    public void expireStale() {
        for (RefreshTracker.Entry entry : tracker.expire(clock.getAsLong(), settings.refreshTimeoutMillis())) {
            PlayerConnection connection = connections.remove(entry.player());
            logger.warning("biometune: refresh of " + entry.player() + " timed out in stage " + entry.stage());
            if (connection != null && connection.isConnected()) {
                connection.disconnect(Component.text("Biome refresh did not finish - please rejoin."));
            }
        }
    }

    public void shutdown() {
        connections.clear();
        publishQueue.clear();
    }
}
