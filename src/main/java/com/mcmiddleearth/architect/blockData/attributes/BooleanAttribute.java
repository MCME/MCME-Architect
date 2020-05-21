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
public class BooleanAttribute extends Attribute {

    protected String getMethod = "";
    
    public BooleanAttribute(String name, Class<? extends BlockData> clazz) {
        super(name, clazz);
        getMethod = "is"+name;
    }
    
    public BooleanAttribute(String name, Class<? extends BlockData> clazz, String getMethod) {
        super(name, clazz);
        this.getMethod = getMethod;
    }
    
    @Override
    @SuppressWarnings("CloneDoesntCallSuperClone")
    public Attribute clone() throws CloneNotSupportedException {
        Attribute clone = new BooleanAttribute(name,clazz,getMethod);
        clone.blockData = this.blockData.clone();
        return clone;
    }
    
    @Override
    public int countSubAttributes() {
        return 1;
    }

    @Override
    public int countStates() {
        return 2;
    }
    
    public boolean getValue() {
        if(blockData==null) {
            return false;
        }
        try {
            Method getter = clazz.getDeclaredMethod(getMethod);
            return (boolean) getter.invoke(blockData);
        } catch (NoSuchMethodException | SecurityException | IllegalAccessException 
                | IllegalArgumentException | InvocationTargetException ex) {
            Logger.getLogger(BooleanAttribute.class.getName()).log(Level.SEVERE, null, ex);
        }
        return false;
    }

    @Override
    public String getState() {
        return ""+getValue();
    }
    
    @Override
    public void cycleState() {
        if(blockData!=null) {
            setState(!getValue());
        }
    }
    
    @Override
    public void setState(Object newValue) {
        if(blockData!=null) {
            try {
                Method setter = clazz.getDeclaredMethod("set"+name,boolean.class);
                setter.invoke(blockData,newValue);
            } catch (NoSuchMethodException | SecurityException | IllegalAccessException 
                    | IllegalArgumentException | InvocationTargetException ex) {
                Logger.getLogger(BooleanAttribute.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
    }
    
    @Override
    public void saveToConfig(ConfigurationSection config) {
        if(getValue()) {
            config.set(name, getValue());
        }
    }
    
    @Override
    public void loadFromConfig(ConfigurationSection config) {
        if(config.contains(name)) {
            setState(Boolean.parseBoolean(config.getString(name)));
        } else {
            setState(false);
        }
    }
    
    
}
