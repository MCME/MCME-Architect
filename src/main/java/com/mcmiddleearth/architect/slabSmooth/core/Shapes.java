package com.mcmiddleearth.architect.slabSmooth.core;

import java.util.ArrayList;
import java.util.List;

/**
 * Block pieces and the column shapes the smoother chooses between.
 *
 * <p>For terrain only "bottom" pieces matter, so a block is described by four quadrant columns, each filled
 * 0, 1 or 2 half blocks high. Quadrants are numbered 0 = north-west, 1 = north-east, 2 = south-west,
 * 3 = south-east (north is -z, east is +x). A set of quadrants is a 4-bit mask.
 */
public final class Shapes {

    private Shapes() {
    }

    public static final int NW = 0, NE = 1, SW = 2, SE = 3;
    static final int[] QX = {0, 1, 0, 1};
    static final int[] QZ = {0, 0, 1, 1};

    public static final int NORTH = 0b0011, SOUTH = 0b1100, WEST = 0b0101, EAST = 0b1010, ALL = 0b1111;

    public enum Kind {
        AIR, FULL, SLAB, STAIRS, VSLAB, VSTAIRS;

        /** Name as in the table reports ("slab", "stairs", ...). */
        public String id() {
            return name().toLowerCase();
        }
    }

    /** kind, and high: the quadrants filled to the top (for a vertical slab its solid side, for a stair its step). */
    public record Piece(Kind kind, int high) {
    }

    public static final Piece AIR = new Piece(Kind.AIR, 0);
    public static final Piece FULL = new Piece(Kind.FULL, ALL);
    public static final Piece SLAB = new Piece(Kind.SLAB, 0);

    /** One way to shape a column: d = 4 quadrant tops in half blocks above the bottom of the surface cell (0-4),
     *  lower = piece in the surface cell, upper = piece in the cell above. */
    public record Config(int[] d, Piece lower, Piece upper) {
        boolean hasZero() {
            for (int v : d) {
                if (v == 0) {
                    return true;
                }
            }
            return false;
        }
    }

    public static int side(String name) {
        return switch (name) {
            case "north" -> NORTH;
            case "south" -> SOUTH;
            case "west" -> WEST;
            case "east" -> EAST;
            default -> -1;
        };
    }

    public static String sideName(int mask) {
        return switch (mask) {
            case NORTH -> "north";
            case SOUTH -> "south";
            case WEST -> "west";
            case EAST -> "east";
            default -> null;
        };
    }

    private static boolean adjacent(int a, int b) {
        return QX[a] == QX[b] || QZ[a] == QZ[b];
    }

    /** Quadrant heights of one block -> the piece with exactly that shape, or null. */
    public static Piece classify(int[] fill) {
        boolean has0 = false, has1 = false, has2 = false;
        int high = 0;
        for (int q = 0; q < 4; q++) {
            switch (fill[q]) {
                case 0 -> has0 = true;
                case 1 -> has1 = true;
                default -> {
                    has2 = true;
                    high |= 1 << q;
                }
            }
        }
        int count = Integer.bitCount(high);
        if (!has1 && !has2) {
            return AIR;
        }
        if (!has0 && !has1) {
            return FULL;
        }
        if (!has0 && !has2) {
            return SLAB;
        }
        if (!has0) { // 1s and 2s
            if (count == 2 && !adjacentPair(high)) {
                return null; // two opposite corners up: no such stair
            }
            return new Piece(Kind.STAIRS, high);
        }
        if (!has1) { // 0s and 2s
            if (count == 2 && adjacentPair(high)) {
                return new Piece(Kind.VSLAB, high);
            }
            if (count == 3) {
                return new Piece(Kind.VSTAIRS, high);
            }
        }
        return null;
    }

    private static boolean adjacentPair(int mask) {
        int a = Integer.numberOfTrailingZeros(mask);
        int b = Integer.numberOfTrailingZeros(mask & ~(1 << a));
        return adjacent(a, b);
    }

    public static int[] pieceFill(Piece piece) {
        return switch (piece.kind()) {
            case AIR -> new int[] {0, 0, 0, 0};
            case FULL -> new int[] {2, 2, 2, 2};
            case SLAB -> new int[] {1, 1, 1, 1};
            default -> {
                int low = piece.kind() == Kind.STAIRS ? 1 : 0;
                int[] fill = new int[4];
                for (int q = 0; q < 4; q++) {
                    fill[q] = (piece.high() & (1 << q)) != 0 ? 2 : low;
                }
                yield fill;
            }
        };
    }

    // Vanilla stairs. "facing" is the side of the full-height step; left and right are as seen looking in the
    // facing direction. Each corner has two names; these are the ones we write.
    private static final String[][] OUTER = {
            {"north", "outer_left"}, {"north", "outer_right"}, {"south", "outer_right"}, {"south", "outer_left"}};
    private static final String[][] INNER_MISSING = {
            {"south", "inner_left"}, {"south", "inner_right"}, {"north", "inner_right"}, {"north", "inner_left"}};

    /** Step quadrants of a bottom stair -> vanilla {facing, shape}. */
    public static String[] stairProperties(int high) {
        return switch (Integer.bitCount(high)) {
            case 2 -> new String[] {sideName(high), "straight"};
            case 1 -> OUTER[Integer.numberOfTrailingZeros(high)];
            case 3 -> INNER_MISSING[Integer.numberOfTrailingZeros(~high & ALL)];
            default -> throw new IllegalArgumentException("not a stair step: " + high);
        };
    }

    /** Inverse of stairProperties for any vanilla name; -1 for top stairs or unknown names. */
    public static int stairHigh(String facing, String shape, String half) {
        if (!"bottom".equals(half)) {
            return -1;
        }
        int front = side(facing);
        String left = switch (facing) {
            case "north" -> "west";
            case "south" -> "east";
            case "east" -> "north";
            case "west" -> "south";
            default -> null;
        };
        if (front < 0 || left == null) {
            return -1;
        }
        int leftSide = side(left);
        int rightSide = ALL & ~leftSide; // the opposite side of the left one
        int back = ALL & ~front;
        return switch (shape) {
            case "straight" -> front;
            case "outer_left" -> front & leftSide;
            case "outer_right" -> front & rightSide;
            case "inner_left" -> front | (back & leftSide);
            case "inner_right" -> front | (back & rightSide);
            default -> -1;
        };
    }

    /** Every column shape whose two cells are real pieces. With the defaults a column never loses or gains a
     *  whole block. The order is fixed (each quadrant's height from 0 to 4, the first quadrant slowest); the golden
     *  scenes depend on it. */
    public static List<Config> allConfigs(boolean allowRemove, boolean allowAdd) {
        List<Config> configs = new ArrayList<>();
        for (int a = 0; a < 5; a++) {
            for (int b = 0; b < 5; b++) {
                for (int c = 0; c < 5; c++) {
                    for (int e = 0; e < 5; e++) {
                        int[] d = {a, b, c, e};
                        int[] lowerFill = new int[4];
                        int[] upperFill = new int[4];
                        for (int q = 0; q < 4; q++) {
                            lowerFill[q] = Math.min(d[q], 2);
                            upperFill[q] = Math.max(d[q] - 2, 0);
                        }
                        Piece lower = classify(lowerFill);
                        Piece upper = classify(upperFill);
                        if (lower == null || upper == null) {
                            continue;
                        }
                        boolean allZero = a == 0 && b == 0 && c == 0 && e == 0;
                        boolean allFour = a == 4 && b == 4 && c == 4 && e == 4;
                        if ((!allowRemove && allZero) || (!allowAdd && allFour)) {
                            continue;
                        }
                        configs.add(new Config(d, lower, upper));
                    }
                }
            }
        }
        return configs;
    }
}
