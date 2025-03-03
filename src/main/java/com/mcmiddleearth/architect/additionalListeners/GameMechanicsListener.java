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
import com.mcmiddleearth.architect.Modules;
import com.mcmiddleearth.architect.PluginData;
import com.mcmiddleearth.architect.watcher.WatchedListener;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.block.BlockState;
import org.bukkit.block.Container;
import org.bukkit.block.DecoratedPot;
import org.bukkit.block.Jukebox;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockFadeEvent;
import org.bukkit.event.block.BlockFormEvent;
import org.bukkit.event.block.LeavesDecayEvent;
import org.bukkit.event.block.MoistureChangeEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerGameModeChangeEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.weather.WeatherChangeEvent;
import org.bukkit.event.world.StructureGrowEvent;
import org.bukkit.event.world.WorldLoadEvent;
import org.bukkit.scheduler.BukkitScheduler;

import java.util.logging.Logger;

/**
 *
 * @author Eriol_Eandur
 */
public class GameMechanicsListener extends WatchedListener{
    
    @EventHandler
    public void onWorldLoad(WorldLoadEvent event) {
        PluginData.configureWorld(event.getWorld());
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        event.getPlayer().resetPlayerTime();
    }
    
    @EventHandler
    public void onWeatherChange(WeatherChangeEvent event) {
        if (PluginData.isModuleEnabled(event.getWorld(), Modules.WEATHER_BLOCKING)) {
            if(!PluginData.isOverrideWeather()) {
//Logger.getGlobal().info("Cancelling weather change.");
                event.setCancelled(true);
            } else {
//Logger.getGlobal().info("Allow weather change.");
            }
            PluginData.setOverrideWeather(false);
        }
    }
    
    @EventHandler
    public void blockPlayerDrops(PlayerDropItemEvent event)
    {
//Logger.getGlobal().info("Player Drops.");
        if(PluginData.isModuleEnabled(event.getPlayer().getWorld(), Modules.DROP_BLOCKING)) {
//Logger.getGlobal().info("cancel.");
            event.getItemDrop().remove();
        }
    }
    
    @EventHandler(ignoreCancelled = true)
    public void onBlockBurn(BlockBurnEvent event) {
        if(PluginData.isModuleEnabled(event.getBlock().getWorld(), Modules.FIRE_SPREAD_BLOCKING))
        event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockFade(BlockFadeEvent event) {
//Logger.getGlobal().info("Block fade.");
//Logger.getGlobal().info("Block decay: "+event.getBlock().getType().name()+" "
//                                       +event.getBlock().getX()
//                                       +" "+event.getBlock().getZ());
    if (PluginData.isModuleEnabled(event.getBlock().getWorld(), Modules.BLOCK_FADE_BLOCKING)) {
//Logger.getGlobal().info("Block decay canceled");
//Logger.getGlobal().info("cancel.");
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onLeavesDecay(LeavesDecayEvent event) {
        if (PluginData.isModuleEnabled(event.getBlock().getWorld(), Modules.DECAY_BLOCKING)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockForm(BlockFormEvent event) {
//Logger.getGlobal().info("Block Form.");
        if (PluginData.isModuleEnabled(event.getBlock().getWorld(), Modules.BLOCK_FORM_BLOCKING)) {
//Logger.getGlobal().info("cancel.");
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
//Logger.getGlobal().info("Block Spread.");
        if (PluginData.isModuleEnabled(event.getBlock().getWorld(), Modules.BLOCK_FORM_BLOCKING)) {
            event.setCancelled(true);
//Logger.getGlobal().info("cancel.");
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onStructureGrow(StructureGrowEvent event) {
        if (PluginData.isModuleEnabled(event.getWorld(), Modules.BLOCK_FORM_BLOCKING)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onMoistureChange(MoistureChangeEvent event) {
        if (PluginData.isModuleEnabled(event.getBlock().getWorld(), Modules.BLOCK_FORM_BLOCKING)) {
            event.setCancelled(true);
        }
    }
    
    @EventHandler(ignoreCancelled = true)
    public void onWalkMagma(EntityDamageEvent event) {
        if (PluginData.isModuleEnabled(event.getEntity().getWorld(), Modules.PLAYER_DAMAGE_BLOCKING)
                && event.getEntity() instanceof Player
                && (event.getCause().equals(DamageCause.FALL)
                    || event.getCause().equals(DamageCause.HOT_FLOOR)
                    || event.getCause().equals(DamageCause.CAMPFIRE))) {
            event.setCancelled(true);
        }
    }
    
    @EventHandler(ignoreCancelled = true)
    public void onGamemodeChange(PlayerGameModeChangeEvent event) {
        if (PluginData.isModuleEnabled(event.getPlayer().getWorld(), Modules.PLAYER_SURVIVAL_FLY)
                && event.getNewGameMode().equals(GameMode.SURVIVAL)) {
            event.getPlayer().setAllowFlight(true);
        }
    }
    
    @EventHandler(ignoreCancelled = true)
    public void onJoin(PlayerJoinEvent event) {
        if (PluginData.isModuleEnabled(event.getPlayer().getWorld(), Modules.PLAYER_SURVIVAL_FLY)
                && event.getPlayer().getGameMode().equals(GameMode.SURVIVAL)) {
            event.getPlayer().setAllowFlight(true);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void containerBreak(BlockBreakEvent event) {
        if (PluginData.isModuleEnabled(event.getPlayer().getWorld(), Modules.REMOVE_CONTAINER_ITEMS)) {
            if(event.getBlock().getState() instanceof Container container) {
                container.getInventory().clear();
            } else  if(event.getBlock().getState() instanceof Jukebox
                        || event.getBlock().getState() instanceof DecoratedPot) {
                BlockState state = event.getBlock().getState();
                state.setType(Material.AIR);
                state.update(true, false);
                event.setCancelled(true);
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void shootArrow(EntityShootBowEvent event) {
        if (PluginData.isModuleEnabled(event.getEntity().getWorld(), Modules.BLOCK_ARROW_SHOOTING)) {
            event.setCancelled(true);
        }
    }



/*    @EventHandler(ignoreCancelled = false)
    public void onFurnaceBurn(PlayerInteractEvent event) {
        if(event.hasBlock()
                && event.hasItem()
                && event.getItem().getType().equals(Material.RABBIT_HIDE)) {
Logger.getGlobal().info("Set biome ");
            World world = event.getClickedBlock().getWorld();
            for(int i = 0; i<16; i++) {
                for(int j= 0; j<256; j++) {
                    for(int k = 0; k<16;k++) {
                        if(j<11) 
                            world.setBiome(event.getClickedBlock().getX()+i, j, event.getClickedBlock().getZ()+k, Biome.FOREST);
                                    else 
                            world.setBiome(event.getClickedBlock().getX()+i, j, event.getClickedBlock().getZ()+k, Biome.SWAMP);
                    }
                }
            }
        }
        if(event.hasBlock()
                && event.hasItem()
                && event.getItem().getType().equals(Material.RABBIT_FOOT)) {
                    Logger.getGlobal().info("Biome: "+event.getClickedBlock().getBiome().name());
        }
    }*/
        
}
