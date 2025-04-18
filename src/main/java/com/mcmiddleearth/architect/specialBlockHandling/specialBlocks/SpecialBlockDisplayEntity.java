package com.mcmiddleearth.architect.specialBlockHandling.specialBlocks;

import com.mcmiddleearth.architect.ArchitectPlugin;
import com.mcmiddleearth.architect.chunkUpdate.ChunkUpdateUtil;
import com.mcmiddleearth.architect.specialBlockHandling.SpecialBlockType;
import com.mcmiddleearth.architect.specialBlockHandling.data.SpecialBlockInventoryData;
import com.mcmiddleearth.pluginutil.LegacyMaterialUtil;
import com.mcmiddleearth.pluginutil.NumericUtil;
import com.mcmiddleearth.util.DevUtil;
import net.kyori.adventure.text.serializer.json.JSONComponentSerializer;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.data.BlockData;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.logging.Level;
import java.util.logging.Logger;

public class SpecialBlockDisplayEntity extends SpecialBlock {

    private final String text;
    private final ItemStack item;
    private final BlockData blockData;

    public SpecialBlockDisplayEntity(String id, String text) {
        super(id, Bukkit.createBlockData(Material.AIR), SpecialBlockType.DISPLAY);
        this.text = text;
        this.item = null;
        this.blockData = null;
    }

    public SpecialBlockDisplayEntity(String id, ItemStack item) {
        super(id, Bukkit.createBlockData(Material.AIR), SpecialBlockType.DISPLAY);
        this.text = null;
        this.item = item;
        this.blockData = null;
    }

    public SpecialBlockDisplayEntity(String id, BlockData blockData) {
        super(id, Bukkit.createBlockData(Material.AIR), SpecialBlockType.DISPLAY);
        this.text = null;
        this.item = null;
        this.blockData = blockData;
    }

    public static SpecialBlock loadFromConfig(ConfigurationSection config, String id) {
        String blockData = config.getString("blockData", "");
        String text = config.getString("text", "");
        String itemMaterial = config.getString("itemMaterial", "");
        int cmd = config.getInt("cmd", 0);
        if(!text.isEmpty()) {
            return new SpecialBlockDisplayEntity(id, text);
        } else if(!blockData.isEmpty()) {
            return new SpecialBlockDisplayEntity(id, Bukkit.createBlockData(blockData));
        } else {
            ItemStack item = new ItemStack(Material.valueOf(itemMaterial));
            ItemMeta itemMeta = item.getItemMeta();
            itemMeta.setCustomModelData(cmd);
            item.setItemMeta(itemMeta);
            return new SpecialBlockDisplayEntity(id, item);
        }
    }

    public void placeBlock(final Block blockPlace, final BlockFace blockFace, final Block clicked,
                           final Location interactionPoint, final Player player) {
        if(text!=null) {
            TextDisplay entity = (TextDisplay) blockPlace.getWorld().spawnEntity(blockPlace.getLocation(),
                    EntityType.TEXT_DISPLAY);
            //entity.text(JSONComponentSerializer.json().deserialize(text));
            entity.text(LegacyComponentSerializer.legacySection().deserialize(text));
        } else if(blockData!=null) {
            BlockDisplay entity = (BlockDisplay) blockPlace.getWorld().spawnEntity(blockPlace.getLocation(),
                                                                                   EntityType.BLOCK_DISPLAY);
            entity.setBlock(blockData);
        } else {
            ItemDisplay entity = (ItemDisplay) blockPlace.getWorld().spawnEntity(blockPlace.getLocation(),
                                                                                 EntityType.ITEM_DISPLAY);
            entity.setItemStack(item);
        }
    }
}
