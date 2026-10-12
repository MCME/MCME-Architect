package com.mcmiddleearth.architect.serverResoucePack;

import com.mcmiddleearth.architect.ArchitectPlugin;
import com.mcmiddleearth.architect.ConfigFile;
import com.mcmiddleearth.architect.Log;
import com.mcmiddleearth.architect.PluginData;
import com.mcmiddleearth.util.StreamGobbler;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;

import java.io.File;
import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
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

    // Where the files of a release are downloaded from: GitHub. A test points it at a server of its own.
    static String releaseSite = "https://github.com/";

    // Whether this server runs Windows, where releases don't run. A test sets it.
    static boolean onWindows = System.getProperty("os.name").toLowerCase(Locale.ROOT).startsWith("windows");

    /** Starts a release script in its folder. */
    interface ScriptStarter {
        Process start(File directory, String... command) throws IOException;
    }

    // How release scripts are started. A test starts a fake instead.
    static ScriptStarter starter = RpReleaseUtil::startScript;

    /**
     * Why the pack can't be released from this server, or null when it can: a Windows server, or a gitHubRpReleases
     * entry without an owner, repo, script or path. rpName is the pack's name in config.yml.
     */
    static String cannotRelease(String rpName) {
        if (onWindows) {
            return "Releases only run on Linux servers.";
        }
        ConfigurationSection config = ArchitectPlugin.getPluginInstance().getConfig();
        String entry = "gitHubRpReleases." + rpName + ".";
        if (Stream.of("owner", "repo", "script", "path").anyMatch(key -> config.getString(entry + key) == null)) {
            return rpName + " can't be released from this server: its gitHubRpReleases entry needs owner, repo,"
                    + " script and path. Ask an admin.";
        }
        return null;
    }

    /**
     * Releases a pack, and tells cs how it goes: a start line now, a line as each group of zips is done, and the
     * closing message, which waits for a releaser who has left the server. rpName is the pack's name in config.yml,
     * and cannotRelease(rpName) is null. Called on the main thread; the script runs on another.
     */
    public static void releaseResourcePack(CommandSender cs, String rpName, String version, String title) {
        ArchitectPlugin plugin = ArchitectPlugin.getPluginInstance();
        RpReleaseFeedback feedback = plugin.getReleaseFeedback();
        String prefix = PluginData.getMessageUtil().getPREFIX();
        ConfigurationSection config = plugin.getConfig();
        // Read on the main thread: an update of config.yml reads it again there, in place, and a read from the
        // release's own thread meanwhile could find it empty.
        String gitHubOwner = getGitHubOwner(rpName);
        String gitHubRepo = getGitHubRepo(rpName);
        String releaseScript = config.getString("gitHubRpReleases." + rpName + ".script");
        String scriptPath = config.getString("gitHubRpReleases." + rpName + ".path");
        Duration timeout = getTimeout(config, rpName);
        boolean dynmap = "releaseDynmap.sh".equals(releaseScript);
        File dataFolder = plugin.getDataFolder();
        feedback.tell(cs, RpReleaseChat.start(prefix, rpName, version, title, RpReleaseTimes.last(dataFolder, rpName)));
        RpReleaseReport report = new RpReleaseReport(step -> Bukkit.getScheduler().runTask(plugin,
                () -> feedback.tell(cs, RpReleaseChat.step(prefix, rpName, step))));
        long started = System.nanoTime();
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            RpReleaseVerdict verdict;
            boolean succeeded = false;
            try {
                Process process = starter.start(new File(scriptPath), "sh", releaseScript, rpName, gitHubOwner,
                        gitHubRepo, version, title);
                ScriptResult result = awaitScript(process, timeout, GRACE, line -> {
                    Log.info("[RP release " + rpName + "] " + line);
                    report.accept(line);
                });
                if (!result.terminated()) {
                    Log.warn("The RP release script for '" + rpName + "' version " + version + " still ran after "
                            + timeout.toMinutes() + " minutes, so it was stopped.");
                }
                if (!result.outputComplete()) {
                    Log.warn("Some of the output of the RP release script for '" + rpName + "' version "
                            + version + " may be missing from the log: it could not be read to its end.");
                }
                verdict = report.verdict(result, rpName, version, dynmap, timeout);
                succeeded = result.terminated() && result.exitCode() == 0;
            } catch (IOException e) {
                Log.error("Failed to run RP release script for '" + rpName + "' version " + version, e);
                verdict = RpReleaseVerdict.couldNotStart(rpName, version,
                        e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName());
            } catch (InterruptedException e) {
                // The server is stopping, so there is nobody to tell, and nothing may be scheduled any more
                Log.error("The RP release of '" + rpName + "' version " + version + " was interrupted", e);
                Thread.currentThread().interrupt();
                return;
            }
            Log.info("[RP release " + rpName + "] Result: " + verdict.headline());
            RpReleaseVerdict closing = verdict;
            boolean record = succeeded;
            Duration took = Duration.ofNanos(System.nanoTime() - started);
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (record) {
                    try {
                        RpReleaseTimes.record(dataFolder, rpName, took);
                    } catch (IOException e) {
                        Log.warn("Couldn't note how long the RP release of '" + rpName + "' took", e);
                    }
                }
                feedback.finish(cs, RpReleaseChat.verdict(prefix, closing, rpName, version));
            });
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
                    skippingFailedLines(output)));
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

    // Passes each line to output, and goes on when output fails on one: a script whose output is no longer read
    // stops once the pipe is full, and holds the release lock until its time is up. The first failure is logged.
    private static Consumer<String> skippingFailedLines(Consumer<String> output) {
        AtomicBoolean failed = new AtomicBoolean();
        return line -> {
            try {
                output.accept(line);
            } catch (RuntimeException e) {
                if (failed.compareAndSet(false, true)) {
                    Log.warn("A line of the RP release script's output could not be handled. The rest is still read,"
                            + " but the result shown in chat may be incomplete.", e);
                }
            }
        };
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

    /**
     * Points the slots of a pack for one Minecraft version at the files of a release. Each file is downloaded and
     * hashed off the main thread first. Then, on the main thread, config.yml is read again, each slot whose file was
     * hashed gets its url and sha together, and config.yml is saved once; a slot whose file could not be hashed keeps
     * its url and sha. The result, whose slots are the variants, reaches callback on the main thread. /rp server has
     * checked that rpName matches a pack in config.yml, and that mcVersion (26.2 or 26_2) is a key of
     * protocolVersions.yml.
     */
    public static void setServerResourcePack(CommandSender cs, String rpName, String version, String mcVersion,
                                             Consumer<RpManager.SlotResult> callback) {
        String finalRpName = RpManager.matchRpName(rpName);
        String versionKey = mcVersion.replace('.','_');
        ConfigurationSection rpConfig = RpManager.getRpConfig().getConfigurationSection(finalRpName);
        if(finalRpName.isEmpty() || rpConfig == null) {
            Log.warn("No server resource pack in config.yml matches '" + rpName + "'.");
            callback.accept(RpManager.SlotResult.ofError());
            return;
        }
        // the file of the release for each variant
        Map<String, String> urls = new LinkedHashMap<>();
        String download = releaseSite + getGitHubOwner(finalRpName) + "/" + getGitHubRepo(finalRpName)
                + "/releases/download/" + version + "/";
        if (rpConfig.contains("sodium")) {
            urls.put("vanilla.16px.light", download + finalRpName + "-Vanilla.zip");
            urls.put("vanilla.16px.footprints", download + finalRpName + "-Vanilla-Footprints.zip");
            urls.put("sodium.16px.light", download + finalRpName + "-Sodium.zip");
            urls.put("sodium.16px.footprints", download + finalRpName + "-Sodium-Footprints.zip");
            urls.put("lite.16px.light", download + finalRpName + "-Lite.zip");
            urls.put("lite.16px.footprints", download + finalRpName + "-Lite-Footprints.zip");
        } else {
            urls.put("vanilla.16px.light", download + finalRpName + ".zip");
            urls.put("vanilla.16px.footprints", download + finalRpName + "-Footprints.zip");
        }
        ArchitectPlugin plugin = ArchitectPlugin.getPluginInstance();
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            Map<String, String> shas = new LinkedHashMap<>();
            Map<String, String> failed = new LinkedHashMap<>();
            try {
                for (Map.Entry<String, String> file : urls.entrySet()) {
                    try {
                        shas.put(file.getKey(), RpManager.sha1(file.getValue(), () -> Bukkit.getScheduler().runTask(
                                plugin, () -> PluginData.getMessageUtil().sendInfoMessage(cs, "calculating ..."))));
                    } catch (IOException ex) {
                        String reason = file.getValue() + ": " + RpManager.describe(ex);
                        Log.warn("Server resource pack " + finalRpName + ", variant " + file.getKey() + ", version "
                                + versionKey + " was not set to " + version + ": " + reason);
                        failed.put(file.getKey(), reason);
                    }
                }
            } catch (Exception ex) {
                // Nothing in the background may fail silently: the error is logged, and the admin told.
                Log.error("Failed to set the server resource pack " + finalRpName + " to " + version
                        + " for MC " + versionKey, ex);
                Bukkit.getScheduler().runTask(plugin, () -> callback.accept(RpManager.SlotResult.ofError()));
                return;
            }
            Bukkit.getScheduler().runTask(plugin,
                    () -> callback.accept(setSlots(finalRpName, version, versionKey, urls, shas, failed)));
        });
    }

    // On the main thread, in config.yml as it is now on disk: each variant whose file was hashed gets url and sha
    // together in its slot for the version, and config.yml is saved once.
    private static RpManager.SlotResult setSlots(String rpName, String version, String versionKey,
                                                 Map<String, String> urls, Map<String, String> shas,
                                                 Map<String, String> failed) {
        try {
            if (shas.isEmpty()) {
                return new RpManager.SlotResult(List.of(), failed, false);
            }
            List<String> set = new ArrayList<>();
            boolean saved = ConfigFile.update(config -> {
                ConfigurationSection packs = RpManager.getRpConfig(config);
                ConfigurationSection rpConfig = packs == null ? null : packs.getConfigurationSection(rpName);
                if (rpConfig != null) {
                    shas.forEach((variant, sha) -> {
                        setSlot(rpConfig, variant, versionKey, urls.get(variant), sha);
                        set.add(variant);
                    });
                }
            });
            if (!saved) {
                return RpManager.SlotResult.ofError(); // ConfigFile logged why
            }
            if (set.isEmpty()) {
                Log.warn("Server resource pack " + rpName + " is no longer in config.yml, so it was not set to "
                        + version + ".");
                return RpManager.SlotResult.ofError();
            }
            return new RpManager.SlotResult(set, failed, false);
        } catch (RuntimeException ex) {
            Log.error("Failed to set the server resource pack " + rpName + " to " + version + " for MC " + versionKey, ex);
            return RpManager.SlotResult.ofError();
        }
    }

    // A variant that held one pack for every version, as in old configs, has its url and sha replaced by its first
    // version slot.
    private static void setSlot(ConfigurationSection rpConfig, String variant, String versionKey, String url, String sha) {
        ConfigurationSection section = rpConfig.getConfigurationSection(variant);
        if(section != null && section.contains("url")) {
            section.set("url", null);
            section.set("sha", null);
        }
        rpConfig.set(variant + "." + versionKey + ".url", url);
        rpConfig.set(variant + "." + versionKey + ".sha", sha);
    }

    private static String getGitHubOwner(String rpName) {
        return ArchitectPlugin.getPluginInstance().getConfig().getString("gitHubRpReleases."+rpName+".owner");
    }

    private static String getGitHubRepo(String rpName) {
        return ArchitectPlugin.getPluginInstance().getConfig().getString("gitHubRpReleases."+rpName+".repo");
    }
}