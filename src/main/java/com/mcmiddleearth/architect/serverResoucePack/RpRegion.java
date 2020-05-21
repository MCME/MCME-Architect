/*
 * Copyright (C) 2018 Eriol_Eandur
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
package com.mcmiddleearth.architect.serverResoucePack;

import com.sk89q.worldedit.bukkit.BukkitWorld;
import com.sk89q.worldedit.math.BlockVector2;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.math.Vector2;
import com.sk89q.worldedit.regions.*;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 *
 * @author Eriol_Eandur
 */
public class RpRegion {
    
    private String name;
    
    private int weight = 0;
    
    private String rp = "";
    
    private Region region;
    
    public RpRegion(String name, Region region){
        this.name = name;
        this.region = region;
    }
    
    public boolean contains(Location loc) {
        BlockVector3 vec = BlockVector3.at(loc.getX(),loc.getY(),loc.getZ());
        return region.getWorld().getName().equals(loc.getWorld().getName())
                && region.contains(BlockVector3.at(loc.getX(),loc.getY(),loc.getZ()));
    }

    public Map<String, Object> saveToMap() {
        Map<String,Object> result = new HashMap<>();
        result.put("name", name);
        result.put("weight",weight);
        result.put("rp", rp);
        Map<String,Object> regionMap = new HashMap<>();
        regionMap.put("world", region.getWorld().getName());
        if(region instanceof CuboidRegion) {
            regionMap.put("type", "CuboidRegion");
            BlockVector3 vec = region.getMinimumPoint();
            regionMap.put("minimumPoint", vec.getBlockX()+","+vec.getBlockY()+","+vec.getBlockZ());
            vec = region.getMaximumPoint();
            regionMap.put("maximumPoint", vec.getBlockX()+","+vec.getBlockY()+","+vec.getBlockZ());
        } else if(region instanceof CylinderRegion) {
            regionMap.put("type", "CylinderRegion");
            BlockVector3 vec = (region).getCenter().toBlockPoint();
            regionMap.put("center", vec.getBlockX()+","+vec.getBlockY()+","+vec.getBlockZ());
            regionMap.put("minY", region.getMinimumPoint().getBlockY());
            regionMap.put("maxY", region.getMaximumPoint().getBlockY());
            regionMap.put("radius", ((CylinderRegion)region).getRadius().toBlockPoint().getBlockX()+","
                                   +((CylinderRegion)region).getRadius().toBlockPoint().getBlockZ());
        } else if(region instanceof EllipsoidRegion) {
            regionMap.put("type", "EllipsoidRegion");
            BlockVector3 vec = (region).getCenter().toBlockPoint();
            regionMap.put("center", vec.getBlockX()+","+vec.getBlockY()+","+vec.getBlockZ());
            vec = ((EllipsoidRegion)region).getRadius().toBlockPoint();
            regionMap.put("radius", vec.getBlockX()+","+vec.getBlockY()+","+vec.getBlockZ());
        } else if(region instanceof Polygonal2DRegion) {
            regionMap.put("type", "Polygonal2DRegion");
            regionMap.put("minY", region.getMinimumPoint().getBlockY());
            regionMap.put("maxY", region.getMaximumPoint().getBlockY());
            List<BlockVector2> points = ((Polygonal2DRegion)region).getPoints();
            List<String> pointData = new ArrayList<>();
            for(BlockVector2 vec: points) {
                pointData.add(vec.getBlockX()+","+vec.getBlockZ());
            }
            regionMap.put("points", pointData);
        }
        result.put("region", regionMap);
        return result;
    }
    
    public static RpRegion loadFromMap(Map<String,Object> data) {
        RpRegion result;
        String name = (String) data.get("name");
        Map<String,Object> regionData = ((ConfigurationSection)data.get("region")).getValues(true);
        World world = Bukkit.getWorld((String) regionData.get("world"));
        if(world==null) {
            return null;
        }
        String type = (String) regionData.get("type");
        switch(type) {
            case "CuboidRegion":
                BlockVector3 minPoint = getVector((String) regionData.get("minimumPoint"));
                BlockVector3 maxPoint = getVector((String) regionData.get("maximumPoint"));
                result = new RpRegion(name,new CuboidRegion(new BukkitWorld(world),minPoint,maxPoint));
                break;
            case "CylinderRegion":
                BlockVector3 center = getVector((String) regionData.get("center"));
                BlockVector2 blockRadius = getBlockVector2((String)regionData.get("radius"));
                Vector2 rad = Vector2.at(blockRadius.getBlockX(),blockRadius.getBlockZ());
                int minY = (Integer) regionData.get("minY");
                int maxY = (Integer) regionData.get("maxY");
                result = new RpRegion(name, new CylinderRegion(new BukkitWorld(world),
                                                               center,
                                                               rad,minY,maxY));
                break;
            case "EllipsoidRegion":
                center = getVector((String) regionData.get("center"));
                BlockVector3 radius3D = getVector((String)regionData.get("radius"));
                result = new RpRegion(name, new EllipsoidRegion(new BukkitWorld(world),center,radius3D.toVector3()));
                break;
            case "Polygonal2DRegion":
                minY = (Integer) regionData.get("minY");
                maxY = (Integer) regionData.get("maxY");
                List<String> pointData = (List<String>) regionData.get("points");
                List<BlockVector2> points = new ArrayList<>();
                for(String point: pointData) {
                    points.add(getBlockVector2(point));
                }
                result = new RpRegion(name, new Polygonal2DRegion(new BukkitWorld(world),points,minY,maxY));
                break;
            default:
                throw new UnsupportedOperationException("Not all region types are supported.");
        }
        result.setWeight((Integer) data.get("weight"));
        result.setRp((String) data.get("rp"));
        return result;
    }
    
    private static BlockVector3 getVector(String data) {
        String[] split = data.split(",");
        return BlockVector3.at(Integer.parseInt(split[0]),Integer.parseInt(split[1]),Integer.parseInt(split[2]));
    }
    private static BlockVector2 getBlockVector2(String data) {
        String[] split = data.split(",");
        return BlockVector2.at(Integer.parseInt(split[0]),Integer.parseInt(split[1]));
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public int getWeight() {
        return weight;
    }

    public void setWeight(int weight) {
        this.weight = weight;
    }

    public String getRp() {
        return rp;
    }

    public void setRp(String rp) {
        this.rp = rp;
    }

    public Region getRegion() {
        return region;
    }

    public void setRegion(Region region) {
        this.region = region;
    }
}
