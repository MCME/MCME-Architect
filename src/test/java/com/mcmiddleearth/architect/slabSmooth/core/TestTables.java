package com.mcmiddleearth.architect.slabSmooth.core;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.GZIPInputStream;

import static org.junit.jupiter.api.Assumptions.assumeTrue;

/** Test inputs shared by the core tests; these replace the two helpers Toti kept in ParityTest. */
final class TestTables {

    private TestTables() {
    }

    /** The hand-made RP-Human table, read from the test class path. */
    static Table handMade() throws Exception {
        try (Reader r = new InputStreamReader(TestTables.class.getResourceAsStream("/slabSmooth/table.json"), StandardCharsets.UTF_8)) {
            return Table.load(r);
        }
    }

    /** A terrain fixture made by the Python prototype's tools/make_fixtures.py; the test is skipped without it. */
    static JsonObject fixture(String name) throws Exception {
        Path file = Path.of(System.getProperty("slabsmooth.fixtures", "tools/fixtures"), name + ".json.gz");
        assumeTrue(Files.exists(file), "no fixtures; run tools/make_fixtures.py");
        try (Reader r = new InputStreamReader(new GZIPInputStream(Files.newInputStream(file)), StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(r).getAsJsonObject();
        }
    }
}
