package com.mcmiddleearth.architect.mapLayers;

import org.bukkit.plugin.Plugin;
import org.dynmap.DynmapAPI;
import org.dynmap.markers.AreaMarker;
import org.dynmap.markers.CircleMarker;
import org.dynmap.markers.GenericMarker;
import org.dynmap.markers.MarkerAPI;
import org.dynmap.markers.MarkerSet;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;

/**
 * Draws map layers through dynmap's marker API. It is the only map-layers class that imports dynmap; the entity
 * logger keeps its own layer. Touch it only once dynmap is enabled: without dynmap's classes it cannot be linked.
 * <p>
 * A layer is updated in place: a marker is created, rewritten when its shape changed, and deleted when its shape is
 * gone, because every change is an update dynmap sends to the web map's clients. Whether a shape changed is judged
 * against the shape drawn last time, not read back from dynmap: dynmap stores labels escaped and descriptions
 * sanitised, so they do not read back as they were given.
 */
final class DynmapBackend implements MapBackend {

    private static final String AREA = "area ";
    private static final String CIRCLE = "circle ";
    /**
     * Every area is drawn flat at Y 64, set on every write: dynmap starts a new area at its world's sea level + 1,
     * and one taken over may have a range of its own. LiveAtlas draws an area with a Y range as a hollow 3D outline,
     * as {@link MapShape.Area} explains.
     */
    private static final double FLAT_Y = 64;

    private final MarkerAPI api;
    /** Per marker set, the shapes drawn last time, by kind and id. */
    private final Map<String, Map<String, MapShape>> drawn = new HashMap<>();

    DynmapBackend(MarkerAPI api) {
        this.api = api;
    }

    /** dynmap's map if dynmap is enabled and compatible; otherwise no map, and the log says why. */
    static MapBackend lookup(Plugin plugin) {
        // isPluginEnabled, not getPlugin() != null: a dynmap that failed to enable still implements DynmapAPI, but
        // its core is null, so the first marker call would fail inside dynmap.
        if (!plugin.getServer().getPluginManager().isPluginEnabled("dynmap")) {
            plugin.getLogger().info("dynmap is not enabled, so Architect's map layers are not drawn.");
            return MapBackend.NONE;
        }
        try {
            MarkerAPI api = ((DynmapAPI) plugin.getServer().getPluginManager().getPlugin("dynmap")).getMarkerAPI();
            if (api != null) {
                return new DynmapBackend(api);
            }
            plugin.getLogger().warning("dynmap has no marker API, so Architect's map layers are not drawn.");
        } catch (RuntimeException | LinkageError e) {
            plugin.getLogger().log(Level.WARNING,
                    "dynmap is not compatible, so Architect's map layers are not drawn.", e);
        }
        return MapBackend.NONE;
    }

    @Override
    public void show(MapLayer layer, List<MapShape> shapes) {
        MarkerSet set = markerSet(layer);
        // taken out while drawing: if drawing fails partway, the next call rewrites every marker
        Map<String, MapShape> before = drawn.remove(layer.markerSetId());
        if (before == null) {
            before = Map.of();
        }
        Map<String, MapShape> now = new HashMap<>();
        for (MapShape shape : shapes) {
            String key = (shape instanceof MapShape.Area ? AREA : CIRCLE) + shape.id();
            if (now.putIfAbsent(key, shape) != null) {
                continue; // a second shape with the same id: the first one is drawn
            }
            switch (shape) {
                case MapShape.Area area -> showArea(set, area, before.get(key));
                case MapShape.Circle circle -> showCircle(set, circle, before.get(key));
            }
        }
        for (AreaMarker marker : set.getAreaMarkers()) {
            if (!now.containsKey(AREA + marker.getMarkerID())) {
                marker.deleteMarker();
            }
        }
        for (CircleMarker marker : set.getCircleMarkers()) {
            if (!now.containsKey(CIRCLE + marker.getMarkerID())) {
                marker.deleteMarker();
            }
        }
        drawn.put(layer.markerSetId(), now);
    }

    @Override
    public void remove(String markerSetId) {
        drawn.remove(markerSetId);
        MarkerSet set = api.getMarkerSet(markerSetId);
        if (set != null) {
            set.deleteMarkerSet();
        }
    }

    /** The layer's marker set: after a plugin reload it still exists, and createMarkerSet would return null. */
    private MarkerSet markerSet(MapLayer layer) {
        MarkerSet set = api.getMarkerSet(layer.markerSetId());
        if (set == null) {
            set = api.createMarkerSet(layer.markerSetId(), layer.label(), null, false);
        }
        if (!layer.label().equals(set.getMarkerSetLabel())) {
            set.setMarkerSetLabel(layer.label());
        }
        if (set.getHideByDefault() != layer.hiddenByDefault()) {
            set.setHideByDefault(layer.hiddenByDefault());
        }
        return set;
    }

    /** Draws the area, unless it is drawn already as it was last time. */
    private static void showArea(MarkerSet set, MapShape.Area shape, MapShape last) {
        AreaMarker marker = set.findAreaMarker(shape.id());
        if (marker != null && shape.equals(last)) {
            return;
        }
        if (marker != null && !marker.getWorld().equals(shape.world())) {
            marker.deleteMarker(); // an area marker's world is fixed when it is made
            marker = null;
        }
        if (marker == null) {
            marker = set.createAreaMarker(shape.id(), shape.label(), false, shape.world(), shape.x(), shape.z(), false);
        } else {
            marker.setCornerLocations(shape.x(), shape.z());
            relabel(marker, shape, last);
        }
        marker.setDescription(shape.description());
        marker.setRangeY(FLAT_Y, FLAT_Y); // on every write: a marker taken over may have a range of its own
        MapShape.Style style = shape.style();
        marker.setLineStyle(style.lineWidth(), style.lineOpacity(), style.lineColor());
        marker.setFillStyle(style.fillOpacity(), style.fillColor());
    }

    /** Draws the circle, unless it is drawn already as it was last time. */
    private static void showCircle(MarkerSet set, MapShape.Circle shape, MapShape last) {
        CircleMarker marker = set.findCircleMarker(shape.id());
        if (marker != null && shape.equals(last)) {
            return;
        }
        if (marker != null && !marker.getWorld().equals(shape.world())) {
            marker.deleteMarker(); // made again, as areas are, so the old world's clients see it go
            marker = null;
        }
        if (marker == null) {
            marker = set.createCircleMarker(shape.id(), shape.label(), false, shape.world(), shape.x(), shape.y(),
                    shape.z(), shape.radiusX(), shape.radiusZ(), false);
        } else {
            marker.setCenter(shape.world(), shape.x(), shape.y(), shape.z());
            marker.setRadius(shape.radiusX(), shape.radiusZ());
            relabel(marker, shape, last);
        }
        marker.setDescription(shape.description());
        MapShape.Style style = shape.style();
        marker.setLineStyle(style.lineWidth(), style.lineOpacity(), style.lineColor());
        marker.setFillStyle(style.fillOpacity(), style.fillColor());
    }

    /** Dynmap sends web clients an update for every label it is given, so only a new one is. */
    private static void relabel(GenericMarker marker, MapShape shape, MapShape last) {
        if (last == null || !last.label().equals(shape.label())) {
            marker.setLabel(shape.label());
        }
    }
}
