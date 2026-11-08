package com.mcmiddleearth.architect.slabSmooth;

import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.LocalSession;
import com.sk89q.worldedit.MaxChangedBlocksException;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.util.SideEffect;
import com.sk89q.worldedit.world.block.BlockState;
import org.bukkit.Bukkit;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A BlockTarget on a WorldEdit edit session, so masks, limits and //undo apply. It writes without block updates (like
 * /setblock ... strict): stairs keep the shape the smoother chose, pieces that need support (MCME's sugar cane grass
 * slab) stay put, and MCME's repeater and comparator slabs don't switch off a tick later and reshape their neighbours.
 */
final class EditSessionTarget implements BlockTarget {

    private static final String FAWE_EXCEPTION = "com.fastasyncworldedit.core.internal.exception.FaweException";

    /** Block states by their strings, for every target: on FAWE, brush clicks run on several threads at once. */
    private static final Map<String, BlockState> STATES = new ConcurrentHashMap<>();

    private final EditSession edit;
    private final LocalSession rememberIn;

    /**
     * @param rememberIn the session whose history keeps the edit when it closes; null for a brush click, whose edit
     *                   session WorldEdit closes and remembers itself
     */
    EditSessionTarget(EditSession edit, LocalSession rememberIn) {
        this.edit = edit;
        this.rememberIn = rememberIn;
        edit.setSideEffectApplier(edit.getSideEffectApplier()
                .with(SideEffect.NEIGHBORS, SideEffect.State.OFF)
                .with(SideEffect.UPDATE, SideEffect.State.OFF));
    }

    @Override
    public String stateAt(int x, int y, int z) {
        try {
            return edit.getBlock(BlockVector3.at(x, y, z)).getAsString();
        } catch (RuntimeException e) {
            throw translated(e);
        }
    }

    @Override
    public void set(int x, int y, int z, String state) throws MaxChangedBlocksException {
        try {
            edit.setBlock(BlockVector3.at(x, y, z), STATES.computeIfAbsent(state, s -> BukkitAdapter.adapt(Bukkit.createBlockData(s))));
        } catch (RuntimeException e) {
            throw translated(e);
        }
    }

    /**
     * Writes what WorldEdit buffered, then adds the edit to the history even if writing failed, so what got through can
     * be undone. Does nothing for a brush click.
     */
    @Override
    public void close() {
        if (rememberIn == null) {
            return;
        }
        try {
            edit.close();
        } catch (RuntimeException e) {
            throw translated(e);
        } finally {
            rememberIn.remember(edit);
        }
    }

    /**
     * A FaweException (FAWE refusing the edit: no allowed region, or its own limit) becomes a Refused with its message,
     * and anything else is returned as it is. The FaweException stays as the cause: for a brush click, FAWE looks for it
     * there and tells the player itself. FAWE isn't a compile dependency, so its class is recognised by name.
     */
    static RuntimeException translated(RuntimeException e) {
        for (Class<?> c = e.getClass(); c != null; c = c.getSuperclass()) {
            if (c.getName().equals(FAWE_EXCEPTION)) {
                Refused refused = new Refused(e.getMessage());
                refused.initCause(e);
                return refused;
            }
        }
        return e;
    }
}
