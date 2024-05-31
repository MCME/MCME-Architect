package com.mcmiddleearth.architect.serverResoucePack;

import com.mcmiddleearth.architect.ArchitectPlugin;
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

    public static void setServerResourcePack(CommandSender cs, String rpName, String version,
                                             Consumer<Boolean> callback) {
        String finalRpName = RpManager.matchRpName(rpName);
        ConfigurationSection rpConfig = RpManager.getRpConfig();
        rpConfig = rpConfig.getConfigurationSection(finalRpName);
        if(rpConfig ==null) {
            callback.accept(false);
        } else {
            String download = "https://github.com/" + getGitHubOwner(finalRpName) + "/" + getGitHubRepo(finalRpName) + "/releases/download/";
            if (rpConfig.contains("sodium")) {
                rpConfig.set("vanilla.16px.light.url", download + version + "/"+finalRpName+"-Vanilla.zip");
                rpConfig.set("vanilla.16px.footprints.url", download + version + "/"+finalRpName+"-Vanilla-Footprints.zip");
                rpConfig.set("sodium.16px.light.url", download + version + "/"+finalRpName+"-Sodium.zip");
                rpConfig.set("sodium.16px.footprints.url", download + version + "/"+finalRpName+"-Sodium-Footprints.zip");
            } else {
                rpConfig.set("vanilla.16px.light.url", download + version + "/"+finalRpName+".zip");
                rpConfig.set("vanilla.16px.footprints.url", download + version + "/"+finalRpName+"-footprints.zip");
            }
            ArchitectPlugin.getPluginInstance().saveConfig();
            //ArchitectPlugin.getPluginInstance().loadData(); probably not needed
            Bukkit.getScheduler().runTaskAsynchronously(ArchitectPlugin.getPluginInstance(), () -> {
                callback.accept(RpManager.refreshSHA(cs, finalRpName));
            });
        }
        /*if(rpName.equalsIgnoreCase("human")) {
            ConfigurationSection rpConfig = RpManager.getRpConfig();
            ConfigurationSection humanConfig = rpConfig.getConfigurationSection("Human");
            if(humanConfig !=null) {
                String download = "https://github.com/"+getGitHubOwner("Human")+"/"+getGitHubRepo("Human")+"/releases/download/";
                humanConfig.set("vanilla.16px.light.url", download + version + "/Human-Vanilla.zip");
                humanConfig.set("vanilla.16px.footprints.url", download + version + "/Human-Vanilla-Footprints.zip");
                humanConfig.set("sodium.16px.light.url", download + version + "/Human-Sodium.zip");
                humanConfig.set("sodium.16px.footprints.url", download + version + "/Human-Sodium-Footprints.zip");
                ArchitectPlugin.getPluginInstance().saveConfig();
                //ArchitectPlugin.getPluginInstance().loadData(); probably not needed
                Bukkit.getScheduler().runTaskAsynchronously(ArchitectPlugin.getPluginInstance(), () -> {
                    callback.accept(RpManager.refreshSHA(cs, "Human"));
                });
            }
        } else if(rpName.equalsIgnoreCase("Dwarf")) {
            ConfigurationSection rpConfig = RpManager.getRpConfig();
            ConfigurationSection humanConfig = rpConfig.getConfigurationSection("Dwarf");
            if(humanConfig !=null) {
                String download = "https://github.com/"+getGitHubOwner("Dwarf")+"/"+getGitHubRepo("Dwarf")+"/releases/download/";
                humanConfig.set("vanilla.16px.light.url", download + version + "/Dwarven.zip");
                humanConfig.set("vanilla.16px.footprints.url", download + version + "/Dwarven-footprints.zip");
                ArchitectPlugin.getPluginInstance().saveConfig();
                //ArchitectPlugin.getPluginInstance().loadData(); probably not needed
                Bukkit.getScheduler().runTaskAsynchronously(ArchitectPlugin.getPluginInstance(), () -> {
                    callback.accept(RpManager.refreshSHA(cs, "Dwarf"));
                });
            }
        } else if(rpName.equalsIgnoreCase("Rohan")) {
            ConfigurationSection rpConfig = RpManager.getRpConfig();
            ConfigurationSection humanConfig = rpConfig.getConfigurationSection("Rohan");
            if(humanConfig !=null) {
                String download = "https://github.com/"+getGitHubOwner("Rohan")+"/"+getGitHubRepo("Rohan")+"/releases/download/";
                humanConfig.set("vanilla.16px.light.url", download + version + "/Rohan.zip");
                humanConfig.set("vanilla.16px.footprints.url", download + version + "/Rohan-footprints.zip");
                ArchitectPlugin.getPluginInstance().saveConfig();
                //ArchitectPlugin.getPluginInstance().loadData(); probably not needed
                Bukkit.getScheduler().runTaskAsynchronously(ArchitectPlugin.getPluginInstance(), () -> {
                    callback.accept(RpManager.refreshSHA(cs, "Rohan"));
                });
            }
        } else {
            callback.accept(false);
        }*/
    }

    private static String getGitHubOwner(String rpName) {
        return ArchitectPlugin.getPluginInstance().getConfig().getString("gitHubRpReleases."+rpName+".owner");
    }

    private static String getGitHubRepo(String rpName) {
        return ArchitectPlugin.getPluginInstance().getConfig().getString("gitHubRpReleases."+rpName+".repo");
    }
}