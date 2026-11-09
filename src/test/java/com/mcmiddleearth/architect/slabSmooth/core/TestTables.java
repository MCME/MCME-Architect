package com.mcmiddleearth.architect.slabSmooth.core;

import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;

/** The table shared by the core tests and the job tests; it replaces a helper Toti kept in ParityTest. */
public final class TestTables {

    private TestTables() {
    }

    /** The hand-made RP-Human table, read from the test class path. */
    public static Table handMade() throws Exception {
        try (Reader r = new InputStreamReader(TestTables.class.getResourceAsStream("/slabSmooth/table.json"), StandardCharsets.UTF_8)) {
            return Table.load(r);
        }
    }
}
