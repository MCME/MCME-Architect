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

import com.mcmiddleearth.architect.InventoryAccess;
import com.mcmiddleearth.architect.Modules;
import com.mcmiddleearth.architect.Permission;
import com.mcmiddleearth.architect.PluginData;
import com.mcmiddleearth.architect.noPhysicsEditor.NoPhysicsData;
import com.mcmiddleearth.architect.specialBlockHandling.data.SpecialBlockInventoryData;
import com.mcmiddleearth.architect.specialBlockHandling.data.SpecialHeadInventoryData;
import com.mcmiddleearth.architect.specialBlockHandling.specialBlocks.SpecialBlock;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.ItemStack;

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
    @EventHandler(priority=EventPriority.LOW, ignoreCancelled = true)
    public void openSpecialInventory(PlayerSwapHandItemsEvent event) {
        if(PluginData.isModuleEnabled(event.getPlayer().getWorld(), Modules.SPECIAL_BLOCKS_GET)) {
            event.setCancelled(true);
            //if((event.getPlayer().getOpenInventory().getTopInventory()) instanceof CraftInventoryCrafting) {
                final Player player = event.getPlayer();
                ItemStack handItem = player.getInventory().getItemInMainHand();
                //open custom block inventory
                ItemStack offHandItem = player.getInventory().getItemInOffHand();
//Logger.getGlobal().info("openSpecial: "+(handItem.hasItemMeta()?handItem.getItemMeta().getDisplayName():handItem));
//Logger.getGlobal().info("openSpecial: "+(handItem.hasItemMeta()?handItem.getItemMeta().getLore().get(0):handItem));

                /*if ((handItem.hasItemMeta() && handItem.getItemMeta().hasLore()
                        && handItem.getItemMeta().getLore().get(0).equals(HeadUtil.headCollectionTag))
                        || (offHandItem.hasItemMeta() && offHandItem.getItemMeta().hasLore()
                        && offHandItem.getItemMeta().getLore().get(0).equals(HeadUtil.headCollectionTag))) {*/
                if(player.isSneaking()) {
                    //Player player = event.getPlayer();
//Logger.getGlobal().info("Open search inv!!");
                    /*String rpName = RpManager.getCurrentRpName(player);
                    player.discoverRecipes(SpecialBlockInventoryData.getRecipeKeys(rpName));
                    InventoryView view = player.openWorkbench(null,true);
                    Inventory inv = view.getTopInventory();
                    ProtocolManager manager = ProtocolLibrary.getProtocolManager();
                    PacketAdapter packetListener = new PacketAdapter(ArchitectPlugin.getPluginInstance(), ListenerPriority.NORMAL, PacketType.Play.Client.AUTO_RECIPE) {
                        @Override
                        public void onPacketReceiving(PacketEvent event) {
                            Player thisPlayer = event.getPlayer();
//Logger.getGlobal().info("Auto Recipe ************************");
                            if(thisPlayer.equals(player)) {
                                PacketContainer packet = event.getPacket();
                                NamespacedKey key = new NamespacedKey(packet.getMinecraftKeys().read(0).getPrefix(),
                                        packet.getMinecraftKeys().read(0).getKey());
                                Recipe recipe = SpecialBlockInventoryData.getRecipe(key,rpName);
                                ItemStack item = recipe.getResult().clone();
                                item.setAmount(2);
                                view.setCursor(item);
                                event.setCancelled(true);
                            }
                        }
                    };
                    manager.addPacketListener(packetListener);
                    Listener listener = new Listener(){
                        @EventHandler
                        public void viewClose(InventoryCloseEvent event) {
                            if (event.getInventory() != inv) {
                                return;
                            }
//Logger.getGlobal().info("Close ************************");
                            manager.removePacketListener(packetListener);
                            player.undiscoverRecipes(SpecialBlockInventoryData.getRecipeKeys(rpName));
                            HandlerList.unregisterAll(this);
                        }
                    };
                    Bukkit.getPluginManager().registerEvents(listener, ArchitectPlugin.getPluginInstance());*/
                    String blockId = SpecialBlockInventoryData.getSpecialBlockId(handItem);
                    if(blockId == null) {
                        SpecialHeadInventoryData.openInventory(player);
                    } else {
                        SpecialBlockInventoryData.openInventory(player,handItem,true);
                    }
                    return;
                }
                String rpName = SpecialBlockInventoryData.getRpName(player);
                if (!SpecialBlockInventoryData.openInventory(player, rpName)) {
                    sendNoInventoryError(player, rpName);
                }
            /*} else {
                event.getPlayer().getOpenInventory().close();
            }*/
            //}
        }
    }

    /**
     * If module SPECIAL_BLOCK_GET is enabled in world config file
     * opens the custom block inventory when a player droppes two stone blocks from the creative inventory.
     * @param event 
     */
    /*@EventHandler(priority=EventPriority.LOWEST)
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
    }*/

    @EventHandler(ignoreCancelled = true)
    public void selectItem(PlayerDropItemEvent event) {
        if(PluginData.isModuleEnabled(event.getPlayer().getWorld(), Modules.SPECIAL_BLOCKS_GET)) {
            if(!event.getPlayer().isSneaking()) {
                return;
            }
            if (SpecialBlockInventoryData.getSpecialBlockDataFromItem(event.getItemDrop().getItemStack()) != null) {
                //event.setCancelled(true);
                final Player player = event.getPlayer();
                //ItemStack handItem = player.getInventory().getItemInMainHand();
                ItemStack droppedItem = event.getItemDrop().getItemStack();
                SpecialBlock base = SpecialBlockInventoryData.getSpecialBlockDataFromItem(droppedItem);
                if (droppedItem.getAmount() > 1) {
                    //open block collection if target block has one defined
                    if (base != null && base.hasCollection()) {
                        if(!SpecialBlockInventoryData.openInventory(player, droppedItem)) {
                            sendNoInventoryError(player, "");
                        }
                    }
                } else {
                    if (base != null && base.hasNextBlock()) {
                        base = base.getNextBlock();
                        /*nextItem.setAmount(2);
                        new BukkitRunnable(){
                            @Override
                            public void run() {
                                player.getInventory().setItemInMainHand(nextItem);
                            }
                        }.runTaskLater(ArchitectPlugin.getPluginInstance(),20);*/
                    }
                }
                ItemStack nextItem = SpecialBlockInventoryData.getItem(base);
                nextItem.setAmount(2);
                player.getInventory().setItemInMainHand(nextItem);
            } else {
                event.setCancelled(true);
            }
        }
        /*Logger.getLogger(InventoryListener.class.getSimpleName()).info("Drop event "+ event.getItemDrop().getItemStack().getAmount()
        +" "+(event.getPlayer().getOpenInventory())+" InventoryView "+((event.getPlayer().getOpenInventory().getTopInventory()) instanceof CraftInventoryCrafting)
        +"\n InventoryType"+(event.getPlayer().getOpenInventory().getType())
                +"\n Bottom Inventory"+(event.getPlayer().getOpenInventory().getBottomInventory())
        +"\n Top Inventory"+(event.getPlayer().getOpenInventory().getTopInventory()));*/
    }

    /*@EventHandler(priority = EventPriority.MONITOR)
    public void onInventoryClick(InventoryClickEvent event) {
        if(event.getClickedInventory() != null && event.getClickedInventory().getHolder() != null
                && (event.getClickedInventory().getHolder() instanceof Player)
                && PluginData.isModuleEnabled(((Player)event.getClickedInventory().getHolder()).getWorld(), Modules.SPECIAL_BLOCKS_GET)) {

        }
        Logger.getGlobal().info("CAncelled: "+event.isCancelled());
    }*/

    /*@EventHandler
    public void prepare(CraftItemEvent event) {
        Logger.getGlobal().info("Craft item event");
    }
    @EventHandler
    public void prepare(PrepareItemCraftEvent event) {
        Logger.getGlobal().info("Prepare item craft event");
    }
    static {
        ProtocolManager manager = ProtocolLibrary.getProtocolManager();
        manager.addPacketListener(new PacketAdapter(ArchitectPlugin.getPluginInstance(), ListenerPriority.NORMAL, PacketType.Play.Client.AUTO_RECIPE) {
            @Override
            public void onPacketReceiving(PacketEvent event) {
                Player player = event.getPlayer();
                Logger.getGlobal().info("Auto Recipe ************************");
                PacketContainer packet = event.getPacket();
                for(int i = 0; i < packet.getStrings().size();i++) {
                   Logger.getGlobal().info("Strings: " + packet.getStrings().read(i));
                }
                for(int i = 0; i < packet.getMinecraftKeys().size();i++) {
                    Logger.getGlobal().info("Keys " + packet.getMinecraftKeys().read(i).getFullKey());
                }
                for(int i = 0; i < packet.getIntegers().size();i++) {
                    Logger.getGlobal().info("Ints " + packet.getIntegers().read(i));
                }
                for(int i = 0; i < packet.getItemModifier().size();i++) {
                    Logger.getGlobal().info("Items " + packet.getItemModifier().read(i));
                }
                for(int i = 0; i < packet.getBytes().size();i++) {
                    Logger.getGlobal().info("Bytes " + packet.getBytes().read(i));
                }
                for(int i = 0; i < packet.getFloat().size();i++) {
                    Logger.getGlobal().info("Floats " + packet.getFloat().read(i));
                }
                for(int i = 0; i < packet.getDoubles().size();i++) {
                    Logger.getGlobal().info("Doubles " + packet.getDoubles().read(i));
                }
                for(int i = 0; i < packet.getBooleans().size();i++) {
                    Logger.getGlobal().info("Booleans " + packet.getBooleans().read(i));
                }
            }
        });
        manager.addPacketListener(new PacketAdapter(ArchitectPlugin.getPluginInstance(), ListenerPriority.NORMAL, PacketType.Play.Client.CLIENT_COMMAND) {
            @Override
            public void onPacketReceiving(PacketEvent event) {
                Player player = event.getPlayer();
                Logger.getGlobal().info("Client Command ************************");
                PacketContainer packet = event.getPacket();
                for(int i = 0; i < packet.getStrings().size();i++) {
                    Logger.getGlobal().info("Strings: " + packet.getStrings().read(i));
                }
            }
        });
    }*/

    /*@EventHandler
    public void openSearchInventory(PlayerJumpEvent event) {
        Logger.getGlobal().info("Open search inv");
        if(event.getPlayer().isSneaking()) {
            Player player = event.getPlayer();
            Logger.getGlobal().info("Open search inv!!");
            String rpName = RpManager.getCurrentRpName(player);
            player.discoverRecipes(SpecialBlockInventoryData.getRecipeKeys(rpName));
            InventoryView view = player.openWorkbench(null,true);
            Inventory inv = view.getTopInventory();
            ProtocolManager manager = ProtocolLibrary.getProtocolManager();
            PacketAdapter packetListener = new PacketAdapter(ArchitectPlugin.getPluginInstance(), ListenerPriority.NORMAL, PacketType.Play.Client.AUTO_RECIPE) {
                @Override
                public void onPacketReceiving(PacketEvent event) {
                    Player thisPlayer = event.getPlayer();
Logger.getGlobal().info("Auto Recipe ************************");
                    if(thisPlayer.equals(player)) {
                        PacketContainer packet = event.getPacket();
                        NamespacedKey key = new NamespacedKey(packet.getMinecraftKeys().read(0).getPrefix(),
                                                              packet.getMinecraftKeys().read(0).getKey());
                        Recipe recipe = SpecialBlockInventoryData.getRecipe(key,rpName);
                        view.setCursor(recipe.getResult());
                        event.setCancelled(true);
                    }
                }
            };
            manager.addPacketListener(packetListener);
            Listener listener = new Listener(){
                @EventHandler
                public void viewClose(InventoryCloseEvent event) {
                    if (event.getInventory() != inv) {
                        return;
                    }
Logger.getGlobal().info("Close ************************");
                    manager.removePacketListener(packetListener);
                    HandlerList.unregisterAll(this);
                }
            };
            Bukkit.getPluginManager().registerEvents(listener, ArchitectPlugin.getPluginInstance());
            //Search with AnvilInventory
            //ItemStack item = new ItemStack(Material.STONE);
            //ItemMeta meta = item.getItemMeta();
            //meta.setDisplayName("asd");
            //item.setItemMeta(meta);
            //inv.setItem(0,item);
            //inv.
            //InventoryView view = player.openInventory(inv);
            //inv.setItem(1,item.clone());
            //inv.setItem(2,item.clone());
            /*if(view != null) {
                Listener listener = new Listener() {
                    @EventHandler
                    public void viewClose(InventoryCloseEvent event) {
                        if (event.getInventory() != inv) {
                            return;
                        }
                        HandlerList.unregisterAll(this);
                    }

                    @EventHandler
                    public void getSearch(InventoryClickEvent event) {
Logger.getGlobal().info("slot "+event.getSlotType());
Logger.getGlobal().info("item "+event.getCurrentItem());
                        if (event.getInventory() != inv) {
                            return;
                        }
                        if(event.getSlotType().equals(InventoryType.SlotType.RESULT)) {
                            ItemStack item = event.getCurrentItem();
                            if(item != null && item.hasItemMeta()) {
                                String search = item.getItemMeta().getDisplayName();
Logger.getGlobal().info("search "+search);
                            }
                            view.close();
                            HandlerList.unregisterAll(this);
                        }
                    }
                };
                Bukkit.getPluginManager().registerEvents(listener, ArchitectPlugin.getPluginInstance());
            }
        }
    }*/

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
    

    static void sendNoInventoryError(CommandSender p, String rp) {
        if(rp.equals("")) {
            PluginData.getMessageUtil().sendErrorMessage(p, "Custom inventory not found.");
        } else {
            PluginData.getMessageUtil().sendErrorMessage(p, "No custom inventory found for rp \""+rp+"\".");
        }
    }
       
}
