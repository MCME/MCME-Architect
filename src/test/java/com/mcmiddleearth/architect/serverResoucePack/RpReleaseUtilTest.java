package com.mcmiddleearth.architect.serverResoucePack;

import com.mcmiddleearth.architect.serverResoucePack.RpReleaseUtil.ScriptResult;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.io.OutputStream;
import java.io.SequenceInputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

// A release script runs for minutes: Toti's Human release of 2026-09-30 took 5m12s and ended with exit code 0, yet was
// reported as an error, since Architect gave it 5 minutes. The scripts here are fakes whose time passes only while they
// are waited for, so no test waits for real. No Bukkit server is needed.
@Timeout(10)
class RpReleaseUtilTest {

    private final List<String> output = new ArrayList<>();

    @Test
    void aReleaseThatEndsAfterFiveMinutesIsASuccess() throws Exception {
        FakeScript script = new FakeScript(Duration.ofSeconds(312), 0, "Release run v4.1.3: OK");

        ScriptResult result = RpReleaseUtil.awaitScript(script, RpReleaseUtil.DEFAULT_TIMEOUT, output::add);

        assertEquals(new ScriptResult(true, 0), result);
        assertFalse(script.isStopped(), "left to end by itself");
        assertEquals(List.of("Release run v4.1.3: OK"), output, "its output is logged");
    }

    // Its output stays open while it runs, as a real script's does.
    @Test
    void aScriptStillRunningAfterItsTimeIsStopped() throws Exception {
        FakeScript script = new FakeScript(Duration.ofHours(2), 0).withOutputOpenUntilItEnds();

        ScriptResult result = RpReleaseUtil.awaitScript(script, Duration.ofMillis(100), output::add);

        assertTrue(script.isStopped(), "stopped");
        assertEquals(new ScriptResult(false, FakeScript.STOPPED), result);
    }

    @Test
    void aReleaseMayRunThirtyMinutesUnlessTheConfigSetsAnotherTime() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("gitHubRpReleases.Dwarf.timeoutMinutes", 45);
        config.set("gitHubRpReleases.Rohan.timeoutMinutes", 0);

        assertEquals(Duration.ofMinutes(30), RpReleaseUtil.getTimeout(config, "Human"));
        assertEquals(Duration.ofMinutes(45), RpReleaseUtil.getTimeout(config, "Dwarf"));
        assertEquals(Duration.ofMinutes(30), RpReleaseUtil.getTimeout(config, "Rohan"), "less than a minute is no time");
    }

    /**
     * A release script that ends with an exit code once it has run for runTime. Its time passes only while it is
     * waited for: waitFor(timeout) returns at once, as if that much time had passed. Its output is all there at once
     * and then ends, unless it is to stay open until the script ends or is stopped.
     */
    private static final class FakeScript extends Process {

        static final int STOPPED = 143;

        private final Duration runTime;
        private final int exitCode;
        private final byte[] lines;
        private final CountDownLatch end = new CountDownLatch(1);
        private boolean outputOpenUntilItEnds;
        private Duration ranFor = Duration.ZERO;
        private boolean stopped;

        FakeScript(Duration runTime, int exitCode, String... lines) {
            this.runTime = runTime;
            this.exitCode = exitCode;
            this.lines = String.join("\n", lines).getBytes(StandardCharsets.UTF_8);
        }

        FakeScript withOutputOpenUntilItEnds() {
            outputOpenUntilItEnds = true;
            return this;
        }

        synchronized boolean isStopped() {
            return stopped;
        }

        private synchronized boolean hasEnded() {
            boolean ended = stopped || ranFor.compareTo(runTime) >= 0;
            if (ended) {
                end.countDown();
            }
            return ended;
        }

        @Override
        public synchronized boolean waitFor(long timeout, TimeUnit unit) {
            if (!hasEnded()) {
                ranFor = ranFor.plusNanos(unit.toNanos(timeout));
            }
            return hasEnded();
        }

        @Override
        public synchronized int waitFor() {
            if (!hasEnded()) {
                ranFor = runTime;
            }
            return exitValue();
        }

        @Override
        public synchronized int exitValue() {
            if (!hasEnded()) {
                throw new IllegalThreadStateException("still running");
            }
            return stopped ? STOPPED : exitCode;
        }

        @Override
        public synchronized void destroy() {
            if (!hasEnded()) {
                stopped = true;
                hasEnded();
            }
        }

        @Override
        public Stream<ProcessHandle> descendants() {
            return Stream.empty();
        }

        @Override
        public InputStream getInputStream() {
            return new SequenceInputStream(new ByteArrayInputStream(lines), new InputStream() {
                @Override
                public int read() throws InterruptedIOException {
                    if (outputOpenUntilItEnds) {
                        try {
                            end.await();
                        } catch (InterruptedException ex) {
                            throw new InterruptedIOException();
                        }
                    }
                    return -1;
                }
            });
        }

        @Override
        public InputStream getErrorStream() {
            return InputStream.nullInputStream();
        }

        @Override
        public OutputStream getOutputStream() {
            return OutputStream.nullOutputStream();
        }
    }
}
