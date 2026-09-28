package com.mcmiddleearth.architect.mapLayers;

import org.junit.jupiter.api.Test;

import java.lang.reflect.RecordComponent;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

// A broken shape fails where the layer builds it, not later inside the map.
class MapShapeTest {

    private static final MapShape.Style STYLE = new MapShape.Style(0xff0000, 0.4, 2, 0x800000, 0.2);

    @Test
    void anAreaNeedsAsManyXAsZCornersAndAtLeastThree() {
        assertThrows(IllegalArgumentException.class, () -> new MapShape.Area("a", "a", "", STYLE, "world",
                new double[]{0, 1}, new double[]{0, 1}, 0, 1));
        assertThrows(IllegalArgumentException.class, () -> new MapShape.Area("a", "a", "", STYLE, "world",
                new double[]{0, 1, 1}, new double[]{0, 0, 1, 1}, 0, 1));
    }

    @Test
    void aShapeRefusesMissingParts() {
        assertThrows(NullPointerException.class, () -> new MapShape.Area("a", null, "", STYLE, "world",
                new double[]{0, 1, 1}, new double[]{0, 0, 1}, 0, 1));
        assertThrows(NullPointerException.class, () -> new MapShape.Circle("c", "c", "", STYLE, null,
                0, 0, 0, 1, 1, 0, 1));
    }

    @Test
    void areasCompareTheirCornersByValueAndKeepTheirOwnCopy() {
        double[] x = {0, 16, 16, 0};
        MapShape.Area area = new MapShape.Area("a", "a", "", STYLE, "world", x, new double[]{0, 0, 16, 16}, 0, 1);

        assertEquals(new MapShape.Area("a", "a", "", STYLE, "world", new double[]{0, 16, 16, 0},
                new double[]{0, 0, 16, 16}, 0, 1), area);
        assertEquals(new MapShape.Area("a", "a", "", STYLE, "world", new double[]{0, 16, 16, 0},
                new double[]{0, 0, 16, 16}, 0, 1).hashCode(), area.hashCode());
        x[1] = 99;
        assertEquals(16, area.x()[1], "the caller's array is not the shape's");
        area.x()[1] = 99;
        assertEquals(16, area.x()[1], "nor is the array it hands out");
    }

    // The map skips a marker whose shape equals the one drawn last time, so every part must count in equals,
    // including any part added later.
    @Test
    void anAreaDiffersFromOneThatDiffersInAnyPart() throws Exception {
        MapShape.Area area = new MapShape.Area("a", "label", "<b>popup</b>", STYLE, "world",
                new double[]{0, 16, 16, 0}, new double[]{0, 0, 16, 16}, 0, 1);
        RecordComponent[] parts = MapShape.Area.class.getRecordComponents();
        Class<?>[] types = Arrays.stream(parts).map(RecordComponent::getType).toArray(Class<?>[]::new);
        for (int i = 0; i < parts.length; i++) {
            Object[] values = new Object[parts.length];
            for (int j = 0; j < parts.length; j++) {
                values[j] = parts[j].getAccessor().invoke(area);
            }
            values[i] = different(values[i]);
            MapShape.Area changed = MapShape.Area.class.getDeclaredConstructor(types).newInstance(values);
            assertNotEquals(area, changed, parts[i].getName() + " must count in equals");
        }
    }

    private static Object different(Object value) {
        return switch (value) {
            case String text -> text + "!";
            case Double number -> number + 1;
            case double[] numbers -> {
                double[] copy = numbers.clone();
                copy[copy.length - 1]++;
                yield copy;
            }
            case MapShape.Style style -> new MapShape.Style(style.lineColor() + 1, style.lineOpacity(),
                    style.lineWidth(), style.fillColor(), style.fillOpacity());
            default -> throw new IllegalArgumentException("a new kind of part: " + value);
        };
    }
}
