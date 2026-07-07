package com.mcmiddleearth.architect;

import com.mcmiddleearth.architect.customHeadManager.CustomHeadData;
import com.mcmiddleearth.architect.customHeadManager.CustomHeadManagerData;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import java.io.File;
import java.nio.file.Files;
import static org.junit.jupiter.api.Assertions.*;

// One mock/load per class (Architect caches getDataFolder()-derived paths in static fields at
// class-load); surefire reuseForks=false gives a fresh JVM per class. Mirrors LogFileTest.
class LoadGuardTest {
    private static ServerMock server;
    private static ArchitectPlugin plugin;

    @BeforeAll static void setUp() { server = MockBukkit.mock(); plugin = MockBukkit.load(ArchitectPlugin.class); }
    @AfterAll  static void tearDown() { if (MockBukkit.isMocked()) MockBukkit.unmock(); }

    // ---- Task 1: Mode 1 (corrupt head must not NPE) ----
    @Test void fromFileReturnsNullOnCorruptOrIncompleteHead(@TempDir File tmp) throws Exception {
        File corrupt = new File(tmp, "bad.yml");
        Files.writeString(corrupt.toPath(), ":\n  not: [valid");
        assertNull(CustomHeadData.fromFile(corrupt), "corrupt head file must yield null, not NPE");

        File incomplete = new File(tmp, "incomplete.yml");
        Files.writeString(incomplete.toPath(), "texture: abc\n"); // no headId/owner
        assertNull(CustomHeadData.fromFile(incomplete), "head missing headId/owner must yield null, not NPE");
    }

    @Test void customHeadLoadSkipsCorruptFileWithoutThrowing() throws Exception {
        File accepted = new File(plugin.getDataFolder(), "customHeads/accepted");
        assertTrue(accepted.exists() || accepted.mkdirs());
        Files.writeString(new File(accepted, "broken.yml").toPath(), ":\n  not: [valid");
        assertDoesNotThrow(CustomHeadManagerData::load,
                "one corrupt head file must not break the enable-time load");
    }
}
