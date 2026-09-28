package com.mcmiddleearth.architect.mapLayers;

import org.bukkit.Color;
import org.bukkit.configuration.Configuration;
import org.bukkit.configuration.ConfigurationSection;

/**
 * The mapLayers section of config.yml, one block per layer. The RP-region and item-block layers predate it: until
 * one of them has a block of its own there, it reads the older dynmap section instead, so an existing config keeps
 * its settings.
 */
public final class MapLayerConfig {

    private final Configuration config;

    public MapLayerConfig(Configuration config) {
        this.config = config;
    }

    public Layer layer(String key, boolean predatesMapLayers) {
        if (config.isSet("mapLayers." + key)) {
            return new Layer(config.getConfigurationSection("mapLayers." + key), false);
        }
        return new Layer(predatesMapLayers ? config.getConfigurationSection("dynmap") : null, predatesMapLayers);
    }

    /** One layer's settings; any setting that is missing has its default. */
    public record Layer(ConfigurationSection section, boolean legacy) {

        /** On unless switched off; in the older dynmap section, off unless it says enabled, as it always was. */
        public boolean enabled() {
            return section == null || section.getBoolean("enabled", !legacy);
        }

        public boolean hidden() {
            return section == null || section.getBoolean(legacy ? "hide" : "hidden", true);
        }

        /** A '#rrggbb' colour. */
        public int color(String key, int fallback) {
            String value = section == null ? null : section.getString(key);
            return value != null && value.matches("#[0-9a-fA-F]{6}") ? Integer.parseInt(value.substring(1), 16)
                    : fallback;
        }

        public int integer(String key, int fallback) {
            return section == null ? fallback : section.getInt(key, fallback);
        }

        public double number(String key, double fallback) {
            return section == null ? fallback : section.getDouble(key, fallback);
        }

        /** One colour for line and fill; the older dynmap section has a colour for each. */
        public MapShape.Style style(int color, int borderWidth, double borderOpacity, double areaOpacity) {
            if (legacy && section != null) {
                return new MapShape.Style(legacyColor("borderColor", color),
                        section.getDouble("borderOpacity", borderOpacity), section.getInt("borderWidth", borderWidth),
                        legacyColor("areaColor", color), section.getDouble("areaOpacity", areaOpacity));
            }
            int rgb = color("color", color);
            return new MapShape.Style(rgb, number("borderOpacity", borderOpacity), integer("borderWidth", borderWidth),
                    rgb, number("areaOpacity", areaOpacity));
        }

        /** The dynmap section stores Bukkit colour objects. */
        private int legacyColor(String key, int fallback) {
            Color value = section.getColor(key);
            return value == null ? fallback : value.asRGB();
        }
    }
}
