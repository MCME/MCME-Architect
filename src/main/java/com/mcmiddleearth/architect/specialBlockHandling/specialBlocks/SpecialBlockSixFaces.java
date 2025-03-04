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
import com.mcmiddleearth.util.DevUtil;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.data.BlockData;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

/**
 *
 * @author Eriol_Eandur
 */
public class SpecialBlockSixFaces extends SpecialBlockOrientable {
    
    private static final Orientation[] sixFaces = new Orientation[] {
        new Orientation(BlockFace.SOUTH,"South"),
        new Orientation(BlockFace.WEST,"West"),
        new Orientation(BlockFace.NORTH,"North"),
        new Orientation(BlockFace.EAST,"East"),
        new Orientation(BlockFace.DOWN,"Down"),
        new Orientation(BlockFace.UP,"Up")
    };

    private SpecialBlockSixFaces(String id, 
                        BlockData[] data) {
        super(id, data, SpecialBlockType.SIX_FACES);
        orientations = sixFaces;
    }
    
    public static SpecialBlockSixFaces loadFromConfig(ConfigurationSection config, String id) {
        BlockData[] data = loadBlockDataFromConfig(config, sixFaces);
        if(data==null) {
            return null;
        }
        return new SpecialBlockSixFaces(id, data);
    }

}
