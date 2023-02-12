/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.mcmiddleearth.architect.blockData.attributes;

import org.bukkit.block.data.type.BrewingStand;
import org.bukkit.block.data.type.ChiseledBookshelf;
import org.bukkit.configuration.ConfigurationSection;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author Eriol_Eandur
 */
public class BookshelfAttribute extends Attribute {

    public BookshelfAttribute(String name) {
        super(name, ChiseledBookshelf.class);
    }

    @Override
    @SuppressWarnings("CloneDoesntCallSuperClone")
    public Attribute clone() throws CloneNotSupportedException {
        Attribute clone = new BookshelfAttribute(name);
        clone.blockData = this.blockData.clone();
        return clone;
    }
    
    @Override
    public int countSubAttributes() {
        if(blockData==null || !(blockData instanceof ChiseledBookshelf)) {
            return 0;
        }
        return ((ChiseledBookshelf)blockData).getMaximumOccupiedSlots();
    }

    @Override
    public int countStates() {
        return 2;
    }
    
    @Override
    public String getName() {
        if(blockData == null) {
            return super.getName();
        } else {
            return "slot_"+ getCurrentSlot()+"_"+super.getName();
        }
    }
    
    @Override
    public String getState() {
        return ""+ isSlotOccupied();
    }

    public boolean isSlotOccupied() {
        if(!(blockData instanceof ChiseledBookshelf)) {
            return false;
        }
        return ((ChiseledBookshelf)blockData).isSlotOccupied(getCurrentSlot());
    }
    
    private Set<Integer> getOccupiedSlots() {
        if(blockData==null || !clazz.isInstance(blockData)) {
            return null;
        }
        try {
            Method getAllowed = clazz.getDeclaredMethod("getOccupiedSlots");
            Set<Integer> allowed = (Set<Integer>) getAllowed.invoke(blockData);
            return allowed;
        } catch (NoSuchMethodException | SecurityException | IllegalAccessException 
                | IllegalArgumentException | InvocationTargetException ex) {
            Logger.getLogger(BookshelfAttribute.class.getName()).log(Level.SEVERE, null, ex);
        }
        return null;
    }
    
    private int getCurrentSlot() {
        if(blockData instanceof BrewingStand) {
            int result = currentSubAttribute;
            if(result >= ((ChiseledBookshelf)blockData).getMaximumOccupiedSlots())
                return ((ChiseledBookshelf)blockData).getMaximumOccupiedSlots()-1;
            if(result < 0) {
                return 0;
            }
            return result;
        } else {
            return 0;
        }
    }

    @Override
    public void cycleState() {
        if(blockData instanceof ChiseledBookshelf) {
            boolean newValue = !((ChiseledBookshelf)blockData).isSlotOccupied(getCurrentSlot());
            ((ChiseledBookshelf)blockData).setSlotOccupied(getCurrentSlot(), newValue);
        }
    }
    
    @Override
    public void setState(Object newValue) {
        if(blockData instanceof ChiseledBookshelf) {
            ((ChiseledBookshelf)blockData).setSlotOccupied(getCurrentSlot(), (boolean) newValue);
        }
    }

    @Override
    public void loadFromConfig(ConfigurationSection config) {
        int current = getCurrentSlot();
        if(config.contains("slot"+"_"+current+"_"+name)) {
            setState(Boolean.parseBoolean(config.getString("slot"+"_"+current+"_"+name)));
        } else {
            setState(false);
        }
    }

    @Override
    public void saveToConfig(ConfigurationSection config) {
        int current = getCurrentSlot();
        if(blockData instanceof ChiseledBookshelf && ((ChiseledBookshelf)blockData).isSlotOccupied(current)) {
            config.set("slot"+"_"+current+"_"+name, true);
        }
    }
}
