package com.mcmiddleearth.architect.serverResoucePack;

import com.mcmiddleearth.architect.ArchitectPlugin;
import com.mcmiddleearth.architect.testsupport.TestConfig;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.io.File;
import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.logging.Handler;
import java.util.logging.LogRecord;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

// /rp release as an admin uses it, with a fake script in place of the real one: what is refused before anything
// starts, the start line, the steps, the closing message and its buttons, and a result held across leaving. One
// mock/load per class, as in RpServerCommandTest.
class RpReleaseCommandTest {

    private static final String[] SODIUM_RELEASE = {
            "compiling Human RP zips", "Human-Sodium", "Human-Sodium-Footprints", "Human-Vanilla",
            "        WARNING!!! Missing model file: mcme:block/oak_table_1", "Human-Vanilla-Footprints",
            "Human-Lite", "Human-Lite-Footprints", "releasing Human RP zips",
            "release note: Version v4.1.3 for Minecraft 26.2 and 26.3",
            "https://github.com/MCME/RP-Human/releases/tag/v4.1.3"};

    private static ServerMock server;
    private static ArchitectPlugin plugin;
    private static int admins;
    private final List<String[]> started = new CopyOnWriteArrayList<>();

    @BeforeAll
    static void setUp() throws Exception {
        server = MockBukkit.mock();
        File folder = TestConfig.write(server,
                "ServerResourcePacks:",
                "  Human:",
                "    vanilla:",
                "      16px:",
                "        light:",
                "          26_2:",
                "            url: https://example.invalid/Human-Vanilla.zip",
                "            sha: 0123456789abcdef0123456789abcdef01234567",
                "  Gondor:",
                "    vanilla:",
                "      16px:",
                "        light:",
                "          26_2:",
                "            url: https://example.invalid/Gondor.zip",
                "            sha: 0123456789abcdef0123456789abcdef01234567",
                // Architect copies its bundled config's defaults in, and those hold gitHubRpReleases entries for
                // Human, Dwarf and Rohan, with paths: the incomplete entry must be a pack they don't know
                "gitHubRpReleases:",
                "  Human:",
                "    owner: MCME",
                "    repo: RP-Human",
                "    script: releaseVanillaSodium.sh",
                "    path: /nowhere",
                "  Gondor:",
                "    owner: MCME",
                "    repo: RP-Gondor",
                "    script: releaseDynmap.sh",
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

    @BeforeEach
    void freshRelease() {
        RpReleaseUtil.onWindows = false;
        RpReleaseUtil.starter = (directory, command) -> {
            started.add(command);
            return new FakeScript(Duration.ZERO, 0, SODIUM_RELEASE);
        };
        new File(plugin.getDataFolder(), RpReleaseTimes.FILE).delete();
    }

    @AfterEach
    void realRelease() {
        RpReleaseUtil.onWindows = System.getProperty("os.name").toLowerCase(Locale.ROOT).startsWith("windows");
        RpReleaseUtil.starter = RpReleaseUtil::startScript;
    }

    @Test
    void aReleaseSaysWhenItStartsEachStepAndHowItEnded() {
        PlayerMock admin = admin();
        List<String> logged = logged();

        admin.performCommand("rp release h v4.1.3 Better leaves");

        List<Component> told = told(admin, "Put it live");
        assertEquals(List.of(
                "[Architect] Releasing Human v4.1.3 (\"Better leaves\"). The result comes here.",
                "[Architect] Human: Sodium zips done",
                "[Architect] Human: Vanilla zips done",
                "[Architect] Human: Lite zips done",
                "[Architect] Human: publishing on GitHub…",
                "[Architect] Human v4.1.3 is released, with 1 warning:",
                "[Architect]   Missing model file: mcme:block/oak_table_1",
                "[Architect] Put it live: [26.2] [26.3] [Open on GitHub] "), texts(told));
        assertArrayEquals(new String[] {"sh", "releaseVanillaSodium.sh", "Human", "MCME", "RP-Human", "v4.1.3",
                "Better leaves"}, started.get(0), "the script and its arguments, as before");
        Component putItLive = told.get(told.size() - 1);
        assertEquals(ClickEvent.suggestCommand("/rp server Human v4.1.3 26.2"), putItLive.children().get(0).clickEvent());
        assertTrue(logged.contains("[RP release Human] compiling Human RP zips"), "every line goes to the log");
        assertTrue(logged.contains("[RP release Human] Result: Human v4.1.3 is released, with 1 warning:"),
                "and the result: " + logged);
    }

    @Test
    void theNextReleaseOfAPackSaysHowLongTheLastOneTook() {
        PlayerMock admin = admin();
        admin.performCommand("rp release h v4.1.3 Better leaves");
        told(admin, "Put it live");

        admin.performCommand("rp release h v4.1.4 Again");

        List<String> second = texts(told(admin, "Put it live"));
        assertEquals("[Architect] Releasing Human v4.1.4 (\"Again\"). Last time this took less than a minute."
                + " The result comes here.", second.get(0));
    }

    @Test
    void anUnknownPackIsRefusedBeforeAnythingStarts() {
        PlayerMock admin = admin();
        admin.performCommand("rp release zz v1 x");
        List<String> told = plain(told(admin, "Unknown"));
        String reply = told.get(told.size() - 1);
        assertTrue(reply.startsWith("Unknown resource pack: zz. Use one of: ") && reply.contains("Human")
                && reply.contains("Gondor"), "the reply names the packs there are: " + reply);
        assertEquals(List.of(), started);
    }

    @Test
    void aPackWithoutACompleteReleaseEntryIsRefusedBeforeAnythingStarts() {
        PlayerMock admin = admin();
        admin.performCommand("rp release Gondor v1 x");
        List<Component> told = told(admin, "can't be released");
        assertEquals(List.of("Gondor can't be released from this server: its gitHubRpReleases entry needs owner, repo,"
                + " script and path. Ask an admin."), plain(told));
        assertEquals(List.of(), started);
    }

    @Test
    void aWindowsServerIsRefusedBeforeAnythingStarts() {
        RpReleaseUtil.onWindows = true;
        PlayerMock admin = admin();
        admin.performCommand("rp release Human v1 x");
        assertEquals(List.of("Releases only run on Linux servers."), plain(told(admin, "Linux")));
        assertEquals(List.of(), started);
    }

    @Test
    void aScriptThatCannotBeStartedSaysWhyInRed() {
        RpReleaseUtil.starter = (directory, command) -> {
            throw new IOException("Cannot run program \"sh\" (in directory \"/nowhere\"): error=2, No such file or"
                    + " directory");
        };
        PlayerMock admin = admin();
        admin.performCommand("rp release h v4.1.3 Better leaves");
        List<Component> told = told(admin, "couldn't be started");
        Component closing = told.get(told.size() - 1);
        assertEquals("[Architect] Human v4.1.3 couldn't be started: Cannot run program \"sh\" (in directory"
                + " \"/nowhere\"): error=2, No such file or directory. Ask an admin to check its gitHubRpReleases"
                + " path.", text(closing));
        assertEquals(NamedTextColor.RED, closing.color());
    }

    @Test
    void aReleaserWhoLeftGetsTheResultWhenBack() throws Exception {
        CountDownLatch left = new CountDownLatch(1);
        RpReleaseUtil.starter = (directory, command) -> {
            try {
                if (!left.await(10, TimeUnit.SECONDS)) {
                    throw new IOException("the admin never left");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException(e);
            }
            return new FakeScript(Duration.ZERO, 0, SODIUM_RELEASE);
        };
        PlayerMock admin = admin();
        admin.performCommand("rp release h v4.1.3 Better leaves");
        told(admin, "Releasing");
        admin.disconnect();
        left.countDown();
        tickFor(1000); // the release ends while the admin is away

        admin.reconnect();
        List<String> told = texts(told(admin, "Put it live"));

        int away = told.indexOf("[Architect] While you were away:");
        assertTrue(away >= 0, "the held result comes after a header: " + told);
        assertEquals("[Architect] Human v4.1.3 is released, with 1 warning:", told.get(away + 1));
        assertTrue(told.stream().noneMatch(line -> line.contains("zips done")), "the steps aren't held: " + told);
    }

    // A script whose output is no longer read stops once the pipe is full, and holds the release lock until its time
    // is up, so a line that can't be handled must not end the reading. Here rather than in RpReleaseUtilTest: the
    // failure goes to Architect's log, which needs the plugin.
    @Test
    void theOutputIsReadToItsEndEvenWhenALineCannotBeHandled() throws Exception {
        List<String> logged = logged();
        List<String> handled = new ArrayList<>();
        RpReleaseUtil.ScriptResult result = RpReleaseUtil.awaitScript(new FakeScript(Duration.ZERO, 0, "first",
                "second", "third"), Duration.ofSeconds(30), Duration.ofSeconds(5), line -> {
            handled.add(line);
            throw new IllegalStateException("a line that can't be handled");
        });

        assertEquals(List.of("first", "second", "third"), handled);
        assertTrue(result.outputComplete(), "the output was read to its end");
        assertEquals(1, logged.stream().filter(line -> line.contains("could not be handled")).count(),
                "the first failure is logged, once: " + logged);
    }

    // What Architect logs from now on
    private static List<String> logged() {
        List<String> logged = new CopyOnWriteArrayList<>();
        plugin.getLogger().addHandler(new Handler() {
            @Override
            public void publish(LogRecord record) {
                logged.add(record.getMessage());
            }

            @Override
            public void flush() {
            }

            @Override
            public void close() {
            }
        });
        return logged;
    }

    private static PlayerMock admin() {
        PlayerMock admin = new PlayerMock(server, "Admin" + (++admins), UUID.randomUUID());
        server.addPlayer(admin);
        admin.setOp(false);
        admin.addAttachment(plugin, "architect.resourcePackAdmin", true);
        while (admin.nextComponentMessage() != null) {
            // what joining said
        }
        return admin;
    }

    // Everything the admin is told until a line holding this text, or for 10 seconds: the lines come from work on
    // another thread, and on later ticks
    private static List<Component> told(PlayerMock admin, String text) {
        List<Component> told = new ArrayList<>();
        long end = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (System.nanoTime() < end) {
            server.getScheduler().performOneTick();
            for (Component line = admin.nextComponentMessage(); line != null; line = admin.nextComponentMessage()) {
                told.add(line);
                if (text(line).contains(text)) {
                    return told;
                }
            }
            sleep();
        }
        fail("the admin was not told '" + text + "', only " + texts(told));
        return told;
    }

    private static void tickFor(long millis) {
        long end = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(millis);
        while (System.nanoTime() < end) {
            server.getScheduler().performOneTick();
            sleep();
        }
    }

    private static void sleep() {
        try {
            Thread.sleep(10);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static String text(Component line) {
        return PlainTextComponentSerializer.plainText().serialize(line);
    }

    private static List<String> texts(List<Component> lines) {
        return lines.stream().map(RpReleaseCommandTest::text).toList();
    }

    // MessageUtil's lines, without the prefix and its colour code
    private static List<String> plain(List<Component> lines) {
        return texts(lines).stream().map(line -> line.replaceFirst("^(§.)?\\[Architect\\] ", "")).toList();
    }
}
