package com.mcmiddleearth.architect.mapLayers.customBiomes;

import com.google.gson.JsonElement;
import com.mcmiddleearth.architect.biomeTuning.BiomeDocument;
import com.mcmiddleearth.architect.biomeTuning.BiomeTuning;
import com.mcmiddleearth.architect.biomeTuning.BiomeTuningException;
import com.mcmiddleearth.architect.biomeTuning.ColourMath;
import com.mcmiddleearth.architect.biomeTuning.TuningProperties;
import com.mcmiddleearth.architect.biomeTuning.TuningProperty;
import com.mcmiddleearth.architect.biomeTuning.TuningProperty.Group;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import java.util.function.LongSupplier;
import org.bukkit.NamespacedKey;

/**
 * The facts of the click window, read live from biome tuning: a biome's name, and the settings its definition sets,
 * grouped as the in-game editor groups them. Like biome tuning, it is for the main thread; what it returns is plain
 * data. An answer is never kept, so what is shown is live; a look-up that failed is left alone for a minute, as
 * {@link #reading} says.
 */
public final class TuningFacts implements BiomeFacts {

    /** How long a look-up that failed for a biome is left alone: finding out why costs a scan of the datapacks. */
    static final long FAILURE_RETRY_MILLIS = 60_000;

    private static final String SKY_AND_FOG = "Sky and fog";
    private static final String WATER_AND_PLANTS = "Water and plants";
    private static final String CLIMATE = "Climate";
    private static final String SOUND_AND_PARTICLES = "Sound and particles";
    /** The headings in the order the window shows them. */
    private static final List<String> HEADINGS = List.of(SKY_AND_FOG, WATER_AND_PLANTS, CLIMATE, SOUND_AND_PARTICLES);
    /** The heading each tuning group is shown under. Other groups (raw attribute ids) are not shown. */
    private static final Map<Group, String> HEADING_OF = new EnumMap<>(Group.class);
    private static final int DECIMALS = 3;

    static {
        HEADING_OF.put(Group.SKY, SKY_AND_FOG);
        HEADING_OF.put(Group.FOG, SKY_AND_FOG);
        HEADING_OF.put(Group.WATER, WATER_AND_PLANTS);
        HEADING_OF.put(Group.CLIMATE, CLIMATE);
        HEADING_OF.put(Group.AUDIO, SOUND_AND_PARTICLES);
        HEADING_OF.put(Group.PARTICLES, SOUND_AND_PARTICLES);
    }

    /** A look-up in biome tuning, which says why it cannot answer with a {@link BiomeTuningException}. */
    @FunctionalInterface
    interface Lookup<T> {
        T get(NamespacedKey biome) throws BiomeTuningException;
    }

    private final Function<String, Optional<String>> labels;
    private final Function<String, BiomeDocument> documents;
    private final BooleanSupplier available;
    /** Run when biome tuning is found off, to forget what failed while it was on. */
    private final Runnable forgetFailures;

    /**
     * @param labels the name of a biome by its id; null instead of an Optional means the labels cannot be read
     * @param documents the working copy of a biome's definition by its id, or null when there is none to read
     * @param available whether biome tuning is on: when it is not, neither of the others is asked
     */
    TuningFacts(Function<String, Optional<String>> labels, Function<String, BiomeDocument> documents,
                BooleanSupplier available) {
        this(labels, documents, available, () -> { });
    }

    private TuningFacts(Function<String, Optional<String>> labels, Function<String, BiomeDocument> documents,
                        BooleanSupplier available, Runnable forgetFailures) {
        this.labels = labels;
        this.documents = documents;
        this.available = available;
        this.forgetFailures = forgetFailures;
    }

    /** The facts of the biome tuning running on this server, which has nothing to say while it is off. */
    static TuningFacts fromBiomeTuning() {
        return reading(key -> BiomeTuning.spares().labels().label(key), key -> BiomeTuning.service().document(key),
                BiomeTuning::isAvailable, System::currentTimeMillis);
    }

    /**
     * Facts from these look-ups, with {@code clock} giving epoch milliseconds. A look-up that fails with a
     * {@link BiomeTuningException} (a labels file that cannot be read, a biome no datapack defines) gives null, and so
     * does an id that is not a key.
     * <p>
     * A failure is remembered for {@link #FAILURE_RETRY_MILLIS}, for that biome and that look-up alone, and the look-up
     * is not asked again until then. Biome tuning keeps nothing of a failure, so each new ask would cost the same
     * again: a scan of the datapacks folder that opens every zip pack, or a new reading of the labels file. A biome in
     * a zip pack, defined twice or not at all, or a broken labels file, would cost that for every shown biome at every
     * redraw. What failed is forgotten whenever biome tuning is found off, as it may come back as a new service. An
     * answer is never remembered, so what is shown stays live. Main thread only.
     */
    static TuningFacts reading(Lookup<Optional<String>> labels, Lookup<BiomeDocument> documents,
                               BooleanSupplier available, LongSupplier clock) {
        Remembering<Optional<String>> names = new Remembering<>(labels, clock);
        Remembering<BiomeDocument> definitions = new Remembering<>(documents, clock);
        return new TuningFacts(names::get, definitions::get, available, () -> {
            names.forget();
            definitions.forget();
        });
    }

    /** A look-up that remembers for which biomes it failed, and until when. It never keeps an answer. */
    private static final class Remembering<T> {

        private final Lookup<T> lookup;
        private final LongSupplier clock;
        /** The epoch millisecond until which a biome is not asked about. */
        private final Map<String, Long> failedUntil = new HashMap<>();

        private Remembering(Lookup<T> lookup, LongSupplier clock) {
            this.lookup = lookup;
            this.clock = clock;
        }

        /** The answer, or null for none: a failure now or within the last minute, or an id that is not a key. */
        T get(String biome) {
            Long until = failedUntil.get(biome);
            if (until != null) {
                if (clock.getAsLong() < until) {
                    return null;
                }
                failedUntil.remove(biome);
            }
            NamespacedKey key = NamespacedKey.fromString(biome);
            if (key == null) {
                return null;
            }
            try {
                return lookup.get(key);
            } catch (BiomeTuningException e) {
                failedUntil.put(biome, clock.getAsLong() + FAILURE_RETRY_MILLIS);
                return null;
            }
        }

        void forget() {
            failedUntil.clear();
        }
    }

    /** Whether biome tuning is on. Found off, it forgets what failed: it may come back as a new service. */
    private boolean on() {
        if (available.getAsBoolean()) {
            return true;
        }
        forgetFailures.run();
        return false;
    }

    @Override
    public String label(String biome) {
        if (!on()) {
            return null;
        }
        Optional<String> label = labels.apply(biome);
        return label == null ? null : label.orElse(null);
    }

    @Override
    public Settings settings(String biome) {
        if (!on()) {
            return null;
        }
        BiomeDocument document = documents.apply(biome);
        if (document == null) {
            return null;
        }
        // every heading from the start, so that they come out in display order whatever order the catalogue has
        Map<String, List<Setting>> groups = new LinkedHashMap<>();
        HEADINGS.forEach(heading -> groups.put(heading, new ArrayList<>()));
        for (TuningProperty property : TuningProperties.all()) {
            String heading = HEADING_OF.get(property.group());
            JsonElement value = document.value(property);
            if (heading != null && value != null && !value.isJsonNull()) {
                groups.get(heading).add(setting(property, value));
            }
        }
        groups.values().removeIf(List::isEmpty);
        return new Settings(groups, document.isUnsaved());
    }

    private static Setting setting(TuningProperty property, JsonElement value) {
        String name = nameOf(property);
        return switch (property.kind()) {
            case RGB -> colour(name, value, false);
            case ARGB -> colour(name, value, true);
            case NUMBER -> plain(name, rounded(value), value);
            case BOOLEAN -> plain(name, onOrOff(value), value);
            case CHOICE -> plain(name, choice(value), value);
            case JSON -> new Setting(name, summary(property, value), null);
        };
    }

    /** {@code precipitation} is {@code rain}; the others lose a trailing {@code _color} and their underscores. */
    private static String nameOf(TuningProperty property) {
        String name = property.name();
        if (name.equals("precipitation")) {
            return "rain";
        }
        if (name.endsWith("_color")) {
            name = name.substring(0, name.length() - "_color".length());
        }
        return name.replace('_', ' ');
    }

    /**
     * A colour as {@code #rrggbb}, or {@code #aarrggbb} for a setting with alpha, with its RGB for the swatch. Anything
     * that is not a hex string (the game also reads an int) is shown as it is, without a swatch, as the editor does.
     */
    private static Setting colour(String name, JsonElement value, boolean withAlpha) {
        Integer argb = ColourMath.argb(value);
        if (argb == null) {
            return new Setting(name, TuningProperties.display(value), null);
        }
        return new Setting(name, withAlpha ? ColourMath.hexArgb(argb) : ColourMath.hex(argb), argb & 0xffffff);
    }

    /** A setting of a plain kind: {@code shown}, or the value as it is written when it is not in that kind's shape. */
    private static Setting plain(String name, String shown, JsonElement value) {
        return new Setting(name, shown != null ? shown : TuningProperties.display(value), null);
    }

    /** A number to {@value #DECIMALS} decimals without trailing zeros, such as 0.8 or 8; null if it is no number. */
    private static String rounded(JsonElement value) {
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) {
            return null;
        }
        // read as a double: rounding 1e999999999 as an exact decimal would build a number a billion digits long on the
        // main thread, where as a double it is only infinity
        double number = value.getAsDouble();
        if (!Double.isFinite(number)) {
            return null;
        }
        return BigDecimal.valueOf(number).setScale(DECIMALS, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();
    }

    private static String onOrOff(JsonElement value) {
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isBoolean()) {
            return null;
        }
        return value.getAsBoolean() ? "on" : "off";
    }

    private static String choice(JsonElement value) {
        return value.isJsonPrimitive() && value.getAsJsonPrimitive().isString() ? value.getAsString() : null;
    }

    /** The one-line summary of a structured setting. One this window has no summary for is {@code custom}. */
    private static String summary(TuningProperty property, JsonElement value) {
        return switch (property.name()) {
            case "music" -> ScapeSummary.music(value);
            case "ambient_sounds" -> ScapeSummary.ambientSounds(value);
            case "ambient_particles" -> ScapeSummary.ambientParticles(value);
            case "dripstone_particle" -> ScapeSummary.dripstone(value);
            default -> ScapeSummary.CUSTOM;
        };
    }
}
