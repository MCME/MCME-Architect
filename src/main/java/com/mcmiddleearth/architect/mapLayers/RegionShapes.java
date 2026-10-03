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
 * <p>
 * A box reaches the outer edges of its blocks. A polygon's points are block corners, drawn as they are, as
 * dynmap-worldguard draws them, so its east and south edges stop a block short of a box's.
 */
final class RegionShapes {

    private RegionShapes() {
    }

    /**
     * The region's shape, or none for a region that holds no block: a polygon left with fewer than 3 points, whose
     * {@code contains} is false everywhere, or a region whose world was unloaded. Once Architect compiles against
     * WorldEdit 7.4, its {@code World.isValid()} can replace the catch for an unloaded world.
     */
    static Optional<MapShape> of(Region region, String id, String label, String description, MapShape.Style style) {
        if (region.getWorld() == null) {
            return Optional.empty();
        }
        String world;
        try {
            world = region.getWorld().getName();
        } catch (NullPointerException e) { // WorldEdit's BukkitWorld after its world was unloaded
            return Optional.empty();
        }
        BlockVector3 min = region.getMinimumPoint();
        BlockVector3 max = region.getMaximumPoint();
        return switch (region) {
            case Polygonal2DRegion polygon when polygon.getPoints().size() < 3 -> Optional.empty();
            case Polygonal2DRegion polygon -> {
                List<BlockVector2> points = polygon.getPoints();
                double[] x = points.stream().mapToDouble(BlockVector2::x).toArray();
                double[] z = points.stream().mapToDouble(BlockVector2::z).toArray();
                yield Optional.of(new MapShape.Area(id, label, description, style, world, x, z));
            }
            // from the bounding box: the middle of the covered blocks, out to the edge of the outermost ones
            case CylinderRegion _, EllipsoidRegion _ ->
                    Optional.of(ellipse(id, label, description, style, world, min, max));
            default -> Optional.of(new MapShape.Area(id, label, description, style, world,
                    new double[]{min.x(), max.x() + 1, max.x() + 1, min.x()},
                    new double[]{min.z(), min.z(), max.z() + 1, max.z() + 1}));
        };
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
}
