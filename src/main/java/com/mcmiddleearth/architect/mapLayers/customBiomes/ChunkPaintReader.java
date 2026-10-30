package com.mcmiddleearth.architect.mapLayers.customBiomes;

import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.World;
import org.bukkit.block.Biome;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Reads the custom biomes of a loaded chunk into a {@link ChunkPaint}. The game stores biomes per cell of 4 x 4 x 4
 * blocks, so one block in each cell is asked about, from the floor of the world to its ceiling.
 */
public final class ChunkPaintReader {

    /** The width and depth of a chunk, in blocks. */
    private static final int CHUNK = 16;
    /** Biomes are stored per cell of this many blocks along each axis. */
    private static final int CELL = 4;

    /** What the game holds at a block of the chunk being read. */
    @FunctionalInterface
    public interface BiomeSampler {

        /**
         * The id of the custom biome at the block, or null where the biome is vanilla.
         *
         * @param x the block's x inside the chunk, 0 to 15
         * @param y the block's height in the world
         * @param z the block's z inside the chunk, 0 to 15
         */
        String biomeAt(int x, int y, int z);
    }

    private ChunkPaintReader() {
    }

    /**
     * The paint of a chunk, from asking {@code sampler} about one block in each cell. In a column, a biome's span runs
     * from the bottom of its lowest cell to the top of its highest, even where another biome lies in between.
     *
     * @param minY the lowest block of the world
     * @param maxY one past its highest block, as {@code World.getMaxHeight()} gives it
     */
    public static ChunkPaint read(BiomeSampler sampler, int minY, int maxY) {
        List<List<BiomeSpan>> columns = new ArrayList<>(ChunkPaint.COLUMNS);
        for (int z = 0; z < CHUNK; z += CELL) {
            for (int x = 0; x < CHUNK; x += CELL) {
                columns.add(readColumn(sampler, x, z, minY, maxY));
            }
        }
        return ChunkPaint.of(columns);
    }

    private static List<BiomeSpan> readColumn(BiomeSampler sampler, int x, int z, int minY, int maxY) {
        // Going up, a biome's first cell is its lowest, and the latest cell it shows in is its highest so far.
        // Each biome id maps to {bottom block of its lowest cell, top block of its highest}.
        Map<String, int[]> heights = new HashMap<>();
        for (int y = minY; y < maxY; y += CELL) {
            String biome = sampler.biomeAt(x, y, z);
            if (biome == null) {
                continue;
            }
            int top = y + CELL - 1;
            int[] range = heights.get(biome);
            if (range == null) {
                heights.put(biome, new int[] {y, top});
            } else {
                range[1] = top;
            }
        }
        List<BiomeSpan> spans = new ArrayList<>(heights.size());
        heights.forEach((biome, range) -> spans.add(new BiomeSpan(biome, range[0], range[1])));
        return spans;
    }

    /**
     * The paint of the chunk at {@code chunkX}, {@code chunkZ}. Call it on the main thread, for a loaded chunk: asking
     * about an unloaded one does not read what is saved.
     *
     * @param customId turns a biome into its custom id, or null for a vanilla or unknown one; see {@link #customIds()}
     */
    public static ChunkPaint read(World world, int chunkX, int chunkZ, Function<Biome, String> customId) {
        int blockX = chunkX * CHUNK;
        int blockZ = chunkZ * CHUNK;
        return read((x, y, z) -> customId.apply(world.getBiome(blockX + x, y, blockZ + z)), world.getMinHeight(),
                world.getMaxHeight());
    }

    /**
     * Turns a biome into its custom id, such as {@code cbc:111g380-5p}: its key in the server's biome registry. Gives
     * null for a biome in the {@code minecraft} namespace and for one the registry does not know. The answers are
     * remembered, so the function is cheap to call for every cell.
     */
    public static Function<Biome, String> customIds() {
        Registry<Biome> registry = RegistryAccess.registryAccess().getRegistry(RegistryKey.BIOME);
        // Optional, because a ConcurrentHashMap cannot hold a null answer
        Map<Biome, Optional<String>> remembered = new ConcurrentHashMap<>();
        return biome -> remembered
                .computeIfAbsent(biome, unseen -> Optional.ofNullable(idIfCustom(registry.getKey(unseen))))
                .orElse(null);
    }

    /** Every biome id the server's registry defines outside the {@code minecraft} namespace, sorted. Unmodifiable. */
    public static Set<String> customIdsOnThisServer() {
        Set<String> ids = RegistryAccess.registryAccess().getRegistry(RegistryKey.BIOME).keyStream()
                .map(ChunkPaintReader::idIfCustom)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(TreeSet::new));
        return Collections.unmodifiableSet(ids);
    }

    /** The key as an id, or null if the key is missing or in the {@code minecraft} namespace. */
    private static String idIfCustom(NamespacedKey key) {
        return key == null || key.getNamespace().equals(NamespacedKey.MINECRAFT) ? null : key.toString();
    }
}
