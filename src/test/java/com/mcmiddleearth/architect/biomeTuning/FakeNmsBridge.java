package com.mcmiddleearth.architect.biomeTuning;

import com.google.gson.JsonObject;
import io.papermc.paper.connection.PlayerConfigurationConnection;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;

/**
 * A bridge without Minecraft: decode() returns the JSON itself, apply() records it per biome, encode() serves
 * prepared JSON, and vanilla biomes and codec rejections can be arranged.
 */
final class FakeNmsBridge implements NmsBridge {

    final Map<NamespacedKey, JsonObject> applied = new HashMap<>();
    final Map<NamespacedKey, JsonObject> encoded = new HashMap<>();
    final Set<NamespacedKey> vanilla = new HashSet<>();
    String rejectContaining;
    Path datapacks;
    /** Thrown by selfCheck: a server whose internals changed in a way the check did not foresee. */
    Error selfCheckFailure;

    @Override
    public List<String> selfCheck() {
        if (selfCheckFailure != null) {
            throw selfCheckFailure;
        }
        return List.of();
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
        throw new AssertionError("not used in these tests");
    }

    @Override
    public void switchToConfigurationIgnoringLoginGuard(Player player) {
        throw new AssertionError("not used in these tests");
    }
}
