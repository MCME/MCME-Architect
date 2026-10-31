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
import java.util.concurrent.Executor;
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
 * Refreshes clients so they re-read biome definitions. It sends the player back to the configuration phase; then, on
 * the reconfigure event, it queues vanilla's full registry sync and completes. On Paper 26.2 this is a quit and rejoin
 * on the server, so the quit and join messages of refreshing players are suppressed. It also spaces out previews
 * and publishes.
 * <p>
 * Paper refuses its own route, reenterConfiguration(), for a player whose login ran the deprecated PlayerLoginEvent,
 * which it runs whenever a plugin listens to that event (LuckPerms does). Such a player is switched to configuration
 * past Paper's guard, and Paper's own login step is replayed while they reconfigure: the rejoin builds a new player
 * object, and plugins set that object up in PlayerLoginEvent, as at a real login.
 * <p>
 * Paper fires the reconfigure event on a network thread; everything else here runs on the main thread, which the
 * event hands its work to.
 */
public final class RefreshCoordinator implements Listener {

    public enum Outcome { STARTED, ALREADY_REFRESHING, COOLDOWN, REFUSED }

    /** What a builder reads when a refresh is refused; the server log has the technical reason. */
    static final String NO_LIVE_PREVIEW = "Live preview is not available on this server right now. "
            + "Switching servers and back, or rejoining, shows the live values.";

    /** The disconnect message of a refresh that cannot finish. */
    static final String UNFINISHED = "Biome refresh did not finish - please rejoin.";

    private record QueuedRefresh(UUID player, Component announcement) {
    }

    private final NmsBridge bridge;
    private final LongSupplier clock;
    private final Logger logger;
    private final Function<Player, PlayerConnection> defaultReenter;
    private final BiomeTuningSettings settings;
    private final Executor mainThread;
    private final RefreshTracker tracker = new RefreshTracker();
    /** The last connection seen per refreshing player, so a stuck refresh can be disconnected. */
    private final Map<UUID, PlayerConnection> connections = new HashMap<>();
    private final Map<UUID, Long> lastPreview = new HashMap<>();
    private final Deque<QueuedRefresh> publishQueue = new ArrayDeque<>();
    private long lastPublish = -1;

    public RefreshCoordinator(NmsBridge bridge, LongSupplier clock, Logger logger,
                              Function<Player, PlayerConnection> defaultReenter, BiomeTuningSettings settings,
                              Executor mainThread) {
        this.bridge = bridge;
        this.clock = clock;
        this.logger = logger;
        this.defaultReenter = defaultReenter;
        this.settings = settings;
        this.mainThread = mainThread;
    }

    /** Paper's API route. Paper refuses it for a player who logged in while any plugin listened to PlayerLoginEvent. */
    public static PlayerConnection reenter(Player player) {
        PlayerGameConnection connection = player.getConnection();
        connection.reenterConfiguration();
        return connection;
    }

    /** Refreshes the player by the route that works for them. */
    public Outcome refresh(Player player, boolean inject) {
        return refresh(player, inject, loggedInWithLegacyEvent(player));
    }

    /**
     * {@code pastGuard}: switch the player to configuration past Paper's guard and replay their login while they
     * reconfigure, instead of taking Paper's route. The debug command can force it.
     */
    public Outcome refresh(Player player, boolean inject, boolean pastGuard) {
        return pastGuard ? start(player, inject, true, bridge::switchToConfigurationIgnoringLoginGuard)
                : start(player, inject, false, defaultReenter::apply);
    }

    /** Paper's route, taken by {@code reenter}: tests stand in for Paper with it. */
    public Outcome refresh(Player player, boolean inject, Function<Player, PlayerConnection> reenter) {
        return start(player, inject, false, reenter::apply);
    }

    /** Sends the player to the configuration phase; a refused or failed switch leaves them where they are. */
    @FunctionalInterface
    private interface Reenter {
        PlayerConnection apply(Player player) throws BiomeTuningException;
    }

    private Outcome start(Player player, boolean inject, boolean pastGuard, Reenter reenter) {
        UUID id = player.getUniqueId();
        if (!tracker.begin(id, inject, pastGuard, clock.getAsLong())) {
            return Outcome.ALREADY_REFRESHING;
        }
        logger.info("biometune: refreshing " + player.getName() + " (inject=" + inject
                + (pastGuard ? ", past Paper's login guard" : "") + ")");
        PlayerConnection connection;
        try {
            connection = reenter.apply(player); // the player's PlayerQuitEvent fires inside this call
        } catch (BiomeTuningException e) {
            tracker.forget(id);
            logger.warning("biometune: " + e.getMessage());
            return Outcome.REFUSED;
        } catch (RuntimeException e) {
            tracker.forget(id);
            throw e;
        }
        if (!tracker.hasLeftWorld(id)) {
            tracker.forget(id);
            logger.warning("biometune: Paper did not reconfigure " + player.getName()
                    + "; its warning about it is in the server log");
            return Outcome.REFUSED;
        }
        if (connection != null) {
            connections.put(id, connection);
        }
        return Outcome.STARTED;
    }

    /** Whether Paper would refuse its own route for this player, so that the login replay stands in for it. */
    private boolean loggedInWithLegacyEvent(Player player) {
        if (!bridge.loginReplayCheck().isEmpty()) {
            // a switch past the guard without the replay would cost the player what plugins set up at login
            return false;
        }
        try {
            return bridge.loggedInWithLegacyEvent(player);
        } catch (BiomeTuningException e) {
            logger.warning("biometune: could not tell how " + player.getName()
                    + " logged in, so Paper's route is used: " + e.getMessage());
            return false;
        }
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

    /** The client acknowledged. Paper holds it in the configuration phase until completeReconfiguration(). */
    @EventHandler
    public void onReconfigure(PlayerConnectionReconfigureEvent event) {
        PlayerConfigurationConnection connection = event.getConnection();
        mainThread.execute(() -> reconfigured(connection));
    }

    /** Replays the login when the player came past Paper's guard, queues the sync, and lets the player return. */
    private void reconfigured(PlayerConfigurationConnection connection) {
        UUID id = connection.getProfile().getId();
        RefreshTracker.Entry entry = id == null ? null : tracker.markReconfiguring(id, clock.getAsLong());
        if (entry == null) {
            return; // not a refresh we started
        }
        if (!connection.isConnected()) {
            end(id);
            logger.info("biometune: " + id + " disconnected during the refresh");
            return;
        }
        connections.put(id, connection);
        if (entry.replayLogin() && !replayLogin(id, connection)) {
            return;
        }
        if (entry.inject()) {
            try {
                bridge.injectRegistrySync(connection);
            } catch (BiomeTuningException e) {
                logger.warning("biometune: registry sync for " + id + " failed, completing without it: " + e.getMessage());
            }
        }
        logger.info("biometune: " + id + " acknowledged after " + (entry.reconfiguredAt() - entry.startedAt())
                + " ms (injected=" + entry.inject() + ", login replayed=" + entry.replayLogin() + ")");
        connection.completeReconfiguration();
    }

    /**
     * Paper's own login step for the player object this reconfiguration spawns; false if the refresh ends here. A
     * replay that fails disconnects the player: returning without it would cost them what plugins set up at login, such
     * as LuckPerms' permissions.
     */
    private boolean replayLogin(UUID id, PlayerConfigurationConnection connection) {
        try {
            if (bridge.replayLegacyLogin(connection)) {
                return true;
            }
            logger.info("biometune: a plugin refused the replayed login of " + id + ", which disconnected them");
        } catch (BiomeTuningException e) {
            logger.warning("biometune: replaying the login of " + id + " failed, so they were disconnected: "
                    + e.getMessage());
            connection.disconnect(Component.text(UNFINISHED));
        }
        end(id);
        return false;
    }

    private void end(UUID id) {
        tracker.forget(id);
        connections.remove(id);
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
                connection.disconnect(Component.text(UNFINISHED));
            }
        }
    }

    public void shutdown() {
        connections.clear();
        publishQueue.clear();
    }
}
