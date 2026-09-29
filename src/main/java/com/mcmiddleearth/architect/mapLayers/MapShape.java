package com.mcmiddleearth.architect.mapLayers;

import java.util.Arrays;
import java.util.Objects;

/**
 * One marker on a map layer, as plain data, so a layer can be built and tested without a map.
 * <ul>
 *     <li>Coordinates are block coordinates. A box's corners are outer edges: a block at 15 ends at 16. A polygon
 *     from a WorldEdit region is drawn through its points as they are, which are block corners, so its east and
 *     south edges stop a block short of a box's, as {@link RegionShapes} explains.</li>
 *     <li>The label is plain text; the description is the popup's HTML.</li>
 * </ul>
 */
public sealed interface MapShape {

    /** Unique within its layer, among shapes of its kind. */
    String id();

    String label();

    /** HTML for the marker's popup. */
    String description();

    Style style();

    String world();

    /** Plain text made safe for a popup's HTML; nothing for null. */
    static String html(String text) {
        return text == null ? ""
                : text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }

    /**
     * A polygon by its corners, at least 3: a box is its 4 corners. The corners are copied in and out, and compared
     * by value.
     * <p>
     * An area has no height: the map draws every area flat. LiveAtlas draws an area with a Y range as a 3D outline,
     * which on MCME's top-down flat map has no fill and opens its popup only from its border. The popup gives the
     * heights instead.
     */
    record Area(String id, String label, String description, Style style, String world, double[] x, double[] z)
            implements MapShape {

        public Area {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(label, "label");
            Objects.requireNonNull(description, "description");
            Objects.requireNonNull(style, "style");
            Objects.requireNonNull(world, "world");
            if (x.length != z.length || x.length < 3) {
                throw new IllegalArgumentException("Area " + id + " needs as many x as z corners, and at least 3");
            }
            x = x.clone();
            z = z.clone();
        }

        @Override
        public double[] x() {
            return x.clone();
        }

        @Override
        public double[] z() {
            return z.clone();
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof Area area && id.equals(area.id) && label.equals(area.label)
                    && description.equals(area.description) && style.equals(area.style) && world.equals(area.world)
                    && Arrays.equals(x, area.x) && Arrays.equals(z, area.z);
        }

        @Override
        public int hashCode() {
            return Objects.hash(id, label, description, style, world, Arrays.hashCode(x), Arrays.hashCode(z));
        }

        @Override
        public String toString() {
            return "Area[" + id + " '" + label + "' in " + world + ": x " + Arrays.toString(x) + ", z "
                    + Arrays.toString(z) + ", " + style + "]";
        }
    }

    /**
     * A circle or an ellipse around (x, z), with radii along x and z; y is the centre's height. Dynmap draws a circle
     * at that height only; yMin and yMax say which heights the shape covers.
     */
    record Circle(String id, String label, String description, Style style, String world, double x, double y,
                  double z, double radiusX, double radiusZ, double yMin, double yMax) implements MapShape {

        public Circle {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(label, "label");
            Objects.requireNonNull(description, "description");
            Objects.requireNonNull(style, "style");
            Objects.requireNonNull(world, "world");
        }
    }

    /** Line and fill; colours are 0xRRGGBB, opacities 0 to 1. */
    record Style(int lineColor, double lineOpacity, int lineWidth, int fillColor, double fillOpacity) {}
}
