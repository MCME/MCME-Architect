package com.mcmiddleearth.architect.mapLayers.customBiomes;

import com.mcmiddleearth.architect.mapLayers.MapShape;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * The HTML of the window that opens when someone clicks a painted spot on the map: one section for each custom biome
 * painted there, from the highest down. The markup is fixed down to its bytes, because the size of the map's download
 * is worked out from it. Text a builder may have typed goes through {@link MapShape#html}.
 */
public final class BiomeWindow {

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
     */
    public record Section(String biome, String label, int minY, int maxY, int places, int chunks,
                          BiomeFacts.Settings settings) {

        public Section {
            Objects.requireNonNull(biome, "biome");
        }
    }

    private BiomeWindow() {
    }

    /** The window for these sections, in the order given; nothing for none. */
    public static String html(List<Section> sections) {
        List<String> parts = new ArrayList<>();
        for (Section section : sections) {
            parts.add(section(section));
        }
        return String.join("<hr/>", parts);
    }

    private static String section(Section section) {
        String id = MapShape.html(section.biome());
        boolean named = section.label() != null && !section.label().isBlank();
        StringBuilder html = new StringBuilder();
        html.append("<b>").append(named ? MapShape.html(section.label()) : id).append("</b> <code>").append(id)
                .append("</code><br/>");
        html.append("Y ").append(section.minY()).append(" to ").append(section.maxY())
                .append(" here &middot; painted in ").append(count(section.places(), "place"))
                .append(" on this world, about ").append(count(section.chunks(), "chunk")).append("<br/>");
        BiomeFacts.Settings settings = section.settings();
        if (settings == null) {
            html.append("<i>Its settings cannot be read here.</i><br/>");
        } else {
            for (Map.Entry<String, List<BiomeFacts.Setting>> group : settings.groups().entrySet()) {
                if (!group.getValue().isEmpty()) {
                    html.append(line(group.getKey(), group.getValue()));
                }
            }
            if (settings.unsaved()) {
                html.append("<i>Changed in game, not saved yet</i><br/>");
            }
        }
        return html.append("<code>/biometune edit ").append(id).append("</code> &middot; <code>//setbiome ").append(id)
                .append("</code>").toString();
    }

    /** One group's line: its heading, then its settings divided by dots. */
    private static String line(String heading, List<BiomeFacts.Setting> settings) {
        List<String> items = new ArrayList<>();
        for (BiomeFacts.Setting setting : settings) {
            String words = MapShape.html(setting.name()) + " " + MapShape.html(setting.text());
            // the colour is built from the number alone, never from text, so nothing typed can reach the style
            items.add(setting.rgb() == null ? words
                    : "<span style=\"color:" + String.format("#%06x", setting.rgb() & 0xffffff) + "\">&#9632;</span> "
                            + words);
        }
        return "<i>" + MapShape.html(heading) + "</i>: " + String.join(" &middot; ", items) + "<br/>";
    }

    /** "1 place" or "2 places". */
    private static String count(int number, String noun) {
        return number + " " + noun + (number == 1 ? "" : "s");
    }
}
