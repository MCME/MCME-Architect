package com.mcmiddleearth.architect.biomeTuning;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.bukkit.entity.Player;

/** Keeps the last window shown to each player instead of showing it, and notes whose window was closed. */
final class RecordingPresenter implements DialogPresenter {

    final Map<UUID, EditorView> shown = new HashMap<>();
    final Set<UUID> closed = new HashSet<>();

    @Override
    public void show(Player player, EditorView view) {
        shown.put(player.getUniqueId(), view);
        closed.remove(player.getUniqueId());
    }

    @Override
    public void close(Player player) {
        shown.remove(player.getUniqueId());
        closed.add(player.getUniqueId());
    }

    EditorView of(Player player) {
        return shown.get(player.getUniqueId());
    }
}
