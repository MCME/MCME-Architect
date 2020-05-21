/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.mcmiddleearth.architect.blockData.attributes;

import java.lang.reflect.InvocationTargetException;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.bukkit.block.data.BlockData;
import org.bukkit.configuration.ConfigurationSection;

/**
 *
 * @author Eriol_Eandur
 */
public class SetAttribute extends Attribute {

    protected Class<? extends Enum> enumm;
    
    public SetAttribute(String name, Class<? extends BlockData> clazz, Class<? extends Enum> enumm) {
        super(name, clazz);
        this.enumm = enumm;
    }

    @Override
    public int countSubAttributes() {
        if(blockData==null) {
            return 0;
        }
        return 1;
    }

    @Override
    public int countStates() {
        try {
            return ((Object[])enumm.getMethod("values").invoke(null)).length;
        } catch (NoSuchMethodException | SecurityException | IllegalAccessException 
                | IllegalArgumentException | InvocationTargetException ex) {
            Logger.getLogger(SetAttribute.class.getName()).log(Level.SEVERE, null, ex);
        }
        return 0;
    }
    
    @Override
    public String getState() {
        return ""+getValue();
    }

    public Object getValue() {
        try {
            return clazz.getDeclaredMethod("get"+name).invoke(blockData);
        } catch (NoSuchMethodException | SecurityException | IllegalAccessException 
                | IllegalArgumentException | InvocationTargetException ex) {
            Logger.getLogger(SetAttribute.class.getName()).log(Level.SEVERE, null, ex);
        }
        return null;
    }

    @Override
    public void cycleState() {
        Object current = getValue();
        if(current != null) {
            try {
                Object[] values = (Object[]) enumm.getDeclaredMethod("values").invoke(null);
                boolean found = false;
                for(Object search: values) {
                    if(found) {
                        try {
                            clazz.getDeclaredMethod("set"+name, enumm).invoke(blockData, search);
                            found = false;
                            break;
                        } catch (IllegalArgumentException e) {
                            Logger.getGlobal().info("******************IllegalArgumentException catched");
                        }
                    }
                    if(current.equals(search)) {
                        found = true;
                    }
                }
                if(found) {
                    for(int i=0; i<values.length;i++) {
                        try {
                            clazz.getDeclaredMethod("set"+name, enumm).invoke(blockData, values[i]);
                            break;
                        } catch(IllegalArgumentException e) {};
                    }
                }
            } catch (NoSuchMethodException | SecurityException | IllegalAccessException 
                    | IllegalArgumentException | InvocationTargetException ex) {
                Logger.getLogger(SetAttribute.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
    }
    
    @Override
    public void setState(Object newValue) {
        try {
            Object[] values = (Object[]) enumm.getDeclaredMethod("values").invoke(null);
            for(Object search: values) {
                if(search.equals(newValue)) {
                    clazz.getDeclaredMethod("set"+name, enumm).invoke(blockData, newValue);
                    return;
                }
            }
        } catch (NoSuchMethodException | SecurityException | IllegalAccessException 
                | IllegalArgumentException | InvocationTargetException ex) {
            Logger.getLogger(SetAttribute.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    @Override
    public void loadFromConfig(ConfigurationSection config) {
        String valueName = "";
        if(config.contains(name)) {
            valueName = config.getString(name);
        } else {
            try {
                valueName = enumm.cast(((Object[]) enumm.getDeclaredMethod("values").invoke(null))[0]).name();
            } catch (NoSuchMethodException | SecurityException | IllegalAccessException 
                    | IllegalArgumentException | InvocationTargetException ex) {
                Logger.getLogger(SetAttribute.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
        try {
            setState(enumm.getDeclaredMethod("valueOf",String.class).invoke(null,valueName));
        } catch (NoSuchMethodException | SecurityException | IllegalAccessException 
                | IllegalArgumentException | InvocationTargetException ex) {
            Logger.getLogger(SetAttribute.class.getName()).log(Level.SEVERE, null, ex);
        }
    }
 
    @Override
    public void saveToConfig(ConfigurationSection config) {
            config.set(name, getValue().toString());
    }

    
}
