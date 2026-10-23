package com.mcmiddleearth.architect;

import com.mcmiddleearth.architect.serverResoucePack.RegionEditConversation.RegionEditPrompt;
import com.mcmiddleearth.architect.serverResoucePack.RpManager;
import com.mcmiddleearth.architect.serverResoucePack.RpRegion;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.regions.CuboidRegion;
import com.sk89q.worldedit.world.NullWorld;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.conversations.ConversationContext;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

// A name a player gives to a saved inventory, an item block region or an RP region becomes a file directly in that
// feature's folder: it may not name a folder, and a region file already on disk is never overwritten. The tests have
// no WorldEdit, so a name that passes these checks stops at the WorldEdit step. One mock/load per class.
class NewFileNameTest {

    private static ServerMock server;
    private static ArchitectPlugin plugin;
    private static PlayerMock admin;
    private static final List<String> tellraw = new ArrayList<>();

    @BeforeAll
    static void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.load(ArchitectPlugin.class);
        server.getCommandMap().register("minecraft", new Command("tellraw") {
            @Override
            public boolean execute(CommandSender sender, String label, String[] args) {
                tellraw.add(String.join(" ", args));
                return true;
            }
        });
        server.addSimpleWorld("world");
        server.addSimpleWorld(NullWorld.getInstance().getName()); // the test regions name this world
        admin = server.addPlayer();
        admin.setOp(true);
    }

    @AfterAll
    static void tearDown() {
        if (MockBukkit.isMocked()) {
            MockBukkit.unmock();
        }
    }

    private static List<String> answersTo(String command) {
        while (admin.nextMessage() != null) {
            // what came before this command
        }
        tellraw.clear();
        admin.performCommand(command);
        List<String> answers = new ArrayList<>();
        for (String message = admin.nextMessage(); message != null; message = admin.nextMessage()) {
            answers.add(message);
        }
        answers.addAll(tellraw);
        return answers;
    }

    private static String firstAnswerTo(String command) {
        List<String> answers = answersTo(command);
        assertFalse(answers.isEmpty(), "an answer to " + command);
        return answers.get(0);
    }

    // Saves and adds the RP region regionName, then renames it in the region editor; returns the editor's answer.
    private static String rename(String regionName, String newName) {
        RpRegion region = new RpRegion(regionName, new CuboidRegion(NullWorld.getInstance(),
                BlockVector3.at(0, 0, 0), BlockVector3.at(1, 1, 1)));
        RpManager.saveRpRegion(region);
        RpManager.addRegion(region);
        ConversationContext context = new ConversationContext(plugin, admin,
                new HashMap<>(Map.of("region", region, "player", admin)));
        return new RegionEditPrompt().acceptInput(context, "name " + newName).getPromptText(context);
    }

    @Test
    void anInventoryNameMayNotNameAFolder() {
        for (String name : new String[] {"../../../escaped", "a/b", "..\\x"}) {
            String answer = firstAnswerTo("inv create rp:Human " + name);

            assertTrue(answer.contains("Invalid name."), name + ": " + answer);
        }
        assertFalse(new File(plugin.getDataFolder(), "escaped.yml").exists(), "nothing written outside");
    }

    @Test
    void anInventoryDeleteMayNotNameAFolder() throws IOException {
        File kept = new File(plugin.getDataFolder(), "kept.yml");
        Files.writeString(kept.toPath(), "sentinel");

        String answer = firstAnswerTo("inv delete rp:Human ../../../kept");

        assertTrue(answer.contains("Invalid name."), answer);
        assertTrue(kept.exists(), "the file outside is still there");
    }

    // A guard: a normal name is saved as before.
    @Test
    void aNormalInventoryNameIsSaved() {
        String answer = firstAnswerTo("inv create rp:Human tools");

        assertTrue(answer.contains("Inventory saved."), answer);
        assertTrue(new File(plugin.getDataFolder(), "inventories/saved/Human/tools.yml").exists());
    }

    @ParameterizedTest
    @ValueSource(strings = {"itemblock", "rp"})
    void aRegionNameMayNotNameAFolder(String command) {
        String answer = firstAnswerTo(command + " create ../x");

        assertTrue(answer.contains("Invalid name."), answer);
    }

    @ParameterizedTest
    @CsvSource({"itemblock, itemBlockRegions, An itemblock region with that name already exists.",
                "rp, regions, A rp region with that name already exists."})
    void aRegionFileOnDiskIsNotOverwritten(String command, String folder, String refusal) throws IOException {
        File file = new File(plugin.getDataFolder(), folder + "/Pier.reg");
        file.getParentFile().mkdirs();
        Files.writeString(file.toPath(), "sentinel");

        String answer = firstAnswerTo(command + " create Pier");

        assertTrue(answer.contains(refusal), answer);
        assertEquals("sentinel", Files.readString(file.toPath()), "the file on disk is kept");
    }

    // A guard: a normal region name passes these checks and reaches the WorldEdit step.
    @ParameterizedTest
    @ValueSource(strings = {"itemblock", "rp"})
    void aNormalRegionNameReachesTheWorldEditStep(String command) {
        String answer = firstAnswerTo(command + " create Fresh");

        assertTrue(answer.contains("needs WorldEdit"), answer);
    }

    @Test
    void theRegionEditorKeepsTheRegionWhenTheNewNameIsRefused() {
        String answer = rename("Quay", "../x");

        assertTrue(answer.contains("Invalid name."), answer);
        assertTrue(new File(plugin.getDataFolder(), "regions/Quay.reg").exists(), "the region keeps its file");
        assertFalse(new File(plugin.getDataFolder(), "x.reg").exists(), "nothing written outside");
    }

    @Test
    void theRegionEditorKeepsAnExistingFile() throws IOException {
        File taken = new File(plugin.getDataFolder(), "regions/Taken.reg");
        taken.getParentFile().mkdirs();
        Files.writeString(taken.toPath(), "sentinel");

        String answer = rename("Quay2", "Taken");

        assertTrue(answer.contains("A region with that name already exists."), answer);
        assertEquals("sentinel", Files.readString(taken.toPath()), "the file on disk is kept");
        assertTrue(new File(plugin.getDataFolder(), "regions/Quay2.reg").exists(), "the region keeps its file");
    }
}
