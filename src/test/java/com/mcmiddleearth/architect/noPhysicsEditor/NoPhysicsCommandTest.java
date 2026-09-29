package com.mcmiddleearth.architect.noPhysicsEditor;

import com.mcmiddleearth.architect.ArchitectPlugin;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

// /nophy read its second word as a world name before it checked the first as a subcommand, so a typo such as
// "/nophy expection redstone Mill" answered that "redstone" is no world. One mock/load per class, as in LogFileTest.
class NoPhysicsCommandTest {

    private static ServerMock server;
    private static PlayerMock admin;
    private static final List<String> tellraw = new ArrayList<>();

    @BeforeAll
    static void setUp() {
        server = MockBukkit.mock();
        MockBukkit.load(ArchitectPlugin.class);
        // PluginUtils sends clickable lines, such as the help, as /tellraw from the console. MockBukkit has none.
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

    @Test
    void aMistypedSubcommandIsCalledThatNotAWrongWorld() {
        List<String> answers = answersTo("nophy expection redstone Mill");

        assertFalse(answers.isEmpty(), "an answer");
        assertTrue(answers.get(0).contains("Invalid subcommand."), answers.toString());
        assertTrue(answers.stream().noneMatch(answer -> answer.contains("valid world name")), answers.toString());
    }

    // The check for a known subcommand must not catch one: any case, and the world is still checked after it.
    @Test
    void aKnownSubcommandStillWorks() {
        List<String> listed = answersTo("nophy LIST world");
        assertTrue(listed.stream().anyMatch(answer -> answer.contains("physics list for")), listed.toString());
        assertTrue(listed.stream().noneMatch(answer -> answer.contains("Invalid subcommand.")), listed.toString());

        List<String> noWorld = answersTo("nophy list Mill");
        assertTrue(noWorld.stream().anyMatch(answer -> answer.contains("valid world name")), noWorld.toString());
    }

    @Test
    void theHelpNamesTheExceptionCommandsThatExist() {
        String help = String.join("\n", answersTo("nophy help"));

        assertTrue(help.contains("exception redstone") && help.contains("exception water"), help);
        assertFalse(help.contains("exception set"), help);
    }

    // PluginUtils reads "#" as the start of a colour, so "[#page]" broke that help line; two lines had a double space.
    @Test
    void theHelpLinesAreWellFormed() {
        String help = String.join("\n", answersTo("nophy help"));

        assertTrue(help.contains("/noPhy exception delete <name>"), help);
        assertTrue(help.contains("/noPhy exception list [page]"), help);
        assertFalse(help.contains("#page"), help);
    }
}
