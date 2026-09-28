package com.mcmiddleearth.architect.mapLayers;

import java.util.List;

/** One layer on the web map, such as the RP regions. */
public interface MapLayer {

    /** Dynmap's marker set id. LiveAtlas files ids that contain "debug" under its Debug group. */
    String markerSetId();

    /** The name in the map's layer list. */
    String label();

    boolean hiddenByDefault();

    /** The layer's complete current shapes. */
    List<MapShape> shapes();
}
