package com.mcmiddleearth.architect;

import com.mcmiddleearth.architect.serverResoucePack.RpManager;
import com.mcmiddleearth.architect.testsupport.TestConfig;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

// Production servers share one config.yml. Each change a server makes to it must keep what the other servers wrote
// since this one read it. One mock/load per class, as in LogFileTest.
class ConfigFileTest {

    // a slot that is removed by another server
    private static final String REMOVED = "ServerResourcePacks.Paths.vanilla.16px.light.26_2release";
    private static final String STALE = "da39a3ee5e6b4b0d3255bfef95601890afd80709";

    @TempDir
    static Path files;

    private static ServerMock server;
    private static ArchitectPlugin plugin;
    private static File configFile;

    @BeforeAll
    static void setUp() throws Exception {
        server = MockBukkit.mock();
        Path rohan = Files.writeString(files.resolve("Rohan.zip"), "the pack Rohan");
        YamlConfiguration config = new YamlConfiguration();
        config.set("ServerResourcePacks.Paths.vanilla.16px.light.26_2.url", "https://example.invalid/Paths.zip");
        config.set("ServerResourcePacks.Paths.vanilla.16px.light.26_2.sha", STALE);
        config.set(REMOVED + ".url", "https://example.invalid/Paths-26.2release.zip");
        config.set(REMOVED + ".sha", STALE);
        config.set("ServerResourcePacks.Rohan.vanilla.16px.light.26_2.url", rohan.toUri().toString());
        config.set("ServerResourcePacks.Rohan.vanilla.16px.light.26_2.sha", STALE);
        File folder = TestConfig.write(server, config.saveToString());
        plugin = MockBukkit.load(ArchitectPlugin.class);
        assertEquals(folder, plugin.getDataFolder(), "Architect reads the test's config.yml");
        configFile = new File(folder, "config.yml");
        assertTrue(plugin.getConfig().contains(REMOVED), "the slot is there at first");
    }

    @AfterAll
    static void tearDown() {
        if (MockBukkit.isMocked()) {
            MockBukkit.unmock();
        }
    }

    // Each server saved its whole config, so a slot that another server removed came back the next time this one
    // saved a change of its own. Now this server reads config.yml before it saves.
    @Test
    void aSlotRemovedByAnotherServerStaysRemovedWhenThisOneSavesAChange() throws Exception {
        YamlConfiguration otherServer = YamlConfiguration.loadConfiguration(configFile);
        otherServer.set(REMOVED, null);
        otherServer.save(configFile);

        // this server's change: the sha of another pack
        AtomicBoolean answered = new AtomicBoolean();
        RpManager.refreshSHA(server.getConsoleSender(), "Rohan", true, result -> answered.set(true));
        long end = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (!answered.get() && System.nanoTime() < end) {
            server.getScheduler().performOneTick();
            Thread.sleep(10);
        }

        YamlConfiguration saved = YamlConfiguration.loadConfiguration(configFile);
        String sha = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-1")
                .digest(Files.readAllBytes(files.resolve("Rohan.zip"))));
        assertAll(
                () -> assertTrue(answered.get(), "the change is done"),
                () -> assertEquals(sha, saved.getString("ServerResourcePacks.Rohan.vanilla.16px.light.26_2.sha"),
                        "this server's change, in config.yml"),
                () -> assertFalse(saved.contains(REMOVED), "the removed slot, in config.yml"),
                () -> assertFalse(plugin.getConfig().contains(REMOVED), "the removed slot, in this server's config"));
    }

    // A config.yml that does not read as YAML, as one edited by hand, or one being written by another server, is left
    // as it is: saving this server's copy over it would lose what the others wrote.
    @Test
    void aConfigFileThatCannotBeReadIsLeftAsItIs() throws Exception {
        assertLeftAsItIs("ServerResourcePacks: [no end");
    }

    // So is an empty config.yml, as one that another server has just begun to write.
    @Test
    void anEmptyConfigFileIsLeftAsItIs() throws Exception {
        assertLeftAsItIs("");
    }

    // An update asked for off the main thread runs on it, on a later tick.
    @Test
    void anUpdateOffTheMainThreadRunsOnIt() throws Exception {
        AtomicBoolean onMainThread = new AtomicBoolean();
        Thread worker = new Thread(() -> ConfigFile.update(config -> {
            onMainThread.set(Bukkit.isPrimaryThread());
            config.set("updatedOffTheMainThread", true);
        }));
        worker.start();
        worker.join();
        server.getScheduler().performOneTick();

        assertAll(
                () -> assertTrue(onMainThread.get(), "the change ran on the main thread"),
                () -> assertTrue(YamlConfiguration.loadConfiguration(configFile).getBoolean("updatedOffTheMainThread"),
                        "the change, in config.yml"));
    }

    // Every write of config.yml goes through ConfigFile.update: a saveConfig() anywhere else writes this server's whole
    // copy back, over what the other servers changed.
    @Test
    void nothingButConfigFileSavesTheConfig() throws IOException {
        Path sources = Path.of("src", "main", "java");
        List<String> callers;
        try (Stream<Path> paths = Files.walk(sources)) {
            callers = paths.filter(path -> path.toString().endsWith(".java"))
                    .filter(path -> !path.getFileName().toString().equals("ConfigFile.java"))
                    .filter(path -> read(path).contains("saveConfig()"))
                    .map(path -> path.getFileName().toString())
                    .sorted()
                    .toList();
        }
        assertEquals(List.of(), callers, "the classes that call saveConfig()");
    }

    private static void assertLeftAsItIs(String content) throws Exception {
        String before = Files.readString(configFile.toPath());
        List<LogRecord> errors = new CopyOnWriteArrayList<>();
        Handler handler = new Handler() {
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
        };
        plugin.getLogger().addHandler(handler);
        try {
            Files.writeString(configFile.toPath(), content);

            boolean saved = ConfigFile.update(config -> config.set("unrelated", 1));

            String after = Files.readString(configFile.toPath());
            assertAll(
                    () -> assertFalse(saved, "saved"),
                    () -> assertEquals(content, after, "config.yml"),
                    () -> assertTrue(plugin.getConfig().contains("ServerResourcePacks.Rohan"),
                            "what this server's config holds"),
                    () -> assertTrue(errors.stream().anyMatch(record -> record.getMessage().contains("config.yml")),
                            "an error about config.yml in the log"));
        } finally {
            plugin.getLogger().removeHandler(handler);
            Files.writeString(configFile.toPath(), before);
        }
    }

    private static String read(Path path) {
        try {
            return Files.readString(path);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
