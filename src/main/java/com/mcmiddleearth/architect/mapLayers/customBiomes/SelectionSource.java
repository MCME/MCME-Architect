package com.mcmiddleearth.architect.mapLayers.customBiomes;

import org.bukkit.entity.Player;

import java.util.Optional;

/**
 * Where a builder's WorldEdit selection lies, in whole chunks. WorldEdit is optional, so the tracker asks through this
 * interface, and only {@link WorldEditSelections} names WorldEdit's classes.
 */
@FunctionalInterface
public interface SelectionSource {

    /** A box of chunks, in chunk coordinates, with both ends included. */
    record ChunkBox(String world, int minX, int minZ, int maxX, int maxZ) {}

    /** For a server without WorldEdit: nobody has a selection. */
    SelectionSource NONE = player -> Optional.empty();

    /** The chunks the player's selection covers, or none when the player has no complete selection. */
    Optional<ChunkBox> chunks(Player player);
}
