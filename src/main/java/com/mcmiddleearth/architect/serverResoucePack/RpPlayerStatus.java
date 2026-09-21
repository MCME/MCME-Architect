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
package com.mcmiddleearth.architect.serverResoucePack;

import org.bukkit.event.player.PlayerResourcePackStatusEvent;

/**
 * Resource pack state for a player, as Architect tracks it.
 * <p>
 * This is Bukkit's {@link PlayerResourcePackStatusEvent.Status} plus the two server-side states
 * Bukkit cannot express, because that event only reports what the <em>client</em> answered:
 * <ul>
 *   <li>{@link #NOT_SENT} - no pack has been offered to this player yet.</li>
 *   <li>{@link #SENT} - a pack has been offered and no client reply has arrived.</li>
 * </ul>
 * Without those two, "the pack is still on its way" is indistinguishable from "the client
 * rejected it", and the previous status is lost the moment a new pack is sent. MCME-Introduction
 * relies on exactly that distinction: its intro rooms pair {@code SENT} with
 * {@link RpPlayerData#getLastRpStatus()} to tell "already loaded, just re-sent" from
 * "still loading".
 *
 * @author Eriol_Eandur
 */
public enum RpPlayerStatus {
    NOT_SENT,
    SENT,
    DECLINED,
    ACCEPTED,
    DISCARDED,
    DOWNLOADED,
    FAILED_DOWNLOAD,
    FAILED_RELOAD,
    INVALID_URL,
    SUCCESSFULLY_LOADED;

    /**
     * Maps a Bukkit client-reply status onto its identically named counterpart here.
     * <p>
     * The switch is deliberately exhaustive with no {@code default}: if Bukkit adds a status,
     * this stops compiling rather than silently mapping the new value to something wrong.
     *
     * @param status the status reported by {@link PlayerResourcePackStatusEvent}
     * @return the matching {@code RpPlayerStatus}
     */
    public static RpPlayerStatus forPlayerResourcePackStatusEvent(PlayerResourcePackStatusEvent.Status status) {
        return switch (status) {
            case DECLINED -> DECLINED;
            case ACCEPTED -> ACCEPTED;
            case FAILED_DOWNLOAD -> FAILED_DOWNLOAD;
            case SUCCESSFULLY_LOADED -> SUCCESSFULLY_LOADED;
            case DOWNLOADED -> DOWNLOADED;
            case INVALID_URL -> INVALID_URL;
            case FAILED_RELOAD -> FAILED_RELOAD;
            case DISCARDED -> DISCARDED;
        };
    }
}
