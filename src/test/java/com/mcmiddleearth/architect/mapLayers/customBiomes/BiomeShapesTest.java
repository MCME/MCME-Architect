package com.mcmiddleearth.architect.mapLayers.customBiomes;

import com.mcmiddleearth.architect.mapLayers.MapLayerConfig;
import com.mcmiddleearth.architect.mapLayers.MapShape;
import com.mcmiddleearth.architect.mapLayers.customBiomes.BiomeColours.Assignment;
import com.mcmiddleearth.architect.mapLayers.customBiomes.BiomeFacts.Setting;
import com.mcmiddleearth.architect.mapLayers.customBiomes.BiomeFacts.Settings;
import com.mcmiddleearth.architect.mapLayers.customBiomes.BiomeWindow.Section;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import static com.mcmiddleearth.architect.mapLayers.customBiomes.BiomeColours.PALETTE;
import static org.junit.jupiter.api.Assertions.*;

// A world's record becomes areas off the main thread, and the main thread keeps them. Each region is drawn once per
// biome, all copies with one window, and a world whose markers would add too much to every map visit is not drawn and
// says so. The colours belong to the whole server, so a rebuild of one world must never undo another world's colours.
class BiomeShapesTest {

    @TempDir
    Path dir;

    private final Map<String, String> labels = new HashMap<>();
    private final Map<String, Settings> settings = new HashMap<>();
    /** The biomes whose facts biome tuning fails to read, with an unchecked exception. */
    private final Set<String> failing = new HashSet<>();
    private int factReads;
    private final BiomeFacts facts = new BiomeFacts() {
        @Override
        public String label(String biome) {
            factReads++;
            if (failing.contains(biome)) {
                throw new UncheckedIOException(new IOException("the datapacks folder cannot be listed"));
            }
            return labels.get(biome);
        }

        @Override
        public Settings settings(String biome) {
            factReads++;
            return settings.get(biome);
        }
    };
    private final List<LogRecord> logged = new ArrayList<>();
    private final Logger logger = Logger.getAnonymousLogger();
    private int redraws;
    private BiomeColours colours;
    private BiomeShapes shapes;

    @BeforeEach
    void setUp() {
        logger.setUseParentHandlers(false);
        logger.addHandler(new Handler() {
            @Override
            public void publish(LogRecord record) {
                logged.add(record);
            }

            @Override
            public void flush() {
            }

            @Override
            public void close() {
            }
        });
        colours = new BiomeColours(dir.resolve("colours.txt"));
        shapes = shapes(Runnable::run, Runnable::run);
    }

    private BiomeShapes shapes(Executor background, Executor mainThread) {
        return shapes(new YamlConfiguration(), background, mainThread);
    }

    private BiomeShapes shapes(YamlConfiguration config, Executor background, Executor mainThread) {
        return new BiomeShapes(new MapLayerConfig(config).layer("customBiomes", false), colours, facts, background,
                mainThread, logger, () -> redraws++);
    }

    /** Colours kept from before, as the lines of the colours file: biome;index;order. */
    private void keptColours(List<String> lines) throws IOException {
        Files.write(dir.resolve("colours.txt"), lines);
        colours = new BiomeColours(dir.resolve("colours.txt"));
        shapes = shapes(Runnable::run, Runnable::run);
    }

    /** p has colour 0 and r colour 1, q shares r's, and two biomes that touch nothing hold each colour from 2 up. */
    private static List<String> threeAndFillers() {
        List<String> lines = new ArrayList<>(List.of("cbc:p;0;1", "cbc:r;1;2", "cbc:q;1;3"));
        int order = 4;
        for (int colour = 2; colour < PALETTE.length; colour++) {
            lines.add("cbc:fill" + colour + "a;" + colour + ";" + order++);
            lines.add("cbc:fill" + colour + "b;" + colour + ";" + order++);
        }
        return lines;
    }

    /** Paint laid column by column; x and z count the 4 x 4 columns the record keeps. */
    private static final class Paint {

        private final Map<Long, List<List<BiomeSpan>>> chunks = new HashMap<>();

        /** The biome from Y 0 to 63 in each column, given as x, z pairs. */
        Paint add(String biome, int... xz) {
            return add(new BiomeSpan(biome, 0, 63), xz);
        }

        Paint add(BiomeSpan span, int... xz) {
            for (int i = 0; i < xz.length; i += 2) {
                column(xz[i], xz[i + 1]).add(span);
            }
            return this;
        }

        private List<BiomeSpan> column(int x, int z) {
            List<List<BiomeSpan>> sixteen = chunks.computeIfAbsent(
                    PaintRecord.chunkKey(Math.floorDiv(x, 4), Math.floorDiv(z, 4)), key -> {
                        List<List<BiomeSpan>> columns = new ArrayList<>();
                        for (int i = 0; i < 16; i++) {
                            columns.add(new ArrayList<>());
                        }
                        return columns;
                    });
            return sixteen.get(Math.floorMod(z, 4) * 4 + Math.floorMod(x, 4));
        }

        Map<Long, ChunkPaint> chunks() {
            Map<Long, ChunkPaint> paints = new HashMap<>();
            chunks.forEach((key, columns) -> paints.put(key, ChunkPaint.of(columns)));
            return paints;
        }
    }

    /** One column holding a forest from Y 0 to 319, and fireflies from 60 to 80 within it. */
    private static Map<Long, ChunkPaint> forestWithFireflies() {
        return new Paint().add(new BiomeSpan("cbc:forest", 0, 319), 0, 0)
                .add(new BiomeSpan("cbc:fireflies", 60, 80), 0, 0).chunks();
    }

    /**
     * 4,000 single-column regions of 400 biomes, each with a long label of its own, a column apart so that none touch.
     * The same biome comes back only every 400 regions, too far back for the compressor to find it.
     */
    private Map<Long, ChunkPaint> crowded() {
        Random random = new Random(20261028L);
        Paint paint = new Paint();
        for (int i = 0; i < 4_000; i++) {
            String biome = String.format("cbc:b%03d", i % 400);
            if (i < 400) {
                StringBuilder label = new StringBuilder();
                for (int letter = 0; letter < 250; letter++) {
                    label.append((char) ('a' + random.nextInt(26)));
                }
                labels.put(biome, label.toString());
            }
            paint.add(biome, 2 * i, 0);
        }
        return paint.chunks();
    }

    /** An executor that keeps what it is given until the test runs it. */
    private static final class Held implements Executor {

        private final Deque<Runnable> tasks = new ArrayDeque<>();

        @Override
        public void execute(Runnable task) {
            tasks.addLast(task);
        }

        void runAll() {
            while (!tasks.isEmpty()) {
                tasks.removeFirst().run();
            }
        }

        void runOldest() {
            tasks.removeFirst().run();
        }

        void runNewest() {
            tasks.removeLast().run();
        }
    }

    private List<MapShape.Area> areas() {
        return shapes.shapes().stream().map(MapShape.Area.class::cast).toList();
    }

    private List<MapShape.Area> areas(String world) {
        return areas().stream().filter(area -> area.world().equals(world)).toList();
    }

    private List<String> ids() {
        return areas().stream().map(MapShape.Area::id).toList();
    }

    private MapShape.Area area(String id) {
        return areas().stream().filter(area -> area.id().equals(id)).findFirst()
                .orElseThrow(() -> new AssertionError("no area " + id + " in " + ids()));
    }

    private int index(String biome) {
        return colours.current().get(biome).index();
    }

    private List<String> messages(Level level) {
        return logged.stream().filter(record -> record.getLevel() == level).map(LogRecord::getMessage).toList();
    }

    @Test
    void eachRegionIsOneAreaPerBiomeWithOneSharedWindow() {
        shapes.rebuild("world", forestWithFireflies());

        List<MapShape.Area> areas = areas();
        assertEquals(2, areas.size());
        assertEquals(List.of("cb.world.0.0.cbc:forest", "cb.world.0.0.cbc:fireflies"), ids(), "top first");
        PaintRegions.Region region = PaintRegions.of(forestWithFireflies()).regions().get(0);
        for (MapShape.Area area : areas) {
            assertArrayEquals(region.x(), area.x());
            assertArrayEquals(region.z(), area.z());
            assertEquals("world", area.world());
        }
        assertEquals(areas.get(0).description(), areas.get(1).description());
        assertEquals(BiomeWindow.html(List.of(new Section("cbc:forest", null, 0, 319, 1, 1, null,
                        PALETTE[index("cbc:forest")]),
                new Section("cbc:fireflies", null, 60, 80, 1, 1, null, PALETTE[index("cbc:fireflies")]))),
                areas.get(0).description());
        assertNotEquals(areas.get(0).style().fillColor(), areas.get(1).style().fillColor(), "they touch");
        int forest = PALETTE[index("cbc:forest")];
        assertEquals(new MapShape.Style(forest, 0.6, 2, forest, 0.3), areas.get(0).style());
        assertEquals(1, redraws);
    }

    /** The colour as the window's header spells it. */
    private static String hex(int rgb) {
        return String.format("#%06x", rgb);
    }

    @Test
    void eachHeaderOfTheWindowHasTheColourItsBiomeIsDrawnIn() {
        shapes.rebuild("world", forestWithFireflies());

        String window = areas().get(0).description();
        String forest = "border-left:5px solid " + hex(areas().get(0).style().fillColor()) + ";";
        String fireflies = "border-left:5px solid " + hex(areas().get(1).style().fillColor()) + ";";
        assertEquals(hex(PALETTE[index("cbc:forest")]), hex(areas().get(0).style().fillColor()));
        assertEquals(hex(PALETTE[index("cbc:fireflies")]), hex(areas().get(1).style().fillColor()));
        assertTrue(window.contains(forest), window);
        assertTrue(window.contains(fireflies), window);
        assertTrue(window.indexOf(forest) < window.indexOf(fireflies), "top first: " + window);
    }

    @Test
    void theHoverLabelIsThePlainNamesTopFirst() {
        labels.put("cbc:forest", "Old <forest>");

        shapes.rebuild("world", forestWithFireflies());

        for (MapShape.Area area : areas()) {
            assertEquals("Old <forest> + cbc:fireflies", area.label(), "plain text: the marker is no markup");
        }
        String window = areas().get(0).description();
        assertTrue(window.contains("<b>Old &lt;forest&gt;</b> <code>cbc:forest</code>"),
                "the window escapes it");
        assertFalse(window.contains("Old <forest>"), "and only the label is plain text");
    }

    @Test
    void aRegionWithHolesHidesItsBorder() {
        // a ring of cbc:a round cbc:b: one region with a hole, joined by a seam, and the region in the hole
        shapes.rebuild("world", new Paint().add("cbc:a", 0, 0, 1, 0, 2, 0, 0, 1, 2, 1, 0, 2, 1, 2, 2, 2)
                .add("cbc:b", 1, 1).chunks());

        assertEquals(0.01, area("cb.world.0.0.cbc:a").style().lineOpacity(), "LiveAtlas draws 0 as 1");
        assertEquals(0.6, area("cb.world.1.1.cbc:b").style().lineOpacity());
    }

    @Test
    void markerIdsStayTheSameWhenAnotherRegionAppears() {
        shapes.rebuild("world", new Paint().add("cbc:a", 3, 5).chunks());
        assertEquals(List.of("cb.world.3.5.cbc:a"), ids());

        // further north-west, so it is listed first
        shapes.rebuild("world", new Paint().add("cbc:a", 3, 5).add("cbc:a", -100, -100).chunks());

        assertEquals(List.of("cb.world.-100.-100.cbc:a", "cb.world.3.5.cbc:a"), ids());
    }

    @Test
    void aWorldOverTheSizeTargetIsNotDrawnAndSaysWhy() {
        shapes.rebuild("small", new Paint().add("cbc:a", 0, 0).chunks());
        Map<Long, ChunkPaint> crowded = crowded();

        shapes.rebuild("world", crowded);

        assertTrue(areas("world").isEmpty(), "nothing of it is drawn");
        assertEquals(1, areas("small").size(), "the other world still is");
        assertEquals(Set.of("world"), shapes.oversize().keySet());
        int bytes = shapes.oversize().get("world");
        assertTrue(bytes > MarkerSize.TARGET, () -> bytes + " bytes");
        assertEquals(List.of("Custom biomes layer: world is not drawn: its markers would add "
                + Math.round(bytes / 1000.0) + " KB to every map visit, over the 512 KB target."),
                messages(Level.WARNING));

        shapes.rebuild("world", crowded);

        assertEquals(1, messages(Level.WARNING).size(), "said once while it stays over");
        assertTrue(areas("world").isEmpty());
        assertTrue(shapes.oversize().containsKey("world"));
    }

    // While a world is read for the first time, its markers grow file by file. Once they pass the target, its areas
    // drawn so far leave the map, so that they do not slow down every map visit.
    @Test
    void aDrawnWorldThatGrowsPastTheTargetIsTakenOffTheMap() {
        shapes.rebuild("world", new Paint().add("cbc:a", 0, 0).chunks());
        assertEquals(List.of("cb.world.0.0.cbc:a"), ids());

        shapes.rebuild("world", crowded());

        assertEquals(List.of(), areas("world"), "its areas left the map");
        assertEquals(Set.of("world"), shapes.oversize().keySet());
        assertEquals(1, messages(Level.WARNING).size(), messages(Level.WARNING).toString());
    }

    @Test
    void aWorldBackUnderTheTargetIsDrawnAgain() {
        shapes.rebuild("world", crowded());

        shapes.rebuild("world", new Paint().add("cbc:b007", 0, 0).chunks());

        assertEquals(List.of("cb.world.0.0.cbc:b007"), ids());
        assertTrue(shapes.oversize().isEmpty());
        long kb = Math.round(MarkerSize.gzipLevel1(areas()) / 1000.0);
        assertEquals(List.of("Custom biomes layer: world is drawn again: its markers add " + kb + " KB."),
                messages(Level.INFO));
    }

    @Test
    void anOlderRebuildFinishingLateIsDropped() {
        Held background = new Held();
        shapes = shapes(background, Runnable::run);

        shapes.rebuild("world", new Paint().add("cbc:a", 0, 0).chunks());
        shapes.rebuild("world", new Paint().add("cbc:b", 5, 5).chunks());
        background.runNewest();
        background.runNewest();

        assertEquals(List.of("cb.world.5.5.cbc:b"), ids());
    }

    @Test
    void aResultHandedOverAfterANewerRebuildStartedIsDropped() {
        Held background = new Held();
        Held mainThread = new Held();
        shapes = shapes(background, mainThread);
        shapes.rebuild("world", new Paint().add("cbc:a", 0, 0).chunks());
        background.runAll();
        shapes.rebuild("world", new Paint().add("cbc:b", 5, 5).chunks());

        mainThread.runAll();
        assertEquals(List.of(), ids(), "the first came back after the second had started");
        background.runAll();
        mainThread.runAll();

        assertEquals(List.of("cb.world.5.5.cbc:b"), ids());
    }

    @Test
    void factsChangedNoticesALabelChange() {
        shapes.rebuild("world", forestWithFireflies());
        assertFalse(shapes.factsChanged());

        labels.put("cbc:forest", "Old forest");
        assertTrue(shapes.factsChanged());

        shapes.rebuild("world", forestWithFireflies());
        assertFalse(shapes.factsChanged());
        assertEquals("Old forest + cbc:fireflies", areas().get(0).label());
    }

    @Test
    void factsChangedNoticesASettingsChange() {
        shapes.rebuild("world", forestWithFireflies());

        settings.put("cbc:fireflies", new Settings(Map.of("Climate", List.of(new Setting("rain", "on", null))), true));

        assertTrue(shapes.factsChanged());
    }

    // Biome tuning may fail with an unchecked exception, such as a datapack folder that cannot be listed. The biome it
    // fails for is drawn with its id and no settings, the others with their facts, and the failure is said once, not
    // at every redraw: again only once the biome has been read and fails anew.
    @Test
    void aBiomeWhoseFactsFailToReadIsDrawnWithoutThemAndSaidOnce() {
        labels.put("cbc:forest", "Old forest");
        failing.add("cbc:fireflies");

        shapes.rebuild("world", forestWithFireflies());
        shapes.rebuild("world", forestWithFireflies());

        assertFalse(shapes.factsChanged(), "failing again is no change");
        assertEquals("Old forest + cbc:fireflies", areas().get(0).label());
        assertEquals(BiomeWindow.html(List.of(new Section("cbc:forest", "Old forest", 0, 319, 1, 1, null,
                        PALETTE[index("cbc:forest")]),
                new Section("cbc:fireflies", null, 60, 80, 1, 1, null, PALETTE[index("cbc:fireflies")]))),
                areas().get(0).description());
        assertEquals(List.of("Custom biomes layer: biome tuning could not tell the name and settings of "
                + "cbc:fireflies, so its window shows its id and no settings."), messages(Level.WARNING));
        assertInstanceOf(UncheckedIOException.class, logged.stream()
                .filter(record -> record.getLevel() == Level.WARNING).findFirst().orElseThrow().getThrown());

        failing.clear();
        shapes.rebuild("world", forestWithFireflies());
        failing.add("cbc:fireflies");
        shapes.rebuild("world", forestWithFireflies());

        assertEquals(2, messages(Level.WARNING).size(), "a new failure is said again");
    }

    // Biome tuning may only be asked on the main thread, so the facts are read where a rebuild starts
    @Test
    void theFactsAreReadWhenTheRebuildStartsAndNeverOnTheBackgroundThread() {
        Held background = new Held();
        shapes = shapes(background, Runnable::run);
        labels.put("cbc:forest", "Old forest");

        shapes.rebuild("world", forestWithFireflies());
        int reads = factReads;
        labels.put("cbc:forest", "Young forest");
        background.runAll();

        assertEquals(4, reads, "a label and the settings of each biome");
        assertEquals(reads, factReads, "nothing asked on the background thread");
        assertEquals("Old forest + cbc:fireflies", areas().get(0).label());
    }

    // A stop interrupts the shapes thread. A build that finds itself interrupted gives up before it takes the colours
    // over and saves them, where an interruptible write would fail with a warning: the next start builds the world
    // again.
    @Test
    void aBuildInterruptedByAStopLeavesTheColoursAlone() {
        Logger coloursLog = Logger.getLogger(BiomeColours.class.getName());
        List<LogRecord> warned = new ArrayList<>();
        Handler catcher = new Handler() {
            @Override
            public void publish(LogRecord record) {
                warned.add(record);
            }

            @Override
            public void flush() {
            }

            @Override
            public void close() {
            }
        };
        coloursLog.addHandler(catcher);
        shapes = shapes(task -> {
            Thread.currentThread().interrupt(); // as the stop's shutdownNow() does
            try {
                task.run();
            } finally {
                Thread.interrupted();
            }
        }, Runnable::run);
        try {
            shapes.rebuild("world", new Paint().add("cbc:a", 0, 0).chunks());
        } finally {
            coloursLog.removeHandler(catcher);
        }

        assertEquals(Map.of(), colours.current(), "no colour taken");
        assertFalse(Files.exists(dir.resolve("colours.txt")), "nothing written");
        assertEquals(List.of(), warned);
        assertEquals(List.of(), ids(), "nothing drawn: the layer is stopping");
    }

    @Test
    void theBorderAndFillComeFromTheConfig() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("mapLayers.customBiomes.borderWidth", 3);
        config.set("mapLayers.customBiomes.borderOpacity", 0.8);
        config.set("mapLayers.customBiomes.areaOpacity", 0.5);
        shapes = shapes(config, Runnable::run, Runnable::run);

        shapes.rebuild("world", new Paint().add("cbc:a", 0, 0).chunks());

        int a = PALETTE[index("cbc:a")];
        assertEquals(new MapShape.Style(a, 0.8, 3, a, 0.5), area("cb.world.0.0.cbc:a").style());
    }

    @Test
    void theShapesListTheWorldsByName() {
        shapes.rebuild("zeta", new Paint().add("cbc:a", 0, 0).chunks());
        shapes.rebuild("alpha", new Paint().add("cbc:a", 0, 0).chunks());

        assertEquals(List.of("cb.alpha.0.0.cbc:a", "cb.zeta.0.0.cbc:a"), ids());
    }

    @Test
    void forgetTakesTheWorldOffTheMap() {
        Held background = new Held();
        shapes = shapes(background, Runnable::run);
        shapes.rebuild("world", new Paint().add("cbc:a", 0, 0).chunks());
        shapes.rebuild("held", crowded());
        background.runAll();
        assertEquals(1, ids().size());
        assertEquals(Set.of("held"), shapes.oversize().keySet());
        shapes.rebuild("world", new Paint().add("cbc:a", 9, 9).chunks());

        shapes.forget("world");
        shapes.forget("held");
        background.runAll();

        assertEquals(List.of(), ids(), "a rebuild still under way is dropped too");
        assertTrue(shapes.oversize().isEmpty());
        assertFalse(shapes.factsChanged());
    }

    // A rebuild colours on the background thread, starting from the colours the one before it left. A result worked
    // out from an older copy, such as one taken when both rebuilds started, would write that copy's colours back over
    // newer ones, or colour the same new biome again.
    @Test
    void twoWorldsRebuiltBackToBackCannotRevertEachOthersColours() throws IOException {
        keptColours(List.of("cbc:p;0;1", "cbc:q;0;2")); // q has p's colour, which is fine while they touch nowhere
        Held background = new Held();
        shapes = shapes(background, Runnable::run);
        // in a, q touches p, so a colours q again; x is new
        shapes.rebuild("a", new Paint().add("cbc:p", 0, 0).add("cbc:q", 1, 0).add("cbc:x", 5, 5).chunks());
        // b has a new biome too, which comes before x
        shapes.rebuild("b", new Paint().add("cbc:q", 0, 0).add("cbc:a", 5, 5).chunks());

        background.runOldest();
        Assignment q = colours.current().get("cbc:q");
        Assignment x = colours.current().get("cbc:x");
        assertNotEquals(index("cbc:p"), q.index());
        background.runAll();

        assertEquals(q, colours.current().get("cbc:q"), "a's new colour for q stands");
        assertEquals(x, colours.current().get("cbc:x"), "x keeps the colour and place a gave it");
        assertEquals(PALETTE[q.index()], area("cb.a.1.0.cbc:q").style().fillColor());
        assertEquals(PALETTE[q.index()], area("cb.b.0.0.cbc:q").style().fillColor());
        assertEquals(PALETTE[x.index()], area("cb.a.5.5.cbc:x").style().fillColor());
    }

    // The colours count every world's touches at once. Counting only the rebuilt world's, q, which touches p in one
    // world and r in the other, would move from r's colour to p's and back at every rebuild.
    @Test
    void aBiomeSharedByTwoWorldsKeepsOneColourAcrossRebuilds() throws IOException {
        keptColours(threeAndFillers());
        Map<Long, ChunkPaint> a = new Paint().add("cbc:p", 0, 0).add("cbc:q", 1, 0).chunks();
        Map<Long, ChunkPaint> b = new Paint().add("cbc:q", 0, 0).add("cbc:r", 1, 0).chunks();

        shapes.rebuild("a", a);
        shapes.rebuild("b", b);
        int q = index("cbc:q");
        assertNotEquals(index("cbc:p"), q);
        assertNotEquals(index("cbc:r"), q);
        for (int round = 1; round <= 3; round++) {
            shapes.rebuild("a", a);
            assertEquals(q, index("cbc:q"), "after a, round " + round);
            shapes.rebuild("b", b);
            assertEquals(q, index("cbc:q"), "after b, round " + round);
        }

        assertEquals(PALETTE[q], area("cb.a.1.0.cbc:q").style().fillColor());
        assertEquals(PALETTE[q], area("cb.b.0.0.cbc:q").style().fillColor());
    }

    @Test
    void aColourChangeRestylesTheOtherWorldsAreas() throws IOException {
        keptColours(List.of("cbc:r;1;1", "cbc:q;1;2"));
        // in a, q is a ring round an unpainted column, so its border is hidden
        shapes.rebuild("a", new Paint().add("cbc:q", 0, 0, 1, 0, 2, 0, 0, 1, 2, 1, 0, 2, 1, 2, 2, 2).chunks());
        MapShape.Area before = area("cb.a.0.0.cbc:q");
        assertEquals(PALETTE[1], before.style().fillColor());
        int redrawsBefore = redraws;

        // in b, q touches r, which had colour 1 first, so q takes another
        shapes.rebuild("b", new Paint().add("cbc:q", 0, 0).add("cbc:r", 1, 0).chunks());

        int q = PALETTE[index("cbc:q")];
        assertNotEquals(PALETTE[1], q);
        assertEquals(new MapShape.Area(before.id(), before.label(), before.description(),
                new MapShape.Style(q, 0.01, 2, q, 0.3), "a", before.x(), before.z()), area("cb.a.0.0.cbc:q"));
        assertEquals(q, area("cb.b.0.0.cbc:q").style().fillColor());
        assertEquals(PALETTE[1], area("cb.b.1.0.cbc:r").style().fillColor());
        assertEquals(redrawsBefore + 1, redraws);
    }

    // A window's header has the colour its area is drawn in. When another world's rebuild gives a biome another colour,
    // the areas change colour at once, but their windows, built earlier, still show the old one. Like a changed name,
    // that makes the windows behind: the next facts check draws every world again.
    @Test
    void aColourChangeLeavesTheOtherWorldsWindowsBehindUntilTheyAreDrawnAgain() throws IOException {
        keptColours(List.of("cbc:r;1;1", "cbc:q;1;2"));
        Map<Long, ChunkPaint> a = new Paint().add("cbc:q", 0, 0).chunks();
        shapes.rebuild("a", a);
        assertTrue(area("cb.a.0.0.cbc:q").description().contains("solid " + hex(PALETTE[1]) + ";"));
        assertFalse(shapes.factsChanged());

        // in b, q touches r, which had colour 1 first, so q takes another
        shapes.rebuild("b", new Paint().add("cbc:q", 0, 0).add("cbc:r", 1, 0).chunks());

        int q = PALETTE[index("cbc:q")];
        assertNotEquals(PALETTE[1], q);
        assertEquals(q, area("cb.a.0.0.cbc:q").style().fillColor(), "the area has the new colour at once");
        assertTrue(area("cb.a.0.0.cbc:q").description().contains("solid " + hex(PALETTE[1]) + ";"), "its window not");
        assertTrue(area("cb.b.0.0.cbc:q").description().contains("solid " + hex(q) + ";"), "b was built with it");
        assertTrue(shapes.factsChanged(), "so a is to be drawn again");

        shapes.rebuild("a", a);

        assertTrue(area("cb.a.0.0.cbc:q").description().contains("solid " + hex(q) + ";"));
        assertEquals(q, area("cb.a.0.0.cbc:q").style().fillColor());
        assertFalse(shapes.factsChanged());
    }

    // The same, inside one build: the background thread may colour again after a world's windows are built and before
    // the main thread keeps them, and the areas just kept take the newer colours at once.
    @Test
    void windowsBuiltBeforeAnotherWorldsRebuildChangedTheColoursAreBehindToo() throws IOException {
        keptColours(List.of("cbc:r;1;1", "cbc:q;1;2"));
        Held background = new Held();
        Held mainThread = new Held();
        shapes = shapes(background, mainThread);
        // a has a new biome, so its build changes the colours held; b recolours q, which a holds too
        shapes.rebuild("a", new Paint().add("cbc:q", 0, 0).add("cbc:x", 5, 5).chunks());
        shapes.rebuild("b", new Paint().add("cbc:q", 0, 0).add("cbc:r", 1, 0).chunks());

        background.runAll();
        mainThread.runAll();

        int q = PALETTE[index("cbc:q")];
        assertNotEquals(PALETTE[1], q);
        assertEquals(q, area("cb.a.0.0.cbc:q").style().fillColor());
        assertTrue(area("cb.a.0.0.cbc:q").description().contains("solid " + hex(PALETTE[1]) + ";"), "built before");
        assertTrue(shapes.factsChanged());

        shapes.rebuild("a", new Paint().add("cbc:q", 0, 0).add("cbc:x", 5, 5).chunks());
        background.runAll();
        mainThread.runAll();

        assertTrue(area("cb.a.0.0.cbc:q").description().contains("solid " + hex(q) + ";"));
        assertFalse(shapes.factsChanged());
    }

    // A colour change can leave the windows of several worlds behind. Drawing one of them again, as the report does for
    // a world whose record changed, ends that world's mark only: the other stays behind until it is drawn again too.
    @Test
    void rebuildingOneWorldLeavesAnotherWorldsMarkAlone() throws IOException {
        keptColours(List.of("cbc:r;1;1", "cbc:q;1;2"));
        Map<Long, ChunkPaint> a = new Paint().add("cbc:q", 0, 0).chunks();
        Map<Long, ChunkPaint> c = new Paint().add("cbc:q", 4, 4).chunks();
        shapes.rebuild("a", a);
        shapes.rebuild("c", c);
        // in b, q touches r, which had colour 1 first, so q takes another: the windows of a and c are behind
        shapes.rebuild("b", new Paint().add("cbc:q", 0, 0).add("cbc:r", 1, 0).chunks());
        assertTrue(shapes.factsChanged());

        shapes.rebuild("a", a);

        assertTrue(shapes.factsChanged(), "c is still behind");
        shapes.rebuild("c", c);
        assertFalse(shapes.factsChanged());
    }

    @Test
    void aWorldTakenOffTheMapIsNoLongerBehind() throws IOException {
        keptColours(List.of("cbc:r;1;1", "cbc:q;1;2"));
        shapes.rebuild("a", new Paint().add("cbc:q", 0, 0).chunks());
        shapes.rebuild("b", new Paint().add("cbc:q", 0, 0).add("cbc:r", 1, 0).chunks());
        assertTrue(shapes.factsChanged());

        shapes.forget("a");

        assertFalse(shapes.factsChanged());
    }

    // Like a changed name, a colour that left a window behind is dealt with once the world's rebuild has started, which
    // reads the colours anew: the next check must not ask for another while that one is under way.
    @Test
    void aWorldBeingDrawnAgainIsNotBehindAtTheNextCheck() throws IOException {
        keptColours(List.of("cbc:r;1;1", "cbc:q;1;2"));
        Held background = new Held();
        shapes = shapes(background, Runnable::run);
        Map<Long, ChunkPaint> a = new Paint().add("cbc:q", 0, 0).chunks();
        shapes.rebuild("a", a);
        background.runAll();
        // in b, q touches r, which had colour 1 first, so q takes another: a's window is behind
        shapes.rebuild("b", new Paint().add("cbc:q", 0, 0).add("cbc:r", 1, 0).chunks());
        background.runAll();
        assertTrue(shapes.factsChanged());

        shapes.rebuild("a", a); // the check draws a again, and its build has not run yet

        assertFalse(shapes.factsChanged(), "a is being drawn again: another build would not help");
        background.runAll();
        assertTrue(area("cb.a.0.0.cbc:q").description().contains("solid " + hex(PALETTE[index("cbc:q")]) + ";"));
        assertFalse(shapes.factsChanged());
    }

    // A build that fails, or that takes longer than the 10 seconds between two checks, delivers no finish to clear the
    // mark. The world must not be marked for good: every check would draw every world again, and say why in the log.
    @Test
    void aRecolouredWorldWhoseBuildFailsIsNotDrawnAgainEveryTenSeconds() throws IOException {
        keptColours(List.of("cbc:r;1;1", "cbc:q;1;2"));
        Map<Long, ChunkPaint> a = new Paint().add("cbc:q", 0, 0).chunks();
        Map<Long, ChunkPaint> b = new Paint().add("cbc:q", 0, 0).add("cbc:r", 1, 0).chunks();
        boolean[] failing = {false};
        // while this is set, a build fails when it hands its result over, as any exception in a build would: no finish
        shapes = shapes(Runnable::run, task -> {
            if (failing[0]) {
                throw new IllegalStateException("the hand-over failed");
            }
            task.run();
        });
        shapes.rebuild("a", a);
        shapes.rebuild("b", b); // q takes another colour: a's window is behind
        assertTrue(shapes.factsChanged());
        failing[0] = true;

        int drawnAgain = 0;
        for (int check = 0; check < 5; check++) { // what the timer does every 10 seconds: draw every world again
            if (shapes.factsChanged()) {
                drawnAgain++;
                shapes.rebuild("a", a);
                shapes.rebuild("b", b);
            }
        }

        assertEquals(1, drawnAgain, "drawn again once, not at every check");
        assertEquals(2, messages(Level.WARNING).size(), "a failure for each of the two builds: "
                + messages(Level.WARNING));
    }

    // Another world's recolour can mark a world after its rebuild has started. That build began after the new colours
    // were taken, so its windows have them, and the mark ends with its finish.
    @Test
    void aMarkSetWhileAWorldIsBeingDrawnAgainEndsWithTheFinishOfItsBuild() throws IOException {
        keptColours(List.of("cbc:r;1;1", "cbc:q;1;2"));
        Held background = new Held();
        Held mainThread = new Held();
        shapes = shapes(background, mainThread);
        Map<Long, ChunkPaint> a = new Paint().add("cbc:q", 0, 0).chunks();
        shapes.rebuild("a", a);
        background.runAll();
        mainThread.runAll();
        // b gives q another colour on the background thread; its result has not reached the main thread yet
        shapes.rebuild("b", new Paint().add("cbc:q", 0, 0).add("cbc:r", 1, 0).chunks());
        background.runAll();

        shapes.rebuild("a", a); // starts after the new colours, and before they reach a's areas
        mainThread.runAll(); // b's result: a's areas take the new colour, and a is marked
        assertTrue(shapes.factsChanged(), "marked after its rebuild started");
        background.runAll();
        mainThread.runAll();

        int q = PALETTE[index("cbc:q")];
        assertNotEquals(PALETTE[1], q);
        assertTrue(area("cb.a.0.0.cbc:q").description().contains("solid " + hex(q) + ";"), "built with the new colour");
        assertFalse(shapes.factsChanged(), "so none is behind");
    }

    @Test
    void forgetDropsTheWorldFromTheColourUnion() throws IOException {
        keptColours(threeAndFillers()); // with a and b both on the layer, q gets colour 2: see the test above
        Held background = new Held();
        shapes = shapes(background, Runnable::run);
        Map<Long, ChunkPaint> a = new Paint().add("cbc:p", 0, 0).add("cbc:q", 1, 0).chunks();
        shapes.rebuild("a", a);
        background.runAll();
        shapes.rebuild("a", a); // still waiting when a is taken off

        shapes.forget("a");
        background.runAll();
        shapes.rebuild("b", new Paint().add("cbc:q", 0, 0).add("cbc:r", 1, 0).chunks());
        background.runAll();

        assertEquals(0, index("cbc:q"), "q may take p's colour: they touch nowhere now");
        assertEquals(PALETTE[0], area("cb.b.0.0.cbc:q").style().fillColor());
        assertTrue(areas("a").isEmpty());
    }
}
