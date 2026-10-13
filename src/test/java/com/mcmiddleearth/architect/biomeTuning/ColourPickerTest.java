package com.mcmiddleearth.architect.biomeTuning;

import java.util.List;
import java.util.Map;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ColourPickerTest {

    private static List<Integer> colours(Component cells) {
        return cells.children().stream().map(cell -> cell.color().value()).toList();
    }

    private static FakeAnswers untouched(boolean argb, int colour) {
        return FakeAnswers.untouched(ColourPicker.inputs(argb, colour, ColourMath.toHsb(colour)));
    }

    /** A click from a picker whose sliders were placed from the colour itself. */
    private static int resolve(boolean argb, int colour, FakeAnswers answers) throws BiomeTuningException {
        return ColourPicker.resolve(argb, colour, ColourMath.toHsb(colour), answers).colour();
    }

    // the game's own "Saturation: 80%", which each client shows in its own language
    @Test
    void saturationAndBrightnessUseTheGamesPercentLabel() {
        List<String> formats = ColourPicker.inputs(false, 0xff336699, ColourMath.toHsb(0xff336699)).stream()
                .filter(input -> input.key().equals(ColourPicker.SATURATION)
                        || input.key().equals(ColourPicker.BRIGHTNESS))
                .map(input -> ((EditorView.Slider) input).format())
                .toList();
        assertEquals(List.of(EditorLayout.PERCENT, EditorLayout.PERCENT), formats);
    }

    @Test
    void rulersPrintExactColoursAsWideAsTheSliders() {
        assertEquals(59, ColourPicker.RULER_CELLS, "5 px each in the unicode font: 295 of the sliders' 300 px");
        List<Integer> hue = colours(ColourPicker.hueRuler());
        assertEquals(ColourPicker.RULER_CELLS, hue.size());
        assertEquals(0xff0000, hue.get(0));
        assertEquals(0x00ffff, hue.get(29), "180 degrees halfway");
        assertEquals(0xff0000, hue.get(58), "360 degrees at the end");
        ColourMath.Hsb blue = new ColourMath.Hsb(231, 87, 100);
        List<Integer> saturation = colours(ColourPicker.saturationRuler(blue));
        assertEquals(ColourPicker.RULER_CELLS, saturation.size());
        assertEquals(0xffffff, saturation.get(0));
        assertEquals(ColourMath.toRgb(new ColourMath.Hsb(231, 100, 100)), saturation.get(58));
        List<Integer> brightness = colours(ColourPicker.brightnessRuler(blue));
        assertEquals(ColourPicker.RULER_CELLS, brightness.size());
        assertEquals(0x000000, brightness.get(0));
        assertEquals(0x2142ff, brightness.get(58));
        for (Component cells : List.of(ColourPicker.hueRuler(), ColourPicker.saturationRuler(blue),
                ColourPicker.brightnessRuler(blue), ColourPicker.horizon(0xff2040ff, 0xffc0d8ff))) {
            assertEquals(ColourPicker.CELL_FONT, cells.font(),
                    "the unicode font alone: the default font's █ is 9 px for most players");
        }
        Component swatch = ColourPicker.swatch(0x802040ff);
        assertEquals(TextColor.color(0x2040ff), swatch.color(), "a swatch shows the RGB part");
        assertEquals("████", PlainTextComponentSerializer.plainText().serialize(swatch));
    }

    @Test
    void theHorizonBlendsSkyIntoFog() {
        List<Integer> horizon = colours(ColourPicker.horizon(0xff2040ff, 0xffc0d8ff));
        assertEquals(40, ColourPicker.HORIZON_CELLS, "the strip shares its line with its label");
        assertEquals(ColourPicker.HORIZON_CELLS, horizon.size());
        assertEquals(0x2040ff, horizon.get(0));
        assertEquals(0xc0d8ff, horizon.get(39));
    }

    @Test
    void theInputsStartAtTheColour() {
        List<EditorView.Input> rgb = ColourPicker.inputs(false, 0xff2040ff, ColourMath.toHsb(0xff2040ff));
        assertEquals(List.of("hue", "saturation", "brightness", "hex"),
                rgb.stream().map(EditorView.Input::key).toList(),
                "the sliders first, right under the rulers, and no alpha slider for an RGB value");
        assertEquals(231f, ((EditorView.Slider) rgb.get(0)).initial());
        assertEquals("#2040ff", ((EditorView.TextBox) rgb.get(3)).initial());
        List<EditorView.Input> argb = ColourPicker.inputs(true, 0xccffffff, ColourMath.toHsb(0xccffffff));
        assertEquals(List.of("hue", "saturation", "brightness", "alpha", "hex"),
                argb.stream().map(EditorView.Input::key).toList());
        assertEquals(204f, ((EditorView.Slider) argb.get(3)).initial());
        assertEquals("#ccffffff", ((EditorView.TextBox) argb.get(4)).initial());
        assertTrue(PlainTextComponentSerializer.plainText().serialize(argb.get(4).label()).contains("alpha first"));
        // the Overworld's own four, then the attributes' 26.2 defaults
        Map<String, Integer> starts = Map.of("sky_color", 0xff78a7ff, "fog_color", 0xffc0d8ff, "cloud_color",
                0xccffffff, "ambient_light_color", 0xff0a0a0a, "water_fog_color", 0xff050533, "sky_light_color",
                0xffffffff, "block_light_tint", 0xffffd88c, "night_vision_color", 0xff999999);
        starts.forEach((name, start) -> assertEquals(start,
                ColourPicker.unsetStart(TuningProperties.resolve(name).orElseThrow()), name));
        assertEquals(ColourPicker.UNSET_START,
                ColourPicker.unsetStart(TuningProperties.resolve("water_color").orElseThrow()), "water is required");
    }

    @Test
    void untouchedInputsKeepTheExactColour() throws Exception {
        assertEquals(0xff2040ff, resolve(false, 0xff2040ff, untouched(false, 0xff2040ff)));
        assertEquals(0xccffffff, resolve(true, 0xccffffff, untouched(true, 0xccffffff)));
        assertEquals(0xff2040ff, resolve(false, 0xff2040ff, FakeAnswers.of(Map.of())), "no answers at all");
    }

    @Test
    void movedSlidersMakeTheColour() throws Exception {
        FakeAnswers answers = untouched(false, 0xffffaa00).with("hue", 231f).with("saturation", 87f)
                .with("brightness", 100f);
        assertEquals(0xff2142ff, resolve(false, 0xffffaa00, answers));
        assertEquals(0x80ffffff, resolve(true, 0xccffffff, untouched(true, 0xccffffff).with("alpha", 128f)),
                "alpha alone keeps the RGB exact");
        ColourPicker.Pick full = ColourPicker.resolve(false, 0xff2040ff, ColourMath.toHsb(0xff2040ff),
                untouched(false, 0xff2040ff).with("hue", 360f));
        assertEquals(0xffff2121, full.colour(), "hue 360 is red again");
        assertDoesNotThrow(() -> ColourPicker.inputs(false, full.colour(), full.sliders()));
    }

    @Test
    void aChangedHexWinsOverTheSliders() throws Exception {
        ColourPicker.Pick typed = ColourPicker.resolve(false, 0xffffaa00, ColourMath.toHsb(0xffffaa00),
                untouched(false, 0xffffaa00).with("hex", "#2040FF").with("hue", 0f));
        assertEquals(0xff2040ff, typed.colour());
        assertEquals(new ColourMath.Hsb(231, 87, 100), typed.sliders(), "the sliders move to the typed colour");
        assertEquals(0xff77aaff, resolve(false, 0xffffaa00, untouched(false, 0xffffaa00).with("hex", "#7af")));
        assertEquals(0xff2040ff, resolve(false, 0xffffaa00,
                untouched(false, 0xffffaa00).with("hex", "#2040ff" + Character.toString(0xA0))),
                "a pasted non-breaking space");
        assertEquals(0xff2040ff, resolve(false, 0xffffaa00,
                untouched(false, 0xffffaa00).with("hex", Character.toString(0x200B) + "#2040ff")),
                "a pasted zero-width space");
        int sliders = 0xff000000 | ColourMath.toRgb(new ColourMath.Hsb(231, 100, 100));
        assertEquals(sliders, resolve(false, 0xffffaa00,
                        untouched(false, 0xffffaa00).with("hex", "FFAA00").with("hue", 231f)),
                "the same hex typed differently leaves the sliders in charge");
        assertEquals(sliders, resolve(false, 0xffffaa00,
                        untouched(false, 0xffffaa00).with("hex", "  ").with("hue", 231f)),
                "an emptied box is not a colour typed");
        assertEquals(sliders, resolve(false, 0xffffaa00,
                        untouched(false, 0xffffaa00).with("hex", "#").with("hue", 231f)),
                "nor is a box emptied down to its #");
    }

    @Test
    void theAlphaSliderOrAnEightDigitHexSetsTheAlpha() throws Exception {
        assertEquals(0x802040ff, resolve(true, 0xccffffff,
                untouched(true, 0xccffffff).with("hex", "#2040ff").with("alpha", 128f)));
        assertEquals(0x40ff0000, resolve(true, 0xccffffff,
                untouched(true, 0xccffffff).with("hex", "#40ff0000").with("alpha", 200f)), "typed alpha wins");
    }

    @Test
    void theSlidersKeepTheirHueThroughBlackAndGrey() throws Exception {
        ColourMath.Hsb blue = new ColourMath.Hsb(231, 87, 100);
        ColourPicker.Pick black = ColourPicker.resolve(false, 0xff2040ff, blue,
                FakeAnswers.untouched(ColourPicker.inputs(false, 0xff2040ff, blue)).with("brightness", 0f));
        assertEquals(0xff000000, black.colour());
        assertEquals(new ColourMath.Hsb(231, 87, 0), black.sliders(), "hue and saturation stay where they were");
        ColourPicker.Pick back = ColourPicker.resolve(false, black.colour(), black.sliders(),
                FakeAnswers.untouched(ColourPicker.inputs(false, black.colour(), black.sliders()))
                        .with("brightness", 100f));
        assertEquals(0xff2142ff, back.colour(), "the blue comes back, not white");
        FakeAnswers blueWindow = FakeAnswers.untouched(ColourPicker.inputs(false, 0xff2040ff, blue));
        assertEquals(new ColourMath.Hsb(231, 0, 50),
                ColourPicker.resolve(false, 0xff2040ff, blue, blueWindow.with("hex", "#808080")).sliders(),
                "a typed grey keeps the hue");
        assertEquals(new ColourMath.Hsb(231, 87, 0),
                ColourPicker.resolve(false, 0xff2040ff, blue, blueWindow.with("hex", "#000000")).sliders(),
                "a typed black keeps hue and saturation");
    }

    @Test
    void aBadHexIsRefusedWithTheReason() {
        BiomeTuningException error = assertThrows(BiomeTuningException.class,
                () -> resolve(false, 0xffffaa00, untouched(false, 0xffffaa00).with("hex", "#12")));
        assertTrue(error.getMessage().contains("not a colour"), error.getMessage());
        assertThrows(BiomeTuningException.class,
                () -> resolve(false, 0xffffaa00, untouched(false, 0xffffaa00).with("hex", "#80ffaa00")),
                "an RGB value takes no alpha");
    }

    @Test
    void hostileSliderAnswersStayInRange() throws Exception {
        for (float bad : new float[] {Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY}) {
            ColourPicker.Pick pick = ColourPicker.resolve(true, 0xccffffff, ColourMath.toHsb(0xccffffff),
                    untouched(true, 0xccffffff).with("hue", bad).with("brightness", bad).with("alpha", bad));
            assertEquals(0xccffffff, pick.colour(), "a non-finite answer means the slider did not move: " + bad);
        }
        ColourPicker.Pick far = ColourPicker.resolve(true, 0xccffffff, ColourMath.toHsb(0xccffffff),
                untouched(true, 0xccffffff).with("hue", 400f).with("saturation", -5f).with("brightness", 1e9f)
                        .with("alpha", 999f));
        assertEquals(new ColourMath.Hsb(360, 0, 100), far.sliders());
        assertEquals(0xffffffff, far.colour());
        assertDoesNotThrow(() -> ColourPicker.inputs(true, far.colour(), far.sliders()), "the window can be drawn again");
    }
}
