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
import com.mcmiddleearth.architect.Log;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
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
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.event.world.EntitiesUnloadEvent;
import org.bukkit.scheduler.BukkitTask;

/**
 *
 * @author Eriol_Eandur
 */
public class EntityLogger{
    
    private static BukkitTask loggerTask;
    
    private static final Map<Coordinates,Integer[]> logData = new java.util.concurrent.ConcurrentHashMap<>();
    
    private static final File logFile = new File(ArchitectPlugin.getPluginInstance().getDataFolder(),"entityLog.dat");
    
    private static Listener listener; 
    
    static final Class[] entityTypes = new Class[]{ArmorStand.class,
                                                       Boat.class,
                                                       Arrow.class,
                                                       Item.class,
                                                       Painting.class,
                                                       ItemFrame.class};
    
    /**
     * Starts logging, unless it is already on: a second listener and timer would replace the
     * references to the first pair, which could then never be stopped.
     * @return false if logging was already on
     */
    public static boolean start() {
        if(loggerTask!=null) {
            return false;
        }
        listener = new ELogListener();
        Bukkit.getPluginManager().registerEvents(listener, ArchitectPlugin.getPluginInstance());
        loggerTask = Bukkit.getScheduler().runTaskTimer(ArchitectPlugin.getPluginInstance(),
                                                        EntityLogger::dump, 500, 2000);
        return true;
    }

    /**
     * Stops logging and forgets the counts, taking the markers off the map.
     * @return false if logging was already off
     */
    public static boolean stop() {
        if(loggerTask==null) {
            return false;
        }
        loggerTask.cancel();
        loggerTask = null;
        HandlerList.unregisterAll(listener);
        listener = null;
        logData.clear();
        ELogDynmapUtil.clearMarkers();
        return true;
    }

    // Main thread, which owns logData. The map and the file are made from one snapshot, so the
    // colours are scaled to the counts they show; only the file write leaves the main thread, and
    // it gets the immutable snapshot, never logData itself. The write is handed off first, so a
    // failing dynmap cannot cost the log.
    private static void dump() {
        Map<Coordinates,Integer[]> snapshot = Map.copyOf(logData);
        Bukkit.getScheduler().runTaskAsynchronously(ArchitectPlugin.getPluginInstance(),
                                                    () -> writeLog(snapshot));
        ELogDynmapUtil.showCounts(snapshot);
    }

    private static void writeLog(Map<Coordinates,Integer[]> snapshot) {
        try(PrintWriter fw = new PrintWriter(new FileWriter(logFile))) {
            String topLine = "world;x;z";
            for (Class entityType : entityTypes) {
                topLine = topLine +  ";" + entityType.getSimpleName();
            }
            fw.println(topLine+";all");
            snapshot.forEach((coord,values)->{
                String line = "";
                for (Integer value : values) {
                    line = line +  ";" + value;
                }
                fw.println(coord.world()+";"+coord.x()+";"+coord.z()+line);
            });
            Log.debug("Dumped entity logs for " + snapshot.size() + " chunk(s) to " + logFile.getName());
        } catch (IOException ex) {
            Log.error("Failed to write entity log file " + logFile.getAbsolutePath(), ex);
        }
    }

    public static class ELogListener implements Listener {

        // EntitiesLoadEvent carries the chunk's entities. (Paper fires it right after
        // ChunkLoadEvent; on Spigot entities load after the chunk, so ChunkLoadEvent misses them.)
        @EventHandler
        public void onEntitiesLoad(EntitiesLoadEvent event) {
            count(event.getChunk(), event.getEntities());
        }

        // All of the chunk's entities as it unloads, so those added or removed while it was
        // loaded count too. chunk.getEntities() would be empty by then: it skips untracked
        // entities, and Paper stops tracking them before the chunk unloads.
        @EventHandler
        public void onEntitiesUnload(EntitiesUnloadEvent event) {
            count(event.getChunk(), event.getEntities());
        }

        private static void count(Chunk chunk, List<Entity> entities) {
            Coordinates coords = new Coordinates(chunk.getWorld().getName(), chunk.getX() * 16, chunk.getZ() *16);
            if(entities.isEmpty()) {
                logData.remove(coords);
                return;
            }
            Integer[] values = new Integer[entityTypes.length+1];
            for(int i=0; i<values.length;i++) {
                values[i] = 0;
            }
            for(Entity entity: entities) {
                for(int i = 0; i< values.length-1; i++) {
                    if(entityTypes[i].isInstance(entity)) {
                        values[i]++;
                        break;
                    }
                }
            }
            values[values.length-1] = entities.size();
            // Always a new array, never changed once stored: dump() shares it with the writer thread.
            logData.put(coords, values);
        }
    }
    
    /**
     * A chunk: its world's name and its first block (x, z). The world belongs in the key, since
     * every world has a chunk at each (x, z).
     */
    public record Coordinates(String world, int x, int z) {
    }
}
