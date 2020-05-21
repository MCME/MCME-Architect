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
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

/**
 *
 * @author Eriol_Eandur
 */
public class SpecialBlockDoorFourBlocks extends SpecialBlockDoor {
    
    private final Material lowerMaterial, upperMaterial;
    
    private final boolean lowerPowered, upperPowered;
    
    private SpecialBlockDoorFourBlocks(String id, 
                        Material lowerMaterial, Material upperMaterial, 
                        boolean lowerPowered, boolean upperPowered) {
        super(id, Material.AIR, (byte) 0, SpecialBlockType.DOOR_FOUR_BLOCKS);
        this.upperMaterial = upperMaterial;
        this.lowerMaterial = lowerMaterial;
        this.upperPowered = upperPowered;
        this.lowerPowered = lowerPowered;
    }
    
    public static SpecialBlockDoorFourBlocks loadFromConfig(ConfigurationSection config, String id) {
        Material material = matchMaterial(config.getString("blockMaterial",""));
        Material lMaterial = matchMaterial(config.getString("lowerMaterial",""));
        Material uMaterial = matchMaterial(config.getString("upperMaterial",""));
        if(lMaterial==null) {
            lMaterial = material;
        }
        if(uMaterial==null) {
            uMaterial = material;
        }
        if(lMaterial==null || uMaterial==null) {
            return null;
        }
        boolean lPowered = config.getBoolean("lowerPowered", false);
        boolean uPowered = config.getBoolean("upperPowered", true);
        return new SpecialBlockDoorFourBlocks(id, lMaterial, uMaterial, lPowered, uPowered);
    }
    
    @Override
    public void placeBlock(final Block blockPlace, final BlockFace blockFace, final Player player) {
        final Location playerLoc = player.getLocation();
        placeDoor(blockPlace, playerLoc, lowerMaterial, lowerPowered, false, false, false);
        placeDoor(blockPlace.getRelative(BlockFace.UP,2), playerLoc, upperMaterial, upperPowered, false, false, false);
    }
    
}
