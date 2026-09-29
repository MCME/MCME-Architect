package com.mcmiddleearth.architect.mapLayers;

import com.mcmiddleearth.architect.ArchitectPlugin;
import com.mcmiddleearth.architect.Permission;
import com.mcmiddleearth.architect.entityLogging.FakeDynmap;
import com.mcmiddleearth.architect.noPhysicsEditor.NoPhysicsData;
import com.mcmiddleearth.architect.noPhysicsEditor.WaterFlowArea;
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
    void noPhysicsAreasComeBackWithTheirTypeAndReachTheMap() throws Exception {
        java.util.UUID world = server.getWorld("world").getUID();
        java.nio.file.Path file = new java.io.File(plugin.getDataFolder(), "NoPhyExceptionAreas.txt").toPath();
        // a byte-order mark, CRLF and LF, a bad line, a blank line, and a name saved in Latin-1, not UTF-8
        java.nio.file.Files.writeString(file, "\uFEFF0;60;0;9;10;19;" + world + ";water;Fountain\r\n"
                + "not an area\n\n20;60;0;9;10;19;" + world + ";Old mill\r\n");
        java.nio.file.Files.write(file, ("40;60;0;1;1;1;" + world + ";Caf\u00e9\n")
                .getBytes(java.nio.charset.StandardCharsets.ISO_8859_1), java.nio.file.StandardOpenOption.APPEND);

        NoPhysicsData.loadExceptionAreas();
        server.getScheduler().performOneTick();

        assertInstanceOf(WaterFlowArea.class, NoPhysicsData.getExceptionAreas().get("Fountain"));
        assertInstanceOf(com.mcmiddleearth.architect.noPhysicsEditor.RedstoneCircuitArea.class,
                NoPhysicsData.getExceptionAreas().get("Old mill"), "an unreadable line costs only itself");
        assertTrue(NoPhysicsData.getExceptionAreas().containsKey("Caf\uFFFD"), "a stray byte costs one character");
        assertArrayEquals(java.nio.file.Files.readAllBytes(file),
                java.nio.file.Files.readAllBytes(NoPhysicsData.backupFile().toPath()),
                "the file as it was, byte for byte, since the next save drops the unreadable line");
        org.dynmap.markers.AreaMarker marker = set(NoPhysicsLayer.ID).findAreaMarker("nophysics.Fountain");
        assertEquals("world", marker.getWorld());
        assertEquals(0x1e64ff, marker.getFillColor(), "blue: a water area");

        server.addSimpleWorld(NullWorld.getInstance().getName()); // setExceptionArea finds the world by name
        NoPhysicsData.setExceptionArea("Mill", box(), "redstone");
        server.getScheduler().performOneTick();
        assertNotNull(set(NoPhysicsLayer.ID).findAreaMarker("nophysics.Mill"), "drawn when set");

        NoPhysicsData.deleteExceptionArea("Fountain");
        server.getScheduler().performOneTick();
        assertNull(set(NoPhysicsLayer.ID).findAreaMarker("nophysics.Fountain"), "gone when deleted");
    }

    @Test
    @Order(5)
    void aChunkFillingUpWithItemBlocksShowsOnTheMap() {
        org.bukkit.World world = server.getWorld("world");
        java.util.List<org.bukkit.entity.Entity> stands = new java.util.ArrayList<>();
        for (int i = 0; i < 4; i++) {
            stands.add(world.spawn(new org.bukkit.Location(world, 72, 64, 72), org.bukkit.entity.ArmorStand.class));
        }

        server.getPluginManager().callEvent(
                new org.bukkit.event.world.EntitiesLoadEvent(world.getChunkAt(4, 4), stands));
        server.getScheduler().performTicks(201); // the budget reports at most every 10 s; the redraw follows a tick later

        org.dynmap.markers.AreaMarker tile = set(ItemBlockBudgetLayer.ID).findAreaMarker("budget.world.4.4");
        assertNotNull(tile, "4 of the world's base limit of 5 is 80%");
        assertEquals(0xff8c00, tile.getFillColor(), "orange");
    }

    // A world named in the budget's file may load later, or be gone. While it is not loaded, its chunks get no tile,
    // no world config is made for it, and their counts stay in the file as they were.
    @Test
    @Order(6)
    void aChunkInAWorldThatIsNotLoadedKeepsItsCountButHasNoTile() throws Exception {
        java.io.File folder = new java.io.File(plugin.getDataFolder(), "notLoaded");
        java.nio.file.Path file = folder.toPath().resolve("mapLayers/itemBlockBudget.yml");
        java.nio.file.Files.createDirectories(file.getParent());
        java.nio.file.Files.writeString(file, "chunks:\n- gone;0;0;9;0;0;500\n- later;0;0;3;0;0;500\n");
        ItemBlockBudgetLayer layer = ArchitectLayers.fromConfig(plugin.getConfig(), folder).stream()
                .filter(ItemBlockBudgetLayer.class::isInstance).map(ItemBlockBudgetLayer.class::cast).findFirst()
                .orElseThrow();

        layer.start(plugin);
        try {
            assertEquals(java.util.List.of(), layer.shapes(), "no tile while its world is not loaded");
            server.addSimpleWorld("later");
            assertEquals(java.util.List.of("budget.later.0.0"), layer.shapes().stream().map(MapShape::id).toList(),
                    "a tile once its world loads");
        } finally {
            layer.stop();
        }

        assertTrue(java.nio.file.Files.readString(file).contains("gone;0;0;9;0;0;500"), "kept as it was");
        assertFalse(new java.io.File(plugin.getDataFolder(), "WorldConfig/gone.yml").exists(),
                "no world config is made for a world that is not loaded");
    }

    // A region's limit is also its chunks' limit, and a new one shows at once. /architect reload saves the budget
    // before Architect clears its item-block regions, which load again only later.
    @Test
    @Order(7)
    void aChunkInALowLimitRegionShowsANewLimitAtOnceAndIsKeptOverAReload() throws Exception {
        NullWorld weWorld = new NullWorld() {
            @Override
            public String getName() {
                return "world";
            }
        };
        ItemBlockManager.addRegion(new ItemBlockRegion("Pier", new CuboidRegion(weWorld, BlockVector3.at(128, 0, 128),
                BlockVector3.at(143, 255, 143))));
        org.bukkit.World world = server.getWorld("world");
        server.getPluginManager().callEvent(new org.bukkit.event.world.EntitiesLoadEvent(world.getChunkAt(8, 8),
                java.util.List.of(world.spawn(new org.bukkit.Location(world, 136, 64, 136),
                        org.bukkit.entity.ArmorStand.class))));
        server.getScheduler().performTicks(201);
        assertEquals(0xff0000, set(ItemBlockBudgetLayer.ID).findAreaMarker("budget.world.8.8").getFillColor(),
                "a new region's limit is 0, so one stand fills it");
        PlayerMock admin = server.addPlayer();
        admin.setOp(true);

        admin.performCommand("itemblock limit Pier 2");
        server.getScheduler().performOneTick();
        assertEquals(0xffd700, set(ItemBlockBudgetLayer.ID).findAreaMarker("budget.world.8.8").getFillColor(),
                "1 of the new limit of 2: yellow, at once");
        assertTrue(set(ItemBlockBudgetLayer.ID).findAreaMarker("budget.world.8.8").getDescription()
                .contains("= 1 of 2 (region 'Pier')"), "the popup names the region placing uses");
        admin.performCommand("itemblock limit -base 4");
        server.getScheduler().performOneTick();
        assertEquals(0xff0000, set(ItemBlockBudgetLayer.ID).findAreaMarker("budget.world.4.4").getFillColor(),
                "4 of the new base limit of 4: full, at once");
        admin.performCommand("itemblock limit -base 5");
        server.getScheduler().performOneTick();

        admin.performCommand("architect reload");
        server.getScheduler().performOneTick();
        String saved = java.nio.file.Files.readString(
                new java.io.File(plugin.getDataFolder(), "mapLayers/itemBlockBudget.yml").toPath());
        assertTrue(saved.contains("world;8;8;1;0;0;"), "judged against the region's 2, not the base limit of 5");
        assertNotNull(set(ItemBlockBudgetLayer.ID).findAreaMarker("budget.world.4.4"), "drawn again from the file");
    }

    @Test
    @Order(8)
    void theRefreshCommandCountsWhatNoEventAnnounced() {
        org.bukkit.World world = server.getWorld("world");
        world.getChunkAt(6, 6);
        for (int i = 0; i < 5; i++) {
            world.spawn(new org.bukkit.Location(world, 104, 64, 104), org.bukkit.entity.ArmorStand.class);
        }
        MarkerSet budget = set(ItemBlockBudgetLayer.ID);
        // The reload made a new budget, which reports a first change within a second. Waiting longer would run the
        // region loader the reload scheduled, which needs a real WorldEdit.
        server.getScheduler().performTicks(41);
        assertNull(budget.findAreaMarker("budget.world.6.6"), "no event told the budget of these");

        PlayerMock builder = server.addPlayer();
        builder.performCommand("architect maplayers refresh");
        server.getScheduler().performOneTick();
        assertNull(budget.findAreaMarker("budget.world.6.6"), "a builder may not refresh");

        PlayerMock mapper = server.addPlayer();
        mapper.addAttachment(plugin, Permission.MAP_LAYERS.getPermissionNode(), true);
        mapper.performCommand("architect maplayers");
        assertTrue(mapper.nextMessage().contains("Usage: /architect maplayers refresh"));
        mapper.performCommand("architect maplayers now");
        assertTrue(mapper.nextMessage().contains("Usage: /architect maplayers refresh"));
        server.getScheduler().performOneTick();
        assertNull(budget.findAreaMarker("budget.world.6.6"), "only 'refresh' refreshes");

        mapper.performCommand("architect maplayers refresh");
        assertTrue(mapper.nextMessage().contains("drawn again"));
        server.getScheduler().performOneTick();
        org.dynmap.markers.AreaMarker tile = budget.findAreaMarker("budget.world.6.6");
        assertNotNull(tile, "architect.maplayers alone is enough to refresh");
        assertEquals(0xff0000, tile.getFillColor(), "5 of the base limit of 5: full");

        // The reply says what the refresh found. Each case is undone in a finally, so the next test still runs.
        server.getPluginManager().disablePlugin(dynmap); // the first half of /dynmap reload
        try {
            mapper.performCommand("architect maplayers refresh");
            assertTrue(mapper.nextMessage().contains("dynmap is not enabled here"), "not 'drawn' without dynmap");
        } finally {
            server.getPluginManager().enablePlugin(dynmap);
            server.getScheduler().performOneTick();
        }
        MapLayers.stop(); // as an /architect reload that failed partway leaves them
        try {
            mapper.performCommand("architect maplayers refresh");
            assertTrue(mapper.nextMessage().contains("not running"));
        } finally {
            MapLayers.start(plugin, ArchitectLayers.fromConfig(plugin.getConfig(), plugin.getDataFolder()));
            server.getScheduler().performOneTick();
        }
    }

    @Test
    @Order(9)
    void disablingArchitectTakesItsLayersOffTheMap() {
        server.getPluginManager().disablePlugin(plugin);

        assertNull(set(RpRegionLayer.ID));
        assertNull(set(ItemBlockRegionLayer.ID));
        assertNull(set(NoPhysicsLayer.ID));
        assertNull(set(ItemBlockBudgetLayer.ID));
        assertTrue(new java.io.File(plugin.getDataFolder(), "mapLayers/itemBlockBudget.yml").exists(),
                "the budget is kept for the next start");
    }
}
