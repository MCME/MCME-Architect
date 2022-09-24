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
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

/**
 *
 * @author Eriol_Eandur
 */
public class SpecialBlockBisected extends SpecialBlockOrientable {
    
    private static final Orientation[] half = new Orientation[] {
        new Orientation(BlockFace.UP,"Down"),
        new Orientation(BlockFace.DOWN,"Up")
    };

    private SpecialBlockBisected(String id, 
                        BlockData[] data) {
        super(id, data, SpecialBlockType.THREE_AXIS);
        orientations = half;
    }
    
    @Override
    public void placeBlock(final Block blockPlace, final BlockFace blockFace, Block clicked,
                           final Location interactionPoint, final Player player) {
        BlockFace tempFace = blockFace;
        switch(blockFace) {
            case NORTH:
            case SOUTH:
            case WEST:
            case EAST:
                RayTraceResult traceResult = player.rayTraceBlocks(Bukkit.getServer().getViewDistance()*16);
                Vector hit = traceResult.getHitPosition();
                if(hit.getY()-hit.getBlockY()<0.5) {
                    tempFace = BlockFace.UP;
                } else {
                    tempFace = BlockFace.DOWN;
                }
        }
        super.placeBlock(blockPlace, tempFace, clicked, interactionPoint, player);
    }

    public static SpecialBlockBisected loadFromConfig(ConfigurationSection config, String id) {
        BlockData[] data = loadBlockDataFromConfig(config, half);
        if(data==null) {
            return null;
        }
        return new SpecialBlockBisected(id, data);
    }
    
}
