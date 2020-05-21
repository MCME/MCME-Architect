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

import com.mcmiddleearth.architect.ArchitectPlugin;
import com.mcmiddleearth.architect.PluginData;
import java.util.UUID;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitRunnable;

/**
 *
 * @author Eriol_Eandur
 */
public class AfkListener implements Listener{
    
    @EventHandler
    public void playerQuit(PlayerQuitEvent event) {
        PluginData.undoAFK(event.getPlayer().getUniqueId());
    }
    
    @EventHandler
    public void playerJoin(PlayerJoinEvent event) {
        setListNamesAfk(false);
        new BukkitRunnable(){
            @Override
            public void run() {
                setListNamesAfk(true);
            }
        }.runTaskLater(ArchitectPlugin.getPluginInstance(),1);
    }
    
    private static void setListNamesAfk(boolean afk) {
        for(UUID uuid:PluginData.getAfkPlayerList()) {
            Player player = Bukkit.getPlayer(uuid);
            if(player!=null) {
                if(!afk) {
                    player.setPlayerListName(player.getName());
                } else {
                    player.setPlayerListName(player.getName()+" (AFK)");
                }
                Logger.getGlobal().info("afk join run "+afk);
            }
        }
    }
}
