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
package com.mcmiddleearth.architect.serverResoucePack;

import com.mcmiddleearth.architect.ArchitectPlugin;
import com.mcmiddleearth.architect.serverResoucePack.RpRegion;
import com.mcmiddleearth.util.DevUtil;
import com.sk89q.worldedit.math.BlockVector2;
import com.sk89q.worldedit.math.BlockVector3;
//import com.sk89q.worldedit.BlockVector2D;
import com.sk89q.worldedit.regions.Polygonal2DRegion;
import com.sk89q.worldedit.regions.Region;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.plugin.Plugin;

import org.dynmap.DynmapAPI;
import org.dynmap.markers.AreaMarker;
import org.dynmap.markers.MarkerSet;

/**
 *
 * @author Eriol_Eandur
 */
public class RpDynmapUtil {
    
    private static boolean init = false;
    private static final boolean enabled = getDynmapConfig().getBoolean("enabled",false);
    
    private static DynmapAPI dynmapPlugin;
    
    private static MarkerSet markerSet;
    private static int borderColor;
    private static int areaColor;
    private static int borderWidth;
    private static double borderOpacity;
    private static double areaOpacity;

    private static void init() {
        if(!enabled) {
            return;
        }
        Plugin dynmap = Bukkit.getServer().getPluginManager().getPlugin("dynmap");
        if(dynmap==null) {
            Logger.getGlobal().info("Dynmap not found");
        }
        else {
            try{
                dynmapPlugin = (DynmapAPI) dynmap;
                markerSet = dynmapPlugin.getMarkerAPI().createMarkerSet("rpregions.markerset", "RpRegions", null, false);
                markerSet.setHideByDefault(getDynmapConfig().getBoolean("hide",true));
                borderColor = getDynmapConfig().getColor("borderColor",Color.PURPLE).asRGB();
                areaColor = getDynmapConfig().getColor("areaColor",Color.PURPLE).asRGB();
                borderWidth = getDynmapConfig().getInt("borderWidth",2);
                borderOpacity = getDynmapConfig().getDouble("borderOpacity",0.15);
                areaOpacity = getDynmapConfig().getDouble("areaOpacity",0.25);
                ArchitectPlugin.getPluginInstance().saveConfig();
                init = true;
            } catch(Exception e) {
                Logger.getLogger(RpDynmapUtil.class.getName()).log(Level.WARNING, "Dynmap plugin not compatible",e);
            }
        }
    }
    
    private static ConfigurationSection getDynmapConfig() {
        ConfigurationSection section = ArchitectPlugin.getPluginInstance()
                                                      .getConfig().getConfigurationSection("dynmap");
        if(section==null) {
            section = ArchitectPlugin.getPluginInstance().getConfig().createSection("dynmap");
            section.set("enabled", true);
        }
        return section;
    }
    
    public static void clearMarkers() {
        if(!enabled) {
            return;
        }
        if(!init) {
            init();
        }
        if(init) {
            for(AreaMarker marker: markerSet.getAreaMarkers()) {
                marker.deleteMarker();
            }
        }
    }
    
    public static void createMarker(RpRegion region) {
        if(!enabled) {
            return;
        }
        if(!init) {
            init();
        }
        if(init) {
            String newMarkerId = region.getName().toLowerCase()+".marker";
            for (AreaMarker marker : markerSet.getAreaMarkers())
            {
                if (marker.getMarkerID().equals(newMarkerId)) {
    DevUtil.log("Updating Dynmap AreaMarker for region: " + region.getName());
                    marker.setCornerLocations(getXPoints(region),getZPoints(region));
                    return;

                }
            }
    DevUtil.log("Adding Dynmap AreaMarker for region: " + region.getName());
            AreaMarker areaMarker = markerSet.createAreaMarker(newMarkerId, region.getName(), 
                                                               false, region.getRegion().getWorld().getName(), 
                                                               getXPoints(region),
                                                               getZPoints(region), false);
            areaMarker.setFillStyle(areaOpacity, areaColor);
            areaMarker.setLineStyle(borderWidth, borderOpacity, borderColor);
            //areaMarker.setRangeY(region.getWeight()*10, region.getWeight()*10);
            areaMarker.setDescription(region.getName()+" (RP: "+region.getRp()+", Weight: "+region.getWeight()+")");
        }
    }
    
    private static double[] getXPoints(RpRegion region) {
        if(region.getRegion() instanceof Polygonal2DRegion) {
            double[] result = new double[((Polygonal2DRegion)region.getRegion()).getPoints().size()];
            for(int i = 0; i < result.length; i++) {
                BlockVector2 vector = ((Polygonal2DRegion)region.getRegion()).getPoints().get(i);
                result[i] = vector.getX();
            }
            return result;
        } else {
            Region weRegion = region.getRegion();
            return   new double[]{weRegion.getMaximumPoint().getBlockX(),
                                  weRegion.getMaximumPoint().getBlockX(),
                                  weRegion.getMinimumPoint().getBlockX(),
                                  weRegion.getMinimumPoint().getBlockX()};
        }
    }

    private static double[] getZPoints(RpRegion region) {
        if(region.getRegion() instanceof Polygonal2DRegion) {
            double[] result = new double[((Polygonal2DRegion)region.getRegion()).getPoints().size()];
            for(int i = 0; i < result.length; i++) {
                BlockVector2 vector = ((Polygonal2DRegion)region.getRegion()).getPoints().get(i);
                result[i] = vector.getZ();
            }
            return result;
        } else {
            Region weRegion = region.getRegion();
            return   new double[]{weRegion.getMaximumPoint().getBlockZ(),
                                  weRegion.getMinimumPoint().getBlockZ(),
                                  weRegion.getMinimumPoint().getBlockZ(),
                                  weRegion.getMaximumPoint().getBlockZ()};
        }
    }


}
