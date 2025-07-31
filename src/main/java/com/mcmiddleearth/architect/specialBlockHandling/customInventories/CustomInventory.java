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
package com.mcmiddleearth.architect.specialBlockHandling.customInventories;

/**
 *
 * @author Eriol_Eandur
 */

import com.mcmiddleearth.architect.ArchitectPlugin;
import com.mcmiddleearth.architect.Permission;
import com.mcmiddleearth.architect.serverResoucePack.RpManager;
import com.mcmiddleearth.architect.specialBlockHandling.customInventories.editor.CustomInventoryEditor;
import com.mcmiddleearth.architect.specialBlockHandling.data.SpecialBlockInventoryData;
import com.mcmiddleearth.architect.specialBlockHandling.data.SpecialHeadInventoryData;
import com.mcmiddleearth.architect.specialBlockHandling.data.SpecialItemInventoryData;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.*;
import org.bukkit.event.inventory.InventoryType.SlotType;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.Plugin;

import java.util.*;

public class CustomInventory implements Listener {
 
    //sum should be 54
    public static final int CATEGORY_SLOTS = 9;
    public static final int ITEM_SLOTS = 45;

    private final String name;

    private final Map<String, CustomInventoryCategory> categories = new LinkedHashMap<>();

    private final CustomInventoryCategory withoutCategory = new CustomInventoryCategory(null, true, null,
                                                                        null, false);
    
    private final Map<Inventory,CustomInventoryState> openInventories = new HashMap<>();

    private final Map<UUID, CustomInventoryState> closedInventoryStates = new HashMap<>();
    
    private final String menueItemId = "MCME Inventory Category";

    public CustomInventory(String name) {
        this.name = name;
        Plugin plugin = ArchitectPlugin.getPluginInstance();
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }
   
    public CustomInventory add(ItemStack item, String category, boolean withSubcategories) {//, String name, String... info) {
        if(category == null || category.equalsIgnoreCase("")) {
            withoutCategory.addItem(item);
        } else {
            createCategoryIfNotExists(category, null, true,
                    new ItemStack(Material.GOLD_NUGGET),
                    new ItemStack(Material.GOLD_NUGGET), withSubcategories);
            categories.get(category).addItem(item);
        }
        return this;
    }
   
    public void setCategoryItems(String category, UUID owner, boolean isPublic,
                                 ItemStack item, ItemStack currentItem, boolean withSubcategories) {
        createCategoryIfNotExists(category, owner, isPublic, item, currentItem, withSubcategories);
        CustomInventoryCategory cat = categories.get(category);
        if(item!=null) 
            cat.setCategoryItem(setMenueItemMeta(item,category));
        if(currentItem!=null)
            cat.setCurrentCategoryItem(setMenueItemMeta(currentItem,category));
    }
    
    private void createCategoryIfNotExists(String category, UUID owner, boolean isPublic, ItemStack item,
                                           ItemStack currentItem, boolean usesSubcategories) {
        if(!categories.containsKey(category)) {
            categories.put(category, new CustomInventoryCategory(owner, isPublic,
                                                                 setMenueItemMeta(item,category),
                                                                 setMenueItemMeta(currentItem,category),
                                                                 usesSubcategories));
        }
    }

    public void open(Player player, ItemStack collectionBase) {
        open(player,collectionBase,false);
    }

    public void open(Player player, ItemStack collectionBase, boolean directGet) {
        int size = CATEGORY_SLOTS + ITEM_SLOTS;//Math.min((items.get(startCategory).size()/9+1)*9,54);
        Inventory inventory = Bukkit.createInventory(player, size, name);
        CustomInventoryState state;
        if(collectionBase == null) {
            Set<String> categoryNames = categories.keySet();
            Iterator<String> iterator = categoryNames.iterator();
            String startCategory = iterator.next();
//Logger.getGlobal().info("Start: "+startCategory);
            if(startCategory.equals("Blocks") || startCategory.equals("Heads")) {
                startCategory = iterator.next();
            }
            if(startCategory == null) {
                startCategory = "";
            }
            CustomInventoryState lastState = closedInventoryStates.get(player.getUniqueId());
            if(lastState != null) {
                state = lastState;
                state.setInventory(inventory);
                state.setPlayer(player);
            } else {
                state = new CustomInventoryCategoryState(categories, withoutCategory, inventory, player);
                state.setCategory(startCategory);
            }
        } else {
            state = new CustomInventoryCollectionState(categories, withoutCategory, inventory, player, collectionBase, directGet);
        }
        openInventories.put(inventory, state);
        state.update();
        
        player.openInventory(inventory);
    }
    
    public void deleteCategory(String name) {
        categories.remove(name);
    }
    
    public CustomInventoryCategory getCategory(String name) {
        return categories.get(name);
    }
    
    public void destroy() {
        HandlerList.unregisterAll(this);
        categories.clear();
    }
   
    @EventHandler(priority=EventPriority.MONITOR)
    void onInventoryDrag(final InventoryDragEvent event) {
        if (openInventories.containsKey(event.getInventory())) {//.getTitle().equals(name)) {
            for(int slot : event.getRawSlots()) {
                if(slot<event.getInventory().getSize()) {
                    event.setCancelled(true);
                }
            }
        }
    }
    
    @EventHandler(priority=EventPriority.MONITOR, ignoreCancelled=true)
    void onInventoryClick(final InventoryClickEvent event) {
        if (openInventories.containsKey(event.getInventory())) { //.getTitle.equals(name)) {
            if(event.getSlotType().equals(InventoryType.SlotType.OUTSIDE)
                    || event.getRawSlot() >= event.getInventory().getSize()
                    || event.getRawSlot() < CATEGORY_SLOTS) {//items.size()/9+1)*9
                if(event.getRawSlot()>event.getInventory().getSize() && event.getCursor()!=null) {
                    if(event.getCursor().isSimilar(event.getCurrentItem())
                        && event.getCurrentItem()!=null
                            && event.getCursor().getMaxStackSize()
                                >= event.getCursor().getAmount()+event.getCurrentItem().getAmount()) {
                        event.getCursor().setAmount(event.getCursor().getAmount()
                                + event.getCurrentItem().getAmount());
                    } else {
                        ItemStack temp = event.getCursor();
                        event.getView().setCursor(event.getCurrentItem());
                        event.setCurrentItem(temp);
                    }
                    event.setCancelled(true);
                }
                return;
            }
            event.setCancelled(true);
            CustomInventoryState state = openInventories.get(event.getInventory());
            if(state.isPageUpSlot(event.getRawSlot())) {
                state.pageUp();
                state.update();
                return;
            }
            if(state.isPageDownSlot(event.getRawSlot())) {
                state.pageDown();
                state.update();
                return;
            }
            if(state instanceof CustomInventoryCollectionState) {
                if(event.getRawSlot() == ((CustomInventoryCollectionState)state).getBackSlot()) {
                    state = new CustomInventoryCategoryState(state);
                    openInventories.put(state.inventory, state);
                    state.update();
                    return;
                }
                if(event.getRawSlot() == ((CustomInventoryCollectionState)state).getMaskSlot()) {
                    return;
                }
                if(event.getCurrentItem()!=null && event.isLeftClick() && ((CustomInventoryCollectionState) state).isDirectGet()) {
                    /*ItemStack item = new ItemStack(event.getCurrentItem());
                    item.setAmount(1);
                    ((CustomInventoryCollectionState) state).setDirectGet(false);*/
                    event.getWhoClicked().getInventory().setItem(EquipmentSlot.HAND, new ItemStack(Material.AIR));
                    Bukkit.getScheduler().runTaskLater(ArchitectPlugin.getPluginInstance(),()->
                        event.getWhoClicked().closeInventory(),1);
                }
            }
            if(event.getCurrentItem() != null
                && (event.isRightClick()
                    || (event.isLeftClick() && event.isShiftClick()))
                    || (state.usesSubcategories() && hasCollection(event.getCurrentItem()))) {
                if(hasCollection(event.getCurrentItem())) {
                    state = new CustomInventoryCollectionState(state,event.getCurrentItem());
                    openInventories.put(state.inventory, state);
                    state.update();
                }
                return;
            }
            if(event.getCurrentItem() != null) {
                //pick up SpecialBlock item
                if(event.getCursor() !=null && event.getCursor().getType().equals(Material.AIR)) {
                    ItemStack item = new ItemStack(event.getCurrentItem());
                    item.setAmount(2);
                    event.getView().setCursor(item);
                } else if(event.getCursor().isSimilar(event.getCurrentItem())) { 
                    if(event.getCursor().getMaxStackSize()>event.getCursor().getAmount()) {
                        event.getCursor().setAmount(event.getCursor().getAmount()+1);
                    }
                //} else {
                //    event.setCursor(new ItemStack(Material.AIR));
                }
            }
        }
    }

    /*@EventHandler
    public void onPlayerInventoryClick(final InventoryClickEvent event) {
        Logger.getLogger("CustomInventory").info(""+event.getClickedInventory().getType());
        Logger.getLogger("CustomInventory").info(""+event.getSlotType());
        if(event.getSlotType().equals(SlotType.QUICKBAR)) {//event.getClickedInventory().getType().equals(InventoryType.PLAYER)){
            new BukkitRunnable() {
                @Override
                public void run() {
                    event.getClickedInventory().getItem(event.getSlot()).setAmount(new Random().nextInt(4)-2);
                }
            }.runTaskLater(ArchitectPlugin.getPluginInstance(),1);
            //event.getCurrentItem()item.setAmount(new Random().nextInt(4)-2);


        }
    }*/
    
    @EventHandler(priority=EventPriority.MONITOR, ignoreCancelled=true)
    void onMenueClick(final InventoryClickEvent event) {
        if (openInventories.containsKey(event.getInventory())) { //.getTitle.equals(name)) {
            if(event.getSlotType().equals(InventoryType.SlotType.OUTSIDE)
                    || event.getRawSlot() >= CATEGORY_SLOTS
                    || event.getCurrentItem()==null) {//items.size()/9+1)*9 
                return;
            }
            event.setCancelled(true);
            CustomInventoryState state = openInventories.get(event.getInventory());
            if(state instanceof CustomInventoryCollectionState) {
//Logger.getGlobal().info("Create category view.");
                state = new CustomInventoryCategoryState(state);
                openInventories.put(state.inventory, state);
            }
            if(state.isPageLeftSlot(event.getRawSlot())) {
                state.pageLeft();
                state.update();
                return;
            }
            if(state.isPageRightSlot(event.getRawSlot())) {
                state.pageRight();
                state.update();
                return;
            }
            ItemMeta meta = event.getCurrentItem().getItemMeta();
            if(meta!=null && meta.hasLore() && meta.getLore().size()>1 && meta.getLore().get(0).equals(menueItemId)) {
                //setCategory(event.getInventory(),meta.getLore().get(1));
                String category = meta.getLore().get(1);
                if(category.equals("Heads") || category.equals("Blocks")) {
                    Bukkit.getScheduler().scheduleSyncDelayedTask(ArchitectPlugin.getPluginInstance(),
                            ()-> {
                        event.getInventory().close();
                        Bukkit.getScheduler().scheduleSyncDelayedTask(ArchitectPlugin.getPluginInstance(),
                                ()-> {
                                    if(category.equals("Heads")) {
                                        SpecialHeadInventoryData.openInventory((Player) event.getWhoClicked());
                                    } else {
                                        SpecialBlockInventoryData.openInventory((Player) event.getWhoClicked(),
                                                SpecialBlockInventoryData.getRpName((Player) event.getWhoClicked()));
                                    }
                                },1);
                            }, 1);
                }
                state.setCategory(meta.getLore().get(1));
                state.update();
            }
        }
    }
        
    /*@EventHandler(priority=EventPriority.HIGHEST, ignoreCancelled=true)
    void onScroll(final InventoryClickEvent event) {
        if(openInventories.containsKey(event.getInventory())) {
            CustomInventoryState state = openInventories.get(event.getInventory());
            if(event.getClick().equals(ClickType.SHIFT_RIGHT)) {
                state.pageDown();
                state.update();
                event.setCancelled(true);
            } else if(event.getClick().equals(ClickType.SHIFT_LEFT)) {
                state.pageUp();
                state.update();
                event.setCancelled(true);
            }
        }
    }*/
    
    @EventHandler(priority=EventPriority.HIGHEST, ignoreCancelled=false)
    void onCategoryChange(final InventoryClickEvent event) {
//Logger.getGlobal().info("onCategoryChange");
        if(openInventories.containsKey(event.getInventory())
                && event.getAction().equals(InventoryAction.NOTHING)
                && event.getSlotType().equals(SlotType.OUTSIDE)) {
            CustomInventoryState state = openInventories.get(event.getInventory());
            //String category = inventoryCategories.get(event.getInventory());
            if(event.getClick().equals(ClickType.LEFT)) {
                state.previousCategory();
                state.update();
                event.setCancelled(true);
            } else if(event.getClick().equals(ClickType.RIGHT)){
                state.nextCategory();
                state.update();
                event.setCancelled(true);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    void onInventoryEdit(final InventoryClickEvent event) {
        if (openInventories.containsKey(event.getInventory())) { //.getTitle.equals(name)) {
            if (event.getWhoClicked() instanceof Player player
                    && player.hasPermission(Permission.INV_EDIT.getPermissionNode())
                    && event.getRawSlot() >= CATEGORY_SLOTS
                    && event.getRawSlot() < event.getInventory().getSize()
                    && event.isRightClick()
                    && event.isShiftClick()) {//items.size()/9+1)*9
                CustomInventoryState state = openInventories.get(event.getInventory());
                String category = state.categoryNames[state.currentCategory];
                String rpName = RpManager.getCurrentRpName(state.getPlayer());
                event.getInventory().close();
                if(event.getCurrentItem() == null) {
                    CustomInventoryEditor.addBlock(player, rpName, category, state, event.getRawSlot());
                } else {
                    CustomInventoryEditor.editBlock(player, category, state, event.getRawSlot(), event.getCurrentItem());
                }
            }
        }
    }
    
    @EventHandler(priority=EventPriority.MONITOR, ignoreCancelled=true)
    void onClose(final InventoryCloseEvent event) {
        Inventory inventory = event.getInventory();
        CustomInventoryState state = openInventories.get(inventory);
        if(state != null) {
            closedInventoryStates.put(event.getPlayer().getUniqueId(), state);
        }
        openInventories.remove(inventory);
    }
    
    private ItemStack setItemNameAndLore(ItemStack item, String name, String[] lore) {
        ItemMeta im = item.getItemMeta();
            if(name!=null) {
                im.setDisplayName(name);
            }
            if(lore!=null) {
                im.setLore(Arrays.asList(lore));
            }
        item.setItemMeta(im);
        return item;
    }
    
    private ItemStack setMenueItemMeta(ItemStack item, String category) {
        return setItemNameAndLore(item,null,new String[]{menueItemId,category});
    }

    public boolean contains(String id) {
        for(CustomInventoryCategory cat: categories.values()) {
            for(ItemStack item: cat.getItems()) {
                if(SpecialItemInventoryData.getId(item).equals(id)) {
                    return true;
                }
            }
        }
        return false;
    }
    
    public ItemStack getItem(String id) {
        for(CustomInventoryCategory cat: categories.values()) {
            for(ItemStack item: cat.getItems()) {
                if(SpecialItemInventoryData.getId(item).equals(id)) {
                    return item;
                }
            }
        }
        return null;
    }
    
    public boolean isEmpty() {
        for(CustomInventoryCategory cat: categories.values()) {
            if(cat.getItems().size()>0) {
                return false;
            }
        }
        return true;
    }

    private boolean hasCollection(ItemStack currentItem) {
//Logger.getGlobal().info(""+SpecialBlockInventoryData.getSpecialBlockDataFromItem(currentItem).getId());
//Logger.getGlobal().info(""+SpecialBlockInventoryData.getSpecialBlockDataFromItem(currentItem).getCollection().size());
        return SpecialBlockInventoryData.getSpecialBlockDataFromItem(currentItem).hasCollection();
    }

    public String matchCategory(String filter) {
        for(String search: categories.keySet()) {
            if (search.toLowerCase().startsWith(filter.toLowerCase())) {
                return search;
            }
        }
        return null;
    }
}
