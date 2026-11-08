package com.mcmiddleearth.architect.slabSmooth;

import com.mcmiddleearth.architect.slabSmooth.core.Table;
import com.sk89q.worldedit.math.BlockVector3;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.Set;

/** What the smoothing commands and brushes ask for their material tables. All of it is safe from any thread. */
interface TableSource {

    /**
     * The pack for an edit at location: the one named, the RP region there, the player's pack, the default. A null
     * location looks up no region.
     */
    Packs.Choice choose(Player player, Location location, String named);

    /** The pack of the RP region at a location, or null. */
    String regionPack(Location location);

    /**
     * The packs of the RP regions in a world whose bounding boxes meet min..max (inclusive), or null if the regions
     * can't be read.
     */
    Set<String> regionPacksIn(World world, BlockVector3 min, BlockVector3 max);

    /**
     * The table to smooth with for a choice. allBlocks: with the other blocks that have pieces of their own, not just
     * terrain.
     *
     * @throws Refusal with a message for the player when there is no table yet
     */
    Table table(Packs.Choice choice, boolean allBlocks);
}
