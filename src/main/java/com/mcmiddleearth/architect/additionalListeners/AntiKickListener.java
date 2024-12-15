package com.mcmiddleearth.architect.additionalListeners;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerKickEvent;

public class AntiKickListener implements Listener {

    @EventHandler
    public void onChatOutOfOrderKick(PlayerKickEvent event) {
        if(event.getReason().contains("Out-of-order chat packet")) {
            event.setCancelled(true);
        }
    }
}
