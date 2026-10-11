package com.mcmiddleearth.architect.serverResoucePack;

import com.mcmiddleearth.architect.ArchitectPlugin;
import com.mcmiddleearth.architect.testsupport.TestConfig;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

// /rp server, which points a pack's version slot at the files of a release, as an admin uses it. Its downloads never
// reach the internet: the packs it hashes are local files. One mock/load per class, as in LogFileTest.
class RpServerCommandTest {

    private static final String SHA = "33bece3b361f804e0966271ceaf85a691fe6a11d";

    @TempDir
    static Path files;

    private static ServerMock server;
    private static ArchitectPlugin plugin;
    private static int admins;

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
                "    Paths:",
                "        vanilla:",
                "            16px:",
                "                light:",
                "                    '26_2':",
                "                        url: '" + pack("Paths-old.zip") + "'",
                "                        sha: " + sha1(pack("Paths-old.zip")),
                "                footprints:",
                "                    '26_2':",
                "                        url: '" + pack("Paths-Footprints-old.zip") + "'",
                "                        sha: " + sha1(pack("Paths-Footprints-old.zip")),
                "gitHubRpReleases:",
                "    Crash:",
                "        owner: mcme",
                "        repo: crash",
                "    Paths:",
                "        owner: mcme",
                "        repo: paths",
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

    // A release name in place of the Minecraft version, as in "/rp server p v1.8.9 26.2release", made a slot that no
    // client's version matches. Such a version is refused: nothing is written, and the admin is told the versions
    // there are.
    @Test
    void anUnknownMinecraftVersionIsRefusedAndWritesNothing() throws Exception {
        Admin admin = admin();
        String before = Files.readString(configFile());

        admin.performCommand("rp server Paths v1.9 26.2release");

        String reply = awaitReply(admin);
        Set<String> versions = versions();
        assertAll(
                () -> assertTrue(reply.contains("26.2release"), "the reply names the version: " + reply),
                () -> assertTrue(versions.stream().allMatch(reply::contains),
                        "the reply names the versions there are: " + reply),
                () -> assertFalse(plugin.getConfig().contains("ServerResourcePacks.Paths.vanilla.16px.light.26_2release"),
                        "a slot for that version, in Architect's config"),
                () -> assertEquals(before, Files.readString(configFile()), "config.yml"));
    }

    // The Minecraft version of /rp server completes to the versions there are, as an admin types them: 26.2.
    @Test
    void theMinecraftVersionCompletesToTheVersionsThereAre() throws Exception {
        Admin admin = admin();
        PlayerMock builder = server.addPlayer();
        builder.setOp(false);
        Command rp = server.getCommandMap().getCommand("rp");
        Set<String> versions = versions();

        List<String> all = rp.tabComplete(admin, "rp", new String[] {"server", "Paths", "v1.9", ""});
        assertAll(
                () -> assertEquals(List.of("26.2", "26.1"),
                        rp.tabComplete(admin, "rp", new String[] {"server", "Paths", "v1.9", "26"}), "newest first"),
                () -> assertEquals(versions, new HashSet<>(all), "the versions offered"),
                () -> assertEquals(versions.size(), all.size(), "the number of versions offered: " + all),
                () -> assertEquals(List.of(), rp.tabComplete(builder, "rp", new String[] {"server", "Paths", "v1.9", ""}),
                        "offered to a player who may not use /rp server"));
    }

    // After a release, /rp release suggests the whole /rp server command, Minecraft version included.
    @Test
    void theReleaseHintNamesTheMinecraftVersionToo() {
        assertEquals("RP release finished. You might want to do /rp server Paths v1.9 <mcVersion>",
                RpCommand.releaseFinishedHint("Paths", "v1.9"));
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

    // A local pack file of this name, made on first use: its file: URL.
    private static String pack(String name) throws IOException {
        Path file = files.resolve(name);
        if (!Files.exists(file)) {
            Files.writeString(file, "the pack " + name);
        }
        return file.toUri().toString();
    }

    private static String sha1(String url) throws Exception {
        byte[] bytes = Files.readAllBytes(Path.of(java.net.URI.create(url)));
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-1").digest(bytes));
    }

    private static Path configFile() {
        return new File(plugin.getDataFolder(), "config.yml").toPath();
    }

    // The Minecraft versions in Architect's protocolVersions.yml, as an admin types them: 26.2, 1.21.4 and so on.
    private static Set<String> versions() throws IOException {
        try (InputStream in = ArchitectPlugin.class.getClassLoader().getResourceAsStream("protocolVersions.yml")) {
            assertTrue(in != null, "protocolVersions.yml is on the classpath");
            YamlConfiguration yaml = YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8));
            return yaml.getKeys(false).stream().map(key -> key.replace('_', '.')).collect(Collectors.toSet());
        }
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

    // the first thing the admin is told, at once or later
    private static String awaitReply(PlayerMock player) {
        return awaitMessage(player, "");
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
            super(RpServerCommandTest.server, "Admin" + (++admins), UUID.randomUUID());
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
