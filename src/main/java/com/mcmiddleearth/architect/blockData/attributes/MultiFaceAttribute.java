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
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.MultipleFacing;
import org.bukkit.configuration.ConfigurationSection;

/**
 *
 * @author Eriol_Eandur
 */
public class MultiFaceAttribute extends Attribute {

    public MultiFaceAttribute(String name, Class<? extends BlockData> clazz) {
        super(name, clazz);
    }

    @Override
    @SuppressWarnings("CloneDoesntCallSuperClone")
    public Attribute clone() throws CloneNotSupportedException {
        Attribute clone = new MultiFaceAttribute(name,clazz);
        clone.blockData = this.blockData.clone();
        clone.currentSubAttribute = currentSubAttribute;
        return clone;
    }
    
    @Override
    public int countSubAttributes() {
        if(blockData==null) {
            return 0;
        }
        Set<BlockFace> allowed = getAllowedFaces();
        if(allowed != null) {
            return allowed.size();
        }
        return 0;
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
            return /*super.getName()+"-"+*/getCurrentFace().name().toLowerCase();
        }
    }
    
    @Override
    public String getState() {
        return ""+hasFace();
    }

    public boolean hasFace() {
        if(!clazz.isInstance(blockData)) {
            return false;
        }
        BlockFace face = getCurrentFace();
        try {
            Method getFace = clazz.getDeclaredMethod("hasFace",BlockFace.class);
            boolean hasFace = (boolean) getFace.invoke(blockData,face);
            return hasFace;
        } catch (NoSuchMethodException | SecurityException | IllegalAccessException 
                | IllegalArgumentException | InvocationTargetException ex) {
            Logger.getLogger(MultiFaceAttribute.class.getName()).log(Level.SEVERE, null, ex);
        }
        return false;
    }
    
    private Set<BlockFace> getAllowedFaces() {
        if(blockData==null || !clazz.isInstance(blockData)) {
            return null;
        }
        try {
            Method getAllowed = clazz.getDeclaredMethod("getAllowedFaces");
            Set<BlockFace> allowed = (Set<BlockFace>) getAllowed.invoke(blockData);
            return allowed;
        } catch (NoSuchMethodException | SecurityException | IllegalAccessException 
                | IllegalArgumentException | InvocationTargetException ex) {
            Logger.getLogger(MultiFaceAttribute.class.getName()).log(Level.SEVERE, null, ex);
        }
        return null;
    }
    
    protected BlockFace getCurrentFace() {
        int index = -1;
        Set<BlockFace> allowed = getAllowedFaces();
        if(allowed==null) {
            return null;
        }
        BlockFace last = null;
        for(BlockFace search: BlockFace.values()) {
            if(allowed.contains(search)) {
                index++;
                last = search;
            }
            if(index == currentSubAttribute) {
                return search;
            }
        }
        return last;
    }

    @Override
    public void cycleState() {
        setState(!hasFace());
    }
    
    @Override
    public void setState(Object newValue) {
        if(blockData instanceof MultipleFacing) {
            BlockFace face = getCurrentFace();
            ((MultipleFacing)blockData).setFace(face, (boolean) newValue);
        }
    }

    @Override
    public void loadFromConfig(ConfigurationSection config) {
        BlockFace current = getCurrentFace();
        if(config.contains(name+"_"+current.name())) {
            setState(Boolean.parseBoolean(config.getString(name+"_"+current.name())));
        } else {
            setState(false);
        }
    }

    @Override
    public void saveToConfig(ConfigurationSection config) {
        BlockFace current = getCurrentFace();
        if(blockData instanceof MultipleFacing && ((MultipleFacing)blockData).hasFace(current)) {
            config.set(name+"_"+current.name(), true);
        }
    }
}
