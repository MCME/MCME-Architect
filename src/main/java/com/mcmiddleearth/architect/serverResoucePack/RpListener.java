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
import com.mcmiddleearth.pluginutil.developer.DevUtil;
import com.mcmiddleearth.pluginutil.message.FancyMessage;
import com.mcmiddleearth.pluginutil.message.MessageType;
import com.viaversion.viaversion.api.Via;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerResourcePackStatusEvent;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author Eriol_Eandur
 */
public class RpListener implements Listener {
    
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
        DevUtil devUtil = ArchitectPlugin.getPluginInstance().getDevUtil();
        devUtil.log(2,"PlayerConnectEvent: "+player.getName()+" "+ event.getReason().name());
//Logger.getGlobal().info("PlayerConnectEvent: "+player.getName()+" "+ event.getReason().name());
        int version = player.getProtocolVersion();
        String snapshot = "";
        if(version > 0x40000000) {
            snapshot = "Snapshot ";
            version = version - 0x40000000;
        }
        devUtil.log(2,"Bukkit Protocol Version: "+snapshot + version);
        devUtil.log(2,"ViaVersion Protocol Version: "+Via.getAPI().getPlayerProtocolVersion(player.getUniqueId()).getVersion());
        devUtil.log(2,"Sodium client: "+RpManager.isSodiumClient(player));
        devUtil.log(2,"Incomming plugin channels:");
        Bukkit.getMessenger().getIncomingChannels().forEach(channel->devUtil.log(2,channel));
        if(event.getReason().equals(PlayerConnectEvent.ConnectReason.JOIN_PROXY)) {
            new BukkitRunnable() {
                int counter = 11;
                @Override
                public void run() {
                    if(RpManager.hasPlayerDataLoaded(player) || counter==0) {
                        RpPlayerData data = RpManager.getPlayerData(player);
                        data.setProtocolVersion(Via.getAPI().getPlayerProtocolVersion(player.getUniqueId()).getVersion());
                        if(RpManager.isSodiumClient(player)) {
                            data.setClient("sodium");
                        } else if(!"fabric".equalsIgnoreCase(player.getClientBrandName())) {
                            data.setClient("vanilla");
                        }
                        String lastUrl = data.getCurrentRpUrl();
                        data.setCurrentRpUrl(null);
                        if(!RpManager.setRpRegion(player)) {
                            //if(data.isAutoRp()) {
                                String rp = RpManager.getRpForUrl(lastUrl);
                                devUtil.log(2,"On PlayerConnect: Set rp to last url: "+rp+" "+ lastUrl);
                                RpManager.setRp(rp, player, false);
                            //}
                        }
                        if (RpManager.hasPlayerDataLoaded(player)
                                && player.getClientBrandName() !=null) {
                            if(player.getClientBrandName().contains("fabric")
                                     && !data.getClient().equals("sodium")) {
                                new FancyMessage(MessageType.INFO, PluginData.getMessageUtil())
                                        .addSimple("If you are using " + ChatColor.GREEN + ChatColor.BOLD + "Sodium "
                                                + ChatColor.AQUA + "you might experience" + ChatColor.GREEN + " texture errors"
                                                + ChatColor.AQUA + " as your server RP is not set to Sodium variant. ")
                                        .addClickable("Click here to fix this or do command "
                                                + ChatColor.GREEN + ChatColor.BOLD + "/rp client sodium.",
                                                "/rp client sodium").setRunDirect()
                                        .send(player);
                            } else if(player.getClientBrandName().contains("forge")
                                            || player.getClientBrandName().contains("optifine")) {
                                new FancyMessage(MessageType.INFO, PluginData.getMessageUtil())
                                        .addSimple("If you are using " + ChatColor.GREEN + ChatColor.BOLD + "Optifine "
                                                + ChatColor.AQUA + "you need to" + ChatColor.GREEN +ChatColor.BOLD+ " disable mip-mapping"
                                                + ChatColor.AQUA + ". Also please notice that "
                                                + ChatColor.GREEN+ChatColor.BOLD+"Optifine Shaders do not work"
                                                + ChatColor.AQUA + "with our resource packs.")
                                        .addClickable("Click here to get a " + ChatColor.GREEN + ChatColor.BOLD + "Guide to Shaders on MCME.", "/helper shaders").setRunDirect()
                                        .send(player);
                            }
                        }
                        cancel();
                    } else counter --;
                    if(counter==0) {
                        Logger.getLogger(ArchitectPlugin.class.getName()).log(Level.WARNING,"Could not get player rp settings from the database");        
                    }
                }
            }.runTaskTimer(ArchitectPlugin.getPluginInstance(),30,20);
        }
    }
    
    @EventHandler
    public void playerQuit(PlayerQuitEvent event) {
        RpManager.removeSodiumClient(event.getPlayer());
    }
}
