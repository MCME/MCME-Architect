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
package com.mcmiddleearth.architect.customHeadManager;

import com.mcmiddleearth.util.DevUtil;
import com.mcmiddleearth.util.HeadUtil;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.Sign;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;

/**
 *
 * @author Eriol_Eandur
 */
public class CustomHeadGallery {
    
    private Location location;
    private Location nextLocation;
    
    private final CustomHeadCollection collection;
    
    private final TreeMap<String,CustomHeadGallery> subGalleries = new TreeMap<>();
    
    public CustomHeadGallery(CustomHeadCollection collection, Location location) {
        this.collection = collection;
        this.location = location.clone();
        nextLocation = location.clone();
        placeMainGallery();
        calculateNextLocation(this.getMainWidth());
        TreeMap<String,CustomHeadCollection> subCollections = collection.getSubCollections(); 
        for(String subCollectionName: subCollections.keySet()) {
            CustomHeadCollection subCollection = subCollections.get(subCollectionName);
            CustomHeadGallery subGallery = new CustomHeadGallery(subCollection, nextLocation);
            subGalleries.put(subCollectionName, subGallery); 
            calculateNextLocation(subGallery.getFullWidth());
        }
    }
    
    public void setLocation(Location newLoc) {
        remove();
        setLocationsRecursive(newLoc);
    }
    
    public void setLocationsRecursive(Location newLoc) {
        location = newLoc.clone();
        nextLocation = location.clone();
        placeMainGallery();
        calculateNextLocation(getMainWidth());
        
        //check if collections have been removed >> remove gallery too
        List<String> toBeRemoved = new ArrayList<>();
        for(String subGalleryName: subGalleries.keySet()) {
            if(collection.getSubCollection(subGalleryName)==null) {
                toBeRemoved.add(subGalleryName);
            }
        }
        for(String subGalleryName: toBeRemoved) {
            subGalleries.remove(subGalleryName);
        }
        
        //check if collections have been added >> create new gallery 
        //set location for all other galleries
        for(String subGalleryName: collection.getSubCollections().keySet()) {
            CustomHeadGallery subGallery = subGalleries.get(subGalleryName);
            if(subGallery == null) {
                subGallery = new CustomHeadGallery(collection.getSubCollection(subGalleryName),nextLocation);
                subGalleries.put(subGalleryName, subGallery);
            }
            subGallery.setLocationsRecursive(nextLocation);
            calculateNextLocation(subGallery.getFullWidth());
        }
    }
    
    public void place() {
        setLocationsRecursive(location);
    }
    
    public void remove() {
        removeMainGallery();
        for(String subGalleryName: subGalleries.keySet()) {
            CustomHeadGallery subGallery = subGalleries.get(subGalleryName);
            subGallery.remove();
        }
    }
    
    private void refreshMainGallery() {
        removeMainGallery();
        placeMainGallery();
    }
    
    private void removeMainGallery() {
        int headNumber = collection.getMainHeadNumber();
        DevUtil.log(30,"r**"+headNumber+"**"+collection.absoluteName());
        if(headNumber<1) {
            return;
        }
        for(int iX = 0; iX<getMainWidth()/2;iX++) {
            for(int iZ = 0; iZ<getMainHeight()/2;iZ++) {
                DevUtil.log(31,"h**"+iX+"**"+iZ+"**REMOVED");
                removeHead(iX, iZ);
            }
        }
    }
    
    private void placeMainGallery() {
        int headNumber = collection.getMainHeadNumber();
        DevUtil.log(30,"p**"+headNumber+"**"+collection.absoluteName());
        if(headNumber<1) {
            return;
        }
        String currentHeadName = collection.getCustomHeads().firstKey();
        for(int iX = 0; iX<getMainWidth()/2;iX++) {
            for(int iZ = 0; iZ<getMainHeight()/2;iZ++) {
                if(currentHeadName==null) {
                    return;
                }
                DevUtil.log(31,"h**"+iX+"**"+iZ+"**"+currentHeadName);
                placeHead(iX, iZ, collection.getHead(currentHeadName));
                currentHeadName = collection.getCustomHeads().higherKey(currentHeadName);
            }
        }
    }
    
    private void calculateNextLocation(int distance) {
        Vector step = new Vector(-distance, 0, 0);
        nextLocation = nextLocation.add(step);
    }
    
    private int getMainHeight() {
        return (int) (Math.ceil(Math.sqrt(collection.getMainHeadNumber()))*2);
    }
    
    public int getFullHeight() {
        int maxHeight = getMainHeight();
        for(CustomHeadGallery subGallery: subGalleries.values()) {
            maxHeight = Math.max(maxHeight,subGallery.getFullHeight());
        }
        return maxHeight;
    }
    
    private int getMainWidth() {
        return getMainHeight()+2;
    }
    
    private int getFullWidth() {
        int width = getMainWidth();
        for(CustomHeadGallery subGallery: subGalleries.values()) {
            width = width+subGallery.getFullWidth();
        }
        return width;
    }
    
    private void placeHead(int iX, int iZ, ItemStack head) {
        Location headLocation = location.clone().add(new Vector(-iX*2,0,iZ*2));
        Block block = headLocation.getWorld().getBlockAt(headLocation);
        BlockState blockState;
        for(int i =0; i<(collection.getAbsoluteName().length)/4+1;i++) {
            blockState = block.getState();
            blockState.setType(Material.WHITE_WOOL);
            blockState.setRawData((byte)0);
            blockState.update(true, false);
            block = block.getRelative(BlockFace.UP);
        }
        HeadUtil.placeCustomHead(block, head);
        block = block.getRelative(0, -1, -1);
        for(int i = 0; i<(collection.getAbsoluteName().length)/4+1;i++) {
            blockState = block.getState();
            blockState.setType(Material.OAK_WALL_SIGN);
            blockState.update(true, false);
            blockState = block.getState();
            /*org.bukkit.block.data.type.Sign sign = (org.bukkit.block.data.type.Sign) blockState.getBlockData();
            sign.setRotation(BlockFace.NORTH);
            blockState.setBlockData(sign);*/
            for(int line = 0; line < 4 ; line++) {
                if(4*i+line<collection.getAbsoluteName().length) {
                    ((Sign) blockState).setLine(line, collection.getAbsoluteName()[4*i+line]+'/');
                } else {
                    ((Sign) blockState).setLine(line, head.getItemMeta().getDisplayName());
                    blockState.update(true, false);
                    return;
                }
            }
            blockState.update(true, false);
        }
    }
    
    private void removeHead(int iX, int iZ) {
        Location headLocation = location.clone();
        headLocation.add(new Vector(-iX*2,0,iZ*2));
        Block block = headLocation.getWorld().getBlockAt(headLocation);
        BlockState blockState;
        for(int i =0; i<(collection.getAbsoluteName().length)/4+1;i++) {
            blockState = block.getState();
            blockState.setType(Material.AIR);
            blockState.setRawData((byte)0);
            blockState.update(true, false);
            block = block.getRelative(BlockFace.UP);
        }
        blockState = block.getState();
        blockState.setType(Material.AIR);
        blockState.setRawData((byte)0);
        blockState.update(true, false);
        block = block.getRelative(0, -1, -1);
        for(int i = 0; i<(collection.getAbsoluteName().length)/4+1;i++) {
            blockState = block.getState();
            blockState.setType(Material.AIR);
            blockState.setRawData((byte)0);
            blockState.update(true, false);
        }
    }

    public Location getLocation() {
        return location;
    }
}
