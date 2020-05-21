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
package com.mcmiddleearth.architect.additionalListeners;

import org.bukkit.event.Listener;

/**
 *
 * @author Eriol_Eandur
 */
public class StickBlockBreakListener implements Listener {

    /*1.13 removed @EventHandler
    public void PlayerInteract(PlayerInteractEvent event) {
        Player p = event.getPlayer();
        if((PluginData.isModuleEnabled(p.getWorld(),Modules.ARMOR_STAND_EDITOR)
                || PluginData.isModuleEnabled(p.getWorld(),Modules.PAINTING_EDITOR)
                || PluginData.isModuleEnabled(p.getWorld(),Modules.BANNER_EDITOR)) 
              && p.getInventory().getItemInHand().getType().equals(Material.STICK)
              && EventUtil.isMainHandEvent(event)) {
            event.setCancelled(true);
        }
    }*/
    
}
