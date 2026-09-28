package com.mcmiddleearth.architect.mapLayers;

import com.mcmiddleearth.architect.ArchitectPlugin;
import com.mcmiddleearth.architect.entityLogging.FakeDynmap;
import com.mcmiddleearth.architect.serverResoucePack.RpManager;
import com.mcmiddleearth.architect.serverResoucePack.RpRegion;
import com.mcmiddleearth.architect.specialBlockHandling.itemBlock.ItemBlockManager;
import com.mcmiddleearth.architect.specialBlockHandling.itemBlock.ItemBlockRegion;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.regions.CuboidRegion;
import com.sk89q.worldedit.world.NullWorld;
import org.dynmap.markers.MarkerSet;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import static org.junit.jupiter.api.Assertions.*;

// Architect's hook points, end to end: one mock/load per class, as Architect caches data-folder paths in
// static fields. The fake dynmap is loaded after Architect, as on the server.
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class MapLayersWiringTest {

    private static ServerMock server;
    private static ArchitectPlugin plugin;
    private static FakeDynmap dynmap;

    @BeforeAll
    static void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.load(ArchitectPlugin.class);
        dynmap = MockBukkit.loadWith(FakeDynmap.class, FakeDynmap.description());
        server.addSimpleWorld("world");
        server.getScheduler().performOneTick();
    }

    @AfterAll
    static void tearDown() {
        if (MockBukkit.isMocked()) {
            MockBukkit.unmock();
        }
    }

    private static MarkerSet set(String id) {
        return dynmap.getMarkerAPI().getMarkerSet(id);
    }

    private static CuboidRegion box() {
        return new CuboidRegion(NullWorld.getInstance(), BlockVector3.at(0, 0, 0), BlockVector3.at(31, 255, 31));
    }

    @Test
    @Order(1)
    void architectsLayersAreOnTheMapFromTheStart() {
        assertNotNull(set(RpRegionLayer.ID));
        assertNotNull(set(ItemBlockRegionLayer.ID));
        assertTrue(set(RpRegionLayer.ID).getHideByDefault(), "hidden by default, as before");
    }

    @Test
    @Order(2)
    void rpRegionsReachTheMapWhenTheyAreAddedOrRemoved() {
        RpRegion region = new RpRegion("Minas", box());
        region.setRp("Gondor");
        RpManager.addRegion(region);
        server.getScheduler().performOneTick();
        assertNotNull(set(RpRegionLayer.ID).findAreaMarker("minas.marker"));

        RpManager.removeRegion("Minas");
        server.getScheduler().performOneTick();
        assertNull(set(RpRegionLayer.ID).findAreaMarker("minas.marker"));
    }

    @Test
    @Order(3)
    void aNewItemBlockLimitIsOnTheMapAtOnce() {
        ItemBlockManager.addRegion(new ItemBlockRegion("Market", box()));
        server.getScheduler().performOneTick();
        assertTrue(set(ItemBlockRegionLayer.ID).findAreaMarker("market.marker").getDescription()
                .contains("Limit: 0 item blocks per chunk"), "drawn before the limit changes");
        PlayerMock admin = server.addPlayer();
        admin.setOp(true);

        admin.performCommand("itemblock limit Market 7");
        server.getScheduler().performOneTick();

        String popup = set(ItemBlockRegionLayer.ID).findAreaMarker("market.marker").getDescription();
        assertTrue(popup.contains("Limit: 7 item blocks per chunk"), popup);
    }

    @Test
    @Order(4)
    void disablingArchitectTakesItsLayersOffTheMap() {
        server.getPluginManager().disablePlugin(plugin);

        assertNull(set(RpRegionLayer.ID));
        assertNull(set(ItemBlockRegionLayer.ID));
    }
}
