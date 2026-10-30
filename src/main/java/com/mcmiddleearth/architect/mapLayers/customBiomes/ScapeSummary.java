package com.mcmiddleearth.architect.mapLayers.customBiomes;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * A biome's music, ambient sounds and particles as one short line each, for the click window. Their JSON is long and
 * only the game's codec checks it, so a line gives what a builder wants to know (which sound or particle, how often)
 * and anything that is not in the shape the game writes is called {@code custom}. The lines are plain text: the window
 * escapes them.
 */
public final class ScapeSummary {

    /** What a value is called when it is not in a shape this class reads. */
    static final String CUSTOM = "custom";

    /** The music entries read when there is no {@code default} one, in the order they are tried. */
    private static final List<String> OTHER_MUSIC = List.of("underwater", "creative");
    private static final int TICKS_PER_SECOND = 20;

    private ScapeSummary() {
    }

    /**
     * The track and how long the game waits between tracks: {@code <sound>, every <min> to <max> s}. It is the
     * {@code default} entry, or else the first of {@code underwater} and {@code creative}, which is then named
     * first, as in {@code underwater: <sound>, every ...}.
     */
    static String music(JsonElement music) {
        JsonObject entries = object(music);
        if (entries == null) {
            return CUSTOM;
        }
        String named = "";
        JsonElement entry = entries.get("default");
        if (entry == null) {
            for (String other : OTHER_MUSIC) {
                if (entries.has(other)) {
                    entry = entries.get(other);
                    named = other + ": ";
                    break;
                }
            }
        }
        JsonObject track = object(entry);
        if (track == null) {
            return CUSTOM;
        }
        String sound = sound(track.get("sound"));
        Long shortest = seconds(track.get("min_delay"));
        Long longest = seconds(track.get("max_delay"));
        if (sound == null || shortest == null || longest == null) {
            return CUSTOM;
        }
        return named + sound + ", every " + shortest + " to " + longest + " s";
    }

    /** The sounds a biome plays: {@code loop <id>}, {@code mood <id>} and {@code extra <id>}, or {@code none}. */
    static String ambientSounds(JsonElement ambient) {
        JsonObject sounds = object(ambient);
        if (sounds == null) {
            return CUSTOM;
        }
        if (sounds.size() == 0) {
            return "none";
        }
        List<String> parts = new ArrayList<>();
        // the loop is a sound itself; the mood and the additions, which are shown as "extra", are objects holding one
        if (sounds.has("loop") && !add(parts, "loop", sound(sounds.get("loop")))) {
            return CUSTOM;
        }
        if (sounds.has("mood") && !add(parts, "mood", soundIn(sounds.get("mood")))) {
            return CUSTOM;
        }
        if (sounds.has("additions") && !add(parts, "extra", soundIn(sounds.get("additions")))) {
            return CUSTOM;
        }
        return parts.isEmpty() ? CUSTOM : String.join(", ", parts);
    }

    /** The particles that drift around: {@code <type> <chance>%} for each, or {@code none}. */
    static String ambientParticles(JsonElement ambient) {
        if (ambient == null || !ambient.isJsonArray()) {
            return CUSTOM;
        }
        JsonArray particles = ambient.getAsJsonArray();
        if (particles.isEmpty()) {
            return "none";
        }
        List<String> parts = new ArrayList<>();
        for (JsonElement element : particles) {
            JsonObject entry = object(element);
            String type = entry == null ? null : particleType(entry.get("particle"));
            Double probability = entry == null ? null : number(entry.get("probability"));
            if (type == null || probability == null) {
                return CUSTOM;
            }
            // a chance is stored as 0 to 1; shown as a percent with two decimals, 0.005 is 0.5% and 0.1181 is 11.81%
            parts.add(type + " " + plain(BigDecimal.valueOf(probability).movePointRight(2), 2) + "%");
        }
        return String.join(", ", parts);
    }

    /** The particle that drips from dripstone: its type. */
    static String dripstone(JsonElement particle) {
        String type = particleType(particle);
        return type == null ? CUSTOM : type;
    }

    /** Adds {@code label id} to {@code parts}; false when there is no id to add. */
    private static boolean add(List<String> parts, String label, String id) {
        if (id == null) {
            return false;
        }
        parts.add(label + " " + id);
        return true;
    }

    /** The id of a sound, written as a string or as {@code {"sound_id": id}}, or null. */
    private static String sound(JsonElement sound) {
        JsonObject inline = object(sound);
        return inline == null ? text(sound) : text(inline.get("sound_id"));
    }

    /** The id of the sound held by a mood or additions object, or null. */
    private static String soundIn(JsonElement holder) {
        JsonObject object = object(holder);
        return object == null ? null : sound(object.get("sound"));
    }

    /** The type of a particle, which is an object with a {@code type}, or null. */
    private static String particleType(JsonElement particle) {
        JsonObject object = object(particle);
        return object == null ? null : text(object.get("type"));
    }

    /** A number of ticks as whole seconds, or null when it is not a number. */
    private static Long seconds(JsonElement ticks) {
        Double number = number(ticks);
        return number == null ? null : Math.round(number / TICKS_PER_SECOND);
    }

    /** {@code number} rounded to {@code decimals} places, in plain digits, without trailing zeros: 0.50 is 0.5. */
    private static String plain(BigDecimal number, int decimals) {
        return number.setScale(decimals, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();
    }

    /**
     * A JSON number as a double, or null for anything else, and for a number too big for a double. A double on
     * purpose: rounding 1e999999999 as an exact decimal would build a number a billion digits long on the main
     * thread, where as a double it is only infinity.
     */
    private static Double number(JsonElement element) {
        if (element == null || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isNumber()) {
            return null;
        }
        double number = element.getAsDouble();
        return Double.isFinite(number) ? number : null;
    }

    private static JsonObject object(JsonElement element) {
        return element != null && element.isJsonObject() ? element.getAsJsonObject() : null;
    }

    private static String text(JsonElement element) {
        if (element == null || !element.isJsonPrimitive()) {
            return null;
        }
        JsonPrimitive primitive = element.getAsJsonPrimitive();
        return primitive.isString() ? primitive.getAsString() : null;
    }
}
