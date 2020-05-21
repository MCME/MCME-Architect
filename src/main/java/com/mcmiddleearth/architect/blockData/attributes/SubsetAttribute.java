/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.mcmiddleearth.architect.blockData.attributes;

import java.lang.reflect.InvocationTargetException;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.bukkit.block.data.BlockData;

/**
 *
 * @author Eriol_Eandur
 */
public class SubsetAttribute extends SetAttribute {

    private final String allowedMethod;
    
    public SubsetAttribute(String name, Class<? extends BlockData> clazz, Class<? extends Enum> enumm, String allowedMethod) {
        super(name, clazz, enumm);
        this.allowedMethod = allowedMethod;
    }


    @Override
    public void cycleState() {
        Object current = getValue();
        if(current != null) {
            try {
                Set<Object> allowed = getAllowed();
                Object[] values = (Object[]) enumm.getDeclaredMethod("values").invoke(null);
                boolean found = false;
                for(Object search: values) {
                    if(allowed.contains(search)) {
                        if(found) {
                            clazz.getDeclaredMethod("set"+name, enumm).invoke(blockData, search);
                            found = false;
                            break;
                        }
                        if(current.equals(search)) {
                            found = true;
                        }
                    }
                }
                if(found) {
                    clazz.getDeclaredMethod("set"+name, enumm).invoke(blockData, values[0]);
                }
            } catch (NoSuchMethodException | SecurityException | IllegalAccessException 
                    | IllegalArgumentException | InvocationTargetException ex) {
                Logger.getLogger(SubsetAttribute.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
    }

    protected Set<Object> getAllowed() {
        try {
            return (Set<Object>) clazz.getDeclaredMethod(allowedMethod).invoke(blockData);
        } catch (NoSuchMethodException | SecurityException | IllegalAccessException 
                | IllegalArgumentException | InvocationTargetException ex) {
            Logger.getLogger(SubsetAttribute.class.getName()).log(Level.SEVERE, null, ex);
        }
        return null;
    }
    
    @Override
    public int countStates() {
        return getAllowed().size();
    }
    
    @Override
    public void setState(Object newValue) {
        try {
            Set<Object> allowed = getAllowed();
            Object[] values = (Object[]) enumm.getDeclaredMethod("values").invoke(null);
            for(Object search: values) {
                if(allowed.contains(search) && search.equals(newValue)) {
                    clazz.getDeclaredMethod("set"+name, enumm).invoke(blockData, newValue);
                    return;
                }
            }
        } catch (NoSuchMethodException | SecurityException | IllegalAccessException 
                | IllegalArgumentException | InvocationTargetException ex) {
            Logger.getLogger(SetAttribute.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

}
