package com.mcmiddleearth.architect.biomeTuning;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class EditorLayoutTest {

    /** Sorted names, so a failure shows which values differ. */
    private static Set<String> names(List<TuningProperty> properties) {
        return properties.stream().map(TuningProperty::name).collect(Collectors.toCollection(TreeSet::new));
    }

    @Test
    void everyValueTheOverworldShowsHasExactlyOnePlace() {
        assertTrue(TuningProperties.names().containsAll(EditorLayout.OVERRIDDEN_IN_THE_OVERWORLD),
                "every left-out name is in the catalogue");
        List<TuningProperty> placed = new ArrayList<>();
        for (EditorLayout.Section section : EditorLayout.Section.values()) {
            EditorLayout.fields(section).forEach(field -> placed.add(field.property()));
        }
        List<TuningProperty> shown = TuningProperties.all().stream()
                .filter(property -> !EditorLayout.OVERRIDDEN_IN_THE_OVERWORLD.contains(property.name()))
                .toList();
        assertEquals(32, shown.size(), "the 37 catalogue values less the 5 the day and moon cycle override");
        assertEquals(names(shown), names(placed), "the editor places exactly the values the Overworld shows");
        assertEquals(shown.size(), placed.size(), "no value appears twice");
    }

    @Test
    void numberSlidersStartInRangeAndRangesSitOnTheirStepGrid() {
        for (EditorLayout.Section section : EditorLayout.Section.values()) {
            for (EditorLayout.Field field : EditorLayout.fields(section)) {
                String name = field.property().name();
                if (field.property().kind() != TuningProperty.Kind.NUMBER) {
                    continue;
                }
                assertTrue(field.min() < field.max() && field.step() > 0, name);
                assertTrue(field.fallback() >= field.min() && field.fallback() <= field.max(), name);
                for (float end : new float[] {field.min(), field.max()}) {
                    float steps = end / field.step();
                    assertEquals(Math.round(steps), steps, 1e-3, name + ": " + end + " is off its step grid");
                }
            }
        }
    }

    @Test
    void everyValueHasANameForRunningText() {
        assertEquals("fog end", nameOf("fog_end"));
        assertEquals("star brightness", nameOf("star_brightness"), "without its note");
        assertEquals("firefly bush sounds", nameOf("firefly_bush_sounds"));
        for (EditorLayout.Section section : EditorLayout.Section.values()) {
            for (EditorLayout.Field field : EditorLayout.fields(section)) {
                String name = field.name();
                assertEquals(name.toLowerCase(Locale.ROOT), name, field.label());
                assertFalse(name.contains("(") || name.contains("_"), field.label());
            }
        }
    }

    private static String nameOf(String property) {
        return EditorLayout.field(TuningProperties.resolve(property).orElseThrow()).name();
    }

    @Test
    void everySliderShowsWholeNumbers() {
        for (EditorLayout.Section section : EditorLayout.Section.values()) {
            for (EditorLayout.Field field : EditorLayout.fields(section)) {
                if (field.property().kind() == TuningProperty.Kind.NUMBER) {
                    float shownStep = field.step() * field.scale();
                    assertEquals(Math.round(shownStep), shownStep, 0, field.property().name()
                            + ": the client adds steps in float, and only whole numbers print without noise");
                }
            }
        }
        for (String name : List.of("downfall", "sky_light_factor", "star_brightness", "music_volume")) {
            assertEquals(EditorLayout.PERCENT, field(name).format(), name);
            assertEquals(100f, field(name).scale(), name + ": 0 to 1 shows as 0 to 100 %");
        }
        assertEquals(EditorLayout.HUNDREDTHS, field("temperature").format());
        assertEquals(100f, field("temperature").scale(), "plains' 0.8 shows as 80");
        assertEquals(1f, field("fog_end").scale());
    }

    private static EditorLayout.Field field(String name) {
        return EditorLayout.field(TuningProperties.resolve(name).orElseThrow());
    }

    @Test
    void eachValueKnowsItsSection() {
        assertEquals(EditorLayout.Section.SKY_AND_FOG,
                EditorLayout.sectionOf(TuningProperties.resolve("sky_color").orElseThrow()));
        assertEquals(EditorLayout.Section.AUDIO_AND_PARTICLES,
                EditorLayout.sectionOf(TuningProperties.resolve("music").orElseThrow()));
        assertNull(EditorLayout.sectionOf(TuningProperties.resolve("minecraft:gameplay/fast_lava").orElseThrow()),
                "a raw attribute id has no place in the editor");
        assertNull(EditorLayout.field(TuningProperties.resolve("minecraft:gameplay/fast_lava").orElseThrow()));
        assertNull(EditorLayout.sectionOf(TuningProperties.resolve("sun_angle").orElseThrow()),
                "the Overworld's day cycle overrides a biome's sun angle");
    }
}
