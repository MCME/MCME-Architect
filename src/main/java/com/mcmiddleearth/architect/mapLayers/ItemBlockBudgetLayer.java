package com.mcmiddleearth.architect.mapLayers;

import com.mcmiddleearth.architect.mapLayers.ItemBlockBudget.ChunkKey;
import com.mcmiddleearth.architect.mapLayers.ItemBlockBudget.Count;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Chunks filling up against their item-block limit: one tile per chunk, yellow from warnPercent, orange from
 * highPercent and red when full, where placing an item block is refused.
 * <p>
 * Every report weighs every chunk counted, so each chunk's limit is looked up once, and kept until the limits'
 * version changes or a refresh. For about 10 seconds after a start or {@code /architect reload}, Architect has not
 * loaded its item-block regions yet, so the tiles are judged against the world's base limit. Loading the regions
 * changes the version, and the tiles are judged again. A stop inside that window saves the counts judged that way
 * too, so chunks in regions whose limit is below the base limit can drop out of the file until they are counted
 * again.
 */
public final class ItemBlockBudgetLayer implements MapLayer {

    public static final String ID = "architect.debug.itemblockbudget";
    static final String KEY = "itemBlockBudget";

    /** A chunk's limit: from the item-block region with the highest limit there, or the world's base limit. */
    public record Limit(int value, String region) {}

    /** The limit for the block column (x, z) in a world, or null when that world is not loaded. */
    @FunctionalInterface
    public interface Limits {
        Limit at(String world, int x, int z);

        /** Changes whenever a limit may have changed; until it does, a chunk's limit is not looked up again. */
        default int version() {
            return 0;
        }
    }

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm z", Locale.ENGLISH)
            .withZone(ZoneId.systemDefault());

    private final boolean hidden;
    private final int warnPercent;
    private final int highPercent;
    private final MapShape.Style warn;
    private final MapShape.Style high;
    private final MapShape.Style full;
    private final ItemBlockBudget budget;
    private final Limits limits;
    /** Each chunk's limit, as looked up at the limits' {@link #cachedVersion}. */
    private final Map<ChunkKey, Limit> cache = new HashMap<>();
    private int cachedVersion;

    /** {@code file} keeps the counts across restarts. */
    public ItemBlockBudgetLayer(MapLayerConfig.Layer config, File file, Limits limits) {
        this.hidden = config.hidden();
        // warn is kept from 1 to 100: above 100 it would hide the full chunks. High needs no clamp: a tile shows only
        // from warn, and red wins from 100.
        this.warnPercent = Math.max(1, Math.min(100, config.integer("warnPercent", 50)));
        this.highPercent = config.integer("highPercent", 80);
        double fillOpacity = config.number("areaOpacity", 0.35);
        this.warn = style(config.color("warnColor", 0xffd700), fillOpacity);
        this.high = style(config.color("highColor", 0xff8c00), fillOpacity);
        this.full = style(config.color("fullColor", 0xff0000), fillOpacity);
        this.budget = new ItemBlockBudget(file, () -> MapLayers.changed(ID), System::currentTimeMillis);
        this.limits = limits;
    }

    private static MapShape.Style style(int color, double fillOpacity) {
        return new MapShape.Style(color, 0.6, 1, color, fillOpacity);
    }

    @Override
    public String markerSetId() {
        return ID;
    }

    @Override
    public String label() {
        return "Item-block budget";
    }

    @Override
    public boolean hiddenByDefault() {
        return hidden;
    }

    /** Only the counts the map shows are kept in the file, and those in a world that is not loaded, as they are. */
    @Override
    public void start(Plugin plugin) {
        budget.start(plugin, entry -> {
            Limit limit = limitOf(entry.getKey());
            return limit == null || percent(entry.getValue(), limit) >= warnPercent;
        });
    }

    @Override
    public void stop() {
        budget.stop();
    }

    /** Looks every chunk's limit up again, and recounts the loaded chunks: a refresh starts from scratch. */
    @Override
    public void refresh() {
        cache.clear();
        budget.recountLoaded();
    }

    @Override
    public List<MapShape> shapes() {
        return shapesFor(budget.counts());
    }

    /** Judges every chunk first, then sorts only the tiles it shows. */
    List<MapShape> shapesFor(Map<ChunkKey, Count> counts) {
        List<Tile> tiles = new ArrayList<>();
        for (Map.Entry<ChunkKey, Count> entry : counts.entrySet()) {
            Limit limit = limitOf(entry.getKey());
            if (limit == null) {
                continue; // its world is not loaded
            }
            int percent = percent(entry.getValue(), limit);
            if (percent >= warnPercent) {
                tiles.add(new Tile(entry.getKey(), entry.getValue(), limit, percent));
            }
        }
        tiles.sort(Comparator.comparing((Tile tile) -> tile.chunk().world())
                .thenComparingInt(tile -> tile.chunk().x()).thenComparingInt(tile -> tile.chunk().z()));
        List<MapShape> result = new ArrayList<>();
        for (Tile tile : tiles) {
            ChunkKey chunk = tile.chunk();
            MapShape.Style style = tile.percent() >= 100 ? full : tile.percent() >= highPercent ? high : warn;
            double x1 = chunk.x() * 16;
            double z1 = chunk.z() * 16;
            result.add(new MapShape.Area("budget." + chunk.world() + "." + chunk.x() + "." + chunk.z(),
                    "Chunk " + chunk.x() + ", " + chunk.z(), description(chunk, tile.count(), tile.limit(),
                    tile.percent()), style, chunk.world(), new double[]{x1, x1 + 16, x1 + 16, x1},
                    new double[]{z1, z1, z1 + 16, z1 + 16}));
        }
        return result;
    }

    /** A chunk the layer shows, with what it was judged by. */
    private record Tile(ChunkKey chunk, Count count, Limit limit, int percent) {}

    /**
     * Read at the chunk's centre: a region edge can cross a chunk, while placing checks the exact block. Kept until
     * the limits' version changes or a refresh; a world that is not loaded is asked about again, as it may load.
     */
    private Limit limitOf(ChunkKey chunk) {
        int version = limits.version();
        if (version != cachedVersion) {
            cache.clear();
            cachedVersion = version;
        }
        Limit limit = cache.get(chunk);
        if (limit == null) {
            limit = limits.at(chunk.world(), chunk.x() * 16 + 8, chunk.z() * 16 + 8);
            if (limit != null) {
                cache.put(chunk, limit);
            }
        }
        return limit;
    }

    /** A limit of 0 or less is full as soon as anything counts. */
    private static int percent(Count count, Limit limit) {
        return limit.value() <= 0 ? Integer.MAX_VALUE : (int) (100L * count.total() / limit.value());
    }

    private static String description(ChunkKey chunk, Count count, Limit limit, int percent) {
        return "<b>Chunk " + chunk.x() + ", " + chunk.z() + "</b> in " + MapShape.html(chunk.world())
                + "<br>" + plural(count.armorStands(), "armor stand") + ", " + plural(count.itemFrames(), "item frame")
                + ", " + plural(count.paintings(), "painting") + " = " + count.total() + " of " + limit.value()
                + (limit.region() == null ? " (world base limit)"
                        : " (region '" + MapShape.html(limit.region()) + "')")
                + (percent >= 100 ? "<br><b>Full: placing an item block here is refused.</b>" : "")
                + "<br>Last changed " + TIME.format(Instant.ofEpochMilli(count.changedAt()))
                + "<br><i>The limit is read at the chunk's centre; a region edge can cross a chunk.</i>";
    }

    private static String plural(int n, String what) {
        return n + " " + what + (n == 1 ? "" : "s");
    }
}
