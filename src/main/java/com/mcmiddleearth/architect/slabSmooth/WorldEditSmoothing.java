package com.mcmiddleearth.architect.slabSmooth;

import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.IncompleteRegionException;
import com.sk89q.worldedit.LocalSession;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.command.tool.BrushTool;
import com.sk89q.worldedit.command.tool.InvalidToolBindException;
import com.sk89q.worldedit.command.tool.NavigationWand;
import com.sk89q.worldedit.command.tool.SelectionWand;
import com.sk89q.worldedit.command.tool.Tool;
import com.sk89q.worldedit.command.tool.brush.Brush;
import com.sk89q.worldedit.extension.platform.Actor;
import com.sk89q.worldedit.regions.Region;
import com.sk89q.worldedit.world.item.ItemType;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.logging.Logger;

/**
 * SmoothWorldEdit on the WorldEdit or FastAsyncWorldEdit the server runs. Thin on purpose: it only passes calls on, so
 * it is checked on a real server rather than in unit tests. Only what opening an edit does with a refusal, binding the
 * brush, and asking a session which wand it has on an item are tested, on sessions the tests make.
 */
final class WorldEditSmoothing implements SmoothWorldEdit {

    /** Architect's log, for what a player is told plainly but an admin may want to see. */
    private final Logger log;

    WorldEditSmoothing(Logger log) {
        this.log = log;
    }

    @Override
    public Region selection(Player player) throws IncompleteRegionException {
        var actor = BukkitAdapter.adapt(player);
        return session(actor).getSelection(actor.getWorld());
    }

    /**
     * Pins the edit to world with the session's world override, set only around createEditSession and then put back
     * as it was. The session isn't built by hand: createEditSession also gives it the player's mask and limits, and on
     * FAWE their source mask, transform, side effects, reorder mode, watchdog and survival extent, as their own
     * WorldEdit commands get.
     * <p>
     * FAWE may refuse the edit already here: no allowed region, or too little memory. That comes out as a Refused, as
     * a refusal while writing does. An edit that was made but couldn't be handed over is closed again, so nothing is
     * left open.
     */
    @Override
    public BlockTarget open(World world, Player player) {
        var actor = BukkitAdapter.adapt(player);
        return open(session(actor), actor, BukkitAdapter.adapt(world));
    }

    /** What open(world, player) does once it has the player's session; apart, so it can be tested without WorldEdit. */
    static BlockTarget open(LocalSession session, Actor actor, com.sk89q.worldedit.world.World world) {
        com.sk89q.worldedit.world.World previous = session.getWorldOverride();
        EditSession edit;
        session.setWorldOverride(world);
        try {
            edit = session.createEditSession(actor);
        } catch (RuntimeException e) {
            throw EditSessionTarget.translated(e);
        } finally {
            session.setWorldOverride(previous);
        }
        try {
            return new EditSessionTarget(edit, session);
        } catch (Throwable e) {
            try {
                edit.close();
            } catch (RuntimeException closing) {
                e.addSuppressed(closing);
            }
            if (e instanceof RuntimeException failure) {
                throw EditSessionTarget.translated(failure);
            }
            throw e;
        }
    }

    @Override
    public void bindBrush(Player player, double radius, Brush brush, String permission) throws InvalidToolBindException {
        var actor = BukkitAdapter.adapt(player);
        bind(session(actor), BukkitAdapter.asItemType(player.getInventory().getItemInMainHand().getType()), radius, brush,
                permission, log);
    }

    /**
     * What bindBrush does once it has the player's session and item; apart, so it can be tested without WorldEdit.
     * <p>
     * FAWE 2.16.1's getBrushTool(ItemType) passes no player on. It first sets up the session's wands if it hasn't yet,
     * asking whether that missing player may use the navigation wand, and it asks the same of a selection wand on the
     * item: a NullPointerException. FAWE marks the wands as set up before it asks, so on any item but a selection
     * wand, which the command refuses before this, a second try binds the brush. Only that exception, from
     * getBrushTool, gets a plain answer, and a line at FINE; anything else stays a failure.
     */
    static void bind(LocalSession session, ItemType item, double radius, Brush brush, String permission, Logger log)
            throws InvalidToolBindException {
        BrushTool tool;
        try {
            tool = session.getBrushTool(item);
        } catch (NullPointerException e) {
            log.fine(() -> "WorldEdit's session couldn't bind a brush to " + item + " yet: " + e);
            throw new Refusal("WorldEdit isn't ready for that item yet; try /sls brush again.");
        }
        tool.setBrush(brush, permission);
        tool.setSize(radius);
    }

    @Override
    public String boundWand(Player player, Material item) {
        return boundWand(session(BukkitAdapter.adapt(player)), BukkitAdapter.asItemType(item));
    }

    /**
     * What boundWand(player, item) does once it has the player's session; apart, so it can be tested without WorldEdit.
     * getTool(ItemType) only looks in the session's tools, on FAWE too, without a player, so it can't fail as
     * getBrushTool does.
     */
    static String boundWand(LocalSession session, ItemType item) {
        return wandOf(session.getTool(item));
    }

    /** The name the brush's refusal gives a wand tool, or null for any other tool and for none. */
    static String wandOf(Tool tool) {
        if (tool instanceof SelectionWand) {
            return "WorldEdit wand";
        }
        return tool instanceof NavigationWand ? "WorldEdit navigation wand" : null;
    }

    @Override
    public String wandName(Material item) {
        var configuration = WorldEdit.getInstance().getConfiguration();
        String key = item.getKey().toString();
        if (key.equals(itemId(configuration.wandItem))) {
            return "WorldEdit wand";
        }
        return key.equals(itemId(configuration.navigationWand)) ? "WorldEdit navigation wand" : null;
    }

    /** A configured item id in full ("wooden_axe" is minecraft:wooden_axe), or null if none is set. */
    static String itemId(String configured) {
        if (configured == null || configured.isBlank()) {
            return null;
        }
        String id = configured.strip().toLowerCase(java.util.Locale.ROOT);
        return id.contains(":") ? id : "minecraft:" + id;
    }

    @Override
    public boolean writesOffTheMainThread() {
        return Bukkit.getPluginManager().isPluginEnabled("FastAsyncWorldEdit");
    }

    private static LocalSession session(Actor actor) {
        return WorldEdit.getInstance().getSessionManager().get(actor);
    }
}
