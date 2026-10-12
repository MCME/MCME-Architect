package com.mcmiddleearth.architect;

import org.bukkit.Bukkit;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.FileConfiguration;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.util.function.Consumer;

/**
 * Architect's config.yml, which servers may share. Saving the config writes this server's whole copy, so a server that
 * saved the copy it read at its start would write back what another server had changed since: a removed slot, or a
 * fixed hash. Every change is therefore written through update, which reads the file first.
 */
public final class ConfigFile {

    private ConfigFile() {
    }

    /**
     * Reads config.yml from disk into Architect's config, applies change to it, and saves it, on the main thread.
     * change is given the config just read: code that held a section of the config from before must look it up again
     * in there. A config.yml that cannot be read, or holds nothing, is left as it is, and change is not applied: the
     * file may be in the middle of being written by another server, and saving now would leave only this server's
     * copy. Called off the main thread, the update runs there on a later tick, and false is returned.
     *
     * @return whether the change was saved
     */
    public static boolean update(Consumer<FileConfiguration> change) {
        ArchitectPlugin plugin = ArchitectPlugin.getPluginInstance();
        if (!Bukkit.isPrimaryThread()) {
            Bukkit.getScheduler().runTask(plugin, () -> update(change));
            return false;
        }
        File file = new File(plugin.getDataFolder(), "config.yml");
        FileConfiguration config = plugin.getConfig();
        try {
            String content = Files.readString(file.toPath(), StandardCharsets.UTF_8);
            if (content.isBlank()) {
                Log.error(file + " is empty, so a change to it was not saved. Fix the file, then /architect reload.");
                return false;
            }
            config.loadFromString(content); // in place: the config object stays the one getConfig() returns
        } catch (NoSuchFileException ex) {
            // There is nothing on disk to keep: the config is saved as it is, with the change.
        } catch (IOException | InvalidConfigurationException ex) {
            Log.error(file + " could not be read, so a change to it was not saved. Fix the file, then /architect reload.",
                    ex);
            return false;
        }
        change.accept(config);
        try {
            config.save(file);
            return true;
        } catch (IOException ex) {
            Log.error("Could not save " + file, ex);
            return false;
        }
    }
}
