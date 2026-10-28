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
 */
public final class BiomeWindow {

    private static final String WINDOW_OPEN = "<div style=\"font:12px/1.35 sans-serif;color:#222\">";
    private static final String SETTINGS_OPEN = "<table style=\"border-collapse:collapse;font:12px/1.35 sans-serif;"
            + "color:#222;width:100%\">";
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
    /** How far the map colour is mixed towards white for the header's background, in percent. */
    private static final int TINT_PERCENT = 15;

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
            html.append("<div style=\"padding:0 0 6px;font-size:11px;color:#777\">").append(sections.size())
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
            html.append("<code style=\"color:#777\">").append(id).append("</code> ");
        }
        html.append("<span style=\"color:#777;font-size:11px\">&nbsp;Y ").append(height(section.minY()))
                .append("&ndash;").append(height(section.maxY())).append(" &middot; ")
                .append(count(section.places(), "place")).append(" &middot; ~")
                .append(count(section.chunks(), "chunk")).append("</span></div>");
        BiomeFacts.Settings settings = section.settings();
        if (settings == null) {
            note(html, "#888", "Its settings cannot be read here.");
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
                .append(";font:11px monospace;color:#666\">/biometune edit ").append(id).append("</div>");
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
        table.append("<tr><td style=\"color:#888;padding:0 10px 0 7px;white-space:nowrap;vertical-align:top");
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
                : "<span style=\"color:" + ColourMath.hex(setting.rgb()) + "\">&#9632;</span> " + text;
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
     * The header's background: the biome's map colour with each channel mixed {@value #TINT_PERCENT} percent towards
     * white, as {@code #rrggbb}.
     */
    static String tint(int rgb) {
        int mixed = 0;
        for (int shift = 16; shift >= 0; shift -= 8) {
            int channel = (rgb >> shift) & 0xff;
            // 255 - round((255 - channel) * 0.15), in whole numbers, so that a half always rounds up
            mixed = (mixed << 8) | (255 - ((255 - channel) * TINT_PERCENT + 50) / 100);
        }
        return ColourMath.hex(mixed);
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
