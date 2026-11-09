package com.mcmiddleearth.architect.slabSmooth.core;

/**
 * Thrown by the algorithm when a {@link WorkBudget} runs out. Nothing has been changed when it is thrown: the
 * algorithm only returns blocks at the very end. The message names the limit that was hit, for the log.
 */
public final class TooMuchWork extends RuntimeException {

    public TooMuchWork(String message) {
        super(message);
    }
}
