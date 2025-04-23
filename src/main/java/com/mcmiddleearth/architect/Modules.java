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
package com.mcmiddleearth.architect;

/**
 *
 * @author Eriol_Eandur
 */
public enum Modules {
    
    REDSTONE_TORCH         ("modules.specialBlocks.redstoneTorch"),
    HALF_DOORS             ("modules.specialBlocks.halfDoors"),
    HALF_BEDS              ("modules.specialBlocks.halfBeds"),
    DOUBLE_SLABS           ("modules.specialBlocks.doubleSlabs"),
    PLANTS                 ("modules.specialBlocks.plants"),
    PISTON_EXTENSIONS      ("modules.specialBlocks.pistonExtenstions"),
    SIX_SIDED_LOGS         ("modules.specialBlocks.sixSidedLogs"),
    DRAGON_EGG             ("modules.specialBlocks.dragonEgg"),
    BURNING_FURNACE        ("modules.specialBlocks.burningFurnace"),
    INVENTORY_ACCESS       ("modules.specialBlocks.inventoryAccess"),
    SPECIAL_BLOCKS_PLACE   ("modules.specialBlocks.place"),
    SPECIAL_BLOCKS_FLINT   ("modules.specialBlocks.flint"),
    USE_POWERED_DOORS      ("modules.specialBlocks.poweredDoors"),
    BLOCK_PLAYER_INTERACTION ("modules.specialBlocks.blockPlayerInteraction"),
    PLAYER_SURVIVAL_FLY    ("modules.environment.playerSurvivalFly"),
    PLAYER_DAMAGE_BLOCKING ("modules.environment.playerDamageBlocking"),
    ANIMAL_SPAWN_BLOCKING  ("modules.environment.animalSpawnBlocking"),
    DROP_BLOCKING          ("modules.environment.dropBlocking"),
    MONSTER_SPAWN_BLOCKING ("modules.environment.monsterSpawnBlocking"),
    FIRE_SPREAD_BLOCKING   ("modules.environment.fireSpreadBlocking"),
    WEATHER_BLOCKING       ("modules.environment.weatherBlocking"),
    DECAY_BLOCKING         ("modules.environment.decayBlocking"),
    BLOCK_FORM_BLOCKING    ("modules.environment.formBlocking"),
    BLOCK_FADE_BLOCKING    ("modules.environment.fadeBlocking"),
    NO_PHYSICS_LIST_ENABLED("modules.environment.noPhysicsListEnabled"),
    NO_PHYSICS_LIST_INVERTED("modules.environment.noPhysicsListInverted"),
    NO_PHYSICS_CONNECT_STAIRS("modules.environment.noPhysicsConnectStairs"),
    NO_PHYSICS_CONNECT_FENCES("modules.environment.noPhysicsConnectFences"),
    NO_PHYSICS_CONNECT_WALLS("modules.environment.noPhysicsConnectWalls"),
    NO_PHYSICS_CONNECT_CHORUS("modules.environment.noPhysicsConnectChorus"),
    NO_PHYSICS_CONNECT_CHESTS("modules.environment.noPhysicsConnectChests"),
    NO_PHYSICS_CONNECT_GLASS("modules.environment.noPhysicsConnectGlass"),
    NO_PHYSICS_CONNECT_REDSTONE_WIRE("modules.environment.noPhysicsConnectRedstoneWire"),
    DRAIN_WATERLOGGED_DOUBLE_SLABS ("modules.environment.drainWaterloggedDoubleSlabs"),
    
    SIGN_EDITOR            ("modules.command.signEditor"),
    CUSTOM_HEAD_MANAGER    ("modules.command.customHeadManager"),
    RESOURCE_PACK_SWITCHER ("modules.command.resourcePackSwitcher"),
    FULL_BRIGHTNESS        ("modules.command.fullBrightness"),
    SPECIAL_BLOCKS_GET     ("modules.command.getSpecialBlocks"),
    ITEM_TEXTURES          ("modules.commmand.itemTexture"),
    WE_SCHEMATICS_VIEWER   ("modules.command.weSchemViewer"),
    VOXEL_VIEWER           ("modules.command.voxelViewer"),
    STENCIL_LIST_EDITOR    ("modules.command.stencilListEditor"),
    BANNER_EDITOR          ("modules.command.bannerEditor"),
    PAINTING_EDITOR        ("modules.command.paintingEditor"),
    CYCLE_BLOCKS           ("modules.command.cycleBlocks"),
    ARMOR_STAND_EDITOR     ("modules.command.armorStandEditor"),
    ARMOR_STAND_ROLLBACK   ("modules.command.armorStandRollback"),
    RANDOMISER             ("modules.command.randomiser"),
    COPY_PASTE             ("modules.command.copypaste"),
    VIEW_DISTANCE          ("modules.command.viewdistance"),

    VOXEL_BIOME_BRUSH_FIX  ("modules.voxelBiomeBrushFix"),
    
    CHUNK_UPDATE           ("modules.chunkupdate.manual"),
    CHUNK_UPDATE_AUTO      ("modules.chunkupdate.auto"),
    
    REMOVE_CONTAINER_ITEMS    ("modules.protection.removeContainerItemsOnBreak"),
    BLOCK_OP_ITEMS            ("modules.protection.blockOpItems"),
    BLOCK_ARROW_SHOOTING      ("modules.protection.blockArrowShooting"),
    ARMOR_STAND_PROTECTION    ("modules.protection.armorStand"),
    LILY_PAD_PROTECTION       ("modules.protection.lilyPad"),
    REDSTONE_PROTECTION       ("modules.protection.redstone"),
    CHEST_INVENTORY_PROTECTION ("modules.protection.chestInventory"),
    TNT_PROTECTION            ("modules.protection.tnt"),
    HANGING_ENTITY_PROTECTION ("modules.protection.hangingEntity");


    private final String moduleKey;

    Modules(String key) {
        this.moduleKey = key;
    }

    public String getModuleKey() {
        return moduleKey;
    }
}
