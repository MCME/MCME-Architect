/*
 * Copyright (C) 2019 MCME
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
package com.mcmiddleearth.architect.watcher;

import org.bukkit.event.HandlerList;

import java.util.HashSet;
import java.util.Set;

/**
 *
 * @author Eriol_Eandur
 */
public class WatcherEvent extends org.bukkit.event.Event {
    
    private static final HandlerList handlers = new HandlerList();
  
    private Set<String> confirmations = new HashSet<>();
    
    public WatcherEvent() {
        
    }
    
    @Override
    public HandlerList getHandlers() {
        return handlers;
    }

    public static HandlerList getHandlerList() {
        return handlers;
    }
    
    public void confirm(String confirmation) {
        confirmations.add(confirmation);
    }

    public Set<String> getConfirmations() {
        return confirmations;
    }
}
