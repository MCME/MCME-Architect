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

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mcmiddleearth.architect.ArchitectPlugin;
import com.mcmiddleearth.architect.PluginData;
import com.mcmiddleearth.util.HttpTextInputHandler;
import java.io.BufferedReader;
import java.util.Scanner;
import java.util.UUID;
import java.util.logging.Logger;
import org.bukkit.entity.Player;

/**
 *
 * @author Eriol_Eandur
 */
public class HeadDataBuilderText extends HttpTextInputHandler {

    private final Player sender;
    private final boolean uploadToReview;

    private static final String uploadPath;
    
    private static final String CONFIG_KEY = "CustomHeadUploadPath";
    
    static {
        if(ArchitectPlugin.getPluginInstance().getConfig().isString(CONFIG_KEY)) {
            uploadPath = ArchitectPlugin.getPluginInstance().getConfig().getString(CONFIG_KEY);
        } else {
            uploadPath = "https://raw.githubusercontent.com/EriolEandur/MCME-Architect/master/src/main/resources/";
            ArchitectPlugin.getPluginInstance().getConfig().set(CONFIG_KEY, uploadPath);
            ArchitectPlugin.getPluginInstance().saveConfig();
        }
    }
    
    public HeadDataBuilderText(Player sender, String filename, boolean uploadToReview) {
        super(uploadPath+filename,
              2000);
        this.sender = sender;
        this.uploadToReview =uploadToReview;
    }

    @Override
    protected void handleTextInput(BufferedReader reader) {
        int counter = 0;
        int errors = 0;
        try (Scanner scanner = new Scanner(reader)) {
            while(scanner.hasNext()) {
                String line = scanner.nextLine();
                line = line.substring(line.indexOf('{'));
                JsonObject jInput = (JsonObject) new JsonParser().parse(line);
                JsonObject jDisplay = (JsonObject) jInput.get("display");
                String name = jDisplay.get("Name").getAsString();

                JsonObject jOwner = (JsonObject) jInput.get("SkullOwner");
                String uuidString = jOwner.get("Id").getAsString();

                JsonObject jProperties = (JsonObject) jOwner.get("Properties");
                JsonArray jTextures = (JsonArray) jProperties.get("textures");
                JsonObject jUrl = (JsonObject) jTextures.get(0);
                String textureUrl = jUrl.get("Value").getAsString();
                CustomHeadData headData = new CustomHeadData(UUID.fromString(uuidString), textureUrl);
                if(uploadToReview) {
                    if(!CustomHeadManagerData.addReviewHead(name, headData)) {
                        errors++;
                    }
                } else {
                    if(!CustomHeadManagerData.addHead(name, headData)) {
                        errors++;
                    }
                }
                counter++;
            }
        }
        PluginData.getMessageUtil().sendInfoMessage(sender,counter+" heads uploaded. "+errors+" erroneous head data skipped.");
    }

    @Override
    protected void sendIOException() {
        PluginData.getMessageUtil().sendErrorMessage(sender, "IOException.");
    }

    @Override
    protected void sendBadResponseError() {
        PluginData.getMessageUtil().sendErrorMessage(sender, "File not found.");
    }

}
