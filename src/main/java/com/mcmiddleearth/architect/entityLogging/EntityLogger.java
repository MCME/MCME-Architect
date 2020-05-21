/*
 * Copyright (C) 2020 Eriol_Eandur
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
package com.mcmiddleearth.architect.entityLogging;

import com.mcmiddleearth.architect.ArchitectPlugin;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Boat;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.Painting;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

/**
 *
 * @author Eriol_Eandur
 */
public class EntityLogger{
    
    private static BukkitTask loggerTask;
    
    private static Map<Coordinates,Integer[]> logData = new HashMap<>();
    
    private static File logFile = new File(ArchitectPlugin.getPluginInstance().getDataFolder(),"entityLog.dat");
    
    private static Listener listener; 
    
    private static Class[] entityTypes = new Class[]{ArmorStand.class,
                                                       Boat.class,
                                                       Arrow.class,
                                                       Item.class,
                                                       Painting.class,
                                                       ItemFrame.class};
    
    public static void setLogging(boolean on, World world) {
        if(on) {
            listener = new ELogListener();
            Bukkit.getPluginManager().registerEvents(listener, ArchitectPlugin.getPluginInstance());
            loggerTask = new BukkitRunnable() {
                int maxValue;
                @Override
                public void run() {
                    try(PrintWriter fw = new PrintWriter(new FileWriter(logFile))) {
                        String topLine = ";";
                        for (Class entityType : entityTypes) {
                            topLine = topLine +  ";" + entityType.getSimpleName();
                        }
                        fw.println(topLine+";all");
                        maxValue=0;
                        logData.forEach((coord,values)->{
                            String line = "";
                            int all = values[values.length-1];
                            if(maxValue<all) {
                                maxValue=all;
                            }
                            for (Integer value : values) {
                                line = line +  ";" + value;
                            }
                            fw.println(""+coord.x+";"+coord.z+line);
                                });
                        //new BukkitRunnable() {
                          //  @Override
                           // public void  run() {
                                logData.forEach((coord,values)->
                                    ELogDynmapUtil.createMarker(coord, entityTypes, values, maxValue, world));
                            //}
                        //}.runTask(ArchitectPlugin.getPluginInstance());
                        Logger.getLogger(ArchitectPlugin.class.getName()).log(Level.INFO, "Dumping Entity Logs");
                    } catch (IOException ex) {
                        Logger.getLogger(EntityLogger.class.getName()).log(Level.SEVERE, null, ex);
                    }
                }
            }.runTaskTimerAsynchronously(ArchitectPlugin.getPluginInstance(), 500, 2000);
        } else if(loggerTask!=null){
            loggerTask.cancel();
            HandlerList.unregisterAll(listener);
        }
    }
    
    public static class ELogListener implements Listener {
        
        @EventHandler
        public void onChunkLoad(ChunkLoadEvent event) {
            Chunk chunk = event.getChunk();
            Coordinates coords = new Coordinates(chunk.getX() * 16, chunk.getZ() *16);
            Entity[] entities = chunk.getEntities();
            Integer[] values = new Integer[entityTypes.length+1];
            for(int i=0; i<values.length;i++) {
                values[i] = 0;
            }
            if(entities.length>0) {
                for(Entity entity: entities) {
                    for(int i = 0; i< values.length-1; i++) {
                        if(entityTypes[i].isInstance(entity)) {
                            values[i]++;
                            break;
                        }
                    }
                }
                values[values.length-1] = entities.length;
                logData.put(coords, values);
            }
        }
    }
    
    public static class Coordinates {
        
        public int x;
        public int z;
        
        public Coordinates(int x, int z) {
            this.x = x;
            this.z = z;
        }

        @Override
        public int hashCode() {
            int hash = 7;
            hash = 47 * hash + this.x;
            hash = 47 * hash + this.z;
            return hash;
        }
        
        @Override
        public boolean equals(Object other) {
            return other instanceof Coordinates && this.x == ((Coordinates)other).x && this.z == ((Coordinates)other).z;
        }
    }
}
