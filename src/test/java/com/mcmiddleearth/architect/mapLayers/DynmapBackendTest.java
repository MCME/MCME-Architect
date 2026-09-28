package com.mcmiddleearth.architect.mapLayers;

import com.mcmiddleearth.architect.testsupport.FakeMarkerApi;
import org.dynmap.markers.AreaMarker;
import org.dynmap.markers.CircleMarker;
import org.dynmap.markers.MarkerSet;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

// The backend against an in-memory dynmap: no server needed. Every style has distinct line and fill values, and
// circles have distinct radii, so swapped arguments show.
class DynmapBackendTest {

    private static final MapShape.Style RED = new MapShape.Style(0xff0000, 0.4, 2, 0x800000, 0.2);
    private static final MapShape.Style BLUE = new MapShape.Style(0x1e64ff, 0.6, 3, 0x0000aa, 0.3);

    private final FakeMarkerApi dynmap = new FakeMarkerApi();
    private final DynmapBackend backend = new DynmapBackend(dynmap.api());

    private static MapLayer layer(String id, String label, boolean hidden) {
        return new MapLayer() {
            @Override public String markerSetId() { return id; }
            @Override public String label() { return label; }
            @Override public boolean hiddenByDefault() { return hidden; }
            @Override public List<MapShape> shapes() { return List.of(); }
        };
    }

    private static MapShape.Area box(String id, String world, double x1, double z1, double x2, double z2) {
        return new MapShape.Area(id, id + " label", "<b>" + id + "</b><br>popup", RED, world,
                new double[]{x1, x2, x2, x1}, new double[]{z1, z1, z2, z2}, 0, 255);
    }

    private static MapShape.Circle circle(String id, double radiusX, double radiusZ) {
        return new MapShape.Circle(id, id + " label", id + " popup", BLUE, "world",
                10.5, 64, -20.5, radiusX, radiusZ, 40, 90);
    }

    private MarkerSet set() {
        return dynmap.api().getMarkerSet("test.debug.a");
    }

    @Test
    void showCreatesTheLayerAndItsMarkers() {
        backend.show(layer("test.debug.a", "Test A", true),
                List.of(box("one", "world", 0, 0, 16, 32), circle("two", 8, 5)));

        assertNotNull(set(), "the marker set is created");
        assertEquals("Test A", set().getMarkerSetLabel());
        assertTrue(set().getHideByDefault());

        AreaMarker area = set().findAreaMarker("one");
        assertEquals("world", area.getWorld());
        assertEquals("one label", area.getLabel());
        assertEquals("<b>one</b><br />popup", area.getDescription(), "as dynmap stores it");
        assertEquals(4, area.getCornerCount());
        assertEquals(16, area.getCornerX(1));
        assertEquals(32, area.getCornerZ(2));
        assertEquals(255, area.getTopY());
        assertEquals(0, area.getBottomY());
        assertEquals(0xff0000, area.getLineColor());
        assertEquals(0.4, area.getLineOpacity());
        assertEquals(2, area.getLineWeight());
        assertEquals(0x800000, area.getFillColor());
        assertEquals(0.2, area.getFillOpacity());

        CircleMarker round = set().findCircleMarker("two");
        assertEquals("world", round.getWorld());
        assertEquals("two label", round.getLabel());
        assertEquals("two popup", round.getDescription());
        assertEquals(10.5, round.getCenterX());
        assertEquals(64, round.getCenterY());
        assertEquals(-20.5, round.getCenterZ());
        assertEquals(8, round.getRadiusX());
        assertEquals(5, round.getRadiusZ());
        assertEquals(0x1e64ff, round.getLineColor());
        assertEquals(0.6, round.getLineOpacity());
        assertEquals(3, round.getLineWeight());
        assertEquals(0x0000aa, round.getFillColor());
        assertEquals(0.3, round.getFillOpacity());
    }

    @Test
    void showingTheSameShapesAgainWritesNothing() {
        MapLayer layer = layer("test.debug.a", "Test A", true);
        MapShape.Area named = new MapShape.Area("three", "Tom & Jerry's", "<b>Tom &amp; Jerry's</b><br>popup", RED,
                "world", new double[]{0, 16, 16, 0}, new double[]{0, 0, 16, 16}, 0, 255);
        backend.show(layer, List.of(box("one", "world", 0, 0, 16, 32), circle("two", 8, 5), named));
        int writes = dynmap.writes();

        backend.show(layer, List.of(box("one", "world", 0, 0, 16, 32), circle("two", 8, 5),
                new MapShape.Area("three", "Tom & Jerry's", "<b>Tom &amp; Jerry's</b><br>popup", RED, "world",
                        new double[]{0, 16, 16, 0}, new double[]{0, 0, 16, 16}, 0, 255)));

        assertEquals(writes, dynmap.writes(), "unchanged markers are left alone, so web clients get no updates, "
                + "although dynmap stores labels and descriptions in other forms");
    }

    @Test
    void showUpdatesAMarkerInPlace() {
        MapLayer layer = layer("test.debug.a", "Test A", true);
        backend.show(layer, List.of(box("one", "world", 0, 0, 16, 32), circle("two", 8, 5)));
        AreaMarker area = set().findAreaMarker("one");
        CircleMarker round = set().findCircleMarker("two");

        backend.show(layer, List.of(
                new MapShape.Area("one", "new label", "new popup", BLUE, "world",
                        new double[]{0, 48, 48, 0}, new double[]{0, 0, 40, 40}, 10, 20),
                new MapShape.Circle("two", "new round", "round popup", RED, "world",
                        30, 70, 40, 12, 7, 60, 80)));

        assertSame(area, set().findAreaMarker("one"), "the same marker, rewritten");
        assertEquals(48, area.getCornerX(1));
        assertEquals(40, area.getCornerZ(2));
        assertEquals("new label", area.getLabel());
        assertEquals("new popup", area.getDescription());
        assertEquals(20, area.getTopY());
        assertEquals(10, area.getBottomY());
        assertEquals(0x1e64ff, area.getLineColor());
        assertEquals(0.6, area.getLineOpacity());
        assertEquals(3, area.getLineWeight());
        assertEquals(0x0000aa, area.getFillColor());
        assertEquals(0.3, area.getFillOpacity());

        assertSame(round, set().findCircleMarker("two"));
        assertEquals(30, round.getCenterX());
        assertEquals(70, round.getCenterY());
        assertEquals(40, round.getCenterZ());
        assertEquals(12, round.getRadiusX());
        assertEquals(7, round.getRadiusZ());
        assertEquals("new round", round.getLabel());
        assertEquals("round popup", round.getDescription());
        assertEquals(0xff0000, round.getLineColor());
        assertEquals(0.4, round.getLineOpacity());
        assertEquals(2, round.getLineWeight());
        assertEquals(0x800000, round.getFillColor());
        assertEquals(0.2, round.getFillOpacity());

        int labels = dynmap.labelWrites();
        backend.show(layer, List.of(
                new MapShape.Area("one", "new label", "new popup", BLUE, "world",
                        new double[]{0, 64, 64, 0}, new double[]{0, 0, 40, 40}, 10, 20),
                new MapShape.Circle("two", "new round", "round popup", RED, "world",
                        30, 70, 40, 12, 9, 60, 80)));
        assertEquals(64, area.getCornerX(1));
        assertEquals(9, round.getRadiusZ());
        assertEquals(labels, dynmap.labelWrites(), "an unchanged label is not sent again: dynmap sends every one");
    }

    @Test
    void aShowThatFailsPartwayIsPutRightByTheNextOne() {
        MapLayer layer = layer("test.debug.a", "Test A", true);
        List<MapShape> shapes = List.of(circle("two", 8, 5), box("one", "world", 0, 0, 16, 32));
        backend.show(layer, shapes);

        dynmap.failDrawing(true); // the circle is moved, then drawing the area fails
        assertThrows(NoSuchMethodError.class, () -> backend.show(layer, List.of(
                new MapShape.Circle("two", "two label", "two popup", BLUE, "world", 99, 64, -20.5, 8, 5, 40, 90),
                box("one", "world", 0, 0, 16, 32))));
        dynmap.failDrawing(false);
        backend.show(layer, shapes);

        assertEquals(10.5, set().findCircleMarker("two").getCenterX(), "back where the layer says");
    }

    @Test
    void showDeletesMarkersWhoseShapeIsGone() {
        MapLayer layer = layer("test.debug.a", "Test A", true);
        backend.show(layer, List.of(box("one", "world", 0, 0, 16, 32), circle("two", 8, 5)));

        backend.show(layer, List.of(box("three", "world", 0, 0, 16, 16)));

        assertNull(set().findAreaMarker("one"));
        assertNull(set().findCircleMarker("two"));
        assertNotNull(set().findAreaMarker("three"));
    }

    @Test
    void aShapeThatMovedToAnotherWorldIsRedrawnThere() {
        MapLayer layer = layer("test.debug.a", "Test A", true);
        backend.show(layer, List.of(box("one", "world", 0, 0, 16, 32), circle("two", 8, 5)));
        CircleMarker round = set().findCircleMarker("two");

        backend.show(layer, List.of(box("one", "world_nether", 0, 0, 16, 32),
                new MapShape.Circle("two", "two label", "two popup", BLUE, "world_nether", 10.5, 64, -20.5, 8, 5, 40,
                        90)));

        assertEquals("world_nether", set().findAreaMarker("one").getWorld(),
                "an area marker cannot change world, so it is created again");
        assertNotSame(round, set().findCircleMarker("two"), "a circle is made again too");
        assertEquals("world_nether", set().findCircleMarker("two").getWorld());
    }

    @Test
    void aShapeThatChangedKindReplacesItsOldMarker() {
        MapLayer layer = layer("test.debug.a", "Test A", true);
        backend.show(layer, List.of(box("one", "world", 0, 0, 16, 32)));

        backend.show(layer, List.of(circle("one", 8, 5)));

        assertNull(set().findAreaMarker("one"), "a region redefined as a cylinder is no longer a polygon");
        assertNotNull(set().findCircleMarker("one"));
    }

    @Test
    void ofTwoShapesWithTheSameIdTheFirstIsDrawn() {
        MapLayer layer = layer("test.debug.a", "Test A", true);
        List<MapShape> shapes = List.of(box("one", "world", 0, 0, 16, 32), box("one", "world", 100, 100, 116, 132));

        backend.show(layer, shapes);
        int writes = dynmap.writes();
        backend.show(layer, shapes);

        assertEquals(16, set().findAreaMarker("one").getCornerX(1));
        assertEquals(writes, dynmap.writes(), "and the second one does not rewrite it at every redraw");
    }

    @Test
    void aMarkerSetLeftByAnEarlierRunIsTakenOver() {
        MarkerSet old = dynmap.api().createMarkerSet("test.debug.a", "Old label", null, false);
        old.createAreaMarker("stale", "stale", false, "world", new double[]{0, 1, 1}, new double[]{0, 0, 1}, false);
        old.createAreaMarker("one", "old label", false, "world", new double[]{0, 1, 1}, new double[]{0, 0, 1}, false);

        backend.show(layer("test.debug.a", "Test A", false), List.of(box("one", "world", 0, 0, 16, 32)));

        assertSame(old, set(), "after a plugin reload the set still exists, and createMarkerSet would return null");
        assertEquals("Test A", set().getMarkerSetLabel());
        assertFalse(set().getHideByDefault());
        assertNull(set().findAreaMarker("stale"));
        assertEquals("one label", set().findAreaMarker("one").getLabel(), "a marker it did not draw is rewritten");
        assertEquals(16, set().findAreaMarker("one").getCornerX(1));
    }

    @Test
    void removeDeletesTheLayer() {
        MapLayer layer = layer("test.debug.a", "Test A", true);
        backend.show(layer, List.of(box("one", "world", 0, 0, 16, 32)));

        backend.remove("test.debug.a");
        backend.remove("test.debug.never-shown");

        assertNull(set());
        backend.show(layer, List.of(box("one", "world", 0, 0, 16, 32)));
        assertNotNull(set().findAreaMarker("one"), "shown again after a remove, it is drawn from scratch");

        set().deleteMarkerSet(); // behind the backend's back, as /dmarker deleteset would
        backend.show(layer, List.of(box("one", "world", 0, 0, 16, 32)));
        assertNotNull(set().findAreaMarker("one"), "a marker that vanished is drawn again");
    }
}
