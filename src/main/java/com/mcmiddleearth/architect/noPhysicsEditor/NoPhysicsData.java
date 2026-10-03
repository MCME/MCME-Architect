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
import com.mcmiddleearth.architect.Log;
import com.mcmiddleearth.architect.Modules;
import com.mcmiddleearth.architect.PluginData;
import com.mcmiddleearth.architect.WorldConfig;
import com.mcmiddleearth.architect.mapLayers.MapLayers;
import com.mcmiddleearth.architect.mapLayers.NoPhysicsLayer;
import com.sk89q.worldedit.regions.CuboidRegion;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.*;

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
        MapLayers.changed(NoPhysicsLayer.ID);
    }
    
    public static void deleteExceptionArea(String name) {
        exceptionAreas.remove(name);
        MapLayers.changed(NoPhysicsLayer.ID);
    }
    
    public static boolean exceptionAreaExists(String name) {
        return exceptionAreas.containsKey(name);
    }
    
    /**
     * Writes every area to a temporary file, then moves that over NoPhyExceptionAreas.txt in one step: the file is
     * never missing, and a failed write never replaces it. Any failure throws, so the command says it failed.
     */
    public static void save() throws IOException {
        Path temp = new File(dataFile.getAbsoluteFile()+".tmp").toPath();
        List<String> lines = new ArrayList<>();
        for(String name: exceptionAreas.keySet()) {
            lines.add(ExceptionAreaLine.format(name, exceptionAreas.get(name)));
        }
        Files.deleteIfExists(temp); // a leftover the server may not be allowed to write to
        Files.write(temp, lines, StandardCharsets.UTF_8); // unlike a PrintWriter, it throws a write error
        // the same folder is always the same file system, so the move can always be atomic
        Files.move(temp, dataFile.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    }
    
    /**
     * Reads NoPhyExceptionAreas.txt; a line that cannot be read is skipped and logged. The next save drops such a
     * line, so the file as it was is then kept in {@link #backupFile()}.
     */
    public static void loadExceptionAreas() {
        try {
            byte[] bytes = Files.readAllBytes(dataFile.toPath());
            // decoded leniently: a stray byte costs one character of a name, not every area
            String text = new String(bytes, StandardCharsets.UTF_8);
            if(text.startsWith("\uFEFF")) {
                text = text.substring(1);
            }
            boolean skipped = false;
            for(String line: text.lines().toList()) {
                if(line.isBlank()) {
                    continue;
                }
                try {
                    Map.Entry<String, ExceptionArea> read = ExceptionAreaLine.parse(line);
                    exceptionAreas.put(read.getKey(), read.getValue());
                } catch(RuntimeException ex) {
                    skipped = true;
                    Log.warn("Skipping an unreadable line in " + dataFile.getName() + " (" + ex
                            + "; it is dropped at the next save): " + line);
                }
            }
            if(skipped) {
                keepCopy(bytes);
            }
        } catch (NoSuchFileException ex) {
            Log.warn("No-physics exception area data file not found (expected on first run): " + dataFile.getAbsolutePath());
        } catch (IOException ex) {
            Log.error("Failed to read no-physics exception areas from " + dataFile.getAbsolutePath(), ex);
        }
        MapLayers.changed(NoPhysicsLayer.ID);
    }

    /** A whole-file failure, such as a file saved as UTF-16, skips every line: the next save would lose them all. */
    private static void keepCopy(byte[] bytes) {
        try {
            Files.write(backupFile().toPath(), bytes);
            Log.warn("A copy of " + dataFile.getName() + " with the skipped lines is kept in "
                    + backupFile().getName());
        } catch(IOException ex) {
            Log.error("Could not keep a copy of " + dataFile.getName() + " in " + backupFile().getName(), ex);
        }
    }

    /** Where a load that skipped a line keeps NoPhyExceptionAreas.txt as it was, replacing an older copy. */
    public static File backupFile() {
        return new File(dataFile.getPath() + ".bak");
    }

    public static Map<String, ExceptionArea> getExceptionAreas() {
        return exceptionAreas;
    }
}
