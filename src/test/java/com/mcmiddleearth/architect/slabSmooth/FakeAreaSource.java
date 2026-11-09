package com.mcmiddleearth.architect.slabSmooth;

import com.sk89q.worldedit.math.BlockVector3;
import org.bukkit.World;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

/**
 * AreaSource for the wiring and command tests: it reads an area from a function of the position. A load completes at
 * once, as for chunks Paper has loaded already, or while hold is set, when the test finishes it. Main thread only, as
 * ChunkAreaSource.
 */
final class FakeAreaSource implements AreaSource {

    /** One load: what was asked for, and its future. */
    record Load(World world, BlockVector3 min, BlockVector3 max, CompletableFuture<LoadedArea> future) {
    }

    private final Function<BlockVector3, String> blocks;
    private final List<Load> loads = new ArrayList<>();
    /** True: a load waits for {@link #finishLoading}, as one does while Paper reads the chunks. */
    boolean hold;
    /** How often a loaded area's chunks were let go. */
    int releases;

    /** @param blocks the block state at a position, such as FakeSmoothWorldEdit's stateAt, which its writes read */
    FakeAreaSource(Function<BlockVector3, String> blocks) {
        this.blocks = blocks;
    }

    @Override
    public CompletableFuture<LoadedArea> load(World world, BlockVector3 min, BlockVector3 max) {
        Load load = new Load(world, min, max, new CompletableFuture<>());
        loads.add(load);
        if (!hold) {
            finish(load);
        }
        return load.future();
    }

    /** Every load so far, in order. */
    List<Load> loads() {
        return List.copyOf(loads);
    }

    /** Paper has read the chunks of the latest load. */
    void finishLoading() {
        finish(loads.getLast());
    }

    private void finish(Load load) {
        load.future().complete(new Loaded(load));
    }

    private final class Loaded implements LoadedArea {
        private final Load load;

        Loaded(Load load) {
            this.load = load;
        }

        @Override
        public WorldArea read() {
            World world = load.world();
            WorldArea area = WorldArea.box(load.min(), load.max(), world.getMinHeight(), world.getMaxHeight() - 1);
            int i = 0;
            for (int y = 0; y < area.height; y++) {
                for (int z = 0; z < area.length; z++) {
                    for (int x = 0; x < area.width; x++) {
                        area.blocks[i++] = area.id(blocks.apply(area.min.add(x, y, z)));
                    }
                }
            }
            return area;
        }

        @Override
        public void release() {
            releases++;
        }
    }
}
