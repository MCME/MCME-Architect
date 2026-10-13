package com.mcmiddleearth.architect.mapLayers;

import com.mcmiddleearth.architect.specialBlockHandling.itemBlock.ItemBlockRegion;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;

/** Item-block limit regions: how many item blocks each chunk in them may hold. */
public final class ItemBlockRegionLayer implements MapLayer {

    /** The older layer's id, which LiveAtlas already files under Debug. */
    public static final String ID = "itemBlockLimit.markerset";
    static final String KEY = "itemBlockLimit";

    private final boolean hidden;
    private final MapShape.Style style;
    private final Supplier<? extends Collection<ItemBlockRegion>> regions;

    public ItemBlockRegionLayer(MapLayerConfig.Layer config, Supplier<? extends Collection<ItemBlockRegion>> regions) {
        this.hidden = config.hidden();
        this.style = config.style(0xff0000, 2, 0.15, 0.25);
        this.regions = regions;
    }

    @Override
    public String markerSetId() {
        return ID;
    }

    @Override
    public String label() {
        return "itemBlockLimit";
    }

    @Override
    public boolean hiddenByDefault() {
        return hidden;
    }

    MapShape.Style style() {
        return style;
    }

    /** By name, a stable order. A region that holds no block is left off. */
    @Override
    public List<MapShape> shapes() {
        return regions.get().stream()
                .sorted(Comparator.comparing(ItemBlockRegion::getName))
                .flatMap(region -> RegionShapes.of(region.getRegion(),
                        region.getName().toLowerCase(Locale.ROOT) + ".marker", region.getName(), description(region),
                        style).stream())
                .toList();
    }

    private static String description(ItemBlockRegion region) {
        return "<b>" + MapShape.html(region.getName()) + "</b>"
                + "<br>Limit: " + region.getLimit() + " item blocks per chunk"
                + " (armor stands, item frames and paintings count)"
                + "<br>" + RegionShapes.yRange(region.getRegion())
                + "<br><i>Where regions overlap, the highest limit wins.</i>";
    }
}
