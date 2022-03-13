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
package com.mcmiddleearth.architect.specialBlockHandling.data;

import com.mcmiddleearth.architect.specialBlockHandling.specialBlocks.SpecialBlock;
import com.mcmiddleearth.architect.specialBlockHandling.specialBlocks.SpecialBlockItemBlock;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.SoundGroup;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.ArmorStand;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.Objects;

/**
 *
 * @author Eriol_Eandur
 */
public class ItemBlockData implements BlockData {
    
    private final SpecialBlockItemBlock specialItemBlock;
    private final BlockData blockData;
    private final int currentDamage;
    private final float yaw;
    
    public static final String NAMESPACE = "mcme";
    
    public static ItemBlockData createItemBlockData(Block block, String rpName) {
        ItemStack item = SpecialBlockInventoryData.getItem(block, rpName);
        if(item!=null) {
            SpecialBlock data = SpecialBlockInventoryData.getSpecialBlockDataFromItem(item);
            if(data instanceof SpecialBlockItemBlock) {
                SpecialBlockItemBlock itemBlockData = (SpecialBlockItemBlock) data;
                ArmorStand armorStand = SpecialBlockItemBlock.getArmorStand(block.getLocation());
                ItemStack contentItem = armorStand.getHelmet();
                ItemMeta meta = contentItem.getItemMeta();
                int contentDamage = 0;
                if(meta instanceof Damageable) {
                    contentDamage = ((Damageable)meta).getDamage();
                }
                float yaw = armorStand.getLocation().getYaw();
                return new ItemBlockData(block.getBlockData(), itemBlockData,contentDamage,yaw);
            }
        }
        return null;
    }
    
    public static ItemBlockData createItemBlockData(String data) {
        String[] firstSplit = data.split("::");
        String blockData = firstSplit[1];
        String[] itemBlockData = firstSplit[0].split("[:\\[,\\]]");
        SpecialBlock specialBlock = SpecialBlockInventoryData.getSpecialBlock(itemBlockData[1]);
        if(specialBlock != null && specialBlock instanceof SpecialBlockItemBlock) {
            int currentDamage;
            if (itemBlockData[3].equals("?")) {
                currentDamage = -1;
            } else {
                currentDamage = Integer.parseInt(itemBlockData[3]);
            }
            float yaw = Float.parseFloat(itemBlockData[5]);
            return new ItemBlockData(Bukkit.createBlockData(blockData),(SpecialBlockItemBlock) specialBlock, currentDamage, yaw);
        }
        return null;
    }
    
    public ItemBlockData(BlockData blockData, SpecialBlockItemBlock specialItemBlockData, int currentDamage, float yaw) {
        this.blockData = blockData;
        this.specialItemBlock = specialItemBlockData;
        this.currentDamage = currentDamage;
        this.yaw = yaw;
    }
    
    @Override
    public Material getMaterial() {
        return blockData.getMaterial();
    }

    @Override
    public String getAsString() {
        return NAMESPACE+":"+specialItemBlock.getId()+"[currentDamage:"+currentDamage+",yaw:"+yaw+"]::"+blockData.getAsString(false);
    }

    @Override
    public String getAsString(boolean bln) {
        return getAsString();
    }

    @Override
    public BlockData merge(BlockData bd) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public boolean matches(BlockData bd) {
        return blockData.matches(bd);
    }
    
    @Override
    public boolean equals(Object other) {
        if(other instanceof ItemBlockData) {
            ItemBlockData otherData = (ItemBlockData) other;
/*Logger.getGlobal().info("*****equals*****");
Logger.getGlobal().info(""+this.specialItemBlock.getId());
Logger.getGlobal().info(""+otherData.specialItemBlock.getId());
Logger.getGlobal().info(""+this.blockData.getAsString());
Logger.getGlobal().info(""+otherData.blockData.getAsString());
Logger.getGlobal().info(""+this.currentDamage);
Logger.getGlobal().info(""+otherData.currentDamage);
Logger.getGlobal().info(""+this.yaw);
Logger.getGlobal().info(""+otherData.yaw);
Logger.getGlobal().info("****************");*/
            return this.specialItemBlock.getId().equals(otherData.specialItemBlock.getId())
                && this.blockData.equals(otherData.blockData)
                && (this.currentDamage == otherData.currentDamage || this.currentDamage==-1 || otherData.currentDamage==-1)
                && this.yaw == otherData.yaw;
        }
        return false;
    }

    @Override
    public int hashCode() {
        int hash = 7;
        hash = 17 * hash + Objects.hashCode(this.specialItemBlock);
        hash = 17 * hash + Objects.hashCode(this.blockData);
        hash = 17 * hash + this.currentDamage;
        hash = 17 * hash + Float.floatToIntBits(this.yaw);
        return hash;
    }

    @Override
    public BlockData clone() {
        return new ItemBlockData(blockData.clone(),specialItemBlock,currentDamage,yaw);
    }

    public SpecialBlockItemBlock getSpecialItemBlock() {
        return specialItemBlock;
    }

    public BlockData getBlockData() {
        return blockData;
    }

    public int getCurrentDamage() {
        return currentDamage;
    }

    public float getYaw() {
        return yaw;
    }

    @Override
    public SoundGroup getSoundGroup() {
        return new SoundGroup() {

            @Override
            public float getVolume() {
                return 0;
            }

            @Override
            public float getPitch() {
                return 0;
            }

            @Override
            public Sound getBreakSound() {
                return Sound.ENTITY_ARMOR_STAND_BREAK;
            }

            @Override
            public Sound getStepSound() {
                return Sound.ENTITY_ARMOR_STAND_PLACE;
            }

            @Override
            public Sound getPlaceSound() {
                return Sound.ENTITY_ARMOR_STAND_PLACE;
            }

            @Override
            public Sound getHitSound() {
                return Sound.ENTITY_ARMOR_STAND_HIT;
            }

            @Override
            public Sound getFallSound() {
                return Sound.ENTITY_ARMOR_STAND_FALL;
            }
        };
    }
}
