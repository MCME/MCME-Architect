/*
 * Copyright (C) 2017 MCME
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
package com.mcmiddleearth.architect.specialBlockHandling.data;

import com.mcmiddleearth.pluginutil.LegacyMaterialUtil;
import com.mcmiddleearth.pluginutil.NumericUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.data.BlockData;

/**
 *
 * @author Eriol_Eandur
 */
@Deprecated
public class BlockRawData {
    
    @Deprecated
    int id=0;
    
    @Deprecated
    byte dv=0;
    
    @Deprecated
    public BlockRawData(String input) {
        int separatorPos = input.indexOf(":");
        if(separatorPos<0) {
            if(NumericUtil.isInt(input)) {
                id = NumericUtil.getInt(input);
                dv = -1;
            }
        } else {
            String idInput = input.substring(0,separatorPos);
            id = NumericUtil.getInt(idInput);
            if(input.length()<=separatorPos+1) {
                dv = -1;
            } else {
                String dvInput =  input.substring(separatorPos+1,input.length());
                dv = (byte) NumericUtil.getInt(dvInput);
            }
        }
    }
    
    @Deprecated
    public boolean allDV() {
        return dv==-1;
    }
    
    @Deprecated
    public byte getDV() {
        return dv<0 ? 0 : dv;
    }
    
    @Deprecated
    public int getId() {
        return id<0 ? 0 : id;
    }
    
    public BlockData getBlockData() {
        Material material = LegacyMaterialUtil.getMaterial(getId());
        if(allDV()) {
            return Bukkit.getServer().createBlockData(material.createBlockData().getAsString());
        } else {
            return LegacyMaterialUtil.getBlockData(material,getDV());
        }
    }

    public void setId(int id) {
        this.id = id;
    }

    public void setDv(byte dv) {
        this.dv = dv;
    }
}
