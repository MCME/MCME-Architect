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
package com.mcmiddleearth.util;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mcmiddleearth.architect.ArchitectPlugin;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;

/**
 *
 * @author Eriol_Eandur
 */
public class ConversionUtil_1_13 {
    
    private static final String itemPath = "/items.json";
    private static final Gson gson;
    private static final Map<String,String> itemMapping = new HashMap<>();
    
    static {
        GsonBuilder gsonBuilder = new GsonBuilder();
        gsonBuilder.setPrettyPrinting();
        gson = gsonBuilder.create();
        JsonObject items = readJsonResource(itemPath);
        if (items != null) {
            for (Map.Entry<String, JsonElement> entry : items.entrySet()) {
                itemMapping.put(entry.getKey(), entry.getValue().getAsString());
            }
        }
    }

    public static String convertItemName(String name) {
        if(itemMapping.containsKey(name.toLowerCase())) {
            return itemMapping.get(name.toLowerCase()).toUpperCase();
        } 
        return name;
    }
    
    private static JsonObject readJsonResource(String path) {
        try (InputStream stream = ArchitectPlugin.class.getResourceAsStream(path)) {
            if (stream == null) return null;
            try (InputStreamReader streamReader = new InputStreamReader(stream)) {
                return gson.fromJson(streamReader, JsonObject.class);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

}
