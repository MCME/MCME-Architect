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
package com.mcmiddleearth.util;

import com.mcmiddleearth.architect.specialBlockHandling.command.InvCommand;
import java.lang.reflect.InvocationTargetException;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.bukkit.entity.Player;
import org.bukkit.metadata.MetadataValue;
import org.bukkit.plugin.Plugin;

/**
 *
 * @author Eriol_Eandur
 */
public class _invalid_ResourceRegionsUtil {
    
    public static String getResourceRegionsUrl(Player p) {
        List<MetadataValue> data = p.getMetadata("Region");
        if(data.isEmpty()) {
//Logger.getGlobal().info("Emtpy resource data");
            return "";
        }
        String region = data.get(0).asString();
        Plugin regionsPlugin = data.get(0).getOwningPlugin();
        if(regionsPlugin==null) {
//Logger.getGlobal().info("no regions plugin");
            return "";
        }
        String packUrl="";
        try {
            Class rmClass = Class.forName("me.dags.resourceregions.region.RegionManager");
            Object regionObject = rmClass.getMethod("getRegion",String.class,String.class)
                                         .invoke(null,p.getWorld().getName(), region);
            packUrl = (String) regionObject.getClass().getMethod("getPackUrl").invoke(regionObject);
//Logger.getGlobal().info("pack url "+packUrl);
        } catch (NullPointerException | NoSuchMethodException | SecurityException | IllegalAccessException | IllegalArgumentException | InvocationTargetException | ClassNotFoundException ex) {
            Logger.getLogger(InvCommand.class.getName()).log(Level.WARNING, "No resource region found.");
            return "";
        }
        return packUrl;
    }
    
}
