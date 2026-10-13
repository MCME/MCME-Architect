package com.mcmiddleearth.architect.biomeTuning;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonPrimitive;
import com.mcmiddleearth.architect.Permission;
import com.mcmiddleearth.architect.biomeTuning.ColourMath.Hsb;
import com.mcmiddleearth.architect.biomeTuning.EditorLayout.Field;
import com.mcmiddleearth.architect.biomeTuning.EditorLayout.Section;
import com.mcmiddleearth.architect.biomeTuning.TuningProperty.Group;
import com.mcmiddleearth.architect.biomeTuning.TuningProperty.Kind;
import com.mcmiddleearth.pluginutil.message.MessageUtil;
import io.papermc.paper.event.player.PlayerClientLoadedWorldEvent;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.LongSupplier;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * The Dialog editor: a home window per biome, six sections, a colour picker, a JSON editor, and windows to
 * copy, publish, revert and claim a new biome. Every button checks its permission again when clicked, then shows the
 * next window, closes, or refreshes the player; Close and Escape always just close. A window whose value changed
 * after the builder saw it writes nothing: it is drawn again with the live value, and a section keeps the builder's
 * values on screen to apply on purpose. The editor remembers which window each player has open (Close, Escape, a
 * death and a world change forget it), so after any refresh - a preview, a publish, someone else's publish - that
 * window comes back once the client has loaded the world. Players are tracked by UUID only, because a refresh
 * replaces the Player object. Main thread only.
 */
public final class BiomeEditor implements Listener {

    /** How long after a refresh starts the window may still come back by itself. */
    static final long REOPEN_WINDOW_MILLIS = 120_000;
    /**
     * How long a window stays open before the editor closes it (its buttons work a minute longer). An older window is
     * not brought back.
     */
    static final long WINDOW_LIFETIME_MILLIS = 30 * 60_000;
    /** The longest JSON the editor's text box takes; longer values are changed with /biometune set. */
    static final int JSON_LIMIT = 8192;
    /** The longest biome id the copy windows' box takes. */
    static final int ID_LIMIT = 100;
    /** Colours the game requires (the codec's fieldOf), so the picker offers no Clear for them. */
    private static final Set<String> REQUIRED = Set.of("water_color");
    private static final Gson PRETTY = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final List<String> SWATCHES = List.of("sky_color", "fog_color", "water_color", "grass_color",
            "foliage_color");
    private static final String REFRESH_TIP = "Refresh your own view to see the live values (1-3 s)";

    /** A window that can be built again for the player who clicked, with a notice (a result or an error) first. */
    @FunctionalInterface
    private interface Screen {
        EditorView build(Player viewer, Component notice) throws BiomeTuningException;
    }

    /** What a button does: returns the next window, or null when it closed the window or started a refresh. */
    @FunctionalInterface
    private interface Step {
        EditorView run(Player player, EditorView.Answers answers) throws BiomeTuningException;
    }

    /** The window a player has on screen, and when it was shown. */
    private record Shown(Screen screen, long at) {
    }

    /** A window to bring back once the refreshed client has loaded the world, if that happens by {@code until}. */
    private record Reopen(Screen screen, long until) {
    }

    private final BiomeTuningService service;
    private final SparePool spares;
    private final RefreshCoordinator refresher;
    private final LongSupplier clock;
    private final BiomeTuningSettings settings;
    private final Logger logger;
    private final MessageUtil messages;
    private final Map<UUID, Shown> open = new HashMap<>();
    private final Map<UUID, Reopen> reopen = new HashMap<>();
    private DialogPresenter presenter;
    private boolean shutDown;

    /** {@code messages} writes to chat in Architect's own style, as its commands do. */
    public BiomeEditor(BiomeTuningService service, SparePool spares, RefreshCoordinator refresher,
                       DialogPresenter presenter, LongSupplier clock, BiomeTuningSettings settings, Logger logger,
                       MessageUtil messages) {
        this.service = service;
        this.spares = spares;
        this.refresher = refresher;
        this.presenter = presenter;
        this.clock = clock;
        this.settings = settings;
        this.logger = logger;
        this.messages = messages;
    }

    /** Tests record windows instead of showing them. */
    void presentWith(DialogPresenter presenter) {
        this.presenter = presenter;
    }

    /**
     * Opens the editor on {@code biome}; fails with the reason when the biome cannot be tuned. Anything unexpected
     * closes the window and says so, as a click does.
     */
    public void open(Player player, NamespacedKey biome) throws BiomeTuningException {
        try {
            presenter.show(player, home(player, biome, null));
        } catch (RuntimeException e) {
            failed(player, e);
        }
    }

    /** Old windows stop working: their buttons ask the player to open the editor again. */
    public void shutdown() {
        shutDown = true;
        open.clear();
        reopen.clear();
    }

    /**
     * Closes windows open for {@link #WINDOW_LIFETIME_MILLIS}, whose buttons are about to stop working, and says so.
     * The plugin's one-second timer calls this.
     */
    public void closeExpired() {
        long now = clock.getAsLong();
        List<UUID> expired = open.entrySet().stream()
                .filter(entry -> now - entry.getValue().at() >= WINDOW_LIFETIME_MILLIS)
                .map(Map.Entry::getKey)
                .toList();
        for (UUID id : expired) {
            open.remove(id);
            Player player = Bukkit.getPlayer(id);
            if (player != null) {
                presenter.close(player);
                messages.sendInfoMessage(player, "The editor closed after " + WINDOW_LIFETIME_MILLIS / 60_000
                        + " minutes: open it again with /biometune.");
            }
        }
    }

    /** Brings back the window a refresh took away, if it is recent and the player may still use the editor. */
    @EventHandler
    public void onClientLoaded(PlayerClientLoadedWorldEvent event) {
        Player player = event.getPlayer();
        Reopen pending = reopen.remove(player.getUniqueId());
        if (pending != null && !shutDown && clock.getAsLong() <= pending.until()
                && player.hasPermission(Permission.BIOME_TUNE.getPermissionNode())) {
            logger.info("biometune: bringing the editor back for " + player.getName());
            show(player, pending.screen(), null);
        }
    }

    /**
     * A refresh is a quit on the server: an open window is kept to come back. A real quit forgets everything. A
     * refresh that ends in a disconnect (a timeout, a crash) still counts as one: rejoining within
     * {@link #REOPEN_WINDOW_MILLIS} brings the window back.
     */
    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        long now = clock.getAsLong();
        reopen.values().removeIf(entry -> entry.until() < now); // players who never came back
        Shown window = open.remove(id);
        if (!refresher.isRefreshing(id)) {
            reopen.remove(id); // a real quit: the editor must not pop up at their next join
        } else if (window != null && now - window.at() < WINDOW_LIFETIME_MILLIS) {
            reopen.put(id, new Reopen(window.screen(), now + REOPEN_WINDOW_MILLIS));
        }
    }

    /** The death screen replaces the window, so it no longer counts as open; a cancelled death changes nothing. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDeath(PlayerDeathEvent event) {
        open.remove(event.getPlayer().getUniqueId());
    }

    /** A world change puts up the client's loading screen, which replaces the window: it no longer counts as open. */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorldChange(PlayerChangedWorldEvent event) {
        open.remove(event.getPlayer().getUniqueId());
    }

    // ---- windows

    private EditorView home(Player viewer, NamespacedKey biome, Component notice) throws BiomeTuningException {
        BiomeDocument doc = service.document(biome);
        Screen self = (player, n) -> home(player, biome, n);
        List<Component> body = new ArrayList<>();
        body.add(Component.text(heading(biome) + "  ", NamedTextColor.WHITE).append(doc.isUnsaved()
                ? Component.text("● unsaved", NamedTextColor.GOLD) : Component.text("✓ saved", NamedTextColor.GREEN)));
        body.add(swatches(doc));
        body.add(horizon(colourShown(doc, "sky_color"), colourShown(doc, "fog_color")));
        // two lines in either font: home's height budget counts on it
        body.add(Component.text("Edits are live here at once. Players see them after a refresh (Preview in world, "
                + "Publish) or when they join.", NamedTextColor.GRAY));

        List<EditorView.Button> buttons = new ArrayList<>();
        for (Section section : Section.values()) {
            buttons.add(button(section.title(), null, Permission.BIOME_TUNE, self,
                    (player, answers) -> section(player, biome, section, null)));
        }
        buttons.add(button("Preview in world", REFRESH_TIP, Permission.BIOME_TUNE, self,
                (player, answers) -> preview(player, self)));
        if (viewer.hasPermission(Permission.BIOME_TUNE_PUBLISH.getPermissionNode())) {
            buttons.add(button("Publish…", "Refresh other players so they see the live values",
                    Permission.BIOME_TUNE_PUBLISH, self, (player, answers) -> publish(player, biome, null)));
        }
        if (viewer.hasPermission(Permission.BIOME_TUNE_SAVE.getPermissionNode())) {
            buttons.add(button("Save", "Write this biome to the datapack", Permission.BIOME_TUNE_SAVE, self,
                    (player, answers) -> save(player, biome)));
        }
        buttons.add(button("Undo", "Step back one edit", Permission.BIOME_TUNE, self, (player, answers) ->
                home(player, biome, service.undo(biome) ? ok("Undid the last edit (live; preview to see it).")
                        : error("Nothing to undo."))));
        buttons.add(button("Revert…", "Go back to the saved file", Permission.BIOME_TUNE, self,
                (player, answers) -> revert(player, biome, null)));
        buttons.add(button("New biome…", "Claim a spare biome for a new look", Permission.BIOME_TUNE, self,
                (player, answers) -> newBiome(player, biome, "", true, null)));
        // three columns, at most four rows: with its two-line intro, home fits a GUI 240 px tall (854x480 at scale 2,
        // 1280x720 at scale 3) with a notice of up to two lines
        return window(viewer, self, title("Biome editor", biome), notice, body, List.of(), buttons, 3);
    }

    private EditorView section(Player viewer, NamespacedKey biome, Section section, Component notice)
            throws BiomeTuningException {
        return section(viewer, biome, section, Map.of(), notice);
    }

    /**
     * A builder's edit a section keeps on screen after a warning or a refused Apply, with the live value when it was
     * kept, which the next Apply checks against.
     */
    private record Kept(BiomeTuningService.Edit edit, JsonElement seen) {
    }

    /**
     * A section. A field the builder changed whose live value is no longer the one the builder saw (someone else
     * changed it meanwhile) is not written over: an Apply that touches one writes nothing, and draws the window again
     * with a warning, keeping the builder's edits on screen as {@code pending}, each with the live value when the
     * warning was shown. The next Apply writes them, unless one of those values changed again, which warns again; a
     * window that comes back after a refresh keeps them the same way. A kept edit whose field now holds something the
     * window cannot show is dropped, for good. An Apply the game refuses writes nothing either, and keeps that click's
     * edits on screen the same way, below the reason. While the window keeps edits, a line right under the notice says
     * they are not applied yet; an Apply that goes through, or Back, drops them and the line.
     */
    private EditorView section(Player viewer, NamespacedKey biome, Section section, Map<String, Kept> pending,
                               Component notice) throws BiomeTuningException {
        BiomeDocument doc = service.document(biome);
        Map<String, Kept> kept = new LinkedHashMap<>(); // the pending edits still on screen, filled below
        Screen self = (player, n) -> section(player, biome, section, kept, n);
        List<Component> body = new ArrayList<>();
        List<EditorView.Input> inputs = new ArrayList<>();
        List<EditorView.Button> buttons = new ArrayList<>();
        // what the builder saw of each input: its live value, or for a kept edit the live value when it was kept
        Map<TuningProperty, JsonElement> seen = new HashMap<>();
        if (EditorLayout.fields(section).stream().anyMatch(field -> field.property().isColour())) {
            body.add(Component.text("Click a colour to open the picker.", NamedTextColor.GRAY));
        }
        for (Field field : EditorLayout.fields(section)) {
            TuningProperty property = field.property();
            JsonElement value = doc.value(property);
            if (property.isColour()) {
                buttons.add(button(colourLabel(field, value), property.name() + ": " + TuningProperties.display(value),
                        Permission.BIOME_TUNE, self,
                        (player, answers) -> picker(player, biome, property, null, null, null, null)));
            } else if (property.kind() == Kind.JSON) {
                body.add(Component.text(field.label() + ": ", NamedTextColor.GRAY)
                        .append(Component.text(shorten(TuningProperties.display(value)), NamedTextColor.WHITE)));
                buttons.add(button("Edit " + field.label() + "…", "Change it as JSON", Permission.BIOME_TUNE, self,
                        (player, answers) -> json(player, biome, property, null, null, null)));
            } else {
                EditorView.Input input = input(field, value);
                Kept keptEdit = pending.get(property.name());
                EditorView.Input keptInput = input == null || keptEdit == null ? null
                        : input(field, wanted(property, keptEdit.edit()));
                if (input == null) {
                    body.add(Component.text(field.label() + " is " + TuningProperties.display(value)
                            + ": change it with /biometune set.", NamedTextColor.GRAY));
                } else if (keptInput != null) {
                    kept.put(property.name(), keptEdit);
                    inputs.add(keptInput);
                    seen.put(property, keptEdit.seen());
                } else {
                    inputs.add(input);
                    seen.put(property, seenAs(value));
                }
            }
        }
        if (!kept.isEmpty()) {
            body.add(0, notApplied(doc, kept.values())); // first: the window puts the notice above it
        }
        if (section == Section.CLIMATE) {
            body.add(Component.text("Climate also changes the world: where it is cold enough, rain falls as snow, and "
                    + "snow and ice can form (unless the world turns that off). Temperature and downfall also tint "
                    + "grass and leaves whose colours are not set.", NamedTextColor.GOLD));
        }
        if (!inputs.isEmpty()) {
            List<EditorView.Input> form = List.copyOf(inputs);
            buttons.add(button("Apply", "Apply the sliders and switches you changed", Permission.BIOME_TUNE, self,
                    (player, answers) -> {
                        Map<String, BiomeTuningService.Edit> edits = edits(kept, form, answers);
                        List<String> conflicts = conflicts(biome, seen, edits.values());
                        if (!conflicts.isEmpty()) {
                            return section(player, biome, section, keep(biome, edits), outOfDate(conflicts));
                        }
                        List<BiomeTuningService.Change> changes;
                        try {
                            changes = service.edit(biome, List.copyOf(edits.values()));
                        } catch (BiomeTuningException e) { // nothing was written: this click's values stay on screen
                            return section(player, biome, section, keep(biome, edits), error(e.getMessage()));
                        }
                        return section(player, biome, section, changes.isEmpty() ? ok("Nothing changed.")
                                : ok("Changed " + names(properties(changes)) + " (live; preview to see it)."));
                    }));
            // in place of Preview in world, which would lose sliders moved but not applied yet
            buttons.add(button("Apply + preview in world", "Apply what you changed, then refresh your view",
                    Permission.BIOME_TUNE, self, (player, answers) -> {
                        Map<String, BiomeTuningService.Edit> edits = edits(kept, form, answers);
                        List<String> conflicts = conflicts(biome, seen, edits.values());
                        if (!conflicts.isEmpty()) { // nor does it preview
                            return section(player, biome, section, keep(biome, edits), outOfDate(conflicts));
                        }
                        List<BiomeTuningService.Change> changes;
                        try {
                            changes = service.edit(biome, List.copyOf(edits.values()));
                        } catch (BiomeTuningException e) { // nothing was written, nor previewed, as with Apply
                            return section(player, biome, section, keep(biome, edits), error(e.getMessage()));
                        }
                        // a click that touched nothing is a plain preview, and the window says nothing about it
                        Component done = edits.isEmpty() ? null : changes.isEmpty() ? ok("Nothing changed.")
                                : ok("Changed " + names(properties(changes)) + ".");
                        return preview(player, (p, n) -> section(p, biome, section, both(done, n)));
                    }));
        }
        if (section == Section.AUDIO_AND_PARTICLES) {
            buttons.add(button("Copy from biome…", "Copy music, sounds and particles from another biome",
                    Permission.BIOME_TUNE, self, (player, answers) -> copySounds(player, biome, "minecraft:", null)));
        }
        if (inputs.isEmpty()) {
            buttons.add(button("Preview in world", REFRESH_TIP, Permission.BIOME_TUNE, self,
                    (player, answers) -> preview(player, self)));
        }
        buttons.add(button("Back", null, Permission.BIOME_TUNE, self, (player, answers) -> home(player, biome, null)));
        return window(viewer, self, title(section.title(), biome), notice, body, inputs, buttons);
    }

    /**
     * The colour picker. {@code pending} is the colour the builder chose here, or null while they have chosen none:
     * New then shows where the picker starts for the live value (that colour, or a stand-in when it is not set or not
     * a plain colour), and it is never written. {@code seen} is the value the builder last saw here (JsonNull when it
     * was not set), or null right after their own change. If the live value moved since, the window says so: an
     * untouched New follows it, and a chosen one waits for the builder to apply it again on purpose.
     */
    private EditorView picker(Player viewer, NamespacedKey biome, TuningProperty property, Integer pending,
                              Hsb sliders, JsonElement seen, Component notice) throws BiomeTuningException {
        BiomeDocument doc = service.document(biome);
        boolean argb = property.kind() == Kind.ARGB;
        JsonElement value = doc.value(property);
        Integer live = ColourMath.argb(value);
        boolean moved = seen != null && !seen.equals(seenAs(value));
        int colour = pending != null ? pending : start(property, value);
        Hsb hsb = sliders != null && (pending != null || !moved) ? sliders : ColourMath.toHsb(colour);
        JsonElement now = seenAs(value);
        Screen self = (player, n) -> picker(player, biome, property, pending, hsb, now, n);
        List<Component> body = new ArrayList<>();
        if (moved) {
            body.add(error("The live colour changed while this window was open" + (pending != null
                    ? ": check it before you click again." : live != null ? ", and New follows it."
                    : ": it is now " + (value == null ? "not set" : "not a plain colour")
                    + ", and New shows where the picker starts.")));
        }
        body.add(Component.text("Live ", NamedTextColor.GRAY)
                .append(live == null
                        ? Component.text(value == null ? "(not set)" : "(not a plain colour)", NamedTextColor.DARK_GRAY)
                        : ColourPicker.swatch(live).append(Component.text(" " + ColourPicker.text(argb, live),
                                NamedTextColor.WHITE)))
                .append(Component.text("      New ", NamedTextColor.GRAY))
                .append(ColourPicker.swatch(colour))
                .append(Component.text(" " + ColourPicker.text(argb, colour), NamedTextColor.WHITE)));
        if (property.name().equals("sky_color")) {
            body.add(horizon(colour, colourShown(doc, "fog_color")));
        } else if (property.name().equals("fog_color")) {
            body.add(horizon(colourShown(doc, "sky_color"), colour));
        }
        body.add(Component.text("Base colour at midday: the day cycle darkens it at dusk and night.",
                NamedTextColor.GRAY));
        // last: a dialog shows all its text before its inputs, so the rulers end the text right above the sliders
        addRuler(body, "Hue", ColourPicker.hueRuler());
        addRuler(body, "Saturation", ColourPicker.saturationRuler(hsb));
        addRuler(body, "Brightness", ColourPicker.brightnessRuler(hsb));

        List<EditorView.Button> buttons = new ArrayList<>();
        buttons.add(button("Show", "Show the new colour here; the biome does not change", Permission.BIOME_TUNE, self,
                (player, answers) -> {
                    ColourPicker.Pick pick = ColourPicker.resolve(argb, colour, hsb, answers);
                    return picker(player, biome, property, picked(pending, colour, pick), pick.sliders(), now,
                            null);
                }));
        // A click on a window drawn before the live colour changed writes nothing: the window is drawn again first.
        buttons.add(button("Apply", "Make it the biome's live value", Permission.BIOME_TUNE, self,
                (player, answers) -> {
                    ColourPicker.Pick pick = ColourPicker.resolve(argb, colour, hsb, answers);
                    Integer chosen = picked(pending, colour, pick);
                    if (movedSince(biome, property, value)) {
                        return picker(player, biome, property, chosen, pick.sliders(), now, null);
                    }
                    boolean changed = chosen != null && !chosen.equals(live)
                            && apply(biome, property, ColourPicker.text(argb, chosen));
                    return picker(player, biome, property, null, pick.sliders(), null, changed
                            ? ok("Applied (live; preview to see it).") : nothingChanged(chosen, live));
                }));
        // the window that comes back after the refresh, or at once when there is none, says what a touched click did
        buttons.add(button("Apply + preview in world", "Apply it, then refresh your view", Permission.BIOME_TUNE, self,
                (player, answers) -> {
                    ColourPicker.Pick pick = ColourPicker.resolve(argb, colour, hsb, answers);
                    Integer chosen = picked(pending, colour, pick);
                    if (movedSince(biome, property, value)) {
                        return picker(player, biome, property, chosen, pick.sliders(), now, null);
                    }
                    boolean changed = chosen != null && !chosen.equals(live)
                            && apply(biome, property, ColourPicker.text(argb, chosen));
                    // a click that touched nothing is a plain preview, and the window says nothing about it
                    boolean touched = chosen != null || !pick.sliders().equals(hsb);
                    Component done = changed ? ok("Applied.") : touched ? nothingChanged(chosen, live) : null;
                    JsonElement applied = seenAs(service.document(biome).value(property));
                    return preview(player, (p, n) -> picker(p, biome, property, null, pick.sliders(), applied,
                            both(done, n)));
                }));
        buttons.add(button("Copy from biome…", "Take this colour from another biome", Permission.BIOME_TUNE, self,
                (player, answers) -> copyColour(player, biome, property, pending, hsb, "minecraft:", now, null)));
        if (value != null && !REQUIRED.contains(property.name())) {
            String fallback = whenCleared(property);
            buttons.add(button("Clear", "Remove the value, so " + fallback + " applies", Permission.BIOME_TUNE, self,
                    (player, answers) -> {
                        // drawn again first, with what is live now; a Clear chooses no colour, so the window keeps
                        // what it was drawn with and does not read the inputs (a bad hex there must not block a Clear)
                        if (movedSince(biome, property, value)) {
                            return picker(player, biome, property, pending, hsb, now, null);
                        }
                        service.unset(biome, property.name());
                        return picker(player, biome, property, null, null, null,
                                ok("Cleared: " + fallback + " applies (live; preview to see it)."));
                    }));
        }
        buttons.add(button("Back", null, Permission.BIOME_TUNE, self,
                (player, answers) -> back(player, biome, property, null)));
        return window(viewer, self, title(label(property), biome), notice, body,
                ColourPicker.inputs(argb, colour, hsb), buttons);
    }

    /** Takes a colour from another biome, which counts as a choice; {@code pending} and {@code seen}: see picker. */
    private EditorView copyColour(Player viewer, NamespacedKey biome, TuningProperty property, Integer pending,
                                  Hsb sliders, String typed, JsonElement seen, Component notice) {
        Screen self = (player, n) -> copyColour(player, biome, property, pending, sliders, typed, seen, n);
        List<Component> body = new ArrayList<>();
        body.add(Component.text("Take " + label(property) + " from another biome, vanilla included. It shows as the "
                + "new colour, and Apply makes it live.", NamedTextColor.GRAY));
        List<EditorView.Input> inputs = List.of(
                new EditorView.TextBox("from", Component.text("Biome id"), fit(typed, ID_LIMIT), ID_LIMIT, false));
        List<EditorView.Button> buttons = List.of(
                button("Use its colour", null, Permission.BIOME_TUNE, self, (player, answers) -> {
                    String text = orEmpty(answers.text("from"));
                    try {
                        NamespacedKey from = key(text);
                        Integer found = ColourMath.argb(service.value(from, property));
                        if (found == null) {
                            throw new BiomeTuningException(from + " has no plain colour of its own for "
                                    + name(property));
                        }
                        int chosen = property.kind() == Kind.ARGB ? found : found | 0xff000000;
                        return picker(player, biome, property, chosen, ColourPicker.slidersFor(chosen, sliders), seen,
                                ok("Loaded from " + from + ": Apply makes it live."));
                    } catch (BiomeTuningException e) {
                        return copyColour(player, biome, property, pending, sliders, text, seen,
                                error(e.getMessage()));
                    }
                }),
                button("Back", null, Permission.BIOME_TUNE, self,
                        (player, answers) -> picker(player, biome, property, pending, sliders, seen, null)));
        return window(viewer, self, title("Copy " + label(property), biome), notice, body, inputs, buttons);
    }

    /**
     * The JSON editor. {@code text} is what the box holds (null: the live value), and {@code seen} is the value the
     * builder last saw here, as in the picker. If the live value moved since, the window says what it is now, and a
     * box the builder had not changed shows the new value.
     */
    private EditorView json(Player viewer, NamespacedKey biome, TuningProperty property, String text,
                            JsonElement seen, Component notice) throws BiomeTuningException {
        JsonElement value = service.document(biome).value(property);
        boolean moved = seen != null && !seen.equals(seenAs(value));
        boolean untouched = text == null || moved && text.equals(boxText(seen));
        String shown = untouched ? boxText(value) : text;
        JsonElement now = seenAs(value);
        Screen self = (player, n) -> json(player, biome, property, shown, now, n);
        String label = label(property);
        List<Component> body = new ArrayList<>();
        if (moved) {
            String live = shorten(TuningProperties.display(value));
            String stop = live.endsWith("…") ? "" : "."; // a full stop after a value cut short shows as four dots
            body.add(error("The live value changed while this window was open; it is now " + live
                    + (!untouched ? stop + " Check it before you click again."
                    : shown.length() <= JSON_LIMIT ? ", shown below." : stop)));
        }
        body.add(Component.text(label + " as JSON (" + property.path().get(property.path().size() - 1) + ")."
                + (shown.length() <= JSON_LIMIT ? " Vanilla checks it when you apply." : ""), NamedTextColor.GRAY));
        List<EditorView.Input> inputs = new ArrayList<>();
        List<EditorView.Button> buttons = new ArrayList<>();
        if (shown.length() > JSON_LIMIT) {
            body.add(Component.text("It is too long to change here: use /biometune set from the console.",
                    NamedTextColor.GOLD));
        } else {
            inputs.add(new EditorView.TextBox("json", Component.text("JSON"), shown, JSON_LIMIT, true));
            buttons.add(button("Apply", "Check it and make it live", Permission.BIOME_TUNE, self, (player, answers) -> {
                String typed = orEmpty(answers.text("json"));
                if (typed.isBlank()) {
                    return json(player, biome, property, typed, now, service.document(biome).value(property) == null
                            ? ok("Nothing changed: " + label + " is not set, and the box is empty.")
                            : error("It is empty: use Clear to remove the value."));
                }
                if (movedSince(biome, property, value)) { // drawn again first, saying what is live now
                    return json(player, biome, property, typed, now, null);
                }
                boolean changed;
                try {
                    changed = apply(biome, property, typed);
                } catch (BiomeTuningException e) {
                    return json(player, biome, property, typed, now, error(e.getMessage()));
                }
                return back(player, biome, property, changed ? ok(label + " changed (live; preview to see it).")
                        : ok("Nothing changed: " + label + " already has that value."));
            }));
        }
        if (value != null) {
            buttons.add(button("Clear", "Remove the value, so the default applies", Permission.BIOME_TUNE, self,
                    (player, answers) -> {
                        if (movedSince(biome, property, value)) { // drawn again first, saying what is live now
                            String typed = answers.text("json");
                            return json(player, biome, property, typed == null ? shown : typed, now, null);
                        }
                        service.unset(biome, property.name());
                        return back(player, biome, property, ok(label + " cleared (live; preview to see it)."));
                    }));
        }
        buttons.add(button("Back", null, Permission.BIOME_TUNE, self,
                (player, answers) -> back(player, biome, property, null)));
        return window(viewer, self, title(label, biome), notice, body, inputs, buttons);
    }

    private EditorView copySounds(Player viewer, NamespacedKey biome, String typed, Component notice) {
        Screen self = (player, n) -> copySounds(player, biome, typed, n);
        List<Component> body = new ArrayList<>();
        body.add(Component.text("Copy from another biome, vanilla included (for example minecraft:basalt_deltas). "
                + "Values that biome leaves unset are removed here too.", NamedTextColor.GRAY));
        List<EditorView.Input> inputs = List.of(
                new EditorView.TextBox("from", Component.text("Biome id"), fit(typed, ID_LIMIT), ID_LIMIT, false),
                new EditorView.Toggle("audio", Component.text("Music and sounds"), true),
                new EditorView.Toggle("particles", Component.text("Particles"), true));
        List<EditorView.Button> buttons = List.of(
                button("Copy", null, Permission.BIOME_TUNE, self, (player, answers) -> {
                    String text = orEmpty(answers.text("from"));
                    Set<Group> groups = EnumSet.noneOf(Group.class);
                    if (!Boolean.FALSE.equals(answers.flag("audio"))) {
                        groups.add(Group.AUDIO);
                    }
                    if (!Boolean.FALSE.equals(answers.flag("particles"))) {
                        groups.add(Group.PARTICLES);
                    }
                    try {
                        if (groups.isEmpty()) {
                            throw new BiomeTuningException("Tick music and sounds, particles, or both.");
                        }
                        NamespacedKey from = key(text);
                        List<BiomeTuningService.Change> changes = service.copy(from, biome, groups);
                        return section(player, biome, Section.AUDIO_AND_PARTICLES, changes.isEmpty()
                                ? ok("Nothing to copy: those values are already the same.")
                                : ok("Copied " + names(properties(changes)) + " from " + from
                                + " (live; preview to see it)."));
                    } catch (BiomeTuningException e) {
                        return copySounds(player, biome, text, error(e.getMessage()));
                    }
                }),
                button("Back", null, Permission.BIOME_TUNE, self,
                        (player, answers) -> section(player, biome, Section.AUDIO_AND_PARTICLES, null)));
        return window(viewer, self, title("Copy sounds and particles", biome), notice, body, inputs, buttons);
    }

    /** The window that asks whom to refresh; publishTo refreshes them. */
    private EditorView publish(Player viewer, NamespacedKey biome, Component notice) {
        Screen self = (player, n) -> publish(player, biome, n);
        List<Component> body = new ArrayList<>();
        body.add(Component.text("Refresh other players now, " + settings.publishPlayersPerSecond() + " per second, so "
                + "they see this server's live biome values. Everyone else sees them when they next join or switch "
                + "servers.", NamedTextColor.GRAY));
        List<EditorView.Button> buttons = List.of(
                button("Everyone in this world", null, Permission.BIOME_TUNE_PUBLISH, self,
                        (player, answers) -> publishTo(player, biome, player.getWorld().getPlayers())),
                button("Everyone on this server", null, Permission.BIOME_TUNE_PUBLISH, self,
                        (player, answers) -> publishTo(player, biome, Bukkit.getOnlinePlayers())),
                button("Back", null, Permission.BIOME_TUNE, self, (player, answers) -> home(player, biome, null)));
        return window(viewer, self, title("Publish", biome), notice, body, List.of(), buttons);
    }

    /** The window that asks before going back to the saved file. */
    private EditorView revert(Player viewer, NamespacedKey biome, Component notice) {
        Screen self = (player, n) -> revert(player, biome, n);
        List<Component> body = new ArrayList<>();
        body.add(Component.text("Go back to the saved file? Unsaved changes to " + biome + " are lost, and Undo "
                + "cannot bring them back.", NamedTextColor.GOLD));
        List<EditorView.Button> buttons = List.of(
                button("Revert", null, Permission.BIOME_TUNE, self, (player, answers) -> {
                    service.revert(biome);
                    return home(player, biome, ok("Back to the saved file (live; preview to see it)."));
                }),
                button("Back", null, Permission.BIOME_TUNE, self, (player, answers) -> home(player, biome, null)));
        return window(viewer, self, title("Revert", biome), notice, body, List.of(), buttons);
    }

    /**
     * The window that claims a spare biome for a new look: a label, and whether it starts from this biome's look or
     * from neutral plains. Create claims the next free spare, which the window names, and opens the editor on it. A
     * free spare's look is neutral plains, so on its own home the form has no choice to offer: the new biome starts
     * there, and a claim names no source. {@code typed} and {@code fromHere} are what the form shows. When no spare
     * can be claimed (none is free, or the labels file can't be read), the window says why, with Back.
     */
    private EditorView newBiome(Player viewer, NamespacedKey biome, String typed, boolean fromHere, Component notice) {
        Screen self = (player, n) -> newBiome(player, biome, typed, fromHere, n);
        Component title = title("New biome", biome);
        EditorView.Button back = button("Back", null, Permission.BIOME_TUNE, self,
                (player, answers) -> home(player, biome, null));
        List<NamespacedKey> free;
        boolean freeSpare;
        try {
            free = spares.free();
            freeSpare = spares.isFreeSpare(biome);
        } catch (BiomeTuningException e) { // the labels file can't be read: no claim until someone fixes it
            return window(viewer, self, title, notice, List.of(error(e.getMessage())), List.of(), List.of(back));
        }
        if (free.isEmpty()) {
            return window(viewer, self, title, notice, List.of(Component.text("No spare biome is free. "
                    + SparePool.HOW_TO_ADD, NamedTextColor.GOLD)), List.of(), List.of(back));
        }
        boolean start = fromHere && !freeSpare;
        List<Component> body = List.of(Component.text("Create claims " + free.get(0) + ", the next free spare, with "
                + "this label, and opens it here. A claim can't be undone; you can change the label later.",
                NamedTextColor.GRAY));
        List<EditorView.Input> inputs = new ArrayList<>();
        inputs.add(new EditorView.TextBox("label", Component.text("Label"), fit(typed, BiomeLabels.LABEL_LIMIT),
                BiomeLabels.LABEL_LIMIT, false));
        if (!freeSpare) {
            inputs.add(new EditorView.Choice("start", Component.text("Start from"), List.of(
                    new EditorView.Option("this", Component.text("this biome's look"), start),
                    new EditorView.Option("plains", Component.text("neutral plains"), !start))));
        }
        EditorView.Button create = button("Create", "Claim the next free spare biome", Permission.BIOME_TUNE, self,
                (player, answers) -> {
                    String label = orEmpty(answers.text("label"));
                    boolean here = switch (orEmpty(answers.text("start"))) {
                        case "this" -> true;
                        case "plains" -> false;
                        default -> start; // a choice that is missing or not offered keeps the one drawn
                    };
                    NamespacedKey from = here && !freeSpare ? biome : null;
                    SparePool.Claim claim;
                    try {
                        claim = spares.claim(label, from, player.getUniqueId(), player.getName());
                    } catch (BiomeTuningException e) { // nothing was claimed
                        // when no spare can be claimed any more, the window drawn again says why, once
                        return newBiome(player, biome, label, here, claimable() ? error(e.getMessage()) : null);
                    }
                    if (claim.copyFailure() != null) { // chat has why, and how to copy the look
                        ChatFeedback.claimed(claim, from).forEach(player::sendMessage);
                    }
                    return home(player, claim.spare(), claimed(claim, from));
                });
        return window(viewer, self, title, notice, body, inputs, List.of(create, back));
    }

    /** Whether a spare can be claimed now: one is free, and the labels file can be read. */
    private boolean claimable() {
        try {
            return !spares.free().isEmpty();
        } catch (BiomeTuningException e) {
            return false;
        }
    }

    // ---- what buttons do

    /** Saves the biome and names what changed; the log keeps each value's before and after. */
    private EditorView save(Player player, NamespacedKey biome) throws BiomeTuningException {
        BiomeDocument doc = service.document(biome);
        if (!doc.isUnsaved()) {
            return home(player, biome, ok("Nothing to save: the file already has these values."));
        }
        List<TuningProperty> changed = doc.changedSinceSave();
        service.save(biome, player.getName());
        return home(player, biome, ok("Saved " + (changed.isEmpty() ? "other values" : names(changed))
                + " to the datapack."));
    }

    private EditorView publishTo(Player player, NamespacedKey biome, Collection<? extends Player> players)
            throws BiomeTuningException {
        if (!refresher.publish(players, ChatFeedback.publishAnnouncement(player.getName()))) {
            return publish(player, biome, error("A publish ran moments ago; try again in a little while."));
        }
        return home(player, biome, ok("Refreshing " + players.size() + " player(s), "
                + settings.publishPlayersPerSecond() + " per second."));
    }

    /** Refreshes the player; {@code after} is the window that comes back once their client has loaded the world. */
    private EditorView preview(Player player, Screen after) throws BiomeTuningException {
        open.put(player.getUniqueId(), new Shown(after, clock.getAsLong())); // the refresh's quit keeps it to come back
        return switch (refresher.preview(player)) {
            case STARTED -> null;
            case COOLDOWN -> after.build(player, error("Wait "
                    + (refresher.previewCooldownLeft(player.getUniqueId()) + 999) / 1000 + " s before the next preview."));
            case ALREADY_REFRESHING -> after.build(player, error("Your view is already being refreshed."));
            case REFUSED -> after.build(player, error("Paper refused to refresh you: a plugin on this server listens "
                    + "to PlayerLoginEvent. Rejoin to see the live values."));
        };
    }

    /**
     * The edits a section's Apply asks for, in the window's order: for each input, what the builder changed now, or
     * else the edit kept on screen for it.
     */
    private static Map<String, BiomeTuningService.Edit> edits(Map<String, Kept> kept, List<EditorView.Input> form,
                                                             EditorView.Answers answers) {
        Map<String, BiomeTuningService.Edit> changed = new HashMap<>();
        EditorForms.changes(form, answers).forEach(edit -> changed.put(edit.property(), edit));
        Map<String, BiomeTuningService.Edit> edits = new LinkedHashMap<>();
        for (EditorView.Input input : form) {
            if (changed.containsKey(input.key())) {
                edits.put(input.key(), changed.get(input.key()));
            } else if (kept.containsKey(input.key())) {
                edits.put(input.key(), kept.get(input.key()).edit());
            }
        }
        return edits;
    }

    /** The edits to keep on screen after a warning or a refused Apply, each with the live value now. */
    private Map<String, Kept> keep(NamespacedKey biome, Map<String, BiomeTuningService.Edit> edits)
            throws BiomeTuningException {
        BiomeDocument doc = service.document(biome);
        Map<String, Kept> kept = new LinkedHashMap<>();
        for (BiomeTuningService.Edit edit : edits.values()) {
            kept.put(edit.property(), new Kept(edit, seenAs(doc.value(property(edit.property())))));
        }
        return kept;
    }

    /**
     * What changed under a section: for each field {@code edits} would change whose live value is no longer the one
     * the builder saw ({@code seen}), "fog end became 64 blocks", with the value as {@link #shown} writes it, or "fog
     * end is now not set" when it was removed. A field that now holds the builder's own value is no conflict, as
     * applying it writes nothing.
     */
    private List<String> conflicts(NamespacedKey biome, Map<TuningProperty, JsonElement> seen,
                                   Collection<BiomeTuningService.Edit> edits) throws BiomeTuningException {
        BiomeDocument doc = service.document(biome);
        List<String> conflicts = new ArrayList<>();
        for (BiomeTuningService.Edit edit : edits) {
            TuningProperty property = property(edit.property());
            JsonElement live = doc.value(property);
            if (!seenAs(live).equals(seen.get(property)) && !Objects.equals(live, wanted(property, edit))) {
                conflicts.add(name(property) + (live == null ? " is now not set" : " became " + shown(property, live)));
            }
        }
        return conflicts;
    }

    /**
     * A value in running text: a number its slider can show in the slider's units, exactly, and anything else, a
     * modifier or a number out of the slider's range, as it is. Exactly, as a value rounded to the slider's step could
     * read as the builder's own: a downfall of 0.37 is "37%", though its slider starts on 35.
     */
    private static String shown(TuningProperty property, JsonElement value) {
        Field slider = sliderFor(property, value);
        return slider == null ? TuningProperties.display(value) : slider.inText(value.getAsBigDecimal());
    }

    /**
     * A value as {@link #shown} writes it, for a line by the sliders, whose labels show the scale: a temperature of
     * 0.25 is "25", not "25 (×100)". A value that is not set is "not set", without brackets.
     */
    private static String bySlider(TuningProperty property, JsonElement value) {
        Field slider = sliderFor(property, value);
        return value == null ? "not set"
                : slider == null ? TuningProperties.display(value) : slider.bySlider(value.getAsBigDecimal());
    }

    /**
     * The field whose slider can show {@code value}, or null: for a value that is not set, a modifier, or a number out
     * of the slider's range.
     */
    private static Field sliderFor(TuningProperty property, JsonElement value) {
        Field field = EditorLayout.field(property);
        return value != null && field != null && input(field, value) instanceof EditorView.Slider ? field : null;
    }

    /** Makes {@code text} the live value, as one undo step; false (and no undo step) when it already is. */
    private boolean apply(NamespacedKey biome, TuningProperty property, String text) throws BiomeTuningException {
        return !service.edit(biome, List.of(new BiomeTuningService.Edit(property.name(), text))).isEmpty();
    }

    /** Whether the live value of {@code property} is no longer the one a window was drawn with. */
    private boolean movedSince(NamespacedKey biome, TuningProperty property, JsonElement drawn)
            throws BiomeTuningException {
        return !Objects.equals(service.document(biome).value(property), drawn);
    }

    private EditorView back(Player player, NamespacedKey biome, TuningProperty property, Component notice)
            throws BiomeTuningException {
        Section section = EditorLayout.sectionOf(property);
        return section == null ? home(player, biome, notice) : section(player, biome, section, notice);
    }

    // ---- clicks

    private EditorView.Button button(String label, String tooltip, Permission permission, Screen self, Step step) {
        return button(Component.text(label), tooltip, permission, self, step);
    }

    private EditorView.Button button(Component label, String tooltip, Permission permission, Screen self, Step step) {
        return new EditorView.Button(label, tooltip == null ? null : Component.text(tooltip),
                (player, answers) -> click(player, answers, permission, self, step));
    }

    private void click(Player player, EditorView.Answers answers, Permission permission, Screen self, Step step) {
        if (shutDown) {
            close(player);
            messages.sendErrorMessage(player, "The editor was restarted: open it again with /biometune.");
            return;
        }
        if (!player.hasPermission(permission.getPermissionNode())) {
            close(player);
            messages.sendNoPermissionError(player);
            return;
        }
        try {
            EditorView next = step.run(player, answers);
            if (next != null) {
                presenter.show(player, next);
            }
        } catch (BiomeTuningException e) {
            show(player, self, error(e.getMessage()));
        } catch (RuntimeException e) {
            failed(player, e);
        }
    }

    /** Shows {@code screen} again; if that fails, the window closes, because its buttons are spent. */
    private void show(Player player, Screen screen, Component notice) {
        try {
            presenter.show(player, screen.build(player, notice));
        } catch (BiomeTuningException e) {
            close(player);
            messages.sendErrorMessage(player, e.getMessage());
        } catch (RuntimeException e) {
            failed(player, e);
        }
    }

    /** Something unexpected: the window closes (its buttons are spent), the player is told, and the log has why. */
    private void failed(Player player, RuntimeException e) {
        logger.log(Level.WARNING, "biometune: an editor window failed", e);
        close(player);
        messages.sendErrorMessage(player, "That did not work" + (e.getMessage() == null ? ""
                : " (" + e.getMessage() + ")") + "; see the server log.");
    }

    private void close(Player player) {
        open.remove(player.getUniqueId());
        presenter.close(player);
    }

    /** A window with its buttons in two columns; see the next one. */
    private EditorView window(Player viewer, Screen self, Component title, Component notice, List<Component> body,
                              List<EditorView.Input> inputs, List<EditorView.Button> buttons) {
        return window(viewer, self, title, notice, body, inputs, buttons, 2);
    }

    /**
     * A window with the footer Close, remembered as the one this player has open. The notice (may be null) is its
     * first line: the client draws every window from the top, and a long one scrolls. Escape sends Close too, so
     * Close just closes: no permission check, and no message after a restart. The buttons sit in {@code columns}
     * columns: 2, or 3 for home, whose short labels fit four rows of three.
     */
    private EditorView window(Player viewer, Screen self, Component title, Component notice, List<Component> body,
                              List<EditorView.Input> inputs, List<EditorView.Button> buttons, int columns) {
        EditorView.Button exit = new EditorView.Button(Component.text("Close"),
                Component.text("Close the editor (Esc does the same)"), (player, answers) -> close(player));
        open.put(viewer.getUniqueId(), new Shown(self, clock.getAsLong()));
        List<Component> lines = new ArrayList<>();
        addIfPresent(lines, notice);
        lines.addAll(body);
        return new EditorView(title, lines, inputs, buttons, exit, columns);
    }

    // ---- small parts

    /** The slider, switch or choice for a value, or null when the value is something the form cannot show. */
    private static EditorView.Input input(Field field, JsonElement value) {
        TuningProperty property = field.property();
        Component label = Component.text(field.label());
        JsonPrimitive plain = value != null && value.isJsonPrimitive() ? value.getAsJsonPrimitive() : null;
        if (value != null && plain == null) {
            return null; // a modifier object or a list
        }
        return switch (property.kind()) {
            case NUMBER -> {
                if (plain != null && (!plain.isNumber() || plain.getAsFloat() < field.min()
                        || plain.getAsFloat() > field.max())) {
                    yield null;
                }
                float current = plain == null ? field.fallback() : plain.getAsFloat();
                float scale = field.scale();
                float min = field.min() * scale;
                float max = field.max() * scale;
                float step = field.step() * scale;
                // A quarter step of slack at both ends, a guard for a slider whose steps are not whole: the client
                // adds steps in float, and such steps can land an end a hair outside, out of reach. Every slider
                // shows whole steps today, so its ends are exact. Less than half a step, so nothing outside is written.
                float slack = step / 4;
                yield new EditorView.Slider(property.name(), label, min - slack, max + slack, step,
                        EditorForms.initial(current * scale, min, max, step), field.format(), scale);
            }
            case BOOLEAN -> plain != null && !plain.isBoolean() ? null
                    : new EditorView.Toggle(property.name(), label, plain != null && plain.getAsBoolean());
            case CHOICE -> {
                String current = plain == null ? null : plain.getAsString();
                if (plain != null && (!plain.isString() || !property.choices().contains(current))) {
                    yield null;
                }
                List<EditorView.Option> options = new ArrayList<>();
                options.add(new EditorView.Option(EditorForms.NOT_SET, Component.text("(not set)"), current == null));
                for (String choice : property.choices()) {
                    options.add(new EditorView.Option(choice, Component.text(choice), choice.equals(current)));
                }
                yield new EditorView.Choice(property.name(), label, options);
            }
            default -> null;
        };
    }

    /** What shows once a colour is cleared: the climate colour map for grass and foliage, else the default. */
    private static String whenCleared(TuningProperty property) {
        return property.name().equals("grass_color") || property.name().endsWith("foliage_color")
                ? "the climate colour map" : "the default colour";
    }

    /**
     * The colour a picker click has chosen: the inputs' colour once the builder has chosen one here (now or before),
     * else null. Only a different colour is a choice: a hue moved on grey changes nothing. A New the builder never
     * chose only shows where the picker starts, and is never written.
     */
    private static Integer picked(Integer pending, int colour, ColourPicker.Pick pick) {
        return pending != null || pick.colour() != colour ? pick.colour() : null;
    }

    /** Why a picker click wrote nothing: the colour is live already, or New only shows where the picker starts. */
    private static Component nothingChanged(Integer chosen, Integer live) {
        return ok(chosen != null || live != null ? "Nothing changed: that is already the live colour."
                : "Nothing changed: New only shows where the picker starts. Change it first, or set exactly this "
                + "colour with /biometune set.");
    }

    /** The value a section edit asks for, as the biome holds values: null when it removes the value. */
    private static JsonElement wanted(TuningProperty property, BiomeTuningService.Edit edit) {
        return edit.value() == null ? null : TuningProperties.parse(property, edit.value());
    }

    /** The warning an out-of-date section is drawn again with: nothing was written. */
    private static Component outOfDate(List<String> conflicts) {
        return error("Nothing was applied: while this window was open, " + inText(conflicts)
                + ". Check, then apply again.");
    }

    /**
     * The line a section shows while it keeps edits on screen, in the colour of unsaved: "Not applied yet: fog end 101
     * blocks (live 64 blocks)". It sits by the sliders, so values are written as {@link #bySlider} writes them, and
     * the edits are named as notices name values.
     */
    private static Component notApplied(BiomeDocument doc, Collection<Kept> kept) {
        List<String> parts = new ArrayList<>();
        for (Kept keptEdit : kept) {
            TuningProperty property = property(keptEdit.edit().property());
            parts.add(name(property) + " " + bySlider(property, wanted(property, keptEdit.edit())) + " (live "
                    + bySlider(property, doc.value(property)) + ")");
        }
        return Component.text("Not applied yet: " + few(parts), NamedTextColor.GOLD);
    }

    /** A value as a window saw it: JsonNull when it was not set, which differs from nothing seen yet (null). */
    private static JsonElement seenAs(JsonElement value) {
        return value == null ? JsonNull.INSTANCE : value;
    }

    /** Where the picker starts for a value: its colour, or where an unset (or not plain) colour starts. */
    private static int start(TuningProperty property, JsonElement value) {
        Integer colour = ColourMath.argb(value);
        return colour != null ? colour : ColourPicker.unsetStart(property);
    }

    /** What the JSON box shows for a value; empty when it is not set. */
    private static String boxText(JsonElement value) {
        return value == null || value.isJsonNull() ? "" : jsonText(value);
    }

    /** A value for the JSON box: pretty when that fits, else compact. */
    private static String jsonText(JsonElement value) {
        String pretty = PRETTY.toJson(value);
        return pretty.length() <= JSON_LIMIT ? pretty : value.toString();
    }

    private static Component colourLabel(Field field, JsonElement value) {
        Integer colour = ColourMath.argb(value);
        return (colour == null ? Component.text("-- ", NamedTextColor.DARK_GRAY)
                : Component.text("██ ", TextColor.color(colour & 0xffffff)))
                .append(Component.text(field.label(), NamedTextColor.WHITE));
    }

    /** "Sky ██  Fog ██  Water ██  Grass ██  Foliage ██", with -- for values that are not set. */
    private static Component swatches(BiomeDocument doc) {
        Component line = Component.empty();
        for (String name : SWATCHES) {
            TuningProperty property = property(name);
            Integer colour = ColourMath.argb(doc.value(property));
            line = line.append(Component.text(label(property) + " ", NamedTextColor.GRAY))
                    .append(colour == null ? Component.text("--  ", NamedTextColor.DARK_GRAY)
                            : Component.text("██  ", TextColor.color(colour & 0xffffff)));
        }
        return line;
    }

    /** The sky blending into the fog: the two mix on screen. */
    private static Component horizon(int sky, int fog) {
        return ruler("Sky to horizon", ColourPicker.horizon(sky, fog));
    }

    /** The colour a biome shows for {@code name}: its own, or where an unset (or not plain) colour starts. */
    private static int colourShown(BiomeDocument doc, String name) {
        TuningProperty property = property(name);
        return start(property, doc.value(property));
    }

    /** A slider ruler: its label, then the cells on a line of their own, as wide as the sliders so they line up. */
    private static void addRuler(List<Component> body, String label, Component cells) {
        body.add(Component.text(label, NamedTextColor.GRAY));
        body.add(cells);
    }

    private static Component ruler(String label, Component cells) {
        return Component.text(label + "  ", NamedTextColor.GRAY).append(cells);
    }

    /**
     * What home's first line calls a biome: its label, or its id when it has none or the labels file cannot be read
     * (the label commands say what is wrong with the file). The title shows the id either way.
     */
    private String heading(NamespacedKey biome) {
        try {
            return spares.labels().label(biome).orElse(biome.toString());
        } catch (BiomeTuningException e) {
            return biome.toString();
        }
    }

    private static Component title(String what, NamespacedKey biome) {
        return Component.text(what + ": " + biome);
    }

    private static String label(TuningProperty property) {
        Field field = EditorLayout.field(property);
        return field == null ? property.name() : field.label();
    }

    private static TuningProperty property(String name) {
        return TuningProperties.resolve(name).orElseThrow();
    }

    private static NamespacedKey key(String text) throws BiomeTuningException {
        String id = text.trim().toLowerCase(Locale.ROOT);
        if (!id.matches("([a-z0-9_.-]+:)?[a-z0-9_./-]+")) {
            throw new BiomeTuningException("'" + text.trim() + "' is not a biome id: for example minecraft:plains");
        }
        return NamespacedKey.fromString(id);
    }

    /**
     * The notice home shows after a claim: the id to paint with, and whether the source's look came along, or was there
     * already, as nothing was copied. Home has room for a notice of two lines, so a look that did not come along gets
     * one short sentence; chat has the claim's lines, as the command gives them, with why and how to copy the look.
     */
    private static Component claimed(SparePool.Claim claim, NamespacedKey from) {
        String paint = "Paint with it: //setbiome " + claim.spare();
        if (claim.copyFailure() != null) {
            return error("Claimed. " + paint + ". The look of " + from + " could not be copied: see chat.");
        }
        if (from == null) {
            return ok("Claimed. " + paint);
        }
        return ok(claim.copied().isEmpty() ? "Claimed. It already has the look of " + from + ". " + paint
                : "Claimed, with the look of " + from + " (live; Save keeps it). " + paint);
    }

    /** Values as running text, the way the windows name them: "sky, fog end and water fog end"; see {@link #few}. */
    private static String names(List<TuningProperty> properties) {
        return few(properties.stream().map(BiomeEditor::name).toList());
    }

    /**
     * Parts of a notice or line as running text, at most four, so it stays short: "a, b, c and d", and from five on the
     * first four and how many more, "a, b, c, d and 1 more".
     */
    private static String few(List<String> parts) {
        if (parts.size() <= 4) {
            return inText(parts);
        }
        List<String> named = new ArrayList<>(parts.subList(0, 4));
        named.add(parts.size() - 4 + " more");
        return inText(named);
    }

    /** Parts of a sentence as running text: "a", "a and b", "a, b and c". */
    private static String inText(List<String> parts) {
        int last = parts.size() - 1;
        return last < 1 ? String.join("", parts)
                : String.join(", ", parts.subList(0, last)) + " and " + parts.get(last);
    }

    /**
     * A value's name in running text (see {@link Field#name()}); a value the editor leaves out goes by its id, spaced.
     */
    private static String name(TuningProperty property) {
        Field field = EditorLayout.field(property);
        return field == null ? property.name().replace('_', ' ') : field.name();
    }

    private static List<TuningProperty> properties(List<BiomeTuningService.Change> changes) {
        return changes.stream().map(BiomeTuningService.Change::property).toList();
    }

    private static String shorten(String text) {
        return text.length() <= 60 ? text : text.substring(0, 59) + "…";
    }

    private static String orEmpty(String text) {
        return text == null ? "" : text;
    }

    /** Typed text for a box, cut to fit: only a modified client sends more than the box takes. */
    private static String fit(String text, int limit) {
        return text.length() <= limit ? text : text.substring(0, limit);
    }

    private static void addIfPresent(List<Component> body, Component line) {
        if (line != null) {
            body.add(line);
        }
    }

    /** What a click did, then how its refresh went, as one notice; either may be null. */
    private static Component both(Component done, Component refresh) {
        return done == null ? refresh : refresh == null ? done : done.append(Component.text(" ")).append(refresh);
    }

    private static Component ok(String text) {
        return Component.text(text, NamedTextColor.GREEN);
    }

    private static Component error(String text) {
        return Component.text(text, NamedTextColor.RED);
    }
}
