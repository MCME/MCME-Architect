/*
 * Copyright (C) 2018 MCME
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

import java.util.Comparator;
import org.bukkit.DyeColor;
import org.bukkit.Material;

/**
 *
 * @author Eriol_Eandur
 * @param <Material>
 */
public class MaterialComparator<T> implements Comparator<T> {

    @Override
    public int compare(T o1, T o2) {
        //int id1 = mat1.getId();
        //int id2 = mat2.getId();
        if(!((o1 instanceof Material) && (o2 instanceof Material))) {
            return 0;
        }
        Material mat1 = (Material) o1;
        Material mat2 = (Material) o2;
        String[] name1 = reverse(mat1.name().split("_"));
        String[] name2 = reverse(mat2.name().split("_"));
        if(mat1.isOccluding() ^ mat2.isOccluding()) {
            return (mat1.isOccluding()?-1:1);
        }
        if(mat1.isSolid() ^ mat2.isSolid()) {
            return (mat1.isSolid()?-1:1);
        }
        for(int i=0; i<name1.length && i<name2.length;i++) {
            if(isColor(name1[i]) ^ isColor(name2[i])) {
                return (isColor(name1[i])?-1:1);
            }
            int compare = name1[i].compareTo(name2[i]);
            if(compare == 0) {
                continue;
            }
            return compare;
        }
        return (name1.length<name2.length?1:-1);
    }
    
    private static boolean isColor(String name) {
        for(DyeColor color: DyeColor.values()) {
            if(color.name().equalsIgnoreCase(name)) {
                return true;
            }
        }
        return false;
    }
    
    private static String[] reverse(String[] words) {
        String[] result = new String[words.length];
        for(int i=0; i<words.length;i++) {
            result[i] = words[words.length-1-i];
        }
        return result;
    }
        
    public static boolean isSimilar(Material mat1, Material mat2) {
        String[] name1 = reverse(mat1.name().split("_"));
        String[] name2 = reverse(mat2.name().split("_"));
        return name1[0].equals(name2[0]);
    }
    
}
