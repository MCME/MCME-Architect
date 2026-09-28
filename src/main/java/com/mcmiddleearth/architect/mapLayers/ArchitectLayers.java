package com.mcmiddleearth.architect.mapLayers;

import com.mcmiddleearth.architect.noPhysicsEditor.NoPhysicsData;
import com.mcmiddleearth.architect.serverResoucePack.RpManager;
import com.mcmiddleearth.architect.specialBlockHandling.itemBlock.ItemBlockManager;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.configuration.Configuration;

import java.util.ArrayList;
import java.util.List;

/** Architect's map layers, as config.yml switches them on. */
public final class ArchitectLayers {

    private ArchitectLayers() {
    }

    public static List<MapLayer> fromConfig(Configuration config) {
        MapLayerConfig layers = new MapLayerConfig(config);
        List<MapLayer> result = new ArrayList<>();
        MapLayerConfig.Layer rp = layers.layer(RpRegionLayer.KEY, true);
        if (rp.enabled()) {
            result.add(new RpRegionLayer(rp, () -> RpManager.getRegions().values()));
        }
        MapLayerConfig.Layer itemBlocks = layers.layer(ItemBlockRegionLayer.KEY, true);
        if (itemBlocks.enabled()) {
            result.add(new ItemBlockRegionLayer(itemBlocks, () -> ItemBlockManager.getRegions().values()));
        }
        MapLayerConfig.Layer noPhysics = layers.layer(NoPhysicsLayer.KEY, false);
        if (noPhysics.enabled()) {
            result.add(new NoPhysicsLayer(noPhysics, NoPhysicsData::getExceptionAreas, uuid -> {
                World world = Bukkit.getWorld(uuid);
                return world == null ? null : world.getName();
            }));
        }
        return result;
    }
}
