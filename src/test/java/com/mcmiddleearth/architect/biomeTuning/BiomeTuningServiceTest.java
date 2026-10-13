package com.mcmiddleearth.architect.biomeTuning;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumSet;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Logger;
import org.bukkit.NamespacedKey;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.*;

class BiomeTuningServiceTest {

    private static final NamespacedKey CBC = NamespacedKey.fromString("cbc:111g380-5p");
    private static final NamespacedKey PLAINS = NamespacedKey.minecraft("plains");
    private static final String SAMPLE = "{\"attributes\":{\"minecraft:visual/sky_color\":\"#ffaa00\","
            + "\"minecraft:visual/fog_color\":\"#ffaa00\"},\"downfall\":0.8,\"effects\":{\"water_color\":\"#263a22\"},"
            + "\"temperature\":0.7}";

    @TempDir
    Path dir;
    private FakeNmsBridge bridge;
    private BiomeTuningService service;
    private Path file;

    @BeforeEach
    void setUp() throws Exception {
        file = dir.resolve("datapacks/bukkit/data/cbc/worldgen/biome/111g380-5p.json");
        Files.createDirectories(file.getParent());
        Files.writeString(file, SAMPLE);
        bridge = new FakeNmsBridge();
        service = new BiomeTuningService(bridge, dir.resolve("datapacks"), dir.resolve("backups"),
                BiomeTuningSettings.DEFAULTS, Logger.getLogger("test"));
    }

    private String applied(String attribute) {
        JsonObject attributes = bridge.applied.get(CBC).getAsJsonObject("attributes");
        return attributes.has(attribute) ? attributes.get(attribute).getAsString() : null;
    }

    @Test
    void setAppliesTheValidatedCandidateAndLeavesTheFileAlone() throws Exception {
        BiomeTuningService.Change change = service.set(CBC, "sky_color", "#2040ff");
        assertEquals("#ffaa00", change.before().getAsString());
        assertEquals("#2040ff", change.after().getAsString());
        assertEquals("#2040ff", applied("minecraft:visual/sky_color"));
        assertEquals(SAMPLE, Files.readString(file), "nothing is written until save");
        assertEquals(List.of(CBC), service.unsaved());
    }

    @Test
    void aValueTheCodecRejectsChangesNothing() {
        bridge.rejectContaining = "#000000";
        BiomeTuningException e = assertThrows(BiomeTuningException.class, () -> service.set(CBC, "sky_color", "#000000"));
        assertTrue(e.getMessage().contains("the codec says no"), e.getMessage());
        assertNull(bridge.applied.get(CBC));
        assertTrue(service.unsaved().isEmpty());
    }

    @Test
    void badInputIsExplained() {
        assertTrue(assertThrows(BiomeTuningException.class, () -> service.set(CBC, "sky_color", "blue"))
                .getMessage().contains("colour"));
        assertTrue(assertThrows(BiomeTuningException.class, () -> service.set(CBC, "sky_colour", "#ffffff"))
                .getMessage().contains("unknown property"));
    }

    // the spare pool locks its free spares: a claim hands a spare out as it is
    @Test
    void aLockedBiomeRefusesEveryChangeAndSaysWhy() throws Exception {
        bridge.encoded.put(PLAINS, JsonParser.parseString("{\"attributes\":{\"minecraft:visual/sky_color\":"
                + "\"#78a7ff\"},\"downfall\":0.4,\"temperature\":0.8}").getAsJsonObject());
        bridge.vanilla.add(PLAINS);
        service.lock(biome -> biome.equals(CBC) ? "locked, for the test" : null);

        for (Executable change : List.<Executable>of(
                () -> service.set(CBC, "sky_color", "#2040ff"),
                () -> service.unset(CBC, "fog_color"),
                () -> service.edit(CBC, List.of(new BiomeTuningService.Edit("sky_color", "#2040ff"))),
                () -> service.copy(PLAINS, CBC, EnumSet.allOf(TuningProperty.Group.class)))) {
            assertEquals("locked, for the test", assertThrows(BiomeTuningException.class, change).getMessage());
        }
        assertNull(bridge.applied.get(CBC), "nothing went live");
        assertTrue(service.unsaved().isEmpty(), "nothing to save");
        assertEquals("#ffaa00", service.value(CBC, TuningProperties.resolve("sky_color").orElseThrow()).getAsString(),
                "reading is still fine");
    }

    // a claim removed by hand frees a spare that still holds live edits: they may not be saved or stepped back through,
    // but revert, the way back to the file, stays open
    @Test
    void aLockedBiomeKeepsItsEditsUnsavedUntilItIsReverted() throws Exception {
        service.set(CBC, "sky_color", "#2040ff");
        service.lock(biome -> biome.equals(CBC) ? "locked, for the test" : null);

        assertEquals("locked, for the test",
                assertThrows(BiomeTuningException.class, () -> service.save(CBC, "test")).getMessage());
        assertEquals("locked, for the test", assertThrows(BiomeTuningException.class, () -> service.undo(CBC)).getMessage());
        assertEquals(SAMPLE, Files.readString(file), "nothing saved");
        assertEquals("#2040ff", applied("minecraft:visual/sky_color"), "nothing stepped back");

        service.revert(CBC);

        assertEquals("#ffaa00", applied("minecraft:visual/sky_color"), "back to the file");
        assertTrue(service.unsaved().isEmpty(), "nothing left to save");
    }

    @Test
    void vanillaBiomesAreRefused() {
        bridge.vanilla.add(PLAINS);
        BiomeTuningException e = assertThrows(BiomeTuningException.class, () -> service.set(PLAINS, "sky_color", "#2040ff"));
        assertTrue(e.getMessage().contains("clients use their own copy"), e.getMessage());
    }

    @Test
    void unsetUndoAndRevert() throws Exception {
        service.set(CBC, "fog_color", "#123456");
        service.unset(CBC, "fog_color");
        assertNull(applied("minecraft:visual/fog_color"));
        assertTrue(service.undo(CBC));
        assertEquals("#123456", applied("minecraft:visual/fog_color"));
        service.revert(CBC);
        assertEquals("#ffaa00", applied("minecraft:visual/fog_color"));
        assertTrue(service.unsaved().isEmpty());
        assertFalse(service.undo(CBC), "revert drops the undo history");
    }

    @Test
    void copyMakesTheChosenGroupsEqualToTheSource() throws Exception {
        bridge.encoded.put(PLAINS, JsonParser.parseString("{\"attributes\":{\"minecraft:visual/sky_color\":\"#78a7ff\"},"
                + "\"effects\":{\"water_color\":\"#3f76e4\"},\"temperature\":0.8}").getAsJsonObject());
        List<BiomeTuningService.Change> changes = service.copy(PLAINS, CBC,
                EnumSet.of(TuningProperty.Group.SKY, TuningProperty.Group.FOG));
        assertEquals("#78a7ff", applied("minecraft:visual/sky_color"));
        assertNull(applied("minecraft:visual/fog_color"), "plains has no fog colour, so it is removed");
        assertEquals("#263a22", bridge.applied.get(CBC).getAsJsonObject("effects").get("water_color").getAsString(),
                "water is not in the copied groups");
        assertEquals(List.of("sky_color", "fog_color"), changes.stream().map(change -> change.property().name())
                .toList(), "the changes come in catalogue order");
        JsonObject climate = new JsonObject();
        climate.addProperty("temperature", 0.7f); // the game's codec writes floats
        climate.addProperty("downfall", 0.8f);
        bridge.encoded.put(PLAINS, climate);
        assertEquals(List.of(), service.copy(PLAINS, CBC, EnumSet.of(TuningProperty.Group.CLIMATE)),
                "0.7f and 0.8f from the game equal the file's 0.7 and 0.8");
    }

    @Test
    void theDatapacksFolderIsLookedUpWhenFirstNeededNotWhenBuilt() throws Exception {
        // Architect loads at STARTUP, before any world exists: the folder is only known once worlds have loaded.
        AtomicReference<Path> folder = new AtomicReference<>();
        BiomeTuningService lazy = new BiomeTuningService(bridge, folder::get, dir.resolve("backups"),
                BiomeTuningSettings.DEFAULTS, Logger.getLogger("test"));
        assertTrue(assertThrows(BiomeTuningException.class, () -> lazy.set(CBC, "sky_color", "#2040ff"))
                .getMessage().contains("no datapacks folder"), "no world yet");
        folder.set(dir.resolve("datapacks"));
        assertEquals("#2040ff", lazy.set(CBC, "sky_color", "#2040ff").after().getAsString(), "the world has loaded");
    }

    @Test
    void saveWritesTheFileAndReportsTheChanges() throws Exception {
        service.set(CBC, "sky_color", "#2040ff");
        assertEquals(List.of("sky_color: #ffaa00 -> #2040ff"), service.save(CBC, "tester"));
        assertTrue(Files.readString(file).contains("#2040ff"));
        assertTrue(service.unsaved().isEmpty());
        assertTrue(Files.isDirectory(dir.resolve("backups/cbc/111g380-5p")));
    }

    @Test
    void editChangesSeveralValuesAsOneUndoStep() throws Exception {
        List<BiomeTuningService.Change> changes = service.edit(CBC, List.of(
                new BiomeTuningService.Edit("sky_color", "#010203"), new BiomeTuningService.Edit("fog_color", "#040506")));
        assertEquals(2, changes.size());
        assertEquals("#010203", applied("minecraft:visual/sky_color"));
        assertEquals("#040506", applied("minecraft:visual/fog_color"));
        assertTrue(service.undo(CBC));
        assertEquals("#ffaa00", applied("minecraft:visual/sky_color"), "one undo took back both values");
        assertEquals("#ffaa00", applied("minecraft:visual/fog_color"));
        assertFalse(service.undo(CBC), "exactly one undo step");
        List<BiomeTuningService.Change> twice = service.edit(CBC, List.of(
                new BiomeTuningService.Edit("sky_color", "#010203"),
                new BiomeTuningService.Edit("minecraft:visual/sky_color", "#040506")));
        assertEquals(1, twice.size(), "a value named twice changes once, to the last value");
        assertEquals("#040506", applied("minecraft:visual/sky_color"));
    }

    @Test
    void editSkipsEqualValuesAndNullRemovesOne() throws Exception {
        assertEquals(List.of(), service.edit(CBC, List.of(new BiomeTuningService.Edit("sky_color", "#FFAA00"))));
        assertNull(bridge.applied.get(CBC), "nothing changed, so nothing went live");
        List<BiomeTuningService.Change> changes = service.edit(CBC,
                List.of(new BiomeTuningService.Edit("fog_color", null)));
        assertNull(changes.get(0).after());
        assertNull(applied("minecraft:visual/fog_color"));
        assertEquals(List.of(), service.edit(CBC, List.of(new BiomeTuningService.Edit("fog_color", null))),
                "removing a value that is not set changes nothing");
        List<BiomeTuningService.Change> mixed = service.edit(CBC, List.of(
                new BiomeTuningService.Edit("sky_color", "#ffaa00"), new BiomeTuningService.Edit("fog_color", "#010203")));
        assertEquals(1, mixed.size(), "only the value that differs counts");
    }

    @Test
    void editIsAllOrNothing() {
        assertThrows(BiomeTuningException.class, () -> service.edit(CBC, List.of(
                new BiomeTuningService.Edit("sky_color", "#010203"),
                new BiomeTuningService.Edit("moon_phase", "blue_moon"))));
        assertNull(bridge.applied.get(CBC), "the valid value did not go live either");
        bridge.rejectContaining = "#040506";
        assertThrows(BiomeTuningException.class, () -> service.edit(CBC, List.of(
                new BiomeTuningService.Edit("sky_color", "#010203"), new BiomeTuningService.Edit("fog_color", "#040506"))));
        assertNull(bridge.applied.get(CBC), "nor when the game's codec refuses the whole");
        assertTrue(service.unsaved().isEmpty());
        assertThrows(NullPointerException.class, () -> new BiomeTuningService.Edit(null, "#010203"));
    }

    @Test
    void valueReadsAnyBiome() throws Exception {
        TuningProperty sky = TuningProperties.resolve("sky_color").orElseThrow();
        bridge.vanilla.add(PLAINS);
        bridge.encoded.put(PLAINS, JsonParser.parseString(
                "{\"attributes\":{\"minecraft:visual/sky_color\":\"#78a7ff\"}}").getAsJsonObject());
        assertEquals("#78a7ff", service.value(PLAINS, sky).getAsString(), "a vanilla biome answers from the game");
        assertNull(service.value(PLAINS, TuningProperties.resolve("fog_color").orElseThrow()), "a value left unset");
        service.set(CBC, "sky_color", "#2040ff");
        assertEquals("#2040ff", service.value(CBC, sky).getAsString(), "a biome being edited answers with its working copy");
    }
}
