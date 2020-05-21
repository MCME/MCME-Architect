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

import com.mcmiddleearth.architect.PluginData;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.logging.Level;
import java.util.logging.Logger;
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
                Logger.getLogger(PluginData.class.getName()).log(Level.SEVERE, "Error getting BuildPermission from TheGaffer", ex);
                return true;
            }
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
                Logger.getLogger(PluginData.class.getName()).log(Level.SEVERE, "Error getting BuildProtectionMessage from TheGaffer", ex);
                return "";
            }
        }
    }
    
    
}
