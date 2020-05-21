/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.mcmiddleearth.architect.blockData.attributes;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.bukkit.block.data.type.BrewingStand;
import org.bukkit.configuration.ConfigurationSection;

/**
 *
 * @author Eriol_Eandur
 */
public class BrewingStandAttribute extends Attribute {

    public BrewingStandAttribute(String name) {
        super(name, BrewingStand.class);
    }

    @Override
    @SuppressWarnings("CloneDoesntCallSuperClone")
    public Attribute clone() throws CloneNotSupportedException {
        Attribute clone = new BrewingStandAttribute(name);
        clone.blockData = this.blockData.clone();
        return clone;
    }
    
    @Override
    public int countSubAttributes() {
        if(blockData==null || !(blockData instanceof BrewingStand)) {
            return 0;
        }
        return ((BrewingStand)blockData).getMaximumBottles();
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
            return "has_"+super.getName()+"_"+getCurrentBottle();
        }
    }
    
    @Override
    public String getState() {
        return ""+hasBottle();
    }

    public boolean hasBottle() {
        if(!(blockData instanceof BrewingStand)) {
            return false;
        }
        return ((BrewingStand)blockData).hasBottle(getCurrentBottle());
    }
    
    private Set<Integer> getBottles() {
        if(blockData==null || !clazz.isInstance(blockData)) {
            return null;
        }
        try {
            Method getAllowed = clazz.getDeclaredMethod("getBottles");
            Set<Integer> allowed = (Set<Integer>) getAllowed.invoke(blockData);
            return allowed;
        } catch (NoSuchMethodException | SecurityException | IllegalAccessException 
                | IllegalArgumentException | InvocationTargetException ex) {
            Logger.getLogger(BrewingStandAttribute.class.getName()).log(Level.SEVERE, null, ex);
        }
        return null;
    }
    
    private int getCurrentBottle() {
        if(blockData instanceof BrewingStand) {
            int result = currentSubAttribute;
            if(result >= ((BrewingStand)blockData).getMaximumBottles())
                return ((BrewingStand)blockData).getMaximumBottles()-1;
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
        if(blockData instanceof BrewingStand) {
            boolean newValue = !((BrewingStand)blockData).hasBottle(getCurrentBottle());
            ((BrewingStand)blockData).setBottle(getCurrentBottle(), newValue);
        }
    }
    
    @Override
    public void setState(Object newValue) {
        if(blockData instanceof BrewingStand) {
            ((BrewingStand)blockData).setBottle(getCurrentBottle(), (boolean) newValue);
        }
    }

    @Override
    public void loadFromConfig(ConfigurationSection config) {
        int current = getCurrentBottle();
        if(config.contains(name+"_"+current)) {
            setState(Boolean.parseBoolean(config.getString(name+"_"+current)));
        } else {
            setState(false);
        }
    }

    @Override
    public void saveToConfig(ConfigurationSection config) {
        int current = getCurrentBottle();
        if(blockData instanceof BrewingStand && ((BrewingStand)blockData).hasBottle(current)) {
            config.set(name+"_"+current, true);
        }
    }
}
