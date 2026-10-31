package com.mcmiddleearth.architect.biomeTuning;

import com.google.gson.JsonObject;
import io.papermc.paper.connection.PlayerConfigurationConnection;
import io.papermc.paper.connection.PlayerConnection;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;

/**
 * A bridge without Minecraft: decode() returns the JSON itself, apply() records it per biome, encode() serves
 * prepared JSON, and vanilla biomes and codec rejections can be arranged. The refresh steps (switching past Paper's
 * guard, replaying a login, queueing the sync) are recorded in order in {@link #steps}.
 */
final class FakeNmsBridge implements NmsBridge {

    final Map<NamespacedKey, JsonObject> applied = new HashMap<>();
    final Map<NamespacedKey, JsonObject> encoded = new HashMap<>();
    final Set<NamespacedKey> vanilla = new HashSet<>();
    String rejectContaining;
    Path datapacks;
    /** Thrown by selfCheck: a server whose internals changed in a way the check did not foresee. */
    Error selfCheckFailure;
    /** Players whose login ran PlayerLoginEvent, as Paper marks them. */
    final Set<UUID> legacyLogins = new HashSet<>();
    /** What the login replay would lack on this server; empty means it can run. */
    List<String> loginReplayProblems = List.of();
    /** Runs when a player is switched past Paper's guard; tests make the player leave the world, as Paper does. */
    Consumer<Player> onSwitchPastGuard = player -> {
    };
    BiomeTuningException switchFailure;
    /** A plugin disallows the replayed login. */
    boolean replayRefuses;
    BiomeTuningException replayFailure;
    final List<String> steps = new ArrayList<>();

    @Override
    public List<String> selfCheck() {
        if (selfCheckFailure != null) {
            throw selfCheckFailure;
        }
        return List.of();
    }

    @Override
    public List<String> loginReplayCheck() {
        return loginReplayProblems;
    }

    @Override
    public Path datapacksFolder() throws BiomeTuningException {
        if (datapacks == null) {
            throw new BiomeTuningException("no datapacks folder arranged");
        }
        return datapacks;
    }

    @Override
    public boolean sentInFull(NamespacedKey biome) {
        return !vanilla.contains(biome);
    }

    @Override
    public JsonObject encode(NamespacedKey biome) throws BiomeTuningException {
        JsonObject json = encoded.get(biome);
        if (json == null) {
            throw new BiomeTuningException("unknown biome " + biome);
        }
        return json.deepCopy();
    }

    @Override
    public DecodedBiome decode(JsonObject biomeJson) throws BiomeTuningException {
        if (rejectContaining != null && biomeJson.toString().contains(rejectContaining)) {
            throw new BiomeTuningException("invalid biome: the codec says no");
        }
        return new DecodedBiome(biomeJson.deepCopy());
    }

    @Override
    public void apply(NamespacedKey biome, DecodedBiome decoded) {
        applied.put(biome, (JsonObject) decoded.handle());
    }

    @Override
    public void injectRegistrySync(PlayerConfigurationConnection connection) {
        steps.add("sync " + connection.getProfile().getName());
    }

    @Override
    public boolean loggedInWithLegacyEvent(Player player) {
        return legacyLogins.contains(player.getUniqueId());
    }

    @Override
    public PlayerConnection switchToConfigurationIgnoringLoginGuard(Player player) throws BiomeTuningException {
        if (switchFailure != null) {
            throw switchFailure;
        }
        steps.add("switch " + player.getName());
        onSwitchPastGuard.accept(player);
        return null;
    }

    @Override
    public boolean replayLegacyLogin(PlayerConfigurationConnection connection) throws BiomeTuningException {
        if (replayFailure != null) {
            throw replayFailure;
        }
        steps.add("replay " + connection.getProfile().getName() + (replayRefuses ? " (refused)" : ""));
        return !replayRefuses;
    }
}
