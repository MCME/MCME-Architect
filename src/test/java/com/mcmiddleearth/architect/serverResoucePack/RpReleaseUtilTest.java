package com.mcmiddleearth.architect.serverResoucePack;

import com.mcmiddleearth.architect.serverResoucePack.RpReleaseUtil.ScriptResult;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

// A release script runs for minutes: a Human release of 2026-09-30 took 5m12s and ended with exit code 0, yet was
// reported as an error, since Architect gave it 5 minutes. The scripts here are fakes whose time passes only while
// they are waited for, so no test waits for real, but for one real script. No Bukkit server is needed.
@Timeout(10)
class RpReleaseUtilTest {

    private static final String READER = "Architect RP release output";

    private final List<String> output = new ArrayList<>();

    @Test
    void aReleaseThatEndsAfterFiveMinutesIsASuccess() throws Exception {
        FakeScript script = new FakeScript(Duration.ofSeconds(312), 0, "Release run v4.1.3: OK");

        ScriptResult result = RpReleaseUtil.awaitScript(script, RpReleaseUtil.DEFAULT_TIMEOUT, RpReleaseUtil.GRACE,
                output::add);

        assertEquals(new ScriptResult(true, 0, true), result);
        assertEquals(0, script.destroyCalls(), "a script that has ended is left alone");
        assertEquals(List.of("Release run v4.1.3: OK"), output, "its output is logged");
        assertTrue(script.readByDaemon(), "read by a daemon thread, which cannot keep the server from stopping");
        assertNoReaderLeft();
    }

    // Its output stays open while it runs, as a real script's does.
    @Test
    void aScriptStillRunningAfterItsTimeIsStoppedWithWhatItStarted() throws Exception {
        FakeChild child = new FakeChild(true);
        FakeScript script = new FakeScript(Duration.ofHours(2), 0).withOutputOpenUntilItEnds().withChild(child);

        ScriptResult result = RpReleaseUtil.awaitScript(script, Duration.ofMillis(100), RpReleaseUtil.GRACE,
                output::add);

        assertEquals(new ScriptResult(false, FakeScript.STOPPED, true), result);
        assertTrue(script.destroyCalls() > 0, "stopped");
        assertTrue(child.destroyed, "what it started is stopped too");
        assertFalse(child.killed, "and not killed, as it ended when asked to");
        assertNoReaderLeft();
    }

    // On Linux, sh ends at once when it is asked to, so its end does not mean that what it started has ended.
    @Test
    void whatAStoppedScriptStartedIsKilledIfItDoesNotEnd() throws Exception {
        FakeChild child = new FakeChild(false);
        FakeScript script = new FakeScript(Duration.ofHours(2), 0).withChild(child);

        RpReleaseUtil.awaitScript(script, Duration.ofMillis(100), Duration.ofMillis(100), output::add);

        assertTrue(child.destroyed, "asked to end");
        assertTrue(child.killed, "killed when it did not");
    }

    // A process the script started may hold its output open after the script has ended.
    @Test
    void outputStillOpenAfterTheScriptEndedIsNotAwaitedButReported() throws Exception {
        FakeScript script = new FakeScript(Duration.ofSeconds(10), 0, "Release run v4.1.3: OK").withOutputHeldOpen();

        ScriptResult result = RpReleaseUtil.awaitScript(script, RpReleaseUtil.DEFAULT_TIMEOUT, Duration.ofMillis(100),
                output::add);

        assertEquals(new ScriptResult(true, 0, false), result);
        assertEquals(List.of("Release run v4.1.3: OK"), output, "what came is logged");
        assertNoReaderLeft();
    }

    // A real one: this JVM runs a program that writes 2 MB to its error stream before a line to its standard output.
    // A pipe holds much less, so the program waits for its error stream to be read before it can write that line. A
    // JVM that compiles the program before it runs it can be slow on a busy machine, so it has half a minute.
    @Test
    @Timeout(60)
    void aScriptThatWritesMuchToItsErrorStreamIsNotHeldUp(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve("Noisy.java"), """
                public class Noisy {
                    public static void main(String[] args) {
                        String line = "x".repeat(99);
                        for (int i = 0; i < 20_000; i++) {
                            System.err.println(line);
                        }
                        System.out.println("done");
                    }
                }
                """);
        String java = ProcessHandle.current().info().command().orElseThrow();

        Process script = RpReleaseUtil.startScript(dir.toFile(), java, "Noisy.java");
        ScriptResult result = RpReleaseUtil.awaitScript(script, Duration.ofSeconds(30), Duration.ofSeconds(5),
                output::add);

        assertEquals(new ScriptResult(true, 0, true), result);
        assertTrue(output.contains("done"), "its standard output is logged");
        assertEquals(20_000, output.stream().filter(line -> line.startsWith("xxx")).count(), "and its error stream");
    }

    @Test
    void aReleaseMayRunThirtyMinutesUnlessTheConfigSetsAnotherTime() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("gitHubRpReleases.Dwarf.timeoutMinutes", 45);
        config.set("gitHubRpReleases.Rohan.timeoutMinutes", 0);

        assertEquals(Duration.ofMinutes(30), RpReleaseUtil.getTimeout(config, "Human"));
        assertEquals(Duration.ofMinutes(45), RpReleaseUtil.getTimeout(config, "Dwarf"));
        assertEquals(Duration.ofMinutes(30), RpReleaseUtil.getTimeout(config, "Rohan"),
                "less than a minute is no time");
    }

    // The reader is shut down when awaitScript returns, and with these fake scripts its thread ends a moment later. Not
    // so with a real script whose output a process it started still holds open: an interrupt does not end a read of a
    // pipe, so that thread, a daemon, stays until the output closes, which does no harm.
    private static void assertNoReaderLeft() throws InterruptedException {
        long until = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
        while (readerAlive() && System.nanoTime() < until) {
            Thread.sleep(10);
        }
        assertFalse(readerAlive(), "the reader thread has ended");
    }

    private static boolean readerAlive() {
        return Thread.getAllStackTraces().keySet().stream()
                .anyMatch(thread -> thread.getName().equals(READER) && thread.isAlive());
    }
}
