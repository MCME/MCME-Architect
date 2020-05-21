/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.mcmiddleearth.architect.armorStand.guard;

import com.mcmiddleearth.architect.ArchitectPlugin;
import java.util.HashMap;
import java.util.Map;
import org.bukkit.Location;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

/**
 *
 * @author Eriol_Eandur
 */
public class ArmorStandGuard {

    private final static Map<Player,AbstractRollback> currentRollbacks = new HashMap<>();
    
    private static CleanupTask cleanupTask;

    private static StoreTask storeTask;
    
    private final static int storePeriod = 20*60*10;
    
    static void init() {
        storeTask = new StoreTask();
        storeTask.runTaskTimer(ArchitectPlugin.getPluginInstance(), storePeriod, storePeriod);
    }
    
    static void removeRollback(Player player) {
    }

    static void startSingleRollback(Player player, ArmorStand armorStand) {
    }

    static void startShpereRollback(Player player, Location location, int aInt) {
    }

    static AbstractRollback getRollback(Player player) {
        return null;
    }

    public static void setModifiedFlag(ArmorStand armor) {
    }
    
    private static class CleanupTask extends BukkitRunnable {
        @Override
        public void run() {
        }
    }
    
    private static class StoreTask extends BukkitRunnable {
        @Override
        public void run() {
        }
    }
    
    
    
}
