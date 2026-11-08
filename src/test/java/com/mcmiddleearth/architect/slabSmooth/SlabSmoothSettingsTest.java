package com.mcmiddleearth.architect.slabSmooth;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SlabSmoothSettingsTest {

    private static YamlConfiguration yaml(String text) throws Exception {
        YamlConfiguration config = new YamlConfiguration();
        config.loadFromString(text);
        return config;
    }

    @Test
    void aMissingSectionGivesTheDefaults() {
        List<String> warnings = new ArrayList<>();
        assertSame(SlabSmoothSettings.DEFAULTS, SlabSmoothSettings.from(null, warnings::add));
        SlabSmoothSettings d = SlabSmoothSettings.DEFAULTS;
        assertEquals("Human", d.defaultPack());
        assertTrue(d.download());
        assertEquals(List.of("Terra"), d.terrainCategories());
        assertEquals(List.of("Plant", "Crop"), d.plantCategories());
        assertEquals(10, d.lookTolerance());
        assertEquals("", d.clientJar());
        assertEquals(12, d.maxBrushRadius());
        assertEquals(65536, d.maxColumns());
        assertEquals(8_000_000L, d.maxCells());
        assertEquals(120, d.maxSeconds());
        assertTrue(d.overrides().isEmpty());
        assertTrue(d.defaults().isEmpty());
        assertTrue(warnings.isEmpty());
    }

    @Test
    void anEmptySectionGivesTheDefaultsWithoutWarnings() throws Exception {
        List<String> warnings = new ArrayList<>();
        SlabSmoothSettings s = SlabSmoothSettings.from(yaml("a: 1").createSection("x"), warnings::add);
        assertEquals(SlabSmoothSettings.DEFAULTS, s);
        assertTrue(warnings.isEmpty(), warnings.toString());
    }

    @Test
    void valuesAreRead() throws Exception {
        List<String> warnings = new ArrayList<>();
        SlabSmoothSettings s = SlabSmoothSettings.from(yaml("""
                defaultPack: Elven
                download: false
                terrainCategories: [Terra, Rock]
                plantCategories: []
                lookTolerance: 0
                vanilla:
                  clientJar: C:/mc/client.jar
                brush:
                  maxRadius: 20
                selection:
                  maxColumns: 1000
                  maxCells: 5000000000
                  maxSeconds: 30
                defaults:
                  band: 2
                  sigma: 1.5
                  mode: cut
                """), warnings::add);
        assertEquals("Elven", s.defaultPack());
        assertEquals(false, s.download());
        assertEquals(List.of("Terra", "Rock"), s.terrainCategories());
        assertEquals(List.of(), s.plantCategories());
        assertEquals(0, s.lookTolerance());
        assertEquals("C:/mc/client.jar", s.clientJar());
        assertEquals(20, s.maxBrushRadius());
        assertEquals(1000, s.maxColumns());
        assertEquals(5_000_000_000L, s.maxCells());
        assertEquals(30, s.maxSeconds());
        assertEquals(Map.of("band", "2", "sigma", "1.5", "mode", "cut"), s.defaults());
        assertTrue(warnings.isEmpty(), warnings.toString());
    }

    @Test
    void aNonPositiveLimitFallsBackAndWarns() throws Exception {
        List<String> warnings = new ArrayList<>();
        SlabSmoothSettings s = SlabSmoothSettings.from(yaml("""
                brush:
                  maxRadius: 0
                selection:
                  maxColumns: -5
                  maxCells: lots
                  maxSeconds: 0.5
                lookTolerance: -1
                """), warnings::add);
        assertEquals(12, s.maxBrushRadius());
        assertEquals(65536, s.maxColumns());
        assertEquals(8_000_000L, s.maxCells());
        assertEquals(120, s.maxSeconds());
        assertEquals(10, s.lookTolerance());
        assertEquals(5, warnings.size(), warnings.toString());
        assertTrue(warnings.stream().anyMatch(w -> w.contains("brush.maxRadius")), warnings.toString());
        assertTrue(warnings.stream().anyMatch(w -> w.contains("selection.maxColumns")), warnings.toString());
        assertTrue(warnings.stream().anyMatch(w -> w.contains("selection.maxCells")), warnings.toString());
        assertTrue(warnings.stream().anyMatch(w -> w.contains("selection.maxSeconds")), warnings.toString());
        assertTrue(warnings.stream().anyMatch(w -> w.contains("lookTolerance")), warnings.toString());
    }

    @Test
    void aBadDefaultOptionIsLeftOutAndWarns() throws Exception {
        List<String> warnings = new ArrayList<>();
        SlabSmoothSettings s = SlabSmoothSettings.from(yaml("""
                defaults:
                  band: 2
                  band2: 1
                  sigma: 99
                  mode: sideways
                """), warnings::add);
        assertEquals(Map.of("band", "2"), s.defaults());
        assertEquals(3, warnings.size(), warnings.toString());
    }

    @Test
    void overridesAreRead() throws Exception {
        List<String> warnings = new ArrayList<>();
        SlabSmoothSettings s = SlabSmoothSettings.from(yaml("""
                overrides:
                  Human:
                    terrain: [moss, mud]
                    exclude: [lava_rock]
                    pairs:
                      grass: [grass_slab, grass_stairs]
                      sand: sand_slab
                  Elven:
                    exclude: [x]
                """), warnings::add);
        SlabSmoothSettings.PackOverride human = s.overrides().get("Human");
        assertEquals(Set.of("moss", "mud"), human.terrain());
        assertEquals(Set.of("lava_rock"), human.exclude());
        assertEquals(Map.of("grass", List.of("grass_slab", "grass_stairs"), "sand", List.of("sand_slab")), human.pairs());
        SlabSmoothSettings.PackOverride elven = s.overrides().get("Elven");
        assertEquals(Set.of(), elven.terrain());
        assertEquals(Set.of("x"), elven.exclude());
        assertEquals(Map.of(), elven.pairs());
        assertTrue(warnings.isEmpty(), warnings.toString());
    }
}
