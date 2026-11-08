package com.mcmiddleearth.architect.slabSmooth;

import com.mcmiddleearth.architect.ArchitectPlugin;
import com.mcmiddleearth.architect.serverResoucePack.RpManager;
import com.mcmiddleearth.architect.serverResoucePack.RpRegion;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.regions.CuboidRegion;
import com.sk89q.worldedit.world.NullWorld;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ArchitectPacksTest {

    private static ServerMock server;
    private static ArchitectPlugin plugin;
    private static final List<String> WARNINGS = new ArrayList<>();
    private static ArchitectPacks packs;

    @BeforeAll
    static void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.load(ArchitectPlugin.class);
        Logger log = Logger.getLogger("ArchitectPacksTest");
        log.setUseParentHandlers(false);
        log.addHandler(new Handler() {
            @Override
            public void publish(LogRecord record) {
                if (record.getLevel().intValue() >= Level.WARNING.intValue()) {
                    WARNINGS.add(record.getMessage());
                }
            }

            @Override
            public void flush() {
            }

            @Override
            public void close() {
            }
        });
        packs = new ArchitectPacks(plugin, log);
    }

    @AfterAll
    static void tearDown() {
        if (MockBukkit.isMocked()) {
            MockBukkit.unmock();
        }
    }

    @Test
    void thePacksComeFromTheRpConfig() {
        plugin.getConfig().set("ServerResourcePacks.Elf.optifine.16px.light.url", "https://example.invalid/Elf-optifine.zip");
        plugin.getConfig().set("ServerResourcePacks.Elf.vanilla.16px.light.url", "https://example.invalid/Elf.zip");
        plugin.getConfig().set("ServerResourcePacks.Elf.vanilla.16px.light.sha", " ABC123 ");

        assertTrue(packs.packs().contains("Elf"), packs.packs().toString());
        PackSources.Source source = packs.source("Elf");
        assertEquals("https://example.invalid/Elf.zip", source.url());
        assertEquals("abc123", source.sha1(), "the checksum is trimmed and in lower case");
        assertNull(packs.source("NoSuchPack"));
        assertEquals(plugin.getDataFolder().toPath().resolve("inventories").resolve("block").resolve("Elf"), packs.inventory("Elf"));
    }

    @Test
    void theVanillaVariantIsPreferred() {
        // the optifine variant is listed first; every variant has the same block models, so the vanilla one is read
        plugin.getConfig().set("ServerResourcePacks.Dwarf.optifine.16px.light.26_2.url", "https://example.invalid/Dwarf-optifine.zip");
        plugin.getConfig().set("ServerResourcePacks.Dwarf.vanilla.16px.light.26_2.url", "https://example.invalid/Dwarf-vanilla.zip");
        plugin.getConfig().set("ServerResourcePacks.Orc.optifine.16px.light.url", "https://example.invalid/Orc-optifine.zip");

        assertEquals("https://example.invalid/Dwarf-vanilla.zip", packs.source("Dwarf").url());
        assertEquals("https://example.invalid/Orc-optifine.zip", packs.source("Orc").url(), "the first url when there is no vanilla variant");
    }

    @Test
    void aRegionGivesItsPack() {
        CuboidRegion area = new CuboidRegion(NullWorld.getInstance(), BlockVector3.at(0, 0, 0), BlockVector3.at(10, 10, 10));
        RpRegion region = new RpRegion("ArchitectPacksTest-region", area) {
            @Override
            public boolean contains(Location loc) {
                return loc.getBlockX() < 100;
            }
        };
        region.setRp("Elf");
        RpManager.addRegion(region);
        try {
            Location inside = new Location(server.addSimpleWorld("architectpacks"), 5, 5, 5);
            Location outside = new Location(inside.getWorld(), 500, 5, 5);
            assertEquals("Elf", packs.regionPack(inside));
            assertNull(packs.regionPack(outside));
            RpRegion noPack = new RpRegion("ArchitectPacksTest-nopack", area) {
                @Override
                public boolean contains(Location loc) {
                    return loc.getBlockX() >= 400;
                }
            };
            noPack.setWeight(5);
            RpManager.addRegion(noPack);
            assertNull(packs.regionPack(outside), "a region without a pack answers null");
        } finally {
            RpManager.removeRegion("ArchitectPacksTest-region");
            RpManager.removeRegion("ArchitectPacksTest-nopack");
        }
    }

    // An RP entry without a url makes RpManager.getRpForUrl throw; that must not reach an edit.
    @Test
    void aBrokenRpConfigMeansNoPack() {
        plugin.getConfig().set("ServerResourcePacks.Broken.vanilla.16px.light.1_21_4.sha", "abc");
        Player player = server.addPlayer();
        WARNINGS.clear();

        assertNull(packs.currentPack(player));
        assertNull(packs.currentPack(player));

        assertEquals(1, WARNINGS.size(), "said once: " + WARNINGS);
        assertNotNull(WARNINGS.get(0));
        assertTrue(WARNINGS.get(0).contains("default pack"), WARNINGS.get(0));
    }
}
