package com.mcmiddleearth.architect.slabSmooth;

import com.sk89q.worldedit.IncompleteRegionException;
import com.sk89q.worldedit.command.tool.InvalidToolBindException;
import com.sk89q.worldedit.command.tool.brush.Brush;
import com.sk89q.worldedit.regions.Region;
import org.bukkit.Material;
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

    /**
     * "WorldEdit wand" or "WorldEdit navigation wand" if item is the one WorldEdit's configuration names as that, else
     * null. The brush is bound to neither wand. On FAWE, binding it to the selection wand's item fails: getBrushTool
     * asks the wand whether a player it doesn't have may use it. Binding it to the navigation wand there, or to
     * either wand on plain WorldEdit, would replace that wand; refusing those is a choice, so a builder keeps them.
     */
    String wandName(Material item);

    /**
     * "WorldEdit wand" or "WorldEdit navigation wand" if the player's session has that wand on item, else null. A
     * player can move a wand to any item (/tool selwand, /tool navwand), so this asks the session, without a player,
     * which can't fail as binding can.
     */
    String boundWand(Player player, Material item);

    /** True on FAWE, which writes edits off the main thread; plain WorldEdit writes on it. */
    boolean writesOffTheMainThread();
}
