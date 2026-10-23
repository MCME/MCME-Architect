package com.mcmiddleearth.architect.additionalCommands;

import com.mcmiddleearth.architect.ArchitectPlugin;
import com.mcmiddleearth.architect.armorStand.ArmorStandEditorConfig;
import com.mcmiddleearth.architect.bannerEditor.BannerEditorConfig;
import com.mcmiddleearth.architect.customHeadManager.CustomHeadManagerData;
import com.mcmiddleearth.architect.voxelStencilEditor.VoxelConstants;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

// The folder a player names for a file list, or for /get head, must stay inside the folder that command lists.
// Sub-folders, a leading slash and a lone page number keep working. One mock/load per class.
class FileListFolderTest {

    private static ServerMock server;
    private static ArchitectPlugin plugin;
    private static PlayerMock admin;
    private static final List<String> tellraw = new ArrayList<>();

    @BeforeAll
    static void setUp() throws IOException {
        server = MockBukkit.mock();
        plugin = MockBukkit.load(ArchitectPlugin.class);
        // PluginUtils sends file lists as /tellraw from the console. MockBukkit has none.
        server.getCommandMap().register("minecraft", new Command("tellraw") {
            @Override
            public boolean execute(CommandSender sender, String label, String[] args) {
                tellraw.add(String.join(" ", args));
                return true;
            }
        });
        server.addSimpleWorld("world");
        admin = server.addPlayer();
        admin.setOp(true);

        File plugins = plugin.getDataFolder().getParentFile();
        new File(plugins, "WorldEdit/schematics").mkdirs();
        VoxelConstants.STENCIL_LISTS_DIR.mkdirs();
        VoxelConstants.STENCILS_DIR.mkdirs();
        BannerEditorConfig.getDataDir().mkdirs();
        File trees = new File(ArmorStandEditorConfig.getDataDir(), "trees");
        trees.mkdirs();
        Files.writeString(new File(trees, "oak.yml").toPath(), "description: oak\n");
        new File(CustomHeadManagerData.getAcceptedHeadDir(), "nature").mkdirs();
        // Next to the collection, outside it. Never put a .yml in it: the head walk would then throw.
        new File(CustomHeadManagerData.getAcceptedHeadDir().getParentFile(), "elsewhere").mkdirs();
        File outside = new File(plugins, "outside");
        outside.mkdirs();
        Files.writeString(new File(outside, "sentinel.yml").toPath(), "description: sentinel\n");
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
        answers.addAll(tellraw); // the clickable lines, as JSON
        return answers;
    }

    @ParameterizedTest
    @ValueSource(strings = {"schlist ..", "vv list ..", "vv stencil ..", "armor files ..", "banner files ..",
            "chead list ..", "chead reviewList .."})
    void aListFolderMustStayInsideItsBase(String command) {
        List<String> answers = answersTo(command);

        assertFalse(answers.isEmpty(), "an answer");
        assertTrue(answers.get(0).contains("Invalid folder name. Pick a folder from the list."), answers.toString());
        assertTrue(tellraw.isEmpty(), "nothing listed: " + tellraw);
    }

    @Test
    void aDeeperOrBackslashedFolderIsRefusedToo() {
        for (String command : new String[] {"armor files a/../../x", "armor files ..\\x"}) {
            List<String> answers = answersTo(command);

            assertFalse(answers.isEmpty(), "an answer to " + command);
            assertTrue(answers.get(0).contains("Invalid folder name."), command + ": " + answers);
        }
    }

    // A guard: java.io joins an absolute name under the base, so nothing outside is ever listed.
    @Test
    void anAbsoluteFolderListsNothingOutside() {
        String outside = new File(plugin.getDataFolder().getParentFile(), "outside").getAbsolutePath();

        List<String> answers = answersTo("armor files " + outside);

        assertTrue(answers.stream().noneMatch(answer -> answer.contains("sentinel")), answers.toString());
    }

    // A guard: sub-folders, a leading slash and a lone page number list as before.
    @Test
    void subFoldersALeadingSlashAndAPageStillList() {
        assertTrue(answersTo("armor files trees").stream().anyMatch(answer -> answer.contains("oak")));
        assertTrue(answersTo("armor files /trees").stream().anyMatch(answer -> answer.contains("oak")));
        answersTo("chead list 1");
        assertFalse(tellraw.isEmpty(), "page 1 is listed");
    }

    // Whether the folder outside exists or not, the answer is the same.
    @Test
    void aHeadFolderOutsideTheCollectionIsRefusedWhetherOrNotItExists() {
        for (String command : new String[] {"get head ../elsewhere", "get head ../missing"}) {
            List<String> answers = answersTo(command);

            assertFalse(answers.isEmpty(), "an answer to " + command);
            assertTrue(answers.get(0).contains("Invalid head or folder name."), command + ": " + answers);
        }
    }

    // A guard: a folder of the collection, and "/" for the whole collection, still give their heads.
    @Test
    void headFoldersInsideTheCollectionStillWork() {
        for (String command : new String[] {"get head nature", "get head /"}) {
            List<String> answers = answersTo(command);

            assertFalse(answers.isEmpty(), "an answer to " + command);
            assertTrue(answers.get(0).contains("Given all heads from"), command + ": " + answers);
        }
    }
}
