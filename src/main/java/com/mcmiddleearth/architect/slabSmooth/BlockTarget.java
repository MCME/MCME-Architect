package com.mcmiddleearth.architect.slabSmooth;

import com.sk89q.worldedit.MaxChangedBlocksException;

/** Where a smoothing writes its result: the world as it is now, block by block, as block state strings. */
interface BlockTarget extends AutoCloseable {

    /** The block as it is now. */
    String stateAt(int x, int y, int z);

    void set(int x, int y, int z, String state) throws MaxChangedBlocksException;

    /** Writes what's buffered and keeps it for //undo. */
    @Override
    void close();

    /** FAWE refused the edit: no allowed region, or its own limit. The message is for the player. */
    final class Refused extends RuntimeException {
        Refused(String message) {
            super(message);
        }
    }
}
