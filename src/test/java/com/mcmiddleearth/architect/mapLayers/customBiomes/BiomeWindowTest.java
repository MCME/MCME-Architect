package com.mcmiddleearth.architect.mapLayers.customBiomes;

import com.mcmiddleearth.architect.mapLayers.customBiomes.BiomeFacts.Setting;
import com.mcmiddleearth.architect.mapLayers.customBiomes.BiomeFacts.Settings;
import com.mcmiddleearth.architect.mapLayers.customBiomes.BiomeWindow.Section;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

// The window is what a staff member reads on clicking a painted spot, and the size of the map's download is worked out
// from its bytes, so the markup is pinned exactly. Names are typed by builders, so every text must come out escaped.
class BiomeWindowTest {

    private static final Settings NO_SETTINGS = new Settings(Map.of(), false);
    /** The first three colours of the palette. */
    private static final int RED = 0xe6194b;
    private static final int GREEN = 0x3cb44b;
    private static final int YELLOW = 0xffe119;
    /** The header of the old harbour: a named biome that is painted in 2 places and 180 chunks, from Y 40 to 120. */
    private static final String HARBOUR = header("#fbdce4", "#e6194b", "Old harbour",
            "<code style=\"color:#777\">cbc:111g380-5p</code> ",
            "Y 40&ndash;120 &middot; 2 places &middot; ~180 chunks");

    /** One heading and the settings under it. */
    private record Group(String heading, List<Setting> settings) {
    }

    private static Group group(String heading, Setting... settings) {
        return new Group(heading, List.of(settings));
    }

    /** A setting without a colour. */
    private static Setting item(String name, String text) {
        return new Setting(name, text, null);
    }

    /** A colour setting, with its value written as #rrggbb. */
    private static Setting colour(String name, int rgb) {
        return new Setting(name, String.format("#%06x", rgb), rgb);
    }

    /** The settings of a biome, with its groups in the order given. */
    private static Settings settings(boolean unsaved, Group... groups) {
        Map<String, List<Setting>> byHeading = new LinkedHashMap<>();
        for (Group group : groups) {
            byHeading.put(group.heading(), group.settings());
        }
        return new Settings(byHeading, unsaved);
    }

    /** A section of the old harbour: 2 places and 180 chunks, painted from Y 40 to 120, in the first colour. */
    private static Section harbour(Settings settings) {
        return new Section("cbc:111g380-5p", "Old harbour", 40, 120, 2, 180, settings, RED);
    }

    // The pieces of the markup, each spelled once out in full

    private static String window(String... blocks) {
        return "<div style=\"font:12px/1.35 sans-serif;color:#222\">" + String.join("", blocks) + "</div>";
    }

    /** The header of a biome; {@code code} is the id in its own element, or nothing, and {@code facts} the rest. */
    private static String header(String tint, String colour, String name, String code, String facts) {
        return "<div style=\"background:" + tint + ";border-left:5px solid " + colour + ";padding:4px 7px\"><b>"
                + name + "</b> " + code + "<span style=\"color:#777;font-size:11px\">&nbsp;" + facts + "</span></div>";
    }

    /** The table of a biome's settings, from the rows given. */
    private static String rows(String... settings) {
        return "<table style=\"border-collapse:collapse;font:12px/1.35 sans-serif;color:#222;width:100%\">"
                + String.join("", settings) + "</table>";
    }

    /** A row of a table of settings after the first. */
    private static String setting(String label, String value) {
        return "<tr><td style=\"color:#888;padding:0 10px 0 7px;white-space:nowrap;vertical-align:top\">" + label
                + "</td><td>" + value + "</td></tr>";
    }

    /** The first row of a table of settings: its name cell sets the width of the column of names. */
    private static String firstSetting(String label, String value) {
        return "<tr><td style=\"color:#888;padding:0 10px 0 7px;white-space:nowrap;vertical-align:top;"
                + "width:112px\">" + label + "</td><td>" + value + "</td></tr>";
    }

    private static String swatch(String colour) {
        return "<span style=\"color:" + colour + "\">&#9632;</span> " + colour;
    }

    private static String edit(String id, boolean last) {
        return "<div style=\"padding:3px 7px " + (last ? "0" : "8px")
                + ";font:11px monospace;color:#666\">/biometune edit " + id + "</div>";
    }

    /** The window of one biome that has no settings to show. */
    private static String bare(String id, String name, String code, String facts) {
        return window(header("#fbdce4", "#e6194b", name, code, facts), edit(id, true));
    }

    // --- the layout ---

    @Test
    void oneBiomeIsAHeaderItsSettingRowsAndTheEditCommandWithoutACountLine() {
        Settings settings = settings(false,
                group("Sky and fog", colour("sky", 0x2040ff), colour("fog", 0xffaa00)),
                group("Climate", item("temperature", "0.7"), item("downfall", "0.8"), item("rain", "on")),
                group("Sound and particles",
                        item("music", "minecraft:music.nether.basalt_deltas, every 600 to 1200 s"),
                        item("ambient particles", "minecraft:crimson_spore 2.5%")));

        assertEquals("<div style=\"font:12px/1.35 sans-serif;color:#222\">"
                + "<div style=\"background:#fbdce4;border-left:5px solid #e6194b;padding:4px 7px\">"
                + "<b>Old harbour</b> <code style=\"color:#777\">cbc:111g380-5p</code> "
                + "<span style=\"color:#777;font-size:11px\">&nbsp;Y 40&ndash;120 &middot; 2 places &middot; "
                + "~180 chunks</span></div>"
                + "<table style=\"border-collapse:collapse;font:12px/1.35 sans-serif;color:#222;width:100%\">"
                + "<tr><td style=\"color:#888;padding:0 10px 0 7px;white-space:nowrap;"
                + "vertical-align:top;width:112px\">Sky</td>"
                + "<td><span style=\"color:#2040ff\">&#9632;</span> #2040ff</td></tr>"
                + "<tr><td style=\"color:#888;padding:0 10px 0 7px;white-space:nowrap;"
                + "vertical-align:top\">Fog</td>"
                + "<td><span style=\"color:#ffaa00\">&#9632;</span> #ffaa00</td></tr>"
                + "<tr><td style=\"color:#888;padding:0 10px 0 7px;white-space:nowrap;"
                + "vertical-align:top\">Climate"
                + "</td><td>temperature 0.7 &middot; downfall 0.8 &middot; rain on</td></tr>"
                + "<tr><td style=\"color:#888;padding:0 10px 0 7px;white-space:nowrap;"
                + "vertical-align:top\">Music</td>"
                + "<td>music.nether.basalt_deltas, every 600 to 1200 s</td></tr>"
                + "<tr><td style=\"color:#888;padding:0 10px 0 7px;white-space:nowrap;vertical-align:top\">"
                + "Ambient particles</td><td>crimson_spore 2.5%</td></tr>"
                + "</table>"
                + "<div style=\"padding:3px 7px 0;font:11px monospace;color:#666\">"
                + "/biometune edit cbc:111g380-5p</div>"
                + "</div>", BiomeWindow.html(List.of(harbour(settings))));
    }

    @Test
    void aStackedSpotOfThreeHasACountLineAndOneBlockForEachBiomeTopFirst() {
        Settings first = settings(false, group("Sky and fog", colour("sky", 0x2040ff)),
                group("Climate", item("temperature", "0.7"), item("downfall", "0.8"), item("rain", "on")));
        Settings second = settings(false, group("Sky and fog", colour("sky", 0x78a7ff)));
        Settings third = settings(false, group("Water and plants", colour("water", 0x3f76e4)));

        String html = BiomeWindow.html(List.of(
                new Section("cbc:111g380-5p", null, -64, 319, 8, 1574, first, RED),
                new Section("mcme:custom_003", null, 60, 123, 1, 2, second, GREEN),
                new Section("mcme:custom_002", null, -40, 3, 2, 6, third, YELLOW)));

        assertEquals("<div style=\"font:12px/1.35 sans-serif;color:#222\">"
                + "<div style=\"padding:0 0 6px;font-size:11px;color:#777\">3 custom biomes here, top first</div>"

                + "<div style=\"background:#fbdce4;border-left:5px solid #e6194b;padding:4px 7px\">"
                + "<b>cbc:111g380-5p</b> <span style=\"color:#777;font-size:11px\">&nbsp;Y &minus;64&ndash;319 "
                + "&middot; 8 places &middot; ~1574 chunks</span></div>"
                + "<table style=\"border-collapse:collapse;font:12px/1.35 sans-serif;color:#222;width:100%\">"
                + "<tr><td style=\"color:#888;padding:0 10px 0 7px;white-space:nowrap;"
                + "vertical-align:top;width:112px\">Sky</td>"
                + "<td><span style=\"color:#2040ff\">&#9632;</span> #2040ff</td></tr>"
                + "<tr><td style=\"color:#888;padding:0 10px 0 7px;white-space:nowrap;"
                + "vertical-align:top\">Climate"
                + "</td><td>temperature 0.7 &middot; downfall 0.8 &middot; rain on</td></tr>"
                + "</table>"
                + "<div style=\"padding:3px 7px 8px;font:11px monospace;color:#666\">"
                + "/biometune edit cbc:111g380-5p</div>"

                + "<div style=\"background:#e2f4e4;border-left:5px solid #3cb44b;padding:4px 7px\">"
                + "<b>mcme:custom_003</b> <span style=\"color:#777;font-size:11px\">&nbsp;Y 60&ndash;123 "
                + "&middot; 1 place &middot; ~2 chunks</span></div>"
                + "<table style=\"border-collapse:collapse;font:12px/1.35 sans-serif;color:#222;width:100%\">"
                + "<tr><td style=\"color:#888;padding:0 10px 0 7px;white-space:nowrap;"
                + "vertical-align:top;width:112px\">Sky</td>"
                + "<td><span style=\"color:#78a7ff\">&#9632;</span> #78a7ff</td></tr>"
                + "</table>"
                + "<div style=\"padding:3px 7px 8px;font:11px monospace;color:#666\">"
                + "/biometune edit mcme:custom_003</div>"

                + "<div style=\"background:#fffadc;border-left:5px solid #ffe119;padding:4px 7px\">"
                + "<b>mcme:custom_002</b> <span style=\"color:#777;font-size:11px\">&nbsp;Y &minus;40&ndash;3 "
                + "&middot; 2 places &middot; ~6 chunks</span></div>"
                + "<table style=\"border-collapse:collapse;font:12px/1.35 sans-serif;color:#222;width:100%\">"
                + "<tr><td style=\"color:#888;padding:0 10px 0 7px;white-space:nowrap;"
                + "vertical-align:top;width:112px\">Water"
                + "</td><td><span style=\"color:#3f76e4\">&#9632;</span> #3f76e4</td></tr>"
                + "</table>"
                + "<div style=\"padding:3px 7px 0;font:11px monospace;color:#666\">"
                + "/biometune edit mcme:custom_002</div>"
                + "</div>", html);
    }

    @Test
    void twoStackedBiomesAreCountedToo() {
        String html = BiomeWindow.html(List.of(new Section("cbc:a", "A", 0, 1, 1, 1, NO_SETTINGS, RED),
                new Section("cbc:b", "B", 0, 1, 1, 1, NO_SETTINGS, GREEN)));

        assertTrue(html.contains("<div style=\"padding:0 0 6px;font-size:11px;color:#777\">"
                + "2 custom biomes here, top first</div>"), html);
        assertEquals(1, html.split("custom biomes here", -1).length - 1, html);
    }

    @Test
    void theBiomesKeepTheOrderTheyAreGivenAndOnlyTheLastHasNoSpaceBelow() {
        List<Section> three = List.of(new Section("cbc:a", "A", 0, 1, 1, 1, NO_SETTINGS, RED),
                new Section("cbc:b", "B", 0, 1, 1, 1, null, GREEN),
                new Section("cbc:c", "C", 0, 1, 1, 1, NO_SETTINGS, YELLOW));

        String html = BiomeWindow.html(three);

        assertTrue(html.indexOf("<b>A</b>") < html.indexOf("<b>B</b>"), html);
        assertTrue(html.indexOf("<b>B</b>") < html.indexOf("<b>C</b>"), html);
        assertEquals(2, html.split("padding:3px 7px 8px", -1).length - 1, html);
        assertEquals(1, html.split("padding:3px 7px 0;", -1).length - 1, html);
        assertTrue(html.indexOf("padding:3px 7px 0;") > html.indexOf("<b>C</b>"), html);
    }

    // A note belongs to its own biome: in a stacked spot it stands under that biome's header, between the blocks of the
    // biomes above and below it, and the neighbours say nothing of it
    @Test
    void aNoteInAStackedSpotStandsUnderItsOwnBiomesHeader() {
        Settings unsaved = settings(true, group("Climate", item("rain", "on")));
        Settings plain = settings(false, group("Sky and fog", colour("sky", 0x78a7ff)));

        String html = BiomeWindow.html(List.of(
                new Section("cbc:a", null, 100, 200, 1, 2, unsaved, RED),
                new Section("cbc:b", null, 50, 99, 1, 1, null, GREEN),
                new Section("cbc:c", null, 0, 49, 2, 3, plain, YELLOW)));

        assertEquals(window(
                "<div style=\"padding:0 0 6px;font-size:11px;color:#777\">3 custom biomes here, top first</div>",
                header("#fbdce4", "#e6194b", "cbc:a", "", "Y 100&ndash;200 &middot; 1 place &middot; ~2 chunks"),
                "<div style=\"padding:2px 7px 0;color:#b26a00\">Changed in game, not saved yet</div>",
                rows(firstSetting("Climate", "rain on")), edit("cbc:a", false),
                header("#e2f4e4", "#3cb44b", "cbc:b", "", "Y 50&ndash;99 &middot; 1 place &middot; ~1 chunk"),
                "<div style=\"padding:2px 7px 0;color:#888\">Its settings cannot be read here.</div>",
                edit("cbc:b", false),
                header("#fffadc", "#ffe119", "cbc:c", "", "Y 0&ndash;49 &middot; 2 places &middot; ~3 chunks"),
                rows(firstSetting("Sky", swatch("#78a7ff"))), edit("cbc:c", true)), html);
    }

    @Test
    void noSectionsGiveNoHtml() {
        assertEquals("", BiomeWindow.html(List.of()));
    }

    @Test
    void aSectionNeedsABiome() {
        assertThrows(NullPointerException.class, () -> new Section(null, "X", 0, 1, 1, 1, NO_SETTINGS, RED));
    }

    // --- the notes ---

    @Test
    void anUnsavedBiomeSaysSoUnderItsHeaderBeforeItsSettings() {
        Settings unsaved = settings(true, group("Climate", item("rain", "on")));

        assertEquals(window(HARBOUR,
                "<div style=\"padding:2px 7px 0;color:#b26a00\">Changed in game, not saved yet</div>",
                rows(firstSetting("Climate", "rain on")), edit("cbc:111g380-5p", true)),
                BiomeWindow.html(List.of(harbour(unsaved))));
    }

    @Test
    void aSavedBiomeSaysNothingAboutSaving() {
        String html = BiomeWindow.html(List.of(harbour(settings(false, group("Climate", item("rain", "on"))))));

        assertFalse(html.contains("not saved"), html);
    }

    @Test
    void aBiomeWhoseSettingsCannotBeReadSaysSoInsteadOfShowingThem() {
        assertEquals(window(HARBOUR,
                "<div style=\"padding:2px 7px 0;color:#888\">Its settings cannot be read here.</div>",
                edit("cbc:111g380-5p", true)), BiomeWindow.html(List.of(harbour(null))));
    }

    @Test
    void aBiomeThatSetsNothingHasOnlyAHeaderAndTheCommand() {
        String html = BiomeWindow.html(List.of(harbour(NO_SETTINGS)));

        assertEquals(window(HARBOUR, edit("cbc:111g380-5p", true)), html);
        assertFalse(html.contains("cannot be read"), html);
        assertFalse(html.contains("<table"), "no settings, no table: " + html);
    }

    // --- the header ---

    @Test
    void aLabelThatDiffersFromTheIdShowsTheIdBesideIt() {
        assertEquals(bare("cbc:x", "Old harbour", "<code style=\"color:#777\">cbc:x</code> ",
                        "Y 0&ndash;63 &middot; 3 places &middot; ~9 chunks"),
                BiomeWindow.html(List.of(new Section("cbc:x", "Old harbour", 0, 63, 3, 9, NO_SETTINGS, RED))));
    }

    @Test
    void aBiomeWithoutALabelOrWithABlankOrIdenticalOneIsNamedByItsIdAlone() {
        String expected = bare("cbc:x", "cbc:x", "", "Y 0&ndash;63 &middot; 3 places &middot; ~9 chunks");

        assertEquals(expected, BiomeWindow.html(List.of(new Section("cbc:x", null, 0, 63, 3, 9, NO_SETTINGS, RED))));
        assertEquals(expected, BiomeWindow.html(List.of(new Section("cbc:x", "  ", 0, 63, 3, 9, NO_SETTINGS, RED))));
        assertEquals(expected, BiomeWindow.html(List.of(new Section("cbc:x", "", 0, 63, 3, 9, NO_SETTINGS, RED))));
        assertEquals(expected, BiomeWindow.html(List.of(new Section("cbc:x", "cbc:x", 0, 63, 3, 9, NO_SETTINGS,
                RED))));
    }

    @Test
    void onePlaceAndOneChunkAreSingular() {
        assertEquals(bare("cbc:x", "X", "<code style=\"color:#777\">cbc:x</code> ",
                        "Y 0&ndash;63 &middot; 1 place &middot; ~1 chunk"),
                BiomeWindow.html(List.of(new Section("cbc:x", "X", 0, 63, 1, 1, NO_SETTINGS, RED))));
    }

    @Test
    void noPlacesAndSeveralChunksArePlural() {
        String code = "<code style=\"color:#777\">cbc:x</code> ";
        assertEquals(bare("cbc:x", "X", code, "Y 0&ndash;63 &middot; 0 places &middot; ~0 chunks"),
                BiomeWindow.html(List.of(new Section("cbc:x", "X", 0, 63, 0, 0, NO_SETTINGS, RED))));
        assertEquals(bare("cbc:x", "X", code, "Y 0&ndash;63 &middot; 1 place &middot; ~2 chunks"),
                BiomeWindow.html(List.of(new Section("cbc:x", "X", 0, 63, 1, 2, NO_SETTINGS, RED))));
        assertEquals(bare("cbc:x", "X", code, "Y 0&ndash;63 &middot; 2 places &middot; ~1 chunk"),
                BiomeWindow.html(List.of(new Section("cbc:x", "X", 0, 63, 2, 1, NO_SETTINGS, RED))));
    }

    @Test
    void heightsBelowZeroHaveAMinusSignAndLargeCountsAreWrittenPlainly() {
        String code = "<code style=\"color:#777\">cbc:x</code> ";
        assertEquals(bare("cbc:x", "X", code, "Y &minus;64&ndash;751 &middot; 1234 places &middot; ~1234567 chunks"),
                BiomeWindow.html(List.of(new Section("cbc:x", "X", -64, 751, 1234, 1234567, NO_SETTINGS, RED))));
        assertEquals(bare("cbc:x", "X", code, "Y &minus;60&ndash;&minus;40 &middot; 1 place &middot; ~1 chunk"),
                BiomeWindow.html(List.of(new Section("cbc:x", "X", -60, -40, 1, 1, NO_SETTINGS, RED))));
    }

    @Test
    void theTintMixesTheMapColourFifteenPercentTowardsWhite() {
        assertEquals("#fbdce4", BiomeWindow.tint(0xe6194b));
        assertEquals("#e2f4e4", BiomeWindow.tint(GREEN));
        assertEquals("#fffadc", BiomeWindow.tint(YELLOW));
        assertEquals("#d9d9d9", BiomeWindow.tint(0x000000));
        assertEquals("#ffffff", BiomeWindow.tint(0xffffff));
        assertEquals("#dbe1e6", BiomeWindow.tint(0x123456));
    }

    @Test
    void everyChannelOfTheTintFollowsTheFormula() {
        for (int channel = 0; channel < 256; channel++) {
            int expected = 255 - (int) Math.round((255 - channel) * 0.15);
            assertEquals(String.format("#%02x%02x%02x", expected, expected, expected),
                    BiomeWindow.tint(channel << 16 | channel << 8 | channel), "channel " + channel);
        }
    }

    @Test
    void theHeaderTakesTheBiomesTintAndColourFromTheSection() {
        String html = BiomeWindow.html(List.of(new Section("cbc:x", "X", 0, 63, 1, 1, NO_SETTINGS, 0x4363d8)));

        assertTrue(html.contains("style=\"background:#e3e8f9;border-left:5px solid #4363d8;padding:4px 7px\""), html);
    }

    // --- the rows of settings ---

    @Test
    void colourSettingsShowASwatchAndTheirHexWithSixLowercaseDigits() {
        Settings colours = settings(false, group("Sky and fog", colour("sky", 0xab), colour("sky light", 0xABCDEF),
                colour("fog", 0)));

        assertEquals(window(HARBOUR, rows(firstSetting("Sky", swatch("#0000ab")),
                setting("Sky light", swatch("#abcdef")), setting("Fog", swatch("#000000"))),
                edit("cbc:111g380-5p", true)), BiomeWindow.html(List.of(harbour(colours))));
    }

    @Test
    void aSwatchKeepsOnlyTheColourPartOfAValue() {
        // an ARGB setting arrives with just its low 24 bits as the number, but a stray alpha must never reach a style
        Settings argb = settings(false, group("Sky and fog", new Setting("sunrise", "#80ff8040", 0x80ff8040)));

        assertEquals(window(HARBOUR,
                rows(firstSetting("Sunrise", "<span style=\"color:#ff8040\">&#9632;</span> #80ff8040")),
                edit("cbc:111g380-5p", true)), BiomeWindow.html(List.of(harbour(argb))));
    }

    @Test
    void theClimateSettingsShareOneRowWhereTheirHeadingStands() {
        // the groups are neither in alphabetical order nor in reverse: the window shows them as it is given them
        Settings groups = settings(false,
                group("Sky and fog", item("fog start", "8")),
                group("Climate", item("temperature", "0.8"), item("rain", "on")),
                group("Sound and particles", item("music volume", "0.5")));

        assertEquals(window(HARBOUR, rows(firstSetting("Fog start", "8"),
                setting("Climate", "temperature 0.8 &middot; rain on"), setting("Music volume", "0.5")),
                edit("cbc:111g380-5p", true)), BiomeWindow.html(List.of(harbour(groups))));
    }

    @Test
    void aSingleClimateSettingIsAClimateRowToo() {
        assertEquals(window(HARBOUR, rows(firstSetting("Climate", "rain off")), edit("cbc:111g380-5p", true)),
                BiomeWindow.html(List.of(harbour(settings(false, group("Climate", item("rain", "off")))))));
    }

    @Test
    void everyOtherSettingIsARowNamedByItsNameWithACapitalLetter() {
        Settings groups = settings(false,
                group("Sky and fog", colour("sky light", 0xffaa00), item("fog start", "8")),
                group("Water and plants", item("grass modifier", "swamp")),
                group("Sound and particles", item("music", "music.overworld.forest, every 600 to 1200 s"),
                        item("ambient sounds", "loop ambient.cave"), item("ambient particles", "none"),
                        item("firefly bush sounds", "off")));

        assertEquals(window(HARBOUR, rows(firstSetting("Sky light", swatch("#ffaa00")), setting("Fog start", "8"),
                setting("Grass modifier", "swamp"), setting("Music", "music.overworld.forest, every 600 to 1200 s"),
                setting("Ambient sounds", "loop ambient.cave"), setting("Ambient particles", "none"),
                setting("Firefly bush sounds", "off")), edit("cbc:111g380-5p", true)),
                BiomeWindow.html(List.of(harbour(groups))));
    }

    @Test
    void aSettingKeepsItsNameWhateverItStartsWith() {
        Settings odd = settings(false, group("Sky and fog", item("Sky", "a"), item("9 lives", "b"), item("", "c"),
                item("x", "d")));

        assertEquals(window(HARBOUR, rows(firstSetting("Sky", "a"), setting("9 lives", "b"), setting("", "c"),
                setting("X", "d")), edit("cbc:111g380-5p", true)), BiomeWindow.html(List.of(harbour(odd))));
    }

    @Test
    void aSettingUnderAnotherHeadingIsARowToo() {
        // only Climate has a place of its own: the headings are not shown, and a heading the window does not know
        // does not hide its settings
        Settings groups = settings(false, group("Weather", item("thunder", "on")),
                group("Sky and fog", item("fog start", "8")));

        String html = BiomeWindow.html(List.of(harbour(groups)));

        assertEquals(window(HARBOUR, rows(firstSetting("Thunder", "on"), setting("Fog start", "8")),
                edit("cbc:111g380-5p", true)), html);
        assertFalse(html.contains("Weather"), html);
        assertFalse(html.contains("Sky and fog"), html);
    }

    @Test
    void aGroupWithoutSettingsHasNoRow() {
        Settings groups = settings(false, group("Sky and fog"), group("Climate"), group("Water and plants",
                item("grass modifier", "none")));

        assertEquals(window(HARBOUR, rows(firstSetting("Grass modifier", "none")), edit("cbc:111g380-5p", true)),
                BiomeWindow.html(List.of(harbour(groups))));
    }

    @Test
    void minecraftIsDroppedFromIdsInValuesAndOtherNamespacesStay() {
        Settings sound = settings(false, group("Sound and particles",
                item("music", "minecraft:music.nether.basalt_deltas, every 600 to 1200 s"),
                item("ambient sounds", "loop minecraft:ambient.loop, mood rohan:ambient.mood, "
                        + "extra minecraft:ambient.additions"),
                item("ambient particles", "minecraft:crimson_spore 2.5%, mcme:ember 1%"),
                item("dripstone particle", "notminecraft:drip, minecraft:dripping_dripstone_water"),
                item("sky", "minecraft")),
                group("Climate", item("temperature modifier", "minecraft:frozen")));

        assertEquals(window(HARBOUR, rows(firstSetting("Music", "music.nether.basalt_deltas, every 600 to 1200 s"),
                setting("Ambient sounds", "loop ambient.loop, mood rohan:ambient.mood, extra ambient.additions"),
                setting("Ambient particles", "crimson_spore 2.5%, mcme:ember 1%"),
                setting("Dripstone particle", "notminecraft:drip, dripping_dripstone_water"),
                setting("Sky", "minecraft"), setting("Climate", "temperature modifier frozen")),
                edit("cbc:111g380-5p", true)), BiomeWindow.html(List.of(harbour(sound))));
    }

    @Test
    void theNamespaceIsOnlyDroppedFromValuesNotFromTheBiomeId() {
        // a biome id is never in the minecraft: namespace on the layer, but if one were, the command needs it whole
        String html = BiomeWindow.html(List.of(new Section("minecraft:plains", "Plains", 0, 63, 1, 1, NO_SETTINGS,
                RED)));

        assertTrue(html.contains("<code style=\"color:#777\">minecraft:plains</code>"), html);
        assertTrue(html.contains("/biometune edit minecraft:plains</div>"), html);
    }

    // --- escaping ---

    @Test
    void aLabelAnIdAndAValueAreEscaped() {
        // every text a builder may have typed reaches a browser that will parse it
        Settings dangerous = settings(false, group("Weather", item("a<b", "x&y\"z")));

        String html = BiomeWindow.html(List.of(new Section("cbc:<x>&\"y\"", "<b>Fen & \"Marsh\"</b>", 0, 63, 1, 1,
                dangerous, RED)));

        assertEquals(window(header("#fbdce4", "#e6194b", "&lt;b&gt;Fen &amp; &quot;Marsh&quot;&lt;/b&gt;",
                        "<code style=\"color:#777\">cbc:&lt;x&gt;&amp;&quot;y&quot;</code> ",
                        "Y 0&ndash;63 &middot; 1 place &middot; ~1 chunk"),
                rows(firstSetting("A&lt;b", "x&amp;y&quot;z")), edit("cbc:&lt;x&gt;&amp;&quot;y&quot;", true)), html);
        assertFalse(html.contains("Fen & "), html);
    }

    @Test
    void anIdShownAsTheNameIsEscapedToo() {
        String html = BiomeWindow.html(List.of(new Section("cbc:<x>", null, 0, 63, 1, 1, NO_SETTINGS, RED)));

        assertEquals(bare("cbc:&lt;x&gt;", "cbc:&lt;x&gt;", "", "Y 0&ndash;63 &middot; 1 place &middot; ~1 chunk"),
                html);
    }

    @Test
    void everyOtherTextIsEscapedToo() {
        // the settings' names and texts, a colour setting's text, the climate row, and a heading that is never shown
        Settings dangerous = settings(false,
                group("<Sky> & \"fog\"", item("a<b", "x&y\"z"), new Setting("c>d", "<i>e</i>", 0x123456)),
                group("Climate", item("t<", "\"hot\" & <dry>"), item("rain", "<on>")));

        String html = BiomeWindow.html(List.of(new Section("cbc:<x>", "L", 0, 63, 1, 1, dangerous, RED)));

        assertEquals(window(header("#fbdce4", "#e6194b", "L", "<code style=\"color:#777\">cbc:&lt;x&gt;</code> ",
                        "Y 0&ndash;63 &middot; 1 place &middot; ~1 chunk"),
                rows(firstSetting("A&lt;b", "x&amp;y&quot;z"),
                        setting("C&gt;d", "<span style=\"color:#123456\">&#9632;</span> &lt;i&gt;e&lt;/i&gt;"),
                        setting("Climate", "t&lt; &quot;hot&quot; &amp; &lt;dry&gt; &middot; rain &lt;on&gt;")),
                edit("cbc:&lt;x&gt;", true)), html);
        assertFalse(html.contains("<Sky>"), html);
    }

    // --- the bytes ---

    @Test
    void theMarkupHasNoLineBreaksOrExtraSpaces() {
        // the window's size is worked out from these bytes: nothing separates two blocks, and no part is padded
        Settings groups = settings(true, group("Climate", item("rain", "on")),
                group("Sky and fog", colour("sky", 0x102030)));

        String html = BiomeWindow.html(List.of(harbour(groups), harbour(null), harbour(NO_SETTINGS)));

        assertEquals(-1, html.indexOf('\n'), html);
        assertEquals(-1, html.indexOf('\r'), html);
        assertEquals(-1, html.indexOf('\t'), html);
        assertFalse(html.contains("  "), html);
        assertFalse(html.contains("> <div"), html);
        assertFalse(html.contains("</div> "), html);
        assertFalse(html.contains("> <table"), html);
        assertFalse(html.contains("</table> "), html);
        assertFalse(html.contains("> <tr"), html);
        assertFalse(html.contains("</tr> "), html);
    }

    private static final Set<String> TAGS = Set.of("div", "table", "tr", "td", "b", "code", "span");
    private static final Set<String> PROPERTIES = Set.of("color", "background", "border-left", "padding", "font",
            "font-size", "white-space", "vertical-align", "width", "border-collapse");

    // dynmap keeps a marker's description through a sanitiser that allows blocks, tables, formatting and inline styles
    // of plain CSS, and nothing else. Run on this window, it drops every attribute but style, so a cell spanning two
    // columns (colspan) would shrink to one, and it drops a style block, a class, a script and any CSS property it does
    // not know.
    @Test
    void onlyWhatDynmapKeepsIsUsed() {
        Settings groups = settings(true, group("Sky and fog", colour("sky", 0x102030), item("fog start", "8")),
                group("Climate", item("rain", "on")));
        String html = BiomeWindow.html(List.of(harbour(groups), harbour(null), harbour(NO_SETTINGS)));

        Matcher tag = Pattern.compile("<(/?)([a-z0-9]+)([^>]*)>").matcher(html);
        List<String> found = new ArrayList<>();
        while (tag.find()) {
            found.add(tag.group(2));
            assertTrue(TAGS.contains(tag.group(2)), "tag " + tag.group(2) + " in " + html);
            Matcher attribute = Pattern.compile("\\s([a-z-]+)=\"([^\"]*)\"").matcher(tag.group(3));
            int attributes = 0;
            while (attribute.find()) {
                attributes++;
                assertEquals("style", attribute.group(1), "attribute " + attribute.group(1) + " in " + html);
                for (String declaration : attribute.group(2).split(";")) {
                    String property = declaration.substring(0, declaration.indexOf(':'));
                    assertTrue(PROPERTIES.contains(property), "property " + property + " in " + html);
                }
            }
            assertTrue(attributes <= 1, "attributes of " + tag.group(0));
        }
        assertTrue(found.containsAll(TAGS), found.toString());
    }

    // Settings are read on the main thread and used on the one that builds the window
    @Test
    void settingsKeepTheirGroupsInOrderAndCannotBeChangedAfterwards() {
        Map<String, List<Setting>> groups = new LinkedHashMap<>();
        groups.put("Sky and fog", new ArrayList<>(List.of(item("fog start", "8"))));
        groups.put("Climate", new ArrayList<>(List.of(item("rain", "on"))));
        Settings settings = new Settings(groups, false);

        groups.put("Water and plants", List.of());
        groups.get("Climate").add(item("temperature", "0.8"));

        assertEquals(List.of("Sky and fog", "Climate"), List.copyOf(settings.groups().keySet()));
        assertEquals(List.of(item("rain", "on")), settings.groups().get("Climate"));
        assertThrows(UnsupportedOperationException.class, () -> settings.groups().put("Climate", List.of()));
        assertThrows(UnsupportedOperationException.class, () -> settings.groups().get("Climate").add(item("x", "y")));
    }

    @Test
    void settingsAreEqualWhenTheirGroupsAndFlagAre() {
        Settings one = settings(false, group("Climate", item("rain", "on")));

        assertEquals(one, settings(false, group("Climate", item("rain", "on"))));
        assertEquals(one.hashCode(), settings(false, group("Climate", item("rain", "on"))).hashCode());
        assertNotEquals(one, settings(true, group("Climate", item("rain", "on"))));
        assertNotEquals(one, settings(false, group("Climate", item("rain", "off"))));
        assertNotEquals(one, settings(false, group("Climate", new Setting("rain", "on", 1))));
    }
}
