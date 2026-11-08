package com.mcmiddleearth.architect.slabSmooth;

import com.sk89q.worldedit.math.BlockVector3;
import org.bukkit.World;

import java.util.concurrent.CompletableFuture;

/** Where a smoothing job reads its terrain. A fake stands in for it in tests. */
interface AreaSource {

    /**
     * Starts reading min..max (inclusive) of a world. Call on the main thread; the future completes on the main thread.
     * It fails with an IllegalArgumentException, whose message is for the player, when part of the area hasn't been
     * generated.
     */
    CompletableFuture<LoadedArea> load(World world, BlockVector3 min, BlockVector3 max);

    /** An area that has been read. Its chunks stay loaded until {@link #release}, so writing doesn't load them again. */
    interface LoadedArea {

        /** The blocks, clamped to the world's height, in a new WorldArea each time. Any thread. */
        WorldArea read();

        /** Lets the chunks unload. Main thread; a second call does nothing. */
        void release();
    }
}
