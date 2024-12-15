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
import org.bukkit.*;
import org.bukkit.block.*;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.structure.Mirror;
import org.bukkit.block.structure.StructureRotation;
import org.bukkit.entity.ArmorStand;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.util.VoxelShape;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;
import java.util.logging.Logger;

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
        String[] itemBlockData = firstSplit[0].split("[:=\\[,\\]]");
Logger.getGlobal().info("itemBlockData: "+itemBlockData[1]);
        SpecialBlock specialBlock = SpecialBlockInventoryData.getSpecialBlock(itemBlockData[1]);
Logger.getGlobal().info("specialBlock: "+specialBlock);
        if(specialBlock instanceof SpecialBlockItemBlock) {
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
    public @NotNull Material getMaterial() {
        return blockData.getMaterial();
    }

    @Override
    public @NotNull String getAsString() {
        return NAMESPACE+":"+specialItemBlock.getId()+"[currentDamage:"+currentDamage+",yaw:"+yaw+"]::"+blockData.getAsString(false);
    }

    @Override
    public @NotNull String getAsString(boolean bln) {
        return getAsString();
    }

    @Override
    public @NotNull BlockData merge(@NotNull BlockData bd) {
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
    public @NotNull BlockData clone() {
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
    public @NotNull SoundGroup getSoundGroup() {
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
            public @NotNull Sound getBreakSound() {
                return Sound.ENTITY_ARMOR_STAND_BREAK;
            }

            @Override
            public @NotNull Sound getStepSound() {
                return Sound.ENTITY_ARMOR_STAND_PLACE;
            }

            @Override
            public @NotNull Sound getPlaceSound() {
                return Sound.ENTITY_ARMOR_STAND_PLACE;
            }

            @Override
            public @NotNull Sound getHitSound() {
                return Sound.ENTITY_ARMOR_STAND_HIT;
            }

            @Override
            public @NotNull Sound getFallSound() {
                return Sound.ENTITY_ARMOR_STAND_FALL;
            }
        };
    }

    @Override
    public int getLightEmission() {
        return 0;
    }

    @Override
    public boolean isOccluding() {
        return false;
    }

    @Override
    public boolean requiresCorrectToolForDrops() {
        return false;
    }

    @Override
    public boolean isSupported(@NotNull Block block) {
        return false;
    }

    @Override
    public boolean isSupported(@NotNull Location location) {
        return false;
    }

    @Override
    public boolean isFaceSturdy(@NotNull BlockFace blockFace, @NotNull BlockSupport blockSupport) {
        return false;
    }

    @Override
    public @NotNull VoxelShape getCollisionShape(@NotNull Location location) {
        return null;
    }

    @Override
    public @NotNull Color getMapColor() {
        return null;
    }

    @Override
    public @NotNull Material getPlacementMaterial() {
        return null;
    }

    @Override
    public void rotate(@NotNull StructureRotation structureRotation) {

    }

    @Override
    public void mirror(@NotNull Mirror mirror) {

    }

    @Override
    public void copyTo(@NotNull BlockData blockData) {

    }

    @Override
    public @NotNull BlockState createBlockState() {
        return null;
    }

    @Override
    public float getDestroySpeed(@NotNull ItemStack itemStack, boolean b) {
        return 0;
    }

    @Override
    public boolean isRandomlyTicked() {
        return false;
    }

    @Override
    public boolean isPreferredTool(@NotNull ItemStack itemStack) {
        return false;
    }

    @Override
    public @NotNull PistonMoveReaction getPistonMoveReaction() {
        return PistonMoveReaction.IGNORE;
    }

}
