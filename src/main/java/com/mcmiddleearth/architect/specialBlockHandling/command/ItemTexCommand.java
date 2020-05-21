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
package com.mcmiddleearth.architect.specialBlockHandling.command;

import com.mcmiddleearth.architect.Modules;
import com.mcmiddleearth.architect.Permission;
import com.mcmiddleearth.architect.PluginData;
import com.mcmiddleearth.architect.additionalCommands.AbstractArchitectCommand;
import com.mcmiddleearth.pluginutil.NumericUtil;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 *
 * @author EriolEandur
 */
public class ItemTexCommand extends AbstractArchitectCommand {

    @Override
    public boolean onCommand(CommandSender cs, Command command, String label, String[] args) {
        if (!(cs instanceof Player)) {
            PluginData.getMessageUtil().sendPlayerOnlyCommandError(cs);
            return true;
        }
        Player p = (Player) cs;
        if(!PluginData.hasPermission(p,Permission.ITEM_TEX_COMMAND)) {
            PluginData.getMessageUtil().sendNoPermissionError(cs);
            return true;
        }
        if(!PluginData.isModuleEnabled(p.getWorld(), Modules.ITEM_TEXTURES)) {
            sendNotEnabledErrorMessage(cs);
            return true;
        }
        if(args.length==0 || args[0].equalsIgnoreCase("help")) {
            int page = 1;
            if(args.length>1 && NumericUtil.isInt(args[1])) {
                page = NumericUtil.getInt(args[1]);
            }
            sendHelpMessage((Player)cs,page);
            return true;
        }
/*        if(args[0].equalsIgnoreCase("add")) {
            if(!PluginData.hasPermission(p, Permission.ITEM_TEX_MANAGER)) {
                PluginData.getMessageUtil().sendNoPermissionError(cs);
                return true;
            }
            if(args.length<3) {
                PluginData.getMessageUtil().sendNotEnoughArgumentsError(p);
                return true;
            }
            if(!NumericUtil.isInt(args[2])) {
                sendNotANumberMessage(p);
                return true;
            }
            if(SpecialInventoryData.addItem(args[1])) {
                sendItemAddedMessage(p);
            } else {
                sendItemAlreadyExistsMessage(p);
            }
            return true;
        }
        if(args[0].equalsIgnoreCase("remove")) {
            if(!PluginData.hasPermission(p, Permission.ITEM_TEX_MANAGER)) {
                PluginData.getMessageUtil().sendNoPermissionError(cs);
                return true;
            }
            if(args.length<2) {
                PluginData.getMessageUtil().sendNotEnoughArgumentsError(p);
                return true;
            }
            if(SpecialInventoryData.removeItem(args[1])) {
                sendItemRemovedMessage(p);
            } else {
                sendItemNotFoundMessage(p);
            }
            return true;
        }*/
        if(!NumericUtil.isInt(args[0])) {
            sendNotANumberMessage(p);
            return true;
        }
        ItemStack item = p.getInventory().getItemInHand();
        if(item == null) {
            sendNoItemMessage(p);
            return true;
        }
        item.setDurability((short) NumericUtil.getInt(args[0]));
        return true;
    }
    
    private void sendNotEnabledErrorMessage(CommandSender cs) {
        PluginData.getMessageUtil().sendErrorMessage(cs, "Item textures based on durability are not enabled in this world.");
    }
    
    private void sendItemNotFoundMessage(CommandSender cs) {
        PluginData.getMessageUtil().sendErrorMessage(cs, "Item not found in build inventory");
    }
    
    private void sendItemRemovedMessage(CommandSender cs) {
        PluginData.getMessageUtil().sendInfoMessage(cs, "Item removed from build inventory.");
    }
    
    private void sendItemAlreadyExistsMessage(CommandSender cs) {
        PluginData.getMessageUtil().sendErrorMessage(cs, "Item already exists in buid inventory.");
    }
    
    private void sendItemAddedMessage(CommandSender cs) {
        PluginData.getMessageUtil().sendInfoMessage(cs, "Item added to build inventory.");
    }
    
    private void sendNoItemMessage(Player p) {
        PluginData.getMessageUtil().sendErrorMessage(p, "No item in main hand.");
    }
    
    private void sendNotANumberMessage(Player p) {
        PluginData.getMessageUtil().sendErrorMessage(p, "You need to specify a texture number.");
    }
    
    @Override
    public String getHelpPermission() {
        return Permission.ITEM_TEX_COMMAND.getPermissionNode();
    }

    @Override
    public String getShortDescription() {
        return ": Change texture of an item (durability).";
    }
    @Override
    public String getUsageDescription() {
        return " <index>: Switches the item in your had to the texture associated with <index>. \n "
                +ChatColor.WHITE+"Click for detailed help.";
    }
    
    @Override
    public String getHelpCommand() {
        return "/itemTex help";
    }

    

}
