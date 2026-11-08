package com.mcmiddleearth.architect.slabSmooth;

import com.mcmiddleearth.architect.slabSmooth.core.Table;
import org.bukkit.Location;
import org.bukkit.entity.Player;

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
     * The table to smooth with for a choice. allBlocks: with the other blocks that have pieces of their own, not just
     * terrain.
     *
     * @throws IllegalStateException with a message for the player when there is no table yet
     */
    Table table(Packs.Choice choice, boolean allBlocks);
}
