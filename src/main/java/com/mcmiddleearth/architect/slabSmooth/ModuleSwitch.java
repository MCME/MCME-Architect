package com.mcmiddleearth.architect.slabSmooth;

import com.mcmiddleearth.architect.Modules;
import com.mcmiddleearth.architect.PluginData;
import org.bukkit.Bukkit;
import org.bukkit.World;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;

/**
 * Whether SlabSmooth is on in a world (Modules.SLAB_SMOOTH, off unless a world switches it on). Reading a world's
 * config can save a file and PluginData's world configs are not thread-safe, so only the main thread reads them; the
 * answer is kept per world name, and other threads (FAWE's brush clicks) answer from that.
 *
 * <p>PluginData.isModuleEnabled is not used: it counts a missing key as on.
 */
final class ModuleSwitch {

    private final Executor mainThread;
    private final Map<String, Boolean> known = new ConcurrentHashMap<>();
    private final Set<String> reading = ConcurrentHashMap.newKeySet();

    /** @param mainThread runs a task on the main thread, later */
    ModuleSwitch(Executor mainThread) {
        this.mainThread = mainThread;
    }

    /**
     * On the main thread this reads the config. Elsewhere it answers with what the main thread read last (off for a
     * world it has not read yet) and has the main thread read it again, so a change shows from the next click on.
     */
    boolean enabledIn(World world) {
        String name = world.getName();
        if (Bukkit.isPrimaryThread()) {
            return read(name);
        }
        if (reading.add(name)) {
            try {
                mainThread.execute(() -> {
                    reading.remove(name);
                    read(name);
                });
            } catch (RuntimeException e) {
                reading.remove(name); // the executor is gone (plugin disabling): ask again next time
            }
        }
        return known.getOrDefault(name, false);
    }

    private boolean read(String worldName) {
        boolean on = PluginData.getOrCreateWorldConfig(worldName).isModuleEnabled(Modules.SLAB_SMOOTH, false);
        known.put(worldName, on);
        return on;
    }
}
