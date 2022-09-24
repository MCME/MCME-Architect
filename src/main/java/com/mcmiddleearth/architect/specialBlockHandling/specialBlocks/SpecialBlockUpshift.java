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
import com.mcmiddleearth.architect.chunkUpdate.ChunkUpdateUtil;
import com.mcmiddleearth.architect.noPhysicsEditor.NoPhysicsListener;
import com.mcmiddleearth.architect.specialBlockHandling.SpecialBlockType;
import com.mcmiddleearth.architect.specialBlockHandling.data.SpecialBlockInventoryData;
import com.mcmiddleearth.pluginutil.LegacyMaterialUtil;
import com.mcmiddleearth.pluginutil.NumericUtil;
import com.mcmiddleearth.util.DevUtil;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.data.BlockData;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author Eriol_Eandur
 */
public class SpecialBlockUpshift extends SpecialBlock {

    private SpecialBlockUpshift(String id, BlockData data) {
        super(id, data, SpecialBlockType.UPSHIFT);
    }

    public static SpecialBlockUpshift loadFromConfig(ConfigurationSection config, String id) {
        BlockData data;
        try {
            data = Bukkit.getServer().createBlockData(config.getString("blockData",""));
        } catch(IllegalArgumentException e) {
            return null;
        }
        return new SpecialBlockUpshift(id, data);
    }
    
    public void placeBlock(final Block blockPlace, final BlockFace blockFace, Block clicked,
                           final Location interactionPoint, final Player player) {
        super.placeBlock(blockPlace.getRelative(BlockFace.UP),blockFace,clicked,interactionPoint,player);
    }
    
}
