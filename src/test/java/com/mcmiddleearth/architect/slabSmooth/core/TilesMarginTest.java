package com.mcmiddleearth.architect.slabSmooth.core;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/** {@link Tiles#margin} is Toti's formula: it gives the margin his released jar used in the golden tiles run. */
class TilesMarginTest {

    @Test
    void theMarginIsTheOneStoredWithTheGoldenTilesRun() throws Exception {
        JsonObject tiles = null;
        for (JsonElement run : GoldenScenes.read("hillside").getAsJsonArray("runs")) {
            if ("tiles".equals(run.getAsJsonObject().get("mode").getAsString())) {
                tiles = run.getAsJsonObject();
            }
        }
        assertNotNull(tiles, "the hillside has a tiles run");
        Options opts = new Options();
        for (Map.Entry<String, JsonElement> option : tiles.getAsJsonObject("options").entrySet()) {
            opts.set(option.getKey(), option.getValue().getAsString());
        }
        assertEquals(tiles.get("margin").getAsInt(), Tiles.margin(opts));
    }
}
