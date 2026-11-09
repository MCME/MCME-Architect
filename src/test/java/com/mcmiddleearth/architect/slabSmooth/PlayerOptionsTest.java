package com.mcmiddleearth.architect.slabSmooth;

import com.mcmiddleearth.architect.slabSmooth.core.Options;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PlayerOptionsTest {

    private final UUID alice = UUID.randomUUID(), bob = UUID.randomUUID();

    @Test
    void eachPlayerGetsACopyOfTheDefaults() {
        Options defaults = new Options();
        defaults.band = 3;
        PlayerOptions options = new PlayerOptions(defaults);

        Options first = options.of(alice);
        assertEquals(3, first.band);
        assertNotSame(defaults, first);
        assertNotSame(first, options.of(alice), "a new copy every time");

        options.set(alice, "band", "1");
        assertEquals(1, options.of(alice).band);
        assertEquals(3, options.of(bob).band, "another player keeps the defaults");
        assertEquals(3, defaults.band, "the defaults they were built from are never touched");

        defaults.band = 4;
        assertEquals(3, options.of(bob).band, "and later changes to those don't reach them either");
    }

    @Test
    void aChangeDoesNotReachAnOptionsAlreadyHandedOut() {
        PlayerOptions options = new PlayerOptions(new Options());
        Options handedOut = options.of(alice);

        options.set(alice, "band", "4");
        assertEquals(2, handedOut.band);
        handedOut.band = 0;
        assertEquals(4, options.of(alice).band, "and editing a copy changes nothing here");

        assertThrows(IllegalArgumentException.class, () -> options.set(alice, "band", "99"));
        assertEquals(4, options.of(alice).band, "a bad value changes nothing");
    }

    @Test
    void resetAlsoForgetsTheNamedPack() {
        PlayerOptions options = new PlayerOptions(new Options());
        assertNull(options.namedPack(alice), "automatic until a pack is named");

        options.set(alice, "band", "4");
        options.namePack(alice, "Human");
        options.namePack(bob, "Elf");
        assertEquals("Human", options.namedPack(alice));

        options.reset(alice);
        assertEquals(2, options.of(alice).band);
        assertNull(options.namedPack(alice));
        assertEquals("Elf", options.namedPack(bob), "only this player");

        options.namePack(bob, null);
        assertNull(options.namedPack(bob), "null goes back to automatic");
    }
}
