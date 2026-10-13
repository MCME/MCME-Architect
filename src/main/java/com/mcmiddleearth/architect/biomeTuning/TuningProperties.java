package com.mcmiddleearth.architect.biomeTuning;

import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.mcmiddleearth.architect.biomeTuning.TuningProperty.Group;
import com.mcmiddleearth.architect.biomeTuning.TuningProperty.Kind;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * The catalogue of tunable biome values and the parsing of builder input into the JSON the 26.2 biome
 * codec expects. The codec still validates every edit; this only gives builders friendly input and messages.
 */
public final class TuningProperties {

    private static final Map<String, TuningProperty> BY_NAME = new LinkedHashMap<>();
    private static final Pattern HEX = Pattern.compile("#?([0-9a-fA-F]{3}|[0-9a-fA-F]{6}|[0-9a-fA-F]{8})");

    static {
        // sky, light and celestial
        attribute("sky_color", "visual/sky_color", Kind.RGB, Group.SKY);
        attribute("sunrise_color", "visual/sunrise_sunset_color", Kind.ARGB, Group.SKY);
        attribute("cloud_color", "visual/cloud_color", Kind.ARGB, Group.SKY);
        attribute("cloud_height", "visual/cloud_height", Kind.NUMBER, Group.SKY);
        attribute("sky_light_color", "visual/sky_light_color", Kind.RGB, Group.SKY);
        attribute("sky_light_factor", "visual/sky_light_factor", Kind.NUMBER, Group.SKY);
        attribute("ambient_light_color", "visual/ambient_light_color", Kind.RGB, Group.SKY);
        attribute("block_light_tint", "visual/block_light_tint", Kind.RGB, Group.SKY);
        attribute("night_vision_color", "visual/night_vision_color", Kind.RGB, Group.SKY);
        attribute("star_brightness", "visual/star_brightness", Kind.NUMBER, Group.SKY);
        attribute("sun_angle", "visual/sun_angle", Kind.NUMBER, Group.SKY);
        attribute("moon_angle", "visual/moon_angle", Kind.NUMBER, Group.SKY);
        attribute("star_angle", "visual/star_angle", Kind.NUMBER, Group.SKY);
        choice(List.of("attributes", "minecraft:visual/moon_phase"), "moon_phase", Group.SKY,
                "full_moon", "waning_gibbous", "third_quarter", "waning_crescent",
                "new_moon", "waxing_crescent", "first_quarter", "waxing_gibbous");
        // fog
        attribute("fog_color", "visual/fog_color", Kind.RGB, Group.FOG);
        attribute("fog_start", "visual/fog_start_distance", Kind.NUMBER, Group.FOG);
        attribute("fog_end", "visual/fog_end_distance", Kind.NUMBER, Group.FOG);
        attribute("sky_fog_end", "visual/sky_fog_end_distance", Kind.NUMBER, Group.FOG);
        attribute("cloud_fog_end", "visual/cloud_fog_end_distance", Kind.NUMBER, Group.FOG);
        // water and vegetation
        effect("water_color", "water_color", Kind.RGB);
        attribute("water_fog_color", "visual/water_fog_color", Kind.RGB, Group.WATER);
        attribute("water_fog_start", "visual/water_fog_start_distance", Kind.NUMBER, Group.WATER);
        attribute("water_fog_end", "visual/water_fog_end_distance", Kind.NUMBER, Group.WATER);
        effect("grass_color", "grass_color", Kind.RGB);
        effect("foliage_color", "foliage_color", Kind.RGB);
        effect("dry_foliage_color", "dry_foliage_color", Kind.RGB);
        choice(List.of("effects", "grass_color_modifier"), "grass_modifier", Group.WATER, "none", "dark_forest", "swamp");
        // climate
        add(new TuningProperty("temperature", List.of("temperature"), Kind.NUMBER, Group.CLIMATE, List.of()));
        add(new TuningProperty("downfall", List.of("downfall"), Kind.NUMBER, Group.CLIMATE, List.of()));
        add(new TuningProperty("precipitation", List.of("has_precipitation"), Kind.BOOLEAN, Group.CLIMATE, List.of()));
        choice(List.of("temperature_modifier"), "temperature_modifier", Group.CLIMATE, "none", "frozen");
        // audio and particles
        attribute("music", "audio/background_music", Kind.JSON, Group.AUDIO);
        attribute("music_volume", "audio/music_volume", Kind.NUMBER, Group.AUDIO);
        attribute("ambient_sounds", "audio/ambient_sounds", Kind.JSON, Group.AUDIO);
        attribute("firefly_bush_sounds", "audio/firefly_bush_sounds", Kind.BOOLEAN, Group.AUDIO);
        attribute("ambient_particles", "visual/ambient_particles", Kind.JSON, Group.PARTICLES);
        attribute("dripstone_particle", "visual/default_dripstone_particle", Kind.JSON, Group.PARTICLES);
    }

    private TuningProperties() {
    }

    public static List<TuningProperty> all() {
        return List.copyOf(BY_NAME.values());
    }

    public static List<String> names() {
        return List.copyOf(BY_NAME.keySet());
    }

    public static List<TuningProperty> inGroup(Group group) {
        return BY_NAME.values().stream().filter(property -> property.group() == group).toList();
    }

    /**
     * A short name from the catalogue, or any full attribute id ("minecraft:visual/fog_color" or "visual/fog_color").
     * An attribute id the catalogue doesn't know is treated as raw JSON.
     */
    public static Optional<TuningProperty> resolve(String nameOrId) {
        String key = nameOrId.trim().toLowerCase(Locale.ROOT);
        TuningProperty known = BY_NAME.get(key);
        if (known != null) {
            return Optional.of(known);
        }
        if (!key.contains("/")) {
            return Optional.empty();
        }
        List<String> path = List.of("attributes", key.contains(":") ? key : "minecraft:" + key);
        return Optional.of(BY_NAME.values().stream()
                .filter(property -> property.path().equals(path))
                .findFirst()
                .orElse(new TuningProperty(path.get(1), path, Kind.JSON, Group.OTHER, List.of())));
    }

    /** Turns builder input into the JSON value the codec expects; the exception's message is shown to the builder. */
    public static JsonElement parse(TuningProperty property, String raw) {
        String value = raw.trim();
        if (value.isEmpty()) {
            throw new IllegalArgumentException(property.name() + " needs a value");
        }
        return switch (property.kind()) {
            case RGB -> new JsonPrimitive(colour(property, value, false));
            case ARGB -> new JsonPrimitive(colour(property, value, true));
            case NUMBER -> {
                try {
                    yield new JsonPrimitive(new BigDecimal(value));
                } catch (NumberFormatException e) {
                    throw new IllegalArgumentException(property.name() + " needs a number, not '" + value + "'");
                }
            }
            case BOOLEAN -> switch (value.toLowerCase(Locale.ROOT)) {
                case "true", "on", "yes" -> new JsonPrimitive(true);
                case "false", "off", "no" -> new JsonPrimitive(false);
                default -> throw new IllegalArgumentException(property.name() + " needs true or false");
            };
            case CHOICE -> {
                String choice = value.toLowerCase(Locale.ROOT);
                if (!property.choices().contains(choice)) {
                    throw new IllegalArgumentException(property.name() + " must be one of "
                            + String.join(", ", property.choices()));
                }
                yield new JsonPrimitive(choice);
            }
            case JSON -> json(property, value);
        };
    }

    /** A short display form: strings as they are (colours are hex), everything else as compact JSON. */
    public static String display(JsonElement value) {
        if (value == null || value.isJsonNull()) {
            return "(not set)";
        }
        return value.isJsonPrimitive() && value.getAsJsonPrimitive().isString() ? value.getAsString() : value.toString();
    }

    private static String colour(TuningProperty property, String value, boolean argb) {
        if (!HEX.matcher(value).matches()) {
            throw new IllegalArgumentException(property.name() + " needs a colour like "
                    + (argb ? "#ffffffff (alpha first) or #ffffff" : "#78a7ff or #7af"));
        }
        String hex = (value.startsWith("#") ? value.substring(1) : value).toLowerCase(Locale.ROOT);
        if (hex.length() == 3) {
            hex = "" + hex.charAt(0) + hex.charAt(0) + hex.charAt(1) + hex.charAt(1) + hex.charAt(2) + hex.charAt(2);
        }
        if (hex.length() == 8 && !argb) {
            throw new IllegalArgumentException(property.name() + " has no alpha: use #rrggbb");
        }
        if (hex.length() == 6 && argb) {
            hex = "ff" + hex;
        }
        return "#" + hex;
    }

    private static JsonElement json(TuningProperty property, String value) {
        if (value.startsWith("#") && HEX.matcher(value).matches()) {
            return new JsonPrimitive(value.toLowerCase(Locale.ROOT));
        }
        try {
            return JsonParser.parseString(value);
        } catch (JsonParseException e) {
            throw new IllegalArgumentException(property.name()
                    + " needs valid JSON: check its brackets, quotes and commas");
        }
    }

    private static void attribute(String name, String attribute, Kind kind, Group group) {
        add(new TuningProperty(name, List.of("attributes", "minecraft:" + attribute), kind, group, List.of()));
    }

    private static void effect(String name, String key, Kind kind) {
        add(new TuningProperty(name, List.of("effects", key), kind, Group.WATER, List.of()));
    }

    private static void choice(List<String> path, String name, Group group, String... choices) {
        add(new TuningProperty(name, path, Kind.CHOICE, group, List.of(choices)));
    }

    private static void add(TuningProperty property) {
        BY_NAME.put(property.name(), property);
    }
}
