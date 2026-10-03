package com.mcmiddleearth.architect;

import com.mcmiddleearth.architect.customHeadManager.CustomHeadData;
import com.mcmiddleearth.architect.customHeadManager.CustomHeadManagerData;
import com.mcmiddleearth.architect.specialBlockHandling.data.GetData;
import org.junit.jupiter.api.*;
import org.bukkit.configuration.file.YamlConfiguration;
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

    // ---- Task 3: Mode 2 (corrupt world config must not be overwritten) ----
    @Test void worldConfigDoesNotOverwriteCorruptFile() throws Exception {
        File worldDir = new File(plugin.getDataFolder(), "WorldConfig");
        assertTrue(worldDir.exists() || worldDir.mkdirs());
        File wf = new File(worldDir, "traptown.yml");
        String original = "moduleX: [unclosed";      // invalid YAML
        Files.writeString(wf.toPath(), original);

        WorldConfig wc = new WorldConfig("traptown", new YamlConfiguration());
        wc.setModuleEnabled(Modules.values()[0], false); // would trigger saveWorldConfig

        assertEquals(original, Files.readString(wf.toPath()),
                "a world config that failed to load must not be overwritten by a toggle");
    }

    // ---- Task 5: Mode 2 (corrupt itemSets.yml must not be wiped by the delayed save) ----
    @Test void getDataDoesNotWipeCorruptFile() throws Exception {
        server.getScheduler().performTicks(2100L); // flush any save scheduled during enable
        File dataFile = new File(plugin.getDataFolder(), "itemSets.yml");
        String original = "set1: [unclosed";       // invalid YAML
        Files.writeString(dataFile.toPath(), original);

        GetData.load();
        server.getScheduler().performTicks(2100L); // a scheduled save (if any) would fire here

        assertEquals(original, Files.readString(dataFile.toPath()),
                "a corrupt itemSets.yml must be preserved, not overwritten with empty");
    }

    // ---- Review follow-up: corrupt defaultWorldConfig.yml must not be overwritten via saveDefaultConfig ----
    @Test void defaultWorldConfigNotOverwrittenWhenCorrupt() throws Exception {
        File worldDir = new File(plugin.getDataFolder(), "WorldConfig");
        assertTrue(worldDir.exists() || worldDir.mkdirs());
        File df = new File(worldDir, "defaultWorldConfig.yml");
        String original = "physics: [unclosed";     // invalid YAML
        Files.writeString(df.toPath(), original);

        new WorldConfig("defaultWorldConfig", new YamlConfiguration()); // loadX() -> saveDefaultConfig

        assertEquals(original, Files.readString(df.toPath()),
                "a corrupt defaultWorldConfig.yml must not be overwritten during construction");
    }
}
