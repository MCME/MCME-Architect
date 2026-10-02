package com.mcmiddleearth.architect.biomeTuning;

import com.mcmiddleearth.util.PathSafety;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;
import java.util.zip.ZipFile;
import org.bukkit.NamespacedKey;

/**
 * Finds the datapack file that defines a biome, in the primary world's datapacks folder, and writes the temporary
 * files that tuning's writes move into place.
 */
public final class BiomeFiles {

    private BiomeFiles() {
    }

    /**
     * Writes {@code text} to {@code file} in UTF-8, as Files.writeString does (text UTF-8 cannot hold fails before the
     * file is touched), and forces it to the disk before returning. A file moved into place after this is whole even
     * after a power loss: without it, the new name can come back empty.
     */
    static void writeToDisk(Path file, String text) throws IOException {
        writeToDisk(file, StandardCharsets.UTF_8.newEncoder().encode(CharBuffer.wrap(text)));
    }

    /** As {@link #writeToDisk(Path, String)}, for bytes. */
    static void writeToDisk(Path file, byte[] bytes) throws IOException {
        writeToDisk(file, ByteBuffer.wrap(bytes));
    }

    private static void writeToDisk(Path file, ByteBuffer bytes) throws IOException {
        try (FileChannel channel = FileChannel.open(file, StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE)) {
            while (bytes.hasRemaining()) {
                channel.write(bytes);
            }
            channel.force(true);
        }
    }

    /** The one folder-pack file that defines {@code biome}; otherwise an exception that says why it can't be edited. */
    public static Path locate(Path datapacksDir, NamespacedKey biome) throws BiomeTuningException {
        if (datapacksDir == null || !Files.isDirectory(datapacksDir)) {
            throw new BiomeTuningException("no datapacks folder found for the primary world");
        }
        String relative = "data/" + biome.getNamespace() + "/worldgen/biome/" + biome.getKey() + ".json";
        List<Path> folders = new ArrayList<>();
        List<String> zips = new ArrayList<>();
        try (Stream<Path> packs = Files.list(datapacksDir)) {
            for (Path pack : packs.sorted().toList()) {
                if (Files.isDirectory(pack)) {
                    File candidate;
                    try {
                        candidate = PathSafety.resolveInside(pack.toFile(), relative);
                    } catch (SecurityException e) {
                        throw new BiomeTuningException("not a valid biome id: " + biome);
                    }
                    if (candidate.isFile()) {
                        folders.add(candidate.toPath());
                    }
                } else if (pack.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".zip")
                        && zipContains(pack, relative)) {
                    zips.add(pack.getFileName().toString());
                }
            }
        } catch (IOException e) {
            throw new BiomeTuningException("could not read the datapacks folder: " + e.getMessage(), e);
        }
        if (!zips.isEmpty()) {
            throw new BiomeTuningException(biome + " is defined in the zip pack " + zips.get(0)
                    + "; unzip it into a folder pack to edit it");
        }
        if (folders.size() > 1) {
            throw new BiomeTuningException(biome + " is defined by more than one pack: " + folders);
        }
        if (folders.isEmpty()) {
            throw new BiomeTuningException(biome + " is not defined by a datapack in this world");
        }
        return folders.get(0);
    }

    private static boolean zipContains(Path zip, String entry) throws IOException {
        try (ZipFile file = new ZipFile(zip.toFile())) {
            return file.getEntry(entry) != null;
        }
    }
}
