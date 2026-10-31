package com.mcmiddleearth.architect.mapLayers.customBiomes;

import com.mcmiddleearth.architect.biomeTuning.ColourMath;
import com.mcmiddleearth.architect.mapLayers.MapShape;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * The HTML of the window that opens when someone clicks a painted spot on the map: a block for each custom biome
 * painted there, from the highest down, each with a header in the biome's map colour and a row for each of its
 * settings. The markup is fixed down to its bytes, because the size of the map's download is worked out from it.
 * <p>
 * dynmap passes a marker's description through a sanitiser that keeps blocks, tables, {@code <b>}, {@code <code>},
 * {@code <span>} and inline styles of plain CSS, and drops the rest: a style block, a class, a script, a CSS property
 * it does not know, and every attribute but {@code style}. A cell spanning two columns ({@code colspan}) would shrink
 * to one. So a part that is as wide as the window, such as a header, is a {@code div}, and only the settings, in
 * columns of a name and a value, are a table. Text a builder may have typed goes through {@link MapShape#html}.
 * <p>
 * The window is shown in the map's own popup, and it cannot tell whose: dynmap's own map draws popups white with dark
 * text, LiveAtlas (as on MCME's map) dark with light text. So the window names no colour for its text, which takes the
 * popup's, and the few colours it names read on both: {@link #GREY} for the quieter lines and an orange for unsaved
 * edits. The header's tint is the map colour laid thinly over the popup, and a swatch is a chip with a grey edge, so a
 * dark colour shows on a dark popup and a pale one on a white popup. The sanitiser drops {@code opacity} and
 * {@code currentColor}, so a paler shade of the popup's own colour cannot be written.
 */
public final class BiomeWindow {

    private static final String WINDOW_OPEN = "<div style=\"font:12px/1.35 sans-serif\">";
    private static final String SETTINGS_OPEN = "<table style=\"border-collapse:collapse;font:12px/1.35 sans-serif;"
            + "width:100%\">";
    /**
     * The one grey the window names: for the names of the settings, the count of a stacked spot, the note that
     * settings cannot be read, the command, and the edge of a swatch. It reads on a white popup (3.5 : 1) and on
     * LiveAtlas's dark one (4.7 : 1).
     */
    private static final String GREY = "#888";
    /**
     * The width of the column of names, in pixels. Each biome's settings are a table of their own, so this fits the
     * longest names (Firefly bush sounds takes about 104 in 12 px Arial, and a wider font takes more), and the values
     * of every biome start at the same place. A name that is longer still pushes its own table's values to the right,
     * and no more. Only the first name of a table carries it: in an automatic table layout one cell sets its column.
     */
    private static final int NAME_WIDTH = 112;
    /** The heading whose settings share one row. */
    private static final String CLIMATE = "Climate";
    /** The namespace of the game's own ids, which the window leaves out. */
    private static final String MINECRAFT = "minecraft:";
    /**
     * How much of the map colour the header's background takes. Over a white popup the header looks as it always did,
     * the colour mixed 15 percent towards white; over a dark one it darkens with the popup.
     */
    private static final String TINT_ALPHA = "0.15";

    /**
     * One biome at the clicked spot.
     *
     * @param biome the biome id, such as {@code cbc:111g380-5p}
     * @param label the name builders gave it, or null for none: the window then names it by its id
     * @param minY the lowest painted height of the biome at this spot
     * @param maxY the highest painted height of the biome at this spot
     * @param places how many separate places on this world the biome is painted in
     * @param chunks about how many chunks it covers on this world
     * @param settings its settings, or null when they cannot be read
     * @param rgb the colour its areas are drawn in on the map, as {@code 0xRRGGBB}
     */
    public record Section(String biome, String label, int minY, int maxY, int places, int chunks,
                          BiomeFacts.Settings settings, int rgb) {

        public Section {
            Objects.requireNonNull(biome, "biome");
        }
    }

    private BiomeWindow() {
    }

    /** The window for these sections, in the order given; nothing for none. */
    public static String html(List<Section> sections) {
        if (sections.isEmpty()) {
            return "";
        }
        StringBuilder html = new StringBuilder(WINDOW_OPEN);
        if (sections.size() > 1) {
            html.append("<div style=\"padding:0 0 6px;font-size:11px;color:" + GREY + "\">").append(sections.size())
                    .append(" custom biomes here, top first</div>");
        }
        for (int i = 0; i < sections.size(); i++) {
            section(html, sections.get(i), i == sections.size() - 1);
        }
        return html.append("</div>").toString();
    }

    /** One biome's block: its header, what is to be said about its settings, a row for each, and its command. */
    private static void section(StringBuilder html, Section section, boolean last) {
        String id = MapShape.html(section.biome());
        // a label that is no more than the id is no name: the id stands alone
        boolean named = section.label() != null && !section.label().isBlank()
                && !section.label().equals(section.biome());
        html.append("<div style=\"background:").append(tint(section.rgb())).append(";border-left:5px solid ")
                .append(ColourMath.hex(section.rgb())).append(";padding:4px 7px\"><b>")
                .append(named ? MapShape.html(section.label()) : id).append("</b> ");
        if (named) {
            html.append("<code>").append(id).append("</code> ");
        }
        html.append("<span style=\"font-size:11px\">&nbsp;Y ").append(height(section.minY()))
                .append("&ndash;").append(height(section.maxY())).append(" &middot; ")
                .append(count(section.places(), "place")).append(" &middot; ~")
                .append(count(section.chunks(), "chunk")).append("</span></div>");
        BiomeFacts.Settings settings = section.settings();
        if (settings == null) {
            note(html, GREY, "Its settings cannot be read here.");
        } else {
            if (settings.unsaved()) {
                note(html, "#b26a00", "Changed in game, not saved yet");
            }
            StringBuilder table = new StringBuilder();
            for (Map.Entry<String, List<BiomeFacts.Setting>> group : settings.groups().entrySet()) {
                rows(table, group.getKey(), group.getValue());
            }
            if (table.length() > 0) {
                html.append(SETTINGS_OPEN).append(table).append("</table>");
            }
        }
        html.append("<div style=\"padding:3px 7px ").append(last ? "0" : "8px")
                .append(";font:11px monospace;color:" + GREY + "\">/biometune edit ").append(id).append("</div>");
    }

    /** A line under the header that is not a setting. */
    private static void note(StringBuilder html, String colour, String text) {
        html.append("<div style=\"padding:2px 7px 0;color:").append(colour).append("\">").append(text)
                .append("</div>");
    }

    /**
     * The rows of one heading's settings, in order: the settings of {@value #CLIMATE} share one row, any other setting
     * has a row of its own. The headings are not shown, so a heading the window does not know hides nothing.
     */
    private static void rows(StringBuilder table, String heading, List<BiomeFacts.Setting> settings) {
        if (settings.isEmpty()) {
            return;
        }
        if (CLIMATE.equals(heading)) {
            List<String> items = new ArrayList<>();
            for (BiomeFacts.Setting setting : settings) {
                items.add(MapShape.html(setting.name()) + " " + value(setting));
            }
            row(table, CLIMATE, String.join(" &middot; ", items));
            return;
        }
        for (BiomeFacts.Setting setting : settings) {
            row(table, MapShape.html(capitalised(setting.name())), value(setting));
        }
    }

    /**
     * A row for the table of settings, whose rows so far are in {@code table}; the label and the value are HTML
     * already. The first row's name cell carries the width of the column of names, and the others take it from there.
     */
    private static void row(StringBuilder table, String label, String value) {
        boolean first = table.length() == 0;
        table.append("<tr><td style=\"color:" + GREY + ";padding:0 10px 0 7px;white-space:nowrap;vertical-align:top");
        if (first) {
            table.append(";width:").append(NAME_WIDTH).append("px");
        }
        table.append("\">").append(label).append("</td><td>").append(value).append("</td></tr>");
    }

    /** A setting's value: its text without the {@code minecraft:} namespace, after a swatch for a colour. */
    private static String value(BiomeFacts.Setting setting) {
        String text = MapShape.html(withoutMinecraft(setting.text()));
        // the swatch is built from the number alone, never from text, so nothing typed can reach the style
        return setting.rgb() == null ? text
                : "<span style=\"background:" + ColourMath.hex(setting.rgb()) + ";border:1px solid " + GREY
                        + ";padding:0 4px;font-size:10px\">&nbsp;</span> " + text;
    }

    /** The name with a capital first letter. */
    private static String capitalised(String name) {
        if (name == null || name.isEmpty()) {
            return "";
        }
        int first = name.codePointAt(0);
        return new StringBuilder(name.length()).appendCodePoint(Character.toUpperCase(first))
                .append(name, Character.charCount(first), name.length()).toString();
    }

    /** The text without the {@code minecraft:} namespace on its ids; the ids of any other namespace keep theirs. */
    private static String withoutMinecraft(String text) {
        if (text == null || !text.contains(MINECRAFT)) {
            return text;
        }
        StringBuilder out = new StringBuilder(text.length());
        int from = 0;
        for (int at = text.indexOf(MINECRAFT); at >= 0; at = text.indexOf(MINECRAFT, from)) {
            out.append(text, from, at);
            // right behind a character of an id, it ends the name of another namespace, such as notminecraft:
            if (at > 0 && inAnId(text.charAt(at - 1))) {
                out.append(MINECRAFT);
            }
            from = at + MINECRAFT.length();
        }
        return out.append(text, from, text.length()).toString();
    }

    private static boolean inAnId(char c) {
        return Character.isLetterOrDigit(c) || c == '_' || c == '.' || c == '-' || c == '/' || c == ':';
    }

    /**
     * The header's background: the biome's map colour at {@link #TINT_ALPHA}, as {@code rgba( r , g , b , 0.15 )}.
     * dynmap's sanitiser writes every {@code rgba} it keeps that way, so the bytes the map stores are the bytes
     * counted.
     */
    static String tint(int rgb) {
        return "rgba( " + ((rgb >> 16) & 0xff) + " , " + ((rgb >> 8) & 0xff) + " , " + (rgb & 0xff) + " , "
                + TINT_ALPHA + " )";
    }

    /** A height, with a minus sign for one below zero. */
    private static String height(int y) {
        return y < 0 ? "&minus;" + Math.abs((long) y) : String.valueOf(y);
    }

    /** "1 place" or "2 places". */
    private static String count(int number, String noun) {
        return number + " " + noun + (number == 1 ? "" : "s");
    }
}
