package com.mcmiddleearth.architect.mapLayers;

import com.mcmiddleearth.architect.noPhysicsEditor.ExceptionArea;
import com.mcmiddleearth.architect.noPhysicsEditor.RedstoneCircuitArea;
import com.mcmiddleearth.architect.noPhysicsEditor.WaterFlowArea;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.util.Vector;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class NoPhysicsLayerTest {

    private static final UUID OVERWORLD = UUID.randomUUID();
    private static final UUID UNLOADED = UUID.randomUUID();

    private static NoPhysicsLayer layer(Map<String, ExceptionArea> areas) {
        return new NoPhysicsLayer(new MapLayerConfig(new YamlConfiguration()).layer(NoPhysicsLayer.KEY, false),
                () -> areas, uuid -> uuid.equals(OVERWORLD) ? "world" : null);
    }

    private static Map<String, ExceptionArea> areas() {
        Map<String, ExceptionArea> areas = new LinkedHashMap<>();
        areas.put("Mill&Co", new RedstoneCircuitArea(OVERWORLD, new Vector(0, 60, 0), new Vector(9, 70, 19)));
        areas.put("Fountain", new WaterFlowArea(OVERWORLD, new Vector(100, 50, 100), new Vector(104, 55, 104)));
        areas.put("Elsewhere", new WaterFlowArea(UNLOADED, new Vector(0, 0, 0), new Vector(1, 1, 1)));
        return areas;
    }

    @Test
    void eachAreaIsABoxAroundItsBlocksInItsTypesColour() {
        List<MapShape> shapes = layer(areas()).shapes();

        assertEquals(List.of("nophysics.Fountain", "nophysics.Mill&Co"), shapes.stream().map(MapShape::id).toList(),
                "sorted by name; an area whose world is not loaded is left out");
        MapShape.Area mill = (MapShape.Area) shapes.get(1);
        assertArrayEquals(new double[]{0, 10, 10, 0}, mill.x());
        assertArrayEquals(new double[]{0, 0, 20, 20}, mill.z());
        assertEquals("world", mill.world());
        assertEquals(new MapShape.Style(0xff8c00, 0.3, 2, 0xff8c00, 0.2), mill.style(), "orange, as spec §6");
        assertEquals(new MapShape.Style(0x1e64ff, 0.3, 2, 0x1e64ff, 0.2), shapes.get(0).style(), "blue");
    }

    @Test
    void thePopupSaysWhatTheAreaFreesAndThatPowerStaysFrozen() {
        List<MapShape> shapes = layer(areas()).shapes();
        String water = shapes.get(0).description();
        String redstone = shapes.get(1).description();

        assertTrue(redstone.startsWith("<b>Mill&amp;Co</b>"), "the name, escaped: " + redstone);
        assertTrue(redstone.contains("Redstone area"), redstone);
        assertTrue(redstone.contains("can be opened here"), redstone);
        assertTrue(redstone.contains("set to EXCEPTION in the world config"), "as the config spells it: " + redstone);
        assertTrue(redstone.contains("keep their block physics here"), redstone);
        assertTrue(redstone.contains("redstone torches (not on walls)"), "wall torches are other blocks: " + redstone);
        assertTrue(redstone.contains("pistons (not sticky ones)"), redstone);
        assertTrue(redstone.contains("trapdoors of oak, spruce, birch, jungle, acacia and dark oak:"),
                "no newer wood: " + redstone);
        assertTrue(redstone.contains("still react to power that is already there, such as a redstone block"),
                "what reads power without the event: " + redstone);
        assertTrue(redstone.contains("beacons and undamaged anvils keep"), "chipped ones are not: " + redstone);
        assertTrue(redstone.contains("beacons and undamaged anvils set to"), "nor in the container line: " + redstone);
        assertTrue(redstone.contains("Redstone power itself stays frozen on the whole server"),
                "the server-wide freeze stays (Eriol, 2026-09-27): " + redstone);
        assertTrue(redstone.contains("Y 60 to 70"), redstone);
        ExceptionArea parts = new RedstoneCircuitArea(OVERWORLD, new Vector(0, 0, 0), new Vector(0, 0, 0));
        assertEquals(48, java.util.Arrays.stream(org.bukkit.Material.values()).filter(parts::isAffected).count(),
                "RedstoneCircuitArea's parts changed: REDSTONE and CONTAINERS must say so");
        assertTrue(water.contains("Water and lava flow here"), water);
        assertFalse(water.contains("can be opened"), "no container is water or lava, so none opens here: " + water);
    }

    @Test
    void theLayerIsFiledUnderDebug() {
        NoPhysicsLayer layer = layer(Map.of());

        assertTrue(layer.markerSetId().contains("debug"), "LiveAtlas files ids with 'debug' under Debug");
        assertEquals("No-physics exceptions", layer.label());
        assertTrue(layer.hiddenByDefault());
    }
}
