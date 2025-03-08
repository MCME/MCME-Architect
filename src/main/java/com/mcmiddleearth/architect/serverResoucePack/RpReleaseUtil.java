package com.mcmiddleearth.architect.serverResoucePack;

import com.mcmiddleearth.architect.ArchitectPlugin;
import com.mcmiddleearth.connect.log.Log;
import com.mcmiddleearth.util.StreamGobbler;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.*;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.logging.Logger;

public class RpReleaseUtil {

    public static void releaseResourcePack(String rpName, String version, String title, BiConsumer<Boolean,Integer> callback ) {
        String finalRpName = RpManager.matchRpName(rpName);//.substring(0,1).toUpperCase()+rpName.substring(1).toLowerCase();
        Bukkit.getScheduler().runTaskAsynchronously(ArchitectPlugin.getPluginInstance(), () -> {
            try {
                Process process;
                boolean isWindows = System.getProperty("os.name")
                        .toLowerCase().startsWith("windows");
                String gitHubOwner = getGitHubOwner(finalRpName);
                String gitHubRepo = getGitHubRepo(finalRpName);
                String releaseScript = ArchitectPlugin.getPluginInstance().getConfig().getString("gitHubRpReleases."+finalRpName+".script");
                String scriptPath = ArchitectPlugin.getPluginInstance().getConfig().getString("gitHubRpReleases."+finalRpName+".path");
                if (isWindows || gitHubOwner==null || gitHubRepo==null || releaseScript==null || scriptPath==null) {
                    callback.accept(true, -1);
                    return;
                } else {
                    process = Runtime.getRuntime()
                            .exec(new String[]{"sh", releaseScript, finalRpName, gitHubOwner, gitHubRepo, version, title}, null,
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
                e.printStackTrace();
                callback.accept(false, -1);
            }
        });
    }

    public static void setServerResourcePack(CommandSender cs, String rpName, String version, String requiredMcVersion,
                                             Consumer<Boolean> callback) {
        String finalRpName = RpManager.matchRpName(rpName);
        requiredMcVersion = requiredMcVersion.replace('.','_');
        ConfigurationSection rpConfig = RpManager.getRpConfig();
        rpConfig = rpConfig.getConfigurationSection(finalRpName);
        if(rpConfig ==null) {
            callback.accept(false);
        } else {
            String download = "https://github.com/" + getGitHubOwner(finalRpName) + "/" + getGitHubRepo(finalRpName) + "/releases/download/";
            if (rpConfig.contains("sodium")) {
                updateSection(rpConfig, "vanilla.16px.light",requiredMcVersion, download + version + "/"+finalRpName+"-Vanilla.zip");
                updateSection(rpConfig, "vanilla.16px.footprints",requiredMcVersion, download + version + "/"+finalRpName+"-Vanilla-Footprints.zip");
                updateSection(rpConfig, "sodium.16px.light",requiredMcVersion, download + version + "/"+finalRpName+"-Sodium.zip");
                updateSection(rpConfig, "sodium.16px.footprints",requiredMcVersion, download + version + "/"+finalRpName+"-Sodium-Footprints.zip");
                updateSection(rpConfig, "lite.16px.light",requiredMcVersion, download + version + "/"+finalRpName+"-Lite.zip");
                updateSection(rpConfig, "lite.16px.footprints",requiredMcVersion, download + version + "/"+finalRpName+"-Lite-Footprints.zip");
            } else {
                updateSection(rpConfig, "vanilla.16px.light",requiredMcVersion, download + version + "/"+finalRpName+".zip");
                updateSection(rpConfig, "vanilla.16px.footprints",requiredMcVersion, download + version + "/"+finalRpName+"-Footprints.zip");
            }
            ArchitectPlugin.getPluginInstance().saveConfig();
            Bukkit.getScheduler().runTaskAsynchronously(ArchitectPlugin.getPluginInstance(), () -> {
                callback.accept(RpManager.refreshSHA(cs, finalRpName, false));
            });
        }
    }

    private static void updateSection(ConfigurationSection rpConfig, String path, String requiredMcVersion, String url) {
Logger.getGlobal().info("Key search: "+path);
rpConfig.getKeys(true).forEach(key -> Logger.getGlobal().info(key));
        ConfigurationSection section = rpConfig.getConfigurationSection(path);
        if(section.contains("url")) {
            section.set("url", null);
            section.set("sha", null);
        }
        rpConfig.set(path+"."+requiredMcVersion+".url", url);
    }

    private static String getGitHubOwner(String rpName) {
        return ArchitectPlugin.getPluginInstance().getConfig().getString("gitHubRpReleases."+rpName+".owner");
    }

    private static String getGitHubRepo(String rpName) {
        return ArchitectPlugin.getPluginInstance().getConfig().getString("gitHubRpReleases."+rpName+".repo");
    }
}