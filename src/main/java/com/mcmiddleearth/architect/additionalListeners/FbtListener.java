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

import com.mcmiddleearth.architect.Modules;
import com.mcmiddleearth.architect.PluginData;
import java.util.logging.Logger;
import org.bukkit.Chunk;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.potion.PotionEffectType;

/**
 *
 * @author Eriol_Eandur
 */
public class FbtListener implements Listener{
    
    @EventHandler
    public void playerChangeWorld(PlayerChangedWorldEvent event) {
        Player player = event.getPlayer();
        if(!PluginData.isModuleEnabled(event.getPlayer().getWorld(), Modules.FULL_BRIGHTNESS)
                && player.hasPotionEffect(PotionEffectType.NIGHT_VISION)) {
            player.removePotionEffect(PotionEffectType.NIGHT_VISION);
            PluginData.getMessageUtil().sendInfoMessage(player, "Fullbright is not allowed in this world!");
        }
    }
    
    /*public static Chunk chunk;
    
    @EventHandler
    public void unloadChunk(ChunkUnloadEvent event) {
        if(event.getChunk().equals(chunk)) {
            Logger.getGlobal().info("unloaded");
        }
    }
    
    @EventHandler
    public void loadChunk(ChunkLoadEvent event) {
        if(event.getChunk().equals(chunk)) {
            Logger.getGlobal().info("loaded");
        }
    }*/

}
