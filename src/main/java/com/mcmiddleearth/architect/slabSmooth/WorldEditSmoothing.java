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
import org.bukkit.World;
import org.bukkit.entity.Player;

/**
 * SmoothWorldEdit on the WorldEdit or FastAsyncWorldEdit the server runs. Thin on purpose: it only passes calls on, so
 * it is checked on a real server rather than in unit tests.
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
     */
    @Override
    public BlockTarget open(World world, Player player) {
        var actor = BukkitAdapter.adapt(player);
        LocalSession session = session(actor);
        com.sk89q.worldedit.world.World previous = session.getWorldOverride();
        EditSession edit;
        session.setWorldOverride(BukkitAdapter.adapt(world));
        try {
            edit = session.createEditSession(actor);
        } finally {
            session.setWorldOverride(previous);
        }
        return new EditSessionTarget(edit, session);
    }

    @Override
    public void bindBrush(Player player, double radius, Brush brush, String permission) throws InvalidToolBindException {
        var actor = BukkitAdapter.adapt(player);
        BrushTool tool = session(actor).getBrushTool(BukkitAdapter.asItemType(player.getInventory().getItemInMainHand().getType()));
        tool.setBrush(brush, permission);
        tool.setSize(radius);
    }

    @Override
    public boolean writesOffTheMainThread() {
        return Bukkit.getPluginManager().isPluginEnabled("FastAsyncWorldEdit");
    }

    private static LocalSession session(Actor actor) {
        return WorldEdit.getInstance().getSessionManager().get(actor);
    }
}
