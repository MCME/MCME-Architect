package com.mcmiddleearth.architect.biomeTuning;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Bookkeeping for players whose client is being refreshed. Pure (no Bukkit, no clock), main thread only.
 */
public final class RefreshTracker {

    /** REQUESTED: reenter was called. RECONFIGURING: the client acknowledged and the sync was queued. */
    public enum Stage { REQUESTED, RECONFIGURING }

    public record Entry(UUID player, boolean inject, long startedAt, Stage stage, long reconfiguredAt, boolean leftWorld) {

        Entry withLeftWorld() {
            return new Entry(player, inject, startedAt, stage, reconfiguredAt, true);
        }

        Entry withReconfiguring(long now) {
            return new Entry(player, inject, startedAt, Stage.RECONFIGURING, now, leftWorld);
        }
    }

    private final Map<UUID, Entry> entries = new HashMap<>();

    /** Registers a refresh; false if one is already pending for this player. */
    public boolean begin(UUID player, boolean inject, long now) {
        if (entries.containsKey(player)) {
            return false;
        }
        entries.put(player, new Entry(player, inject, now, Stage.REQUESTED, -1, false));
        return true;
    }

    public boolean isPending(UUID player) {
        return entries.containsKey(player);
    }

    /** Records that the player left the world (their quit event fired); false if no refresh is pending. */
    public boolean markLeftWorld(UUID player) {
        Entry entry = entries.get(player);
        if (entry == null) {
            return false;
        }
        entries.put(player, entry.withLeftWorld());
        return true;
    }

    public boolean hasLeftWorld(UUID player) {
        Entry entry = entries.get(player);
        return entry != null && entry.leftWorld();
    }

    /** The client acknowledged the reconfiguration; the updated entry, or null if the refresh isn't ours. */
    public Entry markReconfiguring(UUID player, long now) {
        Entry entry = entries.get(player);
        if (entry == null) {
            return null;
        }
        Entry updated = entry.withReconfiguring(now);
        entries.put(player, updated);
        return updated;
    }

    /** The player is back in the world; the finished entry, or null. */
    public Entry complete(UUID player) {
        return entries.remove(player);
    }

    public void forget(UUID player) {
        entries.remove(player);
    }

    /** Removes and returns every refresh that started at least {@code timeoutMillis} ago. */
    public List<Entry> expire(long now, long timeoutMillis) {
        List<Entry> expired = new ArrayList<>();
        entries.values().removeIf(entry -> {
            boolean stale = now - entry.startedAt() >= timeoutMillis;
            if (stale) {
                expired.add(entry);
            }
            return stale;
        });
        return expired;
    }
}
