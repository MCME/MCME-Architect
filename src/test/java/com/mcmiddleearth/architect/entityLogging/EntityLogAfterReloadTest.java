package com.mcmiddleearth.architect.entityLogging;

import com.mcmiddleearth.architect.ArchitectPlugin;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.dynmap.markers.MarkerSet;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.world.WorldMock;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;

// Its own class, so its own JVM (surefire reuseForks=false): ELogDynmapUtil sets itself up once
// per start, and this test needs that first setup to meet a marker set that is already there.
class EntityLogAfterReloadTest {

    private static ServerMock server;
    private static FakeDynmap dynmap;
    private static WorldMock world;

    @BeforeAll
    static void setUp() {
        server = MockBukkit.mock();
        MockBukkit.load(ArchitectPlugin.class);
        dynmap = MockBukkit.loadWith(FakeDynmap.class, FakeDynmap.description());
        world = server.addSimpleWorld("world_a");
    }

    @AfterAll
    static void tearDown() { if (MockBukkit.isMocked()) MockBukkit.unmock(); }

    // dynmap outlives a reload of Architect, and so does the "Entities" set with the old
    // instance's markers. createMarkerSet refuses an id that is taken, so the new instance has
    // to take the set over: clear the old markers and draw its own.
    @Test
    void takesOverTheMarkerSetAnEarlierInstanceLeft() {
        MarkerSet leftOver = dynmap.getMarkerAPI()
                                   .createMarkerSet("entities.markerset", "Entities", null, false);
        leftOver.createAreaMarker("e_world_a_0_0.marker", "0 0", false, "world_a",
                                  new double[]{16, 16, 0, 0}, new double[]{16, 0, 0, 16}, false);

        EntityLogger.start();
        Chunk chunk = world.getChunkAt(2, -3);
        Entity stand = world.spawn(new Location(world, 40, 64, -40), ArmorStand.class);
        server.getPluginManager().callEvent(new EntitiesLoadEvent(chunk, List.of(stand)));
        server.getScheduler().performTicks(500);

        Set<String> markers = leftOver.getAreaMarkers().stream()
                                      .map(marker -> marker.getWorld() + " " + marker.getLabel())
                                      .collect(Collectors.toSet());
        assertEquals(Set.of("world_a 32 -48"), markers);
    }
}
