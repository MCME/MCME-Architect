package com.mcmiddleearth.architect.slabSmooth;

import com.fastasyncworldedit.core.internal.exception.FaweException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * How EditSessionTarget passes on what WorldEdit throws. FaweException here is a stand-in with FAWE's package and
 * name; the rest of the class needs a real WorldEdit, so it's checked on a server.
 */
class EditSessionTargetTest {

    /** Another plugin's exception that happens to have the same simple name. */
    private static final class Elsewhere {
        static final class FaweException extends RuntimeException {
            FaweException(String message) {
                super(message);
            }
        }
    }

    @Test
    void aFaweExceptionBecomesARefusal() {
        FaweException fawe = new FaweException("No allowed region");

        BlockTarget.Refused refused = assertInstanceOf(BlockTarget.Refused.class, EditSessionTarget.translated(fawe));
        assertEquals("No allowed region", refused.getMessage());
        assertSame(fawe, refused.getCause(), "kept, so FAWE can still find it and tell the player about a brush click");

        RuntimeException limit = EditSessionTarget.translated(new FaweException("Max changes reached") {
        });
        assertInstanceOf(BlockTarget.Refused.class, limit, "FAWE's own subclasses are refusals too");
        assertEquals("Max changes reached", limit.getMessage());
    }

    @Test
    void anythingElseStaysAsItIs() {
        RuntimeException other = new IllegalStateException("something else");
        assertSame(other, EditSessionTarget.translated(other));

        RuntimeException lookalike = new Elsewhere.FaweException("not FAWE's");
        assertSame(lookalike, EditSessionTarget.translated(lookalike), "only FAWE's own class counts, not its simple name");
    }
}
