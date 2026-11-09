package com.mcmiddleearth.architect.slabSmooth.core;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Compares the algorithm with what Toti's released SlabSmooth 1.3.0 jar produced on fixed scenes. The files in
 * {@code /slabSmooth/golden} hold each scene and the blocks of every run; this test reads only those files.
 */
class GoldenScenesTest {

    @TestFactory
    Stream<DynamicTest> goldenRuns() throws Exception {
        Table table = TestTables.handMade();
        List<DynamicTest> tests = new ArrayList<>();
        for (String name : GoldenScenes.NAMES) {
            JsonObject scene = GoldenScenes.read(name);
            JsonArray runs = scene.getAsJsonArray("runs");
            for (int r = 0; r < runs.size(); r++) {
                JsonObject run = runs.get(r).getAsJsonObject();
                String title = name + "/" + run.get("mode").getAsString() + "/" + run.get("options");
                tests.add(DynamicTest.dynamicTest(title, () -> check(GoldenScenes.scene(scene), run, table)));
            }
        }
        return tests.stream();
    }

    private static void check(GoldenScenes.Scene scene, JsonObject run, Table table) {
        String name = scene.name();
        String mode = run.get("mode").getAsString();
        int height = scene.height(), length = scene.length(), width = scene.width();
        int[] blocks = scene.blocks();
        List<String> palette = scene.palette();
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
        int[] expected = GoldenScenes.ints(run.getAsJsonArray("blocks"));
        List<String> expectedPalette = GoldenScenes.strings(run.getAsJsonArray("palette"));
        assertEquals(expected.length, result.blocks().length, name + "/" + mode + " block count");
        for (int i = 0; i < expected.length; i++) {
            assertEquals(BlockStates.normalize(expectedPalette.get(expected[i])),
                    BlockStates.normalize(result.palette().get(result.blocks()[i])),
                    name + "/" + mode + " at index " + i);
        }
        assertEquals(run.get("changedColumns").getAsInt(), result.report().changedColumns(), name + "/" + mode + " changed columns");
    }
}
