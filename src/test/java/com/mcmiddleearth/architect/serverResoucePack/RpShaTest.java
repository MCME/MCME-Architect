package com.mcmiddleearth.architect.serverResoucePack;

import com.mcmiddleearth.architect.ArchitectPlugin;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

// The hash sent with a resource pack must be the 20 bytes of its SHA-1, or Paper refuses the pack. The config holds
// it as hex, which RpManager reads through BigInteger: that drops leading zero bytes and may add a sign byte, so
// about one release in 256 has a hash that comes out short unless it is padded. The inputs below were picked for the
// first bytes of their SHA-1. One mock/load per class, as in LogFileTest.
class RpShaTest {

    private static ServerMock server;
    private static ArchitectPlugin plugin;
    private static int packs;

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

    // A slot without a valid SHA-1 gives no hash. Paper then sends an empty one, and the client loads the pack
    // without checking it; an all-zero hash made every client reject the pack.
    @ParameterizedTest(name = "sha \"{0}\"")
    @NullSource
    @ValueSource(strings = {"", "   ", "abc", "zz838c761529237eaf1d9cdc8cd4c439d9cb0770"})
    void aSlotWithoutAValidShaGivesNoHash(String sha) {
        String rp = "NoSha" + (++packs);
        String slot = "ServerResourcePacks." + rp + ".vanilla.16px.light.1_18_1";
        plugin.getConfig().set(slot + ".url", "https://example.invalid/" + rp + ".zip");
        plugin.getConfig().set(slot + ".sha", sha);

        assertNull(RpManager.getSHA(rp, null), "the hash given for the slot");
    }

    @Test
    void aSlotWithoutAShaIsLoggedOnceWithItsPackVariantAndVersion() {
        String rp = "Unhashed";
        plugin.getConfig().set("ServerResourcePacks." + rp + ".vanilla.16px.light.1_18_1.url",
                "https://example.invalid/Unhashed.zip");
        List<String> warnings = warnings();

        RpManager.getSHA(rp, null);
        RpManager.getSHA(rp, null);

        List<String> about = warnings.stream().filter(warning -> warning.contains(rp)).toList();
        assertEquals(1, about.size(), "the warnings about the slot, of " + warnings);
        assertTrue(about.get(0).contains("vanilla.16px.light") && about.get(0).contains("1_18_1"),
                "the warning names the variant and the version: " + about.get(0));
    }

    @Test
    void aPlayerIsSentThePackOfASlotWithoutAShaWithNoHash() {
        String rp = "UnhashedSend";
        plugin.getConfig().set("ServerResourcePacks." + rp + ".vanilla.16px.light.1_18_1.url",
                "https://example.invalid/UnhashedSend.zip");
        List<byte[]> hashes = new ArrayList<>();
        PlayerMock player = new PlayerMock(server, "UnhashedSend", UUID.randomUUID()) {
            @Override
            public int getProtocolVersion() {
                return 775;
            }

            @Override
            public void setResourcePack(String url, byte[] hash) {
                hashes.add(hash);
            }
        };
        server.addPlayer(player);

        assertTrue(RpManager.setRp(rp, player, true), "the pack is sent");
        assertEquals(1, hashes.size(), "the packs sent");
        assertNull(hashes.get(0), "the hash sent with the pack");
    }

    // the warnings Architect logs from now on
    private static List<String> warnings() {
        List<String> warnings = new CopyOnWriteArrayList<>();
        plugin.getLogger().addHandler(new Handler() {
            @Override
            public void publish(LogRecord record) {
                if (record.getLevel() == Level.WARNING) {
                    warnings.add(record.getMessage());
                }
            }

            @Override
            public void flush() {
            }

            @Override
            public void close() {
            }
        });
        return warnings;
    }
}
