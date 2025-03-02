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
package com.mcmiddleearth.architect.armorStand;

import org.bukkit.Location;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;

import static com.mcmiddleearth.pluginutil.ConfigurationUtil.*;

/**
 *
 * @author Eriol_Eandur
 */
public class ArmorStandUtil {
    
    public static Map<String,Object> serializeEntity(Entity entity) {
        Map<String,Object> result = new HashMap<>();
        Location loc = entity.getLocation();
        result.put("location", serializeLocation(loc));
        return result;
    }
    
    public static Map<String,Object> serializeArmorStand(ArmorStand armor) {
        Map<String,Object> result = serializeEntity(armor);

        Map<String,Object> items = new HashMap<>();
        EntityEquipment equipment = armor.getEquipment();
        items.put("boots", equipment.getBoots().serialize());
        items.put("chestplate", equipment.getChestplate().serialize());
        items.put("helmet", equipment.getHelmet().serialize());
        items.put("leggins", equipment.getLeggings().serialize());
        items.put("hand", equipment.getItemInMainHand().serialize());
        items.put("offhand", equipment.getItemInOffHand().serialize());

        Map<String,Object> pose = new HashMap<>();
        pose.put("body", serializeEulerAngle(armor.getBodyPose()));
        pose.put("leftArm", serializeEulerAngle(armor.getLeftArmPose()));
        pose.put("rightArm", serializeEulerAngle(armor.getRightArmPose()));
        pose.put("leftLeg", serializeEulerAngle(armor.getLeftLegPose()));
        pose.put("rightLeg", serializeEulerAngle(armor.getRightLegPose()));
        pose.put("head", serializeEulerAngle(armor.getHeadPose()));
        pose.put("hand", serializeEulerAngle(armor.getBodyPose()));
        
        result.put("items", items);
        result.put("pose", pose);
        result.put("arms", armor.hasArms());
        result.put("base", armor.hasBasePlate());
        result.put("gravity", armor.hasGravity());
        //result.put("marker", armor.isMarker());
        result.put("small", armor.isSmall());
        result.put("visible", armor.isVisible());
        return result;
    }
    
    public static ArmorStand deserializeArmorStand(Map<String,Object> data) {
        Location loc = deserializeLocation((Map<String,Object>)data.get("location"));
        if(loc == null) {
            return null;
        }
        else {
            ArmorStand armor = (ArmorStand) loc.getWorld().spawnEntity(loc, EntityType.ARMOR_STAND);
            if(armor != null) {
                Map<String,Object> pose = getMap(data,"pose"); //(Map<String,Object>)data.get("pose");
                if(pose!=null) {
                    armor.setBodyPose(deserializeEulerAngle(getMap(pose,"body")));//(Map<String,Object>)pose.get("body")));
                    armor.setLeftLegPose(deserializeEulerAngle(getMap(pose,"leftLeg")));//(Map<String,Object>)pose.get("leftLeg")));
                    armor.setRightLegPose(deserializeEulerAngle(getMap(pose,"rightLeg")));//(Map<String,Object>)pose.get("rightLeg")));
                    armor.setLeftArmPose(deserializeEulerAngle(getMap(pose,"leftArm")));//(Map<String,Object>)pose.get("leftArm")));
                    armor.setRightArmPose(deserializeEulerAngle(getMap(pose,"rightArm")));//(Map<String,Object>)pose.get("rightArm")));
                    armor.setHeadPose(deserializeEulerAngle(getMap(pose,"head")));//(Map<String,Object>)pose.get("head")));
                }

                Map<String,Object> items = getMap(data,"items");//(Map<String,Object>) data.get("items");
                if(items!=null) {
                    EntityEquipment equipment = armor.getEquipment();
                    equipment.setBoots(ItemStack.deserialize(getMap(items,"boots")));//(Map<String,Object>)items.get("boots")));
                    equipment.setLeggings(ItemStack.deserialize(getMap(items,"leggins")));//(Map<String,Object>)items.get("leggins")));
                    equipment.setChestplate(ItemStack.deserialize(getMap(items,"chestplate")));//(Map<String,Object>)items.get("chestplate")));
                    equipment.setHelmet(ItemStack.deserialize(getMap(items,"helmet")));//(Map<String,Object>)items.get("helmet")));
                    equipment.setItemInMainHand(ItemStack.deserialize(getMap(items,"hand")));//(Map<String,Object>)items.get("hand")));
                    if(items.get("offhand")!=null)
                        equipment.setItemInOffHand(ItemStack.deserialize(getMap(items,"offhand")));//(Map<String,Object>)items.get("hand")));
                }
                
                try {
                    armor.setArms((Boolean)data.get("arms"));
                    armor.setBasePlate((Boolean)data.get("base"));
                    armor.setVisible((Boolean)data.get("visible"));
                    //armor.setMarker((boolean)armor.get("marker"));
                    armor.setSmall((Boolean)data.get("small"));
                    armor.setGravity((Boolean)data.get("gravity"));
                }
                catch(NullPointerException e) {}
            }
            return armor;
        }
    }
    
    public static final String LOCKED = "LOCKED";
    
    public static void lockArmorStand(ArmorStand armorStand, boolean locked) {
        if(locked) {
            armorStand.addScoreboardTag(LOCKED);
        } else {
            armorStand.removeScoreboardTag(LOCKED);
        }
    }
    
    public static boolean isLocked(ArmorStand armorStand) {
        return armorStand.getScoreboardTags().contains(LOCKED);
    }

}
