package com.mcmiddleearth.architect.slabSmooth;

import com.mcmiddleearth.architect.slabSmooth.core.Table;
import com.sk89q.worldedit.math.BlockVector3;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * PackFootprint on a TableSource that records where it was asked, with fakes of World and Player (proxies, so no
 * server is needed).
 */
class PackFootprintTest {

    private static <T> T fake(Class<T> type, String label) {
        return type.cast(Proxy.newProxyInstance(PackFootprintTest.class.getClassLoader(), new Class<?>[] {type},
                (proxy, method, args) -> switch (method.getName()) {
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    case "toString" -> label;
                    default -> throw new AssertionError("unexpected call: " + method.getName());
                }));
    }

    private final World world = fake(World.class, "world");
    private final Player player = fake(Player.class, "player");

    /**
     * RP regions by location; outside them the normal choice is the player's pack, Dwarven. The packs of the regions
     * whose boxes meet a box are given by the test: none unless it says.
     */
    private final class Tables implements TableSource {
        final Function<Location, String> regions;
        final List<Location> samples = new ArrayList<>();
        int normalChoices;
        /** What regionPacksIn answers; null: the regions can't be read. */
        Set<String> inBoxes = Set.of();
        final List<List<BlockVector3>> boxesAsked = new ArrayList<>();

        Tables(Function<Location, String> regions) {
            this.regions = regions;
        }

        @Override
        public Set<String> regionPacksIn(World in, BlockVector3 min, BlockVector3 max) {
            assertSame(world, in);
            boxesAsked.add(List.of(min, max));
            return inBoxes;
        }

        @Override
        public Packs.Choice choose(Player who, Location location, String named) {
            assertSame(player, who);
            assertEquals(null, location, "the normal choice outside any region is asked without a place");
            assertEquals(null, named);
            normalChoices++;
            return new Packs.Choice("Dwarven", "your pack");
        }

        @Override
        public String regionPack(Location location) {
            assertSame(world, location.getWorld());
            samples.add(location);
            return regions.apply(location);
        }

        @Override
        public Table table(Packs.Choice choice, boolean allBlocks) {
            throw new AssertionError("the footprint needs no table");
        }

        /** The distinct sample coordinates on one axis. */
        TreeSet<Integer> on(Function<Location, Integer> axis) {
            TreeSet<Integer> out = new TreeSet<>();
            samples.forEach(l -> out.add(axis.apply(l)));
            return out;
        }
    }

    @Test
    void aRegionNarrowerThanTheBoxIsFound() {
        // 16 blocks wide in x, in the middle of a box 101 blocks wide: no corner is in it
        Tables tables = new Tables(l -> l.getBlockX() >= 40 && l.getBlockX() <= 55 ? "Human" : null);

        Set<String> packs = PackFootprint.packsIn(world, BlockVector3.at(0, 60, -20), BlockVector3.at(100, 70, 20), player, tables);

        assertEquals(Set.of("Dwarven", "Human"), packs);
    }

    @ParameterizedTest
    @CsvSource({"0, 0", "0, 1", "0, 15", "0, 16", "0, 17", "0, 33", "-40, 7", "-1000, -871", "5, 260"})
    void everySampleIsAtMostSixteenBlocksApart(int lo, int hi) {
        Tables tables = new Tables(l -> null);

        PackFootprint.packsIn(world, BlockVector3.at(lo, 10, lo - 3), BlockVector3.at(hi, 10, hi - 3), player, tables);

        for (Function<Location, Integer> axis : List.<Function<Location, Integer>>of(Location::getBlockX, l -> l.getBlockZ() + 3)) {
            TreeSet<Integer> at = tables.on(axis);
            assertEquals(lo, at.first(), "the box's first corner is sampled");
            assertEquals(hi, at.last(), "the box's last corner is sampled");
            Integer previous = null;
            for (int position : at) {
                assertTrue(position >= lo && position <= hi, position + " is inside the box");
                assertTrue(previous == null || position - previous <= PackFootprint.SAMPLE_STEP,
                        previous + " to " + position + " is more than " + PackFootprint.SAMPLE_STEP + " blocks: " + at);
                previous = position;
            }
        }
        int perAxis = tables.on(Location::getBlockX).size();
        assertEquals(perAxis * perAxis, tables.samples.size(), "every pair of x and z is sampled once, on the one layer");
    }

    @Test
    void theLowestAndHighestLayersAreBothSampled() {
        // a region over the top of the box only, as a 3D RP region can be; its box isn't given here, so only the
        // samples can find it
        Tables tables = new Tables(l -> l.getBlockY() >= 90 ? "Human" : null);

        Set<String> packs = PackFootprint.packsIn(world, BlockVector3.at(0, 10, 0), BlockVector3.at(20, 90, 20), player, tables);

        assertEquals(Set.of(10, 90), tables.on(Location::getBlockY));
        assertEquals(Set.of("Dwarven", "Human"), packs);
    }

    // A region inside a tall box, above its lowest layer and below its highest, is between the samples: its box finds it.
    @Test
    void theRegionsWhoseBoxesMeetTheBoxAreCountedWithTheSamples() {
        Tables tables = new Tables(l -> null);
        tables.inBoxes = Set.of("Elvish");
        BlockVector3 min = BlockVector3.at(0, -64, 0), max = BlockVector3.at(20, 319, 20);

        Set<String> packs = PackFootprint.packsIn(world, min, max, player, tables);

        assertEquals(Set.of("Dwarven", "Elvish"), packs, "the region's pack, and the normal choice the samples found");
        assertEquals(List.of(List.of(min, max)), tables.boxesAsked, "asked once, for the whole box");
    }

    @Test
    void withoutTheRegionsBoxesTheSamplesStillCount() {
        Tables tables = new Tables(l -> l.getBlockX() < 10 ? "Human" : null);
        tables.inBoxes = null; // the regions can't be read

        Set<String> packs = PackFootprint.packsIn(world, BlockVector3.at(0, 0, 0), BlockVector3.at(20, 10, 20), player, tables);

        assertEquals(Set.of("Dwarven", "Human"), packs);
    }

    @Test
    void outsideAnyRegionCountsAsTheNormalChoice() {
        Tables tables = new Tables(l -> null);

        Set<String> packs = PackFootprint.packsIn(world, BlockVector3.at(-50, 0, -50), BlockVector3.at(50, 100, 50), player, tables);

        assertEquals(Set.of("Dwarven"), packs);
    }

    @Test
    void theNormalChoiceIsAskedOnlyOnce() {
        Tables tables = new Tables(l -> l.getBlockX() < 0 ? "Human" : null);

        PackFootprint.packsIn(world, BlockVector3.at(-100, 0, -100), BlockVector3.at(100, 100, 100), player, tables);

        assertTrue(tables.samples.size() > 100, "many samples are outside any region: " + tables.samples.size());
        assertEquals(1, tables.normalChoices, "the player's pack walks the whole RP config, so it is asked once");
    }

    @Test
    void aBoxInsideOneRegionNeedsNoNormalChoice() {
        Tables tables = new Tables(l -> "Human");

        Set<String> packs = PackFootprint.packsIn(world, BlockVector3.at(0, 0, 0), BlockVector3.at(40, 10, 40), player, tables);

        assertEquals(Set.of("Human"), packs);
        assertEquals(0, tables.normalChoices);
    }
}
