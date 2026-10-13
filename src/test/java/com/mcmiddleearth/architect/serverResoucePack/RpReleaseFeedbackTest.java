package com.mcmiddleearth.architect.serverResoucePack;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.event.player.PlayerJoinEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.command.ConsoleCommandSenderMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

// Who gets the lines of /rp release, and when. A plain RpReleaseFeedback on a mock plugin: not registered, so each
// test calls its join handler itself.
class RpReleaseFeedbackTest {

    private ServerMock server;
    private RpReleaseFeedback feedback;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        feedback = new RpReleaseFeedback(MockBukkit.createMockPlugin(), "[Architect] ");
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    private static Component line(String text) {
        return Component.text(text);
    }

    private static List<String> told(PlayerMock player) {
        List<String> told = new ArrayList<>();
        for (Component message = player.nextComponentMessage(); message != null; message = player.nextComponentMessage()) {
            told.add(PlainTextComponentSerializer.plainText().serialize(message));
        }
        return told;
    }

    private void join(PlayerMock player) {
        feedback.onJoin(new PlayerJoinEvent(player, Component.empty()));
        server.getScheduler().performTicks(RpReleaseFeedback.DELIVERY_DELAY_TICKS);
    }

    @Test
    void aReleaserOnTheServerGetsEveryLine() {
        PlayerMock admin = server.addPlayer("Admin");
        feedback.tell(admin, line("start"));
        feedback.finish(admin, List.of(line("done"), line("Put it live")));
        assertEquals(List.of("start", "done", "Put it live"), told(admin));
    }

    @Test
    void aReleaserWhoWasOnTheServerIsNotToldTheResultAgainOnTheirNextJoin() {
        PlayerMock admin = server.addPlayer("Admin");
        feedback.finish(admin, List.of(line("done")));
        assertEquals(List.of("done"), told(admin));
        admin.disconnect();
        admin.reconnect();
        told(admin); // what reconnecting said
        join(admin);
        assertEquals(List.of(), told(admin));
    }

    @Test
    void aReleaserWhoLeftGetsTheClosingMessageWhenBackButNoSteps() {
        PlayerMock admin = server.addPlayer("Admin");
        admin.disconnect();
        feedback.tell(admin, line("Human: Sodium zips done"));
        feedback.finish(admin, List.of(line("Human v4.1.3 is released.")));
        admin.reconnect();
        told(admin); // what reconnecting said
        join(admin);
        assertEquals(List.of("[Architect] While you were away:", "Human v4.1.3 is released."), told(admin));
    }

    @Test
    void heldResultsComeInTheirOrderAndOnlyOnce() {
        PlayerMock admin = server.addPlayer("Admin");
        admin.disconnect();
        feedback.finish(admin, List.of(line("Human v4.1.3 is released.")));
        feedback.finish(admin, List.of(line("Rohan v1.0 failed."), line("Last output: x")));
        admin.reconnect();
        told(admin);
        join(admin);
        join(admin);
        assertEquals(List.of("[Architect] While you were away:", "Human v4.1.3 is released.", "Rohan v1.0 failed.",
                "Last output: x"), told(admin));
    }

    @Test
    void aReleaserWhoLeftAgainBeforeTheDeliveryKeepsTheirResults() {
        PlayerMock admin = server.addPlayer("Admin");
        admin.disconnect();
        feedback.finish(admin, List.of(line("Human v4.1.3 is released.")));
        admin.reconnect();
        feedback.onJoin(new PlayerJoinEvent(admin, Component.empty()));
        admin.disconnect();
        server.getScheduler().performTicks(RpReleaseFeedback.DELIVERY_DELAY_TICKS);
        admin.reconnect();
        told(admin);
        join(admin);
        assertEquals(List.of("[Architect] While you were away:", "Human v4.1.3 is released."), told(admin));
    }

    // Paper gives a player who rejoins a new Player object, while /rp release still holds the old one
    @Test
    void aReleaserWhoRejoinedIsReachedUnderTheNewConnection() {
        PlayerMock admin = server.addPlayer("Admin");
        admin.disconnect();
        PlayerMock again = new PlayerMock(server, "Admin", admin.getUniqueId());
        server.addPlayer(again);
        told(again);
        feedback.tell(admin, line("Human: Lite zips done"));
        feedback.finish(admin, List.of(line("Human v4.1.3 is released.")));
        assertEquals(List.of("Human: Lite zips done", "Human v4.1.3 is released."), told(again));
    }

    @Test
    void theConsoleGetsEveryLine() {
        ConsoleCommandSenderMock console = (ConsoleCommandSenderMock) server.getConsoleSender();
        feedback.tell(console, line("start"));
        feedback.finish(console, List.of(line("done")));
        assertEquals("start", console.nextMessage());
        assertEquals("done", console.nextMessage());
    }
}
