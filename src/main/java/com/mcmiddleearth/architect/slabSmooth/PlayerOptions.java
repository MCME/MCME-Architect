package com.mcmiddleearth.architect.slabSmooth;

import com.mcmiddleearth.architect.slabSmooth.core.Options;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Each player's smoothing options and named pack. A brush click reads them from FAWE's threads while commands change
 * them on the main thread, so nothing handed out is ever changed afterwards: a player's options are replaced as a
 * whole, and every reader gets a copy.
 */
final class PlayerOptions {

    private final Options defaults;
    private final Map<UUID, Options> options = new ConcurrentHashMap<>();
    private final Map<UUID, String> packs = new ConcurrentHashMap<>();

    /** @param defaults copied, so later changes to it don't reach anyone */
    PlayerOptions(Options defaults) {
        this.defaults = defaults.copy();
    }

    /** A copy of the player's options: the defaults until the player sets something. Any thread. */
    Options of(UUID player) {
        Options own = options.get(player);
        return (own == null ? defaults : own).copy();
    }

    /** @throws IllegalArgumentException for an unknown name or a bad value; nothing changes then */
    void set(UUID player, String name, String value) {
        options.compute(player, (id, old) -> {
            Options changed = (old == null ? defaults : old).copy();
            changed.set(name, value);
            return changed;
        });
    }

    /** Back to the defaults and to the automatic pack. */
    void reset(UUID player) {
        options.remove(player);
        packs.remove(player);
    }

    /** The pack this player named, or null for the automatic choice. */
    String namedPack(UUID player) {
        return packs.get(player);
    }

    /** @param pack null for the automatic choice */
    void namePack(UUID player, String pack) {
        if (pack == null) {
            packs.remove(player);
        } else {
            packs.put(player, pack);
        }
    }
}
