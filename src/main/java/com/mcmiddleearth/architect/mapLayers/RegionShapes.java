package com.mcmiddleearth.architect.mapLayers;

import com.sk89q.worldedit.math.BlockVector2;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.regions.CylinderRegion;
import com.sk89q.worldedit.regions.EllipsoidRegion;
import com.sk89q.worldedit.regions.Polygonal2DRegion;
import com.sk89q.worldedit.regions.Region;

import java.util.List;
import java.util.Optional;

/**
 * A WorldEdit region as a map shape: a polygon by its points, a cylinder or an ellipsoid as an ellipse around its
 * blocks, and anything else (a cuboid) as the box around its blocks.
 * <ul>
 *     <li>A box reaches the outer edges of its blocks. A polygon's points are block corners, drawn as they are, as
 *     dynmap-worldguard draws them, so its east and south edges stop a block short of a box's.</li>
 *     <li>Areas are drawn flat, at {@link #FLAT_Y}. LiveAtlas draws an area with a Y range as a 3D outline, which on
 *     MCME's top-down flat map has no fill and opens its popup only from its border. The popup gives the heights.
 *     </li>
 * </ul>
 */
final class RegionShapes {

    /** The height areas are drawn at: dynmap's own default for an area marker. */
    static final double FLAT_Y = 64;

    private RegionShapes() {
    }

    /**
     * The region's shape, or none for a region that holds no block: a polygon left with fewer than 3 points, whose
     * {@code contains} is false everywhere, or a region whose world was unloaded.
     */
    static Optional<MapShape> of(Region region, String id, String label, String description, MapShape.Style style) {
        String world;
        try {
            world = region.getWorld().getName();
        } catch (NullPointerException e) { // no world, or WorldEdit's BukkitWorld after its world was unloaded
            return Optional.empty();
        }
        BlockVector3 min = region.getMinimumPoint();
        BlockVector3 max = region.getMaximumPoint();
        return Optional.ofNullable(switch (region) {
            case Polygonal2DRegion polygon -> {
                List<BlockVector2> points = polygon.getPoints();
                if (points.size() < 3) {
                    yield null;
                }
                double[] x = points.stream().mapToDouble(BlockVector2::x).toArray();
                double[] z = points.stream().mapToDouble(BlockVector2::z).toArray();
                yield new MapShape.Area(id, label, description, style, world, x, z, FLAT_Y, FLAT_Y);
            }
            // from the bounding box: the middle of the covered blocks, out to the edge of the outermost ones
            case CylinderRegion _, EllipsoidRegion _ -> ellipse(id, label, description, style, world, min, max);
            default -> new MapShape.Area(id, label, description, style, world,
                    new double[]{min.x(), max.x() + 1, max.x() + 1, min.x()},
                    new double[]{min.z(), min.z(), max.z() + 1, max.z() + 1}, FLAT_Y, FLAT_Y);
        });
    }

    private static MapShape.Circle ellipse(String id, String label, String description, MapShape.Style style,
                                           String world, BlockVector3 min, BlockVector3 max) {
        return new MapShape.Circle(id, label, description, style, world, (min.x() + max.x() + 1) / 2.0,
                (min.y() + max.y() + 1) / 2.0, (min.z() + max.z() + 1) / 2.0, (max.x() - min.x() + 1) / 2.0,
                (max.z() - min.z() + 1) / 2.0, min.y(), max.y() + 1);
    }

    /** The region's heights for a popup, both ends included. */
    static String yRange(Region region) {
        return "Y " + region.getMinimumPoint().y() + " to " + region.getMaximumPoint().y();
    }

    /** Text safe to put in a popup's HTML; nothing for null. */
    static String html(String text) {
        return text == null ? ""
                : text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }
}
