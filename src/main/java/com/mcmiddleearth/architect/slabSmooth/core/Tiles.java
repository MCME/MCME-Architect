package com.mcmiddleearth.architect.slabSmooth.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Consumer;

/**
 * Runs {@link Directional} over a large area in square tiles, so memory stays small. Each tile reads a margin
 * around it from the blocks as they are after the tiles before it, and only changes blocks inside itself.
 */
public final class Tiles {

    private Tiles() {
    }

    public static Smoother.Result smooth(int[] blocks, int height, int length, int width, List<String> palette, Table table,
                                         Options opts, boolean[] editable, int tile, int margin, Consumer<String> log) {
        return smooth(blocks, height, length, width, palette, table, opts, editable, tile, margin, log, WorkBudget.NONE);
    }

    /** As above, but stops with {@link TooMuchWork} when the budget runs out; nothing changes then. */
    public static Smoother.Result smooth(int[] blocks, int height, int length, int width, List<String> palette, Table table,
                                         Options opts, boolean[] editable, int tile, int margin, Consumer<String> log,
                                         WorkBudget budget) {
        int[] work = blocks.clone();
        List<String> pal = new ArrayList<>(palette);
        int columns = 0, free = 0, changed = 0, plants = 0, sweeps = 0;
        double before = 0, after = 0;
        Map<String, Integer> pieces = new TreeMap<>();
        for (int tz = 0; tz < length; tz += tile) {
            for (int tx = 0; tx < width; tx += tile) {
                budget.checkTime();
                int x0 = Math.max(0, tx - margin), x1 = Math.min(width, tx + tile + margin);
                int z0 = Math.max(0, tz - margin), z1 = Math.min(length, tz + tile + margin);
                int sw = x1 - x0, sl = z1 - z0;
                int[] sub = new int[height * sl * sw];
                boolean[] subEditable = new boolean[sub.length];
                boolean any = false;
                for (int y = 0; y < height; y++) {
                    for (int z = z0; z < z1; z++) {
                        for (int x = x0; x < x1; x++) {
                            int i = (y * length + z) * width + x, j = (y * sl + z - z0) * sw + x - x0;
                            sub[j] = work[i];
                            subEditable[j] = x >= tx && x < tx + tile && z >= tz && z < tz + tile
                                    && (editable == null || editable[i]);
                            any |= subEditable[j];
                        }
                    }
                }
                if (!any) {
                    continue;
                }
                Smoother.Result r = Directional.smooth(sub, height, sl, sw, pal, table, opts, subEditable, log, budget);
                pal = new ArrayList<>(r.palette());
                for (int y = 0; y < height; y++) {
                    for (int z = z0; z < z1; z++) {
                        for (int x = x0; x < x1; x++) {
                            work[(y * length + z) * width + x] = r.blocks()[(y * sl + z - z0) * sw + x - x0];
                        }
                    }
                }
                Smoother.Report rep = r.report();
                columns += rep.columns();
                free += rep.freeColumns();
                changed += rep.changedColumns();
                plants += rep.plantsRemoved();
                before += rep.energyBefore();
                after += rep.energyAfter();
                sweeps = Math.max(sweeps, rep.sweeps());
                rep.pieces().forEach((k, v) -> pieces.merge(k, v, Integer::sum));
            }
        }
        return new Smoother.Result(work, pal, new Smoother.Report(columns, free, changed, pieces, plants,
                Math.round(before * 10) / 10.0, Math.round(after * 10) / 10.0, sweeps));
    }
}
