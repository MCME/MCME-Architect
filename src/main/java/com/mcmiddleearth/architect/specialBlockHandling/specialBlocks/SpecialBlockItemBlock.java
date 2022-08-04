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
import com.mcmiddleearth.architect.PluginData;
import com.mcmiddleearth.architect.armorStand.ArmorStandUtil;
import com.mcmiddleearth.architect.specialBlockHandling.SpecialBlockType;
import com.mcmiddleearth.architect.specialBlockHandling.data.SpecialBlockInventoryData;
import com.mcmiddleearth.architect.specialBlockHandling.itemBlock.ItemBlockManager;
import com.mcmiddleearth.pluginutil.LegacyMaterialUtil;
import com.mcmiddleearth.pluginutil.NumericUtil;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Scanner;
import java.util.logging.Logger;

/**
 *
 * @author Eriol_Eandur
 */
public class SpecialBlockItemBlock extends SpecialBlock {
    
    public static final String PREFIX = "iBE_";
    public static final String ID_DELIMITER = "_id_";
    
    protected Material contentItem;
    protected Integer[] contentDamage;
    private final double contentHeight;
    
    private SpecialBlockItemBlock(String id, 
                        BlockData blockData,
                        Material contentItem,
                        Integer[] contentDamage,
                        double contentHeight) {
        this(id, blockData, contentItem, contentDamage, contentHeight,
                SpecialBlockType.ITEM_BLOCK);
    }
    
    protected SpecialBlockItemBlock(String id, 
                        BlockData blockData,
                        Material contentItem,
                        Integer[] contentDamage,
                        double contentHeight,
                        SpecialBlockType type) {
        super(id, blockData, type);
        this.contentItem = contentItem;
        this.contentDamage = contentDamage;
        this.contentHeight = contentHeight;
    }
    
    public static SpecialBlockItemBlock loadFromConfig(ConfigurationSection config, String id) {
        BlockData data;
        //convert old data
        if(!config.contains("blockData")) {
            Material blockMat =  Material.matchMaterial(config.getString("blockMaterial",""));
            byte rawData = (byte) config.getInt("blockDataValue", 0);
            data = LegacyMaterialUtil.getBlockData(blockMat, rawData);
            if(data == null) {
                return null;
            }
            config.set("blockData", data.getAsString());
            config.set("blockMaterial", null);
            config.set("blockDataValue",null);
        // end convert old data
        }else {
            try {
                data = Bukkit.getServer().createBlockData(config.getString("blockData",""));
            } catch(IllegalArgumentException e) {
                return null;
            }
        }
        Material contentItem = matchMaterial(config.getString("contentItem",""));
        Integer[] contentDamage;
        if (config.isSet("contentCmd")) {
            contentDamage = getContentDamage(config.getString("contentCmd","0"));
        } else {
            contentDamage = getContentDamage(config.getString("contentDamage", "0"));
        }
        double contentHeight = config.getDouble("contentHeight",0);
        return new SpecialBlockItemBlock(id, data, contentItem, 
                                         contentDamage, contentHeight);
    }
    
    @Override
    public void placeBlock(final Block blockPlace, final BlockFace blockFace, Block clicked,
                           final Location interactionPoint, final Player player) {
        final Location playerLoc = player.getLocation();
        if (ItemBlockManager.allowPlace(blockPlace, player)) {
            super.placeBlock(blockPlace, blockFace, clicked, interactionPoint, player);
            placeArmorStand(blockPlace, blockFace, playerLoc,contentDamage[NumericUtil.getRandom(0, contentDamage.length-1)]);
        } else {
            PluginData.getMessageUtil().sendErrorMessage(player, "Too many entities (paintings, item frames, item blocks and armorstands) in this chunk already. (Limit: "
                                                                 +ItemBlockManager.getLimit(blockPlace)+")");
        }
    }
    
    public void placeArmorStand(Block blockPlace, BlockFace blockFace, Location playerLoc, int currentDamage) {
//Logger.getGlobal().info("Place item block: "+currentDamage);
        Location loc = getArmorStandLocation(blockPlace, blockFace, playerLoc);
        removeArmorStands(blockPlace.getLocation());
        final ArmorStand armor = (ArmorStand) blockPlace.getWorld().spawnEntity(loc, EntityType.ARMOR_STAND);
        armor.setVisible(false);
        armor.setGravity(false);
        armor.setCustomName(getArmorStandName(blockPlace)+ID_DELIMITER+getId());
        armor.addScoreboardTag(ArmorStandUtil.LOCKED);
        new BukkitRunnable() {
            @Override
            public void run() {
                ItemStack item = new ItemStack(contentItem,1);
                ItemMeta meta = item.getItemMeta();
                if(meta instanceof Damageable && item.getType().equals(Material.DIAMOND_CHESTPLATE)) {
                    ((Damageable) meta).setDamage(currentDamage);
                } else {
                    meta.setCustomModelData(currentDamage);
                }
                item.setItemMeta(meta);
                armor.setHelmet(item);
            }
        }.runTaskLater(ArchitectPlugin.getPluginInstance(), 2);
    }
    
    public Block getBlock(Location armorLoc) {
        World world = armorLoc.getWorld();
        Location blockLoc = new Location(world,armorLoc.getX(),
                                               armorLoc.getY()+2-contentHeight+0.01,
                                               armorLoc.getZ());
        return world.getBlockAt(blockLoc);
    }
    
    public boolean isArmorStandChanged(ArmorStand armor, Block block) {
        Location originalLoc = getArmorStandLocation(block,BlockFace.UP,block.getLocation());
        Location armorLoc = armor.getLocation();
        return !(matches(armorLoc.getX(),originalLoc.getX())
              && matches(armorLoc.getY(),originalLoc.getY())
              && matches(armorLoc.getZ(),originalLoc.getZ()));
    }
    
    private boolean matches(double d1, double d2) {
        double diff = 0.01;
        return Math.abs(d2-d1)<diff;
    }
    
    protected Location getArmorStandLocation(Block blockPlace, BlockFace blockFace, Location playerLoc) {
        return new Location(blockPlace.getWorld(), blockPlace.getX()+0.5, 
                                    blockPlace.getY()-2+contentHeight, blockPlace.getZ()+0.5);
    }
    
    protected static String getArmorStandName(Block blockPlace) {
        return PREFIX+blockPlace.getX()+"_"+blockPlace.getY()+"_"+blockPlace.getZ();
    }
    
    public static String getIdFromArmorStandName(String name) {
        return name.substring(name.indexOf(ID_DELIMITER)+ID_DELIMITER.length()).replace("2Gondor", "Gondor");
    }
    
    public static String getIdFromArmorStand(ArmorStand armorStand) {
        return getIdFromArmorStandName(armorStand.getCustomName());
    }
    
    public static boolean isItemBlockArmorStand(ArmorStand armorStand) {
        return armorStand.getCustomName()!=null 
           &&  armorStand.getCustomName().startsWith(PREFIX);
    }
    
    public static int getContentDamage(ArmorStand armorStand) {
        ItemStack helmet = armorStand.getHelmet();
        ItemMeta meta = helmet.getItemMeta();
        if(meta != null) {
            return ((Damageable) armorStand.getHelmet().getItemMeta()).getDamage();
        }
        return 0;
    }
    
    public static int[] getCoordinatesFromArmorStand(ArmorStand armorStand) {
        String[] data = armorStand.getCustomName().split("_");
        return new int[]{Integer.parseInt(data[1]),Integer.parseInt(data[2]),Integer.parseInt(data[3])};
    }
    
    public int getNextDurability(int currentDurability) {
        for(int i=0;i<contentDamage.length;i++) {
            if(contentDamage[i]==currentDurability) {
                return ((i+1)<contentDamage.length?contentDamage[i+1]:contentDamage[0]);
            }
        }
        return contentDamage[0];
    }
    
    public int getPreviousDurability(int currentDurability) {
        for(int i=0;i<contentDamage.length;i++) {
            if(contentDamage[i]==currentDurability) {
                return ((i-1)>=0?contentDamage[i-1]:contentDamage[contentDamage.length-1]);
            }
        }
        return contentDamage[0];
    }
    
    protected static Integer[] getContentDamage(String data) {
        Scanner scanner = new Scanner(data);
            scanner.useDelimiter(",");
            List<Integer> contentDamageList = new ArrayList<>();
            while(scanner.hasNext()) {
                String dataValue = scanner.next();
                if(NumericUtil.isInt(dataValue)) {
                    int value = NumericUtil.getInt(dataValue);
                    contentDamageList.add(value);
                }
            }
        return contentDamageList.toArray(new Integer[contentDamageList.size()]);
    }
    
    public static void removeArmorStands(Location loc) {
        removeArmorStands(loc, 0.5, 2, true);
    }
    
    public static void removeArmorStands(Location loc, double xzRadius, double yRadius, boolean exactMatch) {
        for(Entity entity: loc.getBlock().getWorld().getNearbyEntities(loc, xzRadius, yRadius, xzRadius)) {
            if(entity instanceof ArmorStand && entity.getCustomName()!=null
                    && (!exactMatch || entity.getCustomName().startsWith(getArmorStandName(loc.getBlock())))) {
                entity.remove();
            }
        }
    }
    
    public static ArmorStand getArmorStand(Location loc) {
        Location center = new Location(loc.getWorld(),loc.getBlockX()+0.5,loc.getBlockY()-1.5,loc.getBlockZ()+0.5);
        for(Entity entity: loc.getBlock().getWorld().getNearbyEntities(center, 0.5, 1, 0.5)) {
            if(entity instanceof ArmorStand && entity.getCustomName()!=null
                    && isItemBlockArmorStand((ArmorStand)entity)) {
                SpecialBlockItemBlock specialBlock = (SpecialBlockItemBlock) SpecialBlockInventoryData
                                                             .getSpecialBlock(SpecialBlockItemBlock
                                                                              .getIdFromArmorStand((ArmorStand)entity));
                if(!specialBlock.isArmorStandChanged((ArmorStand)entity, loc.getBlock())) {
                    return (ArmorStand) entity;
                }
            }
        }
        return null;
    }

    public static List<ItemBlockStat> getStatistic(Location loc, double xzRadius, double yRadius) {
        List<ItemBlockStat> stats = new ArrayList<>();
        for(Entity entity: loc.getBlock().getWorld().getNearbyEntities(loc, xzRadius, yRadius, xzRadius)) {
            if(entity instanceof ArmorStand && entity.getCustomName()!=null
                    && ((ArmorStand)entity).getHelmet()!=null) {
                ItemStack content = ((ArmorStand)entity).getHelmet();
                boolean found = false;
                for(ItemBlockStat entry: stats) {
                    if(entry.getItem().getType().equals(content.getType()) 
                            && entry.getItem().getDurability()==content.getDurability()) {
                        entry.increase();
                        found = true;
                        break;
                    }
                }
                if(!found) {
                    stats.add(new ItemBlockStat(content));
                }
            }
        }
        Collections.sort(stats);
        return stats;
    }

    public static class ItemBlockStat implements Comparable<ItemBlockStat>{
        
        private int count;
        private final ItemStack item;


        public ItemBlockStat(ItemStack item) {
            this.item = item;
            count = 1;
        }
        
        public void increase() {
            count++;
        }
        
        @Override
        public int compareTo(ItemBlockStat otherStat) {
            return count==otherStat.getCount()?0:(count<otherStat.getCount()?-1:1);
        }

        public int getCount() {
            return count;
        }

        public ItemStack getItem() {
            return item;
        }
    }
    
    @Override
    public boolean matches(Block block) {
        if(super.matches(block)) {
            ArmorStand holder = getArmorStand(block.getLocation());
            if(holder!=null) {
                ItemStack content = holder.getHelmet();
                if(content.getType().equals(contentItem)) {
                    for(int damage: contentDamage) {
                        if(damage == content.getDurability()) {
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }

    public Material getContentItem() {
        return contentItem;
    }

    public Integer[] getContentDamage() {
        return contentDamage;
    }

    public double getContentHeight() {
        return contentHeight;
    }
}
