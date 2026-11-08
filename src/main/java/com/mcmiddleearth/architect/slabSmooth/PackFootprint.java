package com.mcmiddleearth.architect.slabSmooth;

import com.sk89q.worldedit.math.BlockVector3;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.Set;
import java.util.TreeSet;

/**
 * The packs a box covers, so a job can refuse to cross a border between packs (Rule 9). At each sample point the pack
 * is the RP region's there, or where no region has one, what the normal choice gives (the player's pack, else the
 * default). The samples are the box's corners and points at most {@link #SAMPLE_STEP} blocks apart between them, on x
 * and z, on the box's lowest and highest layers.
 */
final class PackFootprint {

    static final int SAMPLE_STEP = 16;

    private PackFootprint() {
    }

    /** The packs at the samples of min..max (inclusive), in alphabetical order. Any thread the tables allow. */
    static Set<String> packsIn(World world, BlockVector3 min, BlockVector3 max, Player player, TableSource tables) {
        int[] xs = samples(min.x(), max.x()), zs = samples(min.z(), max.z());
        int[] ys = min.y() == max.y() ? new int[] {min.y()} : new int[] {min.y(), max.y()};
        Set<String> packs = new TreeSet<>();
        String normal = null; // asked once at most: the player's pack walks the whole RP config
        for (int y : ys) {
            for (int x : xs) {
                for (int z : zs) {
                    String pack = tables.regionPack(new Location(world, x, y, z));
                    if (pack == null) {
                        if (normal == null) {
                            normal = tables.choose(player, null, null).pack();
                        }
                        pack = normal;
                    }
                    packs.add(pack);
                }
            }
        }
        return packs;
    }

    /** lo, then every SAMPLE_STEP blocks after it, then hi. */
    private static int[] samples(int lo, int hi) {
        int[] out = new int[(int) (((long) hi - lo + SAMPLE_STEP - 1) / SAMPLE_STEP) + 1];
        for (int i = 0; i < out.length - 1; i++) {
            out[i] = lo + i * SAMPLE_STEP;
        }
        out[out.length - 1] = hi;
        return out;
    }
}
