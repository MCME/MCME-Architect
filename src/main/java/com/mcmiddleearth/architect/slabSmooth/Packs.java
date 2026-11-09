package com.mcmiddleearth.architect.slabSmooth;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mcmiddleearth.architect.slabSmooth.core.Inventory;
import com.mcmiddleearth.architect.slabSmooth.core.Models;
import com.mcmiddleearth.architect.slabSmooth.core.Resources;
import com.mcmiddleearth.architect.slabSmooth.core.Table;
import com.mcmiddleearth.architect.slabSmooth.core.TableBuilder;
import com.sk89q.worldedit.math.BlockVector3;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.LongSupplier;
import java.util.function.Predicate;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * The material table of each resource pack, built from the pack's block inventory and its models, and built again
 * when either changes. Packs and inventories come from {@link PackSources} (MCME-Architect).
 *
 * <p>Everything here is safe from any thread, and no call touches a file or the network: the change check (the
 * inventory files' contents, the zip's and the client jar's sizes and dates, at most every few seconds per pack), the
 * download and the build all run on one background thread.
 * A table built before is saved under tables/, with the fingerprint of what it was built from; after a restart a pack
 * whose fingerprint is unchanged loads that table and opens neither the pack's zip nor the vanilla jar. Until a rebuilt
 * table is ready the previous one stays in use.
 */
final class Packs implements TableSource, Closeable {

    private static final long CHECK_EVERY_MS = 5_000, RETRY_AFTER_ERROR_MS = 300_000;
    /**
     * Part of every fingerprint: raise it when the saved table's contents or meaning change, or the fingerprint's
     * layout, so a fingerprint saved before never matches by chance. 2: the inventory files by their content.
     */
    private static final int TABLE_FORMAT = 2;
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    static final class Pack {
        final String name;
        volatile Table table, tableAll; // terrain only; also the other blocks with pieces (blocks all)
        volatile String fingerprint = "";
        volatile long checkedAt = Long.MIN_VALUE / 2, failedAt;
        /** Set when a check (and maybe a build) is handed to the worker; cleared when it ends. */
        final AtomicBoolean building = new AtomicBoolean();
        /** True while a table is being read or built, not during a plain change check. */
        volatile boolean reading;
        /** The saved table has been looked at once; it is not tried again. */
        volatile boolean savedTried;
        volatile String status = "not read yet";
        volatile String error;

        Pack(String name) {
            this.name = name;
        }
    }

    /** A pack choice and why it was chosen ("region", "your pack", "set", "default"). */
    record Choice(String pack, String why) {
    }

    /**
     * What a saved table depends on besides its pack: the server's Minecraft version (the client jar is that version's
     * too) and Architect's own, so a table built by an earlier Architect is built again, with any fix to how tables are
     * built.
     */
    record Versions(String minecraft, String architect) {
    }

    private record Source(Path inventory, String zip, String sha1, String problem) {
    }

    private final Logger log;
    private final Path dataDir, cacheDir, tablesDir;
    private final Downloads downloads;
    /** A plain single-thread executor starts its thread with the first task. */
    private final ExecutorService worker = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "Architect SlabSmooth packs");
        t.setDaemon(true);
        return t;
    });
    private final Map<String, Pack> packs = new ConcurrentHashMap<>();
    private final SlabSmoothSettings settings;
    private final PackSources sources;
    private final Versions versions;
    private final Predicate<String> validState, hasBlockEntity;
    private final LongSupplier clock;

    Packs(Logger log, Path dataDir, SlabSmoothSettings settings, PackSources sources, Versions versions,
          Predicate<String> validState, Predicate<String> hasBlockEntity) {
        this(log, dataDir, settings, sources, versions, validState, hasBlockEntity, System::currentTimeMillis);
    }

    /** With a clock in milliseconds, so the change check's pause can be tested. */
    Packs(Logger log, Path dataDir, SlabSmoothSettings settings, PackSources sources, Versions versions,
          Predicate<String> validState, Predicate<String> hasBlockEntity, LongSupplier clock) {
        this.log = log;
        this.dataDir = dataDir;
        this.cacheDir = dataDir.resolve("cache");
        this.tablesDir = dataDir.resolve("tables");
        this.downloads = new Downloads(log);
        this.settings = settings;
        this.sources = sources;
        this.versions = versions;
        this.validState = validState;
        this.hasBlockEntity = hasBlockEntity;
        this.clock = clock;
    }

    /** Every pack SlabSmooth knows of. */
    List<String> names() {
        Set<String> out = new LinkedHashSet<>(sources.packs());
        out.addAll(packs.keySet());
        return new ArrayList<>(out);
    }

    /** The pack for an edit at location: the player's choice, the RP region there, the player's pack, the default. */
    @Override
    public Choice choose(Player player, Location location, String named) {
        if (named != null) {
            return new Choice(named, "set");
        }
        String region = location != null ? sources.regionPack(location) : null;
        if (region != null) {
            return new Choice(region, "region");
        }
        String current = player != null ? sources.currentPack(player) : null;
        if (current != null) {
            return new Choice(current, "your pack");
        }
        return new Choice(settings.defaultPack(), "default");
    }

    @Override
    public String regionPack(Location location) {
        return sources.regionPack(location);
    }

    @Override
    public Set<String> regionPacksIn(World world, BlockVector3 min, BlockVector3 max) {
        return sources.regionPacksIn(world, min, max);
    }

    String defaultPack() {
        return settings.defaultPack();
    }

    /**
     * The table to smooth with for a pack: the current one, or the one from before its latest change while that is
     * rebuilt. allBlocks: with the other blocks that have pieces of their own, not just terrain. Throws with a message
     * for the player when there is none yet (the first ask only starts reading the pack).
     */
    @Override
    public Table table(Choice choice, boolean allBlocks) {
        Pack pack = packs.computeIfAbsent(choice.pack(), Pack::new);
        refresh(pack, false);
        Table t = allBlocks ? pack.tableAll : pack.table;
        if (t != null) {
            return t;
        }
        if (pack.building.get()) {
            throw new Refusal("Reading the " + pack.name + " pack (" + choice.why() + ") for the first time; try again in a few seconds.");
        }
        t = allBlocks ? pack.tableAll : pack.table; // a build that ended just now has published its table before it cleared the flag
        if (t != null) {
            return t;
        }
        throw new Refusal("The " + pack.name + " pack (" + choice.why() + ") can't be used: " + pack.error);
    }

    /** Status lines for /slabsmooth packs. */
    List<String> status() {
        List<String> out = new ArrayList<>();
        for (String name : names()) {
            Pack p = packs.get(name);
            boolean reading = p != null && p.reading;
            out.add(name + ": " + (p == null ? "not read yet (read on first use)" : reading ? "reading... " + p.status : p.status)
                    + (p != null && p.error != null && !reading ? " - " + p.error : ""));
        }
        return out;
    }

    /**
     * Rebuilds a pack's table now, even if nothing changed.
     *
     * @return false if nothing started: a check of that pack is under way already (it builds only if something
     *         changed), or the packs are closed
     */
    boolean rebuild(String name) {
        return refresh(packs.computeIfAbsent(name, Pack::new), true);
    }

    /**
     * Whether a check of that pack, and maybe a build, is under way: handed to the worker and not ended yet. A table is
     * published a moment before its check ends, so tests wait for this before they start another.
     */
    boolean checking(String name) {
        Pack pack = packs.get(name);
        return pack != null && pack.building.get();
    }

    /** Starts reading the default pack (from its saved table if that is still good). */
    void prewarm() {
        refresh(packs.computeIfAbsent(settings.defaultPack(), Pack::new), false);
    }

    @Override
    public void close() {
        worker.shutdownNow();
        downloads.close();
    }

    // ---------------------------------------------------------------------------------------------------------

    /**
     * Hands the change check (and a build, if one is due) to the worker; does nothing else. Any thread.
     *
     * @return whether a check was handed over
     */
    private boolean refresh(Pack pack, boolean force) {
        long now = clock.getAsLong();
        if (!force && now - pack.checkedAt < CHECK_EVERY_MS) {
            return false;
        }
        if (!pack.building.compareAndSet(false, true)) {
            return false;
        }
        pack.checkedAt = now;
        try {
            worker.execute(() -> run(pack, force));
            return true;
        } catch (RejectedExecutionException closed) {
            pack.building.set(false);
            return false;
        }
    }

    /** Worker thread. Nothing escapes, errors included: the thread must go on serving other packs. */
    private void run(Pack pack, boolean force) {
        try {
            check(pack, force);
        } catch (Throwable t) {
            fail(pack, t);
        } finally {
            pack.reading = false;
            pack.building.set(false);
        }
    }

    private void check(Pack pack, boolean force) throws IOException {
        long now = clock.getAsLong();
        Source source = source(pack.name);
        if (source.problem() != null) {
            pack.error = source.problem();
            if (pack.table == null) {
                pack.status = "can't be used";
            }
            return;
        }
        String fingerprint = fingerprint(pack.name, source);
        if (pack.table == null && !force && !pack.savedTried) {
            pack.savedTried = true;
            if (loadSaved(pack, fingerprint)) {
                return;
            }
        }
        boolean changed = !fingerprint.equals(pack.fingerprint);
        boolean retry = pack.error != null && now - pack.failedAt > RETRY_AFTER_ERROR_MS;
        if (!force && !changed && !retry) {
            return;
        }
        boolean first = pack.table == null;
        try {
            build(pack, source, fingerprint, first ? "first use" : force ? "asked for" : changed ? "inventory or pack changed" : "retry");
        } catch (Throwable t) {
            pack.fingerprint = fingerprint; // not tried again before the retry time, unless the inventory or pack changes
            throw t;
        }
    }

    /** Where a pack's inventory and zip are, or why it has none. */
    private Source source(String name) {
        PackSources.Source a = sources.source(name);
        List<String> known = sources.packs();
        if (!known.contains(name)) {
            return new Source(null, null, null, "no such pack (MCME-Architect's packs: " + known + ")");
        }
        Path inventory = sources.inventory(name);
        if (!Files.isDirectory(inventory)) {
            return new Source(null, null, null, "no inventory folder " + inventory);
        }
        Path local = dataDir.resolve("packs").resolve(name + ".zip");
        if (Files.isRegularFile(local)) {
            return new Source(inventory, local.toString(), "", null);
        }
        if (a != null && settings.download()) {
            return new Source(inventory, a.url(), a.sha1() == null ? "" : a.sha1(), null);
        }
        return new Source(null, null, null, a == null ? "no download URL in MCME-Architect's config"
                : "downloads are off (slabSmooth.download in config.yml); put the pack zip at " + local);
    }

    /**
     * What a table is built from: the pack's address and file, the inventory files, the settings that shape the table,
     * the server's and Architect's versions, the table format and the configured client jar. Equal fingerprints mean
     * an equal table. The zip and the jar count by their size and date: they are large, and nothing rewrites them. The
     * inventory files count by their content: Architect writes each one again at every start, with a new date.
     */
    private String fingerprint(String name, Source source) {
        StringBuilder sb = new StringBuilder("format ").append(TABLE_FORMAT).append("|versions minecraft ")
                .append(versions.minecraft()).append(" architect ").append(versions.architect())
                .append('|').append(source.zip()).append('|').append(source.sha1()).append('|');
        if (!isUrl(source.zip())) {
            stamp(Path.of(source.zip()), sb);
        }
        sb.append('|');
        String jar = settings.clientJar();
        if (jar != null && !jar.isBlank()) {
            stamp(Path.of(jar), sb);
        }
        sb.append('|');
        try (Stream<Path> files = Files.list(source.inventory())) {
            files.filter(p -> p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".yml")).sorted().forEach(p -> hash(p, sb));
        } catch (IOException e) {
            sb.append("unreadable");
        }
        TableBuilder.Settings s = tableSettings(name);
        // sorted, so the same settings give the same text in every run
        sb.append(s.terrainCategories).append(s.decorCategories).append(s.colorTolerance).append(new TreeSet<>(s.exclude))
                .append(new TreeMap<>(s.pairs)).append(new TreeSet<>(s.terrain));
        return sb.toString();
    }

    /** A file by its size and date. */
    private static void stamp(Path p, StringBuilder sb) {
        try {
            BasicFileAttributes a = Files.readAttributes(p, BasicFileAttributes.class);
            sb.append(p.getFileName()).append(':').append(a.size()).append(':').append(a.lastModifiedTime().toMillis()).append(';');
        } catch (IOException e) {
            sb.append(p.getFileName()).append(":missing;");
        }
    }

    /** A file by its content: the SHA-1 of its bytes. */
    private static void hash(Path p, StringBuilder sb) {
        MessageDigest sha1 = sha1();
        try (InputStream in = new DigestInputStream(Files.newInputStream(p), sha1)) {
            in.transferTo(OutputStream.nullOutputStream());
            sb.append(p.getFileName()).append(':').append(HexFormat.of().formatHex(sha1.digest())).append(';');
        } catch (IOException e) {
            sb.append(p.getFileName()).append(":unreadable;");
        }
    }

    private TableBuilder.Settings tableSettings(String name) {
        TableBuilder.Settings s = new TableBuilder.Settings();
        s.terrainCategories = settings.terrainCategories();
        s.decorCategories = settings.plantCategories();
        s.colorTolerance = settings.lookTolerance();
        s.validState = validState;
        s.hasBlockEntity = hasBlockEntity;
        SlabSmoothSettings.PackOverride o = settings.overrides().get(name);
        if (o != null) {
            s.exclude = o.exclude();
            s.terrain = o.terrain();
            s.pairs = o.pairs();
        }
        return s;
    }

    // ---------------------------------------------------------------------------------------------------------

    /** Loads the table saved for this exact fingerprint, if there is one that reads. Worker thread. */
    private boolean loadSaved(Pack pack, String fingerprint) {
        Path json = tablesDir.resolve(fileName(pack.name) + ".json");
        Path saved = tablesDir.resolve(fileName(pack.name) + ".fingerprint");
        if (!Files.isRegularFile(json) || !Files.isRegularFile(saved)) {
            return false;
        }
        long start = clock.getAsLong();
        pack.reading = true;
        pack.status = "loading the saved table";
        try {
            if (!Files.readString(saved, StandardCharsets.UTF_8).equals(fingerprint)) {
                return false;
            }
            JsonObject spec;
            try (Reader r = Files.newBufferedReader(json, StandardCharsets.UTF_8)) {
                spec = JsonParser.parseReader(r).getAsJsonObject();
            }
            Table table = new Table(spec), tableAll = new Table(spec, true);
            publish(pack, table, tableAll, fingerprint);
            pack.status = "ready, " + table.materials.size() + " terrain materials, " + (tableAll.materials.size() - table.materials.size())
                    + " more with blocks all (loaded the saved table)";
            log.info("Pack " + pack.name + ": loaded the saved table (" + (clock.getAsLong() - start) + " ms).");
            return true;
        } catch (IOException | RuntimeException e) {
            log.warning("Pack " + pack.name + ": the saved table can't be used (" + e + "); building it again.");
            return false;
        } finally {
            pack.reading = false;
        }
    }

    private void publish(Pack pack, Table table, Table tableAll, String fingerprint) {
        pack.tableAll = tableAll; // before table, so a pack with a table has both
        pack.table = table;
        pack.fingerprint = fingerprint;
        pack.error = null;
    }

    /** Worker thread. */
    private void build(Pack pack, Source source, String fingerprint, String why) throws IOException {
        long start = clock.getAsLong();
        pack.reading = true;
        pack.status = "getting the pack";
        Path zip = packZip(pack.name, source);
        pack.status = "getting the vanilla models";
        Path jar = clientJar();
        pack.status = "reading the inventory and models";
        List<String> problems = new ArrayList<>();
        List<Inventory.Entry> entries = Inventory.read(source.inventory(), problems::add);
        TableBuilder.Result result;
        try (Resources resources = Resources.layered(List.of(Resources.zip(zip), Resources.zip(jar)))) {
            result = TableBuilder.build(pack.name, entries, new Models(resources), tableSettings(pack.name));
        }
        Table table = new Table(result.table()), tableAll = new Table(result.table(), true);
        write(pack.name, result, problems, why, fingerprint);
        publish(pack, table, tableAll, fingerprint);
        pack.status = "ready, " + result.materials() + " terrain materials, " + result.others() + " more with blocks all (read "
                + LocalDateTime.now().format(TIME) + ", " + why + ")";
        log.info("Pack " + pack.name + ": " + result.materials() + " terrain materials, and " + result.others()
                + " other blocks with pieces, from " + entries.size() + " inventory entries (" + why + ", "
                + (clock.getAsLong() - start) + " ms). Details: tables/" + fileName(pack.name) + "-report.txt");
        problems.forEach(p -> log.warning("Pack " + pack.name + " inventory: " + p));
    }

    /** A build that failed (an error as well as an exception): the previous table, if any, stays in use. */
    private void fail(Pack pack, Throwable t) {
        pack.error = t.getMessage() == null ? t.toString() : t.getMessage();
        pack.failedAt = clock.getAsLong();
        pack.status = pack.table == null ? "failed" : "failed to update; still using the previous table";
        log.warning("Pack " + pack.name + ": " + pack.error);
        if (!(t instanceof Exception)) {
            log.warning("Pack " + pack.name + ": that was " + t.getClass().getName() + ", not an ordinary error.");
        }
    }

    private Path packZip(String name, Source source) throws IOException {
        if (!isUrl(source.zip())) {
            Path p = Path.of(source.zip());
            if (!Files.isRegularFile(p)) {
                throw new IOException("no pack zip at " + p);
            }
            return p;
        }
        String id = source.sha1() != null && !source.sha1().isEmpty() ? source.sha1() : urlHash(source.zip());
        Path dir = cacheDir.resolve("packs");
        Path target = dir.resolve(fileName(name) + "-" + id.substring(0, Math.min(12, id.length())) + ".zip");
        if (!Files.isRegularFile(target)) {
            downloads.fetch(source.zip(), target, source.sha1());
            // older versions of this pack: its name, a dash and 12 hex digits, so not those of a pack whose name starts
            // the same, such as Human-Night's for Human
            Pattern older = Pattern.compile(Pattern.quote(fileName(name)) + "-[0-9a-f]{12}\\.zip");
            try (Stream<Path> old = Files.list(dir)) {
                for (Path p : old.filter(p -> !p.equals(target) && older.matcher(p.getFileName().toString()).matches())
                        .toList()) {
                    Files.deleteIfExists(p);
                }
            }
        }
        return target;
    }

    private Path clientJar() throws IOException {
        String clientJar = settings.clientJar();
        if (clientJar != null && !clientJar.isBlank()) {
            Path p = Path.of(clientJar);
            if (!Files.isRegularFile(p)) {
                throw new IOException("slabSmooth.vanilla.clientJar " + p + " doesn't exist");
            }
            return p;
        }
        if (!settings.download()) {
            throw new IOException("downloads are off (slabSmooth.download in config.yml); set slabSmooth.vanilla.clientJar to the "
                    + versions.minecraft() + " client jar");
        }
        return downloads.clientJar(versions.minecraft(), cacheDir);
    }

    /** The table, its report and (last, so a half-written table is never trusted) the fingerprint it was built from. */
    private void write(String name, TableBuilder.Result result, List<String> problems, String why, String fingerprint) throws IOException {
        Files.createDirectories(tablesDir);
        Files.deleteIfExists(tablesDir.resolve(fileName(name) + ".fingerprint"));
        Files.writeString(tablesDir.resolve(fileName(name) + ".json"),
                new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create().toJson(result.table()), StandardCharsets.UTF_8);
        String overrides = "slabSmooth.overrides." + name;
        List<String> lines = new ArrayList<>();
        lines.add("SlabSmooth table for pack " + name + ", built " + LocalDateTime.now().format(TIME) + " (" + why + ").");
        lines.add("Each terrain material: its full blocks (inventory names), and where its pieces come from (same texture,");
        lines.add("same name, or a similar look). Terrain is what the terrain categories place, plus the blocks");
        lines.add("listed under " + overrides + ".terrain in Architect's config.yml: the pack's terrain overrides.");
        lines.add("To change the table, list the pieces a block always uses under " + overrides + ".pairs,");
        lines.add("or blocks never to use under " + overrides + ".exclude, then run /architect reload and");
        lines.add("/sls packs rebuild " + name + ".");
        lines.add("");
        lines.addAll(result.report());
        problems.forEach(p -> lines.add("inventory problem: " + p));
        Files.write(tablesDir.resolve(fileName(name) + "-report.txt"), lines, StandardCharsets.UTF_8);
        Files.writeString(tablesDir.resolve(fileName(name) + ".fingerprint"), fingerprint, StandardCharsets.UTF_8);
    }

    /** A pack without a known checksum is cached under its URL (a new release has a new URL). */
    private static String urlHash(String url) {
        return HexFormat.of().formatHex(sha1().digest(url.getBytes(StandardCharsets.UTF_8)));
    }

    private static MessageDigest sha1() {
        try {
            return MessageDigest.getInstance("SHA-1");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e); // every Java has SHA-1
        }
    }

    private static boolean isUrl(String s) {
        return s.startsWith("http://") || s.startsWith("https://");
    }

    private static String fileName(String pack) {
        return pack.replaceAll("[^A-Za-z0-9._-]", "_");
    }
}
