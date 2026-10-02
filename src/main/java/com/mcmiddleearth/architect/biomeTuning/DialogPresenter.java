package com.mcmiddleearth.architect.biomeTuning;

import org.bukkit.entity.Player;

/** Shows editor windows. The Paper implementation turns them into Dialogs; tests record them instead. */
public interface DialogPresenter {

    void show(Player player, EditorView view);

    void close(Player player);
}
