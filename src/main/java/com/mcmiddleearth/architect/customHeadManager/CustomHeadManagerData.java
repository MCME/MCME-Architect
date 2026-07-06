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
package com.mcmiddleearth.architect.customHeadManager;

import com.mcmiddleearth.architect.ArchitectPlugin;
import com.mcmiddleearth.architect.Log;
import com.mcmiddleearth.pluginutil.FileUtil;
import com.mcmiddleearth.util.HeadUtil;
import com.mcmiddleearth.util.PathSafety;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.util.*;

import static com.mcmiddleearth.pluginutil.ConfigurationUtil.deserializeLocation;
import static com.mcmiddleearth.pluginutil.ConfigurationUtil.serializeLocation;

/**
 *
 * @author Eriol_Eandur
 */
public class CustomHeadManagerData {
    
    private static final File acceptedHeadDir = new File(ArchitectPlugin.getPluginInstance()
                                                       .getDataFolder(),"customHeads/accepted");
    private static final File submittedHeadDir = new File(ArchitectPlugin.getPluginInstance()
                                                       .getDataFolder(),"customHeads/submitted");
    
    private static final String fileExtension = "yml";
    private static CustomHeadCollection collection = new CustomHeadCollection();
    
    private static CustomHeadGallery gallery;
    
    private static final String CONFIG_KEY = "headGalleryLocation";
    
    static {
        if(!acceptedHeadDir.exists()) {
            acceptedHeadDir.mkdirs();
        }
        if(!submittedHeadDir.exists()) {
            submittedHeadDir.mkdirs();
        }
    }
    
    public static void load() {
        if(gallery!=null) {
            gallery.remove();
            gallery = null;
        }
        collection = new CustomHeadCollection();
        List<File> headFiles = getFiles(acceptedHeadDir);
        for(File headFile : headFiles) {
            String headName = headFile.toString().substring(0,headFile.toString().lastIndexOf("."));
            headName = headName.replace('\\', '/');
            headName = headName.substring(acceptedHeadDir.toString().length()+1);
            collection.addHead(headName, CustomHeadData.fromFile(headFile));
        }
        if(!ArchitectPlugin.getPluginInstance().getConfig().isConfigurationSection(CONFIG_KEY)) {
            return;
        }
        Map<String,Object> galleryLoc = ArchitectPlugin.getPluginInstance()
                                            .getConfig()
                                            .getConfigurationSection(CONFIG_KEY)
                                            .getValues(true);
        if(galleryLoc == null){
            return;
        }
        Location loc = deserializeLocation(galleryLoc);
        if(loc == null) {
            return;
        }
        gallery = new CustomHeadGallery(collection, loc);
    }
    
    private static List<File> getFiles(File directory) {
        File[] subDirs = directory.listFiles(FileUtil.getDirFilter());
        List<File> fileList = new ArrayList<>();
        File[] headFiles = directory.listFiles(FileUtil.getFileExtFilter(fileExtension));
        Collections.addAll(fileList, headFiles);
        for(File subDir : subDirs) {
            fileList.addAll(getFiles(subDir));
        }
        return fileList;
    }
    
    public static void saveGallery() {
        ConfigurationSection config = ArchitectPlugin.getPluginInstance().getConfig();
        config.createSection(CONFIG_KEY, serializeLocation(gallery.getLocation()));
        ArchitectPlugin.getPluginInstance().saveConfig();
    }
    
    public static String getFullName(String name) {
        return collection.getFullName(name);
    }
    
    public static ItemStack getSumittedHead(String name) {
        File file = safeHeadFile(submittedHeadDir, name, "get submitted head");
        if(file==null || !file.exists()) {
            return null;
        }
        CustomHeadData data = CustomHeadData.fromFile(file);
        if(data==null) {
            return null;
        } else {
            return HeadUtil.getCustomHead(name, data.getHeadId(), data.getTexture());
        }
    }
    
    public static ItemStack getHead(String name) {
        File file = safeHeadFile(acceptedHeadDir, name, "get head");
        if(file==null || !file.exists()) {
            return null;
        }
        CustomHeadData data = CustomHeadData.fromFile(file);
        if(data==null) {
            return null;
        } else {
            return HeadUtil.getCustomHead(name, data.getHeadId(), data.getTexture());
        }
    }
    
    public static Map<String,ItemStack> getHeads() {
        Map<String, ItemStack> headMap = new HashMap<>();
        collection.getAllHeadsIncludingSubCollections(headMap);
        return headMap;
    }
    
    public static String getHeadName(UUID headId) {
        return collection.getHeadName(headId);
    }
    
    public static boolean acceptHead(String name, String newName) {
        CustomHeadData data = getSubmittedHeadData(name);
        if(data!=null) {
            File file = safeHeadFile(acceptedHeadDir, newName, "accept head");
            if(file==null || file.exists()) {
                return false;
            }
            if(data.saveToFile(file)){
                if(gallery!=null) {
                    gallery.remove();
                }
                collection.addHead(newName, data);
                file = safeHeadFile(submittedHeadDir, name, "accept head");
                if(file==null) {
                    return false;
                }
                removeFileAndDirectory(file);
                if(gallery!=null) {
                    gallery.place();
                }
                return true;
            }
        }
        return false;
    }
    
    public static boolean rejectHead(String name) {
        File file = safeHeadFile(submittedHeadDir, name, "reject head");
        if(file==null) {
            return false;
        }
        if(file.exists()) {
            removeFileAndDirectory(file);
            return true;
        }
        return false;
    }
    
    public static boolean addHead(String name, CustomHeadData headData) {
        if(addHead(name, headData, acceptedHeadDir)) {
            if(gallery!=null) {
                gallery.remove();
            }
            collection.addHead(name, headData);
            if(gallery!=null) {
                gallery.place();
            }
            return true;
        }
        return false;
    }
    
    public static boolean addReviewHead(String name, CustomHeadData headData) {
        return addHead(name, headData, submittedHeadDir);
    }
    
    private static boolean addHead(String name, CustomHeadData headData, File dir) {
        File file = safeHeadFile(dir, name, "add head");
        if(file==null) {
            return false;
        }
        int index = 0;
        while(file.exists()) {
            index++;
            file = safeHeadFile(dir, name+index, "add head");
            if(file==null) {
                return false;
            }
        }
        return headData.saveToFile(file);
    }
    
    public static boolean deleteHead(String name) {
        File file = safeHeadFile(acceptedHeadDir, name, "delete head");
        if(file==null) {
            return false;
        }
        if(file.exists()) {
            if(gallery!=null) {
                gallery.remove();
            }
            collection.removeHead(name);
            if(gallery!=null) {
                gallery.place();
            }
            removeFileAndDirectory(file);
            return true;
        }
        return false;
    }
    
    public static boolean renameHead(String oldName, String newName) {
        File oldFile = safeHeadFile(acceptedHeadDir, oldName, "rename head");
        File newFile = safeHeadFile(acceptedHeadDir, newName, "rename head");
        if(oldFile==null || newFile==null) {
            return false;
        }
        if(oldFile.exists() && !newFile.exists()) {
            CustomHeadData data = getHeadData(oldName);
            if(data.saveToFile(newFile)) {
                if(gallery!=null) {
                    gallery.remove();
                }
                collection.removeHead(oldName);
                collection.addHead(newName, data);
                removeFileAndDirectory(oldFile);
                if(gallery!=null) {
                    gallery.place();
                }
                return true;
            }
        }
        return false;
    }
    
    /**
     * Resolve a head file inside {@code baseDir}, guarding against directory traversal.
     * @return the safe file, or {@code null} if {@code name} escapes {@code baseDir} (rejection is logged).
     */
    private static File safeHeadFile(File baseDir, String name, String operation) {
        try {
            return PathSafety.resolveInside(baseDir, name, fileExtension);
        } catch (SecurityException ex) {
            Log.warn("Rejected unsafe custom-head name '" + name + "' for " + operation + ": " + ex.getMessage());
            return null;
        }
    }

    private static void removeFileAndDirectory(File file) {
        if(!PathSafety.isInside(acceptedHeadDir, file) && !PathSafety.isInside(submittedHeadDir, file)) {
            Log.warn("Refusing to delete '" + file + "': it is outside the custom-head directories.");
            return;
        }
        if(!file.isDirectory()) {
            file.delete();
            file = file.getParentFile();
        }
        File[] children;
        while(file != null
                && !file.equals(acceptedHeadDir) && !file.equals(submittedHeadDir)
                && (PathSafety.isInside(acceptedHeadDir, file) || PathSafety.isInside(submittedHeadDir, file))
                && (children = file.listFiles()) != null && children.length == 0) {
            file.delete();
            file = file.getParentFile();
        }
    }
    
    public static CustomHeadData getSubmittedHeadData(String name) {
        File file = safeHeadFile(submittedHeadDir, name, "get submitted head data");
        if(file==null || !file.exists()) {
            return null;
        }
        return CustomHeadData.fromFile(file);
    }
    
    public static CustomHeadData getHeadData(String name) {
        File file = safeHeadFile(acceptedHeadDir, name, "get head data");
        if(file==null || !file.exists()) {
            return null;
        }
        return CustomHeadData.fromFile(file);
    }
    
    public static void submitHead(Player submitter, UUID owner, String name) {
        new HeadDataBuilderPlayer(submitter, owner, name);
    }

    public static void submitHead(Player submitter, String owner, String name) {
        new HeadDataBuilderPlayer(submitter, owner, name);
    }

    public static void submitHeadFromItem(Player submitter, String name) {
        new HeadDataBuilderItem(submitter, name);
    }

    public static void upload(Player sender, String filename, boolean uploadToReviewFolder) {
        new HeadDataBuilderText(sender, filename, uploadToReviewFolder).start();
    }

    public static File getAcceptedHeadDir() {
        return acceptedHeadDir;
    }

    public static File getSubmittedHeadDir() {
        return submittedHeadDir;
    }

    public static String getFileExtension() {
        return fileExtension;
    }

    public static CustomHeadCollection getCollection() {
        return collection;
    }

    public static CustomHeadGallery getGallery() {
        return gallery;
    }

    public static void setGallery(CustomHeadGallery gallery) {
        CustomHeadManagerData.gallery = gallery;
    }
}
