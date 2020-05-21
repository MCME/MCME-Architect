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
package com.mcmiddleearth.architect.entityLogging;

import com.mcmiddleearth.architect.ArchitectPlugin;
import com.mcmiddleearth.architect.entityLogging.EntityLogger.Coordinates;
import com.mcmiddleearth.util.DevUtil;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.plugin.Plugin;

import org.dynmap.DynmapAPI;
import org.dynmap.markers.AreaMarker;
import org.dynmap.markers.MarkerSet;

/**
 *
 * @author Eriol_Eandur
 */
public class ELogDynmapUtil {
    
    private static boolean init = false;
    private static final boolean enabled = getDynmapConfig().getBoolean("enabled",false);
    
    private static DynmapAPI dynmapPlugin;
    
    private static MarkerSet markerSet;
    //private static int borderColor;
    //private static int areaColor;
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
                markerSet = dynmapPlugin.getMarkerAPI().createMarkerSet("entities.markerset", "Entities", null, false);
                markerSet.setHideByDefault(getDynmapConfig().getBoolean("hide",true));
                //borderColor = getDynmapConfig().getColor("borderColor",Color.PURPLE).asRGB();
                //areaColor = getDynmapConfig().getColor("areaColor",Color.PURPLE).asRGB();
                borderWidth = getDynmapConfig().getInt("borderWidth",0);
                borderOpacity = getDynmapConfig().getDouble("borderOpacity",0.5);
                areaOpacity = getDynmapConfig().getDouble("areaOpacity",0.5);
                ArchitectPlugin.getPluginInstance().saveConfig();
                init = true;
                clearMarkers();
            } catch(Exception e) {
                Logger.getLogger(ELogDynmapUtil.class.getName()).log(Level.WARNING, "Dynmap plugin not compatible",e);
            }
        }
    }
    
    private static ConfigurationSection getDynmapConfig() {
        ConfigurationSection section = ArchitectPlugin.getPluginInstance()
                                                      .getConfig().getConfigurationSection("entityLogger");
        if(section==null) {
            section = ArchitectPlugin.getPluginInstance().getConfig().createSection("entityLogger");
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
    
    public static void createMarker(Coordinates coord, Class[] entityTypes, Integer[] values, int maxValue, World world) {
        if(!enabled) {
            return;
        }
        if(!init) {
            init();
        }
        AreaMarker areaMarker=null;
        if(init) {
            String newMarkerId = "e_"+coord.x+"_"+coord.z+".marker";
            for (AreaMarker marker : markerSet.getAreaMarkers())
            {
                if (marker.getMarkerID().equals(newMarkerId)) {
    DevUtil.log("Updating Dynmap Entity Log for: " + coord.x+" "+coord.z);
                    marker.setCornerLocations(getXPoints(coord),getZPoints(coord));
                    areaMarker = marker;
                    break;

                }
            }
            if(areaMarker == null) {
//Logger.getGlobal().info("Create new Marker: "+coord.x+" "+coord.z);
                areaMarker = markerSet.createAreaMarker(newMarkerId, coord.x+" "+coord.z, 
                                                                   false, world.getName(), 
                                                                   getXPoints(coord),
                                                                   getZPoints(coord), false);
                //markerSet.createMarker("t"+newMarkerId, "t"+newMarkerId, true, world.getName(), coord.x, 100, coord.z, markerSet.getDefaultMarkerIcon(), false);
            }
    DevUtil.log("Adding Dynmap Entity Log for: " + coord.x+" "+coord.z);
            areaMarker.setFillStyle(areaOpacity, colorOf((1.0*values[values.length-1])/maxValue));
            areaMarker.setLineStyle(borderWidth, borderOpacity, colorOf((1.0*values[values.length-1])/maxValue));
            areaMarker.setDescription(getDescription(entityTypes,values));
        }
    }
    
    private static String getDescription(Class[] entityTypes, Integer[]values) {
        String result = "";
        for(int i=0; i< entityTypes.length;i++) {
            result = result + entityTypes[i].getSimpleName()+":"+values[i]+",";
        }
        return result + "All:"+values[values.length-1];
    }
    
    private static int colorOf(double value) {
        //return Color.RED.asRGB();/*
        value = Math.sqrt(value);
        int blue = Math.max(0, (int) Math.max(255-1024*value,-769+1024*value));
        int green = Math.max(0, Math.min(255,(int) Math.min(1024*value,767-1024*value)));
        int red = Math.max(0, Math.min(255,(int)(-256+1024*value)));
//Logger.getGlobal().info("value: "+value+" r: "+red+" g: "+green+" b: "+blue);
        return red*256*256+green*256+blue;
    }
    
    private static double[] getXPoints(Coordinates coord) {
        return   new double[]{coord.x+16,
                              coord.x+16,
                              coord.x,
                              coord.x};
    }

    private static double[] getZPoints(Coordinates coord) {
        return   new double[]{coord.z+16,
                              coord.z,
                              coord.z,
                              coord.z+16};
    }


}
