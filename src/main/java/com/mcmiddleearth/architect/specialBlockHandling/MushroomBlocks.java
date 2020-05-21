/*
 * Copyright (C) 2016 MCME
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
package com.mcmiddleearth.architect.specialBlockHandling;

/**
 *
 * @author Eriol_Eandur
 */
@Deprecated
public enum MushroomBlocks {
    
    INSIDE  ("inside", (byte) 0),
    NWT     ("skin N W T", (byte) 1),
    NT      ("skin N T", (byte) 2),
    NET     ("skin N E T", (byte) 3),
    WT       ("skin W T", (byte) 4),
    T       ("skin T", (byte) 5),
    ET       ("skin E T", (byte) 6),
    SWT     ("skin S W T", (byte) 7),
    ST      ("skin S T", (byte) 8),
    SET     ("skin S E T", (byte) 9),
    STEM    ("stem", (byte) 10),
    ALL     ("skin all", (byte) 14),
    STEM_ALL("stem all", (byte) 15);
    
    private final String displayName;
    
    private final byte dataValue;

    MushroomBlocks(String key, byte dataValue) {
        this.displayName = key;
        this.dataValue = dataValue;
    }
    
    @Deprecated
    public static byte getDataValue(String displayName) {
        for(MushroomBlocks search: MushroomBlocks.values()) {
            if(search.displayName.equalsIgnoreCase(displayName)) {
                return search.dataValue;
            }
        }
        return 0;
    }

    public String getDisplayName() {
        return displayName;
    }
}
