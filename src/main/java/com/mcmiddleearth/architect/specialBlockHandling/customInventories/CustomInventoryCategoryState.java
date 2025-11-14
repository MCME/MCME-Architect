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

import java.util.List;
import java.util.Map;

import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

/**
 *
 * @author Eriol_Eandur
 */
public class CustomInventoryCategoryState extends CustomInventoryState {
    
    private int upperLeftItem;

    public CustomInventoryCategoryState(Map<String, CustomInventoryCategory> categories, CustomInventoryCategory withoutCategory,
                                        Inventory inventory, Player player) {
        super(categories, withoutCategory, inventory, player);
        upperLeftItem = 0;
    }

    CustomInventoryCategoryState(CustomInventoryState state) {
        this(state.categories,state.withoutCategory,state.inventory,state.player);
        currentCategory = state.currentCategory;
        leftCategory = state.leftCategory;
        if(state instanceof CustomInventoryCollectionState collectionState) {
            upperLeftItem = collectionState.getReturnUpperLeftItem();
        }
    }
    
    @Override
    public void update()  {
        super.update();
        CustomInventoryCategory category = categories.get(categoryNames[currentCategory]);
        int slotIndex=0;
        if(category.isVisible(player)) {
            slotIndex = CustomInventory.CATEGORY_SLOTS;
            List<ItemStack> items = category.getItems();
            for (int i = upperLeftItem; i < upperLeftItem + visibleItemSlots()
                                      && i < items.size(); i++) {
                    if(slotIndex==CustomInventory.CATEGORY_SLOTS+8 && !isFirstItemVisible()) { //leave pageUp slot empty if needed
                        slotIndex++;
                    }
                    inventory.setItem(slotIndex, items.get(i));
                    slotIndex++;
                }
            if(!isFirstItemVisible()) {
                //inventory.setItem(slotIndex, newPagingItem(pagingMaterial,pageUp, "page up"));
                inventory.setItem(CustomInventory.CATEGORY_SLOTS+8,
                                  newPagingItem(pagingMaterial,pageUp, "page up"));
            }
            if(!isLastItemVisible()) {
                inventory.setItem(CustomInventory.CATEGORY_SLOTS+CustomInventory.ITEM_SLOTS-1,
                                  newPagingItem(pagingMaterial,pageDown, "page down"));
            }
        }
    }
    
    @Override
    protected void setCategory(int newCategory) {
        upperLeftItem = 0;
        super.setCategory(newCategory);
    }
    
    @Override
    public void pageDown() {
        if(!isLastItemVisible()) {
            upperLeftItem += visibleItemSlots();
        }
    }
    
    @Override
    public void pageUp() {
        if(!isFirstItemVisible()) {
            if(isLastItemVisible()) {
                upperLeftItem -=visibleItemSlots()-1;
            } else {
                upperLeftItem -= visibleItemSlots();
            }
            if(upperLeftItem==1) {
                upperLeftItem=0;
            }
            if(upperLeftItem<0) {
                upperLeftItem = 0 ;
            }
        }
    }
    
    @Override
    public boolean isPageUpSlot(int slot) {
        return (slot == CustomInventory.CATEGORY_SLOTS+8 && !isFirstItemVisible());
    }
    
    @Override
    public boolean isPageDownSlot(int slot) {
        return (slot == CustomInventory.CATEGORY_SLOTS + CustomInventory.ITEM_SLOTS - 1
                && !isLastItemVisible());
    }
    
    private int visibleItemSlots() {
        CustomInventoryCategory category = categories.get(categoryNames[currentCategory]);
        if(category.size()<=CustomInventory.ITEM_SLOTS) {
            return CustomInventory.ITEM_SLOTS;
        } else  if(isFirstItemVisible() || isLastItemVisible()) {
            return CustomInventory.ITEM_SLOTS-1;
        } else {
            return CustomInventory.ITEM_SLOTS-2;
        }
    }
    
    private boolean isFirstItemVisible() {
        return upperLeftItem == 0;
    }
    
    private boolean isLastItemVisible() {
        CustomInventoryCategory category = categories.get(categoryNames[currentCategory]);
        if(category.size()<=CustomInventory.ITEM_SLOTS) {
            return true;
        } else {
            return category.size() <= upperLeftItem + CustomInventory.ITEM_SLOTS-2;
        }
    }

    @Override
    public boolean usesSubcategories() {return categories.get(categoryNames[currentCategory]).usesSubcategories();}

    protected int getUpperLeftItem() {
        return upperLeftItem;
    }

}