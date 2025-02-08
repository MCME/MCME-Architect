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
package com.mcmiddleearth.util;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.destroystokyo.paper.profile.ProfileProperty;
import com.google.common.io.BaseEncoding;
import com.mcmiddleearth.architect.customHeadManager.CustomHeadManagerData;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.Skull;
import org.bukkit.block.data.Rotatable;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.Collections;
import java.util.Set;
import java.util.UUID;

/**
 *
 * @author Eriol_Eandur
 */
public class HeadUtil {

    public static String headCollectionTag = "MCME Head Collection";

    public static ItemStack getCustomHead(String name, UUID uuid, String headTexture) {
        PlayerProfile profile = Bukkit.getServer().createProfile(uuid, "MCME_Head");
        Set<ProfileProperty> propertySet = profile.getProperties();
        propertySet.add(new ProfileProperty("textures", headTexture));
        ItemStack itemStack = new ItemStack(Material.PLAYER_HEAD, 1);
        SkullMeta headMeta = (SkullMeta) itemStack.getItemMeta();
        headMeta.setPlayerProfile(profile);
        headMeta.setDisplayName(name);
        headMeta.setLore(Collections.singletonList(headCollectionTag));
        itemStack.setItemMeta(headMeta);
        return itemStack;
    }
    
    public static void placeCustomHead(Block block, ItemStack head) {
        PlayerProfile profile = ((SkullMeta)head.getItemMeta()).getPlayerProfile();
        if(profile != null) {
            BlockState blockState = block.getState();
            blockState.setType(Material.PLAYER_HEAD);
            blockState.getBlock().setBlockData(blockState.getBlockData());//.update(true, false);
            blockState = block.getState();
            Skull skullData = (Skull) blockState;
            skullData.setPlayerProfile(profile);
            skullData.update(true, false);
            Rotatable data = ((Rotatable)block.getState().getBlockData());
            data.setRotation(BlockFace.SOUTH_SOUTH_EAST);
            skullData.getBlock().setBlockData(data);
        }
    }

    public static ItemStack pickCustomHead(Skull skullBlockState) {
        PlayerProfile profile = skullBlockState.getPlayerProfile();
        if(profile!=null) {
            ItemStack head = new ItemStack(Material.PLAYER_HEAD, 1);
            SkullMeta headMeta = (SkullMeta) head.getItemMeta();
            headMeta.setPlayerProfile(profile);
            headMeta.setDisplayName(CustomHeadManagerData.getHeadName(profile.getId()));
            head.setItemMeta(headMeta);
            return head;
        }
        return null;
    }

    public static String getHeadTexture(String url) {
        return BaseEncoding.base64().encode(String.format("{textures:{SKIN:{url:\"%s\"}}}", url).getBytes());
    }
    
}
