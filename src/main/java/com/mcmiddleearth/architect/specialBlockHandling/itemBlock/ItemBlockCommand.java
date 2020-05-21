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
package com.mcmiddleearth.architect.specialBlockHandling.itemBlock;

import com.mcmiddleearth.architect.Modules;
import com.mcmiddleearth.architect.Permission;
import com.mcmiddleearth.architect.PluginData;
import com.mcmiddleearth.architect.additionalCommands.AbstractArchitectCommand;
import com.mcmiddleearth.architect.specialBlockHandling.specialBlocks.SpecialBlockItemBlock;
import com.mcmiddleearth.pluginutil.NumericUtil;
import com.mcmiddleearth.pluginutil.WEUtil;
import com.sk89q.worldedit.regions.Region;
import java.util.List;
import net.md_5.bungee.api.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 *
 * @author EriolEandur
 */
public class ItemBlockCommand extends AbstractArchitectCommand {

    @Override
    public boolean onCommand(CommandSender cs, Command command, String label, String[] args) {
        if (!(cs instanceof Player)) {
            PluginData.getMessageUtil().sendPlayerOnlyCommandError(cs);
            return true;
        }
        Player p = (Player) cs;
        if (!PluginData.isModuleEnabled(p.getWorld(), Modules.SPECIAL_BLOCKS_PLACE)) {
            sendNotEnabledErrorMessage(cs);
            return true;
        }
        if (!PluginData.hasPermission(p,Permission.ITEM_BLOCK_COMMAND)) {
            PluginData.getMessageUtil().sendNoPermissionError(cs);
            return true;
        }
        if ((args.length<2 && !args[0].equalsIgnoreCase("list")) || args[0].equalsIgnoreCase("help")) {
            int page = 1;
            if(args.length>1 && NumericUtil.isInt(args[1])) {
                page = NumericUtil.getInt(args[1]);
            }
            sendHelpMessage((Player)cs,page);
            return true;
        } else if (args[0].equalsIgnoreCase("stats")) {
            if(args.length<2) {
                PluginData.getMessageUtil().sendNotEnoughArgumentsError(cs);
                return true;
            }
            if(!NumericUtil.isInt(args[1])) {
                sendNotANumberMessage(p);
                return true;
            }
            int radius = NumericUtil.getInt(args[1]);
            int radiusY = radius;
            if(args.length>2 && NumericUtil.isInt(args[2])) {
                radiusY = NumericUtil.getInt(args[2]);
            }
            List<SpecialBlockItemBlock.ItemBlockStat> stats = SpecialBlockItemBlock.getStatistic(p.getLocation(), 
                                                                              radius, radiusY);
            PluginData.getMessageUtil().sendInfoMessage(p, "Item block statistics:");
            stats.forEach((itemStat) -> {
                PluginData.getMessageUtil().sendIndentedInfoMessage(p,
                        "Type: "+itemStat.getItem().getType()
                                +" Durability: "+itemStat.getItem().getDurability()
                                +" Number: "+itemStat.getCount());
            });
            return true;
        } else if (args[0].equalsIgnoreCase("glow")) {
            if(args.length<2) {
                PluginData.getMessageUtil().sendNotEnoughArgumentsError(cs);
                return true;
            }
            if(!NumericUtil.isInt(args[1])) {
                ItemBlockManager.removeGlowPlayer(p);
                PluginData.getMessageUtil().sendInfoMessage(cs, "Glowing of entities around you disabled.");
                return true;
            } else {
                ItemBlockManager.addGlowPlayer(p,NumericUtil.getInt(args[1]));
                PluginData.getMessageUtil().sendInfoMessage(cs, "Glowing of entities up to "+NumericUtil.getInt(args[1])+" blocks around you enabled.");
                return true;
            }
        } else if (args[0].equalsIgnoreCase("remove")) {
            if (!PluginData.hasPermission(p,Permission.ITEM_BLOCK_COMMAND_REMOVE)) {
                PluginData.getMessageUtil().sendNoPermissionError(cs);
                return true;
            }
            if(!NumericUtil.isInt(args[1])) {
                sendNotANumberMessage(p);
                return true;
            }
            if(args.length>2 && NumericUtil.isInt(args[2])) {
                SpecialBlockItemBlock.removeArmorStands(p.getLocation(), 
                                                        NumericUtil.getInt(args[1]), 
                                                        NumericUtil.getInt(args[2]),false);
            } else {
                SpecialBlockItemBlock.removeArmorStands(p.getLocation(), 
                                                        NumericUtil.getInt(args[1]), 
                                                        NumericUtil.getInt(args[1]),false);
            }
            sendItemRemovedMessage(p);
            return true;
        } else if (args[0].equalsIgnoreCase("list")) {
            PluginData.getMessageUtil().sendInfoMessage(p, "Itemblock limit regions:");
            ItemBlockManager.getRegions().forEach((name,region) -> {
                PluginData.getMessageUtil().sendNoPrefixInfoMessage(p, ChatColor.BLACK+"..."
                        +ChatColor.AQUA+"- "+name+", limit: "+region.getLimit());
            });
            return true;
        } else if (args[0].equalsIgnoreCase("create")) {
            if (!PluginData.hasPermission(p,Permission.ITEM_BLOCK_REGION_CREATE)) {
                PluginData.getMessageUtil().sendNoPermissionError(cs);
                return true;
            }
            Region weRegion = WEUtil.getSelection((Player)cs);
            if(weRegion==null) {
                PluginData.getMessageUtil().sendErrorMessage(cs, "Please make a WE selection first.");
                return true;
            }
            ItemBlockRegion region = ItemBlockManager.getRegion(args[1]);
            if(region!=null) {
                PluginData.getMessageUtil().sendErrorMessage(cs, "An itemblock region with that name already exists.");
                return true;
            }
            region = new ItemBlockRegion(args[1],weRegion);
            ItemBlockManager.saveItemBlockRegion(region);
            ItemBlockManager.addRegion(region);
            PluginData.getMessageUtil().sendInfoMessage(p, "Region created.");
            return true;
        } else if (args[0].equalsIgnoreCase("delete")) {
            if (!PluginData.hasPermission(p,Permission.ITEM_BLOCK_REGION_CREATE)) {
                PluginData.getMessageUtil().sendNoPermissionError(cs);
                return true;
            }
            ItemBlockRegion region = ItemBlockManager.getRegion(args[1]);
            if(region==null) {
                PluginData.getMessageUtil().sendErrorMessage(cs, "There is no itemblock region with that name.");
                return true;
            }
            ItemBlockManager.removeRegion(args[1]);
            PluginData.getMessageUtil().sendInfoMessage(p, "Region deleted.");
            return true;
        } else if (args[0].equalsIgnoreCase("limit")) {
            if (!PluginData.hasPermission(p,Permission.ITEM_BLOCK_REGION_LIMIT)) {
                PluginData.getMessageUtil().sendNoPermissionError(cs);
                return true;
            }
            if(args.length<3) {
                PluginData.getMessageUtil().sendErrorMessage(p, "You must specify a limit.");
                return true;
            }
            if(!NumericUtil.isInt(args[2])) {
                sendNotANumberMessage(p);
                return true;
            }
            int limit = NumericUtil.getInt(args[2]);
            if(args[1].equalsIgnoreCase("-base")) {
                PluginData.setItemBlockBaseLimit(p.getWorld(), limit);
                PluginData.getMessageUtil().sendInfoMessage(p, "Item block base limit set to "+limit+".");
                return true;
            }
            ItemBlockRegion region = ItemBlockManager.getRegion(args[1]);
            if(region==null) {
                PluginData.getMessageUtil().sendErrorMessage(cs, "There is no itemblock region with that name.");
                return true;
            }
            region.setLimit(limit);
            PluginData.getMessageUtil().sendInfoMessage(p, "Region limit set to "+limit+".");
            ItemBlockManager.saveItemBlockRegion(region);
            return true;
        }
        PluginData.getMessageUtil().sendInvalidSubcommandError(cs);
        return true;
    }
    
    private void sendNotEnabledErrorMessage(CommandSender cs) {
        PluginData.getMessageUtil().sendErrorMessage(cs, "Item blocks are not enabled in this world.");
    }
    
    private void sendItemRemovedMessage(CommandSender cs) {
        PluginData.getMessageUtil().sendInfoMessage(cs, "Item block armor stands removed.");
    }
    
    private void sendNotANumberMessage(Player p) {
        PluginData.getMessageUtil().sendErrorMessage(p, "You need to specify a texture number.");
    }
    
    @Override
    public String getHelpPermission() {
        return Permission.ITEM_BLOCK_COMMAND.getPermissionNode();
    }

    @Override
    public String getShortDescription() {
        return ": manages item block armor stands.";
    }
    @Override
    public String getUsageDescription() {
        return " remove <radius> | list|create|remove|limit <region> [limit]: Removes all item block armor stands within <radius> blocks around you and manages item block limit regions.";
    }
    
    @Override
    public String getHelpCommand() {
        return "/itemBlock help";
    }

    @Override
    protected void sendHelpMessage(Player player, int page) {
        helpHeader = "Help for "+PluginData.getMessageUtil().STRESSED+"Item block command -";
        help = new String[][]{{getUsageDescription(),"    ","    ","    "}};
        super.sendHelpMessage(player, page);
    }
    

}
