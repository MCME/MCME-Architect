package com.mcmiddleearth.architect.slabSmooth.core;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorkBudgetTest {

    private static final int SIZE = 40;
    private static final List<String> PALETTE = List.of("minecraft:air", "minecraft:stone");

    /** A SIZE-cubed box where half the blocks are stone: a huge number of surfaces. */
    private static int[] noise() {
        Random random = new Random(7);
        int[] blocks = new int[SIZE * SIZE * SIZE];
        for (int i = 0; i < blocks.length; i++) {
            blocks[i] = random.nextBoolean() ? 1 : 0;
        }
        return blocks;
    }

    /** One way of running the algorithm with a budget. */
    private interface Run {
        void with(WorkBudget budget) throws Exception;
    }

    private static List<Run> allThree() throws Exception {
        Table table = TestTables.handMade();
        int[] blocks = noise();
        Options opts = new Options();
        return List.of(
                budget -> Directional.smooth(blocks, SIZE, SIZE, SIZE, PALETTE, table, opts, null, null, budget),
                budget -> Tiles.smooth(blocks, SIZE, SIZE, SIZE, PALETTE, table, opts, null, 20, 2, null, budget),
                budget -> Smoother.smooth(blocks, SIZE, SIZE, SIZE, PALETTE, table, opts, null, null, budget));
    }

    @Test
    void tooManySurfacesStopsBeforeSolving() throws Exception {
        for (Run run : allThree()) {
            WorkBudget budget = new WorkBudget(() -> 0L, Long.MAX_VALUE, 100);
            TooMuchWork stop = assertThrows(TooMuchWork.class, () -> run.with(budget));
            assertTrue(stop.getMessage().contains("100 surfaces"), stop.getMessage());
        }
    }

    @Test
    void aPassedDeadlineStopsTheWork() throws Exception {
        for (Run run : allThree()) {
            WorkBudget budget = new WorkBudget(() -> 1_000L, 500L, Integer.MAX_VALUE);
            TooMuchWork stop = assertThrows(TooMuchWork.class, () -> run.with(budget));
            assertEquals("time limit reached", stop.getMessage());
        }
    }

    @Test
    void aDeadlineInTheFutureLetsTheWorkRun() throws Exception {
        Table table = TestTables.handMade();
        GoldenScenes.Scene hill = GoldenScenes.scene("hillside");
        WorkBudget budget = new WorkBudget(() -> 0L, 1_000L, Integer.MAX_VALUE);
        assertDoesNotThrow(() -> Directional.smooth(hill.blocks(), hill.height(), hill.length(), hill.width(),
                hill.palette(), table, new Options(), null, null, budget));
    }

    @Test
    void anInterruptedThreadStopsTheWork() throws Exception {
        List<Run> runs = allThree();
        Thread.currentThread().interrupt();
        try {
            for (Run run : runs) {
                WorkBudget budget = new WorkBudget(() -> 0L, Long.MAX_VALUE, Integer.MAX_VALUE);
                assertThrows(TooMuchWork.class, () -> run.with(budget));
                assertTrue(Thread.currentThread().isInterrupted(), "the interrupt flag stays set");
            }
        } finally {
            Thread.interrupted(); // clear the flag for the other tests
        }
    }

    @Test
    void noBudgetNeverStopsEvenWhenInterrupted() {
        Thread.currentThread().interrupt();
        try {
            assertDoesNotThrow(WorkBudget.NONE::checkTime);
            assertDoesNotThrow(() -> WorkBudget.NONE.checkSurfaces(Integer.MAX_VALUE));
            assertTrue(Thread.currentThread().isInterrupted());
        } finally {
            Thread.interrupted();
        }
        assertFalse(Thread.currentThread().isInterrupted());
    }

    @Test
    void noBudgetChangesNothing() throws Exception {
        Table table = TestTables.handMade();
        GoldenScenes.Scene hill = GoldenScenes.scene("hillside");
        int[] copy = hill.blocks().clone();
        Consumer<String> noLog = null;
        Smoother.Result old = Directional.smooth(hill.blocks(), hill.height(), hill.length(), hill.width(),
                hill.palette(), table, new Options(), null, noLog);
        Smoother.Result none = Directional.smooth(hill.blocks(), hill.height(), hill.length(), hill.width(),
                hill.palette(), table, new Options(), null, noLog, WorkBudget.NONE);
        assertArrayEquals(old.blocks(), none.blocks());
        assertEquals(old.palette(), none.palette());
        assertArrayEquals(copy, hill.blocks(), "the input is not touched");
    }
}
