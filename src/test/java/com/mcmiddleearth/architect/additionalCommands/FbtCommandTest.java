package com.mcmiddleearth.architect.additionalCommands;

import com.mcmiddleearth.architect.ArchitectPlugin;
import org.bukkit.command.PluginCommand;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

// /fbt switches night vision on and off for those with architect.fullBrightness. The effect has no end, rather than
// a very long one, and the command also answers to /fullbright and /nightvision. One mock/load per class, as in
// LogFileTest.
class FbtCommandTest {

    private static ServerMock server;
    private static ArchitectPlugin plugin;

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

    @Test
    void fbtHasItsAliases() {
        PluginCommand fbt = plugin.getCommand("fbt");
        assertNotNull(fbt, "/fbt is registered");
        assertEquals(List.of("fullbright", "nightvision"), fbt.getAliases(), "the aliases of /fbt");
    }

    @ParameterizedTest(name = "/{0}")
    @ValueSource(strings = {"fbt", "fullbright", "nightvision"})
    void nightVisionWithoutEndGoesOnAndOff(String command) {
        PlayerMock builder = server.addPlayer();
        builder.setOp(false);
        builder.addAttachment(plugin, "architect.fullBrightness", true);

        builder.performCommand(command);

        PotionEffect effect = builder.getPotionEffect(PotionEffectType.NIGHT_VISION);
        assertNotNull(effect, "/" + command + " gives night vision");
        assertTrue(effect.isInfinite(), "/" + command + " gives night vision without end, not for "
                + effect.getDuration() + " ticks");
        assertEquals(PotionEffect.INFINITE_DURATION, effect.getDuration(), "the duration of the night vision");

        builder.performCommand(command);

        assertFalse(builder.hasPotionEffect(PotionEffectType.NIGHT_VISION), "/" + command + " again takes it away");
    }
}
