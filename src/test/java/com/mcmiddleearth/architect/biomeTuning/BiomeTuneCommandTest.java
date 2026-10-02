package com.mcmiddleearth.architect.biomeTuning;

import com.google.gson.JsonParser;
import com.mcmiddleearth.architect.ArchitectPlugin;
import io.papermc.paper.connection.PlayerGameConnection;
import io.papermc.paper.event.player.PlayerClientLoadedWorldEvent;
import java.io.IOException;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.stream.Stream;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.NamespacedKey;
import org.bukkit.command.Command;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.command.ConsoleCommandSenderMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import static org.junit.jupiter.api.Assertions.*;

// One mock/load per class: Architect caches data-folder paths in static fields; surefire forks per class.
class BiomeTuneCommandTest {

    private static final NamespacedKey CBC = NamespacedKey.fromString("cbc:111g380-5p");
    private static final String SAMPLE = "{\"attributes\":{\"minecraft:visual/sky_color\":\"#ffaa00\"},\"downfall\":0.8,"
            + "\"effects\":{\"water_color\":\"#263a22\"},\"temperature\":0.7}";

    private static ServerMock server;
    private static ArchitectPlugin plugin;

    @TempDir
    static Path dir;
    private FakeNmsBridge fake;
    private Path file;
    private ConsoleCommandSenderMock console;

    @BeforeAll
    static void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.load(ArchitectPlugin.class);
    }

    @AfterAll
    static void tearDown() {
        if (MockBukkit.isMocked()) {
            MockBukkit.unmock();
        }
    }

    // every test starts from a fresh datapacks folder: spares and labels one test writes must not reach the next
    @BeforeEach
    void freshBiome() throws Exception {
        deleteTree(dir.resolve("datapacks"));
        file = dir.resolve("datapacks/bukkit/data/cbc/worldgen/biome/111g380-5p.json");
        Files.createDirectories(file.getParent());
        Files.writeString(file, SAMPLE);
        fake = new FakeNmsBridge();
        console = server.getConsoleSender();
        while (console.nextMessage() != null) {
            // start every test with an empty console
        }
    }

    @AfterEach
    void backToTheRealBridge() {
        BiomeTuning.disable();
        BiomeTuning.enable(plugin);
    }

    private void useFake() {
        BiomeTuning.disable();
        BiomeTuning.enable(plugin, fake, dir.resolve("datapacks"));
    }

    /** The fake, on a server that loaded {@code count} spares at startup: their files are in the pack. */
    private void useFakeWithSpares(int count) throws Exception {
        List<NamespacedKey> known = new ArrayList<>(List.of(CBC));
        for (int number = 1; number <= count; number++) {
            NamespacedKey spare = SpareIds.DEFAULT.key(number);
            Path spareFile = dir.resolve("datapacks/bukkit/data/mcme/worldgen/biome/" + spare.getKey() + ".json");
            Files.createDirectories(spareFile.getParent());
            Files.writeString(spareFile, SparePool.NEUTRAL);
            known.add(spare);
        }
        BiomeTuning.disable();
        BiomeTuning.enable(plugin, fake, dir.resolve("datapacks"), known::stream);
    }

    /** What the labels file says about a biome, read afresh. */
    private static BiomeLabels.Entry labelled(NamespacedKey biome) throws Exception {
        return new BiomeLabels(() -> dir.resolve("datapacks"), "bukkit").entry(biome).orElseThrow();
    }

    private static String plain(Component component) {
        return PlainTextComponentSerializer.plainText().serialize(component);
    }

    /** Every line the console has not read yet, as plain text. */
    private List<String> consoleLines() {
        List<String> lines = new ArrayList<>();
        for (Component line = console.nextComponentMessage(); line != null; line = console.nextComponentMessage()) {
            lines.add(plain(line));
        }
        return lines;
    }

    private static void deleteTree(Path root) throws IOException {
        if (!Files.exists(root)) {
            return;
        }
        try (Stream<Path> paths = Files.walk(root)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.delete(path);
            }
        }
    }

    @Test
    void withoutMinecraftInternalsTuningSwitchesOffAndArchitectStillRuns() {
        assertTrue(plugin.isEnabled());
        assertFalse(BiomeTuning.isAvailable());
        server.dispatchCommand(console, "biometune status");
        String reply = console.nextMessage();
        assertNotNull(reply);
        assertTrue(reply.contains("unavailable"), reply);
    }

    @Test
    void aFailureWhileStartingSwitchesTuningOffAndArchitectKeepsRunning() {
        fake.selfCheckFailure = new NoSuchMethodError("a future server renamed something");
        BiomeTuning.disable();
        assertDoesNotThrow(() -> BiomeTuning.enable(plugin, fake, dir.resolve("datapacks")),
                "it must not reach Architect's onEnable, which would disable all of Architect");
        assertFalse(BiomeTuning.isAvailable());
        assertTrue(BiomeTuning.problems().toString().contains("a future server renamed something"),
                BiomeTuning.problems().toString());
        assertNull(BiomeTuning.bridge(), "the bridge it had set before the failure is cleared again");
        server.dispatchCommand(console, "biometune status");
        String reply = console.nextMessage();
        assertTrue(reply != null && reply.contains("unavailable")
                && reply.contains("a future server renamed something"), "the command says why: " + reply);
    }

    @Test
    void aBridgeThatFailsToBuildSwitchesTuningOffAndSaysWhy() {
        BiomeTuning.disable();
        assertDoesNotThrow(() -> BiomeTuning.enable(plugin, () -> {
            throw new ClassCastException("DIRECT_CODEC is not a Codec");
        }), "enable(plugin) builds the real bridge the same way, inside the guard");
        assertFalse(BiomeTuning.isAvailable());
        assertEquals(List.of("it could not start: ClassCastException: DIRECT_CODEC is not a Codec"),
                BiomeTuning.problems());
        BiomeTuning.disable();
        BiomeTuning.enable(plugin, () -> {
            throw new ExceptionInInitializerError(new IllegalStateException("no registry access yet"));
        });
        assertEquals(List.of("it could not start: IllegalStateException: no registry access yet"),
                BiomeTuning.problems(), "the reason is the root cause, not its wrapper");
    }

    @Test
    void aLoopingChainOfCausesStillGivesAReason() {
        RuntimeException first = new RuntimeException("first");
        RuntimeException second = new RuntimeException("second", first);
        first.initCause(second);
        String reason = assertTimeoutPreemptively(Duration.ofSeconds(5), () -> BiomeTuning.reason(second),
                "the guard's catch runs in onEnable, where a hang is worse than tuning switched off");
        assertTrue(reason.startsWith("RuntimeException: "), reason);
    }

    // the admin reads the log, so a spare pattern tuning cannot use is said there once, and the default applies
    @Test
    void aSparePatternItCannotUseIsLoggedAtStart() {
        List<LogRecord> logged = new ArrayList<>();
        Handler handler = new Handler() {
            @Override
            public void publish(LogRecord record) {
                logged.add(record);
            }

            @Override
            public void flush() {
            }

            @Override
            public void close() {
            }
        };
        Object configured = plugin.getConfig().get("biomeTuning.sparePattern");
        plugin.getLogger().addHandler(handler);
        plugin.getConfig().set("biomeTuning.sparePattern", "mcme:custom_.*");
        try {
            useFake();
            assertEquals(SpareIds.DEFAULT, BiomeTuning.settings().spares());
            long said = logged.stream().filter(record -> record.getLevel() == Level.WARNING
                    && record.getMessage().contains("mcme:custom_.*")).count();
            assertEquals(1, said, "the log says once which pattern it ignored");
        } finally {
            plugin.getConfig().set("biomeTuning.sparePattern", configured);
            plugin.getLogger().removeHandler(handler);
        }
    }

    @Test
    void playersWithoutPermissionAreRefusedFirst() {
        PlayerMock guest = server.addPlayer("Guest");
        guest.setOp(false);
        while (guest.nextMessage() != null) {
            // ignore join messages
        }
        guest.performCommand("biometune selftest");
        String reply = guest.nextMessage();
        assertNotNull(reply);
        assertTrue(reply.toLowerCase().contains("permission"), reply);
    }

    @Test
    void setChangesTheLiveBiomeButNotTheFile() throws Exception {
        useFake();
        server.dispatchCommand(console, "biometune set cbc:111g380-5p sky_color #2040FF");
        assertEquals("#2040ff", fake.applied.get(CBC).getAsJsonObject("attributes").get("minecraft:visual/sky_color").getAsString());
        String reply = console.nextMessage();
        assertNotNull(reply);
        assertTrue(reply.contains("sky_color") && reply.contains("#2040ff"), reply);
        assertEquals(SAMPLE, Files.readString(file));
    }

    @Test
    void savingNeedsItsOwnPermission() throws Exception {
        useFake();
        server.dispatchCommand(console, "biometune set cbc:111g380-5p sky_color #2040ff");
        PlayerMock builder = server.addPlayer("Builder");
        builder.setOp(false);
        builder.addAttachment(plugin, "architect.biomeTune", true);
        while (builder.nextMessage() != null) {
            // ignore join messages
        }
        builder.performCommand("biometune save cbc:111g380-5p");
        String refusal = builder.nextMessage();
        assertNotNull(refusal);
        assertTrue(refusal.toLowerCase().contains("permission"), refusal);
        assertEquals(SAMPLE, Files.readString(file), "nothing was written");
        builder.addAttachment(plugin, "architect.biomeTune.save", true);
        builder.performCommand("biometune save cbc:111g380-5p");
        assertTrue(Files.readString(file).contains("#2040ff"));
    }

    @Test
    void theDatapacksFolderComesFromTheServer() throws Exception {
        // Architect loads at STARTUP, and a 26.x World path is its dimension folder, not the level root:
        // the folder must come from the server itself (the bridge), asked when first needed.
        fake.datapacks = dir.resolve("datapacks");
        BiomeTuning.disable();
        BiomeTuning.enable(plugin, fake);
        server.dispatchCommand(console, "biometune set cbc:111g380-5p sky_color #2040ff");
        assertNotNull(fake.applied.get(CBC), "the biome file was found through the bridge");
    }

    @Test
    void statusListsUnsavedBiomes() {
        useFake();
        server.dispatchCommand(console, "biometune status");
        assertTrue(console.nextMessage().contains("No unsaved"));
        server.dispatchCommand(console, "biometune set cbc:111g380-5p fog_color #123456");
        assertNotNull(console.nextMessage()); // the change line
        server.dispatchCommand(console, "biometune status");
        assertTrue(console.nextMessage().contains("Unsaved"));
        assertTrue(console.nextMessage().contains("cbc:111g380-5p"));
    }

    // a biome is shown with its label wherever it is listed; a labels file that cannot be read hides the labels only
    @Test
    void statusShowsLabelsNextToTheIds() throws Exception {
        useFake();
        server.dispatchCommand(console, "biometune label cbc:111g380-5p Harbour at dusk");
        server.dispatchCommand(console, "biometune set cbc:111g380-5p fog_color #123456");
        consoleLines(); // their replies

        server.dispatchCommand(console, "biometune status");

        List<String> labelled = consoleLines();
        assertTrue(labelled.get(1).startsWith("  cbc:111g380-5p Harbour at dusk [Save]"), labelled.toString());
        Files.writeString(dir.resolve("datapacks/bukkit/" + BiomeLabels.FILE), "{");
        server.dispatchCommand(console, "biometune status");
        List<String> unreadable = consoleLines();
        assertTrue(unreadable.get(1).startsWith("  cbc:111g380-5p [Save]"), "the list, without labels: " + unreadable);
    }

    @Test
    void otherListenersCanSeeARefreshInProgress() {
        useFake();
        PlayerMock alice = server.addPlayer("Alice");
        List<Boolean> seenDuringQuit = new ArrayList<>();
        BiomeTuning.refresher().refresh(alice, true, player -> {
            seenDuringQuit.add(BiomeTuning.isRefreshing(player.getUniqueId()));
            server.getPluginManager().callEvent(new PlayerQuitEvent(player, Component.text("left"),
                    PlayerQuitEvent.QuitReason.DISCONNECTED)); // RpListener runs here and must skip its cleanup
            return null;
        });
        assertEquals(List.of(true), seenDuringQuit);
        assertTrue(BiomeTuning.isRefreshing(alice.getUniqueId()));
        server.getPluginManager().callEvent(new PlayerJoinEvent(alice, Component.text("joined")));
        assertFalse(BiomeTuning.isRefreshing(alice.getUniqueId()));
    }

    // Paper refuses to refresh a player who logged in while a plugin listened to PlayerLoginEvent: chat says what a
    // rejoin shows, in the editor's words
    @Test
    void aRefusedPreviewSaysARejoinShowsTheLiveValues() {
        useFake();
        PlayerGameConnection unmoved = (PlayerGameConnection) Proxy.newProxyInstance(
                PlayerGameConnection.class.getClassLoader(), new Class<?>[] {PlayerGameConnection.class},
                (proxy, method, arguments) -> null); // takes the request to reconfigure, and nothing happens
        PlayerMock refused = new PlayerMock(server, "Refused") {
            @Override
            public PlayerGameConnection getConnection() {
                return unmoved;
            }
        };
        server.addPlayer(refused);
        refused.addAttachment(plugin, "architect.biomeTune", true);
        while (refused.nextMessage() != null) {
            // ignore join messages
        }

        refused.performCommand("biometune preview");

        List<String> replies = new ArrayList<>();
        for (String reply = refused.nextMessage(); reply != null; reply = refused.nextMessage()) {
            replies.add(reply);
        }
        assertFalse(BiomeTuning.isRefreshing(refused.getUniqueId()), "Paper refused it");
        assertTrue(!replies.isEmpty() && replies.get(replies.size() - 1).contains("Rejoin to see the live values."),
                "as the editor says it: " + replies);
    }

    @Test
    void claimTakesTheNextSpareAndSaysHowToPaintWithIt() throws Exception {
        useFakeWithSpares(2);
        PlayerMock builder = server.addPlayer("Claimer");
        builder.addAttachment(plugin, "architect.biomeTune", true);
        while (builder.nextMessage() != null) {
            // ignore join messages
        }

        builder.performCommand("biometune claim Harbour at dusk");

        String reply = builder.nextMessage();
        assertTrue(reply != null && reply.contains("mcme:custom_001") && reply.contains("Harbour at dusk")
                && reply.contains("//setbiome mcme:custom_001"), reply);
        BiomeLabels.Entry entry = labelled(SpareIds.DEFAULT.key(1));
        assertEquals("Harbour at dusk", entry.label());
        assertEquals(builder.getUniqueId(), entry.claimedBy());
    }

    // the last two words name the source when the one before them is "from"; everything before is the label
    @Test
    void claimFromABiomeTakesItsLook() throws Exception {
        useFakeWithSpares(1);
        fake.encoded.put(CBC, JsonParser.parseString(SAMPLE).getAsJsonObject());

        server.dispatchCommand(console, "biometune claim Ash fields from cbc:111g380-5p");

        NamespacedKey spare = SpareIds.DEFAULT.key(1);
        assertEquals("#ffaa00", fake.applied.get(spare).getAsJsonObject("attributes")
                .get("minecraft:visual/sky_color").getAsString());
        BiomeLabels.Entry entry = labelled(spare);
        assertEquals("Ash fields", entry.label());
        assertEquals(CBC, entry.copiedFrom());
        assertNull(entry.claimedBy(), "the console is no player");
    }

    // a new spare already has the look of plains, so nothing is copied: the "from" still gets an answer
    @Test
    void aClaimFromALookTheSpareAlreadyHasSaysSo() throws Exception {
        useFakeWithSpares(1);
        NamespacedKey plains = NamespacedKey.minecraft("plains");
        fake.encoded.put(plains, JsonParser.parseString(SparePool.NEUTRAL).getAsJsonObject());
        fake.vanilla.add(plains);

        server.dispatchCommand(console, "biometune claim Quay from minecraft:plains");

        List<Component> reply = new ArrayList<>();
        for (Component line = console.nextComponentMessage(); line != null; line = console.nextComponentMessage()) {
            reply.add(line);
        }
        assertEquals(List.of("[Biome] Claimed mcme:custom_001 as 'Quay'. Paint with //setbiome mcme:custom_001 "
                        + "[Editor] ", "  It already has the look of minecraft:plains."),
                reply.stream().map(BiomeTuneCommandTest::plain).toList());
        assertEquals(NamedTextColor.GRAY, reply.get(1).color());
    }

    @Test
    void aSourceTheGameDoesNotKnowClaimsNothing() throws Exception {
        useFakeWithSpares(1);

        server.dispatchCommand(console, "biometune claim Far from home");

        String reply = console.nextMessage();
        assertTrue(reply != null && reply.contains("minecraft:home"), reply);
        assertTrue(new BiomeLabels(() -> dir.resolve("datapacks"), "bukkit").all().isEmpty());
    }

    // Architect's log says who claimed, labelled or added what, by the sender's name as for a save; a refusal is not
    // logged
    @Test
    void claimsLabelsAndNewSparesAreLogged() throws Exception {
        useFakeWithSpares(2);
        fake.encoded.put(CBC, JsonParser.parseString(SAMPLE).getAsJsonObject());
        Files.writeString(dir.resolve("datapacks/bukkit/pack.mcmeta"),
                "{\"pack\":{\"pack_format\":94,\"description\":\"test\"}}");
        PlayerMock planner = server.addPlayer("Planner");
        planner.addAttachment(plugin, "architect.biomeTune", true);
        List<String> logged = new ArrayList<>();
        Handler handler = new Handler() {
            @Override
            public void publish(LogRecord record) {
                String message = record.getMessage();
                if (message.contains("biometune: ")) {
                    logged.add(message.substring(message.indexOf("biometune: ")));
                }
            }

            @Override
            public void flush() {
            }

            @Override
            public void close() {
            }
        };
        plugin.getLogger().addHandler(handler);
        try {
            planner.performCommand("biometune claim Harbour at dusk from cbc:111g380-5p");
            server.dispatchCommand(console, "biometune claim Quay");
            server.dispatchCommand(console, "biometune claim Pier"); // refused: no spare is free
            server.dispatchCommand(console, "biometune label cbc:111g380-5p Harbour");
            server.dispatchCommand(console, "biometune label cbc:111g380-5p " + "x".repeat(40)); // refused: too long
            server.dispatchCommand(console, "biometune spares add 2");
            server.dispatchCommand(console, "biometune spares add 1");
        } finally {
            plugin.getLogger().removeHandler(handler);
        }

        assertEquals(List.of(
                "biometune: Planner claimed mcme:custom_001 as 'Harbour at dusk' (look of cbc:111g380-5p)",
                "biometune: " + console.getName() + " claimed mcme:custom_002 as 'Quay'",
                "biometune: " + console.getName() + " labelled cbc:111g380-5p 'Harbour'",
                "biometune: " + console.getName() + " added spares mcme:custom_003 to mcme:custom_004 to the pack "
                        + "bukkit",
                "biometune: " + console.getName() + " added spare mcme:custom_005 to the pack bukkit"), logged);
    }

    // the claimed spares are listed by id, not by label
    @Test
    void sparesSaysWhatIsClaimedAndWhatIsFree() throws Exception {
        useFakeWithSpares(3);
        server.dispatchCommand(console, "biometune claim Quay");
        server.dispatchCommand(console, "biometune claim Harbour");
        consoleLines(); // the claims' own replies

        server.dispatchCommand(console, "biometune spares");

        List<String> reply = consoleLines();
        assertEquals(3, reply.size(), "the summary, and a line for each claimed spare: " + reply);
        assertTrue(reply.get(0).contains("2 claimed") && reply.get(0).contains("1 free")
                && reply.get(0).contains("the next claim takes mcme:custom_003"), reply.get(0));
        assertEquals(List.of("  mcme:custom_001 Quay [Editor] ", "  mcme:custom_002 Harbour [Editor] "),
                reply.subList(1, 3), "in the order of their ids");
    }

    // the listing and a claim agree on which spare comes next; and painting with a spare nobody claimed is not safe
    @Test
    void sparesNamesTheSpareTheNextClaimTakes() throws Exception {
        useFakeWithSpares(2);
        Files.delete(dir.resolve("datapacks/bukkit/data/mcme/worldgen/biome/custom_001.json")); // removed by hand

        server.dispatchCommand(console, "biometune spares");

        String summary = console.nextMessage();
        assertTrue(summary != null && summary.contains("1 free")
                && summary.contains("the next claim takes mcme:custom_002"), "custom_001 is gone at the next restart: "
                + summary);
        assertTrue(summary.contains("Paint only with a spare you have claimed"), summary);
    }

    @Test
    void sparesWithoutAnySaysHowToAddThem() {
        useFake();

        server.dispatchCommand(console, "biometune spares");

        String reply = console.nextMessage();
        assertTrue(reply != null && reply.contains("/biometune spares add"), reply);
    }

    @Test
    void addingSparesIsForAdmins() throws Exception {
        useFake();
        Files.writeString(dir.resolve("datapacks/bukkit/pack.mcmeta"),
                "{\"pack\":{\"pack_format\":94,\"description\":\"test\"}}");
        PlayerMock builder = server.addPlayer("Adder");
        builder.setOp(false);
        builder.addAttachment(plugin, "architect.biomeTune", true);
        while (builder.nextMessage() != null) {
            // ignore join messages
        }
        Path first = dir.resolve("datapacks/bukkit/data/mcme/worldgen/biome/custom_001.json");

        builder.performCommand("biometune spares add 2");

        String refusal = builder.nextMessage();
        assertTrue(refusal != null && refusal.toLowerCase().contains("permission"), refusal);
        assertFalse(Files.exists(first), "nothing was written");
        builder.addAttachment(plugin, "architect.biomeTune.admin", true);
        builder.performCommand("biometune spares add 2");
        String reply = builder.nextMessage();
        assertTrue(reply != null && reply.contains("mcme:custom_002") && reply.contains("restart"), reply);
        assertTrue(Files.exists(first));
    }

    // the pool's lock is wired in: a free spare stays neutral until someone claims it
    @Test
    void aFreeSpareCannotBeTunedOrLabelledBeforeItIsClaimed() throws Exception {
        useFakeWithSpares(1);
        NamespacedKey spare = SpareIds.DEFAULT.key(1);

        server.dispatchCommand(console, "biometune set mcme:custom_001 sky_color #2040FF");
        String set = console.nextMessage();
        assertTrue(set != null && set.contains("free spare") && set.contains("/biometune claim"), set);
        assertNull(fake.applied.get(spare), "nothing went live");

        server.dispatchCommand(console, "biometune label mcme:custom_001 Mine");
        String label = console.nextMessage();
        assertTrue(label != null && label.contains("free spare"), label);
        assertTrue(new BiomeLabels(() -> dir.resolve("datapacks"), "bukkit").entry(spare).isEmpty(), "no label");
    }

    // a claim is for good, so a slip in the command claims nothing: no label, or a from without its biome
    @Test
    void aSlipInAClaimClaimsNothing() throws Exception {
        useFakeWithSpares(1);

        for (String command : List.of("biometune claim from cbc:111g380-5p", "biometune claim Harbour from")) {
            server.dispatchCommand(console, command);
            String reply = console.nextMessage();
            assertTrue(reply != null && reply.contains("/biometune claim"), "it says how: " + command + " -> " + reply);
        }
        server.dispatchCommand(console, "biometune claim Far from");
        String far = console.nextMessage();
        assertTrue(far != null && far.contains("A label can't end in 'from'."),
                "a label that ends in from reads as a claim without its biome: " + far);
        assertTrue(new BiomeLabels(() -> dir.resolve("datapacks"), "bukkit").all().isEmpty(), "nothing claimed");
    }

    @Test
    void labelNamesABiome() throws Exception {
        useFake();

        server.dispatchCommand(console, "biometune label cbc:111g380-5p Harbour at dusk");

        String reply = console.nextMessage();
        assertTrue(reply != null && reply.contains("Harbour at dusk"), reply);
        assertEquals("Harbour at dusk", labelled(CBC).label());
        server.dispatchCommand(console, "biometune info cbc:111g380-5p");
        console.nextComponentMessage(); // the saved state
        assertEquals("  label: Harbour at dusk", plain(console.nextComponentMessage()), "info shows it");
    }

    // a labels file someone broke by hand hides labels, not the biome's values
    @Test
    void infoWorksWhenTheLabelsFileIsBrokenAndSaysWhy() throws Exception {
        useFake();
        Files.writeString(dir.resolve("datapacks/bukkit/" + BiomeLabels.FILE), "{");

        server.dispatchCommand(console, "biometune info cbc:111g380-5p");

        List<String> lines = consoleLines();
        assertTrue(lines.stream().anyMatch(line -> line.contains("sky_color")), "the values: " + lines);
        assertTrue(lines.get(lines.size() - 1).contains(BiomeLabels.FILE),
                "why there is no label, last, below the line that points to it: " + lines);
        assertTrue(lines.contains("  label: (can't be read, see below)"), "the label line says so: " + lines);
        assertTrue(lines.stream().noneMatch(line -> line.contains("(not set)")),
                "a file that cannot be read is not a biome without a label: " + lines);
    }

    // a free spare takes no label until it is claimed, and a labels file that cannot be read shows none: neither line
    // offers a click, as a label command could only fail
    @Test
    void infoSaysWhyABiomeHasNoLabelToChange() throws Exception {
        useFakeWithSpares(1);
        PlayerMock reader = server.addPlayer("Reader");
        reader.addAttachment(plugin, "architect.biomeTune", true);
        while (reader.nextMessage() != null) {
            // ignore join messages
        }

        reader.performCommand("biometune info mcme:custom_001");
        reader.nextComponentMessage(); // the saved state
        Component free = reader.nextComponentMessage();
        while (reader.nextComponentMessage() != null) {
            // the rest of it
        }
        Files.writeString(dir.resolve("datapacks/bukkit/" + BiomeLabels.FILE), "{");
        reader.performCommand("biometune info cbc:111g380-5p");
        reader.nextComponentMessage(); // the saved state
        Component unreadable = reader.nextComponentMessage();

        assertEquals("  label: (a free spare: claiming one names it)", plain(free));
        assertNull(free.clickEvent(), "nothing to click: a free spare is named when it is claimed");
        assertEquals("  label: (can't be read, see below)", plain(unreadable));
        assertNull(unreadable.clickEvent(), "nothing to click while the file cannot be read");
    }

    @Test
    void theNewSubcommandsComplete() {
        useFake();
        Command biometune = server.getCommandMap().getCommand("biometune");

        assertEquals(List.of("spares"), biometune.tabComplete(console, "biometune", new String[] {"sp"}));
        assertEquals(List.of("add"), biometune.tabComplete(console, "biometune", new String[] {"spares", ""}));
        assertEquals(List.of("from"), biometune.tabComplete(console, "biometune", new String[] {"claim", "Ash", "f"}));
        assertEquals(List.of(), biometune.tabComplete(console, "biometune", new String[] {"claim", ""}),
                "the label is the builder's own words");
        assertEquals(List.of(), biometune.tabComplete(console, "biometune", new String[] {"claim", "from", ""}),
                "the label comes first");
    }

    // copy takes the groups of a biome's look, and all
    @Test
    void copyCompletesItsGroups() {
        useFake();
        List<String> offered = server.getCommandMap().getCommand("biometune").tabComplete(console, "biometune",
                new String[] {"copy", "cbc:111g380-5p", "cbc:111g380-5p", ""});
        assertEquals(List.of("sky", "fog", "water", "climate", "audio", "particles", "all"), offered);
    }

    @Test
    void theFirstWordCompletesToHereToo() {
        useFake();
        List<String> offered = server.getCommandMap().getCommand("biometune").tabComplete(console, "biometune",
                new String[] {"h"});
        assertEquals(List.of("here"), offered, "/biometune here opens the editor on the biome at your feet");
    }

    @Test
    void editOpensTheEditorInTheGame() {
        useFake();
        RecordingPresenter screen = new RecordingPresenter();
        BiomeTuning.editor().presentWith(screen);
        PlayerMock builder = server.addPlayer("Editor");
        builder.addAttachment(plugin, "architect.biomeTune", true);
        builder.performCommand("biometune edit cbc:111g380-5p");
        assertNotNull(screen.of(builder), "the editor opened");
        screen.shown.clear();
        builder.performCommand("biometune cbc:111g380-5p");
        assertNotNull(screen.of(builder), "a bare biome id opens it too");
        server.dispatchCommand(console, "biometune edit cbc:111g380-5p");
        String reply = console.nextMessage();
        assertNotNull(reply);
        assertTrue(reply.contains("opens in the game"), reply);
        server.dispatchCommand(console, "biometune");
        String usage = console.nextMessage();
        assertTrue(usage != null && usage.contains("Usage"), "the console gets the usage: " + usage);
        while (builder.nextMessage() != null) {
            // start from an empty chat
        }
        builder.performCommand("biometune");
        builder.performCommand("biometune here");
        for (String form : List.of("/biometune", "/biometune here")) {
            String answer = builder.nextMessage();
            assertTrue(answer != null && answer.contains("minecraft:plains"),
                    form + " opens the biome at your feet: " + answer);
        }
        PlayerMock visitor = server.addPlayer("Visitor");
        visitor.setOp(false);
        visitor.performCommand("biometune");
        visitor.performCommand("biometune cbc:111g380-5p");
        assertNull(screen.of(visitor), "without the permission, the short forms open nothing");
        BiomeTuning.refresher().refresh(builder, true, player -> {
            server.getPluginManager().callEvent(new PlayerQuitEvent(player, Component.text("left"),
                    PlayerQuitEvent.QuitReason.DISCONNECTED));
            return null;
        });
        screen.shown.clear();
        server.getPluginManager().callEvent(new PlayerJoinEvent(builder, Component.text("joined")));
        server.getPluginManager().callEvent(new PlayerClientLoadedWorldEvent(builder, false));
        EditorView back = screen.of(builder);
        assertNotNull(back, "the plugin registered the editor's listeners: the window came back after the refresh");
        BiomeTuning.disable();
        assertNull(BiomeTuning.editor(), "no editor while tuning is off");
        while (builder.nextMessage() != null) {
            // start from an empty chat
        }
        back.buttons().get(0).action().run(builder, FakeAnswers.untouched(back));
        String restarted = builder.nextMessage();
        assertTrue(restarted != null && restarted.contains("open it again"), "an old window's button: " + restarted);
    }
}
