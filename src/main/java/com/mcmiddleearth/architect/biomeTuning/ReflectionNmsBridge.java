package com.mcmiddleearth.architect.biomeTuning;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.JsonOps;
import io.papermc.paper.connection.PlayerConfigurationConnection;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Queue;
import java.util.stream.Stream;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;

/**
 * The only code that touches Minecraft internals. Paper 26.x runs them unobfuscated, so these are plain
 * Mojang names, checked against Paper 26.2 (server classes of build 111, API of build 126).
 * Every handle is resolved once in the constructor; a failure is recorded, never thrown, and
 * {@link #selfCheck()} reports it so biome tuning can switch itself off.
 */
public final class ReflectionNmsBridge implements NmsBridge {

    @FunctionalInterface
    private interface Lookup<T> {
        T get() throws ReflectiveOperationException;
    }

    private final List<String> problems = new ArrayList<>();

    private final Method getServer;
    private final Method registryAccess;
    private final Method registries;
    private final Method getResourceManager;
    private final Method lookupOrThrow;
    private final Object biomeRegistryKey;
    private final Method getValue;
    private final Method createResourceKey;
    private final Method identifier;
    private final Method registryOpsCreate;
    private final Codec<Object> biomeCodec;
    private final Field climateSettings;
    private final Field attributes;
    private final Field specialEffects;
    private final Field temperatureCache;
    private final Method listPacks;
    private final Method packLocation;
    private final Method knownPackInfo;
    private final Constructor<?> syncTaskConstructor;
    private final Field configurationTasks;
    private final Field synchronizeRegistriesTask;
    private final Field packetListener;
    private final Method registrationInfo;
    private final Method registrationKnownPack;
    private final Method getWorldPath;
    private final Object datapackDir;

    public ReflectionNmsBridge() {
        Class<?> server = type("net.minecraft.server.MinecraftServer");
        Class<?> resourceKey = type("net.minecraft.resources.ResourceKey");
        Class<?> identifierType = type("net.minecraft.resources.Identifier");
        Class<?> registryAccessType = type("net.minecraft.core.RegistryAccess");
        Class<?> registry = type("net.minecraft.core.Registry");
        Class<?> registriesType = type("net.minecraft.core.registries.Registries");
        Class<?> holderLookupProvider = type("net.minecraft.core.HolderLookup$Provider");
        Class<?> registryOps = type("net.minecraft.resources.RegistryOps");
        Class<?> biome = type("net.minecraft.world.level.biome.Biome");
        Class<?> resourceManager = type("net.minecraft.server.packs.resources.ResourceManager");
        Class<?> packResources = type("net.minecraft.server.packs.PackResources");
        Class<?> packLocationInfo = type("net.minecraft.server.packs.PackLocationInfo");
        Class<?> layeredRegistryAccess = type("net.minecraft.core.LayeredRegistryAccess");
        Class<?> syncTask = type("net.minecraft.server.network.config.SynchronizeRegistriesTask");
        Class<?> configListener = type("net.minecraft.server.network.ServerConfigurationPacketListenerImpl");
        Class<?> paperConnection = type("io.papermc.paper.connection.PaperCommonConnection");
        Class<?> registrationInfoType = type("net.minecraft.core.RegistrationInfo");
        Class<?> levelResource = type("net.minecraft.world.level.storage.LevelResource");

        getServer = method(server, "getServer");
        registryAccess = method(server, "registryAccess");
        registries = method(server, "registries");
        getResourceManager = method(server, "getResourceManager");
        lookupOrThrow = method(registryAccessType, "lookupOrThrow", resourceKey);
        biomeRegistryKey = lookup("Registries.BIOME", registriesType, () -> registriesType.getField("BIOME").get(null));
        getValue = method(registry, "getValue", resourceKey);
        createResourceKey = method(resourceKey, "create", resourceKey, identifierType);
        identifier = method(identifierType, "fromNamespaceAndPath", String.class, String.class);
        registryOpsCreate = method(registryOps, "create", DynamicOps.class, holderLookupProvider);
        biomeCodec = castCodec(lookup("Biome.DIRECT_CODEC", biome, () -> biome.getField("DIRECT_CODEC").get(null)));
        climateSettings = field(biome, "climateSettings");
        attributes = field(biome, "attributes");
        specialEffects = field(biome, "specialEffects");
        temperatureCache = field(biome, "temperatureCache");
        listPacks = method(resourceManager, "listPacks");
        packLocation = method(packResources, "location");
        knownPackInfo = method(packLocationInfo, "knownPackInfo");
        syncTaskConstructor = layeredRegistryAccess == null ? null
                : lookup("SynchronizeRegistriesTask(List, LayeredRegistryAccess)", syncTask,
                        () -> syncTask.getConstructor(List.class, layeredRegistryAccess));
        configurationTasks = field(configListener, "configurationTasks");
        synchronizeRegistriesTask = field(configListener, "synchronizeRegistriesTask");
        packetListener = field(paperConnection, "packetListener");
        registrationInfo = method(registry, "registrationInfo", resourceKey);
        registrationKnownPack = method(registrationInfoType, "knownPackInfo");
        getWorldPath = method(server, "getWorldPath", levelResource);
        datapackDir = lookup("LevelResource.DATAPACK_DIR", levelResource, () -> levelResource.getField("DATAPACK_DIR").get(null));
    }

    @Override
    public List<String> selfCheck() {
        return List.copyOf(problems);
    }

    @Override
    public boolean sentInFull(NamespacedKey biomeKey) throws BiomeTuningException {
        requireReady();
        try {
            Object access = registryAccess.invoke(getServer.invoke(null));
            Object biomes = lookupOrThrow.invoke(access, biomeRegistryKey);
            Object id = identifier.invoke(null, biomeKey.getNamespace(), biomeKey.getKey());
            Optional<?> info = (Optional<?>) registrationInfo.invoke(biomes, createResourceKey.invoke(null, biomeRegistryKey, id));
            if (info.isEmpty()) {
                throw new BiomeTuningException("unknown biome " + biomeKey);
            }
            return ((Optional<?>) registrationKnownPack.invoke(info.get())).isEmpty();
        } catch (ReflectiveOperationException e) {
            throw new BiomeTuningException("could not inspect " + biomeKey + ": " + rootMessage(e), e);
        }
    }

    @Override
    public Path datapacksFolder() throws BiomeTuningException {
        requireReady();
        try {
            return (Path) getWorldPath.invoke(getServer.invoke(null), datapackDir);
        } catch (ReflectiveOperationException e) {
            throw new BiomeTuningException("could not find the datapacks folder: " + rootMessage(e), e);
        }
    }

    @Override
    public JsonObject encode(NamespacedKey biomeKey) throws BiomeTuningException {
        requireReady();
        Object biome = liveBiome(biomeKey);
        DataResult<JsonElement> result = biomeCodec.encodeStart(jsonOps(), biome);
        Optional<JsonElement> json = result.result();
        if (json.isEmpty() || !json.get().isJsonObject()) {
            throw new BiomeTuningException("could not encode " + biomeKey + ": " + errorMessage(result));
        }
        return json.get().getAsJsonObject();
    }

    @Override
    public DecodedBiome decode(JsonObject biomeJson) throws BiomeTuningException {
        requireReady();
        DataResult<Object> result = biomeCodec.parse(jsonOps(), biomeJson);
        Optional<Object> biome = result.result();
        if (biome.isEmpty()) {
            throw new BiomeTuningException("invalid biome: " + errorMessage(result));
        }
        return new DecodedBiome(biome.get());
    }

    @Override
    public void apply(NamespacedKey biomeKey, DecodedBiome decoded) throws BiomeTuningException {
        requireReady();
        Object live = liveBiome(biomeKey);
        Object fresh = decoded.handle();
        try {
            // Biome's fields are private final. Deep reflection may still set them on Java 25 (JEP 500 warns from 26 on).
            climateSettings.set(live, climateSettings.get(fresh));
            attributes.set(live, attributes.get(fresh));
            specialEffects.set(live, specialEffects.get(fresh));
            temperatureCache.set(live, temperatureCache.get(fresh));
        } catch (IllegalAccessException | IllegalArgumentException e) {
            throw new BiomeTuningException("could not update " + biomeKey + ": " + e.getMessage(), e);
        }
    }

    @Override
    public void injectRegistrySync(PlayerConfigurationConnection connection) throws BiomeTuningException {
        requireReady();
        try {
            Object listener = packetListener.get(connection);
            Object server = getServer.invoke(null);
            List<?> packs;
            try (Stream<?> stream = (Stream<?>) listPacks.invoke(getResourceManager.invoke(server))) {
                packs = stream.toList();
            }
            // The same known-packs list Paper's startConfiguration() builds for a first login.
            List<Object> knownPacks = new ArrayList<>();
            for (Object pack : packs) {
                Optional<?> known = (Optional<?>) knownPackInfo.invoke(packLocation.invoke(pack));
                known.ifPresent(knownPacks::add);
            }
            Object task = syncTaskConstructor.newInstance(knownPacks, registries.invoke(server));
            synchronizeRegistriesTask.set(listener, task);
            @SuppressWarnings("unchecked")
            Queue<Object> tasks = (Queue<Object>) configurationTasks.get(listener);
            tasks.add(task);
        } catch (ReflectiveOperationException | ClassCastException | IllegalArgumentException e) {
            throw new BiomeTuningException("could not queue the registry sync: " + rootMessage(e), e);
        }
    }

    @Override
    public void switchToConfigurationIgnoringLoginGuard(Player player) throws BiomeTuningException {
        try {
            Object handle = player.getClass().getMethod("getHandle").invoke(player);
            Object gameListener = handle.getClass().getField("connection").get(handle);
            gameListener.getClass().getMethod("switchToConfig").invoke(gameListener);
        } catch (ReflectiveOperationException | RuntimeException e) {
            throw new BiomeTuningException("could not switch " + player.getName() + " to configuration: " + rootMessage(e), e);
        }
    }

    private void requireReady() throws BiomeTuningException {
        if (!problems.isEmpty()) {
            throw new BiomeTuningException("biome tuning is unavailable on this server version (" + problems.get(0) + ")");
        }
    }

    private Object liveBiome(NamespacedKey biomeKey) throws BiomeTuningException {
        try {
            Object access = registryAccess.invoke(getServer.invoke(null));
            Object biomes = lookupOrThrow.invoke(access, biomeRegistryKey);
            Object id = identifier.invoke(null, biomeKey.getNamespace(), biomeKey.getKey());
            Object key = createResourceKey.invoke(null, biomeRegistryKey, id);
            Object biome = getValue.invoke(biomes, key);
            if (biome == null) {
                throw new BiomeTuningException("unknown biome " + biomeKey);
            }
            return biome;
        } catch (ReflectiveOperationException e) {
            throw new BiomeTuningException("could not look up " + biomeKey + ": " + rootMessage(e), e);
        }
    }

    @SuppressWarnings("unchecked")
    private DynamicOps<JsonElement> jsonOps() throws BiomeTuningException {
        try {
            Object access = registryAccess.invoke(getServer.invoke(null));
            return (DynamicOps<JsonElement>) registryOpsCreate.invoke(null, JsonOps.INSTANCE, access);
        } catch (ReflectiveOperationException e) {
            throw new BiomeTuningException("could not create registry-aware JSON ops: " + rootMessage(e), e);
        }
    }

    private static String errorMessage(DataResult<?> result) {
        return result.error().map(DataResult.Error::message).orElse("unknown codec error");
    }

    private static String rootMessage(Throwable e) {
        Throwable root = e;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        return root.getClass().getSimpleName() + (root.getMessage() == null ? "" : ": " + root.getMessage());
    }

    @SuppressWarnings("unchecked")
    private static Codec<Object> castCodec(Object codec) {
        return (Codec<Object>) codec;
    }

    private Class<?> type(String name) {
        try {
            return Class.forName(name);
        } catch (ClassNotFoundException | LinkageError e) {
            problems.add("missing class " + name);
            return null;
        }
    }

    private Method method(Class<?> owner, String name, Class<?>... parameters) {
        if (owner == null || Arrays.asList(parameters).contains(null)) {
            return null; // the missing class is already reported
        }
        return lookup(owner.getSimpleName() + "#" + name, owner, () -> owner.getMethod(name, parameters));
    }

    private Field field(Class<?> owner, String name) {
        return lookup(owner == null ? name : owner.getSimpleName() + "." + name, owner, () -> {
            Field field = owner.getDeclaredField(name);
            field.setAccessible(true);
            return field;
        });
    }

    private <T> T lookup(String what, Class<?> owner, Lookup<T> lookup) {
        if (owner == null) {
            return null; // the missing class is already reported
        }
        try {
            return lookup.get();
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            problems.add("missing " + what + " (" + e.getClass().getSimpleName() + ")");
            return null;
        }
    }
}
