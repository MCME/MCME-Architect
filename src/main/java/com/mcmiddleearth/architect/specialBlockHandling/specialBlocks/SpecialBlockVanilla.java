/*
 * Copyright (C) 2016 MCME
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
import com.mcmiddleearth.pluginutil.LegacyMaterialUtil;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.data.BlockData;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

import java.util.logging.Logger;

/**
 *
 * @author Eriol_Eandur
 */
public class SpecialBlockVanilla extends SpecialBlock {
    
    private SpecialBlockVanilla(String id, 
                        //Material material, 
                        //byte dataValue,
                        BlockData data) {
        this(id, data, SpecialBlockType.VANILLA);
    }
    
    protected SpecialBlockVanilla(String id, 
                        //Material material, 
                        //byte dataValue,
                        BlockData data,
                        SpecialBlockType type) {
        super(id,data,type);
    }
    
    public static SpecialBlockVanilla loadFromConfig(ConfigurationSection config, String id) {
        //convert old data
        if(config.contains("blockMaterial")) {
            config.set("blockMaterial", null);
            config.set("dataValue",null);
        }
        // end convert old data
//Logger.getGlobal().info("\n"+id);
        String name = config.getString("itemMaterial","AIR");
//Logger.getGlobal().info(name);
        BlockData data = matchBlockData(name);
//Logger.getGlobal().info(data.getAsString());
        return new SpecialBlockVanilla(id, data);
    }

    public static BlockData matchBlockData(String itemMaterial) {
        Material material = Material.AIR;
        if (itemMaterial!=null) {
            material = Material.getMaterial(itemMaterial);
        }
        if(material == null) {
            material = Material.AIR;
        }
//Logger.getGlobal().info(material.name());
        try {
            return material.createBlockData();
        } catch(NullPointerException | IllegalArgumentException ex){
            Logger.getGlobal().warning("SpecialBlockVanilla.matchBlockData "+ex.getMessage());
            return Material.AIR.createBlockData();
        }
    }

    @Override
    public void placeBlock(final Block blockPlace, final BlockFace blockFace, Block clicked,
                           final Location interactionPoint, final Player player) {
    }
    
    @Override
    protected BlockState getBlockState(Block blockPlace, Block clicked, BlockFace blockFace,
                                       Player player, Location interactionPoint) {
        return blockPlace.getState();
    }


}
