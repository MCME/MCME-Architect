package com.mcmiddleearth.architect.biomeTuning;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import java.lang.reflect.Proxy;
import java.nio.file.FileSystemException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.bukkit.NamespacedKey;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.*;

class BiomeDocumentTest {

    private static final NamespacedKey KEY = NamespacedKey.fromString("cbc:111g380-5p");
    private static final String SAMPLE = "{\"attributes\":{\"minecraft:visual/sky_color\":\"#ffaa00\"},\"carvers\":[],"
            + "\"downfall\":0.8,\"effects\":{\"water_color\":\"#263a22\"},\"features\":[],\"temperature\":0.7}";
    private static final TuningProperty SKY = TuningProperties.resolve("sky_color").orElseThrow();
    private static final TuningProperty GRASS = TuningProperties.resolve("grass_color").orElseThrow();

    private static BiomeDocument load(Path dir) throws Exception {
        Path file = dir.resolve("111g380-5p.json");
        Files.writeString(file, SAMPLE);
        return BiomeDocument.load(KEY, file, 3);
    }

    @Test
    void readsValuesAtCataloguePaths(@TempDir Path dir) throws Exception {
        BiomeDocument doc = load(dir);
        assertEquals("#ffaa00", doc.value(SKY).getAsString());
        assertNull(doc.value(GRASS));
        assertFalse(doc.isUnsaved());
    }

    @Test
    void candidatesLeaveTheDocumentAloneUntilAccepted(@TempDir Path dir) throws Exception {
        BiomeDocument doc = load(dir);
        JsonObject candidate = doc.with(GRASS, new JsonPrimitive("#224422"));
        assertEquals("#224422", candidate.getAsJsonObject("effects").get("grass_color").getAsString());
        assertEquals("#263a22", candidate.getAsJsonObject("effects").get("water_color").getAsString());
        assertNull(doc.value(GRASS), "not accepted yet");
        doc.accept(candidate);
        assertEquals("#224422", doc.value(GRASS).getAsString());
        assertTrue(doc.isUnsaved());
        assertEquals(List.of("grass_color: (not set) -> #224422"), doc.changesSinceSave());
        assertEquals(List.of(GRASS), doc.changedSinceSave(), "the same, as values rather than text");
    }

    @Test
    void undoStepsBackAndIsBounded(@TempDir Path dir) throws Exception {
        BiomeDocument doc = load(dir);
        for (String colour : List.of("#000001", "#000002", "#000003", "#000004")) {
            doc.accept(doc.with(SKY, new JsonPrimitive(colour)));
        }
        List<String> seen = new ArrayList<>();
        while (doc.canUndo()) {
            assertNotNull(doc.undoCandidate());
            doc.undone();
            seen.add(doc.value(SKY).getAsString());
        }
        assertEquals(List.of("#000003", "#000002", "#000001"), seen, "an undo depth of 3 keeps the last three steps");
    }

    @Test
    void withoutRemovesAValue(@TempDir Path dir) throws Exception {
        BiomeDocument doc = load(dir);
        doc.accept(doc.without(SKY));
        assertNull(doc.value(SKY));
        assertTrue(doc.current().has("attributes"));
    }

    @Test
    void saveBacksUpAndWritesPrettyJsonWithEveryOtherKeyIntact(@TempDir Path dir) throws Exception {
        BiomeDocument doc = load(dir);
        doc.accept(doc.with(SKY, new JsonPrimitive("#2040ff")));
        Path backups = dir.resolve("backups");
        doc.save(backups, 20);
        String written = Files.readString(dir.resolve("111g380-5p.json"));
        JsonObject reread = JsonParser.parseString(written).getAsJsonObject();
        assertEquals("#2040ff", reread.getAsJsonObject("attributes").get("minecraft:visual/sky_color").getAsString());
        assertEquals(0.8, reread.get("downfall").getAsDouble());
        assertEquals(List.of("attributes", "carvers", "downfall", "effects", "features", "temperature"),
                new ArrayList<>(reread.keySet()), "key order is kept");
        assertTrue(written.contains("\n  \"attributes\""), "2-space pretty printing");
        assertEquals("{\n  \"attributes\": {\n    \"minecraft:visual/sky_color\": \"#2040ff\"\n  },\n"
                + "  \"carvers\": [],\n  \"downfall\": 0.8,\n  \"effects\": {\n    \"water_color\": \"#263a22\"\n  },\n"
                + "  \"features\": [],\n  \"temperature\": 0.7\n}\n", written, "the whole file");
        assertFalse(doc.isUnsaved());
        try (Stream<Path> files = Files.list(backups.resolve("cbc").resolve("111g380-5p"))) {
            assertEquals(SAMPLE, Files.readString(files.findFirst().orElseThrow()), "the backup holds the old file");
        }
    }

    // the pack travels between servers, so a save whose move fails leaves no temporary file next to the biome's file
    @Test
    void aSaveWhoseMoveFailsLeavesNoTemporaryFile(@TempDir Path dir) throws Exception {
        BiomeDocument doc = load(dir);
        doc.accept(doc.with(SKY, new JsonPrimitive("#2040ff")));
        Path file = dir.resolve("111g380-5p.json");
        Path backups = dir.resolve("backups");
        // save asks for the backups folder once it has read the biome's file: a folder then takes the file's place,
        // so the move at the end fails
        Path backupsFolder = (Path) Proxy.newProxyInstance(Path.class.getClassLoader(), new Class<?>[] {Path.class},
                (proxy, method, arguments) -> {
                    if (!method.getName().equals("toFile")) {
                        throw new UnsupportedOperationException(method.getName());
                    }
                    Files.delete(file);
                    Files.createDirectories(file.resolve("in the way"));
                    return backups.toFile();
                });

        BiomeTuningException e = assertThrows(BiomeTuningException.class, () -> doc.save(backupsFolder, 20));

        FileSystemException move = assertInstanceOf(FileSystemException.class, e.getCause(), "the move failed");
        assertEquals(file + ".tmp", move.getFile(), "after the temporary file was written: " + move);
        assertFalse(Files.exists(dir.resolve("111g380-5p.json.tmp")), "no temporary file is left in the pack");
    }

    @Test
    void saveRefusesWhenTheFileWasEditedByHand(@TempDir Path dir) throws Exception {
        BiomeDocument doc = load(dir);
        doc.accept(doc.with(SKY, new JsonPrimitive("#2040ff")));
        Files.writeString(dir.resolve("111g380-5p.json"), SAMPLE.replace("#ffaa00", "#123456"));
        BiomeTuningException e = assertThrows(BiomeTuningException.class, () -> doc.save(dir.resolve("backups"), 20));
        assertTrue(e.getMessage().contains("changed on disk"), e.getMessage());
        assertTrue(Files.readString(dir.resolve("111g380-5p.json")).contains("#123456"), "the hand edit is kept");
    }

    @Test
    void oldBackupsArePruned(@TempDir Path dir) throws Exception {
        BiomeDocument doc = load(dir);
        Path backups = dir.resolve("backups");
        for (int i = 0; i < 4; i++) {
            doc.accept(doc.with(SKY, new JsonPrimitive("#00000" + i)));
            doc.save(backups, 2);
        }
        try (Stream<Path> files = Files.list(backups.resolve("cbc").resolve("111g380-5p"))) {
            assertEquals(2, files.count());
        }
    }
}
