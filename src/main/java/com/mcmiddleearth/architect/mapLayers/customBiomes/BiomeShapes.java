package com.mcmiddleearth.architect.mapLayers.customBiomes;

import com.mcmiddleearth.architect.mapLayers.MapLayerConfig;
import com.mcmiddleearth.architect.mapLayers.MapShape;
import com.mcmiddleearth.architect.mapLayers.customBiomes.BiomeColours.Assignment;
import com.mcmiddleearth.architect.mapLayers.customBiomes.PaintRegions.Geometry;
import com.mcmiddleearth.architect.mapLayers.customBiomes.PaintRegions.Region;
import com.mcmiddleearth.architect.mapLayers.customBiomes.PaintRegions.Touch;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * The shapes of the Custom biomes layer. Each region of a world's record is drawn once for each biome painted in it,
 * in identical areas that share one window, each in its biome's colour. A world whose markers would add more than
 * {@link MarkerSize#TARGET} to every map visit is held back: none of its areas are drawn, and the log says why.
 * <p>
 * A {@link #rebuild} starts on the main thread, which reads the biomes' names and settings: biome tuning may only be
 * asked there. The regions, the colours, the windows and the size are worked out on {@code background}, and the result
 * comes back on {@code mainThread}, where the shapes are kept. Which thread owns what:
 * <ul>
 *     <li><b>The main thread</b> owns the shapes kept for each world, the worlds held back, the facts each world's
 *     latest rebuild read, and the worlds whose windows show a colour that has changed since. It also counts each
 *     world's rebuilds and forgets, in a map that the background thread reads, to skip a rebuild that a newer one or a
 *     forget has replaced.</li>
 *     <li><b>The background thread</b> owns each world's biomes and touches as its newest rebuild found them, and it
 *     alone changes the colours: one rebuild at a time, each starting from the colours the one before left, and over
 *     the biomes and touches of every world at once. That needs {@code background} to be one thread that runs what it
 *     is given in that order, and {@code mainThread} to run what it is given in order too, as the Bukkit scheduler
 *     does.</li>
 *     <li>What one thread hands the other is never changed afterwards, and the colours may be read from any
 *     thread.</li>
 * </ul>
 */
public final class BiomeShapes {

    /** LiveAtlas draws a line opacity of 0 as 1, so a region with seams gets this one: its seams do not show. */
    static final double SEAM_OPACITY = 0.01;

    private static final Facts NO_FACTS = new Facts(null, null);

    /** A biome's name and settings as a rebuild read them; equal when both are. */
    private record Facts(String label, BiomeFacts.Settings settings) {
    }

    /** An area, with the biome it is drawn for, so that it can take that biome's new colour. */
    private record Shown(String biome, MapShape.Area area) {
    }

    /**
     * A world's biomes and touches as its rebuild found them; the colours are worked out over every world's. A forget
     * leaves an empty one. Of two, the one with the higher count stands, whichever came last.
     */
    private record Painted(long generation, Set<String> biomes, Set<Touch> touching) {
    }

    private final BiomeColours colours;
    private final BiomeFacts facts;
    private final Executor background;
    private final Executor mainThread;
    private final Logger logger;
    private final Runnable redrawn;
    private final int borderWidth;
    private final double borderOpacity;
    private final double areaOpacity;

    /** Each world's count of rebuilds and forgets so far: written on the main thread, read on both. */
    private final Map<String, Long> generations = new ConcurrentHashMap<>();
    /** Main thread: the areas drawn, by world name. */
    private final Map<String, List<Shown>> drawn = new TreeMap<>();
    /** Main thread: the worlds held back, with the gzipped bytes their markers would add. */
    private final Map<String, Integer> oversize = new TreeMap<>();
    /** Main thread: the facts each world's latest rebuild read, by biome. */
    private final Map<String, Map<String, Facts>> factsRead = new HashMap<>();
    /** Main thread: the biomes whose facts failed to read at the last ask, each logged once until it reads again. */
    private final Set<String> unreadable = new HashSet<>();
    /**
     * Main thread: the worlds with an area that took a new colour after its window was built. The header of a window
     * has its biome's colour, so such a window still shows the old one until the world is drawn again: the mark goes
     * when a rebuild of the world starts, and again when one finishes, for a colour that changed between the two.
     */
    private final Set<String> behindInColour = new HashSet<>();
    /** Background thread: what each world's newest rebuild found, for the colours. */
    private final Map<String, Painted> painted = new HashMap<>();

    /**
     * @param config     the layer's settings: {@code borderWidth} (2), {@code borderOpacity} (0.6) and
     *                   {@code areaOpacity} (0.3)
     * @param background one thread, which runs what it is given in that order
     * @param mainThread the server's main thread, which runs what it is given in that order
     * @param logger     where a world held back, or drawn again, is reported
     * @param redrawn    run on the main thread whenever the shapes changed
     */
    BiomeShapes(MapLayerConfig.Layer config, BiomeColours colours, BiomeFacts facts, Executor background,
                Executor mainThread, Logger logger, Runnable redrawn) {
        this.colours = colours;
        this.facts = facts;
        this.background = background;
        this.mainThread = mainThread;
        this.logger = logger;
        this.redrawn = redrawn;
        this.borderWidth = config.integer("borderWidth", 2);
        this.borderOpacity = config.number("borderOpacity", 0.6);
        this.areaOpacity = config.number("areaOpacity", 0.3);
    }

    /**
     * Draws the world's record again. The names and settings of its biomes are read now; the rest is done on the
     * background thread, and the world's shapes change once that is done. Main thread only.
     *
     * @param chunks the world's paint by chunk key, such as a record's copy, which must not change afterwards
     */
    void rebuild(String world, Map<Long, ChunkPaint> chunks) {
        Map<String, Facts> read = new HashMap<>();
        for (ChunkPaint paint : chunks.values()) {
            for (String biome : paint.biomes()) {
                if (!read.containsKey(biome)) {
                    read.put(biome, factsOf(biome));
                }
            }
        }
        Map<String, Facts> used = Map.copyOf(read);
        long generation = generations.merge(world, 1L, Long::sum);
        factsRead.put(world, used);
        // drawn again from here on, like a name or settings once they are read: a build that fails, or one that takes
        // longer than a check, delivers no finish to clear the mark, and every check would start another build
        behindInColour.remove(world);
        background.execute(() -> build(world, generation, chunks, used));
    }

    /**
     * Takes the world off the layer: its shapes go at once, a rebuild of it still under way is dropped, and its biomes
     * and touches no longer count for the colours. Main thread only.
     */
    void forget(String world) {
        long generation = generations.merge(world, 1L, Long::sum);
        drawn.remove(world);
        oversize.remove(world);
        factsRead.remove(world);
        behindInColour.remove(world);
        // on the background thread, which runs it after any rebuild of the world it was given before
        background.execute(() -> painted.merge(world, new Painted(generation, Set.of(), Set.of()),
                BiomeShapes::newer));
    }

    /** Every world's shapes, the worlds sorted by name. Main thread only. */
    List<MapShape> shapes() {
        List<MapShape> all = new ArrayList<>();
        for (List<Shown> shown : drawn.values()) {
            for (Shown one : shown) {
                all.add(one.area());
            }
        }
        return Collections.unmodifiableList(all);
    }

    /** The worlds held back for their size, by name, with the gzipped bytes their markers would add. Main thread. */
    Map<String, Integer> oversize() {
        return Collections.unmodifiableMap(new TreeMap<>(oversize));
    }

    /**
     * Whether a biome of a world on the layer now has another name or other settings than its world's latest rebuild
     * read, or an area of a world has taken a new colour since its window was built, so that its windows are behind. A
     * world held back counts too for a name or settings, since its size changes with them. Once a rebuild of a world
     * has started, the colour no longer counts, whether or not that rebuild ends. Main thread only: it reads them.
     */
    boolean factsChanged() {
        if (!behindInColour.isEmpty()) {
            return true;
        }
        Map<String, Facts> now = new HashMap<>();
        for (Map<String, Facts> read : factsRead.values()) {
            for (Map.Entry<String, Facts> entry : read.entrySet()) {
                if (!now.computeIfAbsent(entry.getKey(), this::factsOf).equals(entry.getValue())) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * The biome's name and settings, or none when biome tuning fails to read them with an unchecked exception, such as
     * a datapack folder that cannot be listed: a failure must not stop the drawing of every other biome. It is logged
     * once while it lasts, not at every redraw.
     */
    private Facts factsOf(String biome) {
        try {
            Facts read = new Facts(facts.label(biome), facts.settings(biome));
            unreadable.remove(biome);
            return read;
        } catch (RuntimeException e) {
            if (unreadable.add(biome)) {
                logger.log(Level.WARNING, "Custom biomes layer: biome tuning could not tell the name and settings of "
                        + biome + ", so its window shows its id and no settings.", e);
            }
            return NO_FACTS;
        }
    }

    /** Whether no newer rebuild and no forget of the world came since this rebuild started. Any thread. */
    private boolean latest(String world, long generation) {
        Long latest = generations.get(world);
        return latest != null && latest == generation;
    }

    private static Painted newer(Painted kept, Painted given) {
        return given.generation() > kept.generation() ? given : kept;
    }

    /** The rebuild's work on the background thread: the regions, the colours, the areas and their size. */
    private void build(String world, long generation, Map<Long, ChunkPaint> chunks, Map<String, Facts> facts) {
        if (!latest(world, generation)) {
            return; // replaced already: a newer rebuild draws the world, or a forget took it off
        }
        try {
            Geometry geometry = PaintRegions.of(chunks);
            Map<String, Assignment> next = colour(world, generation, geometry);
            if (Thread.currentThread().isInterrupted()) {
                return; // the layer is stopping: the colours stay as saved, and the next start builds the world again
            }
            boolean recoloured = colours.update(next);
            List<Shown> shown = shown(world, geometry, facts, next);
            int bytes = MarkerSize.gzipLevel1(shown.stream().map(Shown::area).toList());
            mainThread.execute(() -> finish(world, generation, shown, bytes, recoloured));
        } catch (RuntimeException e) {
            logger.log(Level.WARNING, "Custom biomes layer: the shapes of " + world + " could not be built", e);
        }
    }

    /**
     * The colours to have next, worked out from the colours now over the biomes and touches of every world on the
     * layer, this world's as this rebuild found them. So a biome painted on two worlds gets one colour that suits both.
     */
    private Map<String, Assignment> colour(String world, long generation, Geometry geometry) {
        Set<String> biomes = geometry.places().keySet();
        painted.merge(world, new Painted(generation, biomes, geometry.touching()), BiomeShapes::newer);
        Set<String> allBiomes = new HashSet<>(biomes);
        Set<Touch> allTouching = new HashSet<>(geometry.touching());
        for (Painted each : painted.values()) {
            allBiomes.addAll(each.biomes());
            allTouching.addAll(each.touching());
        }
        return BiomeColours.assign(colours.current(), allBiomes, allTouching);
    }

    /** The world's areas: for each region, one per biome, top first, all with the region's one window. */
    private List<Shown> shown(String world, Geometry geometry, Map<String, Facts> facts,
                              Map<String, Assignment> next) {
        List<Shown> shown = new ArrayList<>();
        for (Region region : geometry.regions()) {
            Map<String, int[]> heights = region.heights();
            List<BiomeWindow.Section> sections = new ArrayList<>();
            List<String> names = new ArrayList<>();
            for (String biome : region.biomes()) {
                Facts fact = facts.getOrDefault(biome, NO_FACTS);
                int[] range = heights.get(biome);
                sections.add(new BiomeWindow.Section(biome, fact.label(), range[0], range[1],
                        geometry.places().get(biome), geometry.chunks().get(biome), fact.settings(),
                        BiomeColours.PALETTE[next.get(biome).index()]));
                names.add(fact.label() != null && !fact.label().isBlank() ? fact.label() : biome);
            }
            // plain text, as the marker is made without markup
            String label = String.join(" + ", names);
            String window = BiomeWindow.html(sections);
            double lineOpacity = region.holes() ? SEAM_OPACITY : borderOpacity;
            double[] x = region.x();
            double[] z = region.z();
            for (String biome : region.biomes()) {
                int rgb = BiomeColours.PALETTE[next.get(biome).index()];
                String id = "cb." + world + "." + region.firstX() + "." + region.firstZ() + "." + biome;
                shown.add(new Shown(biome, new MapShape.Area(id, label, window,
                        new MapShape.Style(rgb, lineOpacity, borderWidth, rgb, areaOpacity), world, x, z)));
            }
        }
        return shown;
    }

    /**
     * Back on the main thread: keeps the world's areas, or holds the world back, unless a newer rebuild or a forget of
     * it came since. Then, if the rebuild changed any colours, every area kept takes its biome's colour.
     */
    private void finish(String world, long generation, List<Shown> shown, int bytes, boolean recoloured) {
        boolean changed = false;
        if (latest(world, generation)) {
            // its windows are new, which also settles a mark that another world's recolour set since the rebuild
            // started; a colour that changed after they were built marks the world again below
            behindInColour.remove(world);
            long kb = Math.round(bytes / 1000.0);
            if (bytes > MarkerSize.TARGET) {
                drawn.remove(world);
                if (oversize.put(world, bytes) == null) {
                    logger.warning("Custom biomes layer: " + world + " is not drawn: its markers would add " + kb
                            + " KB to every map visit, over the " + MarkerSize.TARGET / 1000 + " KB target.");
                }
            } else {
                drawn.put(world, new ArrayList<>(shown));
                if (oversize.remove(world) != null) {
                    logger.info("Custom biomes layer: " + world + " is drawn again: its markers add " + kb + " KB.");
                }
            }
            changed = true;
        }
        if (recoloured) {
            changed |= recolour();
        }
        if (changed) {
            redrawn.run();
        }
    }

    /**
     * Gives every area kept its biome's colour now, where that changed, and keeps all else, the line opacity included.
     * The areas just kept are among them, since the background thread may have coloured again after building them.
     * Says whether any changed. The window of such an area keeps the colour it was built with, so its world is marked
     * as behind, and {@link #factsChanged} says so until the world is built again.
     */
    private boolean recolour() {
        Map<String, Assignment> now = colours.current();
        boolean any = false;
        for (Map.Entry<String, List<Shown>> world : drawn.entrySet()) {
            List<Shown> shown = world.getValue();
            for (int i = 0; i < shown.size(); i++) {
                Shown one = shown.get(i);
                Assignment colour = now.get(one.biome());
                if (colour == null) {
                    continue;
                }
                int rgb = BiomeColours.PALETTE[colour.index()];
                MapShape.Area area = one.area();
                MapShape.Style style = area.style();
                if (style.lineColor() != rgb || style.fillColor() != rgb) {
                    shown.set(i, new Shown(one.biome(), new MapShape.Area(area.id(), area.label(),
                            area.description(), new MapShape.Style(rgb, style.lineOpacity(), style.lineWidth(), rgb,
                            style.fillOpacity()), area.world(), area.x(), area.z())));
                    behindInColour.add(world.getKey());
                    any = true;
                }
            }
        }
        return any;
    }
}
