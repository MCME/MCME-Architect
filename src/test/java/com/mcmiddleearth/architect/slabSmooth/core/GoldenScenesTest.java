package com.mcmiddleearth.architect.slabSmooth.core;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import java.util.zip.GZIPInputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Compares the algorithm with what Toti's released SlabSmooth 1.3.0 jar produced on fixed scenes. The files in
 * {@code /slabSmooth/golden} hold each scene and the blocks of every run; this test reads only those files.
 */
class GoldenScenesTest {

    private static final List<String> SCENES = List.of("hillside", "cliff", "overhang", "crevice", "tower", "boulder");

    @TestFactory
    Stream<DynamicTest> goldenRuns() throws Exception {
        Table table = TestTables.handMade();
        List<DynamicTest> tests = new ArrayList<>();
        for (String name : SCENES) {
            JsonObject scene = readScene(name);
            JsonArray runs = scene.getAsJsonArray("runs");
            for (int r = 0; r < runs.size(); r++) {
                JsonObject run = runs.get(r).getAsJsonObject();
                String title = name + "/" + run.get("mode").getAsString() + "/" + run.get("options");
                tests.add(DynamicTest.dynamicTest(title, () -> check(scene, run, table)));
            }
        }
        return tests.stream();
    }

    private static void check(JsonObject scene, JsonObject run, Table table) {
        String name = scene.get("name").getAsString();
        String mode = run.get("mode").getAsString();
        int height = scene.get("height").getAsInt(), length = scene.get("length").getAsInt(), width = scene.get("width").getAsInt();
        int[] blocks = ints(scene.getAsJsonArray("blocks"));
        List<String> palette = strings(scene.getAsJsonArray("palette"));
        Options opts = new Options();
        for (Map.Entry<String, JsonElement> option : run.getAsJsonObject("options").entrySet()) {
            opts.set(option.getKey(), option.getValue().getAsString());
        }
        Smoother.Result result = switch (mode) {
            case "directional" -> Directional.smooth(blocks, height, length, width, palette, table, opts, null, null);
            case "top" -> Smoother.smooth(blocks, height, length, width, palette, table, opts, null, null);
            case "tiles" -> Tiles.smooth(blocks, height, length, width, palette, table, opts, null,
                    run.get("tile").getAsInt(), run.get("margin").getAsInt(), null);
            default -> throw new IllegalStateException("unknown mode " + mode);
        };
        int[] expected = ints(run.getAsJsonArray("blocks"));
        List<String> expectedPalette = strings(run.getAsJsonArray("palette"));
        assertEquals(expected.length, result.blocks().length, name + "/" + mode + " block count");
        for (int i = 0; i < expected.length; i++) {
            assertEquals(BlockStates.normalize(expectedPalette.get(expected[i])),
                    BlockStates.normalize(result.palette().get(result.blocks()[i])),
                    name + "/" + mode + " at index " + i);
        }
        assertEquals(run.get("changedColumns").getAsInt(), result.report().changedColumns(), name + "/" + mode + " changed columns");
    }

    private static JsonObject readScene(String name) throws Exception {
        try (InputStream in = GoldenScenesTest.class.getResourceAsStream("/slabSmooth/golden/" + name + ".json.gz")) {
            assertNotNull(in, "missing golden file for " + name);
            try (Reader reader = new InputStreamReader(new GZIPInputStream(in), StandardCharsets.UTF_8)) {
                return JsonParser.parseReader(reader).getAsJsonObject();
            }
        }
    }

    private static int[] ints(JsonArray array) {
        int[] out = new int[array.size()];
        for (int i = 0; i < out.length; i++) {
            out[i] = array.get(i).getAsInt();
        }
        return out;
    }

    private static List<String> strings(JsonArray array) {
        List<String> out = new ArrayList<>();
        array.forEach(e -> out.add(e.getAsString()));
        return out;
    }
}
