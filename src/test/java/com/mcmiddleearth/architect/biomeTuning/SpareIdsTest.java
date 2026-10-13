package com.mcmiddleearth.architect.biomeTuning;

import org.bukkit.NamespacedKey;
import org.junit.jupiter.api.Test;

import java.util.Locale;

import static org.junit.jupiter.api.Assertions.*;

class SpareIdsTest {

    private static final SpareIds IDS = SpareIds.DEFAULT;

    @Test
    void theDefaultIsMcmeCustomWithThreeDigits() {
        assertEquals(new SpareIds("mcme", "custom_", 3), IDS);
        assertEquals("mcme:custom_###", IDS.pattern());
        assertEquals(IDS, SpareIds.parse("mcme:custom_###").orElseThrow());
    }

    @Test
    void matchesItsOwnIdsOnly() {
        assertTrue(IDS.matches(NamespacedKey.fromString("mcme:custom_001")));
        assertTrue(IDS.matches(NamespacedKey.fromString("mcme:custom_999")));
        assertFalse(IDS.matches(NamespacedKey.fromString("mcme:custom_000")), "numbering starts at 1");
        assertFalse(IDS.matches(NamespacedKey.fromString("mcme:custom_01")), "too few digits");
        assertFalse(IDS.matches(NamespacedKey.fromString("mcme:custom_0001")), "too many digits");
        assertFalse(IDS.matches(NamespacedKey.fromString("mcme:custom_00a")));
        assertFalse(IDS.matches(NamespacedKey.fromString("other:custom_001")));
        assertFalse(IDS.matches(NamespacedKey.fromString("mcme:forest_001")));
    }

    // the ends, and numbers without a leading zero, so an off-by-one cannot hide behind one
    @Test
    void numbersIdsBothWays() {
        assertEquals(NamespacedKey.fromString("mcme:custom_007"), IDS.key(7));
        assertEquals(NamespacedKey.fromString("mcme:custom_999"), IDS.key(999));
        assertEquals(42, IDS.number(NamespacedKey.fromString("mcme:custom_042")));
        assertEquals(142, IDS.number(NamespacedKey.fromString("mcme:custom_142")));
        assertEquals(999, IDS.number(NamespacedKey.fromString("mcme:custom_999")));
        assertThrows(IllegalArgumentException.class, () -> IDS.key(0), "numbering starts at 1");
        assertThrows(IllegalArgumentException.class, () -> IDS.key(1000), "four digits do not fit in three");
    }

    // String.format writes a locale's own digits, which no biome id may hold
    @Test
    void idsAreWrittenWithPlainDigitsInAnyLocale() {
        Locale before = Locale.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("ar-EG"));
            assertEquals(NamespacedKey.fromString("mcme:custom_007"), IDS.key(7));
        } finally {
            Locale.setDefault(before);
        }
    }

    // spares add numbers new spares, so a pattern must be a namespace, a prefix and one # per digit
    @Test
    void readsOnlyPatternsItCanNumber() {
        assertEquals(new SpareIds("mymod", "sky/", 2), SpareIds.parse("mymod:sky/##").orElseThrow());
        assertEquals(new SpareIds("mcme", "", 4), SpareIds.parse("mcme:####").orElseThrow());
        assertTrue(SpareIds.parse("mcme:custom_.*").isEmpty());
        assertTrue(SpareIds.parse("custom_###").isEmpty(), "no namespace");
        assertTrue(SpareIds.parse("MCME:custom_###").isEmpty(), "ids are lower case");
        assertTrue(SpareIds.parse("mcme:Custom_###").isEmpty(), "the prefix too");
        assertTrue(SpareIds.parse("mcme:custom\\###").isEmpty(), "no backslash, which Windows reads as a folder");
        assertTrue(SpareIds.parse("mcme:custom_").isEmpty(), "no digits");
        assertTrue(SpareIds.parse("mcme:custom_" + "#".repeat(10)).isEmpty(), "at most nine digits");
        assertTrue(SpareIds.parse("mcme:custom_#a#").isEmpty(), "the digits come last");
        assertTrue(SpareIds.parse("mcme:/###").isEmpty(), "a path from the root");
        assertTrue(SpareIds.parse("mcme:../../x_###").isEmpty(), "a path out of the pack");
        assertTrue(SpareIds.parse("mcme:./###").isEmpty(), "a path the game reads as another id");
        assertTrue(SpareIds.parse("mcme:a//b###").isEmpty(), "an empty part");
        assertTrue(SpareIds.parse("..:custom_###").isEmpty(), "a namespace of dots alone");
        assertTrue(SpareIds.parse(null).isEmpty());
    }

    // spares add writes each id as a file in the pack, so no id may lead anywhere else
    @Test
    void aSpareIdStaysInsideThePack() {
        assertThrows(IllegalArgumentException.class, () -> new SpareIds("..", "custom_", 3));
        assertThrows(IllegalArgumentException.class, () -> new SpareIds("mcme", "../custom_", 3));
        assertThrows(IllegalArgumentException.class, () -> new SpareIds("mcme", "/custom_", 3));
        assertThrows(IllegalArgumentException.class, () -> new SpareIds("MCME", "custom_", 3), "ids are lower case");
        assertEquals(NamespacedKey.fromString("mcme:biomes/custom_007"),
                new SpareIds("mcme", "biomes/custom_", 3).key(7), "a prefix may name a folder inside the pack");
    }

    @Test
    void aSpareIdHasOneToNineDigits() {
        assertThrows(IllegalArgumentException.class, () -> new SpareIds("mcme", "custom_", 0));
        assertThrows(IllegalArgumentException.class, () -> new SpareIds("mcme", "custom_", 10),
                "ten digits would not fit in an int");
        assertThrows(NullPointerException.class, () -> new SpareIds(null, "custom_", 3));
    }
}
