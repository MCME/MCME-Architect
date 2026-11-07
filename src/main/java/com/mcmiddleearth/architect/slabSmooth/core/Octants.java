package com.mcmiddleearth.architect.slabSmooth.core;

import com.mcmiddleearth.architect.slabSmooth.core.Shapes.Piece;

/**
 * A block as 2x2x2 half-block cubes (octants), as an 8-bit mask: bit ox + 2*oz + 4*oy (x east, z south, y up).
 * The lower layer is bits 0-3, in quadrant order NW, NE, SW, SE; the upper layer is bits 4-7.
 */
public final class Octants {

    private Octants() {
    }

    public static final int FULL = 0xFF, SLAB = 0x0F, TOP_SLAB = 0xF0;

    /** A stair: a slab plus a step over the quadrants in {@code high}. Upside down: a top slab, step below. */
    public static int stairs(int high, boolean top) {
        return top ? TOP_SLAB | high : SLAB | high << 4;
    }

    /** Full-height columns over the quadrants in {@code quadrants} (vertical slab or vertical stair). */
    public static int column(int quadrants) {
        return quadrants | quadrants << 4;
    }

    public static int of(Piece piece) {
        return switch (piece.kind()) {
            case AIR -> 0;
            case FULL -> FULL;
            case SLAB -> SLAB;
            case STAIRS -> stairs(piece.high(), false);
            case VSLAB, VSTAIRS -> column(piece.high());
        };
    }

    /** Name of a piece's kind, for reports (the prototype's names, plus "top slab" and "upside-down stairs"). */
    public static String kindName(int mask) {
        int lower = mask & 0x0F, upper = mask >> 4;
        if (mask == SLAB) {
            return "slab";
        }
        if (mask == TOP_SLAB) {
            return "top slab";
        }
        if (lower == 0x0F) {
            return "stairs";
        }
        if (upper == 0x0F) {
            return "upside-down stairs";
        }
        if (lower == upper) {
            return Integer.bitCount(lower) == 2 ? "vslab" : "vstairs";
        }
        return "piece";
    }

    /** Block directions: 0 +x, 1 -x, 2 +z, 3 -z, 4 +y, 5 -y. */
    static final int[] DX = {1, -1, 0, 0, 0, 0}, DY = {0, 0, 0, 0, 1, -1}, DZ = {0, 0, 1, -1, 0, 0};
}
