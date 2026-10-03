package com.mcmiddleearth.architect.noPhysicsEditor;

import org.bukkit.util.Vector;

import java.util.AbstractMap;
import java.util.Map;
import java.util.UUID;

/**
 * One line of NoPhyExceptionAreas.txt: {@code minX;minY;minZ;dX;dY;dZ;worldUUID;type;name}, where type is redstone or
 * water. Lines written before the type was saved have no type, and are read as redstone areas, as they always were.
 */
final class ExceptionAreaLine {

    private ExceptionAreaLine() {
    }

    static String format(String name, ExceptionArea area) {
        return area.getX() + ";" + area.getY() + ";" + area.getZ() + ";" + area.getDX() + ";" + area.getDY() + ";"
                + area.getDZ() + ";" + area.getWorldUID() + ";" + area.typeName() + ";" + name;
    }

    /** The area's name and the area. A line that cannot be read throws a RuntimeException. */
    static Map.Entry<String, ExceptionArea> parse(String line) {
        String[] parts = line.split(";", 8);
        int[] numbers = new int[6];
        for (int i = 0; i < 6; i++) {
            numbers[i] = Integer.parseInt(parts[i].trim());
        }
        Vector min = new Vector(numbers[0], numbers[1], numbers[2]);
        Vector max = new Vector(numbers[0] + numbers[3], numbers[1] + numbers[4], numbers[2] + numbers[5]);
        UUID world = UUID.fromString(parts[6]);
        String rest = parts[7];
        if (rest.startsWith("water;")) {
            return new AbstractMap.SimpleEntry<>(rest.substring("water;".length()), new WaterFlowArea(world, min, max));
        }
        String name = rest.startsWith("redstone;") ? rest.substring("redstone;".length()) : rest;
        return new AbstractMap.SimpleEntry<>(name, new RedstoneCircuitArea(world, min, max));
    }
}
