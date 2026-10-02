package com.mcmiddleearth.architect.biomeTuning;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.mcmiddleearth.util.PathSafety;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;
import org.bukkit.NamespacedKey;

/**
 * The working copy of one biome's datapack JSON, the single source of truth for tuning. Unknown keys and
 * worldgen pass through untouched; only catalogue paths are ever written.
 */
public final class BiomeDocument {

    private static final Gson PRETTY = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss-SSSSSS");

    private final NamespacedKey key;
    private final Path file;
    private final int undoDepth;
    private final Deque<JsonObject> undo = new ArrayDeque<>();
    private JsonObject current;
    private JsonObject saved;
    private String savedHash;

    private BiomeDocument(NamespacedKey key, Path file, int undoDepth, JsonObject json, String hash) {
        this.key = key;
        this.file = file;
        this.undoDepth = undoDepth;
        this.current = json;
        this.saved = json.deepCopy();
        this.savedHash = hash;
    }

    public static BiomeDocument load(NamespacedKey key, Path file, int undoDepth) throws BiomeTuningException {
        try {
            byte[] bytes = Files.readAllBytes(file);
            JsonElement json = JsonParser.parseString(new String(bytes, StandardCharsets.UTF_8));
            if (!json.isJsonObject()) {
                throw new BiomeTuningException(file.getFileName() + " is not a JSON object");
            }
            return new BiomeDocument(key, file, undoDepth, json.getAsJsonObject(), sha256(bytes));
        } catch (IOException | JsonParseException e) {
            throw new BiomeTuningException("could not read " + file + ": " + e.getMessage(), e);
        }
    }

    public NamespacedKey key() {
        return key;
    }

    public Path file() {
        return file;
    }

    public boolean isUnsaved() {
        return !current.equals(saved);
    }

    public boolean canUndo() {
        return !undo.isEmpty();
    }

    /** A deep copy of the current JSON. */
    public JsonObject current() {
        return current.deepCopy();
    }

    /** The current value at {@code property}'s path, or null if it is not set. */
    public JsonElement value(TuningProperty property) {
        return valueIn(current, property);
    }

    /** A candidate copy of the current JSON with {@code property} set; the document itself is unchanged. */
    public JsonObject with(TuningProperty property, JsonElement value) {
        return with(current, property, value);
    }

    /** A candidate copy of the current JSON without {@code property}. */
    public JsonObject without(TuningProperty property) {
        return with(current, property, null);
    }

    /** Makes a validated candidate current; the old JSON goes on the undo stack. */
    public void accept(JsonObject candidate) {
        undo.push(current);
        while (undo.size() > undoDepth) {
            undo.removeLast();
        }
        current = candidate.deepCopy();
    }

    /** The JSON an undo would restore, or null. Call {@link #undone()} once it has been validated and applied. */
    public JsonObject undoCandidate() {
        return undo.isEmpty() ? null : undo.peek().deepCopy();
    }

    public void undone() {
        current = undo.pop();
    }

    /** Every catalogue value that differs from the saved file. */
    public List<TuningProperty> changedSinceSave() {
        List<TuningProperty> changed = new ArrayList<>();
        for (TuningProperty property : TuningProperties.all()) {
            if (!Objects.equals(valueIn(saved, property), valueIn(current, property))) {
                changed.add(property);
            }
        }
        return changed;
    }

    /** "sky_color: #ffaa00 -> #2040ff" for every catalogue value that differs from the saved file. */
    public List<String> changesSinceSave() {
        List<String> changes = new ArrayList<>();
        for (TuningProperty property : changedSinceSave()) {
            changes.add(property.name() + ": " + TuningProperties.display(valueIn(saved, property)) + " -> "
                    + TuningProperties.display(valueIn(current, property)));
        }
        return changes;
    }

    /**
     * Writes the current JSON. It refuses if the file was edited by hand since it was loaded or last saved, backs up
     * the old file, and then replaces it in one move of a temporary file that is on the disk first, so a power loss
     * leaves the old file or the new one, whole. A save that fails leaves no temporary file.
     */
    public void save(Path backupsDir, int keepBackups) throws BiomeTuningException {
        try {
            byte[] onDisk = Files.readAllBytes(file);
            if (!sha256(onDisk).equals(savedHash)) {
                throw new BiomeTuningException(file.getFileName() + " was changed on disk since it was loaded; use "
                        + "/biometune revert " + key + " to load that version first");
            }
            Path backups;
            try {
                backups = PathSafety.resolveInside(backupsDir.toFile(), key.getNamespace() + "/" + key.getKey()).toPath();
            } catch (SecurityException e) {
                throw new BiomeTuningException("not a valid biome id: " + key);
            }
            Files.createDirectories(backups);
            String stamp = LocalDateTime.now().format(STAMP);
            Path backup = backups.resolve(stamp + ".json");
            for (int n = 1; Files.exists(backup); n++) {
                backup = backups.resolve(stamp + "_" + n + ".json");
            }
            Files.write(backup, onDisk);
            pruneBackups(backups, keepBackups);
            byte[] bytes = (PRETTY.toJson(current) + "\n").getBytes(StandardCharsets.UTF_8);
            replace(file, bytes);
            saved = current.deepCopy();
            savedHash = sha256(bytes);
        } catch (IOException e) {
            throw new BiomeTuningException("could not save " + file + ": " + e.getMessage(), e);
        }
    }

    /**
     * Writes {@code bytes} to a temporary file next to {@code file}, then moves it over the file. A temporary file that
     * is not moved is removed: the pack travels between servers, so it keeps no leftovers.
     */
    private static void replace(Path file, byte[] bytes) throws IOException {
        Path temp = file.resolveSibling(file.getFileName() + ".tmp");
        try {
            BiomeFiles.writeToDisk(temp, bytes);
            try {
                Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            try {
                Files.deleteIfExists(temp);
            } catch (IOException cleanup) {
                e.addSuppressed(cleanup);
            }
            throw e;
        }
    }

    /** The value at {@code property}'s path inside {@code json}, or null. */
    static JsonElement valueIn(JsonObject json, TuningProperty property) {
        JsonElement node = json;
        for (String step : property.path()) {
            if (node == null || !node.isJsonObject()) {
                return null;
            }
            node = node.getAsJsonObject().get(step);
        }
        return node;
    }

    /** A copy of {@code json} with {@code property} set to {@code value}, or removed when {@code value} is null. */
    static JsonObject with(JsonObject json, TuningProperty property, JsonElement value) {
        JsonObject copy = json.deepCopy();
        JsonObject parent = copy;
        List<String> path = property.path();
        for (String step : path.subList(0, path.size() - 1)) {
            JsonElement child = parent.get(step);
            if (child == null || !child.isJsonObject()) {
                if (value == null) {
                    return copy; // nothing to remove
                }
                child = new JsonObject();
                parent.add(step, child);
            }
            parent = child.getAsJsonObject();
        }
        String last = path.get(path.size() - 1);
        if (value == null) {
            parent.remove(last);
        } else {
            parent.add(last, value.deepCopy());
        }
        return copy;
    }

    private static void pruneBackups(Path dir, int keep) throws IOException {
        List<Path> files;
        try (Stream<Path> stream = Files.list(dir)) {
            files = stream.filter(path -> path.getFileName().toString().endsWith(".json")).sorted().toList();
        }
        for (int i = 0; i < files.size() - keep; i++) {
            Files.deleteIfExists(files.get(i));
        }
    }

    private static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
