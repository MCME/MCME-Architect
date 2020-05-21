/*
 * Copyright (C) 2017 MCME
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.mcmiddleearth.architect.specialBlockHandling.specialBlocks;

import com.mcmiddleearth.architect.specialBlockHandling.SpecialBlockType;
import static com.mcmiddleearth.architect.specialBlockHandling.specialBlocks.SpecialBlock.matchMaterial;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;

/**
 *
 * @author Eriol_Eandur
 */
public class SpecialBlockVanillaDoor extends SpecialBlockDoor {
    
    private SpecialBlockVanillaDoor(String id, 
                        Material material, boolean powered, boolean hingeRight) {
        super(id, material, powered, hingeRight, SpecialBlockType.DOOR_VANILLA);
    }
    
    public static SpecialBlockDoor loadFromConfig(ConfigurationSection config, String id) {
        Material material = matchMaterial(config.getString("blockMaterial",""));
//Logger.getGlobal().info("VanillaDoor material: "+material); 
        if(material==null) {
            return null;
        }
//Logger.getGlobal().info("VanillaDoor material: "+material.name()); 
        boolean powered = config.getBoolean("powered", false);
        boolean hingeRight = config.getBoolean("hingeRight", false);
        return new SpecialBlockVanillaDoor(id, material, powered, hingeRight);
    }
    

}
