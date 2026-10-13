package com.mcmiddleearth.architect.mapLayers;

import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

// Servers without the dynmap jar (terrain, rpserver and pvpserver may lack it) must still enable Architect, which
// builds its layers in onEnable. A class that names a dynmap type may fail to link there, and so may DynmapBackend
// once anything but MapLayers.lookupMap, which checks that dynmap is enabled first, reaches for it. MapLayersTest
// starts the service without dynmap, but with no layer; these keep every layer, including those still to come, and
// the rest of Architect off both. ELogDynmapUtil is the entity logger's own, older layer.
class DynmapIsolationTest {

    private static List<String> classesNaming(String name) throws Exception {
        Path classes = Path.of(MapLayers.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        try (Stream<Path> files = Files.walk(classes)) {
            return files.filter(file -> file.toString().endsWith(".class"))
                    .filter(file -> names(file, name))
                    .map(file -> classes.relativize(file).toString().replace(File.separatorChar, '/'))
                    .sorted()
                    .toList();
        }
    }

    private static boolean names(Path classFile, String name) {
        try {
            return new String(Files.readAllBytes(classFile), StandardCharsets.ISO_8859_1).contains(name);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Test
    void onlyDynmapBackendNamesDynmap() throws Exception {
        assertEquals(List.of("com/mcmiddleearth/architect/entityLogging/ELogDynmapUtil.class",
                "com/mcmiddleearth/architect/mapLayers/DynmapBackend.class"), classesNaming("org/dynmap/"));
    }

    @Test
    void onlyMapLayersNamesDynmapBackend() throws Exception {
        assertEquals(List.of("com/mcmiddleearth/architect/mapLayers/DynmapBackend.class",
                "com/mcmiddleearth/architect/mapLayers/MapLayers.class"),
                classesNaming("com/mcmiddleearth/architect/mapLayers/DynmapBackend"),
                "MapLayers.lookupMap touches it only once dynmap is enabled");
    }
}
