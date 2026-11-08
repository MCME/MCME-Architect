package com.mcmiddleearth.architect.slabSmooth;

import com.mcmiddleearth.architect.slabSmooth.core.Table;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Predicate;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Packs on a tiny pack (a zip), a tiny client jar (a zip) and an inventory folder, with a fake source of packs. The
 * pack holds stone and its model; the jar holds the slab pieces (so both zips are needed); more.yml adds bricks.
 */
class PacksTest {

    private static final String WORKER = "Architect SlabSmooth packs";
    private static final String MC = "26.2";

    @TempDir
    Path dir;
    private Path data, zip, jar, inventory;
    private final List<Packs> opened = new ArrayList<>();
    private final List<String> infos = Collections.synchronizedList(new ArrayList<>());
    private final List<String> warnings = Collections.synchronizedList(new ArrayList<>());
    private Logger log;
    private Fake fake;
    private final AtomicLong clock = new AtomicLong(1_000_000);

    /** The made-up source of packs: one pack, Human, whose zip is already in the data folder. */
    private final class Fake implements PackSources {
        final Set<String> inventoryThreads = ConcurrentHashMap.newKeySet(), sourceThreads = ConcurrentHashMap.newKeySet();
        final AtomicInteger regionCalls = new AtomicInteger(), sourceCalls = new AtomicInteger();
        volatile CountDownLatch gate; // while set, asking for the inventory waits for it
        volatile CountDownLatch entered; // counted down when something waits at the gate
        volatile String region, current;

        @Override
        public List<String> packs() {
            return List.of("Human");
        }

        @Override
        public Path inventory(String pack) {
            inventoryThreads.add(Thread.currentThread().getName());
            CountDownLatch g = gate;
            if (g != null) {
                CountDownLatch e = entered;
                if (e != null) {
                    e.countDown();
                }
                try {
                    g.await(20, TimeUnit.SECONDS);
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                }
            }
            return inventory;
        }

        @Override
        public Source source(String pack) {
            sourceThreads.add(Thread.currentThread().getName());
            sourceCalls.incrementAndGet();
            return null;
        }

        @Override
        public String regionPack(Location location) {
            regionCalls.incrementAndGet();
            return region;
        }

        @Override
        public String currentPack(Player player) {
            return current;
        }
    }

    @BeforeEach
    void setUp() throws IOException {
        data = dir.resolve("data");
        zip = data.resolve("packs").resolve("Human.zip");
        jar = dir.resolve("client.jar");
        inventory = dir.resolve("inventory");
        Files.createDirectories(zip.getParent());
        Files.createDirectories(inventory);
        writeZip(zip, Map.of(
                "pack.mcmeta", "{}",
                "assets/minecraft/blockstates/stone.json", "{\"variants\": {\"\": {\"model\": \"block/rock\"}}}",
                "assets/minecraft/models/block/rock.json", model("block/rock", 16)));
        writeZip(jar, Map.of(
                "assets/minecraft/blockstates/comparator.json", "{\"variants\": {\"facing=north\": {\"model\": \"block/rock_slab\"}}}",
                "assets/minecraft/models/block/rock_slab.json", model("block/rock", 8),
                "assets/minecraft/blockstates/bricks.json", "{\"variants\": {\"\": {\"model\": \"block/brick\"}}}",
                "assets/minecraft/models/block/brick.json", model("block/brick", 16),
                "assets/minecraft/blockstates/purpur_slab.json", "{\"variants\": {\"type=bottom\": {\"model\": \"block/brick_slab\"}}}",
                "assets/minecraft/models/block/brick_slab.json", model("block/brick", 8)));
        Files.writeString(inventory.resolve("terra.yml"), """
                Items:
                  rock:
                    type: VANILLA
                    itemMaterial: stone
                    category: Terra
                    display: Rock
                  rock_slab:
                    type: BLOCK
                    blockData: "minecraft:comparator[facing=north]"
                    display: Rock slab
                    collection:
                      indirect: rock
                """);
        fake = new Fake();
        log = Logger.getAnonymousLogger();
        log.setUseParentHandlers(false);
        log.addHandler(new Handler() {
            @Override
            public void publish(LogRecord r) {
                (r.getLevel().intValue() >= Level.WARNING.intValue() ? warnings : infos).add(r.getMessage());
            }

            @Override
            public void flush() {
            }

            @Override
            public void close() {
            }
        });
    }

    @AfterEach
    void closePacks() {
        opened.forEach(Packs::close);
    }

    private static String model(String texture, int top) {
        return """
                {"textures": {"t": "%s"}, "elements": [{"from": [0, 0, 0], "to": [16, %d, 16], "faces": {
                  "up": {"texture": "#t"}, "down": {"texture": "#t"}, "north": {"texture": "#t"},
                  "south": {"texture": "#t"}, "east": {"texture": "#t"}, "west": {"texture": "#t"}}}]}""".formatted(texture, top);
    }

    private static void writeZip(Path file, Map<String, String> entries) throws IOException {
        try (OutputStream out = Files.newOutputStream(file); ZipOutputStream zos = new ZipOutputStream(out)) {
            for (Map.Entry<String, String> e : new LinkedHashMap<>(entries).entrySet()) {
                zos.putNextEntry(new ZipEntry(e.getKey()));
                zos.write(e.getValue().getBytes(StandardCharsets.UTF_8));
                zos.closeEntry();
            }
        }
    }

    /** Adds a second terrain material, bricks, to the inventory. */
    private void addBricks() throws IOException {
        Files.writeString(inventory.resolve("more.yml"), """
                Items:
                  bricks:
                    type: VANILLA
                    itemMaterial: bricks
                    category: Terra
                    display: Bricks
                  brick_slab:
                    type: BLOCK
                    blockData: "minecraft:purpur_slab[type=bottom]"
                    display: Brick slab
                    collection:
                      indirect: bricks
                """);
    }

    private SlabSmoothSettings settings() {
        return new SlabSmoothSettings("Human", false, List.of("Terra"), List.of("Plant", "Crop"), 10, jar.toString(),
                Map.of(), 12, 65536, 8_000_000L, 120, Map.of());
    }

    private Packs packs() {
        return packs(MC, s -> true, s -> false);
    }

    private Packs packs(String version, Predicate<String> validState, Predicate<String> hasBlockEntity) {
        Packs p = new Packs(log, data, settings(), fake, version, validState, hasBlockEntity, clock::get);
        opened.add(p);
        return p;
    }

    private static Packs.Choice human() {
        return new Packs.Choice("Human", "default");
    }

    /** Asks until the table is there (the first asks say to try again). */
    private Table await(Packs packs) {
        return await(packs, t -> true);
    }

    private Table await(Packs packs, Predicate<Table> wanted) {
        long end = System.nanoTime() + TimeUnit.SECONDS.toNanos(30);
        String last = "";
        while (System.nanoTime() < end) {
            try {
                Table t = packs.table(human(), false);
                if (wanted.test(t)) {
                    return t;
                }
            } catch (IllegalStateException e) {
                last = e.getMessage();
            }
            sleep(10);
        }
        return fail("no table after 30 s; " + last + "; " + packs.status() + "; " + warnings);
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    private static List<String> names(Table table) {
        return table.materials.stream().map(m -> m.name).toList();
    }

    private long count(List<String> lines, String start) {
        synchronized (lines) {
            return lines.stream().filter(l -> l.startsWith(start)).count();
        }
    }

    /** Overwrites a file with garbage of the same size and gives it back its date. */
    private static void spoil(Path file) throws IOException {
        FileTime time = Files.getLastModifiedTime(file);
        Files.write(file, new byte[(int) Files.size(file)]);
        Files.setLastModifiedTime(file, time);
    }

    // ---------------------------------------------------------------------------------------------------------

    @Test
    void noThreadStartsUntilItIsNeeded() {
        long end = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (workerThreads() > 0 && System.nanoTime() < end) {
            sleep(20); // one of an earlier test, still ending
        }
        Packs packs = packs();
        packs.names();
        packs.status();
        packs.choose(null, null, null);
        assertEquals(0, workerThreads(), "after the constructor and calls that read nothing");
        assertThrows(IllegalStateException.class, () -> packs.table(human(), false));
        await(packs);
        assertEquals(1, workerThreads(), "started by the first table");
    }

    private static long workerThreads() {
        return Thread.getAllStackTraces().keySet().stream().filter(t -> t.getName().equals(WORKER) && t.isAlive()).count();
    }

    @Test
    void aPackIsBuiltOnTheWorkerThreadNotTheCaller() {
        Packs packs = packs();
        await(packs);
        String caller = Thread.currentThread().getName();
        assertFalse(fake.inventoryThreads.isEmpty());
        assertFalse(fake.sourceThreads.isEmpty());
        assertEquals(Set.of(WORKER), fake.inventoryThreads, "caller was " + caller);
        assertEquals(Set.of(WORKER), fake.sourceThreads, "caller was " + caller);
    }

    @Test
    void theFirstAskSaysToTryAgain() {
        fake.gate = new CountDownLatch(1); // the worker has started but can't get on
        Packs packs = packs();
        IllegalStateException e = assertThrows(IllegalStateException.class, () -> packs.table(human(), false));
        assertTrue(e.getMessage().contains("try again"), e.getMessage());
        assertThrows(IllegalStateException.class, () -> packs.table(human(), true), "still reading");
        fake.gate.countDown();
        Table table = await(packs);
        assertEquals(1, table.materials.size());
        assertEquals(Table.TERRAIN, table.kind3("minecraft:stone").category());
    }

    @Test
    void aSavedTableLoadsWithoutOpeningTheZipOrTheJar() throws IOException {
        Packs first = packs();
        List<String> built = names(await(first));
        first.close();
        assertEquals(1, count(infos, "Pack Human:"));
        assertTrue(Files.isRegularFile(data.resolve("tables").resolve("Human.json")));
        assertTrue(Files.isRegularFile(data.resolve("tables").resolve("Human-report.txt")));
        assertTrue(Files.isRegularFile(data.resolve("tables").resolve("Human.fingerprint")));
        spoil(zip);
        spoil(jar); // opening either now would fail

        infos.clear();
        warnings.clear();
        Packs second = packs();
        Table table = await(second);

        assertEquals(built, names(table));
        assertEquals(Table.TERRAIN, table.kind3("minecraft:stone").category());
        assertTrue(infos.stream().anyMatch(l -> l.startsWith("Pack Human: loaded the saved table (")), infos.toString());
        assertEquals(1, count(infos, "Pack Human:"), infos.toString());
        assertTrue(warnings.isEmpty(), warnings.toString());
        assertEquals(built, names(second.table(human(), true)), "the blocks-all table too");
    }

    @Test
    void prewarmLoadsTheSavedTableOfTheDefaultPack() {
        Packs first = packs();
        await(first);
        first.close();
        infos.clear();

        Packs second = packs();
        second.prewarm();
        long end = System.nanoTime() + TimeUnit.SECONDS.toNanos(30);
        while (count(infos, "Pack Human: loaded") == 0 && System.nanoTime() < end) {
            sleep(10);
        }
        assertEquals(1, count(infos, "Pack Human: loaded the saved table"), infos.toString());
        assertEquals(1, second.table(human(), false).materials.size());
    }

    @Test
    void aDamagedSavedTableBuildsAgain() throws IOException {
        Packs first = packs();
        await(first);
        first.close();
        Files.writeString(data.resolve("tables").resolve("Human.json"), "{\"materials\": [", StandardCharsets.UTF_8);
        infos.clear();

        Table table = await(packs());

        assertEquals(1, table.materials.size());
        assertEquals(1, count(infos, "Pack Human: 1 terrain materials"), infos.toString());
        assertEquals(0, count(infos, "Pack Human: loaded"), infos.toString());
        assertTrue(warnings.stream().anyMatch(w -> w.contains("saved table can't be used")), warnings.toString());
    }

    @Test
    void aSavedTableOfTheWrongShapeBuildsAgain() throws IOException {
        Packs first = packs();
        await(first);
        first.close();
        Files.writeString(data.resolve("tables").resolve("Human.json"), "{\"name\": \"x\"}", StandardCharsets.UTF_8);
        infos.clear();

        assertEquals(1, await(packs()).materials.size());
        assertEquals(1, count(infos, "Pack Human: 1 terrain materials"), infos.toString());
    }

    @Test
    void aDifferentServerVersionBuildsAgain() {
        Packs first = packs();
        await(first);
        first.close();
        infos.clear();

        Table table = await(packs("26.3", s -> true, s -> false));

        assertEquals(1, table.materials.size());
        assertEquals(1, count(infos, "Pack Human: 1 terrain materials"), infos.toString());
        assertEquals(0, count(infos, "Pack Human: loaded"), infos.toString());
    }

    @Test
    void aChangedClientJarBuildsAgain() throws IOException {
        Packs first = packs();
        await(first);
        first.close();
        Files.setLastModifiedTime(jar, FileTime.fromMillis(Files.getLastModifiedTime(jar).toMillis() + 60_000));
        infos.clear();

        await(packs());

        assertEquals(1, count(infos, "Pack Human: 1 terrain materials"), infos.toString());
    }

    @Test
    void aChangedInventoryBuildsAgainAndKeepsTheOldTableMeanwhile() throws Exception {
        Packs packs = packs();
        Table old = await(packs);
        assertEquals(1, old.materials.size());

        addBricks();
        fake.entered = new CountDownLatch(1);
        fake.gate = new CountDownLatch(1);
        clock.addAndGet(6_000); // past the pause between change checks
        long end = System.nanoTime() + TimeUnit.SECONDS.toNanos(30);
        while (!fake.entered.await(10, TimeUnit.MILLISECONDS) && System.nanoTime() < end) {
            assertSame(old, packs.table(human(), false), "the check hasn't started");
        }
        assertEquals(0, fake.entered.getCount(), "the worker reached the inventory");

        assertSame(old, packs.table(human(), false), "the old table stays while the new one is made");
        fake.gate.countDown();
        fake.gate = null;
        Table rebuilt = await(packs, t -> t != old);

        assertEquals(2, rebuilt.materials.size());
        assertEquals(1, old.materials.size());
    }

    @Test
    void changesAreLookedForAtMostEveryFiveSeconds() throws Exception {
        Packs packs = packs();
        Table old = await(packs);
        sleep(100); // the first check ends
        addBricks();

        for (int i = 0; i < 5; i++) {
            assertSame(old, packs.table(human(), false), "no check inside the pause");
            sleep(50);
        }
        clock.addAndGet(5_001);
        Table rebuilt = await(packs, t -> t != old);
        assertEquals(2, rebuilt.materials.size());
    }

    @Test
    void aFailedBuildKeepsTheOldTable() throws Exception {
        Packs packs = packs();
        Table old = await(packs);
        sleep(100);
        Files.write(zip, new byte[] {1, 2, 3}); // not a zip

        clock.addAndGet(6_000);
        long end = System.nanoTime() + TimeUnit.SECONDS.toNanos(30);
        while (count(warnings, "Pack Human:") == 0 && System.nanoTime() < end) {
            assertSame(old, packs.table(human(), false));
            sleep(10);
        }

        assertEquals(1, count(warnings, "Pack Human:"), warnings.toString());
        assertSame(old, packs.table(human(), false));
        assertTrue(packs.status().get(0).contains("still using the previous table"), packs.status().toString());
        // not tried again every few seconds
        clock.addAndGet(6_000);
        for (int i = 0; i < 6; i++) {
            assertSame(old, packs.table(human(), false));
            sleep(50);
        }
        assertEquals(1, count(warnings, "Pack Human:"), warnings.toString());
    }

    @Test
    void errorsInABuildAreCaught() {
        AtomicBoolean explode = new AtomicBoolean(true);
        Packs packs = packs(MC, s -> {
            if (explode.get()) {
                throw new OutOfMemoryError("test");
            }
            return true;
        }, s -> false);
        assertThrows(IllegalStateException.class, () -> packs.table(human(), false));
        long end = System.nanoTime() + TimeUnit.SECONDS.toNanos(30);
        while (!packs.status().get(0).contains("failed") && System.nanoTime() < end) {
            sleep(10);
        }
        assertTrue(packs.status().get(0).contains("failed"), packs.status().toString());
        assertTrue(packs.status().get(0).contains("test"), packs.status().toString());
        IllegalStateException e = assertThrows(IllegalStateException.class, () -> packs.table(human(), false));
        assertTrue(e.getMessage().contains("can't be used"), e.getMessage());

        explode.set(false);
        sleep(100);
        packs.rebuild("Human");

        assertEquals(1, await(packs).materials.size(), "the worker thread survived the error");
        assertTrue(packs.status().get(0).contains("ready"), packs.status().toString());
    }

    // The worker is held up at the inventory, so every ask comes while the first check is still under way: only one
    // check may have been handed over, however many threads ask (a single worker would hide a second build, so the
    // number of checks is what is counted).
    @Test
    void twoThreadsAskingAtOnceBuildOnce() throws Exception {
        fake.gate = new CountDownLatch(1);
        fake.entered = new CountDownLatch(1);
        Packs packs = packs();
        int askers = 8;
        CountDownLatch start = new CountDownLatch(1);
        List<Thread> threads = new ArrayList<>();
        for (int i = 0; i < askers; i++) {
            Thread t = new Thread(() -> {
                try {
                    start.await();
                    packs.table(human(), false);
                } catch (IllegalStateException | InterruptedException e) {
                    // the first ask: try again
                }
            });
            t.start();
            threads.add(t);
        }
        start.countDown();
        for (Thread t : threads) {
            t.join(60_000);
        }
        assertTrue(fake.entered.await(30, TimeUnit.SECONDS), "the worker started its check");
        fake.gate.countDown();
        Table table = await(packs);
        sleep(300); // a second check, if one was handed over, would run now
        assertSame(table, await(packs));
        assertEquals(1, fake.sourceCalls.get(), "one check for eight asks");
        assertEquals(1, count(infos, "Pack Human:"), infos.toString());
    }

    @Test
    void validStateAndHasBlockEntityReachTheBuilder() {
        Set<String> valid = ConcurrentHashMap.newKeySet(), entity = ConcurrentHashMap.newKeySet();
        Table all = await(packs(MC, s -> {
            valid.add(s);
            return true;
        }, s -> {
            entity.add(s);
            return false;
        }));
        assertEquals(1, all.materials.size());
        assertTrue(valid.stream().anyMatch(s -> s.startsWith("minecraft:stone")), valid.toString());
        assertTrue(entity.stream().anyMatch(s -> s.startsWith("minecraft:stone")), entity.toString());

        // a server that doesn't know stone, or one that says it has a block entity, gets no material from it
        setUpAnotherDataFolder();
        assertEquals(0, await(packs(MC, s -> !s.startsWith("minecraft:stone"), s -> false)).materials.size());
        setUpAnotherDataFolder();
        assertEquals(0, await(packs(MC, s -> true, s -> s.startsWith("minecraft:stone"))).materials.size());
    }

    /** A new data folder, so no saved table of an earlier Packs is used. */
    private void setUpAnotherDataFolder() {
        try {
            Path moved = dir.resolve("data" + opened.size());
            Files.createDirectories(moved.resolve("packs"));
            Files.copy(zip, moved.resolve("packs").resolve("Human.zip"));
            data = moved;
            zip = moved.resolve("packs").resolve("Human.zip");
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    void theChoiceFollowsNamedRegionYourPackDefault() {
        Packs packs = packs();
        Player player = (Player) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[] {Player.class},
                (proxy, method, args) -> method.getName().equals("hashCode") ? 1 : method.getName().equals("equals") ? proxy == args[0] : null);
        Location somewhere = new Location(null, 1, 2, 3);
        fake.region = "Elven";
        fake.current = "Dwarf";

        assertEquals(new Packs.Choice("Orc", "set"), packs.choose(player, somewhere, "Orc"));
        assertEquals(new Packs.Choice("Elven", "region"), packs.choose(player, somewhere, null));
        assertEquals("Elven", packs.regionPack(somewhere));
        int calls = fake.regionCalls.get();
        assertEquals(new Packs.Choice("Dwarf", "your pack"), packs.choose(player, null, null), "a null location looks up no region");
        assertEquals(calls, fake.regionCalls.get());
        fake.region = null;
        assertEquals(new Packs.Choice("Dwarf", "your pack"), packs.choose(player, somewhere, null));
        fake.current = null;
        assertEquals(new Packs.Choice("Human", "default"), packs.choose(player, somewhere, null));
        assertEquals(new Packs.Choice("Human", "default"), packs.choose(null, null, null));
        assertEquals("Human", packs.defaultPack());
    }

    @Test
    void anUnknownPackSaysSo() {
        Packs packs = packs();
        Packs.Choice choice = new Packs.Choice("Nobody", "set");
        assertThrows(IllegalStateException.class, () -> packs.table(choice, false));
        long end = System.nanoTime() + TimeUnit.SECONDS.toNanos(30);
        String message = "";
        while (!message.contains("can't be used") && System.nanoTime() < end) {
            sleep(10);
            try {
                packs.table(choice, false);
            } catch (IllegalStateException e) {
                message = e.getMessage();
            }
        }
        assertTrue(message.contains("no such pack"), message);
    }
}
