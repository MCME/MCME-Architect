package com.mcmiddleearth.architect.serverResoucePack;

import org.bukkit.event.player.PlayerResourcePackStatusEvent;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

// Pure enum mapping, so no Bukkit server is needed.
//
// RpPlayerStatus exists because Bukkit's PlayerResourcePackStatusEvent.Status only reports what
// the *client* answered. It cannot express the two server-side states Architect needs:
// NOT_SENT (no pack has been offered yet) and SENT (offered, awaiting a client reply).
// Downstream plugins depend on those - MCME-Introduction's intro rooms use SENT together with
// getLastRpStatus() to tell "pack already loaded, just re-sent" from "pack still loading".
class RpPlayerStatusTest {

    @Test
    void everyBukkitStatusMapsToTheSameNamedConstant() {
        for (PlayerResourcePackStatusEvent.Status bukkit : PlayerResourcePackStatusEvent.Status.values()) {
            RpPlayerStatus mapped = RpPlayerStatus.forPlayerResourcePackStatusEvent(bukkit);
            assertNotNull(mapped, "no mapping for Bukkit status " + bukkit);
            assertEquals(bukkit.name(), mapped.name(),
                    "Bukkit status " + bukkit + " must map to the identically named RpPlayerStatus");
        }
    }

    @Test
    void carriesTheTwoServerSideStatesBukkitCannotExpress() {
        Set<String> bukkitNames = Arrays.stream(PlayerResourcePackStatusEvent.Status.values())
                .map(Enum::name).collect(Collectors.toSet());
        Set<String> extra = EnumSet.allOf(RpPlayerStatus.class).stream()
                .map(Enum::name).filter(n -> !bukkitNames.contains(n)).collect(Collectors.toSet());
        assertEquals(Set.of("NOT_SENT", "SENT"), extra,
                "RpPlayerStatus must be Bukkit's set plus exactly NOT_SENT and SENT");
    }

    @Test
    void isASupersetOfBukkitsStatuses() {
        Set<String> ours = EnumSet.allOf(RpPlayerStatus.class).stream()
                .map(Enum::name).collect(Collectors.toSet());
        for (PlayerResourcePackStatusEvent.Status bukkit : PlayerResourcePackStatusEvent.Status.values()) {
            assertTrue(ours.contains(bukkit.name()),
                    "RpPlayerStatus is missing Bukkit's " + bukkit + "; the mapping switch would not compile");
        }
    }
}
