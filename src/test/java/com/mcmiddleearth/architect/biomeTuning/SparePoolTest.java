package com.mcmiddleearth.architect.biomeTuning;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.bukkit.NamespacedKey;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.AccessDeniedException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.NotDirectoryException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;

class SparePoolTest {

    private static final UUID BART = UUID.fromString("4e0d6ab1-3c2e-4b44-9b0b-6d1f4f1a2b3c");
    private static final Instant NOON = Instant.parse("2026-09-29T12:00:00Z");
    private static final NamespacedKey ASH = NamespacedKey.fromString("minecraft:basalt_deltas");
    private static final NamespacedKey CBC = NamespacedKey.fromString("cbc:111g380-5p");

    @TempDir
    Path dir;
    private Path datapacks;
    private Path pack;
    /** The biome ids the server knows. */
    private final List<NamespacedKey> registry = new ArrayList<>();
    private BiomeLabels labels;
    private FakeNmsBridge bridge;
    private BiomeTuningService service;
    private SparePool pool;
    /** What the pools logged, a line per record. */
    private final List<String> logged = new ArrayList<>();

    @BeforeEach
    void setUp() throws Exception {
        datapacks = dir.resolve("datapacks");
        pack = Files.createDirectories(datapacks.resolve("bukkit"));
        Files.writeString(pack.resolve("pack.mcmeta"), "{\"pack\":{\"pack_format\":94,\"description\":\"test\"}}");
        labels = new BiomeLabels(() -> datapacks, "bukkit");
        bridge = new FakeNmsBridge();
        service = new BiomeTuningService(bridge, datapacks, dir.resolve("backups"), BiomeTuningSettings.DEFAULTS,
                Logger.getLogger("test"));
        pool = pool(SpareIds.DEFAULT);
        service.lock(pool::lockedBecause); // as BiomeTuning wires them
    }

    private SparePool pool(SpareIds ids) {
        return pool(ids, () -> datapacks);
    }

    private SparePool pool(SpareIds ids, BiomeTuningService.DatapacksFolder folder) {
        Logger log = new Logger("pool", null) {
            @Override
            public void log(LogRecord record) {
                logged.add(record.getMessage());
            }
        };
        return new SparePool(ids, "bukkit", registry::stream, folder, labels, service, () -> NOON, log);
    }

    private static NamespacedKey key(String id) {
        return NamespacedKey.fromString(id);
    }

    private Path spareFile(String key) {
        return pack.resolve("data/mcme/worldgen/biome/" + key + ".json");
    }

    /** A spare the server loaded at startup: its file is in the pack, and the registry knows it. */
    private NamespacedKey loadedSpare(String key) throws Exception {
        Files.createDirectories(spareFile(key).getParent());
        Files.writeString(spareFile(key), SparePool.NEUTRAL);
        NamespacedKey spare = key("mcme:" + key);
        registry.add(spare);
        return spare;
    }

    @Test
    void theSparesAreTheKnownIdsThatMatchInNumberOrder() {
        registry.addAll(List.of(key("mcme:custom_002"), key("minecraft:plains"), key("mcme:custom_001"),
                key("cbc:111g380-5p"), key("mcme:custom_01")));

        assertEquals(List.of(key("mcme:custom_001"), key("mcme:custom_002")), pool.spares(),
                "only ids of the pattern's form, lowest number first");
    }

    @Test
    void aClaimedSpareIsNotFree() throws Exception {
        loadedSpare("custom_001");
        loadedSpare("custom_002");
        labels.claim(key("mcme:custom_001"), "Harbour", BART, NOON, null);

        assertEquals(List.of(key("mcme:custom_002")), pool.free(), "the claimed one is taken");
    }

    @Test
    void aLabelledSpareIsStillFree() throws Exception {
        loadedSpare("custom_001");
        loadedSpare("custom_002");
        labels.setLabel(key("mcme:custom_001"), "Maybe a harbour");

        assertEquals(List.of(key("mcme:custom_001"), key("mcme:custom_002")), pool.free(), "a label is no claim");
    }

    // a spare may be painted anywhere, so it brings no worldgen: no features, carvers or mobs
    @Test
    void aNeutralSpareHasNoWorldgen() {
        JsonObject neutral = JsonParser.parseString(SparePool.NEUTRAL).getAsJsonObject();

        for (String list : List.of("features", "carvers")) {
            assertTrue(neutral.getAsJsonArray(list).isEmpty(), list);
        }
        for (String map : List.of("spawners", "spawn_costs")) {
            assertTrue(neutral.getAsJsonObject(map).isEmpty(), map);
        }
    }

    // spares added earlier exist only on disk until the next restart, so numbering goes past them too
    @Test
    void addWritesNeutralSparesAfterTheHighestNumber() throws Exception {
        registry.addAll(List.of(key("mcme:custom_001"), key("mcme:custom_003")));
        Files.createDirectories(spareFile("custom_004").getParent());
        Files.writeString(spareFile("custom_004"), SparePool.NEUTRAL);

        List<NamespacedKey> added = pool.add(2, "Bart");

        assertEquals(List.of(key("mcme:custom_005"), key("mcme:custom_006")), added, "after 004, which is on disk");
        for (String id : List.of("custom_005", "custom_006")) {
            assertEquals(SparePool.NEUTRAL, Files.readString(spareFile(id)), id + ", whole");
        }
        assertEquals(List.of(key("mcme:custom_001"), key("mcme:custom_003")), pool.spares(),
                "new spares exist from the next restart");
    }

    // a claimed id is never handed out again, even when its file was removed by hand
    @Test
    void addNumbersPastClaimedSpares() throws Exception {
        registry.add(key("mcme:custom_001"));
        labels.claim(key("mcme:custom_007"), "Removed by hand", BART, NOON, null);

        assertEquals(List.of(key("mcme:custom_008")), pool.add(1, "Bart"), "007 is claimed in the labels file");
    }

    // the pattern can change; claims made under an older one stay in the file and do not count
    @Test
    void claimsOfAnotherPatternDoNotCount() throws Exception {
        labels.claim(key("mcme:custom_007"), "Old pattern", BART, NOON, null);

        assertEquals(List.of(key("mcme:spare_001")), pool(new SpareIds("mcme", "spare_", 3)).add(1, "Bart"),
                "custom_007 is no spare_ id");
    }

    @Test
    void filesOfAnotherFormDoNotCount() throws Exception {
        registry.add(key("mcme:custom_001"));
        for (String other : List.of("custom_0100", "custom_000", "harbour")) {
            Files.createDirectories(spareFile(other).getParent());
            Files.writeString(spareFile(other), SparePool.NEUTRAL);
        }

        assertEquals(List.of(key("mcme:custom_002")), pool.add(1, "Bart"), "files of another form are no spares");
    }

    // a prefix may name a folder in the pack, which the walk of the folder sees with a backslash on Windows
    @Test
    void addFindsTheSparesInAFolderOfThePack() throws Exception {
        SparePool nested = pool(new SpareIds("mcme", "spares/custom_", 3));
        Path file = pack.resolve("data/mcme/worldgen/biome/spares/custom_004.json");
        Files.createDirectories(file.getParent());
        Files.writeString(file, SparePool.NEUTRAL);

        assertEquals(List.of(key("mcme:spares/custom_005")), nested.add(1, "Bart"), "after the one in the folder");
        assertTrue(Files.exists(file.resolveSibling("custom_005.json")), "written in the same folder");
    }

    @Test
    void theFirstSpareIsNumberOne() throws Exception {
        assertEquals(List.of(key("mcme:custom_001")), pool.add(1, "Bart"), "no spare anywhere yet");
    }

    // Minecraft loads a pack folder only with a pack.mcmeta: spares written elsewhere would never exist
    @Test
    void addNeedsAPackMinecraftLoads() throws Exception {
        Files.delete(pack.resolve("pack.mcmeta"));
        BiomeTuningException noMeta = assertThrows(BiomeTuningException.class, () -> pool.add(1, "Bart"));
        assertTrue(noMeta.getMessage().contains("pack.mcmeta"), noMeta.getMessage());

        Files.delete(pack);
        BiomeTuningException noPack = assertThrows(BiomeTuningException.class, () -> pool.add(1, "Bart"));
        assertTrue(noPack.getMessage().contains("bukkit") && noPack.getMessage().contains("biomeTuning.targetPack"),
                noPack.getMessage());
        assertFalse(Files.exists(pack), "add makes no pack");
    }

    // while the server starts there is no world yet: add says so, and throws nothing unchecked
    @Test
    void noWorldYetIsSaidPlainly() {
        SparePool early = pool(SpareIds.DEFAULT, () -> {
            throw new IllegalStateException("no world yet");
        });

        BiomeTuningException e = assertThrows(BiomeTuningException.class, () -> early.add(1, "Bart"));
        assertTrue(e.getMessage().contains("no world yet"), e.getMessage());
    }

    // a broken biome file stops the server at its next start, so a spare's file is whole or not there at all
    @Test
    void aWriteThatFailsLeavesNoFileMinecraftWouldLoad() throws Exception {
        Path stuck = spareFile("custom_003").resolveSibling("custom_003.json.tmp");
        Files.createDirectories(stuck.resolve("in the way"));

        BiomeTuningException e = assertThrows(BiomeTuningException.class, () -> pool.add(3, "Bart"));

        assertTrue(e.getMessage().contains("wrote mcme:custom_001 to mcme:custom_002 "),
                "the spares it wrote, which are always in a row: " + e.getMessage());
        assertTrue(e.getMessage().contains(stuck + " is a folder, in the way"), "why, and where: " + e.getMessage());
        for (String id : List.of("custom_001", "custom_002")) {
            assertEquals(JsonParser.parseString(SparePool.NEUTRAL),
                    JsonParser.parseString(Files.readString(spareFile(id))), id + ", written before it, is whole");
        }
        assertFalse(Files.exists(spareFile("custom_003")), "the one that failed is not there at all");
    }

    // the spares written before a failure are there from the next restart, so the log says who added them
    @Test
    void theSparesWrittenBeforeAFailureAreLogged() throws Exception {
        Files.createDirectories(spareFile("custom_003").resolveSibling("custom_003.json.tmp").resolve("in the way"));

        assertThrows(BiomeTuningException.class, () -> pool.add(3, "Bart"));

        assertEquals(List.of("biometune: Bart added spares mcme:custom_001 to mcme:custom_002 to the pack bukkit"),
                logged);
    }

    // no file is ever written over. A folder of a spare's name stands for a file the numbering cannot see, such as
    // CUSTOM_001.json on a disk that ignores case
    @Test
    void addNeverWritesOverWhatIsThere() throws Exception {
        Files.createDirectories(spareFile("custom_001"));

        BiomeTuningException e = assertThrows(BiomeTuningException.class, () -> pool.add(1, "Bart"));

        assertTrue(e.getMessage().contains(spareFile("custom_001") + " is in the way"), e.getMessage());
        assertTrue(Files.isDirectory(spareFile("custom_001")), "left as it was");
        assertFalse(Files.exists(spareFile("custom_001").resolveSibling("custom_001.json.tmp")),
                "the pack travels, so it keeps no temporary file");
    }

    @Test
    void aFileWhereTheBiomeFolderGoesIsNamed() throws Exception {
        Path biomes = pack.resolve("data/mcme/worldgen/biome");
        Files.createDirectories(biomes.getParent());
        Files.writeString(biomes, "a file, not a folder");

        BiomeTuningException e = assertThrows(BiomeTuningException.class, () -> pool.add(1, "Bart"));

        assertTrue(e.getMessage().contains(biomes + " is in the way"), e.getMessage());
    }

    // NIO often says these with the path alone. Which one a disk throws differs between systems (Windows calls a path
    // through a file missing, Linux says "Not a directory"), so the test makes each one
    @Test
    void aFailedFileOperationSaysWhatWentWrong() {
        String path = spareFile("custom_001").toString();

        assertEquals(path + " is missing", SparePool.reason(new NoSuchFileException(path)), "a missing file");
        assertEquals(path + " is not a folder", SparePool.reason(new NotDirectoryException(path)),
                "a file listed as a folder");
        assertEquals("the server may not use " + path, SparePool.reason(new AccessDeniedException(path)),
                "access denied");
    }

    // numbering must see every claim, so a labels file that cannot be read stops add too
    @Test
    void addWaitsForAnUnreadableLabelsFile() throws Exception {
        Files.writeString(pack.resolve(BiomeLabels.FILE), "{\"mcme:custom_007\": {\"label\": ");

        BiomeTuningException e = assertThrows(BiomeTuningException.class, () -> pool.add(1, "Bart"));

        assertTrue(e.getMessage().contains("new spares wait"), e.getMessage());
        assertFalse(Files.exists(spareFile("custom_001")), "nothing written");
    }

    @Test
    void addStopsAtTheLastId() throws Exception {
        SparePool small = pool(new SpareIds("mcme", "custom_", 1));
        registry.add(key("mcme:custom_8"));

        BiomeTuningException full = assertThrows(BiomeTuningException.class, () -> small.add(2, "Bart"),
                "custom_10 does not fit one digit");
        assertTrue(full.getMessage().endsWith("room for only 1 more after mcme:custom_8"), full.getMessage());
        assertFalse(Files.exists(spareFile("custom_9")), "all or nothing");
        assertEquals(List.of(key("mcme:custom_9")), small.add(1, "Bart"), "the last one fits");
        BiomeTuningException none = assertThrows(BiomeTuningException.class, () -> small.add(1, "Bart"));
        assertTrue(none.getMessage().endsWith("room for no more spares after mcme:custom_9"),
                "it names the spare in the way, which may be a stray claim or file: " + none.getMessage());
        BiomeTuningException fresh = assertThrows(BiomeTuningException.class,
                () -> pool(new SpareIds("mcme", "other_", 1)).add(10, "Bart"));
        assertTrue(fresh.getMessage().endsWith("room for only 9 more"), "no spare yet: " + fresh.getMessage());
    }

    @Test
    void addTakesOneToAHundred() throws Exception {
        assertThrows(BiomeTuningException.class, () -> pool.add(0, "Bart"), "at least one");
        assertThrows(BiomeTuningException.class, () -> pool.add(SparePool.ADD_LIMIT + 1, "Bart"), "at most a hundred");

        List<NamespacedKey> hundred = pool.add(SparePool.ADD_LIMIT, "Bart");
        assertEquals(SparePool.ADD_LIMIT, hundred.size(), "a hundred at once is fine");
        assertEquals(key("mcme:custom_100"), hundred.get(SparePool.ADD_LIMIT - 1));
    }

    @Test
    void aClaimTakesTheLowestFreeSpareAndRecordsWhoAndWhen() throws Exception {
        NamespacedKey first = loadedSpare("custom_001");
        loadedSpare("custom_002");

        SparePool.Claim claim = pool.claim(" Harbour ", null, BART, "Bart");

        assertEquals(first, claim.spare(), "the lowest free spare");
        assertEquals("Harbour", claim.label(), "the label as it is kept");
        assertEquals(List.of(), claim.copied(), "no source, nothing copied");
        assertNull(claim.copyFailure(), "nothing failed");
        assertEquals(new BiomeLabels.Entry("Harbour", true, BART, NOON, null), labels.entry(first).orElseThrow());
        assertTrue(bridge.applied.isEmpty(), "a neutral start changes nothing live");
        assertEquals(key("mcme:custom_002"), pool.claim("Quay", null, BART, "Bart").spare(),
                "the next claim, the next spare");
    }

    // every group, as /biometune copy <from> <to> all does, vanilla biomes included
    @Test
    void aClaimFromABiomeTakesItsLookAsAnUnsavedEdit() throws Exception {
        NamespacedKey spare = loadedSpare("custom_001");
        JsonObject ash = JsonParser.parseString("{\"attributes\":{\"minecraft:visual/sky_color\":\"#123456\","
                + "\"minecraft:visual/fog_color\":\"#654321\"},\"downfall\":0,\"effects\":{\"water_color\":\"#3f76e4\"},"
                + "\"has_precipitation\":false,\"temperature\":2}").getAsJsonObject();
        bridge.encoded.put(ASH, ash);
        bridge.vanilla.add(ASH);

        SparePool.Claim claim = pool.claim("Ash fields", ASH, BART, "Bart");

        assertFalse(claim.copied().isEmpty(), "the look came along");
        assertNull(claim.copyFailure(), "nothing failed");
        BiomeDocument doc = service.document(spare);
        assertEquals("#123456", doc.value(TuningProperties.resolve("sky_color").orElseThrow()).getAsString(), "sky");
        assertEquals("#654321", doc.value(TuningProperties.resolve("fog_color").orElseThrow()).getAsString(), "fog");
        assertEquals(2, doc.value(TuningProperties.resolve("temperature").orElseThrow()).getAsInt(), "climate");
        assertTrue(doc.isUnsaved(), "save writes the look");
        assertEquals(ASH, labels.entry(spare).orElseThrow().copiedFrom(), "the claim says where the look came from");
    }

    // a claim copies the whole look, so no group may be left out, now or when the groups change
    @Test
    void aClaimCopiesEveryGroupOfTheLook() throws Exception {
        loadedSpare("custom_001");
        bridge.encoded.put(ASH, JsonParser.parseString("{\"attributes\":{\"minecraft:visual/sky_color\":\"#123456\","
                + "\"minecraft:visual/fog_color\":\"#654321\",\"minecraft:visual/water_fog_color\":\"#050533\","
                + "\"minecraft:audio/music_volume\":0.5,\"minecraft:visual/ambient_particles\":[{\"particle\":"
                + "{\"type\":\"minecraft:white_ash\"},\"probability\":0.1}]},\"downfall\":0.4,\"effects\":"
                + "{\"water_color\":\"#3f76e4\"},\"has_precipitation\":true,\"temperature\":2}").getAsJsonObject());
        bridge.vanilla.add(ASH);

        SparePool.Claim claim = pool.claim("Ash fields", ASH, BART, "Bart");

        EnumSet<TuningProperty.Group> copied = EnumSet.noneOf(TuningProperty.Group.class);
        claim.copied().forEach(change -> copied.add(change.property().group()));
        assertEquals(EnumSet.complementOf(EnumSet.of(TuningProperty.Group.OTHER)), copied,
                "every group but OTHER, which holds raw ids");
    }

    @Test
    void withNoFreeSpareItSaysHowToAddMore() throws Exception {
        NamespacedKey only = loadedSpare("custom_001");
        labels.claim(only, "Taken", BART, NOON, null);

        BiomeTuningException e = assertThrows(BiomeTuningException.class, () -> pool.claim("More", null, BART, "Bart"));
        assertTrue(e.getMessage().contains("/biometune spares add"), e.getMessage());
    }

    // everything that can fail is checked before the claim is written, as a claim is for good
    @Test
    void aClaimThatCannotGoThroughClaimsNothing() throws Exception {
        NamespacedKey spare = loadedSpare("custom_001");

        assertThrows(BiomeTuningException.class, () -> pool.claim("  ", null, BART, "Bart"), "no label");
        assertThrows(BiomeTuningException.class, () -> pool.claim("Ash", key("mymod:nowhere"), BART, "Bart"),
                "a source the game does not know");

        assertFalse(labels.isClaimed(spare), "nothing claimed");
        assertTrue(labels.all().isEmpty(), "nothing written");
    }

    // the claim is written before the copy, and a claim that went through returns: when the game refuses the
    // copied values, the Claim says why, so no caller mistakes it for a claim that never happened
    @Test
    void aCopyTheGameRefusesKeepsTheClaimAndSaysWhy() throws Exception {
        NamespacedKey spare = loadedSpare("custom_001");
        bridge.encoded.put(ASH, JsonParser.parseString("{\"temperature\":2}").getAsJsonObject());
        bridge.rejectContaining = "\"temperature\":2";

        SparePool.Claim claim = pool.claim("Ash", ASH, BART, "Bart");

        assertEquals(spare, claim.spare(), "the claim went through");
        assertTrue(labels.isClaimed(spare), "a claim is for good");
        assertEquals(List.of(), claim.copied(), "nothing copied");
        assertTrue(claim.copyFailure().contains("the codec says no"), claim.copyFailure());
        assertNull(bridge.applied.get(spare), "nothing went live");
        assertFalse(service.document(spare).isUnsaved(), "nothing is left unsaved: the spare stays as its file has it");
    }

    @Test
    void aSpareWhoseFileIsGoneIsNotClaimed() throws Exception {
        NamespacedKey spare = key("mcme:custom_001");
        registry.add(spare); // known since startup, but its file was removed since

        bridge.encoded.put(ASH, JsonParser.parseString("{\"temperature\":2}").getAsJsonObject());
        bridge.vanilla.add(ASH);

        assertThrows(BiomeTuningException.class, () -> pool.claim("Ash", null, BART, "Bart"), "a neutral claim");
        BiomeTuningException withLook = assertThrows(BiomeTuningException.class,
                () -> pool.claim("Ash", ASH, BART, "Bart"));

        assertTrue(withLook.getMessage().startsWith("No spare biome is free"),
                "and one with a look, as no spare is free: " + withLook.getMessage());
        assertFalse(labels.isClaimed(spare), "nothing claimed");
    }

    // free means a claim can take it: a spare whose file was removed since startup is gone at the next restart
    @Test
    void aSpareWhoseFileIsGoneIsNotFree() throws Exception {
        registry.add(key("mcme:custom_001")); // known since startup, but its file was removed since
        NamespacedKey next = loadedSpare("custom_002");

        assertEquals(List.of(next), pool.free(), "only the spares a claim can take");
    }

    // the next restart drops a spare whose file was removed, so a claim takes the next one, and never names an id the
    // builder did not ask for
    @Test
    void aClaimSkipsASpareWhoseFileIsGone() throws Exception {
        NamespacedKey gone = key("mcme:custom_001");
        registry.add(gone); // known since startup, but its file was removed since
        NamespacedKey next = loadedSpare("custom_002");

        SparePool.Claim claim = pool.claim("Harbour", null, BART, "Bart");

        assertEquals(next, claim.spare(), "the next spare that is there");
        assertFalse(labels.isClaimed(gone), "the one whose file is gone stays unclaimed");
    }

    // the service keeps a spare's file once it has read it, for the listing or a claim; a file an admin removes after
    // that still counts as gone
    @Test
    void aSpareWhoseFileIsRemovedAfterItWasReadIsNotFree() throws Exception {
        NamespacedKey gone = loadedSpare("custom_001");
        NamespacedKey next = loadedSpare("custom_002");
        assertEquals(List.of(gone, next), pool.free(), "both files are there, and now read");
        Files.delete(spareFile("custom_001")); // an admin removes it

        assertEquals(List.of(next), pool.free(), "the listing skips it");
        assertEquals(next, pool.claim("Harbour", null, BART, "Bart").spare(), "and so does a claim");
        assertFalse(labels.isClaimed(gone), "the one whose file is gone stays unclaimed");
    }

    // a CBC biome's random id says nothing; a label says what it is
    @Test
    void anyBiomeThatCanBeTunedCanBeLabelled() throws Exception {
        Path cbc = pack.resolve("data/cbc/worldgen/biome/111g380-5p.json");
        Files.createDirectories(cbc.getParent());
        Files.writeString(cbc, SparePool.NEUTRAL);

        pool.label(CBC, "Harbour at dusk", "Bart");

        assertEquals("Harbour at dusk", labels.label(CBC).orElseThrow(), "written at once");
        bridge.vanilla.add(ASH);
        assertThrows(BiomeTuningException.class, () -> pool.label(ASH, "Ash", "Bart"),
                "the game's own biomes cannot be tuned");
        assertTrue(labels.label(ASH).isEmpty(), "nothing written");
    }

    // a claim hands a spare out as it is, so a free spare stays neutral until someone claims it
    @Test
    void aFreeSpareIsLockedUntilItIsClaimed() throws Exception {
        NamespacedKey spare = loadedSpare("custom_001");

        BiomeTuningException e = assertThrows(BiomeTuningException.class,
                () -> service.set(spare, "sky_color", "#2040ff"));
        assertTrue(e.getMessage().contains("free spare") && e.getMessage().contains("/biometune claim"),
                "it says how to get one: " + e.getMessage());
        assertNull(pool.lockedBecause(CBC), "only spares are locked");

        pool.claim("Harbour", null, BART, "Bart");

        assertNull(pool.lockedBecause(spare), "a claimed spare is its builder's to tune");
        service.set(spare, "sky_color", "#2040ff");
        assertEquals("#2040ff", service.value(spare, TuningProperties.resolve("sky_color").orElseThrow())
                .getAsString());
    }

    @Test
    void aFreeSpareCannotBeLabelled() throws Exception {
        NamespacedKey spare = loadedSpare("custom_001");

        BiomeTuningException e = assertThrows(BiomeTuningException.class, () -> pool.label(spare, "Mine", "Bart"));

        assertTrue(e.getMessage().contains("free spare"), e.getMessage());
        assertTrue(labels.entry(spare).isEmpty(), "no label: claiming a spare names it");
    }

    // a label put on a free spare by hand is still no claim, so the spare stays locked
    @Test
    void aLabelledSpareStaysLocked() throws Exception {
        NamespacedKey spare = loadedSpare("custom_001");
        labels.setLabel(spare, "Maybe a harbour");

        assertNotNull(pool.lockedBecause(spare), "a label is no claim");
    }

    // a claim removed from the labels file by hand frees a spare that still holds the old look: it cannot be saved or
    // stepped back through, and the next claim starts from the spare's file
    @Test
    void aSpareFreedByHandIsHandedOutAsItsFileHasIt() throws Exception {
        NamespacedKey spare = loadedSpare("custom_001");
        bridge.encoded.put(ASH, JsonParser.parseString("{\"attributes\":{\"minecraft:visual/sky_color\":\"#123456\"}}")
                .getAsJsonObject());
        bridge.vanilla.add(ASH);
        pool.claim("Ash fields", ASH, BART, "Bart");
        Files.writeString(pack.resolve(BiomeLabels.FILE), "{}"); // the claim removed by hand

        assertThrows(BiomeTuningException.class, () -> service.save(spare, "test"), "a free spare is not saved");
        assertThrows(BiomeTuningException.class, () -> service.undo(spare), "nor stepped back");

        SparePool.Claim claim = pool.claim("Quay", null, BART, "Bart");

        assertEquals(spare, claim.spare(), "the same spare, free again");
        assertFalse(service.document(spare).isUnsaved(), "the old look is gone");
        assertEquals("#78a7ff", service.value(spare, TuningProperties.resolve("sky_color").orElseThrow()).getAsString(),
                "neutral, as its file has it");
    }

    // with nothing unsaved, a claim removed by hand can still leave undo steps: the next claim starts without them,
    // so its builder cannot step back into the old claim's look
    @Test
    void aClaimLeavesNoUndoStepOfAnOldClaim() throws Exception {
        NamespacedKey spare = loadedSpare("custom_001");
        pool.claim("Harbour", null, BART, "Bart");
        service.set(spare, "sky_color", "#123456");
        service.set(spare, "sky_color", "#78a7ff"); // back as its file has it: nothing unsaved, two undo steps
        Files.writeString(pack.resolve(BiomeLabels.FILE), "{}"); // the claim removed by hand

        pool.claim("Quay", null, BART, "Bart");

        assertFalse(service.undo(spare), "no step of the old claim is left");
        assertEquals("#78a7ff", service.value(spare, TuningProperties.resolve("sky_color").orElseThrow()).getAsString(),
                "neutral, as its file has it");
    }

    // while the labels file cannot be read, nobody can tell a free spare from a claimed one, so every spare waits; a
    // claimed spare cannot be saved either, so the message says what a restart would cost
    @Test
    void aSpareWaitsWhileTheLabelsFileCannotBeRead() throws Exception {
        NamespacedKey spare = loadedSpare("custom_001");
        pool.claim("Harbour", null, BART, "Bart");
        Files.writeString(pack.resolve(BiomeLabels.FILE), "{ not json");

        BiomeTuningException e = assertThrows(BiomeTuningException.class,
                () -> service.set(spare, "sky_color", "#2040ff"));

        assertTrue(e.getMessage().contains("can't tell whether mcme:custom_001 is claimed"), e.getMessage());
        assertTrue(e.getMessage().contains("edits not saved before a restart are lost"),
                "live edits wait with the spare, and a restart drops them: " + e.getMessage());
    }

    // a hand edit that breaks the labels file holds back the spares only: every other biome is tuned and saved as
    // before, as it has no claim to tell
    @Test
    void aBrokenLabelsFileHoldsBackOnlyTheSpares() throws Exception {
        NamespacedKey spare = loadedSpare("custom_001");
        Path cbc = pack.resolve("data/cbc/worldgen/biome/111g380-5p.json");
        Files.createDirectories(cbc.getParent());
        Files.writeString(cbc, SparePool.NEUTRAL);
        Files.writeString(pack.resolve(BiomeLabels.FILE), "{ not json");

        assertDoesNotThrow(() -> service.set(CBC, "sky_color", "#2040ff"), "a biome that is no spare is tuned");
        assertDoesNotThrow(() -> service.save(CBC, "Bart"), "and saved");

        assertTrue(Files.readString(cbc).contains("#2040ff"), "set and saved");
        BiomeTuningException e = assertThrows(BiomeTuningException.class,
                () -> service.set(spare, "sky_color", "#2040ff"));
        assertTrue(e.getMessage().contains("can't tell whether mcme:custom_001 is claimed"), e.getMessage());
    }

    // a spare id the server does not know, a typo or one waiting for a restart, is no free spare
    @Test
    void labelChecksTheBiomeBeforeTheLock() {
        BiomeTuningException e = assertThrows(BiomeTuningException.class,
                () -> pool.label(key("mcme:custom_050"), "Harbour", "Bart"));

        assertFalse(e.getMessage().contains("free spare"), "it says the biome is not there: " + e.getMessage());
    }
}
