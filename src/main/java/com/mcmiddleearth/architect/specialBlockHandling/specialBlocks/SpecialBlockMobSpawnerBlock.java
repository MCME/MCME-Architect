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

import com.mcmiddleearth.architect.PluginData;
import com.mcmiddleearth.architect.specialBlockHandling.SpecialBlockType;
import com.mcmiddleearth.pluginutil.NBTTagBuilder;
import com.mcmiddleearth.pluginutil.NMSUtil;
import com.mcmiddleearth.pluginutil.NumericUtil;
import com.mcmiddleearth.util.DevUtil;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 *
 * @author Eriol_Eandur
 */
public class SpecialBlockMobSpawnerBlock extends SpecialBlock {
    
    protected Material contentItem;
    protected Short[] contentDamage;
    
    private SpecialBlockMobSpawnerBlock(String id, 
                        Material contentItem,
                        Short[] contentDamage) {
        this(id, contentItem, contentDamage,
                SpecialBlockType.MOB_SPAWNER_BLOCK);
    }
    
    protected SpecialBlockMobSpawnerBlock(String id, 
                        Material contentItem,
                        Short[] contentDamage,
                        SpecialBlockType type) {
        super(id, Material.SPAWNER.createBlockData(), type);
        this.contentItem = contentItem;
        this.contentDamage = contentDamage;
    }
    
    public static SpecialBlockMobSpawnerBlock loadFromConfig(ConfigurationSection config, String id) {
        Material contentItem = matchMaterial(config.getString("contentItem",""));
        Short[] contentDamage = getContentDamage(config.getString("contentDamage","0"));
        return new SpecialBlockMobSpawnerBlock(id, contentItem, 
                                         contentDamage);
    }
    
    @Override
    public void placeBlock(final Block blockPlace, final BlockFace blockFace, Block clicked,
                           final Location interactionPoint, final Player player) {
        final Location playerLoc = player.getLocation();
        if(!PluginData.moreEntitiesAllowed(blockPlace)) {
            int count = PluginData.countNearbyEntities(blockPlace);
            PluginData.getMessageUtil().sendErrorMessage(player, "WARNING! Already "+count+" entities (paintings, item frames, item blocks and armorstands) around here. Placing more will cause lag.");
        }
        super.placeBlock(blockPlace, blockFace, clicked, interactionPoint, player);
        final Location loc = blockPlace.getLocation();
        final BlockState state = getBlockState(blockPlace, blockFace, playerLoc);
        state.setType(Material.SPAWNER);
        state.getBlock().setBlockData(state.getBlockData(),false);//.update(true, false);
        final BlockState spawner = blockPlace.getState();
        if(! spawner.getType().equals(Material.SPAWNER)) {
            DevUtil.log("Mob spawner block place failed: "+loc.getBlockX()+" - "
                                                          +loc.getBlockY()+" - "
                                                          +loc.getBlockZ()+" - "
                                                          +spawner.getType().name());
            return;
        }
        ItemStack item = new ItemStack(contentItem,1,
                                       contentDamage[NumericUtil.getRandom(0, contentDamage.length-1)]);
        /*boolean useNMS = true;
        if(!useNMS) {
            String nbtCommand = "execute "+player.getName()+" ~ ~ ~ "
                +"blockdata "+loc.getBlockX()+" "
                             +loc.getBlockY()+" "
                             +loc.getBlockZ()+" "
                +"{RequiredPlayerRange:0s,SpawnData:{id:\"minecraft:armor_stand\",Invisible:0,Marker:1"
                //+ ",ArmorItems:[\"0\":{},\"1\":{},\"2\":{},\"3\":{id:\"minecraft:"+contentItem.name().toLowerCase()+"\","
                //+ "Count:1b,Damage:"+contentDamage[NumericUtil.getRandom(0, contentDamage.length-1)]+"s,"
                //+ "tag:{Unbreakable:1}}]"+
                +"}}";
            DevUtil.log("Dispatch command: "+nbtCommand);
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), nbtCommand);
        } else {*/
        try {
            Object nmsSpawnerTileEntity = NMSUtil.getTileEntity(blockPlace);
            final Object nmsSpawner = nmsSpawnerTileEntity.getClass().getMethod("getSpawner")
                                                               .invoke(nmsSpawnerTileEntity);
            NBTTagBuilder itemDataBuilder = new NBTTagBuilder();
            itemDataBuilder.setString("id", "minecraft:"+contentItem.name().toLowerCase())
                           .setByte("Count", (byte)1)
                           .setShort("Damage",item.getDurability())
                           .setTag("tag", new NBTTagBuilder().setBoolean("Unbreakable",true).getTag());
            //NBTTagBuilder poseDataBuilder = new NBTTagBuilder();
            //poseDataBuilder.setFloatList("Head", 0f,40f,0f);
            NBTTagBuilder spawnDataBuilder = new NBTTagBuilder();
            Object emptyTag = new NBTTagBuilder().getTag();
            spawnDataBuilder.setString("id","minecraft:armor_stand")
                            .setBoolean("Invisible", true)
                            .setBoolean("Marker", true)
                            .setTagList("ArmorItems",emptyTag, emptyTag, emptyTag, itemDataBuilder.getTag());
                            //.setTag("Pose",poseDataBuilder.getTag());
            /*spawnDataBuilder.setString("id", "minecraft:item")
                    .setShort("Age", (short) -32768)
                    .setTag("Item", itemDataBuilder.getTag());*/
            NBTTagBuilder blockDataBuilder = new NBTTagBuilder();
            blockDataBuilder.setShort("RequiredPlayerRange", (short) 0)
                            .setShort("MaxNearbyEntities", (short) 87)
                            .setTag("SpawnData",spawnDataBuilder.getTag());
            nmsSpawner.getClass().getMethod("a", NMSUtil.getNMSClass("NBTTagCompound"))
                                 .invoke(nmsSpawner, blockDataBuilder.getTag());
            /*ReflectionUtil.showFields(nmsSpawner);
            Field e = nmsSpawner.getClass().getDeclaredField("e");
            e.setAccessible(true);
            e.set(nmsSpawner, 0d);
            Field d = nmsSpawner.getClass().getDeclaredField("d");
            d.setAccessible(true);
            d.set(nmsSpawner, 0d);*/
            //NMSUtil.sendPacket(player, nmsSpawnerTileEntity.getClass().getMethod("getUpdatePacket")
            //                                                          .invoke(nmsSpawnerTileEntity));
        } catch (NoSuchMethodException | SecurityException | IllegalAccessException | IllegalArgumentException | InvocationTargetException | InstantiationException | ClassNotFoundException ex) {
            Logger.getLogger(SpecialBlockMobSpawnerBlock.class.getName()).log(Level.SEVERE, null, ex);
        }
    }
    
    
    protected static Short[] getContentDamage(String data) {
        Scanner scanner = new Scanner(data);
            scanner.useDelimiter(",");
            List<Short> contentDamageList = new ArrayList<>();
            while(scanner.hasNext()) {
                String dataValue = scanner.next();
                if(NumericUtil.isShort(dataValue)) {
                    short value = (short) NumericUtil.getShort(dataValue);
                    contentDamageList.add(value);
                }
            }
        return contentDamageList.toArray(new Short[contentDamageList.size()]);
    }
    
    @Override
    public boolean matches(Block block) {
        if(super.matches(block)) {
            /*ArmorStand holder = getArmorStand(block.getLocation());
            if(holder!=null) {
                ItemStack content = holder.getHelmet();
                if(content.getType().equals(contentItem)) {
                    for(short damage: contentDamage) {
                        if(damage == content.getDurability()) {
                            return true;
                        }
                    }
                }
            }*/
        }
        return false;
    }

    public short getNextDurability(short currentDurability) {
        for(int i=0;i<contentDamage.length;i++) {
            if(contentDamage[i]==currentDurability) {
                return ((i+1)<contentDamage.length?contentDamage[i+1]:contentDamage[0]);
            }
        }
        return contentDamage[0];
    }
    
    public short getPreviousDurability(short currentDurability) {
        for(int i=0;i<contentDamage.length;i++) {
            if(contentDamage[i]==currentDurability) {
                return ((i-1)>=0?contentDamage[i-1]:contentDamage[contentDamage.length-1]);
            }
        }
        return contentDamage[0];
    }
    

}
