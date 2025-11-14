/*
 * Copyright (C) 2016 MCME
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
import com.mcmiddleearth.architect.Modules;
import com.mcmiddleearth.architect.Permission;
import com.mcmiddleearth.architect.PluginData;
import com.mcmiddleearth.architect.serverResoucePack.RpManager;
import com.mcmiddleearth.architect.serverResoucePack.RpRegion;
import com.mcmiddleearth.architect.specialBlockHandling.SpecialBlockType;
import com.mcmiddleearth.architect.specialBlockHandling.data.SpecialBlockInventoryData;
import com.mcmiddleearth.architect.specialBlockHandling.specialBlocks.SpecialBlock;
import com.mcmiddleearth.architect.specialBlockHandling.specialBlocks.SpecialBlockItemBlock;
import com.mcmiddleearth.architect.watcher.WatchedListener;
import com.mcmiddleearth.pluginutil.EventUtil;
import com.mcmiddleearth.util.DevUtil;
import com.mcmiddleearth.util.TheGafferUtil;
import io.papermc.paper.event.player.PlayerItemFrameChangeEvent;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.Furnace;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Directional;
import org.bukkit.block.data.type.*;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityInteractEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemDamageEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.HashSet;
import java.util.Set;
import java.util.logging.Logger;

/**
 *
 * @author Eriol_Eandur
 */
public class SpecialBlockListener extends WatchedListener{
 
    /**
     * Handles player interaction with dragon eggs if module DRAGON_EGG is enabled in 
     * world config file. Teleportation of the egg is blocked.
     * Players need creative mode, permission INTERACT_EGG and build permission from 
     * TheGaffer plugin to interact with a dragon egg.
     * @param event 
     */
    @EventHandler(priority = EventPriority.HIGH)
    private void eggInteract(PlayerInteractEvent event) {
        Player p = event.getPlayer();
        if (event.hasBlock() 
                && EventUtil.isMainHandEvent(event)
                && event.getClickedBlock().getType().equals(Material.DRAGON_EGG)) {
            DevUtil.log(2,"eggInteract fired cancelled: " + event.isCancelled());
            if(event.isCancelled()) {
                return;
            }
            if(!PluginData.isModuleEnabled(event.getClickedBlock().getWorld(), Modules.DRAGON_EGG)) {
                return;
            }
            if (event.getAction().equals(Action.RIGHT_CLICK_BLOCK)) {
                event.setCancelled(true);
            }
            if (event.getAction().equals(Action.LEFT_CLICK_BLOCK)) {
                if (!(p.getGameMode().equals(GameMode.CREATIVE)
                        && PluginData.checkBuildPermissions(p, event.getClickedBlock().getLocation(),
                                                       Permission.INTERACT_EGG))) {
                    event.setCancelled(true);
                }
            }
        }
    }

    private Set<PlayerInteractEventData> interactEventDataSet = new HashSet<>();

    private record PlayerInteractEventData(int tick, Location loc) {

        public boolean matches(Object other) {
//Logger.getGlobal().info("matches");
            if(other instanceof PlayerInteractEventData(int tick1, Location loc1)) {
//Logger.getGlobal().info("Ticks: " + this.tick + " " + tick1 + " Location: " + this.loc.getBlockX() + " " + loc1.getBlockX()+" "+this.loc.getBlockY() + " " + loc1.getBlockY()+" "+this.loc.getBlockZ() + " " + loc1.getBlockZ()+" ");
                return this.tick == tick1
                    && this.loc.getBlockX() <= loc1.getBlockX() + 1 && this.loc.getBlockX() >= loc1.getBlockX() - 1
                    && this.loc.getBlockY() <= loc1.getBlockY() + 1 && this.loc.getBlockY() >= loc1.getBlockY() - 1
                    && this.loc.getBlockZ() <= loc1.getBlockZ() + 1 && this.loc.getBlockZ() >= loc1.getBlockZ() - 1;
            }
            return false;
        }
    }

    /**
     * If module SPECIAL_BLOCK_PLACE is enabled in world config file
     * handles placement of blocks from the MCME custom inventories.
     * @param event 
     */
    @EventHandler
    public void placeSpecialBlock(PlayerInteractEvent event) {
        if(event.getClickedBlock()==null) {
            return;
        }
        if(!PluginData.isModuleEnabled(event.getPlayer().getWorld(), Modules.SPECIAL_BLOCKS_PLACE)
                || event.getHand() == null
                || !event.getHand().equals(EquipmentSlot.HAND) 
                || event.getAction().equals(Action.LEFT_CLICK_AIR)
                || event.getAction().equals(Action.PHYSICAL)
                || event.getAction().equals(Action.LEFT_CLICK_BLOCK)
                || event.getPlayer().getInventory().getItemInMainHand().getType().equals(Material.AIR)
                //|| !(event.getPlayer().getInventory().getItemInMainHand().hasItemMeta())) {
                || !(SpecialBlockInventoryData.isSpecialBlockItem(event.getPlayer().getInventory().getItemInMainHand()))) {
            return;
        }
        PlayerInteractEventData eventData = new PlayerInteractEventData(Bukkit.getServer().getCurrentTick(),
                event.getClickedBlock().getLocation());
        if(interactEventDataSet.stream().anyMatch(data -> data.matches(eventData))) {
            return;
        }
        interactEventDataSet.removeIf(data -> data.tick < Bukkit.getServer().getCurrentTick());
        interactEventDataSet.add(eventData);
//Logger.getGlobal().info("PlayerInteractEvent: "+event.getHand()+" "+event.getAction()+ " "+event.getClickedBlock()+" "+event.getBlockFace()+" "+event.getInteractionPoint());
        final Player player = event.getPlayer();
        final ItemStack handItem = player.getInventory().getItemInMainHand();
        SpecialBlock data = SpecialBlockInventoryData.getSpecialBlockDataFromItem(handItem);
        if(data == null || data.getType().equals(SpecialBlockType.VANILLA)
                        || data.getType().equals(SpecialBlockType.DOOR_VANILLA)
                        || (!data.getType().equals(SpecialBlockType.BLOCK_ON_WATER)
                                && !event.getAction().equals(Action.RIGHT_CLICK_BLOCK))
                        || (event.getAction().equals(Action.RIGHT_CLICK_BLOCK)
                                && !event.getPlayer().isSneaking()
                                && !PluginData.getOrCreateWorldConfig(event.getPlayer().getWorld().getName())
                                         .getNoInteraction(event.getClickedBlock().getBlockData())
                                && (event.getClickedBlock().getBlockData() instanceof Door
                                 || event.getClickedBlock().getBlockData() instanceof TrapDoor
                                 || event.getClickedBlock().getBlockData() instanceof Switch
                                 || event.getClickedBlock().getBlockData() instanceof Gate))
                        || (event.hasBlock() && event.getClickedBlock().getType().equals(Material.FLOWER_POT))) {
            if(data==null) {
                PluginData.getMessageUtil().sendErrorMessage(player, "Special block data not found, item is probably outdated.");
                return;
            /*} else if(data instanceof SpecialBlockSign specialSign) {
                Block blockPlace = data.getBlock(event.getClickedBlock(), event.getBlockFace(), event.getInteractionPoint(), player);
                specialSign.registerSign(blockPlace);*/
            }
            return;
        }
        event.setCancelled(true); //cancel Event for main and off hand to avoid perks plugin removing the item
        final ItemStack[] armor = player.getInventory().getArmorContents();
        final ItemStack offHandItem = player.getInventory().getItemInOffHand();
        new BukkitRunnable() {
            @Override
            public void run() {
                ((PlayerInventory)player.getInventory()).setArmorContents(armor);
                player.getInventory().setItemInMainHand(handItem);
                player.getInventory().setItemInOffHand(offHandItem);
            }
        }.runTaskLater(ArchitectPlugin.getPluginInstance(), 1);
        Block blockPlace = data.getBlock(event.getClickedBlock(), event.getBlockFace(), event.getInteractionPoint(), player);
//Logger.getGlobal().info("get Block: "+blockPlace.getLocation());
        /*if(data instanceof SpecialBlockOnWater) {
            blockPlace = player.getTargetBlockExact(4, FluidCollisionMode.ALWAYS).getRelative(BlockFace.UP);
        } else {
            blockPlace = event.getClickedBlock().getRelative(event.getBlockFace());
        }*/
        if((player.isSneaking() && data.isEditOnSneaking())
            || data.canPlace(blockPlace))  {
            Location permissionLocation = ((player.isSneaking() && data.isEditOnSneaking())?
                                                        event.getClickedBlock().getLocation():
                                                        blockPlace.getLocation());
            if(!TheGafferUtil.hasGafferPermission(player,blockPlace.getLocation())) {
                return;
            }
//Logger.getGlobal().info("Block place");
            data.placeBlock(blockPlace, event.getBlockFace(), event.getClickedBlock(), event.getInteractionPoint(), player);
        }
    }

    /**
     * If module SPECIAL_BLOCK_PLACE is enabled in world config file
     * handles breaking of special blocks from the MCME custom inventories.
     * @param event
     */
    @EventHandler(priority = EventPriority.LOW)
    public void breakSpecialBlock(BlockBreakEvent event) {
//Logger.getGlobal().info("Block break");
//event.setCancelled(true);
//if(true) return;

        if(!PluginData.isModuleEnabled(event.getPlayer().getWorld(), Modules.SPECIAL_BLOCKS_PLACE)) {
            return;
        }
//Logger.getGlobal().info("enabled");
        final Player player = event.getPlayer();
        String rpName = RpManager.getCurrentRpName(event.getPlayer());
        ItemStack handItem = event.getPlayer().getInventory().getItemInMainHand();
        String rpNameItem = SpecialBlockInventoryData.getRpName(handItem);
        if(!rpNameItem.equals("") && !rpNameItem.equals(rpName)) {
            PluginData.getMessageUtil().sendErrorMessage(player, "WARNING: Resource pack of your hand item doesn't match your server RP setting.");
        }
//Logger.getGlobal().info("rp: "+rpName+ " "+rpNameItem);
        if(!rpNameItem.equals("")) {
            rpName = rpNameItem;
        }
        if(!rpName.equals("")) {
            SpecialBlock data = SpecialBlockInventoryData.getSpecialBlock(
                    SpecialBlockInventoryData.getSpecialBlockId(
                            SpecialBlockInventoryData.getItem(event.getBlock(), rpName)));
            if (data == null) return;
//Logger.getGlobal().info("Found special block data: "+data.getId());
            if (!TheGafferUtil.hasGafferPermission(player, event.getBlock().getLocation())) {
//Logger.getGlobal().warning("Cancel block break!");
                event.setCancelled(true);
                return;
            }
//Logger.getGlobal().info("Has permission!");
            BlockState state = event.getBlock().getState();
            new BukkitRunnable() {
                @Override
                public void run() {
                    data.handleBlockBreak(state);
                }
            }.runTaskLater(ArchitectPlugin.getPluginInstance(), 6);
        }
    }



    /**
     * If module SPECIAL_BLOCK_PLACE is enabled in world config file
     * prevents changes of item durability.
     * @param event 
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void blockChangeDurability(PlayerItemDamageEvent event) {
        if(PluginData.isModuleEnabled(event.getPlayer().getWorld(), Modules.SPECIAL_BLOCKS_PLACE)) {
                event.setCancelled(true);
        }
    }
    
    @EventHandler(priority = EventPriority.HIGH)
    public void blockPressurePlate(EntityInteractEvent event) {
        if(PluginData.isModuleEnabled(event.getBlock().getWorld(), Modules.SPECIAL_BLOCKS_PLACE)) {
                event.setCancelled(true);
        }
    }
    
    
    
    /**
     * If module SPECIAL_BLOCK_PLACE is enabled in world config file
     * prevents creation of a double slab block when stacking two half slabs.
     * Instead a corresponding full block is placed.
     * @param event 
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void avoidDoubleSlab(BlockPlaceEvent event) {
        if(PluginData.isModuleEnabled(event.getPlayer().getWorld(), Modules.SPECIAL_BLOCKS_PLACE)
                && event.getBlockPlaced().getBlockData() instanceof Slab
                && ((Slab)event.getBlockPlaced().getBlockData()).getType().equals(Slab.Type.DOUBLE)) {
            String rp = RpManager.getCurrentRpName(event.getPlayer());
            if(rp.equalsIgnoreCase("")) {
                RpRegion rpRegion = RpManager.getRegion(event.getBlock().getLocation());
                rp = rpRegion.getRp();
            }
            if(rp!=null && !rp.equals("")) {
                BlockData data = PluginData.getOrCreateWorldConfig(event.getBlock().getWorld().getName())
                        .getDoubleSlabReplacement(event.getBlockReplacedState().getBlockData(),
                                                  rp);
                if(data!=null) {
                    Block block = event.getBlockPlaced();
                    block.setBlockData(data,false);
                }
            }
        }
    }
        
    /**
     * If module SPECIAL_BLOCK_PLACE is enabled in world config file
     * prevents vanilla leave placement with various distance attribute.
     * @param event 
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void avoidVanillaLeaves(BlockPlaceEvent event) {
        if(PluginData.isModuleEnabled(event.getPlayer().getWorld(), Modules.SPECIAL_BLOCKS_PLACE)
                && event.getBlockPlaced().getBlockData() instanceof Leaves) {
            //event.setCancelled(true);
            Block block = event.getBlockPlaced();
            Leaves blockData = (Leaves) block.getBlockData();
            if(block.getType().equals(Material.ACACIA_LEAVES)) {
                blockData.setDistance(1);
                blockData.setPersistent(true);
            } else {
                blockData.setDistance(7);
                blockData.setPersistent(false);
            }
            block.setBlockData(blockData,false);
        }
    }
        
    @EventHandler(priority = EventPriority.HIGH)
    public void blockPlayerInteraction(PlayerInteractEvent event) { //used for item blocks
        if(!PluginData.isModuleEnabled(event.getPlayer().getWorld(), Modules.BLOCK_PLAYER_INTERACTION)
                || !(event.getPlayer() instanceof Player)) {
            return;
        }
        if(event.getAction().equals(Action.RIGHT_CLICK_BLOCK)
                && PluginData.getNoInteraction(event.getClickedBlock())) {
            event.setCancelled(true);
        }
    }
    
    @EventHandler(priority = EventPriority.HIGH) //TODO make configurable
    public void blockVanillaOrientations(BlockPlaceEvent event) {
        if(!PluginData.isModuleEnabled(event.getPlayer().getWorld(), Modules.SPECIAL_BLOCKS_PLACE)) {
            return;
        }
        if(event.getBlockPlaced().getType().equals(Material.PUMPKIN) 
                || event.getBlockPlaced().getType().equals(Material.END_PORTAL_FRAME) ) {
            BlockState state = event.getBlock().getState();
            BlockData data = state.getBlockData();
            if(data instanceof Directional) {
                ((Directional) data).setFacing(BlockFace.NORTH);
            }
            //state.setRawData((byte)0);
            state.getBlock().setBlockData(data,false);//update(true,false);
        }
    }
    
    /**
     * If module SPECIAL_BLOCK_PLACE is enabled in world config file
     * prevents player from changing armor stands used for item blocks.
     * @param event 
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void blockArmorAtItemBlocks(PlayerInteractAtEntityEvent event) { //used for item blocks
        if(!PluginData.isModuleEnabled(event.getPlayer().getWorld(), Modules.SPECIAL_BLOCKS_PLACE)
                || !(event.getPlayer() instanceof Player)) {
            return;
        }
        if(event.getRightClicked() instanceof ArmorStand
                && ((ArmorStand)event.getRightClicked()).getCustomName()!=null
                && ((ArmorStand)event.getRightClicked()).getCustomName()
                                     .startsWith(SpecialBlockItemBlock.PREFIX)) {
            event.setCancelled(true);
        }
    }
    
    /**
     * If module SPECIAL_BLOCK_PLACE is enabled in world config file
     * handles removing of armor stands associated to an item block when the 
     * item block is removed.
     * @param event 
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true) 
    public void removeItemBlockArmorStand(BlockBreakEvent event) {
        if(!PluginData.isModuleEnabled(event.getPlayer().getWorld(), Modules.SPECIAL_BLOCKS_PLACE)) {
            return;
        }
        Location loc = new Location(event.getBlock().getWorld(), event.getBlock().getX()+0.5,
                                    event.getBlock().getY(), event.getBlock().getZ()+0.5);
        SpecialBlockItemBlock.removeArmorStands(loc);
    }

    /**
     * Removes invisible item frames when content item is removed.
     * @param event
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void removeItemFrame(PlayerItemFrameChangeEvent event) {
        if(!PluginData.isModuleEnabled(event.getPlayer().getWorld(), Modules.SPECIAL_BLOCKS_PLACE)) {
            return;
        }
        if(event.getAction().equals(PlayerItemFrameChangeEvent.ItemFrameChangeAction.REMOVE)) {
            ItemFrame itemFrame = event.getItemFrame();
            if(!itemFrame.isVisible()) {
                event.setCancelled(true);
                itemFrame.remove();
            }
        }
    }
    
/***********************************************************************************************
/* Methods for old special blocks like six sided logs. Will no longer be needed once there are *
/* custom inventories for all                                                                  *
/***********************************************************************************************/
    

    /**
     * If module PISTON_EXTENSIONS is enabled in world config file
     * handles placing of piston extensions.
     * @param event 
     */
    @EventHandler(priority = EventPriority.HIGH)
    private void pistonPlace(BlockPlaceEvent event) {
        Player p = event.getPlayer();
        if (p.getItemInHand().getType().equals(Material.PISTON)
                || p.getItemInHand().getType().equals(Material.PISTON)) {
            if (!PluginData.isModuleEnabled(event.getBlock().getWorld(), Modules.PISTON_EXTENSIONS)) {
                return;
            }
            if (p.getItemInHand().getItemMeta().hasDisplayName() && 
                (p.getItemInHand().getItemMeta().getDisplayName().startsWith("Table")
                || p.getItemInHand().getItemMeta().getDisplayName().startsWith("Wheel"))) {
                    DevUtil.log(2,"pistonPlace fired cancelled: " + event.isCancelled());
                    if(event.isCancelled()) {
                        return;
                    }
                    event.setCancelled(true);
                    if(!(PluginData.hasPermission(p, Permission.PLACE_PISTON_EXTENSION))) {
                        PluginData.getMessageUtil().sendNoPermissionError(p);
                        return;
                } else if(!TheGafferUtil.hasGafferPermission(p,event.getBlock().getLocation())) {
//                    PluginData.getMessageUtil().sendErrorMessage(p, 
//                            PluginData.getGafferProtectionMessage(p, event.getBlock().getLocation()));
                    return;
                }
                    float yaw = p.getLocation().getYaw();
                    float pitch = p.getLocation().getPitch();
                    byte data = getPistonOrFurnaceDat(yaw, pitch, p.getItemInHand().getType());
                    Block block = event.getBlock();
                    final BlockState blockState = block.getState();
                    blockState.setType(Material.PISTON_HEAD);
                    blockState.setRawData(data);
                    new BukkitRunnable() {
                        @Override
                        public void run() {
                            blockState.update(true, false);
                        }
                    }.runTaskLater(ArchitectPlugin.getPluginInstance(), 1);
            }
        }
    }

    /**
     * Determines the correct data value for a piston extension or furnace block 
     * depending on player orientation.
     * @param yaw of the player who wants to place the block
     * @param pitch of the player who wants to place the block
     * @param type piston base or sticky piston base
     * @return 
     */
    private static byte getPistonOrFurnaceDat(float yaw, float pitch, Material type) {
        byte dat = 0;
        while(yaw>180) yaw-=360;
        while(yaw<-180) yaw+=360;
        if (pitch  < -45) {
            dat = 0;
        } else if (pitch  > 45) {
            dat = 1;
        } else if ((yaw >= -45 && yaw < 45)) {
            dat = 2;
        } else if (yaw < -135 || yaw >= 135) {
            dat = 3;
        } else if ((yaw >= -135 && yaw < -45)) {
            dat = 4;
        } else if ((yaw >= 45 && yaw < 135)) {
            dat = 5;
        }
        if(type.equals(Material.PISTON)) {
            dat+=8;
        }
        return dat;
    }
    
    /**
     * If module PLANTS is enabled in world config file
     * handles placing of pants independent of ground below.
     * If a block is clicked with a corresponding material in hand the data
     * value of the block is changed instead of placing a new block.
     * @param event 
     */
    @EventHandler(priority = EventPriority.HIGH)
    private void vegPlace(PlayerInteractEvent event) {
        Player p = event.getPlayer();
        if (event.getAction().equals(Action.RIGHT_CLICK_BLOCK)
                && (EventUtil.isMainHandEvent(event) 
                    || p.getItemInHand().getType().equals(Material.CACTUS)
                    //|| p.getItemInHand().getType().equals(Material.ROSE_RED)
                    || p.getItemInHand().getType().equals(Material.ROSE_BUSH)
                    || p.getItemInHand().getType().equals(Material.DEAD_BUSH)
                    || p.getItemInHand().getType().equals(Material.BROWN_MUSHROOM)
                    || p.getItemInHand().getType().equals(Material.RED_MUSHROOM)
                    || p.getItemInHand().getType().equals(Material.LILY_PAD))
                &&(p.getItemInHand().getType().equals(Material.CARROT)
                  || p.getItemInHand().getType().equals(Material.POTATO)
                  || p.getItemInHand().getType().equals(Material.WHEAT)
                  || p.getItemInHand().getType().equals(Material.MELON_SEEDS)
                  || p.getItemInHand().getType().equals(Material.PUMPKIN_SEEDS)
                  || p.getItemInHand().getType().equals(Material.BROWN_MUSHROOM)
                  || p.getItemInHand().getType().equals(Material.CACTUS)
                  //|| p.getItemInHand().getType().equals(Material.ROSE_RED)
                  || p.getItemInHand().getType().equals(Material.ROSE_BUSH)
                  || p.getItemInHand().getType().equals(Material.DEAD_BUSH)
                  || p.getItemInHand().getType().equals(Material.NETHER_WART)
                  || p.getItemInHand().getType().equals(Material.LILY_PAD)
                  || p.getItemInHand().getType().equals(Material.RED_MUSHROOM))) {
            if (!PluginData.isModuleEnabled(event.getClickedBlock().getWorld(), Modules.PLANTS)) {
                return;
            }
            if (p.getItemInHand().getItemMeta().hasDisplayName()
                    && p.getItemInHand().getItemMeta().getDisplayName().startsWith("Placeable")) {
                DevUtil.log(2,"vegPlace fired cancelled: " + event.isCancelled());
                if(event.isCancelled()) {
                    return;
                }
                if(!(PluginData.checkBuildPermissions(p, event.getClickedBlock().getLocation(),
                                                Permission.PLACE_PLANT))) {
                    event.setCancelled(true);
                    return;
                }
                Block b = event.getClickedBlock().getRelative(event.getBlockFace());
                BlockState bs = b.getState();
                bs = switch (p.getItemInHand().getType()) {
                    case BROWN_MUSHROOM -> handleInteract(event.getClickedBlock(), event.getBlockFace(),
                            Material.BROWN_MUSHROOM, true, (byte) 0);
                    case RED_MUSHROOM -> handleInteract(event.getClickedBlock(), event.getBlockFace(),
                            Material.RED_MUSHROOM, true, (byte) 0);
                    case WHEAT -> handleInteract(event.getClickedBlock(), event.getBlockFace(),
                            Material.WHEAT, true, (byte) 7);
                    case MELON_SEEDS -> handleInteract(event.getClickedBlock(), event.getBlockFace(),
                            Material.MELON_STEM, true, (byte) 7);
                    case PUMPKIN_SEEDS -> handleInteract(event.getClickedBlock(), event.getBlockFace(),
                            Material.PUMPKIN_STEM, true, (byte) 7);
                    case CARROT -> handleInteract(event.getClickedBlock(), event.getBlockFace(),
                            Material.CARROT, true, (byte) 7);
                    case POTATO -> handleInteract(event.getClickedBlock(), event.getBlockFace(),
                            Material.POTATO, true, (byte) 7);
                    case CACTUS -> handleInteract(event.getClickedBlock(), event.getBlockFace(),
                            Material.CACTUS, false, (byte) 0);
                    case NETHER_WART -> handleInteract(event.getClickedBlock(), event.getBlockFace(),
                            Material.NETHER_WART, true, (byte) 3);
                    case LILY_PAD -> handleInteract(event.getClickedBlock(), event.getBlockFace(),
                            Material.LILY_PAD, false, (byte) 0);
                    /*case ROSE_RED:
                        bs = handleInteract(event.getClickedBlock(), event.getBlockFace(),
                                            Material.ROSE_RED, false,p.getItemInHand().getData().getData());
                        break;
                        */
                    case ROSE_BUSH -> handleInteract(event.getClickedBlock(), event.getBlockFace(),
                            Material.ROSE_BUSH, false, (byte) 0);
                    case DEAD_BUSH -> handleInteract(event.getClickedBlock(), event.getBlockFace(),
                            Material.DEAD_BUSH, false, (byte) 0);
                    default -> bs;
                };
                bs.update(true,false);
            }
        }
    }
    
    /**
     * Handles placing and data value editing of plants. 
     * @param clickedBlock block the Player clicked at
     * @param clickedFace block Face the Placer clicked at, in this direction the new block will be placed
     * @param materialMatch If his material is found data value of that block is changed instead of placing a new block
     * @param editDataValue If editing of data value is allowed.
     * @param maxDataValue Maximum for data value.
     * @return 
     */
    private static BlockState handleInteract(Block clickedBlock,BlockFace clickedFace, 
                                             Material materialMatch, boolean editDataValue, byte maxDataValue) {
        BlockState blockState;
        if(clickedBlock.getType().equals(materialMatch) ) {
            blockState = clickedBlock.getState();
            if(editDataValue) {
                byte dataValue = (byte) (blockState.getRawData()- 1);
                if(dataValue<0) {
                    dataValue = maxDataValue;
                }
                blockState.setRawData(dataValue);
            }
        }
        else {
            blockState = clickedBlock.getRelative(clickedFace).getState();
            if(blockState.getType().equals(Material.AIR)) {
                blockState.setType(materialMatch);
                blockState.setRawData(maxDataValue);
            }
        }
        return blockState;
    }

    /**
     * If module REDSTONE_TORCH is enabled in world config file
     * handles placing of redstone torches.
     * @param event 
     */
    @EventHandler(priority = EventPriority.HIGH)
    private void torchPlace(PlayerInteractEvent event) {
        Player p = event.getPlayer();
        if (event.getAction().equals(Action.RIGHT_CLICK_BLOCK)
                && EventUtil.isMainHandEvent(event)
                &&(p.getInventory().getItemInHand().getType().equals(Material.REDSTONE_TORCH))) {
            if (!PluginData.isModuleEnabled(event.getClickedBlock().getWorld(), Modules.REDSTONE_TORCH)) {
                return;
            }
            if (p.getInventory().getItemInHand().getItemMeta().hasDisplayName()
                    && p.getInventory().getItemInHand().getItemMeta().getDisplayName().startsWith("Unlit")) {
                DevUtil.log(2,"torchPlace fired cancelled: " + event.isCancelled());
                if(event.isCancelled()) {
                    return;
                }
                event.setCancelled(true);
                if(!(PluginData.checkBuildPermissions(p,event.getClickedBlock().getLocation(),
                                                 Permission.PLACE_TORCH))) {
                    return;
                }
                Block b = event.getClickedBlock().getRelative(event.getBlockFace());
                final BlockState bs = b.getState();
                if(bs.getType().equals(Material.AIR)) {
                    bs.setType(Material.REDSTONE_TORCH);
                    bs.setRawData(getTorchDat(event.getBlockFace()));
                    new BukkitRunnable() {
                        @Override
                        public void run() {
                            bs.update(true, false);
                        }
                    }.runTaskLater(ArchitectPlugin.getPluginInstance(), 1);
                }
            }
        }
    }
    
    private byte getTorchDat(BlockFace face) {
        switch(face) {
            case NORTH:
                return (byte) 4;
            case SOUTH:
                return (byte) 3;
            case WEST:
                return (byte) 2;
            case EAST:
                return (byte) 1;
            default:
                return (byte) 0;
        }
    }
    
    @EventHandler(priority = EventPriority.HIGH)
    private void bedPlace(PlayerInteractEvent event) {
        final Player p = event.getPlayer();
        if (event.getAction().equals(Action.RIGHT_CLICK_BLOCK)
                && event.hasBlock()
                && EventUtil.isMainHandEvent(event)
                &&(p.getInventory().getItemInHand().getType().equals(Material.RED_BED))) {            
            if (!PluginData.isModuleEnabled(event.getClickedBlock().getWorld(), Modules.HALF_BEDS)) {
                return;
            }
            ItemStack item = p.getItemInHand();
            if(item.hasItemMeta() && item.getItemMeta().hasDisplayName()
                                  && item.getItemMeta().getDisplayName().startsWith("Half")) {
                DevUtil.log(2,"bedPlace fired cancelled: " + event.isCancelled());
                if(event.isCancelled()) {
                    return;
                }
                event.setCancelled(true);
                Block block = event.getClickedBlock().getRelative(event.getBlockFace());
                final BlockState blockState = block.getState();
                //final BlockState upperBlockState = block.getRelative(0, 1, 0).getState();
                if((PluginData.checkBuildPermissions(p, event.getClickedBlock().getLocation(),
                                                 Permission.PLACE_HALF_BED))) {
                    float yaw = p.getLocation().getYaw();
                    byte data = (byte)(getDoorDat(yaw)-1);
                    if(data<0) {
                        data=3;
                    }
                    if(item.getItemMeta().getDisplayName().endsWith("(head)")){
                        data = (byte) (data+6);
                    }
                    if(data<8) {
                        data = (byte) (data+4);
                    }
                    blockState.setType(Material.RED_BED);
                    blockState.setRawData(data);
                    blockState.update(true, false);
                    new BukkitRunnable() {
                        @Override
                        public void run() {
                            blockState.update(true, false);
                        }
                    }.runTaskLater(ArchitectPlugin.getPluginInstance(), 1);
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    private void doorPlace(PlayerInteractEvent event) {
        final Player p = event.getPlayer();
        if (event.getAction().equals(Action.RIGHT_CLICK_BLOCK)
                && event.hasBlock()
                && EventUtil.isMainHandEvent(event)
                &&(isDoorItem(p.getInventory().getItemInHand().getType()))) {            
            if (!PluginData.isModuleEnabled(event.getClickedBlock().getWorld(), Modules.HALF_DOORS)) {
                return;
            }
            ItemStack item = p.getItemInHand();
            if(item.hasItemMeta() && item.getItemMeta().hasDisplayName()
                                  && item.getItemMeta().getDisplayName().startsWith("Half")) {
                DevUtil.log(2,"doorPlace fired cancelled: " + event.isCancelled());
                if(event.isCancelled()) {
                    return; 
                }
                event.setCancelled(true);
                Block block = event.getClickedBlock().getRelative(event.getBlockFace());
                final BlockState blockState = block.getState();
                final BlockState upperBlockState = block.getRelative(0, 1, 0).getState();
                if((PluginData.checkBuildPermissions(p, event.getClickedBlock().getLocation(),
                                                Permission.PLACE_HALF_DOOR))) {
                    float yaw = p.getLocation().getYaw();
                    byte data = getDoorDat(yaw);

                    blockState.setType(doorItemToBlock(item.getType()));
                    blockState.setRawData(data);
                }
                new BukkitRunnable() {
                    @Override
                    public void run() {
                        blockState.update(true, false);
                        upperBlockState.update(true, false);
                    }
                }.runTaskLater(ArchitectPlugin.getPluginInstance(), 1);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void placeFurnace(BlockPlaceEvent event) {
        final Player p = event.getPlayer();
        if(event.getBlock().getType().equals(Material.FURNACE)) {
            if (!PluginData.isModuleEnabled(event.getBlock().getWorld(), Modules.BURNING_FURNACE)) {
                return;
            }
            if (p.getItemInHand().getItemMeta().hasDisplayName()
                    && p.getItemInHand().getItemMeta().getDisplayName().startsWith("Burning")) {
                DevUtil.log(2,"placeBurningFurnace fired cancelled: " + event.isCancelled());
                if(event.isCancelled()) {
                    return;
                }
                if(!(PluginData.checkBuildPermissions(p, event.getBlock().getLocation(), 
                                                      Permission.BURNING_FURNACE))) {
                    event.setCancelled(true);
                    return;
                }
                Furnace furnace = (Furnace) event.getBlock().getState();
                furnace.setType(Material.FURNACE);
                furnace.setRawData(getPistonOrFurnaceDat(p.getLocation().getYaw(),0,Material.FURNACE));
                furnace.getBlock().setBlockData(furnace.getBlockData(), false);//.update(true, false);
                final Block block = event.getBlock();
                new BukkitRunnable() {
                    @Override
                    public void run() {
                        final Furnace furnace = (Furnace) block.getState();
                        furnace.setBurnTime(Short.MAX_VALUE);
                        furnace.getBlock().setBlockData(furnace.getBlockData(), false);//update(true, false); 
                        new BukkitRunnable() {
                            @Override
                            public void run(){
                                furnace.getInventory().setSmelting(new ItemStack(Material.COD));
                            }
                        }.runTaskLater(ArchitectPlugin.getPluginInstance(), 1);
                    }
                }.runTaskLater(ArchitectPlugin.getPluginInstance(), 10);
            }
        }
    }
    
    private byte getDoorDat(float yaw) {
        if ((yaw >= -225 && yaw < -135)
                || (yaw >= 135 && yaw <= 225)) {
            return 3;
        } else if ((yaw >= -135 && yaw < -45)
                || (yaw >= 225 && yaw < 315)) {
            return 0;
        } else if ((yaw >= -45 && yaw < 45)
                || (yaw >= -360 && yaw < -315)
                || (yaw >= 315 && yaw <= 360)) {
            return 1;
        } else if ((yaw >= -315 && yaw < -225)
                || (yaw >= 45 && yaw < 135)) {
            return 2;
        } else {
            return 0;
        }
    }
    
    private boolean isDoorItem(Material blockType) {
        return blockType.equals(Material.OAK_DOOR)
                || blockType.equals(Material.IRON_DOOR)
                || blockType.equals(Material.SPRUCE_DOOR)
                || blockType.equals(Material.BIRCH_DOOR)
                || blockType.equals(Material.JUNGLE_DOOR)
                || blockType.equals(Material.ACACIA_DOOR)
                || blockType.equals(Material.DARK_OAK_DOOR);
    }
    
    private Material doorItemToBlock(Material itemMaterial) {
        return itemMaterial;
    }

//END DELETE
    
    
}
