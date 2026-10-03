package com.mcmiddleearth.architect.biomeTuning;

import io.papermc.paper.connection.PlayerConnection;
import java.util.ArrayList;
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
    private RefreshCoordinator coordinator;

    /** Behaves like Paper: leaving the world fires the player's quit event inside the call. */
    private final Function<Player, PlayerConnection> paperLike = player -> {
        reentered.add(player.getName());
        coordinator.onQuit(new PlayerQuitEvent(player, Component.text(player.getName() + " left"),
                PlayerQuitEvent.QuitReason.DISCONNECTED));
        return null;
    };

    /** Behaves like Paper when a plugin listens to PlayerLoginEvent: nothing happens. */
    private final Function<Player, PlayerConnection> refusing = player -> null;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        coordinator = new RefreshCoordinator(new FakeNmsBridge(), () -> now, Logger.getLogger("test"), paperLike,
                BiomeTuningSettings.DEFAULTS);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    private void backInTheWorld(PlayerMock player) {
        coordinator.onJoin(new PlayerJoinEvent(player, Component.text(player.getName() + " joined")));
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
        PlayerMock alice = server.addPlayer("Alice");
        assertEquals(RefreshCoordinator.Outcome.REFUSED, coordinator.refresh(alice, true, refusing));
        assertFalse(coordinator.isRefreshing(alice.getUniqueId()));
    }

    @Test
    void theQuitMessageOfARefreshingPlayerIsSuppressed() {
        PlayerMock alice = server.addPlayer("Alice");
        List<PlayerQuitEvent> fired = new ArrayList<>();
        coordinator.refresh(alice, true, player -> {
            PlayerQuitEvent quit = new PlayerQuitEvent(player, Component.text("Alice left"), PlayerQuitEvent.QuitReason.DISCONNECTED);
            coordinator.onQuit(quit);
            fired.add(quit);
            return null;
        });
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
}
