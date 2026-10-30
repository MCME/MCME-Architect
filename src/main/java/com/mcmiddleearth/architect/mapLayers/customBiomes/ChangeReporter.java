package com.mcmiddleearth.architect.mapLayers.customBiomes;

/**
 * Paces redraws as the item-block budget does: the first change after a quiet spell is reported at the next second,
 * and after that changes are reported at most once every {@code reportSeconds}. Each report rebuilds shapes, and each
 * changed marker makes dynmap rewrite its world's whole marker file on the main thread. Main thread only.
 */
final class ChangeReporter {

    private final Runnable changed;
    private final int reportSeconds;
    private boolean unreported;
    private int secondsSinceReport;

    /** {@code changed} runs after a {@link #mark}, at most once every {@code reportSeconds}. */
    ChangeReporter(Runnable changed, int reportSeconds) {
        this.changed = changed;
        this.reportSeconds = reportSeconds;
        this.secondsSinceReport = reportSeconds; // so the first change is reported at the next second
    }

    /** Something changed: it is reported at the next {@link #everySecond} the pace allows. */
    void mark() {
        unreported = true;
    }

    /** Called once a second: reports a change, unless the last report was less than {@code reportSeconds} ago. */
    void everySecond() {
        if (secondsSinceReport < reportSeconds) {
            secondsSinceReport++;
        }
        if (unreported && secondsSinceReport >= reportSeconds) {
            unreported = false;
            secondsSinceReport = 0;
            changed.run();
        }
    }
}
