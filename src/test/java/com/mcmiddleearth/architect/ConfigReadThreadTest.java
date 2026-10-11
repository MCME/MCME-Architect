package com.mcmiddleearth.architect;

import com.mcmiddleearth.architect.testsupport.TestConfig;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

import java.io.File;
import java.lang.reflect.Field;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

// Architect's config is read again in place, on the main thread, whenever config.yml is written (ConfigFile.update),
// and a read from another thread meanwhile may find it empty. So work that runs off the main thread is given the
// settings it needs, read on the main thread before it starts. One mock/load per class, as in LogFileTest.
class ConfigReadThreadTest {

    private static ServerMock server;
    private static WatchedConfig config;

    @BeforeAll
    static void setUp() throws Exception {
        server = MockBukkit.mock();
        File folder = TestConfig.write(server,
                "ServerResourcePacks:",
                "    Paths:",
                "        vanilla:",
                "            16px:",
                "                light:",
                "                    '26_2':",
                "                        url: https://example.invalid/Paths.zip",
                "gitHubRpReleases:",
                "    Paths:",
                "        owner: mcme",
                "        repo: paths",
                "");
        ArchitectPlugin plugin = MockBukkit.load(ArchitectPlugin.class);
        assertEquals(folder, plugin.getDataFolder(), "Architect reads the test's config.yml");
        // From now on Architect's config, read from the same file, notes where each read is made.
        config = new WatchedConfig();
        config.load(new File(folder, "config.yml"));
        Field newConfig = JavaPlugin.class.getDeclaredField("newConfig");
        newConfig.setAccessible(true);
        newConfig.set(plugin, config);
    }

    @AfterAll
    static void tearDown() {
        if (MockBukkit.isMocked()) {
            MockBukkit.unmock();
        }
    }

    // /rp release runs its script off the main thread: its owner, repo, script, path and timeout are read before.
    @Test
    void rpReleaseReadsItsSettingsOnTheMainThread() {
        server.dispatchCommand(server.getConsoleSender(), "rp release Paths v2 A new release");

        assertAll(
                () -> assertTrue(awaitRead("gitHubRpReleases.Paths.owner"), "the owner is read, of " + config.read),
                () -> assertEquals(List.of(), config.readOffTheMainThread("gitHubRpReleases"),
                        "the settings read off the main thread"));
    }

    // /inv upload and /inv download run their scripts off the main thread: which script, and where, is read before.
    @Test
    void invUploadAndDownloadReadTheirScriptsOnTheMainThread() {
        server.dispatchCommand(server.getConsoleSender(), "inv upload Paths a new block");
        server.dispatchCommand(server.getConsoleSender(), "inv download Paths");

        assertAll(
                () -> assertTrue(awaitRead("inventoryUpload.script") && awaitRead("inventoryDownload.script"),
                        "the scripts are read, of " + config.read),
                () -> assertEquals(List.of(), config.readOffTheMainThread("inventory"),
                        "the settings read off the main thread"));
    }

    // Waits up to 10 seconds, while the server ticks, for the config to be read at this path.
    private static boolean awaitRead(String path) throws InterruptedException {
        long end = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (!config.read.contains(path) && System.nanoTime() < end) {
            server.getScheduler().performOneTick();
            Thread.sleep(10);
        }
        return config.read.contains(path);
    }

    // A config that notes each path read from it, and which of those reads were made off the main thread.
    private static final class WatchedConfig extends YamlConfiguration {
        private final List<String> read = new CopyOnWriteArrayList<>();
        private final List<String> offMainThread = new CopyOnWriteArrayList<>();

        @Override
        public Object get(String path, Object def) {
            read.add(path);
            if (!Bukkit.isPrimaryThread()) {
                offMainThread.add(path);
            }
            return super.get(path, def);
        }

        List<String> readOffTheMainThread(String start) {
            return offMainThread.stream().filter(path -> path.startsWith(start)).toList();
        }
    }
}
