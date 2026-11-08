package com.mcmiddleearth.architect.slabSmooth.core;

import com.mcmiddleearth.architect.slabSmooth.core.Shapes.Config;
import com.mcmiddleearth.architect.slabSmooth.core.Shapes.Piece;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.SplittableRandom;
import java.util.TreeMap;
import java.util.function.Consumer;

import static com.mcmiddleearth.architect.slabSmooth.core.Table.AIR;
import static com.mcmiddleearth.architect.slabSmooth.core.Table.DECOR;
import static com.mcmiddleearth.architect.slabSmooth.core.Table.PIECE;
import static com.mcmiddleearth.architect.slabSmooth.core.Table.TERRAIN;

/**
 * The smoother: turns stair-stepped terrain into slabs, stairs and vertical slabs. A port of the Python
 * prototype (SlabSmooth/slabsmooth/smooth.py); see there for how it works.
 *
 * <p>Blocks are a flat array indexed [y][z][x]: {@code (y * length + z) * width + x}. Columns are indexed
 * {@code z * width + x}.
 */
public final class Smoother {

    private Smoother() {
    }

    static final int NONE = 0, FIXED = 1, FREE = 2;

    /** Neighbouring quadrant pairs: {dz, dx, own quadrant, neighbour quadrant, weight class}. Weight class 0 =
     *  edge-sharing octant columns, 1 = corner-sharing. */
    static final int[][] PAIRS;
    static final int[][] INTERNAL = {{0, 1, 0}, {2, 3, 0}, {0, 2, 0}, {1, 3, 0}, {0, 3, 1}, {1, 2, 1}};

    static {
        int[][][] spec = {
                {{0, 1}, {1, 0, 0}, {3, 2, 0}, {1, 2, 1}, {3, 0, 1}},
                {{0, -1}, {0, 1, 0}, {2, 3, 0}, {0, 3, 1}, {2, 1, 1}},
                {{1, 0}, {2, 0, 0}, {3, 1, 0}, {2, 1, 1}, {3, 0, 1}},
                {{-1, 0}, {0, 2, 0}, {1, 3, 0}, {0, 3, 1}, {1, 2, 1}},
                {{1, 1}, {3, 0, 1}},
                {{-1, 1}, {1, 2, 1}},
                {{1, -1}, {2, 1, 1}},
                {{-1, -1}, {0, 3, 1}},
        };
        List<int[]> pairs = new ArrayList<>();
        for (int[][] s : spec) {
            for (int i = 1; i < s.length; i++) {
                pairs.add(new int[] {s[0][0], s[0][1], s[i][0], s[i][1], s[i][2]});
            }
        }
        PAIRS = pairs.toArray(new int[0][]);
    }

    /** The smoothed blocks (same layout, indices into palette, which extends the input palette) and a report. */
    public record Result(int[] blocks, List<String> palette, Report report) {
    }

    public record Report(int columns, int freeColumns, int changedColumns, Map<String, Integer> pieces,
                         int plantsRemoved, double energyBefore, double energyAfter, int sweeps) {
        public int piecesPlaced() {
            return pieces.values().stream().mapToInt(Integer::intValue).sum();
        }
    }

    /**
     * Smooths blocks[height][length][width].
     *
     * @param editable null, or per column whether it may change. Columns outside still shape the result (they are
     *                 the surroundings the edit blends into). With a mask, ground reaching the top of the area
     *                 counts as covered, since what is above it is unknown.
     */
    public static Result smooth(int[] blocks, int height, int length, int width, List<String> palette, Table table,
                                Options opts, boolean[] editable, Consumer<String> log) {
        return smooth(blocks, height, length, width, palette, table, opts, editable, log, WorkBudget.NONE);
    }

    /** As above, but stops with {@link TooMuchWork} when the budget runs out; nothing changes then. */
    public static Result smooth(int[] blocks, int height, int length, int width, List<String> palette, Table table,
                                Options opts, boolean[] editable, Consumer<String> log, WorkBudget budget) {
        budget.checkTime();
        Terrain t = new Terrain(blocks, height, length, width, palette, table, opts, editable);
        Problem problem = new Problem(t, opts, budget);
        problem.solve(log);
        return apply(t, problem, blocks, palette, table);
    }

    // ---------------------------------------------------------------------------------------------------------

    /** Per-column view of the area: surface cell, material, quadrant tops. */
    static final class Terrain {
        final Table table;
        final int height, length, width, cols;
        final Table.Kind[] kinds;
        final byte[] cat;
        final short[] mat;
        final int[] s;         // y of the surface cell, -1 = no ground
        final byte[] status;
        final int[] material;  // material index of the surface, -1 = none
        final int[] cur;       // quadrant tops in half blocks, cols x 4
        final boolean[] plantAbove;

        Terrain(int[] blocks, int height, int length, int width, List<String> palette, Table table, Options opts,
                boolean[] editable) {
            this.table = table;
            this.height = height;
            this.length = length;
            this.width = width;
            this.cols = length * width;
            int n = height * cols;
            if (blocks.length != n) {
                throw new IllegalArgumentException("blocks has " + blocks.length + " entries, expected " + n);
            }
            kinds = new Table.Kind[palette.size()];
            for (int i = 0; i < kinds.length; i++) {
                kinds[i] = table.kind(palette.get(i));
            }
            cat = new byte[n];
            mat = new short[n];
            boolean[] ground = new boolean[n];
            for (int i = 0; i < n; i++) {
                Table.Kind k = kinds[blocks[i]];
                cat[i] = (byte) k.category();
                mat[i] = (short) k.material();
                ground[i] = k.category() == TERRAIN || k.category() == PIECE;
            }
            boolean[] isGround = supported(ground, height, cols, opts.minThickness);

            s = new int[cols];
            status = new byte[cols];
            material = new int[cols];
            cur = new int[cols * 4];
            plantAbove = new boolean[cols];
            for (int c = 0; c < cols; c++) {
                int top = -1;
                for (int y = height - 1; y >= 0; y--) {
                    if (isGround[y * cols + c]) {
                        top = y;
                        break;
                    }
                }
                boolean anyGround = top >= 0;
                // The surface is the top of the ground; it can change only with open air (or plants) above it.
                boolean freeAbove;
                if (top + 1 < height) {
                    int above = cat[(top + 1) * cols + c];
                    freeAbove = above == AIR || above == DECOR;
                } else {
                    freeAbove = editable == null;
                }
                boolean openTop = anyGround && freeAbove;
                s[c] = anyGround ? top : -1;
                status[c] = (byte) (openTop ? FREE : anyGround ? FIXED : NONE);
                int sClip = Math.clamp(s[c], 0, height - 1);
                material[c] = status[c] != NONE ? mat[sClip * cols + c] : -1;

                if (status[c] != NONE) {
                    Table.Kind kind = kinds[blocks[sClip * cols + c]];
                    int[] fill = kind.category() == PIECE ? Shapes.pieceFill(kind.piece()) : new int[] {2, 2, 2, 2};
                    int belowCat = cat[Math.max(sClip - 1, 0) * cols + c];
                    if (kind.category() == PIECE && contains(fill, 0) && belowCat != TERRAIN) {
                        status[c] = FIXED; // a gap reaching into a block that isn't solid ground
                    }
                    for (int q = 0; q < 4; q++) {
                        cur[c * 4 + q] = 2 * s[c] + fill[q];
                    }
                }
                plantAbove[c] = status[c] != NONE && sClip + 1 < height && cat[(sClip + 1) * cols + c] == DECOR;
                if ("keep".equals(opts.plants) && plantAbove[c] && status[c] == FREE) {
                    status[c] = FIXED;
                }
                int z = c / width, x = c % width, b = opts.border;
                if (b > 0 && (z < b || z >= length - b || x < b || x >= width - b) && status[c] == FREE) {
                    status[c] = FIXED;
                }
                if (editable != null && !editable[c] && status[c] == FREE) {
                    status[c] = FIXED;
                }
            }
        }

        int block(int y, int c) {
            return y * cols + c;
        }
    }

    private static boolean contains(int[] a, int v) {
        for (int x : a) {
            if (x == v) {
                return true;
            }
        }
        return false;
    }

    /** Ground blocks in a vertical run that reaches the bottom of the area or is at least minThickness blocks
     *  deep. Leaves that double as grass (MCME's grass variants are leaf states) then don't turn tree tops into
     *  terrain. */
    static boolean[] supported(boolean[] ground, int height, int cols, int minThickness) {
        boolean[] out = new boolean[ground.length];
        int[] below = new int[ground.length];
        int[] above = new int[ground.length];
        for (int y = 0; y < height; y++) {
            for (int c = 0; c < cols; c++) {
                int i = y * cols + c;
                below[i] = ground[i] ? (y > 0 ? below[i - cols] : 0) + 1 : 0;
            }
        }
        for (int y = height - 1; y >= 0; y--) {
            for (int c = 0; c < cols; c++) {
                int i = y * cols + c;
                above[i] = ground[i] ? (y + 1 < height ? above[i + cols] : 0) + 1 : 0;
            }
        }
        for (int y = 0; y < height; y++) {
            for (int c = 0; c < cols; c++) {
                int i = y * cols + c;
                int run = below[i] + above[i] - 1;
                out[i] = ground[i] && (run >= minThickness || below[i] == y + 1);
            }
        }
        return out;
    }

    /** Blurred quadrant tops (half blocks), ignoring neighbours across cliffs. cols x 4. */
    static double[] target(Terrain t, Options opts) {
        int gl = 2 * t.length, gw = 2 * t.width;
        double[] grid = new double[gl * gw];
        boolean[] valid = new boolean[gl * gw];
        for (int z = 0; z < t.length; z++) {
            for (int x = 0; x < t.width; x++) {
                int c = z * t.width + x;
                for (int q = 0; q < 4; q++) {
                    int g = (2 * z + Shapes.QZ[q]) * gw + 2 * x + Shapes.QX[q];
                    grid[g] = t.cur[c * 4 + q];
                    valid[g] = t.status[c] != NONE;
                }
            }
        }
        double sigma = Math.max(opts.sigma * 2, 1e-6); // octant units
        int radius = (int) Math.ceil(2.5 * sigma);
        List<int[]> offsets = new ArrayList<>();
        List<Double> weights = new ArrayList<>();
        for (int dz = -radius; dz <= radius; dz++) {
            for (int dx = -radius; dx <= radius; dx++) {
                double w = Math.exp(-(dz * dz + dx * dx) / (2 * sigma * sigma));
                if (w >= 1e-3) {
                    offsets.add(new int[] {dz, dx});
                    weights.add(w);
                }
            }
        }
        double[] out = new double[t.cols * 4];
        for (int i = 0; i < gl; i++) {
            for (int j = 0; j < gw; j++) {
                double self = grid[i * gw + j];
                double num = 0, den = 0;
                for (int k = 0; k < offsets.size(); k++) {
                    int ni = i + offsets.get(k)[0], nj = j + offsets.get(k)[1];
                    boolean inside = ni >= 0 && ni < gl && nj >= 0 && nj < gw;
                    // Values outside repeat the edge (numpy "edge" padding); validity outside is false.
                    double gn = grid[Math.clamp(ni, 0, gl - 1) * gw + Math.clamp(nj, 0, gw - 1)];
                    boolean ok = inside && valid[ni * gw + nj] && Math.abs(gn - self) < opts.cliff;
                    double w = weights.get(k);
                    num += w * (ok ? 1 : 0) * gn;
                    den += w * (ok ? 1 : 0);
                }
                double value = den > 0 ? num / Math.max(den, 1e-9) : self;
                int z = i / 2, x = j / 2, q = (i % 2) * 2 + (j % 2);
                out[(z * t.width + x) * 4 + q] = value;
            }
        }
        return out;
    }

    // ---------------------------------------------------------------------------------------------------------

    static final class Problem {
        final Terrain t;
        final Options opts;
        final WorkBudget budget;
        final List<Config> configs = Shapes.allConfigs(false, false);
        final int k;
        final int[] d;               // k x 4
        final double[] unary;        // cols x k (only rows of free columns are filled)
        final int[] currentChoice;   // per column, -1 = not free
        final boolean[] free;
        final double[][] pairOk;     // per pair: cols
        int[] choice;
        double energyBefore, energyAfter;
        int sweeps;

        Problem(Terrain t, Options opts, WorkBudget budget) {
            this.budget = budget;
            this.t = t;
            this.opts = opts;
            k = configs.size();
            d = new int[k * 4];
            int keep = -1;
            for (int i = 0; i < k; i++) {
                System.arraycopy(configs.get(i).d(), 0, d, i * 4, 4);
                if (Arrays.equals(configs.get(i).d(), new int[] {2, 2, 2, 2})) {
                    keep = i;
                }
            }

            double[] pieceCost = new double[k];
            for (int i = 0; i < k; i++) {
                Config c = configs.get(i);
                for (Piece p : new Piece[] {c.lower(), c.upper()}) {
                    if (p.kind() == Shapes.Kind.STAIRS) {
                        pieceCost[i] += opts.costStairs + (Integer.bitCount(p.high()) != 2 ? opts.costCorner : 0);
                    } else if (p.kind() == Shapes.Kind.VSLAB || p.kind() == Shapes.Kind.VSTAIRS) {
                        pieceCost[i] += opts.costVslab;
                    }
                }
                if (!c.upper().equals(Shapes.AIR)) {
                    pieceCost[i] += opts.costFill;
                }
            }
            double[] internal = new double[k];
            for (int[] in : INTERNAL) {
                double w = in[2] == 0 ? 1.0 : opts.wDiag;
                for (int i = 0; i < k; i++) {
                    int diff = d[i * 4 + in[0]] - d[i * 4 + in[1]];
                    internal[i] += w * (diff * diff);
                }
            }

            // Which shapes each material can build; the extra last row (no material) only keeps the column.
            int materials = t.table.materials.size();
            boolean[][] can = new boolean[materials + 1][k];
            for (int m = 0; m < materials; m++) {
                for (int i = 0; i < k; i++) {
                    Config c = configs.get(i);
                    can[m][i] = t.table.materials.get(m).has(c.lower()) && t.table.materials.get(m).has(c.upper());
                }
            }
            can[materials][keep] = true;

            double[] target = target(t, opts);
            budget.checkSurfaces(t.cols);
            unary = new double[t.cols * k];
            currentChoice = new int[t.cols];
            Arrays.fill(currentChoice, -1);
            for (int c = 0; c < t.cols; c++) {
                if (t.status[c] != FREE) {
                    continue;
                }
                int base = 2 * t.s[c];
                boolean[] row = can[t.material[c] >= 0 ? t.material[c] : materials];
                for (int i = 0; i < k; i++) {
                    boolean allowed = row[i], current = true, allBand = true, allCut = true, allFill = true;
                    double data = 0;
                    for (int q = 0; q < 4; q++) {
                        int cand = base + d[i * 4 + q];
                        int delta = cand - t.cur[c * 4 + q];
                        allBand &= Math.abs(delta) <= opts.band;
                        allCut &= delta <= 0;
                        allFill &= delta >= 0;
                        current &= delta == 0;
                        double diff = cand - target[c * 4 + q];
                        data += diff * diff;
                    }
                    allowed &= allBand;
                    if ("cut".equals(opts.mode)) {
                        allowed &= allCut;
                    } else if ("fill".equals(opts.mode)) {
                        allowed &= allFill;
                    }
                    allowed |= current; // the column as it is (also covers pieces from an earlier run)
                    if (current && currentChoice[c] < 0) {
                        currentChoice[c] = i;
                    }
                    unary[c * k + i] = allowed ? opts.wData * data + internal[i] + pieceCost[i] : Double.POSITIVE_INFINITY;
                }
                if (currentChoice[c] < 0) {
                    t.status[c] = FIXED; // shapes we can't describe stay put
                }
            }
            free = new boolean[t.cols];
            for (int c = 0; c < t.cols; c++) {
                free[c] = t.status[c] == FREE;
            }

            // Cliff mask per neighbour pair, from the original heights.
            pairOk = new double[PAIRS.length][t.cols];
            for (int p = 0; p < PAIRS.length; p++) {
                int[] pr = PAIRS[p];
                double w = pr[4] == 0 ? 1.0 : opts.wDiag;
                for (int z = 0; z < t.length; z++) {
                    for (int x = 0; x < t.width; x++) {
                        int nz = z + pr[0], nx = x + pr[1];
                        if (nz < 0 || nz >= t.length || nx < 0 || nx >= t.width) {
                            continue;
                        }
                        int c = z * t.width + x, nc = nz * t.width + nx;
                        if (t.status[c] != NONE && t.status[nc] != NONE
                                && Math.abs(t.cur[c * 4 + pr[2]] - t.cur[nc * 4 + pr[3]]) < opts.cliff) {
                            pairOk[p][c] = w;
                        }
                    }
                }
            }
        }

        /** Quadrant tops for the current choice. */
        int[] tops() {
            int[] tops = t.cur.clone();
            for (int c = 0; c < t.cols; c++) {
                if (choice[c] >= 0) {
                    setTops(tops, c);
                }
            }
            return tops;
        }

        void setTops(int[] tops, int c) {
            for (int q = 0; q < 4; q++) {
                tops[c * 4 + q] = 2 * t.s[c] + d[choice[c] * 4 + q];
            }
        }

        /** Energy of every candidate shape for column c, given the rest. */
        void localEnergy(int[] tops, int c, double[] e) {
            int z = c / t.width, x = c % t.width, base = 2 * t.s[c];
            System.arraycopy(unary, c * k, e, 0, k);
            for (int p = 0; p < PAIRS.length; p++) {
                int[] pr = PAIRS[p];
                double w = pairOk[p][c];
                if (w == 0) {
                    continue;
                }
                int nt = tops[((z + pr[0]) * t.width + x + pr[1]) * 4 + pr[3]];
                for (int i = 0; i < k; i++) {
                    int diff = base + d[i * 4 + pr[2]] - nt;
                    e[i] += w * (diff * diff);
                }
            }
        }

        double energy(int[] tops) {
            double total = 0;
            for (int c = 0; c < t.cols; c++) {
                if (choice[c] >= 0) {
                    total += unary[c * k + choice[c]];
                }
            }
            double pairs = 0;
            for (int p = 0; p < PAIRS.length; p++) {
                int[] pr = PAIRS[p];
                for (int c = 0; c < t.cols; c++) {
                    double w = pairOk[p][c];
                    if (w != 0) {
                        int z = c / t.width, x = c % t.width;
                        int diff = tops[c * 4 + pr[2]] - tops[((z + pr[0]) * t.width + x + pr[1]) * 4 + pr[3]];
                        pairs += w * (diff * diff);
                    }
                }
            }
            return total + 0.5 * pairs;
        }

        void solve(Consumer<String> log) {
            SplittableRandom rng = new SplittableRandom(opts.seed);
            choice = currentChoice.clone();
            // Four interleaved column sets: no two columns in a set are neighbours, so a set can be updated at once.
            List<int[]> sets = new ArrayList<>();
            for (int pz = 0; pz < 2; pz++) {
                for (int px = 0; px < 2; px++) {
                    List<Integer> set = new ArrayList<>();
                    for (int z = pz; z < t.length; z += 2) {
                        for (int x = px; x < t.width; x += 2) {
                            if (free[z * t.width + x]) {
                                set.add(z * t.width + x);
                            }
                        }
                    }
                    if (!set.isEmpty()) {
                        sets.add(set.stream().mapToInt(Integer::intValue).toArray());
                    }
                }
            }
            int[] tops = tops();
            energyBefore = energy(tops);
            double[] e = new double[k];
            double[] prob = new double[k];
            int n = opts.anneal;
            for (int step = 0; step < n; step++) {
                budget.checkTime();
                double temp = n == 1 ? 2.0 : 2.0 * Math.pow(0.05 / 2.0, step / (double) (n - 1));
                for (int[] set : sets) {
                    for (int c : set) {
                        localEnergy(tops, c, e);
                        double min = Double.POSITIVE_INFINITY;
                        for (double v : e) {
                            min = Math.min(min, v);
                        }
                        double sum = 0;
                        for (int i = 0; i < k; i++) {
                            prob[i] = Math.exp(-(e[i] - min) / temp);
                            sum += prob[i];
                        }
                        double r = rng.nextDouble() * sum, acc = 0;
                        int pick = -1;
                        for (int i = 0; i < k; i++) {
                            if (prob[i] > 0) {
                                pick = i;
                                acc += prob[i];
                                if (acc > r) {
                                    break;
                                }
                            }
                        }
                        choice[c] = pick;
                        setTops(tops, c);
                    }
                }
            }
            int sweep = 0;
            for (; sweep < 100; sweep++) {
                budget.checkTime();
                int changed = 0;
                for (int[] set : sets) {
                    for (int c : set) {
                        localEnergy(tops, c, e);
                        int best = 0;
                        for (int i = 1; i < k; i++) {
                            if (e[i] < e[best]) {
                                best = i;
                            }
                        }
                        if (e[best] < e[choice[c]] - 1e-9) {
                            choice[c] = best;
                            setTops(tops, c);
                            changed++;
                        }
                    }
                }
                if (changed == 0) {
                    break;
                }
            }
            sweeps = Math.min(sweep + 1, 100);
            energyAfter = energy(tops);
            if (log != null) {
                log.accept(String.format("energy %.1f -> %.1f after %d annealing and %d descent sweeps",
                        energyBefore, energyAfter, n, sweeps));
            }
        }
    }

    // ---------------------------------------------------------------------------------------------------------

    static Result apply(Terrain t, Problem problem, int[] blocks, List<String> inPalette, Table table) {
        int[] out = blocks.clone();
        List<String> palette = new ArrayList<>(inPalette);
        Map<String, Integer> index = new HashMap<>();
        for (int i = palette.size() - 1; i >= 0; i--) {
            index.put(palette.get(i), i);
        }
        java.util.function.ToIntFunction<String> stateIndex = state -> index.computeIfAbsent(state, st -> {
            palette.add(st);
            return palette.size() - 1;
        });
        int air = stateIndex.applyAsInt("minecraft:air");
        int height = t.height;
        Map<String, Integer> placed = new TreeMap<>();
        int plantsRemoved = 0, changedColumns = 0;
        for (int c = 0; c < t.cols; c++) {
            int ch = problem.choice[c];
            if (ch < 0 || ch == problem.currentChoice[c]) {
                continue;
            }
            changedColumns++;
            Config config = problem.configs.get(ch);
            Table.Material material = table.materials.get(t.material[c]);
            int s = t.s[c];
            Piece lower = config.lower(), upper = config.upper();
            int surface = t.block(s, c);
            String original = palette.get(blocks[surface]);
            boolean surfaceIsTerrain = t.kinds[blocks[surface]].category() == TERRAIN;
            if (lower.equals(Shapes.FULL)) {
                if (!surfaceIsTerrain) {
                    out[surface] = stateIndex.applyAsInt(material.fullState);
                }
            } else if (lower.equals(Shapes.AIR)) {
                out[surface] = air;
            } else {
                out[surface] = stateIndex.applyAsInt(material.state(lower));
                placed.merge(material.name + " " + lower.kind().id(), 1, Integer::sum);
            }
            if (config.hasZero() && s > 0 && t.kinds[blocks[t.block(s - 1, c)]].category() == TERRAIN
                    && t.mat[t.block(s - 1, c)] != t.material[c]) {
                // Half of the block is cut away: the top of the block below shows, so it takes the surface material.
                out[t.block(s - 1, c)] = stateIndex.applyAsInt(surfaceIsTerrain ? original : material.fullState);
            }
            if (s + 1 < height) {
                int above = t.block(s + 1, c);
                boolean tallPlant = s + 2 < height && t.cat[t.block(s + 2, c)] == DECOR;
                if (upper.equals(Shapes.AIR)) {
                    if (t.plantAbove[c] && !lower.equals(Shapes.FULL)) {
                        out[above] = air;
                        plantsRemoved++;
                        if (tallPlant) {
                            out[t.block(s + 2, c)] = air; // the top half of a tall plant
                        }
                    }
                } else {
                    if (t.plantAbove[c]) {
                        plantsRemoved++;
                        if (tallPlant) {
                            out[t.block(s + 2, c)] = air;
                        }
                    }
                    out[above] = stateIndex.applyAsInt(material.state(upper));
                    placed.merge(material.name + " " + upper.kind().id(), 1, Integer::sum);
                }
            }
        }
        int columns = 0, freeColumns = 0;
        for (int c = 0; c < t.cols; c++) {
            columns += t.status[c] != NONE ? 1 : 0;
            freeColumns += problem.free[c] ? 1 : 0;
        }
        Report report = new Report(columns, freeColumns, changedColumns, placed, plantsRemoved,
                Math.round(problem.energyBefore * 10) / 10.0, Math.round(problem.energyAfter * 10) / 10.0, problem.sweeps);
        return new Result(out, palette, report);
    }
}
