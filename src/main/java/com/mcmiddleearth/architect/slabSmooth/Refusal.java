package com.mcmiddleearth.architect.slabSmooth;

/**
 * SlabSmooth turning a request down for a reason the player can act on, such as a selection that is too big or a pack
 * that is still being read. Its message is for the player and is shown as it is. Any other exception is unexpected:
 * the player is told that something went wrong, and the log gets the details. (FAWE turning an edit down is
 * {@link BlockTarget.Refused}.)
 */
final class Refusal extends RuntimeException {

    Refusal(String message) {
        super(message);
    }
}
