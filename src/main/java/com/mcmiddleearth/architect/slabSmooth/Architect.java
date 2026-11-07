package com.mcmiddleearth.architect.slabSmooth;

import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

/**
 * What SlabSmooth reads from MCME-Architect: its server resource packs (names and download URLs from its
 * config.yml), each pack's block inventory folder, and which pack an RP region uses. Region lookups go through
 * Architect's RpManager by reflection, so SlabSmooth doesn't need Architect to build or run.
 */
final class Architect {

    static final String PLUGIN = "MCME-Architect";
    private static final String RP_MANAGER = "com.mcmiddleearth.architect.serverResoucePack.RpManager";

    record Source(String url, String sha1) {
    }

    private final Plugin plugin;
    private final Logger log;
    private Method getRegion, getRp, currentRpName;
    private boolean warned;

    private Architect(Plugin plugin, Logger log) {
        this.plugin = plugin;
        this.log = log;
        try {
            Class<?> manager = Class.forName(RP_MANAGER, true, plugin.getClass().getClassLoader());
            getRegion = manager.getMethod("getRegion", Location.class);
            currentRpName = manager.getMethod("getCurrentRpName", Player.class);
        } catch (ReflectiveOperationException | LinkageError e) {
            log.warning("MCME-Architect's RP regions can't be read (" + e + "); every place uses the default pack.");
        }
    }

    /** Architect if it is installed and enabled, else null. */
    static Architect find(org.bukkit.Server server, Logger log) {
        Plugin p = server.getPluginManager().getPlugin(PLUGIN);
        return p != null && p.isEnabled() ? new Architect(p, log) : null;
    }

    /** The server resource packs Architect knows. */
    List<String> packs() {
        ConfigurationSection rps = plugin.getConfig().getConfigurationSection("ServerResourcePacks");
        return rps == null ? List.of() : new ArrayList<>(rps.getKeys(false));
    }

    /** plugins/MCME-Architect/inventories/block/&lt;pack&gt;/ */
    Path inventory(String pack) {
        return plugin.getDataFolder().toPath().resolve("inventories").resolve("block").resolve(pack);
    }

    /**
     * Where to download a pack: the first url in its section of Architect's config, preferring the vanilla client
     * variant (every variant has the same block models). Null if there is none.
     */
    Source source(String pack) {
        ConfigurationSection rps = plugin.getConfig().getConfigurationSection("ServerResourcePacks");
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

    /** The pack of the heaviest RP region at a location, or null. */
    String regionPack(Location location) {
        if (getRegion == null) {
            return null;
        }
        try {
            Object region = getRegion.invoke(null, location);
            if (region == null) {
                return null;
            }
            if (getRp == null) {
                getRp = region.getClass().getMethod("getRp");
            }
            Object rp = getRp.invoke(region);
            return rp == null || rp.toString().isEmpty() ? null : rp.toString();
        } catch (ReflectiveOperationException | RuntimeException e) {
            warnOnce(e);
            return null;
        }
    }

    /** The pack the player has now, or null. */
    String currentPack(Player player) {
        if (currentRpName == null) {
            return null;
        }
        try {
            Object rp = currentRpName.invoke(null, player);
            return rp == null || rp.toString().isEmpty() ? null : rp.toString();
        } catch (ReflectiveOperationException | RuntimeException e) {
            warnOnce(e);
            return null;
        }
    }

    private void warnOnce(Exception e) {
        if (!warned) {
            warned = true;
            log.warning("Reading MCME-Architect's RP data failed (" + e + "); using the default pack there.");
        }
    }
}
