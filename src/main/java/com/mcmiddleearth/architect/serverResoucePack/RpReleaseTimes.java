package com.mcmiddleearth.architect.serverResoucePack;

import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.time.Duration;

/**
 * How long each pack's last release took, for the start line of its next: rp-release-times.yml in Architect's plugin
 * folder, one "&lt;pack&gt;: &lt;seconds&gt;" per pack. Only releases that ended with exit code 0 are recorded.
 */
final class RpReleaseTimes {

    static final String FILE = "rp-release-times.yml";

    private RpReleaseTimes() {
    }

    /** The pack's last release time; null without a record, or when the file can't be read. */
    static Duration last(File folder, String pack) {
        File file = new File(folder, FILE);
        if (!file.isFile()) {
            return null;
        }
        YamlConfiguration times = new YamlConfiguration();
        try {
            times.load(file);
        } catch (IOException | InvalidConfigurationException e) {
            return null;
        }
        long seconds = times.getLong(pack, -1);
        return seconds < 0 ? null : Duration.ofSeconds(seconds);
    }

    /** Records how long the pack's release took. A file that can't be read is replaced. */
    static void record(File folder, String pack, Duration took) throws IOException {
        File file = new File(folder, FILE);
        YamlConfiguration times = new YamlConfiguration();
        if (file.isFile()) {
            try {
                times.load(file);
            } catch (InvalidConfigurationException e) {
                // a broken file is replaced
            }
        }
        times.set(pack, took.toSeconds());
        times.save(file);
    }
}
