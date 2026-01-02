package com.mcmiddleearth.architect.serverResoucePack;

import org.bukkit.event.player.PlayerResourcePackStatusEvent;

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
