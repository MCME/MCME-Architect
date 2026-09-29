package com.mcmiddleearth.util;

import com.mcmiddleearth.architect.ArchitectPlugin;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginDescriptionFile;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;

import static org.junit.jupiter.api.Assertions.*;

// A server may run no TheGaffer, or one from before 3.0.0, which has no recordExternalBuild. A placed special block
// must then cost at most one warning, not an error at every block. One mock/load per class, as in LogFileTest.
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class TheGafferUtilTest {

    private static ServerMock server;
    private static PlayerMock player;
    private static final List<LogRecord> logged = new ArrayList<>();

    /** TheGaffer before 3.0.0: the protection API, and nothing to record builds with. */
    public static class OldGaffer extends JavaPlugin {
        public static boolean hasBuildPermission(Player player, Location location) {
            return true;
        }

        public static String getBuildProtectionMessage(Player player, Location location) {
            return "";
        }
    }

    @BeforeAll
    static void setUp() {
        server = MockBukkit.mock();
        MockBukkit.load(ArchitectPlugin.class).getLogger().addHandler(new Handler() {
            @Override
            public void publish(LogRecord record) {
                logged.add(record);
            }

            @Override
            public void flush() {
            }

            @Override
            public void close() {
            }
        });
        server.addSimpleWorld("world");
        player = server.addPlayer();
    }

    @AfterAll
    static void tearDown() {
        if (MockBukkit.isMocked()) {
            MockBukkit.unmock();
        }
    }

    private static List<String> gafferWarnings() {
        return logged.stream().filter(record -> record.getLevel().intValue() >= Level.WARNING.intValue())
                .map(LogRecord::getMessage).filter(message -> message.contains("TheGaffer")).toList();
    }

    @Test
    @Order(1)
    void withoutTheGafferNothingIsReportedOrLogged() {
        assertDoesNotThrow(() -> TheGafferUtil.recordPlace(player, player.getLocation()));
        assertEquals(List.of(), gafferWarnings());
    }

    @Test
    @Order(2)
    void aTheGafferThatCannotRecordBuildsIsLoggedOnceNotAtEveryBlock() {
        Plugin old = MockBukkit.loadWith(OldGaffer.class,
                new PluginDescriptionFile("TheGaffer", "2.8-fake", OldGaffer.class.getName()));
        server.getPluginManager().disablePlugin(old);
        TheGafferUtil.recordPlace(player, player.getLocation());
        assertEquals(List.of(), gafferWarnings(), "a disabled TheGaffer is left alone");

        server.getPluginManager().enablePlugin(old);
        TheGafferUtil.recordPlace(player, player.getLocation());
        TheGafferUtil.recordPlace(player, player.getLocation());

        assertEquals(1, gafferWarnings().size(), gafferWarnings().toString());
        assertTrue(logged.stream().noneMatch(record -> record.getLevel() == Level.SEVERE), "a warning, not an error");
        assertTrue(gafferWarnings().get(0).contains("2.8-fake") && gafferWarnings().get(0).contains("3.0.0"),
                "names the version that cannot count, and the one that can: " + gafferWarnings());
    }
}
