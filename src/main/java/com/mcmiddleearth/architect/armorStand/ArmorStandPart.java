/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.mcmiddleearth.architect.armorStand;

/**
 *
 * @author Eriol_Eandur
 */
public enum ArmorStandPart {
    HEAD        ("h"),
    LARM        ("la"),
    RARM        ("ra"),
    LLEG        ("ll"),
    RLEG        ("rl"),
    BODY        ("b"),
    ALL         ("all");
    
    private final String name;

    ArmorStandPart(String name) {
        this.name = name;
    }
    
    public static ArmorStandPart getPart(String name) {
        for(ArmorStandPart type: ArmorStandPart.values()) {
            if(name.toLowerCase().startsWith(type.name)) {
                return type;
            }
        }
        return null;
    }

    public String getPartName() {
        switch(this) {
            case HEAD:
                return "head";
            case BODY:
                return "body";
            case LARM:
                return "left arm";
            case RARM:
                return "right arm";
            case LLEG:
                return "left leg";
            case RLEG:
                return "right leg";
            case ALL:
                return "all";
        }
        return "";
    }

}
