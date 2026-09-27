package com.mcmiddleearth.architect;

import org.bukkit.permissions.Permission;
import org.bukkit.plugin.PluginDescriptionFile;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

// Bukkit grants a child permission by name whether or not it is declared, so a misspelt child
// (archtiect.resourcePackAdmin) loads without a warning and grants a node nothing checks. Requiring
// every child to be declared catches that. Bukkit matches permission names case-insensitively, and
// so does this test.
//
// plugin.yml is read from the classpath because only the Maven-filtered copy has a valid name, and
// is parsed by Bukkit's own PluginDescriptionFile, as the server does. Its Permission objects need a
// server, hence MockBukkit.mock() without loading the plugin.
class PluginYmlPermissionsTest {
    @BeforeAll static void setUp() { MockBukkit.mock(); }
    @AfterAll  static void tearDown() { if (MockBukkit.isMocked()) MockBukkit.unmock(); }

    @Test void everyChildPermissionIsDeclared() throws Exception {
        PluginDescriptionFile description;
        try (InputStream in = ArchitectPlugin.class.getClassLoader().getResourceAsStream("plugin.yml")) {
            assertNotNull(in, "plugin.yml is not on the classpath");
            description = new PluginDescriptionFile(in);
        }
        assertEquals(ArchitectPlugin.class.getName(), description.getMain(), "read another plugin's plugin.yml");

        Set<String> declared = description.getPermissions().stream()
                .map(permission -> permission.getName().toLowerCase(Locale.ROOT))
                .collect(Collectors.toSet());
        List<String> undeclared = new ArrayList<>();
        for (Permission parent : description.getPermissions()) {
            for (String child : parent.getChildren().keySet()) {
                if (!declared.contains(child.toLowerCase(Locale.ROOT))) {
                    undeclared.add(parent.getName() + " -> " + child);
                }
            }
        }
        assertEquals(List.of(), undeclared, "children of these permissions are not declared under permissions:");
    }
}
