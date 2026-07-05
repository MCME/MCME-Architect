package com.mcmiddleearth.architect.viewDistance;

import com.mcmiddleearth.architect.Modules;
import com.mcmiddleearth.architect.PluginData;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

public class ViewDistanceListener implements Listener {

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if(!PluginData.isModuleEnabled(player.getWorld(), Modules.VIEW_DISTANCE)) {
            return;
        }
        int viewDistance = ViewDistanceManager.getViewDistance(player);
        player.setViewDistance(viewDistance);
        player.setSendViewDistance(viewDistance);
    }
}
