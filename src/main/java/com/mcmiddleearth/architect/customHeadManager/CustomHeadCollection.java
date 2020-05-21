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

import com.mcmiddleearth.util.HeadUtil;
import org.bukkit.inventory.ItemStack;

import java.util.Arrays;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

/**
 *
 * @author Eriol_Eandur
 */
public class CustomHeadCollection {
    
    private final TreeMap<String,CustomHeadCollection> subCollections = new TreeMap<>();
    private final TreeMap<String,CustomHeadData> customHeads = new TreeMap<>();
    
    private String[] description; //for sign, max length???
    
    private String[] absoluteName = new String[0]; //full name including all parent collections
    
    public boolean addHead(String headName, CustomHeadData head) {
        if(headName.endsWith("/")) {
            return false;
        }
        if(headName.contains("/")) {
            int separator = headName.indexOf("/");
            if(separator == 0) {
                return false;
            }
            String path = headName.substring(0,separator);
            headName = headName.substring(separator+1);
            CustomHeadCollection subCollection = subCollections.get(path);
            if(subCollection==null) {
                subCollection = new CustomHeadCollection();
                subCollection.absoluteName = Arrays.copyOf(absoluteName,absoluteName.length+1);
                subCollection.absoluteName[absoluteName.length] = path;
                subCollections.put(path, subCollection);
            }
            return subCollection.addHead(headName, head);
        } else {
            if(customHeads.containsKey(headName)) {
                return false;
            }
            customHeads.put(headName, head);
            return true;
        }
    }
    
    public boolean removeHead(String headName) {
        if(headName.endsWith("/")) {
            return false;
        }
        if(headName.contains("/")) {
            int separator = headName.indexOf("/");
            String path = headName.substring(0,separator);
            headName = headName.substring(separator+1);
            CustomHeadCollection subCollection = subCollections.get(path);
            if(subCollection==null) {
                return false;
            }
            if(subCollection.removeHead(headName)) {
                if(subCollection.getFullHeadNumber()==0) {
                    subCollections.remove(path);
                }
                return true;
            }
        } else {
            if(customHeads.containsKey(headName)) {
                customHeads.remove(headName);
                return true;
            }
        }
        return false;
    }
    
    public CustomHeadCollection getSubCollection(String collectionName) {
        return subCollections.get(collectionName);
    }
    
    private CustomHeadData getHeadData(String headName) {
        if(headName.endsWith("/")) {
            return null;
        }
        if(headName.contains("/")) {
            int separator = headName.indexOf("/");
            String path = headName.substring(0,separator);
            headName = headName.substring(separator+1);
            CustomHeadCollection subCollection = subCollections.get(path);
            if(subCollection==null) {
                return null;
            }
            return subCollection.getHeadData(headName);
        } else {
            if(customHeads.containsKey(headName)) {
                return customHeads.get(headName);
            }
            return null;
        }
    }
    
    public String getFullName(String headName) {
        while(headName.startsWith("/")) {
            headName = headName.substring(1);
        }
        int separator = headName.indexOf("/");
        if(separator==-1) {
            if(customHeads.containsKey(headName)) {
            String foundName = absoluteName()+headName;
            return foundName;
            }
            separator = headName.length();
        }
        String firstName = headName.substring(0,separator);
        CustomHeadCollection subCollection = subCollections.get(firstName);
        if(subCollection!=null 
                && (separator == headName.length() || subCollection.contains(headName.substring(separator+1)))) {
            String foundName = absoluteName()+headName;
            return foundName;
        } 
        for(String search: subCollections.navigableKeySet()) {
            String fullName = subCollections.get(search).getFullName(headName);
            if(!fullName.equals("")) {
                return fullName;
            }
        }
        return "";
    }
    
    public String absoluteName() { //public for dev output only
            String result = "";
            for(String str:absoluteName) {
                result+=str+"/";
            }
            return result;
    }
    
    private boolean contains(String name) {
        int separator = name.indexOf("/");
        if(separator<0) {
            return customHeads.get(name)!=null;
        } else {
            String firstName = name.substring(0,separator);
            CustomHeadCollection subCollection = subCollections.get(firstName);
            if(subCollection==null) {
                return false;
            } else {
                return subCollection.contains(name.substring(separator+1));
            }
        }
    }
    
    public String getHeadName(UUID headId) {
        for(String name: customHeads.keySet()) {
            if(customHeads.get(name).getHeadId().equals(headId)) {
                String result = "";
                for(String path: absoluteName) {
                    result = result + path + "/";
                }
                return result+name;
            }
        }
        for(CustomHeadCollection subCollection: subCollections.values()) {
            String result = subCollection.getHeadName(headId);
            if(result!=null) {
                return result;
            }
        }
        return null;
    }
    
    public ItemStack getHead(String headName) {
        CustomHeadData data = this.getHeadData(headName);
        if(data==null) {
            return null;
        } else {
            return HeadUtil.getCustomHead(headName, data.getHeadId(), data.getTexture());
        }
    }
    
    public void getAllHeadsIncludingSubCollections(Map<String,ItemStack> headMap) {
        for(String headName: customHeads.keySet()) {
            headMap.put(absoluteName()+headName, getHead(headName));
        }
        for(CustomHeadCollection subCollection: subCollections.values()) {
            subCollection.getAllHeadsIncludingSubCollections(headMap);
        }
    }
    
    public int getMainHeadNumber() {
        return customHeads.size();
    }
    
    public int getFullHeadNumber() {
        int result = getMainHeadNumber();
        for(CustomHeadCollection subCollection: subCollections.values()) {
            result += subCollection.getFullHeadNumber();
        }
        return result;
    }

    public TreeMap<String, CustomHeadCollection> getSubCollections() {
        return subCollections;
    }

    public TreeMap<String, CustomHeadData> getCustomHeads() {
        return customHeads;
    }

    public String[] getDescription() {
        return description;
    }

    public void setDescription(String[] description) {
        this.description = description;
    }

    public String[] getAbsoluteName() {
        return absoluteName;
    }
}
