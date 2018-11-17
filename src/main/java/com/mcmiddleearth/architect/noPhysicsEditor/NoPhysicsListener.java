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

import com.mcmiddleearth.architect.Modules;
import com.mcmiddleearth.architect.PluginData;
import com.mcmiddleearth.util.DevUtil;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPhysicsEvent;

/**
 *
 * @author Eriol_Eandur
 */
public class NoPhysicsListener implements Listener {
    
    @EventHandler
    private void noPhysicsList(BlockPhysicsEvent event) {
        if(PluginData.isModuleEnabled(event.getBlock().getWorld(), Modules.NO_PHYSICS_LIST_ENABLED)
                && NoPhysicsData.isNoPhysicsBlock(event.getBlock())
                && !NoPhysicsData.hasNoPhysicsException(event.getBlock())) {
            DevUtil.log(4,"no Physics "+event.getBlock().getType().name()+" "+event.getBlock().getX()+" "+event.getBlock().getZ());
            event.setCancelled(true);
        } else {
            DevUtil.log(4,"allow Physics "+event.getBlock().getType().name()+" "+event.getBlock().getX()+" "+event.getBlock().getZ());
        }            
    }

}
