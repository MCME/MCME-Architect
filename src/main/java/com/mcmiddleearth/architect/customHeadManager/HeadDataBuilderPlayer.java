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
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import com.mcmiddleearth.architect.ArchitectPlugin;
import com.mcmiddleearth.architect.Log;
import com.mcmiddleearth.architect.PluginData;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.UUID;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

/**
 *
 * @author Eriol_Eandur
 */
public class HeadDataBuilderPlayer {

    private final String mojangSkinUrl = "https://sessionserver.mojang.com/session/minecraft/profile/%s";

    private final String mojangUuidUrl = "https://api.mojang.com/users/profiles/minecraft/%s";

    public HeadDataBuilderPlayer(final Player submitter, final UUID ownerId, final String name) {
        fetchCustomHeadData(submitter, ownerId, name);
    }

    public HeadDataBuilderPlayer(final Player submitter, final String ownerName, final String name) {
        fetchCustomHeadData(submitter, ownerName, name);
    }

    /** Resolve a player name to a UUID (async), then continue on the main thread. */
    private void fetchCustomHeadData(final Player submitter, final String ownerName, final String name) {
        new BukkitRunnable() {
            @Override
            public void run() {
                try {
                    JsonObject jsonObject = readJson(String.format(mojangUuidUrl, ownerName));
                    if (jsonObject == null) {
                        sendOnMain(submitter, "Player name not found.");
                        return;
                    }
                    final UUID ownerId = UUID.fromString(jsonObject.get("id").getAsString()
                            .replaceFirst("(\\w{8})(\\w{4})(\\w{4})(\\w{4})(\\w{12})", "$1-$2-$3-$4-$5"));
                    runOnMain(() -> fetchCustomHeadData(submitter, ownerId, name));
                } catch (IOException | JsonSyntaxException | IllegalStateException | NullPointerException ex) {
                    Log.error("Failed Mojang UUID lookup for player name " + ownerName, ex);
                    sendOnMain(submitter, "Error. Your head has not been submitted.");
                }
            }
        }.runTaskAsynchronously(ArchitectPlugin.getPluginInstance());
    }

    /** Fetch a player's skin texture (async), then submit the head on the main thread. */
    private void fetchCustomHeadData(final Player submitter, final UUID ownerId, final String name) {
        new BukkitRunnable() {
            @Override
            public void run() {
                try {
                    JsonObject jsonObject = readJson(String.format(mojangSkinUrl, ownerId.toString().replace("-", "")));
                    if (jsonObject == null) {
                        sendOnMain(submitter, "Error. Invalid UUID or too many requests. Wait one minute at least before you try again.");
                        return;
                    }
                    String textures = "";
                    for (JsonElement jProperty : jsonObject.getAsJsonArray("properties")) {
                        if (jProperty.getAsJsonObject().has("name")
                                && jProperty.getAsJsonObject().get("name").getAsString().equals("textures")) {
                            textures = jProperty.getAsJsonObject().get("value").getAsString();
                            break;
                        }
                    }
                    JsonObject skin = JsonParser.parseString(new String(BaseEncoding.base64().decode(textures)))
                            .getAsJsonObject().getAsJsonObject("textures").getAsJsonObject("SKIN");
                    final String url = BaseEncoding.base64().encode(
                            String.format("{textures:{SKIN:{url:\"%s\"}}}", skin.get("url").getAsString()).getBytes());
                    runOnMain(() -> {
                        CustomHeadData headData = new CustomHeadData(ownerId, url);
                        if (CustomHeadManagerData.addReviewHead(name, headData)) {
                            PluginData.getMessageUtil().sendInfoMessage(submitter, "Head has been submitted.");
                        } else {
                            PluginData.getMessageUtil().sendErrorMessage(submitter, "Error. Your head has not been submitted.");
                        }
                    });
                } catch (IOException | JsonSyntaxException | IllegalStateException | NullPointerException ex) {
                    Log.error("Failed Mojang skin lookup for player " + ownerId, ex);
                    sendOnMain(submitter, "Error. Your head has not been submitted.");
                }
            }
        }.runTaskAsynchronously(ArchitectPlugin.getPluginInstance());
    }

    /** Blocking HTTP GET on the CALLING (async) thread. @return parsed body, or null on a non-200 response. */
    private static JsonObject readJson(String urlString) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(urlString).openConnection();
        connection.setConnectTimeout(5000);
        connection.setReadTimeout(5000);
        try {
            if (connection.getResponseCode() != HttpURLConnection.HTTP_OK) {
                return null;
            }
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()))) {
                return (JsonObject) JsonParser.parseReader(reader);
            }
        } finally {
            connection.disconnect();
        }
    }

    private static void runOnMain(Runnable task) {
        new BukkitRunnable() {
            @Override
            public void run() {
                task.run();
            }
        }.runTask(ArchitectPlugin.getPluginInstance());
    }

    private static void sendOnMain(final Player submitter, final String message) {
        runOnMain(() -> PluginData.getMessageUtil().sendErrorMessage(submitter, message));
    }
}
