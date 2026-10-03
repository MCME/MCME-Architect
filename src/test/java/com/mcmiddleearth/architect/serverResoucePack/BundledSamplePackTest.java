package com.mcmiddleearth.architect.serverResoucePack;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

// The bundled config.yml has a sample pack, whose version must be named the way /rp server writes one: as a quoted
// string. Unquoted, YAML reads 1_18_1 as the number 1181, which protocolVersions.yml does not know, and the search
// for a player's pack gives up at a version it does not know. The bundled config is the defaults of every server's
// config, so a server without a pack of its own at the same place finds the sample there.
class BundledSamplePackTest {

    @Test
    void eachVersionInTheBundledConfigIsOneProtocolVersionsKnows() throws IOException {
        ConfigurationSection packs = load("config.yml").getConfigurationSection("ServerResourcePacks");
        assertNotNull(packs, "the bundled packs");
        List<String> versions = new ArrayList<>();
        for (String path : packs.getKeys(true)) {
            // pack.client.resolution.variant.version
            if (path.split("[.]").length == 5 && packs.isConfigurationSection(path)) {
                versions.add(path.substring(path.lastIndexOf('.') + 1));
            }
        }
        assertEquals(List.of("1_18_1"), versions, "the versions of the bundled packs");
        assertTrue(load("protocolVersions.yml").getKeys(false).containsAll(versions),
                "protocolVersions.yml knows " + versions);
    }

    private static YamlConfiguration load(String resource) throws IOException {
        try (InputStream in = BundledSamplePackTest.class.getClassLoader().getResourceAsStream(resource)) {
            assertNotNull(in, resource + " is not on the classpath");
            return YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8));
        }
    }
}
