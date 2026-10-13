package com.mcmiddleearth.architect.biomeTuning;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.bukkit.NamespacedKey;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.*;

class BiomeFilesTest {

    private static final NamespacedKey KEY = NamespacedKey.fromString("cbc:111g380-5p");

    private static Path write(Path file) throws IOException {
        Files.createDirectories(file.getParent());
        Files.writeString(file, "{}");
        return file;
    }

    private static String failure(Path datapacks, NamespacedKey key) {
        return assertThrows(BiomeTuningException.class, () -> BiomeFiles.locate(datapacks, key)).getMessage();
    }

    @Test
    void findsTheFileInAFolderPack(@TempDir Path datapacks) throws Exception {
        Path file = write(datapacks.resolve("bukkit/data/cbc/worldgen/biome/111g380-5p.json"));
        assertEquals(file.toRealPath(), BiomeFiles.locate(datapacks, KEY).toRealPath());
    }

    @Test
    void refusesABiomeNoPackDefines(@TempDir Path datapacks) throws Exception {
        Files.createDirectories(datapacks.resolve("bukkit"));
        assertTrue(failure(datapacks, KEY).contains("not defined"));
    }

    @Test
    void refusesABiomeTwoPacksDefine(@TempDir Path datapacks) throws Exception {
        write(datapacks.resolve("a/data/cbc/worldgen/biome/111g380-5p.json"));
        write(datapacks.resolve("b/data/cbc/worldgen/biome/111g380-5p.json"));
        assertTrue(failure(datapacks, KEY).contains("more than one"));
    }

    @Test
    void refusesABiomeFromAZipPack(@TempDir Path datapacks) throws Exception {
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(datapacks.resolve("colours.zip")))) {
            zip.putNextEntry(new ZipEntry("data/cbc/worldgen/biome/111g380-5p.json"));
            zip.write("{}".getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }
        assertTrue(failure(datapacks, KEY).contains("zip"));
    }

    @Test
    void refusesAnIdThatEscapesThePack(@TempDir Path datapacks) throws Exception {
        Files.createDirectories(datapacks.resolve("bukkit"));
        NamespacedKey escaping = NamespacedKey.fromString("cbc:../../../../../../outside");
        assertNotNull(escaping, "such an id is syntactically valid, which is why the check exists");
        assertTrue(failure(datapacks, escaping).contains("not a valid biome id"));
    }

    @Test
    void explainsAMissingDatapacksFolder(@TempDir Path dir) {
        assertTrue(failure(dir.resolve("nope"), KEY).contains("no datapacks folder"));
        assertTrue(failure(null, KEY).contains("no datapacks folder"));
    }
}
