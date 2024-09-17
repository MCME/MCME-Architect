package com.mcmiddleearth.architect.serverResoucePack;

import org.bukkit.entity.Player;
import org.bukkit.plugin.messaging.PluginMessageListener;
import org.jetbrains.annotations.NotNull;

import java.util.logging.Logger;

public class RpPluginMessageListener implements PluginMessageListener {

    @Override
    public void onPluginMessageReceived(@NotNull String s, @NotNull Player player, byte[] bytes) {
        //Logger.getGlobal().info("Sodium client detected: "+player.getName());
        RpManager.addSodiumClient(player,"unknown");
    }
}
