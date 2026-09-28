package com.mcmiddleearth.architect.mapLayers;

import org.bukkit.Color;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MapLayerConfigTest {

    private static MapLayerConfig.Layer layer(YamlConfiguration config, String key, boolean predatesMapLayers) {
        return new MapLayerConfig(config).layer(key, predatesMapLayers);
    }

    @Test
    void withNothingSetALayerIsOnHiddenAndInItsDefaultColour() {
        MapLayerConfig.Layer layer = layer(new YamlConfiguration(), "noPhysics", false);

        assertTrue(layer.enabled());
        assertTrue(layer.hidden());
        assertEquals(0x1e64ff, layer.color("waterColor", 0x1e64ff));
        assertEquals(50, layer.integer("warnPercent", 50));
        assertEquals(new MapShape.Style(0x800080, 0.15, 2, 0x800080, 0.25), layer.style(0x800080, 2, 0.15, 0.25));
    }

    @Test
    void theMapLayersSectionIsRead() throws Exception {
        YamlConfiguration config = new YamlConfiguration();
        config.loadFromString("""
                mapLayers:
                  rpRegions: {enabled: false, hidden: false, color: '#123456', borderWidth: 3,
                              borderOpacity: 0.5, areaOpacity: 0.1}
                  itemBlockBudget: {warnPercent: 40}
                """);

        MapLayerConfig.Layer rp = layer(config, "rpRegions", true);
        assertFalse(rp.enabled());
        assertFalse(rp.hidden());
        assertEquals(new MapShape.Style(0x123456, 0.5, 3, 0x123456, 0.1), rp.style(0x800080, 2, 0.15, 0.25));
        assertEquals(40, layer(config, "itemBlockBudget", false).integer("warnPercent", 50));
        assertTrue(layer(config, "noPhysics", false).enabled(), "a layer missing from the section keeps its defaults");
    }

    @Test
    void withoutMapLayersTheOlderLayersReadTheDynmapSection() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("dynmap.enabled", true);
        config.set("dynmap.hide", false);
        config.set("dynmap.borderColor", Color.fromRGB(0x112233));
        config.set("dynmap.areaColor", Color.fromRGB(0x445566));
        config.set("dynmap.borderWidth", 4);
        config.set("dynmap.borderOpacity", 0.3);
        config.set("dynmap.areaOpacity", 0.6);

        MapLayerConfig.Layer rp = layer(config, "rpRegions", true);

        assertTrue(rp.enabled());
        assertFalse(rp.hidden(), "the old key is 'hide'");
        assertEquals(new MapShape.Style(0x112233, 0.3, 4, 0x445566, 0.6), rp.style(0x800080, 2, 0.15, 0.25));
        assertTrue(layer(config, "noPhysics", false).hidden(), "a new layer never reads the old section");
    }

    @Test
    void aLayerWithItsOwnBlockNoLongerReadsTheDynmapSection() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("dynmap.enabled", false);
        config.set("mapLayers.rpRegions.color", "#123456");
        config.set("mapLayers.itemBlockBudget.warnPercent", 40);

        assertTrue(layer(config, "rpRegions", true).enabled(), "its own block, where enabled defaults to on");
        assertFalse(layer(config, "itemBlockLimit", true).enabled(),
                "a block for another layer leaves this one on the dynmap section");
    }

    // As the old utils read it: a dynmap section without 'enabled' was off, and a missing dynmap section was made
    // with enabled: true.
    @Test
    void theOlderLayersAreOnOnlyWhereTheOldUtilsHadThemOn() {
        YamlConfiguration off = new YamlConfiguration();
        off.set("dynmap.enabled", false);
        YamlConfiguration silent = new YamlConfiguration();
        silent.set("dynmap.hide", true);

        assertFalse(layer(off, "rpRegions", true).enabled());
        assertFalse(layer(off, "itemBlockLimit", true).enabled());
        assertFalse(layer(silent, "rpRegions", true).enabled(), "a dynmap section without 'enabled' was off");
        assertTrue(layer(new YamlConfiguration(), "rpRegions", true).enabled(), "no dynmap section at all was on");
    }

    @Test
    void aColourThatIsNotHexKeepsTheDefault() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("mapLayers.noPhysics.waterColor", "blue");
        config.set("mapLayers.noPhysics.redstoneColor", "#12345z");

        assertEquals(0x1e64ff, layer(config, "noPhysics", false).color("waterColor", 0x1e64ff));
        assertEquals(0xff8c00, layer(config, "noPhysics", false).color("redstoneColor", 0xff8c00));
    }
}
