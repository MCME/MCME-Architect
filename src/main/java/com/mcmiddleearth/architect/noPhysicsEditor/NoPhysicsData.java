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
package com.mcmiddleearth.architect.noPhysicsEditor;

import com.mcmiddleearth.architect.ArchitectPlugin;
import com.mcmiddleearth.architect.Modules;
import com.mcmiddleearth.architect.PluginData;
import com.mcmiddleearth.architect.WorldConfig;
import com.sk89q.worldedit.regions.CuboidRegion;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.util.Vector;

import java.io.*;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author Eriol_Eandur
 */
public class NoPhysicsData {
    
    private static final Map<String, ExceptionArea> exceptionAreas = new HashMap<>();
    
    private static final File dataFile = new File(ArchitectPlugin.getPluginInstance().getDataFolder(),
                                                "NoPhyExceptionAreas.txt");
    
    private static final Map<World, Set<BlockData>> noPhysicsLists = new HashMap<>();
    
    public static boolean isNoPhysicsBlock(Block block) {
        if(!PluginData.isModuleEnabled(block.getWorld(), Modules.NO_PHYSICS_LIST_ENABLED)) {
            return false;
        }
        Set<BlockData> noPhysicsList = noPhysicsLists.get(block.getWorld());
        if(noPhysicsList == null) {
            noPhysicsList = createNoPhysicsList(block.getWorld());
            noPhysicsLists.put(block.getWorld(), noPhysicsList);
        }
        BlockData data = block.getBlockData();
        boolean inverted = PluginData.isModuleEnabled(block.getWorld(), Modules.NO_PHYSICS_LIST_INVERTED);
        for(BlockData search: noPhysicsList) {
           if(data.matches(search)) {
               return !inverted;
           }
        }
        return inverted; //1.13 removed config.isNoPhysicsBlock(block.getBlockData());
    }
    
    private static Set<BlockData> createNoPhysicsList(World world) {
        Set<BlockData> noPhysicsList = new HashSet<>();
        for(String input: getNoPhysicsListAsStrings(world.getName())) {
            BlockData data = createBlockData(input);
            if(data != null) {
                noPhysicsList.add(data);
            }
        }
        return noPhysicsList;
    }
    
    public static BlockData createBlockData(String input) {
        try {
            return Bukkit.getServer().createBlockData("minecraft:"+input.toLowerCase());
        } catch(IllegalArgumentException e) {
            return null;
        }
    }
    
    public static List<String> getNoPhysicsListAsStrings(String worldName) {
        WorldConfig config = PluginData.getOrCreateWorldConfig(worldName);
        return config.getNoPhysicsListAsStrings();
    }

    public static boolean addNpBlock(String worldName, String blockData) {
        WorldConfig config = PluginData.getOrCreateWorldConfig(worldName);
        if(config.addToNpList(blockData)) {
            noPhysicsLists.clear();
            return true;
        }
        return false;
    }
    
    public static boolean removeNpBlock(String worldName, String blockData) {
        WorldConfig config = PluginData.getOrCreateWorldConfig(worldName);
        if(config.removeFromNpList(blockData)) {
            noPhysicsLists.clear();
            return true;
        }
        return false;
    }
    

    public static boolean hasNoPhysicsException(Block block) {
        for(ExceptionArea area: exceptionAreas.values()) {
            if(area.isAffected(block.getType()) && area.isInside(block.getLocation())) {
                return true;
            }
        }
        return false;
    }
    
    public static void setExceptionArea(String name, CuboidRegion region, String type) {
        if(type.equalsIgnoreCase("redstone")) {
            exceptionAreas.put(name, new RedstoneCircuitArea(region));
        } else {
            exceptionAreas.put(name, new WaterFlowArea(region));
        }
    }
    
    public static void deleteExceptionArea(String name) {
        exceptionAreas.remove(name);
    }
    
    public static boolean exceptionAreaExists(String name) {
        return exceptionAreas.containsKey(name);
    }
    
    public static void save() throws IOException {
        File temp = new File(dataFile.getAbsoluteFile()+".tmp");
        if(temp.exists()) {
            temp.delete();
        }
        try (FileWriter fw = new FileWriter(temp);
            PrintWriter writer = new PrintWriter(fw)) {
            for(String name: exceptionAreas.keySet()) {
                ExceptionArea area = exceptionAreas.get(name);
                writer.println(area.getX()+";"+area.getY()+";"+area.getZ()+";"
                        +area.getDX()+";"+area.getDY()+";"+area.getDZ()+";"
                        +area.getWorldUID()+";"+name);
            }
        }
        if(dataFile.exists()) {
            dataFile.delete();
        }
        temp.renameTo(dataFile);
    }
    
    public static void loadExceptionAreas() {
        try (Scanner scanner = new Scanner(dataFile)) {
            scanner.useDelimiter(";");
            while(scanner.hasNext()) {
                int minX = scanner.nextInt();
                int minY = scanner.nextInt();
                int minZ = scanner.nextInt();
                int maxX = minX+scanner.nextInt();
                int maxY = minY+scanner.nextInt();
                int maxZ = minZ+scanner.nextInt();
                Vector minPoint = new Vector(minX,minY,minZ);
                Vector maxPoint = new Vector(maxX,maxY,maxZ);
                UUID world = UUID.fromString(scanner.next());
                String name = scanner.nextLine().substring(1);
                if(world==null) continue;
                exceptionAreas.put(name,new RedstoneCircuitArea(world,minPoint,maxPoint));
            }
        } catch (FileNotFoundException ex) {
            Logger.getLogger(NoPhysicsData.class.getName()).log(Level.WARNING,"No physics exception data file not found.");
        }
    }

    public static Map<String, ExceptionArea> getExceptionAreas() {
        return exceptionAreas;
    }
}
