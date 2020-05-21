/*
 * Copyright (C) 2017 MCME
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.mcmiddleearth.architect.specialBlockHandling.listener;

import com.mcmiddleearth.architect.ArchitectPlugin;
import com.mcmiddleearth.architect.InventoryAccess;
import com.mcmiddleearth.architect.Modules;
import com.mcmiddleearth.architect.Permission;
import com.mcmiddleearth.architect.PluginData;
import com.mcmiddleearth.architect.noPhysicsEditor.NoPhysicsData;
import com.mcmiddleearth.architect.serverResoucePack.RpManager;
import com.mcmiddleearth.architect.specialBlockHandling.data.SpecialBlockInventoryData;
import com.mcmiddleearth.architect.specialBlockHandling.data.SpecialHeadInventoryData;
import com.mcmiddleearth.architect.specialBlockHandling.specialBlocks.SpecialBlock;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryCreativeEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;

/**
 *
 * @author Eriol_Eandur
 */
public class InventoryListener implements Listener{
    
    /**
     * If module SPECIAL_BLOCK_GET is enabled in world config file
     * opens the custom block inventory when a player presses his swap hand item key.
     * Tries to read players metadata set by ResourceRegions plugin. 
     * If there is no metadata tries to open inventory for resource pack "Gondor" as defined
     * in Architect config file.
     * @param event 
     */
    @EventHandler(priority=EventPriority.LOW)  
    public void openSpecialInventory(PlayerSwapHandItemsEvent event) {
        if(PluginData.isModuleEnabled(event.getPlayer().getWorld(), Modules.SPECIAL_BLOCKS_GET)) {
            event.setCancelled(true);
            final Player p = (Player) event.getPlayer();
            ItemStack handItem = p.getInventory().getItemInMainHand();
            ItemStack offHandItem = p.getInventory().getItemInOffHand();
            if((handItem.hasItemMeta() && handItem.getItemMeta().hasDisplayName()
                    && handItem.getItemMeta().getDisplayName().startsWith("Head Inventory"))
                || (offHandItem.hasItemMeta() && offHandItem.getItemMeta().hasDisplayName()
                    && offHandItem.getItemMeta().getDisplayName().startsWith("Head Inventory"))) {
                SpecialHeadInventoryData.openInventory(p);
                return;
            }
            String rpN = RpManager.getCurrentRpName(p);//1.13 removed: PluginData.getRpName(ResourceRegionsUtil.getResourceRegionsUrl(p));
            if(rpN==null || rpN.equals("")) {
                rpN = getRpName(handItem);
                if(rpN.equals("")) {
                    rpN = getRpName(offHandItem);
                }
            }
            if(!SpecialBlockInventoryData.openInventory(p, rpN)) {
                sendNoInventoryError(p,rpN);
            }
        }
    }
    
    public static String getRpName(ItemStack item) {
        String rpN="";
        if(item.hasItemMeta()
                && item.getItemMeta().hasDisplayName()) {
            String displayName = item.getItemMeta().getDisplayName();
            if(displayName.indexOf(' ')>0) {
                displayName = displayName.substring(0,displayName.indexOf(' '));
            }
            if(!RpManager.getRpUrl(displayName,null).equalsIgnoreCase("")) {
                rpN = displayName;
            }
        } 
        return rpN;
    }
    
    /**
     * If module SPECIAL_BLOCK_GET is enabled in world config file
     * opens the custom block inventory when a player droppes two stone blocks from the creative inventory.
     * @param event 
     */
    @EventHandler(priority=EventPriority.LOWEST) 
    public void openSpecialInventory(InventoryCreativeEvent event) {
        if(PluginData.isModuleEnabled(event.getWhoClicked().getWorld(), Modules.SPECIAL_BLOCKS_GET)) {
            if(event.getSlotType().equals(InventoryType.SlotType.OUTSIDE)
                    && event.getCursor().getType().equals(Material.STONE)
                    && event.getCursor().getAmount()==2) {
                if(!(event.getWhoClicked() instanceof Player)) {
                    return;
                }
                final Player p = (Player) event.getWhoClicked();
                String rpN = RpManager.getCurrentRpName(p);//PluginData.getRpName(ResourceRegionsUtil.getResourceRegionsUrl(p));
                if(rpN==null || rpN.equals("")) {
                    return; //+++rpN = "Gondor";
                }
                final String rpName = rpN;
                new BukkitRunnable() {
                    @Override
                    public void run() {
                        p.closeInventory();
                        new BukkitRunnable() {
                            @Override
                            public void run() {
                                if(!SpecialBlockInventoryData.openInventory(p, rpName)) {
                                    sendNoInventoryError(p,rpName);
                                }  
                            }
                        }.runTaskLater(ArchitectPlugin.getPluginInstance(), 1);
                    }
                }.runTaskLater(ArchitectPlugin.getPluginInstance(), 1);
            }
        }
    }
    
    /**
     * If module SPECIAL_BLOCK_GET is enabled in world config file
     * opens the custom block collection inventory when a player does a SHIFT-LEFT-CLICK.
     * @param event 
     */
    @EventHandler(priority=EventPriority.LOW)  
    public void openSpecialInventory(PlayerInteractEvent event) {
        if(PluginData.isModuleEnabled(event.getPlayer().getWorld(), Modules.SPECIAL_BLOCKS_GET)
                && event.getPlayer().isSneaking()
                && (event.getAction().equals(Action.LEFT_CLICK_AIR)
                    || event.getAction().equals(Action.LEFT_CLICK_BLOCK))) {
            event.setCancelled(true);
            final Player p = (Player) event.getPlayer();
            ItemStack handItem = p.getInventory().getItemInMainHand();
            SpecialBlock base = SpecialBlockInventoryData.getSpecialBlockDataFromItem(handItem);
            if(base == null || !base.hasCollection()) {
                sendNoInventoryError(p,"");
                return;
            }
            if(!SpecialBlockInventoryData.openInventory(p, handItem)) {
                sendNoInventoryError(p,"");
            }
        }
    }
    
    /**
     * If module INVENTORY_ACCESS is enabled in world config file
     * allowes or blocks opening of inventories as defined in world config file.
     * Possible configurations:
     * TRUE: Always allow to open this kind of inventory
     * FALSE: Never allow to open this kind of inventory
     * BUILDER: TheGaffer plugin build permission needed to open this kind of inventory
     * EXCEPTION: As BUILDER but additionally inventory can be opened in no physics exception areas only.
     * @param event 
     */
    @EventHandler(priority = EventPriority.LOWEST) 
    public void blockInventories(InventoryOpenEvent event) {
        if(!PluginData.isModuleEnabled(event.getPlayer().getWorld(), Modules.INVENTORY_ACCESS)
                || !(event.getPlayer() instanceof Player)) {
            return;
        }
        InventoryAccess access = PluginData.getInventoryAccess(event.getInventory());
        Player player = (Player)event.getPlayer();
        Location loc = event.getInventory().getLocation();
        switch(access) {
            case TRUE:
                return;
            case FALSE:
                event.setCancelled(true);
                return;
            case BUILDER:
                if(!PluginData.checkBuildPermissions((Player)event.getPlayer(), loc,
                                                Permission.INVENTORY_OPEN)) {
                    event.setCancelled(true);
                }
                return;
            case EXCEPTION:
                if(!PluginData.checkBuildPermissions((Player)event.getPlayer(),loc,
                                                Permission.INVENTORY_OPEN)
                        || !NoPhysicsData.hasNoPhysicsException(loc.getBlock())){
                    event.setCancelled(true);
                }
                return;
        }
        /*
        if (//event.getInventory().getType().equals(InventoryType.ANVIL)
          event.getInventory().getType().equals(InventoryType.BEACON) 
         || event.getInventory().getType().equals(InventoryType.BREWING) 
         || event.getInventory().getType().equals(InventoryType.DISPENSER) 
         || event.getInventory().getType().equals(InventoryType.HOPPER) 
         || event.getInventory().getHolder() instanceof ShulkerBox 
         || event.getInventory().getType().equals(InventoryType.DROPPER)){
            Block block = event.getInventory().getLocation().getBlock();
            if(!NoPhysicsData.hasNoPhysicsException(block)
                    || !PluginData.hasPermission((Player)event.getPlayer(),Permission.INVENTORY_OPEN)) {
                event.setCancelled(true);
            }
        }*/
    }

/*    static HashSet<String> found = new HashSet<>();
   
    @EventHandler
    public void moveInventoryItem(InventoryMoveItemEvent event) {
        Logger.getGlobal().info("moveInventory");
    }
    @EventHandler
    public void moveInventoryInteact(InventoryInteractEvent event) {
        Logger.getGlobal().info("interactInventory");
    }*/
    

    private void sendNoInventoryError(CommandSender p, String rp) {
        if(rp.equals("")) {
            PluginData.getMessageUtil().sendErrorMessage(p, "Custom inventory not found.");
        } else {
            PluginData.getMessageUtil().sendErrorMessage(p, "No custom inventory found for rp \""+rp+"\".");
        }
    }
       
}
