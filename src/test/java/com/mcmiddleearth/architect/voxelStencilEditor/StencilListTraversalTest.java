package com.mcmiddleearth.architect.voxelStencilEditor;

import com.mcmiddleearth.architect.ArchitectPlugin;
import org.junit.jupiter.api.*;
import org.mockbukkit.mockbukkit.MockBukkit;
import static org.junit.jupiter.api.Assertions.*;

// Mirrors LogFileTest: one mock/load per class, fresh JVM per class via surefire reuseForks=false.
class StencilListTraversalTest {

    @BeforeAll static void setUp() { MockBukkit.mock(); MockBukkit.load(ArchitectPlugin.class); }
    @AfterAll  static void tearDown() { if (MockBukkit.isMocked()) MockBukkit.unmock(); }

    @Test void saveRejectsTraversalListName() {
        // Regression for the write-path escape the security review found:
        // /sl create ../../../../evil then /sl save wrote a .txt outside STENCIL_LISTS_DIR.
        StencilList list = new StencilList("../../../../evil_pwned");
        assertFalse(list.saveToFile(), "a traversal list name must not be writable");
    }

    @Test void saveAcceptsLegitListName() {
        // Guard against over-rejection: a normal name must still save.
        StencilList list = new StencilList("legit_list");
        assertTrue(list.saveToFile(), "a normal list name must still save");
    }
}
