package com.mcmiddleearth.architect.WorldGeneration;

import org.bukkit.generator.ChunkGenerator;

public class WorldGenerationManager {
    public static ChunkGenerator getGenerator(String worldName, String id) {
        if(id == null) {
            return new SuperflatChunkGenerator("512");
        }
        String[] split = id.split("_");
        if(split.length>1) {
            return new SuperflatChunkGenerator(split[1]);
        } else {
            return new SuperflatChunkGenerator("512");
        }
    }
}
