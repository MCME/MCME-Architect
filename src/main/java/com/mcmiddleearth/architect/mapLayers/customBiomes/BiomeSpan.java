package com.mcmiddleearth.architect.mapLayers.customBiomes;

import java.util.Objects;

/**
 * One custom biome in a column, over a run of heights, as block coordinates with both ends included. Biomes are
 * painted in cells 4 blocks tall, so {@code minY} is the bottom block of the lowest painted cell and {@code maxY} the
 * top block of the highest: that cell's bottom plus 3.
 */
public record BiomeSpan(String biome, int minY, int maxY) {

    public BiomeSpan {
        Objects.requireNonNull(biome, "biome");
    }
}
