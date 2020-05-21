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

import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author Eriol_Eandur
 */
public class CustomHeadData {
    
    private final UUID headId;
    
    private final UUID submittingPlayer;
    
    private final String texture; //base64 encoded skin texture url
    
    public CustomHeadData(UUID headId, UUID ownerId, String texture) {
        this.submittingPlayer = ownerId;
        this.headId = headId;
        this.texture = texture;
    }
    
    public CustomHeadData(UUID ownerId, String texture) {
        this.submittingPlayer = ownerId;
        this.headId = new UUID(ownerId.getMostSignificantBits()+System.currentTimeMillis(),
                               ownerId.getLeastSignificantBits()+System.currentTimeMillis());
        this.texture = texture;
    }
        
    public static CustomHeadData fromFile(File file) {
        YamlConfiguration config = new YamlConfiguration();
        try {
            config.load(file);
        } catch (IOException | InvalidConfigurationException ex) {
            Logger.getLogger(CustomHeadData.class.getName()).log(Level.SEVERE, null, ex);
        }
        return new CustomHeadData(UUID.fromString(config.getString("headId")), 
                                  UUID.fromString(config.getString("owner")), 
                                  config.getString("texture"));
        
    }
    
    public boolean saveToFile(File file) {
        YamlConfiguration config = new YamlConfiguration();
        config.set("owner", submittingPlayer.toString());
        config.set("headId", headId.toString());
        config.set("texture", texture);
        try {
            config.save(file);
            return true;
        } catch (IOException ex) {
            Logger.getLogger(CustomHeadData.class.getName()).log(Level.SEVERE, null, ex);
            return false;
        }
    }

    public UUID getHeadId() {
        return headId;
    }

    public UUID getSubmittingPlayer() {
        return submittingPlayer;
    }

    public String getTexture() {
        return texture;
    }
}
