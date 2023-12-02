package com.mcmiddleearth.architect.specialBlockHandling.listener;

import com.mcmiddleearth.util.TheGafferUtil;
import org.bukkit.block.Block;
import org.bukkit.block.data.type.Light;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;

public class LightBlockListener implements Listener {

    @EventHandler
    public void changeLightLevel(PlayerInteractEvent event) {
        Block block = event.getClickedBlock();
        if(event.getAction().equals(Action.RIGHT_CLICK_BLOCK)
                && event.getClickedBlock()!=null
                && block!=null && block.getBlockData() instanceof Light lightData) {
            event.setCancelled(true);
            if(TheGafferUtil.checkGafferPermission(event.getPlayer(),event.getClickedBlock().getLocation())) {
                if (event.getPlayer().isSneaking()) {
                    lightData.setLevel((lightData.getLevel() + 15) % 16);
                } else {
                    lightData.setLevel((lightData.getLevel() + 1) % 16);
                }
                block.setBlockData(lightData, true);
            }
        }
    }
}
