package com.mcmiddleearth.architect.biomeTuning;

import org.bukkit.NamespacedKey;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.AccessDeniedException;
import java.nio.file.DirectoryNotEmptyException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.FileSystemException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.NotDirectoryException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.logging.Logger;
import java.util.stream.Stream;

/**
 * The spare biomes: biomes a datapack defines at startup whose ids match biomeTuning.sparePattern, neutral until a
 * builder claims one and gives it a look. A claim is for good, because a spare may already be painted somewhere.
 * {@link #add} writes new spares into the target pack; they exist from the next restart, when Minecraft reads its
 * datapacks again. Claims, labels and new spares are logged with who made them, as saves are; what is refused is not.
 * Main thread only.
 */
public final class SparePool {

    /**
     * The most spares one add writes. Every spare is a biome that each client loads and the server keeps for good, so
     * a slip such as 1000 for 10 must not make hundreds of them.
     */
    public static final int ADD_LIMIT = 100;
    /** How spares are added, for every message that says there is none to take. */
    public static final String HOW_TO_ADD = "An admin can add spares with /biometune spares add <count>; they exist "
            + "after the next restart.";
    /**
     * What a new spare starts as: the climate and colours of plains, and no worldgen (no features, carvers or mobs),
     * so it is safe to paint anywhere.
     */
    static final String NEUTRAL = """
            {
              "attributes": {
                "minecraft:visual/sky_color": "#78a7ff"
              },
              "carvers": [],
              "downfall": 0.4,
              "effects": {
                "water_color": "#3f76e4"
              },
              "features": [],
              "has_precipitation": true,
              "spawn_costs": {},
              "spawners": {},
              "temperature": 0.8
            }
            """;

    /** Any value: asking for one tells whether the game knows a biome. */
    private static final TuningProperty ANY = TuningProperties.all().get(0);

    /**
     * What a claim did: the spare it took, its label as kept, the values it copied (none without a source), and why
     * the source's look could not be copied (null when it was, or without a source).
     */
    public record Claim(NamespacedKey spare, String label, List<BiomeTuningService.Change> copied, String copyFailure) {
    }

    private final SpareIds ids;
    private final String pack;
    private final Supplier<Stream<NamespacedKey>> known;
    private final BiomeTuningService.DatapacksFolder datapacks;
    private final BiomeLabels labels;
    private final BiomeTuningService service;
    private final Supplier<Instant> clock;
    private final Logger logger;

    /**
     * {@code known} gives the biome ids the server knows now; {@code datapacks} is asked when first needed.
     * {@code logger} is the log the service writes its saves to.
     */
    public SparePool(SpareIds ids, String pack, Supplier<Stream<NamespacedKey>> known,
                     BiomeTuningService.DatapacksFolder datapacks, BiomeLabels labels, BiomeTuningService service,
                     Supplier<Instant> clock, Logger logger) {
        this.ids = ids;
        this.pack = pack;
        this.known = known;
        this.datapacks = datapacks;
        this.labels = labels;
        this.service = service;
        this.clock = clock;
        this.logger = logger;
    }

    public BiomeLabels labels() {
        return labels;
    }

    /** The spares the server knows, lowest number first. */
    public List<NamespacedKey> spares() {
        try (Stream<NamespacedKey> keys = known.get()) {
            return keys.filter(ids::matches).sorted(Comparator.comparingInt(ids::number)).toList();
        }
    }

    /**
     * The spares a claim can take, lowest number first: nobody has claimed them, and their file is there and loads. A
     * spare whose file was removed since startup is left out, as the next restart drops it.
     */
    public List<NamespacedKey> free() throws BiomeTuningException {
        List<NamespacedKey> free = new ArrayList<>();
        for (NamespacedKey spare : spares()) {
            if (!labels.isClaimed(spare) && loads(spare)) {
                free.add(spare);
            }
        }
        return free;
    }

    /**
     * Claims the lowest free spare for {@code by} (null for the console), with {@code label}. With {@code from}, the
     * spare takes that biome's look (every group, vanilla biomes included) as a live edit that save writes; without,
     * it stays neutral. A claim is for good, so all that can be checked is checked before it is written: the label, a
     * free spare whose file is there, and a source the game knows. The look is copied after, so a claim that went
     * through returns, and a look the game refuses is reported in the Claim. Throws only when nothing was claimed.
     * The log names {@code actor}, the claimer's name, and the source when its look was copied: "biometune: Builder
     * claimed mcme:custom_003 as 'Quay' (look of cbc:111g380-5p)".
     */
    public Claim claim(String label, NamespacedKey from, UUID by, String actor) throws BiomeTuningException {
        String checked = BiomeLabels.checkLabel(label);
        List<NamespacedKey> free = free();
        if (free.isEmpty()) {
            throw new BiomeTuningException("No spare biome is free. " + HOW_TO_ADD);
        }
        NamespacedKey spare = free.get(0);
        if (from != null) {
            service.value(from, ANY); // throws for a biome the game does not know
        }
        BiomeDocument doc = service.document(spare);
        if (doc.isUnsaved() || doc.canUndo()) {
            // edits or undo steps left by a claim removed by hand: a claim starts from the spare's file alone
            service.revert(spare);
        }
        labels.claim(spare, checked, by, clock.get(), from);
        Claim claim = from == null ? new Claim(spare, checked, List.of(), null) : copyLook(spare, checked, from);
        logger.info("biometune: " + actor + " claimed " + spare + " as '" + checked + "'"
                + (claim.copied().isEmpty() ? "" : " (look of " + from + ")"));
        return claim;
    }

    /** A claim's look, copied from {@code from}: what was copied, or why the game refused it. */
    private Claim copyLook(NamespacedKey spare, String label, NamespacedKey from) {
        try {
            return new Claim(spare, label, service.copy(from, spare, TuningProperty.Group.LOOK), null);
        } catch (BiomeTuningException e) {
            return new Claim(spare, label, List.of(), e.getMessage());
        }
    }

    /**
     * Labels any biome that can be tuned (a CBC biome's random id, say), but not a free spare: claiming one names it.
     * Written at once, and logged with {@code actor}, the name of who labelled it; returns the label as kept.
     */
    public String label(NamespacedKey biome, String label, String actor) throws BiomeTuningException {
        String checked = BiomeLabels.checkLabel(label);
        service.document(biome); // throws for the game's own biomes, and for one without a datapack file
        String locked = lockedBecause(biome);
        if (locked != null) {
            throw new BiomeTuningException(locked);
        }
        labels.setLabel(biome, checked);
        logger.info("biometune: " + actor + " labelled " + biome + " '" + checked + "'");
        return checked;
    }

    /**
     * Whether the spare's file is there and loads. The service loads a file once and keeps what it read, so the file
     * itself is looked for each time: an admin may remove it later.
     */
    private boolean loads(NamespacedKey spare) {
        try {
            return Files.isRegularFile(service.document(spare).file());
        } catch (BiomeTuningException e) {
            return false; // its file is gone
        }
    }

    /**
     * Whether {@code biome} is an id of the spare pattern that nobody has claimed: a free spare, which stays neutral
     * until a claim names it, or such an id the server does not know. Unlike {@link #free}, it asks nothing of the
     * spare's file.
     */
    public boolean isFreeSpare(NamespacedKey biome) throws BiomeTuningException {
        // the pattern first: a labels file that cannot be read then holds back the spares only, not every biome
        return ids.matches(biome) && !labels.isClaimed(biome);
    }

    /**
     * Why {@code biome} may not be tuned or labelled now, or null when it may: a free spare stays neutral until it is
     * claimed, as a claim hands a spare out as it is. The service's {@link BiomeTuningService.Lock}.
     */
    public String lockedBecause(NamespacedKey biome) throws BiomeTuningException {
        boolean free;
        try {
            free = isFreeSpare(biome);
        } catch (BiomeTuningException e) {
            throw new BiomeTuningException("can't tell whether " + biome + " is claimed, so it waits, and edits not "
                    + "saved before a restart are lost: " + e.getMessage(), e);
        }
        if (!free) {
            return null;
        }
        return biome + " is a free spare: it stays neutral until it is claimed. Claim one with /biometune claim "
                + "<label>, or New biome… in the editor.";
    }

    /**
     * Writes the next {@code count} spares into the target pack and returns their ids. They are numbered after the
     * highest spare the server knows, the pack holds (spares added earlier exist only on disk until a restart) or the
     * labels record as claimed (a claimed id is never handed out again), and the pattern must have room for them all
     * before any file is written. They exist from the next restart. The log names {@code actor}, the name of who added
     * them: "biometune: Builder added spares mcme:custom_004 to mcme:custom_006 to the pack bukkit"; when a write
     * fails, the spares written before it, which exist from the next restart too.
     */
    public List<NamespacedKey> add(int count, String actor) throws BiomeTuningException {
        if (count < 1 || count > ADD_LIMIT) {
            throw new BiomeTuningException("Add 1 to " + ADD_LIMIT + " spares at a time.");
        }
        Path packFolder = datapacks.existingPack(pack);
        if (!Files.isRegularFile(packFolder.resolve("pack.mcmeta"))) {
            throw new BiomeTuningException("the pack folder " + pack + " has no pack.mcmeta, so Minecraft would never "
                    + "load spares from it");
        }
        Path biomeFolder = packFolder.resolve("data").resolve(ids.namespace()).resolve("worldgen").resolve("biome");
        int highest = Stream.of(spares(), onDisk(biomeFolder), claimed()).flatMap(List::stream)
                .mapToInt(ids::number).max().orElse(0);
        int room = Integer.parseInt("9".repeat(ids.digits())) - highest;
        if (count > room) {
            throw new BiomeTuningException("the pattern " + ids.pattern() + " has room for "
                    + (room == 0 ? "no more spares" : "only " + room + " more")
                    + (highest == 0 ? "" : " after " + ids.key(highest)));
        }
        List<NamespacedKey> written = new ArrayList<>();
        for (int number = highest + 1; number <= highest + count; number++) {
            NamespacedKey spare = ids.key(number);
            try {
                write(biomeFolder.resolve(spare.getKey() + ".json"));
            } catch (IOException e) {
                logAdded(actor, written);
                throw new BiomeTuningException(wrote(written) + "could not write " + spare + " (" + reason(e) + ")", e);
            }
            written.add(spare);
        }
        logAdded(actor, written);
        return written;
    }

    /** Logs the spares {@code actor} added, if there are any. */
    private void logAdded(String actor, List<NamespacedKey> added) {
        if (!added.isEmpty()) {
            logger.info("biometune: " + actor + " added " + (added.size() == 1 ? "spare " : "spares ") + range(added)
                    + " to the pack " + pack);
        }
    }

    /** The spares written before a failure, which are always in a row: "wrote a to b (…), but ", or nothing. */
    private static String wrote(List<NamespacedKey> written) {
        if (written.isEmpty()) {
            return "";
        }
        return "wrote " + range(written) + " (there from the next restart), but ";
    }

    /** Spares in a row as running text: "a", or "a to b" for more than one. */
    static String range(List<NamespacedKey> spares) {
        return spares.size() == 1 ? spares.get(0).toString() : spares.get(0) + " to " + spares.get(spares.size() - 1);
    }

    /**
     * Writes one spare through a temporary file, which Minecraft does not read, as it loads only .json files: a broken
     * biome file stops the server at its next start, so the spare's file is whole or not there at all, after a power
     * loss too, as the temporary file is on the disk before it is moved. The move never replaces a file, not even one
     * the numbering cannot see, such as CUSTOM_001.json on a disk that ignores case. So it has no REPLACE_EXISTING,
     * and no ATOMIC_MOVE, which replaces the file on Linux.
     */
    private static void write(Path file) throws IOException {
        Path temp = file.resolveSibling(file.getFileName() + ".tmp");
        Files.createDirectories(file.getParent());
        Files.deleteIfExists(temp); // left by a write that failed before
        try {
            BiomeFiles.writeToDisk(temp, NEUTRAL);
            Files.move(temp, file);
        } catch (IOException e) {
            try {
                Files.deleteIfExists(temp); // the pack travels, so it keeps no leftovers
            } catch (IOException cleanup) {
                e.addSuppressed(cleanup);
            }
            throw e;
        }
    }

    /** The spare ids the pack holds a file for, loaded or not. */
    private List<NamespacedKey> onDisk(Path folder) throws BiomeTuningException {
        if (!Files.isDirectory(folder)) {
            return List.of();
        }
        try (Stream<Path> files = Files.walk(folder)) {
            return files.filter(Files::isRegularFile)
                    .map(file -> folder.relativize(file).toString().replace(file.getFileSystem().getSeparator(), "/"))
                    .filter(name -> name.endsWith(".json"))
                    .map(name -> NamespacedKey.fromString(ids.namespace() + ":" + name.substring(0, name.length() - 5)))
                    .filter(Objects::nonNull)
                    .filter(ids::matches)
                    .toList();
        } catch (IOException e) {
            throw new BiomeTuningException("could not read the pack's biomes (" + reason(e) + ")", e);
        } catch (UncheckedIOException e) { // the walk throws this for a folder it cannot open part-way
            throw new BiomeTuningException("could not read the pack's biomes (" + reason(e.getCause()) + ")", e);
        }
    }

    /** The spares the labels record as claimed, loaded or not. */
    private List<NamespacedKey> claimed() throws BiomeTuningException {
        return labels.all().entrySet().stream()
                .filter(entry -> entry.getValue().spare() && ids.matches(entry.getKey()))
                .map(Map.Entry::getKey)
                .toList();
    }

    /**
     * A failed file operation in words, with the path it failed on: NIO's own message is often the path alone, and
     * the path may be a folder on the way rather than the file itself. The labels file says its failures this way too.
     */
    static String reason(IOException e) {
        if (!(e instanceof FileSystemException f) || f.getFile() == null) {
            return e.getMessage();
        }
        String path = f.getFile();
        return switch (f) {
            case FileAlreadyExistsException _ -> path + " is in the way";
            case DirectoryNotEmptyException _ -> path + " is a folder, in the way";
            case NoSuchFileException _ -> path + " is missing";
            case NotDirectoryException _ -> path + " is not a folder";
            case AccessDeniedException _ -> "the server may not use " + path;
            default -> path + ": " + (f.getReason() == null ? "it failed" : f.getReason());
        };
    }
}
