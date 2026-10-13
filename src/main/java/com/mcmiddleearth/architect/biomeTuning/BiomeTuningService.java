package com.mcmiddleearth.architect.biomeTuning;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mcmiddleearth.architect.biomeTuning.TuningProperty.Group;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.logging.Logger;
import org.bukkit.NamespacedKey;

/**
 * Runs edits: patch the working copy, have vanilla's codec decode the whole candidate, and only then swap
 * it into the running biome. Nothing is written to disk until save. Main thread only.
 */
public final class BiomeTuningService {

    /** One changed value, for chat feedback. {@code after} is null when the value was removed. */
    public record Change(NamespacedKey biome, TuningProperty property, JsonElement before, JsonElement after) {
    }

    /** Where the datapacks live; asked on each lookup, because Architect loads before any world exists. */
    @FunctionalInterface
    public interface DatapacksFolder {
        Path get() throws BiomeTuningException;

        /** The folder, with any failure to find it, such as no world yet, said as a BiomeTuningException. */
        default Path folder() throws BiomeTuningException {
            try {
                return get();
            } catch (RuntimeException e) {
                throw new BiomeTuningException("could not find the primary world's folder: " + e.getMessage(), e);
            }
        }

        /** The pack folder {@code pack}, which must be there: spares and labels are written only into a pack. */
        default Path existingPack(String pack) throws BiomeTuningException {
            Path folder = folder().resolve(pack);
            if (!Files.isDirectory(folder)) {
                throw new BiomeTuningException("there is no pack folder " + pack + " in the world's datapacks folder "
                        + "(biomeTuning.targetPack)");
            }
            return folder;
        }
    }

    /** One value to change: {@code value} is builder text as for set, or null to remove the value. */
    public record Edit(String property, String value) {

        public Edit {
            Objects.requireNonNull(property, "property");
        }
    }

    private final NmsBridge bridge;
    private final DatapacksFolder datapacksDir;
    private final Path backupsDir;
    private final BiomeTuningSettings settings;
    private final Logger logger;
    private final Map<NamespacedKey, BiomeDocument> documents = new HashMap<>();
    private Lock lock = biome -> null;

    public BiomeTuningService(NmsBridge bridge, Path datapacksDir, Path backupsDir, BiomeTuningSettings settings,
                              Logger logger) {
        this(bridge, () -> datapacksDir, backupsDir, settings, logger);
    }

    /** {@code datapacksDir} is asked each time a biome file is looked up: Architect loads before any world exists. */
    public BiomeTuningService(NmsBridge bridge, DatapacksFolder datapacksDir, Path backupsDir, BiomeTuningSettings settings,
                              Logger logger) {
        this.bridge = bridge;
        this.datapacksDir = datapacksDir;
        this.backupsDir = backupsDir;
        this.settings = settings;
        this.logger = logger;
    }

    /**
     * Says why a biome may not change now, or null when it may. The spare pool locks its free spares: a claim hands a
     * spare out as it is, so none may change before it is claimed. Every change and save asks, and every undo that has
     * a step to take back; revert does not, as it is the way back to the biome's file.
     */
    @FunctionalInterface
    public interface Lock {
        String why(NamespacedKey biome) throws BiomeTuningException;
    }

    /** Set at start, once the spare pool exists; until then nothing is locked. */
    public void lock(Lock lock) {
        this.lock = lock;
    }

    /** The working copy of a tunable biome, loaded on first use. */
    public BiomeDocument document(NamespacedKey biome) throws BiomeTuningException {
        BiomeDocument doc = documents.get(biome);
        if (doc == null) {
            if (!bridge.sentInFull(biome)) {
                throw new BiomeTuningException(biome + " comes from the game itself: clients use their own copy of it, "
                        + "so changes would not show");
            }
            doc = BiomeDocument.load(biome, BiomeFiles.locate(datapacksDir.folder(), biome), settings.undoDepth());
            documents.put(biome, doc);
        }
        return doc;
    }

    public Change set(NamespacedKey biome, String propertyName, String rawValue) throws BiomeTuningException {
        TuningProperty property = property(propertyName);
        JsonElement value = parse(property, rawValue);
        BiomeDocument doc = document(biome);
        JsonElement before = doc.value(property);
        commit(biome, doc, doc.with(property, value));
        return new Change(biome, property, before, doc.value(property));
    }

    public Change unset(NamespacedKey biome, String propertyName) throws BiomeTuningException {
        TuningProperty property = property(propertyName);
        BiomeDocument doc = document(biome);
        JsonElement before = doc.value(property);
        if (before == null) {
            throw new BiomeTuningException(property.name() + " is not set on " + biome);
        }
        commit(biome, doc, doc.without(property));
        return new Change(biome, property, before, null);
    }

    /**
     * Makes every value of {@code groups} on {@code to} equal to {@code from}'s, set or unset, as one undo step.
     * {@code from} can be any biome, vanilla included (its running definition is encoded).
     */
    public List<Change> copy(NamespacedKey from, NamespacedKey to, Set<Group> groups) throws BiomeTuningException {
        JsonObject source = definition(from);
        Map<TuningProperty, JsonElement> wanted = new LinkedHashMap<>();
        for (TuningProperty property : TuningProperties.all()) {
            if (groups.contains(property.group())) {
                wanted.put(property, BiomeDocument.valueIn(source, property));
            }
        }
        return patch(to, wanted);
    }

    /**
     * Changes several values as one step, all or nothing: every value is parsed and the whole candidate decoded
     * before it goes live, with a single undo step. A null value removes the value, and does nothing when it is not
     * set. Values that are already equal are skipped; a property named twice takes the last value. Returns what
     * changed.
     */
    public List<Change> edit(NamespacedKey biome, List<Edit> edits) throws BiomeTuningException {
        Map<TuningProperty, JsonElement> wanted = new LinkedHashMap<>();
        for (Edit edit : edits) {
            TuningProperty property = property(edit.property());
            wanted.put(property, edit.value() == null ? null : parse(property, edit.value()));
        }
        return patch(biome, wanted);
    }

    /**
     * A value of any biome, vanilla included: from its working copy if it has one, else its running definition.
     * Null when the biome leaves it unset; throws for a biome the game does not know.
     */
    public JsonElement value(NamespacedKey biome, TuningProperty property) throws BiomeTuningException {
        return BiomeDocument.valueIn(definition(biome), property);
    }

    /**
     * Steps back one edit; false if there is nothing to undo, which is said before the lock is asked, as nothing would
     * change.
     */
    public boolean undo(NamespacedKey biome) throws BiomeTuningException {
        BiomeDocument doc = document(biome);
        JsonObject previous = doc.undoCandidate();
        if (previous == null) {
            return false;
        }
        requireUnlocked(biome);
        bridge.apply(biome, bridge.decode(previous));
        doc.undone();
        return true;
    }

    /** Reloads the file, dropping unsaved edits and the undo history, and swaps it back in. */
    public void revert(NamespacedKey biome) throws BiomeTuningException {
        if (!bridge.sentInFull(biome)) {
            throw new BiomeTuningException(biome + " comes from the game itself and has no file to revert to");
        }
        BiomeDocument fresh = BiomeDocument.load(biome, BiomeFiles.locate(datapacksDir.folder(), biome),
                settings.undoDepth());
        bridge.apply(biome, bridge.decode(fresh.current()));
        documents.put(biome, fresh);
    }

    /** Writes the working copy to the datapack; returns what changed, which is also logged with {@code actor}. */
    public List<String> save(NamespacedKey biome, String actor) throws BiomeTuningException {
        BiomeDocument doc = document(biome);
        requireUnlocked(biome);
        List<String> changes = doc.changesSinceSave();
        if (!doc.isUnsaved()) {
            return changes;
        }
        bridge.decode(doc.current()); // validate once more before writing
        doc.save(backupsDir, settings.backupsPerBiome());
        logger.info("biometune: " + actor + " saved " + biome + " " + (changes.isEmpty() ? "(other values)" : changes));
        return changes;
    }

    /** Biomes with live edits that are not saved yet. */
    public List<NamespacedKey> unsaved() {
        return documents.values().stream()
                .filter(BiomeDocument::isUnsaved)
                .map(BiomeDocument::key)
                .sorted(Comparator.comparing(NamespacedKey::toString))
                .toList();
    }

    /** A biome's working copy if it has one, else its running definition, encoded. */
    private JsonObject definition(NamespacedKey biome) throws BiomeTuningException {
        BiomeDocument doc = documents.get(biome);
        if (doc != null) {
            return doc.current();
        }
        // the codec writes floats, and 0.8f never equals the 0.8 a file holds; printed and read back, it does
        return JsonParser.parseString(bridge.encode(biome).toString()).getAsJsonObject();
    }

    /** Gives each property its {@code wanted} value (null removes it) as one undo step; returns what changed. */
    private List<Change> patch(NamespacedKey biome, Map<TuningProperty, JsonElement> wanted)
            throws BiomeTuningException {
        BiomeDocument doc = document(biome);
        JsonObject candidate = doc.current();
        List<Change> changes = new ArrayList<>();
        for (Map.Entry<TuningProperty, JsonElement> entry : wanted.entrySet()) {
            JsonElement before = BiomeDocument.valueIn(candidate, entry.getKey());
            if (!Objects.equals(before, entry.getValue())) {
                candidate = BiomeDocument.with(candidate, entry.getKey(), entry.getValue());
                changes.add(new Change(biome, entry.getKey(), before, entry.getValue()));
            }
        }
        if (!changes.isEmpty()) {
            commit(biome, doc, candidate);
        }
        return changes;
    }

    private static JsonElement parse(TuningProperty property, String raw) throws BiomeTuningException {
        try {
            return TuningProperties.parse(property, raw);
        } catch (IllegalArgumentException e) {
            throw new BiomeTuningException(e.getMessage());
        }
    }

    private void commit(NamespacedKey biome, BiomeDocument doc, JsonObject candidate) throws BiomeTuningException {
        requireUnlocked(biome);
        bridge.apply(biome, bridge.decode(candidate));
        doc.accept(candidate);
    }

    private void requireUnlocked(NamespacedKey biome) throws BiomeTuningException {
        String locked = lock.why(biome);
        if (locked != null) {
            throw new BiomeTuningException(locked);
        }
    }

    private static TuningProperty property(String name) throws BiomeTuningException {
        return TuningProperties.resolve(name).orElseThrow(() -> new BiomeTuningException("unknown property '" + name
                + "'; for example sky_color, fog_color or water_color (tab completes them all)"));
    }
}
