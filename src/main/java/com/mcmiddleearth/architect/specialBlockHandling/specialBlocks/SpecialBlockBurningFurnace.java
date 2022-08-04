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
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.Furnace;
import org.bukkit.block.data.BlockData;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;

/**
 *
 * @author Eriol_Eandur
 */
public class SpecialBlockBurningFurnace extends SpecialBlock {
    
    private SpecialBlockBurningFurnace(String id) {
        super(id, Material.FURNACE.createBlockData(), SpecialBlockType.BURNING_FURNACE);
    }
    
    public static SpecialBlockBurningFurnace loadFromConfig(ConfigurationSection config, String id) {
        return new SpecialBlockBurningFurnace(id);
    }
    
    @Override
    public void placeBlock(final Block blockPlace, BlockFace blockFace, Block clicked,
                           final Location interactionPoint, Player player) {
        final Location playerLoc = player.getLocation();
        final BlockState state = blockPlace.getState();
        state.setType(Material.FURNACE);
        org.bukkit.block.data.type.Furnace data = (org.bukkit.block.data.type.Furnace)getBlockData();
        data.setLit(true);
        data.setFacing(getBlockFace(playerLoc.getYaw()).getOppositeFace());
        //state.setBlockData(data);
        //state.update(true, false);
        blockPlace.setBlockData(data,false);
        new BukkitRunnable() {
            @Override
            public void run() {
                final Furnace furnace = (Furnace) blockPlace.getState();
                furnace.setBurnTime(Short.MAX_VALUE);
                //furnace.update(true, false); 
                blockPlace.setBlockData(furnace.getBlockData(),false);
                new BukkitRunnable() {
                    @Override
                    public void run(){
                        furnace.getInventory().setSmelting(new ItemStack(Material.COD));
                    }
                }.runTaskLater(ArchitectPlugin.getPluginInstance(), 1);
            }
        }.runTaskLater(ArchitectPlugin.getPluginInstance(), 10);
    }

    @Override
    public boolean matches(Block block) {
        BlockState state = block.getState();
        return state instanceof Furnace
                && ((Furnace)state).getBurnTime()>0;
    }

}
