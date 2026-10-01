package com.mcmiddleearth.architect.serverResoucePack;

import com.mcmiddleearth.architect.ArchitectPlugin;
import com.mcmiddleearth.architect.Log;
import com.mcmiddleearth.util.StreamGobbler;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;

import java.io.File;
import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.*;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.stream.Stream;

public class RpReleaseUtil {

    // How long a release script may run before it is stopped, unless gitHubRpReleases.<rp>.timeoutMinutes sets
    // another time. A Human release took over 5 minutes in September 2026.
    static final Duration DEFAULT_TIMEOUT = Duration.ofMinutes(30);

    // How long a script that is being stopped, and what it started, have to end, and how long the rest of a script's
    // output may take once it has ended.
    static final Duration GRACE = Duration.ofSeconds(10);

    /**
     * How a release script ended: whether by itself, in the time it had, its exit code (-1 if it has none), and
     * whether its output was read to the end.
     */
    record ScriptResult(boolean terminated, int exitCode, boolean outputComplete) {}

    public static void releaseResourcePack(String rpName, String version, String title, BiConsumer<Boolean,Integer> callback ) {
        String finalRpName = RpManager.matchRpName(rpName);//.substring(0,1).toUpperCase()+rpName.substring(1).toLowerCase();
        Bukkit.getScheduler().runTaskAsynchronously(ArchitectPlugin.getPluginInstance(), () -> {
            try {
                boolean isWindows = System.getProperty("os.name")
                        .toLowerCase().startsWith("windows");
                String gitHubOwner = getGitHubOwner(finalRpName);
                String gitHubRepo = getGitHubRepo(finalRpName);
                String releaseScript = ArchitectPlugin.getPluginInstance().getConfig().getString("gitHubRpReleases."+finalRpName+".script");
                String scriptPath = ArchitectPlugin.getPluginInstance().getConfig().getString("gitHubRpReleases."+finalRpName+".path");
                if (isWindows || gitHubOwner==null || gitHubRepo==null || releaseScript==null || scriptPath==null) {
                    runOnMain(callback, true, -1);
                    return;
                }
                Process process = startScript(new File(scriptPath), "sh", releaseScript, finalRpName, gitHubOwner,
                        gitHubRepo, version, title);
                Duration timeout = getTimeout(ArchitectPlugin.getPluginInstance().getConfig(), finalRpName);
                ScriptResult result = awaitScript(process, timeout, GRACE,
                        line -> Log.info("[RP release " + finalRpName + "] " + line));
                if (!result.terminated()) {
                    Log.warn("The RP release script for '" + finalRpName + "' version " + version + " still ran after "
                            + timeout.toMinutes() + " minutes, so it was stopped.");
                }
                if (!result.outputComplete()) {
                    Log.warn("Some of the output of the RP release script for '" + finalRpName + "' version "
                            + version + " may be missing from the log: it could not be read to its end.");
                }
                runOnMain(callback, result.terminated(), result.exitCode());
            } catch (InterruptedException | IOException e) {
                Log.error("Failed to run RP release script for '" + finalRpName + "' version " + version, e);
                runOnMain(callback, false, -1);
            }
        });
    }

    // Its error stream goes into its standard output, so there is one stream to read: a script that writes more to
    // one stream than a pipe holds, while the other is being read, would wait for a reader forever.
    static Process startScript(File directory, String... command) throws IOException {
        return new ProcessBuilder(command).directory(directory).redirectErrorStream(true).start();
    }

    // The time a release script of this resource pack may run: gitHubRpReleases.<rp>.timeoutMinutes, if it is at
    // least one minute.
    static Duration getTimeout(ConfigurationSection config, String rpName) {
        int minutes = config.getInt("gitHubRpReleases." + rpName + ".timeoutMinutes", 0);
        return minutes > 0 ? Duration.ofMinutes(minutes) : DEFAULT_TIMEOUT;
    }

    /**
     * Waits up to timeout for a release script to end, while its output goes to output line by line. A script still
     * running then is stopped, with the processes it started; whatever still runs after the grace time is killed. The
     * thread that reads the output is shut down in every case, and ends then, unless a process the script started
     * still holds the output open: an interrupt does not end a read of a pipe, so that thread, a daemon, stays until
     * the output closes.
     */
    static ScriptResult awaitScript(Process process, Duration timeout, Duration grace, Consumer<String> output)
            throws InterruptedException {
        ExecutorService reader = Executors.newSingleThreadExecutor(task -> {
            Thread thread = new Thread(task, "Architect RP release output");
            thread.setDaemon(true);
            return thread;
        });
        try {
            Future<?> lines = reader.submit(new StreamGobbler(process.getInputStream(), process.getErrorStream(),
                    output));
            boolean terminated = process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS);
            if (!terminated) {
                stop(process, grace);
            }
            // A process the script started may hold its output open after it ended: then what came is all there is.
            boolean outputComplete;
            try {
                lines.get(grace.toMillis(), TimeUnit.MILLISECONDS);
                outputComplete = true;
            } catch (ExecutionException | TimeoutException ex) {
                outputComplete = false;
            }
            return new ScriptResult(terminated, process.isAlive() ? -1 : process.exitValue(), outputComplete);
        } finally {
            reader.shutdownNow();
        }
    }

    // Stops a script that is still running, and what it started, which may hold its output open. Each is asked to
    // end; whatever still runs when the grace time is over is killed. The script itself ending is no sign: sh ends at
    // once when it is asked to.
    private static void stop(Process process, Duration grace) throws InterruptedException {
        List<ProcessHandle> started = process.descendants().toList();
        process.destroy();
        started.forEach(ProcessHandle::destroy);
        CompletableFuture<?>[] ends = Stream.concat(Stream.of(process.onExit()),
                started.stream().map(ProcessHandle::onExit)).toArray(CompletableFuture[]::new);
        try {
            CompletableFuture.allOf(ends).get(grace.toMillis(), TimeUnit.MILLISECONDS);
        } catch (ExecutionException | TimeoutException ex) {
            // what still runs is killed below
        }
        if (process.isAlive()) {
            process.destroyForcibly();
        }
        started.stream().filter(ProcessHandle::isAlive).forEach(ProcessHandle::destroyForcibly);
    }

    // Deliver the release result on the main thread (the callback messages the command sender).
    private static void runOnMain(BiConsumer<Boolean, Integer> callback, boolean exit, int exitCode) {
        Bukkit.getScheduler().runTask(ArchitectPlugin.getPluginInstance(), () -> callback.accept(exit, exitCode));
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