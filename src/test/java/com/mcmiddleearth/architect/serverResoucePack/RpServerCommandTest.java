package com.mcmiddleearth.architect.serverResoucePack;

import com.mcmiddleearth.architect.ArchitectPlugin;
import com.mcmiddleearth.architect.testsupport.TestConfig;
import org.bukkit.Bukkit;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

// /rp server, which points a pack's version slot at the files of a release, as an admin uses it. Its downloads never
// reach the internet. One mock/load per class, as in LogFileTest.
class RpServerCommandTest {

    private static final String SHA = "33bece3b361f804e0966271ceaf85a691fe6a11d";

    private static ServerMock server;
    private static ArchitectPlugin plugin;

    @BeforeAll
    static void setUp() throws Exception {
        server = MockBukkit.mock();
        File folder = TestConfig.write(server,
                "ServerResourcePacks:",
                // a pack whose first entry is no section: the background work of /rp server fails on it
                "    Crash:",
                "        broken: not a section",
                "        vanilla:",
                "            16px:",
                "                light:",
                "                    '26_2':",
                "                        url: https://example.invalid/Crash-old.zip",
                "                        sha: " + SHA,
                "                footprints:",
                "                    '26_2':",
                "                        url: https://example.invalid/Crash-Footprints-old.zip",
                "                        sha: " + SHA,
                "gitHubRpReleases:",
                "    Crash:",
                "        owner: mcme",
                "        repo: crash",
                "");
        plugin = MockBukkit.load(ArchitectPlugin.class);
        assertEquals(folder, plugin.getDataFolder(), "Architect reads the test's config.yml");
    }

    @AfterAll
    static void tearDown() {
        if (MockBukkit.isMocked()) {
            MockBukkit.unmock();
        }
    }

    // An error in the background work of /rp server is logged, and the admin is told, on the main thread.
    @Test
    void anErrorInTheBackgroundIsLoggedAndReported() {
        Admin admin = admin();
        List<LogRecord> errors = errors();

        admin.performCommand("rp server Crash v2 26.2");

        String reply = awaitMessage(admin, "Error while setting server resource pack");
        assertAll(
                () -> assertTrue(reply.contains("Error while setting server resource pack"), reply),
                () -> assertEquals(List.of(), admin.offMainThread, "messages sent off the main thread"),
                () -> assertTrue(errors.stream().anyMatch(record -> record.getThrown() != null),
                        "the error in Architect's log, with its stack trace"));
    }

    private static Admin admin() {
        Admin admin = new Admin();
        server.addPlayer(admin);
        admin.setOp(false);
        admin.addAttachment(plugin, "architect.resourcePackAdmin", true);
        while (admin.nextMessage() != null) {
            // what joining said
        }
        return admin;
    }

    // the errors Architect logs from now on
    private static List<LogRecord> errors() {
        List<LogRecord> errors = new CopyOnWriteArrayList<>();
        plugin.getLogger().addHandler(new Handler() {
            @Override
            public void publish(LogRecord record) {
                if (record.getLevel() == Level.SEVERE) {
                    errors.add(record);
                }
            }

            @Override
            public void flush() {
            }

            @Override
            public void close() {
            }
        });
        return errors;
    }

    // The reply comes from work on another thread, and on a later tick.
    private static String awaitMessage(PlayerMock player, String text) {
        List<String> told = new ArrayList<>();
        long end = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (System.nanoTime() < end) {
            server.getScheduler().performOneTick();
            for (String message = player.nextMessage(); message != null; message = player.nextMessage()) {
                told.add(message);
                if (message.contains(text)) {
                    return message;
                }
            }
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        return fail("the admin was not told '" + text + "', only " + told);
    }

    // An admin who notes every message that reaches them off the main thread.
    private static final class Admin extends PlayerMock {
        private final List<String> offMainThread = new CopyOnWriteArrayList<>();

        Admin() {
            super(RpServerCommandTest.server, "Admin", UUID.randomUUID());
        }

        @Override
        public void sendMessage(String message) {
            if (!Bukkit.isPrimaryThread()) {
                offMainThread.add(message);
            }
            super.sendMessage(message);
        }
    }
}
