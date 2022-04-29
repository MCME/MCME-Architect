package com.mcmiddleearth.architect.WorldGeneration;

import com.mcmiddleearth.pluginutil.NumericUtil;
import org.bukkit.Material;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.generator.WorldInfo;
import org.jetbrains.annotations.NotNull;

import java.util.Random;
import java.util.logging.Logger;

public class SuperflatChunkGenerator extends ChunkGenerator {

    private int size;

    public SuperflatChunkGenerator(String size) {
        if(NumericUtil.isInt(size)) {
            this.size = NumericUtil.getInt(size)/16;
        } else {
            this.size = 512/16;
        }
    }

    @Override
    public void generateBedrock(@NotNull WorldInfo worldInfo, @NotNull Random random, int x, int z, @NotNull ChunkData chunkData) {
        int min = worldInfo.getMinHeight();
        if(Math.abs( x) < size && Math.abs(z) < size) {
            for (int i = 0; i < 16; i++) {
                for (int j = 0; j < 16; j++) {
                    chunkData.setBlock(i, min, j, Material.BEDROCK);
                    for (int k = min + 1; k < min + 11; k++) {
                        chunkData.setBlock(i, k, j, Material.DIRT);
                    }
                    chunkData.setBlock(i, min + 11, j, Material.GRASS_BLOCK);
                }
            }
        }
    }

    @Override
    public boolean shouldGenerateNoise() {
        return false;
    }

    @Override
    public boolean shouldGenerateSurface() {
        return false;
    }

    @Override
    public boolean shouldGenerateBedrock() {
        return false;
    }

    @Override
    public boolean shouldGenerateCaves() {
        return false;
    }

    @Override
    public boolean shouldGenerateDecorations() {
        return false;
    }

    @Override
    public boolean shouldGenerateMobs() {
        return false;
    }

    @Override
    public boolean shouldGenerateStructures() {
        return false;
    }
}
