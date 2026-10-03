package com.mcmiddleearth.architect;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PluginEnableTest {
    @AfterEach
    void tearDown() { if (MockBukkit.isMocked()) MockBukkit.unmock(); }

    @Test
    void pluginEnablesOnMockServer() {
        MockBukkit.mock();
        ArchitectPlugin plugin = MockBukkit.load(ArchitectPlugin.class);
        assertTrue(plugin.isEnabled());
    }
}
