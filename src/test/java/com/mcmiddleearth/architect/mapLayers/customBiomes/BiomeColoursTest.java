package com.mcmiddleearth.architect.mapLayers.customBiomes;

import com.mcmiddleearth.architect.mapLayers.customBiomes.BiomeColours.Assignment;
import com.mcmiddleearth.architect.mapLayers.customBiomes.PaintRegions.Touch;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import static com.mcmiddleearth.architect.mapLayers.customBiomes.BiomeColours.PALETTE;
import static com.mcmiddleearth.architect.mapLayers.customBiomes.BiomeColours.assign;
import static org.junit.jupiter.api.Assertions.*;

// A biome's colour must differ from the colours of the biomes it touches, and must not change from one redraw or one
// restart to the next, or the map flickers. So what is chosen is kept, and only the later of two clashing biomes moves.
// Within that, the colours are spread over the palette: each biome gets a colour of its own while there are any left.
class BiomeColoursTest {

    /** A touch of two biomes, named in whichever order. */
    private static Touch touch(String one, String two) {
        return one.compareTo(two) < 0 ? new Touch(one, two) : new Touch(two, one);
    }

    /** The touches between biomes named two by two. */
    private static Set<Touch> touches(String... pairs) {
        Set<Touch> touches = new HashSet<>();
        for (int i = 0; i < pairs.length; i += 2) {
            touches.add(touch(pairs[i], pairs[i + 1]));
        }
        return touches;
    }

    private static int index(Map<String, Assignment> colours, String biome) {
        return colours.get(biome).index();
    }

    /** Just the colour of each biome. */
    private static Map<String, Integer> indexes(Map<String, Assignment> colours) {
        Map<String, Integer> indexes = new TreeMap<>();
        colours.forEach((biome, assignment) -> indexes.put(biome, assignment.index()));
        return indexes;
    }

    /** How many biomes have each colour. */
    private static int[] uses(Map<String, Assignment> colours) {
        int[] uses = new int[PALETTE.length];
        colours.values().forEach(assignment -> uses[assignment.index()]++);
        return uses;
    }

    /** Biomes cbc:b00 and up, every one touching every other. */
    private static List<String> crowd(int count, Set<Touch> touching) {
        List<String> ids = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            String id = String.format("cbc:b%02d", i);
            for (String other : ids) {
                touching.add(touch(other, id));
            }
            ids.add(id);
        }
        return ids;
    }

    // ---- the rules of assign ----

    @Test
    void aNewBiomeTakesTheFirstColour() {
        assertEquals(0, assign(Map.of(), Set.of("cbc:a"), Set.of()).get("cbc:a").index());
    }

    @Test
    void touchingBiomesGetDifferentColours() {
        Map<String, Assignment> colours = assign(Map.of(), Set.of("cbc:a", "cbc:b"), touches("cbc:a", "cbc:b"));

        assertEquals(0, index(colours, "cbc:a"));
        assertEquals(1, index(colours, "cbc:b"));
    }

    // Each biome gets a colour of its own while the palette lasts. Sharing one with a biome that is not a neighbour
    // would be allowed, but it is what turns a map of many biomes into a map of a few colours.
    @Test
    void aBiomeThatTouchesOnlyOneOtherStillGetsAColourOfItsOwn() {
        Map<String, Assignment> colours = assign(Map.of(), Set.of("cbc:a", "cbc:b", "cbc:c"),
                touches("cbc:a", "cbc:b", "cbc:b", "cbc:c"));

        assertEquals(0, index(colours, "cbc:a"));
        assertEquals(1, index(colours, "cbc:b"));
        assertEquals(2, index(colours, "cbc:c"), "c could share a's colour 0, but nobody holds 2");
    }

    @Test
    void sevenBiomesThatDoNotTouchGetSevenColours() {
        Set<String> biomes = new TreeSet<>(List.of("cbc:a", "cbc:b", "cbc:c", "cbc:d", "cbc:e", "cbc:f", "cbc:g"));

        Map<String, Assignment> colours = assign(Map.of(), biomes, Set.of());

        int colour = 0;
        for (String biome : biomes) {
            assertEquals(colour++, index(colours, biome), biome);
        }
    }

    // A forest with a coast, a dusk and four more biomes around it, some of which touch each other.
    @Test
    void aTypicalNeighbourhoodOfSevenBiomesGetsSevenColours() {
        Set<Touch> touching = touches("cbc:coast", "cbc:forest", "cbc:dusk", "cbc:forest", "cbc:fireflies",
                "cbc:forest", "cbc:mist", "cbc:forest", "cbc:wind", "cbc:forest", "cbc:smoke", "cbc:forest",
                "cbc:fireflies", "cbc:mist", "cbc:fireflies", "cbc:wind", "cbc:mist", "cbc:smoke", "cbc:wind",
                "cbc:smoke");
        Set<String> biomes = Set.of("cbc:coast", "cbc:dusk", "cbc:fireflies", "cbc:forest", "cbc:mist", "cbc:smoke",
                "cbc:wind");

        Map<String, Assignment> colours = assign(Map.of(), biomes, touching);

        assertEquals(Map.of("cbc:coast", 0, "cbc:dusk", 1, "cbc:fireflies", 2, "cbc:forest", 3, "cbc:mist", 4,
                "cbc:smoke", 5, "cbc:wind", 6), indexes(colours));
    }

    @Test
    void aNewBiomeTakesAColourNoNeighbourHasThatFewBiomesHold() {
        Map<String, Assignment> current = Map.of("cbc:a", new Assignment(0, 1), "cbc:b", new Assignment(1, 2),
                "cbc:c", new Assignment(3, 3), "cbc:d", new Assignment(2, 4));
        Set<String> biomes = Set.of("cbc:a", "cbc:b", "cbc:c", "cbc:d", "cbc:z");

        Map<String, Assignment> all = assign(current, biomes,
                touches("cbc:a", "cbc:z", "cbc:b", "cbc:z", "cbc:c", "cbc:z"));
        Map<String, Assignment> some = assign(current, biomes, touches("cbc:b", "cbc:z", "cbc:c", "cbc:z"));

        assertEquals(4, index(all, "cbc:z"), "z could take 2, which only d holds, but nobody holds 4");
        assertEquals(4, index(some, "cbc:z"), "and 0, which a holds, though a is not a neighbour");
    }

    // The colours of the biomes it touches come first, and how many biomes hold a colour only second.
    @Test
    void aColourNoNeighbourHasWinsOverOneThatFewerBiomesHold() {
        Map<String, Assignment> current = new HashMap<>();
        Set<String> biomes = new HashSet<>(Set.of("cbc:x"));
        Set<Touch> touching = new HashSet<>();
        for (int i = 0; i < 15; i++) { // the neighbours n00 to n14 hold the colours 0 to 14, one each
            String neighbour = String.format("cbc:n%02d", i);
            current.put(neighbour, new Assignment(i, i + 1));
            biomes.add(neighbour);
            touching.add(touch(neighbour, "cbc:x"));
        }
        for (int i = 0; i < 3; i++) { // three biomes that x does not touch hold colour 15
            current.put("cbc:far" + i, new Assignment(15, 16 + i));
            biomes.add("cbc:far" + i);
        }

        Map<String, Assignment> next = assign(current, biomes, touching);

        assertEquals(15, index(next, "cbc:x"), "no neighbour has 15, though it is the colour most biomes hold");
    }

    // Colours are shared by the whole server, so the biomes of other worlds count, though only one world is given.
    @Test
    void aNewBiomeAvoidsTheColoursOfBiomesOutsideTheGivenSetToo() {
        Map<String, Assignment> current = Map.of("cbc:a", new Assignment(0, 1), "cbc:b", new Assignment(1, 2),
                "cbc:c", new Assignment(2, 3));

        Map<String, Assignment> next = assign(current, Set.of("cbc:d"), Set.of());

        assertEquals(new Assignment(3, 4), next.get("cbc:d"), "the other biomes hold 0, 1 and 2");
    }

    @Test
    void newBiomesGetTheOrdersAfterTheHighestSoFarInIdOrder() {
        Map<String, Assignment> current = Map.of("cbc:m", new Assignment(7, 30), "cbc:n", new Assignment(8, 10));

        Map<String, Assignment> next = assign(current, new LinkedHashSet<>(List.of("cbc:z", "cbc:a", "cbc:n", "cbc:q")),
                Set.of());

        assertEquals(31, next.get("cbc:a").order());
        assertEquals(32, next.get("cbc:q").order());
        assertEquals(33, next.get("cbc:z").order());
        assertEquals(new Assignment(8, 10), next.get("cbc:n"), "n is not new");
    }

    @Test
    void theFirstBiomeEverHasOrderOne() {
        assertEquals(1, assign(Map.of(), Set.of("cbc:a"), Set.of()).get("cbc:a").order());
    }

    @Test
    void aBiomeKeepsItsColourAndOrderWhenOthersJoin() {
        Map<String, Assignment> current = Map.of("cbc:a", new Assignment(5, 3));

        Map<String, Assignment> next = assign(current, Set.of("cbc:a", "cbc:b"), touches("cbc:a", "cbc:b"));

        assertEquals(new Assignment(5, 3), next.get("cbc:a"), "not moved to the free colour 0");
        assertEquals(new Assignment(0, 4), next.get("cbc:b"));
    }

    @Test
    void biomesOutsideTheGivenSetKeepTheirColours() {
        // another world may use them, and this world's biomes are all that is given
        Map<String, Assignment> current = Map.of("cbc:a", new Assignment(3, 1), "cbc:b", new Assignment(4, 2));

        Map<String, Assignment> next = assign(current, Set.of("cbc:c"), Set.of());

        assertEquals(Map.of("cbc:a", new Assignment(3, 1), "cbc:b", new Assignment(4, 2),
                "cbc:c", new Assignment(0, 3)), next);
    }

    @Test
    void aTouchNamingABiomeWithoutAColourIsIgnored() {
        Map<String, Assignment> next = assign(Map.of(), Set.of("cbc:a"), touches("cbc:a", "cbc:ghost"));

        assertEquals(Set.of("cbc:a"), next.keySet());
        assertEquals(0, index(next, "cbc:a"));
    }

    @Test
    void whenTwoBiomesOfOneColourComeToTouchTheLaterOneChanges() {
        Map<String, Assignment> current = Map.of("cbc:a", new Assignment(0, 1), "cbc:b", new Assignment(0, 2));

        Map<String, Assignment> next = assign(current, Set.of("cbc:a", "cbc:b"), touches("cbc:a", "cbc:b"));

        assertEquals(new Assignment(0, 1), next.get("cbc:a"));
        assertEquals(new Assignment(1, 2), next.get("cbc:b"), "it keeps its place in the order");
    }

    @Test
    void theLaterOneIsTheOneWithTheHigherOrderWhateverTheIds() {
        Map<String, Assignment> current = Map.of("cbc:a", new Assignment(0, 2), "cbc:b", new Assignment(0, 1));

        Map<String, Assignment> next = assign(current, Set.of("cbc:a", "cbc:b"), touches("cbc:a", "cbc:b"));

        assertEquals(new Assignment(1, 2), next.get("cbc:a"));
        assertEquals(new Assignment(0, 1), next.get("cbc:b"));
    }

    @Test
    void biomesOfTheSameOrderChangeTheHigherId() {
        Map<String, Assignment> current = Map.of("cbc:a", new Assignment(0, 1), "cbc:b", new Assignment(0, 1));

        Map<String, Assignment> next = assign(current, Set.of("cbc:a", "cbc:b"), touches("cbc:a", "cbc:b"));

        assertEquals(0, index(next, "cbc:a"));
        assertEquals(1, index(next, "cbc:b"));
    }

    @Test
    void threeBiomesOfOneColourThatAllTouchGetThreeColours() {
        Map<String, Assignment> current = Map.of("cbc:a", new Assignment(0, 1), "cbc:b", new Assignment(0, 2),
                "cbc:c", new Assignment(0, 3));

        Map<String, Assignment> next = assign(current, Set.of("cbc:a", "cbc:b", "cbc:c"),
                touches("cbc:a", "cbc:b", "cbc:a", "cbc:c", "cbc:b", "cbc:c"));

        assertEquals(0, index(next, "cbc:a"));
        assertEquals(1, index(next, "cbc:b"));
        assertEquals(2, index(next, "cbc:c"));
    }

    // The ids run against the order here: z is the oldest. Changing z, y and x in id order would move x first, then y
    // as well; taking the oldest first moves only y, and x is then free of its clash.
    @Test
    void olderBiomesAreSettledFirstSoAClashMovesAsFewAsPossible() {
        Map<String, Assignment> current = Map.of("cbc:z", new Assignment(0, 1), "cbc:y", new Assignment(0, 2),
                "cbc:x", new Assignment(0, 3));

        Map<String, Assignment> next = assign(current, Set.of("cbc:x", "cbc:y", "cbc:z"),
                touches("cbc:z", "cbc:y", "cbc:y", "cbc:x"));

        assertEquals(new Assignment(0, 1), next.get("cbc:z"));
        assertEquals(new Assignment(1, 2), next.get("cbc:y"));
        assertEquals(new Assignment(0, 3), next.get("cbc:x"), "it clashed only with y, which has moved");
    }

    @Test
    void withEveryColourTakenByNeighboursTheLeastUsedOneIsTaken() {
        // neighbours n00 to n15 have the colours 0 to 15, and n16 has 0 again: 0 is the only colour two have
        Map<String, Assignment> current = new HashMap<>();
        Set<String> biomes = new HashSet<>(Set.of("cbc:x"));
        Set<Touch> touching = new HashSet<>();
        for (int i = 0; i <= 16; i++) {
            String neighbour = String.format("cbc:n%02d", i);
            current.put(neighbour, new Assignment(i % 16, i + 1));
            biomes.add(neighbour);
            touching.add(touch(neighbour, "cbc:x"));
        }

        Map<String, Assignment> next = assign(current, biomes, touching);

        assertEquals(1, index(next, "cbc:x"));
        assertEquals(18, next.get("cbc:x").order());
        current.forEach((biome, assignment) -> assertEquals(assignment, next.get(biome), biome + " stays"));
    }

    // When the neighbours hold every colour alike, the colour that fewer biomes hold goes first, and only then the
    // lowest.
    @Test
    void whenEveryColourIsTakenByNeighboursTheOneFewerBiomesHoldIsTaken() {
        Map<String, Assignment> current = new HashMap<>();
        Set<String> biomes = new HashSet<>(Set.of("cbc:x"));
        Set<Touch> touching = new HashSet<>();
        for (int i = 0; i < 16; i++) { // the neighbours n00 to n15 hold each colour once
            String neighbour = String.format("cbc:n%02d", i);
            current.put(neighbour, new Assignment(i, i + 1));
            biomes.add(neighbour);
            touching.add(touch(neighbour, "cbc:x"));
        }
        for (int i = 0; i < 4; i++) { // four biomes that x does not touch hold the colours 0 to 3 as well
            current.put("cbc:far" + i, new Assignment(i, 17 + i));
            biomes.add("cbc:far" + i);
        }

        Map<String, Assignment> next = assign(current, biomes, touching);

        assertEquals(4, index(next, "cbc:x"), "every neighbour colour is as bad as the next; 0 to 3 are held twice");
    }

    @Test
    void aBiomeColouredAgainTakesAColourFewBiomesHold() {
        Map<String, Assignment> current = Map.of("cbc:a", new Assignment(0, 1), "cbc:b", new Assignment(0, 2),
                "cbc:d", new Assignment(1, 3));

        Map<String, Assignment> next = assign(current, Set.of("cbc:a", "cbc:b", "cbc:d"), touches("cbc:a", "cbc:b"));

        assertEquals(new Assignment(2, 2), next.get("cbc:b"), "not 1, which d holds, though d does not touch b");
    }

    // The biome that is coloured again is one of those that hold its old colour. It must not count there, or it would
    // always be pushed off the colour it has.
    @Test
    void aBiomeColouredAgainDoesNotCountItself() {
        Map<String, Assignment> current = new HashMap<>();
        Set<String> biomes = new HashSet<>(Set.of("cbc:x"));
        Set<Touch> touching = new HashSet<>();
        for (int i = 0; i < 16; i++) { // the neighbours n00 to n15 hold each colour once
            String neighbour = String.format("cbc:n%02d", i);
            current.put(neighbour, new Assignment(i, i + 1));
            biomes.add(neighbour);
            touching.add(touch(neighbour, "cbc:x"));
        }
        current.put("cbc:x", new Assignment(3, 17)); // the colour of n03, which is older
        for (int colour = 0; colour < 16; colour++) { // all colours but 3 and 7 are held by one more biome
            if (colour != 3 && colour != 7) {
                current.put("cbc:far" + colour, new Assignment(colour, 18 + colour));
                biomes.add("cbc:far" + colour);
            }
        }

        Map<String, Assignment> next = assign(current, biomes, touching);

        assertEquals(new Assignment(3, 17), next.get("cbc:x"), "3 and 7 are held once, and 3 is the lower");
    }

    @Test
    void withMoreTouchingBiomesThanColoursTheColoursAreSharedEvenly() {
        Set<Touch> touching = new HashSet<>();
        List<String> ids = crowd(40, touching);

        Map<String, Assignment> colours = assign(Map.of(), new HashSet<>(ids), touching);

        int[] used = uses(colours);
        for (int colour = 0; colour < used.length; colour++) {
            int times = used[colour];
            assertTrue(times == 2 || times == 3, "colour " + colour + " is used " + times + " times");
        }
        for (int i = 0; i < PALETTE.length; i++) {
            assertEquals(i, index(colours, ids.get(i)), "the first sixteen take one colour each, in id order");
        }
        assertEquals(colours, assign(colours, new HashSet<>(ids), touching), "nothing moves when worked out again");
    }

    @Test
    void thirtyBiomesThatDoNotTouchUseEveryColourBeforeAnyIsUsedAThirdTime() {
        Set<String> biomes = new TreeSet<>();
        for (int i = 0; i < 30; i++) {
            biomes.add(String.format("cbc:b%02d", i));
        }

        int[] used = uses(assign(Map.of(), biomes, Set.of()));

        for (int colour = 0; colour < used.length; colour++) {
            assertEquals(colour < 14 ? 2 : 1, used[colour], "uses of colour " + colour);
        }
    }

    // In a chain each biome touches the one before. The colours still go round the whole palette before they repeat.
    @Test
    void aChainOfTwentyFourBiomesUsesEveryColourBeforeAnyIsUsedAThirdTime() {
        Set<Touch> touching = new HashSet<>();
        List<String> ids = new ArrayList<>();
        for (int i = 0; i < 24; i++) {
            String id = String.format("cbc:b%02d", i);
            if (i > 0) {
                touching.add(touch(ids.get(i - 1), id));
            }
            ids.add(id);
        }

        Map<String, Assignment> colours = assign(Map.of(), new HashSet<>(ids), touching);

        int[] used = uses(colours);
        for (int colour = 0; colour < used.length; colour++) {
            assertEquals(colour < 8 ? 2 : 1, used[colour], "uses of colour " + colour);
        }
        for (Touch touch : touching) {
            assertNotEquals(index(colours, touch.a()), index(colours, touch.b()), touch.toString());
        }
    }

    @Test
    void assignLeavesWhatItIsGivenAloneAndGivesAResultThatCannotBeChanged() {
        Map<String, Assignment> current = new HashMap<>(
                Map.of("cbc:a", new Assignment(0, 1), "cbc:b", new Assignment(0, 2)));
        Set<String> biomes = new HashSet<>(Set.of("cbc:a", "cbc:b", "cbc:c"));
        Set<Touch> touching = touches("cbc:a", "cbc:b", "cbc:b", "cbc:c");
        Map<String, Assignment> currentBefore = new HashMap<>(current);
        Set<String> biomesBefore = new HashSet<>(biomes);
        Set<Touch> touchingBefore = new HashSet<>(touching);

        Map<String, Assignment> next = assign(current, biomes, touching);

        assertEquals(currentBefore, current);
        assertEquals(biomesBefore, biomes);
        assertEquals(touchingBefore, touching);
        assertEquals(next, assign(current, biomes, touching), "the same inputs give the same result");
        assertThrows(UnsupportedOperationException.class, () -> next.put("cbc:z", new Assignment(0, 9)));
    }

    @Test
    void newBiomesAreColouredInIdOrderWhateverTheOrderOfTheSets() {
        List<String> ids = List.of("cbc:a", "cbc:b", "cbc:c", "cbc:d");
        Set<Touch> all = new HashSet<>();
        for (int i = 0; i < ids.size(); i++) {
            for (int j = i + 1; j < ids.size(); j++) {
                all.add(touch(ids.get(i), ids.get(j)));
            }
        }
        Random random = new Random(7);

        for (int round = 0; round < 10; round++) {
            List<String> shuffledIds = new ArrayList<>(ids);
            Collections.shuffle(shuffledIds, random);
            List<Touch> shuffledTouches = new ArrayList<>(all);
            Collections.shuffle(shuffledTouches, random);

            Map<String, Assignment> colours = assign(Map.of(), new LinkedHashSet<>(shuffledIds),
                    new LinkedHashSet<>(shuffledTouches));

            assertEquals(Map.of("cbc:a", new Assignment(0, 1), "cbc:b", new Assignment(1, 2),
                    "cbc:c", new Assignment(2, 3), "cbc:d", new Assignment(3, 4)), colours, "round " + round);
        }
    }

    // ---- many worlds, made at random ----

    /**
     * Biomes cbc:b00 and up, touching at random but at most {@code maxTouches} each, and about half of them coloured
     * already, at random, so that biomes which touch may share a colour, as colours from older files can. The orders
     * of those have gaps.
     */
    private static final class World {
        final Map<String, Assignment> current = new HashMap<>();
        final Set<String> biomes = new HashSet<>();
        final Set<Touch> touching = new HashSet<>();
        private final Map<String, Set<String>> neighbours = new HashMap<>();

        World(Random random, int count, int attempts, int maxTouches) {
            List<String> ids = new ArrayList<>();
            for (int i = 0; i < count; i++) {
                ids.add(String.format("cbc:b%02d", i));
            }
            List<String> coloured = new ArrayList<>();
            for (String id : ids) {
                if (random.nextBoolean()) {
                    coloured.add(id);
                }
            }
            Collections.shuffle(coloured, random);
            for (int i = 0; i < coloured.size(); i++) {
                current.put(coloured.get(i), new Assignment(random.nextInt(PALETTE.length), 10 * (i + 1)));
            }
            for (String id : ids) {
                // a coloured biome may be left out of the set: it is then another world's
                if (!current.containsKey(id) || random.nextBoolean()) {
                    biomes.add(id);
                }
            }
            for (int i = 0; i < attempts; i++) {
                String one = ids.get(random.nextInt(count));
                String two = ids.get(random.nextInt(count));
                if (!one.equals(two) && degree(one) < maxTouches && degree(two) < maxTouches
                        && touching.add(touch(one, two))) {
                    neighbours.computeIfAbsent(one, id -> new HashSet<>()).add(two);
                    neighbours.computeIfAbsent(two, id -> new HashSet<>()).add(one);
                }
            }
        }

        private int degree(String id) {
            return neighbours.getOrDefault(id, Set.of()).size();
        }

        Map<String, Assignment> colours() {
            return assign(current, biomes, touching);
        }

        /** The same inputs, with each collection filled in another order. */
        Map<String, Assignment> coloursFromShuffledSets(Random random) {
            Map<String, Assignment> shuffledCurrent = new LinkedHashMap<>();
            for (String id : shuffled(current.keySet(), random)) {
                shuffledCurrent.put(id, current.get(id));
            }
            return assign(shuffledCurrent, shuffled(biomes, random), shuffled(touching, random));
        }

        private static <T> Set<T> shuffled(Collection<T> items, Random random) {
            List<T> list = new ArrayList<>(items);
            Collections.shuffle(list, random);
            return new LinkedHashSet<>(list);
        }

        /** Whether the biome, as it was coloured, shared its colour with a touching biome that came before it. */
        boolean clashedWithAnOlderOne(String id) {
            Assignment mine = current.get(id);
            for (String other : neighbours.getOrDefault(id, Set.of())) {
                Assignment theirs = current.get(other);
                if (theirs != null && theirs.index() == mine.index() && theirs.order() < mine.order()) {
                    return true;
                }
            }
            return false;
        }
    }

    // With at most 15 touches each there is always a free colour, so nothing may clash in the end.
    @Test
    void biomesThatTouchEndWithDifferentColoursAndOnlyAClashingOneMoved() {
        Random random = new Random(20261028);
        for (int round = 0; round < 300; round++) {
            int number = round;
            World world = new World(random, 2 + random.nextInt(24), random.nextInt(200), 15);

            Map<String, Assignment> next = world.colours();

            Set<String> everyone = new HashSet<>(world.current.keySet());
            everyone.addAll(world.biomes);
            assertEquals(everyone, next.keySet(), () -> "round " + number + ": the biomes coloured");
            for (Touch touch : world.touching) {
                assertNotEquals(index(next, touch.a()), index(next, touch.b()), () -> "round " + number + ": " + touch);
            }
            int highest = world.current.values().stream().mapToInt(Assignment::order).max().orElse(0);
            Set<String> added = new TreeSet<>(world.biomes);
            added.removeAll(world.current.keySet());
            for (String biome : added) {
                assertEquals(++highest, next.get(biome).order(), () -> "round " + number + ": order of " + biome);
            }
            world.current.forEach((biome, before) -> {
                assertEquals(before.order(), next.get(biome).order(), () -> "round " + number + ": order of " + biome);
                if (next.get(biome).index() != before.index()) {
                    assertTrue(world.clashedWithAnOlderOne(biome), () -> "round " + number + ": " + biome
                            + " moved without a clash");
                }
            });
            assertEquals(next, world.coloursFromShuffledSets(random), () -> "round " + number + ": set order");
            assertEquals(next, assign(next, world.biomes, world.touching), () -> "round " + number + ": again");
        }
    }

    // Where biomes touch far more than there are colours, some clashes stay. Even then the result must be the same for
    // the same sets, and the passes must come to an end.
    @Test
    void crowdedWorldsAreColouredTheSameWhateverTheOrderOfTheSets() {
        Random random = new Random(1028);
        for (int round = 0; round < 100; round++) {
            int number = round;
            World world = new World(random, 20 + random.nextInt(30), 400 + random.nextInt(600), Integer.MAX_VALUE);

            Map<String, Assignment> next = world.colours();

            assertEquals(next, world.coloursFromShuffledSets(random), () -> "round " + number);
            world.current.forEach((biome, before) ->
                    assertEquals(before.order(), next.get(biome).order(), () -> "round " + number + ": " + biome));
        }
    }

    // Whatever touches what, a biome takes a colour that nobody holds while there is one: such a colour is held by no
    // neighbour either. So the first sixteen biomes get sixteen different colours, and no colour is left out after.
    @Test
    void everyBiomeGetsAColourOfItsOwnWhileThePaletteLastsWhateverTouchesWhat() {
        Random random = new Random(16);
        for (int round = 0; round < 300; round++) {
            int number = round;
            int count = 1 + random.nextInt(32);
            double density = random.nextDouble();
            List<String> ids = new ArrayList<>();
            for (int i = 0; i < count; i++) {
                ids.add(String.format("cbc:b%02d", i));
            }
            Set<Touch> touching = new HashSet<>();
            for (int i = 0; i < count; i++) {
                for (int j = i + 1; j < count; j++) {
                    if (random.nextDouble() < density) {
                        touching.add(touch(ids.get(i), ids.get(j)));
                    }
                }
            }

            Map<String, Assignment> colours = assign(Map.of(), new HashSet<>(ids), touching);

            long different = colours.values().stream().mapToInt(Assignment::index).distinct().count();
            assertEquals(Math.min(count, PALETTE.length), different,
                    () -> "round " + number + ": " + count + " biomes");
        }
    }

    // ---- the palette and Assignment ----

    // A colour is saved as its place in the palette, so the palette is part of the file format.
    @Test
    void thePaletteIsTheSixteenColoursOfTheFiles() {
        assertArrayEquals(new int[] {0xe6194b, 0x3cb44b, 0xffe119, 0x4363d8, 0xf58231, 0x911eb4, 0x46f0f0, 0xf032e6,
                0xbcf60c, 0xfabebe, 0x008080, 0xe6beff, 0x9a6324, 0xfffac8, 0x800000, 0xaaffc3}, PALETTE);
        assertEquals(PALETTE.length, Arrays.stream(PALETTE).distinct().count());
    }

    @Test
    void anAssignmentNeedsAColourOfThePalette() {
        assertEquals(15, new Assignment(15, 1).index());
        assertThrows(IllegalArgumentException.class, () -> new Assignment(16, 1));
        assertThrows(IllegalArgumentException.class, () -> new Assignment(-1, 1));
    }

    // ---- the instance and its file ----

    /** Collects what the class logs while it is open, and keeps that off the console. */
    private static final class Warnings implements AutoCloseable {
        final List<String> messages = new ArrayList<>();
        private final Logger logger = Logger.getLogger(BiomeColours.class.getName());
        private final boolean parentHandlers = logger.getUseParentHandlers();
        private final Handler handler = new Handler() {
            @Override
            public void publish(LogRecord record) {
                messages.add(record.getMessage());
            }

            @Override
            public void flush() {
            }

            @Override
            public void close() {
            }
        };

        Warnings() {
            logger.setUseParentHandlers(false);
            logger.addHandler(handler);
        }

        @Override
        public void close() {
            logger.removeHandler(handler);
            logger.setUseParentHandlers(parentHandlers);
        }
    }

    @Test
    void aBiomeKeepsItsColourAcrossRestarts(@TempDir Path dir) {
        Path file = dir.resolve("colours.txt");
        BiomeColours colours = new BiomeColours(file);
        colours.update(assign(colours.current(), Set.of("cbc:a", "cbc:b", "cbc:c"),
                touches("cbc:a", "cbc:b", "cbc:b", "cbc:c")));

        BiomeColours restarted = new BiomeColours(file);

        assertEquals(PALETTE[0], restarted.rgb("cbc:a"));
        assertEquals(PALETTE[1], restarted.rgb("cbc:b"), "not the colour of a biome nobody knows");
        assertEquals(PALETTE[2], restarted.rgb("cbc:c"));
        assertEquals(colours.current(), restarted.current(), "orders too");
    }

    @Test
    void nothingChangesWhenNothingConflicts(@TempDir Path dir) {
        BiomeColours colours = new BiomeColours(dir.resolve("colours.txt"));
        Set<String> biomes = Set.of("cbc:a", "cbc:b");
        Set<Touch> touching = touches("cbc:a", "cbc:b");
        assertTrue(colours.update(assign(colours.current(), biomes, touching)));

        assertFalse(colours.update(colours.current()));
        assertFalse(colours.update(assign(colours.current(), biomes, touching)), "worked out again, it is the same");
    }

    @Test
    void anUpdateSaysWhetherAnythingChanged(@TempDir Path dir) {
        BiomeColours colours = new BiomeColours(dir.resolve("colours.txt"));

        assertFalse(colours.update(Map.of()));
        assertTrue(colours.update(Map.of("cbc:a", new Assignment(3, 1))));
        assertFalse(colours.update(Map.of("cbc:a", new Assignment(3, 1))));
        assertTrue(colours.update(Map.of("cbc:a", new Assignment(4, 1))), "another colour");
        assertTrue(colours.update(Map.of("cbc:a", new Assignment(4, 2))), "another order");
        assertEquals(Map.of("cbc:a", new Assignment(4, 2)), colours.current());
    }

    @Test
    void updatingChangesTheColoursAtOnce(@TempDir Path dir) {
        BiomeColours colours = new BiomeColours(dir.resolve("colours.txt"));
        Map<String, Assignment> before = colours.current();

        colours.update(Map.of("cbc:a", new Assignment(5, 1)));

        assertEquals(PALETTE[5], colours.rgb("cbc:a"));
        assertTrue(before.isEmpty(), "what current() gave earlier does not change under the reader");
    }

    // A biome that a result leaves out keeps its colour. (That is all the protection there is: a result must have been
    // worked out from the colours held when it is applied, with assign and update going one after the other. One worked
    // out from an older copy writes that copy's colours back over newer ones.)
    @Test
    void anUpdateDoesNotForgetColoursItIsNotGiven(@TempDir Path dir) {
        BiomeColours colours = new BiomeColours(dir.resolve("colours.txt"));
        colours.update(Map.of("cbc:a", new Assignment(3, 1)));

        assertTrue(colours.update(Map.of("cbc:b", new Assignment(4, 2))));

        assertEquals(PALETTE[3], colours.rgb("cbc:a"));
        assertEquals(PALETTE[4], colours.rgb("cbc:b"));
        assertFalse(colours.update(Map.of()));
        assertEquals(2, colours.current().size());
    }

    @Test
    void aBiomeWithoutAColourShowsTheFirstOneOfThePalette(@TempDir Path dir) {
        BiomeColours colours = new BiomeColours(dir.resolve("colours.txt"));
        colours.update(Map.of("cbc:a", new Assignment(5, 1)));

        assertEquals(PALETTE[0], colours.rgb("cbc:nobody"));
        assertFalse(colours.current().containsKey("cbc:nobody"), "asking gives it no colour");
    }

    @Test
    void theFileHoldsOneLinePerBiomeInIdOrder(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("colours.txt");
        BiomeColours colours = new BiomeColours(file);
        Map<String, Assignment> next = new LinkedHashMap<>();
        next.put("cbc:b", new Assignment(15, 2));
        next.put("cbc:a", new Assignment(0, 1));

        colours.update(next);

        assertEquals("cbc:a;0;1\ncbc:b;15;2\n", Files.readString(file));
    }

    @Test
    void nothingIsWrittenWhenNothingChanged(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("colours.txt");
        BiomeColours colours = new BiomeColours(file);
        colours.update(Map.of("cbc:a", new Assignment(3, 1)));
        Files.delete(file);

        assertFalse(colours.update(colours.current()));
        assertFalse(colours.update(Map.of("cbc:a", new Assignment(3, 1))));

        assertFalse(Files.exists(file), "no change, no save");
    }

    @Test
    void savingMakesTheFolderAndLeavesNoTemporaryFile(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("customBiomes").resolve("colours.txt");
        BiomeColours colours = new BiomeColours(file);

        colours.update(Map.of("cbc:a", new Assignment(3, 1)));
        colours.update(Map.of("cbc:a", new Assignment(4, 1)));

        assertEquals("cbc:a;4;1\n", Files.readString(file), "the second save replaced the first");
        assertFalse(Files.exists(dir.resolve("customBiomes").resolve("colours.txt.tmp")));
    }

    // A failed save must not stop the map from being drawn in the right colours. The file is behind until a save is
    // tried again, which the next update does.
    @Test
    void aSaveThatFailsKeepsTheColoursAndIsTriedAgainAtTheNextUpdate(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("colours.txt");
        BiomeColours colours = new BiomeColours(file);
        Files.createDirectories(file.resolve("inside")); // a folder where the file belongs, so the move cannot work

        try (Warnings warnings = new Warnings()) {
            assertTrue(colours.update(Map.of("cbc:a", new Assignment(3, 1))), "the colours changed, saved or not");

            assertEquals(PALETTE[3], colours.rgb("cbc:a"));
            assertFalse(Files.exists(dir.resolve("colours.txt.tmp")), "no half-written file is left");
            assertEquals(1, warnings.messages.size(), () -> "the failure is said once: " + warnings.messages);
            assertTrue(warnings.messages.get(0).contains("colours.txt"), "and names the file");
            Files.delete(file.resolve("inside"));
            Files.delete(file);

            assertFalse(colours.update(colours.current()), "nothing changed since");

            assertEquals("cbc:a;3;1\n", Files.readString(file));
            assertEquals(1, warnings.messages.size(), "the second try worked, so it said nothing more");
        }
    }

    // A missing file is how every first start begins, so it is no news.
    @Test
    void aMissingFileMeansNoColoursYet(@TempDir Path dir) {
        Path file = dir.resolve("colours.txt");

        try (Warnings warnings = new Warnings()) {
            BiomeColours colours = new BiomeColours(file);

            assertTrue(colours.current().isEmpty());
            assertFalse(Files.exists(file), "reading makes no file");
            assertEquals(List.of(), warnings.messages);
        }
    }

    @Test
    void aFileThatCannotBeReadMeansNoColoursYetAndIsSaid(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("colours.txt");
        Files.createDirectories(file); // a folder where the file belongs

        try (Warnings warnings = new Warnings()) {
            assertTrue(new BiomeColours(file).current().isEmpty());

            assertEquals(1, warnings.messages.size(), () -> "said: " + warnings.messages);
            assertTrue(warnings.messages.get(0).contains("colours.txt"), "and names the file");
        }
    }

    // The format is the contract with the files already on disk, so one is written here by hand.
    @Test
    void aFileWrittenByHandInTheDocumentedFormatLoads(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("colours.txt");
        Files.writeString(file, "cbc:forest;3;7\r\ncbc:sky;15;2\r\n");

        try (Warnings warnings = new Warnings()) {
            BiomeColours colours = new BiomeColours(file);

            assertEquals(Map.of("cbc:forest", new Assignment(3, 7), "cbc:sky", new Assignment(15, 2)),
                    colours.current());
            assertEquals(PALETTE[3], colours.rgb("cbc:forest"));
            assertEquals(PALETTE[15], colours.rgb("cbc:sky"));
            assertEquals(List.of(), warnings.messages);
        }
    }

    // Every line that is not a line of the format is skipped and said, with its text. An empty line is no news.
    @Test
    void aBadLineIsSkippedAndSaid(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("colours.txt");
        List<String> bad = List.of("cbc:short;2", "cbc:long;2;3;4", "cbc:trailing;2;3;", "cbc:text;two;3",
                "cbc:late;2;three", "cbc:high;16;3", "cbc:low;-1;3", ";2;3", "just some words");
        List<String> lines = new ArrayList<>(bad);
        lines.add(0, "cbc:good;2;1");
        lines.add(4, "");
        lines.add("cbc:also;5;2");
        Files.writeString(file, String.join("\n", lines) + "\n");

        try (Warnings warnings = new Warnings()) {
            BiomeColours colours = new BiomeColours(file);

            assertEquals(Map.of("cbc:good", new Assignment(2, 1), "cbc:also", new Assignment(5, 2)),
                    colours.current());
            assertEquals(bad.size(), warnings.messages.size(), () -> "said: " + warnings.messages);
            for (String line : bad) {
                assertTrue(warnings.messages.stream().anyMatch(message -> message.endsWith(": " + line)),
                        () -> "no word about " + line + " in " + warnings.messages);
            }
        }
    }

    @Test
    void aDamagedByteSpoilsOnlyItsOwnLine(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("colours.txt");
        byte[] before = "cbc:before;1;1\n".getBytes(StandardCharsets.UTF_8);
        byte[] damaged = {'c', 'b', 'c', ':', (byte) 0xC3, (byte) 0x28, ';', 'x', '\n'}; // not UTF-8
        byte[] after = "cbc:after;2;2\n".getBytes(StandardCharsets.UTF_8);
        Files.write(file, concat(before, damaged, after));

        try (Warnings warnings = new Warnings()) {
            BiomeColours colours = new BiomeColours(file);

            assertEquals(Map.of("cbc:before", new Assignment(1, 1), "cbc:after", new Assignment(2, 2)),
                    colours.current());
            assertEquals(1, warnings.messages.size(), () -> "said: " + warnings.messages);
        }
    }

    private static byte[] concat(byte[]... parts) {
        int length = 0;
        for (byte[] part : parts) {
            length += part.length;
        }
        byte[] all = new byte[length];
        int at = 0;
        for (byte[] part : parts) {
            System.arraycopy(part, 0, all, at, part.length);
            at += part.length;
        }
        return all;
    }
}
