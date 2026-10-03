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
package com.mcmiddleearth.architect.util;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import com.mcmiddleearth.architect.PluginData;

/**
 * Runtime guard for WorldEdit-selection features. No 26.x WorldEdit build
 * exists upstream yet, so WorldEdit may be absent on a 26.2 server; the
 * WEUtil selection calls would otherwise fail. Guard call sites so these
 * features degrade gracefully instead of throwing.
 */
public final class WorldEditGuard {
    private WorldEditGuard() {}

    public static boolean isAvailable() {
        return Bukkit.getPluginManager().getPlugin("WorldEdit") != null;
    }

    /** Returns true if WE is available; otherwise messages the sender and returns false. */
    public static boolean require(CommandSender cs) {
        if (isAvailable()) return true;
        PluginData.getMessageUtil().sendErrorMessage(cs,
            "This feature needs WorldEdit, which isn't available on this server version yet.");
        return false;
    }
}
