package com.mcmiddleearth.architect.specialBlockHandling.command;

import com.mcmiddleearth.architect.ArchitectPlugin;
import com.mcmiddleearth.util.StreamGobbler;
import org.bukkit.Bukkit;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.*;
import java.util.function.BiConsumer;
import java.util.logging.Logger;

public class InventoryDownloadUtil {

    public static void downloadInventory(String rpName, BiConsumer<Boolean, Integer> callback) {
        Bukkit.getScheduler().runTaskAsynchronously(ArchitectPlugin.getPluginInstance(), () -> {
            try {
                Process process;
                boolean isWindows = System.getProperty("os.name")
                        .toLowerCase().startsWith("windows");
                String downloadScript = ArchitectPlugin.getPluginInstance().getConfig().getString("inventoryDownload.script");
                String scriptPath = ArchitectPlugin.getPluginInstance().getConfig().getString("inventoryDownload.path");
                if (isWindows || downloadScript == null || scriptPath == null) {
                    callback.accept(false, -1);
                    return;
                } else {
                    process = Runtime.getRuntime()
                            .exec(new String[]{"sh", downloadScript,
                                            rpName.substring(0,1).toUpperCase()+rpName.substring(1).toLowerCase()}, null,
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