package com.mcmiddleearth.architect.slabSmooth;

import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitScheduler;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

/**
 * A BukkitScheduler (a proxy) that runs main-thread tasks as Paper's does, in ticks the test runs. A task is due at the
 * current tick plus its delay, and a tick runs every task that is due, oldest first, including the ones handed over while
 * the tick runs. So a task that a running task hands to runTask, with no delay, runs in the same tick, as on Paper;
 * MockBukkit runs every task a tick later at the earliest. A tick that has run {@link #cap} tasks and still has one due
 * stops with {@link TickNeverEnds}: on Paper it would never end, and the watchdog would stop the server.
 *
 * <p>It knows runTask and runTaskLater with a Runnable, records every call, and fails a test on any other call. Tasks may
 * be handed over from any thread; the test runs the ticks, on MockBukkit's main thread where a test needs one.
 */
final class FakeScheduler {

    /** One call: runTask, which has no delay, or runTaskLater with its delay. */
    record Call(String method, Plugin plugin, Runnable task, long delay) {
    }

    /** A tick that would never end on Paper: a task keeps handing itself on to the same tick. */
    static final class TickNeverEnds extends AssertionError {
        TickNeverEnds(String message) {
            super(message);
        }
    }

    private record Due(long tick, long order, Runnable task) {
    }

    /** The scheduler to hand to the code under test. */
    final BukkitScheduler scheduler;
    /** The most tasks one tick may run. */
    final int cap;
    private final List<Call> calls = new ArrayList<>();
    private final List<Due> waiting = new ArrayList<>();
    private long tick, handedOver;

    FakeScheduler() {
        this(100);
    }

    FakeScheduler(int cap) {
        this.cap = cap;
        scheduler = (BukkitScheduler) Proxy.newProxyInstance(FakeScheduler.class.getClassLoader(),
                new Class<?>[] {BukkitScheduler.class}, (proxy, method, args) -> switch (method.getName()) {
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    case "toString" -> "a scheduler that runs tasks as Paper does";
                    case "runTask" -> handOver("runTask", args, 0);
                    case "runTaskLater" -> handOver("runTaskLater", args, (long) args[2]);
                    default -> throw new AssertionError("unexpected call: " + method);
                });
    }

    private Object handOver(String method, Object[] args, long delay) {
        if (!(args[1] instanceof Runnable task)) {
            throw new AssertionError("unexpected call: " + method + " with " + args[1]);
        }
        synchronized (this) {
            calls.add(new Call(method, (Plugin) args[0], task, delay));
            waiting.add(new Due(tick + Math.max(0, delay), handedOver++, task)); // Paper counts a negative delay as none
        }
        return null; // the BukkitTask: the code under test doesn't keep it
    }

    /** Every call so far, in order. */
    synchronized List<Call> calls() {
        return List.copyOf(calls);
    }

    /** How many tasks wait for a tick. */
    synchronized int waiting() {
        return waiting.size();
    }

    /**
     * Runs the next tick, on the caller's thread: every task due by now, the oldest first, also those handed over while
     * it runs that are due by now too. A task's exception reaches the caller.
     *
     * @return how many tasks ran
     * @throws TickNeverEnds when {@link #cap} tasks have run and one is still due
     */
    int tick() {
        long now;
        synchronized (this) {
            now = ++tick;
        }
        int ran = 0;
        for (Runnable task = nextDue(now); task != null; task = nextDue(now)) {
            if (ran == cap) {
                throw new TickNeverEnds("tick " + now + " has run " + cap + " tasks and another is due: a task keeps"
                        + " handing itself on to the same tick, so on Paper this tick would never end");
            }
            ran++;
            task.run();
        }
        return ran;
    }

    private synchronized Runnable nextDue(long now) {
        Due first = null;
        for (Due due : waiting) {
            if (due.tick() <= now && (first == null || due.tick() < first.tick()
                    || due.tick() == first.tick() && due.order() < first.order())) {
                first = due;
            }
        }
        if (first == null) {
            return null;
        }
        waiting.remove(first);
        return first.task();
    }
}
