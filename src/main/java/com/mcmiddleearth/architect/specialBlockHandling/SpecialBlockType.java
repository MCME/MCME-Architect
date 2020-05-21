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
public enum SpecialBlockType {
    
    BLOCK         ,//("normal"),
    BISECTED         ,//("normal"),
    BLOCK_ON_WATER,//("placed on top of clicked water block"),
    OPEN_HALF_DOOR, //("open half doors")
    FOUR_DIRECTIONS,//("fourFaced"),
    MATCH_ORIENTATION,//("fourFaced"),
    FIVE_FACES     ,//("sixFaces"),
    SIX_FACES     ,//("sixFaces"),
    EIGHT_FACES,
    INVALID       ,//("invalid"),
    BURNING_FURNACE,
    THREE_AXIS,
    TWO_AXIS,
    WALL_COMBI,
    ITEM_BLOCK,
    ITEM_BLOCK_TWO_DIRECTIONS,
    ITEM_BLOCK_FOUR_DIRECTIONS,
    MOB_SPAWNER_BLOCK,
    DOOR,
    THIN_WALL,
    DOUBLE_Y_BLOCK,
    VANILLA,
    DOOR_VANILLA,
    DOOR_THREE_BLOCKS,
    DOOR_FOUR_BLOCKS //("threeAxis");
    
}
