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
package com.mcmiddleearth.architect.noPhysicsEditor;

import com.sk89q.worldedit.regions.CuboidRegion;
import java.util.UUID;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.util.Vector;

/**
 *
 * @author Eriol_Eandur
 */
public class WaterFlowArea extends ExceptionArea {

    
    public WaterFlowArea(CuboidRegion region) {
        super(region);
    }
    public WaterFlowArea(World world, Vector minPoint, Vector maxPoint) {
        super(world,minPoint,maxPoint);
    }
    
    public WaterFlowArea(UUID world, Vector minPoint, Vector maxPoint) {
        super(world,minPoint,maxPoint);
    }
    
    @Override
    public boolean isAffected(Material material) {
        return     material.equals(Material.WATER)              //55
                || material.equals(Material.LAVA);            //93
                
    }
    
}
