package com.mcmiddleearth.architect.slabSmooth;

import com.mcmiddleearth.architect.serverResoucePack.RpManager;
import com.mcmiddleearth.architect.serverResoucePack.RpRegion;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.regions.CuboidRegion;
import com.sk89q.worldedit.regions.Region;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;

/**
 * The packs of this server, read from Architect itself: its server resource packs (names and download addresses from
 * config.yml), each pack's block inventory folder, the pack of an RP region or a player, and the packs of the RP regions
 * in a box. A lookup that fails answers null (and says so once), so a broken RP config leaves the default pack in use
 * instead of stopping an edit.
 */
final class ArchitectPacks implements PackSources {

    private final JavaPlugin plugin;
    private final Logger log;
    /** Set once the lookups by place or player have warned; the box lookup has its own, as its fallback differs. */
    private final AtomicBoolean warned = new AtomicBoolean(), boxesWarned = new AtomicBoolean();

    ArchitectPacks(JavaPlugin plugin, Logger log) {
        this.plugin = plugin;
        this.log = log;
    }

    @Override
    public List<String> packs() {
        ConfigurationSection rps = RpManager.getRpConfig();
        return rps == null ? List.of() : new ArrayList<>(rps.getKeys(false));
    }

    /** plugins/MCME-Architect/inventories/block/&lt;pack&gt;/ */
    @Override
    public Path inventory(String pack) {
        return plugin.getDataFolder().toPath().resolve("inventories").resolve("block").resolve(pack);
    }

    /**
     * Where to download a pack: the first url in its section of Architect's config, preferring the vanilla client
     * variant (every variant has the same block models). Null if there is none.
     */
    @Override
    public Source source(String pack) {
        ConfigurationSection rps = RpManager.getRpConfig();
        ConfigurationSection section = rps == null ? null : rps.getConfigurationSection(pack);
        if (section == null) {
            return null;
        }
        ConfigurationSection vanilla = section.getConfigurationSection("vanilla");
        Source s = vanilla != null ? first(vanilla) : null;
        return s != null ? s : first(section);
    }

    private static Source first(ConfigurationSection section) {
        if (section.isString("url")) {
            String sha = section.getString("sha", "");
            return new Source(section.getString("url"), sha == null ? "" : sha.trim().toLowerCase());
        }
        for (String key : section.getKeys(false)) {
            ConfigurationSection child = section.getConfigurationSection(key);
            if (child != null) {
                Source s = first(child);
                if (s != null) {
                    return s;
                }
            }
        }
        return null;
    }

    @Override
    public String regionPack(Location location) {
        try {
            RpRegion region = RpManager.getRegion(location);
            return region == null ? null : nameOrNull(region.getRp());
        } catch (RuntimeException e) {
            warnOnce(e);
            return null;
        }
    }

    /**
     * Every region with a pack whose bounding box meets the box, apart from one that a heavier region overrides there.
     * RpManager gives a block the pack of the heaviest region it is in, so a region is left out when a cuboid region of
     * strictly greater weight holds all of the region's box that lies in min..max: that cuboid, or a region heavier
     * still, decides every block there. Equal weights don't count as heavier, since which of two such regions wins
     * isn't defined. For a region that isn't a box, its bounding box can name a pack no block of the box gets, which
     * errs towards refusing: the player can name a pack.
     */
    @Override
    public Set<String> regionPacksIn(World world, BlockVector3 min, BlockVector3 max) {
        try {
            List<RpRegion> meeting = new ArrayList<>(); // with a pack or not: one without decides blocks too
            for (RpRegion region : RpManager.getRegions().values()) {
                Region area = region.getRegion();
                if (area.getWorld().getName().equals(world.getName())
                        && meet(area.getMinimumPoint(), area.getMaximumPoint(), min, max)) {
                    meeting.add(region);
                }
            }
            Set<String> packs = new TreeSet<>();
            for (RpRegion region : meeting) {
                String pack = nameOrNull(region.getRp());
                if (pack != null && !decidedByAHeavierBox(region, meeting, min, max)) {
                    packs.add(pack);
                }
            }
            return packs;
        } catch (RuntimeException e) {
            if (boxesWarned.compareAndSet(false, true)) {
                log.warning("Could not check the RP regions inside a selection (" + e
                        + "); using the sample points only.");
            }
            return null;
        }
    }

    /** Whether a heavier cuboid region holds all of region's bounding box that lies in min..max. */
    private static boolean decidedByAHeavierBox(RpRegion region, List<RpRegion> others, BlockVector3 min,
                                                BlockVector3 max) {
        Region area = region.getRegion();
        BlockVector3 lo = area.getMinimumPoint().getMaximum(min), hi = area.getMaximumPoint().getMinimum(max);
        for (RpRegion other : others) {
            if (other.getWeight() > region.getWeight() && other.getRegion() instanceof CuboidRegion box
                    && lo.containedWithin(box.getMinimumPoint(), box.getMaximumPoint())
                    && hi.containedWithin(box.getMinimumPoint(), box.getMaximumPoint())) {
                return true;
            }
        }
        return false;
    }

    /** Whether the boxes lo..hi and min..max (inclusive) have a block in common. */
    private static boolean meet(BlockVector3 lo, BlockVector3 hi, BlockVector3 min, BlockVector3 max) {
        return lo.x() <= max.x() && hi.x() >= min.x() && lo.y() <= max.y() && hi.y() >= min.y()
                && lo.z() <= max.z() && hi.z() >= min.z();
    }

    @Override
    public String currentPack(Player player) {
        try {
            return nameOrNull(RpManager.getCurrentRpName(player));
        } catch (RuntimeException e) {
            warnOnce(e);
            return null;
        }
    }

    private static String nameOrNull(String name) {
        return name == null || name.isEmpty() ? null : name;
    }

    private void warnOnce(RuntimeException e) {
        if (warned.compareAndSet(false, true)) {
            log.warning("Reading MCME-Architect's RP data failed (" + e + "); using the default pack there.");
        }
    }
}
