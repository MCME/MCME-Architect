package com.mcmiddleearth.architect.slabSmooth.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ResourcesTest {

    /** Zeros pack down to almost nothing, so a small zip can hold an entry far too big to read. */
    @Test
    void anEntryOverTheLimitReadsAsMissing(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("pack.zip");
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(file))) {
            zeros(zip, "assets/minecraft/at_the_limit.bin", Resources.MAX_ENTRY_BYTES);
            zeros(zip, "assets/minecraft/over_the_limit.bin", Resources.MAX_ENTRY_BYTES + 1);
        }
        try (Resources pack = Resources.zip(file)) {
            assertEquals(Resources.MAX_ENTRY_BYTES, pack.read("assets/minecraft/at_the_limit.bin").length);
            byte[] over = pack.read("assets/minecraft/over_the_limit.bin");
            assertTrue(over == null, () -> over.length + " bytes read from an entry over the limit");
        }
    }

    private static void zeros(ZipOutputStream zip, String name, int size) throws IOException {
        zip.putNextEntry(new ZipEntry(name));
        byte[] chunk = new byte[64 * 1024];
        for (int left = size; left > 0; left -= chunk.length) {
            zip.write(chunk, 0, Math.min(left, chunk.length));
        }
        zip.closeEntry();
    }
}
