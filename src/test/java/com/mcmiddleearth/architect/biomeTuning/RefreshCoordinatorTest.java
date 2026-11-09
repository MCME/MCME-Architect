package com.mcmiddleearth.architect.biomeTuning;

import com.destroystokyo.paper.profile.PlayerProfile;
import io.papermc.paper.connection.PlayerConfigurationConnection;
import io.papermc.paper.connection.PlayerConnection;
import io.papermc.paper.event.connection.configuration.PlayerConnectionReconfigureEvent;
import java.lang.reflect.Proxy;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.function.Function;
import java.util.logging.Logger;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import static org.junit.jupiter.api.Assertions.*;

class RefreshCoordinatorTest {

    private ServerMock server;
    private long now = 1_000;
    private final List<String> reentered = new ArrayList<>();
    /** Tasks handed to the main thread; a test runs them when it means "the next tick". */
    private final Deque<Runnable> mainThread = new ArrayDeque<>();
    private FakeNmsBridge bridge;
    private RefreshCoordinator coordinator;

    /** Behaves like Paper: leaving the world fires the player's quit event inside the call. */
    private final Function<Player, PlayerConnection> paperLike = player -> {
        reentered.add(player.getName());
        leaveTheWorld(player);
        return null;
    };

    /** Behaves like Paper when a plugin listens to PlayerLoginEvent: nothing happens. */
    private final Function<Player, PlayerConnection> refusing = player -> null;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        bridge = new FakeNmsBridge();
        bridge.onSwitchPastGuard = this::leaveTheWorld; // switching past the guard leaves the world too
        coordinator = coordinator(paperLike);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    private RefreshCoordinator coordinator(Function<Player, PlayerConnection> reenter) {
        return new RefreshCoordinator(bridge, () -> now, Logger.getLogger("test"), reenter,
                BiomeTuningSettings.DEFAULTS, mainThread::add);
    }

    private void leaveTheWorld(Player player) {
        coordinator.onQuit(new PlayerQuitEvent(player, Component.text(player.getName() + " left"),
                PlayerQuitEvent.QuitReason.DISCONNECTED));
    }

    private void backInTheWorld(PlayerMock player) {
        coordinator.onJoin(new PlayerJoinEvent(player, Component.text(player.getName() + " joined")));
    }

    private void nextTick() {
        while (!mainThread.isEmpty()) {
            mainThread.poll().run();
        }
    }

    /** The configuration connection Paper hands over when the player's client acknowledges; it records its calls. */
    private PlayerConfigurationConnection reconfiguring(Player player, boolean connected) {
        PlayerProfile profile = server.createProfile(player.getUniqueId(), player.getName());
        Class<?>[] type = {PlayerConfigurationConnection.class};
        return (PlayerConfigurationConnection) Proxy.newProxyInstance(type[0].getClassLoader(), type,
                (proxy, method, arguments) -> switch (method.getName()) {
                    case "getProfile" -> profile;
                    case "isConnected" -> connected;
                    case "completeReconfiguration" -> bridge.steps.add("complete " + player.getName());
                    case "disconnect" -> bridge.steps.add("disconnect " + player.getName() + ": "
                            + PlainTextComponentSerializer.plainText().serialize((Component) arguments[0]));
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == arguments[0];
                    case "toString" -> "configuration connection of " + player.getName();
                    default -> throw new UnsupportedOperationException(method.getName());
                });
    }

    private void acknowledged(Player player, boolean connected) {
        coordinator.onReconfigure(new PlayerConnectionReconfigureEvent(reconfiguring(player, connected)));
    }

    @Test
    void startsOneRefreshAtATime() {
        PlayerMock alice = server.addPlayer("Alice");
        assertEquals(RefreshCoordinator.Outcome.STARTED, coordinator.refresh(alice, true));
        assertEquals(RefreshCoordinator.Outcome.ALREADY_REFRESHING, coordinator.refresh(alice, true));
        assertEquals(List.of("Alice"), reentered);
        assertTrue(coordinator.isRefreshing(alice.getUniqueId()));
    }

    @Test
    void reportsARefusedReconfigurationAndForgetsIt() {
        coordinator = coordinator(refusing);
        PlayerMock alice = server.addPlayer("Alice");
        assertEquals(RefreshCoordinator.Outcome.REFUSED, coordinator.refresh(alice, true));
        assertFalse(coordinator.isRefreshing(alice.getUniqueId()));
    }

    @Test
    void theQuitMessageOfARefreshingPlayerIsSuppressed() {
        List<PlayerQuitEvent> fired = new ArrayList<>();
        coordinator = coordinator(player -> {
            PlayerQuitEvent quit = new PlayerQuitEvent(player, Component.text("Alice left"), PlayerQuitEvent.QuitReason.DISCONNECTED);
            coordinator.onQuit(quit);
            fired.add(quit);
            return null;
        });
        coordinator.refresh(server.addPlayer("Alice"), true);
        assertNull(fired.get(0).quitMessage());
    }

    @Test
    void joinAndQuitMessagesOfOtherPlayersAreKept() {
        PlayerMock alice = server.addPlayer("Alice");
        PlayerMock bob = server.addPlayer("Bob");
        PlayerQuitEvent bobQuit = new PlayerQuitEvent(bob, Component.text("Bob left"), PlayerQuitEvent.QuitReason.DISCONNECTED);
        coordinator.onQuit(bobQuit);
        assertNotNull(bobQuit.quitMessage(), "an ordinary quit keeps its message");

        coordinator.refresh(alice, true);
        PlayerJoinEvent aliceJoin = new PlayerJoinEvent(alice, Component.text("Alice joined"));
        coordinator.onJoin(aliceJoin);
        assertNull(aliceJoin.joinMessage());
        assertFalse(coordinator.isRefreshing(alice.getUniqueId()), "the refresh ends when the player is back");

        PlayerJoinEvent bobJoin = new PlayerJoinEvent(bob, Component.text("Bob joined"));
        coordinator.onJoin(bobJoin);
        assertNotNull(bobJoin.joinMessage());
    }

    @Test
    void aRefreshThatNeverFinishesIsDroppedAfterTheTimeout() {
        PlayerMock alice = server.addPlayer("Alice");
        coordinator.refresh(alice, true);
        now += BiomeTuningSettings.DEFAULTS.refreshTimeoutMillis();
        coordinator.expireStale();
        assertFalse(coordinator.isRefreshing(alice.getUniqueId()));
    }

    @Test
    void previewHasACooldown() {
        PlayerMock alice = server.addPlayer("Alice");
        assertEquals(RefreshCoordinator.Outcome.STARTED, coordinator.preview(alice));
        backInTheWorld(alice);
        now += 1_000;
        assertEquals(4_000, coordinator.previewCooldownLeft(alice.getUniqueId()));
        assertEquals(RefreshCoordinator.Outcome.COOLDOWN, coordinator.preview(alice));
        now += 4_000;
        assertEquals(RefreshCoordinator.Outcome.STARTED, coordinator.preview(alice));
    }

    @Test
    void publishRefreshesAFewPlayersPerSecondAfterAnnouncingIt() {
        List<PlayerMock> players = new ArrayList<>();
        for (int i = 0; i < 7; i++) {
            players.add(server.addPlayer("P" + i));
        }
        assertTrue(coordinator.publish(players, Component.text("Biome colours were updated")));
        coordinator.drainPublishQueue(server::getPlayer);
        assertEquals(5, reentered.size(), "five per second by default");
        assertEquals("Biome colours were updated",
                PlainTextComponentSerializer.plainText().serialize(players.get(0).nextComponentMessage()));
        coordinator.drainPublishQueue(server::getPlayer);
        assertEquals(7, reentered.size());
        assertFalse(coordinator.publish(players, Component.text("again")), "publish has a cooldown");
        now += BiomeTuningSettings.DEFAULTS.publishCooldownMillis();
        assertTrue(coordinator.publish(players, Component.text("again")));
    }

    // Paper refuses its own route for a player whose login ran PlayerLoginEvent (on MCME's servers: everyone, since
    // LuckPerms listens to it), so such a player is switched past Paper's guard instead
    @Test
    void aPlayerWhoseLoginRanTheOldLoginEventIsSwitchedPastPaperSGuard() {
        PlayerMock alice = server.addPlayer("Alice");
        bridge.legacyLogins.add(alice.getUniqueId());
        assertEquals(RefreshCoordinator.Outcome.STARTED, coordinator.refresh(alice, true));
        assertEquals(List.of(), reentered, "Paper's route would be refused, so it is not tried");
        assertEquals(List.of("switch Alice"), bridge.steps);
        assertTrue(coordinator.isRefreshing(alice.getUniqueId()));
    }

    @Test
    void aPlayerWhoseLoginDidNotRunItTakesPaperSRoute() {
        PlayerMock alice = server.addPlayer("Alice");
        coordinator.refresh(alice, true);
        assertEquals(List.of("Alice"), reentered);
        assertEquals(List.of(), bridge.steps);
    }

    @Test
    void withoutWhatTheReplayNeedsEveryoneTakesPaperSRoute() {
        bridge.loginReplayProblems = List.of("missing class io.papermc.paper.connection.HorriblePlayerLoginEventHack");
        PlayerMock alice = server.addPlayer("Alice");
        bridge.legacyLogins.add(alice.getUniqueId());
        coordinator.refresh(alice, true);
        assertEquals(List.of("Alice"), reentered, "switching past the guard without the replay would cost permissions");
        assertEquals(List.of(), bridge.steps);
    }

    @Test
    void aSwitchPastTheGuardThatFailsIsReportedAsRefused() {
        PlayerMock alice = server.addPlayer("Alice");
        bridge.legacyLogins.add(alice.getUniqueId());
        bridge.switchFailure = new BiomeTuningException("could not switch Alice to configuration: boom");
        assertEquals(RefreshCoordinator.Outcome.REFUSED, coordinator.refresh(alice, true));
        assertFalse(coordinator.isRefreshing(alice.getUniqueId()));
    }

    // once the player has left the world, a failing switch cannot be undone: the refresh is kept, so the client's
    // acknowledgement still completes it (or the timeout ends it) instead of nobody ever completing it
    @Test
    void aSwitchThatFailsAfterThePlayerLeftTheWorldIsStillSeenThrough() {
        PlayerMock alice = server.addPlayer("Alice");
        bridge.legacyLogins.add(alice.getUniqueId());
        bridge.switchFailureAfterLeaving = new BiomeTuningException("could not switch Alice to configuration: boom");
        assertEquals(RefreshCoordinator.Outcome.STARTED, coordinator.refresh(alice, true));
        assertTrue(coordinator.isRefreshing(alice.getUniqueId()));
        bridge.steps.clear();
        acknowledged(alice, true);
        nextTick();
        assertEquals(List.of("replay Alice", "sync Alice", "complete Alice"), bridge.steps);
    }

    @Test
    void theDebugBypassSwitchesPastTheGuardEvenAfterAPlainLogin() {
        PlayerMock alice = server.addPlayer("Alice");
        assertEquals(RefreshCoordinator.Outcome.STARTED, coordinator.refresh(alice, true, true));
        assertEquals(List.of(), reentered);
        assertEquals(List.of("switch Alice"), bridge.steps);
    }

    @Test
    void theReconfigurationWaitsForTheMainThread() {
        PlayerMock alice = server.addPlayer("Alice");
        coordinator.refresh(alice, true);
        acknowledged(alice, true);
        assertEquals(List.of(), bridge.steps, "Paper fires the event on a network thread; nothing happens there");
        nextTick();
        assertEquals(List.of("sync Alice", "complete Alice"), bridge.steps);
        backInTheWorld(alice);
        assertFalse(coordinator.isRefreshing(alice.getUniqueId()));
    }

    @Test
    void aRefreshPastTheGuardReplaysTheLoginBeforeTheSyncAndTheReturn() {
        PlayerMock alice = server.addPlayer("Alice");
        bridge.legacyLogins.add(alice.getUniqueId());
        coordinator.refresh(alice, true);
        bridge.steps.clear();
        acknowledged(alice, true);
        nextTick();
        assertEquals(List.of("replay Alice", "sync Alice", "complete Alice"), bridge.steps,
                "plugins set the new player up in the replayed login, before the client is sent anything");
        backInTheWorld(alice);
        assertFalse(coordinator.isRefreshing(alice.getUniqueId()));
    }

    @Test
    void aRefreshWithoutTheSyncStillReplaysTheLogin() {
        PlayerMock alice = server.addPlayer("Alice");
        bridge.legacyLogins.add(alice.getUniqueId());
        coordinator.refresh(alice, false);
        bridge.steps.clear();
        acknowledged(alice, true);
        nextTick();
        assertEquals(List.of("replay Alice", "complete Alice"), bridge.steps);
    }

    @Test
    void aLoginThatAPluginRefusesEndsTheRefreshThere() {
        PlayerMock alice = server.addPlayer("Alice");
        bridge.legacyLogins.add(alice.getUniqueId());
        bridge.replayRefuses = true;
        coordinator.refresh(alice, true);
        bridge.steps.clear();
        acknowledged(alice, true);
        nextTick();
        assertEquals(List.of("replay Alice (refused)"), bridge.steps, "the bridge disconnected them; nothing follows");
        assertFalse(coordinator.isRefreshing(alice.getUniqueId()));
    }

    @Test
    void aReplayThatFailsDisconnectsRatherThanReturningWithoutTheLogin() {
        PlayerMock alice = server.addPlayer("Alice");
        bridge.legacyLogins.add(alice.getUniqueId());
        bridge.replayFailure = new BiomeTuningException("could not replay the login: boom");
        coordinator.refresh(alice, true);
        bridge.steps.clear();
        acknowledged(alice, true);
        nextTick();
        assertEquals(List.of("disconnect Alice: Biome refresh did not finish - please rejoin."), bridge.steps,
                "without the replayed login, plugins such as LuckPerms would not have set up the new player");
        assertFalse(coordinator.isRefreshing(alice.getUniqueId()));
    }

    @Test
    void aConnectionThatClosedWhileWaitingIsLeftAlone() {
        PlayerMock alice = server.addPlayer("Alice");
        bridge.legacyLogins.add(alice.getUniqueId());
        coordinator.refresh(alice, true);
        bridge.steps.clear();
        acknowledged(alice, false);
        nextTick();
        assertEquals(List.of(), bridge.steps, "no login is replayed for a client that is gone");
        assertFalse(coordinator.isRefreshing(alice.getUniqueId()));
    }

    @Test
    void aReconfigurationThatIsNotOursIsLeftAlone() {
        PlayerMock alice = server.addPlayer("Alice");
        acknowledged(alice, true);
        nextTick();
        assertEquals(List.of(), bridge.steps, "another plugin's reconfiguration is that plugin's to complete");
    }
}
