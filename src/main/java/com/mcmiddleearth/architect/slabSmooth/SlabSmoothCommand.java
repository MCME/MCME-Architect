package com.mcmiddleearth.architect.slabSmooth;

import com.mcmiddleearth.architect.Log;
import com.mcmiddleearth.architect.Permission;
import com.mcmiddleearth.architect.PluginData;
import com.mcmiddleearth.architect.additionalCommands.AbstractArchitectCommand;
import com.mcmiddleearth.architect.slabSmooth.core.Options;
import com.sk89q.worldedit.IncompleteRegionException;
import com.sk89q.worldedit.command.tool.InvalidToolBindException;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.regions.CuboidRegion;
import com.sk89q.worldedit.regions.Region;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * /slabsmooth, /sls: smooths stair-stepped terrain with a resource pack's slabs, stairs and vertical slabs, on a
 * WorldEdit selection, with a brush, or on a box given by its corners, and keeps each player's settings for it. Toti's
 * subcommands, checked in Architect's order: a known subcommand, its permission, whether SlabSmooth is available, and
 * then, for an edit, WorldEdit and the world's module switch. There is no reload: /architect reload reads the settings
 * again.
 */
public class SlabSmoothCommand extends AbstractArchitectCommand {

    private static final List<String> SUBCOMMANDS = List.of("selection", "brush", "area", "set", "options", "reset",
            "pack", "packs", "help");
    /** They write to a world, through the player's own WorldEdit session, so //undo reverts them. */
    private static final Set<String> EDITS = Set.of("selection", "brush", "area");
    private static final String AREA_USAGE = "Usage: /sls area <x1> <y1> <z1> <x2> <y2> <z2> [world] [pack]";
    /** No block of any world is farther from 0 than this on x or z; it also keeps the margin from overflowing. */
    private static final long EDGE = 30_000_000;

    @Override
    public boolean onCommand(@NotNull CommandSender cs, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        String sub = args.length == 0 ? "help" : subcommand(args[0]);
        if (!SUBCOMMANDS.contains(sub)) {
            PluginData.getMessageUtil().sendInvalidSubcommandError(cs);
            return true;
        }
        if (!PluginData.hasPermission(cs, permissionFor(sub, args))) {
            PluginData.getMessageUtil().sendNoPermissionError(cs);
            return true;
        }
        if (!SlabSmooth.isAvailable()) {
            error(cs, "SlabSmooth is unavailable: " + SlabSmooth.problems().get(0));
            return true;
        }
        SlabSmooth.Parts parts = SlabSmooth.parts();
        try {
            if (sub.equals("help")) {
                help(cs, label, parts);
            } else if (sub.equals("packs")) {
                packs(cs, args, parts.packs());
            } else if (cs instanceof Player player) {
                own(player, sub, args, parts);
            } else if (sub.equals("area")) {
                error(cs, "Only a player can smooth an area, so that //undo can revert it.");
            } else {
                PluginData.getMessageUtil().sendPlayerOnlyCommandError(cs);
            }
        } catch (IllegalArgumentException | IllegalStateException e) {
            error(cs, e.getMessage()); // a refusal, worded for the player
        } catch (IncompleteRegionException e) {
            error(cs, "Make a WorldEdit selection first (//wand, or //pos1 and //pos2).");
        } catch (InvalidToolBindException e) {
            error(cs, "The brush can't be bound to that item: " + e.getMessage());
        } catch (LinkageError e) { // WorldEdit or the server isn't what this was built for: SlabSmooth switches off
            error(cs, SmoothJobs.FAILED);
            SlabSmooth.fail(e);
        } catch (RuntimeException e) {
            error(cs, SmoothJobs.FAILED);
            Log.warn(cs.getName() + ": /" + label + " " + String.join(" ", args) + " failed", e);
        }
        return true;
    }

    /** The subcommand a first word names, sel being short for selection. */
    private static String subcommand(String word) {
        String sub = word.toLowerCase(Locale.ROOT);
        return sub.equals("sel") ? "selection" : sub;
    }

    static Permission permissionFor(String sub, String[] args) {
        return switch (sub) {
            case "selection" -> Permission.SLAB_SMOOTH_SELECTION;
            case "brush" -> Permission.SLAB_SMOOTH_BRUSH;
            case "area" -> Permission.SLAB_SMOOTH_AREA;
            case "packs" -> args.length > 1 && args[1].equalsIgnoreCase("rebuild") ? Permission.SLAB_SMOOTH_ADMIN
                    : Permission.SLAB_SMOOTH;
            default -> Permission.SLAB_SMOOTH;
        };
    }

    /** What a player does for themselves: edits, which need WorldEdit, and their own settings. */
    private static void own(Player player, String sub, String[] args, SlabSmooth.Parts parts)
            throws IncompleteRegionException, InvalidToolBindException {
        if (EDITS.contains(sub) && !SlabSmooth.worldEditRunning()) {
            throw new IllegalStateException("WorldEdit isn't available on this server, so SlabSmooth can't edit.");
        }
        switch (sub) {
            case "selection" -> selection(player, parts);
            case "brush" -> brush(player, args, parts);
            case "area" -> area(player, args, parts);
            case "set" -> set(player, args, parts);
            case "options" -> options(player, parts);
            case "reset" -> {
                parts.options().reset(player.getUniqueId());
                info(player, "Your settings are back to the defaults, and your pack to automatic.");
            }
            default -> pack(player, args, parts);
        }
    }

    private static void help(CommandSender cs, String label, SlabSmooth.Parts parts) {
        String command = "/" + label;
        List<String> lines = new ArrayList<>();
        if (PluginData.hasPermission(cs, Permission.SLAB_SMOOTH_SELECTION)) {
            lines.add(command + " selection: smooth your WorldEdit selection: its tops, walls and overhangs, or with "
                    + command + " set surfaces top only its tops. //undo reverts it.");
        }
        if (PluginData.hasPermission(cs, Permission.SLAB_SMOOTH_BRUSH)) {
            lines.add(command + " brush [radius]: bind the smoothing brush to the item in your hand (radius 1 to "
                    + parts.settings().maxBrushRadius() + ")");
        }
        if (PluginData.hasPermission(cs, Permission.SLAB_SMOOTH_AREA)) {
            lines.add(command + " area <x1> <y1> <z1> <x2> <y2> <z2> [world] [pack]: smooth a box");
        }
        if (!lines.isEmpty()) { // they may edit
            lines.add("Every terrain block inside is smoothed, also where it is part of a building: near buildings, "
                    + "use //mask or a smaller selection.");
        }
        lines.add(command + " set <option> <value>, options, reset: your settings");
        lines.add(command + " pack [name|auto]: which pack's blocks to use (auto: the RP region's, else your pack, else "
                + parts.packs().defaultPack() + ")");
        lines.add(command + " packs: the packs and their tables");
        if (PluginData.hasPermission(cs, Permission.SLAB_SMOOTH_ADMIN)) {
            lines.add(command + " packs rebuild [pack]: read the packs again");
        }
        info(cs, "SlabSmooth smooths terrain blocks with your pack's slabs, stairs and vertical slabs:");
        lines.forEach(line -> PluginData.getMessageUtil().sendIndentedInfoMessage(cs, line));
    }

    private static void selection(Player player, SlabSmooth.Parts parts) throws IncompleteRegionException {
        requireOn(player.getWorld(), "this world", parts);
        Region region = SlabSmooth.worldEdit().selection(player);
        UUID id = player.getUniqueId();
        SlabSmooth.jobs().start(player, player.getWorld(), region, parts.options().namedPack(id), true,
                parts.options().of(id));
    }

    private static void brush(Player player, String[] args, SlabSmooth.Parts parts) throws InvalidToolBindException {
        requireOn(player.getWorld(), "this world", parts);
        int max = parts.settings().maxBrushRadius();
        double radius = args.length > 1 ? number(args[1]) : Math.min(5, max);
        if (!(radius >= 1 && radius <= max)) { // also NaN
            throw new IllegalArgumentException("The radius must be a number from 1 to " + max + ".");
        }
        Material item = player.getInventory().getItemInMainHand().getType();
        if (item.isAir() || item.isBlock()) {
            throw new IllegalArgumentException("Hold a tool or another item that isn't a block, such as a stick or a "
                    + "feather, to bind the brush to.");
        }
        // WorldEdit checks the node at every click; the brush asks SlabSmooth for its settings at every click too
        SlabSmooth.worldEdit().bindBrush(player, radius, new SmoothBrush(player.getUniqueId(), SlabSmooth::brushContext,
                Bukkit::getPlayer), Permission.SLAB_SMOOTH_BRUSH.getPermissionNode());
        info(player, "SlabSmooth brush (radius " + format(radius) + ") bound to " + item.getKey().getKey()
                + ". Right-click terrain to smooth it; //mask and //undo work as usual.");
    }

    /** /sls area: a box by its corners, in the world named or the player's own, as a job that writes there. */
    private static void area(Player player, String[] args, SlabSmooth.Parts parts) {
        if (args.length < 7 || args.length > 9) {
            throw new IllegalArgumentException(AREA_USAGE);
        }
        World world = args.length > 7 ? Bukkit.getWorld(args[7]) : player.getWorld();
        if (world == null) {
            throw new IllegalArgumentException("There is no world named " + args[7] + ".");
        }
        requireOn(world, world.getName(), parts);
        long[] corners = new long[6];
        for (int i = 0; i < 6; i++) {
            try {
                corners[i] = Long.parseLong(args[i + 1]);
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Coordinates are whole numbers; " + args[i + 1] + " isn't one.");
            }
        }
        // bounded first, so the margin read around the box can't overflow
        for (int i : new int[] {0, 2, 3, 5}) {
            if (Math.abs(corners[i]) > EDGE) {
                throw new IllegalArgumentException("x and z must be between -" + EDGE + " and " + EDGE + ".");
            }
        }
        int bottom = world.getMinHeight(), top = world.getMaxHeight() - 1;
        for (int i : new int[] {1, 4}) {
            if (corners[i] < bottom || corners[i] > top) {
                throw new IllegalArgumentException("y must be between " + bottom + " and " + top + " in " + world.getName() + ".");
            }
        }
        UUID id = player.getUniqueId();
        String pack = args.length > 8 ? knownPack(parts.packs(), args[8]) : parts.options().namedPack(id);
        Region box = new CuboidRegion(BlockVector3.at((int) corners[0], (int) corners[1], (int) corners[2]),
                BlockVector3.at((int) corners[3], (int) corners[4], (int) corners[5]));
        SlabSmooth.jobs().start(player, world, box, pack, false, parts.options().of(id));
    }

    private static void set(Player player, String[] args, SlabSmooth.Parts parts) {
        if (args.length != 3) {
            throw new IllegalArgumentException("Usage: /sls set <option> <value>; options: " + String.join(", ", Options.NAMES));
        }
        // checked here, and again whenever a job starts or a brush clicks, as the node can be taken away meanwhile
        if (args[1].equals("blocks") && args[2].equals("all") && !PluginData.hasPermission(player, Permission.SLAB_SMOOTH_ALL_BLOCKS)) {
            throw new IllegalArgumentException("blocks all needs a permission of its own; blocks terrain is yours to use.");
        }
        UUID id = player.getUniqueId();
        parts.options().set(id, args[1], args[2]);
        info(player, args[1] + " = " + parts.options().of(id).values().get(args[1]) + ", for your selections, areas and brush.");
    }

    private static void options(Player player, SlabSmooth.Parts parts) {
        Map<String, String> defaults = parts.defaults().values();
        List<String> shown = new ArrayList<>();
        parts.options().of(player.getUniqueId()).values()
                .forEach((name, value) -> shown.add(name + "=" + value + (value.equals(defaults.get(name)) ? "" : "*")));
        info(player, "Your settings (* = changed): " + String.join(", ", shown));
    }

    private static void pack(Player player, String[] args, SlabSmooth.Parts parts) {
        if (args.length > 2) {
            throw new IllegalArgumentException("Usage: /sls pack [name|auto]");
        }
        UUID id = player.getUniqueId();
        if (args.length > 1) {
            parts.options().namePack(id, args[1].equalsIgnoreCase("auto") ? null : knownPack(parts.packs(), args[1]));
        }
        String named = parts.options().namedPack(id);
        Packs.Choice here = parts.packs().choose(player, player.getLocation(), named);
        info(player, (named == null ? "Pack: automatic (the RP region's, else your pack, else " + parts.packs().defaultPack() + ")"
                : "Pack: " + named) + ". Here: " + here.pack() + " (" + here.why() + ").");
    }

    private static void packs(CommandSender cs, String[] args, Packs packs) {
        if (args.length == 1) {
            info(cs, "Default pack: " + packs.defaultPack() + ". The packs and their tables:");
            packs.status().forEach(line -> PluginData.getMessageUtil().sendIndentedInfoMessage(cs, line));
            return;
        }
        if (!args[1].equalsIgnoreCase("rebuild") || args.length > 3) {
            throw new IllegalArgumentException("Usage: /sls packs [rebuild [pack]]");
        }
        List<String> names = args.length > 2 ? List.of(knownPack(packs, args[2])) : packs.names();
        if (names.isEmpty()) {
            throw new IllegalStateException("There are no packs to read: Architect's config.yml lists no ServerResourcePacks.");
        }
        List<String> started = new ArrayList<>(), checking = new ArrayList<>();
        for (String name : names) {
            (packs.rebuild(name) ? started : checking).add(name);
        }
        if (!started.isEmpty()) {
            info(cs, "Reading " + String.join(", ", started) + " again; /sls packs shows how it goes.");
        }
        if (!checking.isEmpty()) { // a check under way builds only if something changed, so nothing was started
            error(cs, String.join(", ", checking) + (checking.size() == 1 ? " is" : " are")
                    + " being checked already; try again in a moment.");
        }
    }

    /** Refuses an edit in a world where SlabSmooth is off. */
    private static void requireOn(World world, String name, SlabSmooth.Parts parts) {
        if (!parts.module().enabledIn(world)) {
            throw new IllegalStateException("SlabSmooth is off in " + name + ".");
        }
    }

    /** The pack of this name, whatever its case. */
    private static String knownPack(Packs packs, String name) {
        List<String> names = packs.names();
        return names.stream().filter(known -> known.equalsIgnoreCase(name)).findFirst().orElseThrow(
                () -> new IllegalArgumentException("No pack " + name + "; packs: " + String.join(", ", names) + "."));
    }

    private static double number(String text) {
        try {
            return Double.parseDouble(text);
        } catch (NumberFormatException e) {
            return Double.NaN;
        }
    }

    private static String format(double value) {
        return value == Math.rint(value) ? String.valueOf((long) value) : String.valueOf(value);
    }

    private static void info(CommandSender cs, String message) {
        PluginData.getMessageUtil().sendInfoMessage(cs, message);
    }

    private static void error(CommandSender cs, String message) {
        PluginData.getMessageUtil().sendErrorMessage(cs, message);
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, Command command, String alias, String[] args) {
        // Only what the sender may use, by the rules onCommand applies. Nothing is an empty list, not null: on null,
        // Bukkit would offer the names of every online player.
        if (args.length == 0) {
            return List.of();
        }
        String sub = subcommand(args[0]);
        List<String> options;
        if (args.length == 1) {
            options = SUBCOMMANDS.stream()
                    .filter(word -> PluginData.hasPermission(sender, permissionFor(word, new String[] {word}))).toList();
        } else if (!SUBCOMMANDS.contains(sub) || !PluginData.hasPermission(sender, permissionFor(sub, args))) {
            return List.of();
        } else {
            options = switch (sub) {
                case "set" -> args.length == 2 ? Options.NAMES : args.length == 3 ? values(sender, args[1]) : List.<String>of();
                case "pack" -> args.length == 2 ? Stream.concat(packNames().stream(), Stream.of("auto")).toList()
                        : List.<String>of();
                // while rebuild is typed, permissionFor sees no rebuild yet, so its own node is checked here
                case "packs" -> args.length == 2
                        ? PluginData.hasPermission(sender, Permission.SLAB_SMOOTH_ADMIN) ? List.of("rebuild") : List.<String>of()
                        : args.length == 3 && args[1].equalsIgnoreCase("rebuild") ? packNames() : List.<String>of();
                case "area" -> args.length == 8 ? Bukkit.getWorlds().stream().map(World::getName).toList()
                        : args.length == 9 ? packNames() : List.<String>of();
                default -> List.<String>of();
            };
        }
        String last = args[args.length - 1].toLowerCase(Locale.ROOT);
        return options.stream().filter(option -> option.toLowerCase(Locale.ROOT).startsWith(last)).toList();
    }

    /** The values of an option worth offering; blocks all only to those who may use it. */
    private static List<String> values(CommandSender sender, String option) {
        return switch (option) {
            case "surfaces" -> List.of("all", "top");
            case "mode" -> List.of("both", "cut", "fill");
            case "plants" -> List.of("remove", "keep");
            case "blocks" -> PluginData.hasPermission(sender, Permission.SLAB_SMOOTH_ALL_BLOCKS)
                    ? List.of("terrain", "all") : List.of("terrain");
            default -> {
                SlabSmooth.Parts parts = SlabSmooth.parts();
                String value = parts == null ? null : parts.defaults().values().get(option);
                yield value == null ? List.<String>of() : List.of(value);
            }
        };
    }

    private static List<String> packNames() {
        SlabSmooth.Parts parts = SlabSmooth.parts();
        return parts == null ? List.of() : parts.packs().names();
    }

    @Override
    public String getHelpPermission() {
        return Permission.SLAB_SMOOTH.getPermissionNode();
    }

    @Override
    public String getShortDescription() {
        return ": Smooth terrain with a pack's slabs, stairs and vertical slabs.";
    }

    @Override
    public String getUsageDescription() {
        return " [subcommand]: Smooth stair-stepped terrain with the slabs, stairs and vertical slabs of the resource pack "
                + "you build with: your WorldEdit selection, a brush, or a box by its corners. //undo reverts it. "
                + "Subcommands: selection, brush, area, set, options, reset, pack, packs and help.";
    }

    @Override
    public String getHelpCommand() {
        return null;
    }
}
