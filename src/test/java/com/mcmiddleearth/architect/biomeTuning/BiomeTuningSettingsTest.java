package com.mcmiddleearth.architect.biomeTuning;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class BiomeTuningSettingsTest {

    @Test
    void aMissingSectionGivesTheDefaults() {
        List<String> warnings = new ArrayList<>();
        assertEquals(BiomeTuningSettings.DEFAULTS, BiomeTuningSettings.from(null, warnings::add));
        assertEquals(List.of(), warnings);
        assertEquals(20, BiomeTuningSettings.DEFAULTS.undoDepth());
        assertEquals(5_000, BiomeTuningSettings.DEFAULTS.refreshCooldownMillis());
        assertEquals(15_000, BiomeTuningSettings.DEFAULTS.refreshTimeoutMillis());
        assertEquals(5, BiomeTuningSettings.DEFAULTS.publishPlayersPerSecond());
        assertEquals(30_000, BiomeTuningSettings.DEFAULTS.publishCooldownMillis());
        assertEquals(SpareIds.DEFAULT, BiomeTuningSettings.DEFAULTS.spares());
        assertEquals("bukkit", BiomeTuningSettings.DEFAULTS.targetPack());
    }

    @Test
    void readsValuesAndIgnoresNonsense() throws Exception {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.loadFromString("biomeTuning:\n  undoDepth: 5\n  backupsPerBiome: 0\n  refresh:\n"
                + "    cooldownSeconds: 2\n    publishPlayersPerSecond: 10\n");
        List<String> warnings = new ArrayList<>();
        BiomeTuningSettings settings = BiomeTuningSettings.from(yaml.getConfigurationSection("biomeTuning"),
                warnings::add);
        assertEquals(5, settings.undoDepth());
        assertEquals(20, settings.backupsPerBiome(), "0 is not a sensible value, so the default applies");
        assertEquals(2_000, settings.refreshCooldownMillis());
        assertEquals(15_000, settings.refreshTimeoutMillis());
        assertEquals(10, settings.publishPlayersPerSecond());
        assertEquals(30_000, settings.publishCooldownMillis());
        assertEquals(List.of(), warnings, "a value left out is no mistake: its default applies quietly");
    }

    @Test
    void readsTheSparePatternAndTheTargetPack() throws Exception {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.loadFromString("biomeTuning:\n  sparePattern: 'mymod:sky_##'\n  targetPack: mcme-biomes\n");
        List<String> warnings = new ArrayList<>();
        BiomeTuningSettings settings = BiomeTuningSettings.from(yaml.getConfigurationSection("biomeTuning"),
                warnings::add);
        assertEquals(new SpareIds("mymod", "sky_", 2), settings.spares());
        assertEquals("mcme-biomes", settings.targetPack());
        assertEquals(List.of(), warnings);
    }

    // spares add numbers new spares and writes them into the pack, so both must be something it can use
    @Test
    void aPatternOrPackItCannotUseFallsBackAndSaysSo() throws Exception {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.loadFromString("biomeTuning:\n  sparePattern: 'mcme:custom_.*'\n  targetPack: ../elsewhere\n");
        List<String> warnings = new ArrayList<>();
        BiomeTuningSettings settings = BiomeTuningSettings.from(yaml.getConfigurationSection("biomeTuning"),
                warnings::add);
        assertEquals(SpareIds.DEFAULT, settings.spares());
        assertEquals("bukkit", settings.targetPack());
        assertEquals(2, warnings.size(), warnings.toString());
        assertTrue(warnings.get(0).contains("mcme:custom_.*") && warnings.get(0).contains("lower-case letters"),
                "it says which patterns it takes: " + warnings);
        assertTrue(warnings.get(1).contains("../elsewhere") && warnings.get(1).contains("letters, digits"),
                "it says which names it takes: " + warnings);
    }

    // dots alone name this folder or the one above, and on Windows "..." is the datapacks folder itself
    @Test
    void aPackNameOfDotsAloneIsRefused() throws Exception {
        for (String dots : List.of(".", "..", "...")) {
            YamlConfiguration yaml = new YamlConfiguration();
            yaml.loadFromString("biomeTuning:\n  targetPack: '" + dots + "'\n");
            List<String> warnings = new ArrayList<>();
            BiomeTuningSettings settings = BiomeTuningSettings.from(yaml.getConfigurationSection("biomeTuning"),
                    warnings::add);
            assertEquals("bukkit", settings.targetPack(), dots);
            assertEquals(1, warnings.size(), dots + ": " + warnings);
        }
    }

    // the values the jar ships must read without a warning, or every server would log one at each start
    @Test
    void theShippedConfigReadsWithoutWarnings() throws Exception {
        YamlConfiguration yaml = new YamlConfiguration();
        try (InputStream shipped = getClass().getResourceAsStream("/config.yml")) {
            assertNotNull(shipped, "config.yml is on the test classpath");
            yaml.load(new InputStreamReader(shipped, StandardCharsets.UTF_8)); // throws on a YAML mistake
        }
        assertNotNull(yaml.getConfigurationSection("biomeTuning"), "the section is there, spelled right");
        List<String> warnings = new ArrayList<>();
        BiomeTuningSettings settings = BiomeTuningSettings.from(yaml.getConfigurationSection("biomeTuning"),
                warnings::add);
        assertEquals(List.of(), warnings);
        assertEquals(SpareIds.DEFAULT, settings.spares());
        assertEquals("bukkit", settings.targetPack());
    }
}
