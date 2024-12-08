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
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.*;
import java.util.logging.Logger;

/**
 *
 * @author Eriol_Eandur
 */
public class CustomInventoryCategory {
    
    boolean isPublic;
    
    UUID owner;
    
    Set<String> permissions=new HashSet<>();
    
    private ItemStack categoryItem;
    private ItemStack currentCategoryItem;
    
    private final List<ItemStack> items;

    private final boolean usesSubcategories;
    
    public CustomInventoryCategory(UUID owner, boolean isPublic, ItemStack categoryItem,
                                   ItemStack currentCategoryItem, boolean usesSubcategories) {
        //this.name = name;
        this.categoryItem = categoryItem;
        this.currentCategoryItem = currentCategoryItem;
        items = new ArrayList<>();
        this.isPublic = isPublic;
        this.owner = owner;
        this.usesSubcategories = usesSubcategories;
    }
    
    public void addItem(ItemStack item) {
        items.add(item);
    }
    
    public void addPermission(String permission) {
        permissions.add(permission);
    }
    
    public void removePermission(String permission) {
        permissions.remove(permission);
    }
    
    public int size() {
        return items.size();
    }
    
    public boolean isVisible(Player player) {
        if(player.getUniqueId().equals(owner)) {
            return true;
        }
        if(!isPublic) {
            return false;
        } else {
            for(String perm: permissions) {
                if(!player.hasPermission(perm)) {
                    return false;
                }
            }
            return true;
        }
    }
    
    public ItemStack getItem(String id) {
        return items.stream().filter(item -> {
            String blockId = SpecialBlockInventoryData.getSpecialBlockId(item);
            return blockId != null && blockId.equals(id);
        }).findAny().orElse(null);
    }

    public boolean isPublic() {
        return isPublic;
    }

    public UUID getOwner() {
        return owner;
    }

    public ItemStack getCategoryItem() {
        return categoryItem;
    }

    public void setCategoryItem(ItemStack categoryItem) {
        this.categoryItem = categoryItem;
    }

    public ItemStack getCurrentCategoryItem() {
        return currentCategoryItem;
    }

    public void setCurrentCategoryItem(ItemStack currentCategoryItem) {
        this.currentCategoryItem = currentCategoryItem;
    }

    public List<ItemStack> getItems() {
        return items;
    }

    public boolean usesSubcategories() {return usesSubcategories;}
}
