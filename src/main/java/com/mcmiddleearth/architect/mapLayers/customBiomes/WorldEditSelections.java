package com.mcmiddleearth.architect.mapLayers.customBiomes;

import com.sk89q.worldedit.IncompleteRegionException;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.regions.Region;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;

import java.util.Optional;
import java.util.logging.Level;

/**
 * A player's WorldEdit selection, as chunks. WorldEdit and FastAsyncWorldEdit share this API. This is the only class of
 * the package that imports WorldEdit; the rest of the layer links, and runs, without it.
 * <p>
 * Every use of a WorldEdit class is in {@link WorldEditCalls}, a class of its own. A class that names WorldEdit's types
 * in its methods can fail to initialise on a server without WorldEdit, whether or not those methods ever run, and then
 * {@link #lookup} could not answer {@link SelectionSource#NONE}. Main thread only.
 */
final class WorldEditSelections implements SelectionSource {

    private final Plugin plugin;
    private boolean warned;

    private WorldEditSelections(Plugin plugin) {
        this.plugin = plugin;
    }

    /** WorldEdit's selections if WorldEdit or FastAsyncWorldEdit is enabled; otherwise none. */
    static SelectionSource lookup(Plugin plugin) {
        // isPluginEnabled, not getPlugin() != null: a plugin that failed to enable still has its classes loaded
        PluginManager plugins = plugin.getServer().getPluginManager();
        if (plugins.isPluginEnabled("WorldEdit") || plugins.isPluginEnabled("FastAsyncWorldEdit")) {
            return new WorldEditSelections(plugin);
        }
        return NONE;
    }

    /**
     * The chunks of the player's selection in the world the player is in, or none while the selection is incomplete.
     * A WorldEdit that fails or is not compatible also gives none, with one warning for as long as this object lives.
     */
    @Override
    public Optional<ChunkBox> chunks(Player player) {
        try {
            return WorldEditCalls.selectionOf(player);
        } catch (RuntimeException | LinkageError e) {
            if (!warned) {
                warned = true;
                plugin.getLogger().log(Level.WARNING, "Custom biomes layer: WorldEdit would not say what a player has "
                        + "selected, so the chunks of a selection are not read after WorldEdit commands. This is "
                        + "logged once until the next start.", e);
            }
            return Optional.empty();
        }
    }

    /** The calls into WorldEdit. Touch this class only after {@link #lookup} found WorldEdit enabled. */
    static final class WorldEditCalls {

        private WorldEditCalls() {
        }

        static Optional<ChunkBox> selectionOf(Player player) {
            try {
                Region region = WorldEdit.getInstance().getSessionManager().get(BukkitAdapter.adapt(player))
                        .getSelection(BukkitAdapter.adapt(player.getWorld()));
                return Optional.of(boxOf(player.getWorld().getName(), region));
            } catch (IncompleteRegionException e) {
                return Optional.empty();
            }
        }

        /** The chunks that hold the region's lowest and highest blocks, and all between. */
        static ChunkBox boxOf(String world, Region region) {
            BlockVector3 min = region.getMinimumPoint();
            BlockVector3 max = region.getMaximumPoint();
            return new ChunkBox(world, min.x() >> 4, min.z() >> 4, max.x() >> 4, max.z() >> 4);
        }
    }
}
