package com.mcmiddleearth.architect.slabSmooth.core;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Smoother settings. Names and defaults match the Python prototype's smooth.Options. */
public final class Options {

    public String surfaces = "all";  // "all": tops, walls and overhangs (Directional); "top": tops only (Smoother)
    public int band = 2;             // max change per quadrant, in half blocks
    public int cliff = 6;            // steps this high (half blocks) or more are cliffs, left alone
    public double sigma = 1.5;       // blur of the target surface, in blocks
    public double wData = 0.5;
    public double wDiag = 0.5;       // corner-sharing neighbours count this much of an edge
    public double costStairs = 0.2;
    public double costCorner = 0.1;  // extra for outer and inner corner stairs
    public double costVslab = 1.0;
    public double costFill = 0.05;   // any piece placed on top of the original surface
    public String mode = "both";     // "cut" (only lower), "fill" (only raise) or "both"
    public String plants = "remove"; // "remove" plants where the ground under them changes, or "keep" those columns
    public int border = 1;           // columns this close to the edge of the area stay as they are
    public int minThickness = 5;     // ground runs thinner than this (and not reaching the bottom) are ignored
    public int anneal = 12;          // annealing sweeps before the final descent
    public long seed = 1;
    public String blocks = "terrain"; // "terrain", or "all": also other blocks with pieces of their own (plugin only)

    /** Option names as the prototype spells them (for --set and /slabsmooth set). */
    public static final List<String> NAMES = List.of("surfaces", "band", "cliff", "sigma", "w_data", "w_diag", "cost_stairs",
            "cost_corner", "cost_vslab", "cost_fill", "mode", "plants", "border", "min_thickness", "anneal", "seed", "blocks");

    public Options copy() {
        Options o = new Options();
        values().forEach(o::set);
        return o;
    }

    /** Sets an option by name. Throws IllegalArgumentException for unknown names or bad values. */
    public void set(String name, String value) {
        try {
            switch (name) {
                case "surfaces" -> surfaces = oneOf(value, name, "all", "top");
                case "band" -> band = range(Integer.parseInt(value), 0, 4, name);
                case "cliff" -> cliff = range(Integer.parseInt(value), 1, 64, name);
                case "sigma" -> sigma = range(Double.parseDouble(value), 0, 8, name);
                case "w_data" -> wData = range(Double.parseDouble(value), 0, 100, name);
                case "w_diag" -> wDiag = range(Double.parseDouble(value), 0, 100, name);
                case "cost_stairs" -> costStairs = range(Double.parseDouble(value), -100, 100, name);
                case "cost_corner" -> costCorner = range(Double.parseDouble(value), -100, 100, name);
                case "cost_vslab" -> costVslab = range(Double.parseDouble(value), -100, 100, name);
                case "cost_fill" -> costFill = range(Double.parseDouble(value), -100, 100, name);
                case "mode" -> mode = oneOf(value, name, "both", "cut", "fill");
                case "plants" -> plants = oneOf(value, name, "remove", "keep");
                case "border" -> border = range(Integer.parseInt(value), 0, 16, name);
                case "min_thickness" -> minThickness = range(Integer.parseInt(value), 1, 64, name);
                case "anneal" -> anneal = range(Integer.parseInt(value), 0, 100, name);
                case "seed" -> seed = Long.parseLong(value);
                case "blocks" -> blocks = oneOf(value, name, "terrain", "all");
                default -> throw new IllegalArgumentException("unknown option '" + name + "'; options: " + String.join(", ", NAMES));
            }
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(name + " needs a number, not '" + value + "'");
        }
    }

    public Map<String, String> values() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("surfaces", surfaces);
        m.put("band", String.valueOf(band));
        m.put("cliff", String.valueOf(cliff));
        m.put("sigma", String.valueOf(sigma));
        m.put("w_data", String.valueOf(wData));
        m.put("w_diag", String.valueOf(wDiag));
        m.put("cost_stairs", String.valueOf(costStairs));
        m.put("cost_corner", String.valueOf(costCorner));
        m.put("cost_vslab", String.valueOf(costVslab));
        m.put("cost_fill", String.valueOf(costFill));
        m.put("mode", mode);
        m.put("plants", plants);
        m.put("border", String.valueOf(border));
        m.put("min_thickness", String.valueOf(minThickness));
        m.put("anneal", String.valueOf(anneal));
        m.put("seed", String.valueOf(seed));
        m.put("blocks", blocks);
        return m;
    }

    private static int range(int v, int lo, int hi, String name) {
        if (v < lo || v > hi) {
            throw new IllegalArgumentException(name + " must be between " + lo + " and " + hi);
        }
        return v;
    }

    private static double range(double v, double lo, double hi, String name) {
        if (!(v >= lo && v <= hi)) {
            throw new IllegalArgumentException(name + " must be between " + lo + " and " + hi);
        }
        return v;
    }

    private static String oneOf(String v, String name, String... allowed) {
        for (String a : allowed) {
            if (a.equals(v)) {
                return v;
            }
        }
        throw new IllegalArgumentException(name + " must be one of " + String.join(", ", allowed));
    }
}
