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

import com.google.common.io.BaseEncoding;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import com.mcmiddleearth.architect.ArchitectPlugin;
import com.mcmiddleearth.architect.PluginData;
import com.mojang.util.UUIDTypeAdapter;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

/**
 *
 * @author Eriol_Eandur
 */
public class HeadDataBuilderPlayer {

    private boolean received;

    private HttpURLConnection connection;

    private final String mojangSkinUrl = "https://sessionserver.mojang.com/session/minecraft/profile/%s";

    private final String mojangUuidUrl = "https://api.mojang.com/users/profiles/minecraft/%s";

    public HeadDataBuilderPlayer(final Player submitter, final UUID ownerId, final String name) {
        fetchCustomHeadData(submitter, ownerId, name);
    }

    public HeadDataBuilderPlayer(final Player submitter, final String ownerName, final String name) {
        fetchCustomHeadData(submitter, ownerName, name);
    }

    private void fetchCustomHeadData(final Player submitter, final String ownerName, final String name) {
        received = false; 
        new BukkitRunnable() {
            @Override
            public void run() {
                if(received) {
                    try {
                        if (connection.getResponseCode() == HttpURLConnection.HTTP_OK) {
                            String json = new BufferedReader(new InputStreamReader(connection.getInputStream())).readLine();
                            JsonObject jsonObject = (JsonObject) new JsonParser().parse(json);
                            String uuidString = jsonObject.get("id").getAsString();
                            UUID ownerId = UUIDTypeAdapter.fromString(uuidString);
                            fetchCustomHeadData(submitter, ownerId, name);
                        } else {
                            PluginData.getMessageUtil().sendErrorMessage(submitter, "Player name not found.");
                        }
                        cancel();
                        return;
                    } catch (IOException | JsonSyntaxException ex) {
                        Logger.getLogger(CustomHeadManagerData.class.getName()).log(Level.SEVERE, null, ex);
                    } finally {
                        cancel();
                    }
                    PluginData.getMessageUtil().sendErrorMessage(submitter, "Error. Your head has not been submitted.");
                }
            }
        }.runTaskTimer(ArchitectPlugin.getPluginInstance(), 10, 10);
        new BukkitRunnable() {
            @Override
            public void run() {
                try {
                    URL url = new URL(String.format(mojangUuidUrl, ownerName));
                    connection = (HttpURLConnection) url.openConnection();
                    connection.setReadTimeout(5000);
                    connection.connect();
                } catch (IOException ex) {
                    Logger.getLogger(CustomHeadManagerData.class.getName()).log(Level.SEVERE, null, ex);
                } finally {
                    received = true;
                }
            }
        }.runTaskAsynchronously(ArchitectPlugin.getPluginInstance());
    }

    private void fetchCustomHeadData(final Player submitter, final UUID ownerId, final String name) {
        received = false; 
        new BukkitRunnable() {
            @Override
            public void run() {
                if(received) {
                    try {
                        if (connection.getResponseCode() == HttpURLConnection.HTTP_OK) {
                            String json = new BufferedReader(new InputStreamReader(connection.getInputStream())).readLine();
                            JsonObject jsonObject = (JsonObject) new JsonParser().parse(json);
                            JsonArray jProperties = jsonObject.getAsJsonArray("properties");
                            String textures="";
                            for(JsonElement jProperty : jProperties) {
                                if(jProperty.getAsJsonObject().has("name")
                                        && jProperty.getAsJsonObject().get("name").getAsString().equals("textures")) {
                                    textures = jProperty.getAsJsonObject().get("value").getAsString();
                                    break;
                                }
                            }
                            jsonObject = new JsonParser().parse(new String(BaseEncoding.base64().decode(textures)))
                                                         .getAsJsonObject();
                            jsonObject = jsonObject.getAsJsonObject("textures");
                            jsonObject = jsonObject.getAsJsonObject("SKIN");
                            String url = jsonObject.get("url").getAsString();
                            url = BaseEncoding.base64().encode(String.format("{textures:{SKIN:{url:\"%s\"}}}", url).getBytes());
                            CustomHeadData headData = new CustomHeadData(ownerId, url);
                            //ToDo check for heads with same texture
                            if(CustomHeadManagerData.addReviewHead(name, headData)) {
                                PluginData.getMessageUtil().sendInfoMessage(submitter,"Head has been submitted.");
                                cancel();
                                return;
                            }
                        } else {
                            PluginData.getMessageUtil().sendErrorMessage(submitter, "Error. Invalid UUID or too many requests. Wait one minute at last before you try again.");
                            cancel();
                            return;
                            }
                    } catch (IOException | JsonSyntaxException ex) {
                        Logger.getLogger(CustomHeadManagerData.class.getName()).log(Level.SEVERE, null, ex);
                    } finally {
                        cancel();
                    }
                    PluginData.getMessageUtil().sendErrorMessage(submitter, "Error. Your head has not been submitted.");
                }
            }
        }.runTaskTimer(ArchitectPlugin.getPluginInstance(), 10, 10);
        new BukkitRunnable() {
            @Override
            public void run() {
                try {
                    URL url = new URL(String.format(mojangSkinUrl, UUIDTypeAdapter.fromUUID(ownerId)));
                    connection = (HttpURLConnection) url.openConnection();
                    connection.setReadTimeout(5000);
                    connection.connect();
                } catch (IOException ex) {
                    Logger.getLogger(CustomHeadManagerData.class.getName()).log(Level.SEVERE, null, ex);
                } finally {
                    received = true;
                }
            }
        }.runTaskAsynchronously(ArchitectPlugin.getPluginInstance());
    }
}
