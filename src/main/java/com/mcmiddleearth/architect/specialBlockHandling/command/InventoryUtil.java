package com.mcmiddleearth.architect.specialBlockHandling.command;

import com.mcmiddleearth.architect.ArchitectPlugin;
import com.mcmiddleearth.util.StreamGobbler;
import org.bukkit.Bukkit;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.*;
import java.util.function.BiConsumer;
import java.util.logging.Logger;

public class InventoryUtil {

    public static void downloadInventory(String rpName, BiConsumer<Boolean, Integer> callback) {
        executeScript("inventoryDownload", rpName, "", callback);
    }

    public static void uploadInventory(String rpName, String description, BiConsumer<Boolean, Integer> callback) {
        executeScript("inventoryUpload", rpName, description, callback);
    }

    private static void executeScript(String action, String rpName, String description, BiConsumer<Boolean, Integer> callback) {
        Bukkit.getScheduler().runTaskAsynchronously(ArchitectPlugin.getPluginInstance(), () -> {
            try {
                Process process;
                boolean isWindows = System.getProperty("os.name")
                        .toLowerCase().startsWith("windows");
                String script = ArchitectPlugin.getPluginInstance().getConfig().getString(action+".script");
                String scriptPath = ArchitectPlugin.getPluginInstance().getConfig().getString(action+".path");
                if (isWindows || script == null || scriptPath == null) {
                    callback.accept(false, -1);
                    return;
                } else {
                    process = Runtime.getRuntime()
                            .exec(new String[]{"sh", script,
                                            rpName.substring(0,1).toUpperCase()+rpName.substring(1).toLowerCase(),
                                            description}, null,
                                    new File(scriptPath));
                }
                StreamGobbler streamGobbler =
                        new StreamGobbler(process.getInputStream(), process.getErrorStream(),
                                line -> Logger.getGlobal().info(line));
                ExecutorService executorService = Executors.newSingleThreadExecutor();
                Future<?> future = executorService.submit(streamGobbler);
                boolean exit = process.waitFor(5, TimeUnit.MINUTES);
                future.get(5, TimeUnit.MINUTES);
                process.destroy();
                int exitCode = process.waitFor();
                callback.accept(exit, exitCode);
            } catch (InterruptedException | ExecutionException | TimeoutException | IOException e) {
                callback.accept(false, -1);
                e.printStackTrace();
            }
        });
    }
}