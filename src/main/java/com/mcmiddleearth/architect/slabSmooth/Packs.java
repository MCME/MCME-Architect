package com.mcmiddleearth.architect.slabSmooth;

import com.google.gson.GsonBuilder;
import com.mcmiddleearth.architect.slabSmooth.core.Inventory;
import com.mcmiddleearth.architect.slabSmooth.core.Models;
import com.mcmiddleearth.architect.slabSmooth.core.Resources;
import com.mcmiddleearth.architect.slabSmooth.core.Table;
import com.mcmiddleearth.architect.slabSmooth.core.TableBuilder;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
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
import java.util.logging.Logger;
import java.util.stream.Stream;

/**
 * The material table of each resource pack, built from the pack's block inventory and its models, and built again
 * when either changes. Packs, inventories and RP regions come from MCME-Architect (or the "extra" packs in
 * config.yml); without either, every place uses the hand-made table in table.json.
 *
 * <p>Change checks are cheap (file sizes and dates, at most every few seconds per pack) and run when someone
 * smooths. Building runs on one background thread: download the pack zip if it is new, read the inventory, and
 * work out every piece from the models. Until a rebuilt table is ready the previous one stays in use.
 */
final class Packs {

    private static final long CHECK_EVERY_MS = 5_000, RETRY_AFTER_ERROR_MS = 300_000;
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    static final class Pack {
        final String name;
        volatile Table table, tableAll; // terrain only; also the other blocks with pieces (blocks all)
        volatile String fingerprint = "";
        volatile long checkedAt, failedAt;
        volatile boolean building;
        volatile String status = "not read yet";
        volatile String error;

        Pack(String name) {
            this.name = name;
        }
    }

    /** A pack choice and why it was chosen ("region", "your pack", "set", "default"). */
    record Choice(String pack, String why) {
    }

    private record Source(Path inventory, String zip, String sha1, String problem) {
    }

    private final Logger log;
    private final Path dataDir, cacheDir, tablesDir;
    private final Downloads downloads;
    private final ExecutorService worker = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "SlabSmooth packs");
        t.setDaemon(true);
        return t;
    });
    private final Map<String, Pack> packs = new ConcurrentHashMap<>();
    private final Table builtIn;
    private final Architect architect;

    // config.yml, packs section
    private final String defaultPack;
    private final boolean download;
    private final List<String> terrainCategories, plantCategories;
    private final double lookTolerance;
    private final ConfigurationSection overrides, extra;
    private final String clientJar;

    Packs(SlabSmoothPlugin plugin, Table builtIn) {
        this.log = plugin.getLogger();
        this.dataDir = plugin.getDataPath();
        this.cacheDir = dataDir.resolve("cache");
        this.tablesDir = dataDir.resolve("tables");
        this.downloads = new Downloads(log);
        this.builtIn = builtIn;
        var config = plugin.getConfig();
        this.defaultPack = config.getString("packs.default", "Human");
        this.download = config.getBoolean("packs.download", true);
        this.terrainCategories = config.getStringList("packs.terrain-categories").isEmpty() ? List.of("Terra")
                : config.getStringList("packs.terrain-categories");
        this.plantCategories = config.isList("packs.plant-categories") ? config.getStringList("packs.plant-categories")
                : List.of("Plant", "Crop");
        this.lookTolerance = config.getDouble("packs.look-tolerance", 10);
        this.overrides = config.getConfigurationSection("packs.overrides");
        this.extra = config.getConfigurationSection("packs.extra");
        this.clientJar = config.getString("vanilla.client-jar", "");
        this.architect = Architect.find(plugin.getServer(), log);
    }

    /** True if tables come from packs; false if every place uses the hand-made table. */
    boolean dynamic() {
        return architect != null || (extra != null && !extra.getKeys(false).isEmpty());
    }

    String describe() {
        if (!dynamic()) {
            return "hand-made table \"" + builtIn.name + "\" (" + builtIn.materials.size() + " materials; no MCME-Architect or extra packs)";
        }
        return (architect != null ? "MCME-Architect's packs" : "extra packs") + ", default " + defaultPack;
    }

    /** Every pack SlabSmooth knows of. */
    List<String> names() {
        Set<String> out = new LinkedHashSet<>();
        if (architect != null) {
            out.addAll(architect.packs());
        }
        if (extra != null) {
            out.addAll(extra.getKeys(false));
        }
        out.addAll(packs.keySet());
        return new ArrayList<>(out);
    }

    /** The pack for an edit at location: the player's choice, the RP region there, the player's pack, the default. */
    Choice choose(Player player, Location location, String set) {
        if (!dynamic()) {
            return new Choice(builtIn.name, "built in");
        }
        if (set != null) {
            return new Choice(set, "set");
        }
        String region = architect != null && location != null ? architect.regionPack(location) : null;
        if (region != null) {
            return new Choice(region, "region");
        }
        String current = architect != null && player != null ? architect.currentPack(player) : null;
        if (current != null) {
            return new Choice(current, "your pack");
        }
        return new Choice(defaultPack, "default");
    }

    String defaultPack() {
        return defaultPack;
    }

    /**
     * The table to smooth with for a pack: the current one, or the one from before its latest change while that is
     * rebuilt. allBlocks: with the other blocks that have pieces of their own, not just terrain. Main thread. Throws
     * with a message for the player when there is none yet.
     */
    Table table(Choice choice, boolean allBlocks) {
        if (!dynamic()) {
            return builtIn;
        }
        Pack pack = packs.computeIfAbsent(choice.pack(), Pack::new);
        refresh(pack, false);
        Table t = allBlocks ? pack.tableAll : pack.table;
        if (t != null) {
            return t;
        }
        if (pack.building) {
            throw new IllegalStateException("Reading the " + pack.name + " pack (" + choice.why() + ") for the first time; try again in a few seconds.");
        }
        throw new IllegalStateException("The " + pack.name + " pack (" + choice.why() + ") can't be used: " + pack.error);
    }

    /** Status lines for /slabsmooth packs. */
    List<String> status() {
        List<String> out = new ArrayList<>();
        for (String name : names()) {
            Pack p = packs.get(name);
            out.add(name + ": " + (p == null ? "not read yet (read on first use)" : p.building ? "reading... " + p.status : p.status)
                    + (p != null && p.error != null && !p.building ? " - " + p.error : ""));
        }
        return out;
    }

    /** Rebuilds a pack's table now, even if nothing changed. */
    void rebuild(String name) {
        Pack pack = packs.computeIfAbsent(name, Pack::new);
        refresh(pack, true);
    }

    void prewarm() {
        if (dynamic()) {
            refresh(packs.computeIfAbsent(defaultPack, Pack::new), false);
        }
    }

    void close() {
        worker.shutdownNow();
        downloads.close();
    }

    // ---------------------------------------------------------------------------------------------------------

    private void refresh(Pack pack, boolean force) {
        long now = System.currentTimeMillis();
        if (pack.building || (!force && now - pack.checkedAt < CHECK_EVERY_MS)) {
            return;
        }
        pack.checkedAt = now;
        Source source = source(pack.name);
        if (source.problem() != null) {
            pack.error = source.problem();
            if (pack.table == null) {
                pack.status = "can't be used";
            }
            return;
        }
        String fingerprint = fingerprint(pack.name, source);
        boolean changed = !fingerprint.equals(pack.fingerprint);
        boolean retry = pack.error != null && now - pack.failedAt > RETRY_AFTER_ERROR_MS;
        if (!force && !changed && !retry) {
            return;
        }
        pack.building = true;
        boolean first = pack.table == null;
        worker.submit(() -> build(pack, source, fingerprint, first ? "first use" : force ? "asked for" : changed ? "inventory or pack changed" : "retry"));
    }

    /** Where a pack's inventory and zip are, or why it has none. */
    private Source source(String name) {
        ConfigurationSection x = extra == null ? null : extra.getConfigurationSection(name);
        Path inventory = null;
        if (x != null && x.isString("inventory")) {
            inventory = Path.of(x.getString("inventory"));
        } else if (architect != null && architect.packs().contains(name)) {
            inventory = architect.inventory(name);
        }
        if (inventory == null) {
            return new Source(null, null, null, "no such pack (MCME-Architect's packs: " + (architect == null ? "none" : architect.packs()) + ")");
        }
        if (!Files.isDirectory(inventory)) {
            return new Source(null, null, null, "no inventory folder " + inventory);
        }
        Path local = dataDir.resolve("packs").resolve(name + ".zip");
        if (Files.isRegularFile(local)) {
            return new Source(inventory, local.toString(), "", null);
        }
        if (x != null && x.isString("pack")) {
            return new Source(inventory, x.getString("pack"), x.getString("sha1", ""), null);
        }
        Architect.Source a = architect != null ? architect.source(name) : null;
        if (a != null && download) {
            return new Source(inventory, a.url(), a.sha1(), null);
        }
        return new Source(null, null, null, a == null ? "no download URL in MCME-Architect's config"
                : "downloads are off (packs.download); put the pack zip at " + local);
    }

    private String fingerprint(String name, Source source) {
        StringBuilder sb = new StringBuilder(source.zip()).append('|').append(source.sha1()).append('|');
        if (!isUrl(source.zip())) {
            stamp(Path.of(source.zip()), sb);
        }
        try (Stream<Path> files = Files.list(source.inventory())) {
            files.filter(p -> p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".yml")).sorted().forEach(p -> stamp(p, sb));
        } catch (IOException e) {
            sb.append("unreadable");
        }
        TableBuilder.Settings s = settings(name);
        sb.append(s.terrainCategories).append(s.decorCategories).append(s.colorTolerance).append(s.exclude).append(s.pairs)
                .append(new TreeSet<>(s.terrain));
        return sb.toString();
    }

    private static void stamp(Path p, StringBuilder sb) {
        try {
            BasicFileAttributes a = Files.readAttributes(p, BasicFileAttributes.class);
            sb.append(p.getFileName()).append(':').append(a.size()).append(':').append(a.lastModifiedTime().toMillis()).append(';');
        } catch (IOException e) {
            sb.append(p.getFileName()).append(":missing;");
        }
    }

    private TableBuilder.Settings settings(String name) {
        TableBuilder.Settings s = new TableBuilder.Settings();
        s.terrainCategories = terrainCategories;
        s.decorCategories = plantCategories;
        s.colorTolerance = lookTolerance;
        ConfigurationSection o = overrides == null ? null : overrides.getConfigurationSection(name);
        if (o != null) {
            s.exclude = Set.copyOf(o.getStringList("exclude"));
            s.terrain = Set.copyOf(o.getStringList("terrain"));
            ConfigurationSection pairs = o.getConfigurationSection("pairs");
            if (pairs != null) {
                Map<String, List<String>> map = new TreeMap<>();
                for (String key : pairs.getKeys(false)) {
                    map.put(key, pairs.isList(key) ? pairs.getStringList(key) : List.of(pairs.getString(key, "")));
                }
                s.pairs = map;
            }
        }
        return s;
    }

    /** Background thread. */
    private void build(Pack pack, Source source, String fingerprint, String why) {
        long start = System.currentTimeMillis();
        try {
            pack.status = "getting the pack";
            Path zip = packZip(pack.name, source);
            pack.status = "getting the vanilla models";
            Path jar = clientJar();
            pack.status = "reading the inventory and models";
            List<String> problems = new ArrayList<>();
            List<Inventory.Entry> entries = Inventory.read(source.inventory(), problems::add);
            TableBuilder.Result result;
            try (Resources resources = Resources.layered(List.of(Resources.zip(zip), Resources.zip(jar)))) {
                result = TableBuilder.build(pack.name, entries, new Models(resources), settings(pack.name));
            }
            Table table = new Table(result.table()), tableAll = new Table(result.table(), true);
            write(pack.name, result, problems, why);
            pack.table = table;
            pack.tableAll = tableAll;
            pack.fingerprint = fingerprint;
            pack.error = null;
            pack.status = "ready, " + result.materials() + " terrain materials, " + result.others() + " more with blocks all (read "
                    + LocalDateTime.now().format(TIME) + ", " + why + ")";
            log.info("Pack " + pack.name + ": " + result.materials() + " terrain materials, and " + result.others()
                    + " other blocks with pieces, from " + entries.size() + " inventory entries (" + why + ", "
                    + (System.currentTimeMillis() - start) + " ms). Details: tables/" + fileName(pack.name) + "-report.txt");
            problems.forEach(p -> log.warning("Pack " + pack.name + " inventory: " + p));
        } catch (Exception e) {
            pack.error = e.getMessage() == null ? e.toString() : e.getMessage();
            pack.failedAt = System.currentTimeMillis();
            pack.fingerprint = fingerprint;
            pack.status = pack.table == null ? "failed" : "failed to update; still using the previous table";
            log.warning("Pack " + pack.name + ": " + pack.error);
        } finally {
            pack.building = false;
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
            try (Stream<Path> old = Files.list(dir)) { // older versions of this pack
                for (Path p : old.filter(p -> p.getFileName().toString().startsWith(fileName(name) + "-") && !p.equals(target)).toList()) {
                    Files.deleteIfExists(p);
                }
            }
        }
        return target;
    }

    private Path clientJar() throws IOException {
        if (clientJar != null && !clientJar.isBlank()) {
            Path p = Path.of(clientJar);
            if (!Files.isRegularFile(p)) {
                throw new IOException("vanilla.client-jar " + p + " doesn't exist");
            }
            return p;
        }
        if (!download) {
            throw new IOException("downloads are off (packs.download); set vanilla.client-jar to the " + Bukkit.getMinecraftVersion() + " client jar");
        }
        return downloads.clientJar(Bukkit.getMinecraftVersion(), cacheDir);
    }

    private void write(String name, TableBuilder.Result result, List<String> problems, String why) throws IOException {
        Files.createDirectories(tablesDir);
        Files.writeString(tablesDir.resolve(fileName(name) + ".json"),
                new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create().toJson(result.table()), StandardCharsets.UTF_8);
        List<String> lines = new ArrayList<>();
        lines.add("SlabSmooth table for pack " + name + ", built " + LocalDateTime.now().format(TIME) + " (" + why + ").");
        lines.add("Each terrain material: its full blocks (inventory names), and where its pieces come from (same texture,");
        lines.add("same name, or a similar look). Change it with packs.overrides in config.yml. Terrain is what the");
        lines.add("terrain categories place, plus the blocks listed under packs.overrides." + name + ".terrain.");
        lines.add("");
        lines.addAll(result.report());
        problems.forEach(p -> lines.add("inventory problem: " + p));
        Files.write(tablesDir.resolve(fileName(name) + "-report.txt"), lines, StandardCharsets.UTF_8);
    }

    /** A pack without a known checksum is cached under its URL (a new release has a new URL). */
    private static String urlHash(String url) {
        try {
            return HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-1").digest(url.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static boolean isUrl(String s) {
        return s.startsWith("http://") || s.startsWith("https://");
    }

    private static String fileName(String pack) {
        return pack.replaceAll("[^A-Za-z0-9._-]", "_");
    }
}
