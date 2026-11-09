package com.mcmiddleearth.architect.slabSmooth;

import com.sk89q.worldedit.IncompleteRegionException;
import com.sk89q.worldedit.MaxChangedBlocksException;
import com.sk89q.worldedit.command.tool.InvalidToolBindException;
import com.sk89q.worldedit.command.tool.brush.Brush;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.regions.Region;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * SmoothWorldEdit for tests. It records every call and the thread it ran on, keeps the world its targets read and
 * write as a map over a base, and throws at the stage a test names. Safe to call from the test's worker thread.
 */
final class FakeSmoothWorldEdit implements SmoothWorldEdit {

    /** One call of open, close, selection or bindBrush, with the thread it ran on. */
    record Call(String method, String thread, World world, Player player) {
    }

    /** A brush bound with bindBrush. */
    record Bound(Player player, double radius, Brush brush, String permission) {
    }

    /** open, close, selection and bindBrush, in order. Sets and reads are too many to list here: see sets and reads. */
    final List<Call> calls = Collections.synchronizedList(new ArrayList<>());
    /**
     * What happened, in order: "open", "close", and whatever a test adds (such as "release" from its AreaSource), so
     * a test can check what came first.
     */
    final List<String> events = Collections.synchronizedList(new ArrayList<>());
    /** The blocks that differ from base: set by a target, or changed by a test (a builder placing a block). */
    final Map<BlockVector3, String> blocks = new ConcurrentHashMap<>();
    /** The world where blocks has nothing: the test's terrain. */
    volatile Function<BlockVector3, String> base = position -> "minecraft:air";
    /** Every block a target set, in order; and the threads that set or read them. */
    final Map<BlockVector3, String> sets = Collections.synchronizedMap(new LinkedHashMap<>());
    final Set<String> setThreads = ConcurrentHashMap.newKeySet(), readThreads = ConcurrentHashMap.newKeySet();
    final List<Bound> brushes = Collections.synchronizedList(new ArrayList<>());

    /** The items WorldEdit's configuration names as its wand and navigation wand; null: none. */
    volatile Material wandItem, navigationWandItem;

    /** True: like FAWE, which writes off the main thread. */
    volatile boolean offTheMainThread;
    /** What selection returns; null: the player has none. */
    volatile Region selection;
    /** What to throw, by stage: "selection", "open", "stateAt", "set", "close" or "bindBrush". */
    final Map<String, Throwable> failures = new ConcurrentHashMap<>();
    /** How many sets go through before "set" throws its failure. */
    volatile int setsBeforeFailure;
    /** Runs after each set that went through, and in close after it is recorded; such as a test closing the jobs. */
    volatile Runnable afterSet = () -> { }, onClose = () -> { };

    /** The world as a target sees it now. */
    String stateAt(BlockVector3 position) {
        String state = blocks.get(position);
        return state != null ? state : base.apply(position);
    }

    /** The calls of one method, in order. */
    List<Call> calls(String method) {
        synchronized (calls) {
            return calls.stream().filter(c -> c.method().equals(method)).toList();
        }
    }

    @Override
    public Region selection(Player player) throws IncompleteRegionException {
        calls.add(new Call("selection", Thread.currentThread().getName(), null, player));
        fail("selection");
        Region region = selection;
        if (region == null) {
            throw new IncompleteRegionException();
        }
        return region.clone();
    }

    @Override
    public BlockTarget open(World world, Player player) {
        calls.add(new Call("open", Thread.currentThread().getName(), world, player));
        events.add("open");
        fail("open");
        return new Target(world, player);
    }

    @Override
    public void bindBrush(Player player, double radius, Brush brush, String permission) throws InvalidToolBindException {
        calls.add(new Call("bindBrush", Thread.currentThread().getName(), null, player));
        fail("bindBrush");
        brushes.add(new Bound(player, radius, brush, permission));
    }

    @Override
    public String wandName(Material item) {
        if (item == wandItem) {
            return "WorldEdit wand";
        }
        return item == navigationWandItem ? "WorldEdit navigation wand" : null;
    }

    @Override
    public boolean writesOffTheMainThread() {
        return offTheMainThread;
    }

    /** Throws the failure set for a stage, if any. Only "set" may be given a MaxChangedBlocksException. */
    private void fail(String stage) {
        Throwable failure = failures.get(stage);
        if (failure == null) {
            return;
        }
        if (failure instanceof RuntimeException e) {
            throw e;
        }
        if (failure instanceof Error e) {
            throw e;
        }
        throw new AssertionError(stage + " can't throw " + failure, failure);
    }

    private final class Target implements BlockTarget {
        private final World world;
        private final Player player;
        private int setsDone;

        Target(World world, Player player) {
            this.world = world;
            this.player = player;
        }

        @Override
        public String stateAt(int x, int y, int z) {
            readThreads.add(Thread.currentThread().getName());
            fail("stateAt");
            return FakeSmoothWorldEdit.this.stateAt(BlockVector3.at(x, y, z));
        }

        @Override
        public void set(int x, int y, int z, String state) throws MaxChangedBlocksException {
            setThreads.add(Thread.currentThread().getName());
            if (setsDone >= setsBeforeFailure) {
                if (failures.get("set") instanceof MaxChangedBlocksException limit) {
                    throw limit;
                }
                fail("set");
            }
            BlockVector3 position = BlockVector3.at(x, y, z);
            blocks.put(position, state);
            sets.put(position, state);
            setsDone++;
            afterSet.run();
        }

        @Override
        public void close() {
            calls.add(new Call("close", Thread.currentThread().getName(), world, player));
            events.add("close");
            onClose.run();
            fail("close");
        }
    }
}
