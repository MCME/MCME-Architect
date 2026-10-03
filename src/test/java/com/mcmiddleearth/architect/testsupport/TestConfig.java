package com.mcmiddleearth.architect.testsupport;

import com.mcmiddleearth.architect.ArchitectPlugin;
import org.bukkit.plugin.PluginDescriptionFile;
import org.mockbukkit.mockbukkit.ServerMock;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;

/**
 * Gives Architect a config.yml of a test's own, which it reads instead of the bundled one; what the file leaves out
 * still comes from the bundled config. MockBukkit makes a plugin's data folder only when it loads the plugin, as
 * {@code <name>-<version>} in its temporary folder, so the file is written there first: call this after
 * {@code MockBukkit.mock()} and before {@code MockBukkit.load}, and compare {@code getDataFolder()} with the folder
 * it returns.
 */
public final class TestConfig {

    private TestConfig() {
    }

    /** Writes config.yml with these lines where Architect's data folder will be, and returns that folder. */
    public static File write(ServerMock server, String... lines) throws Exception {
        PluginDescriptionFile description;
        try (InputStream in = ArchitectPlugin.class.getClassLoader().getResourceAsStream("plugin.yml")) {
            if (in == null) {
                throw new IOException("plugin.yml is not on the classpath");
            }
            description = new PluginDescriptionFile(in);
        }
        File folder = new File(server.getPluginManager().getParentTemporaryDirectory(),
                description.getName() + "-" + description.getVersion());
        if (!folder.isDirectory() && !folder.mkdirs()) {
            throw new IOException("could not make " + folder);
        }
        Files.writeString(new File(folder, "config.yml").toPath(), String.join(System.lineSeparator(), lines));
        return folder;
    }

    /**
     * Writes config.yml with an RP database of this name, which FakeMysql then stands in for: the bundled config
     * holds placeholders, which mean no database.
     */
    public static File withRpDatabase(ServerMock server, String dbName) throws Exception {
        return write(server,
                "rpSettingsDatabase:",
                "    user: architect",
                "    password: architect",
                "    dbName: " + dbName,
                "    ip: localhost",
                "    port: 3306",
                "");
    }
}
