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
import com.mcmiddleearth.architect.Log;
import com.mcmiddleearth.architect.PluginData;
import com.mcmiddleearth.architect.biomeTuning.BiomeTuning;
import com.mcmiddleearth.connect.events.PlayerConnectEvent;
import com.mcmiddleearth.pluginutil.developer.DevUtil;
import com.mcmiddleearth.pluginutil.message.FancyMessage;
import com.mcmiddleearth.pluginutil.message.MessageType;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerResourcePackStatusEvent;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.UUID;

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
        RpManager.getPlayerData(player)
                 .setCurrentRpStatus(RpPlayerStatus.forPlayerResourcePackStatusEvent(event.getStatus()));
        // Stored at once, so that the server the player switches to knows whether their client has the pack.
        RpManager.savePlayerData(player);
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
        int version = player.getProtocolVersion();
        String snapshot = "";
        if(version > 0x40000000) {
            snapshot = "Snapshot ";
            version = version - 0x40000000;
        }
        devUtil.log(2,"Bukkit Protocol Version: "+snapshot + version);
        devUtil.log(2,"Client Protocol Version: "+RpManager.getClientProtocolVersion(player));
        devUtil.log(2,"Sodium client: "+RpManager.isSodiumClient(player));
        devUtil.log(2,"Incomming plugin channels:");
        Bukkit.getMessenger().getIncomingChannels().forEach(channel->devUtil.log(2,channel));
        if(event.getReason().equals(PlayerConnectEvent.ConnectReason.JOIN_PROXY)) {
            UUID uuid = player.getUniqueId();
            new BukkitRunnable() {
                int counter = 30;
                @Override
                public void run() {
                    // The player as they are now: a biome refresh takes them off the server for a moment and
                    // brings them back as a new Player, so the check holds their UUID and looks them up each time.
                    Player player = Bukkit.getPlayer(uuid);
                    if(player == null) {
                        if(!BiomeTuning.isRefreshing(uuid)) {
                            // The player left: there is no one to send a pack to, and a save now would put
                            // defaults over the row the server they went to writes.
                            cancel();
                        } else if(counter > 0) {
                            counter--; // a run during a refresh counts towards the limit, and sends and saves nothing
                        }
                        return;
                    }
                    boolean loaded = RpManager.hasPlayerDataLoaded(player);
                    if(loaded || counter==0) {
                        // This run is the check's last, whatever happens in it. Cancelled first, the task is not
                        // run again even when the send below throws: a repeat would send and save every half second.
                        cancel();
                        if(!loaded) {
                            Log.warn("Timed out waiting for RP settings to load from the database for player "
                                    + player.getName() + " (" + player.getUniqueId() + "); RP will use defaults.");
                        }
                        RpPlayerData data = RpManager.getPlayerData(player);
                        // A player who has just joined the proxy has no server resource pack yet, whatever
                        // status the database holds from their last visit.
                        data.setCurrentRpStatus(RpPlayerStatus.NOT_SENT);
                        RpManager.savePlayerData(player);
                        data.setProtocolVersion(RpManager.getClientProtocolVersion(player));
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
                    } else counter --;
                }
            }.runTaskTimer(ArchitectPlugin.getPluginInstance(),0,10);
        }
    }
    
    @EventHandler
    public void playerQuit(PlayerQuitEvent event) {
        if (BiomeTuning.isRefreshing(event.getPlayer().getUniqueId())) {
            return; // a biome refresh: the player comes straight back, and no pre-login event would reload this data
        }
        RpManager.removeSodiumClient(event.getPlayer());
        RpManager.removePlayerData(event.getPlayer());
    }
}
