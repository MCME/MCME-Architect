package com.mcmiddleearth.architect.additionalListeners;

import com.mcmiddleearth.architect.Modules;
import com.mcmiddleearth.architect.Permission;
import com.mcmiddleearth.architect.PluginData;
import com.mcmiddleearth.util.TheGafferUtil;
import io.papermc.paper.event.player.PlayerPickBlockEvent;
import org.bukkit.Location;
import org.bukkit.block.Barrel;
import org.bukkit.block.Block;
import org.bukkit.block.BrewingStand;
import org.bukkit.block.Chest;
import org.bukkit.block.Crafter;
import org.bukkit.block.Dispenser;
import org.bukkit.block.DoubleChest;
import org.bukkit.block.Dropper;
import org.bukkit.block.Furnace;
import org.bukkit.block.Hopper;
import org.bukkit.block.Lectern;
import org.bukkit.block.ShulkerBox;
import org.bukkit.entity.AbstractHorse;
import org.bukkit.entity.AbstractNautilus;
import org.bukkit.entity.ChestBoat;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.minecart.HopperMinecart;
import org.bukkit.entity.minecart.StorageMinecart;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;

import java.util.EnumSet;
import java.util.Set;

/**
 * Where protection.chestInventory is on, a player who may not build at a storage container that belongs to the world
 * can look into it, but cannot move an item into or out of it: every click and drag that would is cancelled before
 * anything moves, so closing the window changes nothing. Ender chests, workstations, villagers and plugin windows are
 * not world storage and are left to vanilla.
 */
public class InventoryProtectionListener implements Listener {

    // Clicks that move items between the container and the player's own slots from either side of the window.
    private static final Set<InventoryAction> ANYWHERE = EnumSet.of(InventoryAction.MOVE_TO_OTHER_INVENTORY,
            InventoryAction.COLLECT_TO_CURSOR, InventoryAction.UNKNOWN);

    // LOWEST, so a refused click is cancelled before FurnaceListener, which acts only on clicks that are allowed.
    @EventHandler(priority = EventPriority.LOWEST)
    public void keepItemsInPlace(InventoryClickEvent event) {
        Inventory top = event.getView().getTopInventory();
        if(!isProtected(event.getWhoClicked(), top)) {
            return;
        }
        int raw = event.getRawSlot();
        if((raw >= 0 && raw < top.getSize()) || ANYWHERE.contains(event.getAction())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void keepDragsOut(InventoryDragEvent event) {
        Inventory top = event.getView().getTopInventory();
        if(isProtected(event.getWhoClicked(), top)
                && event.getRawSlots().stream().anyMatch(raw -> raw < top.getSize())) {
            event.setCancelled(true);
        }
    }

    // In creative, picking a block with its data copies a container with its contents, without opening it.
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void keepCopiedStorageEmpty(PlayerPickBlockEvent event) {
        Player player = event.getPlayer();
        Block block = event.getBlock();
        if(event.isIncludeData() && isRuleOn(player) && isWorldStorage(block.getState())
                && !TheGafferUtil.hasGafferPermission(player, block.getLocation())) {
            event.setCancelled(true);
        }
    }

    /**
     * Whether an inventory's holder is storage that belongs to the world. Decided by the holder's type: Paper reports
     * the inventory type CHEST for minecarts, boats, mounts and plugin windows alike.
     */
    static boolean isWorldStorage(Object holder) {
        return holder instanceof Chest || holder instanceof DoubleChest || holder instanceof Barrel
                || holder instanceof ShulkerBox || holder instanceof Hopper || holder instanceof Dropper
                || holder instanceof Dispenser || holder instanceof Crafter || holder instanceof Furnace
                || holder instanceof BrewingStand || holder instanceof Lectern || holder instanceof StorageMinecart
                || holder instanceof HopperMinecart || holder instanceof ChestBoat || holder instanceof AbstractHorse
                || holder instanceof AbstractNautilus;
    }

    private static boolean isProtected(HumanEntity who, Inventory top) {
        if(!(who instanceof Player player) || !isRuleOn(player) || !isWorldStorage(top.getHolder())) {
            return false;
        }
        Location location = top.getLocation();
        // silent: TheGaffer's message for every click would flood the chat
        return location != null && !TheGafferUtil.hasGafferPermission(player, location);
    }

    // The module switch is read in the player's world.
    private static boolean isRuleOn(Player player) {
        return PluginData.isModuleEnabled(player.getWorld(), Modules.CHEST_INVENTORY_PROTECTION)
                && !player.hasPermission(Permission.IGNORE_INVENTORY_PROTECTION.getPermissionNode());
    }
}
