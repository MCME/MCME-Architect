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

import java.util.Map;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

/**
 *
 * @author Eriol_Eandur
 */
public abstract class CustomInventoryState {
    
    final static public Material pagingMaterial = Material.GOLDEN_HELMET;
    final static public short pageUp = 1;
    final static public short pageDown = 2;
    final static public short pageLeft = 3;
    final static public short pageRight = 4;
    
    protected final Map<String,CustomInventoryCategory> categories;

    protected final String[] categoryNames;
    
    protected boolean stressCurrentCategoryItem = true;
    
    protected int currentCategory;

    protected int leftCategory;
    
    protected final Inventory inventory;
    
    protected final Player player;
    
    
    
    public CustomInventoryState(Map<String, CustomInventoryCategory> categories, Inventory inventory, Player player) {
        this.categories = categories;
        this.categoryNames = categories.keySet().toArray(new String[0]);
        this.currentCategory = 0;
        this.leftCategory = 0;
        this.inventory = inventory;
        this.player = player;
        //showCat();
    }
    
    public void update()  {
        inventory.clear();
        CustomInventoryCategory category = categories.get(categoryNames[currentCategory]);
        if(!category.isVisible(player)) {
            nextCategory();
            category = categories.get(categoryNames[currentCategory]);
        }
        int slotIndex=0;
        if(!isFirstCategoryVisible()) {
            inventory.setItem(slotIndex, newPagingItem(pagingMaterial,pageLeft,"previous Categories"));
            slotIndex++;
        }
        for (int i = leftCategory; i< leftCategory+visibleCategorySlots()
                                && i< categories.size(); i++) {
            ItemStack item;
            CustomInventoryCategory cat = categories.get(categoryNames[i]);
            if(cat.isVisible(player)) {
                if(stressCurrentCategoryItem && i==currentCategory) {
                     item = new ItemStack(cat.getCurrentCategoryItem());
                     ItemMeta meta = item.getItemMeta();
                     meta.setUnbreakable(false);
                     item.setItemMeta(meta);
                } else {
                     item = new ItemStack(cat.getCategoryItem());
                }
                inventory.setItem(slotIndex, item);
                slotIndex++;
            } else {
            }
        }
        if(!isLastCategoryVisible()) {
            inventory.setItem(CustomInventory.CATEGORY_SLOTS-1, newPagingItem(pagingMaterial,pageRight,"next Categories"));
        }
    }
    
    public void nextCategory() {
        int newCategory = currentCategory;
        while(newCategory<categoryNames.length-1 
                && !categories.get(categoryNames[newCategory]).isVisible(player)) {
            newCategory++;
        }
        if(categories.get(categoryNames[newCategory]).isVisible(player)) {
            setCategory(newCategory);
        }
    }
    
    public void previousCategory() {
        int newCategory = currentCategory;
        while(newCategory > 0 
                && !categories.get(categoryNames[newCategory]).isVisible(player)) {
            newCategory--;
        }
        if(categories.get(categoryNames[newCategory]).isVisible(player)) {
            setCategory(newCategory);
        }
    }
    
    public void setCategory(String category) {
        if(!categories.get(category).isVisible(player)) {
            return;
        }
        if(categories.containsKey(category)) {
            int newCategory = categoryIndexOf(category);
            setCategory(newCategory);
        }
    }
    
    protected void setCategory(int newCategory) {
        while(newCategory>=leftCategory+visibleCategorySlots()) {
            leftCategory -= visibleCategorySlots();
            if(leftCategory < 0) {
                leftCategory = 0;
            }
        }
        while(newCategory<leftCategory) {
            leftCategory += visibleCategorySlots();
        }
        currentCategory = newCategory;
    }
    
    public void pageRight() {
        if(!isLastCategoryVisible()) {
            leftCategory += visibleCategorySlots();
            if(currentCategory<leftCategory) {
                setCategory(leftCategory);
            }
        }
    }
    
    public void pageLeft() {
        if(!isFirstCategoryVisible()) {
            leftCategory -= visibleCategorySlots();
            if(leftCategory < 0) {
                leftCategory = 0;
            }
            if(currentCategory>=leftCategory+visibleCategorySlots()) {
                setCategory(Math.min(leftCategory+visibleCategorySlots()-1,categoryNames.length-1));
            }
        }
    }
    
    public void pageDown(){}
    
    public void pageUp(){}
    
    public boolean isPageUpSlot(int slot) {
        return false;
    }
    
    public boolean isPageDownSlot(int slot) {
        return false;
    }
    
    public boolean isPageLeftSlot(int slot) {
        return (slot == 0 && !isFirstCategoryVisible());
    }
    
    public boolean isPageRightSlot(int slot) {
        return (slot == CustomInventory.CATEGORY_SLOTS-1
                && !isLastCategoryVisible());
    }
    
    private int visibleCategorySlots() {
        if(countVisibleCategories()<=CustomInventory.CATEGORY_SLOTS) {
            return CustomInventory.CATEGORY_SLOTS;
        } else  if(isFirstCategoryVisible() || isLastCategoryVisible()) {
            return CustomInventory.CATEGORY_SLOTS-1;
        } else {
            return CustomInventory.CATEGORY_SLOTS-2;
        }
    }
    
    private int categoryIndexOf(String category) {
        for(int i=0; i<categoryNames.length; i++) {
            if(categoryNames[i].equals(category)) {
                return i;
            }
        }
        return -1;
    }
    
    private boolean isFirstCategoryVisible() {
        return countVisibleCategoriesBefore(leftCategory) == 0;
    }
    
    private boolean isLastCategoryVisible() {
        if(countVisibleCategories()<=CustomInventory.CATEGORY_SLOTS) {
            return true;
        } else {
            return countVisibleCategoriesAfter(leftCategory) <= CustomInventory.CATEGORY_SLOTS-1;
        }
    }
    
    private int countVisibleCategoriesBefore(int end) {
        int result = 0;
        for(int i=0;i<categories.size() && i<end; i++) {
            CustomInventoryCategory category = categories.get(categoryNames[i]);
            if(category.isVisible(player)) {
                result++;
            }
        }
        return result;
    }
    
    private int countVisibleCategoriesAfter(int start) {
        int result = 0;
        for(int i=start+1;i<categories.size();i++) {
            CustomInventoryCategory category = categories.get(categoryNames[i]);
            if(category.isVisible(player)) {
                result++;
            }
        }
        return result;
    }
    
    private int countVisibleCategories() {
        int result = 0;
        for(int i=0;i<categories.size();i++) {
            CustomInventoryCategory category = categories.get(categoryNames[i]);
            if(category.isVisible(player)) {
                result++;
            }
        }
        return result;
    }
    
    protected ItemStack newPagingItem(Material material, short damage, String display) {
        ItemStack item = new ItemStack(material,1,damage);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(display);
        meta.setUnbreakable(true);
        meta.addItemFlags(ItemFlag.HIDE_UNBREAKABLE);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        item.setItemMeta(meta);
        return item;
    }
}