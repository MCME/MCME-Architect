/*
 * Copyright (C) 2019 Eriol_Eandur
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
package com.mcmiddleearth.architect.blockData;

/**
 *
 * @author Eriol_Eandur
 */
public class LegacyBlockData {
    
    private final int id;
    
    private final byte data;
    
    LegacyBlockData(int id, byte data) {
        this.id = id;
        this.data = data;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof LegacyBlockData && ((LegacyBlockData)o).getId()==id
                &&((LegacyBlockData)o).getData()==data;
    }

    @Override
    public int hashCode() {
        int hash = 5;
        hash = 29 * hash + this.id;
        hash = 29 * hash + this.data;
        return hash;
    }
    
    @Override
    public String toString() {
        return ""+id+":"+data;
    }

    public int getId() {
        return id;
    }

    public byte getData() {
        return data;
    }
}
