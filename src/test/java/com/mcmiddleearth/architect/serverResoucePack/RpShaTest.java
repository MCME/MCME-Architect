package com.mcmiddleearth.architect.serverResoucePack;

import com.mcmiddleearth.architect.ArchitectPlugin;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockbukkit.mockbukkit.MockBukkit;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

// The hash sent with a resource pack must be the 20 bytes of its SHA-1, or Paper refuses the pack. The config holds
// it as hex, which RpManager reads through BigInteger: that drops leading zero bytes and may add a sign byte, so
// about one release in 256 has a hash that comes out short unless it is padded. The inputs below were picked for the
// first bytes of their SHA-1. One mock/load per class, as in LogFileTest.
class RpShaTest {

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

    @ParameterizedTest(name = "the SHA-1 of {0} starts with {1}")
    @CsvSource({
            "pack-126,   0055",   // one zero byte: BigInteger gives 19 bytes
            "pack-28147, 000032", // two zero bytes: 18 bytes
            "pack-1540,  008d",   // a zero byte, then one with the high bit set: 20 bytes, the first a sign byte
            "pack-0,     33",     // a usual hash: 20 bytes
            "pack-1,     e6",     // the high bit set: 21 bytes, the first a sign byte
    })
    void theHashSentIsTheSha1(String input, String start) throws Exception {
        byte[] sha1 = MessageDigest.getInstance("SHA-1").digest(input.getBytes(StandardCharsets.US_ASCII));
        String hex = HexFormat.of().formatHex(sha1);
        assertTrue(hex.startsWith(start), "the SHA-1 of " + input + " is " + hex);

        String rp = "Sha" + input.replace("-", "");
        plugin.getConfig().set("ServerResourcePacks." + rp + ".url", "https://example.invalid/" + input + ".zip");
        plugin.getConfig().set("ServerResourcePacks." + rp + ".sha", hex);

        assertArrayEquals(sha1, RpManager.getSHA(rp, null), "the hash sent for a pack whose SHA-1 is " + hex);
    }
}
