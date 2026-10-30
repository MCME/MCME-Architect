package com.mcmiddleearth.architect.mapLayers.customBiomes;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

// Each report rebuilds a world's shapes, and each changed marker makes dynmap rewrite the world's whole marker file, so
// paint reaches the map at most once every 10 seconds. A change after a quiet spell still shows within a second.
class ChangeReporterTest {

    private int reports;
    private final ChangeReporter reporter = new ChangeReporter(() -> reports++, 10);

    private void seconds(int count) {
        for (int i = 0; i < count; i++) {
            reporter.everySecond();
        }
    }

    @Test
    void theFirstChangeAfterAQuietSpellIsReportedAtTheNextSecond() {
        reporter.mark();
        assertEquals(0, reports, "not while the change is being made");

        reporter.everySecond();

        assertEquals(1, reports);
    }

    @Test
    void laterChangesWaitUntilTenSecondsHavePassed() {
        reporter.mark();
        reporter.everySecond();
        reporter.mark();

        seconds(9);
        assertEquals(1, reports, "within 10 s of the last report");
        reporter.everySecond();

        assertEquals(2, reports);
    }

    @Test
    void manyChangesBetweenReportsAreOneReport() {
        reporter.mark();
        reporter.everySecond();
        for (int i = 0; i < 5; i++) {
            reporter.mark();
            reporter.everySecond();
        }

        seconds(20);

        assertEquals(2, reports);
    }

    @Test
    void secondsWithoutAChangeReportNothing() {
        seconds(30);
        assertEquals(0, reports);

        reporter.mark();
        reporter.everySecond();

        assertEquals(1, reports, "the quiet spell counts: no wait");
    }

    @Test
    void aChangeTenQuietSecondsAfterAReportIsReportedAtTheNextSecond() {
        reporter.mark();
        reporter.everySecond();
        seconds(10);

        reporter.mark();
        reporter.everySecond();

        assertEquals(2, reports);
    }
}
