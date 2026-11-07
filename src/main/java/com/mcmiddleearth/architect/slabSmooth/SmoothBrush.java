package com.mcmiddleearth.architect.slabSmooth;

import com.mcmiddleearth.architect.slabSmooth.core.Options;
import com.mcmiddleearth.architect.slabSmooth.core.Smoother;
import com.mcmiddleearth.architect.slabSmooth.core.Table;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.MaxChangedBlocksException;
import com.sk89q.worldedit.command.tool.brush.Brush;
import com.sk89q.worldedit.function.pattern.Pattern;
import com.sk89q.worldedit.math.BlockVector3;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.UUID;

/**
 * WorldEdit brush. With surfaces=all it smooths every surface (tops, walls, overhangs) within {@code size} blocks
 * of the clicked block (a sphere); with surfaces=top it smooths the tops in a cylinder of that radius. The terrain
 * around it is read too, so the smoothed patch blends in, but only blocks inside change. Uses its owner's current
 * /slabsmooth settings at every click.
 */
final class SmoothBrush implements Brush {

    private final SlabSmoothPlugin plugin;
    private final UUID owner;

    SmoothBrush(SlabSmoothPlugin plugin, UUID owner) {
        this.plugin = plugin;
        this.owner = owner;
    }

    @Override
    public void build(EditSession session, BlockVector3 position, Pattern pattern, double size) throws MaxChangedBlocksException {
        Options opts = plugin.options(owner);
        Player player = Bukkit.getPlayer(owner);
        Packs.Choice choice = plugin.packs().choose(player, new Location(BukkitAdapter.adapt(session.getWorld()),
                position.x(), position.y(), position.z()), plugin.packChoice(owner));
        boolean everyBlock = "all".equals(opts.blocks);
        Table table, tableAll;
        try {
            table = plugin.packs().table(choice, everyBlock);
            tableAll = everyBlock ? table : plugin.packs().table(choice, true);
        } catch (IllegalStateException e) {
            if (player != null) {
                player.sendActionBar(Component.text("SlabSmooth: " + e.getMessage(), NamedTextColor.RED));
            }
            return;
        }
        boolean all = !"top".equals(opts.surfaces);
        int reach = (int) Math.ceil(size);
        int margin = SlabSmoothPlugin.margin(opts);
        int up = all ? reach + margin : reach + SlabSmoothPlugin.VERTICAL_MARGIN;
        WorldArea area = WorldArea.read(session, session.getWorld(),
                position.add(-reach - margin, -up, -reach - margin),
                position.add(reach + margin, up, reach + margin));
        double r2 = (size + 0.5) * (size + 0.5);
        boolean[] inside = all
                ? area.cells((x, y, z) -> sq(x - position.x()) + sq(y - position.y()) + sq(z - position.z()) <= r2)
                : area.columns((x, y, z) -> sq(x - position.x()) + sq(z - position.z()) <= r2);
        Smoother.Result result = area.smooth(table, opts, inside, false);
        int changed = result == null ? 0 : area.write(session, result, plugin::parseState);
        if (player != null) {
            String pack = plugin.packs().dynamic() ? " [" + choice.pack() + "]" : "";
            String hint = changed == 0 ? SlabSmoothPlugin.blocksHint(area.palette, table, tableAll) : "";
            player.sendActionBar(Component.text(changed == 0 ? "SlabSmooth: nothing to change here" + pack + hint
                    : "SlabSmooth: " + result.report().changedColumns() + " surfaces, " + changed + " blocks" + pack, NamedTextColor.GOLD));
        }
    }

    private static double sq(double v) {
        return v * v;
    }
}
