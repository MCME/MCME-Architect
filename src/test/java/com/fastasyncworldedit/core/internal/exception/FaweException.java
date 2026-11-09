package com.fastasyncworldedit.core.internal.exception;

/**
 * Stands in for FastAsyncWorldEdit's refusal in tests. FAWE isn't on the test class path, so this has its package and
 * name, which is all the slabSmooth code goes by.
 */
public class FaweException extends RuntimeException {

    public FaweException(String message) {
        super(message);
    }
}
