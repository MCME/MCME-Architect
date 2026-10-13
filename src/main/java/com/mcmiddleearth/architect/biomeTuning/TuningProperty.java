package com.mcmiddleearth.architect.biomeTuning;

import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * One tunable value of a biome's datapack JSON: a short name for builders, where it lives in the JSON, how its
 * value is written, and which copy group it belongs to.
 */
public record TuningProperty(String name, List<String> path, Kind kind, Group group, List<String> choices) {

    /**
     * How the JSON value is written: RGB as "#rrggbb", ARGB as "#aarrggbb" (alpha first), NUMBER as a number, BOOLEAN
     * as true or false, CHOICE as one of {@code choices}, and JSON as any value, which only the game's codec checks.
     */
    public enum Kind { RGB, ARGB, NUMBER, BOOLEAN, CHOICE, JSON }

    /** The groups /biometune copy works with; OTHER is for raw attribute ids outside the catalogue. */
    public enum Group {
        SKY, FOG, WATER, CLIMATE, AUDIO, PARTICLES, OTHER;

        /** Every group of a biome's look: what /biometune copy calls all, and what a claim copies. */
        public static final Set<Group> LOOK = Collections.unmodifiableSet(EnumSet.complementOf(EnumSet.of(OTHER)));
    }

    public boolean isColour() {
        return kind == Kind.RGB || kind == Kind.ARGB;
    }
}
