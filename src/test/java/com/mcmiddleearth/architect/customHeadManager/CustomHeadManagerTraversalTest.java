package com.mcmiddleearth.architect.customHeadManager;

import com.mcmiddleearth.architect.ArchitectPlugin;
import org.junit.jupiter.api.*;
import org.mockbukkit.mockbukkit.MockBukkit;
import java.io.File;
import java.nio.file.Files;
import static org.junit.jupiter.api.Assertions.*;

// One mock/load per class: Architect caches getDataFolder()-derived paths in static fields at
// class-load, so re-loading in the same JVM would use a stale path. surefire reuseForks=false gives
// a fresh JVM per test class. Mirrors LogFileTest.
class CustomHeadManagerTraversalTest {
    private static ArchitectPlugin plugin;

    @BeforeAll static void setUp() { MockBukkit.mock(); plugin = MockBukkit.load(ArchitectPlugin.class); }
    @AfterAll  static void tearDown() { if (MockBukkit.isMocked()) MockBukkit.unmock(); }

    @Test void deleteRejectsTraversalAndKeepsOutsideFile() throws Exception {
        // Sentinel lives in the data folder, OUTSIDE customHeads/accepted.
        File sentinel = new File(plugin.getDataFolder(), "sentinel.yml");
        Files.writeString(sentinel.toPath(), "do not delete me");
        assertTrue(sentinel.exists(), "precondition: sentinel created");

        // "../../sentinel" escapes accepted/ up to the data folder. Must be rejected.
        assertFalse(CustomHeadManagerData.deleteHead("../../sentinel"),
                "a traversal delete must be rejected, not treated as a real head");
        assertTrue(sentinel.exists(),
                "traversal delete must NOT reach a file outside the custom-head directories");
    }

    @Test void rejectRejectsTraversalName() {
        assertFalse(CustomHeadManagerData.rejectHead("../../evil"));
    }

    @Test void getHeadDataReturnsNullForTraversalName() {
        assertNull(CustomHeadManagerData.getHeadData("../../evil"));
    }
}
