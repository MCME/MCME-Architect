package com.mcmiddleearth.architect.mapLayers;

import com.mcmiddleearth.architect.serverResoucePack.RpRegion;
import com.mcmiddleearth.architect.specialBlockHandling.itemBlock.ItemBlockRegion;
import com.sk89q.worldedit.math.BlockVector2;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.math.Vector2;
import com.sk89q.worldedit.regions.CuboidRegion;
import com.sk89q.worldedit.regions.CylinderRegion;
import com.sk89q.worldedit.regions.Polygonal2DRegion;
import com.sk89q.worldedit.regions.Region;
import com.sk89q.worldedit.world.NullWorld;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RegionLayersTest {

    private static final MapLayerConfig.Layer DEFAULTS = new MapLayerConfig(new YamlConfiguration()).layer("x", true);

    private static CuboidRegion box(int x1, int z1, int x2, int z2) {
        return new CuboidRegion(NullWorld.getInstance(), BlockVector3.at(x1, 0, z1), BlockVector3.at(x2, 255, z2));
    }

    private static RpRegion rp(String name, String pack, int weight, Region region) {
        RpRegion result = new RpRegion(name, region);
        result.setRp(pack);
        result.setWeight(weight);
        return result;
    }

    @Test
    void rpRegionsAreListedLightestFirst() {
        RpRegionLayer layer = new RpRegionLayer(DEFAULTS, () -> List.of(
                rp("Minas", "Gondor", 5, box(0, 0, 10, 10)),
                rp("Pelennor", "Gondor", 1, box(-100, -100, 100, 100)),
                rp("Anorien", "Gondor", 1, box(200, 200, 300, 300)),
                rp("Tower", "Human", 9, new CylinderRegion(NullWorld.getInstance(), BlockVector3.at(5, 0, 5),
                        Vector2.at(3, 3), 0, 255))));

        List<MapShape> shapes = layer.shapes();

        assertEquals(List.of("anorien.marker", "pelennor.marker", "minas.marker", "tower.marker"),
                shapes.stream().map(MapShape::id).toList(),
                "the ids the older layer used; equal weights by name");
        assertInstanceOf(MapShape.Circle.class, shapes.get(3), "a round selection stays round");
        assertEquals("Minas", shapes.get(2).label());
        assertEquals(new MapShape.Style(0x800080, 0.15, 2, 0x800080, 0.25), shapes.get(0).style());
        assertEquals("rpregions.markerset", layer.markerSetId());
        assertEquals("RpRegions", layer.label());
    }

    @Test
    void anRpRegionsPopupSaysItsPackWeightAndHeights() {
        RpRegionLayer layer = new RpRegionLayer(DEFAULTS,
                () -> List.of(rp("Minas<1>", "Gondor", 5, box(0, 0, 10, 10))));

        String popup = layer.shapes().get(0).description();

        assertTrue(popup.contains("Minas&lt;1&gt;"), popup);
        assertTrue(popup.contains("Resource pack: Gondor"), popup);
        assertTrue(popup.contains("Weight: 5"), popup);
        assertTrue(popup.contains("Y 0 to 255"), popup);
        assertTrue(popup.contains("highest weight wins"), popup);
    }

    @Test
    void anRpRegionWithoutAPackSaysNone() {
        RpRegionLayer layer = new RpRegionLayer(DEFAULTS, () -> List.of(rp("Unset", "", 1, box(0, 0, 10, 10)),
                rp("Missing", null, 2, box(20, 20, 30, 30))));

        List<MapShape> shapes = layer.shapes();

        assertTrue(shapes.get(0).description().contains("Resource pack: none"), shapes.get(0).description());
        assertTrue(shapes.get(1).description().contains("Resource pack: none"), shapes.get(1).description());
    }

    @Test
    void aRegionThatHoldsNoBlockIsLeftOffAndTheRestDrawn() {
        RpRegionLayer layer = new RpRegionLayer(DEFAULTS, () -> List.of(
                rp("Broken", "Gondor", 1, new Polygonal2DRegion(NullWorld.getInstance(),
                        List.of(BlockVector2.at(0, 0), BlockVector2.at(10, 0)), 0, 255)),
                rp("Minas", "Gondor", 5, box(0, 0, 10, 10))));

        assertEquals(List.of("minas.marker"), layer.shapes().stream().map(MapShape::id).toList(),
                "a polygon left with 2 points by /rp edit covers nothing");
    }

    @Test
    void itemBlockRegionsShowTheirLimitPerChunk() {
        ItemBlockRegion region = new ItemBlockRegion("Market & <Bazaar>", box(0, 0, 40, 40));
        region.setLimit(20);
        ItemBlockRegionLayer layer = new ItemBlockRegionLayer(DEFAULTS, () -> List.of(region));

        MapShape shape = layer.shapes().get(0);

        assertEquals("market & <bazaar>.marker", shape.id());
        assertEquals("Market & <Bazaar>", shape.label(), "a plain label: dynmap escapes it");
        assertTrue(shape.description().contains("Market &amp; &lt;Bazaar&gt;"), shape.description());
        assertTrue(shape.description().contains("Y 0 to 255"), shape.description());
        assertEquals("itemBlockLimit.markerset", layer.markerSetId());
        assertEquals("itemBlockLimit", layer.label());
        assertTrue(shape.description().contains("Limit: 20 item blocks per chunk"), shape.description());
        assertTrue(shape.description().contains("armor stands, item frames and paintings count"), shape.description());
        assertTrue(shape.description().contains("highest limit wins"), shape.description());
    }

    @Test
    void theLayersTakeTheirColourAndVisibilityFromTheConfig() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("mapLayers.rpRegions.hidden", false);
        config.set("mapLayers.rpRegions.color", "#00ff00");

        RpRegionLayer rp = new RpRegionLayer(new MapLayerConfig(config).layer(RpRegionLayer.KEY, true),
                () -> List.of(rp("Minas", "Gondor", 5, box(0, 0, 10, 10))));
        ItemBlockRegionLayer items = new ItemBlockRegionLayer(new MapLayerConfig(config).layer(ItemBlockRegionLayer.KEY,
                true), List::of);

        assertFalse(rp.hiddenByDefault());
        assertEquals(0x00ff00, rp.shapes().get(0).style().fillColor());
        assertTrue(items.hiddenByDefault());
        assertEquals(0x800080, new RpRegionLayer(DEFAULTS, List::of).style().fillColor(), "purple, as before");
        assertEquals(0xff0000, items.style().fillColor(), "red, as before");
    }

    @Test
    void architectsLayersFollowTheirOwnSwitchAndSettings() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("mapLayers.rpRegions.enabled", false);
        config.set("mapLayers.noPhysics.enabled", false);
        config.set("mapLayers.itemBlockLimit.color", "#00ff00");
        config.set("mapLayers.itemBlockBudget.enabled", false);

        List<MapLayer> layers = ArchitectLayers.fromConfig(config, new File("unused"));

        assertTrue(layers.stream().noneMatch(layer -> layer.markerSetId().equals(RpRegionLayer.ID)), "switched off");
        assertTrue(layers.stream().noneMatch(ItemBlockBudgetLayer.class::isInstance), "the budget's tracker too");
        assertTrue(layers.stream().noneMatch(NoPhysicsLayer.class::isInstance), "switched off too");
        ItemBlockRegionLayer items = layers.stream().filter(ItemBlockRegionLayer.class::isInstance)
                .map(ItemBlockRegionLayer.class::cast).findFirst().orElseThrow();
        assertEquals(0x00ff00, items.style().fillColor());
    }
}
