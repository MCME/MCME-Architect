/*
 * Copyright (C) 2020 MCME
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

import com.mcmiddleearth.architect.specialBlockHandling.SpecialBlockType;
import org.bukkit.Bukkit;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

/**
 *
 * @author Eriol_Eandur
 */
public class SpecialBlockOnWaterConnect extends SpecialBlockConnect {

    private SpecialBlockOnWaterConnect(String id, BlockData data) {
        super(id, data, SpecialBlockType.BLOCK_ON_WATER_CONNECT);
    }
    
    public static SpecialBlockOnWaterConnect loadFromConfig(ConfigurationSection config, String id) {
        BlockData data;
        try {
            data = Bukkit.getServer().createBlockData(config.getString("blockData",""));
            return new SpecialBlockOnWaterConnect(id, data);
        } catch(IllegalArgumentException e) {
            return null;
        }
    }

    @Override
    public Block getBlock(Block clicked, BlockFace blockFace,
                          Location interactionPoint, Player player) {
        return player.getTargetBlockExact(4, FluidCollisionMode.ALWAYS).getRelative(BlockFace.UP);

    }
}
