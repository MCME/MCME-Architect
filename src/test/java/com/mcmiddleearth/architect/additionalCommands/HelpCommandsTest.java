package com.mcmiddleearth.architect.additionalCommands;

import com.mcmiddleearth.architect.ArchitectPlugin;
import org.bukkit.command.PluginCommand;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;

// A click on a line of /architect help runs the command's getHelpCommand() as it is, or, when that is null, puts
// /<name> in the chat box. So a help command must be a command that runs: one of the command's own names, alone or
// with "help". /rot once ran /rotate, which is not Architect's, and /viewdistance and /biometune ran their usage text,
// brackets and all. One mock/load per class, as in FbtCommandTest.
class HelpCommandsTest {

    private static ArchitectPlugin plugin;

    @BeforeAll
    static void setUp() {
        MockBukkit.mock();
        plugin = MockBukkit.load(ArchitectPlugin.class);
    }

    @AfterAll
    static void tearDown() {
        if (MockBukkit.isMocked()) {
            MockBukkit.unmock();
        }
    }

    @Test
    void everyHelpClickRunsTheCommandItself() {
        List<String> wrong = new ArrayList<>();
        for (String name : plugin.getDescription().getCommands().keySet()) {
            PluginCommand command = plugin.getCommand(name);
            if (command == null || !(command.getExecutor() instanceof AbstractArchitectCommand architect)) {
                continue;
            }
            String click = architect.getHelpCommand();
            if (click == null) {
                continue;
            }
            List<String> names = new ArrayList<>(command.getAliases());
            names.add(command.getName());
            String lower = click.toLowerCase(Locale.ROOT);
            if (names.stream().map(own -> "/" + own.toLowerCase(Locale.ROOT))
                    .noneMatch(own -> lower.equals(own) || lower.equals(own + " help"))) {
                wrong.add("/" + name + " -> " + click);
            }
        }
        assertEquals(List.of(), wrong, "these help clicks run something other than the command itself:");
    }
}
