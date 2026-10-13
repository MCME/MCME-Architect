package com.mcmiddleearth.architect.biomeTuning;

import java.math.BigDecimal;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Where each tunable value sits in the editor and how it is edited: six sections and a label per value.
 * Numbers also get a slider range, a step, where the slider starts when the biome leaves the value unset (the
 * attribute's own 26.2 default), and a scale that keeps the numbers on the slider whole. Temperature and downfall are
 * required biome fields rather than attributes, so theirs is only a starting point. Values the Overworld's day and
 * moon cycle override are left out. Pure.
 */
public final class EditorLayout {

    public enum Section {
        SKY_AND_FOG("Sky & fog colours"),
        DISTANCES("Distances"),
        WATER_AND_VEGETATION("Water & vegetation"),
        LIGHT_AND_CELESTIAL("Light & celestial"),
        CLIMATE("Climate"),
        AUDIO_AND_PARTICLES("Audio & particles");

        private final String title;

        Section(String title) {
            this.title = title;
        }

        public String title() {
            return title;
        }
    }

    /**
     * One value in a section. The slider fields (min to scale) only mean something for numbers: min, max, step and
     * fallback are in the value's own units, and the slider shows them times {@code scale}.
     */
    public record Field(TuningProperty property, String label, float min, float max, float step, float fallback,
                        String format, float scale) {

        /** The value's name in running text: its label in lower case, without a closing note such as "(at least)". */
        public String name() {
            return label.replaceFirst(" \\([^()]*\\)$", "").toLowerCase(Locale.ROOT);
        }

        /**
         * A value given in its own units, as running text in the slider's units: times the scale, exactly, with its
         * format's unit. A downfall of 0.35 is "35%", and 0.37 is "37%" though its slider starts on 35; a fog end of 64
         * is "64 blocks", and a temperature of 0.25 is "25 (×100)".
         */
        public String inText(BigDecimal value) {
            return onSlider(value) + unit(format);
        }

        /**
         * As {@link #inText}, for text by the slider, whose label shows the scale: a temperature of 0.25 is "25", not
         * "25 (×100)". Units stay: a fog end of 64 is "64 blocks".
         */
        public String bySlider(BigDecimal value) {
            return format.equals(HUNDREDTHS) ? onSlider(value) : inText(value);
        }

        /** A value given in its own units as the slider shows it: times the scale, exactly. */
        private String onSlider(BigDecimal value) {
            return value.multiply(new BigDecimal(Float.toString(scale))).stripTrailingZeros().toPlainString();
        }
    }

    /** Slider label formats. The client uses an unknown translation key as the format itself. */
    static final String BLOCKS = "%s: %s blocks";
    static final String DEGREES = "%s: %s°";
    static final String PLAIN = "options.generic_value";
    /**
     * "Downfall: 80%", vanilla's own format, for values from 0 to 1 shown with scale 100. The client adds a slider's
     * steps in float, so 0.05 steps would print noise such as 0.14999998; whole numbers print exactly.
     */
    static final String PERCENT = "options.percent_value";
    /** "Temperature ×100: 70", for the one value outside 0 to 1 that moves in 0.05 steps; see {@link #PERCENT}. */
    static final String HUNDREDTHS = "%s ×100: %s";

    /**
     * Left out of the editor. The Overworld's day and moon timelines replace these: their tracks for them have no
     * modifier, which means override. So a biome's own value never shows there. /biometune set still takes them, for
     * dimensions without those timelines.
     */
    static final Set<String> OVERRIDDEN_IN_THE_OVERWORLD = Set.of("sunrise_color", "sun_angle", "moon_angle",
            "star_angle", "moon_phase");

    private static final Map<Section, List<Field>> FIELDS = new EnumMap<>(Section.class);

    static {
        section(Section.SKY_AND_FOG, value("sky_color", "Sky"), value("fog_color", "Fog"),
                value("cloud_color", "Clouds"));
        section(Section.DISTANCES,
                slider("fog_start", "Fog start", -64, 1024, 1, 0, BLOCKS),
                slider("fog_end", "Fog end", 0, 1024, 1, 1024, BLOCKS),
                slider("sky_fog_end", "Sky fog end", 0, 1024, 1, 512, BLOCKS),
                slider("cloud_fog_end", "Cloud fog end", 0, 4096, 16, 2048, BLOCKS),
                slider("water_fog_start", "Water fog start", -64, 64, 1, -8, BLOCKS),
                slider("water_fog_end", "Water fog end", 0, 256, 1, 96, BLOCKS),
                slider("cloud_height", "Cloud height", -64, 640, 1, 192.33f, PLAIN));
        section(Section.WATER_AND_VEGETATION, value("water_color", "Water"), value("water_fog_color", "Water fog"),
                value("grass_color", "Grass"), value("foliage_color", "Foliage"),
                value("dry_foliage_color", "Dry foliage"), value("grass_modifier", "Grass colour modifier"));
        section(Section.LIGHT_AND_CELESTIAL, value("sky_light_color", "Sky light"),
                value("ambient_light_color", "Ambient light"), value("block_light_tint", "Block light tint"),
                value("night_vision_color", "Night vision"),
                percent("sky_light_factor", "Sky light factor", 1),
                percent("star_brightness", "Star brightness (at least)", 0));
        section(Section.CLIMATE,
                slider("temperature", "Temperature", -1, 2, 0.05f, 0.5f, HUNDREDTHS, 100),
                percent("downfall", "Downfall", 0.5f),
                value("precipitation", "Precipitation"), value("temperature_modifier", "Temperature modifier"));
        section(Section.AUDIO_AND_PARTICLES, value("music", "Music"), value("ambient_sounds", "Ambient sounds"),
                value("ambient_particles", "Ambient particles"), value("dripstone_particle", "Dripstone particle"),
                percent("music_volume", "Music volume", 1),
                value("firefly_bush_sounds", "Firefly bush sounds (also by day)"));
    }

    private EditorLayout() {
    }

    public static List<Field> fields(Section section) {
        return FIELDS.get(section);
    }

    /**
     * The field for a value, or null when the editor leaves it out: a raw attribute id, or one of
     * {@link #OVERRIDDEN_IN_THE_OVERWORLD}.
     */
    public static Field field(TuningProperty property) {
        for (List<Field> fields : FIELDS.values()) {
            for (Field field : fields) {
                if (field.property().equals(property)) {
                    return field;
                }
            }
        }
        return null;
    }

    /**
     * The section that holds a value, or null when the editor leaves it out: a raw attribute id, or one of
     * {@link #OVERRIDDEN_IN_THE_OVERWORLD}.
     */
    public static Section sectionOf(TuningProperty property) {
        for (Map.Entry<Section, List<Field>> entry : FIELDS.entrySet()) {
            if (entry.getValue().stream().anyMatch(field -> field.property().equals(property))) {
                return entry.getKey();
            }
        }
        return null;
    }

    /**
     * What running text puts after a slider's number: the unit its label format shows, or for HUNDREDTHS, whose ×100
     * is in the label, " (×100)". PLAIN has none.
     */
    private static String unit(String format) {
        return switch (format) {
            case BLOCKS -> " blocks";
            case DEGREES -> "°";
            case PERCENT -> "%";
            case HUNDREDTHS -> " (×100)";
            default -> "";
        };
    }

    private static void section(Section section, Field... fields) {
        FIELDS.put(section, List.of(fields));
    }

    private static Field value(String name, String label) {
        return new Field(property(name), label, 0, 0, 0, 0, PLAIN, 1);
    }

    private static Field slider(String name, String label, float min, float max, float step, float fallback,
                                String format) {
        return slider(name, label, min, max, step, fallback, format, 1);
    }

    private static Field slider(String name, String label, float min, float max, float step, float fallback,
                                String format, float scale) {
        return new Field(property(name), label, min, max, step, fallback, format, scale);
    }

    /** A value from 0 to 1 in 0.05 steps, shown as 0 to 100 %. */
    private static Field percent(String name, String label, float fallback) {
        return slider(name, label, 0, 1, 0.05f, fallback, PERCENT, 100);
    }

    private static TuningProperty property(String name) {
        return TuningProperties.resolve(name)
                .orElseThrow(() -> new IllegalStateException("not in the catalogue: " + name));
    }
}
