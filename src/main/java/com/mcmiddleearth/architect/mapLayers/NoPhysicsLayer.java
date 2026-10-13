package com.mcmiddleearth.architect.mapLayers;

import com.mcmiddleearth.architect.noPhysicsEditor.ExceptionArea;
import com.mcmiddleearth.architect.noPhysicsEditor.WaterFlowArea;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Supplier;

/** No-physics exception areas: where Architect lets redstone parts, or water and lava, keep their physics. */
public final class NoPhysicsLayer implements MapLayer {

    public static final String ID = "architect.debug.nophysics";
    static final String KEY = "noPhysics";

    // As RedstoneCircuitArea.isAffected lists them.
    private static final String REDSTONE = "Redstone wire, repeaters, comparators, redstone torches (not on walls),"
            + " redstone lamps, pistons (not sticky ones), dispensers, droppers, hoppers, dyed shulker boxes,"
            + " enchanting tables, beacons and undamaged anvils keep their block physics here, and so do iron doors"
            + " and trapdoors, and the doors, gates and trapdoors of oak, spruce, birch, jungle, acacia and dark oak:"
            + " they connect and update. Redstone power itself stays frozen on the whole server, but pistons,"
            + " dispensers, droppers and hoppers here still react to power that is already there, such as a redstone"
            + " block.";
    private static final String WATER = "Water and lava flow here.";
    // A container opens in an area only if the area affects its block, and a water area affects only water and lava.
    private static final String CONTAINERS = "Dispensers, droppers, hoppers, dyed shulker boxes, enchanting tables,"
            + " beacons and undamaged anvils set to EXCEPTION in the world config can be opened here.";

    private final boolean hidden;
    private final MapShape.Style redstone;
    private final MapShape.Style water;
    private final Supplier<Map<String, ExceptionArea>> areas;
    private final Function<UUID, String> worldNames;

    /** {@code worldNames} gives a loaded world's name, or null. */
    public NoPhysicsLayer(MapLayerConfig.Layer config, Supplier<Map<String, ExceptionArea>> areas,
                          Function<UUID, String> worldNames) {
        this.hidden = config.hidden();
        int width = config.integer("borderWidth", 2);
        double lineOpacity = config.number("borderOpacity", 0.3);
        double fillOpacity = config.number("areaOpacity", 0.2);
        int redstoneColor = config.color("redstoneColor", 0xff8c00);
        int waterColor = config.color("waterColor", 0x1e64ff);
        this.redstone = new MapShape.Style(redstoneColor, lineOpacity, width, redstoneColor, fillOpacity);
        this.water = new MapShape.Style(waterColor, lineOpacity, width, waterColor, fillOpacity);
        this.areas = areas;
        this.worldNames = worldNames;
    }

    @Override
    public String markerSetId() {
        return ID;
    }

    @Override
    public String label() {
        return "No-physics exceptions";
    }

    @Override
    public boolean hiddenByDefault() {
        return hidden;
    }

    /** One box per area whose world is loaded, sorted by name. */
    @Override
    public List<MapShape> shapes() {
        List<MapShape> result = new ArrayList<>();
        new TreeMap<>(areas.get()).forEach((name, area) -> {
            String world = worldNames.apply(area.getWorldUID());
            if (world == null) {
                return;
            }
            boolean isWater = area instanceof WaterFlowArea;
            double x1 = area.getX();
            double x2 = area.getX() + area.getDX() + 1;
            double z1 = area.getZ();
            double z2 = area.getZ() + area.getDZ() + 1;
            result.add(new MapShape.Area("nophysics." + name, name, description(name, area, isWater),
                    isWater ? water : redstone, world, new double[]{x1, x2, x2, x1}, new double[]{z1, z1, z2, z2}));
        });
        return result;
    }

    private static String description(String name, ExceptionArea area, boolean isWater) {
        return "<b>" + MapShape.html(name) + "</b>"
                + "<br>" + (isWater ? "Water area" : "Redstone area")
                + "<br>Y " + area.getY() + " to " + (area.getY() + area.getDY())
                + "<br>" + (isWater ? WATER : REDSTONE)
                + (isWater ? "" : "<br>" + CONTAINERS);
    }
}
