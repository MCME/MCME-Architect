package com.mcmiddleearth.architect.mapLayers.customBiomes;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * The automatic map colour of each custom biome. A biome gets one of sixteen {@link #PALETTE colours}, different from
 * those of the biomes it touches where the palette allows, and keeps it from one redraw to the next and across
 * restarts, because the colours are kept in a small file.
 * <p>
 * {@link #assign} decides the colours and touches nothing else, so a background thread can run it on a copy of
 * {@link #current()}, and the main thread then applies the result with {@link #update}. The colours can be read from
 * any thread at any time.
 * <p>
 * The file holds a line {@code biome;index;order} for each biome, in id order. A line that cannot be read is skipped,
 * and a missing file means no colours yet.
 */
public final class BiomeColours {

    /**
     * The colours as {@code 0xRRGGBB}. A colour is saved as its place in this array, so the array must not be
     * changed.
     */
    public static final int[] PALETTE = {0xe6194b, 0x3cb44b, 0xffe119, 0x4363d8, 0xf58231, 0x911eb4, 0x46f0f0,
            0xf032e6, 0xbcf60c, 0xfabebe, 0x008080, 0xe6beff, 0x9a6324, 0xfffac8, 0x800000, 0xaaffc3};

    private static final Logger LOG = Logger.getLogger(BiomeColours.class.getName());

    /**
     * One biome's colour, and its place in the order the biomes were coloured.
     *
     * @param index which colour of the {@link #PALETTE}
     * @param order 1 for the first biome ever coloured, and one more for each one after; of two touching biomes of one
     *              colour, the one with the higher order is the one that changes
     */
    public record Assignment(int index, int order) {

        public Assignment {
            if (index < 0 || index >= PALETTE.length) {
                throw new IllegalArgumentException("A colour is a number from 0 to " + (PALETTE.length - 1)
                        + ", not " + index);
            }
        }
    }

    private final Path file;
    /** Unmodifiable, and replaced as a whole, so that any thread can read it. */
    private volatile Map<String, Assignment> colours;
    /** Whether the file is behind {@link #colours} because the last save failed. Guarded by {@code this}. */
    private boolean unsaved;

    /** Reads the colours kept in {@code file}. */
    public BiomeColours(Path file) {
        this.file = file;
        this.colours = Collections.unmodifiableMap(load(file));
    }

    /**
     * Works out the colours to have next. Nothing is dropped, and nothing changes but what a rule says:
     * <ul>
     *     <li>A biome in {@code current} keeps its colour and its order, even when it is not in {@code biomes}:
     *     another world may use it.</li>
     *     <li>A biome in {@code biomes} that {@code current} lacks is coloured in id order. It takes the lowest colour
     *     that none of the biomes it touches has; when they have every colour, the colour the fewest have, the lowest
     *     on a tie. Its order is one more than the highest so far.</li>
     *     <li>Where two touching biomes have the same colour, the one with the higher order (on equal orders, the
     *     higher id) is coloured again by the rule above, and keeps its order. The biomes are taken oldest first, so a
     *     clash moves as few as it can. This repeats until nothing changes, at most once for each biome.</li>
     * </ul>
     * A biome that touches biomes of every colour can still share one. The result depends only on what the arguments
     * hold, not on the order of their elements.
     *
     * @param current  the colours so far, which is not changed
     * @param biomes   the biomes that need a colour
     * @param touching the biomes that touch, in one column or in columns side by side; a touch that names a biome
     *                 with no colour, which is not in {@code biomes} either, is ignored
     * @return the next colours, in id order, which cannot be changed
     */
    public static Map<String, Assignment> assign(Map<String, Assignment> current, Set<String> biomes,
                                                 Set<PaintRegions.Touch> touching) {
        Map<String, Assignment> next = new TreeMap<>(current);
        Map<String, List<String>> neighbours = neighbours(touching);
        int highest = 0;
        for (Assignment assignment : next.values()) {
            highest = Math.max(highest, assignment.order());
        }
        for (String biome : new TreeSet<>(biomes)) {
            if (!next.containsKey(biome)) {
                next.put(biome, new Assignment(leastUsed(biome, next, neighbours), ++highest));
            }
        }
        recolourClashes(next, neighbours);
        return Collections.unmodifiableMap(next);
    }

    private static Map<String, List<String>> neighbours(Set<PaintRegions.Touch> touching) {
        Map<String, List<String>> neighbours = new HashMap<>();
        for (PaintRegions.Touch touch : touching) {
            neighbours.computeIfAbsent(touch.a(), biome -> new ArrayList<>()).add(touch.b());
            neighbours.computeIfAbsent(touch.b(), biome -> new ArrayList<>()).add(touch.a());
        }
        return neighbours;
    }

    /**
     * The colour a biome takes among its neighbours' colours: the lowest that none of them has or, when they have every
     * colour, the lowest that the fewest have. Both come to the lowest colour with the smallest count.
     */
    private static int leastUsed(String biome, Map<String, Assignment> colours,
                                 Map<String, List<String>> neighbours) {
        int[] taken = new int[PALETTE.length];
        for (String neighbour : neighbours.getOrDefault(biome, List.of())) {
            Assignment assignment = colours.get(neighbour);
            if (assignment != null) {
                taken[assignment.index()]++;
            }
        }
        int best = 0;
        for (int index = 1; index < taken.length; index++) {
            if (taken[index] < taken[best]) {
                best = index;
            }
        }
        return best;
    }

    /**
     * Colours again each biome that has the colour of an older neighbour, oldest first, and goes over them again until
     * nothing changes, but not more often than there are biomes.
     */
    private static void recolourClashes(Map<String, Assignment> colours, Map<String, List<String>> neighbours) {
        List<String> oldestFirst = new ArrayList<>(colours.keySet());
        oldestFirst.sort(Comparator.comparingInt((String biome) -> colours.get(biome).order())
                .thenComparing(Comparator.naturalOrder()));
        for (int pass = 0; pass < oldestFirst.size(); pass++) {
            boolean changed = false;
            for (String biome : oldestFirst) {
                Assignment mine = colours.get(biome);
                if (clashesWithAnOlderOne(biome, mine, colours, neighbours)) {
                    int index = leastUsed(biome, colours, neighbours);
                    if (index != mine.index()) {
                        colours.put(biome, new Assignment(index, mine.order()));
                        changed = true;
                    }
                }
            }
            if (!changed) {
                return;
            }
        }
    }

    private static boolean clashesWithAnOlderOne(String biome, Assignment mine, Map<String, Assignment> colours,
                                                 Map<String, List<String>> neighbours) {
        for (String other : neighbours.getOrDefault(biome, List.of())) {
            Assignment theirs = colours.get(other);
            if (theirs != null && theirs.index() == mine.index() && cameBefore(other, theirs, biome, mine)) {
                return true;
            }
        }
        return false;
    }

    /** Whether {@code other} comes before {@code biome}: by a lower order, or by a lower id on equal orders. */
    private static boolean cameBefore(String other, Assignment theirs, String biome, Assignment mine) {
        return theirs.order() != mine.order() ? theirs.order() < mine.order() : other.compareTo(biome) < 0;
    }

    /** The colours now: a snapshot that cannot be changed and does not follow later updates. */
    public Map<String, Assignment> current() {
        return colours;
    }

    /**
     * Takes the colours in {@code next} over the ones held, saves them when that changed anything, and says whether
     * it did. A biome that {@code next} leaves out keeps its colour, so a result worked out from an older copy of
     * {@link #current()} cannot make the colours of another result forgotten. When a save fails, the colours are held
     * anyway, and the next update saves them.
     */
    public synchronized boolean update(Map<String, Assignment> next) {
        Map<String, Assignment> merged = new TreeMap<>(colours);
        merged.putAll(next);
        boolean changed = !merged.equals(colours);
        if (changed) {
            colours = Collections.unmodifiableMap(merged);
            unsaved = true;
        }
        if (unsaved) {
            save();
        }
        return changed;
    }

    /**
     * The colour of the biome as {@code 0xRRGGBB}. A biome that has no colour yet has {@code PALETTE[0]}, which is
     * also the colour the first biome gets.
     */
    public int rgb(String biome) {
        Assignment assignment = colours.get(biome);
        return PALETTE[assignment == null ? 0 : assignment.index()];
    }

    private static Map<String, Assignment> load(Path file) {
        Map<String, Assignment> loaded = new TreeMap<>();
        String text;
        try {
            // decoded leniently: a damaged byte spoils its own line, which is skipped, and not the whole file
            text = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
        } catch (NoSuchFileException e) {
            return loaded;
        } catch (IOException e) {
            LOG.log(Level.WARNING, "Custom biomes layer: could not read " + file + ", so its biomes get new colours",
                    e);
            return loaded;
        }
        for (String line : text.lines().toList()) {
            if (line.isBlank()) {
                continue;
            }
            String[] parts = line.split(";", -1);
            try {
                if (parts.length != 3 || parts[0].isEmpty()) {
                    throw new IllegalArgumentException("not biome;index;order");
                }
                loaded.put(parts[0], new Assignment(Integer.parseInt(parts[1]), Integer.parseInt(parts[2])));
            } catch (IllegalArgumentException e) {
                LOG.warning("Custom biomes layer: skipping an unreadable line in " + file.getFileName() + ": " + line);
            }
        }
        return loaded;
    }

    private void save() {
        try {
            write();
            unsaved = false;
        } catch (IOException e) {
            LOG.log(Level.WARNING, "Custom biomes layer: could not save " + file + ", it is tried again at the next "
                    + "update", e);
        }
    }

    /**
     * Writes a temporary file, then moves it over the old one in one step, so a crash mid-write keeps the old file.
     * The file's folder is made when it is missing.
     */
    private void write() throws IOException {
        StringBuilder text = new StringBuilder();
        for (Map.Entry<String, Assignment> entry : colours.entrySet()) {
            text.append(entry.getKey()).append(';').append(entry.getValue().index()).append(';')
                    .append(entry.getValue().order()).append('\n');
        }
        Path temp = file.resolveSibling(file.getFileName() + ".tmp");
        Path folder = file.toAbsolutePath().getParent();
        if (folder != null) {
            Files.createDirectories(folder);
        }
        try {
            Files.writeString(temp, text);
            try {
                Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException | RuntimeException e) {
            // a full disk would otherwise keep the half-written file as well
            try {
                Files.deleteIfExists(temp);
            } catch (IOException cleanup) {
                e.addSuppressed(cleanup);
            }
            throw e;
        }
    }
}
