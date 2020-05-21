/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.mcmiddleearth.architect.blockData.attributes;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.bukkit.block.data.BlockData;
import org.bukkit.configuration.ConfigurationSection;

/**
 *
 * @author Eriol_Eandur
 */
public class IntAttribute extends Attribute {

    private int minValue = 0;
    private int maxValue = 0;
    private String maxMethod = "";
    private String minMethod = "";
    public IntAttribute(String name, Class<? extends BlockData> clazz) {
        super(name, clazz);
        maxMethod = "getMaximum"+name;
        minMethod = "getMinimum"+name;
    }
    
    public IntAttribute(String name, Class<? extends BlockData> clazz, int minValue, int maxValue) {
        super(name, clazz);
        this.minValue = minValue;
        this.maxValue = maxValue;
    }
    
    @Override
    @SuppressWarnings("CloneDoesntCallSuperClone")
    public Attribute clone() throws CloneNotSupportedException {
        IntAttribute clone = new IntAttribute(name,clazz,minValue, maxValue);
        clone.maxMethod = maxMethod;
        clone.minMethod = minMethod;
        clone.blockData = this.blockData.clone();
        return clone;
    }
    
    @Override
    public int countSubAttributes() {
        return 1;
    }

    @Override
    public int countStates() {
        return getMaxValue()-getMinValue()+1;
    }
    public int getValue() {
        if(blockData==null) {
            return 0;
        }
        try {
            Method getter = clazz.getDeclaredMethod("get"+name);
            return (int) getter.invoke(blockData);
        } catch (NoSuchMethodException | SecurityException | IllegalAccessException 
                | IllegalArgumentException | InvocationTargetException ex) {
            Logger.getLogger(IntAttribute.class.getName()).log(Level.SEVERE, null, ex);
        }
        return 0;
    }

    @Override
    public String getState() {
        return ""+getValue();
    }
    
    @Override
    public void cycleState() {
        if(blockData!=null) {
            int newValue = getValue()+1;
            int maxValue = getMaxValue();
            int minValue = getMinValue();
            if(newValue > maxValue) {
                newValue = minValue;
            }
            setState(newValue);
        }
    }
    
    @Override
    public void setState(Object newValue) {
        if(blockData!=null) {
            try {
                Method setter = clazz.getDeclaredMethod("set"+name,int.class);
                setter.invoke(blockData,newValue);
            } catch (NoSuchMethodException | SecurityException | IllegalAccessException 
                    | IllegalArgumentException | InvocationTargetException ex) {
                Logger.getLogger(IntAttribute.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
    }
    
    private int getMaxValue() {
        if(maxMethod.equals("")) {
            return maxValue;
        }
        if(blockData!=null) {
            try {
                Method max = clazz.getDeclaredMethod(maxMethod);
                return (int) max.invoke(blockData);
            } catch (NoSuchMethodException | SecurityException | IllegalAccessException 
                    | IllegalArgumentException | InvocationTargetException ex) {
                Logger.getLogger(IntAttribute.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
        return 0;
    }
    
    private int getMinValue() {
        if(minMethod.equals("")) {
            return minValue;
        }
        try {
            Method min = clazz.getDeclaredMethod(minMethod);
            return (int) min.invoke(blockData);
        } catch (NoSuchMethodException e) {} catch (IllegalAccessException | IllegalArgumentException 
                | InvocationTargetException ex) {
            Logger.getLogger(IntAttribute.class.getName()).log(Level.SEVERE, null, ex);
        }
        return 0;
    }
    
    @Override
    public void saveToConfig(ConfigurationSection config) {
        if(getValue()!=0) {
            config.set(name, getValue());
        }
    }
    
    @Override
    public void loadFromConfig(ConfigurationSection config) {
        if(config.contains(name)) {
            setState(Integer.parseInt(config.getString(name)));
        } else {
            setState(0);
        }
    }
    
}
