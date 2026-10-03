package com.mcmiddleearth.architect.biomeTuning;

import org.bukkit.plugin.java.JavaPlugin;

import java.nio.file.Path;

/**
 * Starts biome tuning on FakeNmsBridge, for tests in other packages that need a biome refresh to happen: under
 * MockBukkit the real bridge finds no server internals, so biome tuning stays off and nothing is ever refreshing.
 */
public final class BiomeTuningOnAFakeServer {

    private BiomeTuningOnAFakeServer() {
    }

    /** Biome tuning on the fake bridge, with this datapacks folder, in place of what Architect started. */
    public static void enable(JavaPlugin plugin, Path datapacks) {
        BiomeTuning.disable();
        BiomeTuning.enable(plugin, new FakeNmsBridge(), datapacks);
    }
}
