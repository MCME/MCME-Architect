package com.mcmiddleearth.architect.additionalListeners;

import com.mcmiddleearth.architect.ArchitectPlugin;
import com.mcmiddleearth.architect.Modules;
import com.mcmiddleearth.architect.PluginData;
import org.bukkit.GameMode;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockbukkit.mockbukkit.world.WorldMock;

import static org.junit.jupiter.api.Assertions.*;

// Survival flight: where playerSurvivalFly is on, players may fly in survival. Paper fires PlayerGameModeChangeEvent
// before it changes the mode, then gives the player the new mode's own abilities, and survival's turn flight off.
// MockBukkit's player changes only the mode, so these tests use one that follows Paper's order. One mock/load per
// class, as Architect caches data-folder paths in static fields.
class GameMechanicsListenerTest {

    private static ServerMock server;
    private static WorldMock world;
    private static WorldMock worldWithoutSurvivalFly;
    private static int players;

    @BeforeAll
    static void setUp() {
        server = MockBukkit.mock();
        MockBukkit.load(ArchitectPlugin.class);
        world = server.addSimpleWorld("world");
        worldWithoutSurvivalFly = server.addSimpleWorld("noSurvivalFly");
        PluginData.setModuleEnabled(worldWithoutSurvivalFly, Modules.PLAYER_SURVIVAL_FLY, false);
    }

    @AfterAll
    static void tearDown() {
        if (MockBukkit.isMocked()) {
            MockBukkit.unmock();
        }
    }

    private static void tick() {
        server.getScheduler().performOneTick();
    }

    /** A builder in creative in this world, as they are before they switch to survival. */
    private static PlayerMock builderInCreative(WorldMock in) {
        PlayerMock builder = new PaperOrderPlayer(server, "Builder" + (++players));
        server.addPlayer(builder);
        if (builder.getWorld() != in) {
            builder.teleport(in.getSpawnLocation());
        }
        builder.setGameMode(GameMode.CREATIVE);
        return builder;
    }

    @Test
    void aSwitchToSurvivalAllowsFlightOnceTheServerHasTicked() {
        PlayerMock builder = builderInCreative(world);

        builder.setGameMode(GameMode.SURVIVAL); // as /gamemode, an op's F3+F4 and Architect's switcher do
        tick();

        assertTrue(builder.getAllowFlight(), "flight is allowed once the server has ticked");
    }

    @Test
    void notInAWorldWithoutSurvivalFly() {
        PlayerMock builder = builderInCreative(worldWithoutSurvivalFly);

        builder.setGameMode(GameMode.SURVIVAL);
        tick();

        assertFalse(builder.getAllowFlight());
    }

    @Test
    void notForAPlayerWhoLeftSurvivalTheWorldOrTheServerBeforeTheTick() {
        PlayerMock toAdventure = builderInCreative(world);
        toAdventure.setGameMode(GameMode.SURVIVAL);
        toAdventure.setGameMode(GameMode.ADVENTURE);
        PlayerMock toOtherWorld = builderInCreative(world);
        toOtherWorld.setGameMode(GameMode.SURVIVAL);
        toOtherWorld.teleport(worldWithoutSurvivalFly.getSpawnLocation());
        PlayerMock leaving = builderInCreative(world);
        leaving.setGameMode(GameMode.SURVIVAL);
        leaving.disconnect();

        tick();

        assertFalse(toAdventure.getAllowFlight(), "in adventure by then");
        assertFalse(toOtherWorld.getAllowFlight(), "in a world without survival flight by then");
        assertFalse(leaving.getAllowFlight(), "gone by then");
    }

    /**
     * Changes game mode in Paper's order: the event, then the mode, then the mode's own abilities as vanilla's
     * GameType.updatePlayerAbilities sets them. Creative may fly, spectator flies, survival and adventure may not.
     */
    private static final class PaperOrderPlayer extends PlayerMock {

        PaperOrderPlayer(ServerMock server, String name) {
            super(server, name);
        }

        @Override
        public void setGameMode(GameMode mode) {
            GameMode before = getGameMode();
            super.setGameMode(mode);
            if (before == mode || getGameMode() != mode) {
                return; // no change, or the event was cancelled: the server leaves the abilities alone
            }
            switch (mode) {
                case CREATIVE -> setAllowFlight(true);
                case SPECTATOR -> {
                    setAllowFlight(true);
                    setFlying(true);
                }
                default -> setAllowFlight(false); // which also stops the player flying
            }
        }
    }
}
