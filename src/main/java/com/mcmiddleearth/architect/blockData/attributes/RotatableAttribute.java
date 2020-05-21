/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.mcmiddleearth.architect.blockData.attributes;

import java.util.ArrayList;
import java.util.List;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Rotatable;
import org.bukkit.configuration.ConfigurationSection;

/**
 *
 * @author Eriol_Eandur
 */
public class RotatableAttribute extends Attribute {

    List<BlockFace> faces = new ArrayList<>();

    public RotatableAttribute(String name) {
        super(name, Rotatable.class);
        faces.add(BlockFace.NORTH);
        faces.add(BlockFace.NORTH_NORTH_EAST);
        faces.add(BlockFace.NORTH_EAST);
        faces.add(BlockFace.EAST_NORTH_EAST);
        faces.add(BlockFace.EAST);
        faces.add(BlockFace.EAST_SOUTH_EAST);
        faces.add(BlockFace.SOUTH_EAST);
        faces.add(BlockFace.SOUTH_SOUTH_EAST);
        faces.add(BlockFace.SOUTH);
        faces.add(BlockFace.SOUTH_SOUTH_WEST);
        faces.add(BlockFace.SOUTH_WEST);
        faces.add(BlockFace.WEST_SOUTH_WEST);
        faces.add(BlockFace.WEST);
        faces.add(BlockFace.WEST_NORTH_WEST);
        faces.add(BlockFace.NORTH_WEST);
        faces.add(BlockFace.NORTH_NORTH_WEST);
    }

    @Override
    public int countSubAttributes() {
        if(blockData!=null) {
            return 1;
        }
        return 0;
    }

    @Override
    public int countStates() {
        return 16;
    }
    
    @Override
    public String getState() {
        if(blockData!=null && (blockData instanceof Rotatable)) {
            return ((Rotatable)blockData).getRotation().name();
        }
        return "null";
    }

    @Override
    public void cycleState() {
        if(blockData!=null && (blockData instanceof Rotatable)) {
            BlockFace current =((Rotatable)blockData).getRotation();
            int index = 0;
            for(int i=0; i<faces.size()-1;i++) {
                if(faces.get(i).equals(current)) {
                    index = i+1;
                    break;
                }
            }
            ((Rotatable)blockData).setRotation(faces.get(index));
        }
    }

    @Override
    public void setState(Object newValue) {
        if(blockData!=null && (blockData instanceof Rotatable)) {
            ((Rotatable)blockData).setRotation((BlockFace) newValue);
        }
    }

    @Override
    public void loadFromConfig(ConfigurationSection config) {
        if(config.contains(name)) {
            setState(BlockFace.valueOf(config.getString(name)));
        } else {
            setState(faces.get(0));
        }
    }

    @Override
    public void saveToConfig(ConfigurationSection config) {
        if(blockData!=null && (blockData instanceof Rotatable)) {
            config.set(name, ((Rotatable)blockData).getRotation().name());
        }
    }
}
