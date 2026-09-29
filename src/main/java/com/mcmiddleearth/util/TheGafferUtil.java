/*
 * Copyright (C) 2018 MCME
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
package com.mcmiddleearth.util;

import com.mcmiddleearth.architect.Log;
import com.mcmiddleearth.architect.PluginData;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

/**
 *
 * @author Eriol_Eandur
 */
public class TheGafferUtil {
    
    public static boolean checkGafferPermission(Player p, Location loc) {
        if(!hasGafferPermission(p,loc)) {
                PluginData.getMessageUtil().sendErrorMessage(p,getGafferProtectionMessage(p, loc));
                return false; 
        } else {
            return true;
        }
    }
    
    public static boolean hasGafferPermission(Player player, Location location) {
        Plugin theGaffer = Bukkit.getPluginManager().getPlugin("TheGaffer");
        if(theGaffer == null) {
            return true;
        } else {
            try {
                Method getBuildPermMethod = theGaffer.getClass().getMethod("hasBuildPermission", Player.class, Location.class);
                return (boolean) getBuildPermMethod.invoke(null, player, location);
            } catch (NoSuchMethodException | SecurityException | IllegalAccessException | IllegalArgumentException | InvocationTargetException ex) {
                Log.error("Failed to reflectively invoke TheGaffer.hasBuildPermission for player " + player.getName()
                        + " at " + location + "; failing open (permission granted)", ex);
                return true;
            }
        }
    }
    
    // TheGaffer's recordExternalBuild, looked up once per TheGaffer class. TheGaffer before 3.0.0 has none: null.
    private static Class<?> recordClass;
    private static Method recordMethod;

    /**
     * Tells TheGaffer that the player placed a new special block, so it counts in their job's stats. Architect sets
     * such blocks itself, and TheGaffer counts only the BlockPlaceEvents it sees. Does nothing without an enabled
     * TheGaffer; one that cannot record builds is logged once.
     */
    public static void recordPlace(Player player, Location location) {
        Plugin theGaffer = Bukkit.getPluginManager().getPlugin("TheGaffer");
        if(theGaffer == null || !theGaffer.isEnabled()) {
            return;
        }
        if(theGaffer.getClass() != recordClass) {
            recordClass = theGaffer.getClass();
            try {
                recordMethod = recordClass.getMethod("recordExternalBuild", Player.class, Location.class,
                        boolean.class);
            } catch (NoSuchMethodException | SecurityException ex) {
                recordMethod = null;
                Log.warn("TheGaffer " + theGaffer.getPluginMeta().getVersion() + " cannot count the special blocks"
                        + " Architect places, so job stats leave them out. TheGaffer 3.0.0 counts them.");
            }
        }
        if(recordMethod == null) {
            return;
        }
        try {
            recordMethod.invoke(null, player, location, true);
        } catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException ex) {
            Log.error("Failed to reflectively invoke TheGaffer.recordExternalBuild for player " + player.getName()
                    + " at " + location, ex);
        }
    }

    public static String getGafferProtectionMessage(Player player, Location location) {
        Plugin theGaffer = Bukkit.getPluginManager().getPlugin("TheGaffer");
        if(theGaffer == null) {
            return "";
        } else {
            try {
                Method getBuildPermMethod = theGaffer.getClass().getMethod("getBuildProtectionMessage", Player.class, Location.class);
                return (String) getBuildPermMethod.invoke(null, player, location);
            } catch (NoSuchMethodException | SecurityException | IllegalAccessException | IllegalArgumentException | InvocationTargetException ex) {
                Log.error("Failed to reflectively invoke TheGaffer.getBuildProtectionMessage for player " + player.getName()
                        + " at " + location, ex);
                return "";
            }
        }
    }
    
    
}
