package com.mcmiddleearth.architect.testsupport;

import org.dynmap.markers.AreaMarker;
import org.dynmap.markers.CircleMarker;
import org.dynmap.markers.MarkerAPI;
import org.dynmap.markers.MarkerSet;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;

// dynmap's marker API, kept in memory, so a test can look at the map through dynmap's own marker
// interfaces. It models the calls Architect makes, and keeps dynmap's rules for them: create* returns
// null for an id that is taken, get*Markers returns a copy, and area and circle markers have separate
// ids. Like dynmap 3.x, it stores a plain label HTML-escaped and a description sanitised (<br> becomes
// <br />), so neither reads back exactly as it was given. Every other call throws, so a new dynmap call
// in production code fails loudly here instead of passing against a silent default. It needs no
// server: FakeDynmap wraps it as a plugin.
public class FakeMarkerApi {

    private final Map<String, MarkerSet> markerSets = new LinkedHashMap<>();

    // While set, looking up or creating an area marker throws, as an incompatible dynmap build does:
    // the classes load and the marker set is made, then a marker call fails.
    private volatile boolean failDrawing;

    // Every call that changes a marker or a marker set, so a test can check nothing was rewritten.
    private int writes;

    // setLabel calls: dynmap sends web clients an update for each one, even for the same label.
    private int labelWrites;

    private final MarkerAPI api = proxy(MarkerAPI.class, (method, args) -> switch (method.getName()) {
        case "getMarkerSet" -> markerSets.get((String) args[0]);
        case "getMarkerSets" -> new HashSet<>(markerSets.values());
        case "createMarkerSet" -> markerSets.containsKey((String) args[0])
                ? null : newMarkerSet((String) args[0], (String) args[1]);
        default -> throw unsupported(method);
    });

    public MarkerAPI api() { return api; }

    public void failDrawing(boolean fail) { failDrawing = fail; }

    public int writes() { return writes; }

    public int labelWrites() { return labelWrites; }

    private MarkerSet newMarkerSet(String id, String initialLabel) {
        writes++;
        Map<String, AreaMarker> areas = new LinkedHashMap<>();
        Map<String, CircleMarker> circles = new LinkedHashMap<>();
        boolean[] hideByDefault = {false};
        String[] label = {initialLabel};
        MarkerSet[] self = new MarkerSet[1];
        self[0] = proxy(MarkerSet.class, (method, args) -> {
            if (failDrawing && method.getName().matches("findAreaMarker|createAreaMarker")) {
                throw new NoSuchMethodError("FakeMarkerApi: simulated incompatible dynmap");
            }
            return switch (method.getName()) {
                case "getMarkerSetID" -> id;
                case "getMarkerSetLabel" -> label[0];
                case "setMarkerSetLabel" -> { writes++; label[0] = (String) args[0]; yield null; }
                case "setHideByDefault" -> { writes++; hideByDefault[0] = (Boolean) args[0]; yield null; }
                case "getHideByDefault" -> hideByDefault[0];
                case "getAreaMarkers" -> new HashSet<>(areas.values());
                case "findAreaMarker" -> areas.get((String) args[0]);
                case "createAreaMarker" -> areas.containsKey((String) args[0])
                        ? null : newAreaMarker(self[0], areas, args);
                case "getCircleMarkers" -> new HashSet<>(circles.values());
                case "findCircleMarker" -> circles.get((String) args[0]);
                case "createCircleMarker" -> circles.containsKey((String) args[0])
                        ? null : newCircleMarker(self[0], circles, args);
                case "deleteMarkerSet" -> {
                    writes++;
                    areas.clear();
                    circles.clear();
                    markerSets.remove(id);
                    yield null;
                }
                default -> throw unsupported(method);
            };
        });
        markerSets.put(id, self[0]);
        return self[0];
    }

    // createAreaMarker(id, label, markup, world, double[] x, double[] z, persistent)
    private AreaMarker newAreaMarker(MarkerSet set, Map<String, AreaMarker> areas, Object[] create) {
        writes++;
        String id = (String) create[0];
        Map<String, Object> state = new HashMap<>(Map.of(
                "label", label((String) create[1], (Boolean) create[2]), "world", create[3],
                "x", ((double[]) create[4]).clone(), "z", ((double[]) create[5]).clone(),
                "topY", 64.0, "bottomY", 64.0,
                "fillOpacity", 0.0, "fillColor", 0, "lineWeight", 0, "lineOpacity", 0.0));
        state.put("lineColor", 0);
        AreaMarker[] self = new AreaMarker[1];
        self[0] = proxy(AreaMarker.class, (method, args) -> switch (method.getName()) {
            case "getMarkerID" -> id;
            case "getMarkerSet" -> set;
            case "getLabel" -> state.get("label");
            case "setLabel" -> { writes++; labelWrites++; state.put("label", label(args)); yield null; }
            case "getWorld" -> state.get("world");
            case "isPersistentMarker" -> create[6];
            case "setDescription" -> { writes++; state.put("description", sanitised((String) args[0])); yield null; }
            case "getDescription" -> state.get("description");
            case "setCornerLocations" -> {
                writes++;
                state.put("x", ((double[]) args[0]).clone());
                state.put("z", ((double[]) args[1]).clone());
                yield null;
            }
            case "getCornerCount" -> ((double[]) state.get("x")).length;
            case "getCornerX" -> ((double[]) state.get("x"))[(Integer) args[0]];
            case "getCornerZ" -> ((double[]) state.get("z"))[(Integer) args[0]];
            case "setRangeY" -> { writes++; state.put("topY", args[0]); state.put("bottomY", args[1]); yield null; }
            case "getTopY" -> state.get("topY");
            case "getBottomY" -> state.get("bottomY");
            case "setFillStyle" -> {
                writes++;
                state.put("fillOpacity", args[0]);
                state.put("fillColor", args[1]);
                yield null;
            }
            case "getFillOpacity" -> state.get("fillOpacity");
            case "getFillColor" -> state.get("fillColor");
            case "setLineStyle" -> {
                writes++;
                state.put("lineWeight", args[0]);
                state.put("lineOpacity", args[1]);
                state.put("lineColor", args[2]);
                yield null;
            }
            case "getLineWeight" -> state.get("lineWeight");
            case "getLineOpacity" -> state.get("lineOpacity");
            case "getLineColor" -> state.get("lineColor");
            case "deleteMarker" -> { writes++; areas.remove(id, self[0]); yield null; }
            default -> throw unsupported(method);
        });
        areas.put(id, self[0]);
        return self[0];
    }

    // createCircleMarker(id, label, markup, world, x, y, z, xr, zr, persistent)
    private CircleMarker newCircleMarker(MarkerSet set, Map<String, CircleMarker> circles, Object[] create) {
        writes++;
        String id = (String) create[0];
        Map<String, Object> state = new HashMap<>(Map.of(
                "label", label((String) create[1], (Boolean) create[2]), "world", create[3],
                "x", create[4], "y", create[5], "z", create[6], "radiusX", create[7], "radiusZ", create[8],
                "fillOpacity", 0.0, "fillColor", 0, "lineWeight", 0));
        state.put("lineOpacity", 0.0);
        state.put("lineColor", 0);
        CircleMarker[] self = new CircleMarker[1];
        self[0] = proxy(CircleMarker.class, (method, args) -> switch (method.getName()) {
            case "getMarkerID" -> id;
            case "getMarkerSet" -> set;
            case "getLabel" -> state.get("label");
            case "setLabel" -> { writes++; labelWrites++; state.put("label", label(args)); yield null; }
            case "getWorld" -> state.get("world");
            case "isPersistentMarker" -> create[9];
            case "setDescription" -> { writes++; state.put("description", sanitised((String) args[0])); yield null; }
            case "getDescription" -> state.get("description");
            case "setCenter" -> {
                writes++;
                state.put("world", args[0]);
                state.put("x", args[1]);
                state.put("y", args[2]);
                state.put("z", args[3]);
                yield null;
            }
            case "getCenterX" -> state.get("x");
            case "getCenterY" -> state.get("y");
            case "getCenterZ" -> state.get("z");
            case "setRadius" -> { writes++; state.put("radiusX", args[0]); state.put("radiusZ", args[1]); yield null; }
            case "getRadiusX" -> state.get("radiusX");
            case "getRadiusZ" -> state.get("radiusZ");
            case "setFillStyle" -> {
                writes++;
                state.put("fillOpacity", args[0]);
                state.put("fillColor", args[1]);
                yield null;
            }
            case "getFillOpacity" -> state.get("fillOpacity");
            case "getFillColor" -> state.get("fillColor");
            case "setLineStyle" -> {
                writes++;
                state.put("lineWeight", args[0]);
                state.put("lineOpacity", args[1]);
                state.put("lineColor", args[2]);
                yield null;
            }
            case "getLineWeight" -> state.get("lineWeight");
            case "getLineOpacity" -> state.get("lineOpacity");
            case "getLineColor" -> state.get("lineColor");
            case "deleteMarker" -> { writes++; circles.remove(id, self[0]); yield null; }
            default -> throw unsupported(method);
        });
        circles.put(id, self[0]);
        return self[0];
    }

    // setLabel(label) keeps the marker's plain text; setLabel(label, markup) says which it is.
    private static String label(Object[] args) {
        return label((String) args[0], args.length > 1 && (Boolean) args[1]);
    }

    private static String label(String label, boolean markup) {
        return markup ? label : label.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&#39;");
    }

    private static String sanitised(String description) {
        return description.replace("<br>", "<br />");
    }

    @FunctionalInterface
    private interface Handler {
        Object handle(Method method, Object[] args) throws Throwable;
    }

    @SuppressWarnings("unchecked")
    private static <T> T proxy(Class<T> type, Handler handler) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type},
                (proxy, method, args) -> switch (method.getName()) {
                    case "equals" -> proxy == args[0];
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "toString" -> "Fake" + type.getSimpleName();
                    default -> handler.handle(method, args);
                });
    }

    private static UnsupportedOperationException unsupported(Method method) {
        return new UnsupportedOperationException("FakeMarkerApi does not model "
                + method.getDeclaringClass().getSimpleName() + "." + method.getName());
    }
}
