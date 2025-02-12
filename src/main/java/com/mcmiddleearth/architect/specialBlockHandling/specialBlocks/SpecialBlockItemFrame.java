package com.mcmiddleearth.architect.specialBlockHandling.specialBlocks;

import com.mcmiddleearth.architect.ArchitectPlugin;
import com.mcmiddleearth.architect.specialBlockHandling.SpecialBlockType;
import com.mcmiddleearth.architect.specialBlockHandling.data.SpecialBlockInventoryData;
import com.mcmiddleearth.util.DevUtil;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Rotation;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitRunnable;

public class SpecialBlockItemFrame extends SpecialBlock {

    private final ItemStack item;
    private final Rotation rotation;

    private SpecialBlockItemFrame(String id, ItemStack item, Rotation rotation) {
        super(id, Bukkit.createBlockData(Material.AIR), SpecialBlockType.ITEM_FRAME);
        this.item = item;
        this.rotation = rotation;
    }

    public static SpecialBlock loadFromConfig(ConfigurationSection config, String id) {
        Material itemMaterial = SpecialBlockInventoryData.loadItemMaterial(config);
        if(itemMaterial!=null) {
            ItemStack item = new ItemStack(itemMaterial);
            ItemMeta meta = SpecialBlockInventoryData.loadItemMeta(item.getItemMeta(), config);
            item.setItemMeta(meta);
            Rotation rotation = Rotation.valueOf(config.getString("rotation", Rotation.NONE.name()));
            return new SpecialBlockItemFrame(id, item, rotation);
        }
        return null;
    }

    @Override
    public void placeBlock(Block blockPlace, BlockFace blockFace, Block clicked, Location interactionPoint, Player player) {
        new BukkitRunnable() {
            @Override
            public void run() {
                blockPlace.setBlockData(Material.AIR.createBlockData(),false);
                DevUtil.log("Special block place item frame");
                final BlockState tempState = getBlockState(blockPlace, clicked, blockFace, player, interactionPoint);
                new BukkitRunnable() {
                    @Override
                    public void run() {
                        ItemFrame itemFrame = (ItemFrame) blockPlace.getWorld()
                                                                .spawnEntity(blockPlace.getLocation(), EntityType.ITEM_FRAME);
                        itemFrame.setFacingDirection(blockFace);
                        itemFrame.setItem(item.clone());
                        itemFrame.setRotation(rotation);
                        itemFrame.setVisible(false);
                        itemFrame.setFixed(true);
                        DevUtil.log("Special block place item frame x2");
                    }
                }.runTaskLater(ArchitectPlugin.getPluginInstance(), 2);
            }
        }.runTaskLater(ArchitectPlugin.getPluginInstance(), 1);
    }
}
