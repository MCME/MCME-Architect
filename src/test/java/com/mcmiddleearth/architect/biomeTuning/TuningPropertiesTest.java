package com.mcmiddleearth.architect.biomeTuning;

import com.google.gson.JsonPrimitive;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TuningPropertiesTest {

    private static TuningProperty property(String name) {
        return TuningProperties.resolve(name).orElseThrow();
    }

    @Test
    void theCatalogueCoversEveryAtmosphereValue() {
        assertEquals(37, TuningProperties.all().size());
        assertEquals(37, TuningProperties.names().stream().distinct().count());
        assertEquals(List.of("attributes", "minecraft:visual/sky_color"), property("sky_color").path());
        assertEquals(List.of("effects", "water_color"), property("WATER_COLOR").path());
        assertEquals(List.of("has_precipitation"), property("precipitation").path());
    }

    @Test
    void fullAttributeIdsResolveToTheSameEntryOrToRawJson() {
        assertEquals("fog_color", property("minecraft:visual/fog_color").name());
        assertEquals("fog_color", property("visual/fog_color").name());
        TuningProperty gameplay = property("gameplay/water_evaporates");
        assertEquals(List.of("attributes", "minecraft:gameplay/water_evaporates"), gameplay.path());
        assertEquals(TuningProperty.Kind.JSON, gameplay.kind());
        assertTrue(TuningProperties.resolve("no_such_thing").isEmpty());
    }

    @Test
    void coloursAreNormalised() {
        assertEquals(new JsonPrimitive("#77aaff"), TuningProperties.parse(property("sky_color"), "#7AF"));
        assertEquals(new JsonPrimitive("#78a7ff"), TuningProperties.parse(property("sky_color"), "78A7FF"));
        assertEquals(new JsonPrimitive("#ff78a7ff"), TuningProperties.parse(property("cloud_color"), "#78a7ff"),
                "an ARGB colour without alpha is opaque");
        assertEquals(new JsonPrimitive("#8078a7ff"), TuningProperties.parse(property("cloud_color"), "#8078A7FF"));
        assertThrows(IllegalArgumentException.class, () -> TuningProperties.parse(property("sky_color"), "#8078a7ff"));
        assertThrows(IllegalArgumentException.class, () -> TuningProperties.parse(property("sky_color"), "blue"));
    }

    @Test
    void numbersBooleansAndChoices() {
        assertEquals(new JsonPrimitive(new BigDecimal("0.25")), TuningProperties.parse(property("downfall"), "0.25"));
        assertEquals(new JsonPrimitive(false), TuningProperties.parse(property("precipitation"), "off"));
        assertEquals(new JsonPrimitive("swamp"), TuningProperties.parse(property("grass_modifier"), "SWAMP"));
        assertThrows(IllegalArgumentException.class, () -> TuningProperties.parse(property("fog_end"), "far"));
        assertThrows(IllegalArgumentException.class, () -> TuningProperties.parse(property("moon_phase"), "blue_moon"));
        assertThrows(IllegalArgumentException.class, () -> TuningProperties.parse(property("sky_color"), "  "));
    }

    @Test
    void structuredValuesAreJson() {
        TuningProperty sounds = property("ambient_sounds");
        assertTrue(TuningProperties.parse(sounds, "{\"loop\":\"minecraft:ambient.basalt_deltas.loop\"}").isJsonObject());
        assertThrows(IllegalArgumentException.class, () -> TuningProperties.parse(sounds, "{\"loop\":"));
    }

    @Test
    void jsonThatDoesNotReadSaysWhatToCheck() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> TuningProperties.parse(property("ambient_particles"), "[{\"particle\":"));
        assertEquals("ambient_particles needs valid JSON: check its brackets, quotes and commas", e.getMessage());
    }

    @Test
    void everyCopyGroupHasMembers() {
        for (TuningProperty.Group group : TuningProperty.Group.values()) {
            if (group != TuningProperty.Group.OTHER) {
                assertFalse(TuningProperties.inGroup(group).isEmpty(), group.name());
            }
        }
    }

    @Test
    void displayShowsColoursAsHexAndMissingValuesPlainly() {
        assertEquals("#ffaa00", TuningProperties.display(new JsonPrimitive("#ffaa00")));
        assertEquals("0.7", TuningProperties.display(new JsonPrimitive(0.7)));
        assertEquals("(not set)", TuningProperties.display(null));
    }
}
