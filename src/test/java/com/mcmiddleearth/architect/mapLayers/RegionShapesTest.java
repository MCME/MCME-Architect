package com.mcmiddleearth.architect.mapLayers;

import com.sk89q.worldedit.math.BlockVector2;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.math.Vector2;
import com.sk89q.worldedit.math.Vector3;
import com.sk89q.worldedit.regions.CuboidRegion;
import com.sk89q.worldedit.regions.CylinderRegion;
import com.sk89q.worldedit.regions.EllipsoidRegion;
import com.sk89q.worldedit.regions.Polygonal2DRegion;
import com.sk89q.worldedit.regions.Region;
import com.sk89q.worldedit.world.NullWorld;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

// NullWorld gives WorldEdit regions a world without a running WorldEdit.
class RegionShapesTest {

    private static final MapShape.Style STYLE = new MapShape.Style(0x800080, 0.15, 2, 0x800080, 0.25);
    private static final String WORLD = NullWorld.getInstance().getName();

    private static MapShape shape(Region region) {
        return RegionShapes.of(region, "id", "label", "popup", STYLE).orElseThrow();
    }

    @Test
    void aCuboidIsABoxAroundItsBlocks() {
        MapShape.Area box = assertInstanceOf(MapShape.Area.class, shape(new CuboidRegion(NullWorld.getInstance(),
                BlockVector3.at(1, 0, 2), BlockVector3.at(10, 255, 20))));

        assertArrayEquals(new double[]{1, 11, 11, 1}, box.x());
        assertArrayEquals(new double[]{2, 2, 21, 21}, box.z());
        assertEquals(RegionShapes.FLAT_Y, box.yMin(), "flat: LiveAtlas draws an area with a Y range hollow");
        assertEquals(RegionShapes.FLAT_Y, box.yMax());
        assertEquals(WORLD, box.world());
        assertEquals("id", box.id());
        assertEquals("popup", box.description());
    }

    @Test
    void aPolygonKeepsItsPoints() {
        MapShape.Area polygon = assertInstanceOf(MapShape.Area.class, shape(new Polygonal2DRegion(
                NullWorld.getInstance(), List.of(BlockVector2.at(0, 0), BlockVector2.at(10, 0), BlockVector2.at(5, 8)),
                10, 20)));

        assertArrayEquals(new double[]{0, 10, 5}, polygon.x());
        assertArrayEquals(new double[]{0, 0, 8}, polygon.z());
        assertEquals(RegionShapes.FLAT_Y, polygon.yMin());
        assertEquals(RegionShapes.FLAT_Y, polygon.yMax());
    }

    @Test
    void aPolygonWithFewerThanThreePointsIsLeftOff() {
        assertTrue(RegionShapes.of(new Polygonal2DRegion(NullWorld.getInstance(),
                List.of(BlockVector2.at(0, 0), BlockVector2.at(10, 0)), 10, 20), "id", "label", "popup", STYLE)
                .isEmpty(), "it holds no block: WorldEdit's contains() is false everywhere");
    }

    @Test
    void aCylinderIsACircleThroughItsOuterBlocks() {
        MapShape.Circle circle = assertInstanceOf(MapShape.Circle.class, shape(new CylinderRegion(
                NullWorld.getInstance(), BlockVector3.at(100, 70, -20), Vector2.at(10, 6), 60, 80)));

        assertEquals(100.5, circle.x(), "the centre block's middle");
        assertEquals(70.5, circle.y());
        assertEquals(-19.5, circle.z());
        assertEquals(10.5, circle.radiusX(), "to the outer edge of the last block");
        assertEquals(6.5, circle.radiusZ());
        assertEquals(60, circle.yMin());
        assertEquals(81, circle.yMax());
    }

    @Test
    void anEllipsoidIsItsWidestEllipse() {
        MapShape.Circle ellipse = assertInstanceOf(MapShape.Circle.class, shape(new EllipsoidRegion(
                NullWorld.getInstance(), BlockVector3.at(0, 64, 0), Vector3.at(8, 4, 5))));

        assertEquals(0.5, ellipse.x());
        assertEquals(0.5, ellipse.z());
        assertEquals(8.5, ellipse.radiusX());
        assertEquals(5.5, ellipse.radiusZ());
        assertEquals(60, ellipse.yMin());
        assertEquals(69, ellipse.yMax());
    }

    @Test
    void theYRangeIsInclusiveInText() {
        assertEquals("Y 10 to 20", RegionShapes.yRange(new Polygonal2DRegion(NullWorld.getInstance(),
                List.of(BlockVector2.at(0, 0), BlockVector2.at(10, 0), BlockVector2.at(5, 8)), 10, 20)));
    }

    @Test
    void htmlInNamesIsEscaped() {
        assertEquals("a&lt;b&gt; &amp; &quot;c&quot;", RegionShapes.html("a<b> & \"c\""));
    }
}
