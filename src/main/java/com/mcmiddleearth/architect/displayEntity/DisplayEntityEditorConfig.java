
package com.mcmiddleearth.architect.displayEntity;

import com.mcmiddleearth.architect.ArchitectPlugin;
import com.mcmiddleearth.architect.Permission;
import com.mcmiddleearth.architect.PluginData;
import com.mcmiddleearth.pluginutil.FileUtil;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.MemoryConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Display;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.io.File;
import java.io.IOException;
import java.util.*;

import static com.mcmiddleearth.pluginutil.ConfigurationUtil.deserializeLocation;
import static com.mcmiddleearth.pluginutil.ConfigurationUtil.serializeLocation;

/**
 *
 * @author Eriol_Eandur
 */
public class DisplayEntityEditorConfig {
    
    private DisplayEntityEditorMode editorMode = DisplayEntityEditorMode.XMOVE;
    
    private int rotationStep = 10;
    
    private MemoryConfiguration copiedEntity;
    
    private boolean hasCopiedEntity = false;
    
    private Vector copiedEntityRelativeLoc = new Vector(0,0,0);
    
    private static final File dataDir = new File(ArchitectPlugin.getPluginInstance().getDataFolder(),"displayEntities");
    
    private static final String fileExtension = "yml";
    
    public DisplayEntityEditorConfig(Player p) {
        if(!dataDir.exists()) {
            dataDir.mkdirs();
        }
        clearCopiedDisplayEntities();
        if(!PluginData.hasPermission(p, Permission.DISPLAY_ENTITY_EDITOR)) {
            editorMode = DisplayEntityEditorMode.ROLLBACK;
        }
     }

    public void placeDisplayEntity(Location loc, boolean exact) {
        Map<String,Object> data = (Map<String,Object>)copiedEntity.get("DisplayEntity");
        Location saved = deserializeLocation((Map<String,Object>) data.get("location"));
        if(!exact) {
            loc.setX(loc.getBlockX()+saved.getX()-saved.getBlockX());
            loc.setZ(loc.getBlockZ()+saved.getZ()-saved.getBlockZ());
            loc.setYaw(saved.getYaw());
        }
        data.put("location",serializeLocation(loc));
        Display entity = DisplayEntityUtil.deserializeDisplayEntity((Map<String,Object>)copiedEntity.get("DisplayEntity"));
        data.put("location",serializeLocation(saved));
    }
    
    public final void clearCopiedDisplayEntities() {
        copiedEntity = new MemoryConfiguration();
        Map<String,Object> data = new HashMap<String,Object>();
        data.put("location", serializeLocation(new Location(Bukkit.getWorlds().get(0),0.5,0,0.5,0,0)));
        copiedEntity.set("Display Entity", data);
        hasCopiedEntity = false;
    }
    
    public void copyDisplayEntity(Player player, Display displayEntity) {
        copiedEntity = new MemoryConfiguration();
        copiedEntity.set("Display Entity", DisplayEntityUtil.serializeDisplayEntity(displayEntity));
        copiedEntityRelativeLoc = displayEntity.getLocation().getBlock().getLocation().toVector()
                     .subtract(player.getLocation().toVector());
        hasCopiedEntity = true;
    }
    
    public boolean hasCopiedDisplayEntity() {
        return hasCopiedEntity;
    }
    
    public boolean isCreator(String filename, UUID creator) {
        File file = new File(dataDir,filename+"."+fileExtension);
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
    
    public boolean saveArmorStand(String filename, String description, UUID creator) throws IOException {
        YamlConfiguration data = new YamlConfiguration();
        data.set("creator", creator.toString());
        data.set("description", description);
        data.set("DisplayEntity", copiedEntity.get("DisplayEntity"));
        File saveFile = new File(dataDir, filename+"."+fileExtension);
        if(!saveFile.exists()) {
            data.save(saveFile);
            return true;
        }
        else {
            return false;
        }
    }
    
    public boolean loadArmorStand(String filename) throws IOException, InvalidConfigurationException {
        File file = new File(dataDir,filename+"."+fileExtension);
        if(file.exists()) {
            YamlConfiguration data = new YamlConfiguration();
            data.load(file);
            copiedEntity = new MemoryConfiguration();
            Map<String, Object> displayData = data.getConfigurationSection("DisplayEntity").getValues(true);
            displayData.put("location", serializeLocation(new Location(Bukkit.getWorlds().get(0),0.5,0,0.5,0,0)));
            copiedEntity.set("ArmorStand",displayData);
            return true;
        }
        else {
            return false;
        }
    }
    
    public File[] getFiles(String folder) {
        File dir;
        if(folder == null || folder.equals("")) {
            dir = dataDir;
        }
        else {
            dir = new File(dataDir,folder);
        }
        List<File> list = new ArrayList<File>();
        File[] files = dir.listFiles(FileUtil.getDirFilter());
        Set<File> set = new TreeSet<File>();
        if(files.length>0) {
            set.addAll(Arrays.asList(files));
            list.addAll(set);
            set.clear();
        }
        files = dir.listFiles(FileUtil.getFileExtFilter("yml"));
        if(files.length>0) {
            set.addAll(Arrays.asList(files));
            list.addAll(set);
        }
        return list.toArray(new File[0]);
    }
    
    public boolean existsFile(String filename) {
        File file = new File(dataDir, filename+".yml");
        if(file.exists()) {
            return true;
        } else {
            file = new File(dataDir, filename);
            return file.exists() && file.isDirectory();
        }
    }
    
    public boolean deleteFile(String filename) {
        boolean result = false;
        File file = new File(dataDir, filename+".yml");
        if(file.exists()) {
            file.delete();
            result = true;
        }
        else {
            result =  false;
        }
        file = new File(dataDir, filename);
        if(file.exists() && file.isDirectory()) {
            if(file.listFiles().length==0) {
                return file.delete();
            }
            else {
                result = false;
            }
        }
        return result;
    }
    
    public boolean renameFile(String filename, String newName) {
        boolean result = false;
        File file = new File(dataDir, filename+".yml");
        File newFile = new File(dataDir, newName+".yml");
        if(file.exists() && !newFile.exists()) {
            return file.renameTo(newFile);
        }
        else {
            result =  false;
        }
        return result;
    }
    
    public String getDescription(File file) {
        if(file.exists()) {
            YamlConfiguration data = new YamlConfiguration();
            try {
                data.load(file);
            } catch (IOException ex) {
                return "";
            } catch (InvalidConfigurationException ex) {
                return "";
            }
            return data.getString("description");
        }
        else {
            return "";
        }
    }
    
    public DisplayEntityEditorMode getEditorMode() {
        return editorMode;
    }

    public void setEditorMode(DisplayEntityEditorMode editorMode) {
        this.editorMode = editorMode;
    }

    public void setRotationStep(int stepInDegree) {
        rotationStep = stepInDegree;
    }
    
    public int getRotationStep() {
        return rotationStep;
    }

    public Vector getCopiedEntityRelativeLoc() {
        return copiedEntityRelativeLoc;
    }

    public static File getDataDir() {
        return dataDir;
    }

    public static String getFileExtension() {
        return fileExtension;
    }
}
