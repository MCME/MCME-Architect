package com.mcmiddleearth.architect.biomeTuning;

import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.mcmiddleearth.architect.biomeTuning.ColourMath.Hsb;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ColourMathTest {

    @Test
    void pureColoursConvertExactly() {
        assertEquals(new Hsb(0, 100, 100), ColourMath.toHsb(0xff0000));
        assertEquals(new Hsb(120, 100, 100), ColourMath.toHsb(0x00ff00));
        assertEquals(new Hsb(240, 100, 100), ColourMath.toHsb(0x0000ff));
        assertEquals(new Hsb(0, 0, 100), ColourMath.toHsb(0xffffff));
        assertEquals(new Hsb(0, 0, 0), ColourMath.toHsb(0x000000));
        assertEquals(0xff0000, ColourMath.toRgb(new Hsb(0, 100, 100)));
        assertEquals(0x0000ff, ColourMath.toRgb(new Hsb(240, 100, 100)));
        assertEquals(0x808080, ColourMath.toRgb(new Hsb(0, 0, 50)));
    }

    @Test
    void theBlue2040ffIs231Degrees() {
        assertEquals(new Hsb(231, 87, 100), ColourMath.toHsb(0x2040ff));
        assertEquals(new Hsb(231, 87, 100), ColourMath.toHsb(0xff2040ff),
                "alpha is ignored, even when it makes the int negative");
        assertEquals(0x2142ff, ColourMath.toRgb(new Hsb(231, 87, 100)), "whole-number HSB lands next to it, not on it");
    }

    @Test
    void hueWrapsAround() {
        assertEquals(0xff0000, ColourMath.toRgb(new Hsb(360, 100, 100)));
        assertEquals(330, ColourMath.toHsb(0xff0080).hue());
        assertEquals(0, ColourMath.toHsb(0xff0001).hue(), "359.8 degrees rounds to 360, which is 0");
        assertEquals(ColourMath.toRgb(new Hsb(359, 100, 100)), ColourMath.toRgb(new Hsb(-1, 100, 100)));
        assertEquals(0xff0000, ColourMath.toRgb(new Hsb(0, 150, 150)), "saturation and brightness are clamped");
    }

    @Test
    void aRoundTripStaysWithinThreeOfEveryChannel() {
        // 3 is the exact worst case over all 16.7 million colours; this grid reaches it too
        for (int r = 0; r < 256; r += 17) {
            for (int g = 0; g < 256; g += 17) {
                for (int b = 0; b < 256; b += 17) {
                    int rgb = r << 16 | g << 8 | b;
                    int back = ColourMath.toRgb(ColourMath.toHsb(rgb));
                    for (int shift = 0; shift <= 16; shift += 8) {
                        assertTrue(Math.abs((rgb >> shift & 0xff) - (back >> shift & 0xff)) <= 3,
                                ColourMath.hex(rgb) + " came back as " + ColourMath.hex(back));
                    }
                }
            }
        }
    }

    @Test
    void hexTextComesInThreeForms() {
        assertEquals(new ColourMath.Typed(0x77aaff, -1), ColourMath.parseHex("#7af"));
        assertEquals(new ColourMath.Typed(0x2040ff, -1), ColourMath.parseHex(" 2040FF "));
        assertEquals(new ColourMath.Typed(0xff0000, 0x80), ColourMath.parseHex("#80ff0000"));
        assertNull(ColourMath.parseHex("#12"));
        assertNull(ColourMath.parseHex("#gggggg"));
        assertNull(ColourMath.parseHex(null));
        assertEquals("#2040ff", ColourMath.hex(0xff2040ff));
        assertEquals("#802040ff", ColourMath.hexArgb(0x802040ff));
    }

    @Test
    void jsonColoursReadAsArgb() {
        assertEquals(0xff2040ff, ColourMath.argb(new JsonPrimitive("#2040ff")));
        assertEquals(0x80ff0000, ColourMath.argb(new JsonPrimitive("#80ff0000")));
        assertNull(ColourMath.argb(new JsonPrimitive(0.7)));
        assertNull(ColourMath.argb(new JsonObject()));
        assertNull(ColourMath.argb(null));
    }

    @Test
    void lerpBlendsEachChannel() {
        assertEquals(0x808080, ColourMath.lerp(0x000000, 0xffffff, 0.5));
        assertEquals(0x2040ff, ColourMath.lerp(0x2040ff, 0xc0d8ff, 0));
        assertEquals(0xc0d8ff, ColourMath.lerp(0x2040ff, 0xc0d8ff, 1));
        assertEquals(0x2040ff, ColourMath.lerp(0x2040ff, 0xc0d8ff, -1), "t is kept to 0..1");
        assertEquals(0xc0d8ff, ColourMath.lerp(0x2040ff, 0xc0d8ff, 2));
        assertEquals(0x2040ff, ColourMath.lerp(0xff2040ff, 0xffc0d8ff, 0), "the result is RGB only");
    }
}
