/*
 * Copyright (C) 2018 Eriol_Eandur
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

import java.io.Serializable;

/**
 *
 * @author Eriol_Eandur
 */
public class RpPlayerData implements Serializable {
    
    private final long serialVerionsUID = 1;

    private boolean autoRp = true;
    private String variant = "light";
    private String client = "vanilla";
    private int resolution = 16;
    private transient RpRegion currentRegion = null;
    private String currentRpUrl = null;
    private transient PlayerResourcePackStatusEvent.Status currentRpStatus
            = PlayerResourcePackStatusEvent.Status.DECLINED;

    private transient int protocolVersion;

    public boolean isAutoRp() {
        return autoRp;
    }

    public void setAutoRp(boolean autoRp) {
        this.autoRp = autoRp;
    }

    public String getVariant() {
        return variant;
    }

    public void setVariant(String variant) {
        this.variant = variant;
    }

    public String getClient() {
        return client;
    }

    public void setClient(String client) {
        this.client = client;
    }

    public int getResolution() {
        return resolution;
    }

    public void setResolution(int resolution) {
        this.resolution = resolution;
    }

    public RpRegion getCurrentRegion() {
        return currentRegion;
    }

    public void setCurrentRegion(RpRegion currentRegion) {
        this.currentRegion = currentRegion;
    }

    public String getCurrentRpUrl() {
        return currentRpUrl;
    }

    public void setCurrentRpUrl(String currentRpUrl) {
        this.currentRpUrl = currentRpUrl;
    }

    public PlayerResourcePackStatusEvent.Status getCurrentRpStatus() {
        return currentRpStatus;
    }

    public void setCurrentRpStatus(PlayerResourcePackStatusEvent.Status currentRpStatus) {
        this.currentRpStatus = currentRpStatus;
    }

    public void setProtocolVersion(int protocolVersion) {
        this.protocolVersion = protocolVersion;
    }

    public int getProtocolVersion() {
        return protocolVersion;
    }
}
