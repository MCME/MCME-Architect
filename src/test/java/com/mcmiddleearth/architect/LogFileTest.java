package com.mcmiddleearth.architect;

import org.junit.jupiter.api.*;
import org.mockbukkit.mockbukkit.MockBukkit;
import java.io.File;
import java.nio.file.Files;
import static org.junit.jupiter.api.Assertions.*;

// One mock/load per class: Architect caches getDataFolder()-derived paths in static fields at
// class-load, so re-loading the plugin in the same JVM would use a stale (deleted) path. With
// surefire reuseForks=false (a fresh JVM per test class), this keeps the path correct throughout.
class LogFileTest {
    private static ArchitectPlugin plugin;

    @BeforeAll static void setUp() { MockBukkit.mock(); plugin = MockBukkit.load(ArchitectPlugin.class); }
    @AfterAll  static void tearDown() { if (MockBukkit.isMocked()) MockBukkit.unmock(); }

    @Test void logFileCreatedAndOutOfServerLog() {
        File[] logs = logs();
        assertNotNull(logs, "logs/ dir should exist");
        assertTrue(logs.length >= 1, "a log file should be created on enable");
        assertFalse(plugin.getLogger().getUseParentHandlers(),
                "Architect logging must not propagate to the server console");
    }

    @Test void infoIsWrittenToFile() throws Exception {
        Log.info("marker-12345");
        LogFileManager.flush();
        assertTrue(readLatestLog().contains("marker-12345"));
    }

    @Test void errorWritesMessageAndStacktrace() throws Exception {
        Log.error("boom-context", new IllegalStateException("kaboom"));
        LogFileManager.flush();
        String c = readLatestLog();
        assertTrue(c.contains("boom-context"), "error message present");
        assertTrue(c.contains("kaboom"), "stacktrace present");
    }

    @Test void devUtilDebugWritesToFileWhenLevelPasses() throws Exception {
        com.mcmiddleearth.util.DevUtil.setLevel(5);
        com.mcmiddleearth.util.DevUtil.log(1, "dbg-777");
        LogFileManager.flush();
        assertTrue(readLatestLog().contains("dbg-777"));
    }

    @Test void devUtilDebugSuppressedBelowLevel() throws Exception {
        com.mcmiddleearth.util.DevUtil.setLevel(1);
        com.mcmiddleearth.util.DevUtil.log(9, "dbg-should-not-appear");
        LogFileManager.flush();
        assertFalse(readLatestLog().contains("dbg-should-not-appear"));
    }

    private File[] logs() {
        return new File(plugin.getDataFolder(), "logs")
                .listFiles((d, n) -> n.startsWith("architect_") && n.endsWith(".log"));
    }
    private String readLatestLog() throws Exception {
        File[] logs = logs();
        File latest = logs[0];
        for (File f : logs) if (f.lastModified() >= latest.lastModified()) latest = f;
        return new String(Files.readAllBytes(latest.toPath()));
    }
}
