package com.mcmiddleearth.architect.biomeTuning;

import com.mcmiddleearth.architect.Permission;
import com.mcmiddleearth.architect.PluginData;
import com.mcmiddleearth.architect.additionalCommands.AbstractArchitectCommand;
import com.mcmiddleearth.architect.biomeTuning.TuningProperty.Group;
import io.papermc.paper.connection.PlayerConnection;
import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Biome;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

/**
 * /biometune: tune a datapack biome's atmosphere live. "/biometune [edit] [biome]" opens the Dialog editor; edits go
 * through BiomeTuningService, and refreshing clients goes through RefreshCoordinator. spares, claim and label look
 * after the spare biomes and the labels. spares add, selftest and debug are for admins.
 */
public class BiomeTuneCommand extends AbstractArchitectCommand {

    private static final List<String> SUBCOMMANDS = List.of("edit", "info", "set", "unset", "copy", "undo", "revert",
            "save", "status", "preview", "publish", "spares", "claim", "label", "selftest", "debug");
    /**
     * The first word's completions without a colon: a subcommand, or here, which opens the editor on the biome at your
     * feet. A first word with a colon is a biome id, completed from the registry.
     */
    private static final List<String> FIRST_WORDS = Stream.concat(SUBCOMMANDS.stream(), Stream.of("here")).toList();
    /** The groups copy takes: every group of a biome's look, in lower case, then all. */
    private static final List<String> GROUPS = Stream.concat(
            Group.LOOK.stream().map(group -> group.name().toLowerCase(Locale.ROOT)), Stream.of("all")).toList();

    @Override
    public boolean onCommand(@NotNull CommandSender cs, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (args.length == 0 && !(cs instanceof Player)) {
            PluginData.getMessageUtil().sendErrorMessage(cs, "Usage: " + getHelpCommand());
            return true;
        }
        if (args.length == 0 || args[0].equalsIgnoreCase("here")
                || (args[0].contains(":") && !SUBCOMMANDS.contains(args[0].toLowerCase(Locale.ROOT)))) {
            args = withEdit(args); // "/biometune", "/biometune here" and "/biometune <biome>" open the editor
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        if (!SUBCOMMANDS.contains(sub)) {
            PluginData.getMessageUtil().sendInvalidSubcommandError(cs);
            return true;
        }
        if (!PluginData.hasPermission(cs, permissionFor(sub, args))) {
            PluginData.getMessageUtil().sendNoPermissionError(cs);
            return true;
        }
        if (!BiomeTuning.isAvailable()) {
            PluginData.getMessageUtil().sendErrorMessage(cs, "Biome tuning is unavailable on this server version: "
                    + BiomeTuning.problems().get(0));
            return true;
        }
        try {
            switch (sub) {
                case "edit" -> edit(cs, args);
                case "info" -> info(cs, args);
                case "set" -> set(cs, args);
                case "unset" -> unset(cs, args);
                case "copy" -> copy(cs, args);
                case "undo" -> undo(cs, args);
                case "revert" -> revert(cs, args);
                case "save" -> save(cs, args);
                case "status" -> status(cs);
                case "preview" -> preview(cs, args);
                case "publish" -> publish(cs, args);
                case "spares" -> spares(cs, args);
                case "claim" -> claim(cs, args);
                case "label" -> label(cs, args);
                case "selftest" -> selftest(cs, args);
                default -> debug(cs, args);
            }
        } catch (BiomeTuningException e) {
            PluginData.getMessageUtil().sendErrorMessage(cs, e.getMessage());
        }
        return true;
    }

    static Permission permissionFor(String sub, String[] args) {
        return switch (sub) {
            case "save" -> Permission.BIOME_TUNE_SAVE;
            case "publish" -> Permission.BIOME_TUNE_PUBLISH;
            case "preview" -> args.length > 1 ? Permission.BIOME_TUNE_PUBLISH : Permission.BIOME_TUNE;
            case "spares" -> args.length > 1 && args[1].equalsIgnoreCase("add") ? Permission.BIOME_TUNE_ADMIN
                    : Permission.BIOME_TUNE;
            case "selftest", "debug" -> Permission.BIOME_TUNE_ADMIN;
            default -> Permission.BIOME_TUNE;
        };
    }

    private void edit(CommandSender cs, String[] args) throws BiomeTuningException {
        if (!(cs instanceof Player player)) {
            throw new BiomeTuningException("The editor opens in the game; from the console use /biometune info or set");
        }
        BiomeTuning.editor().open(player, biome(cs, args.length > 1 ? args[1] : "here"));
    }

    private void info(CommandSender cs, String[] args) throws BiomeTuningException {
        NamespacedKey biome = biome(cs, args.length > 1 ? args[1] : "here");
        BiomeDocument doc = BiomeTuning.service().document(biome);
        SparePool pool = BiomeTuning.spares();
        String label = null;
        ChatFeedback.NoLabel noLabel = null;
        String labelsProblem = null;
        try {
            label = pool.labels().label(biome).orElse(null);
            noLabel = pool.isFreeSpare(biome) ? ChatFeedback.NoLabel.FREE_SPARE : null;
        } catch (BiomeTuningException e) {
            labelsProblem = e.getMessage(); // a broken labels file hides the label, not the values
            noLabel = ChatFeedback.NoLabel.UNREADABLE;
        }
        ChatFeedback.info(doc, label, noLabel).forEach(cs::sendMessage);
        if (labelsProblem != null) {
            PluginData.getMessageUtil().sendErrorMessage(cs, labelsProblem);
        }
    }

    private void set(CommandSender cs, String[] args) throws BiomeTuningException {
        requireArgs(args, 4, "/biometune set <biome> <property> <value>");
        NamespacedKey biome = biome(cs, args[1]);
        String value = String.join(" ", Arrays.copyOfRange(args, 3, args.length));
        cs.sendMessage(ChatFeedback.change(BiomeTuning.service().set(biome, args[2], value)));
    }

    private void unset(CommandSender cs, String[] args) throws BiomeTuningException {
        requireArgs(args, 3, "/biometune unset <biome> <property>");
        cs.sendMessage(ChatFeedback.change(BiomeTuning.service().unset(biome(cs, args[1]), args[2])));
    }

    private void copy(CommandSender cs, String[] args) throws BiomeTuningException {
        requireArgs(args, 3, "/biometune copy <from> <to> [sky|fog|water|climate|audio|particles|all]");
        Set<Group> groups = groups(Arrays.copyOfRange(args, 3, args.length));
        List<BiomeTuningService.Change> changes = BiomeTuning.service().copy(biome(cs, args[1]), biome(cs, args[2]), groups);
        if (changes.isEmpty()) {
            PluginData.getMessageUtil().sendInfoMessage(cs, "Nothing to copy: those values are already the same.");
        }
        changes.forEach(change -> cs.sendMessage(ChatFeedback.change(change)));
    }

    private void undo(CommandSender cs, String[] args) throws BiomeTuningException {
        requireArgs(args, 2, "/biometune undo <biome>");
        NamespacedKey biome = biome(cs, args[1]);
        if (BiomeTuning.service().undo(biome)) {
            PluginData.getMessageUtil().sendInfoMessage(cs, "Undid the last change to " + biome + " (live; preview to see it).");
        } else {
            PluginData.getMessageUtil().sendErrorMessage(cs, "Nothing to undo for " + biome + ".");
        }
    }

    private void revert(CommandSender cs, String[] args) throws BiomeTuningException {
        requireArgs(args, 2, "/biometune revert <biome>");
        NamespacedKey biome = biome(cs, args[1]);
        BiomeTuning.service().revert(biome);
        PluginData.getMessageUtil().sendInfoMessage(cs, biome + " is back to its saved file (live; preview to see it).");
    }

    private void save(CommandSender cs, String[] args) throws BiomeTuningException {
        requireArgs(args, 2, "/biometune save <biome>");
        NamespacedKey biome = biome(cs, args[1]);
        if (!BiomeTuning.service().document(biome).isUnsaved()) {
            PluginData.getMessageUtil().sendInfoMessage(cs, biome + " has no unsaved changes.");
            return;
        }
        List<String> changes = BiomeTuning.service().save(biome, cs.getName());
        PluginData.getMessageUtil().sendInfoMessage(cs, "Saved " + biome + ": "
                + (changes.isEmpty() ? "other values" : String.join(", ", changes)));
    }

    private void status(CommandSender cs) {
        List<NamespacedKey> unsaved = BiomeTuning.service().unsaved();
        if (unsaved.isEmpty()) {
            PluginData.getMessageUtil().sendInfoMessage(cs, "No unsaved biome changes on this server.");
            return;
        }
        Map<NamespacedKey, BiomeLabels.Entry> labels;
        try {
            labels = BiomeTuning.spares().labels().all();
        } catch (BiomeTuningException e) {
            labels = Map.of(); // a broken labels file hides the labels, not the list
        }
        PluginData.getMessageUtil().sendInfoMessage(cs, "Unsaved biome changes (live until the next restart):");
        for (NamespacedKey biome : unsaved) {
            BiomeLabels.Entry entry = labels.get(biome);
            cs.sendMessage(ChatFeedback.unsavedLine(biome, entry == null ? null : entry.label()));
        }
    }

    private void preview(CommandSender cs, String[] args) {
        Player target = args.length > 1 ? Bukkit.getPlayerExact(args[1]) : cs instanceof Player player ? player : null;
        if (target == null) {
            PluginData.getMessageUtil().sendErrorMessage(cs, args.length > 1 ? "No online player named " + args[1]
                    : "From the console, name a player: /biometune preview <player>");
            return;
        }
        RefreshCoordinator refresher = BiomeTuning.refresher();
        if (refresher.isRefreshing(target.getUniqueId())) {
            PluginData.getMessageUtil().sendErrorMessage(cs, target.getName() + " is already being refreshed.");
            return;
        }
        long wait = refresher.previewCooldownLeft(target.getUniqueId());
        if (wait > 0) {
            PluginData.getMessageUtil().sendErrorMessage(cs, "Wait " + ((wait + 999) / 1000) + " s before the next preview.");
            return;
        }
        // Chat goes out before the refresh: once a player is reconfiguring, messages cannot reach them.
        PluginData.getMessageUtil().sendInfoMessage(cs, "Refreshing " + target.getName() + "...");
        report(cs, target.getName(), refresher.preview(target));
    }

    private void publish(CommandSender cs, String[] args) {
        Collection<? extends Player> players;
        if (args.length > 1 && args[1].equalsIgnoreCase("server")) {
            players = Bukkit.getOnlinePlayers();
        } else if (cs instanceof Player player) {
            players = player.getWorld().getPlayers();
        } else {
            PluginData.getMessageUtil().sendErrorMessage(cs, "From the console, use /biometune publish server");
            return;
        }
        Component announcement = ChatFeedback.publishAnnouncement(cs instanceof Player ? cs.getName() : "the server");
        if (!BiomeTuning.refresher().publish(players, announcement)) {
            PluginData.getMessageUtil().sendErrorMessage(cs, "A publish ran moments ago; try again in a little while.");
            return;
        }
        PluginData.getMessageUtil().sendInfoMessage(cs, "Refreshing " + players.size() + " player(s), "
                + BiomeTuning.settings().publishPlayersPerSecond() + " per second.");
    }

    private void spares(CommandSender cs, String[] args) throws BiomeTuningException {
        SparePool pool = BiomeTuning.spares();
        if (args.length > 1) {
            if (!args[1].equalsIgnoreCase("add") || args.length != 3) {
                throw new BiomeTuningException("Usage: /biometune spares [add <count>]");
            }
            addSpares(cs, pool, args[2]);
            return;
        }
        Map<NamespacedKey, String> claimed = new LinkedHashMap<>();
        for (NamespacedKey spare : pool.spares()) {
            Optional<BiomeLabels.Entry> entry = pool.labels().entry(spare);
            if (entry.isPresent() && entry.get().spare()) {
                claimed.put(spare, entry.get().label());
            }
        }
        List<NamespacedKey> listed = new ArrayList<>(claimed.keySet());
        listed.addAll(pool.free()); // what a claim can take, in the order it takes them
        ChatFeedback.spares(listed, claimed).forEach(cs::sendMessage);
    }

    private void addSpares(CommandSender cs, SparePool pool, String count) throws BiomeTuningException {
        List<NamespacedKey> added;
        try {
            added = pool.add(Integer.parseInt(count), cs.getName());
        } catch (NumberFormatException e) {
            throw new BiomeTuningException("Usage: /biometune spares add <count>, a count of 1 to " + SparePool.ADD_LIMIT);
        }
        PluginData.getMessageUtil().sendInfoMessage(cs, "Wrote " + SparePool.range(added) + " into the pack "
                + BiomeTuning.settings().targetPack() + "; new spares exist after the next restart.");
    }

    /** "claim <label> [from <biome>]": the label is every word before a closing "from <biome>", spaces and all. */
    private void claim(CommandSender cs, String[] args) throws BiomeTuningException {
        requireArgs(args, 2, "/biometune claim <label> [from <biome>]");
        if (args[args.length - 1].equalsIgnoreCase("from")) {
            throw new BiomeTuningException("Say which biome to start from: /biometune claim <label> from <biome>. "
                    + "A label can't end in 'from'.");
        }
        int end = args.length;
        String source = null;
        if (args.length >= 3 && args[args.length - 2].equalsIgnoreCase("from")) {
            source = args[args.length - 1];
            end = args.length - 2;
        }
        if (end == 1) { // a claim is for good, so it never goes through without a name
            throw new BiomeTuningException("Name the new biome first: /biometune claim <label> [from <biome>]");
        }
        NamespacedKey from = source == null ? null : biome(cs, source);
        String label = String.join(" ", Arrays.copyOfRange(args, 1, end));
        SparePool.Claim claim = BiomeTuning.spares().claim(label, from,
                cs instanceof Player player ? player.getUniqueId() : null, cs.getName());
        ChatFeedback.claimed(claim, from).forEach(cs::sendMessage);
    }

    private void label(CommandSender cs, String[] args) throws BiomeTuningException {
        requireArgs(args, 3, "/biometune label <biome> <label>");
        NamespacedKey biome = biome(cs, args[1]);
        String label = BiomeTuning.spares().label(biome, String.join(" ", Arrays.copyOfRange(args, 2, args.length)),
                cs.getName());
        PluginData.getMessageUtil().sendInfoMessage(cs, "Labelled " + biome + " '" + label + "'.");
    }

    private void selftest(CommandSender cs, String[] args) throws BiomeTuningException {
        PluginData.getMessageUtil().sendInfoMessage(cs, "Every internal handle resolved.");
        if (args.length < 2) {
            return;
        }
        NamespacedKey key = biome(cs, args[1]);
        NmsBridge bridge = BiomeTuning.bridge();
        bridge.apply(key, bridge.decode(bridge.encode(key)));
        PluginData.getMessageUtil().sendInfoMessage(cs, "Round trip OK for " + key + ": encoded, decoded and swapped back unchanged.");
    }

    private void debug(CommandSender cs, String[] args) {
        String action = args.length > 1 ? args[1].toLowerCase(Locale.ROOT) : "";
        if (action.equals("legacylogin")) {
            boolean state = BiomeTuning.setLegacyLoginProbe(args.length > 2 && args[2].equalsIgnoreCase("on"));
            PluginData.getMessageUtil().sendInfoMessage(cs, "PlayerLoginEvent probe is now " + (state ? "ON" : "OFF")
                    + (state ? "; players must rejoin before Paper marks their connection." : "."));
            return;
        }
        Player target = action.equals("refresh") && args.length > 3 ? Bukkit.getPlayerExact(args[2]) : null;
        String mode = args.length > 3 ? args[3].toLowerCase(Locale.ROOT) : "";
        if (target == null || !(mode.equals("noinject") || mode.equals("bypass"))) {
            PluginData.getMessageUtil().sendErrorMessage(cs,
                    "Usage: /biometune debug refresh <online player> noinject|bypass | debug legacylogin on|off");
            return;
        }
        PluginData.getMessageUtil().sendInfoMessage(cs, "Refreshing " + target.getName()
                + (mode.equals("noinject") ? " without the registry sync..." : " (bypassing Paper's login guard)..."));
        try {
            report(cs, target.getName(), mode.equals("noinject")
                    ? BiomeTuning.refresher().refresh(target, false)
                    : BiomeTuning.refresher().refresh(target, true, BiomeTuneCommand::switchIgnoringLoginGuard));
        } catch (IllegalStateException e) {
            PluginData.getMessageUtil().sendErrorMessage(cs, "Refresh failed: " + e.getMessage());
        }
    }

    private static void report(CommandSender cs, String name, RefreshCoordinator.Outcome outcome) {
        if (outcome == RefreshCoordinator.Outcome.REFUSED) {
            PluginData.getMessageUtil().sendErrorMessage(cs, "Paper refused to reconfigure " + name + ": a plugin on "
                    + "this server listens to PlayerLoginEvent (see the log). Rejoin to see the live values.");
        }
    }

    private static PlayerConnection switchIgnoringLoginGuard(Player player) {
        try {
            BiomeTuning.bridge().switchToConfigurationIgnoringLoginGuard(player);
        } catch (BiomeTuningException e) {
            throw new IllegalStateException(e.getMessage(), e);
        }
        return player.getConnection();
    }

    /** A biome id, or "here" for the biome at the player's feet. */
    private static NamespacedKey biome(CommandSender cs, String arg) throws BiomeTuningException {
        if (arg.equalsIgnoreCase("here")) {
            if (!(cs instanceof Player player)) {
                throw new BiomeTuningException("From the console, name the biome (for example cbc:111g380-5p)");
            }
            Biome biome = player.getLocation().getBlock().getBiome();
            NamespacedKey key = RegistryAccess.registryAccess().getRegistry(RegistryKey.BIOME).getKey(biome);
            if (key == null) {
                throw new BiomeTuningException("Could not tell which biome you are standing in");
            }
            return key;
        }
        NamespacedKey key = NamespacedKey.fromString(arg.toLowerCase(Locale.ROOT));
        if (key == null) {
            throw new BiomeTuningException("Not a biome id: " + arg);
        }
        return key;
    }

    private static String[] withEdit(String[] args) {
        String[] words = new String[args.length + 1];
        words[0] = "edit";
        System.arraycopy(args, 0, words, 1, args.length);
        return words;
    }

    private static Set<Group> groups(String[] names) throws BiomeTuningException {
        Set<Group> all = Group.LOOK;
        if (names.length == 0) {
            return all;
        }
        Set<Group> groups = EnumSet.noneOf(Group.class);
        for (String name : names) {
            String lower = name.toLowerCase(Locale.ROOT);
            if (!GROUPS.contains(lower)) {
                throw new BiomeTuningException("Unknown group '" + name + "': use sky, fog, water, climate, audio, particles or all");
            }
            if (lower.equals("all")) {
                groups.addAll(all);
            } else {
                groups.add(Group.valueOf(lower.toUpperCase(Locale.ROOT)));
            }
        }
        return groups;
    }

    private static void requireArgs(String[] args, int count, String usage) throws BiomeTuningException {
        if (args.length < count) {
            throw new BiomeTuningException("Usage: " + usage);
        }
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, Command command, String alias, String[] args) {
        String sub = args[0].toLowerCase(Locale.ROOT);
        List<String> options = switch (args.length) {
            case 1 -> args[0].contains(":") ? biomes(false) : FIRST_WORDS; // a biome id opens the editor
            case 2 -> switch (sub) {
                case "edit", "info", "set", "unset", "undo", "revert", "save", "label", "selftest" -> biomes(false);
                case "copy" -> biomes(true);
                case "preview" -> playerNames();
                case "publish" -> List.of("world", "server");
                case "spares" -> List.of("add");
                case "debug" -> List.of("refresh", "legacylogin");
                default -> List.<String>of();
            };
            case 3 -> switch (sub) {
                case "set", "unset" -> TuningProperties.names();
                case "copy" -> biomes(false);
                case "claim" -> args[1].equalsIgnoreCase("from") ? List.<String>of() : List.of("from");
                case "debug" -> args[1].equalsIgnoreCase("legacylogin") ? List.of("on", "off") : playerNames();
                default -> List.<String>of();
            };
            default -> switch (sub) {
                case "set" -> args.length == 4 ? valueHints(args[2]) : List.<String>of();
                case "copy" -> GROUPS;
                case "claim" -> args[args.length - 2].equalsIgnoreCase("from") ? biomes(true) : List.of("from");
                case "debug" -> args.length == 4 ? List.of("noinject", "bypass") : List.<String>of();
                default -> List.<String>of();
            };
        };
        String last = args[args.length - 1].toLowerCase(Locale.ROOT);
        return options.stream().filter(option -> option.toLowerCase(Locale.ROOT).startsWith(last)).toList();
    }

    private static List<String> biomes(boolean includeVanilla) {
        List<String> keys = new ArrayList<>();
        keys.add("here");
        RegistryAccess.registryAccess().getRegistry(RegistryKey.BIOME).keyStream()
                .filter(key -> includeVanilla || !key.getNamespace().equals(NamespacedKey.MINECRAFT))
                .map(NamespacedKey::toString)
                .sorted()
                .forEach(keys::add);
        return keys;
    }

    private static List<String> playerNames() {
        return Bukkit.getOnlinePlayers().stream().map(Player::getName).toList();
    }

    private static List<String> valueHints(String propertyName) {
        return TuningProperties.resolve(propertyName).map(property -> switch (property.kind()) {
            case CHOICE -> property.choices();
            case BOOLEAN -> List.of("true", "false");
            case RGB -> List.of("#78a7ff");
            case ARGB -> List.of("#ff78a7ff");
            default -> List.<String>of();
        }).orElse(List.of());
    }

    @Override
    public String getHelpPermission() {
        return Permission.BIOME_TUNE.getPermissionNode();
    }

    @Override
    public String getShortDescription() {
        return ": Tune biome colours and atmosphere live.";
    }

    @Override
    public String getUsageDescription() {
        return ": Change biome sky, fog, water, climate, sound and particles without a restart; save them to the datapack. "
                + "Claim spare biomes for new looks, and label biomes.";
    }

    @Override
    public String getHelpCommand() {
        return "/biometune [edit] [biome] | info|set|unset|copy|undo|revert|save|status|preview|publish|spares|claim|label ...";
    }
}
