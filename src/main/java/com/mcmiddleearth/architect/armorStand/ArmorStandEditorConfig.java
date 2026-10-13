
package com.mcmiddleearth.architect.armorStand;

import com.mcmiddleearth.architect.ArchitectPlugin;
import com.mcmiddleearth.architect.Log;
import com.mcmiddleearth.architect.Permission;
import com.mcmiddleearth.architect.PluginData;
import com.mcmiddleearth.architect.armorStand.guard.ArmorStandGuard;
import com.mcmiddleearth.pluginutil.FileUtil;
import com.mcmiddleearth.util.PathSafety;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.MemoryConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.ArmorStand;
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
public class ArmorStandEditorConfig {
    
    private ArmorStandPart part = ArmorStandPart.HEAD;
    
    private ArmorStandEditorMode editorMode = ArmorStandEditorMode.COPY;
    
    private int rotationStep = 10;
    
    private MemoryConfiguration copiedEntity;
    
    private boolean hasCopiedArmorStand = false;
    
    private Vector copiedArmorStandRelativeLoc = new Vector(0,0,0);
    
    private static final File dataDir = new File(ArchitectPlugin.getPluginInstance().getDataFolder(),"armorStands");
    
    private static final String fileExtension = "yml";
    
    public ArmorStandEditorConfig(Player p) {
        if(!dataDir.exists()) {
            dataDir.mkdirs();
        }
        clearCopiedArmorStand();
        if(!PluginData.hasPermission(p, Permission.ARMOR_STAND_EDITOR)) {
            editorMode = ArmorStandEditorMode.ROLLBACK;
        }
     }

    public void placeArmorStand(Location loc, boolean exact) {
        Map<String,Object> data = (Map<String,Object>)copiedEntity.get("ArmorStand");
        Location saved = deserializeLocation((Map<String,Object>) data.get("location"));
        if(!exact) {
            loc.setX(loc.getBlockX()+saved.getX()-saved.getBlockX());
            loc.setZ(loc.getBlockZ()+saved.getZ()-saved.getBlockZ());
            loc.setYaw(saved.getYaw());
        }
        data.put("location",serializeLocation(loc));
        ArmorStand armor = ArmorStandUtil.deserializeArmorStand((Map<String,Object>)copiedEntity.get("ArmorStand"));
        ArmorStandGuard.setModifiedFlag(armor);
        data.put("location",serializeLocation(saved));
    }
    
    public final void clearCopiedArmorStand() {
        copiedEntity = new MemoryConfiguration();
        Map<String,Object> data = new HashMap<String,Object>();
        data.put("location", serializeLocation(new Location(Bukkit.getWorlds().get(0),0.5,0,0.5,0,0)));
        copiedEntity.set("ArmorStand", data);
        hasCopiedArmorStand = false;
    }
    
    public void copyArmorStand(Player player, ArmorStand armor) {
        copiedEntity = new MemoryConfiguration();
        copiedEntity.set("ArmorStand",ArmorStandUtil.serializeArmorStand(armor));
        copiedArmorStandRelativeLoc = armor.getLocation().getBlock().getLocation().toVector()
                     .subtract(player.getLocation().toVector());
        hasCopiedArmorStand = true;
    }
    
    public boolean hasCopiedArmorStand() {
        return hasCopiedArmorStand;
    }
    
    /**
     * @return true if {@code filename} is safe to use inside the armor-stand directory; false
     *         (logged) if it would escape via directory traversal.
     */
    private static boolean isSafeArmorName(String filename, String operation) {
        try {
            PathSafety.resolveInside(dataDir, filename);
            return true;
        } catch (SecurityException ex) {
            Log.warn("Rejected unsafe armor-stand name '" + filename + "' for " + operation + ": " + ex.getMessage());
            return false;
        }
    }

    public boolean isCreator(String filename, UUID creator) {
        if(!isSafeArmorName(filename, "isCreator")) {
            return false;
        }
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
        if(!isSafeArmorName(filename, "save")) {
            return false;
        }
        YamlConfiguration data = new YamlConfiguration();
        data.set("creator", creator.toString());
        data.set("description", description);
        data.set("ArmorStand", copiedEntity.get("ArmorStand"));
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
        if(!isSafeArmorName(filename, "load")) {
            return false;
        }
        File file = new File(dataDir,filename+"."+fileExtension);
        if(file.exists()) {
            YamlConfiguration data = new YamlConfiguration();
            data.load(file);
            copiedEntity = new MemoryConfiguration();
            Map<String, Object> armorData = data.getConfigurationSection("ArmorStand").getValues(true);
            armorData.put("location", serializeLocation(new Location(Bukkit.getWorlds().get(0),0.5,0,0.5,0,0)));
            copiedEntity.set("ArmorStand",armorData);
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
            if(!isSafeArmorName(folder, "list")) {
                return new File[0];
            }
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
        if(!isSafeArmorName(filename, "exists")) {
            return false;
        }
        File file = new File(dataDir, filename+".yml");
        if(file.exists()) {
            return true;
        } else {
            file = new File(dataDir, filename);
            return file.exists() && file.isDirectory();
        }
    }
    
    public boolean deleteFile(String filename) {
        if(!isSafeArmorName(filename, "delete")) {
            return false;
        }
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
        if(!isSafeArmorName(filename, "rename") || !isSafeArmorName(newName, "rename")) {
            return false;
        }
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
    
    public ArmorStandEditorMode getEditorMode() {
        return editorMode;
    }

    public void setEditorMode(ArmorStandEditorMode editorMode) {
        this.editorMode = editorMode;
    }

    public ArmorStandPart getPart() {
        return part;
    }

    public void setPart(ArmorStandPart part) {
        this.part = part;
    }
    
    public void setRotationStep(int stepInDegree) {
        rotationStep = stepInDegree;
    }
    
    public int getRotationStep() {
        return rotationStep;
    }

    public Vector getCopiedArmorStandRelativeLoc() {
        return copiedArmorStandRelativeLoc;
    }

    public static File getDataDir() {
        return dataDir;
    }

    public static String getFileExtension() {
        return fileExtension;
    }
}
