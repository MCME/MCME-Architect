package com.mcmiddleearth.architect.gamemodeSwitcher;

import com.mcmiddleearth.architect.Permission;
import com.mcmiddleearth.architect.PluginData;
import org.bukkit.Material;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;

import java.util.Set;

/**
 * Lets only players with architect.invisibleBlocks (ops, by default) place barrier, light and structure void blocks.
 * <p>
 * The game mode switcher tells builders' clients they have permission level 2, and at that level the creative
 * inventory shows its Operator Items tab. The server still refuses non-ops the tab's command, structure and jigsaw
 * blocks and its debug stick, as it checks their real level, but nothing gates these three blocks. They are
 * invisible, so hard to find and to remove again. The rule holds in every world, whether the switcher is on there or
 * not. Architect's own special blocks, such as an item block's barrier, are placed by Architect without a
 * BlockPlaceEvent, so this does not touch them.
 */
public final class InvisibleBlockListener implements Listener {

    private static final Set<Material> INVISIBLE = Set.of(Material.BARRIER, Material.LIGHT, Material.STRUCTURE_VOID);

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        if (INVISIBLE.contains(event.getBlockPlaced().getType())
                && !PluginData.hasPermission(event.getPlayer(), Permission.INVISIBLE_BLOCKS)) {
            event.setCancelled(true);
            PluginData.getMessageUtil().sendErrorMessage(event.getPlayer(),
                    "You can't place invisible blocks (barrier, light, structure void).");
        }
    }
}
