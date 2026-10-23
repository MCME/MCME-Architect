package com.mcmiddleearth.architect.specialBlockHandling.command;

import com.mcmiddleearth.architect.ArchitectPlugin;
import com.mcmiddleearth.architect.Log;
import com.mcmiddleearth.util.StreamGobbler;
import org.bukkit.Bukkit;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.concurrent.*;
import java.util.function.BiConsumer;
import java.util.regex.Pattern;

public class InventoryUtil {

    // Letters, digits, spaces and simple punctuation. A hyphen may sit inside a word, as in oak-log.
    private static final Pattern DESCRIPTION = Pattern.compile("[\\p{L}\\p{N} .,:;!'()/_-]+");

    /**
     * Whether a description may go to the upload script as its commit message: letters, digits, spaces and
     * . , : ; ! ' ( ) / _ - only, and no word that starts with a dash, so git never reads a word as an option.
     */
    static boolean isAllowedDescription(String description) {
        return description != null && !description.isBlank() && DESCRIPTION.matcher(description).matches()
                && Arrays.stream(description.split(" ")).noneMatch(word -> word.startsWith("-"));
    }

    public static void downloadInventory(String rpName, BiConsumer<Boolean, Integer> callback) {
        executeScript("inventoryDownload", rpName, "", callback);
    }

    public static void uploadInventory(String rpName, String description, BiConsumer<Boolean, Integer> callback) {
        executeScript("inventoryUpload", rpName, description, callback);
    }

    private static void executeScript(String action, String rpName, String description, BiConsumer<Boolean, Integer> callback) {
        // Read on the main thread: an update of config.yml reads it again there, in place, and a read from the
        // script's own thread meanwhile could find it empty.
        String script = ArchitectPlugin.getPluginInstance().getConfig().getString(action+".script");
        String scriptPath = ArchitectPlugin.getPluginInstance().getConfig().getString(action+".path");
        Bukkit.getScheduler().runTaskAsynchronously(ArchitectPlugin.getPluginInstance(), () -> {
            boolean exit = false;
            int exitCode = -1;
            ExecutorService executorService = null;
            try {
                boolean isWindows = System.getProperty("os.name")
                        .toLowerCase().startsWith("windows");
                if (isWindows || script == null || scriptPath == null) {
                    runOnMain(callback, false, -1);
                    return;
                }
                Process process = Runtime.getRuntime()
                        .exec(new String[]{"sh", script,
                                        rpName.substring(0,1).toUpperCase()+rpName.substring(1).toLowerCase(),
                                        description}, null,
                                new File(scriptPath));
                StreamGobbler streamGobbler =
                        new StreamGobbler(process.getInputStream(), process.getErrorStream(),
                                line -> Log.info("[" + action + " " + rpName + "] " + line));
                executorService = Executors.newSingleThreadExecutor();
                Future<?> future = executorService.submit(streamGobbler);
                exit = process.waitFor(5, TimeUnit.MINUTES);
                future.get(5, TimeUnit.MINUTES);
                process.destroy();
                exitCode = process.waitFor();
                runOnMain(callback, exit, exitCode);
            } catch (InterruptedException | ExecutionException | TimeoutException | IOException e) {
                Log.error("Failed to run " + action + " script for RP " + rpName, e);
                runOnMain(callback, false, -1);
            } finally {
                if (executorService != null) {
                    executorService.shutdownNow();
                }
            }
        });
    }

    // Deliver the result on the main thread: the /inv download callback reloads inventories (main-thread state).
    private static void runOnMain(BiConsumer<Boolean, Integer> callback, boolean exit, int exitCode) {
        Bukkit.getScheduler().runTask(ArchitectPlugin.getPluginInstance(), () -> callback.accept(exit, exitCode));
    }
}