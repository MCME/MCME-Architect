package com.mcmiddleearth.architect.slabSmooth.core;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OptionsTest {

    /** A negative cost would reward pieces on every free surface within band. */
    @Test
    void costsCannotBeNegative() {
        for (String cost : List.of("cost_stairs", "cost_corner", "cost_vslab", "cost_fill")) {
            assertThrows(IllegalArgumentException.class, () -> new Options().set(cost, "-1"));
            assertDoesNotThrow(() -> new Options().set(cost, "0"), cost);
        }
    }
}
