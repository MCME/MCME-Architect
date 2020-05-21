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
package com.mcmiddleearth.architect.specialBlockHandling.specialBlocks;

import com.mcmiddleearth.architect.ArchitectPlugin;
import com.mcmiddleearth.architect.specialBlockHandling.SpecialBlockType;
import com.mcmiddleearth.util.DevUtil;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.data.Bisected;
import org.bukkit.block.data.type.Door;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

/**
 *
 * @author Eriol_Eandur
 */
public class SpecialBlockDoorThreeBlocks extends SpecialBlockDoor {
    
    private final Material lowerMaterial, upperMaterial;
    
    private final boolean powered;
    
    private SpecialBlockDoorThreeBlocks(String id, 
                        Material lowerMaterial, Material upperMaterial, 
                        boolean powered) {
        super(id, Material.AIR, (byte) 0, SpecialBlockType.DOOR_THREE_BLOCKS);
        this.upperMaterial = upperMaterial;
        this.lowerMaterial = lowerMaterial;
        this.powered = powered;
    }
    
    public static SpecialBlockDoorThreeBlocks loadFromConfig(ConfigurationSection config, String id) {
        Material material = matchMaterial(config.getString("blockMaterial",""));
        Material lMaterial = matchMaterial(config.getString("lowerMaterial",""));
        Material uMaterial = matchMaterial(config.getString("upperMaterial",""));
        if(lMaterial==null) {
            lMaterial = material;
        }
        if(uMaterial==null) {
            uMaterial = material;
        }
        if(lMaterial==null || uMaterial==null) {
            return null;
        }
        boolean powered = config.getBoolean("powered", false);
        return new SpecialBlockDoorThreeBlocks(id, lMaterial, uMaterial, powered);
    }
    
    @Override
    public void placeBlock(final Block blockPlace, final BlockFace blockFace, final Player player) {
        final Location playerLoc = player.getLocation();
        placeDoor(blockPlace, playerLoc, lowerMaterial, powered, false, false, false);
        placeHalfDoor(blockPlace.getRelative(BlockFace.UP,2), playerLoc, upperMaterial);
    }
    
    private void placeHalfDoor(final Block block, final Location playerLoc, final Material material) {
        new BukkitRunnable() {
            @Override
            public void run() {
                Block lowerBlock = block.getRelative(BlockFace.DOWN);
                final BlockState state = block.getState();
                state.setType(material);
                if(lowerBlock.getBlockData() instanceof Door && state.getBlockData() instanceof Door) {
                    Door lowerData = (Door) lowerBlock.getBlockData();
                    Door data = (Door) state.getBlockData();
                    data.setFacing(lowerData.getFacing());
                    data.setHalf(Bisected.Half.BOTTOM);
                    data.setOpen(lowerData.isOpen());
                    data.setPowered(lowerData.isPowered());
                    data.setHinge(lowerData.getHinge());
                    state.setBlockData(data);
                    DevUtil.log("4 half door block place: ID "+state.getType()+" - DV "+state.getRawData());
                    state.getBlock().setBlockData(state.getBlockData(),false);//.update(true, false);
                } else {
                    DevUtil.log("invalid door material: ID "+state.getType());
                }
            }
        }.runTaskLater(ArchitectPlugin.getPluginInstance(), 3);

    }
    
}
