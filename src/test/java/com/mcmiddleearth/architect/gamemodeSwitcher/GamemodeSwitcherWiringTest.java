package com.mcmiddleearth.architect.gamemodeSwitcher;

import com.mcmiddleearth.architect.ArchitectPlugin;
import com.mcmiddleearth.architect.LogFileManager;
import org.bukkit.event.HandlerList;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

import java.io.File;
import java.nio.file.Files;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

// Without ProtocolLib, as under MockBukkit, the switcher can neither tell a client its level nor read its requests:
// it stays off, Architect's log says so once, and nothing of it is registered. One mock/load per class, as in
// LogFileTest.
class GamemodeSwitcherWiringTest {

    private static ArchitectPlugin plugin;

    @BeforeAll
    static void setUp() {
        MockBukkit.mock();
        plugin = MockBukkit.load(ArchitectPlugin.class);
    }

    @AfterAll
    static void tearDown() {
        if (MockBukkit.isMocked()) {
            MockBukkit.unmock();
        }
    }

    @Test
    void withoutProtocolLibTheSwitcherIsOffAndTheLogSaysSoOnce() throws Exception {
        assertTrue(plugin.isEnabled());

        LogFileManager.flush();
        List<String> lines = Files.readAllLines(latestLog().toPath()).stream()
                .filter(line -> line.contains("game mode switcher"))
                .toList();
        assertEquals(1, lines.size(), lines.toString());
        assertTrue(HandlerList.getRegisteredListeners(plugin).stream()
                .noneMatch(registered -> registered.getListener() instanceof GamemodeSwitcher));
    }

    private static File latestLog() {
        File[] logs = new File(plugin.getDataFolder(), "logs")
                .listFiles((dir, name) -> name.startsWith("architect_") && name.endsWith(".log"));
        assertNotNull(logs, "Architect's log folder");
        assertTrue(logs.length > 0, "Architect's log");
        File latest = logs[0];
        for (File log : logs) {
            if (log.lastModified() >= latest.lastModified()) {
                latest = log;
            }
        }
        return latest;
    }
}
