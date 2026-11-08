package com.mcmiddleearth.architect.slabSmooth;

import com.sk89q.worldedit.IncompleteRegionException;
import com.sk89q.worldedit.command.tool.InvalidToolBindException;
import com.sk89q.worldedit.command.tool.brush.Brush;
import com.sk89q.worldedit.regions.Region;
import org.bukkit.World;
import org.bukkit.entity.Player;

/**
 * The only code that calls WorldEdit's sessions, so the jobs can be tested without a server: a fake stands in for it.
 * WorldEdit and FastAsyncWorldEdit share this API.
 */
interface SmoothWorldEdit {

    /** The player's selection in the world they are in. */
    Region selection(Player player) throws IncompleteRegionException;

    /**
     * An edit of world on the player's behalf: their mask, limits and history apply, and closing it makes it one //undo
     * step. Call on the thread that writes, since FAWE's edits belong to the thread that made them.
     */
    BlockTarget open(World world, Player player);

    /** Binds the brush to the item in the player's main hand. WorldEdit checks the permission at every click. */
    void bindBrush(Player player, double radius, Brush brush, String permission) throws InvalidToolBindException;

    /** True on FAWE, which writes edits off the main thread; plain WorldEdit writes on it. */
    boolean writesOffTheMainThread();
}
