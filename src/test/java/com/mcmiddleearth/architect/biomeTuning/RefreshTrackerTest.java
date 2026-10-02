package com.mcmiddleearth.architect.biomeTuning;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RefreshTrackerTest {

    private static final UUID ALICE = UUID.fromString("00000000-0000-0000-0000-00000000000a");
    private static final UUID BOB = UUID.fromString("00000000-0000-0000-0000-00000000000b");

    @Test
    void refusesASecondRefreshWhileOneIsPending() {
        RefreshTracker tracker = new RefreshTracker();
        assertTrue(tracker.begin(ALICE, true, 0));
        assertFalse(tracker.begin(ALICE, false, 10));
        assertTrue(tracker.isPending(ALICE));
        assertFalse(tracker.isPending(BOB));
    }

    @Test
    void remembersThatThePlayerLeftTheWorld() {
        RefreshTracker tracker = new RefreshTracker();
        tracker.begin(ALICE, true, 0);
        assertFalse(tracker.hasLeftWorld(ALICE));
        assertTrue(tracker.markLeftWorld(ALICE));
        assertTrue(tracker.hasLeftWorld(ALICE));
        assertFalse(tracker.markLeftWorld(BOB), "no refresh is pending for Bob");
    }

    @Test
    void reconfiguringKeepsTheInjectFlagAndRecordsTheTime() {
        RefreshTracker tracker = new RefreshTracker();
        tracker.begin(ALICE, false, 100);
        RefreshTracker.Entry entry = tracker.markReconfiguring(ALICE, 250);
        assertEquals(RefreshTracker.Stage.RECONFIGURING, entry.stage());
        assertFalse(entry.inject());
        assertEquals(250, entry.reconfiguredAt());
        assertNull(tracker.markReconfiguring(BOB, 250), "a reconfiguration we did not start is not ours");
    }

    @Test
    void completeFinishesTheRefresh() {
        RefreshTracker tracker = new RefreshTracker();
        tracker.begin(ALICE, true, 0);
        assertNotNull(tracker.complete(ALICE));
        assertFalse(tracker.isPending(ALICE));
        assertNull(tracker.complete(ALICE));
    }

    @Test
    void expireReturnsOnlyRefreshesPastTheTimeout() {
        RefreshTracker tracker = new RefreshTracker();
        tracker.begin(ALICE, true, 0);
        tracker.begin(BOB, true, 10_000);
        List<RefreshTracker.Entry> expired = tracker.expire(15_000, 15_000);
        assertEquals(1, expired.size());
        assertEquals(ALICE, expired.get(0).player());
        assertFalse(tracker.isPending(ALICE));
        assertTrue(tracker.isPending(BOB));
    }
}
