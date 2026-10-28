package com.mcmiddleearth.architect.mapLayers.customBiomes;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * What the click window says about a custom biome besides where it is painted: its name and its settings. They are
 * behind an interface so that the window and the shapes can be tested without biome tuning, which only runs on a
 * server. Both are asked on the main thread, where biome tuning's own state may be read; what comes back is plain data
 * that any thread may use.
 */
public interface BiomeFacts {

    /** The name builders gave the biome, or null when it has none. */
    String label(String biome);

    /**
     * The settings the biome's definition sets, as they are now, or null when they cannot be read: biome tuning is off,
     * or the biome has no file it can read.
     */
    Settings settings(String biome);

    /**
     * One setting as the window shows it.
     *
     * @param name a short name for builders, such as {@code fog start}
     * @param text the value in words and numbers, as plain text: the window escapes it
     * @param rgb the colour as {@code 0xRRGGBB} for a colour setting, so the window can draw a swatch, else null
     */
    record Setting(String name, String text, Integer rgb) {
    }

    /**
     * A biome's settings, and whether biome tuning holds changes to them that are not saved yet.
     *
     * @param groups the settings by heading, in the order the window shows them
     */
    record Settings(Map<String, List<Setting>> groups, boolean unsaved) {

        /** The groups are copied, in order, so that a result handed from one thread to another cannot change. */
        public Settings {
            Objects.requireNonNull(groups, "groups");
            Map<String, List<Setting>> copy = new LinkedHashMap<>();
            groups.forEach((heading, settings) -> copy.put(heading, List.copyOf(settings)));
            groups = Collections.unmodifiableMap(copy);
        }
    }
}
