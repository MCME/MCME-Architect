package com.mcmiddleearth.architect.serverResoucePack;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

// rp-release-times.yml: how long each pack's last release took. No Bukkit server is needed.
class RpReleaseTimesTest {

    @Test
    void aRecordedTimeIsReadBack(@TempDir Path folder) throws Exception {
        RpReleaseTimes.record(folder.toFile(), "Human", Duration.ofSeconds(312));
        RpReleaseTimes.record(folder.toFile(), "Rohan", Duration.ofSeconds(9));
        assertEquals(Duration.ofSeconds(312), RpReleaseTimes.last(folder.toFile(), "Human"));
        assertEquals(Duration.ofSeconds(9), RpReleaseTimes.last(folder.toFile(), "Rohan"));
        assertNull(RpReleaseTimes.last(folder.toFile(), "Mordor"));
    }

    @Test
    void withoutTheFileThereIsNoTime(@TempDir Path folder) {
        assertNull(RpReleaseTimes.last(folder.toFile(), "Human"));
    }

    // Review focus 4: a broken file costs the start line its sentence, and the next record replaces it
    @Test
    void aBrokenFileGivesNoTimeAndIsReplaced(@TempDir Path folder) throws Exception {
        Files.writeString(folder.resolve(RpReleaseTimes.FILE), "Human: [unclosed");
        assertNull(RpReleaseTimes.last(folder.toFile(), "Human"));
        RpReleaseTimes.record(folder.toFile(), "Human", Duration.ofSeconds(60));
        assertEquals(Duration.ofSeconds(60), RpReleaseTimes.last(folder.toFile(), "Human"));
    }
}
