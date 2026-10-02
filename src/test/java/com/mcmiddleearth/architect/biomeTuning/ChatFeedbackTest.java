package com.mcmiddleearth.architect.biomeTuning;

import com.google.gson.JsonPrimitive;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.NamespacedKey;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.*;

class ChatFeedbackTest {

    private static String plain(Component component) {
        return PlainTextComponentSerializer.plainText().serialize(component);
    }

    private static void commands(Component component, List<String> out) {
        ClickEvent<?> click = component.clickEvent();
        if (click != null && click.action() == ClickEvent.Action.RUN_COMMAND) {
            out.add(((ClickEvent.Payload.Text) click.payload()).value());
        }
        component.children().forEach(child -> commands(child, out));
    }

    @Test
    void aChangeShowsBothValuesAndTheFollowUpCommands() {
        Component line = ChatFeedback.change(new BiomeTuningService.Change(NamespacedKey.fromString("cbc:111g380-5p"),
                TuningProperties.resolve("sky_color").orElseThrow(), new JsonPrimitive("#ffaa00"), new JsonPrimitive("#2040ff")));
        String text = plain(line);
        assertTrue(text.contains("cbc:111g380-5p sky_color ██ #ffaa00 → ██ #2040ff"), text);
        List<String> commands = new ArrayList<>();
        commands(line, commands);
        assertEquals(List.of("/biometune preview", "/biometune publish", "/biometune save cbc:111g380-5p",
                "/biometune undo cbc:111g380-5p", "/biometune revert cbc:111g380-5p"), commands);
    }

    @Test
    void onlyColoursGetASwatchInTheirOwnColour() {
        assertEquals(TextColor.color(0xff0000), ChatFeedback.value(new JsonPrimitive("#80ff0000")).color(),
                "an ARGB swatch shows the RGB part");
        assertEquals("0.7", plain(ChatFeedback.value(new JsonPrimitive(0.7))));
        assertEquals("(not set)", plain(ChatFeedback.value(null)));
    }

    @Test
    void anUnsavedLineOffersSaveAndRevert() {
        List<String> commands = new ArrayList<>();
        commands(ChatFeedback.unsavedLine(NamespacedKey.fromString("cbc:111g380-5p"), null), commands);
        assertEquals(List.of("/biometune save cbc:111g380-5p", "/biometune revert cbc:111g380-5p",
                "/biometune info cbc:111g380-5p"), commands);
    }

    @Test
    void infoOffersTheEditor(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("111g380-5p.json");
        Files.writeString(file, "{\"attributes\":{\"minecraft:visual/sky_color\":\"#ffaa00\"}}");
        BiomeDocument doc = BiomeDocument.load(NamespacedKey.fromString("cbc:111g380-5p"), file, 20);
        List<String> commands = new ArrayList<>();
        commands(ChatFeedback.info(doc, null, null).get(0), commands);
        assertEquals(List.of("/biometune edit cbc:111g380-5p"), commands);
        assertEquals("[Biome] cbc:111g380-5p  ✓ saved  [Editor] ", plain(ChatFeedback.info(doc, null, null).get(0)),
                "the button follows the saved state");
    }

    // a rename is an edit of the label there is, so a click fills it in
    @Test
    void infoShowsTheLabelWhichAClickChanges(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("111g380-5p.json");
        Files.writeString(file, "{\"attributes\":{\"minecraft:visual/sky_color\":\"#ffaa00\"}}");
        BiomeDocument doc = BiomeDocument.load(NamespacedKey.fromString("cbc:111g380-5p"), file, 20);

        Component labelled = ChatFeedback.info(doc, "Harbour at dusk", null).get(1);
        Component unlabelled = ChatFeedback.info(doc, null, null).get(1);

        assertEquals("  label: Harbour at dusk", plain(labelled));
        assertEquals(ClickEvent.suggestCommand("/biometune label cbc:111g380-5p Harbour at dusk"),
                labelled.clickEvent(), "the label there is, to edit");
        assertEquals("  label: (not set)", plain(unlabelled));
        assertEquals(ClickEvent.suggestCommand("/biometune label cbc:111g380-5p "), unlabelled.clickEvent(),
                "room for a first label");
    }

    @Test
    void aClaimWhoseLookWasRefusedSaysHowToCopyItLater() {
        NamespacedKey spare = NamespacedKey.fromString("mcme:custom_004");
        NamespacedKey ash = NamespacedKey.fromString("minecraft:basalt_deltas");

        List<Component> lines = ChatFeedback.claimed(
                new SparePool.Claim(spare, "Ash", List.of(), "invalid biome: the codec says no"), ash);

        assertTrue(plain(lines.get(0)).contains("//setbiome mcme:custom_004"), plain(lines.get(0)));
        assertTrue(plain(lines.get(1)).contains("the codec says no")
                && plain(lines.get(1)).contains("/biometune copy minecraft:basalt_deltas mcme:custom_004 sky")
                && plain(lines.get(1)).contains("a group at a time"),
                "copying the whole look again would fail the same way: " + plain(lines.get(1)));
        assertEquals(NamedTextColor.RED, lines.get(1).color());
        assertEquals(ClickEvent.suggestCommand("/biometune copy minecraft:basalt_deltas mcme:custom_004 "),
                lines.get(1).clickEvent(), "a click fills in the copy, and the builder adds a group");
        assertNotNull(lines.get(1).hoverEvent(), "the hover says so");
    }

    @Test
    void aClaimThatTookALookSaysSoAndOffersSave() {
        NamespacedKey spare = NamespacedKey.fromString("mcme:custom_004");
        NamespacedKey ash = NamespacedKey.fromString("minecraft:basalt_deltas");
        BiomeTuningService.Change sky = new BiomeTuningService.Change(spare,
                TuningProperties.resolve("sky_color").orElseThrow(), new JsonPrimitive("#78a7ff"),
                new JsonPrimitive("#123456"));

        List<Component> lines = ChatFeedback.claimed(new SparePool.Claim(spare, "Ash", List.of(sky), null), ash);

        assertEquals(2, lines.size(), "the claim, and the look it took");
        assertTrue(plain(lines.get(1)).contains("took the look of minecraft:basalt_deltas"), plain(lines.get(1)));
        assertTrue(plain(lines.get(1)).contains("live now and not saved yet"), "Save keeps it: " + plain(lines.get(1)));
        List<String> commands = new ArrayList<>();
        commands(lines.get(1), commands);
        assertEquals(List.of("/biometune save mcme:custom_004"), commands, "its Save button saves the spare");
    }

    // a claim without a source took no look, so there is nothing more to say
    @Test
    void aClaimWithoutASourceIsOneLine() {
        NamespacedKey spare = NamespacedKey.fromString("mcme:custom_004");

        List<Component> lines = ChatFeedback.claimed(new SparePool.Claim(spare, "Blank", List.of(), null), null);

        assertEquals(1, lines.size(), "the claim alone: " + lines.stream().map(ChatFeedbackTest::plain).toList());
    }

    @Test
    void thePublishAnnouncementSaysWhoPublished() {
        assertEquals("Biome colours were updated by the server - refreshing your view...",
                plain(ChatFeedback.publishAnnouncement("the server")));
        assertEquals(NamedTextColor.AQUA, ChatFeedback.publishAnnouncement("the server").color(),
                "the colour the command used before");
    }

    // with every spare claimed there is no next one to name, only how to add more
    @Test
    void aListWithEverySpareClaimedSaysHowToAddMore() {
        NamespacedKey first = NamespacedKey.fromString("mcme:custom_001");
        NamespacedKey second = NamespacedKey.fromString("mcme:custom_002");
        Map<NamespacedKey, String> claimed = new LinkedHashMap<>(); // in order, as the command builds it
        claimed.put(first, "Harbour");
        claimed.put(second, "Quay");

        List<Component> lines = ChatFeedback.spares(List.of(first, second), claimed);

        assertTrue(plain(lines.get(0)).contains("2 claimed, 0 free")
                && plain(lines.get(0)).contains("/biometune spares add"), plain(lines.get(0)));
        assertEquals(3, lines.size(), "the summary, and a line for each claimed spare");
        assertTrue(plain(lines.get(1)).startsWith("  mcme:custom_001 Harbour")
                && plain(lines.get(2)).startsWith("  mcme:custom_002 Quay"),
                "in the map's order: " + plain(lines.get(1)) + " / " + plain(lines.get(2)));
    }
}
