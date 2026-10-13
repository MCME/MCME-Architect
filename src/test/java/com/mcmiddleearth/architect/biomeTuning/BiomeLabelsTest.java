package com.mcmiddleearth.architect.biomeTuning;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.bukkit.NamespacedKey;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class BiomeLabelsTest {

    private static final NamespacedKey CBC = NamespacedKey.fromString("cbc:111g380-5p");
    private static final NamespacedKey SPARE = NamespacedKey.fromString("mcme:custom_001");
    private static final NamespacedKey SECOND_SPARE = NamespacedKey.fromString("mcme:custom_002");
    private static final NamespacedKey PLAINS = NamespacedKey.fromString("minecraft:plains");
    private static final UUID BART = UUID.fromString("4e0d6ab1-3c2e-4b44-9b0b-6d1f4f1a2b3c");
    private static final Instant NOON = Instant.parse("2026-09-29T12:00:00Z");

    @TempDir
    Path dir;
    private Path pack;
    private Path file;

    @BeforeEach
    void setUp() throws Exception {
        pack = Files.createDirectories(dir.resolve("datapacks/bukkit"));
        file = pack.resolve(BiomeLabels.FILE);
    }

    private BiomeLabels labels() {
        return new BiomeLabels(() -> dir.resolve("datapacks"), "bukkit");
    }

    private JsonObject written() throws Exception {
        return JsonParser.parseString(Files.readString(file)).getAsJsonObject();
    }

    @Test
    void noFileMeansNoLabels() throws Exception {
        assertTrue(labels().all().isEmpty());
        assertTrue(labels().label(CBC).isEmpty());
        assertFalse(labels().isClaimed(SPARE));
        assertFalse(Files.exists(file), "reading writes nothing");
    }

    @Test
    void aLabelIsWrittenAtOnceAndReadBack() throws Exception {
        labels().setLabel(CBC, "  Harbour at dusk ");

        assertEquals("Harbour at dusk", written().getAsJsonObject("cbc:111g380-5p").get("label").getAsString());
        assertEquals("Harbour at dusk", labels().label(CBC).orElseThrow(), "a new reader finds it in the file");
        assertFalse(labels().isClaimed(CBC), "a label is no claim");
    }

    @Test
    void theFileIsWrittenWhole() throws Exception {
        labels().setLabel(CBC, "Harbour");

        assertEquals("{\n  \"cbc:111g380-5p\": {\n    \"label\": \"Harbour\"\n  }\n}\n", Files.readString(file));
    }

    @Test
    void aClaimRecordsWhoWhenAndFrom() throws Exception {
        labels().claim(SPARE, "Minas Tirith sky", BART, NOON, PLAINS);

        BiomeLabels.Entry entry = labels().entry(SPARE).orElseThrow();
        assertEquals(new BiomeLabels.Entry("Minas Tirith sky", true, BART, NOON, PLAINS), entry);
        assertTrue(labels().isClaimed(SPARE));
        JsonObject json = written().getAsJsonObject("mcme:custom_001");
        assertTrue(json.get("spare").getAsBoolean());
        assertEquals(BART.toString(), json.get("claimedBy").getAsString());
        assertEquals("2026-09-29T12:00:00Z", json.get("claimedAt").getAsString());
        assertEquals("minecraft:plains", json.get("copiedFrom").getAsString());
    }

    @Test
    void aClaimWithoutASourceHasNoCopiedFrom() throws Exception {
        labels().claim(SPARE, "Blank", BART, NOON, null);

        assertNull(labels().entry(SPARE).orElseThrow().copiedFrom());
        assertFalse(written().getAsJsonObject("mcme:custom_001").has("copiedFrom"));
    }

    @Test
    void aClaimFromTheConsoleNamesNoPlayer() throws Exception {
        labels().claim(SPARE, "Server", null, NOON, null);

        assertNull(labels().entry(SPARE).orElseThrow().claimedBy());
        assertTrue(labels().isClaimed(SPARE));
        assertFalse(written().getAsJsonObject("mcme:custom_001").has("claimedBy"));
    }

    // a spare may already be painted somewhere, so a claim is for good
    @Test
    void aClaimedSpareCannotBeClaimedAgain() throws Exception {
        labels().claim(SPARE, "First", BART, NOON, null);

        BiomeTuningException e = assertThrows(BiomeTuningException.class,
                () -> labels().claim(SPARE, "Second", BART, NOON, null));
        assertTrue(e.getMessage().contains("already claimed"), e.getMessage());
        assertEquals("First", labels().label(SPARE).orElseThrow());
    }

    @Test
    void aNewLabelKeepsTheClaim() throws Exception {
        labels().claim(SPARE, "First", BART, NOON, PLAINS);

        labels().setLabel(SPARE, "Renamed");

        assertEquals(new BiomeLabels.Entry("Renamed", true, BART, NOON, PLAINS), labels().entry(SPARE).orElseThrow());
    }

    // a label shows in chat and in the editor: one short line of plain, visible text
    @Test
    void aLabelIsOneShortLine() {
        Map<String, String> bad = new LinkedHashMap<>();
        bad.put("", "can't be empty");
        bad.put("   ", "can't be empty");
        bad.put("\u200b", "can't be empty");
        bad.put("x".repeat(BiomeLabels.LABEL_LIMIT + 1), "at most 32 characters");
        bad.put("two\nlines", "one line of plain text");
        bad.put("tab\there", "one line of plain text");
        bad.put("one\u2028line", "one line of plain text");
        bad.put("one\u2029paragraph", "one line of plain text");
        bad.put("half \ud83d", "one line of plain text");
        bad.put("\u00a7kHidden", "can't hold \u00a7");
        for (Map.Entry<String, String> label : bad.entrySet()) {
            BiomeTuningException e = assertThrows(BiomeTuningException.class,
                    () -> BiomeLabels.checkLabel(label.getKey()), label.getKey());
            assertTrue(e.getMessage().contains(label.getValue()), label.getKey() + " -> " + e.getMessage());
        }
        BiomeTuningException empty = assertThrows(BiomeTuningException.class, () -> labels().setLabel(CBC, " "));
        assertEquals("A label can't be empty.", empty.getMessage(), "setLabel checks it too");
        assertDoesNotThrow(() -> labels().setLabel(CBC, "x".repeat(BiomeLabels.LABEL_LIMIT)));
        assertDoesNotThrow(() -> labels().setLabel(CBC, "Mordor \ud83c\udf0b"), "a whole emoji is fine");
    }

    // a later version may keep more in the file; a write must not lose it
    @Test
    void fieldsItDoesNotKnowSurviveAWrite() throws Exception {
        Files.writeString(file, "{\"cbc:111g380-5p\":{\"label\":\"Old\",\"painter\":\"Bart\"}}");

        labels().setLabel(CBC, "New");

        JsonObject json = written().getAsJsonObject("cbc:111g380-5p");
        assertEquals("New", json.get("label").getAsString());
        assertEquals("Bart", json.get("painter").getAsString());
    }

    @Test
    void anUnreadableFileIsNeverOverwritten() throws Exception {
        String broken = "{\"cbc:111g380-5p\": {\"label\": ";
        Files.writeString(file, broken);

        BiomeTuningException read = assertThrows(BiomeTuningException.class, () -> labels().label(CBC));
        assertTrue(read.getMessage().contains(BiomeLabels.FILE), read.getMessage());
        assertTrue(read.getMessage().contains("removing it would free every claimed spare"),
                "the advice says what removing it costs: " + read.getMessage());
        assertFalse(read.getMessage().contains("\n"),
                "one line in chat, without the parser's links: " + read.getMessage());
        assertThrows(BiomeTuningException.class, () -> labels().setLabel(CBC, "New"));
        assertEquals(broken, Files.readString(file), "left as it was, for someone to fix");
    }

    // someone fixes a hand edit that went wrong, so the message says where, in words, on one line
    @Test
    void aFileThatDoesNotReadSaysWhyInPlainWords() throws Exception {
        Map<String, String> broken = new LinkedHashMap<>();
        broken.put("{\"cbc:111g380-5p\" {\"label\": \"A\"}}", "Expected ':' at line 1 column 20");
        broken.put("{\"cbc:111g380-5p\": {\"label\": ", "End of input at line 1 column 30");
        broken.put("{// Bart\n\"cbc:111g380-5p\": {\"label\": \"A\"}}",
                "something JSON does not allow, such as a comment, at line 1 column 3");
        broken.put("{'cbc:111g380-5p': {'label': 'A'}}",
                "something JSON does not allow, such as a comment, at line 1 column 3");
        broken.put("{\"cbc:111g380-5p\": {\"label\": \"Caf\u00e9\"}}", "it is not saved as UTF-8");
        broken.put("[{\"cbc:111g380-5p\": {\"label\": \"A\"}}]", "it is not one { } of biome ids");
        broken.put("\"Harbour\"", "it is not one { } of biome ids");
        broken.put("{\"cbc:111g380-5p\": {\"label\": \"A\tB\"}}",
                "a tab or line break inside quotes, which JSON does not allow, at line 1 column 31");
        for (Map.Entry<String, String> content : broken.entrySet()) {
            // in ISO-8859-1, as an old editor saves it: the accent is one byte, where UTF-8 takes two
            byte[] bytes = content.getKey().getBytes(StandardCharsets.ISO_8859_1);
            Files.write(file, bytes);

            BiomeTuningException e = assertThrows(BiomeTuningException.class, () -> labels().label(CBC),
                    content.getKey());
            assertTrue(e.getMessage().contains("(" + content.getValue() + ")"),
                    content.getKey() + " -> " + e.getMessage());
            for (String jargon : List.of("\n", "com.google", "java.", "Exception", "Strictness", "https", "path $",
                    "BEGIN_", "strict mode")) {
                assertFalse(e.getMessage().contains(jargon), "'" + jargon + "' in: " + e.getMessage());
            }
            assertThrows(BiomeTuningException.class, () -> labels().setLabel(CBC, "New"));
            assertArrayEquals(bytes, Files.readAllBytes(file), "left as it was, for someone to fix");
        }
    }

    // reading a folder fails as access denied on Windows and as "Is a directory" on Linux; both mean the same
    @Test
    void aFolderWhereTheFileGoesIsNamedAsOne() throws Exception {
        Files.createDirectories(file.resolve("by mistake"));

        BiomeTuningException e = assertThrows(BiomeTuningException.class, () -> labels().label(CBC));

        assertTrue(e.getMessage().contains("(it is a folder, not a file)"), e.getMessage());
        assertThrows(BiomeTuningException.class, () -> labels().setLabel(CBC, "New"), "nothing is written either");
        assertTrue(Files.isDirectory(file.resolve("by mistake")), "left as it was");
    }

    @Test
    void anEntryItCannotReadMakesTheWholeFileUnreadable() throws Exception {
        String odd = "{\"not an id\":{\"label\":\"A\"},\"cbc:111g380-5p\":{\"label\":\"B\"}}";
        Files.writeString(file, odd);

        assertThrows(BiomeTuningException.class, () -> labels().label(CBC));
        assertThrows(BiomeTuningException.class, () -> labels().setLabel(CBC, "C"));
        assertEquals(odd, Files.readString(file), "writing would have dropped the entry it could not read");
    }

    // spares and info must work without the pack, so reading finds no labels and makes nothing
    @Test
    void theTargetPackMustExist() throws Exception {
        Files.delete(pack);

        assertTrue(labels().all().isEmpty());
        assertFalse(Files.exists(pack), "reading makes no pack");
        BiomeTuningException e = assertThrows(BiomeTuningException.class, () -> labels().setLabel(CBC, "Harbour"));
        assertTrue(e.getMessage().contains("bukkit") && e.getMessage().contains("biomeTuning.targetPack"),
                e.getMessage());
    }

    // on Windows a path through a file is "no such file", on Linux "Not a directory": both mean no labels
    @Test
    void aPackThatIsAFileMeansNoLabels() throws Exception {
        Files.delete(pack);
        Files.writeString(pack, "a file, not a folder");

        assertTrue(labels().all().isEmpty());
        BiomeTuningException e = assertThrows(BiomeTuningException.class, () -> labels().setLabel(CBC, "Harbour"));
        assertTrue(e.getMessage().contains("biomeTuning.targetPack"), e.getMessage());
    }

    // each entry that cannot be read says which it is and why, and the file is left for someone to fix
    @Test
    void anEntryItCannotReadIsNamedWithTheReason() throws Exception {
        Map<String, String> bad = Map.of(
                "{\"cbc:111g380-5p\":{\"label\":\"A\",\"claimedAt\":\"yesterday\"}}", "claimedAt",
                "{\"cbc:111g380-5p\":{}}", "label",
                "{\"cbc:111g380-5p\":{\"label\":\"two\\nlines\"}}", "label",
                "{\"cbc:111g380-5p\":{\"label\":\"A\",\"spare\":1}}", "spare",
                "{\"cbc:111g380-5p\":{\"label\":\"A\",\"copiedFrom\":7}}", "copiedFrom",
                "{\"cbc:111g380-5p\":{\"label\":\"A\",\"claimedBy\":\"bart\"}}", "claimedBy",
                "{\"plains\":{\"label\":\"A\"}}", "'plains'",
                "{\"cbc:111g380-5p\":{\"label\":\"A\"},\"cbc:111g380-5p\":{\"label\":\"B\"}}", "duplicate");
        for (Map.Entry<String, String> content : bad.entrySet()) {
            Files.writeString(file, content.getKey());
            BiomeTuningException e = assertThrows(BiomeTuningException.class, () -> labels().label(CBC),
                    content.getKey());
            assertTrue(e.getMessage().contains(content.getValue()), content.getKey() + " -> " + e.getMessage());
            assertThrows(BiomeTuningException.class, () -> labels().setLabel(CBC, "New"));
            assertEquals(content.getKey(), Files.readString(file), "left as it was");
        }
    }

    // the server keeps one reader, and people edit the file by hand while it runs: that is how a claim is fixed
    @Test
    void anEditOnDiskCountsAtOnce() throws Exception {
        BiomeLabels labels = labels();
        labels.setLabel(CBC, "Old");
        Files.writeString(file, "{\"cbc:111g380-5p\":{\"label\":\"Old\"},\"mcme:custom_001\":{\"label\":\"By hand\","
                + "\"spare\":true}}");

        assertTrue(labels.isClaimed(SPARE), "a claim made by hand counts");
        assertThrows(BiomeTuningException.class, () -> labels.claim(SPARE, "Again", BART, NOON, null));
        labels.setLabel(CBC, "New");
        assertEquals("By hand", written().getAsJsonObject("mcme:custom_001").get("label").getAsString(),
                "a write keeps what was changed on disk");
    }

    // an edit can keep the file's time and size; a write reads the file again anyway, so it keeps the edit
    @Test
    void aWriteReadsTheFileAgainEvenWhenItLooksUnchanged() throws Exception {
        BiomeLabels labels = labels();
        labels.setLabel(CBC, "Old");
        FileTime written = Files.getLastModifiedTime(file);
        Files.writeString(file, Files.readString(file).replace("Old", "Odd")); // the same size
        Files.setLastModifiedTime(file, written);

        labels.setLabel(SPARE, "Sky");

        assertEquals("Odd", written().getAsJsonObject("cbc:111g380-5p").get("label").getAsString());
    }

    // a claim reads the file again first too, so a claim made by hand that kept the time and size still counts
    @Test
    void aClaimReadsTheFileAgainEvenWhenItLooksUnchanged() throws Exception {
        Files.writeString(file, "{\"mcme:custom_001\":{\"label\":\"Sky\",\"spare\":false}}");
        BiomeLabels labels = labels();
        assertFalse(labels.isClaimed(SPARE));
        FileTime read = Files.getLastModifiedTime(file);
        Files.writeString(file, "{\"mcme:custom_001\":{\"label\":\"Sky\",\"spare\":true }}"); // the same size
        Files.setLastModifiedTime(file, read);

        assertThrows(BiomeTuningException.class, () -> labels.claim(SPARE, "Again", BART, NOON, null));
        assertTrue(Files.readString(file).contains("\"spare\":true }"), "the claim made by hand is left as it was");
    }

    // the time counts as well as the size: an edit that keeps the size shows on the next read
    @Test
    void anEditThatKeepsTheSizeShowsOnTheNextRead() throws Exception {
        BiomeLabels labels = labels();
        labels.setLabel(CBC, "Old");
        FileTime written = Files.getLastModifiedTime(file);
        Files.writeString(file, Files.readString(file).replace("Old", "Odd")); // the same size
        Files.setLastModifiedTime(file, FileTime.fromMillis(written.toMillis() + 2_000));

        assertEquals("Odd", labels.label(CBC).orElseThrow());
    }

    // while the server starts there is no world yet: the reader says so, and throws nothing unchecked
    @Test
    void noWorldYetIsSaidPlainly() {
        BiomeLabels labels = new BiomeLabels(() -> {
            throw new IllegalStateException("no world yet");
        }, "bukkit");

        BiomeTuningException e = assertThrows(BiomeTuningException.class, labels::all);
        assertTrue(e.getMessage().contains("no world yet"), e.getMessage());
    }

    @Test
    void aFileBrokenAfterItWasReadIsNeverOverwritten() throws Exception {
        BiomeLabels labels = labels();
        labels.setLabel(CBC, "Old");
        Files.writeString(file, "{\"cbc:111g380-5p\": {\"label\": "); // someone is halfway through an edit

        assertThrows(BiomeTuningException.class, () -> labels.setLabel(CBC, "New"));
        assertEquals("{\"cbc:111g380-5p\": {\"label\": ", Files.readString(file));
    }

    @Test
    void aFileCopiedInLaterIsRead() throws Exception {
        BiomeLabels labels = labels();
        assertTrue(labels.label(CBC).isEmpty(), "no file yet");
        Files.writeString(file, "{\"cbc:111g380-5p\":{\"label\":\"Harbour\"}}");

        assertEquals("Harbour", labels.label(CBC).orElseThrow());
        labels.setLabel(SPARE, "Sky");
        assertEquals("Harbour", written().getAsJsonObject("cbc:111g380-5p").get("label").getAsString());
    }

    // by the whole id: a NamespacedKey's own order compares the part after the colon first, so cbc:zzz would be last
    @Test
    void theFileAndTheListAreInIdOrder() throws Exception {
        BiomeLabels labels = labels();
        labels.setLabel(SPARE, "Sky");
        labels.setLabel(SECOND_SPARE, "Field");
        labels.setLabel(NamespacedKey.fromString("cbc:zzz"), "Far harbour");
        labels.setLabel(CBC, "Harbour");

        List<String> ids = List.of("cbc:111g380-5p", "cbc:zzz", "mcme:custom_001", "mcme:custom_002");
        assertEquals(ids, List.copyOf(written().keySet()), "the file");
        assertEquals(ids, labels.all().keySet().stream().map(NamespacedKey::toString).toList(), "the list");
    }

    // here the temporary file cannot be made, so the write fails before the old file is touched
    @Test
    void aWriteThatFailsKeepsTheOldFile() throws Exception {
        BiomeLabels labels = labels();
        labels.setLabel(CBC, "Old");
        Files.createDirectories(pack.resolve(BiomeLabels.FILE + ".tmp").resolve("stuck")); // cannot be removed

        BiomeTuningException e = assertThrows(BiomeTuningException.class, () -> labels.setLabel(CBC, "New"));
        assertTrue(e.getMessage().contains(pack.resolve(BiomeLabels.FILE + ".tmp") + " is a folder, in the way"),
                "why, and where: " + e.getMessage());
        assertThrows(BiomeTuningException.class, () -> labels.claim(SPARE, "Sky", BART, NOON, null));

        assertEquals("Old", written().getAsJsonObject("cbc:111g380-5p").get("label").getAsString());
        assertEquals("Old", labels.label(CBC).orElseThrow(), "the reader still has what the file has");
        assertFalse(labels.isClaimed(SPARE), "a claim whose write failed is no claim");
    }

    // here the temporary file is written, but the move fails: the pack travels between servers, so it keeps no
    // leftovers
    @Test
    void aMoveThatFailsLeavesNoTemporaryFile() throws Exception {
        BiomeLabels labels = new BiomeLabels(new BiomeTuningService.DatapacksFolder() {
            @Override
            public Path get() {
                return dir.resolve("datapacks");
            }

            @Override
            public Path existingPack(String name) throws BiomeTuningException {
                try {
                    Files.createDirectories(file.resolve("in the way")); // just before the write, so the move fails
                } catch (IOException e) {
                    throw new AssertionError("could not put a folder in the way", e);
                }
                return BiomeTuningService.DatapacksFolder.super.existingPack(name);
            }
        }, "bukkit");

        BiomeTuningException e = assertThrows(BiomeTuningException.class, () -> labels.setLabel(CBC, "Harbour"));

        assertTrue(e.getMessage().contains("could not write " + BiomeLabels.FILE), e.getMessage());
        assertFalse(Files.exists(pack.resolve(BiomeLabels.FILE + ".tmp")), "no temporary file is left in the pack");
    }
}
