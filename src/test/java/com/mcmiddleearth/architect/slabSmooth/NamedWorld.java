package com.mcmiddleearth.architect.slabSmooth;

import com.sk89q.worldedit.world.NullWorld;

/**
 * A WorldEdit world with a name, for the regions of RP region tests: RpRegion matches worlds by name, and WorldEdit's
 * BukkitWorld can't be made under MockBukkit.
 */
final class NamedWorld extends NullWorld {

    private final String name;

    NamedWorld(String name) {
        this.name = name;
    }

    @Override
    public String getName() {
        return name;
    }
}
