package com.mcmiddleearth.architect.serverResoucePack;

import com.mcmiddleearth.architect.ArchitectPlugin;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.regions.CuboidRegion;
import com.sk89q.worldedit.world.NullWorld;
import org.bukkit.Location;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

// The RP regions are read from other threads (SlabSmooth looks them up while FastAsyncWorldEdit's queue runs a brush).
class RpRegionsConcurrencyTest {

    private static ServerMock server;

    @BeforeAll
    static void setUp() {
        server = MockBukkit.mock();
        MockBukkit.load(ArchitectPlugin.class);
    }

    @AfterAll
    static void tearDown() {
        if (MockBukkit.isMocked()) {
            MockBukkit.unmock();
        }
    }

    // A region that adds another while the regions are being walked: with a plain HashMap that threw
    // ConcurrentModificationException.
    @Test
    void addingARegionWhileReadingIsSafe() {
        RpManager.addRegion(new RpRegion("first", box()));
        AtomicInteger added = new AtomicInteger();
        RpRegion adder = new RpRegion("adder", box()) {
            @Override
            public boolean contains(Location loc) {
                RpManager.addRegion(new RpRegion("added-" + added.incrementAndGet(), box()));
                return false;
            }
        };
        try {
            RpManager.addRegion(adder);
            Location somewhere = new Location(server.addSimpleWorld("rpconcurrency"), 1, 1, 1);
            assertDoesNotThrow(() -> RpManager.getRegion(somewhere));
            assertTrue(added.get() > 0, "the adding region was asked");
            assertEquals(null, RpManager.getRegion("nothing"));
        } finally {
            RpManager.removeRegion("adder");
            RpManager.removeRegion("first");
            for (int i = 1; i <= added.get(); i++) {
                RpManager.removeRegion("added-" + i);
            }
        }
    }

    private static CuboidRegion box() {
        return new CuboidRegion(NullWorld.getInstance(), BlockVector3.at(0, 0, 0), BlockVector3.at(10, 10, 10));
    }
}
