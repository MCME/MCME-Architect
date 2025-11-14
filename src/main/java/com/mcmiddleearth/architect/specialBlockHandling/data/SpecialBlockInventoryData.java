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

import com.google.common.collect.Sets;
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
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.meta.*;

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
    
    public static final File configFolder = new File(ArchitectPlugin.getPluginInstance()
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
        SearchInventory searchInventory = new SearchInventory(ChatColor.WHITE+"blocks", rpName);
        searchInventories.put(rpName, searchInventory);
        File blockFile = new File(folder,"categories.yml");
        if(blockFile.exists()) {
            loadCategories(rpName, blockFile);
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

    private static void loadCategories(String rpName, File file) {
        DevUtil.log(1, "Loading categories to inventory for resource pack "+rpName+" from "+file.getName());
        CustomInventory inventory = inventories.get(rpName);
        inventory.setCategoryItems("Heads",null, true,
                new ItemStack(Material.PLAYER_HEAD), new ItemStack(Material.PLAYER_HEAD),false);
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
                if(section.contains("cmdCurrent")) {
                    ItemMeta meta = currentCategoryItem.getItemMeta();
                    if(meta!=null) {
                        meta.setCustomModelData(section.getInt("cmdCurrent"));
                        currentCategoryItem.setItemMeta(meta);
                    }
                }
                boolean useSubcategories = section.getBoolean("useSubcategories",false);
                inventory.setCategoryItems(categoryKey, null, true, categoryItem, currentCategoryItem, useSubcategories);
            }
        }
    }
    
    private static void loadFromFile(String rpName, File file) {
        DevUtil.log(1,"Loading items into to inventory for resource pack "+rpName+" from "+file.getName());
        CustomInventory inventory = inventories.get(rpName);
        SearchInventory searchInventory = searchInventories.get(rpName);
        YamlConfiguration config = new YamlConfiguration();
        try {
            config.load(file);
        } catch (IOException | InvalidConfigurationException ex) {
            Logger.getLogger(SpecialBlockInventoryData.class.getName()).log(Level.SEVERE, null, ex);
        }
        int separator = file.getName().lastIndexOf(".");
        String categoryName = inventory.matchCategory(file.getName().substring(0,separator));
        DevUtil.log(2, "Loading category: "+categoryName);
        ConfigurationSection itemConfig = config.getConfigurationSection("Items");

        if(itemConfig!=null) {
            DevUtil.log(2, "Loading category items.");
            for(String itemKey : config.getStringList("Main")) {
                if(getSpecialBlock(fullName(rpName, itemKey))!=null) {
                    DevUtil.log(2, "Error. Double custom block ID "+fullName(rpName,itemKey)+"'. Block skipped.");
                } else {
                    addSpecialBlock(inventory, searchInventory, categoryName, itemConfig, rpName, itemKey);
                }
            };
            DevUtil.log(2, "Loading uncategorized items.");
            for(String itemKey: itemConfig.getKeys(false)) {
                if(getSpecialBlock(fullName(rpName, itemKey))!=null) {
                    DevUtil.log(2, "Warning. Double custom block ID "+fullName(rpName,itemKey)
                            +"'. Maybe that Block was already loaded from Main lists?");
                } else {
                    addSpecialBlock(inventory, searchInventory, null, itemConfig, rpName, itemKey);
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

    private static void addSpecialBlock(CustomInventory inventory, SearchInventory searchInventory, String category,
                                        ConfigurationSection itemConfig, String rpName, String itemKey) {
        SpecialBlockType type;
        ConfigurationSection section = itemConfig.getConfigurationSection(itemKey);
        if(section != null) {
            try {
                String typeName = section.getString("type");
                if (typeName == null) {
                    typeName = "VANILLA";
                    DevUtil.log(2, "Waring, missing block type for: " + fullName(rpName, itemKey));
                }
                type = SpecialBlockType.valueOf(typeName);
            } catch (IllegalArgumentException e) {
                type = SpecialBlockType.INVALID;
            }

            SpecialBlock blockData = SpecialBlock.createSpecialBlock(type, section, fullName(rpName, itemKey));

            ItemStack inventoryItem = loadItemFromConfig(section, itemKey, rpName);
            if (blockData != null && !inventoryItem.getType().equals(Material.AIR)) {
                blockData.loadPriority(section);
                blockData.loadNextBlock(section, rpName);
                blockData.loadBlockCollection(section, rpName);
                blockList.add(blockData);

                if (category != null) {
                    inventory.add(inventoryItem, category, false);
                } else {
                    Object categoryObject = section.get("category");
                    if (categoryObject instanceof String) {
                        inventory.add(inventoryItem, (String) categoryObject, false);
                    } else if ((categoryObject instanceof List) && !((List<?>) categoryObject).isEmpty()) {
                        ((List<String>) categoryObject).forEach(thisCategory -> inventory.add(inventoryItem, thisCategory, false));
                    } else {
                        inventory.add(inventoryItem, null, false);
                        //Logger.getGlobal().info("category object: "+categoryObject);
                    }
                }
                searchInventory.add(inventoryItem);
            } else {
                DevUtil.log(2, "Invalid config data while loading Special MCME Block '" + itemKey + "'. Block skipped."
                        + " block Data: " + blockData + " - item: " + inventoryItem);
            }
        } else {
            DevUtil.log(2, "Expected config section does not exist for id: "+itemKey);
        }
    }

    public static boolean openInventory(Player p, String resourcePack) {
        return openInventory(p,resourcePack,null);
    }

    private static boolean openInventory(Player p, String resourcePack, ItemStack collectionBase) {
        return openInventory(p, resourcePack, collectionBase, false);
    }

    private static boolean openInventory(Player p, String resourcePack, ItemStack collectionBase, boolean directGet) {
        CustomInventory inv = inventories.get(resourcePack);
        if(inv==null) {
            DevUtil.log("block inventory not found for "+resourcePack);
        }
        if(inv!=null && !inv.isEmpty()) {
            inv.open(p,collectionBase, directGet);
            return true;
        }
        return false;
    }

    public static boolean openInventory(Player p, ItemStack collectionBase) {
        return openInventory(p, collectionBase, false);
    }

    public static boolean openInventory(Player p, ItemStack collectionBase, boolean directGet) {
//Logger.getGlobal().info(collectionBase.toString());
        SpecialBlock baseBlock = matchSpecialBlock(collectionBase);
//Logger.getGlobal().info("open Inventory: "+baseBlock);
        if(baseBlock != null) {
            String rpName = SpecialBlockInventoryData.rpName(baseBlock.getId());
//Logger.getGlobal().info("open Inventory rp: "+rpName);
            return openInventory(p, rpName, searchInventories.get(rpName).getItem(baseBlock.getId()),directGet);
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
        if(handItem != null) {
            ItemMeta meta = handItem.getItemMeta();
            if (meta == null
                    || (!(meta.hasLore()
                    && meta.getLore().size() > 1
                    && meta.getLore().get(0).equals(SPECIAL_BLOCK_TAG)))) {
                //Logger.getGlobal().info("getID return null");
                return null;
            }
            //Logger.getGlobal().info("getID return "+meta.getLore().get(1));
            return meta.getLore().get(1);
        } else {
            return null;
        }
    }

    public static ItemStack getItem(Block block, String rpName) {
        //Material material = block.getType();
        //byte dataValue = block.getData();
        List<SpecialBlock> vanillaMatches = new ArrayList<>();
        List<SpecialBlock> specialMatches = new ArrayList<>();
        for(SpecialBlock data: blockList) {
/*if(data.getId().contains("redstone_dust_power4_center")) {
Logger.getGlobal().info("data " + data.getBlockData().getAsString(true));
Logger.getGlobal().info("block " + block.getBlockData().getAsString(true));
}*/
            if(rpName(data.getId()).equals(rpName)
                    && data.matches(block)) {
                if(data instanceof SpecialBlockVanilla) {
                    vanillaMatches.add(data);
                } else {
                    specialMatches.add(data);
                }
            }
        }
        SpecialBlock result = null;
        for(SpecialBlock data: specialMatches) {
            if(result == null || data.getPriority() > result.getPriority()) {
                result = data;
            }
        }
        if(result != null) return searchInventories.get(rpName).getItem(result.getId());
        if(!vanillaMatches.isEmpty()) {
            return searchInventories.get(rpName).getItem(vanillaMatches.get(0).getId());
        }
        Material type = block.getType();
        if(type.isItem()) {
            return new ItemStack(getHandItem(block.getType()), 1);
        } else {
            return null;
        }
        //1.13 removed: return getHandItem(new ItemStack(block.getType(),1,(short)0,block.getData()));
    }

   public static ItemStack getItem(SpecialBlock block) {
        SearchInventory inventory = searchInventories.get(rpName(block.getId()));
        if (inventory != null) {
            return inventory.getItem(block.getId()).clone();
        } else {
            return null;
        }
    }

    public static SpecialBlock getSpecialBlockDataFromBlock(Block block, Player player, Class classFilter) {
        String rpName = RpManager.getCurrentRpName(player);
        SpecialBlock result = null;
        List<SpecialBlock> matches = new ArrayList<>();
        for(SpecialBlock data: blockList) {
            if(rpName(data.getId()).equals(rpName)
                    && data.matches(block)) {
                matches.add(data);
            }
        }
//Logger.getGlobal().info("SpecialBlockInventoryData.getSpecialBlockDataFromBlock: "+block+" "+item);
        for(SpecialBlock specialBlockData: matches) {
//Logger.getGlobal().info("SpecialBlock: "+specialBlockData+" Priority: "+specialBlockData.getPriority());
//Logger.getGlobal().info("Filter: "+classFilter);
//if(classFilter!=null) Logger.getGlobal().info("instance: "+classFilter.isInstance(specialBlockData));
            if(classFilter==null || classFilter.isInstance(specialBlockData)) {
                if(result==null || result.getPriority()<specialBlockData.getPriority()) {
                    result = specialBlockData;
                }
            }
        }
//Logger.getGlobal().info("Result SpecialBlock: "+result+" Priority: "+(result!=null?result.getPriority():""));
        return result;
    }
    
    private static Material getHandItem(Material material) {
        String name = material.name();
        if(material.name().contains("WALL_BANNER")) {
            return Material.valueOf(name.replace("WALL_BANNER", "BANNER"));
        }
        if(material.name().contains("WALL_SIGN")) {
            return Material.valueOf(name.replace("WALL_SIGN", "SIGN"));
        }
        if(material.name().contains("WALL_HANGING_SIGN")) {
            return Material.valueOf(name.replace("WALL_HANGING_SIGN", "HANGING_SIGN"));
        }
        return material;
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

    public static Material loadItemMaterial(ConfigurationSection config) {
        String materialName = config.getString("itemMaterial","");
        if(materialName.startsWith("LEGACY")) {
            materialName = ConversionUtil_1_13.convertItemName(materialName.substring(7));
            config.set("itemMaterial", materialName);
        }
        return Material.matchMaterial(materialName);
    }

    public static ItemMeta loadItemMeta(ItemMeta im, ConfigurationSection config) {
        if(im instanceof Damageable) {
            short dam = (short) config.getInt("damage",0);
            ((Damageable)im).setDamage(dam);
        } else {
            config.set("damage",null);
        }
        if(config.isInt("cmd")) {
            im.setCustomModelData(config.getInt("cmd"));
        }
        if(config.isInt("color")) {
            if(im instanceof LeatherArmorMeta armorMeta) {
                armorMeta.setColor(Color.fromRGB(config.getInt("color")));
            } else if(im instanceof PotionMeta potionMeta) {
                potionMeta.setColor(Color.fromRGB(config.getInt("color")));
            } else if(im instanceof FireworkEffectMeta fireworkMeta) {
                fireworkMeta.setEffect(FireworkEffect.builder()
                        .withColor(Color.fromRGB(config.getInt("color"))).build());
            }
        }
        return im;
    }

    private static ItemStack loadItemFromConfig(ConfigurationSection config, String name, String rp) {
        Material itemMat = loadItemMaterial(config);
        String displayName = (String) config.get("display");
        if(displayName==null) {
            displayName = name;
        }
        if(itemMat!=null) {
            try {
                ItemStack item = new ItemStack(itemMat, 1);
                ItemMeta im = loadItemMeta(item.getItemMeta(), config);
                im.setDisplayName(displayName);
                im.setLore(Arrays.asList(new String[]{SPECIAL_BLOCK_TAG, fullName(rp, name)}));
                im.setUnbreakable(true);
                im.addItemFlags(ItemFlag.HIDE_UNBREAKABLE);
                im.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
                item.setItemMeta(im);
                return item;
            } catch(IllegalArgumentException ex) {
                Logger.getLogger(SpecialBlockInventoryData.class.getName())
                        .warning("Not an item material: "+itemMat.name());
            }
        }
        return new ItemStack(Material.STONE);
    }

    public static boolean isSpecialBlockItem(ItemStack item) {
        return item.hasItemMeta()
                && item.getItemMeta().hasLore()
                && Objects.requireNonNull(item.getItemMeta().getLore()).contains(SPECIAL_BLOCK_TAG);
    }
    
    public static String fullName(String rpName, String name) {
        return rpName+"/"+name;
    }
    
    public static String rpName(String id) {
        return id.substring(0,id.indexOf("/"));
    }

    public static String getRpName(ItemStack item) {
        String rpN="";
        if(item.hasItemMeta()
                && item.getItemMeta().hasDisplayName()) {
            String displayName = item.getItemMeta().getDisplayName();
            if(displayName.indexOf(' ')>0) {
                displayName = displayName.substring(0,displayName.indexOf(' '));
            }
            if(!RpManager.getRpUrl(displayName,null).equalsIgnoreCase("")) {
                rpN = displayName;
            }
        }
        return rpN;
    }

    public static String getRpName(Player player) {
        ItemStack handItem = player.getInventory().getItemInMainHand();
        ItemStack offHandItem = player.getInventory().getItemInOffHand();
        String rpName = RpManager.getCurrentRpName(player);
        if (rpName == null || rpName.equals("")) {
            rpName = SpecialBlockInventoryData.getRpName(handItem);
            if (rpName.equals("")) {
                rpName = SpecialBlockInventoryData.getRpName(offHandItem);
            }
        }
        return rpName;
    }

    /*public static void setRecipes(String rpName) {
        SearchInventory inv = searchInventories.get(rpName);
        Bukkit.clearRecipes();
        if(inv!=null) {
            inv.setRecipes();
        }
    }*/
    public static Set<NamespacedKey> getRecipeKeys(String rpName) {
        SearchInventory inv = searchInventories.get(rpName);
        if(inv!=null) {
            return inv.getRecipeKeys();
        }
        return Sets.newHashSet();
    }

    public static Recipe getRecipe(NamespacedKey key, String rpName) {
        SearchInventory inv = searchInventories.get(rpName);
        if(inv!=null) {
            return inv.getRecipe(key);
        }
        return null;
    }
}
