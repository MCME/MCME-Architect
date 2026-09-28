package com.mcmiddleearth.architect.mapLayers;

import com.destroystokyo.paper.event.entity.EntityAddToWorldEvent;
import com.destroystokyo.paper.event.entity.EntityRemoveFromWorldEvent;
import com.mcmiddleearth.architect.specialBlockHandling.itemBlock.ItemBlockCount;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.event.world.EntitiesUnloadEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.LongSupplier;
import java.util.function.Predicate;
import java.util.logging.Level;

/**
 * Counts, per chunk, the entities that count toward the item-block limit: when a chunk's entities load or unload,
 * and, in a batch once a second, in loaded chunks where such an entity came or went. An unloaded chunk keeps its last
 * count. Changes are reported at most once a second, since chunks load all the time. The counts the map shows are
 * kept in a file, so they are back after a restart.
 * <p>
 * A move between two loaded chunks, such as a teleport by {@code /tp} or by the {@code /armor} editor, fires no event.
 * Both chunks then stay off by one until they are counted again: as they unload or load, or when an entity that
 * counts comes or goes there.
 */
final class ItemBlockBudget implements Listener {

    /** A chunk, by world name and chunk coordinates. */
    record ChunkKey(String world, int x, int z) {}

    /** What counts in a chunk, and since when (epoch milliseconds). */
    record Count(int armorStands, int itemFrames, int paintings, long changedAt) {
        int total() {
            return armorStands + itemFrames + paintings;
        }
    }

    private static final long RECOUNT_TICKS = 20;
    private static final long SAVE_TICKS = 1200;

    private final Map<ChunkKey, Count> counts = new HashMap<>();
    private final Set<ChunkKey> marked = new LinkedHashSet<>();
    private final File file;
    private final Runnable changed;
    private final LongSupplier clock;
    private Plugin plugin;
    private Predicate<Map.Entry<ChunkKey, Count>> keep;
    private final List<BukkitTask> tasks = new ArrayList<>();
    private boolean unsaved;
    private boolean unreported;

    /** {@code changed} runs, at most once a second, after counts changed; {@code clock} gives epoch milliseconds. */
    ItemBlockBudget(File file, Runnable changed, LongSupplier clock) {
        this.file = file;
        this.changed = changed;
        this.clock = clock;
    }

    /** Starts counting; {@code keep} says which counts are worth keeping in the file. */
    void start(Plugin plugin, Predicate<Map.Entry<ChunkKey, Count>> keep) {
        this.plugin = plugin;
        this.keep = keep;
        load();
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        tasks.add(plugin.getServer().getScheduler().runTaskTimer(plugin, this::everySecond, RECOUNT_TICKS,
                RECOUNT_TICKS));
        tasks.add(plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            if (unsaved) {
                save();
            }
        }, SAVE_TICKS, SAVE_TICKS));
    }

    void stop() {
        if (plugin == null) {
            return;
        }
        HandlerList.unregisterAll(this);
        tasks.forEach(BukkitTask::cancel);
        tasks.clear();
        save();
        plugin = null;
    }

    Map<ChunkKey, Count> counts() {
        return Collections.unmodifiableMap(counts);
    }

    /**
     * Paper 26.2 starts tracking a chunk's entities before this event, whose list holds them all, so the marks those
     * EntityAddToWorldEvents left need no second count. LOWEST, because the list is made before any handler runs: an
     * entity another plugin's handler adds or removes marks the chunk again, after this.
     */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onEntitiesLoad(EntitiesLoadEvent event) {
        count(event.getChunk(), event.getEntities());
        marked.remove(key(event.getChunk()));
    }

    /**
     * Lists the chunk's entities as it stops being accessible, before anything is saved. They are no longer valid by
     * then, so the chunk itself no longer lists them.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onEntitiesUnload(EntitiesUnloadEvent event) {
        count(event.getChunk(), event.getEntities());
        marked.remove(key(event.getChunk()));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onEntityAdded(EntityAddToWorldEvent event) {
        mark(event.getEntity());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onEntityRemoved(EntityRemoveFromWorldEvent event) {
        mark(event.getEntity());
    }

    private void mark(Entity entity) {
        if (ItemBlockCount.countsTowardLimit(entity)) {
            Location location = entity.getLocation();
            marked.add(new ChunkKey(location.getWorld().getName(), location.getBlockX() >> 4,
                    location.getBlockZ() >> 4));
        }
    }

    /** Recounts marked chunks, then reports any change. Only loaded chunks: entities also leave as a chunk unloads. */
    private void everySecond() {
        for (ChunkKey key : marked) {
            World world = plugin.getServer().getWorld(key.world());
            if (world != null && world.isChunkLoaded(key.x(), key.z())) {
                Chunk chunk = world.getChunkAt(key.x(), key.z());
                count(chunk, Arrays.asList(chunk.getEntities()));
            }
        }
        marked.clear();
        if (unreported) {
            unreported = false;
            changed.run();
        }
    }

    /** Counts every loaded chunk now, for when the map seems off. */
    void recountLoaded() {
        for (World world : plugin.getServer().getWorlds()) {
            for (Chunk chunk : world.getLoadedChunks()) {
                count(chunk, Arrays.asList(chunk.getEntities()));
            }
        }
    }

    private void count(Chunk chunk, List<Entity> entities) {
        int[] kinds = new int[ItemBlockCount.values().length];
        for (Entity entity : entities) {
            ItemBlockCount kind = ItemBlockCount.of(entity);
            if (kind != null) {
                kinds[kind.ordinal()]++;
            }
        }
        ChunkKey key = key(chunk);
        Count old = counts.get(key);
        int armorStands = kinds[ItemBlockCount.ARMOR_STAND.ordinal()];
        int itemFrames = kinds[ItemBlockCount.ITEM_FRAME.ordinal()];
        int paintings = kinds[ItemBlockCount.PAINTING.ordinal()];
        if (armorStands + itemFrames + paintings == 0) {
            if (counts.remove(key) != null) {
                unsaved = true;
                unreported = true;
            }
            return;
        }
        if (old != null && old.armorStands() == armorStands && old.itemFrames() == itemFrames
                && old.paintings() == paintings) {
            return;
        }
        counts.put(key, new Count(armorStands, itemFrames, paintings, clock.getAsLong()));
        unsaved = true;
        unreported = true;
    }

    private static ChunkKey key(Chunk chunk) {
        return new ChunkKey(chunk.getWorld().getName(), chunk.getX(), chunk.getZ());
    }

    /** One line per chunk: world;x;z;armorStands;itemFrames;paintings;changedAt. A bad line is skipped. */
    private void load() {
        counts.clear();
        if (!file.exists()) {
            return;
        }
        for (String line : YamlConfiguration.loadConfiguration(file).getStringList("chunks")) {
            try {
                String[] parts = line.split(";");
                counts.put(new ChunkKey(parts[0], Integer.parseInt(parts[1]), Integer.parseInt(parts[2])),
                        new Count(Integer.parseInt(parts[3]), Integer.parseInt(parts[4]), Integer.parseInt(parts[5]),
                                Long.parseLong(parts[6])));
            } catch (RuntimeException e) {
                plugin.getLogger().warning("Skipping an unreadable line in " + file.getName() + ": " + line);
            }
        }
    }

    /**
     * Writes a temporary file, then moves it over the old one in one step, so a crash mid-write keeps the old file. A
     * save that fails is tried again at the next minute.
     */
    private void save() {
        List<String> lines = new ArrayList<>();
        for (Map.Entry<ChunkKey, Count> entry : counts.entrySet()) {
            if (keep.test(entry)) {
                ChunkKey key = entry.getKey();
                Count count = entry.getValue();
                lines.add(key.world() + ";" + key.x() + ";" + key.z() + ";" + count.armorStands() + ";"
                        + count.itemFrames() + ";" + count.paintings() + ";" + count.changedAt());
            }
        }
        YamlConfiguration config = new YamlConfiguration();
        config.set("chunks", lines);
        File temp = new File(file.getPath() + ".tmp");
        try {
            file.getParentFile().mkdirs();
            config.save(temp);
            try {
                Files.move(temp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
            unsaved = false;
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "Could not save " + file.getAbsolutePath(), e);
        }
    }
}
