package com.mcmiddleearth.architect.slabSmooth;

import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.IncompleteRegionException;
import com.sk89q.worldedit.LocalSession;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.command.tool.BrushTool;
import com.sk89q.worldedit.command.tool.InvalidToolBindException;
import com.sk89q.worldedit.command.tool.brush.Brush;
import com.sk89q.worldedit.extension.platform.Actor;
import com.sk89q.worldedit.regions.Region;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;

/**
 * SmoothWorldEdit on the WorldEdit or FastAsyncWorldEdit the server runs. Thin on purpose: it only passes calls on, so
 * it is checked on a real server rather than in unit tests. Only what opening an edit does with a refusal is tested
 * here, on a session the test makes.
 */
final class WorldEditSmoothing implements SmoothWorldEdit {

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
        BrushTool tool = session(actor).getBrushTool(BukkitAdapter.asItemType(player.getInventory().getItemInMainHand().getType()));
        tool.setBrush(brush, permission);
        tool.setSize(radius);
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
