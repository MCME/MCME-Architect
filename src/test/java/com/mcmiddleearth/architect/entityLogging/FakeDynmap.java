package com.mcmiddleearth.architect.entityLogging;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginDescriptionFile;
import org.bukkit.plugin.java.JavaPlugin;
import org.dynmap.DynmapAPI;
import org.dynmap.markers.AreaMarker;
import org.dynmap.markers.MarkerAPI;
import org.dynmap.markers.MarkerSet;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;

// A stand-in for the dynmap plugin that keeps its markers in memory, so a test can look at the
// map through dynmap's own marker interfaces. It models only what the entity log uses, and keeps
// dynmap's rules for it: createMarkerSet and createAreaMarker return null for an id that is taken,
// and getAreaMarkers returns a copy. Every other call throws, so a new dynmap call in production
// code fails loudly here instead of passing against a silent default.
//
// Load it AFTER Architect, as on the server (Architect is load: STARTUP, dynmap is not):
//     MockBukkit.loadWith(FakeDynmap.class, FakeDynmap.description())
public class FakeDynmap extends JavaPlugin implements DynmapAPI {

    private final Map<String, MarkerSet> markerSets = new LinkedHashMap<>();

    // While set, looking up or creating an area marker throws, as an incompatible dynmap build
    // does: the classes load and the marker set is made, then a marker call fails.
    private volatile boolean failDrawing;

    public void failDrawing(boolean fail) { failDrawing = fail; }

    private final MarkerAPI markerAPI = proxy(MarkerAPI.class, (method, args) -> switch (method.getName()) {
        case "getMarkerSet" -> markerSets.get((String) args[0]);
        case "getMarkerSets" -> new HashSet<>(markerSets.values());
        case "createMarkerSet" -> markerSets.containsKey((String) args[0])
                ? null : newMarkerSet((String) args[0], (String) args[1]);
        default -> throw unsupported(method);
    });

    public static PluginDescriptionFile description() {
        return new PluginDescriptionFile("dynmap", "3.5-fake", FakeDynmap.class.getName());
    }

    private MarkerSet newMarkerSet(String id, String label) {
        Map<String, AreaMarker> areas = new LinkedHashMap<>();
        boolean[] hideByDefault = {false};
        MarkerSet[] self = new MarkerSet[1];
        self[0] = proxy(MarkerSet.class, (method, args) -> {
            if (failDrawing && method.getName().matches("findAreaMarker|createAreaMarker")) {
                throw new NoSuchMethodError("FakeDynmap: simulated incompatible dynmap");
            }
            return switch (method.getName()) {
                case "getMarkerSetID" -> id;
                case "getMarkerSetLabel" -> label;
                case "setHideByDefault" -> { hideByDefault[0] = (Boolean) args[0]; yield null; }
                case "getHideByDefault" -> hideByDefault[0];
                case "getAreaMarkers" -> new HashSet<>(areas.values());
                case "findAreaMarker" -> areas.get((String) args[0]);
                case "createAreaMarker" -> areas.containsKey((String) args[0])
                        ? null : newAreaMarker(self[0], areas, args);
                case "deleteMarkerSet" -> { areas.clear(); markerSets.remove(id); yield null; }
                default -> throw unsupported(method);
            };
        });
        markerSets.put(id, self[0]);
        return self[0];
    }

    // createAreaMarker(id, label, markup, world, double[] x, double[] z, persistent)
    private static AreaMarker newAreaMarker(MarkerSet set, Map<String, AreaMarker> areas, Object[] create) {
        String id = (String) create[0];
        Map<String, Object> state = new HashMap<>(Map.of(
                "label", create[1], "world", create[3],
                "x", ((double[]) create[4]).clone(), "z", ((double[]) create[5]).clone(),
                "fillOpacity", 0.0, "fillColor", 0, "lineWeight", 0, "lineOpacity", 0.0, "lineColor", 0));
        AreaMarker marker = proxy(AreaMarker.class, (method, args) -> switch (method.getName()) {
            case "getMarkerID" -> id;
            case "getMarkerSet" -> set;
            case "getLabel" -> state.get("label");
            case "getWorld" -> state.get("world");
            case "isPersistentMarker" -> create[6];
            case "setDescription" -> { state.put("description", args[0]); yield null; }
            case "getDescription" -> state.get("description");
            case "setCornerLocations" -> {
                state.put("x", ((double[]) args[0]).clone());
                state.put("z", ((double[]) args[1]).clone());
                yield null;
            }
            case "getCornerCount" -> ((double[]) state.get("x")).length;
            case "getCornerX" -> ((double[]) state.get("x"))[(Integer) args[0]];
            case "getCornerZ" -> ((double[]) state.get("z"))[(Integer) args[0]];
            case "setFillStyle" -> { state.put("fillOpacity", args[0]); state.put("fillColor", args[1]); yield null; }
            case "getFillOpacity" -> state.get("fillOpacity");
            case "getFillColor" -> state.get("fillColor");
            case "setLineStyle" -> {
                state.put("lineWeight", args[0]);
                state.put("lineOpacity", args[1]);
                state.put("lineColor", args[2]);
                yield null;
            }
            case "getLineWeight" -> state.get("lineWeight");
            case "getLineOpacity" -> state.get("lineOpacity");
            case "getLineColor" -> state.get("lineColor");
            case "deleteMarker" -> { areas.remove(id); yield null; }
            default -> throw unsupported(method);
        });
        areas.put(id, marker);
        return marker;
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
        return new UnsupportedOperationException("FakeDynmap does not model "
                + method.getDeclaringClass().getSimpleName() + "." + method.getName());
    }

    // ---- DynmapAPI: only the marker API is modelled ----

    @Override public MarkerAPI getMarkerAPI() { return markerAPI; }
    @Override public boolean markerAPIInitialized() { return true; }

    @Override public int triggerRenderOfVolume(Location l0, Location l1) { throw no(); }
    @Override public void setPlayerVisiblity(Player player, boolean visible) { throw no(); }
    @Override public boolean getPlayerVisbility(Player player) { throw no(); }
    @Override public void postPlayerMessageToWeb(Player player, String message) { throw no(); }
    @Override public void postPlayerJoinQuitToWeb(Player player, boolean isJoin) { throw no(); }
    @Override public String getDynmapVersion() { throw no(); }
    @Override public void assertPlayerInvisibility(Player player, boolean invisible, Plugin plugin) { throw no(); }
    @Override public void assertPlayerVisibility(Player player, boolean visible, Plugin plugin) { throw no(); }
    @Override public boolean sendBroadcastToWeb(String sender, String message) { throw no(); }
    @Override public int triggerRenderOfVolume(String wid, int minx, int miny, int minz, int maxx, int maxy, int maxz) { throw no(); }
    @Override public int triggerRenderOfBlock(String wid, int x, int y, int z) { throw no(); }
    @Override public void setPauseFullRadiusRenders(boolean dopause) { throw no(); }
    @Override public boolean getPauseFullRadiusRenders() { throw no(); }
    @Override public void setPauseUpdateRenders(boolean dopause) { throw no(); }
    @Override public boolean getPauseUpdateRenders() { throw no(); }
    @Override public void setPlayerVisiblity(String player, boolean visible) { throw no(); }
    @Override public boolean getPlayerVisbility(String player) { throw no(); }
    @Override public void assertPlayerInvisibility(String player, boolean invisible, String plugin_id) { throw no(); }
    @Override public void assertPlayerVisibility(String player, boolean visible, String plugin_id) { throw no(); }
    @Override public void postPlayerMessageToWeb(String playerid, String playerdisplay, String message) { throw no(); }
    @Override public void postPlayerJoinQuitToWeb(String playerid, String playerdisplay, boolean isjoin) { throw no(); }
    @Override public String getDynmapCoreVersion() { throw no(); }
    @Override public boolean setDisableChatToWebProcessing(boolean disable) { throw no(); }
    @Override public boolean testIfPlayerVisibleToPlayer(String player, String player_to_see) { throw no(); }
    @Override public boolean testIfPlayerInfoProtected() { throw no(); }
    @Override public void processSignChange(String material, String world, int x, int y, int z, String[] lines, String playerid) { throw no(); }

    private static UnsupportedOperationException no() {
        return new UnsupportedOperationException("FakeDynmap models only the marker API");
    }
}
