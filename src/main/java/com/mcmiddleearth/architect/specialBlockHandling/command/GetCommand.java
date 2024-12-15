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
import com.mcmiddleearth.architect.customHeadManager.CustomHeadManagerData;
import com.mcmiddleearth.architect.specialBlockHandling.MushroomBlocks;
import com.mcmiddleearth.architect.specialBlockHandling.data.GetData;
import com.mcmiddleearth.pluginutil.FileUtil;
import com.mcmiddleearth.pluginutil.NumericUtil;
import com.mcmiddleearth.pluginutil.message.FancyMessage;
import com.mcmiddleearth.pluginutil.message.MessageType;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.LeatherArmorMeta;

/**
 *
 * @author Eriol_Eandur
 */
public class GetCommand extends AbstractArchitectCommand {

    @Override
    public boolean onCommand(CommandSender cs, Command command, String label, String[] args) {
        if (!(cs instanceof Player)) {
            PluginData.getMessageUtil().sendPlayerOnlyCommandError(cs);
            return true;
        }
        Player p = (Player) cs;
        if(!PluginData.hasPermission(p,Permission.GET_COMMAND)) {
            PluginData.getMessageUtil().sendNoPermissionError(cs);
            return true;
        }
        if(!PluginData.isModuleEnabled(p.getWorld(), Modules.SPECIAL_BLOCKS_GET)) {
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
        if(args[0].equalsIgnoreCase("armor")) {
            if(!PluginData.hasPermission(p, Permission.GET_ARMOR)) {
                PluginData.getMessageUtil().sendNoPermissionError(cs);
            } else {
                if(args.length>1) {
                    getArmor(p, args[1]);
                } else {
                    PluginData.getMessageUtil().sendNotEnoughArgumentsError(cs);
                }
            }
            return true;
        }
        if(args[0].equalsIgnoreCase("list")) {
            int page = 1;
            if(args.length>1 && NumericUtil.isInt(args[1])) {
                page = NumericUtil.getInt(args[1]);
            }
            List<FancyMessage> list = new ArrayList<>();
            for(String name: GetData.getVisibleNames(p)) {
                boolean isPrivate = GetData.isPrivate(name);
                String owner = Bukkit.getOfflinePlayer(GetData.getOwner(name)).getName();
                FancyMessage message = new FancyMessage(MessageType.INFO_NO_PREFIX,PluginData.getMessageUtil())
                                           .addFancy("- "+name, "/get "+name,"Click to get it.");
                if(isPrivate) {
                    message.addSimple(ChatColor.RED+" (private)");
                } else {
                    message.addSimple(ChatColor.YELLOW+" by "+owner);
                }
                message.addSimple(" - "+GetData.getDescription(name));
                list.add(message);
            }
            PluginData.getMessageUtil().sendFancyListMessage(p,
                            new FancyMessage(MessageType.INFO, PluginData.getMessageUtil())
                                    .addSimple(PluginData.getMessageUtil().STRESSED+"Item sets "
                                              +PluginData.getMessageUtil().INFO+"available with "
                                              +PluginData.getMessageUtil().STRESSED+"/get <item set>"),
                            list, "/get list", page);
            return true;
        }
        if(args.length<2 || args[1].equals("-o")) {
            if(!GetData.exists(args[0])) {
                sendItemSetNotFoundError(cs);
            } else {
                giveItems(p, GetData.getItems(args[0]), args.length>1);
                sendItemSetGivenMessage(cs);
            }
            return true;
        }
        if(args[0].equalsIgnoreCase("head")) {
            if(!PluginData.hasPermission(p, Permission.GET_HEAD)) {
                PluginData.getMessageUtil().sendNoPermissionError(cs);
            } else {
                if(args.length>1) {
                    getHeads(p, args[1]);
                } else {
                    PluginData.getMessageUtil().sendNotEnoughArgumentsError(cs);
                }
            }
            return true;
        }
        if(args[0].equalsIgnoreCase("delete")
                || args[0].equalsIgnoreCase("publish")
                || args[0].equalsIgnoreCase("unpublish")) {
            if(!GetData.exists(args[1])) {
                sendItemSetNotFoundError(cs);
                return true;
            }
            if(!GetData.isOwn(p,args[1])
                    && !PluginData.hasPermission(p, Permission.GET_OTHER)) {
                PluginData.getMessageUtil().sendNoPermissionError(cs);
                return true;
            }
            if(args[0].equalsIgnoreCase("delete")) {
                GetData.delete(args[1]);
                sendSetDeletedMessage(cs);
                return true;
            }
            if(!PluginData.hasPermission(p, Permission.GET_CREATE_PUBLIC)) {
                PluginData.getMessageUtil().sendNoPermissionError(cs);
                return true;
            }
            if(args[0].equalsIgnoreCase("publish")) {
                GetData.publish(args[1]);
                sendSetPublishedMessage(cs);
                return true;
            }
            if(args[0].equalsIgnoreCase("unpublish")) {
                GetData.unpublish(args[1]);
                sendSetUnpublishedMessage(cs);
                return true;
            }
            return true; //can't happen
        }
        /*if(args.length<2) {
            PluginData.getMessageUtil().sendNotEnoughArgumentsError(cs);
            return true;
        }*/
        if(args[0].equalsIgnoreCase("pcreate")) {
            if(!PluginData.hasPermission(p, Permission.GET_CREATE_PRIVATE)) {
                PluginData.getMessageUtil().sendNoPermissionError(cs);
                return true;
            }
            if(GetData.countPrivate(p)>10) {
                sendToManyPrivateSetsError(cs);
                return true;
            }
            if(GetData.exists(args[1])) {
                sendSetAlreadyExistsError(cs);
                return true;
            }
            GetData.saveItemSet((Player) cs, args[1], getDescription(args,2), true);
            sendItemSetSavedMessage(cs);
            return true;
        }
        if(args[0].equalsIgnoreCase("create")) {
            if(!PluginData.hasPermission(p, Permission.GET_CREATE_PUBLIC)) {
                PluginData.getMessageUtil().sendNoPermissionError(cs);
                return true;
            }
            if(GetData.exists(args[1])) {
                sendSetAlreadyExistsError(cs);
                return true;
            }
            GetData.saveItemSet((Player) cs, args[1], getDescription(args,2), false);
            sendItemSetSavedMessage(cs);
            return true;
        }
        PluginData.getMessageUtil().sendInvalidSubcommandError(cs);
        return true;
    }

    private String getDescription(String[] args, int start) {
        String result="";
        for(int i=start; i<args.length;i++) {
            result = result+args[i]+" ";
        }
        return result;
    }
    
    private void getHeads(Player p, String headName) {
        headName = headName.replace('\\','/');
        while(headName.contains("//")) {
            headName = headName.replace("//","/");
        }
        if(headName.endsWith("/")) {
            headName = headName.substring(0,headName.length()-1);
        }
        File file = new File(CustomHeadManagerData.getAcceptedHeadDir(),headName);
        if(!file.exists()) {
            headName = CustomHeadManagerData.getFullName(headName);
            if(headName.equals("")) {
                PluginData.getMessageUtil().sendErrorMessage(p, "Head not found in MCME Head Collection");
                return;
            }
            file = new File(CustomHeadManagerData.getAcceptedHeadDir(), headName);
        }
        if(file.exists() && file.isDirectory()) {
            List<File> headFiles = FileUtil.getFilesRecursive(file,FileUtil.getFileExtFilter(CustomHeadManagerData.getFileExtension()));
            for(File headFile:headFiles) {
                String headFilename = FileUtil.getRelativePath(headFile, file);
                headFilename = headFilename.substring(0,headFilename.lastIndexOf('.'));
                ItemStack head = CustomHeadManagerData.getHead(headName+'/'+headFilename);
                p.getInventory().addItem(head);
            }
            PluginData.getMessageUtil().sendInfoMessage(p, "Given all heads from: "
                                            +PluginData.getMessageUtil().STRESSED+headName);
        } else {
            ItemStack head = CustomHeadManagerData.getHead(headName);
            if(head!=null) {
                p.getInventory().addItem(head);
                PluginData.getMessageUtil().sendInfoMessage(p, "Given head: "
                                    +PluginData.getMessageUtil().STRESSED+headName);
            } else {
                PluginData.getMessageUtil().sendErrorMessage(p, "Head not found in MCME Head Collection");
            }
        }
    }
    
    private void getArmor(Player p, String color) {
        ArrayList<ItemStack> armor = new ArrayList();
        armor.add(new ItemStack(Material.LEATHER_HELMET));
        armor.add(new ItemStack(Material.LEATHER_CHESTPLATE));
        armor.add(new ItemStack(Material.LEATHER_LEGGINGS));
        armor.add(new ItemStack(Material.LEATHER_BOOTS));
        try {
            if(!color.startsWith("0x")) {
                color = "0x"+color;
            }
            java.awt.Color desiredcolor = java.awt.Color.decode(color);
            for (ItemStack is : armor) {
                LeatherArmorMeta meta = (LeatherArmorMeta) is.getItemMeta();
                meta.setColor(org.bukkit.Color.fromRGB(desiredcolor.getRed(), desiredcolor.getGreen(), desiredcolor.getBlue()));
                is.setItemMeta(meta);
                p.getInventory().addItem(is);
            }
            PluginData.getMessageUtil().sendInfoMessage(p, "Given colored leather armor!");
        } catch (NumberFormatException ex) {
            PluginData.getMessageUtil().sendErrorMessage(p, "The color " + color + " is not valid.");
        }
    }
    
    private void giveItems(Player p, ItemStack[] items, boolean overwrite) {
            for(int i=0; i<9; i++) {
                if(items[i]!=null && !items[i].getType().equals(Material.AIR)) {
                    if(overwrite) {
                        p.getInventory().setItem(i, items[i]);
                    } else {
                        p.getInventory().addItem(items[i]);
                }
            }
        }
    }

    private void sendNotEnabledErrorMessage(CommandSender cs) {
        PluginData.getMessageUtil().sendErrorMessage(cs, "Get command is not enabled in this world.");
    }

    private void sendItemSetNotFoundError(CommandSender cs) {
        PluginData.getMessageUtil().sendErrorMessage(cs, "Item set not found.");
    }

    private void sendSetDeletedMessage(CommandSender cs) {
        PluginData.getMessageUtil().sendInfoMessage(cs, "Item set deleted.");
    }

    private void sendSetPublishedMessage(CommandSender cs) {
        PluginData.getMessageUtil().sendInfoMessage(cs, "Item set published.");
    }

    private void sendSetUnpublishedMessage(CommandSender cs) {
        PluginData.getMessageUtil().sendInfoMessage(cs, "Item set unpublished.");
    }

    private void sendToManyPrivateSetsError(CommandSender cs) {
        PluginData.getMessageUtil().sendErrorMessage(cs, "You already have too many private item sets (max. 10).");
    }

    private void sendItemSetSavedMessage(CommandSender cs) {
        PluginData.getMessageUtil().sendInfoMessage(cs, "Item set saved.");
    }

    private void sendSetAlreadyExistsError(CommandSender cs) {
        PluginData.getMessageUtil().sendErrorMessage(cs, "An item set with that name already exists.");
    }
    
    private void sendItemSetGivenMessage(CommandSender cs) {
        PluginData.getMessageUtil().sendInfoMessage(cs, "Item set given.");
    }

    @Override
    public String getHelpPermission() {
        return Permission.GET_COMMAND.getPermissionNode();
    }

    @Override
    public String getShortDescription() {
        return ": Get special blocks for building.";
    }
    @Override
    public String getUsageDescription() {
        return ": Get special blocks for building directly into your inventory. Also manages kits of blocks for private and public use.\n "
                +ChatColor.WHITE+"Click for detailed help.";
    }
    
    @Override
    public String getHelpCommand() {
        return "/get help";
    }
    
    @Override
    protected void sendHelpMessage(Player player, int page) {
        helpHeader = "Help for "+PluginData.getMessageUtil().STRESSED+"command /get ... -";
        help = new String[][]{
                   {"/get ","<kitName> [-o]",": Get a kit of special blocks."," With -o flag your quickbar items are replaced (overwritten) with the items from the kit. Otherwise the kit items are placed at free slots."},
                   //{"/get logs","",": Get six sided logs, useful for trees."},
                   //{"/get doors","",": Get half doors."},
                   //{"/get plants","",": Get placeable plants."},
                   //{"/get slabs"," [#data value]",": Get double slabs",". If no data value argument is specified you will get all double slabs."},
                   //{"/get misc","",": Get miscellaneous blocks",". Piston tables, burning furnaces,..."},
                   {"/get list"," #page",": Shows a list of all item kits visible to you"," (public kits and your own private ones)"},
                   {"/get head"," <head or folder name>",": Get heads"," from the MCME Head Collection. If a folder name is specified you will get all heads in that folder."},
                   {"/get armor"," <color>",": Get dyed leather armor",". Color must be hex RGB code. For example 'FF0000' for red and '000000' for black."},
                   {"/get pcreate"," <kitName>",": Create a private item kit."," /get list shows this kit to you only. But everyone who knows it can use it. Each player may create 10 private kits. Private kits of a player are deleted when a player didn't log in for more than 100 days."},
                   {"/get create"," <kitName>",": Create a public item kit"," shown to everyone with /get list."},
                   {"/get publish"," <kitName>",": Make an item kit public."},
                   {"/get unpublish"," <kitName>",": Make an item kit public."}};
        super.sendHelpMessage(player, page);
    }
    


}
