package com.mcmiddleearth.architect.slabSmooth;

import com.mcmiddleearth.architect.serverResoucePack.RpManager;
import com.mcmiddleearth.architect.serverResoucePack.RpRegion;
import com.sk89q.worldedit.math.BlockVector3;
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
    private final AtomicBoolean warned = new AtomicBoolean();

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
     * Every region with a pack whose bounding box meets the box, whatever its weight. For a region that isn't a box, or
     * one that a heavier region covers, that can name a pack no block of the box gets, which errs towards refusing: the
     * player can name a pack.
     */
    @Override
    public Set<String> regionPacksIn(World world, BlockVector3 min, BlockVector3 max) {
        try {
            Set<String> packs = new TreeSet<>();
            for (RpRegion region : RpManager.getRegions().values()) {
                Region area = region.getRegion();
                String pack = nameOrNull(region.getRp());
                if (pack != null && area.getWorld().getName().equals(world.getName())
                        && meet(area.getMinimumPoint(), area.getMaximumPoint(), min, max)) {
                    packs.add(pack);
                }
            }
            return packs;
        } catch (RuntimeException e) {
            warnOnce(e);
            return null;
        }
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
