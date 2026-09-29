package com.mcmiddleearth.architect.noPhysicsEditor;

import com.mcmiddleearth.architect.ArchitectPlugin;
import org.bukkit.util.Vector;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

// NoPhyExceptionAreas.txt on disk: one mock/load per class, as Architect caches data-folder paths in static fields.
// A write error, such as a full disk, cannot be made here; a file that cannot be replaced can.
class NoPhysicsDataTest {

    private static final UUID WORLD = UUID.fromString("0f1e2d3c-4b5a-6978-8796-a5b4c3d2e1f0");

    private static File file;

    @BeforeAll
    static void setUp() {
        MockBukkit.mock();
        file = new File(MockBukkit.load(ArchitectPlugin.class).getDataFolder(), "NoPhyExceptionAreas.txt");
    }

    @AfterAll
    static void tearDown() {
        MockBukkit.unmock();
    }

    @AfterEach
    void removeTheFiles() throws IOException {
        NoPhysicsData.getExceptionAreas().clear();
        if (file.isDirectory()) { // on Linux, deleting "<a file>/in the way" throws "Not a directory"
            Files.deleteIfExists(new File(file, "in the way").toPath());
        }
        for (File each : new File[]{file, new File(file.getPath() + ".tmp"), NoPhysicsData.backupFile()}) {
            Files.deleteIfExists(each.toPath());
        }
    }

    @Test
    void savedAreasComeBackWithTheirType() throws Exception {
        Map<String, ExceptionArea> areas = NoPhysicsData.getExceptionAreas();
        areas.put("Mill", new RedstoneCircuitArea(WORLD, new Vector(0, 60, 0), new Vector(9, 70, 19)));
        areas.put("Fountain", new WaterFlowArea(WORLD, new Vector(100, 50, 100), new Vector(104, 55, 104)));

        NoPhysicsData.save();
        areas.clear();
        NoPhysicsData.loadExceptionAreas();

        assertInstanceOf(RedstoneCircuitArea.class, areas.get("Mill"));
        assertInstanceOf(WaterFlowArea.class, areas.get("Fountain"));
        assertFalse(new File(file.getPath() + ".tmp").exists(), "moved into place");
        assertFalse(NoPhysicsData.backupFile().exists(), "no line was skipped, so no copy is kept");
    }

    @Test
    void aSaveThatCannotReplaceTheFileFailsLoudly() {
        assertTrue(new File(file, "in the way").mkdirs(), "a folder that is not empty, where the file goes");
        NoPhysicsData.getExceptionAreas().put("Mill",
                new RedstoneCircuitArea(WORLD, new Vector(0, 60, 0), new Vector(9, 70, 19)));

        assertThrows(IOException.class, NoPhysicsData::save, "so staff are told it failed, not that it was saved");
    }
}
