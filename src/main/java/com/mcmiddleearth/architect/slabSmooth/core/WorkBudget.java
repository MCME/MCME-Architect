package com.mcmiddleearth.architect.slabSmooth.core;

import java.util.function.LongSupplier;

/**
 * Limits how much work one smoothing may do: a deadline on a nanosecond clock, and a number of surfaces per pass
 * (the surfaces decide how much memory and time a pass needs). The algorithm calls {@link #checkTime} once per sweep
 * and {@link #checkSurfaces} before it allocates per-surface arrays. {@link #NONE} never stops anything.
 */
public final class WorkBudget {

    /** No limits, and it ignores interrupts: the brush runs on a thread whose interrupts are not ours to act on. */
    public static final WorkBudget NONE = new WorkBudget(null, 0, Integer.MAX_VALUE);

    private final LongSupplier nanoClock;
    private final long deadlineNanos;
    private final int maxSurfacesPerPass;

    /**
     * @param nanoClock          the clock, such as {@code System::nanoTime}
     * @param deadlineNanos      the time on that clock after which {@link #checkTime} throws
     * @param maxSurfacesPerPass the most surfaces one pass may have
     */
    public WorkBudget(LongSupplier nanoClock, long deadlineNanos, int maxSurfacesPerPass) {
        this.nanoClock = nanoClock;
        this.deadlineNanos = deadlineNanos;
        this.maxSurfacesPerPass = maxSurfacesPerPass;
    }

    /** Throws {@link TooMuchWork} when the deadline has passed or the current thread was interrupted. */
    public void checkTime() {
        if (nanoClock == null) {
            return;
        }
        if (Thread.currentThread().isInterrupted()) {
            throw new TooMuchWork("interrupted");
        }
        if (nanoClock.getAsLong() - deadlineNanos > 0) {
            throw new TooMuchWork("time limit reached");
        }
    }

    /** Throws {@link TooMuchWork} when a pass has more surfaces than allowed. */
    public void checkSurfaces(int surfaces) {
        if (surfaces > maxSurfacesPerPass) {
            throw new TooMuchWork("more than " + maxSurfacesPerPass + " surfaces in one pass");
        }
    }
}
