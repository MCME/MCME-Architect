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
 * answer is kept per world name, and other threads (FAWE's brush clicks) answer from that. A world the main thread
 * hasn't read yet, such as every world right after a start or a reload, is neither on nor off for them.
 *
 * <p>PluginData.isModuleEnabled is not used: it counts a missing key as on.
 */
final class ModuleSwitch {

    /** What the switch says in a world, as far as the asking thread can know. */
    enum Answer { ON, OFF, NOT_READ_YET }

    private final Executor mainThread;
    private final Map<String, Boolean> known = new ConcurrentHashMap<>();
    private final Set<String> reading = ConcurrentHashMap.newKeySet();

    /**
     * @param mainThread runs a task on the main thread in a later tick, never the current one, as
     *                   {@link SlabSmooth#nextTick} does
     */
    ModuleSwitch(Executor mainThread) {
        this.mainThread = mainThread;
    }

    /**
     * Whether SlabSmooth is on in the world. For the main thread, where this reads the config. Elsewhere it is
     * {@link #answerIn} with a world not read yet counted as off.
     */
    boolean enabledIn(World world) {
        return answerIn(world) == Answer.ON;
    }

    /**
     * On the main thread this reads the config. Elsewhere it answers with what the main thread read last, or
     * NOT_READ_YET for a world it has not read, and has the main thread read it again, so a change shows from the next
     * click on. Any thread.
     */
    Answer answerIn(World world) {
        String name = world.getName();
        if (Bukkit.isPrimaryThread()) {
            return read(name) ? Answer.ON : Answer.OFF;
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
        Boolean on = known.get(name);
        return on == null ? Answer.NOT_READ_YET : on ? Answer.ON : Answer.OFF;
    }

    private boolean read(String worldName) {
        boolean on = PluginData.getOrCreateWorldConfig(worldName).isModuleEnabled(Modules.SLAB_SMOOTH, false);
        known.put(worldName, on);
        return on;
    }
}
