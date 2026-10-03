package com.mcmiddleearth.architect.mapLayers;

import java.util.List;

/** Where map layers are drawn. */
interface MapBackend {

    /**
     * Makes the map show exactly these shapes for this layer. Of two areas, or two circles, with the same id, the
     * first is drawn. A failure can leave the layer partly drawn; the next call completes it.
     */
    void show(MapLayer layer, List<MapShape> shapes);

    /** Removes a layer from the map. */
    void remove(String markerSetId);

    /** No map: dynmap is missing, disabled or incompatible. */
    MapBackend NONE = new MapBackend() {
        @Override
        public void show(MapLayer layer, List<MapShape> shapes) {
        }

        @Override
        public void remove(String markerSetId) {
        }
    };
}
