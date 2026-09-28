package com.mcmiddleearth.architect.mapLayers;

import com.mcmiddleearth.architect.serverResoucePack.RpRegion;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;

/** Resource-pack regions: where Architect switches a player's resource pack, and to which pack. */
public final class RpRegionLayer implements MapLayer {

    /** The older layer's id, which LiveAtlas already files under Debug. */
    public static final String ID = "rpregions.markerset";
    static final String KEY = "rpRegions";

    private final boolean hidden;
    private final MapShape.Style style;
    private final Supplier<? extends Collection<RpRegion>> regions;

    public RpRegionLayer(MapLayerConfig.Layer config, Supplier<? extends Collection<RpRegion>> regions) {
        this.hidden = config.hidden();
        this.style = config.style(0x800080, 2, 0.15, 0.25);
        this.regions = regions;
    }

    @Override
    public String markerSetId() {
        return ID;
    }

    @Override
    public String label() {
        return "RpRegions";
    }

    @Override
    public boolean hiddenByDefault() {
        return hidden;
    }

    MapShape.Style style() {
        return style;
    }

    /**
     * Lightest first, then by name: a stable order. It cannot put heavier regions on top on the map: LiveAtlas draws
     * a layer's areas before its circles, and dynmap's marker file keeps no order. A region that holds no block is
     * left off.
     */
    @Override
    public List<MapShape> shapes() {
        return regions.get().stream()
                .sorted(Comparator.comparingInt(RpRegion::getWeight).thenComparing(RpRegion::getName))
                .flatMap(region -> RegionShapes.of(region.getRegion(),
                        region.getName().toLowerCase(Locale.ROOT) + ".marker", region.getName(), description(region),
                        style).stream())
                .toList();
    }

    private static String description(RpRegion region) {
        return "<b>" + RegionShapes.html(region.getName()) + "</b>"
                + "<br>Resource pack: " + (region.getRp() == null || region.getRp().isBlank() ? "none"
                        : RegionShapes.html(region.getRp()))
                + "<br>Weight: " + region.getWeight()
                + "<br>" + RegionShapes.yRange(region.getRegion())
                + "<br><i>Where regions overlap, the highest weight wins.</i>";
    }
}
