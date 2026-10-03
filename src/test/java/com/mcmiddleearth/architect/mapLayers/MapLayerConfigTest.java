package com.mcmiddleearth.architect.mapLayers;

import org.bukkit.Color;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class MapLayerConfigTest {

    private static MapLayerConfig.Layer layer(YamlConfiguration config, String key, boolean predatesMapLayers) {
        return new MapLayerConfig(config).layer(key, predatesMapLayers);
    }

    /** The config.yml Architect ships, which a server's own config.yml has as its defaults. */
    private static YamlConfiguration shipped() throws IOException {
        try (var in = new InputStreamReader(MapLayerConfigTest.class.getResourceAsStream("/config.yml"),
                StandardCharsets.UTF_8)) {
            return YamlConfiguration.loadConfiguration(in);
        }
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

    // As the old utils read it: a dynmap section without 'enabled' was off. On a server, a config.yml without a
    // dynmap section is read with the shipped config.yml as its defaults, whose dynmap section Bukkit hands out
    // empty: off as well. Only a config without defaults, as in a unit test, has no dynmap section at all; the old
    // utils then wrote one with enabled: true.
    @Test
    void theOlderLayersAreOnOnlyWhereTheOldUtilsHadThemOn() throws Exception {
        YamlConfiguration off = new YamlConfiguration();
        off.set("dynmap.enabled", false);
        YamlConfiguration silent = new YamlConfiguration();
        silent.set("dynmap.hide", true);
        YamlConfiguration server = new YamlConfiguration(); // a server's config.yml without a dynmap section
        server.setDefaults(shipped()); // as JavaPlugin.reloadConfig() sets them

        assertFalse(layer(off, "rpRegions", true).enabled());
        assertFalse(layer(off, "itemBlockLimit", true).enabled());
        assertFalse(layer(silent, "rpRegions", true).enabled(), "a dynmap section without 'enabled' was off");
        assertFalse(layer(server, "rpRegions", true).enabled(),
                "the shipped defaults give an empty dynmap section: off, as the old utils had it");
        assertTrue(layer(new YamlConfiguration(), "rpRegions", true).enabled(),
                "without any defaults, the old utils wrote a dynmap section with enabled: true");
    }

    // Not a block: true or false switches the layer and leaves every other setting at its default, and any other
    // value counts as not set.
    @Test
    void aLayerSetToJustTrueOrFalseIsSwitchedByIt() throws Exception {
        YamlConfiguration config = new YamlConfiguration();
        config.loadFromString("""
                dynmap: {enabled: true, hide: false}
                mapLayers: {rpRegions: off, itemBlockLimit: true}
                """);
        YamlConfiguration quoted = new YamlConfiguration();
        quoted.loadFromString("""
                dynmap: {enabled: true, hide: false}
                mapLayers: {rpRegions: 'false'}
                """);

        assertFalse(layer(config, "rpRegions", true).enabled(),
                "YAML reads off as false, and the dynmap section, which says enabled, is not read");
        assertTrue(layer(config, "itemBlockLimit", true).enabled());
        assertTrue(layer(config, "itemBlockLimit", true).hidden(), "the default, not the dynmap section's hide");
        assertTrue(layer(quoted, "rpRegions", true).legacy(), "a string is no switch: as if not set");
    }

    // The worst case: isConfigurationSection() and isBoolean() fall back to the defaults, which reloadConfig() sets
    // from the shipped config.yml. So a live mapLayers block for these layers there would make every existing server
    // drop its dynmap settings: that is why the shipped file has only a commented example.
    @Test
    void anExistingConfigKeepsItsDynmapSettingsDespiteTheShippedDefaults() throws Exception {
        YamlConfiguration existing = new YamlConfiguration();
        existing.set("dynmap.enabled", true);
        existing.set("dynmap.hide", false);
        existing.setDefaults(shipped());
        existing.options().copyDefaults(true);

        MapLayerConfig.Layer rp = layer(existing, "rpRegions", true);

        assertTrue(rp.legacy(), "the shipped config.yml must not define mapLayers");
        assertFalse(rp.hidden());
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
