package com.mcmiddleearth.architect.serverResoucePack;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

/** A process the script started: one that ends when it is asked to, or one that ends only when killed. */
final class FakeChild implements ProcessHandle {

    private final boolean endsWhenAsked;
    private final CompletableFuture<ProcessHandle> exit = new CompletableFuture<>();
    volatile boolean destroyed;
    volatile boolean killed;

    FakeChild(boolean endsWhenAsked) {
        this.endsWhenAsked = endsWhenAsked;
    }

    @Override
    public boolean destroy() {
        destroyed = true;
        if (endsWhenAsked) {
            exit.complete(this);
        }
        return true;
    }

    @Override
    public boolean destroyForcibly() {
        killed = true;
        exit.complete(this);
        return true;
    }

    @Override
    public boolean isAlive() {
        return !exit.isDone();
    }

    @Override
    public CompletableFuture<ProcessHandle> onExit() {
        return exit;
    }

    @Override
    public long pid() {
        return 4242;
    }

    @Override
    public Optional<ProcessHandle> parent() {
        return Optional.empty();
    }

    @Override
    public Stream<ProcessHandle> children() {
        return Stream.empty();
    }

    @Override
    public Stream<ProcessHandle> descendants() {
        return Stream.empty();
    }

    @Override
    public Info info() {
        throw new UnsupportedOperationException("not needed here");
    }

    @Override
    public boolean supportsNormalTermination() {
        return true;
    }

    @Override
    public int compareTo(ProcessHandle other) {
        return Long.compare(pid(), other.pid());
    }
}
