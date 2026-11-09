package com.mcmiddleearth.architect.slabSmooth;

import com.mcmiddleearth.architect.ArchitectPlugin;
import com.mcmiddleearth.architect.biomeTuning.BiomeTuning;
import org.bukkit.scheduler.BukkitTask;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

import java.lang.reflect.Field;
import java.lang.reflect.Proxy;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Architect stops SlabSmooth before teardown that can fail: biome tuning's has steps that may throw, and SlabSmooth's
 * jobs and threads would then never stop. A class of its own, because it disables Architect.
 */
class SlabSmoothShutdownTest {

    private static ServerMock server;
    private static ArchitectPlugin plugin;

    @BeforeAll
    static void startTheServer() {
        server = MockBukkit.mock();
        plugin = MockBukkit.load(ArchitectPlugin.class);
    }

    @AfterAll
    static void stopTheServer() {
        if (MockBukkit.isMocked()) {
            MockBukkit.unmock();
        }
    }

    @Test
    void slabSmoothStopsBeforeBiomeTuningsTeardown() throws ReflectiveOperationException {
        assertTrue(SlabSmooth.isAvailable(), "running: " + SlabSmooth.problems());
        IllegalStateException failing = new IllegalStateException("test: biome tuning's teardown fails");
        Field timer = BiomeTuning.class.getDeclaredField("timer"); // the first step of BiomeTuning.disable() that can throw
        timer.setAccessible(true);
        Object before = timer.get(null);
        timer.set(null, cancelThrows(failing));
        try {
            server.getPluginManager().disablePlugin(plugin);
        } catch (IllegalStateException e) {
            assertSame(failing, e, "the teardown that fails: a server logs it, MockBukkit passes it on");
        } finally {
            timer.set(null, before);
        }

        assertFalse(SlabSmooth.isAvailable(), "SlabSmooth stopped before the teardown that failed");
        assertNull(SlabSmooth.jobs(), "and closed its jobs");
    }

    /** A scheduled task whose cancel throws. */
    private static BukkitTask cancelThrows(RuntimeException failure) {
        return (BukkitTask) Proxy.newProxyInstance(SlabSmoothShutdownTest.class.getClassLoader(), new Class<?>[] {BukkitTask.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "cancel" -> throw failure;
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    case "toString" -> "a timer whose cancel fails";
                    default -> throw new AssertionError("unexpected call: " + method.getName());
                });
    }
}
