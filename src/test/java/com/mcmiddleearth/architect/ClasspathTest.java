package com.mcmiddleearth.architect;

import org.junit.jupiter.api.Test;

import java.net.URL;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

// dynmap-api 3.5's POM on repo.mikeprimm.com declares Bukkit 1.7.10-R0.1-SNAPSHOT, which happens to carry json-simple.
// PluginUtils 2.0.2's MessageUtil needs json-simple without declaring it. The copy of that POM on MCME's mirror declares
// no Bukkit. So when repo.mikeprimm.com was down and the Maven cache was cold, Maven took the mirror's POM, and every
// test that loads Architect failed with a NoClassDefFoundError (CI run 36352406820, attempt 1).
class ClasspathTest {

    @Test
    void jsonSimpleComesFromItsOwnJar() throws Exception {
        String source = Class.forName("org.json.simple.parser.ParseException").getProtectionDomain().getCodeSource()
                .getLocation().toString();
        assertTrue(source.contains("json-simple"), source);
    }

    @Test
    void onlyPaperProvidesTheBukkitApi() throws Exception {
        List<URL> sources = Collections.list(getClass().getClassLoader().getResources("org/bukkit/Bukkit.class"));
        assertEquals(1, sources.size(), sources.toString());
        assertTrue(sources.get(0).toString().contains("paper-api"), sources.toString());
    }
}
