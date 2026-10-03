package com.mcmiddleearth.architect.biomeTuning;

import java.util.ArrayList;
import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class EditorViewTest {

    private static final EditorView.Button OK = new EditorView.Button(Component.text("OK"), null, (player, answers) -> {
    });

    @Test
    void aSliderMustStartInsideItsRange() {
        assertThrows(IllegalArgumentException.class,
                () -> new EditorView.Slider("fog_end", Component.text("Fog end"), 0, 1024, 1, 2000, "%s: %s blocks"));
        assertDoesNotThrow(
                () -> new EditorView.Slider("fog_end", Component.text("Fog end"), 0, 1024, 1, 1024, "%s: %s blocks"));
        for (float start : new float[] {-1, Float.NaN, -0.0f}) {
            assertThrows(IllegalArgumentException.class,
                    () -> new EditorView.Slider("fog_end", Component.text("Fog end"), 0, 1024, 1, start, "%s"),
                    "Paper refuses a start of " + start);
        }
        for (float step : new float[] {0, -1, Float.NaN, Float.POSITIVE_INFINITY}) {
            assertThrows(IllegalArgumentException.class,
                    () -> new EditorView.Slider("fog_end", Component.text("Fog end"), 0, 1024, step, 1, "%s"),
                    "Paper refuses a step of " + step);
        }
        assertThrows(IllegalArgumentException.class,
                () -> new EditorView.Slider("fog_end", Component.text("Fog end"), 5, 5, 1, 5, "%s"), "not a range");
        assertEquals(1f, new EditorView.Slider("fog_end", Component.text("Fog end"), 0, 1024, 1, 1, "%s").scale(),
                "a slider shows its value as it is, unless it says otherwise");
        for (float scale : new float[] {0, -100, Float.NaN, Float.POSITIVE_INFINITY}) {
            assertThrows(IllegalArgumentException.class,
                    () -> new EditorView.Slider("downfall", Component.text("Downfall"), 0, 100, 5, 80, "%s", scale),
                    "a scale of " + scale);
        }
    }

    @Test
    void buttonsSitInTwoOrThreeColumns() {
        assertEquals(2, new EditorView(Component.text("t"), List.of(), List.of(), List.of(OK), null).columns());
        assertEquals(3, new EditorView(Component.text("t"), List.of(), List.of(), List.of(OK), null, 3).columns());
        for (int columns : new int[] {0, 1, 4}) {
            assertThrows(IllegalArgumentException.class,
                    () -> new EditorView(Component.text("t"), List.of(), List.of(), List.of(OK), null, columns),
                    columns + " columns");
        }
    }

    @Test
    void theTextIsOneBlockOfLines() {
        EditorView window = new EditorView(Component.text("t"), List.of(Component.text("a"), Component.text("b")),
                List.of(), List.of(OK), null);
        assertEquals("a\nb", PlainTextComponentSerializer.plainText().serialize(window.text()),
                "one block: the client puts 18 px around each separate one");
        Component red = Component.text("a", NamedTextColor.RED);
        Component ruler = ColourPicker.hueRuler();
        EditorView styled = new EditorView(Component.text("t"), List.of(red, ruler), List.of(), List.of(OK), null);
        assertEquals(List.of(red, Component.newline(), ruler), styled.text().children(),
                "each line keeps its colours, and a ruler its font");
    }

    @Test
    void keysAreLettersDigitsAndUnderscores() {
        assertThrows(IllegalArgumentException.class, () -> new EditorView.Toggle("rain or snow", Component.text("x"), true));
        assertThrows(IllegalArgumentException.class, () -> new EditorView.Toggle("visual/fog", Component.text("x"), true));
        assertDoesNotThrow(() -> new EditorView.Toggle("firefly_bush_sounds", Component.text("x"), true));
        assertThrows(IllegalArgumentException.class, () -> new EditorView.Toggle("id", Component.text("x"), true),
                "Paper keeps the click's callback id under that key");
    }

    @Test
    void textMustFitItsLimitAndAChoiceStartsOnOneOption() {
        assertThrows(IllegalArgumentException.class,
                () -> new EditorView.TextBox("hex", Component.text("Hex"), "#2040ff00ff", 9, false));
        assertDoesNotThrow(() -> new EditorView.TextBox("hex", Component.text("Hex"), "#2040ff00", 9, false));
        assertThrows(IllegalArgumentException.class,
                () -> new EditorView.TextBox("hex", Component.text("Hex"), "", 0, false), "a limit below 1");
        assertThrows(IllegalArgumentException.class, () -> new EditorView.Choice("moon_phase", Component.text("Moon"),
                List.of(new EditorView.Option("a", Component.text("a"), true),
                        new EditorView.Option("b", Component.text("b"), true))));
        assertThrows(IllegalArgumentException.class, () -> new EditorView.Choice("moon_phase", Component.text("Moon"),
                List.of(new EditorView.Option("a", Component.text("a"), false))), "none selected");
        assertThrows(IllegalArgumentException.class, () -> new EditorView.Choice("grass", Component.text("Grass"),
                List.of(new EditorView.Option("none", Component.text("None"), true),
                        new EditorView.Option("none", null, false))), "two options share an id");
    }

    @Test
    void aWindowHasUniqueKeysAndAtLeastOneButton() {
        EditorView.Toggle toggle = new EditorView.Toggle("audio", Component.text("Audio"), true);
        assertThrows(IllegalArgumentException.class,
                () -> new EditorView(Component.text("t"), List.of(), List.of(toggle, toggle), List.of(OK), null));
        assertThrows(IllegalArgumentException.class,
                () -> new EditorView(Component.text("t"), List.of(), List.of(), List.of(), OK));
        List<EditorView.Input> inputs = new ArrayList<>(List.of(toggle));
        EditorView window = new EditorView(Component.text("t"), List.of(Component.text("line")), inputs, List.of(OK),
                null);
        inputs.clear();
        assertEquals(List.of(toggle), window.inputs(), "the window keeps its own copy");
        assertThrows(UnsupportedOperationException.class, () -> window.buttons().add(OK));
        assertNull(window.exit(), "the footer button is optional");
        assertThrows(NullPointerException.class, () -> new EditorView(null, List.of(), List.of(), List.of(OK), null));
        assertThrows(NullPointerException.class, () -> new EditorView.Button(Component.text("OK"), null, null));
        assertThrows(NullPointerException.class, () -> new EditorView.Toggle("audio", null, true));
        assertThrows(NullPointerException.class, () -> new EditorView.Option(null, Component.text("a"), true));
    }
}
