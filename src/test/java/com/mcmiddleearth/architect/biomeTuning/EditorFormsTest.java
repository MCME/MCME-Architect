package com.mcmiddleearth.architect.biomeTuning;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.kyori.adventure.text.Component;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class EditorFormsTest {

    private static EditorView.Slider slider(String key, float initial, float min, float max, float step) {
        return new EditorView.Slider(key, Component.text(key), min, max, step, initial, EditorLayout.PLAIN);
    }

    private static EditorView.Choice choice(String key, String selected, String... ids) {
        List<EditorView.Option> options = new ArrayList<>();
        for (String id : ids) {
            options.add(new EditorView.Option(id, Component.text(id), id.equals(selected)));
        }
        return new EditorView.Choice(key, Component.text(key), options);
    }

    @Test
    void onlyChangedFieldsBecomeEdits() {
        List<EditorView.Input> form = List.of(slider("temperature", 0.7f, -1, 2, 0.05f),
                slider("downfall", 0.8f, 0, 1, 0.05f),
                new EditorView.Toggle("precipitation", Component.text("Precipitation"), true),
                choice("temperature_modifier", EditorForms.NOT_SET, EditorForms.NOT_SET, "none", "frozen"),
                new EditorView.TextBox("json", Component.text("JSON"), "{}", 100, true));
        List<BiomeTuningService.Edit> edits = EditorForms.changes(form, FakeAnswers.of(Map.of("temperature", 0.25f,
                "downfall", 0.8f, "precipitation", false, "temperature_modifier", "frozen", "json", "{\"a\":1}")));
        assertEquals(List.of(new BiomeTuningService.Edit("temperature", "0.25"),
                        new BiomeTuningService.Edit("precipitation", "false"),
                        new BiomeTuningService.Edit("temperature_modifier", "frozen")), edits,
                "a changed text box is no edit: its own window reads it");
    }

    @Test
    void choosingNotSetRemovesTheValue() {
        List<EditorView.Input> form = List.of(choice("grass_modifier", "swamp", EditorForms.NOT_SET, "none",
                "dark_forest", "swamp"));
        assertEquals(List.of(new BiomeTuningService.Edit("grass_modifier", null)),
                EditorForms.changes(form, FakeAnswers.of(Map.of("grass_modifier", EditorForms.NOT_SET))));
    }

    @Test
    void missingOrForeignAnswersChangeNothing() {
        List<EditorView.Input> form = List.of(slider("fog_end", 1024, 0, 1024, 1),
                new EditorView.Toggle("precipitation", Component.text("Precipitation"), true),
                choice("grass_modifier", "none", "none", "swamp"));
        assertEquals(List.of(), EditorForms.changes(form, FakeAnswers.of(Map.of())));
        for (float bad : new float[] {Float.NaN, Float.POSITIVE_INFINITY}) {
            assertEquals(List.of(), EditorForms.changes(form, FakeAnswers.of(Map.of("fog_end", bad))), "slider " + bad);
        }
        assertEquals(List.of(new BiomeTuningService.Edit("fog_end", "0")),
                EditorForms.changes(form, FakeAnswers.of(Map.of("fog_end", -5000f))), "kept inside the range");
        assertEquals(List.of(), EditorForms.changes(form, FakeAnswers.of(Map.of("grass_modifier", "SWAMP"))),
                "not one of the options");
        assertEquals(List.of(), EditorForms.changes(form, FakeAnswers.of(Map.of("grass_modifier", EditorForms.NOT_SET))),
                "not_set is not offered here");
    }

    @Test
    void slidersLandOnTheirStepGrid() {
        assertEquals("0.35", EditorForms.number(0.35000002f, 0.05f));
        assertEquals("231", EditorForms.number(231f, 1f));
        assertEquals("2048", EditorForms.number(2047.9f, 16f));
        assertEquals("-8", EditorForms.number(-8f, 1f));
        assertEquals("-0.7", EditorForms.number(-0.7f, 0.05f));
        assertEquals("0", EditorForms.number(0f, 0.05f));
        float start = EditorForms.initial(0.9f, 0, 1, 0.05f);
        assertEquals("0.95", EditorForms.number(start + 0.05f, 0.05f), "one step, as the client computes it");
    }

    @Test
    void aScaledSliderWritesTheValueInItsOwnUnits() {
        EditorView.Slider downfall = new EditorView.Slider("downfall", Component.text("Downfall"), -1.25f, 101.25f, 5,
                80, EditorLayout.PERCENT, 100);
        List<EditorView.Input> form = List.of(downfall);
        assertEquals(List.of(new BiomeTuningService.Edit("downfall", "0.85")),
                EditorForms.changes(form, FakeAnswers.of(Map.of("downfall", 85f))), "85 % is 0.85");
        assertEquals(List.of(), EditorForms.changes(form, FakeAnswers.untouched(form)));
        assertEquals(List.of(new BiomeTuningService.Edit("downfall", "1")),
                EditorForms.changes(form, FakeAnswers.of(Map.of("downfall", 101.25f))), "the slack writes the end");
        assertEquals("-0.4", EditorForms.number(-40f, 5, 100));
        assertEquals("2", EditorForms.number(200f, 5, 100));
        assertEquals("0", EditorForms.number(0f, 5, 100));
    }

    @Test
    void aSliderStartsOnItsGridInsideItsRange() {
        assertEquals(192f, EditorForms.initial(192.33f, -64, 640, 1));
        assertEquals(4096f, EditorForms.initial(5000f, 0, 4096, 16));
        assertEquals(-64f, EditorForms.initial(-100f, -64, 640, 1));
        assertEquals(0.7f, EditorForms.initial(0.7f, -1, 2, 0.05f), 1e-6);
        assertEquals(18 * 0.05f, EditorForms.initial(0.9f, 0, 1, 0.05f),
                "float on purpose, like the client's steps: from exactly 0.9 the client can miss 0 by a hair");
        List<EditorView.Input> form = List.of(
                slider("downfall", EditorForms.initial(0.9f, 0, 1, 0.05f), 0, 1, 0.05f),
                slider("cloud_height", EditorForms.initial(192.33f, -64, 640, 1), -64, 640, 1));
        assertEquals(List.of(), EditorForms.changes(form, FakeAnswers.untouched(form)), "untouched starts are no edit");
    }
}
