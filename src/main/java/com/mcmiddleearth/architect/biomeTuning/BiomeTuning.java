package com.mcmiddleearth.architect.biomeTuning;

import com.mcmiddleearth.architect.PluginData;
import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.stream.Stream;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.event.HandlerList;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

/**
 * Wiring for live biome tuning: the bridge and its self-check, settings, the service, the spare pool, the refresh
 * coordinator and its timer, and the editor.
 */
public final class BiomeTuning {

    private static final List<String> NOT_ENABLED = List.of("biome tuning is not enabled");

    private static JavaPlugin plugin;
    private static NmsBridge bridge;
    private static BiomeTuningSettings settings = BiomeTuningSettings.DEFAULTS;
    private static BiomeTuningService service;
    private static SparePool spares;
    private static RefreshCoordinator refresher;
    private static BiomeEditor editor;
    private static BukkitTask timer;
    private static LegacyLoginProbe legacyLoginProbe;
    private static List<String> problems = NOT_ENABLED;

    private BiomeTuning() {
    }

    public static void enable(JavaPlugin owner) {
        enable(owner, ReflectionNmsBridge::new);
    }

    static void enable(JavaPlugin owner, NmsBridge nmsBridge) {
        enable(owner, () -> nmsBridge);
    }

    /**
     * The bridge is built inside the guard: the reflection bridge's constructor does all its lookups. The datapacks
     * folder comes from the server itself and is asked for when first needed: Architect loads at STARTUP, before any
     * world exists, and a 26.x World path is its dimension folder, not the level root.
     */
    static void enable(JavaPlugin owner, Supplier<NmsBridge> bridges) {
        guarded(owner, () -> {
            NmsBridge nmsBridge = bridges.get();
            start(owner, nmsBridge, nmsBridge::datapacksFolder, BiomeTuning::registeredBiomes);
        });
    }

    static void enable(JavaPlugin owner, NmsBridge nmsBridge, Path datapacksDir) {
        enable(owner, nmsBridge, datapacksDir, BiomeTuning::registeredBiomes);
    }

    /** As above, with the biome ids the server knows given by the caller: a test server loads no datapack biomes. */
    static void enable(JavaPlugin owner, NmsBridge nmsBridge, Path datapacksDir,
                       Supplier<Stream<NamespacedKey>> knownBiomes) {
        guarded(owner, () -> start(owner, nmsBridge, () -> datapacksDir, knownBiomes));
    }

    /**
     * A RuntimeException or LinkageError while starting (server internals that changed in a way the self-check did
     * not foresee) switches tuning off and says why, once. Architect's own onEnable must never see it: Paper would
     * then disable all of Architect. Other errors, such as running out of memory, pass through on purpose.
     */
    private static void guarded(JavaPlugin owner, Runnable startup) {
        try {
            startup.run();
        } catch (RuntimeException | LinkageError e) {
            owner.getLogger().log(Level.WARNING, "Biome tuning could not start on " + owner.getServer().getVersion()
                    + ", so it is off", e);
            disable();
            problems = List.of("it could not start: " + reason(e));
        }
    }

    /**
     * The innermost cause's type and message: a wrapper such as ExceptionInInitializerError hides the reason. It is
     * kept here, apart from ReflectionNmsBridge's own copy: the bridge may be what failed, and a class that failed to
     * initialize throws again when touched, so the guard's catch must never call into it. At most 16 causes deep, in
     * case a chain of causes loops.
     */
    static String reason(Throwable e) {
        Throwable root = e;
        for (int depth = 0; depth < 16 && root.getCause() != null && root.getCause() != root; depth++) {
            root = root.getCause();
        }
        return root.getClass().getSimpleName() + (root.getMessage() == null ? "" : ": " + root.getMessage());
    }

    private static void start(JavaPlugin owner, NmsBridge nmsBridge, BiomeTuningService.DatapacksFolder datapacksDir,
                              Supplier<Stream<NamespacedKey>> knownBiomes) {
        plugin = owner;
        bridge = nmsBridge;
        problems = nmsBridge.selfCheck();
        if (!problems.isEmpty()) {
            owner.getLogger().warning("Biome tuning is unavailable on " + owner.getServer().getVersion() + ": "
                    + String.join("; ", problems));
            return;
        }
        settings = BiomeTuningSettings.from(owner.getConfig().getConfigurationSection("biomeTuning"),
                owner.getLogger()::warning);
        service = new BiomeTuningService(nmsBridge, datapacksDir,
                owner.getDataFolder().toPath().resolve("biomeTuning").resolve("backups"), settings, owner.getLogger());
        spares = new SparePool(settings.spares(), settings.targetPack(), knownBiomes, datapacksDir,
                new BiomeLabels(datapacksDir, settings.targetPack()), service, Instant::now, owner.getLogger());
        service.lock(spares::lockedBecause);
        refresher = new RefreshCoordinator(nmsBridge, System::currentTimeMillis, owner.getLogger(),
                RefreshCoordinator::reenter, settings);
        owner.getServer().getPluginManager().registerEvents(refresher, owner);
        // the editor only measures durations, as Paper does for callback lifetimes, so it takes a monotonic clock
        editor = new BiomeEditor(service, spares, refresher, new PaperDialogPresenter(),
                () -> System.nanoTime() / 1_000_000, settings, owner.getLogger(), PluginData.getMessageUtil());
        owner.getServer().getPluginManager().registerEvents(editor, owner);
        timer = owner.getServer().getScheduler().runTaskTimer(owner, () -> {
            refresher.expireStale();
            refresher.drainPublishQueue(Bukkit::getPlayer);
            editor.closeExpired();
        }, 20L, 20L);
        owner.getLogger().info("Biome tuning ready.");
    }

    public static void disable() {
        setLegacyLoginProbe(false);
        if (timer != null) {
            timer.cancel();
            timer = null;
        }
        if (service != null && plugin != null && !service.unsaved().isEmpty()) {
            plugin.getLogger().warning("Biome tuning: unsaved live edits, which revert at restart: " + service.unsaved());
        }
        if (editor != null) {
            HandlerList.unregisterAll(editor);
            editor.shutdown();
            editor = null;
        }
        if (refresher != null) {
            HandlerList.unregisterAll(refresher);
            refresher.shutdown();
            refresher = null;
        }
        spares = null;
        service = null;
        bridge = null;
        problems = NOT_ENABLED;
    }

    public static boolean isAvailable() {
        return bridge != null && problems.isEmpty();
    }

    public static List<String> problems() {
        return problems;
    }

    public static NmsBridge bridge() {
        return bridge;
    }

    public static BiomeTuningService service() {
        return service;
    }

    public static SparePool spares() {
        return spares;
    }

    public static RefreshCoordinator refresher() {
        return refresher;
    }

    public static BiomeEditor editor() {
        return editor;
    }

    public static BiomeTuningSettings settings() {
        return settings;
    }

    /** The biome ids the server knows now: vanilla's, and every biome a datapack defined at startup. */
    private static Stream<NamespacedKey> registeredBiomes() {
        return RegistryAccess.registryAccess().getRegistry(RegistryKey.BIOME).keyStream();
    }

    /** True while a biome refresh has the player out of the world, so other listeners can skip their quit/join work. */
    public static boolean isRefreshing(UUID player) {
        return refresher != null && refresher.isRefreshing(player);
    }

    /** The debug probe: register or remove a do-nothing PlayerLoginEvent listener. Returns the new state. */
    public static boolean setLegacyLoginProbe(boolean on) {
        if (on && legacyLoginProbe == null && plugin != null) {
            legacyLoginProbe = new LegacyLoginProbe(plugin.getLogger());
            plugin.getServer().getPluginManager().registerEvents(legacyLoginProbe, plugin);
        } else if (!on && legacyLoginProbe != null) {
            HandlerList.unregisterAll(legacyLoginProbe);
            legacyLoginProbe = null;
        }
        return legacyLoginProbe != null;
    }
}
