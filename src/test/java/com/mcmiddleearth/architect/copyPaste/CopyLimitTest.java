package com.mcmiddleearth.architect.copyPaste;

import com.mcmiddleearth.architect.ArchitectPlugin;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import static org.junit.jupiter.api.Assertions.*;

// A player's copy limit is the largest architect.copypaste.limit.<n> node set to true. A node a permissions plugin
// sets to false takes that limit away. One mock/load per class.
class CopyLimitTest {

    private static ServerMock server;
    private static ArchitectPlugin plugin;

    @BeforeAll
    static void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.load(ArchitectPlugin.class);
    }

    @AfterAll
    static void tearDown() {
        if (MockBukkit.isMocked()) {
            MockBukkit.unmock();
        }
    }

    @Test
    void aLimitNodeSetToFalseDoesNotCount() {
        PlayerMock builder = server.addPlayer();
        builder.addAttachment(plugin, "architect.copypaste.limit.1000", true);
        builder.addAttachment(plugin, "architect.copypaste.limit.100000", false);

        assertEquals(1000, CopyPasteManager.maxAllowedSize(builder));
    }

    // A guard: of the nodes set to true, the largest counts.
    @Test
    void theLargestLimitCounts() {
        PlayerMock builder = server.addPlayer();
        builder.addAttachment(plugin, "architect.copypaste.limit.1000", true);
        builder.addAttachment(plugin, "architect.copypaste.limit.5000", true);

        assertEquals(5000, CopyPasteManager.maxAllowedSize(builder));
    }

    // A guard: without a limit node, nothing may be copied.
    @Test
    void noLimitNodeMeansZero() {
        assertEquals(0, CopyPasteManager.maxAllowedSize(server.addPlayer()));
    }
}
