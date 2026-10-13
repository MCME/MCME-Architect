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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

// Its own class, so its own JVM (surefire reuseForks=false): it disables Architect.
class EntityLogOnDisableTest {

    private static ServerMock server;
    private static ArchitectPlugin plugin;
    private static FakeDynmap dynmap;
    private static WorldMock world;

    @BeforeAll
    static void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.load(ArchitectPlugin.class);
        dynmap = MockBukkit.loadWith(FakeDynmap.class, FakeDynmap.description());
        world = server.addSimpleWorld("world_a");
    }

    @AfterAll
    static void tearDown() { if (MockBukkit.isMocked()) MockBukkit.unmock(); }

    // dynmap outlives a reload of Architect (and is disabled after it at shutdown, as Paper
    // disables plugins in reverse load order), so Architect takes its markers off the map itself.
    // Stopping also drops the reference to the timer Bukkit cancels on disable.
    @Test
    void disablingArchitectStopsLoggingAndClearsTheMap() {
        EntityLogger.start();
        Chunk chunk = world.getChunkAt(2, -3);
        Entity stand = world.spawn(new Location(world, 40, 64, -40), ArmorStand.class);
        server.getPluginManager().callEvent(new EntitiesLoadEvent(chunk, List.of(stand)));
        server.getScheduler().performTicks(500);
        MarkerSet markers = dynmap.getMarkerAPI().getMarkerSet("entities.markerset");
        assertEquals(1, markers.getAreaMarkers().size(), "the dump should have drawn the chunk");

        server.getPluginManager().disablePlugin(plugin);

        assertEquals(Set.of(), markers.getAreaMarkers());
        assertFalse(EntityLogger.stop(), "logging should already be off");
    }
}
