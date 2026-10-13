package com.mcmiddleearth.architect.biomeTuning;

/** A biome-tuning failure whose message can be shown to the command sender. */
public class BiomeTuningException extends Exception {

    public BiomeTuningException(String message) {
        super(message);
    }

    public BiomeTuningException(String message, Throwable cause) {
        super(message, cause);
    }
}
