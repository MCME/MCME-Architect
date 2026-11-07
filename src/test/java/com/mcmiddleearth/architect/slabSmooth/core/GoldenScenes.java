package com.mcmiddleearth.architect.slabSmooth.core;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.GZIPInputStream;

import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * The golden scenes in {@code /slabSmooth/golden}: each file holds a scene (name, height, length, width, palette and
 * blocks) and the blocks Toti's released SlabSmooth 1.3.0 jar made of it in each run.
 */
final class GoldenScenes {

    static final List<String> NAMES = List.of("hillside", "cliff", "overhang", "crevice", "tower", "boulder");

    /** A scene's input: blocks are indices into palette, at {@code (y * length + z) * width + x}. */
    record Scene(String name, int height, int length, int width, List<String> palette, int[] blocks) {
    }

    private GoldenScenes() {
    }

    /** The whole file of a scene, runs included. */
    static JsonObject read(String name) throws IOException {
        try (InputStream in = GoldenScenes.class.getResourceAsStream("/slabSmooth/golden/" + name + ".json.gz")) {
            assertNotNull(in, "missing golden file for " + name);
            try (Reader reader = new InputStreamReader(new GZIPInputStream(in), StandardCharsets.UTF_8)) {
                return JsonParser.parseReader(reader).getAsJsonObject();
            }
        }
    }

    static Scene scene(String name) throws IOException {
        return scene(read(name));
    }

    static Scene scene(JsonObject file) {
        return new Scene(file.get("name").getAsString(), file.get("height").getAsInt(), file.get("length").getAsInt(),
                file.get("width").getAsInt(), strings(file.getAsJsonArray("palette")), ints(file.getAsJsonArray("blocks")));
    }

    static int[] ints(JsonArray array) {
        int[] out = new int[array.size()];
        for (int i = 0; i < out.length; i++) {
            out[i] = array.get(i).getAsInt();
        }
        return out;
    }

    static List<String> strings(JsonArray array) {
        List<String> out = new ArrayList<>();
        array.forEach(e -> out.add(e.getAsString()));
        return out;
    }
}
