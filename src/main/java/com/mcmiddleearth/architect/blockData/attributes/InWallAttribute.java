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
public class InWallAttribute extends BooleanAttribute {
    
    public InWallAttribute(String name, Class<? extends BlockData> clazz) {
        super(name, clazz);
        getMethod = "isInWall";
    }
    
    @Override
    @SuppressWarnings("CloneDoesntCallSuperClone")
    public Attribute clone() throws CloneNotSupportedException {
        Attribute clone = new InWallAttribute(name,clazz);
        clone.blockData = this.blockData.clone();
        return clone;
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
                Method setter = clazz.getDeclaredMethod("setInWall",boolean.class);
                setter.invoke(blockData,newValue);
            } catch (NoSuchMethodException | SecurityException | IllegalAccessException 
                    | IllegalArgumentException | InvocationTargetException ex) {
                Logger.getLogger(InWallAttribute.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
    }
    
}
