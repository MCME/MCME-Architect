/*
 * Copyright (C) 2019 Eriol_Eandur
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
package com.mcmiddleearth.architect.copyPaste;

import com.mcmiddleearth.architect.ArchitectPlugin;
import com.mcmiddleearth.pluginutil.NumericUtil;
import com.mcmiddleearth.pluginutil.plotStoring.IStoragePlot;
import com.sk89q.worldedit.regions.CuboidRegion;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.bukkit.entity.Player;
import org.bukkit.permissions.PermissionAttachmentInfo;
import org.bukkit.scheduler.BukkitRunnable;

/**
 *
 * @author Eriol_Eandur
 */
public class CopyPasteManager {
 
    private static final Map<UUID, Clipboard> clipboards = new HashMap<>();

    private static final Map<UUID, List<UndoData>> undoData = new HashMap<>();
    
    private static final Map<UUID, List<UndoData>> redoData = new HashMap<>();
    
    private static final String saveUndoFailed = "Failed to save undo data";
    
    private static final int maxSize = ArchitectPlugin.getPluginInstance().getConfig()
                                                       .getInt("CopyPasteUndoLimit",30);
    
    private static final File clipboardFolder = new File(ArchitectPlugin.getPluginInstance().getDataFolder(),"clipboards");
    private static final String cbExtension = ".cbd";
    
    static {
        if(!clipboardFolder.exists()) {
            clipboardFolder.mkdir();
        }
    }
    
    public static boolean copyToClipboard(Player player, CuboidRegion weRegion) throws CopyPasteException{
//Logger.getGlobal().info("2");
        Clipboard cb = new Clipboard(player.getLocation(),weRegion);
        clipboards.put(player.getUniqueId(), cb);
        if(cb.copyToClipboard()) {
            cb.saveToFile(new File(clipboardFolder,player.getUniqueId().toString()+cbExtension));
            return true; 
        }
        return false;
    }
    
    public static boolean cutToClipboard(Player player, CuboidRegion weRegion) throws CopyPasteException {
        Clipboard cb = new Clipboard(player.getLocation(),weRegion);
        if(!saveUndoData(player,cb)) {
            throw new CopyPasteException(saveUndoFailed);
        }
        cb = new Clipboard(player.getLocation(),weRegion);
        clipboards.put(player.getUniqueId(), cb);
        if(cb.cutToClipboard()) {
            cb.saveToFile(new File(clipboardFolder,player.getUniqueId().toString()+".cbd"));
            return true; 
        }
        return false;
    }

    public static boolean pasteClipboard(Player player, boolean withAir, boolean withBiome) throws CopyPasteException {
        if(clipboards.containsKey(player.getUniqueId())) {
            Clipboard cb = clipboards.get(player.getUniqueId());
            if(!saveUndoData(player,cb.getPastePlot(player.getLocation()))) {
               throw new CopyPasteException(saveUndoFailed);
            }
            return cb.paste(player.getLocation(), withAir, withBiome);
        }
        return false;
    }
    
    public static void rotateClipboard(Player player, int degree) {
        if(clipboards.containsKey(player.getUniqueId())) {
            clipboards.get(player.getUniqueId()).rotate(degree);
        }
    }
    
    public static void flipClipboard(Player player, char axis) {
        if(clipboards.containsKey(player.getUniqueId())) {
            clipboards.get(player.getUniqueId()).flip(axis);
        }
    }
    
    public static boolean isAxis(String axis) {
        switch(axis.charAt(0)) {
            case 'x':
            case 'y':
            case 'z':
                return true;
            default:
                return false;
        }
    }
    
    public static boolean hasClipboard(Player player) {
        return clipboards.containsKey(player.getUniqueId());
    }

    public static int maxAllowedSize(Player player) {
        Set<PermissionAttachmentInfo> permissions = player.getEffectivePermissions();
        int maxLimit = 0;
        for(PermissionAttachmentInfo info: permissions) {
            if(info.getPermission().startsWith("architect.copypaste.limit.")) {
                String limitString = info.getPermission().replace("architect.copypaste.limit.", "");
                if(NumericUtil.isInt(limitString)) {
                    int limit = NumericUtil.getInt(limitString);
                    if(limit>maxLimit) {
                        maxLimit = limit;
                    }
                }
            }
        }
        return maxLimit;
    }
    
    public static boolean hasUndos(Player player) {
        return hasData(player, undoData);
    }
    
    public static boolean hasRedos(Player player) {
        return hasData(player, redoData);
    }
    
    private static boolean hasData(Player player, Map<UUID,List<UndoData>> map) {
        return !(map.get(player.getUniqueId())==null 
               || map.get(player.getUniqueId()).isEmpty());
    }
    
    public static void clearUndoData(Player player) {
        undoData.remove(player.getUniqueId());
    }
    
    public static int undoEdits(Player player, int undos) throws CopyPasteException {
        int count = 0;
        for(int i=0; i<undos; i++) {
            if(hasUndos(player)) {
                List<UndoData> list = undoData.get(player.getUniqueId());
                UndoData data = list.get(list.size()-1);
                saveRedoData(player,data);
                if(!data.undo()) {
                    throw new CopyPasteException("Error while undoing edits (reverted "+count+" edits before the error occured.");
                }
                list.remove(list.size()-1);
                count++;
            } else {
                return count;
            }
        }
        return count;
    }
    
    public static int redoEdits(Player player, int redos) throws CopyPasteException {
        int count = 0;
        for(int i=0; i<redos; i++) {
            if(hasRedos(player)) {
                List<UndoData> list = redoData.get(player.getUniqueId());
                UndoData data = list.get(list.size()-1);
                saveUndoData(player,data);
                if(!data.undo()) {
                    throw new CopyPasteException("Error while redoing edits (redone "+count+" edits before the error occured.");
                }
                list.remove(list.size()-1);
                count++;
            } else {
                return count;
            }
        }
        return count;
    }
    
    private static boolean saveUndoData(Player player, IStoragePlot plot) {
        return saveData(player, undoData, plot);
    }
    
    private static boolean saveRedoData(Player player, IStoragePlot plot) {
        return saveData(player, redoData, plot);
    }
    
    private static boolean saveData(Player player, Map<UUID,List<UndoData>> map, IStoragePlot plot) {
        List<UndoData> list = map.computeIfAbsent(player.getUniqueId(), k -> new ArrayList<>());
        try{
            UndoData data = new UndoData(plot);
            list.add(data);
            if(list.size()>maxSize) {
                list.remove(0);
            }
            //Logger.getGlobal().info("Data size is: "+ getUndoDataSize(player,map)
            //                                                             +" byte from "+list.size()+" entries (max "+maxSize+").");
        } catch (CopyPasteException ex) {
            return false;
        }
        return true;
    }
    
    public static int getUndoDataSize(Player player, Map<UUID,List<UndoData>> map) {
        List<UndoData> list = map.get(player.getUniqueId());
        if(list==null) {
            return 0;
        } else {
            int sum = 0;
            for(UndoData data: list) {
                sum=sum+data.getNbtData().length;
            }
            return sum;
        }
    }
    
    public static void loadClipboard(Player player) {
        File file = new File(clipboardFolder,player.getUniqueId().toString()+cbExtension);
        if(file.exists()) {
            new BukkitRunnable() {
                @Override
                public void run() {
                    try {
                        Clipboard cb = new Clipboard(file);
                        clipboards.put(player.getUniqueId(), cb);
                    } catch (IOException ex) {
                        Logger.getLogger(CopyPasteManager.class.getName()).log(Level.SEVERE, null, ex);
                    }
                }
            }.runTaskAsynchronously(ArchitectPlugin.getPluginInstance());
        }
    }
    
}
