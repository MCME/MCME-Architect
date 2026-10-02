package com.mcmiddleearth.architect.biomeTuning;

import com.google.gson.JsonElement;
import java.util.regex.Pattern;

/**
 * Colour arithmetic for the editor's picker: HSB in whole degrees and percents, hex text, the colours in
 * biome JSON, and blends. Pure.
 */
public final class ColourMath {

    /**
     * Hue 0-360 degrees: toHsb gives 0-359, and 360, where the hue slider ends, is red again. Saturation and
     * brightness 0-100 percent.
     */
    public record Hsb(int hue, int saturation, int brightness) {
    }

    /** A colour typed as hex: its RGB, and its alpha when eight digits were typed (else -1). */
    public record Typed(int rgb, int alpha) {
    }

    private static final Pattern TYPED = Pattern.compile("[0-9a-fA-F]{3}|[0-9a-fA-F]{6}|[0-9a-fA-F]{8}");
    private static final Pattern IN_JSON = Pattern.compile("#([0-9a-fA-F]{6}|[0-9a-fA-F]{8})");

    private ColourMath() {
    }

    /** The HSB of a colour's RGB part, rounded to whole degrees and percents. */
    public static Hsb toHsb(int rgb) {
        int r = (rgb >> 16) & 0xff;
        int g = (rgb >> 8) & 0xff;
        int b = rgb & 0xff;
        int max = Math.max(r, Math.max(g, b));
        int delta = max - Math.min(r, Math.min(g, b));
        double hue;
        if (delta == 0) {
            hue = 0;
        } else if (max == r) {
            hue = 60 * ((g - b) / (double) delta);
        } else if (max == g) {
            hue = 60 * ((b - r) / (double) delta + 2);
        } else {
            hue = 60 * ((r - g) / (double) delta + 4);
        }
        int degrees = (int) Math.round(hue < 0 ? hue + 360 : hue) % 360;
        int saturation = max == 0 ? 0 : (int) Math.round(delta * 100.0 / max);
        return new Hsb(degrees, saturation, (int) Math.round(max * 100.0 / 255));
    }

    /** The RGB of an HSB colour; hue wraps around, saturation and brightness are clamped to 0-100. */
    public static int toRgb(Hsb hsb) {
        double sector = Math.floorMod(hsb.hue(), 360) / 60.0;
        double value = clamp(hsb.brightness()) / 100.0;
        double chroma = value * clamp(hsb.saturation()) / 100.0;
        double x = chroma * (1 - Math.abs(sector % 2 - 1));
        double m = value - chroma;
        double[] rgb = switch ((int) sector) {
            case 0 -> new double[] {chroma, x, 0};
            case 1 -> new double[] {x, chroma, 0};
            case 2 -> new double[] {0, chroma, x};
            case 3 -> new double[] {0, x, chroma};
            case 4 -> new double[] {x, 0, chroma};
            default -> new double[] {chroma, 0, x};
        };
        return channel(rgb[0] + m) << 16 | channel(rgb[1] + m) << 8 | channel(rgb[2] + m);
    }

    /** "#rrggbb" for the RGB part. */
    public static String hex(int rgb) {
        return String.format("#%06x", rgb & 0xffffff);
    }

    /** "#aarrggbb". */
    public static String hexArgb(int argb) {
        return String.format("#%08x", argb);
    }

    /**
     * "#rgb", "#rrggbb" or "#aarrggbb", with or without the "#" and surrounding spaces; null for anything else, null
     * included. {@code trim()} leaves non-breaking spaces, so pasted text is cleaned first (ColourPicker does).
     */
    public static Typed parseHex(String text) {
        if (text == null) {
            return null;
        }
        String hex = text.trim();
        if (hex.startsWith("#")) {
            hex = hex.substring(1);
        }
        if (!TYPED.matcher(hex).matches()) {
            return null;
        }
        if (hex.length() == 3) {
            hex = "" + hex.charAt(0) + hex.charAt(0) + hex.charAt(1) + hex.charAt(1) + hex.charAt(2) + hex.charAt(2);
        }
        long value = Long.parseLong(hex, 16);
        return hex.length() == 8 ? new Typed((int) (value & 0xffffff), (int) (value >>> 24)) : new Typed((int) value, -1);
    }

    /**
     * A colour in biome JSON as ARGB ("#rrggbb" gets alpha ff); null when the value is not a hex string. The game
     * also reads a colour written as an int or as three floats, but everything here writes hex, so those count as
     * not a plain colour: reading numbers as colours would give every number value a swatch.
     */
    public static Integer argb(JsonElement value) {
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) {
            return null;
        }
        String text = value.getAsString();
        if (!IN_JSON.matcher(text).matches()) {
            return null;
        }
        long parsed = Long.parseLong(text.substring(1), 16);
        return (int) (text.length() == 7 ? 0xff000000L | parsed : parsed);
    }

    /**
     * The RGB {@code t} of the way from {@code from} to {@code to}, channel by channel, with {@code t} kept to 0..1.
     * Alpha is dropped: the result's is 0.
     */
    public static int lerp(int from, int to, double t) {
        double share = Math.clamp(t, 0.0, 1.0);
        int rgb = 0;
        for (int shift = 16; shift >= 0; shift -= 8) {
            int a = (from >> shift) & 0xff;
            int b = (to >> shift) & 0xff;
            rgb |= (int) Math.round(a + (b - a) * share) << shift;
        }
        return rgb;
    }

    private static int clamp(int percent) {
        return Math.max(0, Math.min(100, percent));
    }

    private static int channel(double unit) {
        return (int) Math.round(unit * 255);
    }
}
