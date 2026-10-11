package com.mcmiddleearth.architect.serverResoucePack;

import com.mcmiddleearth.architect.ArchitectPlugin;
import com.mcmiddleearth.architect.testsupport.TestConfig;
import com.sun.net.httpserver.HttpServer;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.io.File;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

// /rp calcsha, which hashes the packs of a pack's slots again, as an admin uses it. Its downloads never reach the
// internet: the packs are on a server of the test's own. One mock/load per class, as in LogFileTest.
class RpCalcShaCommandTest {

    // the sha in every slot at first: of none of the packs
    private static final String STALE = "da39a3ee5e6b4b0d3255bfef95601890afd80709";

    // the packs on the test's server, by path, and the paths asked for, in order
    private static final Map<String, byte[]> files = new ConcurrentHashMap<>();
    private static final List<String> requested = new CopyOnWriteArrayList<>();

    // A request for this path waits until the test lets it go: arrived counts down when it is there.
    private static volatile String heldPath;
    private static final CountDownLatch arrived = new CountDownLatch(1);
    private static final CountDownLatch release = new CountDownLatch(1);

    private static HttpServer site;
    private static String base;
    private static ServerMock server;
    private static ArchitectPlugin plugin;
    private static int admins;

    @BeforeAll
    static void setUp() throws Exception {
        site = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        site.createContext("/", exchange -> {
            String path = exchange.getRequestURI().getPath();
            requested.add(path);
            if (path.equals(heldPath)) {
                arrived.countDown();
                try {
                    release.await(10, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            byte[] body = files.get(path);
            if (body == null) {
                exchange.sendResponseHeaders(404, -1);
            } else {
                exchange.sendResponseHeaders(200, body.length);
                exchange.getResponseBody().write(body);
            }
            exchange.close();
        });
        site.start();
        base = "http://127.0.0.1:" + site.getAddress().getPort();

        server = MockBukkit.mock();
        YamlConfiguration config = new YamlConfiguration();
        // In config.yml light comes before footprints, and 26_2 before the older versions: not the order in which
        // they are hashed.
        for (String variant : List.of("light", "footprints")) {
            for (String version : List.of("26_2", "1_21_4", "26_1")) {
                addSlot(config, "Mordor", variant, version);
            }
            for (String version : List.of("26_2", "1_21_4")) {
                addSlot(config, "Paths", variant, version);
            }
        }
        addSlot(config, "Rohan", "light", "26_2");
        File folder = TestConfig.write(server, config.saveToString());
        plugin = MockBukkit.load(ArchitectPlugin.class);
        assertEquals(folder, plugin.getDataFolder(), "Architect reads the test's config.yml");
        assertEquals(STALE, sha("Mordor", "light", "26_1"), "the slots Architect reads");
    }

    @AfterAll
    static void tearDown() {
        release.countDown();
        if (MockBukkit.isMocked()) {
            MockBukkit.unmock();
        }
        site.stop(0);
    }

    // A deleted release made /rp calcsha <pack> all stop at its slot, in an order that changed from run to run, and
    // the slots after it kept their stale sha. Every slot is hashed now, in a fixed order, past the slots whose pack
    // cannot be downloaded; the admin is told each of those, with its url, and so is the log.
    @Test
    void allHashesEverySlotInAFixedOrderPastTheOnesThatFail() throws Exception {
        Admin admin = admin();
        List<String> warnings = warnings();
        files.remove("/mordor/light/26_1.zip");
        files.remove("/mordor/footprints/1_21_4.zip");
        Map<String, String> expected = new TreeMap<>();
        for (String variant : List.of("light", "footprints")) {
            for (String version : List.of("26_2", "1_21_4", "26_1")) {
                byte[] file = files.get("/mordor/" + variant + "/" + version + ".zip");
                expected.put(variant + " " + version, file == null ? STALE : sha1(file));
            }
        }

        admin.performCommand("rp calcsha Mordor all");

        List<String> told = told(admin, "SHA recalculated");
        List<String> failures = told.stream().filter(message -> message.contains("keeps its sha")).toList();
        String footprints = base + "/mordor/footprints/1_21_4.zip";
        String light = base + "/mordor/light/26_1.zip";
        assertAll(
                () -> assertEquals(List.of("/mordor/footprints/1_21_4.zip", "/mordor/footprints/26_1.zip",
                        "/mordor/footprints/26_2.zip", "/mordor/light/1_21_4.zip", "/mordor/light/26_1.zip",
                        "/mordor/light/26_2.zip"), requested("/mordor/"), "the packs downloaded, in order"),
                () -> assertEquals(expected, shas("Mordor", List.of("light", "footprints"), "26_2", "1_21_4", "26_1"),
                        "the sha of each slot"),
                () -> assertEquals(2, failures.size(), "the slots the admin is told about, of " + told),
                () -> assertTrue(failures.get(0).contains("vanilla.16px.footprints.1_21_4")
                        && failures.get(0).contains(footprints) && failures.get(0).contains("404"), failures.get(0)),
                () -> assertTrue(failures.get(1).contains("vanilla.16px.light.26_1")
                        && failures.get(1).contains(light) && failures.get(1).contains("404"), failures.get(1)),
                () -> assertTrue(warnings.stream().anyMatch(warning -> warning.contains(footprints))
                        && warnings.stream().anyMatch(warning -> warning.contains(light)),
                        "the slots that failed, in the log: " + warnings),
                () -> assertEquals(List.of(), admin.offMainThread, "messages sent off the main thread"));
    }

    // Without all, /rp calcsha hashes the newest slot of each variant, and goes on past one that fails as well.
    @Test
    void withoutAllTheNewestSlotOfEachVariantIsHashedPastOneThatFails() throws Exception {
        Admin admin = admin();
        files.remove("/paths/light/26_2.zip");
        String light = base + "/paths/light/26_2.zip";

        admin.performCommand("rp calcsha Paths");

        List<String> told = told(admin, "SHA recalculated");
        assertAll(
                () -> assertEquals(List.of("/paths/footprints/26_2.zip", "/paths/light/26_2.zip"), requested("/paths/"),
                        "the packs downloaded, in order"),
                () -> assertEquals(sha1(files.get("/paths/footprints/26_2.zip")), sha("Paths", "footprints", "26_2"),
                        "the sha of the newest footprints slot"),
                () -> assertEquals(STALE, sha("Paths", "light", "26_2"), "the sha of the newest light slot"),
                () -> assertEquals(STALE, sha("Paths", "footprints", "1_21_4"), "the sha of an older slot"),
                () -> assertTrue(told.stream().anyMatch(message -> message.contains("vanilla.16px.light.26_2")
                        && message.contains(light) && message.contains("404")),
                        "the admin is told the slot that failed, with its url: " + told));
    }

    // A slot whose url changes while its pack is hashed, as when /rp server sets it meanwhile, keeps the sha that came
    // with its new url: the hash of the old pack is not written over it.
    @Test
    void aSlotThatChangesWhileItsPackIsHashedKeepsItsNewSha() throws Exception {
        Admin admin = admin();
        String slot = "ServerResourcePacks.Rohan.vanilla.16px.light.26_2";
        String newSha = "33bece3b361f804e0966271ceaf85a691fe6a11d";
        heldPath = "/rohan/light/26_2.zip";
        try {
            admin.performCommand("rp calcsha Rohan all");
            assertTrue(await(arrived), "the pack is being downloaded");
            plugin.getConfig().set(slot + ".url", base + "/rohan/light/new.zip");
            plugin.getConfig().set(slot + ".sha", newSha);
        } finally {
            release.countDown();
        }

        List<String> answer = told(admin, "vanilla.16px.light.26_2");
        assertAll(
                () -> assertEquals(newSha, plugin.getConfig().getString(slot + ".sha"), "the sha of the slot"),
                () -> assertTrue(answer.stream().anyMatch(message -> message.contains("vanilla.16px.light.26_2")),
                        "the admin is told the slot was not hashed: " + answer));
    }

    // A pack name that matches no pack is refused, naming the packs there are, and nothing is written.
    @Test
    void anUnknownPackIsRefused() throws Exception {
        Admin admin = admin();
        String before = Files.readString(configFile());

        admin.performCommand("rp calcsha Nopack all");

        List<String> told = told(admin, "Nopack");
        String reply = told.isEmpty() ? "" : told.get(told.size() - 1);
        assertAll(
                () -> assertTrue(reply.contains("Mordor") && reply.contains("Paths") && reply.contains("Rohan"),
                        "the reply names the packs there are: " + told),
                () -> assertEquals(before, Files.readString(configFile()), "config.yml"));
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

    // A slot of a pack, at a pack on the test's server whose sha is not in it yet.
    private static void addSlot(YamlConfiguration config, String pack, String variant, String version) {
        String path = "/" + pack.toLowerCase(Locale.ROOT) + "/" + variant + "/" + version + ".zip";
        files.put(path, ("the pack at " + path).getBytes(StandardCharsets.UTF_8));
        String slot = "ServerResourcePacks." + pack + ".vanilla.16px." + variant + "." + version;
        config.set(slot + ".url", base + path);
        config.set(slot + ".sha", STALE);
    }

    private static String sha(String pack, String variant, String version) {
        return plugin.getConfig().getString("ServerResourcePacks." + pack + ".vanilla.16px." + variant + "." + version
                + ".sha");
    }

    // the sha of each slot, by "variant version"
    private static Map<String, String> shas(String pack, List<String> variants, String... versions) {
        Map<String, String> shas = new TreeMap<>();
        for (String variant : variants) {
            for (String version : versions) {
                shas.put(variant + " " + version, sha(pack, variant, version));
            }
        }
        return shas;
    }

    // the paths asked for that start so, in order
    private static List<String> requested(String start) {
        return requested.stream().filter(path -> path.startsWith(start)).toList();
    }

    private static String sha1(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-1").digest(bytes));
    }

    private static Path configFile() {
        return new File(plugin.getDataFolder(), "config.yml").toPath();
    }

    // the warnings Architect logs from now on
    private static List<String> warnings() {
        List<String> warnings = new CopyOnWriteArrayList<>();
        plugin.getLogger().addHandler(new Handler() {
            @Override
            public void publish(LogRecord record) {
                if (record.getLevel() == Level.WARNING) {
                    warnings.add(record.getMessage());
                }
            }

            @Override
            public void flush() {
            }

            @Override
            public void close() {
            }
        });
        return warnings;
    }

    // Waits up to 10 seconds for the latch, while the server ticks.
    private static boolean await(CountDownLatch latch) throws InterruptedException {
        long end = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (System.nanoTime() < end) {
            server.getScheduler().performOneTick();
            if (latch.await(10, TimeUnit.MILLISECONDS)) {
                return true;
            }
        }
        return false;
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
            super(RpCalcShaCommandTest.server, "Admin" + (++admins), UUID.randomUUID());
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
