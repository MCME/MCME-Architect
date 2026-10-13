package com.mcmiddleearth.architect.biomeTuning;

import com.mcmiddleearth.architect.biomeTuning.ColourMath.Hsb;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;

/**
 * The colour picker's pure parts: its inputs, the rulers printed above the sliders, the sky-to-horizon
 * blend, and how a click turns the hex box and the sliders into one colour. Colours are ARGB ints; an RGB value
 * carries alpha ff. The slider positions travel with the colour, because black and grey have no hue of their own:
 * re-reading them from the colour would lose the hue on the way through.
 */
final class ColourPicker {

    static final String HEX = "hex";
    static final String HUE = "hue";
    static final String SATURATION = "saturation";
    static final String BRIGHTNESS = "brightness";
    static final String ALPHA = "alpha";
    /** Where the picker starts when nothing better is known: opaque mid grey. */
    static final int UNSET_START = 0xff808080;
    /**
     * Cells per slider ruler. In {@link #CELL_FONT} █ is 5 px wide, so 59 cells span 295 of the 300 px sliders, and the
     * sliders come right after the rulers.
     */
    static final int RULER_CELLS = 59;
    /** Cells in the sky-to-horizon strip, which shares its line with its label. */
    static final int HORIZON_CELLS = 40;
    /**
     * The font every ruler and strip is drawn in: the unicode font alone, where █ is 5 px wide for every player. The
     * default font draws █ from its bitmap at 9 px (at 5 px only with Force Unicode Font on), so without this font a
     * 59-cell ruler would be 531 px for most players, and wrap.
     */
    static final Key CELL_FONT = Key.key("minecraft", "uniform");
    /**
     * What a biome that leaves a colour unset shows in the Overworld: the dimension's value (vanilla's
     * overworld.json), else the attribute's own 26.2 default. The rest start at grey: water colour is required, so
     * it is never unset, and unset grass and foliage colours come from the climate colour maps.
     */
    private static final Map<String, Integer> UNSET = Map.of("sky_color", 0xff78a7ff, "fog_color", 0xffc0d8ff,
            "cloud_color", 0xccffffff, "ambient_light_color", 0xff0a0a0a, "water_fog_color", 0xff050533,
            "sky_light_color", 0xffffffff, "block_light_tint", 0xffffd88c, "night_vision_color", 0xff999999);
    /** Whitespace, including pasted non-breaking spaces, and invisible format characters such as U+200B. */
    private static final Pattern BLANK = Pattern.compile("[\\s\\p{Z}\\p{Cf}]");
    private static final String CELL = "█";

    /** What a click asks for: the colour, and where the sliders stand. */
    record Pick(int colour, Hsb sliders) {
    }

    private ColourPicker() {
    }

    /** "#rrggbb", or "#aarrggbb" for a value with alpha. */
    static String text(boolean argb, int colour) {
        return argb ? ColourMath.hexArgb(colour) : ColourMath.hex(colour);
    }

    /** Where the picker starts when the biome leaves {@code property} unset: what the Overworld then shows. */
    static int unsetStart(TuningProperty property) {
        return UNSET.getOrDefault(property.name(), UNSET_START);
    }

    /**
     * The hue, saturation and brightness sliders at {@code hsb}, an alpha slider for ARGB values, then the hex box. The
     * sliders come first, so they sit right under the rulers that end the window's text.
     */
    static List<EditorView.Input> inputs(boolean argb, int colour, Hsb hsb) {
        List<EditorView.Input> inputs = new ArrayList<>();
        inputs.add(new EditorView.Slider(HUE, Component.text("Hue"), 0, 360, 1, hsb.hue(), EditorLayout.DEGREES));
        // the game's own percent label, which each client shows in its own language
        inputs.add(new EditorView.Slider(SATURATION, Component.text("Saturation"), 0, 100, 1, hsb.saturation(),
                EditorLayout.PERCENT));
        inputs.add(new EditorView.Slider(BRIGHTNESS, Component.text("Brightness"), 0, 100, 1, hsb.brightness(),
                EditorLayout.PERCENT));
        if (argb) {
            inputs.add(new EditorView.Slider(ALPHA, Component.text("Alpha (opacity)"), 0, 255, 1, colour >>> 24,
                    EditorLayout.PLAIN));
        }
        inputs.add(new EditorView.TextBox(HEX, Component.text(argb ? "Hex, alpha first: #aarrggbb (typing one "
                + "overrides the sliders)" : "Hex (typing one overrides the sliders)"), text(argb, colour), 9, false));
        return inputs;
    }

    /**
     * What a click asks for. A hex that differs from the one shown wins, and the sliders move to it: a typed 6- or
     * 3-digit hex keeps the alpha slider's value, an 8-digit one brings its own alpha, and an RGB value refuses 8
     * digits. Otherwise the sliders count, measured from where they stood ({@code sliders}); when they are untouched
     * the colour stays exactly as it was, because whole-number HSB cannot reach every RGB value. Answers are
     * unchecked client data: a blank box, or a missing or non-finite slider, means unchanged, and sliders are kept
     * inside their range.
     *
     * @throws BiomeTuningException when the typed hex is not a colour, with the reason for the builder
     */
    static Pick resolve(boolean argb, int colour, Hsb sliders, EditorView.Answers answers) throws BiomeTuningException {
        int alpha = argb ? whole(answers.number(ALPHA), colour >>> 24, 255) : 0xff;
        String typed = answers.text(HEX);
        String hex = typed == null ? "" : clean(typed);
        String digits = normal(hex);
        if (!digits.isEmpty() && !digits.equals(normal(text(argb, colour)))) {
            ColourMath.Typed parsed = ColourMath.parseHex(hex);
            if (parsed == null) {
                throw new BiomeTuningException("'" + hex + "' is not a colour: use "
                        + (argb ? "#aarrggbb or #rrggbb" : "#rrggbb or #rgb"));
            }
            if (parsed.alpha() >= 0 && !argb) {
                throw new BiomeTuningException("this colour has no alpha: use #rrggbb");
            }
            int chosen = (parsed.alpha() >= 0 ? parsed.alpha() : alpha) << 24 | parsed.rgb();
            return new Pick(chosen, slidersFor(chosen, sliders));
        }
        Hsb after = new Hsb(whole(answers.number(HUE), sliders.hue(), 360),
                whole(answers.number(SATURATION), sliders.saturation(), 100),
                whole(answers.number(BRIGHTNESS), sliders.brightness(), 100));
        int rgb = after.equals(sliders) ? colour & 0xffffff : ColourMath.toRgb(after);
        return new Pick(alpha << 24 | rgb, after);
    }

    /**
     * The sliders for a colour that arrived whole (typed, or copied from a biome). Black has no hue or saturation and
     * grey has no hue, so those keep what the sliders had.
     */
    static Hsb slidersFor(int colour, Hsb before) {
        Hsb hsb = ColourMath.toHsb(colour);
        if (hsb.brightness() == 0) {
            return new Hsb(before.hue(), before.saturation(), 0);
        }
        if (hsb.saturation() == 0) {
            return new Hsb(before.hue(), 0, hsb.brightness());
        }
        return hsb;
    }

    /** Four cells in the colour's RGB. */
    static Component swatch(int colour) {
        return Component.text(CELL.repeat(4), TextColor.color(colour & 0xffffff));
    }

    /** The ruler above the hue slider: hue 0 to 360 at full saturation and brightness. */
    static Component hueRuler() {
        List<Integer> colours = new ArrayList<>();
        for (int i = 0; i < RULER_CELLS; i++) {
            colours.add(ColourMath.toRgb(new Hsb(step(i, 360), 100, 100)));
        }
        return cells(colours);
    }

    /** The ruler above the saturation slider: 0 to 100 % at this hue and brightness. */
    static Component saturationRuler(Hsb hsb) {
        List<Integer> colours = new ArrayList<>();
        for (int i = 0; i < RULER_CELLS; i++) {
            colours.add(ColourMath.toRgb(new Hsb(hsb.hue(), step(i, 100), hsb.brightness())));
        }
        return cells(colours);
    }

    /** The ruler above the brightness slider: 0 to 100 % at this hue and saturation. */
    static Component brightnessRuler(Hsb hsb) {
        List<Integer> colours = new ArrayList<>();
        for (int i = 0; i < RULER_CELLS; i++) {
            colours.add(ColourMath.toRgb(new Hsb(hsb.hue(), hsb.saturation(), step(i, 100))));
        }
        return cells(colours);
    }

    /** Cells blending the sky colour into the fog colour, roughly how the two meet at the horizon. */
    static Component horizon(int sky, int fog) {
        List<Integer> colours = new ArrayList<>();
        for (int i = 0; i < HORIZON_CELLS; i++) {
            colours.add(ColourMath.lerp(sky, fog, i / (HORIZON_CELLS - 1.0)));
        }
        return cells(colours);
    }

    /** Cell {@code i}'s share of {@code max}, so the first cell is 0 and the last is {@code max}. */
    private static int step(int i, int max) {
        return (int) Math.round(i * (double) max / (RULER_CELLS - 1));
    }

    private static Component cells(List<Integer> colours) {
        return Component.empty().font(CELL_FONT).children(colours.stream()
                .map(rgb -> Component.text(CELL, TextColor.color(rgb & 0xffffff)))
                .toList());
    }

    /** A slider answer as a whole number in 0..max; a missing or non-finite answer means the slider did not move. */
    private static int whole(Float value, int fallback, int max) {
        return value == null || !Float.isFinite(value) ? fallback : Math.clamp(Math.round(value), 0, max);
    }

    /** Typed text without whitespace or invisible characters, which pasting can bring along. */
    private static String clean(String typed) {
        return BLANK.matcher(typed).replaceAll("");
    }

    private static String normal(String hex) {
        String text = hex.toLowerCase(Locale.ROOT);
        return text.startsWith("#") ? text.substring(1) : text;
    }
}
