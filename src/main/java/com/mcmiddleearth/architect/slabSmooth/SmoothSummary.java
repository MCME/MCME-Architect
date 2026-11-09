package com.mcmiddleearth.architect.slabSmooth;

import com.mcmiddleearth.architect.slabSmooth.core.Smoother;
import com.mcmiddleearth.architect.slabSmooth.core.Table;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** What a smoothing tells its player afterwards: Toti's wording, for selections, areas and brushes alike. */
final class SmoothSummary {

    private SmoothSummary() {
    }

    /**
     * What changed, in one line: the columns or surfaces, the blocks, and the pieces by kind.
     *
     * @param blocks the blocks written
     * @param all    true when tops, walls and overhangs were smoothed (counted in surfaces), false for tops only
     *               (counted in columns)
     */
    static String summary(Smoother.Report report, int blocks, boolean all) {
        String unit = all ? "surfaces" : "columns";
        if (blocks == 0) {
            return "Nothing to change: " + report.freeColumns() + " " + unit + " could change, none needed to.";
        }
        Map<String, Integer> byKind = new TreeMap<>();
        report.pieces().forEach((k, n) -> byKind.merge(k.substring(k.indexOf(' ') + 1), n, Integer::sum));
        List<String> parts = new ArrayList<>();
        byKind.forEach((kind, n) -> parts.add(n + " " + switch (kind) {
            case "slab" -> "slabs";
            case "top slab" -> "top slabs";
            case "vslab" -> "vertical slabs";
            case "vstairs" -> "vertical stairs";
            default -> kind;
        }));
        return "Smoothed " + report.changedColumns() + " of " + report.freeColumns() + " " + unit + ", " + blocks + " blocks"
                + (parts.isEmpty() ? "" : ": " + String.join(", ", parts))
                + (report.plantsRemoved() > 0 ? ", " + report.plantsRemoved() + " plants removed" : "");
    }

    /**
     * When an edit changed nothing: names a block in the area that isn't terrain in this pack but has pieces of its
     * own, so "blocks all" would smooth it. Empty when there is none (or blocks is already all). Only for players who
     * may use blocks all.
     */
    static String blocksHint(List<String> palette, Table terrain, Table all) {
        if (terrain == all) {
            return "";
        }
        for (String state : palette) {
            Table.Kind3 k = all.kind3(state);
            if (k.category() == Table.TERRAIN && terrain.kind3(state).category() != Table.TERRAIN) {
                return " " + all.materials.get(k.material()).label + " isn't terrain in this pack; /sls set blocks all smooths it too.";
            }
        }
        return "";
    }
}
