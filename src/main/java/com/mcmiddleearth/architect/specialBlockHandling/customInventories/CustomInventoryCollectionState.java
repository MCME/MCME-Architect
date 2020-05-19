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
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

/**
 *
 * @author Eriol_Eandur
 */
public class CustomInventoryCollectionState extends CustomInventoryState {
    
    private SpecialBlock baseBlock;
    
    public CustomInventoryCollectionState(Map<String, CustomInventoryCategory> categories, 
                                          Inventory inventory, Player player, ItemStack baseItem) {
        super(categories, inventory, player);
        stressCurrentCategoryItem = false;
        SpecialBlock tempBase = SpecialBlockInventoryData.getSpecialBlockDataFromItem(baseItem);
//Logger.getGlobal().info("first base: "+tempBase.getId());
        while(tempBase.hasIndirectCollection()) {
            SpecialBlock nextBase = tempBase.getCollectionBase();
            if(nextBase!=null) {
                tempBase = nextBase;
//Logger.getGlobal().info("found base: "+tempBase.getId());
            } else {
                break;
            }
        }
        baseBlock = tempBase;
//Logger.getGlobal().info("final base: "+baseBlock.getId());
    }

    CustomInventoryCollectionState(CustomInventoryState state, ItemStack baseItem) {
        this(state.categories, state.inventory, state.player, baseItem);
    }
    
    @Override
    public void update()  {
        super.update();
        inventory.setItem(CustomInventory.CATEGORY_SLOTS+22, getItem(baseBlock.getId()));
        baseBlock.getCollection().forEach((key,entry)-> {
            List<Character> allowedRows = Arrays.asList(new Character[]{'A','B','C','D','E','F','G','H','I','J','a','b','c','d','e','f','g','h','i','j'});
            char row = key.charAt(0);
            if(allowedRows.contains(row) && NumericUtil.isInt(key.substring(1))) {
                int column = NumericUtil.getInt(key.substring(1));
                if(column > 0 && column < 5) {
                    inventory.setItem(getIndex(row,column), getItem(entry));
                }
            } 
        });
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
        for(CustomInventoryCategory category: categories.values()) {
            ItemStack item = category.getItem(id);
            if(item != null) {
                return item;
            }
        }
        return null;
    }
    
}