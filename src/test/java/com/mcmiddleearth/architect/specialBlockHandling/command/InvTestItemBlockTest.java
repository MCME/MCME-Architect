package com.mcmiddleearth.architect.specialBlockHandling.command;

import com.mcmiddleearth.architect.ArchitectPlugin;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

// The op-only developer command /inv testItemBlock places a test grid of an item block. Its size must be 1 to 32 and
// its step at least 1. Every command names the pack, which is resolved before this branch. The tests use an unknown
// block id, so nothing is ever placed. One mock/load per class.
class InvTestItemBlockTest {

    private static PlayerMock op;

    @BeforeAll
    static void setUp() {
        ServerMock server = MockBukkit.mock();
        MockBukkit.load(ArchitectPlugin.class); // the bundled config has the pack Human
        server.addSimpleWorld("world");
        op = server.addPlayer();
        op.setOp(true);
    }

    @AfterAll
    static void tearDown() {
        if (MockBukkit.isMocked()) {
            MockBukkit.unmock();
        }
    }

    private static List<String> answersTo(String command) {
        while (op.nextMessage() != null) {
            // what came before this command
        }
        op.performCommand(command);
        List<String> answers = new ArrayList<>();
        for (String message = op.nextMessage(); message != null; message = op.nextMessage()) {
            answers.add(message);
        }
        return answers;
    }

    @ParameterizedTest
    @ValueSource(strings = {"nope 5 0", "nope 5 -1", "nope 33 1", "nope 0 1", "nope", "nope x 1"})
    void aGridOutsideTheLimitsIsRefused(String arguments) {
        List<String> answers = answersTo("inv testItemBlock " + arguments + " rp:Human");

        assertFalse(answers.isEmpty(), "an answer");
        assertTrue(answers.get(0).contains("Usage: /inv testItemBlock"), answers.toString());
    }

    @Test
    void anUnknownBlockIsRefused() {
        List<String> answers = answersTo("inv testItemBlock nope 2 1 rp:Human");

        assertFalse(answers.isEmpty(), "an answer");
        assertTrue(answers.get(0).contains("No special block found for: nope"), answers.toString());
    }
}
