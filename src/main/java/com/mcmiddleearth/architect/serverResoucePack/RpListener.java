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

import com.mcmiddleearth.architect.ArchitectPlugin;
import com.mcmiddleearth.architect.PluginData;
import com.mcmiddleearth.connect.events.PlayerConnectEvent;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.mcmiddleearth.pluginutil.message.FancyMessage;
import com.mcmiddleearth.pluginutil.message.MessageType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerResourcePackStatusEvent;
import org.bukkit.scheduler.BukkitRunnable;

/**
 *
 * @author Eriol_Eandur
 */
public class RpListener implements Listener{
    
    @EventHandler
    public void onRpSwitch(PlayerResourcePackStatusEvent event) {
        Player player = event.getPlayer();
        switch(event.getStatus()) {
            case SUCCESSFULLY_LOADED:
                PluginData.getMessageUtil().sendInfoMessage(player, "Resource pack loaded successfully.");
                break;
            case FAILED_DOWNLOAD:
                PluginData.getMessageUtil().sendInfoMessage(player, "Resource pack download failed. Please check your connection.");
                break;
            case DECLINED:
                PluginData.getMessageUtil().sendInfoMessage(player, "Resource pack loading failed. Did you enable server resource packs (edit server in multiplayer list)?");
                break;
        }
        RpManager.getPlayerData(player).setCurrentRpStatus(event.getStatus());
    }
    
    @EventHandler
    public void onPlayerPreLogin(AsyncPlayerPreLoginEvent event) {
        RpManager.loadPlayerData(event.getUniqueId());
    }

    @EventHandler
    public void onPlayerConnect(PlayerConnectEvent event) {
        Player player = event.getPlayer();
//    Logger.getGlobal().info("PlayerConnectEvent: "+player.getName()+" "+ event.getReason().name());        
        if(event.getReason().equals(PlayerConnectEvent.ConnectReason.JOIN_PROXY)) {
            new BukkitRunnable() {
                int counter = 11;
                @Override
                public void run() {
                    if(RpManager.hasPlayerDataLoaded(player) || counter==0) {
                        RpPlayerData data = RpManager.getPlayerData(player);
                        String lastUrl = data.getCurrentRpUrl();
                        data.setCurrentRpUrl(null);
                        if(!RpManager.setRpRegion(player)) {
                            //if(data.isAutoRp()) {
                                String rp = RpManager.getRpForUrl(lastUrl);
//    Logger.getGlobal().info("On PlayerConnect: Set rp to last url: "+rp+" "+ lastUrl);        
                                RpManager.setRp(rp, player, false);
                            //}
                        }
                        if (RpManager.hasPlayerDataLoaded(player)
                                && player.getClientBrandName() !=null
                                && player.getClientBrandName().contains("fabric")
                                && !data.getVariant().equals("Sodium")) {
                            new FancyMessage(MessageType.INFO, PluginData.getMessageUtil())
                                    .addSimple("If you are using Sodium mod you might experience texture errors as your server RP is not set to Sodium variant. ")
                                    .addClickable("Click here to fix this or do command /rp variant sodium.","/rp variant sodium")
                                    .send(player);
                        }
                        cancel();
                    } else counter --;
                    if(counter==0) {
                        Logger.getLogger(ArchitectPlugin.class.getName()).log(Level.WARNING,"Could not get player rp settings from the database");        
                    }
                }
            }.runTaskTimer(ArchitectPlugin.getPluginInstance(),0,20);
        }
    }
    
    
}
