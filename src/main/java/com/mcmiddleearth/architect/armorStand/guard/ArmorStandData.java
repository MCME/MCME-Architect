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
package com.mcmiddleearth.architect.armorStand.guard;

import com.mcmiddleearth.architect.armorStand.ArmorStandPart;
import java.io.FileWriter;
import java.util.Scanner;
import java.util.UUID;
import org.bukkit.Location;
import org.bukkit.entity.ArmorStand;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.EulerAngle;

/**
 *
 * @author Eriol_Eandur
 */
public class ArmorStandData {
    
    private final static int visible=0, marker=1, base=2, arms=3, gravity=4, small=5;
    
    private boolean[] flags = new boolean[6];
    
    private EulerAngle[] poses = new EulerAngle[ArmorStandPart.values().length];
    
    private static final int helmet=0, plate = 1, hand = 2, leggins = 3, boots = 4;
    
    private ItemStack[] items = new ItemStack[5];
    
    private Double turn;
    
    private Location loc;
    
    private UUID uuid;
    
    public ArmorStandData(ArmorStand armor) {
        
    }
    
    public static ArmorStandData fromFile(Scanner scanner) {
        return null;
    }
    
    public void writeToFile(FileWriter fw) {
        
    }
    
    public void placeInWorld() {
        
    }
}
