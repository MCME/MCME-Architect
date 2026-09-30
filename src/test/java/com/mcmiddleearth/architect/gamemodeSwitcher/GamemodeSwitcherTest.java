package com.mcmiddleearth.architect.gamemodeSwitcher;

import com.mcmiddleearth.architect.ArchitectPlugin;
import com.mcmiddleearth.architect.Modules;
import com.mcmiddleearth.architect.PluginData;
import com.mcmiddleearth.architect.WorldConfig;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.GameMode;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.player.PlayerCommandSendEvent;
import org.bukkit.permissions.Permission;
import org.bukkit.permissions.PermissionAttachment;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockbukkit.mockbukkit.world.WorldMock;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.*;

// The switcher's Bukkit side. MockBukkit cannot run ProtocolLib, so the tests stand in for its two edges: a level
// sender that records what each client is told, and onSwitchRequest called as the packet listener calls it. One
// mock/load per class, as Architect caches data-folder paths in static fields.
class GamemodeSwitcherTest {

    private static final String CREATIVE = "architect.gamemodeSwitcher.creative";
    private static final String SURVIVAL = "architect.gamemodeSwitcher.survival";

    private static ServerMock server;
    private static ArchitectPlugin plugin;
    private static WorldMock world;
    private static WorldMock otherWorld;
    private static WorldMock worldWithoutSwitcher;
    private static GamemodeSwitcher switcher;
    /** What the level sender was asked to send, as "player level". */
    private static final List<String> sent = new ArrayList<>();

    @BeforeAll
    static void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.load(ArchitectPlugin.class);
        // MockBukkit does not register plugin.yml's permissions, as a server does; the parent needs them
        for (Permission permission : plugin.getDescription().getPermissions()) {
            if (server.getPluginManager().getPermission(permission.getName()) == null) {
                server.getPluginManager().addPermission(permission);
            }
        }
        world = server.addSimpleWorld("world");
        otherWorld = server.addSimpleWorld("otherWorld");
        worldWithoutSwitcher = server.addSimpleWorld("noSwitcher");
        PluginData.setModuleEnabled(worldWithoutSwitcher, Modules.GAMEMODE_SWITCHER, false);
        switcher = new GamemodeSwitcher(plugin, (player, level) -> sent.add(player.getName() + " " + level));
        server.getPluginManager().registerEvents(switcher, plugin);
    }

    @AfterAll
    static void tearDown() {
        if (MockBukkit.isMocked()) {
            MockBukkit.unmock();
        }
    }

    @BeforeEach
    void reset() {
        tick(); // what an earlier test left for the next tick
        sent.clear();
    }

    private static void tick() {
        server.getScheduler().performOneTick();
    }

    /** A player who joins in the first world with these permissions, given before the switcher looks. */
    private static PlayerMock join(String... permissions) {
        PlayerMock player = server.addPlayer();
        for (String permission : permissions) {
            player.addAttachment(plugin, permission, true);
        }
        return player;
    }

    /** The server sends the command tree after every level it sends, and when permissions change. */
    private static void sendCommands(PlayerMock player) {
        server.getPluginManager().callEvent(new PlayerCommandSendEvent(player, new ArrayList<>()));
    }

    private static void readMessages(PlayerMock player) {
        while (player.nextComponentMessage() != null) {
            // what came before
        }
    }

    private static String plain(Component message) {
        return message == null ? null : PlainTextComponentSerializer.plainText().serialize(message);
    }

    // Who gets the switcher

    @ParameterizedTest
    @EnumSource(GameMode.class)
    void anyOneModePermissionGivesTheSwitcherATickAfterJoining(GameMode mode) {
        PlayerMock builder = join("architect.gamemodeSwitcher." + mode.name().toLowerCase(Locale.ROOT));
        assertEquals(world, builder.getWorld());

        assertEquals(List.of(), sent, "not in the join's own tick: the server sends the real level then");
        tick();
        assertEquals(List.of(builder.getName() + " 2"), sent);
    }

    @Test
    void playersWithoutAModePermissionGetNothing() {
        join();
        join("architect.viewdistance");
        tick();

        assertEquals(List.of(), sent);
    }

    @Test
    void opsAndGamemodeCommandHoldersAreLeftToVanilla() {
        PlayerMock op = join(CREATIVE);
        op.setOp(true);
        join(CREATIVE, "minecraft.command.gamemode");
        tick();

        assertEquals(List.of(), sent, "the server gives them its own level");
    }

    @Test
    void notInAWorldWhereTheModuleIsOff() {
        PlayerMock builder = join(CREATIVE);
        builder.teleport(worldWithoutSwitcher.getSpawnLocation());
        tick();

        assertEquals(List.of(), sent);
    }

    @Test
    void theModuleIsOnByDefault() {
        assertTrue(PluginData.isModuleEnabled(world, Modules.GAMEMODE_SWITCHER));
        YamlConfiguration defaults = YamlConfiguration.loadConfiguration(new File(WorldConfig.getWorldConfigDir(),
                WorldConfig.getDefaultWorldConfigName() + "." + WorldConfig.getCfgExtension()));
        assertTrue(defaults.getBoolean("modules.environment.gamemodeSwitcher"), "in defaultWorldConfig.yml");
    }

    // When: a tick after each moment the server sends a player's real level

    @Test
    void theSwitcherIsSentAgainATickAfterRespawnWorldChangeAndCommandTree() {
        PlayerMock builder = join(CREATIVE);
        tick();
        sent.clear();
        List<String> switcherLevel = List.of(builder.getName() + " 2");

        builder.respawn();
        assertEquals(List.of(), sent);
        tick();
        assertEquals(switcherLevel, sent, "after a respawn");

        sent.clear();
        builder.teleport(otherWorld.getSpawnLocation());
        assertEquals(List.of(), sent);
        tick();
        assertEquals(switcherLevel, sent, "after a world change");

        sent.clear();
        sendCommands(builder);
        assertEquals(List.of(), sent);
        tick();
        assertEquals(switcherLevel, sent, "after the command tree");
    }

    @Test
    void aBuilderWhoStopsQualifyingGetsTheRealLevelBack() {
        PlayerMock builder = join();
        PermissionAttachment creative = builder.addAttachment(plugin, CREATIVE, true);
        tick();
        sent.clear();

        builder.teleport(worldWithoutSwitcher.getSpawnLocation());
        tick();
        assertEquals(List.of(builder.getName() + " 0"), sent, "in a world without the module");

        sent.clear();
        builder.teleport(world.getSpawnLocation());
        tick();
        assertEquals(List.of(builder.getName() + " 2"), sent, "back in a world with it");

        sent.clear();
        creative.remove();
        sendCommands(builder);
        tick();
        assertEquals(List.of(builder.getName() + " 0"), sent, "when the permission is taken away");

        sent.clear();
        sendCommands(builder);
        tick();
        assertEquals(List.of(), sent, "once: after that the level the server sends stands");
    }

    @Test
    void aBuilderWhoIsMadeOpKeepsTheLevelTheServerSent() {
        PlayerMock builder = join(CREATIVE);
        tick();
        sent.clear();

        builder.setOp(true);
        sendCommands(builder);
        tick();

        assertEquals(List.of(), sent, "an op change sends the op's own level, which Bukkit does not tell");
    }

    @Test
    void aBuilderWhoLeftIsForgotten() {
        PlayerMock builder = join(CREATIVE);
        tick();
        builder.disconnect(); // which also drops the permission
        builder.reconnect();
        sent.clear();
        tick();

        assertEquals(List.of(), sent, "the server sent the real level when they joined again");
    }

    // Requests from the switcher

    @Test
    void aPermittedRequestSwitchesOnTheMainThreadAndSaysSoAsVanillaDoes() {
        PlayerMock builder = join(CREATIVE);
        builder.setGameMode(GameMode.SURVIVAL);
        readMessages(builder);

        assertTrue(switcher.onSwitchRequest(builder, GameMode.CREATIVE), "Architect takes the packet");
        assertEquals(GameMode.SURVIVAL, builder.getGameMode(), "not on the network thread");
        tick();

        assertEquals(GameMode.CREATIVE, builder.getGameMode());
        assertEquals(Component.translatable("commands.gamemode.success.self",
                Component.translatable("gameMode.creative")), builder.nextComponentMessage());
        assertNull(builder.nextComponentMessage());

        assertTrue(switcher.onSwitchRequest(builder, GameMode.CREATIVE));
        tick();
        assertNull(builder.nextComponentMessage(), "already in that mode: nothing to say, as in vanilla");
    }

    @Test
    void theParentPermissionGivesEveryMode() {
        PlayerMock builder = join("architect.gamemodeSwitcher");

        for (GameMode mode : List.of(GameMode.CREATIVE, GameMode.ADVENTURE, GameMode.SPECTATOR, GameMode.SURVIVAL)) {
            assertTrue(switcher.onSwitchRequest(builder, mode));
            tick();
            assertEquals(mode, builder.getGameMode());
        }
    }

    @Test
    void aRequestForAModeWithoutItsPermissionIsRefused() {
        PlayerMock builder = join(CREATIVE, SURVIVAL);
        builder.setGameMode(GameMode.CREATIVE);
        readMessages(builder);

        assertTrue(switcher.onSwitchRequest(builder, GameMode.SPECTATOR));
        tick();

        assertEquals(GameMode.CREATIVE, builder.getGameMode());
        assertEquals("You can't switch to spectator mode here.", plain(builder.nextComponentMessage()));
    }

    @Test
    void aRequestInAWorldWithoutTheModuleIsRefused() {
        PlayerMock builder = join(CREATIVE);
        builder.teleport(worldWithoutSwitcher.getSpawnLocation());
        builder.setGameMode(GameMode.SURVIVAL);
        readMessages(builder);

        assertTrue(switcher.onSwitchRequest(builder, GameMode.CREATIVE));
        tick();

        assertEquals(GameMode.SURVIVAL, builder.getGameMode());
        assertEquals("You can't switch to creative mode here.", plain(builder.nextComponentMessage()));
    }

    @Test
    void aRequestVanillaAcceptsIsLeftToVanilla() {
        PlayerMock op = join();
        op.setOp(true);
        PlayerMock commandHolder = join("minecraft.command.gamemode");
        for (PlayerMock player : List.of(op, commandHolder)) {
            player.setGameMode(GameMode.SURVIVAL);
            readMessages(player);
            assertFalse(switcher.onSwitchRequest(player, GameMode.CREATIVE), "the packet goes on to vanilla");
        }
        tick();

        for (PlayerMock player : List.of(op, commandHolder)) {
            assertEquals(GameMode.SURVIVAL, player.getGameMode(), player.getName());
            assertNull(player.nextComponentMessage(), player.getName());
        }
    }

    // GameMechanicsListener lets players fly in survival where playerSurvivalFly is on, as it is by default.
    @Test
    void aSwitchToSurvivalStillGivesSurvivalFlight() {
        PlayerMock builder = join(SURVIVAL);
        builder.setGameMode(GameMode.CREATIVE);
        builder.setAllowFlight(false);

        switcher.onSwitchRequest(builder, GameMode.SURVIVAL);
        tick();

        assertEquals(GameMode.SURVIVAL, builder.getGameMode());
        assertTrue(builder.getAllowFlight());
    }
}
