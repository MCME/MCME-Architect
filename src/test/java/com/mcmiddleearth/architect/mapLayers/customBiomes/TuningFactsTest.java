package com.mcmiddleearth.architect.mapLayers.customBiomes;

import com.google.gson.JsonPrimitive;
import com.mcmiddleearth.architect.biomeTuning.BiomeDocument;
import com.mcmiddleearth.architect.biomeTuning.BiomeTuning;
import com.mcmiddleearth.architect.biomeTuning.BiomeTuningException;
import com.mcmiddleearth.architect.biomeTuning.TuningProperties;
import com.mcmiddleearth.architect.biomeTuning.TuningProperty;
import com.mcmiddleearth.architect.biomeTuning.TuningProperty.Group;
import com.mcmiddleearth.architect.mapLayers.customBiomes.BiomeFacts.Setting;
import com.mcmiddleearth.architect.mapLayers.customBiomes.BiomeFacts.Settings;
import org.bukkit.NamespacedKey;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

// The window shows what a biome's own definition sets, as biome tuning holds it right now, grouped as the in-game
// editor groups it. These tests make a biome document from a JSON file on disk, as biome tuning does, and read it back.
class TuningFactsTest {

    private static final String ID = "cbc:111g380-5p";
    private static final NamespacedKey KEY = NamespacedKey.fromString(ID);
    private static final List<String> HEADINGS = List.of("Sky and fog", "Water and plants", "Climate",
            "Sound and particles");

    @TempDir
    Path dir;

    /** JSON written with single quotes, to keep the tests readable. */
    private static String q(String json) {
        return json.replace('\'', '"');
    }

    /** A biome document loaded from a file holding {@code json}, so that it counts as saved. */
    private BiomeDocument document(String json) throws Exception {
        Path file = dir.resolve("111g380-5p.json");
        Files.writeString(file, json);
        return BiomeDocument.load(KEY, file, 5);
    }

    /** A saved document that sets these properties, given as name, text pairs as a builder would type them. */
    private BiomeDocument documentWith(String... namesAndTexts) throws Exception {
        BiomeDocument draft = document("{}");
        for (int i = 0; i < namesAndTexts.length; i += 2) {
            TuningProperty property = TuningProperties.resolve(namesAndTexts[i]).orElseThrow();
            draft.accept(draft.with(property, TuningProperties.parse(property, namesAndTexts[i + 1])));
        }
        return document(draft.current().toString());
    }

    private static TuningFacts factsOf(BiomeDocument document) {
        return new TuningFacts(biome -> Optional.empty(), biome -> document, () -> true);
    }

    private static Settings settingsOf(BiomeDocument document) {
        Settings settings = factsOf(document).settings(ID);
        assertNotNull(settings);
        return settings;
    }

    private static List<String> headings(Settings settings) {
        return List.copyOf(settings.groups().keySet());
    }

    private static List<String> names(Settings settings, String heading) {
        return settings.groups().get(heading).stream().map(Setting::name).toList();
    }

    private static List<String> texts(Settings settings, String heading) {
        return settings.groups().get(heading).stream().map(Setting::text).toList();
    }

    // --- what is shown ---

    @Test
    void settingsComeFromTheDocumentAndSkipUnsetValues() throws Exception {
        Settings settings = settingsOf(document(q("{'attributes':{'minecraft:visual/sky_color':'#78a7ff'},"
                + "'temperature':0.8}")));

        assertEquals(List.of("Sky and fog", "Climate"), headings(settings));
        assertEquals(List.of(new Setting("sky", "#78a7ff", 0x78a7ff)), settings.groups().get("Sky and fog"));
        assertEquals(List.of(new Setting("temperature", "0.8", null)), settings.groups().get("Climate"));
        assertFalse(settings.unsaved());
    }

    @Test
    void aBiomeThatSetsNothingHasNoGroupsButHasSettings() throws Exception {
        Settings settings = settingsOf(document("{}"));

        assertEquals(Map.of(), settings.groups());
        assertFalse(settings.unsaved());
    }

    @Test
    void numbersAreRoundedAndBooleansSayOnOrOff() throws Exception {
        Settings settings = settingsOf(document(q("{'temperature':0.800000011920929,'has_precipitation':true}")));

        assertEquals(List.of(new Setting("temperature", "0.8", null), new Setting("rain", "on", null)),
                settings.groups().get("Climate"));
    }

    @Test
    void aSettingThatIsOffSaysOff() throws Exception {
        Settings settings = settingsOf(document(q("{'has_precipitation':false,"
                + "'attributes':{'minecraft:audio/firefly_bush_sounds':false}}")));

        assertEquals(List.of(new Setting("rain", "off", null)), settings.groups().get("Climate"));
        assertEquals(List.of(new Setting("firefly bush sounds", "off", null)),
                settings.groups().get("Sound and particles"));
    }

    @Test
    void anUnsavedDocumentIsFlagged() throws Exception {
        BiomeDocument document = document(q("{'attributes':{'minecraft:visual/sky_color':'#78a7ff'}}"));
        TuningFacts facts = factsOf(document);
        assertFalse(facts.settings(ID).unsaved());

        TuningProperty sky = TuningProperties.resolve("sky_color").orElseThrow();
        document.accept(document.with(sky, new JsonPrimitive("#000001")));

        Settings changed = facts.settings(ID);
        assertTrue(changed.unsaved());
        assertEquals(List.of(new Setting("sky", "#000001", 1)), changed.groups().get("Sky and fog"));
    }

    @Test
    void valuesAreReadLiveEachTimeSettingsAreAsked() throws Exception {
        BiomeDocument document = document(q("{'temperature':0.8}"));
        TuningFacts facts = factsOf(document);
        assertEquals(List.of("0.8"), texts(facts.settings(ID), "Climate"));

        TuningProperty temperature = TuningProperties.resolve("temperature").orElseThrow();
        document.accept(document.with(temperature, new JsonPrimitive(2)));

        assertEquals(List.of("2"), texts(facts.settings(ID), "Climate"));
    }

    @Test
    void aDocumentThatCannotBeLoadedHasNoSettings() {
        TuningFacts facts = new TuningFacts(biome -> Optional.empty(), biome -> null, () -> true);

        assertNull(facts.settings(ID));
    }

    @Test
    void withoutBiomeTuningThereAreNoSettings() {
        TuningFacts facts = new TuningFacts(biome -> Optional.empty(), biome -> {
            throw new AssertionError("biome tuning is off, so nothing may be asked of it");
        }, () -> false);

        assertNull(facts.settings(ID));
    }

    @Test
    void theDocumentIsAskedForByTheBiomeIdItWasGiven() throws Exception {
        List<String> asked = new ArrayList<>();
        BiomeDocument document = document(q("{'temperature':0.8}"));
        TuningFacts facts = new TuningFacts(biome -> Optional.empty(), biome -> {
            asked.add(biome);
            return document;
        }, () -> true);

        facts.settings("cbc:one");
        facts.settings("cbc:two");

        assertEquals(List.of("cbc:one", "cbc:two"), asked);
    }

    // --- groups and their order ---

    @Test
    void groupsHaveTheEditorsHeadingsInDisplayOrderWhateverTheOrderInTheFile() throws Exception {
        // written backwards: sound first, sky last
        Settings settings = settingsOf(document(q("{'attributes':{"
                + "'minecraft:audio/music_volume':0.5,"
                + "'minecraft:visual/sky_color':'#000001'},"
                + "'effects':{'water_color':'#000002'},'temperature':2,'downfall':0.4}")));

        assertEquals(HEADINGS, headings(settings));
        assertEquals(List.of("sky"), names(settings, "Sky and fog"));
        assertEquals(List.of("water"), names(settings, "Water and plants"));
        assertEquals(List.of("temperature", "downfall"), names(settings, "Climate"));
        assertEquals(List.of("music volume"), names(settings, "Sound and particles"));
    }

    @Test
    void fogSharesAHeadingWithTheSkyAndComesAfterIt() throws Exception {
        Settings settings = settingsOf(documentWith("fog_color", "#000003", "fog_start", "8", "sky_color", "#000001",
                "moon_phase", "new_moon", "fog_end", "40", "sunrise_color", "#80000002"));

        assertEquals(List.of("Sky and fog"), headings(settings));
        assertEquals(List.of("sky", "sunrise", "moon phase", "fog", "fog start", "fog end"),
                names(settings, "Sky and fog"));
    }

    @Test
    void particlesShareAHeadingWithSoundAndComeAfterIt() throws Exception {
        Settings settings = settingsOf(documentWith(
                "dripstone_particle", "{\"type\":\"minecraft:dripping_lava\"}",
                "ambient_particles", "[{\"particle\":{\"type\":\"minecraft:ash\"},\"probability\":0.1}]",
                "firefly_bush_sounds", "true", "music_volume", "0.5",
                "ambient_sounds", "{\"loop\":\"minecraft:ambient.cave\"}",
                "music", "{\"default\":{\"sound\":\"minecraft:m\",\"min_delay\":20,\"max_delay\":40}}"));

        assertEquals(List.of("Sound and particles"), headings(settings));
        assertEquals(List.of("music", "music volume", "ambient sounds", "firefly bush sounds", "ambient particles",
                "dripstone particle"), names(settings, "Sound and particles"));
    }

    @Test
    void waterAndPlantsHoldWaterGrassFoliageAndTheirModifier() throws Exception {
        Settings settings = settingsOf(documentWith("grass_modifier", "swamp", "foliage_color", "#000003",
                "water_fog_end", "30", "water_color", "#000001", "dry_foliage_color", "#000004",
                "grass_color", "#000002", "water_fog_color", "#000005", "water_fog_start", "-8"));

        assertEquals(List.of("Water and plants"), headings(settings));
        assertEquals(List.of("water", "water fog", "water fog start", "water fog end", "grass", "foliage",
                "dry foliage", "grass modifier"), names(settings, "Water and plants"));
    }

    @Test
    void climateHoldsTemperatureDownfallRainAndTheModifier() throws Exception {
        Settings settings = settingsOf(documentWith("temperature_modifier", "frozen", "precipitation", "true",
                "downfall", "0.4", "temperature", "0.5"));

        assertEquals(List.of("Climate"), headings(settings));
        assertEquals(List.of("temperature", "downfall", "rain", "temperature modifier"), names(settings, "Climate"));
    }

    @Test
    void aValueThatIsNullInTheFileCountsAsNotSet() throws Exception {
        Settings settings = settingsOf(document(q("{'temperature':null,'downfall':0.4,"
                + "'attributes':{'minecraft:visual/sky_color':null}}")));

        assertEquals(List.of("Climate"), headings(settings));
        assertEquals(List.of("downfall"), names(settings, "Climate"));
    }

    // --- names ---

    @Test
    void namesDropTheColourSuffixAndSpaceOutUnderscores() throws Exception {
        Settings settings = settingsOf(documentWith("sky_color", "#000001", "sunrise_color", "#80000002",
                "block_light_tint", "#000003", "night_vision_color", "#000004", "sky_light_factor", "0.5",
                "water_fog_color", "#000005", "dry_foliage_color", "#000006", "temperature_modifier", "frozen",
                "music_volume", "0.5", "firefly_bush_sounds", "true",
                "dripstone_particle", "{\"type\":\"minecraft:ash\"}"));

        assertEquals(List.of("sky", "sunrise", "sky light factor", "block light tint", "night vision"),
                names(settings, "Sky and fog"));
        assertEquals(List.of("water fog", "dry foliage"), names(settings, "Water and plants"));
        assertEquals(List.of("temperature modifier"), names(settings, "Climate"));
        assertEquals(List.of("music volume", "firefly bush sounds", "dripstone particle"),
                names(settings, "Sound and particles"));
    }

    @Test
    void precipitationIsCalledRain() throws Exception {
        Settings settings = settingsOf(documentWith("precipitation", "false"));

        assertEquals(List.of(new Setting("rain", "off", null)), settings.groups().get("Climate"));
    }

    // --- values ---

    @Test
    void colourValuesShowTheirHexAndTheirRgb() throws Exception {
        Settings settings = settingsOf(documentWith("sky_color", "#78A7FF", "fog_color", "#000000",
                "block_light_tint", "#ffffff"));

        assertEquals(List.of(new Setting("sky", "#78a7ff", 0x78a7ff),
                new Setting("block light tint", "#ffffff", 0xffffff), new Setting("fog", "#000000", 0)),
                settings.groups().get("Sky and fog"));
    }

    @Test
    void colourValuesWithAlphaShowAlphaFirstAndKeepTheLow24BitsAsRgb() throws Exception {
        Settings settings = settingsOf(documentWith("sunrise_color", "#80ff8040", "cloud_color", "#ffffffff"));

        assertEquals(List.of(new Setting("sunrise", "#80ff8040", 0xff8040),
                new Setting("cloud", "#ffffffff", 0xffffff)), settings.groups().get("Sky and fog"));
    }

    @Test
    void anAlphaColourWrittenWithoutAlphaIsShownWithFullAlpha() throws Exception {
        // the game reads "#rrggbb" as opaque
        Settings settings = settingsOf(document(q("{'attributes':"
                + "{'minecraft:visual/sunrise_sunset_color':'#102030'}}")));

        assertEquals(List.of(new Setting("sunrise", "#ff102030", 0x102030)), settings.groups().get("Sky and fog"));
    }

    @Test
    void numbersAreRoundedToThreeDecimalsWithoutTrailingZeros() throws Exception {
        String[][] numbers = {{"8", "8"}, {"8.0", "8"}, {"0.5", "0.5"}, {"0.12345", "0.123"}, {"0.1236", "0.124"},
                {"0.0625", "0.063"}, {"100", "100"}, {"0", "0"}, {"-0.25", "-0.25"}, {"-0.0004", "0"},
                {"1.0E-4", "0"}, {"2.50", "2.5"}, {"1200", "1200"}, {"1e3", "1000"}, {"0.800000011920929", "0.8"},
                {"0.9999", "1"}, {"10.0005", "10.001"}};
        for (String[] number : numbers) {
            Settings settings = settingsOf(document("{\"temperature\":" + number[0] + "}"));
            assertEquals(List.of(number[1]), texts(settings, "Climate"), number[0]);
        }
    }

    @Test
    void choicesShowTheirValue() throws Exception {
        Settings settings = settingsOf(documentWith("moon_phase", "waning_gibbous", "grass_modifier", "dark_forest",
                "temperature_modifier", "none"));

        assertEquals(List.of(new Setting("moon phase", "waning_gibbous", null)), settings.groups().get("Sky and fog"));
        assertEquals(List.of(new Setting("grass modifier", "dark_forest", null)),
                settings.groups().get("Water and plants"));
        assertEquals(List.of(new Setting("temperature modifier", "none", null)), settings.groups().get("Climate"));
    }

    @Test
    void structuredSettingsShowTheirSummaryLine() throws Exception {
        Settings settings = settingsOf(documentWith(
                "music", "{\"default\":{\"sound\":\"minecraft:music.overworld.ocean\",\"min_delay\":6000,"
                        + "\"max_delay\":12000}}",
                "ambient_sounds", "{\"loop\":\"minecraft:ambient.cave\",\"mood\":{\"sound\":\"minecraft:ambient.cave\","
                        + "\"tick_delay\":6000},\"additions\":{\"sound\":{\"sound_id\":\"mcme:gulls\"},"
                        + "\"tick_chance\":0.01}}",
                "ambient_particles", "[{\"particle\":{\"type\":\"minecraft:white_ash\"},\"probability\":0.005}]",
                "dripstone_particle", "{\"type\":\"minecraft:dripping_lava\"}"));

        assertEquals(List.of(
                new Setting("music", "minecraft:music.overworld.ocean, every 300 to 600 s", null),
                new Setting("ambient sounds",
                        "loop minecraft:ambient.cave, mood minecraft:ambient.cave, extra mcme:gulls", null),
                new Setting("ambient particles", "minecraft:white_ash 0.5%", null),
                new Setting("dripstone particle", "minecraft:dripping_lava", null)),
                settings.groups().get("Sound and particles"));
    }

    @Test
    void structuredSettingsInAShapeNobodyExpectedAreCustom() throws Exception {
        Settings settings = settingsOf(documentWith("music", "[1,2]", "ambient_particles", "{\"a\":1}"));

        assertEquals(List.of(new Setting("music", "custom", null), new Setting("ambient particles", "custom", null)),
                settings.groups().get("Sound and particles"));
    }

    @Test
    void aValueInTheWrongShapeIsShownAsItIsAndDoesNotBreakTheWindow() throws Exception {
        // the game also reads a colour as an int, but nothing here writes one; a hand-edited file may hold anything
        Settings settings = settingsOf(document(q("{'attributes':{'minecraft:visual/sky_color':4159204,"
                + "'minecraft:visual/fog_color':'red','minecraft:visual/sunrise_sunset_color':'#fff',"
                + "'minecraft:visual/fog_start_distance':'near','minecraft:audio/firefly_bush_sounds':'yes'},"
                + "'temperature':[1],'has_precipitation':1,'temperature_modifier':7}")));

        assertEquals(List.of(new Setting("sky", "4159204", null), new Setting("sunrise", "#fff", null),
                new Setting("fog", "red", null), new Setting("fog start", "near", null)),
                settings.groups().get("Sky and fog"));
        assertEquals(List.of(new Setting("temperature", "[1]", null), new Setting("rain", "1", null),
                new Setting("temperature modifier", "7", null)), settings.groups().get("Climate"));
        assertEquals(List.of(new Setting("firefly bush sounds", "yes", null)),
                settings.groups().get("Sound and particles"));
    }

    @Test
    void aNumberTooBigToWriteOutIsShownAsItIsAndDoesNotThrow() throws Exception {
        Settings settings = settingsOf(document("{\"temperature\":1e999999999}"));

        assertEquals(List.of("1e999999999"), texts(settings, "Climate"));
    }

    // --- the whole catalogue ---

    @Test
    void everyValueOfTheCatalogueIsShownOnceUnderItsHeadingInCatalogueOrder() throws Exception {
        List<String> raw = new ArrayList<>();
        for (TuningProperty property : TuningProperties.all()) {
            raw.add(property.name());
            raw.add(sample(property));
        }
        Settings settings = settingsOf(documentWith(raw.toArray(new String[0])));

        Map<String, Set<Group>> shown = new LinkedHashMap<>();
        shown.put("Sky and fog", Set.of(Group.SKY, Group.FOG));
        shown.put("Water and plants", Set.of(Group.WATER));
        shown.put("Climate", Set.of(Group.CLIMATE));
        shown.put("Sound and particles", Set.of(Group.AUDIO, Group.PARTICLES));
        assertEquals(HEADINGS, headings(settings));
        int total = 0;
        for (Map.Entry<String, Set<Group>> heading : shown.entrySet()) {
            List<String> expected = TuningProperties.all().stream().filter(p -> heading.getValue().contains(p.group()))
                    .map(TuningFactsTest::expectedName).toList();
            assertEquals(expected, names(settings, heading.getKey()), heading.getKey());
            total += expected.size();
        }
        assertEquals(TuningProperties.all().size(), total, "every property of the catalogue has a heading");
        assertFalse(settings.unsaved());
    }

    @Test
    void everyKindOfValueHasItsOwnTextInTheWholeCatalogue() throws Exception {
        List<String> raw = new ArrayList<>();
        for (TuningProperty property : TuningProperties.all()) {
            raw.add(property.name());
            raw.add(sample(property));
        }
        Settings settings = settingsOf(documentWith(raw.toArray(new String[0])));

        for (List<Setting> group : settings.groups().values()) {
            for (Setting setting : group) {
                assertFalse(setting.name().isBlank());
                assertFalse(setting.name().contains("_"), setting.name());
                assertFalse(setting.name().endsWith(" color"), setting.name());
                assertFalse(setting.text().isBlank(), setting.name());
                assertNotEquals("custom", setting.text(), setting.name() + " is in the shape the editor writes");
            }
        }
        for (String colour : List.of("sky", "fog", "water", "grass")) {
            Setting found = find(settings, colour);
            assertEquals("#102030", found.text());
            assertEquals(0x102030, found.rgb());
        }
        assertEquals("#80102030", find(settings, "sunrise").text());
        assertEquals(0x102030, find(settings, "sunrise").rgb());
        assertEquals("2.5", find(settings, "cloud height").text());
        assertEquals("on", find(settings, "rain").text());
    }

    /** The name the window must give a property: precipitation is rain, a trailing _color goes, _ is a space. */
    private static String expectedName(TuningProperty property) {
        return property.name().equals("precipitation") ? "rain"
                : property.name().replaceFirst("_color$", "").replace('_', ' ');
    }

    /** A value of each kind that the editor itself would write, as a builder would type it. */
    private static String sample(TuningProperty property) {
        return switch (property.kind()) {
            case RGB -> "#102030";
            case ARGB -> "#80102030";
            case NUMBER -> "2.5";
            case BOOLEAN -> "true";
            case CHOICE -> property.choices().get(0);
            case JSON -> switch (property.name()) {
                case "music" -> "{\"default\":{\"sound\":\"minecraft:music.overworld.ocean\",\"min_delay\":6000,"
                        + "\"max_delay\":12000}}";
                case "ambient_sounds" -> "{\"loop\":\"minecraft:ambient.cave\"}";
                case "ambient_particles" -> "[{\"particle\":{\"type\":\"minecraft:white_ash\"},\"probability\":0.005}]";
                default -> "{\"type\":\"minecraft:dripping_lava\"}";
            };
        };
    }

    private static Setting find(Settings settings, String name) {
        return settings.groups().values().stream().flatMap(List::stream).filter(s -> s.name().equals(name)).findFirst()
                .orElseThrow(() -> new AssertionError("no setting named " + name + " in " + settings));
    }

    // --- names of biomes ---

    @Test
    void aLabelComesFromTheLabelFunction() {
        List<String> asked = new ArrayList<>();
        TuningFacts facts = new TuningFacts(biome -> {
            asked.add(biome);
            return Optional.of("Old harbour");
        }, biome -> null, () -> true);

        assertEquals("Old harbour", facts.label(ID));
        assertEquals(List.of(ID), asked);
    }

    @Test
    void aBiomeWithoutALabelHasNone() {
        assertNull(new TuningFacts(biome -> Optional.empty(), biome -> null, () -> true).label(ID));
    }

    @Test
    void aLabelFunctionThatCannotAnswerGivesNoLabel() {
        assertNull(new TuningFacts(biome -> null, biome -> null, () -> true).label(ID));
    }

    @Test
    void withoutBiomeTuningThereAreNoLabelsAndNothingIsAsked() {
        TuningFacts facts = new TuningFacts(biome -> {
            throw new AssertionError("biome tuning is off, so nothing may be asked of it");
        }, biome -> null, () -> false);

        assertNull(facts.label(ID));
    }

    // --- the real biome tuning ---

    @Test
    void whileBiomeTuningIsOffTheRealFactsHaveNothingToSayAndAskNothing() {
        assertFalse(BiomeTuning.isAvailable(), "a fresh JVM per test class: nothing has switched biome tuning on");
        TuningFacts facts = TuningFacts.fromBiomeTuning();

        assertNull(facts.settings(ID));
        assertNull(facts.label(ID));
    }

    @Test
    void aFailureInBiomeTuningGivesNoSettingsAndNoLabel() {
        TuningFacts facts = TuningFacts.reading(key -> {
            throw new BiomeTuningException("mcme-biometune.json could not be read");
        }, key -> {
            throw new BiomeTuningException("no datapack defines " + key);
        }, () -> true);

        assertNull(facts.label(ID));
        assertNull(facts.settings(ID));
    }

    @Test
    void biomeTuningIsAskedWithTheBiomeAsAKey() throws Exception {
        List<NamespacedKey> labelsFor = new ArrayList<>();
        List<NamespacedKey> documentsFor = new ArrayList<>();
        BiomeDocument document = document(q("{'temperature':0.8}"));
        TuningFacts facts = TuningFacts.reading(key -> {
            labelsFor.add(key);
            return Optional.of("Old harbour");
        }, key -> {
            documentsFor.add(key);
            return document;
        }, () -> true);

        assertEquals("Old harbour", facts.label(ID));
        assertEquals(List.of("0.8"), texts(facts.settings(ID), "Climate"));
        assertEquals(List.of(KEY), labelsFor);
        assertEquals(List.of(KEY), documentsFor);
    }

    @Test
    void anIdThatIsNotAKeyHasNoLabelAndNoSettingsAndIsNeverAsked() {
        AtomicInteger asked = new AtomicInteger();
        TuningFacts facts = TuningFacts.reading(key -> {
            asked.incrementAndGet();
            return Optional.of("x");
        }, key -> {
            asked.incrementAndGet();
            return null;
        }, () -> true);

        assertNull(facts.label("Not A Key"));
        assertNull(facts.settings("Not A Key"));
        assertEquals(0, asked.get());
    }

    @Test
    void aLabelBiomeTuningDoesNotHaveIsNoLabel() {
        TuningFacts facts = TuningFacts.reading(key -> Optional.empty(), key -> null, () -> true);

        assertNull(facts.label(ID));
    }

    @Test
    void biomeTuningThatIsOffIsNotAskedAnything() {
        TuningFacts facts = TuningFacts.reading(key -> {
            throw new AssertionError("asked for a label");
        }, key -> {
            throw new AssertionError("asked for a document");
        }, () -> false);

        assertNull(facts.label(ID));
        assertNull(facts.settings(ID));
    }
}
