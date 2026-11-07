package com.mcmiddleearth.architect.slabSmooth.core;

import com.mcmiddleearth.architect.slabSmooth.core.Shapes.Config;
import com.mcmiddleearth.architect.slabSmooth.core.Shapes.Piece;

import java.util.ArrayDeque;
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
import static com.mcmiddleearth.architect.slabSmooth.core.Table.OTHER;
import static com.mcmiddleearth.architect.slabSmooth.core.Table.PIECE;
import static com.mcmiddleearth.architect.slabSmooth.core.Table.TERRAIN;

/**
 * Smooths tops, walls and overhangs: the top-only smoother ({@link Smoother}, same energy and solver) run in six
 * directions. In the direction of a wall, "up" points out of the wall: a slab there is a vertical slab, a stair is a
 * stair turned on its side, and a step of {@code cliff} or more (a top seen from the side) is left to the direction
 * it faces. Undersides use top slabs and upside-down stairs.
 *
 * <p>Every surface block belongs to one direction: the one it faces most (from the blurred terrain), among the
 * directions its material has pieces for, with tops preferred on 45 degree slopes. Grass, which only has a slab, so
 * stays with the tops as before. The directions run one after another, tops first, each on the result of the last.
 *
 * <p>A line along a direction can cross several surfaces (the ground under an overhang, and the overhang's top).
 * Each surface is a node; nodes in neighbouring lines are neighbours when they are the nearest in height.
 */
public final class Directional {

    private Directional() {
    }

    /** Directions, in the order they run: tops, walls, undersides. */
    public static final int UP = 0, DOWN = 1, EAST = 2, WEST = 3, SOUTH = 4, NORTH = 5;
    private static final int[] ORDER = {UP, EAST, WEST, SOUTH, NORTH, DOWN};
    private static final String[] NAMES = {"tops", "undersides", "east walls", "west walls", "south walls", "north walls"};
    private static final double TOP_PREFERENCE = 0.1;

    static final int NONE = Smoother.NONE, FIXED = Smoother.FIXED, FREE = Smoother.FREE;
    static final byte ADD_NONE = 0, ADD_SLAB = 1, ADD_ANY = 2;

    /**
     * Smooths every surface. Same block layout and result as {@link Smoother#smooth}.
     *
     * @param editable null, or per block whether it may change.
     */
    public static Smoother.Result smooth(int[] blocks, int height, int length, int width, List<String> palette,
                                         Table table, Options opts, boolean[] editable, Consumer<String> log) {
        World world = new World(blocks, height, length, width, palette, table);
        byte[] owner = owners(world, opts);
        Totals totals = new Totals();
        for (int dir : ORDER) {
            Pass pass = new Pass(world, new Frame(dir, width, height, length), opts, editable, owner, false);
            pass.run(log, totals);
        }
        return totals.result(world);
    }

    /** Tops only, topmost surface per column, with the top-only smoother's ground rules: must match it exactly. */
    static Smoother.Result smoothTopsLikePrototype(int[] blocks, int height, int length, int width, List<String> palette,
                                                   Table table, Options opts) {
        World world = new World(blocks, height, length, width, palette, table);
        Totals totals = new Totals();
        new Pass(world, new Frame(UP, width, height, length), opts, null, null, true).run(null, totals);
        return totals.result(world);
    }

    // ---------------------------------------------------------------------------------------------------------

    /** The area being smoothed: blocks change as the directions run. */
    static final class World {
        final int[] original;
        final int[] blocks;
        final int h, l, w, n;
        final List<String> palette;
        final List<Table.Kind3> kinds = new ArrayList<>();
        final Map<String, Integer> index = new HashMap<>();
        final Table table;

        World(int[] blocks, int h, int l, int w, List<String> palette, Table table) {
            this.original = blocks;
            this.blocks = blocks.clone();
            this.h = h;
            this.l = l;
            this.w = w;
            this.n = h * l * w;
            this.palette = new ArrayList<>(palette);
            this.table = table;
            for (int i = 0; i < this.palette.size(); i++) {
                kinds.add(table.kind3(this.palette.get(i)));
                index.putIfAbsent(this.palette.get(i), i);
            }
        }

        int state(String state) {
            return index.computeIfAbsent(state, s -> {
                palette.add(s);
                kinds.add(table.kind3(s));
                return palette.size() - 1;
            });
        }

        Table.Kind3 kind(int i) {
            return kinds.get(blocks[i]);
        }

        int at(int x, int y, int z) {
            return (y * l + z) * w + x;
        }

        boolean free(int i) {
            int c = kind(i).category();
            return c == AIR || c == DECOR;
        }

        boolean ground(int i) {
            int c = kind(i).category();
            return c == TERRAIN || c == PIECE;
        }

        /**
         * Whether air block i is a one-block gap along an axis (0 x, 1 z, 2 y): something other than air or plants on
         * both sides. Outside the area counts as open.
         */
        boolean narrow(int i, int axis) {
            int x = i % w, z = (i / w) % l, y = i / (w * l);
            int dx = axis == 0 ? 1 : 0, dz = axis == 1 ? 1 : 0, dy = axis == 2 ? 1 : 0;
            if (x - dx < 0 || x + dx >= w || z - dz < 0 || z + dz >= l || y - dy < 0 || y + dy >= h) {
                return false;
            }
            return !free(at(x - dx, y - dy, z - dz)) && !free(at(x + dx, y + dy, z + dz));
        }

        /** Ground that is part of the real terrain: a vertical run reaching the bottom of the area or at least
         *  min_thickness deep, and everything connected to it (so overhangs count, tree tops of leaf grass don't). */
        boolean[] support(int minThickness) {
            boolean[] ground = new boolean[n];
            for (int i = 0; i < n; i++) {
                ground[i] = ground(i);
            }
            boolean[] out = Smoother.supported(ground, h, l * w, minThickness);
            ArrayDeque<Integer> queue = new ArrayDeque<>();
            for (int i = 0; i < n; i++) {
                if (out[i]) {
                    queue.add(i);
                }
            }
            while (!queue.isEmpty()) {
                int i = queue.poll();
                int x = i % w, z = (i / w) % l, y = i / (w * l);
                for (int d = 0; d < 6; d++) {
                    int nx = x + Octants.DX[d], ny = y + Octants.DY[d], nz = z + Octants.DZ[d];
                    if (nx < 0 || nx >= w || ny < 0 || ny >= h || nz < 0 || nz >= l) {
                        continue;
                    }
                    int j = at(nx, ny, nz);
                    if (ground[j] && !out[j]) {
                        out[j] = true;
                        queue.add(j);
                    }
                }
            }
            return out;
        }
    }

    /**
     * A direction's view of the area: frame x and z across, frame y ("up") pointing out of the surfaces it smooths.
     * Maps frame blocks and octants to world ones.
     */
    static final class Frame {
        final int dir, fw, fh, fl, w, h, l;
        final int[] bitToWorld = new int[8], bitToFrame = new int[8];

        Frame(int dir, int w, int h, int l) {
            this.dir = dir;
            this.w = w;
            this.h = h;
            this.l = l;
            int[] dims = switch (dir) {
                case UP, DOWN -> new int[] {w, h, l};
                case EAST, WEST -> new int[] {h, w, l};
                default -> new int[] {w, l, h};
            };
            fw = dims[0];
            fh = dims[1];
            fl = dims[2];
            for (int b = 0; b < 8; b++) {
                int[] p = map(b & 1, (b >> 2) & 1, (b >> 1) & 1, 2, 2, 2);
                int wb = p[0] + 2 * p[2] + 4 * p[1];
                bitToWorld[b] = wb;
                bitToFrame[wb] = b;
            }
        }

        /** World (x, y, z) of frame (fx, fy, fz) in a world of size sw x sh x sl. */
        int[] map(int fx, int fy, int fz, int sw, int sh, int sl) {
            return switch (dir) {
                case UP -> new int[] {fx, fy, fz};
                case DOWN -> new int[] {fx, sh - 1 - fy, fz};
                case EAST -> new int[] {fy, fx, fz};
                case WEST -> new int[] {sw - 1 - fy, fx, fz};
                case SOUTH -> new int[] {fx, fz, fy};
                default -> new int[] {fx, fz, sl - 1 - fy};
            };
        }

        int world(int fx, int fy, int fz) {
            int[] p = map(fx, fy, fz, w, h, l);
            return (p[1] * l + p[2]) * w + p[0];
        }

        int toWorld(int frameMask) {
            int out = 0;
            for (int b = 0; b < 8; b++) {
                if ((frameMask >> b & 1) != 0) {
                    out |= 1 << bitToWorld[b];
                }
            }
            return out;
        }

        int toFrame(int worldMask) {
            int out = 0;
            for (int b = 0; b < 8; b++) {
                if ((worldMask >> b & 1) != 0) {
                    out |= 1 << bitToFrame[b];
                }
            }
            return out;
        }

        /** The world direction "up" points to (index into Octants.DX/DY/DZ). */
        int worldDirection() {
            return switch (dir) {
                case UP -> 4;
                case DOWN -> 5;
                case EAST -> 0;
                case WEST -> 1;
                case SOUTH -> 2;
                default -> 3;
            };
        }
    }

    /** Quadrant heights of a frame octant mask, or null if a quadrant has a gap (upper octant over an empty one). */
    static int[] fill(int frameMask) {
        int[] fill = new int[4];
        for (int q = 0; q < 4; q++) {
            boolean lower = (frameMask >> q & 1) != 0, upper = (frameMask >> (q + 4) & 1) != 0;
            if (upper && !lower) {
                return null;
            }
            fill[q] = (lower ? 1 : 0) + (upper ? 1 : 0);
        }
        return fill;
    }

    static int mask(int[] fill) {
        int m = 0;
        for (int q = 0; q < 4; q++) {
            if (fill[q] >= 1) {
                m |= 1 << q;
            }
            if (fill[q] >= 2) {
                m |= 1 << (q + 4);
            }
        }
        return m;
    }

    // ---------------------------------------------------------------------------------------------------------

    /**
     * Which direction may change each ground block (-1: none). A block can only belong to a direction it is open
     * to and whose pieces its material has; of those it takes the one it faces most, judged from the gradient of
     * the blurred terrain.
     */
    static byte[] owners(World world, Options opts) {
        int h = world.h, l = world.l, w = world.w, n = world.n;
        boolean[] supported = world.support(opts.minThickness);
        float[] num = new float[n], den = new float[n];
        for (int i = 0; i < n; i++) {
            if (supported[i] || world.free(i)) {
                den[i] = 1;
                num[i] = supported[i] ? 1 : 0;
            }
        }
        double sigma = Math.max(opts.sigma, 0.5);
        int radius = (int) Math.ceil(2.5 * sigma);
        float[] kernel = new float[2 * radius + 1];
        for (int k = -radius; k <= radius; k++) {
            kernel[k + radius] = (float) Math.exp(-(k * k) / (2 * sigma * sigma));
        }
        int[] dims = {w, l, h}, strides = {1, w, w * l};
        for (int axis = 0; axis < 3; axis++) {
            blurAxis(num, dims, strides, axis, kernel, radius);
            blurAxis(den, dims, strides, axis, kernel, radius);
        }
        float[] p = new float[n];
        for (int i = 0; i < n; i++) {
            p[i] = den[i] > 1e-4f ? num[i] / den[i] : 0.5f;
        }
        // Which directions each material can build anything in.
        Frame[] frames = new Frame[6];
        boolean[][] capable = new boolean[6][world.table.materials.size()];
        List<Config> configs = Shapes.allConfigs(false, false);
        for (int dir = 0; dir < 6; dir++) {
            frames[dir] = new Frame(dir, w, h, l);
            for (int m = 0; m < capable[dir].length; m++) {
                Table.Material mat = world.table.materials.get(m);
                for (Config c : configs) {
                    if (!Arrays.equals(c.d(), new int[] {2, 2, 2, 2})
                            && buildable(mat, frames[dir].toWorld(mask(lowerFill(c.d()))))
                            && buildable(mat, frames[dir].toWorld(mask(upperFill(c.d()))))) {
                        capable[dir][m] = true;
                        break;
                    }
                }
            }
        }
        byte[] owner = new byte[n];
        Arrays.fill(owner, (byte) -1);
        for (int y = 0; y < h; y++) {
            for (int z = 0; z < l; z++) {
                for (int x = 0; x < w; x++) {
                    int i = world.at(x, y, z);
                    if (!supported[i]) {
                        continue;
                    }
                    int mat = world.kind(i).material();
                    // Outward normal: down the occupancy gradient.
                    double gx = p[world.at(Math.max(x - 1, 0), y, z)] - p[world.at(Math.min(x + 1, w - 1), y, z)];
                    double gy = p[world.at(x, Math.max(y - 1, 0), z)] - p[world.at(x, Math.min(y + 1, h - 1), z)];
                    double gz = p[world.at(x, y, Math.max(z - 1, 0))] - p[world.at(x, y, Math.min(z + 1, l - 1))];
                    double best = Double.NEGATIVE_INFINITY;
                    for (int dir = 0; dir < 6; dir++) {
                        int wd = frames[dir].worldDirection();
                        int nx = x + Octants.DX[wd], ny = y + Octants.DY[wd], nz = z + Octants.DZ[wd];
                        if (nx < 0 || nx >= w || ny < 0 || ny >= h || nz < 0 || nz >= l || !world.free(world.at(nx, ny, nz))
                                || mat < 0 || !capable[dir][mat]) {
                            continue;
                        }
                        double score = gx * Octants.DX[wd] + gy * Octants.DY[wd] + gz * Octants.DZ[wd]
                                + (dir == UP ? TOP_PREFERENCE : 0);
                        if (score > best) {
                            best = score;
                            owner[i] = (byte) dir;
                        }
                    }
                }
            }
        }
        return owner;
    }

    static int[] lowerFill(int[] d) {
        return new int[] {Math.min(d[0], 2), Math.min(d[1], 2), Math.min(d[2], 2), Math.min(d[3], 2)};
    }

    static int[] upperFill(int[] d) {
        return new int[] {Math.max(d[0] - 2, 0), Math.max(d[1] - 2, 0), Math.max(d[2] - 2, 0), Math.max(d[3] - 2, 0)};
    }

    static boolean buildable(Table.Material mat, int worldMask) {
        return worldMask == 0 || (worldMask == Octants.FULL ? mat.fullState != null : mat.shapes.containsKey(worldMask));
    }

    /** Every octant mask the smoother can place (besides air and full blocks), with the world direction (index into
     *  {@link Octants#DX}) of the first pass that places it: the side of the piece that faces out of the surface. */
    static Map<Integer, Integer> pieceMasks() {
        Map<Integer, Integer> out = new java.util.LinkedHashMap<>();
        for (int dir : ORDER) {
            Frame f = new Frame(dir, 1, 1, 1);
            for (Config c : Shapes.allConfigs(true, true)) {
                for (int m : new int[] {f.toWorld(mask(lowerFill(c.d()))), f.toWorld(mask(upperFill(c.d())))}) {
                    if (m != 0 && m != Octants.FULL) {
                        out.putIfAbsent(m, f.worldDirection());
                    }
                }
            }
        }
        return out;
    }

    static void blurAxis(float[] a, int[] dims, int[] strides, int axis, float[] kernel, int radius) {
        int len = dims[axis], stride = strides[axis];
        float[] line = new float[len];
        int o1 = (axis + 1) % 3, o2 = (axis + 2) % 3;
        for (int i1 = 0; i1 < dims[o1]; i1++) {
            for (int i2 = 0; i2 < dims[o2]; i2++) {
                int base = i1 * strides[o1] + i2 * strides[o2];
                for (int k = 0; k < len; k++) {
                    line[k] = a[base + k * stride];
                }
                for (int k = 0; k < len; k++) {
                    float s = 0;
                    for (int m = Math.max(0, k - radius); m <= Math.min(len - 1, k + radius); m++) {
                        s += kernel[m - k + radius] * line[m];
                    }
                    a[base + k * stride] = s;
                }
            }
        }
    }

    // ---------------------------------------------------------------------------------------------------------

    static final class Totals {
        int nodes, free, changed, plantsRemoved, sweeps;
        double before, after;
        final Map<String, Integer> pieces = new TreeMap<>();

        Smoother.Result result(World world) {
            return new Smoother.Result(world.blocks, world.palette, new Smoother.Report(nodes, free, changed, pieces,
                    plantsRemoved, Math.round(before * 10) / 10.0, Math.round(after * 10) / 10.0, sweeps));
        }
    }

    /** One direction: the top-only smoother on the frame, with several surfaces per line. */
    static final class Pass {
        final World world;
        final Frame f;
        final Options opts;
        final boolean prototype; // tops like the Python prototype: topmost surface only, vertical-run ground
        final int cols;
        final int[] frameToWorld;
        final byte[] cat;
        final short[] mat;
        final boolean[] isGround;

        // Nodes: one per surface.
        int count;
        int[] line, s, material;
        byte[] status;
        int[] cur;            // 4 quadrant tops per node, in half blocks
        boolean[] plantAbove;
        byte[] mayAdd;        // what may go into the air block beyond the surface: ADD_NONE, ADD_SLAB or ADD_ANY
        int[][] lineNodes;    // nodes per line, highest first
        int[][] links;        // per node, per neighbour offset (dz, dx index): neighbour node or -1

        Pass(World world, Frame f, Options opts, boolean[] editable, byte[] owner, boolean prototype) {
            this.world = world;
            this.f = f;
            this.opts = opts;
            this.prototype = prototype;
            cols = f.fw * f.fl;
            int fn = cols * f.fh;
            frameToWorld = new int[fn];
            cat = new byte[fn];
            mat = new short[fn];
            boolean[] ground = new boolean[fn];
            boolean[] supported = prototype ? null : world.support(opts.minThickness);
            for (int fy = 0; fy < f.fh; fy++) {
                for (int fz = 0; fz < f.fl; fz++) {
                    for (int fx = 0; fx < f.fw; fx++) {
                        int fi = (fy * f.fl + fz) * f.fw + fx;
                        int wi = f.world(fx, fy, fz);
                        frameToWorld[fi] = wi;
                        Table.Kind3 k = world.kind(wi);
                        int category = k.category();
                        if (category == PIECE && prototype && fill(f.toFrame(k.mask())) == null) {
                            category = OTHER; // the prototype doesn't know top slabs and upside-down stairs
                        }
                        cat[fi] = (byte) category;
                        mat[fi] = (short) k.material();
                        ground[fi] = category == TERRAIN || category == PIECE;
                    }
                }
            }
            if (prototype) {
                isGround = Smoother.supported(ground, f.fh, cols, opts.minThickness);
            } else {
                isGround = new boolean[fn];
                for (int fi = 0; fi < fn; fi++) {
                    isGround[fi] = ground[fi] && supported[frameToWorld[fi]];
                }
            }
            findNodes(editable, owner);
        }

        int block(int fy, int c) {
            return fy * cols + c;
        }

        int frameMask(int fi) {
            Table.Kind3 k = world.kind(frameToWorld[fi]);
            return f.toFrame(k.mask());
        }

        void findNodes(boolean[] editable, byte[] owner) {
            List<int[]> found = new ArrayList<>(); // {line, s}
            lineNodes = new int[cols][];
            for (int c = 0; c < cols; c++) {
                List<Integer> here = new ArrayList<>();
                for (int y = f.fh - 1; y >= 0; y--) {
                    if (isGround[block(y, c)] && (y + 1 == f.fh || !isGround[block(y + 1, c)])) {
                        here.add(found.size());
                        found.add(new int[] {c, y});
                        if (prototype) {
                            break;
                        }
                    }
                }
                lineNodes[c] = here.stream().mapToInt(Integer::intValue).toArray();
            }
            count = found.size();
            line = new int[count];
            s = new int[count];
            material = new int[count];
            status = new byte[count];
            cur = new int[count * 4];
            plantAbove = new boolean[count];
            mayAdd = new byte[count];
            for (int k = 0; k < count; k++) {
                int c = found.get(k)[0], top = found.get(k)[1];
                line[k] = c;
                s[k] = top;
                int sb = block(top, c);
                boolean freeAbove;
                if (top + 1 < f.fh) {
                    int above = cat[block(top + 1, c)];
                    freeAbove = above == AIR || above == DECOR;
                } else {
                    freeAbove = editable == null;
                }
                status[k] = (byte) (freeAbove ? FREE : FIXED);
                material[k] = mat[sb];
                int[] fill = cat[sb] == PIECE ? fill(frameMask(sb)) : new int[] {2, 2, 2, 2};
                if (fill == null) {
                    // Not a height field this way up (a top slab seen from above): keep it, using its top.
                    int m = frameMask(sb);
                    fill = new int[4];
                    for (int q = 0; q < 4; q++) {
                        fill[q] = (m >> (q + 4) & 1) != 0 ? 2 : (m >> q & 1) != 0 ? 1 : 0;
                    }
                    status[k] = FIXED;
                }
                int belowCat = cat[block(Math.max(top - 1, 0), c)];
                if (cat[sb] == PIECE && contains(fill, 0) && belowCat != TERRAIN) {
                    status[k] = FIXED; // a gap reaching into a block that isn't solid ground
                }
                for (int q = 0; q < 4; q++) {
                    cur[k * 4 + q] = 2 * top + fill[q];
                }
                plantAbove[k] = f.dir == UP && top + 1 < f.fh && cat[block(top + 1, c)] == DECOR;
                mayAdd[k] = prototype ? ADD_ANY : top + 1 < f.fh ? roomFor(frameToWorld[block(top + 1, c)]) : ADD_NONE;
                if ("keep".equals(opts.plants) && top + 1 < f.fh && cat[block(top + 1, c)] == DECOR) {
                    status[k] = FIXED;
                }
                int fz = c / f.fw, fx = c % f.fw, b = opts.border;
                if (b > 0 && (fz < b || fz >= f.fl - b || fx < b || fx >= f.fw - b)) {
                    status[k] = FIXED;
                }
                int wi = frameToWorld[sb];
                if (owner != null && owner[wi] != f.dir) {
                    status[k] = FIXED;
                }
                if (editable != null && (!editable[wi] || top + 1 < f.fh && !editable[frameToWorld[block(top + 1, c)]])) {
                    status[k] = FIXED;
                }
            }
            // Neighbours: in each neighbouring line, the surface nearest in height.
            links = new int[count][9];
            for (int k = 0; k < count; k++) {
                int fz = line[k] / f.fw, fx = line[k] % f.fw;
                for (int dz = -1; dz <= 1; dz++) {
                    for (int dx = -1; dx <= 1; dx++) {
                        links[k][(dz + 1) * 3 + dx + 1] = nearest(fz + dz, fx + dx, s[k]);
                    }
                }
            }
        }

        /**
         * What may go into air block i in this direction without clogging a tight crevice (a one-block gap: no air
         * on either side along an axis). Nothing that narrows the gap it is pushed into (a vertical slab in a
         * one-block crevice, a slab under a one-block-high ceiling). In a one-block crevice only a plain slab on its
         * floor, or a top slab under its ceiling: stairs and vertical slabs would reach across and close it.
         */
        byte roomFor(int i) {
            int axis = f.worldDirection() / 2; // 0 x, 1 z, 2 y
            if (world.narrow(i, axis)) {
                return ADD_NONE;
            }
            boolean narrow = world.narrow(i, 0) || world.narrow(i, 1) || world.narrow(i, 2);
            if (!narrow) {
                return ADD_ANY;
            }
            return axis == 2 ? ADD_SLAB : ADD_NONE;
        }

        int nearest(int fz, int fx, int height) {
            if (fz < 0 || fz >= f.fl || fx < 0 || fx >= f.fw) {
                return -1;
            }
            int best = -1, bestDist = Integer.MAX_VALUE;
            for (int k : lineNodes[fz * f.fw + fx]) {
                int dist = Math.abs(s[k] - height);
                if (dist < bestDist) {
                    best = k;
                    bestDist = dist;
                }
            }
            return best;
        }

        int link(int k, int dz, int dx) {
            return links[k][(dz + 1) * 3 + dx + 1];
        }

        /** Blurred quadrant tops (half blocks), ignoring neighbours across cliffs. nodes x 4; only for free nodes,
         *  the only ones that use it. */
        double[] target() {
            double sigma = Math.max(opts.sigma * 2, 1e-6); // octant units
            int radius = (int) Math.ceil(2.5 * sigma);
            int side = 2 * radius + 1;
            int[] offZ = new int[side * side], offX = new int[side * side];
            double[] weight = new double[side * side];
            int offsets = 0;
            for (int dz = -radius; dz <= radius; dz++) {
                for (int dx = -radius; dx <= radius; dx++) {
                    double wgt = Math.exp(-(dz * dz + dx * dx) / (2 * sigma * sigma));
                    if (wgt >= 1e-3) {
                        offZ[offsets] = dz;
                        offX[offsets] = dx;
                        weight[offsets++] = wgt;
                    }
                }
            }
            int gl = 2 * f.fl, gw = 2 * f.fw;
            double[] out = new double[count * 4];
            for (int k = 0; k < count; k++) {
                if (status[k] != FREE) {
                    continue;
                }
                int fz = line[k] / f.fw, fx = line[k] % f.fw;
                for (int q = 0; q < 4; q++) {
                    int i = 2 * fz + Shapes.QZ[q], j = 2 * fx + Shapes.QX[q];
                    double self = cur[k * 4 + q];
                    double num = 0, den = 0;
                    for (int o = 0; o < offsets; o++) {
                        int ni = i + offZ[o], nj = j + offX[o];
                        if (ni < 0 || ni >= gl || nj < 0 || nj >= gw) {
                            continue;
                        }
                        int nq = (ni & 1) * 2 + (nj & 1);
                        double gn = 0, dist = Double.POSITIVE_INFINITY;
                        for (int m : lineNodes[(ni >> 1) * f.fw + (nj >> 1)]) {
                            double v = cur[m * 4 + nq];
                            if (Math.abs(v - self) < dist) {
                                dist = Math.abs(v - self);
                                gn = v;
                            }
                        }
                        if (dist < opts.cliff) {
                            num += weight[o] * 1 * gn;
                            den += weight[o] * 1;
                        }
                    }
                    out[k * 4 + q] = den > 0 ? num / Math.max(den, 1e-9) : self;
                }
            }
            return out;
        }

        // -----------------------------------------------------------------------------------------------------

        List<Config> configs;
        int kc;
        int[] d;
        double[] unary;
        int[] currentChoice, choice;
        boolean[] free;
        double[][] pairOk;   // per pair: nodes
        int[][] pairNode;    // per pair: neighbour node
        double energyBefore, energyAfter;
        int sweeps;

        void build() {
            configs = Shapes.allConfigs(false, false);
            kc = configs.size();
            d = new int[kc * 4];
            int keep = -1;
            for (int i = 0; i < kc; i++) {
                System.arraycopy(configs.get(i).d(), 0, d, i * 4, 4);
                if (Arrays.equals(configs.get(i).d(), new int[] {2, 2, 2, 2})) {
                    keep = i;
                }
            }
            double[] pieceCost = new double[kc];
            for (int i = 0; i < kc; i++) {
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
            double[] internal = new double[kc];
            for (int[] in : Smoother.INTERNAL) {
                double wgt = in[2] == 0 ? 1.0 : opts.wDiag;
                for (int i = 0; i < kc; i++) {
                    int diff = d[i * 4 + in[0]] - d[i * 4 + in[1]];
                    internal[i] += wgt * (diff * diff);
                }
            }
            int materials = world.table.materials.size();
            boolean[][] can = new boolean[materials + 1][kc];
            for (int m = 0; m < materials; m++) {
                Table.Material material = world.table.materials.get(m);
                for (int i = 0; i < kc; i++) {
                    can[m][i] = buildable(material, f.toWorld(mask(lowerFill(configs.get(i).d()))))
                            && buildable(material, f.toWorld(mask(upperFill(configs.get(i).d()))));
                }
            }
            can[materials][keep] = true;

            boolean[] hasUpper = new boolean[kc], upperSlab = new boolean[kc];
            for (int i = 0; i < kc; i++) {
                hasUpper[i] = !configs.get(i).upper().equals(Shapes.AIR);
                upperSlab[i] = configs.get(i).upper().equals(Shapes.SLAB);
            }
            double[] target = target();
            unary = new double[count * kc];
            currentChoice = new int[count];
            Arrays.fill(currentChoice, -1);
            for (int k = 0; k < count; k++) {
                if (status[k] != FREE) {
                    continue;
                }
                int base = 2 * s[k];
                boolean[] row = can[material[k] >= 0 ? material[k] : materials];
                for (int i = 0; i < kc; i++) {
                    boolean allowed = row[i], current = true, allBand = true, allCut = true, allFill = true;
                    double data = 0;
                    for (int q = 0; q < 4; q++) {
                        int cand = base + d[i * 4 + q];
                        int delta = cand - cur[k * 4 + q];
                        allBand &= Math.abs(delta) <= opts.band;
                        allCut &= delta <= 0;
                        allFill &= delta >= 0;
                        current &= delta == 0;
                        double diff = cand - target[k * 4 + q];
                        data += diff * diff;
                    }
                    allowed &= allBand;
                    if (hasUpper[i] && (mayAdd[k] == ADD_NONE || mayAdd[k] == ADD_SLAB && !upperSlab[i])) {
                        allowed = false;
                    }
                    if ("cut".equals(opts.mode)) {
                        allowed &= allCut;
                    } else if ("fill".equals(opts.mode)) {
                        allowed &= allFill;
                    }
                    allowed |= current;
                    if (current && currentChoice[k] < 0) {
                        currentChoice[k] = i;
                    }
                    unary[k * kc + i] = allowed ? opts.wData * data + internal[i] + pieceCost[i] : Double.POSITIVE_INFINITY;
                }
                if (currentChoice[k] < 0) {
                    status[k] = FIXED; // shapes we can't describe stay put
                }
            }
            free = new boolean[count];
            for (int k = 0; k < count; k++) {
                free[k] = status[k] == FREE;
            }
            int pairs = Smoother.PAIRS.length;
            pairOk = new double[pairs][count];
            pairNode = new int[pairs][count];
            for (int p = 0; p < pairs; p++) {
                int[] pr = Smoother.PAIRS[p];
                double wgt = pr[4] == 0 ? 1.0 : opts.wDiag;
                for (int k = 0; k < count; k++) {
                    int nk = link(k, pr[0], pr[1]);
                    pairNode[p][k] = nk;
                    if (nk >= 0 && Math.abs(cur[k * 4 + pr[2]] - cur[nk * 4 + pr[3]]) < opts.cliff) {
                        pairOk[p][k] = wgt;
                    }
                }
            }
        }

        int[] tops() {
            int[] tops = cur.clone();
            for (int k = 0; k < count; k++) {
                if (choice[k] >= 0) {
                    setTops(tops, k);
                }
            }
            return tops;
        }

        void setTops(int[] tops, int k) {
            for (int q = 0; q < 4; q++) {
                tops[k * 4 + q] = 2 * s[k] + d[choice[k] * 4 + q];
            }
        }

        void localEnergy(int[] tops, int k, double[] e) {
            int base = 2 * s[k];
            System.arraycopy(unary, k * kc, e, 0, kc);
            for (int p = 0; p < Smoother.PAIRS.length; p++) {
                double wgt = pairOk[p][k];
                if (wgt == 0) {
                    continue;
                }
                int[] pr = Smoother.PAIRS[p];
                int nt = tops[pairNode[p][k] * 4 + pr[3]];
                for (int i = 0; i < kc; i++) {
                    int diff = base + d[i * 4 + pr[2]] - nt;
                    e[i] += wgt * (diff * diff);
                }
            }
        }

        double energy(int[] tops) {
            double total = 0;
            for (int k = 0; k < count; k++) {
                if (choice[k] >= 0) {
                    total += unary[k * kc + choice[k]];
                }
            }
            double pairs = 0;
            for (int p = 0; p < Smoother.PAIRS.length; p++) {
                int[] pr = Smoother.PAIRS[p];
                for (int k = 0; k < count; k++) {
                    double wgt = pairOk[p][k];
                    if (wgt != 0) {
                        int diff = tops[k * 4 + pr[2]] - tops[pairNode[p][k] * 4 + pr[3]];
                        pairs += wgt * (diff * diff);
                    }
                }
            }
            return total + 0.5 * pairs;
        }

        void solve() {
            SplittableRandom rng = new SplittableRandom(opts.seed + f.dir);
            choice = currentChoice.clone();
            List<int[]> sets = new ArrayList<>();
            for (int pz = 0; pz < 2; pz++) {
                for (int px = 0; px < 2; px++) {
                    List<Integer> set = new ArrayList<>();
                    for (int fz = pz; fz < f.fl; fz += 2) {
                        for (int fx = px; fx < f.fw; fx += 2) {
                            for (int k : lineNodes[fz * f.fw + fx]) {
                                if (free[k]) {
                                    set.add(k);
                                }
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
            double[] e = new double[kc];
            int steps = opts.anneal;
            for (int step = 0; step < steps; step++) {
                double temp = steps == 1 ? 2.0 : 2.0 * Math.pow(0.05 / 2.0, step / (double) (steps - 1));
                for (int[] set : sets) {
                    for (int k : set) {
                        localEnergy(tops, k, e);
                        double min = Double.POSITIVE_INFINITY;
                        for (double v : e) {
                            min = Math.min(min, v);
                        }
                        double sum = 0;
                        for (int i = 0; i < kc; i++) {
                            e[i] = Math.exp(-(e[i] - min) / temp);
                            sum += e[i];
                        }
                        double r = rng.nextDouble() * sum, acc = 0;
                        int pick = -1;
                        for (int i = 0; i < kc; i++) {
                            if (e[i] > 0) {
                                pick = i;
                                acc += e[i];
                                if (acc > r) {
                                    break;
                                }
                            }
                        }
                        choice[k] = pick;
                        setTops(tops, k);
                    }
                }
            }
            int sweep = 0;
            for (; sweep < 100; sweep++) {
                int changed = 0;
                for (int[] set : sets) {
                    for (int k : set) {
                        localEnergy(tops, k, e);
                        int best = 0;
                        for (int i = 1; i < kc; i++) {
                            if (e[i] < e[best]) {
                                best = i;
                            }
                        }
                        if (e[best] < e[choice[k]] - 1e-9) {
                            choice[k] = best;
                            setTops(tops, k);
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
        }

        // -----------------------------------------------------------------------------------------------------

        void run(Consumer<String> log, Totals totals) {
            build();
            solve();
            int air = world.state("minecraft:air");
            int[] before = world.blocks.clone();
            int changedNodes = 0, plantsRemoved = 0;
            for (int k = 0; k < count; k++) {
                int ch = choice[k];
                if (ch < 0 || ch == currentChoice[k]) {
                    continue;
                }
                changedNodes++;
                Config config = configs.get(ch);
                Table.Material material = world.table.materials.get(this.material[k]);
                int c = line[k], top = s[k];
                int surface = frameToWorld[block(top, c)];
                boolean surfaceIsTerrain = world.kinds.get(before[surface]).category() == TERRAIN;
                String original = world.palette.get(before[surface]);
                int lowerMask = f.toWorld(mask(lowerFill(config.d())));
                int upperMask = f.toWorld(mask(upperFill(config.d())));
                if (lowerMask == Octants.FULL) {
                    if (!surfaceIsTerrain) {
                        world.blocks[surface] = world.state(material.fullState);
                    }
                } else if (lowerMask == 0) {
                    world.blocks[surface] = air;
                } else {
                    world.blocks[surface] = world.state(material.shapes.get(lowerMask));
                    totals.pieces.merge(material.name + " " + Octants.kindName(lowerMask), 1, Integer::sum);
                }
                if (f.dir == UP && config.hasZero() && top > 0) {
                    int below = frameToWorld[block(top - 1, c)];
                    if (world.kinds.get(before[below]).category() == TERRAIN && mat[block(top - 1, c)] != this.material[k]) {
                        // Half of the block is cut away: the top of the block below shows, so it takes the surface material.
                        world.blocks[below] = world.state(surfaceIsTerrain ? original : material.fullState);
                    }
                }
                if (top + 1 < f.fh) {
                    int above = frameToWorld[block(top + 1, c)];
                    int tallAbove = top + 2 < f.fh && cat[block(top + 2, c)] == DECOR ? frameToWorld[block(top + 2, c)] : -1;
                    if (upperMask == 0) {
                        if (plantAbove[k] && lowerMask != Octants.FULL) {
                            world.blocks[above] = air;
                            plantsRemoved++;
                            if (tallAbove >= 0) {
                                world.blocks[tallAbove] = air; // the top half of a tall plant
                            }
                        }
                    } else {
                        if (cat[block(top + 1, c)] == DECOR) {
                            plantsRemoved++;
                            int plantTop = f.dir == UP ? tallAbove : aboveInWorld(above, DECOR);
                            if (plantTop >= 0) {
                                world.blocks[plantTop] = air;
                            }
                        }
                        world.blocks[above] = world.state(material.shapes.get(upperMask));
                        totals.pieces.merge(material.name + " " + Octants.kindName(upperMask), 1, Integer::sum);
                    }
                }
            }
            int columns = 0, freeNodes = 0;
            for (int k = 0; k < count; k++) {
                columns += 1;
                freeNodes += free[k] ? 1 : 0;
            }
            totals.nodes += columns;
            totals.free += freeNodes;
            totals.changed += changedNodes;
            totals.plantsRemoved += plantsRemoved;
            totals.before += energyBefore;
            totals.after += energyAfter;
            totals.sweeps = Math.max(totals.sweeps, sweeps);
            if (log != null && freeNodes > 0) {
                log.accept(String.format("%s: %d of %d surfaces changed, energy %.1f -> %.1f, %d sweeps",
                        NAMES[f.dir], changedNodes, freeNodes, energyBefore, energyAfter, sweeps));
            }
        }

        /** The world block above i if it has the given category, else -1. */
        int aboveInWorld(int i, int category) {
            int y = i / (world.w * world.l);
            if (y + 1 >= world.h) {
                return -1;
            }
            int j = i + world.w * world.l;
            return world.kind(j).category() == category ? j : -1;
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
}
