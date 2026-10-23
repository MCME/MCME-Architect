package com.mcmiddleearth.architect.specialBlockHandling.command;

import com.mcmiddleearth.architect.ArchitectPlugin;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.command.ConsoleCommandSenderMock;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

// The description of /inv upload becomes the commit message of the upload script. It may hold letters, digits, spaces
// and simple punctuation, and no word of it may start with a dash. One mock/load per class.
class InvUploadTest {

    private static ServerMock server;

    @BeforeAll
    static void setUp() {
        server = MockBukkit.mock();
        MockBukkit.load(ArchitectPlugin.class); // the bundled config has the pack Human
    }

    @AfterAll
    static void tearDown() {
        if (MockBukkit.isMocked()) {
            MockBukkit.unmock();
        }
    }

    // Waits up to 10 seconds, while the server ticks, for the tasks off the main thread to end, then ticks a few times
    // more for what they scheduled on it.
    private static void awaitNoAsyncTask() throws InterruptedException {
        long end = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (!server.getScheduler().getActiveWorkers().isEmpty() && System.nanoTime() < end) {
            server.getScheduler().performOneTick();
            Thread.sleep(10);
        }
        server.getScheduler().performTicks(5);
    }

    @Test
    void ordinaryDescriptionsAreAllowed() {
        String accented = "Gr" + (char) 0xF6 + (char) 0xDF + "e and caf" + (char) 0xE9;
        for (String description : new String[] {"a new block", "oak-log fix", "Paths: 3 doors (oak), fixed!",
                accented, "rename/merge_v2; done."}) {
            assertTrue(InventoryUtil.isAllowedDescription(description), description);
        }
    }

    @Test
    void descriptionsWithOptionsOrSpecialCharactersAreRefused() {
        for (String description : new String[] {"", "  ", "--amend", "-x", "fix -n", "a*b", "what?", "[x]",
                "$(x)", "a`b", "a\"b", "a\\b", "a|b", "a&b", "a>b", "~"}) {
            assertFalse(InventoryUtil.isAllowedDescription(description), description);
        }
    }

    @Test
    void theCommandRefusesSuchADescriptionAtOnce() throws InterruptedException {
        ConsoleCommandSenderMock console = server.getConsoleSender();
        while (console.nextMessage() != null) {
            // what came before this command
        }

        server.dispatchCommand(console, "inv upload Human --amend");

        String first = console.nextMessage();
        assertNotNull(first, "an answer at once");
        assertTrue(first.contains("Invalid description."), first);
        awaitNoAsyncTask();
        for (String message = console.nextMessage(); message != null; message = console.nextMessage()) {
            assertFalse(message.contains("Error while pushing"), "no upload was started: " + message);
        }
    }
}
