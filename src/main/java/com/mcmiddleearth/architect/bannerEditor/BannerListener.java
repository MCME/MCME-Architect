/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.mcmiddleearth.architect.bannerEditor;

import com.mcmiddleearth.architect.ArchitectPlugin;
import com.mcmiddleearth.architect.Modules;
import com.mcmiddleearth.architect.Permission;
import com.mcmiddleearth.architect.PluginData;
import com.mcmiddleearth.pluginutil.EventUtil;
import java.util.List;
import java.util.logging.Logger;

import org.bukkit.DyeColor;
import org.bukkit.Material;
import org.bukkit.Registry;
import org.bukkit.block.Banner;
import org.bukkit.block.BlockState;
import org.bukkit.block.banner.Pattern;
import org.bukkit.block.banner.PatternType;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Directional;
import org.bukkit.block.data.Rotatable;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BannerMeta;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.scheduler.BukkitRunnable;

/**
 *
 * @author Eriol_Eandur
 */
public class BannerListener implements Listener {
    
    @EventHandler(ignoreCancelled = true)
    public void playerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        if(event.hasBlock() && player.getInventory().getItemInHand().getType().equals(Material.STICK)
                            && EventUtil.isMainHandEvent(event)) {
            BlockState state = event.getClickedBlock().getState();
            if(state instanceof Banner) {
                if(!PluginData.isModuleEnabled(player.getWorld(), Modules.BANNER_EDITOR)) {
                    sendNotEnabledErrorMessage(player);
                    return;
                }
                if(!PluginData.checkBuildPermissions(player,event.getClickedBlock().getLocation(),
                                                Permission.BANNER_EDITOR)) {
                    return;
                }
                Banner banner = (Banner) state;
                BannerEditorConfig config = BannerEditorCommand.getPlayerConfig(event.getPlayer());
                BannerEditorMode mode = config.getEditorMode();
                int patternId = config.getPatternId();
                switch(mode) {
                    case LIST:
                        sendBannerInfoMessage(player,banner);
                        break;
                    case TEXTURE:
                        if(patternId>0 
                                && patternId<=banner.numberOfPatterns()) {
                            banner.setPattern(patternId-1,
                                    new Pattern(banner.getPattern(patternId-1).getColor(),
                                                (PatternType) cycle(banner.getPattern(patternId-1).getPattern(),
                                                                    event.getAction())));
                            banner.update(true, false);
                        }
                        else if(patternId==0) {
                            sendNoPattern(player);
                        }
                        else {
                            sendInvalidPatternId(player,patternId);
                        }
                        break;
                    case COLOR:
                        if(patternId>0 
                                && patternId<=banner.numberOfPatterns()) {
                            banner.setPattern(patternId-1,
                                    new Pattern((DyeColor) cycle(banner.getPattern(patternId-1).getColor(),
                                                                 event.getAction()),
                                                banner.getPattern(patternId-1).getPattern()));
                            banner.update(true, false);
                        }
                        else if(patternId == 0) {
                            //banner.setBaseColor((DyeColor) cycle(banner.getBaseColor(),
                            //                                     event.getAction()));
                            BlockData oldData = banner.getBlockData();
                            banner.setType(cycle(banner.getType(),event.getAction()));
                            BlockData newData = banner.getBlockData();
                            if(newData instanceof Rotatable) {
                                ((Rotatable)newData).setRotation(((Rotatable)oldData).getRotation());
                            } else {
                                ((Directional)newData).setFacing(((Directional)oldData).getFacing());
                            }
                            banner.update(true, false);
                            new BukkitRunnable() {
                                @Override
                                public void run() {
                                    banner.setBlockData(newData);
                                    banner.update(true, false);
                                }
                            }.runTaskLater(ArchitectPlugin.getPluginInstance(),1);
                        }
                        else {
                            sendInvalidPatternId(player,patternId);
                        }
                        break;
                    case ADD:
                        banner.addPattern(new Pattern(DyeColor.WHITE,PatternType.CIRCLE));
                        banner.update(true, false);
                        break;
                    case REMOVE:
                        if(patternId>0 
                                && patternId<=banner.numberOfPatterns()) {
                            Pattern pat = banner.removePattern(patternId-1);
                            banner.update(true, false);
                        }
                        else if(patternId==0){
                            sendBaseNoPattern(player);
                        }
                        else {
                            sendInvalidPatternId(player, patternId);
                        }
                        break;
                    case SHIELD:
                        ItemStack shieldItem = new ItemStack(Material.SHIELD);
                        BlockStateMeta shieldMeta = (BlockStateMeta) shieldItem.getItemMeta();
                        shieldMeta.setBlockState(banner);
                        shieldItem.setItemMeta(shieldMeta);
                        int amoun = shieldItem.getMaxStackSize();
                        shieldItem.setAmount(amoun);
                        player.getInventory().addItem(shieldItem);
                        sendGotShield(player, amoun);
                        break;
                    case GET:
                        ItemStack item = new ItemStack(Material.valueOf(banner.getBaseColor().name().toUpperCase()
                                                                        +"_BANNER"));
                        BannerMeta meta = (BannerMeta) item.getItemMeta();
                        //meta.setBaseColor(banner.getBaseColor());
                        for(Pattern pattern: banner.getPatterns()) {
                            meta.addPattern(pattern);
                        }
                        item.setItemMeta(meta);
                        int amount = item.getMaxStackSize();
                        item.setAmount(amount);
                        player.getInventory().addItem(item);
                        sendGotBanner(player, amount);
                }
                event.setCancelled(true);
            }
        }
    }

    private PatternType cycle(PatternType current, Action direction) {
        PatternType[] types = Registry.BANNER_PATTERN.stream().toArray(PatternType[]::new);// toArray(new PatternType[0]);PatternType.values();
        for(int i = 0; i< types.length; i++) {
            if (types[i].equals(current)) {
                return (PatternType) cycle(types, i, direction);
            }
        }
        return current;
        //int ordinal = Registry.BLOCK.iterator()current.ordinal();
        //return (PatternType) cycle(types, ordinal, direction);
    }
    
    private DyeColor cycle(DyeColor current, Action direction) {
        DyeColor[] types = DyeColor.values();
        int ordinal = current.ordinal();
        return (DyeColor) cycle(types, ordinal, direction);
    }
    
    private Material cycle(Material current, Action direction) {
        Material[] types;
        if(!BannerUtil.isWallBanner(current)) {
            types = new Material[]{Material.WHITE_BANNER,Material.ORANGE_BANNER,Material.MAGENTA_BANNER,Material.LIGHT_BLUE_BANNER,
                                   Material.YELLOW_BANNER,Material.LIME_BANNER,Material.PINK_BANNER,Material.GRAY_BANNER,
                                   Material.LIGHT_GRAY_BANNER,Material.CYAN_BANNER,Material.PURPLE_BANNER,Material.BLUE_BANNER,
                                   Material.BROWN_BANNER,Material.GREEN_BANNER,Material.RED_BANNER,Material.BLACK_BANNER};
        } else {
            types = new Material[]{Material.WHITE_WALL_BANNER,Material.ORANGE_WALL_BANNER,Material.MAGENTA_WALL_BANNER,Material.LIGHT_BLUE_WALL_BANNER,
                                   Material.YELLOW_WALL_BANNER,Material.LIME_WALL_BANNER,Material.PINK_WALL_BANNER,Material.GRAY_WALL_BANNER,
                                   Material.LIGHT_GRAY_WALL_BANNER,Material.CYAN_WALL_BANNER,Material.PURPLE_WALL_BANNER,Material.BLUE_WALL_BANNER,
                                   Material.BROWN_WALL_BANNER,Material.GREEN_WALL_BANNER,Material.RED_WALL_BANNER,Material.BLACK_WALL_BANNER};
        }
        int ordinal = 0;
        for(int i = 0; i< types.length; i++) {
            if(types[i].equals(current)) {
                ordinal = i;
                break;
            }
        }
        return (Material) cycle(types, ordinal, direction);
    }
    
    private Object cycle(Object[] types, int ordinal, Action direction) {
        if(direction.equals(Action.LEFT_CLICK_BLOCK)) {
            ordinal++;
            if(ordinal==types.length) {
                ordinal = 0;
            }
        }
        else {
            ordinal--;
            if(ordinal<0) {
                ordinal = types.length-1;
            }
        }
        return types[ordinal];
    }

    private void sendInvalidPatternId(Player player, int id) {
        PluginData.getMessageUtil().sendErrorMessage(player,"This banner doesn't have "+ id + " patterns.");
    }
    
    private void sendBannerInfoMessage(Player player, Banner banner) {
        List<Pattern> patterns = banner.getPatterns();
        PluginData.getMessageUtil().sendInfoMessage(player,"Base color (ID 0): "+ banner.getBaseColor().toString());
        int id = 1;
        for(Pattern pattern: patterns) {
            PluginData.getMessageUtil().sendInfoMessage(player,"ID "+ id+": "+ pattern.getPattern().toString()+" - "
                                           + pattern.getColor().toString());
            id++;
        }
    }
    
    private void sendNoPattern(Player player) {
        PluginData.getMessageUtil().sendErrorMessage(player,"You can change the color of the base banner only.");
    }

    private void sendBaseNoPattern(Player player) {
        PluginData.getMessageUtil().sendErrorMessage(player,"The banner base has no texture to remove.");
    }

    private void sendBaseNoColor(Player player) {
        PluginData.getMessageUtil().sendErrorMessage(player,"The banner base color can't .");
    }

    private void sendGotBanner(Player player, int amount) {
        PluginData.getMessageUtil().sendInfoMessage(player,"Given "+amount+" banners to "+player.getName()+".");
    }

    private void sendGotShield(Player player, int amount) {
        PluginData.getMessageUtil().sendInfoMessage(player,"Given "+amount+" shields to "+player.getName()+".");
    }

    private void sendNotEnabledErrorMessage(CommandSender cs) {
        PluginData.getMessageUtil().sendErrorMessage(cs, "Banner editor is not enabled for this world.");
    }
    

}