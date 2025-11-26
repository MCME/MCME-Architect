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

import com.mcmiddleearth.architect.specialBlockHandling.data.SpecialBlockInventoryData;
import com.mcmiddleearth.architect.specialBlockHandling.specialBlocks.SpecialBlock;
import com.mcmiddleearth.pluginutil.NumericUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;
import java.util.logging.Logger;

/**
 *
 * @author Eriol_Eandur
 */
public class CustomInventoryCollectionState extends CustomInventoryState {
    
    private final SpecialBlock baseBlock;

    private int returnCategory;
    private int returnUpperLeftItem;
    private String returnSubcategory;

    private final int maskSlot = CustomInventory.CATEGORY_SLOTS + 15;
    private final int centerSlot = CustomInventory.CATEGORY_SLOTS + 22;

    private final ItemStack maskItem = new ItemStack(Material.PAPER,1);

    private boolean directGet;

    public CustomInventoryCollectionState(Map<String, CustomInventoryCategory> categories, CustomInventoryCategory withoutCategory,
                                          Inventory inventory, Player player, ItemStack baseItem, boolean directGet) {
        super(categories, withoutCategory, inventory, player);
        this.directGet = directGet;
        stressCurrentCategoryItem = false;
        SpecialBlock tempBase = SpecialBlockInventoryData.getSpecialBlockDataFromItem(baseItem);
        Set<SpecialBlock> visited = new HashSet<>();
        while(tempBase.hasIndirectCollection() && !visited.contains(tempBase)) {
            visited.add(tempBase);
            SpecialBlock nextBase = tempBase.getCollectionBase();
            if(nextBase!=null) {
                tempBase = nextBase;
            } else {
                break;
            }
        }
        baseBlock = tempBase;
        ItemMeta meta = maskItem.getItemMeta();//Bukkit.getServer().getItemFactory().getItemMeta(Material.GOLDEN_HELMET);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_UNBREAKABLE);
        meta.setUnbreakable(true);
        //((Damageable)meta).setDamage(35);
        meta.setCustomModelData(35);
        maskItem.setItemMeta(meta);
        String foundCat = null;
        for(Map.Entry<String,CustomInventoryCategory> cat: categories.entrySet()) {
            if(cat.getValue().getItem(baseBlock.getId())!=null) {
                foundCat = cat.getKey();
                break;
            }
        }
    }
    
    public CustomInventoryCollectionState(Map<String, CustomInventoryCategory> categories, CustomInventoryCategory withoutCategory,
                                          Inventory inventory, Player player, String inventoryName, ItemStack baseItem, boolean directGet) {
        super(categories, withoutCategory, inventory, player, inventoryName);
        this.directGet = directGet;
        stressCurrentCategoryItem = false;
        SpecialBlock tempBase = SpecialBlockInventoryData.getSpecialBlockDataFromItem(baseItem);
        Set<SpecialBlock> visited = new HashSet<>();
        while(tempBase.hasIndirectCollection() && !visited.contains(tempBase)) {
            visited.add(tempBase);
            SpecialBlock nextBase = tempBase.getCollectionBase();
            if(nextBase!=null) {
                tempBase = nextBase;
            } else {
                break;
            }
        }
        baseBlock = tempBase;
        ItemMeta meta = maskItem.getItemMeta();//Bukkit.getServer().getItemFactory().getItemMeta(Material.GOLDEN_HELMET);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_UNBREAKABLE);
        meta.setUnbreakable(true);
        //((Damageable)meta).setDamage(35);
        meta.setCustomModelData(35);
        maskItem.setItemMeta(meta);
        String foundCat = null;
        for(Map.Entry<String,CustomInventoryCategory> cat: categories.entrySet()) {
            if(cat.getValue().getItem(baseBlock.getId())!=null) {
                foundCat = cat.getKey();
                break;
            }
        }
        currentCategory = 0;
        returnCategory = -1;
        if(foundCat!=null) {
            for (int i = 0; i < categoryNames.length; i++) {
                if (categoryNames[i].equals(foundCat)) {
                    currentCategory = i;
                    returnCategory = i;
                    break;
                }
            }
        }
    }

    CustomInventoryCollectionState(CustomInventoryState state, ItemStack baseItem) {
        this(state.categories, state.withoutCategory, state.inventory, state.player, state.inventoryName, baseItem, false);
        // Preserve the category and left category position from the parent state
        currentCategory = state.currentCategory;
        leftCategory = state.leftCategory;
        returnCategory = state.currentCategory;
        if(state instanceof CustomInventoryCategoryState categoryState) {
            returnUpperLeftItem = categoryState.getUpperLeftItem();
            returnSubcategory = categoryState.getCurrentSubcategory();
        }
    }
    
    @Override
    public void update()  {
        // Keep the category buttons/top row from parent state
        super.update();
        baseBlock.getCollection().forEach((key,entry)-> {
            List<Character> allowedRows = Arrays.asList('A','B','C','D','E','F','G','H','I','J','a','b','c','d','e','f','g','h','i','j');
            char row = key.charAt(0);
            if(allowedRows.contains(row) && NumericUtil.isInt(key.substring(1))) {
                int column = NumericUtil.getInt(key.substring(1));
                if(column >= 0 && column < 5) {
                    inventory.setItem(getIndex(row,column), getItem(entry));
                }
            } 
        });
        inventory.setItem(maskSlot, maskItem);
        // Center slot now available for blocks, no back button
        inventory.setItem(centerSlot, getItem(baseBlock.getId()));
    }

    private ItemStack customItem(int value) {
        ItemStack item = new ItemStack(Material.BROWN_DYE);
        ItemMeta meta = Bukkit.getServer().getItemFactory().getItemMeta(Material.BROWN_DYE);
        meta.setCustomModelData(value);
        item.setItemMeta(meta);
        return item;
    }

    private int getIndex(char row, int column) {
        int rowBase=CustomInventory.CATEGORY_SLOTS+22;
        boolean rowAdd=true;
        switch(row) {
            case 'A':
            case 'a':
                rowBase = CustomInventory.CATEGORY_SLOTS+4;
                rowAdd = false;
                break;
            case 'B':
            case 'b':
                rowBase = CustomInventory.CATEGORY_SLOTS+13;
                rowAdd = false;
                break;
            case 'C':
            case 'c':
                rowBase = CustomInventory.CATEGORY_SLOTS+22;
                rowAdd = false;
                break;
            case 'D':
            case 'd':
                rowBase = CustomInventory.CATEGORY_SLOTS+31;
                rowAdd = false;
                break;
            case 'E':
            case 'e':
                rowBase = CustomInventory.CATEGORY_SLOTS+40;
                rowAdd = false;
                break;
            case 'F':
            case 'f':
                rowBase = CustomInventory.CATEGORY_SLOTS+4;
                rowAdd = true;
                break;
            case 'G':
            case 'g':
                rowBase = CustomInventory.CATEGORY_SLOTS+13;
                rowAdd = true;
                break;
            case 'H':
            case 'h':
                rowBase = CustomInventory.CATEGORY_SLOTS+22;
                rowAdd = true;
                break;
            case 'I':
            case 'i':
                rowBase = CustomInventory.CATEGORY_SLOTS+31;
                rowAdd = true;
                break;
            case 'J':
            case 'j':
                rowBase = CustomInventory.CATEGORY_SLOTS+40;
                rowAdd = true;
                break;
        }
        return rowAdd ? rowBase+column : rowBase-column;
    }
    
    private ItemStack getItem(String id) {
        ItemStack item = withoutCategory.getItem(id);
        if(item != null) {
            return item;
        }
        for(CustomInventoryCategory category: categories.values()) {
            item = category.getItem(id);
            if(item != null) {
                return item;
            }
        }
        return null;
    }

    @Override
    public boolean usesSubcategories() {return false;}

    public int getMaskSlot() {
        return maskSlot;
    }

    public int getCenterSlot() {
        return centerSlot;
    }

    public boolean isDirectGet() {
        return directGet;
    }

    public void setDirectGet(boolean directGet) {
        this.directGet = directGet;
    }

    public SpecialBlock getBaseBlock() {
        return baseBlock;
    }

    public int getReturnUpperLeftItem() {
        return returnUpperLeftItem;
    }
    
    public String getReturnSubcategory() {
        return returnSubcategory;
    }
}