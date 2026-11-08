package com.mcmiddleearth.architect.slabSmooth;

import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.nio.file.Path;
import java.util.List;

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

    /** The pack a player has now, or null if unknown. Any thread. */
    String currentPack(Player player);
}
