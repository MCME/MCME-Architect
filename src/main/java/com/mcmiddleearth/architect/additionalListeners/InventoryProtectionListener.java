package com.mcmiddleearth.architect.additionalListeners;

import com.mcmiddleearth.architect.Modules;
import com.mcmiddleearth.architect.Permission;
import com.mcmiddleearth.architect.PluginData;
import com.mcmiddleearth.util.TheGafferUtil;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class InventoryProtectionListener implements Listener {

    private final Map<UUID, SavedInventory> openInventories = new HashMap<>();

    @EventHandler
    public void openChest(InventoryOpenEvent event) {
        Inventory inv = event.getInventory();
        if(!PluginData.isModuleEnabled(event.getPlayer().getWorld(), Modules.CHEST_INVENTORY_PROTECTION)) {
            return;
        }
        if(!(inv instanceof PlayerInventory) && (event.getPlayer() instanceof Player) && inv.getLocation()!=null
                && !(TheGafferUtil.hasGafferPermission(((Player)event.getPlayer()),inv.getLocation())
                    || (((Player)event.getPlayer()).hasPermission(Permission.IGNORE_INVENTORY_PROTECTION.getPermissionNode())))) {
            ItemStack[] storageContent = inv.getStorageContents();
            ItemStack[] copiedContent = new ItemStack[storageContent.length];
            for(int i = 0; i< storageContent.length;i++) {
                copiedContent[i] = (storageContent[i]==null?null:storageContent[i].clone());
            }
            openInventories.put(event.getPlayer().getUniqueId(),new SavedInventory(inv, copiedContent));
        }
    }

    @EventHandler
    public void closeChest(InventoryCloseEvent event) {
        if(!PluginData.isModuleEnabled(event.getPlayer().getWorld(), Modules.CHEST_INVENTORY_PROTECTION)) {
            return;
        }
        if(!(event.getPlayer() instanceof Player)) return;
        SavedInventory savedInventory = openInventories.get(event.getPlayer().getUniqueId());
        if(savedInventory!=null) {
            savedInventory.getInventory().setStorageContents(savedInventory.getItems());
            openInventories.remove(event.getPlayer().getUniqueId());
        }

    }

    public static class SavedInventory {
        private final Inventory inventory;
        private final ItemStack[] items;

        public SavedInventory(Inventory inventory, ItemStack[] items) {
            this.inventory = inventory;
            this.items = items;
        }

        public Inventory getInventory() {
            return inventory;
        }

        public ItemStack[] getItems() {
            return items;
        }
    }
}