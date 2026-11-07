package com.mcmiddleearth.architect.slabSmooth;

import com.mcmiddleearth.architect.slabSmooth.core.Options;
import com.mcmiddleearth.architect.slabSmooth.core.Smoother;
import com.mcmiddleearth.architect.slabSmooth.core.Table;
import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.IncompleteRegionException;
import com.sk89q.worldedit.LocalSession;
import com.sk89q.worldedit.MaxChangedBlocksException;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.command.tool.BrushTool;
import com.sk89q.worldedit.command.tool.InvalidToolBindException;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.regions.CuboidRegion;
import com.sk89q.worldedit.regions.Region;
import com.sk89q.worldedit.world.block.BlockState;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Smooths stair-stepped terrain with slabs, stairs and vertical slabs, on a WorldEdit selection or as a brush. */
public final class SlabSmoothPlugin extends JavaPlugin implements TabExecutor {

    /** Blocks read above and below the brush radius, so the ground under the brush has its full depth. */
    static final int VERTICAL_MARGIN = 8;

    /** Permissions (plugin.yml). USE is the command's own; it includes SELECTION and BRUSH unless they are taken away. */
    static final String USE = "slabsmooth.use", SELECTION = "slabsmooth.selection", BRUSH = "slabsmooth.brush",
            AREA = "slabsmooth.area", ADMIN = "slabsmooth.admin";

    private static final List<String> SUBCOMMANDS = List.of("selection", "brush", "area", "set", "options", "reset", "pack", "packs", "reload");
    private static final Map<String, String> SUBCOMMAND_PERMISSION = Map.of("selection", SELECTION, "brush", BRUSH, "area", AREA, "reload", ADMIN);
    private static final String FAILED = "Something went wrong; the server log has the details.";

    private final Map<UUID, Options> options = new ConcurrentHashMap<>();
    private final java.util.Set<UUID> busy = ConcurrentHashMap.newKeySet();
    private final Map<String, BlockState> parsedStates = new HashMap<>();
    private final Map<UUID, String> packChoice = new ConcurrentHashMap<>();
    private Options defaults = new Options();
    private Packs packs;
    private int maxBrushRadius;
    private int maxColumns;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        if (!Files.exists(getDataPath().resolve("table.json"))) {
            saveResource("table.json", false);
        }
        try {
            load();
        } catch (Exception e) {
            getLogger().severe("Could not load the material table or config: " + e.getMessage());
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        getCommand("slabsmooth").setExecutor(this);
        getCommand("slabsmooth").setTabCompleter(this);
        Bukkit.getScheduler().runTask(this, () -> packs.prewarm()); // once every plugin is up
    }

    private void load() throws IOException {
        reloadConfig();
        maxBrushRadius = getConfig().getInt("brush.max-radius", 12);
        maxColumns = getConfig().getInt("selection.max-columns", 65536);
        Options d = new Options();
        var section = getConfig().getConfigurationSection("defaults");
        if (section != null) {
            for (String key : section.getKeys(false)) {
                d.set(key, String.valueOf(section.get(key)));
            }
        }
        Path tablePath = getDataPath().resolve(getConfig().getString("table", "table.json"));
        Table builtIn = Table.load(tablePath);
        if (packs != null) {
            packs.close();
        }
        packs = new Packs(this, builtIn);
        defaults = d;
        options.clear();
        parsedStates.clear();
        getLogger().info("Materials: " + packs.describe() + ".");
    }

    Packs packs() {
        return packs;
    }

    /** The pack a player has picked with /slabsmooth pack, or null for automatic. */
    String packChoice(UUID player) {
        return packChoice.get(player);
    }

    Options options(UUID player) {
        return options.computeIfAbsent(player, p -> defaults.copy());
    }

    /**
     * When an edit changed nothing: names a block in the area that isn't terrain in this pack but has pieces of its
     * own, so "blocks all" would smooth it. Empty when there is none (or blocks is already all).
     */
    static String blocksHint(List<String> palette, Table terrain, Table all) {
        if (terrain == all) {
            return "";
        }
        for (String state : palette) {
            Table.Kind3 k = all.kind3(state);
            if (k.category() == Table.TERRAIN && terrain.kind3(state).category() != Table.TERRAIN) {
                return " " + all.materials.get(k.material()).label + " isn't terrain in this pack; /sls set blocks all smooths it too.";
            }
        }
        return "";
    }

    /** Columns read around an edit, so the blur and neighbour terms see the terrain the edit blends into. */
    static int margin(Options opts) {
        int blurOctants = (int) Math.ceil(2.5 * Math.max(opts.sigma * 2, 1e-6));
        return (blurOctants + 1) / 2 + 1;
    }

    BlockState parseState(String state) {
        return parsedStates.computeIfAbsent(state, s -> BukkitAdapter.adapt(Bukkit.createBlockData(s)));
    }

    // ---------------------------------------------------------------------------------------------------------

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            help(sender, label);
            return true;
        }
        String sub = args[0].toLowerCase(java.util.Locale.ROOT);
        String needed = SUBCOMMAND_PERMISSION.get(sub.equals("sel") ? "selection" : sub);
        if (needed != null && !sender.hasPermission(needed)) {
            error(sender, "You need " + needed + " for that.");
            return true;
        }
        try {
            switch (sub) {
                case "selection", "sel" -> selection(sender);
                case "brush" -> brush(sender, args);
                case "area" -> area(sender, args);
                case "set" -> set(sender, args);
                case "options" -> showOptions(sender);
                case "reset" -> {
                    options.remove(key(sender));
                    packChoice.remove(key(sender));
                    info(sender, "Settings back to the defaults.");
                }
                case "pack" -> pack(sender, args);
                case "packs" -> packs(sender, args);
                case "reload" -> {
                    load();
                    info(sender, "Reloaded config.yml. Materials: " + packs.describe() + ".");
                }
                default -> help(sender, label);
            }
        } catch (IllegalArgumentException | IllegalStateException e) {
            error(sender, e.getMessage());
        } catch (Exception e) {
            error(sender, FAILED);
            getLogger().log(java.util.logging.Level.WARNING, sender.getName() + ": /" + label + " " + String.join(" ", args), e);
        }
        return true;
    }

    private void help(CommandSender sender, String label) {
        if (sender.hasPermission(SELECTION)) {
            info(sender, "/" + label + " selection: smooth your WorldEdit selection (//undo works)");
            info(sender, "Smooths tops, walls and overhangs; '/" + label + " set surfaces top' for tops only.");
        }
        if (sender.hasPermission(BRUSH)) {
            info(sender, "/" + label + " brush [radius]: smoothing brush on the item in your hand (max " + maxBrushRadius + ")");
        }
        if (sender.hasPermission(AREA)) {
            info(sender, "/" + label + " area <x1> <y1> <z1> <x2> <y2> <z2> [world]: smooth a box");
        }
        info(sender, "/" + label + " set <option> <value>, options, reset: your settings");
        if (packs.dynamic()) {
            info(sender, "/" + label + " pack [name|auto]: which resource pack's blocks to use (auto: the RP region's)");
            info(sender, "/" + label + " packs: the packs and their tables");
        }
    }

    private static UUID key(CommandSender sender) {
        return sender instanceof Player p ? p.getUniqueId() : new UUID(0, 0);
    }

    private void selection(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            error(sender, "Only players have a selection; use area <x1> <y1> <z1> <x2> <y2> <z2> <world>.");
            return;
        }
        var actor = BukkitAdapter.adapt(player);
        LocalSession session = WorldEdit.getInstance().getSessionManager().get(actor);
        Region region;
        try {
            region = session.getSelection(actor.getWorld());
        } catch (IncompleteRegionException e) {
            error(sender, "Make a WorldEdit selection first (//wand, or //pos1 and //pos2).");
            return;
        }
        smoothRegion(sender, region, session, actor, null);
    }

    private void area(CommandSender sender, String[] args) {
        if (args.length < 7) {
            throw new IllegalArgumentException("Usage: area <x1> <y1> <z1> <x2> <y2> <z2> [world] [pack]");
        }
        World world = args.length > 7 ? Bukkit.getWorld(args[7]) : sender instanceof Player p ? p.getWorld() : null;
        if (world == null) {
            throw new IllegalArgumentException(args.length > 7 ? "No world " + args[7] : "Give a world name.");
        }
        int[] v = new int[6];
        for (int i = 0; i < 6; i++) {
            v[i] = Integer.parseInt(args[i + 1]);
        }
        Region region = new CuboidRegion(BukkitAdapter.adapt(world), BlockVector3.at(v[0], v[1], v[2]), BlockVector3.at(v[3], v[4], v[5]));
        String pack = args.length > 8 ? args[8] : null;
        if (sender instanceof Player player) {
            var actor = BukkitAdapter.adapt(player);
            smoothRegion(sender, region, WorldEdit.getInstance().getSessionManager().get(actor), actor, pack);
        } else {
            smoothRegion(sender, region, null, null, pack);
        }
    }

    /**
     * Smooths inside region, reading a margin around it so the result blends into the surroundings. The world is
     * read now, the smoothing runs off the main thread, and the changes are written back on it as one undo step.
     */
    private void smoothRegion(CommandSender sender, Region region, LocalSession session, com.sk89q.worldedit.entity.Player actor,
                              String pack) {
        UUID who = key(sender);
        Options opts = options(who).copy();
        World bukkitWorld = BukkitAdapter.adapt(region.getWorld());
        var center = region.getCenter();
        Packs.Choice choice = packs.choose(sender instanceof Player p ? p : null,
                new org.bukkit.Location(bukkitWorld, center.x(), center.y(), center.z()), pack != null ? pack : packChoice.get(who));
        boolean everyBlock = "all".equals(opts.blocks);
        Table useTable = packs.table(choice, everyBlock); // throws with a message if the pack isn't ready
        Table allTable = everyBlock ? useTable : packs.table(choice, true);
        String packNote = packs.dynamic() ? " [" + choice.pack() + " pack]" : "";
        boolean all = !"top".equals(opts.surfaces);
        int margin = margin(opts);
        BlockVector3 lo = region.getMinimumPoint(), hi = region.getMaximumPoint();
        long columns = (long) (hi.x() - lo.x() + 1 + 2 * margin) * (hi.z() - lo.z() + 1 + 2 * margin);
        if (columns > maxColumns) {
            throw new IllegalArgumentException("Selection too big: " + columns + " columns with the margin, max "
                    + maxColumns + " (selection.max-columns in config.yml).");
        }
        if (!busy.add(who)) {
            throw new IllegalArgumentException("Still working on your last one.");
        }
        long start = System.nanoTime();
        int up = all ? margin : 0;
        if (columns > 4096) {
            info(sender, "Smoothing " + columns + " columns...");
        }
        Region shape = region.clone(); // the player may change their selection while this runs
        WorldArea.snapshot(this, bukkitWorld, lo.add(-margin, -up, -margin), hi.add(margin, up, margin)).whenComplete((snapshots, error) -> {
            if (error != null) { // main thread
                busy.remove(who);
                Throwable cause = error instanceof java.util.concurrent.CompletionException && error.getCause() != null ? error.getCause() : error;
                if (cause instanceof IllegalArgumentException) {
                    error(sender, cause.getMessage());
                } else {
                    error(sender, FAILED);
                    getLogger().log(java.util.logging.Level.WARNING, "Reading the area failed", cause);
                }
                return;
            }
            smoothAsync(sender, who, region, session, actor, snapshots, shape, opts, useTable, allTable, packNote, all, columns, start);
        });
    }

    private void smoothAsync(CommandSender sender, UUID who, Region region, LocalSession session, com.sk89q.worldedit.entity.Player actor,
                             WorldArea.Snapshots snapshots, Region shape, Options opts, Table useTable, Table allTable, String packNote,
                             boolean all, long columns, long start) {
        long mainThreadMs = snapshots.copyMs();
        BlockVector3 lo = shape.getMinimumPoint(), hi = shape.getMaximumPoint();
        int midY = (lo.y() + hi.y()) / 2;
        // A box (most selections) needs no lookup per block.
        WorldArea.CellTest inBox = (x, y, z) -> x >= lo.x() && x <= hi.x() && y >= lo.y() && y <= hi.y() && z >= lo.z() && z <= hi.z();
        WorldArea.CellTest inShape = shape instanceof CuboidRegion ? inBox : (x, y, z) -> shape.contains(BlockVector3.at(x, y, z));
        Bukkit.getScheduler().runTaskAsynchronously(this, () -> {
            WorldArea area;
            Smoother.Result result;
            try {
                area = snapshots.read();
                boolean[] inside = all
                        ? area.cells(inShape)
                        : area.columns((x, y, z) -> inShape.test(x, midY, z));
                result = area.smooth(useTable, opts, inside, all);
            } catch (RuntimeException e) {
                getLogger().log(java.util.logging.Level.WARNING, "Smoothing failed", e);
                onMainThread(() -> {
                    busy.remove(who);
                    snapshots.release().run();
                    error(sender, FAILED);
                });
                return;
            }
            onMainThread(() -> {
                busy.remove(who);
                if (result == null) {
                    snapshots.release().run();
                    error(sender, "The selection is outside the world's height.");
                    return;
                }
                EditSession edit = session != null ? session.createEditSession(actor)
                        : WorldEdit.getInstance().newEditSessionBuilder().world(region.getWorld()).build();
                try {
                    long writeStart = System.nanoTime();
                    int changed = area.write(edit, result, this::parseState);
                    long ms = (System.nanoTime() - start) / 1_000_000;
                    if (columns > 4096) {
                        getLogger().info(sender.getName() + " smoothed " + columns + " columns: " + changed + " blocks in "
                                + ms + " ms, of which " + (mainThreadMs + (System.nanoTime() - writeStart) / 1_000_000)
                                + " ms on the main thread");
                    }
                    info(sender, summary(result.report(), changed, all) + packNote + " (" + ms + " ms)"
                            + (session != null && changed > 0 ? ". //undo reverts it." : ".")
                            + (changed == 0 ? blocksHint(area.palette, useTable, allTable) : ""));
                } catch (MaxChangedBlocksException e) {
                    error(sender, "Too many blocks for your WorldEdit limit; partly done, //undo reverts it.");
                } finally {
                    edit.close();
                    if (session != null) {
                        session.remember(edit);
                    }
                    snapshots.release().run(); // after close(), which writes what WorldEdit buffered
                }
            });
        });
    }

    /** Runs task on the main thread, unless the plugin has been disabled in the meantime. */
    private void onMainThread(Runnable task) {
        if (isEnabled()) {
            try {
                Bukkit.getScheduler().runTask(this, task);
            } catch (org.bukkit.plugin.IllegalPluginAccessException ignored) {
                // disabled between the check and the call
            }
        }
    }

    private static String summary(Smoother.Report report, int blocks, boolean all) {
        String unit = all ? "surfaces" : "columns";
        if (blocks == 0) {
            return "Nothing to change: " + report.freeColumns() + " " + unit + " could change, none needed to.";
        }
        Map<String, Integer> byKind = new TreeMap<>();
        report.pieces().forEach((k, n) -> byKind.merge(k.substring(k.indexOf(' ') + 1), n, Integer::sum));
        List<String> parts = new ArrayList<>();
        byKind.forEach((kind, n) -> parts.add(n + " " + switch (kind) {
            case "slab" -> "slabs";
            case "top slab" -> "top slabs";
            case "vslab" -> "vertical slabs";
            case "vstairs" -> "vertical stairs";
            default -> kind;
        }));
        return "Smoothed " + report.changedColumns() + " of " + report.freeColumns() + " " + unit + ", " + blocks + " blocks"
                + (parts.isEmpty() ? "" : ": " + String.join(", ", parts))
                + (report.plantsRemoved() > 0 ? ", " + report.plantsRemoved() + " plants removed" : "");
    }

    private void brush(CommandSender sender, String[] args) throws InvalidToolBindException {
        if (!(sender instanceof Player player)) {
            error(sender, "Only players can hold a brush.");
            return;
        }
        double radius = args.length > 1 ? Double.parseDouble(args[1]) : 5;
        if (radius < 1 || radius > maxBrushRadius) {
            throw new IllegalArgumentException("Radius must be between 1 and " + maxBrushRadius + ".");
        }
        ItemStack item = player.getInventory().getItemInMainHand();
        if (item.getType().isAir() || item.getType().isBlock()) {
            error(sender, "Hold a tool or other non-block item (e.g. a stick or feather) to bind the brush to.");
            return;
        }
        var actor = BukkitAdapter.adapt(player);
        LocalSession session = WorldEdit.getInstance().getSessionManager().get(actor);
        BrushTool tool = session.getBrushTool(BukkitAdapter.asItemType(item.getType()));
        tool.setBrush(new SmoothBrush(this, player.getUniqueId()), BRUSH); // WorldEdit checks it at every click
        tool.setSize(radius);
        info(sender, "SlabSmooth brush (radius " + fmt(radius) + ") bound to " + item.getType().getKey().getKey()
                + ". Right-click terrain to smooth it; //mask and //undo work as usual.");
    }

    private void pack(CommandSender sender, String[] args) {
        if (!packs.dynamic()) {
            info(sender, "Only one material table here: " + packs.describe() + ".");
            return;
        }
        UUID who = key(sender);
        if (args.length > 1) {
            if (args[1].equalsIgnoreCase("auto")) {
                packChoice.remove(who);
            } else {
                String name = packs.names().stream().filter(n -> n.equalsIgnoreCase(args[1])).findFirst()
                        .orElseThrow(() -> new IllegalArgumentException("No pack " + args[1] + "; packs: " + String.join(", ", packs.names())));
                packChoice.put(who, name);
            }
        }
        String set = packChoice.get(who);
        Packs.Choice here = sender instanceof Player p ? packs.choose(p, p.getLocation(), set) : packs.choose(null, null, set);
        info(sender, (set == null ? "Pack: auto (the RP region's, else your pack, else " + packs.defaultPack() + ")" : "Pack: " + set)
                + ". Here: " + here.pack() + " (" + here.why() + ").");
    }

    private void packs(CommandSender sender, String[] args) {
        if (args.length > 1 && args[1].equalsIgnoreCase("rebuild")) {
            if (!sender.hasPermission(ADMIN)) {
                error(sender, "You need " + ADMIN + " for that.");
                return;
            }
            List<String> names = args.length > 2 ? List.of(args[2]) : packs.names();
            names.forEach(packs::rebuild);
            info(sender, "Re-reading " + String.join(", ", names) + "; /slabsmooth packs shows how it goes.");
            return;
        }
        info(sender, "Materials: " + packs.describe() + ".");
        packs.status().forEach(line -> info(sender, " " + line));
    }

    private void set(CommandSender sender, String[] args) {
        if (args.length < 3) {
            throw new IllegalArgumentException("Usage: set <option> <value>; options: " + String.join(", ", Options.NAMES));
        }
        Options opts = options(key(sender)).copy();
        opts.set(args[1], args[2]);
        options.put(key(sender), opts);
        info(sender, args[1] + " = " + opts.values().get(args[1]) + " (also for your brush)");
    }

    private void showOptions(CommandSender sender) {
        Options opts = options(key(sender));
        Map<String, String> d = defaults.values();
        List<String> parts = new ArrayList<>();
        opts.values().forEach((k, v) -> parts.add(v.equals(d.get(k)) ? k + "=" + v : k + "=" + v + "*"));
        info(sender, "Your settings (* = changed): " + String.join(", ", parts));
    }

    private static String fmt(double v) {
        return v == Math.rint(v) ? String.valueOf((long) v) : String.valueOf(v);
    }

    private static void info(CommandSender sender, String message) {
        sender.sendMessage(Component.text(message, NamedTextColor.YELLOW));
    }

    private static void error(CommandSender sender, String message) {
        sender.sendMessage(Component.text(message, NamedTextColor.RED));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> options;
        if (args.length == 1) {
            options = SUBCOMMANDS.stream().filter(s -> !SUBCOMMAND_PERMISSION.containsKey(s) || sender.hasPermission(SUBCOMMAND_PERMISSION.get(s))).toList();
        } else if (args.length == 2 && args[0].equalsIgnoreCase("packs")) {
            options = sender.hasPermission(ADMIN) ? List.of("rebuild") : List.of();
        } else if (args.length == 2 && args[0].equalsIgnoreCase("set")) {
            options = Options.NAMES;
        } else if (args.length == 3 && args[0].equalsIgnoreCase("set")) {
            options = switch (args[1]) {
                case "mode" -> List.of("both", "cut", "fill");
                case "plants" -> List.of("remove", "keep");
                case "blocks" -> List.of("terrain", "all");
                case "surfaces" -> List.of("all", "top");
                default -> List.of(defaults.values().getOrDefault(args[1], ""));
            };
        } else if (args[0].equalsIgnoreCase("area") && !sender.hasPermission(AREA)) {
            return List.of();
        } else if (args.length == 8 && args[0].equalsIgnoreCase("area")) {
            options = Bukkit.getWorlds().stream().map(World::getName).toList();
        } else if ((args.length == 9 && args[0].equalsIgnoreCase("area")) || (args.length == 2 && args[0].equalsIgnoreCase("pack"))) {
            List<String> names = new ArrayList<>(packs.names());
            if (args[0].equalsIgnoreCase("pack")) {
                names.add("auto");
            }
            options = names;
        } else if (args.length == 3 && args[0].equalsIgnoreCase("packs") && args[1].equalsIgnoreCase("rebuild") && sender.hasPermission(ADMIN)) {
            options = packs.names();
        } else {
            return List.of();
        }
        String prefix = args[args.length - 1].toLowerCase();
        return options.stream().filter(o -> o.toLowerCase().startsWith(prefix)).toList();
    }

    @Override
    public void onDisable() {
        options.clear();
        if (packs != null) {
            packs.close();
        }
    }
}
