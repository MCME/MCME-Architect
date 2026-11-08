package com.mcmiddleearth.architect.slabSmooth;

import com.sk89q.worldedit.math.BlockVector3;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;

/** Where the resource packs come from: their names, inventories, download addresses and who uses which. */
interface PackSources {

    /** A pack's download address and SHA-1 (empty if unknown). */
    record Source(String url, String sha1) {
    }

    /** Every resource pack the server has. */
    List<String> packs();

    /** The folder with a pack's block inventory. */
    Path inventory(String pack);

    /** Where to download a pack, or null if there is no address. */
    Source source(String pack);

    /** The pack of the RP region at a location, or null if there is none. Any thread. */
    String regionPack(Location location);

    /**
     * The packs of the RP regions in a world whose bounding boxes meet min..max (inclusive), or null if the regions
     * can't be read. A region's bounding box can reach past its shape, so a pack can be named for a box the region
     * itself doesn't reach. Any thread.
     */
    Set<String> regionPacksIn(World world, BlockVector3 min, BlockVector3 max);

    /** The pack a player has now, or null if unknown. Any thread. */
    String currentPack(Player player);
}
