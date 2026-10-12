package com.mcmiddleearth.architect.serverResoucePack;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.io.OutputStream;
import java.io.SequenceInputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * A release script that ends with an exit code once it has run for runTime. Its time passes only while it is
 * waited for: waitFor(timeout) returns at once, as if that much time had passed. Its output is all there at once
 * and then ends, unless it is to stay open until the script ends or is stopped, or to be held open for good.
 */
final class FakeScript extends Process {

    static final int STOPPED = 143;

    private final Duration runTime;
    private final int exitCode;
    private final byte[] lines;
    private final CountDownLatch end = new CountDownLatch(1);
    private final CompletableFuture<Process> exit = new CompletableFuture<>();
    private boolean outputOpenUntilItEnds;
    private boolean outputHeldOpen;
    private FakeChild child;
    private Duration ranFor = Duration.ZERO;
    private boolean stopped;
    private int destroyCalls;
    private volatile boolean readByDaemon;

    FakeScript(Duration runTime, int exitCode, String... lines) {
        this.runTime = runTime;
        this.exitCode = exitCode;
        this.lines = Stream.of(lines).map(line -> line + "\n").collect(Collectors.joining())
                .getBytes(StandardCharsets.UTF_8);
    }

    FakeScript withOutputOpenUntilItEnds() {
        outputOpenUntilItEnds = true;
        return this;
    }

    FakeScript withOutputHeldOpen() {
        outputHeldOpen = true;
        return this;
    }

    FakeScript withChild(FakeChild child) {
        this.child = child;
        return this;
    }

    synchronized int destroyCalls() {
        return destroyCalls;
    }

    boolean readByDaemon() {
        return readByDaemon;
    }

    private synchronized boolean hasEnded() {
        boolean ended = stopped || ranFor.compareTo(runTime) >= 0;
        if (ended) {
            end.countDown();
            exit.complete(this);
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
        destroyCalls++;
        if (!hasEnded()) {
            stopped = true;
            hasEnded();
        }
    }

    @Override
    public CompletableFuture<Process> onExit() {
        return exit;
    }

    @Override
    public Stream<ProcessHandle> descendants() {
        return child == null ? Stream.empty() : Stream.of(child);
    }

    @Override
    public InputStream getInputStream() {
        return new SequenceInputStream(new ByteArrayInputStream(lines), new InputStream() {
            @Override
            public int read() throws InterruptedIOException {
                readByDaemon = Thread.currentThread().isDaemon();
                try {
                    if (outputHeldOpen) {
                        new CountDownLatch(1).await();
                    } else if (outputOpenUntilItEnds) {
                        end.await();
                    }
                } catch (InterruptedException ex) {
                    throw new InterruptedIOException();
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
