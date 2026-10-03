package com.mcmiddleearth.architect.noPhysicsEditor;

import org.bukkit.util.Vector;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

// One line of NoPhyExceptionAreas.txt: minX;minY;minZ;dX;dY;dZ;worldUUID;type;name
class ExceptionAreaLineTest {

    private static final UUID WORLD = UUID.fromString("0f1e2d3c-4b5a-6978-8796-a5b4c3d2e1f0");

    @Test
    void anAreaIsWrittenWithItsType() {
        ExceptionArea water = new WaterFlowArea(WORLD, new Vector(10, 60, -5), new Vector(20, 70, 5));

        assertEquals("10;60;-5;10;10;10;" + WORLD + ";water;Fountain", ExceptionAreaLine.format("Fountain", water));
    }

    @Test
    void eachKindComesBackWhole() {
        List<ExceptionArea> areas = List.of(
                new RedstoneCircuitArea(WORLD, new Vector(-3, 60, 7), new Vector(5, 71, 20)),
                new WaterFlowArea(WORLD, new Vector(10, -64, -5), new Vector(12, -60, 30)));
        for (ExceptionArea area : areas) {
            Map.Entry<String, ExceptionArea> read = ExceptionAreaLine.parse(ExceptionAreaLine.format("Mill", area));

            assertEquals("Mill", read.getKey(), area.typeName());
            assertEquals(area.getClass(), read.getValue().getClass(), "before, every area came back as redstone");
            assertEquals(box(area), box(read.getValue()), area.typeName());
            assertEquals(WORLD, read.getValue().getWorldUID());
        }
    }

    @Test
    void anOldLineWithoutATypeIsReadAsRedstone() {
        Map.Entry<String, ExceptionArea> read = ExceptionAreaLine.parse("0;0;0;1;1;1;" + WORLD + ";Old mill");

        assertEquals("Old mill", read.getKey());
        assertInstanceOf(RedstoneCircuitArea.class, read.getValue(), "its type was never saved");
    }

    @Test
    void anOldLineNamedLikeATypeKeepsItsName() {
        for (String name : List.of("water", "redstone", "waterfall", "redstones")) {
            Map.Entry<String, ExceptionArea> read = ExceptionAreaLine.parse("0;0;0;1;1;1;" + WORLD + ";" + name);

            assertEquals(name, read.getKey());
            assertInstanceOf(RedstoneCircuitArea.class, read.getValue(), name);
        }
    }

    @Test
    void aNameMayHoldSemicolons() {
        ExceptionArea area = new WaterFlowArea(WORLD, new Vector(0, 0, 0), new Vector(1, 1, 1));

        Map.Entry<String, ExceptionArea> read = ExceptionAreaLine.parse(ExceptionAreaLine.format("a;b", area));

        assertEquals("a;b", read.getKey());
        assertInstanceOf(WaterFlowArea.class, read.getValue());
    }

    private static List<Integer> box(ExceptionArea area) {
        return List.of(area.getX(), area.getY(), area.getZ(), area.getDX(), area.getDY(), area.getDZ());
    }
}
