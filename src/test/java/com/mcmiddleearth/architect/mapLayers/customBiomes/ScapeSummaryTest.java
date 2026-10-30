package com.mcmiddleearth.architect.mapLayers.customBiomes;

import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

// A biome's music, ambient sounds and particles are structured JSON that the game's codec checks. The window shows each
// as one short line a builder can read, and anything it does not recognise as "custom" rather than as raw JSON.
class ScapeSummaryTest {

    private static JsonElement json(String text) {
        return JsonParser.parseString(text);
    }

    // --- music ---

    @Test
    void musicShowsItsTrackAndDelayInSeconds() {
        assertEquals("minecraft:music.overworld.ocean, every 300 to 600 s", ScapeSummary.music(json(
                "{\"default\":{\"sound\":\"minecraft:music.overworld.ocean\",\"min_delay\":6000,"
                        + "\"max_delay\":12000}}")));
    }

    @Test
    void anInlineSoundShowsItsId() {
        assertEquals("mcme:lorien, every 60 to 120 s", ScapeSummary.music(json(
                "{\"default\":{\"sound\":{\"sound_id\":\"mcme:lorien\"},\"min_delay\":1200,\"max_delay\":2400}}")));
    }

    @Test
    void musicWithOnlyAnUnderwaterEntryNamesIt() {
        assertEquals("underwater: minecraft:music.under_water, every 600 to 1200 s", ScapeSummary.music(json(
                "{\"underwater\":{\"sound\":\"minecraft:music.under_water\",\"min_delay\":12000,"
                        + "\"max_delay\":24000}}")));
    }

    @Test
    void musicWithOnlyACreativeEntryNamesIt() {
        assertEquals("creative: minecraft:music.creative, every 10 to 20 s", ScapeSummary.music(json(
                "{\"creative\":{\"sound\":\"minecraft:music.creative\",\"min_delay\":200,\"max_delay\":400}}")));
    }

    @Test
    void theDefaultEntryIsTheOneShownWhateverElseIsThere() {
        assertEquals("minecraft:music.a, every 1 to 2 s", ScapeSummary.music(json(
                "{\"underwater\":{\"sound\":\"minecraft:music.u\",\"min_delay\":20,\"max_delay\":40},"
                        + "\"creative\":{\"sound\":\"minecraft:music.c\",\"min_delay\":20,\"max_delay\":40},"
                        + "\"default\":{\"sound\":\"minecraft:music.a\",\"min_delay\":20,\"max_delay\":40}}")));
    }

    @Test
    void withoutADefaultUnderwaterIsShownBeforeCreative() {
        assertEquals("underwater: minecraft:music.u, every 1 to 2 s", ScapeSummary.music(json(
                "{\"creative\":{\"sound\":\"minecraft:music.c\",\"min_delay\":20,\"max_delay\":40},"
                        + "\"underwater\":{\"sound\":\"minecraft:music.u\",\"min_delay\":20,\"max_delay\":40}}")));
    }

    @Test
    void delaysAreRoundedToWholeSeconds() {
        // 29 ticks are 1.45 s, 30 are 1.5 s and 6010 are 300.5 s
        assertEquals("minecraft:m, every 1 to 2 s", ScapeSummary.music(json(
                "{\"default\":{\"sound\":\"minecraft:m\",\"min_delay\":29,\"max_delay\":30}}")));
        assertEquals("minecraft:m, every 301 to 0 s", ScapeSummary.music(json(
                "{\"default\":{\"sound\":\"minecraft:m\",\"min_delay\":6010,\"max_delay\":9}}")));
    }

    @Test
    void delaysWrittenWithADecimalPointAreRoundedToo() {
        assertEquals("minecraft:m, every 300 to 600 s", ScapeSummary.music(json(
                "{\"default\":{\"sound\":\"minecraft:m\",\"min_delay\":6000.0,\"max_delay\":12000.4}}")));
    }

    @Test
    void musicWithAPartMissingOrUnreadableIsCustom() {
        for (String entry : new String[] {
                "{\"min_delay\":6000,\"max_delay\":12000}",
                "{\"sound\":\"minecraft:m\",\"max_delay\":12000}",
                "{\"sound\":\"minecraft:m\",\"min_delay\":6000}",
                "{\"sound\":7,\"min_delay\":6000,\"max_delay\":12000}",
                "{\"sound\":{\"range\":16},\"min_delay\":6000,\"max_delay\":12000}",
                "{\"sound\":\"minecraft:m\",\"min_delay\":\"soon\",\"max_delay\":12000}",
                "{\"sound\":\"minecraft:m\",\"min_delay\":6000,\"max_delay\":[1]}",
                "\"minecraft:m\"", "[]", "null"}) {
            assertEquals("custom", ScapeSummary.music(json("{\"default\":" + entry + "}")), entry);
        }
    }

    @Test
    void musicWithoutAnyEntryItKnowsIsCustom() {
        assertEquals("custom", ScapeSummary.music(json("{}")));
        assertEquals("custom", ScapeSummary.music(json(
                "{\"night\":{\"sound\":\"minecraft:m\",\"min_delay\":6000,\"max_delay\":12000}}")));
    }

    // --- ambient sounds ---

    @Test
    void ambientSoundsListLoopMoodAndExtra() {
        assertEquals("loop minecraft:ambient.cave, mood minecraft:ambient.cave, extra mcme:gulls",
                ScapeSummary.ambientSounds(json("{\"loop\":\"minecraft:ambient.cave\","
                        + "\"mood\":{\"sound\":\"minecraft:ambient.cave\",\"tick_delay\":6000,"
                        + "\"block_search_extent\":8,\"offset\":2.0},"
                        + "\"additions\":{\"sound\":{\"sound_id\":\"mcme:gulls\"},\"tick_chance\":0.0111}}")));
    }

    @Test
    void ambientSoundsShowOnlyWhatIsSet() {
        assertEquals("loop mcme:wind", ScapeSummary.ambientSounds(json("{\"loop\":{\"sound_id\":\"mcme:wind\"}}")));
        assertEquals("mood minecraft:ambient.cave", ScapeSummary.ambientSounds(json(
                "{\"mood\":{\"sound\":\"minecraft:ambient.cave\",\"tick_delay\":6000}}")));
        assertEquals("extra mcme:gulls", ScapeSummary.ambientSounds(json(
                "{\"additions\":{\"sound\":\"mcme:gulls\",\"tick_chance\":0.01}}")));
        assertEquals("loop mcme:wind, extra mcme:gulls", ScapeSummary.ambientSounds(json(
                "{\"additions\":{\"sound\":\"mcme:gulls\",\"tick_chance\":0.01},\"loop\":\"mcme:wind\"}")));
    }

    @Test
    void ambientSoundsAreAlwaysListedLoopMoodExtraWhateverTheirOrderInTheFile() {
        assertEquals("loop a:b, mood c:d, extra e:f", ScapeSummary.ambientSounds(json(
                "{\"additions\":{\"sound\":\"e:f\"},\"mood\":{\"sound\":\"c:d\"},\"loop\":\"a:b\"}")));
    }

    @Test
    void emptyAmbientSoundsAreNone() {
        assertEquals("none", ScapeSummary.ambientSounds(json("{}")));
    }

    @Test
    void ambientSoundsWithAPartUnreadableOrNoPartKnownAreCustom() {
        for (String sounds : new String[] {
                "{\"loop\":5}", "{\"loop\":[\"a:b\"]}", "{\"loop\":{\"range\":2}}",
                "{\"mood\":\"a:b\"}", "{\"mood\":{\"tick_delay\":6000}}", "{\"mood\":{\"sound\":3}}",
                "{\"additions\":[{\"sound\":\"a:b\"}]}", "{\"additions\":{\"tick_chance\":0.1}}",
                "{\"loop\":\"a:b\",\"mood\":null}", "{\"cave\":\"a:b\"}",
                "[]", "\"a:b\"", "3", "true"}) {
            assertEquals("custom", ScapeSummary.ambientSounds(json(sounds)), sounds);
        }
    }

    // --- ambient particles ---

    @Test
    void particlesShowTypeAndChance() {
        assertEquals("minecraft:white_ash 0.5%", ScapeSummary.ambientParticles(json(
                "[{\"particle\":{\"type\":\"minecraft:white_ash\"},\"probability\":0.005}]")));
    }

    @Test
    void severalParticlesAreJoinedInTheirOrder() {
        assertEquals("minecraft:white_ash 11.81%, minecraft:ash 0.63%", ScapeSummary.ambientParticles(json(
                "[{\"particle\":{\"type\":\"minecraft:white_ash\"},\"probability\":0.118093334},"
                        + "{\"particle\":{\"type\":\"minecraft:ash\"},\"probability\":0.00625}]")));
    }

    @Test
    void aChanceHasAtMostTwoDecimalsAndNoTrailingZeros() {
        String[][] chances = {{"1", "100"}, {"0.5", "50"}, {"0.025", "2.5"}, {"0.0001", "0.01"}, {"0.00004", "0"},
                {"0", "0"}, {"0.12345", "12.35"}, {"0.00625", "0.63"}, {"0.1", "10"}, {"0.07", "7"}, {"0.0111", "1.11"},
                {"1.0E-4", "0.01"}, {"0.014285714285714285", "1.43"}};
        for (String[] chance : chances) {
            assertEquals("minecraft:ash " + chance[1] + "%", ScapeSummary.ambientParticles(json(
                    "[{\"particle\":{\"type\":\"minecraft:ash\"},\"probability\":" + chance[0] + "}]")), chance[0]);
        }
    }

    @Test
    void particleSettingsBesideTheTypeAreLeftOut() {
        assertEquals("minecraft:dust 2%", ScapeSummary.ambientParticles(json(
                "[{\"particle\":{\"type\":\"minecraft:dust\",\"color\":[1.0,0.0,0.0],\"scale\":1.0},"
                        + "\"probability\":0.02}]")));
    }

    @Test
    void noParticlesAreNone() {
        assertEquals("none", ScapeSummary.ambientParticles(json("[]")));
    }

    @Test
    void particlesWithAPartMissingOrUnreadableAreCustom() {
        for (String particles : new String[] {
                "[{\"probability\":0.1}]", "[{\"particle\":{\"type\":\"minecraft:ash\"}}]",
                "[{\"particle\":{},\"probability\":0.1}]", "[{\"particle\":\"minecraft:ash\",\"probability\":0.1}]",
                "[{\"particle\":{\"type\":5},\"probability\":0.1}]",
                "[{\"particle\":{\"type\":\"minecraft:ash\"},\"probability\":\"often\"}]",
                "[\"minecraft:ash\"]", "[1]", "[{\"particle\":{\"type\":\"minecraft:ash\"},\"probability\":0.1}, 4]",
                "{\"particle\":{\"type\":\"minecraft:ash\"},\"probability\":0.1}", "\"minecraft:ash\"", "0.1"}) {
            assertEquals("custom", ScapeSummary.ambientParticles(json(particles)), particles);
        }
    }

    // --- dripstone particle ---

    @Test
    void dripstoneShowsItsType() {
        assertEquals("minecraft:dripping_dripstone_lava", ScapeSummary.dripstone(json(
                "{\"type\":\"minecraft:dripping_dripstone_lava\"}")));
    }

    @Test
    void dripstoneSettingsBesideTheTypeAreLeftOut() {
        assertEquals("minecraft:dust", ScapeSummary.dripstone(json("{\"type\":\"minecraft:dust\",\"scale\":2.0}")));
    }

    @Test
    void dripstoneWithoutAReadableTypeIsCustom() {
        for (String particle : new String[] {
                "{}", "{\"type\":3}", "{\"type\":[\"a:b\"]}", "\"minecraft:ash\"", "[]", "7"}) {
            assertEquals("custom", ScapeSummary.dripstone(json(particle)), particle);
        }
    }

    // --- any other shape ---

    @Test
    void anUnexpectedShapeIsShownAsCustom() {
        assertEquals("custom", ScapeSummary.music(json("[1,2]")));
        assertEquals("custom", ScapeSummary.music(json("\"minecraft:music.overworld.ocean\"")));
        assertEquals("custom", ScapeSummary.ambientSounds(json("[1,2]")));
        assertEquals("custom", ScapeSummary.ambientParticles(json("{\"a\":1}")));
        assertEquals("custom", ScapeSummary.dripstone(json("[1,2]")));
    }

    @Test
    void nothingAtAllIsCustomToo() {
        assertEquals("custom", ScapeSummary.music(null));
        assertEquals("custom", ScapeSummary.ambientSounds(null));
        assertEquals("custom", ScapeSummary.ambientParticles(null));
        assertEquals("custom", ScapeSummary.dripstone(null));
        assertEquals("custom", ScapeSummary.music(JsonNull.INSTANCE));
        assertEquals("custom", ScapeSummary.ambientSounds(JsonNull.INSTANCE));
        assertEquals("custom", ScapeSummary.ambientParticles(JsonNull.INSTANCE));
        assertEquals("custom", ScapeSummary.dripstone(JsonNull.INSTANCE));
    }

    @Test
    void aNumberTooBigToReadIsCustomAndDoesNotThrow() {
        assertEquals("custom", ScapeSummary.ambientParticles(json(
                "[{\"particle\":{\"type\":\"minecraft:ash\"},\"probability\":1e999999999}]")));
    }
}
