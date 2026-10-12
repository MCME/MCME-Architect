package com.mcmiddleearth.architect;

import com.mcmiddleearth.architect.serverResoucePack.RpReleaseUtil;
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
import java.util.function.BooleanSupplier;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

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
                // a complete entry, whose folder isn't there: /rp release goes on, and its script can't start
                "        script: releaseVanillaSodium.sh",
                "        path: /nowhere",
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

    // /rp release runs its script off the main thread: its owner, repo, script, path and timeout are read before. This
    // is a Linux server (a Windows one is told releases don't run there before any setting is read), with a complete
    // entry, so the release goes on, and its script can't start, as its folder isn't there. The reads off the main
    // thread are checked once the release's own thread has ended: one made there would show only then.
    @Test
    void rpReleaseReadsItsSettingsOnTheMainThread() throws Exception {
        Field onWindows = RpReleaseUtil.class.getDeclaredField("onWindows");
        onWindows.setAccessible(true);
        boolean wasWindows = onWindows.getBoolean(null);
        onWindows.setBoolean(null, false);
        Logger architectLog = ArchitectPlugin.getPluginInstance().getLogger();
        LogNotes logged = new LogNotes();
        architectLog.addHandler(logged);
        try {
            server.dispatchCommand(server.getConsoleSender(), "rp release Paths v2 A new release");

            // The release's own thread logs the result as its last act that can read a setting
            assertTrue(awaitLogged(logged, "[RP release Paths] Result:"), "the release ends, of " + logged.lines);
            assertTrue(awaitNoAsyncTask(), "the release's own thread has ended");
            assertAll(
                    () -> assertTrue(awaitRead("gitHubRpReleases.Paths.owner"), "the owner is read, of " + config.read),
                    () -> assertEquals(List.of(), config.readOffTheMainThread("gitHubRpReleases"),
                            "the settings read off the main thread"));
        } finally {
            architectLog.removeHandler(logged);
            onWindows.setBoolean(null, wasWindows);
        }
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
        return await(() -> config.read.contains(path));
    }

    // Waits up to 10 seconds, while the server ticks, for Architect to log a line with this text.
    private static boolean awaitLogged(LogNotes logged, String text) throws InterruptedException {
        return await(() -> logged.lines.stream().anyMatch(line -> line.contains(text)));
    }

    // Waits up to 10 seconds, while the server ticks, for the tasks off the main thread to end, and ticks once more
    // for what they scheduled on it: so none of it outlives the test.
    private static boolean awaitNoAsyncTask() throws InterruptedException {
        boolean ended = await(() -> server.getScheduler().getActiveWorkers().isEmpty());
        server.getScheduler().performOneTick();
        return ended;
    }

    private static boolean await(BooleanSupplier done) throws InterruptedException {
        long end = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (!done.getAsBoolean() && System.nanoTime() < end) {
            server.getScheduler().performOneTick();
            Thread.sleep(10);
        }
        return done.getAsBoolean();
    }

    // What Architect logs, noted while this is a handler of its logger.
    private static final class LogNotes extends Handler {
        private final List<String> lines = new CopyOnWriteArrayList<>();

        @Override
        public void publish(LogRecord record) {
            lines.add(record.getMessage());
        }

        @Override
        public void flush() {
        }

        @Override
        public void close() {
        }
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
