/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.mcmiddleearth.architect.blockData.attributes;

import org.bukkit.block.data.BlockData;
import org.bukkit.configuration.ConfigurationSection;

/**
 *
 * @author Eriol_Eandur
 */
public abstract class Attribute {

    protected int currentSubAttribute = 0;
    
    protected Class<? extends BlockData> clazz;
    
    protected String name;
    
    protected BlockData blockData;

    public Attribute(String name, Class<? extends BlockData> clazz) {
        this.name = name;
        this.clazz = clazz;
       
    }
    
    public boolean isInstance(BlockData data) {
        return clazz.isInstance(data);
    }
    
    public void setBlockData(BlockData data) {
        if(isInstance(data)) {
            blockData = data;
        } else {
            blockData = null;
        }
    }
    
    public String getName() {
        return name;
    }
    
    public void setCurrentSubAttribute(int index) {
        if(index >= 0 && index < countSubAttributes()) {
            currentSubAttribute = index;
        }
    }

    public abstract int countSubAttributes();
    
    public abstract int countStates();

    public abstract String getState();    
    public abstract void setState(Object newState);    
    public abstract void cycleState();
    
    public abstract void loadFromConfig(ConfigurationSection config);
    public abstract void saveToConfig(ConfigurationSection config);

    public int getCurrentSubAttribute() {
        return currentSubAttribute;
    }
}
