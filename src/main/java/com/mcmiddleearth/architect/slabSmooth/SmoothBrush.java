package com.mcmiddleearth.architect.slabSmooth;

import com.mcmiddleearth.architect.Permission;
import com.mcmiddleearth.architect.slabSmooth.core.Options;
import com.mcmiddleearth.architect.slabSmooth.core.Smoother;
import com.mcmiddleearth.architect.slabSmooth.core.Table;
import com.mcmiddleearth.architect.slabSmooth.core.Tiles;
import com.mcmiddleearth.architect.slabSmooth.core.WorkBudget;
import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.MaxChangedBlocksException;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.command.tool.brush.Brush;
import com.sk89q.worldedit.function.pattern.Pattern;
import com.sk89q.worldedit.math.BlockVector3;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * WorldEdit brush. With surfaces=all it smooths every surface (tops, walls, overhangs) within {@code size} blocks
 * of the clicked block (a sphere); with surfaces=top it smooths the tops in a cylinder of that radius. The terrain
 * around it is read too, so the smoothed patch blends in, but only blocks inside change. Uses its owner's current
 * options at every click.
 *
 * <p>FAWE runs a click on a thread of its own, so everything a click touches is safe from any thread, and the brush
 * keeps nothing from its {@link Context} between clicks: it asks the supplier each time, so a reload or a disable
 * reaches brushes bound earlier.
 */
final class SmoothBrush implements Brush {

    /** Blocks read above and below a tops-only brush, where the terrain it blends into may rise or fall. */
    static final int VERTICAL_MARGIN = 8;

    /** What a click needs. A null Context means the feature is off. */
    record Context(TableSource tables, PlayerOptions options, ModuleSwitch module, int maxRadius,
                   Consumer<LinkageError> broken) {
    }

    /** What one click will smooth with. {@code tableAll} is {@code table} unless the player may be hinted about blocks all. */
    record Click(Packs.Choice choice, Table table, Table tableAll, Options options, double radius) {
    }

    private final UUID owner;
    private final Supplier<Context> context;
    private final Function<UUID, Player> online;

    /** @param online finds the owner if online; may answer null at any time, since it can run off the main thread */
    SmoothBrush(UUID owner, Supplier<Context> context, Function<UUID, Player> online) {
        this.owner = owner;
        this.context = context;
        this.online = online;
    }

    /**
     * What to smooth with at this click, or null after telling the player why there is nothing to do. Any thread.
     * The radius is clamped to the limit here, because //size can raise it past that (Rule 3).
     */
    Click plan(World world, int x, int y, int z, double size) {
        Context current = context.get();
        if (current == null) {
            return null;
        }
        Player player = online.apply(owner);
        if (!current.module().enabledIn(world)) {
            tell(player, Component.text("SlabSmooth is off in this world.", NamedTextColor.RED));
            return null;
        }
        Options opts = current.options().of(owner);
        // The permission is checked at every click, not when the setting was made: it can be taken away in between.
        boolean mayUseAll = player != null && player.hasPermission(Permission.SLAB_SMOOTH_ALL_BLOCKS.getPermissionNode());
        if (!mayUseAll) {
            opts.blocks = "terrain";
        }
        boolean everyBlock = "all".equals(opts.blocks);
        Packs.Choice choice = current.tables().choose(player, new Location(world, x, y, z), current.options().namedPack(owner));
        Table table, tableAll;
        try {
            table = current.tables().table(choice, everyBlock);
            tableAll = everyBlock || !mayUseAll ? table : current.tables().table(choice, true);
        } catch (IllegalStateException e) {
            tell(player, Component.text("SlabSmooth: " + e.getMessage(), NamedTextColor.RED));
            return null;
        }
        return new Click(choice, table, tableAll, opts, Math.min(size, current.maxRadius()));
    }

    /** As {@link #plan}, but a LinkageError (a plugin or server update this build doesn't fit) goes to broken. */
    Click planReportingBreakage(World world, int x, int y, int z, double size) {
        try {
            return plan(world, x, y, z, size);
        } catch (LinkageError e) {
            fail(e);
            return null;
        }
    }

    @Override
    public void build(EditSession session, BlockVector3 position, Pattern pattern, double size) throws MaxChangedBlocksException {
        try {
            Click click = planReportingBreakage(BukkitAdapter.adapt(session.getWorld()),
                    position.x(), position.y(), position.z(), size);
            if (click == null) {
                return;
            }
            smooth(session, position, click);
        } catch (LinkageError e) {
            fail(e);
        }
        // Everything else, FAWE's refusals included, is left to WorldEdit, which reports it and remembers the session.
    }

    private void smooth(EditSession session, BlockVector3 position, Click click) throws MaxChangedBlocksException {
        Options opts = click.options();
        boolean all = !"top".equals(opts.surfaces);
        int reach = (int) Math.ceil(click.radius());
        int margin = Tiles.margin(opts);
        int up = all ? reach + margin : reach + VERTICAL_MARGIN;
        WorldArea area = WorldArea.read(session, session.getWorld(),
                position.add(-reach - margin, -up, -reach - margin),
                position.add(reach + margin, up, reach + margin));
        double r2 = (click.radius() + 0.5) * (click.radius() + 0.5);
        boolean[] inside = all
                ? area.cells((x, y, z) -> sq(x - position.x()) + sq(y - position.y()) + sq(z - position.z()) <= r2)
                : area.columns((x, y, z) -> sq(x - position.x()) + sq(z - position.z()) <= r2);
        Smoother.Result result = area.smooth(click.table(), opts, inside, false, WorkBudget.NONE);
        int changed = result == null ? 0
                : area.write(new EditSessionTarget(session, null), result, false).blocks();
        Player player = online.apply(owner);
        if (player != null) {
            String pack = "default".equals(click.choice().why()) ? "" : " [" + click.choice().pack() + "]";
            String message = changed == 0
                    ? "Nothing to change here" + pack + SmoothSummary.blocksHint(area.palette, click.table(), click.tableAll())
                    : SmoothSummary.summary(result.report(), changed, all) + pack;
            tell(player, Component.text("SlabSmooth: " + message, NamedTextColor.GOLD));
        }
    }

    private void fail(LinkageError e) {
        Context current = context.get();
        if (current != null) {
            current.broken().accept(e);
        }
        tell(online.apply(owner), Component.text("SlabSmooth has stopped working: ask an admin to check the server log.",
                NamedTextColor.RED));
    }

    private static void tell(Player player, Component message) {
        if (player != null) {
            player.sendActionBar(message);
        }
    }

    private static double sq(double v) {
        return v * v;
    }
}
