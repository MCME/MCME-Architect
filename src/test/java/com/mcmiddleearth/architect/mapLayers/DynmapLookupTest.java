package com.mcmiddleearth.architect.mapLayers;

import com.mcmiddleearth.architect.entityLogging.FakeDynmap;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

import static org.junit.jupiter.api.Assertions.*;

class DynmapLookupTest {

    private Plugin plugin;

    @BeforeEach
    void setUp() {
        MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void withoutDynmapThereIsNoMap() {
        assertSame(MapBackend.NONE, DynmapBackend.lookup(plugin));
    }

    @Test
    void aDynmapThatIsNotEnabledIsNoMap() {
        FakeDynmap dynmap = MockBukkit.loadWith(FakeDynmap.class, FakeDynmap.description());
        MockBukkit.getMock().getPluginManager().disablePlugin(dynmap);

        assertSame(MapBackend.NONE, DynmapBackend.lookup(plugin),
                "its core may be missing, as when it failed to start");
    }

    @Test
    void anEnabledDynmapIsTheMap() {
        MockBukkit.loadWith(FakeDynmap.class, FakeDynmap.description());

        assertInstanceOf(DynmapBackend.class, DynmapBackend.lookup(plugin));
    }
}
