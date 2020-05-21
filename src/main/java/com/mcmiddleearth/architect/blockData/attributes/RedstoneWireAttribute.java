/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.mcmiddleearth.architect.blockData.attributes;

import org.bukkit.block.BlockFace;
import org.bukkit.block.data.MultipleFacing;
import org.bukkit.block.data.type.RedstoneWire;
import org.bukkit.configuration.ConfigurationSection;

/**
 *
 * @author Eriol_Eandur
 */
public class RedstoneWireAttribute extends MultiFaceAttribute {

    public RedstoneWireAttribute(String name) {
        super(name, RedstoneWire.class);
    }

    @Override
    public int countStates() {
        return 3;
    }
    
    @Override
    public String getState() {
        return ""+getConnection().name().toLowerCase();
    }

    @Override
    public void cycleState() {
        if(blockData instanceof RedstoneWire) {
            BlockFace face = getCurrentFace();
            RedstoneWire.Connection current = getConnection();
            boolean found = false;
            for(RedstoneWire.Connection search: RedstoneWire.Connection.values()) {
                if(found) {
                    current = search;
                    found = false;
                    break;
                }
                if(search.equals(current)) {
                    found = true;
                }
            }
            if(found) {
                current = RedstoneWire.Connection.values()[0];
            }
            ((RedstoneWire)blockData).setFace(face, current);
        }
    }
    
    private RedstoneWire.Connection getConnection() {
        if(!(blockData instanceof RedstoneWire)) {
            return RedstoneWire.Connection.NONE;
        }
        BlockFace face = getCurrentFace();
        return ((RedstoneWire)blockData).getFace(face);
    }

    @Override
    public void setState(Object newValue) {
        if(blockData instanceof RedstoneWire) {
            BlockFace face = getCurrentFace();
            ((RedstoneWire)blockData).setFace(face, (RedstoneWire.Connection) newValue);
        }
    }

    @Override
    public void loadFromConfig(ConfigurationSection config) {
        BlockFace current = getCurrentFace();
        if(config.contains(name+"_"+current.name())) {
            setState(RedstoneWire.Connection.valueOf(config.getString(name+"_"+current.name())));
        } else {
            setState(RedstoneWire.Connection.NONE);
        }
    }

    @Override
    public void saveToConfig(ConfigurationSection config) {
        BlockFace current = getCurrentFace();
        if(blockData instanceof RedstoneWire 
                && !((RedstoneWire)blockData).getFace(current).equals(RedstoneWire.Connection.NONE)) {
            config.set(name+"_"+current.name(), ((RedstoneWire)blockData).getFace(current).name());
        }
    }
    
}
