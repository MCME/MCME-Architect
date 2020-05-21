/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.mcmiddleearth.architect.bannerEditor;

import com.mcmiddleearth.architect.ArchitectPlugin;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.UUID;

/**
 *
 * @author Eriol_Eandur
 */
public class BannerEditorConfig {
    
    private static final File dataDir = new File(ArchitectPlugin.getPluginInstance().getDataFolder()+"/banners");
    
    private static final String fileExtension = "yml";
    
    private int patternId = 0;
    
    private BannerEditorMode editorMode = BannerEditorMode.ORIENTATION;

    public BannerEditorMode getEditorMode() {
        return editorMode;
    }

    public void setEditorMode(BannerEditorMode editorMode) {
        this.editorMode = editorMode;
    }

    public int getPatternId() {
        return patternId;
    }

    public void setPatternId(int patternId) {
        this.patternId = patternId;
    }
    
    public boolean saveBanner(ItemStack banner, String fileName, String description,UUID creator) throws IOException {
        YamlConfiguration data = new YamlConfiguration();
        data.set("description", description);
        data.set("Banner", banner.serialize());
        data.set("creator",creator.toString());
        File saveFile = new File(dataDir+"/"+fileName+"."+fileExtension);
        if(!saveFile.exists()) {
            data.save(saveFile);
            return true;
        }
        else {
            return false;
        }
    }
    
    public ItemStack loadBanner(String filename){
        File file = new File(dataDir+"/"+filename+"."+fileExtension);
        YamlConfiguration data = new YamlConfiguration();
        try {
            data.load(file);
        } catch (IOException | InvalidConfigurationException ex) {
            return null;
        }
        Map<String, Object> bannerData = data.getConfigurationSection("Banner").getValues(true);
        if(bannerData==null) {
            return null;
        }
        return ItemStack.deserialize(bannerData);
    }

    public boolean isCreator(String filename, UUID creator) {
        File file = new File(dataDir+"/"+filename+"."+fileExtension);
        if(file.exists()) {
            try {
                YamlConfiguration data = new YamlConfiguration();
                data.load(file);
                return UUID.fromString(data.getString("creator")).equals(creator);
            } catch (IOException | InvalidConfigurationException | NullPointerException ex) {
                return false;
            }
        }
        else {
            return false;
        }
    }
    
    public boolean existsFile(String filename) {
        File file = new File(dataDir+"/"+filename+".yml");
        if(file.exists()) {
            return true;
        } else {
            file = new File(dataDir+"/"+filename);
            return file.exists() && file.isDirectory();
        }
    }
    
    public boolean deleteFile(String filename) {
        boolean result = false;
        File file = new File(dataDir+"/"+filename+".yml");
        if(file.exists()) {
            file.delete();
            result = true;
        }
        else {
            result =  false;
        }
        file = new File(dataDir+"/"+filename);
        if(file.exists() && file.isDirectory()) {
            if(file.listFiles().length==0) {
                file.delete();
                result = true;
            }
            else {
                result = false;
            }
        }
        return result;
    }

    public static File getDataDir() {
        return dataDir;
    }

    public static String getFileExtension() {
        return fileExtension;
    }
}
