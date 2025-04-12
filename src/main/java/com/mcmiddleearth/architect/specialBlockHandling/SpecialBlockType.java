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
    BLOCK_ON_WATER_CONNECT,//("placed on top of clicked water block"),
    BLOCK_CONNECT, //connects to other blocks like vanilla
    DIAGONAL_CONNECT, //connects to diagonally adjacent blocks made e.g. for branches
    BRANCH, //branch system with connection horizontal, diagonal and steep branches
    BRANCH_HORIZONTAL,
    BRANCH_DIAGONAL,
    BRANCH_STEEP,
    BRANCH_CONNECT,
    BRANCH_TRUNK_CONNECT,
    BRANCH_TRUNK_CONNECT_COMPLEX,
    BRANCH_TWIGS,
    BRANCH_TWIGS_EIGHT_DIRECTIONS,
    BRANCH_TWIGS_UPPER, //deprecated
    BRANCH_TWIGS_LOWER, //deprecated
    BRANCH_TWIGS_UPPER_EIGHT_DIRECTIONS, //deprecated
    BRANCH_TWIGS_LOWER_EIGHT_DIRECTIONS, //deprecated
    OPEN_HALF_DOOR, //("open half doors")
    FOUR_DIRECTIONS,//("fourFaced"),
    FOUR_DIRECTIONS_COMPLEX,//("fourFaced") with additional block configuration file for stick editor
    MATCH_ORIENTATION,//("fourFaced"),
    FIVE_FACES     ,//("sixFaces"),
    SIX_FACES     ,//("sixFaces"),
    EIGHT_FACES,
    SIX_FACES_XZ,
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
    UPSHIFT,
    VANILLA,
    MULTI_FACE,
    DOOR_VANILLA,
    DOOR_THREE_BLOCKS,
    DOOR_FOUR_BLOCKS,
    ITEM_FRAME,
    SIGN,
    SIGN_POST,
    SIGN_WALL,
    NONE//("threeAxis");
    
}
