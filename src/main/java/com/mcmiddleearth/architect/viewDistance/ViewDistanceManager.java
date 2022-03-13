package com.mcmiddleearth.architect.viewDistance;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLib;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.events.PacketContainer;
import com.mcmiddleearth.architect.ArchitectPlugin;
import com.mcmiddleearth.pluginutil.NumericUtil;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.HashMap;
import java.util.Map;
import java.util.Scanner;
import java.util.UUID;

public class ViewDistanceManager {

    private static final Map<UUID, Integer> viewDistances = new HashMap<>();

    private static final File viewDistancesFile = new File(ArchitectPlugin.getPluginInstance().getDataFolder(), "viewDistances.dat");

    public static boolean isViewdistanceSet(Player player) {
        return viewDistances.containsKey(player.getUniqueId());
    }

    public static Integer getViewDistance(Player player) {
        return viewDistances.get(player.getUniqueId());

    }

    public static void setViewDistance(Player player, int viewDistance) {
        viewDistances.put(player.getUniqueId(), viewDistance);
        sendViewDistancePacket(player, viewDistance);
        saveViewDistances();
    }

    public static void unsetViewDistance(Player player) {
        viewDistances.remove(player.getUniqueId());
        sendViewDistancePacket(player, Bukkit.getServer().getViewDistance());
        saveViewDistances();
    }

    private static void sendViewDistancePacket(Player player, int viewDistance) {
        PacketContainer packet = new PacketContainer(PacketType.Play.Server.VIEW_DISTANCE);
        packet.getIntegers().write(0, viewDistance);
        try {
            ProtocolLibrary.getProtocolManager().sendServerPacket(player, packet);
        } catch (InvocationTargetException e) {
            e.printStackTrace();
        }
    }

    public static void saveViewDistances() {
        try(FileWriter fileWriter = new FileWriter(viewDistancesFile)) {
            viewDistances.forEach((uuid, viewdistance) -> {
                try {
                    fileWriter.write(uuid.toString()+" "+viewdistance+"\n");
                } catch (IOException e) {
                    e.printStackTrace();
                }
            });
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void loadViewDistances() {
        try (Scanner scanner = new Scanner(viewDistancesFile)) {
            while(scanner.hasNext()) {
                String[] line = scanner.nextLine().split(" ");
                viewDistances.put(UUID.fromString(line[0]), NumericUtil.getInt(line[1]));
            }
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
    }

}
