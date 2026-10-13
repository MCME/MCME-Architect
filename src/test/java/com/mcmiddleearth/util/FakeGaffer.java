package com.mcmiddleearth.util;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.PluginDescriptionFile;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

// A stand-in for TheGaffer 3.0.0's reflective API. Architect finds TheGaffer by its plugin name and calls these public
// static methods on its main class. A test sets where building is allowed, and reads where TheGaffer was asked and
// which builds it was told of.
//
//     MockBukkit.loadWith(FakeGaffer.class, FakeGaffer.description())
public class FakeGaffer extends JavaPlugin {

    /** A build Architect reported, by player name. */
    public record Build(String player, Location location, boolean place) {}

    public static final List<Location> asked = new ArrayList<>();
    public static final List<Build> builds = new ArrayList<>();
    public static Predicate<Location> allowed = location -> true;

    public static PluginDescriptionFile description() {
        return new PluginDescriptionFile("TheGaffer", "3.0.0-fake", FakeGaffer.class.getName());
    }

    public static boolean hasBuildPermission(Player player, Location location) {
        asked.add(location);
        return allowed.test(location);
    }

    public static String getBuildProtectionMessage(Player player, Location location) {
        return allowed.test(location) ? "" : "You are not in the job's area.";
    }

    public static void recordExternalBuild(Player player, Location location, boolean place) {
        builds.add(new Build(player.getName(), location, place));
    }
}
