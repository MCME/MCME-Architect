package com.mcmiddleearth.architect.serverResoucePack;

import com.mcmiddleearth.architect.ArchitectPlugin;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;

// Which slot of a pack's variant a client is sent: the newest one its protocol can load. One mock/load per class, as
// in LogFileTest.
class RpSlotSearchTest {

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

    // A slot under a key that protocolVersions.yml lacks, such as 26_2release, made by a release name in place of the
    // Minecraft version, silenced its whole variant: the search gave up at the first such key, and no client was sent
    // anything. Such a slot is passed over now, and the variant's other slots still serve.
    @Test
    void aStraySlotDoesNotSilenceItsVariant() {
        String slots = "ServerResourcePacks.Stray.vanilla.16px.light.";
        plugin.getConfig().set(slots + "1_21_4.url", "https://example.invalid/Stray-1.21.4.zip");
        plugin.getConfig().set(slots + "26_2.url", "https://example.invalid/Stray-26.2.zip");
        plugin.getConfig().set(slots + "26_2release.url", "https://example.invalid/Stray-26.2release.zip");

        assertAll(
                () -> assertEquals("https://example.invalid/Stray-26.2.zip", RpManager.getRpUrl("Stray", client(776)),
                        "the pack a 26.2 client is sent"),
                () -> assertEquals("https://example.invalid/Stray-1.21.4.zip", RpManager.getRpUrl("Stray", client(769)),
                        "the pack a 1.21.4 client is sent"));
    }

    // a player whose client speaks this protocol
    private static PlayerMock client(int protocol) {
        PlayerMock player = new PlayerMock(server, "Client" + protocol, UUID.randomUUID()) {
            @Override
            public int getProtocolVersion() {
                return protocol;
            }
        };
        server.addPlayer(player);
        return player;
    }
}
