package com.mcmiddleearth.architect.biomeTuning;

import com.google.gson.JsonElement;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.NamespacedKey;

/**
 * Chat lines for /biometune. Colour swatches are drawn in their exact colour. Buttons run commands, so each click
 * is permission-checked again by the command itself.
 */
public final class ChatFeedback {

    /** What info shows in place of a label when a biome has none to change: a note, with nothing to click. */
    public enum NoLabel {
        /** Claiming a free spare names it; until then it takes no label. */
        FREE_SPARE("(a free spare: claiming one names it)"),
        /** The labels file can't be read; the reason follows the values. */
        UNREADABLE("(can't be read, see below)");

        private final String note;

        NoLabel(String note) {
            this.note = note;
        }
    }

    private ChatFeedback() {
    }

    /** "[Biome] cbc:x sky_color ██ #ffaa00 → ██ #2040ff  [Preview] [Publish] [Save] [Undo] [Revert]" */
    public static Component change(BiomeTuningService.Change change) {
        String biome = change.biome().toString();
        return prefix()
                .append(Component.text(biome + " " + change.property().name() + " ", NamedTextColor.GRAY))
                .append(value(change.before()))
                .append(Component.text(" → ", NamedTextColor.GRAY))
                .append(value(change.after()))
                .append(Component.text("  "))
                .append(button("Preview", "/biometune preview", "Refresh your own view (1-3 s)"))
                .append(button("Publish", "/biometune publish", "Refresh everyone in your world"))
                .append(button("Save", "/biometune save " + biome, "Write this biome to the datapack"))
                .append(button("Undo", "/biometune undo " + biome, "Step back one edit"))
                .append(button("Revert", "/biometune revert " + biome, "Go back to the saved file"));
    }

    /**
     * The biome's saved state with an [Editor] button, its label, its file, then one clickable line per value that is
     * set. A click on the label fills in the command that changes it, with the label it has, as a rename is an edit.
     * {@code label} is null for a biome without one. {@code noLabel}, when not null, stands in the label's place;
     * with neither, the line says "(not set)".
     */
    public static List<Component> info(BiomeDocument doc, String label, NoLabel noLabel) {
        List<Component> lines = new ArrayList<>();
        lines.add(prefix().append(Component.text(doc.key() + (doc.isUnsaved() ? "  ● unsaved  " : "  ✓ saved  "),
                        doc.isUnsaved() ? NamedTextColor.GOLD : NamedTextColor.GREEN))
                .append(button("Editor", "/biometune edit " + doc.key(), "Open the editor for this biome")));
        Component labelLine = Component.text("  label: ", NamedTextColor.GRAY);
        if (noLabel != null) {
            lines.add(labelLine.append(Component.text(noLabel.note, NamedTextColor.WHITE)));
        } else {
            lines.add(labelLine.append(Component.text(label == null ? "(not set)" : label, NamedTextColor.WHITE))
                    .clickEvent(ClickEvent.suggestCommand("/biometune label " + doc.key() + " "
                            + (label == null ? "" : label)))
                    .hoverEvent(HoverEvent.showText(Component.text(label == null ? "Click to give it a label"
                            : "Click to change its label"))));
        }
        lines.add(Component.text("  file: " + doc.file(), NamedTextColor.DARK_GRAY));
        for (TuningProperty property : TuningProperties.all()) {
            JsonElement value = doc.value(property);
            if (value != null) {
                lines.add(Component.text("  " + property.name() + " ", NamedTextColor.GRAY)
                        .append(value(value))
                        .clickEvent(ClickEvent.suggestCommand("/biometune set " + doc.key() + " " + property.name() + " "))
                        .hoverEvent(HoverEvent.showText(Component.text("Click to change " + property.name()))));
            }
        }
        return lines;
    }

    /**
     * /biometune spares: how many spares are claimed and free and which one the next claim takes, then each claimed
     * spare with its label and an [Editor] button. {@code spares} are the claimed ones and those a claim can take, in
     * the order it takes them; {@code claimed} maps the claimed ones to their labels, in order.
     */
    public static List<Component> spares(List<NamespacedKey> spares, Map<NamespacedKey, String> claimed) {
        List<NamespacedKey> free = spares.stream().filter(spare -> !claimed.containsKey(spare)).toList();
        String summary = spares.isEmpty() ? "This server has no spare biomes yet. " + SparePool.HOW_TO_ADD
                : "Spare biomes: " + claimed.size() + " claimed, " + free.size() + " free"
                        + (free.isEmpty() ? ". " + SparePool.HOW_TO_ADD : "; the next claim takes " + free.get(0) + ".")
                        + " Paint only with a spare you have claimed.";
        List<Component> lines = new ArrayList<>();
        lines.add(prefix().append(Component.text(summary, NamedTextColor.GRAY)));
        claimed.forEach((spare, label) -> lines.add(Component.text("  " + spare + " ", NamedTextColor.GRAY)
                .append(Component.text(label + " ", NamedTextColor.WHITE))
                .append(button("Editor", "/biometune edit " + spare, "Open the editor for this biome"))));
        return lines;
    }

    /**
     * What a claim did: the spare, its label and the id to paint with, and whose look it took until saved, or why that
     * look could not be copied and how to copy it later, which a click fills in. A look that copied nothing was there
     * already, and the reply says so.
     */
    public static List<Component> claimed(SparePool.Claim claim, NamespacedKey from) {
        String spare = claim.spare().toString();
        List<Component> lines = new ArrayList<>();
        lines.add(prefix().append(Component.text("Claimed " + spare + " as '" + claim.label() + "'. Paint with "
                        + "//setbiome " + spare + " ", NamedTextColor.GRAY))
                .append(button("Editor", "/biometune edit " + spare, "Open the editor for this biome")));
        if (claim.copyFailure() != null) {
            lines.add(Component.text("  " + lookNotCopied(claim, from), NamedTextColor.RED)
                    .clickEvent(ClickEvent.suggestCommand(copy(from, claim.spare())))
                    .hoverEvent(HoverEvent.showText(Component.text("Click, then add a group: sky, fog, water, "
                            + "climate, audio or particles"))));
        } else if (!claim.copied().isEmpty()) {
            lines.add(Component.text("  It took the look of " + from + ", live now and not saved yet ", NamedTextColor.GRAY)
                    .append(button("Save", "/biometune save " + spare, "Write this biome to the datapack")));
        } else if (from != null) {
            lines.add(Component.text("  It already has the look of " + from + ".", NamedTextColor.GRAY));
        }
        return lines;
    }

    /**
     * Why a claim's look could not be copied, and how to copy it a group at a time, as copying all of it again would
     * fail the same way.
     */
    private static String lookNotCopied(SparePool.Claim claim, NamespacedKey from) {
        return "Its look could not be copied from " + from + " (" + claim.copyFailure() + "); copy it a group at a "
                + "time instead: " + copy(from, claim.spare()) + "sky, then fog, water, climate, audio and particles";
    }

    /** "  cbc:x Harbour [Save] [Revert] [Info]" for the status list; {@code label} is null for a biome without one. */
    public static Component unsavedLine(NamespacedKey biome, String label) {
        return Component.text("  " + biome + " ", NamedTextColor.GOLD)
                .append(label == null ? Component.empty() : Component.text(label + " ", NamedTextColor.WHITE))
                .append(button("Save", "/biometune save " + biome, "Write this biome to the datapack"))
                .append(button("Revert", "/biometune revert " + biome, "Go back to the saved file"))
                .append(button("Info", "/biometune info " + biome, "Show its values"));
    }

    /** A value; colours get a swatch drawn in their own colour. */
    public static Component value(JsonElement value) {
        String text = TuningProperties.display(value);
        Integer rgb = rgb(value);
        if (rgb == null) {
            return Component.text(text, NamedTextColor.WHITE);
        }
        return Component.text("██", TextColor.color(rgb)).append(Component.text(" " + text, NamedTextColor.WHITE));
    }

    /**
     * The line each player reads just before a publish refreshes their view. It has no [Biome] prefix, because every
     * player it refreshes reads it, not just builders. {@code by} is the publisher's name, or "the server" for the
     * console and RCON.
     */
    public static Component publishAnnouncement(String by) {
        return Component.text("Biome colours were updated by " + by + " - refreshing your view...", NamedTextColor.AQUA);
    }

    /** The RGB part of "#rrggbb" or "#aarrggbb", or null for anything else. */
    static Integer rgb(JsonElement value) {
        Integer argb = ColourMath.argb(value);
        return argb == null ? null : argb & 0xffffff;
    }

    private static Component prefix() {
        return Component.text("[Biome] ", NamedTextColor.DARK_AQUA);
    }

    /** The copy from one biome to another, up to the groups: "/biometune copy a b ". */
    private static String copy(NamespacedKey from, NamespacedKey to) {
        return "/biometune copy " + from + " " + to + " ";
    }

    private static Component button(String label, String command, String hover) {
        return Component.text("[" + label + "] ", NamedTextColor.AQUA)
                .clickEvent(ClickEvent.runCommand(command))
                .hoverEvent(HoverEvent.showText(Component.text(hover)));
    }
}
