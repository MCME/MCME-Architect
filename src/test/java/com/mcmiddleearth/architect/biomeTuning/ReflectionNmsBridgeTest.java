package com.mcmiddleearth.architect.biomeTuning;

import org.bukkit.NamespacedKey;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** The test JVM has the Paper API but no server internals: the bridge must report that, not throw. */
class ReflectionNmsBridgeTest {

    @Test
    void reportsMissingInternalsInsteadOfThrowing() {
        ReflectionNmsBridge bridge = new ReflectionNmsBridge();
        assertFalse(bridge.selfCheck().isEmpty());
        assertTrue(bridge.selfCheck().contains("missing class net.minecraft.server.MinecraftServer"),
                () -> "problems: " + bridge.selfCheck());
    }

    @Test
    void operationsExplainThatTuningIsUnavailable() {
        ReflectionNmsBridge bridge = new ReflectionNmsBridge();
        BiomeTuningException error = assertThrows(BiomeTuningException.class,
                () -> bridge.encode(NamespacedKey.fromString("cbc:111g380-5p")));
        assertTrue(error.getMessage().contains("unavailable"), error.getMessage());
    }

    @Test
    void theLoginReplayIsCheckedApartFromTheRestOfTuning() {
        ReflectionNmsBridge bridge = new ReflectionNmsBridge();
        assertTrue(bridge.loginReplayCheck()
                        .contains("missing class io.papermc.paper.connection.HorriblePlayerLoginEventHack"),
                () -> "replay problems: " + bridge.loginReplayCheck());
        assertTrue(bridge.selfCheck().stream().noneMatch(problem -> problem.contains("HorriblePlayerLoginEventHack")),
                () -> "a missing replay switches only the replay off, not biome tuning: " + bridge.selfCheck());
    }

    @Test
    void theLoginReplayExplainsThatItIsUnavailable() {
        ReflectionNmsBridge bridge = new ReflectionNmsBridge();
        BiomeTuningException error = assertThrows(BiomeTuningException.class,
                () -> bridge.loggedInWithLegacyEvent(null));
        assertTrue(error.getMessage().contains("unavailable"), error.getMessage());
    }
}
