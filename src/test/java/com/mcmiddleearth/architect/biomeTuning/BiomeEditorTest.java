package com.mcmiddleearth.architect.biomeTuning;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mcmiddleearth.pluginutil.message.MessageUtil;
import io.papermc.paper.connection.PlayerConnection;
import io.papermc.paper.event.player.PlayerClientLoadedWorldEvent;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.ComponentIteratorType;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.permissions.PermissionAttachment;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import static org.junit.jupiter.api.Assertions.*;

/** Clicks the editor's buttons the way Paper would, with a recorder in place of the client. */
class BiomeEditorTest {

    private static final NamespacedKey CBC = NamespacedKey.fromString("cbc:111g380-5p");
    private static final String SAMPLE = "{\"attributes\":{\"minecraft:visual/fog_color\":\"#c0d8ff\","
            + "\"minecraft:visual/sky_color\":\"#ffaa00\"},\"downfall\":0.8,\"effects\":{\"water_color\":\"#263a22\"},"
            + "\"has_precipitation\":true,\"temperature\":0.7}";

    @TempDir
    Path dir;
    private ServerMock server;
    private Plugin plugin;
    private Path file;
    private FakeNmsBridge bridge;
    private BiomeTuningService service;
    /** The biome ids the server knows. */
    private final List<NamespacedKey> known = new ArrayList<>();
    private SparePool spares;
    private RefreshCoordinator refresher;
    private RecordingPresenter screen;
    private BiomeEditor editor;
    private PlayerMock builder;
    private PermissionAttachment tune;
    private boolean refuseReconfiguration;
    /** The editor's and the refresher's clock, which a test can move forward. */
    private final AtomicLong now = new AtomicLong(1_000);
    /** The log the editor, the service and the spare pool write to. */
    private final Logger logger = Logger.getLogger("test");
    /** What they logged in this test, a line per record. */
    private final List<String> logged = new ArrayList<>();
    private final Handler recorder = new Handler() {
        @Override
        public void publish(LogRecord record) {
            logged.add(record.getMessage());
        }

        @Override
        public void flush() {
        }

        @Override
        public void close() {
        }
    };

    @BeforeEach
    void setUp() throws Exception {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin();
        file = dir.resolve("datapacks/bukkit/data/cbc/worldgen/biome/111g380-5p.json");
        Files.createDirectories(file.getParent());
        Files.writeString(file, SAMPLE);
        bridge = new FakeNmsBridge();
        logger.addHandler(recorder);
        service = new BiomeTuningService(bridge, dir.resolve("datapacks"), dir.resolve("backups"),
                BiomeTuningSettings.DEFAULTS, logger);
        spares = new SparePool(SpareIds.DEFAULT, "bukkit", known::stream, () -> dir.resolve("datapacks"),
                new BiomeLabels(() -> dir.resolve("datapacks"), "bukkit"), service,
                () -> Instant.parse("2026-09-29T12:00:00Z"), logger);
        service.lock(spares::lockedBecause); // as BiomeTuning wires them
        refresher = new RefreshCoordinator(bridge, now::get, logger, this::reenter, BiomeTuningSettings.DEFAULTS);
        screen = new RecordingPresenter();
        MessageUtil messages = new MessageUtil();
        messages.setPluginName("Architect");
        editor = new BiomeEditor(service, spares, refresher, screen, now::get, BiomeTuningSettings.DEFAULTS, logger,
                messages);
        server.getPluginManager().registerEvents(refresher, plugin);
        server.getPluginManager().registerEvents(editor, plugin);
        builder = server.addPlayer("Builder");
        builder.setOp(false);
        tune = builder.addAttachment(plugin, "architect.biomeTune", true);
        while (builder.nextMessage() != null) {
            // ignore join messages
        }
    }

    @AfterEach
    void tearDown() {
        logger.removeHandler(recorder);
        MockBukkit.unmock();
    }

    /** Like Paper: leaving the world fires the quit event inside the call, unless a login listener blocks it. */
    private PlayerConnection reenter(Player player) {
        if (!refuseReconfiguration) {
            server.getPluginManager().callEvent(new PlayerQuitEvent(player, Component.text("left"),
                    PlayerQuitEvent.QuitReason.DISCONNECTED));
        }
        return null;
    }

    /** The refreshed player is back in the world, and their client has loaded it. */
    private void backInTheWorld() {
        server.getPluginManager().callEvent(new PlayerJoinEvent(builder, Component.text("joined")));
        server.getPluginManager().callEvent(new PlayerClientLoadedWorldEvent(builder, false));
    }

    private static String plain(Component component) {
        return PlainTextComponentSerializer.plainText().serialize(component);
    }

    private EditorView window() {
        EditorView view = screen.of(builder);
        assertNotNull(view, "a window is showing");
        return view;
    }

    /** The title and every line of the window on screen. */
    private String text() {
        StringBuilder all = new StringBuilder(plain(window().title()));
        window().body().forEach(line -> all.append('\n').append(plain(line)));
        return all.toString();
    }

    private List<String> buttons() {
        return window().buttons().stream().map(button -> plain(button.label())).toList();
    }

    private void click(String label, FakeAnswers answers) {
        EditorView.Button button = window().buttons().stream().filter(b -> plain(b.label()).endsWith(label))
                .findFirst().orElseThrow(() -> new AssertionError("no button '" + label + "' in " + buttons()));
        button.action().run(builder, answers);
    }

    /** Clicks with every input as it was sent, like a player who changed nothing. */
    private void click(String label) {
        click(label, FakeAnswers.untouched(window()));
    }

    /** Escape: the client closes the window and sends its footer button, Close. */
    private void escape() {
        assertEquals("Close", plain(window().exit().label()));
        window().exit().action().run(builder, FakeAnswers.untouched(window()));
    }

    private JsonObject live() {
        return bridge.applied.get(CBC);
    }

    /** A value as it is live now, or null when it is not set. */
    private JsonElement liveValue(String name) throws BiomeTuningException {
        return service.value(CBC, TuningProperties.resolve(name).orElseThrow());
    }

    /**
     * A value as text, or null when there is none: a check of it then fails with its own message, not with a
     * NullPointerException.
     */
    private static String asText(JsonElement value) {
        return value == null ? null : value.getAsString();
    }

    /** The live sky colour, or null when it is not set. */
    private String liveSky() {
        return asText(live().getAsJsonObject("attributes").get("minecraft:visual/sky_color"));
    }

    private EditorView.Slider slider(String key) {
        return (EditorView.Slider) window().inputs().stream().filter(input -> input.key().equals(key)).findFirst()
                .orElseThrow(() -> new AssertionError("no input '" + key + "'"));
    }

    /** Spares the server loaded at startup: their files are in the pack, and the registry knows them. */
    private void loadedSpares(int count) throws Exception {
        for (int number = 1; number <= count; number++) {
            NamespacedKey spare = SpareIds.DEFAULT.key(number);
            Path spareFile = dir.resolve("datapacks/bukkit/data/mcme/worldgen/biome/" + spare.getKey() + ".json");
            Files.createDirectories(spareFile.getParent());
            Files.writeString(spareFile, SparePool.NEUTRAL);
            known.add(spare);
        }
    }

    /** The colours of the window's sky-to-horizon strip, from the sky to the fog. */
    private List<Integer> horizon() {
        Component strip = window().body().stream().filter(line -> plain(line).startsWith("Sky to horizon"))
                .findFirst().orElseThrow(() -> new AssertionError("no horizon strip in " + text()));
        return strip.children().get(0).children().stream().map(cell -> cell.color().value()).toList();
    }

    @Test
    void homeShowsTheBiomeItsStateAndWhatTheBuilderMayDo() throws Exception {
        editor.open(builder, CBC);
        assertTrue(text().contains("cbc:111g380-5p") && text().contains("✓ saved"), text());
        assertTrue(text().contains("Sky to horizon"), text());
        assertTrue(buttons().containsAll(List.of("Sky & fog colours", "Distances", "Water & vegetation",
                "Light & celestial", "Climate", "Audio & particles", "Preview in world", "Undo", "Revert…")),
                buttons().toString());
        assertFalse(buttons().contains("Save") || buttons().contains("Publish…"), "no save or publish permission");
        assertEquals(3, window().columns(), "at most four rows of buttons: home fits a GUI 240 px tall");
        builder.addAttachment(plugin, "architect.biomeTune.save", true);
        builder.addAttachment(plugin, "architect.biomeTune.publish", true);
        editor.open(builder, CBC);
        assertTrue(buttons().containsAll(List.of("Save", "Publish…")), buttons().toString());
        assertTrue(buttons().size() <= 12, "four rows of three at most, or home scrolls again: " + buttons());
        click("Save");
        assertTrue(text().contains("Nothing to save"), text());
        click("Undo");
        assertTrue(text().contains("Nothing to undo"), text());
        escape();
        assertTrue(screen.closed.contains(builder.getUniqueId()));
    }

    @Test
    void homeNamesALabelledBiomeByItsLabel() throws Exception {
        spares.label(CBC, "Harbour at dusk", "Builder");

        editor.open(builder, CBC);

        assertEquals("Harbour at dusk  ✓ saved", plain(window().body().get(0)), "home calls it by its label");
        assertTrue(plain(window().title()).contains("cbc:111g380-5p"), "the id stays in the title");
    }

    // a labels file someone broke by hand must not keep builders out of the editor
    @Test
    void anUnreadableLabelsFileStillOpensTheEditor() throws Exception {
        Files.writeString(dir.resolve("datapacks/bukkit/" + BiomeLabels.FILE), "{");

        editor.open(builder, CBC);

        assertEquals("cbc:111g380-5p  ✓ saved", plain(window().body().get(0)), "the id, where no label can be read");
    }

    // the builder likes this biome but wants a variant: the new one starts with this look, as an unsaved edit
    @Test
    void newBiomeClaimsASpareWithThisLookAndOpensIt() throws Exception {
        loadedSpares(2);
        editor.open(builder, CBC);

        click("New biome…");
        click("Create", FakeAnswers.untouched(window()).with("label", "Harbour at dusk"));

        NamespacedKey spare = SpareIds.DEFAULT.key(1);
        assertEquals("Biome editor: mcme:custom_001", plain(window().title()), "the editor is on the new biome");
        assertEquals("Claimed, with the look of cbc:111g380-5p (live; Save keeps it). Paint with it: //setbiome "
                + "mcme:custom_001", firstLine(), "whose look it took, and the id to paint with");
        assertEquals("Harbour at dusk  ● unsaved", plain(window().body().get(1)), "its label, and a look to save");
        assertEquals("#ffaa00", bridge.applied.get(spare).getAsJsonObject("attributes")
                .get("minecraft:visual/sky_color").getAsString(), "this biome's look, live");
        assertEquals(builder.getUniqueId(), spares.labels().entry(spare).orElseThrow().claimedBy(),
                "the builder's claim");
    }

    // the log says who claimed which spare, and whose look it took; a claim that did not go through is not logged
    @Test
    void newBiomeLogsWhoClaimedWhat() throws Exception {
        loadedSpares(2);
        editor.open(builder, CBC);
        click("New biome…");

        click("Create", FakeAnswers.untouched(window()).with("label", " ")); // refused: no label
        click("Create", FakeAnswers.untouched(window()).with("label", "Harbour at dusk"));
        click("New biome…"); // on the new biome's home
        click("Create", FakeAnswers.untouched(window()).with("label", "Blank").with("start", "plains"));

        assertEquals(List.of(
                "biometune: Builder claimed mcme:custom_001 as 'Harbour at dusk' (look of cbc:111g380-5p)",
                "biometune: Builder claimed mcme:custom_002 as 'Blank'"),
                logged.stream().filter(line -> line.contains(" claimed ")).toList());
    }

    // a spare claimed from neutral plains and never changed: a new biome from its look copies nothing, and says why
    @Test
    void newBiomeFromALookTheSpareAlreadyHasSaysSo() throws Exception {
        loadedSpares(2);
        NamespacedKey first = SpareIds.DEFAULT.key(1);
        spares.claim("Harbour", null, builder.getUniqueId(), "Builder");
        editor.open(builder, first);

        click("New biome…");
        click("Create", FakeAnswers.untouched(window()).with("label", "Quay")); // from this biome's look

        assertEquals("Claimed. It already has the look of mcme:custom_001. Paint with it: //setbiome mcme:custom_002",
                firstLine());
        assertEquals(NamedTextColor.GREEN, window().body().get(0).color(), "nothing went wrong");
    }

    @Test
    void newBiomeCanStartFromNeutralPlains() throws Exception {
        loadedSpares(1);
        editor.open(builder, CBC);

        click("New biome…");
        click("Create", FakeAnswers.untouched(window()).with("label", "Blank").with("start", "plains"));

        assertEquals("Biome editor: mcme:custom_001", plain(window().title()), "the editor is on the new biome");
        assertEquals("Blank  ✓ saved", plain(window().body().get(1)), "nothing to save: it is the neutral file");
        assertNull(bridge.applied.get(SpareIds.DEFAULT.key(1)), "a neutral start changes nothing live");
    }

    @Test
    void newBiomeWithoutAFreeSpareSaysHowToAddMore() throws Exception {
        editor.open(builder, CBC);

        click("New biome…");

        assertTrue(text().contains("/biometune spares add"), text());
        assertEquals(List.of("Back"), buttons(), "nothing to create");
    }

    // a claimed spare is not free: with every spare claimed, the window says how to add more
    @Test
    void newBiomeWithEverySpareClaimedSaysHowToAddMore() throws Exception {
        loadedSpares(1);
        spares.claim("Taken", null, null, "CONSOLE");
        editor.open(builder, CBC);

        click("New biome…");

        assertTrue(text().contains("No spare biome is free. " + SparePool.HOW_TO_ADD), text());
        assertEquals(List.of("Back"), buttons(), "nothing to create");
    }

    @Test
    void backInTheNewBiomeWindowReturnsHome() throws Exception {
        loadedSpares(1);
        editor.open(builder, CBC);
        click("New biome…");

        click("Back");

        assertEquals("Biome editor: cbc:111g380-5p", plain(window().title()), "home, on the same biome");
        assertEquals(List.of(SpareIds.DEFAULT.key(1)), spares.free(), "nothing claimed");
    }

    // the builder knows which id Create takes, and that only the label can change later
    @Test
    void theNewBiomeWindowNamesTheSpareCreateTakes() throws Exception {
        loadedSpares(2);
        editor.open(builder, CBC);

        click("New biome…");

        assertEquals("Create claims mcme:custom_001, the next free spare, with this label, and opens it here. A claim "
                + "can't be undone; you can change the label later.", firstLine());
    }

    // a free spare's look is neutral plains, so on its own home New biome offers no choice: the new biome starts
    // there, and the claim names no source, as it would record the spare as its own source
    @Test
    void newBiomeOnAFreeSparesHomeStartsOnNeutralPlains() throws Exception {
        loadedSpares(1);
        NamespacedKey spare = SpareIds.DEFAULT.key(1);
        editor.open(builder, spare);

        click("New biome…");
        List<String> inputs = window().inputs().stream().map(EditorView.Input::key).toList();
        click("Create", FakeAnswers.untouched(window()).with("label", "Harbour").with("start", "this"));

        assertEquals(List.of("label"), inputs, "nothing to pick: it starts on neutral plains");
        assertEquals("Biome editor: mcme:custom_001", plain(window().title()), "the editor is on the claimed spare");
        assertNull(spares.labels().entry(spare).orElseThrow().copiedFrom(),
                "no source, even from a client that sends this biome's look: not the spare itself");
        assertNull(bridge.applied.get(spare), "neutral plains, as the spare's file has it: nothing went live");
    }

    // a free spare stays neutral until it is claimed: an Apply in the editor opened on it says how to claim one
    @Test
    void anApplyOnAFreeSpareSaysHowToClaimOne() throws Exception {
        loadedSpares(1);
        NamespacedKey spare = SpareIds.DEFAULT.key(1);
        editor.open(builder, spare);
        click("Distances");

        click("Apply", FakeAnswers.untouched(window()).with("fog_end", 48f));

        assertEquals("mcme:custom_001 is a free spare: it stays neutral until it is claimed. Claim one with "
                + "/biometune claim <label>, or New biome… in the editor.", firstLine());
        assertNull(bridge.applied.get(spare), "nothing went live");
    }

    // a free spare has no edit to step back, so Undo says just that, not the lock's message, which makes home scroll
    @Test
    void undoOnAFreeSpareWithNothingToUndoSaysSo() throws Exception {
        loadedSpares(1);
        editor.open(builder, SpareIds.DEFAULT.key(1));

        click("Undo");

        assertEquals("Nothing to undo.", firstLine());
    }

    // someone else took the last spare while the form was open: the window drawn again says so once
    @Test
    void theLastSpareGoingWhileTheFormIsOpenIsSaidOnce() throws Exception {
        loadedSpares(1);
        editor.open(builder, CBC);
        click("New biome…");
        spares.claim("Taken", null, null, "CONSOLE"); // someone else, while the form is open

        click("Create", FakeAnswers.untouched(window()).with("label", "Harbour"));

        assertEquals(1, text().split("No spare biome is free", -1).length - 1, "said once: " + text());
        assertEquals(List.of("Back"), buttons(), "nothing to create");
    }

    // a labels file broken while the form is open stops the claim: the window says why and keeps Back
    @Test
    void aLabelsFileThatCannotBeReadKeepsTheNewBiomeWindow() throws Exception {
        loadedSpares(1);
        editor.open(builder, CBC);
        click("New biome…");
        Files.writeString(dir.resolve("datapacks/bukkit/" + BiomeLabels.FILE), "{"); // someone, by hand

        click("Create", FakeAnswers.untouched(window()).with("label", "Harbour"));

        assertFalse(screen.closed.contains(builder.getUniqueId()), "the window stays open");
        assertTrue(text().startsWith("New biome: cbc:111g380-5p") && text().contains(BiomeLabels.FILE), text());
        assertEquals(1, text().split(BiomeLabels.FILE, -1).length - 1, "said once: " + text());
        assertEquals(List.of("Back"), buttons(), "nothing to create until the file is fixed");
    }

    // nothing is claimed until the label is right, and the form keeps what the builder chose
    @Test
    void aLabelItRefusesClaimsNothingAndKeepsTheForm() throws Exception {
        loadedSpares(1);
        editor.open(builder, CBC);
        click("New biome…");

        click("Create", FakeAnswers.untouched(window()).with("label", "  ").with("start", "plains"));

        assertTrue(text().contains("A label can't be empty."), text());
        assertEquals(List.of(SpareIds.DEFAULT.key(1)), spares.free(), "nothing was claimed");
        EditorView.Choice start = (EditorView.Choice) window().inputs().get(1);
        assertTrue(start.options().stream().anyMatch(option -> option.id().equals("plains") && option.initial()),
                "the choice is kept");
    }

    // Paper does not check answers: a client that sends no choice gets the one the form showed
    @Test
    void aMissingChoiceKeepsTheOneDrawn() throws Exception {
        loadedSpares(1);
        editor.open(builder, CBC);
        click("New biome…");
        click("Create", FakeAnswers.untouched(window()).with("label", " ").with("start", "plains")); // drawn again

        click("Create", FakeAnswers.untouched(window()).with("label", "Blank").with("start", null));

        assertEquals("Biome editor: mcme:custom_001", plain(window().title()), "the claim went through");
        assertNull(bridge.applied.get(SpareIds.DEFAULT.key(1)), "neutral plains, as the form showed");
    }

    @Test
    void aTooLongLabelComesBackCutToFit() throws Exception {
        loadedSpares(1);
        editor.open(builder, CBC);
        click("New biome…");
        String label = "x".repeat(BiomeLabels.LABEL_LIMIT + 8); // only a modified client sends more than the box takes

        click("Create", FakeAnswers.untouched(window()).with("label", label));

        assertTrue(text().contains("at most " + BiomeLabels.LABEL_LIMIT), text());
        EditorView.TextBox box = (EditorView.TextBox) window().inputs().get(0);
        assertEquals(label.substring(0, BiomeLabels.LABEL_LIMIT), box.initial(), "the label, cut to fit the box");
        assertEquals(BiomeLabels.LABEL_LIMIT, box.maxLength(), "the box takes as much as a label holds");
    }

    // the claim went through though the look did not: the editor opens the new biome, so a second click cannot claim
    // another spare. Home has room for a notice of two lines, so why, and how to copy the look a group at a time, go
    // to chat, as the command's claim gives them
    @Test
    void aLookTheGameRefusesStillOpensTheClaimedBiome() throws Exception {
        loadedSpares(2);
        editor.open(builder, CBC);
        click("New biome…");
        bridge.rejectContaining = "#ffaa00";

        click("Create", FakeAnswers.untouched(window()).with("label", "Ash"));

        assertEquals("Biome editor: mcme:custom_001", plain(window().title()), "the editor is on the claimed spare");
        assertEquals("Claimed. Paint with it: //setbiome mcme:custom_001. The look of cbc:111g380-5p could not be "
                + "copied: see chat.", firstLine(), "the id to paint with first, then one short sentence");
        assertEquals(NamedTextColor.RED, window().body().get(0).color(), "the look did not come along");
        List<Component> chat = new ArrayList<>();
        for (Component line = builder.nextComponentMessage(); line != null; line = builder.nextComponentMessage()) {
            chat.add(line);
        }
        List<String> said = chat.stream().map(BiomeEditorTest::plain).toList();
        Component advice = chat.stream().filter(line -> plain(line).contains("could not be copied")).findFirst()
                .orElseThrow(() -> new AssertionError("no advice in chat: " + said));
        assertTrue(plain(advice).contains("(invalid biome: the codec says no)")
                && plain(advice).contains("a group at a time"), "why, and what to do: " + plain(advice));
        assertEquals(ClickEvent.suggestCommand("/biometune copy cbc:111g380-5p mcme:custom_001 "), advice.clickEvent(),
                "a click fills in the copy, and the builder adds a group");
        assertEquals(List.of(SpareIds.DEFAULT.key(2)), spares.free(), "one spare claimed, not two");
    }

    @Test
    void aVanillaBiomeDoesNotOpen() {
        NamespacedKey plains = NamespacedKey.minecraft("plains");
        bridge.vanilla.add(plains);
        BiomeTuningException error = assertThrows(BiomeTuningException.class, () -> editor.open(builder, plains));
        assertTrue(error.getMessage().contains("comes from the game itself"), error.getMessage());
        assertNull(screen.of(builder));
    }

    @Test
    void aSectionAppliesOnlyWhatChangedAsOneUndoStep() throws Exception {
        editor.open(builder, CBC);
        click("Climate");
        assertTrue(text().contains("snow"), "the climate warning");
        assertEquals(2, window().columns(), "the other windows keep two columns, for their longer labels");
        EditorView.Slider temperature = slider("temperature");
        assertEquals(70f, temperature.initial(), "0.7 shows as 70 hundredths");
        assertEquals(5f, temperature.step());
        assertEquals(EditorLayout.HUNDREDTHS, temperature.format());
        assertTrue(temperature.min() < -100 && temperature.max() > 200, "slack keeps both ends reachable");
        assertEquals(80f, slider("downfall").initial(), "0.8 shows as 80 %");
        assertEquals(EditorLayout.PERCENT, slider("downfall").format());
        click("Apply", FakeAnswers.untouched(window()).with("temperature", 25f).with("temperature_modifier", "frozen"));
        assertTrue(text().contains("Changed temperature and temperature modifier (live; preview to see it)."), text());
        assertEquals(0.25, live().get("temperature").getAsDouble(), 1e-9);
        assertEquals("frozen", asText(live().get("temperature_modifier")), "the choice is applied with the slider");
        assertEquals(0.8, live().get("downfall").getAsDouble(), 1e-9, "the untouched slider stayed");
        assertTrue(service.undo(CBC));
        assertEquals(0.7, live().get("temperature").getAsDouble(), 1e-9, "one undo took back both values");
        assertNull(live().get("temperature_modifier"));
        click("Back"); // the undo came from outside this window, which shows it once drawn again
        click("Climate");
        click("Apply", FakeAnswers.untouched(window()).with("temperature", temperature.max()));
        assertEquals(2.0, live().get("temperature").getAsDouble(), 1e-9, "past the end, the slack writes the end");
        click("Apply", FakeAnswers.untouched(window()).with("downfall", 85f));
        assertEquals("0.85", live().get("downfall").getAsString(), "85 % is written as exactly 0.85");
        click("Apply", FakeAnswers.untouched(window()).with("temperature_modifier", "frozen"));
        click("Apply", FakeAnswers.untouched(window()).with("temperature_modifier", EditorForms.NOT_SET));
        assertNull(live().get("temperature_modifier"), "(not set) removes the value");
        click("Back");
        click("Distances");
        EditorView.Slider fogEnd = (EditorView.Slider) window().inputs().stream()
                .filter(input -> input.key().equals("fog_end")).findFirst().orElseThrow();
        click("Apply", FakeAnswers.untouched(window()).with("fog_end", fogEnd.min()));
        assertEquals("0", asText(liveValue("fog_end")), "below 0, the slack writes 0");
    }

    // as in the picker and the JSON editor: a field the builder moved is not written over a value someone else set
    @Test
    void anOutOfDateSectionWritesNothingAndKeepsTheBuildersValues() throws Exception {
        editor.open(builder, CBC);
        click("Distances");
        service.set(CBC, "fog_end", "64"); // someone else, while the section is open

        click("Apply", FakeAnswers.untouched(window()).with("fog_end", 48f).with("sky_fog_end", 96f));

        assertEquals("64", liveValue("fog_end").getAsString(), "not written over");
        assertNull(liveValue("sky_fog_end"), "nor anything else: one Apply is one step");
        assertEquals("Nothing was applied: while this window was open, fog end became 64 blocks. Check, then apply "
                + "again.", firstLine());
        assertEquals(48f, slider("fog_end").initial(), "the builder's values stay on screen");
        assertEquals(96f, slider("sky_fog_end").initial());

        click("Apply"); // the builder saw the warning, and applies on purpose

        assertEquals("48", asText(liveValue("fog_end")), "the builder's value, applied on purpose");
        assertEquals("96", asText(liveValue("sky_fog_end")), "the builder's other value, applied with it");
        assertEquals("Changed fog end and sky fog end (live; preview to see it).", firstLine());
    }

    // the warning names each value as its slider shows it, and a value the window cannot show as it is
    @Test
    void anOutOfDateWarningNamesValuesAsTheirSlidersShowThem() throws Exception {
        editor.open(builder, CBC);
        click("Climate");
        service.edit(CBC, List.of(new BiomeTuningService.Edit("temperature", "0.25"),
                new BiomeTuningService.Edit("downfall", "0.35"))); // someone else, while the section is open

        click("Apply", FakeAnswers.untouched(window()).with("temperature", 40f).with("downfall", 50f));

        assertEquals("Nothing was applied: while this window was open, temperature became 25 (×100) and downfall "
                + "became 35%. Check, then apply again.", firstLine(),
                "in the sliders' units: hundredths, which the text says as the slider's label does, and percent");
        service.set(CBC, "temperature", "2.5"); // more than its slider takes

        click("Apply");

        assertTrue(firstLine().startsWith("Nothing was applied: while this window was open, temperature became 2.5."),
                "a value the window cannot show, as it is: " + firstLine());
    }

    // someone else's publish brings the window back: the builder's value is still checked against what they saw
    @Test
    void keptValuesStayCheckedWhenTheWindowComesBack() throws Exception {
        editor.open(builder, CBC);
        click("Distances");
        service.set(CBC, "fog_end", "64"); // someone else, while the section is open
        click("Apply", FakeAnswers.untouched(window()).with("fog_end", 48f));
        assertTrue(firstLine().startsWith("Nothing was applied"), text());

        service.set(CBC, "fog_end", "80"); // they change it again, then publish
        assertEquals(RefreshCoordinator.Outcome.STARTED, refresher.refresh(builder, true));
        screen.shown.clear();
        backInTheWorld();
        assertEquals(48f, slider("fog_end").initial(), "the builder's value came back with the window");

        click("Apply");

        assertEquals("80", liveValue("fog_end").getAsString(), "not written over");
        assertTrue(firstLine().startsWith("Nothing was applied: while this window was open, fog end became 80"),
                text());
    }

    // after the warning, a value that changes again is caught again
    @Test
    void aKeptValueIsCheckedAgainAtTheNextApply() throws Exception {
        editor.open(builder, CBC);
        click("Distances");
        service.set(CBC, "fog_end", "64");
        click("Apply", FakeAnswers.untouched(window()).with("fog_end", 48f));
        service.set(CBC, "fog_end", "72"); // changed once more, after the warning

        click("Apply");

        assertEquals("72", liveValue("fog_end").getAsString(), "not written over");
        assertTrue(firstLine().startsWith("Nothing was applied: while this window was open, fog end became 72"),
                text());
        assertEquals(48f, slider("fog_end").initial(), "still the builder's value");
    }

    // once the warning is gone, the section still says which values it keeps on screen, until they are applied
    @Test
    void keptValuesAreNamedUntilTheyAreApplied() throws Exception {
        editor.open(builder, CBC);
        click("Distances");
        assertEquals(List.of(), notAppliedYet(), "nothing kept, so no such line");
        service.set(CBC, "fog_end", "64"); // someone else, while the section is open

        click("Apply", FakeAnswers.untouched(window()).with("fog_end", 101f).with("sky_fog_end", 96f));

        String kept = "Not applied yet: fog end 101 blocks (live 64 blocks) and sky fog end 96 blocks (live not set)";
        assertEquals(List.of("Nothing was applied: while this window was open, fog end became 64 blocks. Check, then "
                + "apply again.", kept), lines(), "right under the notice");
        assertEquals(NamedTextColor.GOLD, window().body().get(1).color(), "in the colour of unsaved");
        assertEquals(RefreshCoordinator.Outcome.STARTED, refresher.refresh(builder, true)); // someone else's publish
        screen.shown.clear();
        backInTheWorld();
        assertEquals(List.of(kept), lines(), "the window that comes back has no notice, and still says it");

        click("Apply");

        assertEquals("Changed fog end and sky fog end (live; preview to see it).", firstLine());
        assertEquals(List.of(), notAppliedYet(), "gone with the kept values: " + text());
    }

    // a section with text of its own: the line comes right under the notice, and Back drops it with the values
    @Test
    void theLineOfKeptValuesComesRightUnderTheNotice() throws Exception {
        editor.open(builder, CBC);
        click("Light & celestial");
        service.set(CBC, "sky_light_factor", "0.6"); // someone else, while the section is open

        click("Apply", FakeAnswers.untouched(window()).with("sky_light_factor", 40f));

        assertEquals(List.of("Nothing was applied: while this window was open, sky light factor became 60%. Check, "
                + "then apply again.", "Not applied yet: sky light factor 40% (live 60%)",
                "Click a colour to open the picker."), lines());
        click("Back");
        click("Light & celestial");
        assertEquals(List.of(), notAppliedYet(), "Back drops the kept values, and the line with them");
    }

    // a value someone else removed is "now not set" in the warning, and "live not set" in the line under it
    @Test
    void aValueRemovedMeanwhileIsNowNotSet() throws Exception {
        service.set(CBC, "fog_end", "64");
        editor.open(builder, CBC);
        click("Distances");
        service.unset(CBC, "fog_end"); // someone else, while the section is open

        click("Apply", FakeAnswers.untouched(window()).with("fog_end", 48f));

        assertEquals(List.of("Nothing was applied: while this window was open, fog end is now not set. Check, then "
                + "apply again.", "Not applied yet: fog end 48 blocks (live not set)"), lines());
    }

    // the line sits by the sliders, whose labels show the scale, so it leaves out the "(×100)" the warning has
    @Test
    void theLineOfKeptValuesLeavesTheScaleToTheSliders() throws Exception {
        editor.open(builder, CBC);
        click("Climate");
        service.set(CBC, "downfall", "0.35"); // someone else, while the section is open

        click("Apply", FakeAnswers.untouched(window()).with("temperature", 25f).with("downfall", 50f));

        assertEquals(List.of("Not applied yet: temperature 25 (live 70) and downfall 50% (live 35%)"),
                notAppliedYet());
    }

    // five kept values or more: the line names four, and how many more, as notices do
    @Test
    void theLineOfKeptValuesNamesFourAtMost() throws Exception {
        editor.open(builder, CBC);
        click("Distances");
        service.set(CBC, "fog_end", "64"); // someone else, while the section is open

        click("Apply", FakeAnswers.untouched(window()).with("fog_start", 8f).with("fog_end", 48f)
                .with("sky_fog_end", 96f).with("cloud_fog_end", 160f).with("water_fog_start", 4f));

        assertEquals(List.of("Not applied yet: fog start 8 blocks (live not set), fog end 48 blocks (live 64 blocks), "
                + "sky fog end 96 blocks (live not set), cloud fog end 160 blocks (live not set) and 1 more"),
                notAppliedYet());
    }

    /** Every line of the window on screen, notice first. */
    private List<String> lines() {
        return window().body().stream().map(BiomeEditorTest::plain).toList();
    }

    /** The section's line about the values it keeps on screen, if it shows one. */
    private List<String> notAppliedYet() {
        return lines().stream().filter(line -> line.startsWith("Not applied")).toList();
    }

    // a switch and a choice keep the builder's value through the warning, as sliders do; "(not set)" too
    @Test
    void aChoiceAndASwitchKeepTheBuildersValuesToo() throws Exception {
        service.set(CBC, "grass_modifier", "swamp");
        editor.open(builder, CBC);
        click("Water & vegetation");
        service.set(CBC, "grass_modifier", "dark_forest"); // someone else, while the section is open
        click("Apply", FakeAnswers.untouched(window()).with("grass_modifier", EditorForms.NOT_SET));
        assertTrue(firstLine().startsWith("Nothing was applied"), text());
        assertEquals(List.of("Not applied yet: grass colour modifier not set (live dark_forest)"), notAppliedYet(),
                "a kept (not set) reads as not set");
        click("Apply");
        assertNull(liveValue("grass_modifier"), "the builder's (not set), applied on purpose");

        click("Back");
        click("Audio & particles");
        service.set(CBC, "firefly_bush_sounds", "false"); // someone else: from unset to an explicit false
        click("Apply", FakeAnswers.untouched(window()).with("firefly_bush_sounds", true));
        assertTrue(firstLine().startsWith("Nothing was applied"), text());
        assertEquals(List.of("Not applied yet: firefly bush sounds true (live false)"), notAppliedYet());
        click("Apply");
        assertTrue(liveValue("firefly_bush_sounds").getAsBoolean(), "the builder's switch, applied on purpose");
    }

    // after the warning the builder may change more: what they change now wins, in the window's order
    @Test
    void whatTheBuilderChangesAfterTheWarningWins() throws Exception {
        editor.open(builder, CBC);
        click("Distances");
        service.set(CBC, "sky_fog_end", "100"); // someone else, while the section is open
        click("Apply", FakeAnswers.untouched(window()).with("sky_fog_end", 96f));
        assertTrue(firstLine().startsWith("Nothing was applied"), text());

        click("Apply", FakeAnswers.untouched(window()).with("sky_fog_end", 88f).with("fog_end", 56f));

        assertEquals("88", asText(liveValue("sky_fog_end")), "the value changed after the warning wins");
        assertEquals("56", asText(liveValue("fog_end")), "the builder's new value, applied with it");
        assertEquals("Changed fog end and sky fog end (live; preview to see it).", firstLine(), "in the window's order");
    }

    // an Apply the game refuses draws the window again with that click's values, not with older ones kept from a
    // warning: an untouched Apply after it must not write back what the builder had just moved away from
    @Test
    void aRefusedApplyKeepsTheValuesOfThatClick() throws Exception {
        editor.open(builder, CBC);
        click("Distances");
        service.set(CBC, "fog_end", "64"); // someone else, while the section is open
        click("Apply", FakeAnswers.untouched(window()).with("fog_end", 48f));
        assertTrue(firstLine().startsWith("Nothing was applied"), text());
        bridge.rejectContaining = "sky_fog_end_distance\":96"; // the game refuses this value

        click("Apply", FakeAnswers.untouched(window()).with("fog_end", 64f).with("sky_fog_end", 96f));

        assertTrue(firstLine().contains("the codec says no"), text());
        assertEquals(64f, slider("fog_end").initial(), "the builder took the other value, and it stays on screen");
        assertEquals(96f, slider("sky_fog_end").initial(), "the refused value stays too, to change");
        bridge.rejectContaining = null;
        click("Apply", FakeAnswers.untouched(window()).with("sky_fog_end", 88f));
        assertEquals("64", liveValue("fog_end").getAsString(), "48 was not written back");
        assertEquals("88", asText(liveValue("sky_fog_end")), "the builder's other value is applied");
    }

    // the same for Apply + preview, which does not preview then
    @Test
    void aRefusedApplyAndPreviewKeepsTheValuesOfThatClick() throws Exception {
        editor.open(builder, CBC);
        click("Distances");
        bridge.rejectContaining = "sky_fog_end_distance\":96"; // the game refuses this value

        click("Apply + preview in world", FakeAnswers.untouched(window()).with("fog_end", 48f)
                .with("sky_fog_end", 96f));

        assertTrue(firstLine().contains("the codec says no"), text());
        assertFalse(refresher.isRefreshing(builder.getUniqueId()), "nor did it preview");
        assertEquals(48f, slider("fog_end").initial(), "the builder's other move stays on screen");
        assertEquals(96f, slider("sky_fog_end").initial());
    }

    @Test
    void anOutOfDateSectionDoesNotPreviewEither() throws Exception {
        editor.open(builder, CBC);
        click("Distances");
        service.set(CBC, "fog_end", "64"); // someone else, while the section is open

        click("Apply + preview in world", FakeAnswers.untouched(window()).with("fog_end", 48f));

        assertEquals("64", liveValue("fog_end").getAsString());
        assertFalse(refresher.isRefreshing(builder.getUniqueId()), "nor did it preview");
        assertTrue(firstLine().startsWith("Nothing was applied: while this window was open, fog end became 64"),
                text());
        assertEquals(48f, slider("fog_end").initial());
    }

    @Test
    void aFieldTheBuilderDidNotTouchIsNoConflict() throws Exception {
        editor.open(builder, CBC);
        click("Distances");
        service.set(CBC, "fog_end", "64"); // someone else, while the section is open

        click("Apply", FakeAnswers.untouched(window()).with("sky_fog_end", 96f));

        assertEquals("96", asText(liveValue("sky_fog_end")), "the value the builder moved is applied");
        assertEquals("64", liveValue("fog_end").getAsString(), "the untouched slider did not write its old value");
    }

    // someone else set the very value the builder chose: applying it writes nothing, so it is no conflict
    @Test
    void aFieldThatAlreadyHoldsTheBuildersValueIsNoConflict() throws Exception {
        editor.open(builder, CBC);
        click("Distances");
        service.set(CBC, "fog_end", "64");

        click("Apply", FakeAnswers.untouched(window()).with("fog_end", 64f));

        assertEquals("Nothing changed.", firstLine());
        service.set(CBC, "fog_end", "80"); // and once more, through Apply + preview
        click("Apply + preview in world", FakeAnswers.untouched(window()).with("fog_end", 80f));
        screen.shown.clear();
        backInTheWorld();
        assertEquals("Nothing changed.", firstLine(), "said once the window is back");
    }

    // a kept value whose field turned into a modifier is dropped, and the builder's other values apply
    @Test
    void aValueDroppedForAModifierLeavesTheOthersToApply() throws Exception {
        String withModifier = SAMPLE.replace("{\"minecraft:visual/fog_color\"", "{\"minecraft:visual/water_fog_end"
                + "_distance\":{\"argument\":0.85,\"modifier\":\"multiply\"},\"minecraft:visual/fog_color\"");
        editor.open(builder, CBC);
        click("Distances");
        Files.writeString(file, withModifier); // a hand edit, reverted to while the section is open
        service.revert(CBC);
        click("Apply", FakeAnswers.untouched(window()).with("water_fog_end", 32f).with("fog_end", 48f));
        assertTrue(firstLine().startsWith("Nothing was applied"), text());

        click("Apply");

        assertEquals("48", asText(liveValue("fog_end")), "the builder's other value is applied");
        assertTrue(liveValue("water_fog_end").isJsonObject(), "the modifier stays");
    }

    // and it stays dropped: the window a refresh brings back does not bring the value back
    @Test
    void aValueDroppedForAModifierStaysDropped() throws Exception {
        String withModifier = SAMPLE.replace("{\"minecraft:visual/fog_color\"", "{\"minecraft:visual/water_fog_end"
                + "_distance\":{\"argument\":0.85,\"modifier\":\"multiply\"},\"minecraft:visual/fog_color\"");
        editor.open(builder, CBC);
        click("Distances");
        Files.writeString(file, withModifier); // a hand edit, reverted to while the section is open
        service.revert(CBC);
        click("Apply", FakeAnswers.untouched(window()).with("water_fog_end", 32f));
        assertTrue(firstLine().startsWith("Nothing was applied"), text());

        service.set(CBC, "water_fog_end", "40"); // a plain value again, then a publish
        assertEquals(RefreshCoordinator.Outcome.STARTED, refresher.refresh(builder, true));
        screen.shown.clear();
        backInTheWorld();
        assertEquals(40f, slider("water_fog_end").initial(), "the dropped value did not come back");

        click("Apply");

        assertEquals("40", liveValue("water_fog_end").getAsString(), "nothing written over");
    }

    @Test
    void aModifierIsShownAndNeverOverwritten() throws Exception {
        String withModifier = SAMPLE.replace("{\"minecraft:visual/fog_color\"", "{\"minecraft:visual/water_fog_end"
                + "_distance\":{\"argument\":0.85,\"modifier\":\"multiply\"},\"minecraft:visual/fog_color\"");
        Files.writeString(file, withModifier);
        editor.open(builder, CBC);
        click("Distances");
        assertTrue(text().contains("Water fog end is {\"argument\":0.85,\"modifier\":\"multiply\"}"), text());
        assertTrue(window().inputs().stream().noneMatch(input -> input.key().equals("water_fog_end")));
        click("Apply");
        assertTrue(text().contains("Nothing changed"), text());
        assertNull(live(), "nothing was applied");
        Files.writeString(file, SAMPLE);
        service.revert(CBC);
        click("Back");
        click("Distances");
        Files.writeString(file, withModifier); // a hand edit, reverted to while the section is open
        service.revert(CBC);
        click("Apply", FakeAnswers.untouched(window()).with("water_fog_end", 32f));
        assertTrue(firstLine().startsWith("Nothing was applied: while this window was open, water fog end became {"),
                text());
        assertTrue(live().getAsJsonObject("attributes").get("minecraft:visual/water_fog_end_distance").isJsonObject(),
                "the slider did not overwrite the modifier that arrived while the window was open");
        Files.writeString(file, SAMPLE);
        service.revert(CBC);
        click("Back");
        click("Distances");
        Files.writeString(file, withModifier); // and again, for Apply + preview
        service.revert(CBC);
        click("Apply + preview in world", FakeAnswers.untouched(window()).with("water_fog_end", 32f));
        assertTrue(firstLine().startsWith("Nothing was applied: while this window was open, water fog end"), text());
        assertFalse(refresher.isRefreshing(builder.getUniqueId()), "nor did it preview");
        assertTrue(live().getAsJsonObject("attributes").get("minecraft:visual/water_fog_end_distance").isJsonObject());
        Files.writeString(file, SAMPLE.replace("\"temperature\":0.7", "\"temperature\":2.5"));
        service.revert(CBC);
        click("Back");
        click("Climate");
        assertTrue(text().contains("Temperature is 2.5: change it with /biometune set."), text());
        assertTrue(window().inputs().stream().noneMatch(input -> input.key().equals("temperature")),
                "a number outside the slider's range gets no slider either");
    }

    @Test
    void thePickerShowsANewColourBeforeApplyingIt() throws Exception {
        editor.open(builder, CBC);
        click("Sky & fog colours");
        click("Sky");
        assertTrue(text().contains("Live ████ #ffaa00"), text());
        assertTrue(text().contains("Sky to horizon"), "sky and fog mix on screen, so the picker shows both");
        click("Show", FakeAnswers.untouched(window()).with("hex", "#2040ff"));
        assertTrue(text().contains("New ████ #2040ff"), text());
        assertNull(live(), "Show changes nothing");
        click("Apply");
        assertEquals("#2040ff", liveSky(), "untouched inputs keep the exact colour");
        click("Back");
        click("Clouds");
        assertTrue(text().contains("Live (not set)") && text().contains("New ████ #ccffffff"),
                "an unset colour starts at what the Overworld shows: " + text());
        click("Back");
        click("Back");
        click("Water & vegetation");
        click("Water");
        assertFalse(buttons().contains("Clear"), "the game requires a water colour, so there is no Clear");
        click("Back");
        click("Grass");
        click("Apply", FakeAnswers.untouched(window()).with("hue", 120f));
        assertTrue(text().contains("Nothing changed: New only shows where the picker starts"),
                "a hue on grey changes no colour, so nothing was chosen: " + text());
        assertNull(service.value(CBC, TuningProperties.resolve("grass_color").orElseThrow()),
                "unset grass keeps the climate colour map");
        assertEquals(120f, slider("hue").initial(), "the moved hue stays on screen");
    }

    @Test
    void aNoticeIsTheFirstLineSoAScrollingWindowShowsIt() throws Exception {
        editor.open(builder, CBC);
        click("Undo");
        assertEquals("Nothing to undo.", firstLine());
        click("Climate");
        click("Apply");
        assertEquals("Nothing changed.", firstLine());
        click("Back");
        click("Sky & fog colours");
        click("Sky");
        click("Apply", FakeAnswers.untouched(window()).with("hex", "#12"));
        assertTrue(firstLine().contains("'#12' is not a colour"), firstLine());
        service.set(CBC, "sky_color", "#00ff00"); // someone else, while the picker is open
        click("Show");
        assertTrue(firstLine().contains("changed while this window was open"), "a warning comes first too: " + text());
        service.set(CBC, "dripstone_particle", "{\"type\":\"minecraft:dripping_lava\"}");
        click("Back");
        click("Back");
        click("Audio & particles");
        click("Edit Dripstone particle…");
        service.set(CBC, "dripstone_particle", "{\"type\":\"minecraft:dripping_water\"}");
        click("Apply", FakeAnswers.untouched(window()).with("json", " "));
        assertEquals("It is empty: use Clear to remove the value.", firstLine());
        assertTrue(plain(window().body().get(1)).contains("changed while this window was open"),
                "then the warning: " + text());
    }

    private String firstLine() {
        return plain(window().body().get(0));
    }

    /** The colour that a part of the first line sets itself, found by the words it starts with. */
    private TextColor colourOf(String words) {
        for (Component part : window().body().get(0).iterable(ComponentIteratorType.DEPTH_FIRST)) {
            if (part instanceof TextComponent text && text.content().startsWith(words)) {
                return text.color();
            }
        }
        throw new AssertionError("no '" + words + "' in " + firstLine());
    }

    @Test
    void thePickersTextEndsWithTheRulersRightAboveTheSliders() throws Exception {
        editor.open(builder, CBC);
        click("Sky & fog colours");
        click("Sky");
        List<String> lines = window().body().stream().map(BiomeEditorTest::plain).toList();
        String cells = "█".repeat(ColourPicker.RULER_CELLS);
        assertEquals(List.of("Hue", cells, "Saturation", cells, "Brightness", cells),
                lines.subList(lines.size() - 6, lines.size()), "the text ends with the rulers: " + lines);
        assertEquals("hue", window().inputs().get(0).key(), "and the hue slider comes right after them");
    }

    @Test
    void theHorizonShowsWhatAnUnsetSkyOrFogLooksLike() throws Exception {
        service.set(CBC, "fog_color", "#123456"); // a fog of its own first: the file's fog is the Overworld's too
        service.unset(CBC, "fog_color");
        editor.open(builder, CBC);
        assertEquals(List.of(0xffaa00, 0xc0d8ff), ends(horizon()),
                "an unset fog shows as the Overworld's, and the strip stays: " + text());
        click("Sky & fog colours");
        click("Sky");
        assertEquals(List.of(0xffaa00, 0xc0d8ff), ends(horizon()), "in the sky picker too");
        click("Back");
        service.set(CBC, "fog_color", "#ff0000");
        service.unset(CBC, "sky_color");
        click("Fog");
        assertEquals(List.of(0x78a7ff, 0xff0000), ends(horizon()),
                "an unset sky shows as the Overworld's, and the strip ends in New: " + text());
    }

    private static List<Integer> ends(List<Integer> cells) {
        return List.of(cells.get(0), cells.get(cells.size() - 1));
    }

    @Test
    void thePickerKeepsItsHueThroughBlack() throws Exception {
        editor.open(builder, CBC);
        click("Sky & fog colours");
        click("Sky");
        click("Show", FakeAnswers.untouched(window()).with("hex", "#2040ff"));
        click("Show", FakeAnswers.untouched(window()).with("brightness", 0f));
        assertTrue(text().contains("New ████ #000000"), text());
        click("Show", FakeAnswers.untouched(window()).with("brightness", 100f));
        assertTrue(text().contains("New ████ #2142ff"), "the blue comes back, not white: " + text());
    }

    @Test
    void applyingTheSameColourTwiceAddsNoUndoStep() throws Exception {
        Files.writeString(file, SAMPLE.replace("#ffaa00", "#FFAA00"));
        editor.open(builder, CBC);
        click("Sky & fog colours");
        click("Sky");
        click("Apply");
        assertEquals("Nothing changed: that is already the live colour.", firstLine(),
                "#FFAA00 in the file is the same colour");
        assertFalse(service.document(CBC).isUnsaved());
        click("Apply", FakeAnswers.untouched(window()).with("hex", "#2040ff"));
        click("Apply");
        assertEquals("Nothing changed: that is already the live colour.", firstLine());
        assertTrue(service.undo(CBC));
        assertEquals("#FFAA00", liveSky(), "one undo goes all the way back");
        assertFalse(service.undo(CBC), "there was only one step");
    }

    @Test
    void slidersOrATypedHexChangeTheColour() throws Exception {
        editor.open(builder, CBC);
        click("Sky & fog colours");
        click("Sky");
        click("Apply", FakeAnswers.untouched(window()).with("hue", 231f).with("saturation", 87f)
                .with("brightness", 100f));
        assertEquals("#2142ff", liveSky());
        click("Apply", FakeAnswers.untouched(window()).with("hex", "#2040FF").with("hue", 0f));
        assertEquals("#2040ff", liveSky(), "a typed hex wins over the sliders");
        click("Show", FakeAnswers.untouched(window()).with("hex", "#654321"));
        click("Apply", FakeAnswers.untouched(window()).with("hex", "#12"));
        assertTrue(text().contains("'#12' is not a colour") && text().contains("New ████ #654321"),
                "the error keeps the builder's choice: " + text());
        assertEquals("#2040ff", liveSky());
    }

    @Test
    void previewRefreshesAndBringsTheWindowBack() throws Exception {
        editor.open(builder, CBC);
        click("Sky & fog colours");
        click("Sky");
        click("Apply + preview in world", FakeAnswers.untouched(window()).with("hex", "#2040ff"));
        assertEquals("#2040ff", liveSky());
        assertTrue(refresher.isRefreshing(builder.getUniqueId()), "the refresh started");
        screen.shown.clear();
        backInTheWorld();
        assertTrue(text().startsWith("Sky: cbc:111g380-5p"), text());
        assertTrue(text().contains("Live ████ #2040ff"), text());
        assertEquals("Applied.", firstLine(), "the window that comes back says what happened");
        assertEquals(RefreshCoordinator.Outcome.STARTED, refresher.refresh(builder, true), "another builder's publish");
        screen.shown.clear();
        backInTheWorld();
        assertFalse(text().contains("Applied"), "only the first window back says it: " + text());
        click("Apply + preview in world", FakeAnswers.untouched(window()).with("hex", "#102030"));
        assertEquals("#102030", liveSky());
        assertTrue(text().contains("Applied. Wait 5 s before the next preview."), text());
        click("Apply + preview in world");
        assertEquals("Wait 5 s before the next preview.", firstLine(), "an untouched Apply + preview only previews: "
                + text());
    }

    @Test
    void applyAndPreviewSaysWhatHappenedOnceTheWindowIsBack() throws Exception {
        editor.open(builder, CBC);
        click("Water & vegetation");
        click("Grass");
        click("Apply + preview in world", FakeAnswers.untouched(window()).with("hue", 120f));
        assertTrue(refresher.isRefreshing(builder.getUniqueId()), "it previews all the same");
        screen.shown.clear();
        backInTheWorld();
        assertTrue(firstLine().startsWith("Nothing changed: New only shows where the picker starts"),
                "a hue on grey chose no colour, and the window that comes back says so: " + text());
        assertNull(service.value(CBC, TuningProperties.resolve("grass_color").orElseThrow()));
        assertEquals(120f, slider("hue").initial(), "the moved hue is still on screen");
        now.addAndGet(BiomeTuningSettings.DEFAULTS.refreshCooldownMillis() + 1);
        click("Apply + preview in world");
        assertTrue(refresher.isRefreshing(builder.getUniqueId()));
        screen.shown.clear();
        backInTheWorld();
        assertTrue(firstLine().startsWith("Live"), "a click that touched nothing is a plain preview: " + text());
    }

    @Test
    void aSectionAppliesBeforeItPreviews() throws Exception {
        editor.open(builder, CBC);
        click("Sky & fog colours");
        assertTrue(buttons().contains("Preview in world"), "a section of colour buttons has nothing to apply");
        click("Back");
        click("Distances");
        assertFalse(buttons().contains("Preview in world"), "a moved slider would be lost: " + buttons());
        click("Apply + preview in world", FakeAnswers.untouched(window()).with("fog_end", 48f));
        assertEquals("48", asText(liveValue("fog_end")), "the moved slider is applied before the preview");
        assertTrue(refresher.isRefreshing(builder.getUniqueId()));
        screen.shown.clear();
        backInTheWorld();
        assertTrue(text().startsWith("Distances: cbc:111g380-5p"), text());
        assertEquals("Changed fog end.", firstLine());
        assertEquals(48f, slider("fog_end").initial(), "the window shows the applied value");
        click("Apply + preview in world", FakeAnswers.untouched(window()).with("fog_end", 64f));
        assertEquals("Changed fog end. Wait 5 s before the next preview.", firstLine(), "applied, not previewed");
        now.addAndGet(BiomeTuningSettings.DEFAULTS.refreshCooldownMillis() + 1);
        click("Apply + preview in world");
        screen.shown.clear();
        backInTheWorld();
        assertTrue(window().body().isEmpty(),
                "a click that touched nothing is a plain preview, and the window says nothing about it: " + text());
    }

    // a refresh that must wait keeps what the click did, then says why there is no preview
    @Test
    void aSectionSaysWhatItsClickDidWhenThePreviewMustWait() throws Exception {
        editor.open(builder, CBC);
        click("Preview in world"); // the cooldown starts
        screen.shown.clear();
        backInTheWorld();
        click("Distances");
        service.set(CBC, "fog_end", "64"); // someone else set the very value the builder chooses

        click("Apply + preview in world", FakeAnswers.untouched(window()).with("fog_end", 64f));

        assertEquals("Nothing changed. Wait 5 s before the next preview.", firstLine(),
                "what the click did, then why there is no preview");
        assertEquals(NamedTextColor.GREEN, colourOf("Nothing changed."), "what the click did is no problem");
        assertEquals(NamedTextColor.RED, colourOf("Wait"), "why there is no preview stays red");
    }

    @Test
    void thePickerSaysWhatItsClickDidWhenThePreviewMustWait() throws Exception {
        service.set(CBC, "sky_color", "#808080"); // grey: moving the hue changes nothing
        editor.open(builder, CBC);
        click("Preview in world"); // the cooldown starts
        screen.shown.clear();
        backInTheWorld();
        click("Sky & fog colours");
        click("Sky");

        click("Apply + preview in world", FakeAnswers.untouched(window()).with("hue", 120f));

        assertEquals("Nothing changed: that is already the live colour. Wait 5 s before the next preview.",
                firstLine(), "what the click did, then why there is no preview");
    }

    @Test
    void aRefusedRefreshIsExplainedAndNothingComesBackLater() throws Exception {
        refuseReconfiguration = true;
        editor.open(builder, CBC);
        click("Preview in world");
        assertTrue(text().contains("PlayerLoginEvent"), text());
        assertTrue(text().contains("Rejoin to see the live values."), "what a rejoin shows: " + text());
        screen.shown.clear();
        server.getPluginManager().callEvent(new PlayerClientLoadedWorldEvent(builder, false));
        assertNull(screen.of(builder), "no window pops up later");
    }

    @Test
    void aRefreshSomeoneElseStartsBringsTheOpenWindowBackToo() throws Exception {
        editor.open(builder, CBC);
        click("Climate");
        assertEquals(RefreshCoordinator.Outcome.STARTED, refresher.refresh(builder, true), "another builder's publish");
        screen.shown.clear();
        backInTheWorld();
        assertTrue(text().startsWith("Climate: cbc:111g380-5p"), text());
        tune.remove();
        assertEquals(RefreshCoordinator.Outcome.STARTED, refresher.refresh(builder, true));
        screen.shown.clear();
        backInTheWorld();
        assertNull(screen.of(builder), "without the permission, the window stays away");
    }

    @Test
    void aWindowClosedWithEscapeStaysClosed() throws Exception {
        editor.open(builder, CBC);
        escape();
        assertTrue(screen.closed.contains(builder.getUniqueId()));
        refresher.refresh(builder, true);
        backInTheWorld();
        assertNull(screen.of(builder), "nothing pops up after someone else's publish");
    }

    @Test
    void theJsonEditorKeepsTextTheCodecRejects() throws Exception {
        bridge.rejectContaining = "not_a_particle";
        editor.open(builder, CBC);
        click("Audio & particles");
        click("Edit Dripstone particle…");
        String bad = "{\"type\":\"minecraft:not_a_particle\"}";
        click("Apply", FakeAnswers.untouched(window()).with("json", bad));
        assertTrue(text().contains("the codec says no"), text());
        assertEquals(bad, ((EditorView.TextBox) window().inputs().get(0)).initial(), "the builder's text is still there");
        click("Apply", FakeAnswers.untouched(window()).with("json", "{\"type\":\"minecraft:dripping_lava\"}"));
        assertTrue(text().startsWith("Audio & particles"), text());
        assertEquals("minecraft:dripping_lava", live().getAsJsonObject("attributes")
                .getAsJsonObject("minecraft:visual/default_dripstone_particle").get("type").getAsString());
        click("Edit Dripstone particle…");
        click("Clear");
        assertTrue(text().contains("Dripstone particle cleared"), text());
        assertNull(live().getAsJsonObject("attributes").get("minecraft:visual/default_dripstone_particle"));
    }

    @Test
    void copyFromBiomeFillsTheAudioSection() throws Exception {
        bridge.encoded.put(NamespacedKey.minecraft("basalt_deltas"), JsonParser.parseString("{\"attributes\":{"
                + "\"minecraft:audio/background_music\":{\"default\":{\"sound\":\"minecraft:music.nether.basalt_deltas\"}},"
                + "\"minecraft:audio/music_volume\":0.5,"
                + "\"minecraft:visual/ambient_particles\":[{\"particle\":{\"type\":\"minecraft:white_ash\"},"
                + "\"probability\":0.1}]}}").getAsJsonObject());
        editor.open(builder, CBC);
        click("Audio & particles");
        click("Copy from biome…");
        click("Copy", FakeAnswers.untouched(window()).with("from", "minecraft:basalt_deltas").with("particles", false));
        assertTrue(text().contains("Copied music and music volume from minecraft:basalt_deltas"), text());
        JsonObject attributes = live().getAsJsonObject("attributes");
        assertTrue(attributes.has("minecraft:audio/background_music"));
        assertFalse(attributes.has("minecraft:visual/ambient_particles"), "particles were not ticked");
        click("Copy from biome…");
        click("Copy", FakeAnswers.untouched(window()).with("from", "minecraft:basalt_deltas").with("audio", false)
                .with("particles", false));
        assertEquals("Tick music and sounds, particles, or both.", firstLine(), "the copy window's notice comes first");
    }

    @Test
    void noticesNameValuesTheWayTheWindowsDo() throws Exception {
        builder.addAttachment(plugin, "architect.biomeTune.save", true);
        editor.open(builder, CBC);
        click("Distances");
        click("Apply", FakeAnswers.untouched(window()).with("fog_end", 48f).with("water_fog_end", 32f));
        assertTrue(text().contains("Changed fog end and water fog end (live; preview to see it)."), text());
        click("Back");
        service.set(CBC, "sun_angle", "30"); // a value the editor leaves out goes by its id
        click("Save");
        assertTrue(text().contains("Saved sun angle, fog end and water fog end to the datapack."),
                "names in the catalogue's order, not JSON: " + text());
        click("Audio & particles");
        click("Apply", FakeAnswers.untouched(window()).with("firefly_bush_sounds", true));
        assertTrue(text().contains("Changed firefly bush sounds (live; preview to see it)."),
                "a label's note stays out of running text: " + text());
        click("Edit Dripstone particle…");
        assertFalse(buttons().contains("Clear"), "it is not set, so there is nothing to clear");
        click("Apply", FakeAnswers.untouched(window()).with("json", " "));
        assertTrue(text().contains("Nothing changed: Dripstone particle is not set, and the box is empty."), text());
        service.set(CBC, "dripstone_particle", "{\"type\":\"minecraft:dripping_lava\"}");
        click("Back");
        click("Edit Dripstone particle…");
        service.unset(CBC, "dripstone_particle"); // someone else, while the window is open
        click("Apply", FakeAnswers.untouched(window()).with("json", " "));
        assertTrue(text().contains("Nothing changed: Dripstone particle is not set, and the box is empty."),
                "the live value decides, not the one the window was drawn with: " + text());
        assertFalse(buttons().contains("Clear"), "and the window drawn again has no Clear either");
    }

    // home has room for a notice of two lines, so from five values on a notice names four and counts the rest; the
    // log line of a save keeps every change
    @Test
    void aNoticeNamesFourValuesAtMost() throws Exception {
        builder.addAttachment(plugin, "architect.biomeTune.save", true);

        String twelve = saved(edit("sky_color", "#123456"), edit("sky_light_color", "#234567"),
                edit("fog_color", "#345678"), edit("fog_end", "64"), edit("water_color", "#456789"),
                edit("water_fog_color", "#56789a"), edit("grass_color", "#6789ab"), edit("temperature", "0.25"),
                edit("downfall", "0.35"),
                edit("music", "{\"default\":{\"sound\":\"minecraft:music.overworld.forest\"}}"),
                edit("ambient_sounds", "{\"loop\":\"minecraft:ambient.basalt_deltas.loop\"}"),
                edit("ambient_particles", "[{\"particle\":{\"type\":\"minecraft:white_ash\"},\"probability\":0.1}]"));
        String five = saved(edit("sky_color", "#666666"), edit("sky_light_color", "#777777"),
                edit("fog_color", "#888888"), edit("fog_end", "16"), edit("water_color", "#999999"));
        String four = saved(edit("sky_color", "#aaaaaa"), edit("sky_light_color", "#bbbbbb"),
                edit("fog_color", "#cccccc"), edit("fog_end", "8"));

        assertEquals("Saved sky, sky light, fog, fog end and 8 more to the datapack.", twelve,
                "the first four, and how many more");
        assertEquals("Saved sky, sky light, fog, fog end and 1 more to the datapack.", five, "from five on");
        assertEquals("Saved sky, sky light, fog and fog end to the datapack.", four, "four are all named");
        String log = logged.stream().filter(line -> line.contains(" saved ")).findFirst()
                .orElseThrow(() -> new AssertionError("no save in the log: " + logged));
        assertTrue(log.contains("sky_color") && log.contains("ambient_particles"), "the log keeps all twelve: " + log);
    }

    private static BiomeTuningService.Edit edit(String property, String value) {
        return new BiomeTuningService.Edit(property, value);
    }

    /** Changes the values as one step, then saves them from home: what the notice says. */
    private String saved(BiomeTuningService.Edit... edits) throws Exception {
        service.edit(CBC, List.of(edits));
        editor.open(builder, CBC);
        click("Save");
        return firstLine();
    }

    @Test
    void revertAsksFirst() throws Exception {
        editor.open(builder, CBC);
        click("Sky & fog colours");
        click("Sky");
        click("Apply", FakeAnswers.untouched(window()).with("hex", "#000000"));
        click("Back");
        click("Back");
        assertTrue(text().contains("● unsaved"), text());
        click("Revert…");
        assertTrue(text().contains("Undo cannot bring them back"), text());
        click("Revert");
        assertTrue(text().contains("✓ saved"), text());
        assertEquals("#ffaa00", liveSky());
    }

    @Test
    void aRevertThatFailsSaysWhyOnItsWindow() throws Exception {
        editor.open(builder, CBC);
        click("Revert…");
        Files.delete(file); // someone removed the biome's file meanwhile

        click("Revert");

        assertEquals("cbc:111g380-5p is not defined by a datapack in this world", firstLine());
        assertTrue(text().startsWith("Revert: cbc:111g380-5p"), text());
        assertTrue(buttons().contains("Revert"), "the window asks again: " + buttons());
    }

    @Test
    void publishRefreshesEveryoneOnceInAWhile() throws Exception {
        builder.addAttachment(plugin, "architect.biomeTune.publish", true);
        editor.open(builder, CBC);
        click("Publish…");
        click("Everyone in this world");
        assertTrue(text().contains("Refreshing 1 player(s), 5 per second"), text());
        click("Publish…");
        click("Everyone on this server");
        assertTrue(text().contains("A publish ran moments ago"), text());
        now.addAndGet(BiomeTuningSettings.DEFAULTS.publishCooldownMillis() + 1);
        click("Everyone on this server");
        assertTrue(text().contains("Refreshing 1 player(s)"), "the cooldown is over: " + text());
    }

    @Test
    void everyClickChecksItsPermissionAgain() throws Exception {
        PermissionAttachment save = builder.addAttachment(plugin, "architect.biomeTune.save", true);
        editor.open(builder, CBC);
        save.remove();
        click("Save");
        assertTrue(screen.closed.contains(builder.getUniqueId()), "the window closed");
        assertTrue(plain(builder.nextComponentMessage()).contains("permission"));
        assertEquals(SAMPLE, Files.readString(file));
        editor.open(builder, CBC);
        tune.remove();
        escape();
        assertTrue(screen.closed.contains(builder.getUniqueId()));
        assertNull(builder.nextComponentMessage(), "Close and Escape just close, whatever the permissions");
    }

    @Test
    void oldWindowsStopWorkingAfterARestart() throws Exception {
        editor.open(builder, CBC);
        EditorView old = window();
        editor.shutdown();
        click("Undo");
        assertTrue(screen.closed.contains(builder.getUniqueId()));
        String restarted = plain(builder.nextComponentMessage());
        assertTrue(restarted.startsWith("[Architect] ") && restarted.contains("open it again"),
                "the editor speaks in Architect's own chat style: " + restarted);
        old.exit().action().run(builder, FakeAnswers.untouched(old));
        assertNull(builder.nextComponentMessage(), "Escape on an old window just closes it");
    }

    @Test
    void anOutOfDateWindowWritesNothing() throws Exception {
        editor.open(builder, CBC);
        click("Sky & fog colours");
        click("Sky");
        service.set(CBC, "sky_color", "#00ff00"); // someone else, while the picker is open
        click("Show");
        assertTrue(text().contains("New ████ #00ff00") && text().contains("changed while this window was open"),
                "Show draws the window again, and the untouched New follows the live colour: " + text());
        service.set(CBC, "sky_color", "#0000ff"); // and again
        click("Apply + preview in world");
        assertEquals("#0000ff", liveSky(), "the untouched window did not write its old colour back");
        assertFalse(refresher.isRefreshing(builder.getUniqueId()), "nor did it preview");
        assertTrue(text().contains("New ████ #0000ff"), text());
        click("Apply");
        assertEquals("#0000ff", liveSky(), "an untouched Apply changes nothing");
        click("Show", FakeAnswers.untouched(window()).with("hex", "#123456"));
        service.set(CBC, "sky_color", "#654321"); // once more, after the builder chose a colour
        click("Apply");
        assertEquals("#654321", liveSky(), "not written: the window is drawn again first");
        assertTrue(text().contains("New ████ #123456") && text().contains("check it before you click again"), text());
        click("Apply");
        assertEquals("#123456", liveSky(), "the builder saw the change and applied their colour on purpose");
        service.set(CBC, "sky_color", "#abcdef"); // once applied, the builder's colour is no longer a choice to keep
        click("Show");
        assertTrue(text().contains("New ████ #abcdef") && text().contains("New follows it"),
                "an applied colour is followed, not kept to be written back: " + text());
        service.set(CBC, "dripstone_particle", "{\"type\":\"minecraft:dripping_lava\"}");
        click("Back");
        click("Back");
        click("Audio & particles");
        click("Edit Dripstone particle…");
        service.set(CBC, "dripstone_particle", "{\"type\":\"minecraft:dripping_water\"}");
        click("Apply");
        assertTrue(text().contains("it is now {\"type\":\"minecraft:dripping_water\"}, shown below"), text());
        assertEquals("minecraft:dripping_water", live().getAsJsonObject("attributes")
                .getAsJsonObject("minecraft:visual/default_dripstone_particle").get("type").getAsString(),
                "the out-of-date text was not written");
        service.set(CBC, "dripstone_particle", "{\"type\":\"minecraft:dripping_lava\"}"); // and again
        click("Apply", FakeAnswers.untouched(window()).with("json", "  "));
        assertTrue(text().contains("It is empty")
                        && text().contains("it is now {\"type\":\"minecraft:dripping_lava\"}"),
                "the empty-box notice still knows what the builder saw: " + text());
    }

    // a long value is cut with one ellipsis, which ends its sentence, and the advice suits every button, Clear too
    @Test
    void anOutOfDateJsonWindowSaysWhatIsLiveNow() throws Exception {
        editor.open(builder, CBC);
        click("Audio & particles");
        click("Edit Dripstone particle…");
        String longer = "{\"type\":\"minecraft:dripping_lava\",\"note\":\"" + "x".repeat(40) + "\"}";
        service.set(CBC, "dripstone_particle", longer); // someone else, while the window is open

        click("Apply", FakeAnswers.untouched(window()).with("json", "{\"type\":\"minecraft:dripping_water\"}"));

        assertTrue(firstLine().endsWith("x… Check it before you click again."),
                "no full stop after the ellipsis, which shows as four dots in the game: " + firstLine());
        assertFalse(firstLine().contains("..."), "one ellipsis, not three dots: " + firstLine());
    }

    // a value longer than the box takes is changed with /biometune set; the window offers Clear only, and says so
    @Test
    void aValueTooLongForTheBoxCanOnlyBeCleared() throws Exception {
        String pad = "{\"type\":\"minecraft:dripping_lava\",\"pad\":\"" + "x".repeat(BiomeEditor.JSON_LIMIT) + "\"}";
        service.set(CBC, "dripstone_particle", pad);
        editor.open(builder, CBC);
        click("Audio & particles");
        click("Edit Dripstone particle…");

        assertTrue(window().inputs().isEmpty(), "no box");
        assertTrue(text().contains("It is too long to change here"), text());
        assertFalse(text().contains("Vanilla checks it when you apply"), "there is nothing to apply: " + text());
        service.set(CBC, "dripstone_particle", pad.replace("lava", "water")); // someone else, while it is open
        click("Clear");
        assertTrue(liveValue("dripstone_particle").toString().contains("dripping_water"), "drawn again first");
        assertTrue(firstLine().endsWith("x…"), "the cut value ends the sentence, with no full stop: " + firstLine());
        assertTrue(window().inputs().isEmpty() && text().contains("It is too long to change here"),
                "still too long for a box: " + text());
        click("Clear");
        assertNull(liveValue("dripstone_particle"), "now the builder saw it");
    }

    @Test
    void clearOnAnOutOfDateWindowClearsNothing() throws Exception {
        editor.open(builder, CBC);
        click("Sky & fog colours");
        click("Sky");
        service.set(CBC, "sky_color", "#00ff00"); // someone else, while the picker is open
        click("Clear");
        assertEquals("#00ff00", liveSky(), "the newer colour, which the builder never saw, stays");
        assertTrue(firstLine().contains("changed while this window was open"), text());
        click("Clear");
        assertNull(live().getAsJsonObject("attributes").get("minecraft:visual/sky_color"), "now the builder saw it");
        service.set(CBC, "dripstone_particle", "{\"type\":\"minecraft:dripping_lava\"}");
        click("Back");
        click("Back");
        click("Audio & particles");
        click("Edit Dripstone particle…");
        service.set(CBC, "dripstone_particle", "{\"type\":\"minecraft:dripping_water\"}");
        click("Clear");
        assertEquals(JsonParser.parseString("{\"type\":\"minecraft:dripping_water\"}"), liveValue("dripstone_particle"),
                "the JSON editor's Clear waits until the builder has seen the new value too");
        assertTrue(text().contains("it is now {\"type\":\"minecraft:dripping_water\"}, shown below"), text());
        click("Clear");
        assertNull(live().getAsJsonObject("attributes").get("minecraft:visual/default_dripstone_particle"));
    }

    @Test
    void aWindowThatComesBackShowsWhatChangedMeanwhile() throws Exception {
        editor.open(builder, CBC);
        click("Sky & fog colours");
        click("Sky");
        click("Apply + preview in world", FakeAnswers.untouched(window()).with("hex", "#123456"));
        service.set(CBC, "sky_color", "#00ff00"); // someone else, during the refresh
        screen.shown.clear();
        backInTheWorld();
        assertTrue(text().contains("New ████ #00ff00") && text().contains("changed while this window was open"),
                "the builder's colour was applied, so New follows the live one: " + text());
        click("Apply");
        assertEquals("#00ff00", liveSky(), "nothing old was written back");
        service.set(CBC, "sky_color", "#0000ff"); // another builder's change, then their publish
        assertEquals(RefreshCoordinator.Outcome.STARTED, refresher.refresh(builder, true));
        screen.shown.clear();
        backInTheWorld();
        assertTrue(text().contains("New ████ #0000ff") && text().contains("changed while this window was open"),
                text());
        service.unset(CBC, "sky_color"); // and an undo that leaves it unset
        click("Apply");
        assertTrue(text().contains("it is now not set, and New shows where the picker starts"), text());
        click("Apply");
        assertTrue(text().contains("Nothing changed: New only shows where the picker starts"), text());
        assertNull(live().getAsJsonObject("attributes").get("minecraft:visual/sky_color"),
                "the stand-in where the picker starts is never written");
        service.set(CBC, "dripstone_particle", "{\"type\":\"minecraft:dripping_lava\"}");
        click("Back");
        click("Back");
        click("Audio & particles");
        click("Edit Dripstone particle…");
        service.set(CBC, "dripstone_particle", "{\"type\":\"minecraft:dripping_water\"}");
        assertEquals(RefreshCoordinator.Outcome.STARTED, refresher.refresh(builder, true));
        screen.shown.clear();
        backInTheWorld();
        assertTrue(text().contains("it is now {\"type\":\"minecraft:dripping_water\"}, shown below"), text());
        assertTrue(((EditorView.TextBox) window().inputs().get(0)).initial().contains("dripping_water"),
                "the untouched box shows the live value, not the old one");
        click("Apply");
        assertEquals("minecraft:dripping_water", live().getAsJsonObject("attributes")
                .getAsJsonObject("minecraft:visual/default_dripstone_particle").get("type").getAsString());
    }

    @Test
    void aFailureWhileShowingAWindowClosesItAndSaysSo() throws Exception {
        editor.open(builder, CBC);
        click("Sky & fog colours");
        click("Sky");
        EditorView picker = window();
        editor.presentWith(new DialogPresenter() {
            @Override
            public void show(Player player, EditorView view) {
                throw new IllegalStateException("the client went away");
            }

            @Override
            public void close(Player player) {
                screen.close(player);
            }
        });
        click("Apply", FakeAnswers.untouched(picker).with("hex", "#12"));
        assertTrue(screen.closed.contains(builder.getUniqueId()), "no window is left with spent buttons");
        assertTrue(plain(builder.nextComponentMessage()).contains("That did not work (the client went away)"));
        screen.closed.clear();
        editor.open(builder, CBC);
        assertTrue(screen.closed.contains(builder.getUniqueId()), "opening fails the same way");
        assertTrue(plain(builder.nextComponentMessage()).contains("That did not work (the client went away)"));
    }

    @Test
    void aColourCanBeTakenFromAnotherBiomeOrCleared() throws Exception {
        bridge.encoded.put(NamespacedKey.minecraft("plains"), JsonParser.parseString(
                "{\"attributes\":{\"minecraft:visual/sky_color\":\"#78a7ff\"}}").getAsJsonObject());
        editor.open(builder, CBC);
        click("Sky & fog colours");
        click("Fog");
        click("Copy from biome…");
        click("Use its colour", FakeAnswers.untouched(window()).with("from", "minecraft:plains"));
        assertTrue(text().contains("minecraft:plains has no plain colour of its own for fog"), text());
        assertEquals("minecraft:plains", ((EditorView.TextBox) window().inputs().get(0)).initial(), "the id stays");
        click("Back");
        click("Back");
        click("Sky");
        click("Copy from biome…");
        click("Use its colour", FakeAnswers.untouched(window()).with("from", "minecraft:plains"));
        assertTrue(text().contains("New ████ #78a7ff") && text().contains("Loaded from minecraft:plains"), text());
        assertNull(live(), "loading a colour does not apply it");
        click("Apply");
        assertEquals("#78a7ff", liveSky());
        click("Clear");
        assertTrue(text().contains("Cleared: the default colour applies"), text());
        assertNull(live().getAsJsonObject("attributes").get("minecraft:visual/sky_color"));
        click("Apply");
        assertTrue(text().contains("Nothing changed: New only shows where the picker starts"), text());
        assertNull(live().getAsJsonObject("attributes").get("minecraft:visual/sky_color"), "still cleared");
    }

    @Test
    void aTooLongBiomeIdIsCutToFitItsBox() throws Exception {
        String id = "minecraft:" + "x".repeat(150); // only a modified client sends more than the box takes
        editor.open(builder, CBC);
        click("Sky & fog colours");
        click("Sky");
        click("Copy from biome…");
        click("Use its colour", FakeAnswers.untouched(window()).with("from", id));
        assertTrue(text().contains("unknown biome"), "drawn again with the error, not closed: " + text());
        assertEquals(id.substring(0, BiomeEditor.ID_LIMIT), ((EditorView.TextBox) window().inputs().get(0)).initial());
        click("Back");
        click("Back");
        click("Back");
        click("Audio & particles");
        click("Copy from biome…");
        click("Copy", FakeAnswers.untouched(window()).with("from", id));
        assertTrue(text().contains("unknown biome"), text());
        assertEquals(id.substring(0, BiomeEditor.ID_LIMIT), ((EditorView.TextBox) window().inputs().get(0)).initial());
    }

    @Test
    void theCopyWindowCarriesWhatTheBuilderSaw() throws Exception {
        bridge.encoded.put(NamespacedKey.minecraft("plains"), JsonParser.parseString(
                "{\"attributes\":{\"minecraft:visual/sky_color\":\"#78a7ff\"}}").getAsJsonObject());
        editor.open(builder, CBC);
        click("Sky & fog colours");
        click("Sky");
        click("Copy from biome…");
        service.set(CBC, "sky_color", "#00ff00"); // someone else, while the copy window is open
        assertEquals(RefreshCoordinator.Outcome.STARTED, refresher.refresh(builder, true)); // and their publish
        screen.shown.clear();
        backInTheWorld();
        assertTrue(text().startsWith("Copy Sky"), "the copy window came back: " + text());
        click("Use its colour", FakeAnswers.untouched(window()).with("from", "minecraft:nowhere"));
        assertTrue(text().contains("unknown biome"), text());
        click("Back");
        assertTrue(text().contains("New ████ #00ff00") && text().contains("changed while this window was open"),
                "the copy window, its error and its Back kept what the builder saw: " + text());
        click("Copy from biome…");
        service.set(CBC, "sky_color", "#0000ff");
        click("Use its colour", FakeAnswers.untouched(window()).with("from", "minecraft:plains"));
        assertTrue(text().contains("New ████ #78a7ff") && text().contains("check it before you click again"),
                "a copied colour is a choice, kept for a second Apply: " + text());
        click("Apply");
        assertEquals("#78a7ff", liveSky());
    }

    @Test
    void aWindowComesBackOnlyAfterARecentRefresh() throws Exception {
        editor.open(builder, CBC);
        assertEquals(RefreshCoordinator.Outcome.STARTED, refresher.refresh(builder, true));
        now.addAndGet(BiomeEditor.REOPEN_WINDOW_MILLIS + 1);
        screen.shown.clear();
        backInTheWorld();
        assertNull(screen.of(builder), "over two minutes later: the refresh ended in a disconnect");
        editor.open(builder, CBC);
        server.getPluginManager().callEvent(new PlayerQuitEvent(builder, Component.text("left"),
                PlayerQuitEvent.QuitReason.DISCONNECTED));
        screen.shown.clear();
        backInTheWorld();
        assertNull(screen.of(builder), "a real quit forgets the window");
        editor.open(builder, CBC);
        builder.setHealth(0);
        assertEquals(RefreshCoordinator.Outcome.STARTED, refresher.refresh(builder, true));
        screen.shown.clear();
        backInTheWorld();
        assertNull(screen.of(builder), "the death screen took the window's place");
        editor.open(builder, CBC);
        server.getPluginManager().callEvent(new PlayerChangedWorldEvent(builder, builder.getWorld()));
        assertEquals(RefreshCoordinator.Outcome.STARTED, refresher.refresh(builder, true));
        screen.shown.clear();
        backInTheWorld();
        assertNull(screen.of(builder), "a world change's loading screen took the window's place");
    }

    @Test
    void aWindowClosesBeforeItsButtonsStopWorking() throws Exception {
        editor.open(builder, CBC);
        now.addAndGet(BiomeEditor.WINDOW_LIFETIME_MILLIS - 1);
        editor.closeExpired();
        assertFalse(screen.closed.contains(builder.getUniqueId()), "not yet");
        now.addAndGet(1);
        editor.closeExpired();
        assertTrue(screen.closed.contains(builder.getUniqueId()), "after 30 minutes the window closes");
        assertTrue(plain(builder.nextComponentMessage()).contains("open it again"));
        editor.open(builder, CBC);
        now.addAndGet(BiomeEditor.WINDOW_LIFETIME_MILLIS);
        assertEquals(RefreshCoordinator.Outcome.STARTED, refresher.refresh(builder, true));
        screen.shown.clear();
        backInTheWorld();
        assertNull(screen.of(builder), "a window as old as its buttons does not come back after a refresh");
    }
}
