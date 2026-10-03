package com.mcmiddleearth.architect.randomiser;

import org.junit.jupiter.api.Test;
import java.lang.reflect.Field;
import static org.junit.jupiter.api.Assertions.*;

// RandomiserConfig constructs with no Bukkit, so setProbs is a pure unit test.
class RandomiserConfigTest {

    private static int sumOfProps(RandomiserConfig cfg) throws Exception {
        Field f = RandomiserConfig.class.getDeclaredField("props");
        f.setAccessible(true);
        int sum = 0;
        for (int p : (int[]) f.get(cfg)) sum += p;
        return sum;
    }

    @Test void emptyProbsDoesNotThrow() {
        assertDoesNotThrow(() -> new RandomiserConfig().setProbs(new int[0]));
    }

    @Test void overHundredIsNormalisedNotCrashed() throws Exception {
        RandomiserConfig cfg = new RandomiserConfig();
        assertDoesNotThrow(() -> cfg.setProbs(new int[]{50, 60})); // sum 110 > 100
        assertEquals(100, sumOfProps(cfg), "over-100 probs must be normalised to 100, not AIOOBE");
    }

    @Test void underHundredIsToppedUpToHundred() throws Exception {
        RandomiserConfig cfg = new RandomiserConfig();
        cfg.setProbs(new int[]{30, 30}); // sum 60 < 100
        assertEquals(100, sumOfProps(cfg));
    }
}
