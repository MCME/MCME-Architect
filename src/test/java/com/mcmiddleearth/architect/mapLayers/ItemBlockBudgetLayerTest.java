package com.mcmiddleearth.architect.mapLayers;

import com.mcmiddleearth.architect.mapLayers.ItemBlockBudget.ChunkKey;
import com.mcmiddleearth.architect.mapLayers.ItemBlockBudget.Count;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.MockBukkit;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class ItemBlockBudgetLayerTest {

    private static final long NOON = LocalDateTime.of(2026, 10, 2, 14, 3)
            .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();

    /** A region called Market & Co (limit 20) east of x = 1000; the world's base limit of 5 elsewhere. */
    private static final ItemBlockBudgetLayer.Limits LIMITS = (world, x, z) -> x >= 1000
            ? new ItemBlockBudgetLayer.Limit(20, "Market & Co") : new ItemBlockBudgetLayer.Limit(5, null);

    private static ItemBlockBudgetLayer layer(YamlConfiguration config) {
        return new ItemBlockBudgetLayer(new MapLayerConfig(config).layer(ItemBlockBudgetLayer.KEY, false),
                new File("unused.yml"), LIMITS);
    }

    private static Map<ChunkKey, Count> counts(Object... chunkAndCount) {
        Map<ChunkKey, Count> result = new LinkedHashMap<>();
        for (int i = 0; i < chunkAndCount.length; i += 2) {
            result.put((ChunkKey) chunkAndCount[i], (Count) chunkAndCount[i + 1]);
        }
        return result;
    }

    /** Limits that count their lookups: one limit everywhere, which a test can change, with or without the version. */
    private static final class CountingLimits implements ItemBlockBudgetLayer.Limits {
        final AtomicInteger lookups = new AtomicInteger();
        int limit = 5;
        int version;

        @Override
        public ItemBlockBudgetLayer.Limit at(String world, int x, int z) {
            lookups.incrementAndGet();
            return new ItemBlockBudgetLayer.Limit(limit, null);
        }

        @Override
        public int version() {
            return version;
        }
    }

    private static ItemBlockBudgetLayer layer(CountingLimits limits, File file) {
        return new ItemBlockBudgetLayer(new MapLayerConfig(new YamlConfiguration()).layer(ItemBlockBudgetLayer.KEY,
                false), file, limits);
    }

    @Test
    void onlyChunksFillingUpAreShownInTheirLevelsColour() {
        List<MapShape> shapes = layer(new YamlConfiguration()).shapesFor(counts(
                new ChunkKey("world", 0, 0), new Count(2, 0, 0, NOON),    // 40% of 5: not shown
                new ChunkKey("world", 1, 0), new Count(3, 0, 0, NOON),    // 60%: yellow
                new ChunkKey("world", 2, 0), new Count(4, 0, 0, NOON),    // 80%: orange
                new ChunkKey("world", 3, 0), new Count(3, 1, 1, NOON),    // 100%: red
                new ChunkKey("world", 4, 0), new Count(4, 2, 0, NOON),    // 120%: red too
                new ChunkKey("world", 70, 0), new Count(10, 0, 0, NOON))); // 50% of the region's 20: yellow

        assertEquals(List.of("budget.world.1.0", "budget.world.2.0", "budget.world.3.0", "budget.world.4.0",
                "budget.world.70.0"), shapes.stream().map(MapShape::id).toList());
        assertEquals(0xffd700, shapes.get(0).style().fillColor(), "yellow from 50%");
        assertEquals(0xff8c00, shapes.get(1).style().fillColor(), "orange from 80%");
        assertEquals(0xff0000, shapes.get(2).style().fillColor(), "red when full");
        assertEquals(0xff0000, shapes.get(3).style().fillColor(), "and over the limit, as after a limit is lowered");
        MapShape.Area tile = (MapShape.Area) shapes.get(1);
        assertArrayEquals(new double[]{32, 48, 48, 32}, tile.x(), "one chunk, 16 blocks wide");
        assertArrayEquals(new double[]{0, 0, 16, 16}, tile.z());
    }

    @Test
    void thePopupAddsUpTheChunkAndNamesItsLimit() {
        List<MapShape> shapes = layer(new YamlConfiguration()).shapesFor(counts(
                new ChunkKey("world", 3, 0), new Count(3, 1, 1, NOON),
                new ChunkKey("world", 70, 0), new Count(12, 3, 1, NOON)));

        String base = shapes.get(0).description();
        String region = shapes.get(1).description();
        assertTrue(base.contains("3 armor stands, 1 item frame, 1 painting = 5 of 5 (world base limit)"), base);
        assertTrue(base.contains("Full: placing an item block here is refused"), base);
        assertTrue(base.contains("Last changed 2026-10-02 14:03"), base);
        assertTrue(base.contains("at the chunk's centre"), base);
        assertTrue(region.contains("= 16 of 20 (region 'Market &amp; Co')"), region);
        assertFalse(region.contains("Full"), region);
    }

    @Test
    void theStepsCanBeChanged() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("mapLayers.itemBlockBudget.warnPercent", 20);
        config.set("mapLayers.itemBlockBudget.highPercent", 40);

        List<MapShape> shapes = layer(config).shapesFor(counts(new ChunkKey("world", 0, 0), new Count(1, 0, 0, NOON),
                new ChunkKey("world", 1, 0), new Count(2, 0, 0, NOON)));

        assertEquals(2, shapes.size(), "20% of 5 is shown now");
        assertEquals(0xffd700, shapes.get(0).style().fillColor());
        assertEquals(0xff8c00, shapes.get(1).style().fillColor(), "and 40% is orange");
    }

    @Test
    void theStepsAreKeptInRange() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("mapLayers.itemBlockBudget.warnPercent", 150);
        config.set("mapLayers.itemBlockBudget.highPercent", 20);

        List<MapShape> shapes = layer(config).shapesFor(counts(new ChunkKey("world", 0, 0), new Count(4, 0, 0, NOON),
                new ChunkKey("world", 1, 0), new Count(5, 0, 0, NOON)));

        assertEquals(1, shapes.size(), "a warn step above 100 counts as 100: a full chunk still shows, 80% does not");
        assertEquals(0xff0000, shapes.get(0).style().fillColor());
    }

    @Test
    void aZeroLimitMeansFullAsSoonAsAnythingCounts() {
        ItemBlockBudgetLayer layer = new ItemBlockBudgetLayer(new MapLayerConfig(new YamlConfiguration())
                .layer(ItemBlockBudgetLayer.KEY, false), new File("unused.yml"),
                (world, x, z) -> new ItemBlockBudgetLayer.Limit(0, null));

        List<MapShape> shapes = layer.shapesFor(counts(new ChunkKey("world", 0, 0), new Count(1, 0, 0, NOON)));

        assertEquals(0xff0000, shapes.get(0).style().fillColor());
    }

    // Placing checks the exact block; the map reads one column per chunk, and the popup says it is the centre.
    @Test
    void theLimitIsReadAtEachChunksCentre() {
        List<String> asked = new ArrayList<>();
        ItemBlockBudgetLayer layer = new ItemBlockBudgetLayer(new MapLayerConfig(new YamlConfiguration())
                .layer(ItemBlockBudgetLayer.KEY, false), new File("unused.yml"), (world, x, z) -> {
                    asked.add(world + " " + x + " " + z);
                    return new ItemBlockBudgetLayer.Limit(-1, null);
                });

        MapShape.Area tile = (MapShape.Area) layer.shapesFor(counts(new ChunkKey("world", -1, -2),
                new Count(1, 0, 0, NOON))).get(0);

        assertEquals(List.of("world -8 -24"), asked, "the chunk holds blocks -16 to -1 and -32 to -17");
        assertArrayEquals(new double[]{-16, 0, 0, -16}, tile.x());
        assertArrayEquals(new double[]{-32, -32, -16, -16}, tile.z());
        assertEquals(0xff0000, tile.style().fillColor(), "a negative limit refuses every item block, as 0 does");
    }

    @Test
    void theLayerIsFiledUnderDebug() {
        ItemBlockBudgetLayer layer = layer(new YamlConfiguration());

        assertTrue(layer.markerSetId().contains("debug"));
        assertEquals("Item-block budget", layer.label());
        assertTrue(layer.hiddenByDefault());
    }

    @Test
    void aChangedLimitShowsAtTheNextReportOnceTheVersionMoves() {
        CountingLimits limits = new CountingLimits();
        ItemBlockBudgetLayer layer = layer(limits, new File("unused.yml"));
        Map<ChunkKey, Count> counts = counts(new ChunkKey("world", 0, 0), new Count(3, 0, 0, NOON));
        assertEquals(0xffd700, layer.shapesFor(counts).get(0).style().fillColor(), "3 of 5: yellow");

        limits.limit = 3;
        assertEquals(0xffd700, layer.shapesFor(counts).get(0).style().fillColor(), "kept until the version moves");
        limits.version++;

        assertEquals(0xff0000, layer.shapesFor(counts).get(0).style().fillColor(), "3 of the new limit of 3: full");
    }

    // A report weighs every chunk counted, and a lookup tests every item-block region: so each chunk is looked up once.
    @Test
    void anUnchangedVersionLooksNoLimitUpAgain(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("itemBlockBudget.yml");
        Files.writeString(file, "chunks:\n- world;0;0;3;0;0;500\n- world;1;0;1;0;0;500\n");
        CountingLimits limits = new CountingLimits();
        limits.version = 7; // as on a server, where loading the regions has moved it on
        ItemBlockBudgetLayer layer = layer(limits, file.toFile());
        MockBukkit.mock();
        try {
            layer.start(MockBukkit.createMockPlugin());
            layer.shapes();
            layer.shapes();
            layer.stop(); // saves the counts the map shows
        } finally {
            MockBukkit.unmock();
        }

        assertEquals(2, limits.lookups.get(), "neither the second report nor the save looks a limit up again");
        assertFalse(Files.readString(file).contains("world;1;0;"), "1 of 5 is not kept");
    }

    // A refresh starts from scratch: a limit the version never saw change (a world unloaded since) is read again.
    @Test
    void aRefreshLooksEveryLimitUpAgain(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("itemBlockBudget.yml");
        Files.writeString(file, "chunks:\n- world;0;0;3;0;0;500\n");
        CountingLimits limits = new CountingLimits();
        ItemBlockBudgetLayer layer = layer(limits, file.toFile());
        MockBukkit.mock();
        try {
            layer.start(MockBukkit.createMockPlugin());
            layer.shapes();
            layer.refresh();
            layer.shapes();
            layer.stop();
        } finally {
            MockBukkit.unmock();
        }

        assertEquals(2, limits.lookups.get(), "looked up again after the refresh, although the version is the same");
    }
}
