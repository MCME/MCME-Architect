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

import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.regions.CuboidRegion;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.util.Vector;

import java.util.UUID;

/**
 *
 * @author Eriol_Eandur
 */
public abstract class ExceptionArea {
    
    private Vector minPoint, maxPoint;
    
    private UUID worldUID;
    
    public ExceptionArea(CuboidRegion region) {
        setRegion(region);
    }
    
    public ExceptionArea(World world, Vector minPoint, Vector maxPoint) {
        this(world.getUID(), minPoint, maxPoint);
    }
    
    public ExceptionArea(UUID world, Vector minPoint, Vector maxPoint) {
        this.worldUID = world;
        this.minPoint = minPoint;
        this.maxPoint = maxPoint;
    }
    
    public final void setRegion(CuboidRegion region) {
        worldUID = Bukkit.getWorld(region.getWorld().getName()).getUID();
        minPoint = convertWePoint(region.getMinimumPoint());
        maxPoint = convertWePoint(region.getMaximumPoint());
    }
    
    public boolean isInside(Location loc) {
        if(!loc.getWorld().getUID().equals(worldUID)) {
            return false;
        }
        return     loc.getBlockX()>=minPoint.getBlockX()
                && loc.getBlockY()>=minPoint.getBlockY()
                && loc.getBlockZ()>=minPoint.getBlockZ()
                && loc.getBlockX()<=maxPoint.getBlockX()
                && loc.getBlockY()<=maxPoint.getBlockY()
                && loc.getBlockZ()<=maxPoint.getBlockZ();
    }
    
    public abstract boolean isAffected(Material material);
    
    private Vector convertWePoint(BlockVector3 weVector) {
        int x = weVector.getBlockX();
        int y = weVector.getBlockY();
        int z = weVector.getBlockZ();
        return new Vector(x,y,z);
    }
    
    public int getX() {
        return minPoint.getBlockX();
    }
    public int getY() {
        return minPoint.getBlockY();
    }
    public int getZ() {
        return minPoint.getBlockZ();
    }
    public int getDX() {
        return maxPoint.getBlockX()-minPoint.getBlockX();
    }
    public int getDY() {
        return maxPoint.getBlockY()-minPoint.getBlockY();
    }
    public int getDZ() {
        return maxPoint.getBlockZ()-minPoint.getBlockZ();
    }

    public UUID getWorldUID() {
        return worldUID;
    }
}
