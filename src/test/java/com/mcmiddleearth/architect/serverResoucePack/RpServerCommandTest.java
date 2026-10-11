package com.mcmiddleearth.architect.serverResoucePack;

import com.mcmiddleearth.architect.ArchitectPlugin;
import com.mcmiddleearth.architect.testsupport.TestConfig;
import com.sun.net.httpserver.HttpServer;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.configuration.ConfigurationSection;
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
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
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
// reach the internet: the releases are on a server of the test's own, and the packs already in the slots are local
// files. One mock/load per class, as in LogFileTest.
class RpServerCommandTest {

    // the variants of a pack for vanilla clients only, and the file of a release each one gets
    private static final Map<String, String> PATHS = Map.of(
            "vanilla.16px.light", "Paths.zip",
            "vanilla.16px.footprints", "Paths-Footprints.zip");

    // the variants of a pack for Sodium and Lite clients too, and the file of a release each one gets
    private static final Map<String, String> MORDOR = Map.of(
            "vanilla.16px.light", "Mordor-Vanilla.zip",
            "vanilla.16px.footprints", "Mordor-Vanilla-Footprints.zip",
            "sodium.16px.light", "Mordor-Sodium.zip",
            "sodium.16px.footprints", "Mordor-Sodium-Footprints.zip",
            "lite.16px.light", "Mordor-Lite.zip",
            "lite.16px.footprints", "Mordor-Lite-Footprints.zip");

    @TempDir
    static Path files;

    // the files of the releases, by their path on the release site
    private static final Map<String, byte[]> released = new ConcurrentHashMap<>();
    private static HttpServer releaseSite;

    private static ServerMock server;
    private static ArchitectPlugin plugin;
    private static int admins;

    @BeforeAll
    static void setUp() throws Exception {
        releaseSite = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        releaseSite.createContext("/", exchange -> {
            byte[] body = released.get(exchange.getRequestURI().getPath());
            if (body == null) {
                exchange.sendResponseHeaders(404, -1);
            } else {
                exchange.sendResponseHeaders(200, body.length);
                exchange.getResponseBody().write(body);
            }
            exchange.close();
        });
        releaseSite.start();
        RpReleaseUtil.releaseSite = "http://127.0.0.1:" + releaseSite.getAddress().getPort() + "/";

        server = MockBukkit.mock();
        YamlConfiguration config = new YamlConfiguration();
        addSlots(config, "Crash", PATHS, "26_2"); // /rp server fails for it
        addSlots(config, "Mordor", MORDOR, "26_2", "1_21_4");
        addSlots(config, "Paths", PATHS, "26_2");
        // packs with a slot made under a release name, as by "/rp server p v1.8.9 26.2release"
        addSlots(config, "Dead", PATHS, "26_2", "1_21_4", "26_2release");
        addSlots(config, "Erech", PATHS, "26_2", "26_2release");
        // packs where 26_2release is the last slot of a variant, of a client, and of the whole pack
        addSlots(config, "Lonely", Map.of("vanilla.16px.light", ""), "26_2", "26_2release");
        addSlots(config, "Lonely", Map.of("vanilla.16px.footprints", "", "sodium.16px.light", ""), "26_2release");
        addSlots(config, "Gone", Map.of("vanilla.16px.light", ""), "26_2release");
        for (String pack : List.of("Crash", "Mordor", "Paths")) {
            config.set("gitHubRpReleases." + pack + ".owner", "mcme");
            config.set("gitHubRpReleases." + pack + ".repo", pack.toLowerCase(Locale.ROOT));
        }
        File folder = TestConfig.write(server, config.saveToString());
        plugin = MockBukkit.load(ArchitectPlugin.class);
        assertEquals(folder, plugin.getDataFolder(), "Architect reads the test's config.yml");
        assertEquals(slots(config, "Mordor", MORDOR.keySet(), "1_21_4"),
                slots(plugin.getConfig(), "Mordor", MORDOR.keySet(), "1_21_4"), "the slots Architect reads");
    }

    @AfterAll
    static void tearDown() {
        if (MockBukkit.isMocked()) {
            MockBukkit.unmock();
        }
        releaseSite.stop(0);
        RpReleaseUtil.releaseSite = "https://github.com/";
    }

    // An error in the background work of /rp server is logged, and the admin is told, on the main thread. A release
    // tag with a ^, which no URL may hold, makes one; nothing is written then.
    @Test
    void anErrorInTheBackgroundIsLoggedAndReported() throws Exception {
        Admin admin = admin();
        List<LogRecord> errors = errors();
        String before = Files.readString(configFile());

        admin.performCommand("rp server Crash v^2 26.2");

        String reply = awaitMessage(admin, "Error while setting server resource pack");
        assertAll(
                () -> assertTrue(reply.contains("Error while setting server resource pack"), reply),
                () -> assertEquals(List.of(), admin.offMainThread, "messages sent off the main thread"),
                () -> assertTrue(errors.stream().anyMatch(record -> record.getThrown() != null),
                        "the error in Architect's log, with its stack trace"),
                () -> assertEquals(before, Files.readString(configFile()), "config.yml"));
    }

    // Setting an older slot, 1_21_4 while there is a 26_2 one, paired its new url with its old sha: only the newest
    // slot was hashed. Each slot /rp server sets now gets the sha of its own file, and the other slots stay as they
    // were.
    @Test
    void settingAnOlderSlotGivesItTheShaOfItsOwnFile() throws Exception {
        Admin admin = admin();
        Map<String, String> expected = new TreeMap<>();
        for (Map.Entry<String, String> variant : MORDOR.entrySet()) {
            byte[] file = release("mordor", "v2", variant.getValue());
            expected.put(variant.getKey() + " url", releaseUrl("mordor", "v2", variant.getValue()));
            expected.put(variant.getKey() + " sha", sha1(file));
        }
        Map<String, String> newest = slots(plugin.getConfig(), "Mordor", MORDOR.keySet(), "26_2");

        admin.performCommand("rp server Mordor v2 1.21.4");

        String reply = awaitMessage(admin, "Mordor");
        YamlConfiguration saved = YamlConfiguration.loadConfiguration(configFile().toFile());
        assertAll(
                () -> assertTrue(reply.contains("set to version: v2"), reply),
                () -> assertEquals(expected, slots(plugin.getConfig(), "Mordor", MORDOR.keySet(), "1_21_4"),
                        "the 1_21_4 slots"),
                () -> assertEquals(expected, slots(saved, "Mordor", MORDOR.keySet(), "1_21_4"),
                        "the 1_21_4 slots in config.yml"),
                () -> assertEquals(newest, slots(plugin.getConfig(), "Mordor", MORDOR.keySet(), "26_2"),
                        "the 26_2 slots"));
    }

    // A file of the release that cannot be downloaded leaves its slot as it was, url and sha, and the admin is told
    // which slot, and why; the slot whose file could be downloaded is set.
    @Test
    void aSlotWhoseFileCannotBeDownloadedKeepsItsUrlAndSha() throws Exception {
        Admin admin = admin();
        byte[] light = release("paths", "v3", "Paths.zip"); // and no Paths-Footprints.zip
        String missing = releaseUrl("paths", "v3", "Paths-Footprints.zip");
        String slot = "ServerResourcePacks.Paths.vanilla.16px.";
        String footprintsUrl = plugin.getConfig().getString(slot + "footprints.26_2.url");
        String footprintsSha = plugin.getConfig().getString(slot + "footprints.26_2.sha");

        admin.performCommand("rp server Paths v3 26.2");

        List<String> told = told(admin, "404");
        assertAll(
                () -> assertTrue(told.stream().anyMatch(message -> message.contains("vanilla.16px.footprints")
                        && message.contains(missing) && message.contains("404")),
                        "the admin is told which slot kept its pack, and why: " + told),
                () -> assertEquals(releaseUrl("paths", "v3", "Paths.zip"),
                        plugin.getConfig().getString(slot + "light.26_2.url"), "the url of the light slot"),
                () -> assertEquals(sha1(light), plugin.getConfig().getString(slot + "light.26_2.sha"),
                        "the sha of the light slot"),
                () -> assertEquals(footprintsUrl, plugin.getConfig().getString(slot + "footprints.26_2.url"),
                        "the url of the footprints slot"),
                () -> assertEquals(footprintsSha, plugin.getConfig().getString(slot + "footprints.26_2.sha"),
                        "the sha of the footprints slot"));
    }

    // A pack name that matches no pack is refused, and nothing is written.
    @Test
    void anUnknownPackIsRefusedAndWritesNothing() throws Exception {
        Admin admin = admin();
        String before = Files.readString(configFile());

        admin.performCommand("rp server Nopack v2 26.2");

        String reply = awaitReply(admin);
        assertAll(
                () -> assertTrue(reply.contains("Nopack") && reply.contains("Crash") && reply.contains("Mordor")
                        && reply.contains("Paths"), "the reply names the packs there are: " + reply),
                () -> assertEquals(before, Files.readString(configFile()), "config.yml"));
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

    // /rp server remove <pack> <mc> takes the slot for a version out of every variant of the pack: the way to remove
    // a slot made under a release name, which no client's version matches.
    @Test
    void removeTakesTheSlotOutOfEveryVariant() throws Exception {
        Admin admin = admin();
        Map<String, String> kept = slots(plugin.getConfig(), "Dead", PATHS.keySet(), "26_2");
        kept.putAll(slots(plugin.getConfig(), "Dead", PATHS.keySet(), "1_21_4"));
        Map<String, String> none = new TreeMap<>();
        PATHS.keySet().forEach(variant -> {
            none.put(variant + " url", null);
            none.put(variant + " sha", null);
        });

        admin.performCommand("rp server remove Dead 26.2release");

        String reply = awaitReply(admin);
        YamlConfiguration saved = YamlConfiguration.loadConfiguration(configFile().toFile());
        Map<String, String> savedKept = slots(saved, "Dead", PATHS.keySet(), "26_2");
        savedKept.putAll(slots(saved, "Dead", PATHS.keySet(), "1_21_4"));
        assertAll(
                () -> assertTrue(reply.contains("26.2release") && reply.contains("vanilla.16px.light")
                        && reply.contains("vanilla.16px.footprints"), "the reply names the slots removed: " + reply),
                () -> assertEquals(none, slots(plugin.getConfig(), "Dead", PATHS.keySet(), "26_2release"),
                        "the 26_2release slots"),
                () -> assertEquals(none, slots(saved, "Dead", PATHS.keySet(), "26_2release"),
                        "the 26_2release slots in config.yml"),
                () -> assertEquals(kept, savedKept, "the other slots in config.yml"));
    }

    // A slot that is not there is refused, naming the slots the pack has; so is a pack that is not there, naming the
    // packs.
    @Test
    void removeRefusesASlotOrPackThatIsNotThere() throws Exception {
        Admin admin = admin();
        String before = Files.readString(configFile());

        admin.performCommand("rp server remove Erech 1.20");
        String noSlot = awaitReply(admin);
        admin.performCommand("rp server remove Nopack 26.2");
        String noPack = awaitReply(admin);

        assertAll(
                () -> assertTrue(noSlot.contains("1.20") && noSlot.contains("26.2") && noSlot.contains("26.2release"),
                        "the reply names the slots Erech has: " + noSlot),
                () -> assertTrue(noPack.contains("Nopack") && noPack.contains("Erech") && noPack.contains("Mordor"),
                        "the reply names the packs there are: " + noPack),
                () -> assertEquals(before, Files.readString(configFile()), "config.yml"));
    }

    // Only a resource pack admin may remove a slot, as only one may set it.
    @Test
    void removeIsForResourcePackAdminsOnly() throws Exception {
        PlayerMock builder = server.addPlayer();
        builder.setOp(false);
        String before = Files.readString(configFile());

        builder.performCommand("rp server remove Erech 26_2release");

        assertAll(
                () -> assertTrue(plugin.getConfig().contains("ServerResourcePacks.Erech.vanilla.16px.light.26_2release"),
                        "the slot, in Architect's config"),
                () -> assertEquals(before, Files.readString(configFile()), "config.yml"));
    }

    // Removing a variant's last slot left the variant behind empty ({}), and a client of that variant was sent nothing
    // from it. The sections a removal empties are pruned now: the variant, and then its resolution and its client.
    @Test
    void removePrunesTheSectionsItEmpties() {
        Admin admin = admin();

        admin.performCommand("rp server remove Lonely 26_2release");

        String reply = awaitReply(admin);
        YamlConfiguration saved = YamlConfiguration.loadConfiguration(configFile().toFile());
        String pack = "ServerResourcePacks.Lonely.";
        assertAll(
                () -> assertTrue(reply.contains("vanilla.16px.footprints.26_2release"), reply),
                () -> assertTrue(saved.isSet(pack + "vanilla.16px.light.26_2"), "the slot left, in config.yml"),
                () -> assertFalse(saved.isSet(pack + "vanilla.16px.footprints"), "the variant emptied, in config.yml"),
                () -> assertFalse(saved.isSet(pack + "sodium"), "the client emptied, in config.yml"),
                () -> assertFalse(plugin.getConfig().isSet(pack + "vanilla.16px.footprints"),
                        "the variant emptied, in Architect's config"),
                () -> assertFalse(plugin.getConfig().isSet(pack + "sodium"), "the client emptied, in Architect's config"));
    }

    // A pack whose last slot is removed is removed as a whole, and the admin is told: an empty pack would make the
    // search for a client's slot fail on its empty list of clients.
    @Test
    void removingThePacksLastSlotRemovesThePack() {
        Admin admin = admin();

        admin.performCommand("rp server remove Gone 26_2release");

        String reply = awaitReply(admin);
        YamlConfiguration saved = YamlConfiguration.loadConfiguration(configFile().toFile());
        assertAll(
                () -> assertTrue(reply.contains("no slots left"), "the reply says the pack is gone: " + reply),
                () -> assertFalse(saved.isSet("ServerResourcePacks.Gone"), "the pack, in config.yml"),
                () -> assertFalse(plugin.getConfig().isSet("ServerResourcePacks.Gone"), "the pack, in Architect's config"),
                () -> assertEquals("", RpManager.getRpUrl("Gone", null), "the url a client of the pack is sent"));
    }

    // remove completes, and then the packs, and then the slots of the pack, as an admin types them.
    @Test
    void removeCompletesThePacksAndTheirSlots() {
        Admin admin = admin();
        PlayerMock builder = server.addPlayer();
        builder.setOp(false);
        Command rp = server.getCommandMap().getCommand("rp");

        assertAll(
                () -> assertEquals(List.of("remove"), rp.tabComplete(admin, "rp", new String[] {"server", "rem"})),
                () -> assertEquals(List.of("Erech"), rp.tabComplete(admin, "rp", new String[] {"server", "remove", "E"})),
                () -> assertEquals(List.of("26.2", "26.2release"),
                        rp.tabComplete(admin, "rp", new String[] {"server", "remove", "Erech", ""})),
                () -> assertEquals(List.of("26.2release"),
                        rp.tabComplete(admin, "rp", new String[] {"server", "remove", "Erech", "26.2r"})),
                () -> assertEquals(List.of(), rp.tabComplete(builder, "rp", new String[] {"server", "remove", "Erech", ""}),
                        "offered to a player who may not use /rp server"));
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

    // Gives each variant of a pack a slot for each version, at a local file of its own.
    private static void addSlots(YamlConfiguration config, String pack, Map<String, String> variants, String... versions)
            throws Exception {
        for (String variant : variants.keySet()) {
            for (String version : versions) {
                String slot = "ServerResourcePacks." + pack + "." + variant + "." + version;
                String url = pack(pack + "-" + variant + "-" + version + "-old.zip");
                config.set(slot + ".url", url);
                config.set(slot + ".sha", sha1(Files.readAllBytes(Path.of(java.net.URI.create(url)))));
            }
        }
    }

    // The url and sha of a pack's slots for a version, by variant: "vanilla.16px.light url" and so on.
    private static Map<String, String> slots(ConfigurationSection config, String pack, Collection<String> variants,
                                             String version) {
        Map<String, String> slots = new TreeMap<>();
        for (String variant : variants) {
            String slot = "ServerResourcePacks." + pack + "." + variant + "." + version;
            slots.put(variant + " url", config.getString(slot + ".url"));
            slots.put(variant + " sha", config.getString(slot + ".sha"));
        }
        return slots;
    }

    // A local pack file of this name, made on first use: its file: URL.
    private static String pack(String name) throws IOException {
        Path file = files.resolve(name);
        if (!Files.exists(file)) {
            Files.writeString(file, "the pack " + name);
        }
        return file.toUri().toString();
    }

    // Puts a file of a release of the repository mcme/<repo> on the release site: its bytes.
    private static byte[] release(String repo, String tag, String file) {
        byte[] bytes = ("the file " + file + " of " + repo + " " + tag).getBytes(StandardCharsets.UTF_8);
        released.put("/mcme/" + repo + "/releases/download/" + tag + "/" + file, bytes);
        return bytes;
    }

    private static String releaseUrl(String repo, String tag, String file) {
        return RpReleaseUtil.releaseSite + "mcme/" + repo + "/releases/download/" + tag + "/" + file;
    }

    private static String sha1(byte[] bytes) throws Exception {
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

    private static String awaitMessage(PlayerMock player, String text) {
        List<String> told = told(player, text);
        return told.stream().filter(message -> message.contains(text)).findFirst()
                .orElseGet(() -> fail("the admin was not told '" + text + "', only " + told));
    }

    // Everything the admin is told until a message with this text, or for 10 seconds: the replies come from work on
    // another thread, and on a later tick.
    private static List<String> told(PlayerMock player, String text) {
        List<String> told = new ArrayList<>();
        long end = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (System.nanoTime() < end) {
            server.getScheduler().performOneTick();
            for (String message = player.nextMessage(); message != null; message = player.nextMessage()) {
                told.add(message);
                if (message.contains(text)) {
                    return told;
                }
            }
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        return told;
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
