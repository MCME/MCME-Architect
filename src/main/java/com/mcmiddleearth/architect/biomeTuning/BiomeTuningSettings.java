package com.mcmiddleearth.architect.biomeTuning;

import org.bukkit.configuration.ConfigurationSection;

import java.util.function.Consumer;
import java.util.regex.Pattern;

/** The biomeTuning section of config.yml, and its defaults. */
public record BiomeTuningSettings(int undoDepth, int backupsPerBiome, long refreshCooldownMillis,
                                  long refreshTimeoutMillis, int publishPlayersPerSecond, long publishCooldownMillis,
                                  SpareIds spares, String targetPack) {

    public static final BiomeTuningSettings DEFAULTS = new BiomeTuningSettings(20, 20, 5_000, 15_000, 5, 30_000,
            SpareIds.DEFAULT, "bukkit");
    /**
     * A pack folder's name: letters, digits, _, - and ., but not dots alone. Spares and labels are written inside the
     * world's datapacks folder, never outside it.
     */
    private static final Pattern PACK = Pattern.compile("[A-Za-z0-9_.-]+");

    /**
     * Reads {@code section}, which may be null; a missing or non-positive value falls back to the default. A spare
     * pattern or pack name that cannot be used also falls back to the default, and {@code warnings} hears why.
     */
    public static BiomeTuningSettings from(ConfigurationSection section, Consumer<String> warnings) {
        if (section == null) {
            return DEFAULTS;
        }
        String pattern = section.getString("sparePattern");
        SpareIds spares = SpareIds.parse(pattern).orElse(null);
        if (spares == null) {
            if (pattern != null) {
                warnings.accept("biomeTuning.sparePattern '" + pattern + "' is not a namespace, a colon, a prefix "
                        + "and 1 to 9 # for the number, in lower-case letters, digits, _, - and . (and / in the "
                        + "prefix), so " + SpareIds.DEFAULT.pattern() + " applies");
            }
            spares = SpareIds.DEFAULT;
        }
        String pack = section.getString("targetPack");
        if (pack == null || !PACK.matcher(pack).matches() || pack.chars().allMatch(c -> c == '.')) {
            if (pack != null) {
                warnings.accept("biomeTuning.targetPack '" + pack + "' is not a pack folder's name (letters, "
                        + "digits, _, - and ., not dots alone), so " + DEFAULTS.targetPack() + " applies");
            }
            pack = DEFAULTS.targetPack();
        }
        return new BiomeTuningSettings(
                positive(section.getInt("undoDepth"), DEFAULTS.undoDepth()),
                positive(section.getInt("backupsPerBiome"), DEFAULTS.backupsPerBiome()),
                positive(section.getInt("refresh.cooldownSeconds"), (int) (DEFAULTS.refreshCooldownMillis() / 1000)) * 1000L,
                positive(section.getInt("refresh.timeoutSeconds"), (int) (DEFAULTS.refreshTimeoutMillis() / 1000)) * 1000L,
                positive(section.getInt("refresh.publishPlayersPerSecond"), DEFAULTS.publishPlayersPerSecond()),
                positive(section.getInt("refresh.publishCooldownSeconds"), (int) (DEFAULTS.publishCooldownMillis() / 1000)) * 1000L,
                spares, pack);
    }

    private static int positive(int value, int fallback) {
        return value > 0 ? value : fallback;
    }
}
