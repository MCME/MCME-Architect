package com.mcmiddleearth.architect.slabSmooth;

import com.mcmiddleearth.architect.slabSmooth.core.Options;
import org.bukkit.configuration.ConfigurationSection;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Consumer;

/**
 * The slabSmooth section of Architect's config.yml. A missing or bad value falls back to its default (a bad one is
 * reported); nothing here throws.
 *
 * @param defaultPack     the pack used where no RP region or player pack decides
 * @param download        whether packs and the vanilla client jar may be downloaded
 * @param clientJar       a vanilla client jar to read instead of downloading one; empty: download it
 * @param overrides       per pack: terrain and exclude lists (inventory keys) and the pieces a block always uses
 * @param maxBrushRadius  the largest brush radius
 * @param maxColumns      the most columns (with the margin) one selection may read
 * @param maxCells        the most blocks one smoothing may work on
 * @param maxSeconds      the longest one smoothing may run
 * @param defaults        option values every smoothing starts with, by option name
 */
public record SlabSmoothSettings(String defaultPack, boolean download, List<String> terrainCategories,
                                 List<String> plantCategories, double lookTolerance, String clientJar,
                                 Map<String, PackOverride> overrides, int maxBrushRadius, int maxColumns, long maxCells,
                                 long maxSeconds, Map<String, String> defaults) {

    /** What one pack's table gets on top of what its inventory says. */
    public record PackOverride(Set<String> terrain, Set<String> exclude, Map<String, List<String>> pairs) {
    }

    public static final SlabSmoothSettings DEFAULTS = new SlabSmoothSettings("Human", true, List.of("Terra"),
            List.of("Plant", "Crop"), 10, "", Map.of(), 12, 65536, 8_000_000L, 120, Map.of());

    /** Reads the section (null: all defaults). warnings gets one line per value that had to be left out. */
    public static SlabSmoothSettings from(ConfigurationSection section, Consumer<String> warnings) {
        if (section == null) {
            return DEFAULTS;
        }
        String defaultPack = DEFAULTS.defaultPack;
        if (section.contains("defaultPack")) {
            String value = section.getString("defaultPack", "");
            if (value == null || value.isBlank()) {
                warnings.accept("defaultPack must be a pack name; using " + defaultPack);
            } else {
                defaultPack = value.trim();
            }
        }
        boolean download = DEFAULTS.download;
        if (section.contains("download")) {
            if (section.get("download") instanceof Boolean b) {
                download = b;
            } else {
                warnings.accept("download must be true or false, not '" + section.get("download") + "'; using " + download);
            }
        }
        List<String> terrain = DEFAULTS.terrainCategories;
        if (section.isList("terrainCategories") && !section.getStringList("terrainCategories").isEmpty()) {
            terrain = List.copyOf(section.getStringList("terrainCategories"));
        } else if (section.contains("terrainCategories")) {
            warnings.accept("terrainCategories must be a list of inventory categories; using " + terrain);
        }
        List<String> plants = DEFAULTS.plantCategories; // an empty list is allowed: no plants
        if (section.isList("plantCategories")) {
            plants = List.copyOf(section.getStringList("plantCategories"));
        } else if (section.contains("plantCategories")) {
            warnings.accept("plantCategories must be a list of inventory categories; using " + plants);
        }
        double tolerance = DEFAULTS.lookTolerance;
        if (section.contains("lookTolerance")) {
            if (section.get("lookTolerance") instanceof Number n && n.doubleValue() >= 0 && Double.isFinite(n.doubleValue())) {
                tolerance = n.doubleValue();
            } else {
                warnings.accept("lookTolerance must be a number of 0 or more, not '" + section.get("lookTolerance") + "'; using " + tolerance);
            }
        }
        String clientJar = section.getString("vanilla.clientJar", "");
        clientJar = clientJar == null ? "" : clientJar.trim();
        return new SlabSmoothSettings(defaultPack, download, terrain, plants, tolerance, clientJar, overrides(section, warnings),
                (int) positive(section, "brush.maxRadius", DEFAULTS.maxBrushRadius, Integer.MAX_VALUE, warnings),
                (int) positive(section, "selection.maxColumns", DEFAULTS.maxColumns, Integer.MAX_VALUE, warnings),
                positive(section, "selection.maxCells", DEFAULTS.maxCells, Long.MAX_VALUE, warnings),
                positive(section, "selection.maxSeconds", DEFAULTS.maxSeconds, Long.MAX_VALUE, warnings),
                defaults(section, warnings));
    }

    /** A limit: missing falls back quietly; a number below 1, a fraction or text falls back with a warning. */
    private static long positive(ConfigurationSection section, String path, long fallback, long max, Consumer<String> warnings) {
        if (!section.contains(path)) {
            return fallback;
        }
        Object value = section.get(path);
        if (value instanceof Number n && n.doubleValue() == Math.rint(n.doubleValue()) && n.doubleValue() >= 1
                && n.doubleValue() <= max) {
            return n.longValue();
        }
        warnings.accept(path + " must be a whole number above 0, not '" + value + "'; using " + fallback);
        return fallback;
    }

    private static Map<String, PackOverride> overrides(ConfigurationSection section, Consumer<String> warnings) {
        ConfigurationSection all = section.getConfigurationSection("overrides");
        if (all == null) {
            return Map.of();
        }
        Map<String, PackOverride> out = new LinkedHashMap<>();
        for (String pack : all.getKeys(false)) {
            ConfigurationSection o = all.getConfigurationSection(pack);
            if (o == null) {
                warnings.accept("overrides." + pack + " must list terrain, exclude or pairs; left out");
                continue;
            }
            Map<String, List<String>> pairs = new TreeMap<>();
            ConfigurationSection p = o.getConfigurationSection("pairs");
            if (p != null) {
                for (String key : p.getKeys(false)) {
                    pairs.put(key, p.isList(key) ? List.copyOf(p.getStringList(key)) : List.of(p.getString(key, "")));
                }
            }
            out.put(pack, new PackOverride(Set.copyOf(o.getStringList("terrain")), Set.copyOf(o.getStringList("exclude")),
                    java.util.Collections.unmodifiableMap(pairs)));
        }
        return java.util.Collections.unmodifiableMap(out);
    }

    private static Map<String, String> defaults(ConfigurationSection section, Consumer<String> warnings) {
        ConfigurationSection d = section.getConfigurationSection("defaults");
        if (d == null) {
            return Map.of();
        }
        Map<String, String> out = new LinkedHashMap<>();
        for (String key : d.getKeys(false)) {
            String value = String.valueOf(d.get(key));
            try {
                new Options().set(key, value);
                out.put(key, value);
            } catch (IllegalArgumentException e) {
                warnings.accept("defaults." + key + ": " + e.getMessage() + "; left out");
            }
        }
        return java.util.Collections.unmodifiableMap(out);
    }
}
