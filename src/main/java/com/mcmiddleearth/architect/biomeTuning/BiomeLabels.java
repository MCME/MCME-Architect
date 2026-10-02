package com.mcmiddleearth.architect.biomeTuning;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.Strictness;
import com.google.gson.reflect.TypeToken;
import org.bukkit.NamespacedKey;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.UUID;

/**
 * The labels builders give biomes, and which spares are claimed. They are kept in mcme-biometune.json at the root of
 * the target pack (biomeTuning.targetPack), which Minecraft ignores, so they travel with the pack:
 * <pre>
 * {"cbc:111g380-5p": {"label": "Harbour"},
 *  "mcme:custom_001": {"label": "Minas Tirith sky", "spare": true, "claimedBy": "&lt;uuid&gt;",
 *                      "claimedAt": "2026-09-29T12:00:00Z", "copiedFrom": "minecraft:plains"}}
 * </pre>
 * People may edit the file while the server runs, as that is how a claim is fixed: it is read again whenever it has
 * changed, and always just before a write, so a write keeps an edit made on disk. It is read as strict JSON, so a
 * comment added by hand is refused rather than lost at the next write. A missing pack or file means no
 * labels yet. A file that cannot be read, wholly or in part, is never overwritten: labels, claims and new spares
 * (which must not take a claimed id) wait until someone fixes or removes it, and the message says which entry and
 * why. Fields this code does not know survive a write. Main thread only.
 */
public final class BiomeLabels {

    public static final String FILE = "mcme-biometune.json";
    /**
     * The longest label: it fits on one line of the editor's home window. Reading checks each label against
     * {@link #checkLabel}, so tightening these rules later would make files written today unreadable, and every claim
     * would wait until someone fixed the file.
     */
    public static final int LABEL_LIMIT = 32;
    /** Strict: read leniently, a comment or unquoted text in a hand edit would be dropped by the next write. */
    private static final Gson PRETTY = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping()
            .setStrictness(Strictness.STRICT).create();
    /** Read as a map, an id written twice is an error; read as a JsonObject, the second would silently win. */
    private static final Type ENTRIES = new TypeToken<LinkedHashMap<String, JsonElement>>() { }.getType();
    private static final Comparator<NamespacedKey> BY_ID = Comparator.comparing(NamespacedKey::toString);

    /**
     * One biome's entry. For a biome that is only labelled, spare is false and the claim's parts are null.
     * {@code copiedFrom} is the biome the claim asked to start from: a look the game refused leaves it named, while
     * the spare stays neutral.
     */
    public record Entry(String label, boolean spare, UUID claimedBy, Instant claimedAt, NamespacedKey copiedFrom) {
    }

    /** The file as it was when it was read: its time and size. */
    private record Stamp(long modified, long size) {
    }

    private final BiomeTuningService.DatapacksFolder datapacks;
    private final String pack;
    /** The file's entries by id, as read and written; null until read. */
    private Map<NamespacedKey, JsonObject> raw;
    /** The file when {@link #raw} was filled, or null when there was none. */
    private Stamp stamp;

    /** {@code datapacks} is asked each time the file is needed, as no world exists while Architect starts. */
    public BiomeLabels(BiomeTuningService.DatapacksFolder datapacks, String pack) {
        this.datapacks = datapacks;
        this.pack = pack;
    }

    public Optional<Entry> entry(NamespacedKey biome) throws BiomeTuningException {
        JsonObject json = read().get(biome);
        return json == null ? Optional.empty() : Optional.of(entryOf(json));
    }

    public Optional<String> label(NamespacedKey biome) throws BiomeTuningException {
        return entry(biome).map(Entry::label);
    }

    public boolean isClaimed(NamespacedKey biome) throws BiomeTuningException {
        return entry(biome).map(Entry::spare).orElse(false);
    }

    /** Every entry, sorted by id. */
    public SortedMap<NamespacedKey, Entry> all() throws BiomeTuningException {
        SortedMap<NamespacedKey, Entry> all = new TreeMap<>(BY_ID);
        for (Map.Entry<NamespacedKey, JsonObject> entry : read().entrySet()) {
            all.put(entry.getKey(), entryOf(entry.getValue()));
        }
        return all;
    }

    /** Gives a biome a label, keeping a claim it has; written at once. */
    public void setLabel(NamespacedKey biome, String label) throws BiomeTuningException {
        String checked = checkLabel(label);
        raw = null; // read afresh: the write must keep whatever was changed on disk
        JsonObject json = copyOf(biome);
        json.addProperty("label", checked);
        write(biome, json);
    }

    /**
     * Records that {@code by} claimed {@code spare} at {@code at}, with its label; written at once, and for good.
     * {@code by} is null for the console, which is no player. {@code copiedFrom} is the biome the claim asked to start
     * from, or null for none: a look the game refused leaves it named, while the spare stays neutral.
     */
    public void claim(NamespacedKey spare, String label, UUID by, Instant at, NamespacedKey copiedFrom)
            throws BiomeTuningException {
        String checked = checkLabel(label);
        raw = null; // read afresh: a claim made by hand counts, and the write keeps it
        if (isClaimed(spare)) {
            throw new BiomeTuningException(spare + " is already claimed");
        }
        JsonObject json = copyOf(spare);
        json.addProperty("label", checked);
        json.addProperty("spare", true);
        if (by == null) {
            json.remove("claimedBy");
        } else {
            json.addProperty("claimedBy", by.toString());
        }
        json.addProperty("claimedAt", at.toString());
        if (copiedFrom == null) {
            json.remove("copiedFrom");
        } else {
            json.addProperty("copiedFrom", copiedFrom.toString());
        }
        write(spare, json);
    }

    /**
     * A label as it is kept: one line of 1 to {@link #LABEL_LIMIT} characters, without surrounding spaces, with
     * something to see, and without §, which the game reads as the start of a colour or format code.
     */
    static String checkLabel(String label) throws BiomeTuningException {
        String trimmed = label == null ? "" : label.strip();
        if (trimmed.codePoints().allMatch(c -> Character.isWhitespace(c) || Character.isSpaceChar(c)
                || Character.getType(c) == Character.FORMAT)) {
            throw new BiomeTuningException("A label can't be empty.");
        }
        if (trimmed.length() > LABEL_LIMIT) {
            throw new BiomeTuningException("A label is at most " + LABEL_LIMIT
                    + " characters, so it fits on one line.");
        }
        if (trimmed.codePoints().anyMatch(c -> Character.isISOControl(c)
                || Character.getType(c) == Character.SURROGATE
                || Character.getType(c) == Character.LINE_SEPARATOR
                || Character.getType(c) == Character.PARAGRAPH_SEPARATOR)) {
            throw new BiomeTuningException("A label is one line of plain text.");
        }
        if (trimmed.indexOf('§') >= 0) {
            throw new BiomeTuningException("A label can't hold §, which the game reads as a colour code.");
        }
        return trimmed;
    }

    private JsonObject copyOf(NamespacedKey biome) throws BiomeTuningException {
        JsonObject json = read().get(biome);
        return json == null ? new JsonObject() : json.deepCopy();
    }

    /** The entries, read again if the file changed since they were read. */
    private Map<NamespacedKey, JsonObject> read() throws BiomeTuningException {
        Path file = datapacks.folder().resolve(pack).resolve(FILE);
        Stamp now = stampOf(file);
        if (raw != null && Objects.equals(now, stamp)) {
            return raw;
        }
        Map<NamespacedKey, JsonObject> entries = new HashMap<>();
        if (now != null) {
            Map<String, JsonElement> root;
            try {
                root = PRETTY.fromJson(Files.readString(file, StandardCharsets.UTF_8), ENTRIES);
            } catch (IOException | RuntimeException e) {
                // a folder fails to read as access denied on Windows, and as "Is a directory" on Linux
                throw unreadable(Files.isDirectory(file) ? "it is a folder, not a file" : why(e), e);
            }
            if (root == null) {
                throw unreadable("it is empty", null);
            }
            for (Map.Entry<String, JsonElement> entry : root.entrySet()) {
                NamespacedKey key = NamespacedKey.fromString(entry.getKey());
                if (key == null || !key.toString().equals(entry.getKey())) {
                    throw unreadable("'" + entry.getKey() + "' is not a whole biome id, such as mcme:custom_001",
                            null);
                }
                if (!entry.getValue().isJsonObject()) {
                    throw unreadable("'" + key + "' has no entry in { }", null);
                }
                String problem = problemWith(entry.getValue().getAsJsonObject());
                if (problem != null) {
                    throw unreadable("'" + key + "': " + problem, null);
                }
                entries.put(key, entry.getValue().getAsJsonObject());
            }
        }
        raw = entries;
        stamp = now;
        return raw;
    }

    /**
     * Writes the file with {@code biome}'s new entry through a temporary file, so a failed write keeps the old one. The
     * temporary file is on the disk before it replaces the old one, so a power loss leaves one or the other, whole.
     */
    private void write(NamespacedKey biome, JsonObject json) throws BiomeTuningException {
        Map<NamespacedKey, JsonObject> entries = new HashMap<>(read());
        entries.put(biome, json);
        Path folder = datapacks.existingPack(pack);
        JsonObject root = new JsonObject();
        entries.entrySet().stream()
                .sorted(Map.Entry.comparingByKey(BY_ID))
                .forEach(entry -> root.add(entry.getKey().toString(), entry.getValue()));
        Path file = folder.resolve(FILE);
        try {
            replace(file, folder.resolve(FILE + ".tmp"), PRETTY.toJson(root) + "\n");
        } catch (IOException e) {
            throw new BiomeTuningException("could not write " + FILE + " in the pack " + pack + " ("
                    + SparePool.reason(e) + ")", e);
        }
        raw = entries;
        try {
            stamp = stampOf(file);
        } catch (BiomeTuningException e) {
            raw = null; // written, but not looked at: the next read reads it again
        }
    }

    /**
     * Writes {@code text} to {@code temp}, then moves it over {@code file}. A temporary file that is not moved is
     * removed: the pack travels between servers, so it keeps no leftovers.
     */
    private static void replace(Path file, Path temp, String text) throws IOException {
        Files.deleteIfExists(temp); // left by a write that failed before
        try {
            BiomeFiles.writeToDisk(temp, text);
            try {
                Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING); // as BiomeDocument does
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

    /** The file's time and size, or null when there is no file, or no pack folder to hold one. */
    private Stamp stampOf(Path file) throws BiomeTuningException {
        if (!Files.isDirectory(file.getParent())) {
            return null; // a pack that is a file: Linux says "Not a directory", which is no file either
        }
        try {
            BasicFileAttributes attributes = Files.readAttributes(file, BasicFileAttributes.class);
            return new Stamp(attributes.lastModifiedTime().toMillis(), attributes.size());
        } catch (NoSuchFileException e) {
            return null;
        } catch (IOException e) {
            throw unreadable(SparePool.reason(e), e);
        }
    }

    private BiomeTuningException unreadable(String why, Throwable cause) {
        return new BiomeTuningException(FILE + " in the pack " + pack + " could not be read (" + why + "): fix it "
                + "by hand, as removing it would free every claimed spare; until then, labels, claims and new spares "
                + "wait", cause);
    }

    /** Why an entry cannot be read, or null when it can: every field this code knows must have its own type. */
    private static String problemWith(JsonObject json) {
        JsonElement label = json.get("label");
        if (!isText(label)) {
            return "its label is missing, or is not text";
        }
        try {
            if (!checkLabel(label.getAsString()).equals(label.getAsString())) {
                return "its label has spaces around it";
            }
        } catch (BiomeTuningException e) {
            return "its label: " + e.getMessage();
        }
        JsonElement spare = json.get("spare");
        if (spare != null && !(spare.isJsonPrimitive() && spare.getAsJsonPrimitive().isBoolean())) {
            return "spare is not true or false";
        }
        JsonElement by = json.get("claimedBy");
        if (by != null && !(isText(by) && isUuid(by.getAsString()))) {
            return "claimedBy is not a player's UUID";
        }
        JsonElement at = json.get("claimedAt");
        if (at != null && !(isText(at) && isInstant(at.getAsString()))) {
            return "claimedAt is not a time such as 2026-09-29T12:00:00Z";
        }
        JsonElement from = json.get("copiedFrom");
        if (from != null && !(isText(from) && NamespacedKey.fromString(from.getAsString()) != null)) {
            return "copiedFrom is not a biome id";
        }
        return null;
    }

    private static boolean isText(JsonElement value) {
        return value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isString();
    }

    private static boolean isUuid(String text) {
        try {
            UUID.fromString(text);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private static boolean isInstant(String text) {
        try {
            Instant.parse(text);
            return true;
        } catch (DateTimeParseException e) {
            return false;
        }
    }

    /** Why the file did not read, on one line, in words for the person fixing it: no Java or Gson names. */
    private static String why(Exception e) {
        if (e instanceof CharacterCodingException) {
            return "it is not saved as UTF-8";
        }
        if (e instanceof IOException io) {
            return SparePool.reason(io);
        }
        Throwable reason = e instanceof JsonParseException && e.getCause() != null ? e.getCause() : e;
        // Gson's "Expected BEGIN_OBJECT but was STRING": an entry takes any JSON, so the outer form is wrong. Gson
        // also reads [] and [[id, entry], ...] as a map, which the next write makes { }, but the message names { },
        // the form the file is written in
        if (reason instanceof IllegalStateException) {
            return "it is not one { } of biome ids";
        }
        return firstLine(reason.getMessage())
                .replace("Use JsonReader.setStrictness(Strictness.LENIENT) to accept malformed JSON",
                        "something JSON does not allow, such as a comment,")
                .replaceFirst("^Unescaped control characters .* are not allowed in strict mode",
                        "a tab or line break inside quotes, which JSON does not allow,")
                .replaceFirst(" path \\$.*", "");
    }

    private static String firstLine(String message) {
        String text = message == null ? "no reason given" : message.strip();
        int end = text.indexOf('\n');
        return (end < 0 ? text : text.substring(0, end)).strip();
    }

    /** An entry already checked by {@link #problemWith}. */
    private static Entry entryOf(JsonObject json) {
        return new Entry(json.get("label").getAsString(),
                json.has("spare") && json.get("spare").getAsBoolean(),
                json.has("claimedBy") ? UUID.fromString(json.get("claimedBy").getAsString()) : null,
                json.has("claimedAt") ? Instant.parse(json.get("claimedAt").getAsString()) : null,
                json.has("copiedFrom") ? NamespacedKey.fromString(json.get("copiedFrom").getAsString()) : null);
    }
}
