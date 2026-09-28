package com.mcmiddleearth.architect.mapLayers;

import com.mcmiddleearth.architect.PluginData;
import com.mcmiddleearth.architect.noPhysicsEditor.NoPhysicsData;
import com.mcmiddleearth.architect.serverResoucePack.RpManager;
import com.mcmiddleearth.architect.specialBlockHandling.itemBlock.ItemBlockManager;
import com.mcmiddleearth.architect.specialBlockHandling.itemBlock.ItemBlockRegion;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.configuration.Configuration;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/** Architect's map layers, as config.yml switches them on. */
public final class ArchitectLayers {

    private ArchitectLayers() {
    }

    /** {@code dataFolder} is Architect's, for the files layers keep. */
    public static List<MapLayer> fromConfig(Configuration config, File dataFolder) {
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
        MapLayerConfig.Layer budget = layers.layer(ItemBlockBudgetLayer.KEY, false);
        if (budget.enabled()) {
            result.add(new ItemBlockBudgetLayer(budget, new File(dataFolder, "mapLayers/itemBlockBudget.yml"),
                    new ItemBlockLimits()));
        }
        return result;
    }

    /** The rule placing uses (ItemBlockManager.getLimit), for a block column. */
    private static final class ItemBlockLimits implements ItemBlockBudgetLayer.Limits {

        /** None for a world that is not loaded: asking for its world config would write a new file for it. */
        @Override
        public ItemBlockBudgetLayer.Limit at(String world, int x, int z) {
            if (Bukkit.getWorld(world) == null) {
                return null;
            }
            ItemBlockRegion region = ItemBlockManager.regionForColumn(world, x, z);
            return region != null ? new ItemBlockBudgetLayer.Limit(region.getLimit(), region.getName())
                    : new ItemBlockBudgetLayer.Limit(PluginData.getOrCreateWorldConfig(world).getItemBlockBaseLimit(),
                            null);
        }

        @Override
        public int version() {
            return ItemBlockManager.limitsVersion();
        }
    }
}
