/*
 * Copyright (C) 2026 MCME
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.mcmiddleearth.architect.additionalListeners;

import com.mcmiddleearth.architect.additionalCommands.WeSelectCommand;
import com.mcmiddleearth.architect.armorStand.ArmorStandEditorCommand;
import com.mcmiddleearth.architect.bannerEditor.BannerEditorCommand;
import com.mcmiddleearth.architect.voxelStencilEditor.SlCommand;
import java.util.UUID;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * Frees per-player state held in static maps by command classes (which are not themselves
 * listeners) when a player quits, so they don't grow unbounded over long server uptime.
 */
public class PlayerDataCleanupListener implements Listener {

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        WeSelectCommand.removePlayer(uuid);
        SlCommand.removePlayer(uuid);
        ArmorStandEditorCommand.removePlayer(uuid);
        BannerEditorCommand.removePlayer(uuid);
    }
}
