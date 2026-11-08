package com.mcmiddleearth.architect.slabSmooth;

import com.mcmiddleearth.architect.ArchitectPlugin;
import com.mcmiddleearth.architect.Modules;
import com.mcmiddleearth.architect.PluginData;
import com.mcmiddleearth.architect.WorldConfig;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.world.WorldMock;

import java.io.File;
import java.lang.reflect.Field;
import java.util.ArrayDeque;
import java.util.Queue;
import java.util.concurrent.atomic.AtomicReference;

import static com.mcmiddleearth.architect.slabSmooth.ModuleSwitch.Answer.NOT_READ_YET;
import static com.mcmiddleearth.architect.slabSmooth.ModuleSwitch.Answer.OFF;
import static com.mcmiddleearth.architect.slabSmooth.ModuleSwitch.Answer.ON;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ModuleSwitchTest {

    private static ServerMock server;

    @BeforeAll
    static void startTheServer() {
        server = MockBukkit.mock();
        MockBukkit.load(ArchitectPlugin.class);
    }

    @AfterAll
    static void stopTheServer() {
        if (MockBukkit.isMocked()) {
            MockBukkit.unmock();
        }
    }

    private static World world(String name) {
        WorldMock added = new WorldMock(Material.STONE, -64, 320, 0);
        added.setName(name);
        server.addWorld(added);
        return added;
    }

    /** Asks from a thread that isn't the main one. */
    private static ModuleSwitch.Answer askElsewhere(ModuleSwitch module, World world) throws InterruptedException {
        AtomicReference<ModuleSwitch.Answer> answer = new AtomicReference<>();
        Thread thread = new Thread(() -> answer.set(module.answerIn(world)), "fawe-like");
        thread.start();
        thread.join();
        return answer.get();
    }

    @Test
    void itIsOffByDefault() {
        assertFalse(new ModuleSwitch(Runnable::run).enabledIn(world("ms-default")));
    }

    @Test
    void aWorldCanSwitchItOn() {
        World world = world("ms-on");
        ModuleSwitch module = new ModuleSwitch(Runnable::run);
        assertFalse(module.enabledIn(world));

        PluginData.setModuleEnabled(world, Modules.SLAB_SMOOTH, true);
        assertTrue(module.enabledIn(world), "the main thread reads the config at every ask");
        PluginData.setModuleEnabled(world, Modules.SLAB_SMOOTH, false);
        assertFalse(module.enabledIn(world));
    }

    @Test
    void aNewDefaultFileLeavesItOff() throws Exception {
        Field field = WorldConfig.class.getDeclaredField("defaultConfigFile");
        field.setAccessible(true);
        File defaultFile = (File) field.get(null);
        assertTrue(!defaultFile.exists() || defaultFile.delete(), "the test needs the default file gone");

        new WorldConfig("ms-newdefault", new YamlConfiguration());

        assertTrue(defaultFile.exists(), "WorldConfig wrote a new default file");
        YamlConfiguration written = YamlConfiguration.loadConfiguration(defaultFile);
        assertTrue(written.contains(Modules.SLAB_SMOOTH.getModuleKey()));
        assertFalse(written.getBoolean(Modules.SLAB_SMOOTH.getModuleKey()));
        for (Modules module : Modules.values()) {
            if (module != Modules.SLAB_SMOOTH) {
                assertTrue(written.getBoolean(module.getModuleKey()), module + " stays on");
            }
        }
    }

    @Test
    void offTheMainThreadItAnswersFromWhatTheMainThreadRead() throws Exception {
        World world = world("ms-cached");
        Queue<Runnable> mainQueue = new ArrayDeque<>();
        ModuleSwitch module = new ModuleSwitch(mainQueue::add);

        PluginData.setModuleEnabled(world, Modules.SLAB_SMOOTH, true);
        assertTrue(module.enabledIn(world));
        mainQueue.clear();

        PluginData.setModuleEnabled(world, Modules.SLAB_SMOOTH, false);
        assertEquals(ON, askElsewhere(module, world), "what the main thread read last, not a config read off the thread");
        assertEquals(1, mainQueue.size(), "and a fresh read is queued for the main thread");
        assertEquals(ON, askElsewhere(module, world));
        assertEquals(1, mainQueue.size(), "once, not at every ask");

        mainQueue.remove().run();
        assertEquals(OFF, askElsewhere(module, world), "after the main thread has read it again");
    }

    @Test
    void offTheMainThreadAWorldNotReadYetSaysSoAndGetsReadSoon() throws Exception {
        World world = world("ms-unknown");
        PluginData.setModuleEnabled(world, Modules.SLAB_SMOOTH, true);
        Queue<Runnable> mainQueue = new ArrayDeque<>();
        ModuleSwitch module = new ModuleSwitch(mainQueue::add);

        assertEquals(NOT_READ_YET, askElsewhere(module, world), "nothing is known yet: neither on nor off");
        assertEquals(NOT_READ_YET, askElsewhere(module, world));
        assertEquals(1, mainQueue.size());

        mainQueue.remove().run();
        assertEquals(ON, askElsewhere(module, world), "the next click sees what the main thread read");
    }
}
