package com.mcmiddleearth.architect.mapLayers;

import com.mcmiddleearth.architect.testsupport.FakeMarkerApi;
import org.bukkit.event.server.PluginDisableEvent;
import org.bukkit.event.server.PluginEnableEvent;
import org.bukkit.event.world.WorldLoadEvent;
import org.bukkit.plugin.Plugin;
import org.dynmap.markers.MarkerSet;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import java.util.logging.Handler;
import java.util.logging.LogRecord;

import static org.junit.jupiter.api.Assertions.*;

class MapLayersTest {

    private static final MapShape.Style STYLE = new MapShape.Style(0xff0000, 0.5, 1, 0xff0000, 0.2);

    private ServerMock server;
    private Plugin plugin;
    private final FakeMarkerApi dynmap = new FakeMarkerApi();
    private final AtomicInteger lookups = new AtomicInteger();
    private final List<String> warnings = new ArrayList<>();

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin();
        plugin.getLogger().addHandler(new Handler() {
            @Override public void publish(LogRecord record) {
                warnings.add(record.getLevel() + ": " + record.getMessage());
            }
            @Override public void flush() {}
            @Override public void close() {}
        });
    }

    @AfterEach
    void tearDown() {
        MapLayers.stop();
        MockBukkit.unmock();
    }

    private void start(MapLayer... layers) {
        MapLayers.start(plugin, List.of(layers), p -> {
            lookups.incrementAndGet();
            return new DynmapBackend(dynmap.api());
        });
    }

    /** A layer whose shapes come from a supplier, counting how often it is asked. */
    private static class TestLayer implements MapLayer {
        final String id;
        final AtomicInteger asked = new AtomicInteger();
        Supplier<List<MapShape>> shapes;

        TestLayer(String id, String... boxes) {
            this.id = id;
            List<MapShape> list = new ArrayList<>();
            for (String box : boxes) {
                list.add(new MapShape.Area(box, box, box, STYLE, "world",
                        new double[]{0, 16, 16, 0}, new double[]{0, 0, 16, 16}));
            }
            shapes = () -> list;
        }

        @Override public String markerSetId() { return id; }
        @Override public String label() { return id; }
        @Override public boolean hiddenByDefault() { return true; }
        @Override public List<MapShape> shapes() { asked.incrementAndGet(); return shapes.get(); }
    }

    private MarkerSet set(String id) {
        return dynmap.api().getMarkerSet(id);
    }

    @Test
    void everyLayerIsDrawnOnTheFirstTick() {
        TestLayer a = new TestLayer("test.debug.a", "one");
        TestLayer b = new TestLayer("test.debug.b", "two");
        start(a, b);
        assertEquals(0, lookups.get(), "Architect enables before dynmap, so the map is looked up at the first redraw");

        server.getScheduler().performOneTick();

        assertEquals(1, lookups.get());
        assertNotNull(set("test.debug.a").findAreaMarker("one"));
        assertNotNull(set("test.debug.b").findAreaMarker("two"));
    }

    @Test
    void manyChangesInOneTickCostOneRedraw() {
        TestLayer a = new TestLayer("test.debug.a", "one");
        TestLayer b = new TestLayer("test.debug.b", "two");
        start(a, b);
        server.getScheduler().performOneTick();

        MapLayers.changed("test.debug.a");
        MapLayers.changed("test.debug.a");
        MapLayers.changed("test.debug.a");
        server.getScheduler().performOneTick();

        assertEquals(2, a.asked.get(), "once at start, once for the three changes");
        assertEquals(1, b.asked.get(), "an unchanged layer is not redrawn");
    }

    @Test
    void aLayerThatThrowsIsSkippedAndKeepsItsMarkers() {
        TestLayer a = new TestLayer("test.debug.a", "one");
        TestLayer b = new TestLayer("test.debug.b", "two");
        start(a, b);
        server.getScheduler().performOneTick();

        a.shapes = () -> { throw new IllegalStateException("broken region"); };
        MapLayers.changed("test.debug.a");
        MapLayers.changed("test.debug.b");
        server.getScheduler().performOneTick();
        MapLayers.changed("test.debug.a");
        server.getScheduler().performOneTick();

        assertNotNull(set("test.debug.a").findAreaMarker("one"), "never half-drawn: the old markers stay");
        assertEquals(2, b.asked.get(), "the other layer is still drawn");
        assertEquals(1, warnings.stream().filter(w -> w.contains("test.debug.a")).count(), "logged once: " + warnings);
    }

    @Test
    void aLayerWithAMissingShapeIsSkippedAndTheMapStaysOn() {
        TestLayer a = new TestLayer("test.debug.a", "one");
        TestLayer b = new TestLayer("test.debug.b", "two");
        start(a, b);
        server.getScheduler().performOneTick();

        a.shapes = () -> Arrays.asList((MapShape) null);
        MapLayers.changed("test.debug.a");
        MapLayers.changed("test.debug.b");
        server.getScheduler().performOneTick();

        assertEquals(2, b.asked.get(), "the other layer is still drawn");
        assertNotNull(set("test.debug.a").findAreaMarker("one"), "the broken layer keeps its markers");
        assertTrue(warnings.stream().noneMatch(w -> w.contains("switched off")), warnings.toString());
    }

    @Test
    void aMapThatFailsIsSwitchedOff() {
        TestLayer a = new TestLayer("test.debug.a", "one");
        start(a);
        dynmap.failDrawing(true);
        server.getScheduler().performOneTick();

        MapLayers.changed("test.debug.a");
        server.getScheduler().performOneTick();

        assertEquals(1, a.asked.get(), "after the map failed, the layers are no longer built for it");
        assertEquals(1, warnings.stream().filter(w -> w.contains("switched off")).count(), "logged once: " + warnings);

        MapLayers.stop();
        assertNull(set("test.debug.a"), "at stop its layers still come off the map, if it can");
    }

    @Test
    void whenDynmapStartsAgainItsNewMapIsLookedUpAndEveryLayerDrawn() {
        Plugin dynmapPlugin = MockBukkit.createMockPlugin("dynmap");
        TestLayer a = new TestLayer("test.debug.a", "one");
        start(a);
        server.getScheduler().performOneTick();

        server.getPluginManager().callEvent(new PluginDisableEvent(dynmapPlugin)); // /dynmap reload
        MapLayers.changed("test.debug.a");
        server.getScheduler().performOneTick();
        assertEquals(1, a.asked.get(), "nothing is drawn while dynmap is away");

        server.getPluginManager().callEvent(new PluginEnableEvent(dynmapPlugin));
        server.getScheduler().performOneTick();

        assertEquals(2, lookups.get(), "its new marker API is looked up");
        assertEquals(2, a.asked.get(), "and every layer is drawn again");
    }

    // /mv load: the layers leave out what lies in a world that is not loaded, so a world loaded later needs a redraw.
    @Test
    void aWorldLoadedLaterRedrawsEveryLayer() {
        TestLayer a = new TestLayer("test.debug.a", "one");
        TestLayer b = new TestLayer("test.debug.b", "two");
        start(a, b);
        server.getScheduler().performOneTick();

        server.getPluginManager().callEvent(new WorldLoadEvent(server.addSimpleWorld("later")));
        server.getScheduler().performOneTick();

        assertEquals(2, a.asked.get());
        assertEquals(2, b.asked.get());
    }

    // A server without the dynmap jar: no org.dynmap class can load, and Architect must start all the same.
    @Test
    void mapLayersStartWithoutDynmapInstalled() throws Exception {
        URL classes = MapLayers.class.getProtectionDomain().getCodeSource().getLocation();
        try (URLClassLoader withoutDynmap = new URLClassLoader(new URL[]{classes},
                MapLayersTest.class.getClassLoader()) {
            @Override
            protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
                if (name.startsWith("org.dynmap.")) {
                    throw new ClassNotFoundException(name);
                }
                if (!name.startsWith(MapLayers.class.getPackageName() + ".")) {
                    return super.loadClass(name, resolve);
                }
                synchronized (getClassLoadingLock(name)) {
                    Class<?> loaded = findLoadedClass(name);
                    return loaded != null ? loaded : findClass(name);
                }
            }
        }) {
            Class<?> isolated = withoutDynmap.loadClass(MapLayers.class.getName());
            isolated.getMethod("start", Plugin.class, List.class).invoke(null, plugin, List.of());
            server.getScheduler().performOneTick();
            isolated.getMethod("stop").invoke(null);
        }
        assertTrue(warnings.contains("INFO: dynmap is not enabled, so Architect's map layers are not drawn."),
                "INFO, not a warning: a server without dynmap is no fault " + warnings);
    }

    @Test
    void aMapThatCannotBeLinkedIsSwitchedOffWithOneWarning() {
        TestLayer a = new TestLayer("test.debug.a", "one");
        MapLayers.start(plugin, List.of(a), p -> {
            throw new NoClassDefFoundError("org/dynmap/markers/GenericMarker");
        });

        assertDoesNotThrow(() -> server.getScheduler().performOneTick());
        MapLayers.changed("test.debug.a");
        assertDoesNotThrow(() -> server.getScheduler().performOneTick());

        assertEquals(0, a.asked.get(), "no layer is built for a map that is not there");
        assertEquals(1, warnings.stream().filter(w -> w.contains("not compatible")).count(),
                "logged once: " + warnings);
    }

    @Test
    void aRedrawScheduledBeforeStopNeverRuns() {
        start(new TestLayer("test.debug.a", "one"));
        MapLayers.changed("test.debug.a");
        MapLayers.changed("test.debug.a");

        MapLayers.stop();
        server.getScheduler().performTicks(2);

        assertEquals(0, lookups.get());
        assertNull(set("test.debug.a"));
    }

    @Test
    void afterARestartOnlyTheNewLayersFollowDynmap() {
        Plugin dynmapPlugin = MockBukkit.createMockPlugin("dynmap");
        start(new TestLayer("test.debug.a", "one"));
        server.getScheduler().performOneTick();
        start(new TestLayer("test.debug.b", "two")); // /architect reload with other settings
        server.getScheduler().performOneTick();

        server.getPluginManager().callEvent(new PluginDisableEvent(dynmapPlugin)); // /dynmap reload
        server.getPluginManager().callEvent(new PluginEnableEvent(dynmapPlugin));
        server.getScheduler().performOneTick();

        assertNull(set("test.debug.a"), "the old instance no longer listens");
        assertNotNull(set("test.debug.b").findAreaMarker("two"));
    }

    @Test
    void otherPluginsStartingOrStoppingChangeNothing() {
        Plugin other = MockBukkit.createMockPlugin("other");
        TestLayer a = new TestLayer("test.debug.a", "one");
        start(a);
        server.getScheduler().performOneTick();

        server.getPluginManager().callEvent(new PluginDisableEvent(other));
        server.getPluginManager().callEvent(new PluginEnableEvent(other));
        MapLayers.changed("test.debug.a");
        server.getScheduler().performOneTick();

        assertEquals(1, lookups.get(), "the map is not looked up again");
        assertEquals(2, a.asked.get(), "and not switched off");
    }

    @Test
    void stopRemovesTheLayersFromTheMap() {
        start(new TestLayer("test.debug.a", "one"));
        server.getScheduler().performOneTick();

        MapLayers.stop();
        MapLayers.changed("test.debug.a");
        server.getScheduler().performOneTick();

        assertNull(set("test.debug.a"));
    }

    @Test
    void aLayerThatCannotBeRemovedDoesNotKeepTheOthersOnTheMap() {
        List<String> removed = new ArrayList<>();
        MapLayers.start(plugin, List.of(new TestLayer("test.debug.a"), new TestLayer("test.debug.b")),
                p -> new MapBackend() {
                    @Override public void show(MapLayer layer, List<MapShape> shapes) {}
                    @Override public void remove(String markerSetId) {
                        if (markerSetId.equals("test.debug.a")) {
                            throw new IllegalStateException("stuck");
                        }
                        removed.add(markerSetId);
                    }
                });
        server.getScheduler().performOneTick();

        MapLayers.stop();

        assertEquals(List.of("test.debug.b"), removed);
    }

    @Test
    void startingAgainReplacesTheLayers() {
        start(new TestLayer("test.debug.a", "one"));
        server.getScheduler().performOneTick();

        start(new TestLayer("test.debug.b", "two")); // /architect reload with other settings
        server.getScheduler().performOneTick();

        assertNull(set("test.debug.a"), "a layer switched off in the config leaves the map");
        assertNotNull(set("test.debug.b").findAreaMarker("two"));
    }

    @Test
    void changesBeforeStartOrForUnknownLayersAreIgnored() {
        MapLayers.changed("test.debug.a");
        start(new TestLayer("test.debug.a", "one"));
        MapLayers.changed("no.such.layer");

        assertDoesNotThrow(() -> server.getScheduler().performOneTick());
        assertNotNull(set("test.debug.a"));
        assertTrue(warnings.isEmpty(), "nothing to report: " + warnings);
    }

    @Test
    void layersThatGatherTheirOwnDataAreStartedAndStopped() {
        List<String> calls = new ArrayList<>();
        TestLayer broken = new TestLayer("test.debug.a", "one") {
            @Override public void start(Plugin p) { throw new IllegalStateException("cannot start"); }
            @Override public void stop() { throw new IllegalStateException("cannot stop"); }
        };
        TestLayer gathering = new TestLayer("test.debug.b", "two") {
            @Override public void start(Plugin p) { calls.add("start"); }
            @Override public void stop() { calls.add("stop"); }
        };

        start(broken, gathering);
        MapLayers.stop();

        assertEquals(List.of("start", "stop"), calls, "a layer that cannot start or stop does not hold the others up");
        assertTrue(warnings.stream().anyMatch(w -> w.contains("test.debug.a could not start")), warnings.toString());
    }

    @Test
    void aRefreshLooksTheMapUpAgainAndRedrawsEverything() {
        start(new TestLayer("test.debug.a", "one"));
        server.getScheduler().performOneTick();
        set("test.debug.a").findAreaMarker("one").setLabel("edited by hand");

        MapLayers.refreshAll();
        server.getScheduler().performOneTick();

        assertEquals(2, lookups.get(), "a fresh map, with nothing cached");
        assertEquals("one", set("test.debug.a").findAreaMarker("one").getLabel(), "the hand edit is undone");

        int writes = dynmap.writes();
        MapLayers.changed("test.debug.a");
        server.getScheduler().performOneTick();
        assertEquals(2, lookups.get(), "only the redraw right after the refresh looks the map up again");
        assertEquals(writes, dynmap.writes(), "so later redraws of unchanged shapes write nothing");
    }

    @Test
    void aRefreshSaysWhatItFound() {
        assertEquals(MapLayers.Refresh.NOT_RUNNING, MapLayers.refreshAll());
        start(new TestLayer("test.debug.a", "one"));
        assertEquals(MapLayers.Refresh.NO_MAP, MapLayers.refreshAll(), "no dynmap plugin here");
        MockBukkit.createMockPlugin("dynmap");
        assertEquals(MapLayers.Refresh.DRAWN, MapLayers.refreshAll());
    }

    @Test
    void aLayerThatCannotRefreshDoesNotHoldTheOthersUp() {
        List<String> calls = new ArrayList<>();
        TestLayer broken = new TestLayer("test.debug.a", "one") {
            @Override public void refresh() { throw new IllegalStateException("cannot refresh"); }
        };
        TestLayer gathering = new TestLayer("test.debug.b", "two") {
            @Override public void refresh() { calls.add("refresh"); }
        };
        start(broken, gathering);
        server.getScheduler().performOneTick();

        assertDoesNotThrow(MapLayers::refreshAll);
        server.getScheduler().performOneTick();

        assertEquals(List.of("refresh"), calls, "a layer that cannot refresh does not hold the others up");
        assertTrue(warnings.stream().anyMatch(w -> w.contains("test.debug.a could not refresh")), warnings.toString());
        assertEquals(2, broken.asked.get(), "and it is drawn again all the same");
    }

    @Test
    void aRefreshTriesAMapThatFailedOnceMore() {
        start(new TestLayer("test.debug.a", "one"));
        dynmap.failDrawing(true);
        server.getScheduler().performOneTick();
        dynmap.failDrawing(false);

        MapLayers.refreshAll();
        server.getScheduler().performOneTick();

        assertNotNull(set("test.debug.a").findAreaMarker("one"));
    }

    @Test
    void aRefreshLogsAgainWhyALayerCannotBeBuilt() {
        TestLayer a = new TestLayer("test.debug.a", "one");
        a.shapes = () -> { throw new IllegalStateException("broken region"); };
        start(a);
        server.getScheduler().performOneTick();
        MapLayers.changed("test.debug.a");
        server.getScheduler().performOneTick();

        MapLayers.refreshAll();
        server.getScheduler().performOneTick();

        assertEquals(2, warnings.stream().filter(w -> w.contains("test.debug.a could not be built")).count(),
                "once, then once more for the refresh: " + warnings);
    }

    @Test
    void aStopRightAfterARefreshStillTakesTheLayersOff() {
        start(new TestLayer("test.debug.a", "one"));
        server.getScheduler().performOneTick();

        MapLayers.refreshAll();
        MapLayers.stop(); // in the same tick, before the redraw: say a scripted refresh, then /architect reload

        assertNull(set("test.debug.a"));
    }

    @Test
    void changedAllRedrawsEveryLayer() {
        TestLayer a = new TestLayer("test.debug.a", "one");
        TestLayer b = new TestLayer("test.debug.b", "two");
        start(a, b);
        server.getScheduler().performOneTick();

        MapLayers.changedAll();
        server.getScheduler().performOneTick();

        assertEquals(2, a.asked.get());
        assertEquals(2, b.asked.get());
    }
}
