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
package com.mcmiddleearth.architect.specialBlockHandling.data;

import com.mcmiddleearth.architect.ArchitectPlugin;
import com.mcmiddleearth.architect.serverResoucePack.RpManager;
import com.mcmiddleearth.architect.specialBlockHandling.SpecialBlockType;
import com.mcmiddleearth.architect.specialBlockHandling.customInventories.CustomInventory;
import com.mcmiddleearth.architect.specialBlockHandling.customInventories.SearchInventory;
import com.mcmiddleearth.architect.specialBlockHandling.specialBlocks.*;
import com.mcmiddleearth.pluginutil.FileUtil;
import com.mcmiddleearth.util.ConversionUtil_1_13;
import com.mcmiddleearth.util.DevUtil;
import com.mcmiddleearth.util.ZipUtil;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author Eriol_Eandur
 */
public class SpecialBlockInventoryData {
    
    public final static String SPECIAL_BLOCK_TAG = "MCME Block";

    private final static Map<String,CustomInventory> inventories = new HashMap<>();
    
    private final static Map<String, SearchInventory> searchInventories = new HashMap<>();

    private static List<SpecialBlock> blockList = new ArrayList<>();
    
    private static final String configLocator = "inventories";
    
    private static final File configFolder = new File(ArchitectPlugin.getPluginInstance()
                                                       .getDataFolder(),configLocator+"/block");
    
    static {
        if(!configFolder.exists()) {
            configFolder.mkdirs();
        }
    }
    public static void loadInventories() {
        if(!inventories.isEmpty()) {
            for(CustomInventory inv:inventories.values()) {
                inv.destroy();
            }
            inventories.clear();
        }
        if(!searchInventories.isEmpty()) {
            for(SearchInventory inv:searchInventories.values()) {
                inv.destroy();
            }
            searchInventories.clear();
        }
        blockList = new ArrayList<>();
        File[] files = configFolder.listFiles(FileUtil.getDirFilter());
        if(files!=null) {
            for(File file: files) {
                createBlockInventory(file);
            }
        }
    }
    
    private static void createBlockInventory(File folder) {
        String rpName = folder.getName();
        File[] files = folder.listFiles(FileUtil.getFileExtFilter("yml"));
        CustomInventory inventory = new CustomInventory(ChatColor.WHITE+"MCME Blocks - "+rpName);
        inventories.put(rpName, inventory);
        SearchInventory searchInventory = new SearchInventory(ChatColor.WHITE+"blocks");
        searchInventories.put(rpName, searchInventory);
        File blockFile = new File(folder,"categories.yml");
        if(blockFile.exists()) {
            loadFromFile(rpName, blockFile);
        }
        for(File file: files) {
            if(!file.getName().equals(blockFile.getName())) {
                loadFromFile(rpName, file);
            }
        }
        if(inventory.isEmpty()) {
            inventories.remove(rpName);
            searchInventories.remove(rpName);
            inventory.destroy();
            searchInventory.destroy();
        }
    }
    
    private static void loadFromFile(String rpName, File file) {
        Logger.getGlobal().info("Loading items into to inventory for resource pack "+rpName+" from "+file.getName());
        CustomInventory inventory = inventories.get(rpName);
        SearchInventory searchInventory = searchInventories.get(rpName);
        YamlConfiguration config = new YamlConfiguration();
        try {
            config.load(file);
        } catch (IOException | InvalidConfigurationException ex) {
            Logger.getLogger(SpecialBlockInventoryData.class.getName()).log(Level.SEVERE, null, ex);
        }
        ConfigurationSection categoryConfig = config.getConfigurationSection("Categories");
        if(categoryConfig!=null) {
            for(String categoryKey: categoryConfig.getKeys(false)) {
                ConfigurationSection section = categoryConfig.getConfigurationSection(categoryKey);
                ItemStack categoryItem = loadItemFromConfig(section, categoryKey, rpName);
                ItemStack currentCategoryItem = loadItemFromConfig(section, categoryKey, rpName);
                if(section.contains("damageCurrent")) {
                    currentCategoryItem.setDurability((short)section.getInt("damageCurrent"));
                }
                boolean useSubcategories = section.getBoolean("useSubcategories",false);
                inventory.setCategoryItems(categoryKey, null, true, categoryItem, currentCategoryItem, useSubcategories);
            }
        }
        ConfigurationSection itemConfig = config.getConfigurationSection("Items");
        if(itemConfig!=null) {
            for(String itemKey: itemConfig.getKeys(false)) {
                if(getSpecialBlock(fullName(rpName, itemKey))!=null) {
                    Logger.getLogger(SpecialBlockInventoryData.class.getName())
                        .log(Level.WARNING, "Double custom block ID "+fullName(rpName,itemKey)+"'. Block skipped.");
                    DevUtil.log("Error. Double custom block ID "+fullName(rpName,itemKey)+"'. Block skipped.");
                } else {
                    SpecialBlockType type;
                    ConfigurationSection section = itemConfig.getConfigurationSection(itemKey);
                    try {
                        String typeName = section.getString("type");
                        if(typeName==null) {
                            typeName="VANILLA";
                            DevUtil.log("Waring, missing block type for: "+fullName(rpName,itemKey));
                        }
                        type = SpecialBlockType.valueOf(typeName);
                    } catch( IllegalArgumentException e) { 
                        type = SpecialBlockType.INVALID;
                    }
                    SpecialBlock blockData = null;
                    switch(type) {
                        case BLOCK:
                            blockData = SpecialBlock.loadFromConfig(section, fullName(rpName,itemKey));
                            break;
                        case BLOCK_ON_WATER:
                            blockData = SpecialBlockOnWater.loadFromConfig(section, fullName(rpName,itemKey));
                            break;
                        case BISECTED:
                            blockData = SpecialBlockBisected.loadFromConfig(section, fullName(rpName,itemKey));
                            break;
                        case THREE_AXIS:
                            blockData = SpecialBlockThreeAxis.loadFromConfig(section, fullName(rpName,itemKey));
                            break;
                        case TWO_AXIS:
                            blockData = SpecialBlockTwoAxis.loadFromConfig(section, fullName(rpName,itemKey));
                            break;
                        case FIVE_FACES:
                            blockData = SpecialBlockFiveFaces.loadFromConfig(section, fullName(rpName,itemKey));
                            break;
                        case SIX_FACES:
                            blockData = SpecialBlockSixFaces.loadFromConfig(section, fullName(rpName,itemKey));
                            break;
                        case EIGHT_FACES:
                            blockData = SpecialBlockEightFaces.loadFromConfig(section, fullName(rpName,itemKey));
                            break;
                        case FOUR_DIRECTIONS:
                            blockData = SpecialBlockFourDirections.loadFromConfig(section, fullName(rpName,itemKey));
                            break;
                        case OPEN_HALF_DOOR:
                            blockData = SpecialBlockOpenHalfDoor.loadFromConfig(section, fullName(rpName,itemKey));
                            break;
                        case MATCH_ORIENTATION:
                            blockData = SpecialBlockMatchOrientation.loadFromConfig(section, fullName(rpName,itemKey));
                            break;
                        case WALL_COMBI:
                            blockData = SpecialBlockWallCombi.loadFromConfig(section, fullName(rpName,itemKey));
                            break;
                        case DOOR:
                            blockData = SpecialBlockDoor.loadFromConfig(section, fullName(rpName,itemKey));
                            break;
                        case THIN_WALL:
                            blockData = SpecialBlockThinWall.loadFromConfig(section, fullName(rpName,itemKey));
                            break;
                        case DOOR_VANILLA:
                            blockData = SpecialBlockVanillaDoor.loadFromConfig(section, fullName(rpName,itemKey));
                            break;
                        case DOOR_FOUR_BLOCKS:
                            blockData = SpecialBlockDoorFourBlocks.loadFromConfig(section, fullName(rpName,itemKey));
                            break;
                        case DOOR_THREE_BLOCKS:
                            blockData = SpecialBlockDoorThreeBlocks.loadFromConfig(section, fullName(rpName,itemKey));
                            break;
                        case ITEM_BLOCK:
                            blockData = SpecialBlockItemBlock.loadFromConfig(section, fullName(rpName,itemKey));
                            break;
                        case ITEM_BLOCK_TWO_DIRECTIONS:
                            blockData = SpecialBlockItemTwoDirections.loadFromConfig(section, fullName(rpName,itemKey));
                            break;
                        case ITEM_BLOCK_FOUR_DIRECTIONS:
                            blockData = SpecialBlockItemFourDirections.loadFromConfig(section, fullName(rpName,itemKey));
                            break;
                        case MOB_SPAWNER_BLOCK:
                            blockData = SpecialBlockMobSpawnerBlock.loadFromConfig(section, fullName(rpName,itemKey));
                            break;
                        case BURNING_FURNACE:
                            blockData = SpecialBlockBurningFurnace.loadFromConfig(section, fullName(rpName, itemKey));
                            break;
                        case DOUBLE_Y_BLOCK:
                            blockData = SpecialBlockDoubleY.loadFromConfig(section, fullName(rpName, itemKey));
                            break;
                        case VANILLA:
                            blockData = SpecialBlockVanilla.loadFromConfig(section, fullName(rpName, itemKey));
                            break;
                    }
                    ItemStack inventoryItem = loadItemFromConfig(section, itemKey, rpName);
                    if(blockData !=null && inventoryItem!=null && !inventoryItem.getType().equals(Material.AIR)) {
                        blockData.loadBlockCollection(section,rpName);
                        blockList.add(blockData);

                        Object categoryObject = section.get("category");
                        if(categoryObject instanceof String) {
                            inventory.add(inventoryItem, (String) categoryObject, false);
                        } else if((categoryObject instanceof List) && ! ((List<?>)categoryObject).isEmpty()) {
                            ((List<String>)categoryObject).forEach(category -> inventory.add(inventoryItem, category,false));
                        } else {
                            inventory.add(inventoryItem,null,false);
                            Logger.getGlobal().info("category object: "+categoryObject);
                        }
                        searchInventory.add(inventoryItem);
                    } else {
                        Logger.getLogger(SpecialBlockInventoryData.class.getName())
                            .log(Level.WARNING, "Invalid config data while loading Special MCME Block '"+itemKey+"'. Block skipped.");
                        Logger.getGlobal().info("block Data: "+blockData+" - item: "+inventoryItem);
                    }
                }
            }
        }
        // save conversions to new version
        try {
            config.save(file);
        } catch (IOException ex) {
            Logger.getLogger(SpecialBlockInventoryData.class.getName()).log(Level.SEVERE, null, ex);
        }
    }
    
    public static boolean openInventory(Player p, String resourcePack) {
        return openInventory(p,resourcePack,null);
    }
    
    private static boolean openInventory(Player p, String resourcePack, ItemStack collectionBase) {
        CustomInventory inv = inventories.get(resourcePack);
        if(inv==null) {
            DevUtil.log("block inventory not found for "+resourcePack);
        }
        if(inv!=null && !inv.isEmpty()) {
            inv.open(p,collectionBase);
            return true;
        }
        return false;
    }
    
    public static boolean openInventory(Player p, ItemStack collectionBase) {
//Logger.getGlobal().info(collectionBase.toString());
        SpecialBlock baseBlock = matchSpecialBlock(collectionBase);
//Logger.getGlobal().info(""+baseBlock);
        if(baseBlock != null) {
            String rpName = SpecialBlockInventoryData.rpName(baseBlock.getId());
            return openInventory(p, rpName, inventories.get(rpName).getItem(baseBlock.getId()));
        }
        return false;
    }
    
    public static boolean openSearchInventory(Player p, String resourcePack, String search) {
        SearchInventory inv = searchInventories.get(resourcePack);
        if(inv==null) {
            DevUtil.log(search+" search inventory not found for "+resourcePack);
            //inv = searchInventories.get("Gondor");
        }
        if(inv!=null) {
            inv.open(p, search);
            return true;
//Logger.getGlobal().info("Inventory 3");
        }
        return false;
    }
    
    public static boolean hasBlockInventory(String rpName) {
        return inventories.containsKey(rpName);
    }

    private static SpecialBlock matchSpecialBlock(ItemStack item) {
        SpecialBlock block = getSpecialBlockDataFromItem(item);
        if(block!=null) {
            return block;
        } else {
            BlockData data = SpecialBlockVanilla.matchBlockData(item.getType().name());
            if(data.matches(Material.AIR.createBlockData())) {
                return null;
            }
            return blockList.stream().filter(search->search.matches(data)).findFirst().orElse(null);
        }
    }

    public static SpecialBlock getSpecialBlock(String id) {
        for(SpecialBlock data: blockList) {
            if(data.getId().equals(id)) {
//Logger.getGlobal().info("get data "+data.getId());
                return data;
            }
        }
//Logger.getGlobal().info("get data NULL");
        return null;
    }
    
    public static SpecialBlock getSpecialBlockDataFromItem(ItemStack handItem) {
        return getSpecialBlock(getSpecialBlockId(handItem));
    }

    public static String getSpecialBlockId(ItemStack handItem) {
//Logger.getGlobal().info("getID start");
        ItemMeta meta = handItem.getItemMeta();
        if(meta==null 
            || (!(meta.hasLore() 
                && meta.getLore().size()>1 
                && meta.getLore().get(0).equals(SPECIAL_BLOCK_TAG)))) {
//Logger.getGlobal().info("getID return null");
            return null;
        }
//Logger.getGlobal().info("getID return "+meta.getLore().get(1));
        return meta.getLore().get(1);
    }

    public static ItemStack getItem(Block block, String rpName) {
        //Material material = block.getType();
        //byte dataValue = block.getData();
        for(SpecialBlock data: blockList) {
            if(rpName(data.getId()).equals(rpName)
                    && data.matches(block)) {
                return inventories.get(rpName).getItem(data.getId());
            }
        }
        return getHandItem(new ItemStack(block.getType(),1));
        //1.13 removed: return getHandItem(new ItemStack(block.getType(),1,(short)0,block.getData()));
    }
    
    private static ItemStack getHandItem(ItemStack item) {
        switch(item.getType()) {
            case ACACIA_WALL_SIGN:
                return new ItemStack(Material.ACACIA_SIGN,1);
            case DARK_OAK_WALL_SIGN:
                return new ItemStack(Material.DARK_OAK_SIGN,1);
            case OAK_WALL_SIGN:
                return new ItemStack(Material.OAK_SIGN,1);
            case SPRUCE_WALL_SIGN:
                return new ItemStack(Material.SPRUCE_SIGN,1);
            case JUNGLE_WALL_SIGN:
                return new ItemStack(Material.JUNGLE_SIGN,1);
            case BIRCH_WALL_SIGN:
                return new ItemStack(Material.BIRCH_SIGN,1);
            case WALL_TORCH:
                return new ItemStack(Material.TORCH,1);
            case REDSTONE_WALL_TORCH:
                return new ItemStack(Material.REDSTONE_TORCH,1);
        }
        String material = item.getType().name();
        if(item.getType().name().contains("WALL_BANNER")) {
            return new ItemStack(Material.valueOf(material.replace("WALL_BANNER", "BANNER")));
        }
        return item;
    }
    /*1.13 removed        case BANNER:
            case STANDING_BANNER:
                return new ItemStack(Material.BANNER,1);
            case BED_BLOCK:
                return new ItemStack(Material.BED,1);
            case CAKE_BLOCK:
                return new ItemStack(Material.CAKE,1);
            case WOODEN_DOOR:
                return new ItemStack(Material.WOOD_DOOR,1);
            case SPRUCE_DOOR:
                return new ItemStack(Material.SPRUCE_DOOR_ITEM,1);
            case BIRCH_DOOR:
                return new ItemStack(Material.BIRCH_DOOR_ITEM,1);
            case JUNGLE_DOOR:
                return new ItemStack(Material.JUNGLE_DOOR_ITEM,1);
            case ACACIA_DOOR:
                return new ItemStack(Material.ACACIA_DOOR_ITEM,1);
            case DARK_OAK_DOOR:
                return new ItemStack(Material.DARK_OAK_DOOR_ITEM,1);
            case CAULDRON:
                return new ItemStack(Material.CAULDRON_ITEM,1);
            default:
                return item;
        }*/
    
    public static synchronized int downloadConfig(String rpName, InputStream in) throws IOException {
        return ZipUtil.extract(RpManager.getRpUrl(rpName,null), in, configLocator, new File(configFolder,rpName));
    }
    
    private static ItemStack loadItemFromConfig(ConfigurationSection config, String name, String rp) {
        String materialName = config.getString("itemMaterial","");
        if(materialName.startsWith("LEGACY")) {
            materialName = ConversionUtil_1_13.convertItemName(materialName.substring(7));
            config.set("itemMaterial", materialName);
        }
        Material itemMat = Material.matchMaterial(materialName);
        short dam = (short) config.getInt("damage",0);
        String displayName = (String) config.get("display");
        if(displayName==null) {
            displayName = name;
        }
        if(itemMat!=null) {
            ItemStack item = new ItemStack(itemMat,1);
            ItemMeta im = item.getItemMeta();
            im.setDisplayName(displayName);
            if(im instanceof Damageable) {
                ((Damageable)im).setDamage(dam);
            } else {
                config.set("damage",null);
            }
            im.setLore(Arrays.asList(new String[]{SPECIAL_BLOCK_TAG, fullName(rp,name)}));
            im.setUnbreakable(true);
            im.addItemFlags(ItemFlag.HIDE_UNBREAKABLE);
            im.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            item.setItemMeta(im);
            return item;
        }
        return new ItemStack(Material.STONE);
    }
    
    public static String fullName(String rpName, String name) {
        return rpName+"/"+name;
    }
    
    public static String rpName(String id) {
        return id.substring(0,id.indexOf("/"));
    }
}
