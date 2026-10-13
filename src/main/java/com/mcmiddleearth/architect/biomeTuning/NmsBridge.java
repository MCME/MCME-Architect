package com.mcmiddleearth.architect.biomeTuning;

import com.google.gson.JsonObject;
import io.papermc.paper.connection.PlayerConfigurationConnection;
import java.nio.file.Path;
import java.util.List;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;

/**
 * Everything biome tuning needs from Minecraft internals. ReflectionNmsBridge is the only implementation that
 * touches them; tests use FakeNmsBridge.
 */
public interface NmsBridge {

    /** Problems found while resolving internals; empty means biome tuning can run. */
    List<String> selfCheck();

    /** The folder the server loads datapacks from: vanilla's LevelResource.DATAPACK_DIR under the level root. */
    Path datapacksFolder() throws BiomeTuningException;

    /** Whether clients receive this biome's full data (true for datapack biomes, false for vanilla ones). */
    boolean sentInFull(NamespacedKey biome) throws BiomeTuningException;

    /** The running definition of a biome, encoded with vanilla's datapack codec. */
    JsonObject encode(NamespacedKey biome) throws BiomeTuningException;

    /** Decodes a whole biome with vanilla's datapack codec; the exception carries the codec's message. */
    DecodedBiome decode(JsonObject biomeJson) throws BiomeTuningException;

    /** Swaps the climate, attributes and effects of {@code decoded} into the running biome. Main thread only. */
    void apply(NamespacedKey biome, DecodedBiome decoded) throws BiomeTuningException;

    /** Queues vanilla's full registry sync on a reconfiguring connection; call before completeReconfiguration(). */
    void injectRegistrySync(PlayerConfigurationConnection connection) throws BiomeTuningException;

    /**
     * Switch a player to configuration without Paper's guard: Paper refuses reenterConfiguration() for a player whose
     * login ran PlayerLoginEvent, which it runs whenever a plugin listens to that event. This route was proven on a
     * test server. Only the debug command uses it, until a check on the servers decides the refresh route.
     */
    void switchToConfigurationIgnoringLoginGuard(Player player) throws BiomeTuningException;
}
