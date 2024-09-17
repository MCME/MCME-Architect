package com.mcmiddleearth.architect.serverResoucePack;

import org.bukkit.entity.Player;
import org.bukkit.plugin.messaging.PluginMessageListener;
import org.jetbrains.annotations.NotNull;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.logging.Logger;

public class TestPluginMessageListener implements PluginMessageListener {

    @Override
    public void onPluginMessageReceived(@NotNull String channel, @NotNull Player player, byte[] bytes) {
        //Logger.getGlobal().info("Sodium client detected: "+player.getName());

Logger.getGlobal().info("Received message from player " + player.getName() + " on channel "
        + channel + " with data " + Arrays.toString(bytes));
        try (var dataStream = new DataInputStream(new ByteArrayInputStream(bytes))) {
            int stringLength = readVarInt(dataStream);
            String jsonString = new String(dataStream.readNBytes(stringLength));
            Logger.getGlobal().info("data = " + jsonString);
        } catch (IOException e) {
            Logger.getGlobal().warning("Received invalid MCME Modpack marker data from player " + player.getName() + " (" + player.getUniqueId() + "): " + Arrays.toString(bytes));
        }

    }

    private int readVarInt(DataInputStream dataStream) throws IOException {
        // This will break for strings over 127 characters long.
        int accumulator = 0;
        for (int index = 0; true; index++) {
            if (index >= 4) {
                throw new IllegalArgumentException("VarInt too long. Reached index " + index);
            }

            int nextByte = dataStream.readUnsignedByte();
            accumulator <<= 8;
            accumulator |= nextByte & 0x7f;
            if ((nextByte & 0x80) == 0) break;
        }

        return accumulator;
    }

}
