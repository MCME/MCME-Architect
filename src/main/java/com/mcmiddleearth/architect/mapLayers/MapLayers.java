package com.mcmiddleearth.architect.mapLayers;

import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.server.PluginDisableEvent;
import org.bukkit.event.server.PluginEnableEvent;
import org.bukkit.event.world.WorldLoadEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.logging.Level;

/**
 * Keeps Architect's layers on the web map. A feature calls {@link #changed} when its data changes, and the changed
 * layers are drawn together on the next tick, so a burst of changes costs one redraw. Main thread only: every caller
 * is a command, an event or a scheduled task.
 * <p>
 * The map is looked up at the first redraw, because Architect enables before dynmap does, and again whenever dynmap
 * starts again, since that replaces its marker API. A layer that fails to build keeps its old markers; a map that
 * fails is switched off until {@code /architect maplayers refresh}, or until dynmap or Architect starts again.
 */
public final class MapLayers {

    private static MapLayers active;

    private final Plugin plugin;
    private final Map<String, MapLayer> layers = new LinkedHashMap<>();
    private final Function<Plugin, MapBackend> lookup;
    private final Set<String> changed = new LinkedHashSet<>();
    private final Set<String> reported = new HashSet<>();
    private final Listener watch = new ServerWatch();
    private MapBackend backend;
    /** A map that failed: kept only to take the layers off it at stop, if it still can. */
    private MapBackend failed;
    private BukkitTask redraw;
    /** Set by a refresh: the next redraw looks the map up afresh, and a stop before then still has the old one. */
    private boolean lookAgain;

    private MapLayers(Plugin plugin, List<MapLayer> layers, Function<Plugin, MapBackend> lookup) {
        this.plugin = plugin;
        this.lookup = lookup;
        layers.forEach(layer -> this.layers.put(layer.markerSetId(), layer));
    }

    /**
     * Starts with these layers, all drawn on the next tick. A running instance is stopped first, which takes its
     * layers off the map. {@code /architect reload} stops the layers itself, before it reloads Architect's data,
     * then starts only those the config switches on.
     */
    public static void start(Plugin plugin, List<MapLayer> layers) {
        start(plugin, layers, MapLayers::lookupMap);
    }

    static void start(Plugin plugin, List<MapLayer> layers, Function<Plugin, MapBackend> lookup) {
        stop();
        active = new MapLayers(plugin, layers, lookup);
        plugin.getServer().getPluginManager().registerEvents(active.watch, plugin);
        for (MapLayer layer : layers) {
            try {
                layer.start(plugin);
            } catch (RuntimeException | LinkageError e) {
                plugin.getLogger().log(Level.WARNING, "Map layer " + layer.markerSetId() + " could not start", e);
            }
        }
        changedAll();
    }

    /**
     * dynmap's map, or none. DynmapBackend is touched only once dynmap is enabled: without dynmap's classes it cannot
     * even be linked, and Architect must start on servers without dynmap.
     */
    private static MapBackend lookupMap(Plugin plugin) {
        if (!plugin.getServer().getPluginManager().isPluginEnabled("dynmap")) {
            plugin.getLogger().info("dynmap is not enabled, so Architect's map layers are not drawn.");
            return MapBackend.NONE;
        }
        return DynmapBackend.lookup(plugin);
    }

    /** Stops Architect's layers, so those that keep data (the budget) save it, and takes them off the map. */
    public static void stop() {
        if (active != null) {
            active.shutdown();
            active = null;
        }
    }

    /** The layer's data changed; it is drawn again on the next tick. */
    public static void changed(String markerSetId) {
        if (active != null && active.layers.containsKey(markerSetId)) {
            active.mark(List.of(markerSetId));
        }
    }

    /** Every layer is drawn again on the next tick. */
    public static void changedAll() {
        if (active != null) {
            active.mark(active.layers.keySet());
        }
    }

    /** What {@link #refreshAll} found, for the command's reply. */
    public enum Refresh {
        /** Map layers are not running, so there was nothing to refresh. */
        NOT_RUNNING,
        /** The layers gathered their data again, but dynmap is not enabled, so nothing is drawn. */
        NO_MAP,
        /** The layers gathered their data again, and are drawn on the next tick. */
        DRAWN
    }

    /**
     * For {@code /architect maplayers refresh}: every layer gathers its own data again, and everything is drawn again
     * on a map looked up afresh, with nothing cached. That also restores markers edited by hand, tries a map that
     * failed once more, and logs again why a layer cannot be built.
     */
    public static Refresh refreshAll() {
        if (active == null) {
            return Refresh.NOT_RUNNING;
        }
        for (MapLayer layer : active.layers.values()) {
            try {
                layer.refresh();
            } catch (RuntimeException | LinkageError e) {
                active.plugin.getLogger().log(Level.WARNING, "Map layer " + layer.markerSetId()
                        + " could not refresh", e);
            }
        }
        active.lookAgain = true;
        active.reported.clear();
        changedAll();
        return active.plugin.getServer().getPluginManager().isPluginEnabled("dynmap") ? Refresh.DRAWN
                : Refresh.NO_MAP;
    }

    private void mark(Collection<String> ids) {
        changed.addAll(ids);
        if (redraw == null) {
            redraw = plugin.getServer().getScheduler().runTask(plugin, this::redraw);
        }
    }

    private void redraw() {
        redraw = null;
        if (backend == null || lookAgain) {
            lookAgain = false;
            try {
                backend = lookup.apply(plugin);
            } catch (RuntimeException | LinkageError e) {
                plugin.getLogger().log(Level.WARNING,
                        "dynmap is not compatible, so Architect's map layers are not drawn.", e);
                backend = MapBackend.NONE;
            }
        }
        List<String> ids = new ArrayList<>(changed);
        changed.clear();
        for (String id : ids) {
            if (backend == MapBackend.NONE) {
                return;
            }
            MapLayer layer = layers.get(id);
            List<MapShape> shapes;
            try {
                shapes = List.copyOf(layer.shapes()); // a null list or shape fails here, inside its layer
            } catch (RuntimeException | LinkageError e) {
                if (reported.add(id)) {
                    plugin.getLogger().log(Level.WARNING,
                            "Map layer " + id + " could not be built, so it keeps its old markers", e);
                }
                continue;
            }
            try {
                backend.show(layer, shapes);
            } catch (RuntimeException | LinkageError e) {
                plugin.getLogger().log(Level.WARNING, "The web map failed, so Architect's map layers are switched off"
                        + " until /architect maplayers refresh, or until dynmap or Architect starts again", e);
                failed = backend;
                backend = MapBackend.NONE;
            }
        }
    }

    private void shutdown() {
        HandlerList.unregisterAll(watch);
        if (redraw != null) {
            redraw.cancel();
            redraw = null;
        }
        for (MapLayer layer : layers.values()) {
            try {
                layer.stop();
            } catch (RuntimeException | LinkageError e) {
                plugin.getLogger().log(Level.WARNING, "Map layer " + layer.markerSetId() + " could not stop", e);
            }
        }
        MapBackend map = backend != null && backend != MapBackend.NONE ? backend : failed;
        if (map == null) {
            return;
        }
        for (String id : layers.keySet()) {
            try {
                map.remove(id);
            } catch (RuntimeException | LinkageError e) {
                plugin.getLogger().log(Level.WARNING, "Map layer " + id + " could not be removed from the web map", e);
            }
        }
    }

    /**
     * What else calls for a redraw: a dynmap restart makes a new marker API (dynmap disables and enables, as
     * {@code /dynmap reload} does on builds that have one), and a world loaded later ({@code /mv load}) has no-physics
     * areas and budget tiles the layers left out while it was not loaded. Its RP and item-block regions still need
     * {@code /architect reload}: their managers drop, at a start or reload, a region whose world is not loaded yet.
     */
    private final class ServerWatch implements Listener {

        @EventHandler
        public void onDynmapDisabled(PluginDisableEvent event) {
            if (event.getPlugin().getName().equals("dynmap")) {
                backend = MapBackend.NONE; // until dynmap is back
                failed = null;
            }
        }

        @EventHandler
        public void onDynmapEnabled(PluginEnableEvent event) {
            if (event.getPlugin().getName().equals("dynmap")) {
                backend = null; // looked up again at the next redraw
                failed = null;
                mark(layers.keySet());
            }
        }

        @EventHandler
        public void onWorldLoaded(WorldLoadEvent event) {
            mark(layers.keySet());
        }
    }
}
