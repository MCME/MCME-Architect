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

import com.google.common.io.BaseEncoding;
import com.mcmiddleearth.architect.customHeadManager.CustomHeadManagerData;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import com.mojang.authlib.properties.PropertyMap;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.Skull;
import org.bukkit.block.data.Rotatable;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.lang.reflect.Field;
import java.util.Collections;
import java.util.UUID;
import java.util.logging.Level;

/**
 *
 * @author Eriol_Eandur
 */
public class HeadUtil {

    public static String headCollectionTag = "MCME Head Collection";

    public static ItemStack getCustomHead(String name, UUID uuid, String headTexture) {
        //todo

        GameProfile profile = new GameProfile(uuid, "MCME Head");
        PropertyMap propertyMap = profile.getProperties();
        if(propertyMap == null)
            throw new IllegalStateException("Profile doesn't contain a property map!");
        propertyMap.put("textures", new Property("Value", headTexture));
        ItemStack itemStack = new ItemStack(Material.PLAYER_HEAD, 1, (short) 3);
        ItemMeta headMeta = itemStack.getItemMeta();
        try {
            Field profileField = headMeta.getClass().getDeclaredField("profile");
            profileField.setAccessible(true);
            profileField.set(headMeta, profile);
        } catch (NoSuchFieldException | SecurityException e) {
            Bukkit.getLogger().log(Level.SEVERE, "No such method exception during reflection.", e);
        } catch (IllegalArgumentException | IllegalAccessException e) {
            Bukkit.getLogger().log(Level.SEVERE, "Unable to use reflection.", e);
        }
        ((SkullMeta)headMeta).setOwner(uuid.toString());
        headMeta.setDisplayName(name);
        headMeta.setLore(Collections.singletonList(headCollectionTag));
        itemStack.setItemMeta(headMeta);
        return itemStack;
    }
    
    public static void placeCustomHead(Block block, ItemStack head) {
        try {
            BlockState blockState = block.getState();
            blockState.setType(Material.PLAYER_HEAD);
            blockState.getBlock().setBlockData(blockState.getBlockData());//.update(true, false);
            blockState = block.getState();
            Skull skullData = (Skull) blockState;
            //skullData.setSkullType(SkullType.PLAYER);
            Field profileField = head.getItemMeta().getClass().getDeclaredField("profile");
            profileField.setAccessible(true);
            GameProfile profile = (GameProfile) profileField.get(head.getItemMeta());
            profileField = skullData.getClass().getDeclaredField("profile");
            profileField.setAccessible(true);
            profileField.set(skullData, profile);
            skullData.update(true, false);
            Rotatable data = ((Rotatable)block.getState().getBlockData());
            data.setRotation(BlockFace.SOUTH_SOUTH_EAST);
            skullData.getBlock().setBlockData(data);
        } catch (NoSuchFieldException | SecurityException e) {
            Bukkit.getLogger().log(Level.SEVERE, "No such method exception during reflection.", e);
        } catch (IllegalArgumentException | IllegalAccessException e) {
            Bukkit.getLogger().log(Level.SEVERE, "Unable to use reflection.", e);
        }
    }

    public static ItemStack pickCustomHead(Skull skullBlockState) {
        try {
            Field profileField = skullBlockState.getClass().getDeclaredField("profile");
            profileField.setAccessible(true);
            GameProfile profile = (GameProfile) profileField.get(skullBlockState);

            ItemStack head = new ItemStack(Material.PLAYER_HEAD, 1, (short) 3);
            ItemMeta headMeta = head.getItemMeta();
            
            profileField = headMeta.getClass().getDeclaredField("profile");
            profileField.setAccessible(true);
            profileField.set(headMeta, profile);
            headMeta.setDisplayName(CustomHeadManagerData.getHeadName(profile.getId()));
            head.setItemMeta(headMeta);
            return head;
        } catch (NoSuchFieldException | SecurityException e) {
            Bukkit.getLogger().log(Level.SEVERE, "No such method exception during reflection.", e);
        } catch (IllegalArgumentException | IllegalAccessException e) {
            Bukkit.getLogger().log(Level.SEVERE, "Unable to use reflection.", e);
        }
        return null;
    }

    public static String getHeadTexture(String url) {
        return BaseEncoding.base64().encode(String.format("{textures:{SKIN:{url:\"%s\"}}}", url).getBytes());
    }
    
}
