package com.mcmiddleearth.architect.mapLayers.customBiomes;

import com.mcmiddleearth.architect.mapLayers.customBiomes.BiomeFacts.Setting;
import com.mcmiddleearth.architect.mapLayers.customBiomes.BiomeFacts.Settings;
import com.mcmiddleearth.architect.mapLayers.customBiomes.BiomeWindow.Section;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

// The window is what a staff member reads on clicking a painted spot, and the size of the map's download is worked out
// from its bytes, so the markup is pinned exactly. Names are typed by builders, so every text must come out escaped.
class BiomeWindowTest {

    private static final Settings NO_SETTINGS = new Settings(Map.of(), false);
    private static final String COMMANDS = "<code>/biometune edit cbc:111g380-5p</code> &middot; "
            + "<code>//setbiome cbc:111g380-5p</code>";

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

    /** The settings of a biome, with its groups in the order given. */
    private static Settings settings(boolean unsaved, Group... groups) {
        Map<String, List<Setting>> byHeading = new LinkedHashMap<>();
        for (Group group : groups) {
            byHeading.put(group.heading(), group.settings());
        }
        return new Settings(byHeading, unsaved);
    }

    /** A section of the old harbour: 2 places and 180 chunks, painted from Y 40 to 120. */
    private static Section harbour(Settings settings) {
        return new Section("cbc:111g380-5p", "Old harbour", 40, 120, 2, 180, settings);
    }

    @Test
    void aSectionShowsNameIdHeightsPlacesAndCommands() {
        assertEquals("<b>Old harbour</b> <code>cbc:111g380-5p</code><br/>Y 40 to 120 here &middot; painted in 2 places "
                        + "on this world, about 180 chunks<br/><code>/biometune edit cbc:111g380-5p</code> &middot; "
                        + "<code>//setbiome cbc:111g380-5p</code>",
                BiomeWindow.html(List.of(new BiomeWindow.Section("cbc:111g380-5p", "Old harbour", 40, 120, 2, 180,
                        new BiomeFacts.Settings(Map.of(), false)))));
    }

    @Test
    void aBiomeWithoutALabelIsNamedByItsId() {
        String html = BiomeWindow.html(List.of(new Section("cbc:x", null, 0, 63, 3, 9, NO_SETTINGS)));

        assertEquals("<b>cbc:x</b> <code>cbc:x</code><br/>Y 0 to 63 here &middot; painted in 3 places on this world, "
                + "about 9 chunks<br/><code>/biometune edit cbc:x</code> &middot; <code>//setbiome cbc:x</code>", html);
    }

    @Test
    void aBlankLabelIsNoNameEither() {
        String html = BiomeWindow.html(List.of(new Section("cbc:x", "  ", 0, 63, 3, 9, NO_SETTINGS)));

        assertTrue(html.startsWith("<b>cbc:x</b> <code>cbc:x</code><br/>"), html);
    }

    @Test
    void onePlaceAndOneChunkAreSingular() {
        String html = BiomeWindow.html(List.of(new Section("cbc:x", "X", 0, 63, 1, 1, NO_SETTINGS)));

        assertTrue(html.contains("painted in 1 place on this world, about 1 chunk<br/>"), html);
    }

    @Test
    void noPlacesAndSeveralChunksArePlural() {
        assertTrue(BiomeWindow.html(List.of(new Section("cbc:x", "X", 0, 63, 0, 0, NO_SETTINGS)))
                .contains("painted in 0 places on this world, about 0 chunks<br/>"));
        assertTrue(BiomeWindow.html(List.of(new Section("cbc:x", "X", 0, 63, 1, 2, NO_SETTINGS)))
                .contains("painted in 1 place on this world, about 2 chunks<br/>"));
        assertTrue(BiomeWindow.html(List.of(new Section("cbc:x", "X", 0, 63, 2, 1, NO_SETTINGS)))
                .contains("painted in 2 places on this world, about 1 chunk<br/>"));
    }

    @Test
    void heightsBelowZeroAndLargeCountsAreWrittenPlainly() {
        String html = BiomeWindow.html(List.of(new Section("cbc:x", "X", -64, 751, 1234, 1234567, NO_SETTINGS)));

        assertTrue(html.contains("Y -64 to 751 here &middot; painted in 1234 places on this world, "
                + "about 1234567 chunks<br/>"), html);
    }

    @Test
    void colourSettingsShowASwatchAndTheirHex() {
        Settings sky = settings(false, group("Sky and fog", new Setting("sky", "#78a7ff", 0x78a7ff),
                item("fog start", "8")));

        String html = BiomeWindow.html(List.of(harbour(sky)));

        assertTrue(html.contains("<i>Sky and fog</i>: <span style=\"color:#78a7ff\">&#9632;</span> sky #78a7ff "
                + "&middot; fog start 8<br/>"), html);
    }

    @Test
    void aSwatchHasSixLowercaseHexDigits() {
        Settings colours = settings(false, group("Sky and fog", new Setting("a", "#0000ab", 0xab),
                new Setting("b", "#abcdef", 0xABCDEF), new Setting("c", "#000000", 0)));

        String html = BiomeWindow.html(List.of(harbour(colours)));

        assertTrue(html.contains("<span style=\"color:#0000ab\">&#9632;</span> a #0000ab &middot; "
                + "<span style=\"color:#abcdef\">&#9632;</span> b #abcdef &middot; "
                + "<span style=\"color:#000000\">&#9632;</span> c #000000<br/>"), html);
    }

    @Test
    void aSwatchKeepsOnlyTheColourPartOfAValue() {
        // an ARGB setting arrives with just its low 24 bits, but a stray alpha must never reach the style attribute
        Settings argb = settings(false, group("Sky and fog", new Setting("sunrise", "#80ff8040", 0x80ff8040)));

        assertTrue(BiomeWindow.html(List.of(harbour(argb)))
                .contains("<span style=\"color:#ff8040\">&#9632;</span> sunrise"));
    }

    @Test
    void oneLineIsShownForEachGroupInTheOrderTheyAreGiven() {
        // neither in alphabetical order nor in reverse: the window shows them as it is given them
        Settings groups = settings(false,
                group("Sky and fog", item("fog start", "8")),
                group("Water and plants", item("grass modifier", "swamp")),
                group("Climate", item("temperature", "0.8"), item("rain", "on")),
                group("Sound and particles", item("music volume", "0.5")));

        assertEquals("<b>Old harbour</b> <code>cbc:111g380-5p</code><br/>"
                + "Y 40 to 120 here &middot; painted in 2 places on this world, about 180 chunks<br/>"
                + "<i>Sky and fog</i>: fog start 8<br/>"
                + "<i>Water and plants</i>: grass modifier swamp<br/>"
                + "<i>Climate</i>: temperature 0.8 &middot; rain on<br/>"
                + "<i>Sound and particles</i>: music volume 0.5<br/>"
                + COMMANDS, BiomeWindow.html(List.of(harbour(groups))));
    }

    @Test
    void aGroupWithoutSettingsHasNoLine() {
        Settings groups = settings(false, group("Sky and fog"), group("Climate", item("rain", "off")));

        String html = BiomeWindow.html(List.of(harbour(groups)));

        assertFalse(html.contains("Sky and fog"), html);
        assertTrue(html.contains("<i>Climate</i>: rain off<br/>"), html);
    }

    @Test
    void anUnsavedBiomeSaysSo() {
        String html = BiomeWindow.html(List.of(harbour(new Settings(Map.of(), true))));

        assertTrue(html.contains("<i>Changed in game, not saved yet</i><br/>"), html);
        assertFalse(html.contains("cannot be read"), html);
    }

    @Test
    void aSavedBiomeSaysNothingAboutSaving() {
        String html = BiomeWindow.html(List.of(harbour(settings(false, group("Climate", item("rain", "on"))))));

        assertFalse(html.contains("not saved"), html);
    }

    @Test
    void theUnsavedNoteComesAfterTheGroupsAndBeforeTheCommands() {
        Settings groups = settings(true, group("Climate", item("rain", "on")));

        assertEquals("<b>Old harbour</b> <code>cbc:111g380-5p</code><br/>"
                + "Y 40 to 120 here &middot; painted in 2 places on this world, about 180 chunks<br/>"
                + "<i>Climate</i>: rain on<br/>"
                + "<i>Changed in game, not saved yet</i><br/>"
                + COMMANDS, BiomeWindow.html(List.of(harbour(groups))));
    }

    @Test
    void withoutSettingsTheWindowSaysTheyCannotBeRead() {
        String html = BiomeWindow.html(List.of(harbour(null)));

        assertEquals("<b>Old harbour</b> <code>cbc:111g380-5p</code><br/>"
                + "Y 40 to 120 here &middot; painted in 2 places on this world, about 180 chunks<br/>"
                + "<i>Its settings cannot be read here.</i><br/>"
                + COMMANDS, html);
    }

    @Test
    void stackedSectionsAreSeparatedAndKeepTheirOrder() {
        Section first = new Section("cbc:first", "First", 100, 200, 1, 2, NO_SETTINGS);
        Section second = new Section("cbc:second", "Second", 0, 99, 1, 1, NO_SETTINGS);

        String html = BiomeWindow.html(List.of(first, second));

        assertEquals("<b>First</b> <code>cbc:first</code><br/>Y 100 to 200 here &middot; painted in 1 place "
                + "on this world, about 2 chunks<br/><code>/biometune edit cbc:first</code> &middot; "
                + "<code>//setbiome cbc:first</code>"
                + "<hr/>"
                + "<b>Second</b> <code>cbc:second</code><br/>Y 0 to 99 here &middot; painted in 1 place "
                + "on this world, about 1 chunk<br/><code>/biometune edit cbc:second</code> &middot; "
                + "<code>//setbiome cbc:second</code>", html);
    }

    @Test
    void threeSectionsHaveTwoSeparators() {
        List<Section> three = List.of(new Section("cbc:a", "A", 0, 1, 1, 1, NO_SETTINGS),
                new Section("cbc:b", "B", 0, 1, 1, 1, null), new Section("cbc:c", "C", 0, 1, 1, 1, NO_SETTINGS));

        String html = BiomeWindow.html(three);

        assertEquals(2, html.split("<hr/>", -1).length - 1, html);
        assertTrue(html.indexOf("<b>A</b>") < html.indexOf("<b>B</b>"), html);
        assertTrue(html.indexOf("<b>B</b>") < html.indexOf("<b>C</b>"), html);
    }

    @Test
    void noSectionsGiveNoHtml() {
        assertEquals("", BiomeWindow.html(List.of()));
    }

    @Test
    void builderTextIsEscaped() {
        String html = BiomeWindow.html(List.of(new Section("cbc:x", "<b>Fen & Marsh</b>", 0, 63, 1, 1, NO_SETTINGS)));

        assertTrue(html.startsWith("<b>&lt;b&gt;Fen &amp; Marsh&lt;/b&gt;</b> <code>cbc:x</code><br/>"), html);
        assertFalse(html.contains("Fen & Marsh"), html);
    }

    @Test
    void everyOtherTextIsEscapedToo() {
        // the id, the headings and the settings' names and texts all reach a browser that will parse them
        Settings dangerous = settings(false, group("<Sky> & \"fog\"", item("a<b", "x&y\"z"),
                new Setting("c>d", "<i>e</i>", 0x123456)));

        String html = BiomeWindow.html(List.of(new Section("cbc:<x>", "L", 0, 63, 1, 1, dangerous)));

        assertEquals("<b>L</b> <code>cbc:&lt;x&gt;</code><br/>Y 0 to 63 here &middot; painted in 1 place "
                + "on this world, about 1 chunk<br/>"
                + "<i>&lt;Sky&gt; &amp; &quot;fog&quot;</i>: a&lt;b x&amp;y&quot;z &middot; "
                + "<span style=\"color:#123456\">&#9632;</span> c&gt;d &lt;i&gt;e&lt;/i&gt;<br/>"
                + "<code>/biometune edit cbc:&lt;x&gt;</code> &middot; <code>//setbiome cbc:&lt;x&gt;</code>", html);
    }

    @Test
    void theMarkupHasNoLineBreaksOrExtraSpaces() {
        // the window's size is worked out from these bytes: only <br/> breaks a line, and no part is padded
        Settings groups = settings(true, group("Climate", item("rain", "on")),
                group("Sky and fog", new Setting("sky", "#102030", 0x102030)));

        String html = BiomeWindow.html(List.of(harbour(groups), harbour(null)));

        assertEquals(-1, html.indexOf('\n'), html);
        assertEquals(-1, html.indexOf('\r'), html);
        assertEquals(-1, html.indexOf('\t'), html);
        assertFalse(html.contains("  "), html);
        assertFalse(html.contains("<br/> "), html);
        assertFalse(html.contains(" <br/>"), html);
        assertFalse(html.contains("<hr/> "), html);
        assertFalse(html.contains(" <hr/>"), html);
    }

    @Test
    void aSectionNeedsABiome() {
        assertThrows(NullPointerException.class, () -> new Section(null, "X", 0, 1, 1, 1, NO_SETTINGS));
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
