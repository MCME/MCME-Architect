package com.mcmiddleearth.architect.biomeTuning;

import com.google.gson.JsonObject;
import io.papermc.paper.connection.PlayerConfigurationConnection;
import io.papermc.paper.connection.PlayerConnection;
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

    /**
     * Problems with the internals the login replay needs; empty means it can run. A problem here switches only the
     * replay off: players whose login ran PlayerLoginEvent then take Paper's route, which Paper refuses.
     */
    List<String> loginReplayCheck();

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
     * Whether Paper ran the deprecated PlayerLoginEvent at this player's login, which it does whenever a plugin listens
     * to that event. Paper then refuses reenterConfiguration() for them.
     */
    boolean loggedInWithLegacyEvent(Player player) throws BiomeTuningException;

    /**
     * Switches a player to configuration past Paper's guard, for a player whose login ran PlayerLoginEvent. Their
     * login must then be replayed while they reconfigure (replayLegacyLogin). Returns the connection to watch until
     * the refresh completes.
     */
    PlayerConnection switchToConfigurationIgnoringLoginGuard(Player player) throws BiomeTuningException;

    /**
     * Runs Paper's own legacy login step for a reconfiguring connection, as Paper does at a real login: it builds the
     * player object the reconfiguration spawns and fires PlayerLoginEvent for it, so plugins set that object up. Main
     * thread only, before completeReconfiguration(). If a plugin disallows the login, the connection is disconnected
     * with its message and the result is false.
     */
    boolean replayLegacyLogin(PlayerConfigurationConnection connection) throws BiomeTuningException;
}
